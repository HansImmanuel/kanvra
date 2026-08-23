package com.kanvra.realtime.auth;

import com.kanvra.common.security.AuthenticatedUser;
import com.kanvra.common.security.CookieService;
import com.kanvra.common.security.JwtService;
import com.kanvra.common.security.JwtTokenType;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Realtime handshake authentication (docs/SPEC.md §15.1): browser
 * clients authenticate with the httpOnly {@code access_token} cookie;
 * non-browser
 * clients send {@code Authorization: Bearer <access_token>} on the WebSocket
 * HTTP
 * Upgrade request. A valid access token is parsed and attached to the session
 * attributes (for the later SUBSCRIBE membership check).
 *
 * <p>
 * Query-string tokens are explicitly not supported (they leak into
 * proxy/history logs).
 */
@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHandshakeInterceptor.class);

    public static final String ATTR_USER = "REALTIME_USER";
    public static final String ATTR_USER_ID = "REALTIME_USER_ID";

    private final JwtService jwtService;

    public RealtimeHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest http = servletRequest.getServletRequest();
            String cookieToken = CookieService.readCookie(http, CookieService.ACCESS_COOKIE);
            if (cookieToken != null && !cookieToken.isBlank()) {
                token = cookieToken;
            }
        }
        if (token == null) {
            // Bearer on the HTTP Upgrade (non-browser clients)
            String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (auth != null && auth.startsWith("Bearer ")) {
                token = auth.substring(7);
            }
        }
        if (token == null) {
            log.warn("Realtime handshake rejected: no credentials");
            return false;
        }
        try {
            AuthenticatedUser user = jwtService.parse(token, JwtTokenType.ACCESS);
            attributes.put(ATTR_USER, user);
            attributes.put(ATTR_USER_ID, user.id());
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Realtime handshake rejected: invalid access token");
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // nothing
    }
}