package com.kanvra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.outbox.publisher.OutboxPublisher;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Realtime WebSocket end-to-end (docs/SPEC.md §15, §20 vertical-slice tail):
 * proves the browser-facing loop that was missing after Sprint 2 — a domain event
 * flows outbox → Kafka → RealtimeConsumer → STOMP broadcast → subscribed client.
 * Also verifies handshake auth (rejects missing credentials) and that subscription
 * authorization blocks non-members from a project topic.
 */
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeWebSocketIntegrationTest extends AbstractIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Test
    void authorizedMemberReceivesTaskCreatedBroadcast() throws Exception {
        String token = register("ws-owner@example.com");
        long projectId = createProject(token, "Realtime");
        long boardId = firstBoardId(projectId, token);
        long columnId = firstColumnId(boardId, token);

        // Member subscribes to the project topic with a Bearer access token on the
        // WebSocket handshake (non-browser path, SPEC §15.1).
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        StompSession session = connect(token, frames);
        session.subscribe("/topic/projects/" + projectId, frameHandler(frames));
        try {
            // Create a task via REST; the response carries the outbox eventId.
            MvcResult created = mockMvc.perform(post("/api/v1/columns/" + columnId + "/tasks")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "uuid-ws-e2e")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Realtime task\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String eventId = MAPPER.readTree(created.getResponse().getContentAsString()).get("eventId").asText();
            long taskId = MAPPER.readTree(created.getResponse().getContentAsString()).get("id").asLong();

            // Flush the outbox; RealtimeConsumer broadcasts TASK_CREATED.
            outboxPublisher.publishPending();

            JsonNode message = awaitFrame(frames, "TASK_CREATED");
            assertThat(message.get("eventId").asText()).isEqualTo(eventId);
            assertThat(message.path("payload").path("taskId").asLong()).isEqualTo(taskId);
            assertThat(message.path("projectId").asLong()).isEqualTo(projectId);
        } finally {
            session.disconnect();
        }
    }

    @Test
    void handshakeRejectsMissingCredentials() throws Exception {
        WebSocketStompClient client = stompClient();
        CompletableFuture<StompSession> future = client.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                new StompHeaders(),
                new StompSessionHandlerAdapter() {
                });
        // The handshake interceptor rejects connections with no credentials, so the
        // session future must fail (transport/unsuccessful-handshake exception).
        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void nonMemberReceivesNoBoardEvents() throws Exception {
        String owner = register("ws-owner-b@example.com");
        String outsider = register("ws-outsider@example.com");
        long projectId = createProject(owner, "Private WS");
        long boardId = firstBoardId(projectId, owner);
        long columnId = firstColumnId(boardId, owner);

        // An authenticated-but-non-member user connects (handshake OK) but must be
        // rejected at SUBSCRIBE time (RealtimeSubscriptionInterceptor).
        BlockingQueue<String> outsiderFrames = new LinkedBlockingQueue<>();
        StompSession outsiderSession = connect(outsider, outsiderFrames);
        try {
            outsiderSession.subscribe("/topic/projects/" + projectId, frameHandler(outsiderFrames));
        } catch (Exception e) {
            // Subscription rejected (MessageDeliveryException) — acceptable; the
            // important invariant below is that the outsider never receives events.
        }

        // Trigger a board event as the owner.
        mockMvc.perform(post("/api/v1/columns/" + columnId + "/tasks")
                        .header("Authorization", "Bearer " + owner)
                        .header("Idempotency-Key", "uuid-ws-owner2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Private\"}"))
                .andExpect(status().isCreated());
        outboxPublisher.publishPending();

        // Even after the broadcast propagates, the outsider's queue stays empty —
        // project membership is enforced on subscription, so no leaks.
        Thread.sleep(2000);
        assertThat(outsiderFrames).isEmpty();
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());
        return client;
    }

    private StompSession connect(String accessToken, BlockingQueue<String> errors) throws Exception {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setBearerAuth(accessToken);
        CompletableFuture<StompSession> future = stompClient().connectAsync(
                "ws://localhost:" + port + "/ws",
                handshake,
                new StompHeaders(),
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                                byte[] payload, Throwable exception) {
                        errors.add("ERROR " + exception.getMessage());
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        errors.add("TRANSPORT:" + exception.getMessage());
                    }
                });
        return future.get(10, TimeUnit.SECONDS);
    }

    private StompFrameHandler frameHandler(BlockingQueue<String> frames) {
        return new StompFrameHandler() {
            @Override
            public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add(String.valueOf(payload));
            }
        };
    }

    /** Polls for a frame whose JSON "type" equals {@code type}, parsing it. */
    private JsonNode awaitFrame(BlockingQueue<String> frames, String type) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            String body = frames.poll(1500, TimeUnit.MILLISECONDS);
            if (body == null) {
                continue;
            }
            JsonNode node = MAPPER.readTree(body);
            if (type.equals(node.path("type").asText())) {
                return node;
            }
        }
        throw new AssertionError("Did not receive a " + type + " WS frame within timeout");
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"User\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("access_token").getValue();
    }

    private long createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long firstBoardId(long projectId, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/" + projectId + "/boards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString()).get(0).get("id").asLong();
    }

    private long firstColumnId(long boardId, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/boards/" + boardId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString()).get("columns").get(0).get("id").asLong();
    }
}