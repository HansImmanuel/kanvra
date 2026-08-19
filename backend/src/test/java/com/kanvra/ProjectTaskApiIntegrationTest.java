package com.kanvra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end core-product flow (docs/SPEC.md §4-§7): project -> board ->
 * idempotent task creation -> move -> optimistic-lock conflict, plus CSRF /
 * authorization behavior on the protected mutation endpoints.
 */
@AutoConfigureMockMvc
class ProjectTaskApiIntegrationTest extends AbstractIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullKanbanFlow() throws Exception {
        String token = register("jane@example.com");

        // --- Create project (owner + default board + TODO/IN PROGRESS/DONE) ---
        MvcResult projectResult = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 2\",\"description\":\"core slice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sprint 2"))
                .andReturn();
        long projectId = idOf(projectResult);

        // --- Board is provisioned with the three default columns ---
        long boardId = firstBoardId(projectId, token);
        long columnId = columnId(boardId, 0, token);
        long doneColumnId = columnId(boardId, 2, token);

        // --- Idempotent task creation ---
        MvcResult created = mockMvc.perform(post("/api/v1/columns/" + columnId + "/tasks")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "uuid-1111-2222")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Fix auth\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Fix auth"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.eventId").isString())
                .andReturn();
        long taskId = idOf(created);
        String eventId = textOf(created, "eventId");

        // Repeated key returns the original response, no duplicate task.
        MvcResult dedup = mockMvc.perform(post("/api/v1/columns/" + columnId + "/tasks")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "uuid-1111-2222")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Fix auth\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(taskId))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(textOf(dedup, "eventId")).isEqualTo(eventId);

        // --- Move to DONE completes the task ---
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/move")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetColumnId\":" + doneColumnId + ",\"position\":0,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columnId").value(doneColumnId));

        // --- Stale version is rejected with TASK_VERSION_CONFLICT ---
        mockMvc.perform(patch("/api/v1/tasks/" + taskId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Stale\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_VERSION_CONFLICT"));

        // --- Board nests the task in its column ---
        mockMvc.perform(get("/api/v1/boards/" + boardId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[2].tasks[0].title").value("Fix auth"));

        // --- Activity feed is visible to the member ---
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/activity")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void nonMemberCannotAccessProject() throws Exception {
        String owner = register("owner@example.com");
        String intruder = register("intruder@example.com");

        MvcResult projectResult = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Private\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = idOf(projectResult);

        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cookieMutationWithoutCsrfIsRejected() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CookieUser","email":"cookie@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie access = registerResult.getResponse().getCookie("access_token");

        // Browser-style session (access cookie present) but no X-CSRF-Token on a
        // state-changing endpoint -> 403 (CSRF hardening).
        mockMvc.perform(post("/api/v1/projects")
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NoCsrf\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addMemberThenAssignTaskToMember() throws Exception {
        String owner = register("bob@example.com");
        String second = register("second@example.com");
        long projectId = idOf(mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Team\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        long boardId = firstBoardId(projectId, owner);
        long memberUserId = idOf(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"second@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + memberUserId + ",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated());

        long columnId = columnId(boardId, 0, owner);
        mockMvc.perform(post("/api/v1/columns/" + columnId + "/tasks")
                        .header("Authorization", bearer(owner))
                        .header("Idempotency-Key", "uuid-team-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Assign me\",\"assigneeId\":" + memberUserId + "}"))
                .andExpect(status().isCreated());

        // The newly added member can read the project.
        mockMvc.perform(get("/api/v1/projects/" + projectId)
                        .header("Authorization", bearer(second)))
                .andExpect(status().isOk());
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

    private long idOf(MvcResult result) throws Exception {
        return tree(result).get("id").asLong();
    }

    private String textOf(MvcResult result, String field) throws Exception {
        return tree(result).get(field).asText();
    }

    private long firstBoardId(long projectId, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/" + projectId + "/boards")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return tree(result).get(0).get("id").asLong();
    }

    private long columnId(long boardId, int index, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/boards/" + boardId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return tree(result).get("columns").get(index).get("id").asLong();
    }

    private JsonNode tree(MvcResult result) throws Exception {
        return MAPPER.readTree(result.getResponse().getContentAsString());
    }
}

