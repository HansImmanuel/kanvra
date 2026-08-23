package com.kanvra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Label list endpoint (docs/SPEC.md §9, Sprint 4): membership-scoped, sorted by
 * name, and feeding the task-detail pickers.
 */
@AutoConfigureMockMvc
class LabelApiIntegrationTest extends AbstractIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void labelListIsMembershipScopedSortedByName() throws Exception {
        String owner = register("label-owner@example.com");
        long projectId = createProject(owner);

        createLabel(owner, projectId, "zzz-last", "#111111");
        createLabel(owner, projectId, "aaa-first", "#222222");

        // Sorted by name; both labels present.
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/labels")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("aaa-first"))
                .andExpect(jsonPath("$[1].name").value("zzz-last"))
                .andExpect(jsonPath("$.length()").value(2));

        // A non-member must not see the project's labels.
        String outsider = register("label-outsider@example.com");
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/labels")
                        .header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
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

    private long createProject(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Label project\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode tree = MAPPER.readTree(result.getResponse().getContentAsString());
        return tree.get("id").asLong();
    }

    private void createLabel(String token, long projectId, String name, String color) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/labels")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"color\":\"" + color + "\"}"))
                .andExpect(status().isCreated());
    }
}