package com.kanvra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comment API end-to-end (docs/SPEC.md §8): create/list/edit/delete on a task,
 * author-only rules, project membership, and content validation.
 */
@AutoConfigureMockMvc
class CommentApiIntegrationTest extends AbstractIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void commentCrudLifecycle() throws Exception {
        String owner = register("comment-owner@example.com");
        long projectId = createProject(owner, "Comments");
        long boardId = firstBoardId(projectId, owner);
        long columnId = firstColumnId(boardId, owner);
        long taskId = createTask(columnId, "Fix docs", "uuid-comment-1", owner);

        MvcResult created = mockMvc.perform(post("/api/v1/tasks/" + taskId + "/comments")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Please verify the refresh-token flow.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Please verify the refresh-token flow."))
                .andExpect(jsonPath("$.author.name").value("User"))
                .andReturn();
        long commentId = idOf(created);

        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/comments")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(commentId));

        mockMvc.perform(patch("/api/v1/comments/" + commentId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"edited content\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("edited content"));

        mockMvc.perform(delete("/api/v1/comments/" + commentId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());

        // Soft delete: list no longer returns the comment.
        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/comments")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void blankContentIsRejected() throws Exception {
        String owner = register("comment-blank@example.com");
        long projectId = createProject(owner, "Blank");
        long boardId = firstBoardId(projectId, owner);
        long columnId = firstColumnId(boardId, owner);
        long taskId = createTask(columnId, "Task", "uuid-comment-2", owner);

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/comments")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().is(422));
    }

    @Test
    void nonMemberCannotComment() throws Exception {
        String owner = register("owner-a@example.com");
        String outsider = register("outsider@example.com");
        long projectId = createProject(owner, "Private");
        long boardId = firstBoardId(projectId, owner);
        long columnId = firstColumnId(boardId, owner);
        long taskId = createTask(columnId, "Task", "uuid-comment-3", owner);

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/comments")
                        .header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"intruder\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAuthorCanEdit() throws Exception {
        String owner = register("owner-b@example.com");
        long projectId = createProject(owner, "Edits");
        long boardId = firstBoardId(projectId, owner);
        long columnId = firstColumnId(boardId, owner);
        long taskId = createTask(columnId, "Task", "uuid-comment-4", owner);

        long commentId = idOf(mockMvc.perform(post("/api/v1/tasks/" + taskId + "/comments")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"original\"}"))
                .andExpect(status().isCreated())
                .andReturn());

        String member = register("author-check@example.com");
        long memberUserId = parseId(member);
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/members")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + memberUserId + ",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/comments/" + commentId)
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"someone else's edit\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private long createProject(String token, String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long createTask(long columnId, String title, String idempotencyKey, String token) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/columns/" + columnId + "/tasks")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long firstBoardId(long projectId, String token) throws Exception {
        JsonNode boards = tree(mockMvc.perform(get("/api/v1/projects/" + projectId + "/boards")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
        return boards.get(0).get("id").asLong();
    }

    private long firstColumnId(long boardId, String token) throws Exception {
        JsonNode board = tree(mockMvc.perform(get("/api/v1/boards/" + boardId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
        return board.get("columns").get(0).get("id").asLong();
    }

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

    /** Resolves the current user id of an authenticated token via GET /api/v1/me. */
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