package com.kanvra;

import com.kanvra.analytics.model.ProjectAnalytics;
import com.kanvra.analytics.repository.AnalyticsEventRepository;
import com.kanvra.analytics.repository.ProjectAnalyticsRepository;
import com.kanvra.outbox.publisher.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the full outbox -> Kafka -> Analytics Consumer slice (docs/SPEC.md
 * §12.5): creating tasks publishes {@code task.created} events through the
 * outbox; the Analytics Consumer (group {@code kanvra-analytics}) increments
 * the project's counter row idempotently, and the analytics endpoint returns
 * the event-derived counters plus the live cards-per-column. Non-members get
 * 403.
 */
@AutoConfigureMockMvc
class AnalyticsKafkaIntegrationTest extends AbstractIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private ProjectAnalyticsRepository analyticsRepository;

    @Autowired
    private AnalyticsEventRepository eventRepository;

    @Test
    void taskCreationFlowsThroughOutboxIntoAnalyticsCountersAndEndpoint() throws Exception {
        String owner = register("owner-analytics@example.com");
        long projectId = createProject(owner, "Analytics Project");
        long columnId = firstColumnId(projectId, owner);

        mockMvc.perform(post("/api/v1/columns/" + columnId + "/tasks")
                        .header("Authorization", "Bearer " + owner)
                        .header("Idempotency-Key", "analytics-task-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Analytics task one\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/columns/" + columnId + "/tasks")
                        .header("Authorization", "Bearer " + owner)
                        .header("Idempotency-Key", "analytics-task-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Analytics task two\"}"))
                .andExpect(status().isCreated());

        // Flush the outbox synchronously (the @Scheduled publisher may also fire).
        outboxPublisher.publishPending();

        // The consumer runs on its own thread; poll until the counters land.
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline
                && analyticsRepository.findById(projectId).map(ProjectAnalytics::getTasksCreated).orElse(0L) < 2) {
            Thread.sleep(200);
        }

        ProjectAnalytics row = analyticsRepository.findById(projectId).orElseThrow();
        assertThat(row.getTasksCreated()).isEqualTo(2);
        assertThat(row.getCommentsCreated()).isZero();

        // The idempotency ledger contains one row per consumed event.
        assertThat(eventRepository.count()).isGreaterThanOrEqualTo(2);

        // The endpoint returns event-derived counters + live cards-per-column.
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/analytics")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.counters.tasksCreated").value(2))
                .andExpect(jsonPath("$.cardsPerColumn[0].count").value(2));

        // Non-members are rejected.
        String outsider = register("outsider-analytics@example.com");
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/analytics")
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden());
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
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long firstColumnId(long projectId, String token) throws Exception {
        MvcResult boards = mockMvc.perform(get("/api/v1/projects/" + projectId + "/boards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long boardId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(boards.getResponse().getContentAsString()).get(0).get("id").asLong();

        MvcResult board = mockMvc.perform(get("/api/v1/boards/" + boardId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(board.getResponse().getContentAsString())
                .get("columns").get(0).get("id").asLong();
    }
}