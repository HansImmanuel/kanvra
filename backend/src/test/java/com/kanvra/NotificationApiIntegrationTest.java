package com.kanvra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanvra.notification.model.Notification;
import com.kanvra.notification.repository.NotificationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification read API end-to-end (docs/SPEC.md §12): list, mark one read, mark
 * all read, scoped to the current user only. Rows are seeded directly through
 * the repository (the NotificationConsumer writes them in the Kafka flow test).
 */
@AutoConfigureMockMvc
class NotificationApiIntegrationTest extends AbstractIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void listMarkReadAndReadAll() throws Exception {
        String token = register("notif-list@example.com");
        long recipient = parseId(token);

        // Empty list is fine.
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        long unreadId = seed(recipient, "2nd");

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                // Sprint 4: denormalized project scope rides on every row.
                .andExpect(jsonPath("$.content[0].projectId").value(10));

        mockMvc.perform(post("/api/v1/notifications/" + unreadId + "/read")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty())
                .andExpect(jsonPath("$.id").value(unreadId));

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotMarkOthersNotificationsRead() throws Exception {
        String owner = register("notif-owner@example.com");
        String other = register("notif-other@example.com");

        long notifId = seed(parseId(owner), "private");

        mockMvc.perform(post("/api/v1/notifications/" + notifId + "/read")
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
    }

    private long seed(long recipient, String message) {
        Notification n = new Notification();
        n.setEventId(UUID.randomUUID());
        n.setRecipientId(recipient);
        n.setType("TASK_ASSIGNED");
        n.setReferenceId(1L);
        n.setReferenceType("TASK");
        n.setProjectId(10L);
        n.setMessage(message);
        return notificationRepository.save(n).getId();
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"User\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("access_token").getValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private long parseId(String token) throws Exception {
        return tree(mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn())
                .get("id").asLong();
    }

    private JsonNode tree(MvcResult result) throws Exception {
        return MAPPER.readTree(result.getResponse().getContentAsString());
    }
}