package com.ccm.common.jwt;

import com.ccm.common.exception.UpdateException;
import com.ccm.common.multitenant.TenantContext;
import com.ccm.common.pojo.CCMUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class JwtUtils {

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationMs}")
    private long jwtExpirationMs;

    private static final String SECRET_KEY = "U2FsdGVkX19H0qp5uV8zYg=="; // 128-bit Base64
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    public String generateJwtToken(Authentication authentication, String sessionId) {
        String clientId = TenantContext.getCurrentTenant();
        CCMUser userDetails = (CCMUser) authentication.getPrincipal();

        return Jwts.builder()
                .claim("clientId", clientId)
                .claim("sessionId", sessionId)
                .setSubject(userDetails.getLoginId())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(SignatureAlgorithm.HS512, jwtSecret.getBytes(StandardCharsets.UTF_8)).compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser().setSigningKey(jwtSecret.getBytes()).parseClaimsJws(token).getBody().getSubject();
    }

    public boolean isTokenExpired(String authToken) {
        return Jwts.parser().setSigningKey(jwtSecret.getBytes()).parseClaimsJws(authToken).getBody().getExpiration()
                .before(new Date(System.currentTimeMillis()));
    }

    public boolean validateJwtToken(String authToken) {
        String username = getUserNameFromJwtToken(authToken);
        String clientId = getClientIdFromJwtToken(authToken);
        String sessionId = getSessionIdFromJwtToken(authToken);
        String redisKey = clientId+ "::" +username+"::"+sessionId;
        String existingToken = redisTemplate.opsForValue().get(redisKey);

        if(existingToken != null) {
            Jwts.parser().setSigningKey(jwtSecret.getBytes()).parseClaimsJws(authToken);
            return true;
        } else {
            return false;
        }
    }

    public String getClientIdFromJwtToken(String token) {
        return Jwts.parser().setSigningKey(jwtSecret.getBytes()).parseClaimsJws(token).getBody().get("clientId",
                String.class);
    }

    public String getSessionIdFromJwtToken(String token) {
        return Jwts.parser().setSigningKey(jwtSecret.getBytes()).parseClaimsJws(token).getBody().get("sessionId",
                String.class);
    }

    public String refreshJwtTokenIfExpiringSoon() {
        String token;
        try {
            token = TokenContext.getToken();
            String username = getUserNameFromJwtToken(token);
            String clientId = TenantContext.getCurrentTenant();
            String existingSessionId = getSessionIdFromJwtToken(token);
            String redisKey = clientId+"::" +username+"::"+existingSessionId;

            if (token == null || token.isEmpty()) {
                throw new IllegalStateException("No JWT token found in token context.");
            }

            Claims claims = Jwts.parser()
                    .setSigningKey(jwtSecret.getBytes(StandardCharsets.UTF_8))
                    .parseClaimsJws(token)
                    .getBody();

            Date expiration = claims.getExpiration();
            long timeLeft = expiration.getTime() - System.currentTimeMillis();

            // If timeLeft is less than 2 minutes then refresh the token
            if (timeLeft < 2 * 60 * 1000) {
                token = Jwts.builder()
                        .setSubject(claims.getSubject())
                        .claim("userName", claims.get("userName"))
                        .claim("clientId", claims.get("clientId"))
                        .claim("sessionId", claims.get("sessionId"))
                        .setIssuedAt(new Date())
                        .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                        .signWith(SignatureAlgorithm.HS512, jwtSecret.getBytes(StandardCharsets.UTF_8))
                        .compact();
                redisTemplate.delete(redisKey);
                redisTemplate.opsForValue().set(redisKey, token, 120, TimeUnit.MINUTES);
            }

            // Return original token if not expiring soon
            return token;
        } catch (ExpiredJwtException e) {
            logger.error("JWT expired, can't refresh: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error while refreshing JWT: {}", e.getMessage());
            throw e;
        }
    }

    public static String decryptFromBase64(String base64CipherText) {
        if (base64CipherText == null || base64CipherText.isEmpty()) {
            return null;
        }
        try {
            // Decode the key from Base64
            byte[] aesKey = Base64.getDecoder().decode(SECRET_KEY);
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");

            // Decode base64 input to byte array (iv + encrypted data)
            byte[] encryptedIvAndText = Base64.getDecoder().decode(base64CipherText);

            // Extract IV (first 16 bytes)
            byte[] iv = Arrays.copyOfRange(encryptedIvAndText, 0, 16);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Extract the actual encrypted bytes
            byte[] encryptedBytes = Arrays.copyOfRange(encryptedIvAndText, 16, encryptedIvAndText.length);

            // Initialize Cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            // Decrypt
            byte[] original = cipher.doFinal(encryptedBytes);

            return new String(original, "UTF-8");
        } catch (Exception e) {
            throw new UpdateException("Decryption failed: " + e.getMessage());
        }
    }

    public void sendErrorResponse(HttpServletResponse response, String errorMessge, int statusCode, String statsMessage) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.getWriter().write("{\"errorMessage\": \"" + errorMessge + "\", \"errorCode\": \"FORBIDDEN\"}");
    }
}

