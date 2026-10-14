package com.ccm.common.jwt;

import com.ccm.common.multitenant.TenantContext;
import com.ccm.common.pojo.CCMUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

// This filter validates JWT tokens and sets authentication context.
@Order(2)
@Component
public class JWTTokenFilter extends GenericFilterBean {

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationMs}")
    private long jwtExpirationMs;

    private static final Logger logger = LoggerFactory.getLogger(JWTTokenFilter.class);

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        try{
            if(isPermittedUrl(request)){
                filterChain.doFilter(request, response);
            } else {
                String token = JwtUtils.decryptFromBase64(parseJwtToken(request));
                if (token != null) {
                    if(jwtUtils.validateJwtToken(token)) {
                        Claims claims = Jwts.parser().setSigningKey(jwtSecret.getBytes(StandardCharsets.UTF_8)).parseClaimsJws(token).getBody();

                        String loginId = claims.get("sub", String.class); // Extract username from JWT
                        String clientId = claims.get("clientId", String.class); // Extract clientId from JWT
                        TenantContext.setCurrentTenant(clientId);
                        TokenContext.setToken(token);

                        CCMUser userDetails = CCMUser.builder()
                                .loginId(loginId)
                                .build();
                        setAuthenticationContext(userDetails, request);
                        filterChain.doFilter(request, response);
                    } else {
                        jwtUtils.sendErrorResponse(response, "Invalid JWT token.", HttpServletResponse.SC_FORBIDDEN, HttpStatus.FORBIDDEN.toString());
                    }
                } else {
                    jwtUtils.sendErrorResponse(response, "JWT token cannot be empty.", HttpServletResponse.SC_FORBIDDEN, HttpStatus.FORBIDDEN.toString());
                }
            }
        }catch (MalformedJwtException  | SignatureException e ) {
            logger.error("Invalid JWT token: {}", e.getMessage());
            jwtUtils.sendErrorResponse(response, "Invalid JWT token.", HttpServletResponse.SC_FORBIDDEN, HttpStatus.FORBIDDEN.toString());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token expired.: {}", e.getMessage());
            jwtUtils.sendErrorResponse(response, "JWT token expired.",HttpServletResponse.SC_UNAUTHORIZED, HttpStatus.UNAUTHORIZED.toString());
        } catch (UnsupportedJwtException e) {
            logger.error("Unsupported JWT token.: {}", e.getMessage());
            jwtUtils.sendErrorResponse(response, "Unsupported JWT token.",HttpServletResponse.SC_FORBIDDEN, HttpStatus.FORBIDDEN.toString());
        } catch (RuntimeException e) {
            logger.error("Unknown Exception has occurred.: {}", e.getMessage());
            jwtUtils.sendErrorResponse(response, e.getMessage(),HttpServletResponse.SC_FORBIDDEN, HttpStatus.FORBIDDEN.toString());
        }
        finally {
            TokenContext.clear();
            TenantContext.clear();
        }
    }

    private boolean isPermittedUrl(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        if(logger.isDebugEnabled()){
            logger.debug("requestURI = {}",requestURI);
        }

        return requestURI.matches(".*/swagger-ui\\.html.*")
                || requestURI.matches(".*/v3/api-docs.*")
                || requestURI.matches(".*/swagger-ui/.*")
                || requestURI.matches(".*/swagger-resources.*")
                || requestURI.matches(".*/configuration/.*")
                || requestURI.matches(".*/webjars/.*")
                || requestURI.matches(".*/actuator.*")
                || requestURI.endsWith("/login")
                || requestURI.endsWith("/check");
    }

    private String parseJwtToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private void setAuthenticationContext(CCMUser userDetails, HttpServletRequest request) {
        if (userDetails.getLoginId() != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Collection<? extends GrantedAuthority> authorities = null;
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            if(logger.isDebugEnabled()){
                logger.debug("Authenticated user: {}", userDetails.getLoginId());
            }
            // Set the authentication in the Security Context
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }
}