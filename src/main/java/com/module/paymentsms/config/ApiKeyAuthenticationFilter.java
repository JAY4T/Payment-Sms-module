package com.module.paymentsms.config;

import com.google.gson.Gson;
import com.module.paymentsms.service.ApiClientService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Gate for every /api/** route except the two Intasend webhook callbacks (Intasend can't send
// custom headers, so those stay open - see WEBHOOK_PATHS below). Two separate credential
// schemes:
//  - /api/v1/admin/** : a single static admin secret (ADMIN_API_KEY), since this is where API
//    clients themselves get created - there's no API client yet to authenticate the request
//    that creates the first one.
//  - everything else under /api/** : per-client X-Api-Key / X-Api-Secret, validated against the
//    api_clients table.
@Component
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> WEBHOOK_PATHS = List.of(
            "/api/v1/intasend/transaction/webhook",
            "/api/v1/intasend/transaction/send-money-webhook"
    );

    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin/";

    private final ApiClientService apiClientService;

    @Value("${admin.api.key}")
    private String adminApiKey;

    // @Lazy so this filter (needed to build the SecurityFilterChain bean, which Spring
    // constructs early - before JPA/EntityManager infrastructure is necessarily ready) gets a
    // lazy-init proxy instead of forcing ApiClientService -> ApiClientDao -> EntityManager to
    // resolve at filter-chain construction time.
    @Autowired
    public ApiKeyAuthenticationFilter(@Lazy ApiClientService apiClientService) {
        this.apiClientService = apiClientService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || WEBHOOK_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith(ADMIN_PATH_PREFIX)) {
            String providedAdminKey = request.getHeader("X-Admin-Secret");
            if (adminApiKey != null && !adminApiKey.isEmpty() && adminApiKey.equals(providedAdminKey)) {
                setAuthentication("admin", "ROLE_ADMIN");
                filterChain.doFilter(request, response);
            } else {
                log.warn("Rejected admin request to {} - missing or invalid X-Admin-Secret", path);
                writeUnauthorized(response, "Missing or invalid admin credentials");
            }
            return;
        }

        String apiKey = request.getHeader("X-Api-Key");
        String apiSecret = request.getHeader("X-Api-Secret");

        if (apiClientService.validateCredentials(apiKey, apiSecret)) {
            setAuthentication(apiKey, "ROLE_API_CLIENT");
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rejected request to {} - missing or invalid API credentials", path);
            writeUnauthorized(response, "Missing or invalid API credentials");
        }
    }

    private void setAuthentication(String principal, String authority) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("errors", null);

        response.getWriter().write(new Gson().toJson(body));
    }
}
