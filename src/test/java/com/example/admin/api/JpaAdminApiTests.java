package com.example.admin.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JpaAdminApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsUserAndAssignsRoleThroughJpaTrack() throws Exception {
        String roleResponse = mockMvc.perform(post("/api/jpa/roles")
                        .header("X-User-Id", "jpa-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"ADMIN","roleName":"Administrator","enabled":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleCode", is("ADMIN")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long roleId = objectMapper.readTree(roleResponse).get("id").asLong();

        String userResponse = mockMvc.perform(post("/api/jpa/users")
                        .header("X-User-Id", "jpa-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"mint.admin","name":"Mint Admin","enabled":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy", is("jpa-api")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long userId = objectMapper.readTree(userResponse).get("id").asLong();

        mockMvc.perform(put("/api/jpa/users/{userId}/roles", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[" + roleId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].roleCode", is("ADMIN")));

        mockMvc.perform(get("/api/jpa/users").param("loginKeyword", "MINT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].loginId", is("mint.admin")));
    }

    @Test
    void returnsCommonValidationError() throws Exception {
        mockMvc.perform(post("/api/jpa/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"","name":"","enabled":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }
}
