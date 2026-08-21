package com.kanvra.realtime.auth;

import com.kanvra.common.security.AuthenticatedUser;
import com.kanvra.common.security.JwtService;
import com.kanvra.common.security.JwtTokenType;
import com.kanvra.project.service.ProjectAccessService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Realtime inbound STOMP authorization (docs/SPEC.md §15.1, TECH_DOC.md §13.1):
 * <ul>
 *   <li>{@code CONNECT} — accepts the principal attached by the handshake
 *       interceptor (cookie or Bearer on the HTTP Upgrade); falls back to a
 *       {@code Bearer} token carried on the STOMP CONNECT frame for clients that
 *       cannot set handshake headers; rejects the session if no principal exists.</li>
 *   <li>{@code SUBSCRIBE} — every subscription destination must be
 *       {@code /topic/projects/{projectId}} and the principal must be a member of
 *       that project. Unauthorized subscriptions are denied (an ERROR frame is
 *       sent to the client).</li>
 * </ul>
 */
@Component
public class RealtimeSubscriptionInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSubscriptionInterceptor.class);
    private static final String TOPIC_PREFIX = "/topic/projects/";

    private final ProjectAccessService access;
    private final JwtService jwtService;

    public RealtimeSubscriptionInterceptor(ProjectAccessService access, JwtService jwtService) {
        this.access = access;
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getMessageType() == null) {
            return message;
        }

        switch (accessor.getMessageType()) {
            case CONNECT -> {
                if (accessor.getSessionAttributes() == null) {
                    throw new MessageDeliveryException("Unauthorized");
                }
                // Handshake interceptor already authenticated (cookie / Bearer at Upgrade).
                if (accessor.getSessionAttributes().get(RealtimeHandshakeInterceptor.ATTR_USER) != null) {
                    return message;
                }
                // Fallback: Bearer token on the STOMP CONNECT frame.
                String auth = accessor.getFirstNativeHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    try {
                        AuthenticatedUser user = jwtService.parse(auth.substring(7), JwtTokenType.ACCESS);
                        accessor.getSessionAttributes().put(RealtimeHandshakeInterceptor.ATTR_USER, user);
                        accessor.getSessionAttributes().put(RealtimeHandshakeInterceptor.ATTR_USER_ID, user.id());
                        return message;
                    } catch (JwtException | IllegalArgumentException ex) {
                        log.warn("Realtime CONNECT rejected: invalid token");
                        throw new MessageDeliveryException("Unauthorized");
                    }
                }
                log.warn("Realtime CONNECT rejected: no principal");
                throw new MessageDeliveryException("Unauthorized");
            }
            case SUBSCRIBE -> {
                String destination = accessor.getDestination();
                if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
                    log.warn("Invalid realtime subscription destination: {}", destination);
                    throw new MessageDeliveryException("Bad destination");
                }
                Long projectId = parseProjectId(destination);
                if (projectId == null) {
                    throw new MessageDeliveryException("Invalid project id in subscription");
                }
                AuthenticatedUser user = accessor.getSessionAttributes() == null
                        ? null
                        : (AuthenticatedUser) accessor.getSessionAttributes().get(RealtimeHandshakeInterceptor.ATTR_USER);
                if (user == null) {
                    throw new MessageDeliveryException("Unauthenticated subscription");
                }
                if (!access.isMember(user.id(), projectId)) {
                    log.warn("User {} tried to subscribe to project {} without membership", user.id(), projectId);
                    throw new MessageDeliveryException("Access denied: not a project member");
                }
                return message;
            }
            default -> {
                return message;
            }
        }
    }

    private Long parseProjectId(String destination) {
        String rest = destination.substring(TOPIC_PREFIX.length());
        int end = rest.indexOf('/');
        String id = end >= 0 ? rest.substring(0, end) : rest;
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}