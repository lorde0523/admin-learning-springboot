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
class MyBatisAdminApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void searchesJoinedRoleMenusAndBulkInsertsMenusThroughMyBatisTrack() throws Exception {
        String roleResponse = mockMvc.perform(post("/api/mybatis/roles")
                        .header("X-User-Id", "mybatis-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"OPS","roleName":"Operations","enabled":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long roleId = objectMapper.readTree(roleResponse).get("id").asLong();

        mockMvc.perform(post("/api/mybatis/menus/bulk")
                        .header("X-User-Id", "mybatis-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"menus":[
                                  {"menuCode":"USER_LIST","menuName":"User List","parentMenuId":null,"sortOrder":10,"enabled":true},
                                  {"menuCode":"MENU_LIST","menuName":"Menu List","parentMenuId":null,"sortOrder":20,"enabled":true}
                                ]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.insertedCount", is(2)));

        String menusResponse = mockMvc.perform(get("/api/mybatis/menus").param("nameKeyword", "LIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long firstMenuId = objectMapper.readTree(menusResponse).get(0).get("id").asLong();

        mockMvc.perform(put("/api/mybatis/roles/{roleId}/menus", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuIds\":[" + firstMenuId + "]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/mybatis/roles/{roleId}/menus", roleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].menuCode", is("USER_LIST")));
    }
}
