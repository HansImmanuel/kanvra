package com.kanvra.realtime.config;

import com.kanvra.common.config.KanvraProperties;
import com.kanvra.realtime.auth.RealtimeHandshakeInterceptor;
import com.kanvra.realtime.auth.RealtimeSubscriptionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket/STOMP configuration (docs/SPEC.md §15, TECH_DOC.md §13).
 * Endpoint {@code /ws}; topics {@code /topic/projects/{projectId}}. The
 * handshake interceptor authenticates (cookie or Bearer on the upgrade — no
 * query-string tokens), and the inbound channel interceptor enforces project
 * membership at SUBSCRIBE time.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String WS_ENDPOINT = "/ws";

    private final KanvraProperties properties;
    private final RealtimeHandshakeInterceptor handshakeInterceptor;
    private final RealtimeSubscriptionInterceptor subscriptionInterceptor;

    public WebSocketConfig(KanvraProperties properties,
                           RealtimeHandshakeInterceptor handshakeInterceptor,
                           RealtimeSubscriptionInterceptor subscriptionInterceptor) {
        this.properties = properties;
        this.handshakeInterceptor = handshakeInterceptor;
        this.subscriptionInterceptor = subscriptionInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker; a full external broker is out of scope for MVP.
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(WS_ENDPOINT)
                .setAllowedOrigins(properties.getCorsOrigins().toArray(String[]::new))
                .addInterceptors(handshakeInterceptor);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscriptionInterceptor);
    }
}