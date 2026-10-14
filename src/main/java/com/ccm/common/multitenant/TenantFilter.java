package com.ccm.common.multitenant;

import com.ccm.common.jwt.JwtUtils;
import com.ccm.common.repository.ClientDetailsRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

@Order(1)
@Component
public class TenantFilter extends GenericFilterBean {
    private static final Logger logger = LoggerFactory.getLogger(TenantFilter.class);

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    private ClientDetailsRepository clientDetailsRepository;

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain filterChain) throws ServletException, IOException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if(logger.isDebugEnabled()){
            logger.debug(">>> In TenantFilter class -> TenantContext.getCurrentTenant() ==> "+TenantContext.getCurrentTenant());
        }
        if (TenantContext.getCurrentTenant() != null) {
            filterChain.doFilter(request, response);
            return;
        }else{
            String requestURI = request.getRequestURI();
            if (isPublicEndpoint(requestURI)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        String tenantId = request.getHeader("client_id");
        if (tenantId == null || tenantId.trim().isEmpty()) {
            jwtUtils.sendErrorResponse(response, "Client Id cannot be empty.", HttpServletResponse.SC_FORBIDDEN, HttpStatus.FORBIDDEN.toString());
            return;
        } else {
            if (!clientDetailsRepository.existsByClientId(tenantId)) {
                jwtUtils.sendErrorResponse(response, "Invalid Client Id", HttpServletResponse.SC_FORBIDDEN, HttpStatus.FORBIDDEN.toString());
                return;
            }
        }
        if(logger.isDebugEnabled()) {
            logger.debug(">>> [TenantFilter] Setting TenantContext to: {}", tenantId);
        }
        TenantContext.setCurrentTenant(tenantId);

        try{
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            if(logger.isDebugEnabled()) {
                logger.debug(">>> [TenantFilter] Cleared TenantContext");
            }
        }
    }

    private boolean isPublicEndpoint(String requestURI) {
        if(logger.isDebugEnabled()){
            logger.debug(">>> Checking permitted URL: {}", requestURI);
        }

        return requestURI.matches(".*/swagger-ui\\.html.*")
                || requestURI.matches(".*/v3/api-docs.*")
                || requestURI.matches(".*/swagger-ui/.*")
                || requestURI.matches(".*/swagger-resources.*")
                || requestURI.matches(".*/configuration/.*")
                || requestURI.matches(".*/webjars/.*")
                || requestURI.matches(".*/actuator.*")
                || requestURI.endsWith("/check");
    }
}

