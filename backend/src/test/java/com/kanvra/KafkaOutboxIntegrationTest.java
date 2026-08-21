package com.kanvra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.activity.model.Activity;
import com.kanvra.activity.repository.ActivityRepository;
import com.kanvra.kafka.consumer.ActivityConsumer;
import com.kanvra.outbox.publisher.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the full outbox -> Kafka -> Activity Consumer loop (docs/TECH_DOC.md
 * §8, §14): a project creation writes an outbox row, the OutboxPublisher
 * forwards it to {@code kanvra.domain-events}, and the Activity Consumer
 * persists a deduplicated activity record.
 */
@AutoConfigureMockMvc
class KafkaOutboxIntegrationTest extends AbstractIntegrationTest {

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
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityConsumer activityConsumer;

    @Test
    void outboxEventFlowsThroughKafkaToActivityFeed() throws Exception {
        String token = register("kafka@example.com");

        MvcResult projectResult = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kafka E2E\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(projectResult.getResponse().getContentAsString()).get("id").asLong();

        // Flush the outbox synchronously (the @Scheduled publisher may also fire).
        outboxPublisher.publishPending();

        // The consumer runs on its own thread; poll until the record lands.
        long deadline = System.currentTimeMillis() + 20_000;
        while (activityRepository.count() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }

        assertThat(activityRepository.count()).isGreaterThan(0);
        Activity activity = activityRepository.findAll().iterator().next();
        assertThat(activity.getProjectId()).isEqualTo(projectId);
        assertThat(activity.getType()).isEqualTo("PROJECT_CREATED");
        assertThat(activity.getMessage()).contains("created project 'Kafka E2E'");
    }

    @Test
    void duplicateDeliveryDoesNotCreateDuplicateActivity() throws Exception {
        String eventId = "22222222-2222-2222-2222-222222222222";
        String envelope = String.format("""
                {"eventId":"%s",
                 "eventType":"task.created",
                 "occurredAt":"2026-08-18T09:00:00Z",
                 "actorId":1,
                 "projectId":1,
                 "aggregateType":"task",
                 "aggregateId":5,
                 "payload":{"taskId":5,"taskTitle":"Dup","columnId":1,"columnName":"TODO","actorName":"Hans"}}
                """, eventId);

        activityConsumer.onDomainEvent(envelope);
        activityConsumer.onDomainEvent(envelope);

        long rowsForEvent = activityRepository.findAll().stream()
                .filter(a -> a.getEventId() != null && eventId.equals(a.getEventId().toString()))
                .count();
        assertThat(rowsForEvent).isEqualTo(1);
    }

    @Test
    void commentEventFlowsThroughKafkaToActivityFeed() throws Exception {
        String token = register("comment-kafka@example.com");

        MvcResult projectResult = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Comments E2E\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = new ObjectMapper().readTree(projectResult.getResponse().getContentAsString())
                .get("id").asLong();

        MvcResult boardResult = mockMvc.perform(get("/api/v1/projects/" + projectId + "/boards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long boardId = new ObjectMapper().readTree(boardResult.getResponse().getContentAsString())
                .get(0).get("id").asLong();

        MvcResult columnResult = mockMvc.perform(get("/api/v1/boards/" + boardId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long columnId = new ObjectMapper().readTree(columnResult.getResponse().getContentAsString())
                .get("columns").get(0).get("id").asLong();

        MvcResult taskResult = mockMvc.perform(post("/api/v1/columns/" + columnId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "uuid-comment-e2e")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Task with comments\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long taskId = new ObjectMapper().readTree(taskResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"review my change\"}"))
                .andExpect(status().isCreated());

        outboxPublisher.publishPending();

        // Wait for a COMMENT_CREATED activity row for this project.
        long deadline = System.currentTimeMillis() + 20_000;
        boolean seen = false;
        while (!seen && System.currentTimeMillis() < deadline) {
            seen = activityRepository.findAll().stream()
                    .anyMatch(a -> a.getProjectId() != null
                            && a.getProjectId().equals(projectId)
                            && "COMMENT_CREATED".equals(a.getType()));
            if (!seen) {
                Thread.sleep(200);
            }
        }

        assertThat(seen).as("comment.created event should reach the activity feed").isTrue();
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"User\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("access_token").getValue();
    }
}
