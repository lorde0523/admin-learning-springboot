package com.example.admin.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.admin.common.exception.ResourceNotFoundException;
import com.example.admin.common.grid.GridSaveExecutor;
import com.example.admin.common.grid.GridSaveFailureMode;
import com.example.admin.common.sqltrace.SqlLogEntry;
import com.example.admin.common.sqltrace.SqlLogSearchClient;
import com.example.admin.menu.dto.adminmenu.MenuGridRow;
import com.example.admin.menu.entity.AdminMenu;
import com.example.admin.menu.mapper.MenuMapper;
import com.example.admin.menu.repository.AdminMenuRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JpaAdminApiTests {

    private static final AtomicLong MENU_ID_SEQUENCE = new AtomicLong(100_000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GridSaveExecutor gridSaveExecutor;

    @Autowired
    private AdminMenuRepository menuRepository;

    @Autowired
    private MenuMapper menuMapper;

    @MockitoBean
    private SqlLogSearchClient sqlLogSearchClient;

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
    void assignsMenusToRoleThroughJpaTrack() throws Exception {
        String roleResponse = mockMvc.perform(post("/api/jpa/roles")
                        .header("X-User-Id", "jpa-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"ROLE_MENU","roleName":"Role Menu","enabled":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long roleId = objectMapper.readTree(roleResponse).get("id").asLong();

        long menuId = createMenuThroughGridSave("ROLE_MENU_LIST", "Role menu list", 1);

        mockMvc.perform(put("/api/jpa/roles/{roleId}/menus", roleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuIds\":[" + menuId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId", is((int) roleId)))
                .andExpect(jsonPath("$.menus", hasSize(1)))
                .andExpect(jsonPath("$.menus[0].menuCode", is("ROLE_MENU_LIST")));

        mockMvc.perform(get("/api/jpa/roles/{roleId}/menus", roleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].menuCode", is("ROLE_MENU_LIST")));
    }

    @Test
    void returnsCommonValidationError() throws Exception {
        mockMvc.perform(post("/api/jpa/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"","name":"","enabled":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", is("요청 값 검증에 실패했습니다.")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.occurredAt").doesNotExist());
    }

    @Test
    void savesMenuGridChangesThroughJpa() throws Exception {
        Long existingId = createMenuThroughGridSave("GRID_OLD", "Old menu", 1);
        Long deletedId = createMenuThroughGridSave("GRID_DELETE", "Delete menu", 2);

        mockMvc.perform(post("/api/jpa/menus/grid-save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdRows": [
                                    {
                                      "id": %d,
                                      "menuCode": "GRID_NEW",
                                      "menuName": "New menu",
                                      "parentMenuId": null,
                                      "sortOrder": 3,
                                      "enabled": true
                                    }
                                  ],
                                  "updatedRows": [
                                    {
                                      "id": %d,
                                      "menuCode": "GRID_OLD",
                                      "menuName": "Updated menu",
                                      "parentMenuId": null,
                                      "sortOrder": 4,
                                      "enabled": false
                                    }
                                  ],
                                  "deletedIds": [%d]
                                }
                                """.formatted(nextMenuId(), existingId, deletedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.updatedCount").value(1))
                .andExpect(jsonPath("$.deletedCount").value(1));

        mockMvc.perform(get("/api/jpa/menus/{id}", existingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menuName").value("Updated menu"))
                .andExpect(jsonPath("$.sortOrder").value(4))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/jpa/menus/{id}", deletedId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/jpa/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.menuCode == 'GRID_NEW')]", hasSize(1)));
    }

    @Test
    void returnsPagedMenusThroughJpaAndMyBatis() throws Exception {
        createMenuThroughGridSave("PAGE_ALPHA", "Baseline alpha", 11);
        createMenuThroughGridSave("PAGE_BRAVO", "Baseline bravo", 12);
        createMenuThroughGridSave("PAGE_CHARLIE", "Baseline charlie", 13);

        mockMvc.perform(get("/api/jpa/menus/page")
                        .param("nameKeyword", "Baseline")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].menuCode", is("PAGE_ALPHA")))
                .andExpect(jsonPath("$.content[1].menuCode", is("PAGE_BRAVO")))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2));

        mockMvc.perform(get("/api/mybatis/menus/page")
                        .param("nameKeyword", "Baseline")
                        .param("pageId", "MENU_GRID")
                        .param("sqlCapturePaused", "false")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].menuCode", is("PAGE_CHARLIE")))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void returnsPagedMenusWithoutNewSqlLogsWhenSqlCaptureIsPaused() throws Exception {
        createMenuThroughGridSave("PAGE_PAUSED", "Paused paging", 21);

        mockMvc.perform(get("/api/mybatis/menus/page")
                        .param("nameKeyword", "Paused")
                        .param("pageId", "MENU_GRID")
                        .param("sqlCapturePaused", "true")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageId", is("MENU_GRID")))
                .andExpect(jsonPath("$.sqlCapturePaused", is(true)))
                .andExpect(jsonPath("$.sqlBatchId").isNotEmpty())
                .andExpect(jsonPath("$.serverElapsedMillis").isNumber())
                .andExpect(jsonPath("$.newSqlLogs").isEmpty())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].menuCode", is("PAGE_PAUSED")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));

        verify(sqlLogSearchClient, never()).search(any());
    }

    @Test
    void rejectsMyBatisPagedMenuSearchWithoutPageId() throws Exception {
        mockMvc.perform(get("/api/mybatis/menus/page")
                        .param("sqlCapturePaused", "false")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    void returnsNewSqlLogsWhenSqlCaptureIsActive() throws Exception {
        createMenuThroughGridSave("PAGE_SQL_ACTIVE", "Active SQL paging", 22);
        when(sqlLogSearchClient.search(any())).thenReturn(List.of(SqlLogEntry.builder()
                .queryId("com.example.admin.menu.store.MyBatisAdminMenuStore.searchPage")
                .sqlText("SELECT MENU_ID FROM ADMIN_MENU WHERE MENU_NAME LIKE '%Active%'")
                .executedAt(LocalDateTime.of(2026, 6, 23, 10, 0))
                .elapsedMillis(12)
                .build()));

        mockMvc.perform(get("/api/mybatis/menus/page")
                        .param("nameKeyword", "Active")
                        .param("pageId", "MENU_GRID")
                        .param("sqlCapturePaused", "false")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageId", is("MENU_GRID")))
                .andExpect(jsonPath("$.sqlCapturePaused", is(false)))
                .andExpect(jsonPath("$.sqlBatchId").isNotEmpty())
                .andExpect(jsonPath("$.newSqlLogs", hasSize(1)))
                .andExpect(jsonPath("$.newSqlLogs[0].queryId",
                        is("com.example.admin.menu.store.MyBatisAdminMenuStore.searchPage")))
                .andExpect(jsonPath("$.newSqlLogs[0].sqlText",
                        is("SELECT MENU_ID FROM ADMIN_MENU WHERE MENU_NAME LIKE '%Active%'")))
                .andExpect(jsonPath("$.newSqlLogs[0].elapsedMillis").value(12))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].menuCode", is("PAGE_SQL_ACTIVE")));

        verify(sqlLogSearchClient).search(any());
    }

    @Test
    void returnsPagedMenusWhenSqlLogSearchFails() throws Exception {
        createMenuThroughGridSave("PAGE_SQL_FAIL", "Failed SQL paging", 23);
        when(sqlLogSearchClient.search(any())).thenThrow(new IllegalStateException("elastic timeout"));

        mockMvc.perform(get("/api/mybatis/menus/page")
                        .param("nameKeyword", "Failed")
                        .param("pageId", "MENU_GRID")
                        .param("sqlCapturePaused", "false")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageId", is("MENU_GRID")))
                .andExpect(jsonPath("$.newSqlLogs").isEmpty())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].menuCode", is("PAGE_SQL_FAIL")));
    }

    @Test
    void skipsNullMenuGridChangeGroupsThroughJpa() throws Exception {
        mockMvc.perform(post("/api/jpa/menus/grid-save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdRows": null,
                                  "updatedRows": null,
                                  "deletedIds": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.deletedCount").value(0))
                .andExpect(jsonPath("$.messages").isEmpty());
    }

    @Test
    void rejectsMissingDeletedMenuIdsThroughJpaGridSave() throws Exception {
        mockMvc.perform(post("/api/jpa/menus/grid-save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdRows": [],
                                  "updatedRows": [],
                                  "deletedIds": [999999]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.deletedCount").value(0))
                .andExpect(jsonPath("$.messages[0].operation", is("DELETE")))
                .andExpect(jsonPath("$.messages[0].result", is("SKIPPED")))
                .andExpect(jsonPath("$.messages[0].key", is("999999")))
                .andExpect(jsonPath("$.messages[0].message", is("삭제 대상 데이터가 없습니다.")));
    }

    @Test
    void rejectsMissingUpdatedMenuIdsThroughJpaGridSave() throws Exception {
        mockMvc.perform(post("/api/jpa/menus/grid-save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdRows": [],
                                  "updatedRows": [
                                    {
                                      "id": 999999,
                                      "menuCode": "GRID_MISSING",
                                      "menuName": "Missing menu",
                                      "parentMenuId": null,
                                      "sortOrder": 1,
                                      "enabled": true
                                    }
                                  ],
                                  "deletedIds": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.deletedCount").value(0))
                .andExpect(jsonPath("$.messages[0].operation", is("UPDATE")))
                .andExpect(jsonPath("$.messages[0].result", is("SKIPPED")))
                .andExpect(jsonPath("$.messages[0].key", is("999999")))
                .andExpect(jsonPath("$.messages[0].message", is("수정 대상 데이터가 없습니다.")));
    }

    @Test
    void rejectsDuplicateUpdatedMenuIdsThroughJpaGridSave() throws Exception {
        Long existingId = createMenuThroughGridSave("GRID_DUP", "Duplicate menu", 1);

        mockMvc.perform(post("/api/jpa/menus/grid-save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdRows": [],
                                  "updatedRows": [
                                    {
                                      "id": %d,
                                      "menuCode": "GRID_DUP",
                                      "menuName": "First duplicate",
                                      "parentMenuId": null,
                                      "sortOrder": 2,
                                      "enabled": true
                                    },
                                    {
                                      "id": %d,
                                      "menuCode": "GRID_DUP",
                                      "menuName": "Second duplicate",
                                      "parentMenuId": null,
                                      "sortOrder": 3,
                                      "enabled": false
                                    }
                                  ],
                                  "deletedIds": []
                                }
                                """.formatted(existingId, existingId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message", is("updatedRows key가 중복될 수 없습니다.")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void rejectsNullUpdatedMenuIdsThroughJpaGridSave() throws Exception {
        mockMvc.perform(post("/api/jpa/menus/grid-save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdRows": [],
                                  "updatedRows": [
                                    {
                                      "id": null,
                                      "menuCode": "GRID_NULL",
                                      "menuName": "Null id",
                                      "parentMenuId": null,
                                      "sortOrder": 1,
                                      "enabled": true
                                    }
                                  ],
                                  "deletedIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", is("요청 값 검증에 실패했습니다.")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void rejectsDeleteAndUpdateMenuIdConflictsThroughJpaGridSave() throws Exception {
        Long existingId = createMenuThroughGridSave("GRID_CONFLICT", "Conflict menu", 1);

        mockMvc.perform(post("/api/jpa/menus/grid-save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdRows": [],
                                  "updatedRows": [
                                    {
                                      "id": %d,
                                      "menuCode": "GRID_CONFLICT",
                                      "menuName": "Conflicting update",
                                      "parentMenuId": null,
                                      "sortOrder": 2,
                                      "enabled": false
                                    }
                                  ],
                                  "deletedIds": [%d]
                                }
                                """.formatted(existingId, existingId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message", is("deletedIds와 updatedRows key가 서로 겹칠 수 없습니다.")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void skipsAlreadyRegisteredMenuIdsThroughJpaGridSave() throws Exception {
        Long existingId = createMenuThroughGridSave("GRID_ALREADY_EXISTS", "Already exists menu", 1);

        mockMvc.perform(post("/api/jpa/menus/grid-save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdRows": [
                                    {
                                      "id": %d,
                                      "menuCode": "GRID_ALREADY_EXISTS_AGAIN",
                                      "menuName": "Already exists again",
                                      "parentMenuId": null,
                                      "sortOrder": 2,
                                      "enabled": true
                                    }
                                  ],
                                  "updatedRows": [],
                                  "deletedIds": []
                                }
                                """.formatted(existingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.deletedCount").value(0))
                .andExpect(jsonPath("$.messages[0].operation", is("CREATE")))
                .andExpect(jsonPath("$.messages[0].result", is("SKIPPED")))
                .andExpect(jsonPath("$.messages[0].key", is(String.valueOf(existingId))))
                .andExpect(jsonPath("$.messages[0].message", is("이미 등록된 데이터입니다.")));
    }

    @Test
    void throwsExceptionForMissingUpdatedMenuIdsWhenGridSaveModeIsStrict() {
        MenuGridRow missingUpdateRow = menuGridRow(999999L, "GRID_STRICT", "Strict missing menu", 1, true);

        assertThatThrownBy(() -> gridSaveExecutor.update(
                        List.of(missingUpdateRow),
                        menuRepository,
                        MenuGridRow::getId,
                        AdminMenu::getId,
                        menuMapper::updateEntity,
                        "존재하지 않는 메뉴가 포함되어 있습니다.",
                        GridSaveFailureMode.STRICT_EXCEPTION))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("존재하지 않는 메뉴가 포함되어 있습니다.");
    }

    @Test
    void deletesMenuRowsWithDtoKeyReaderThroughGridSaveExecutor() throws Exception {
        Long deleteId = createMenuThroughGridSave("GRID_DELETE_DTO", "Delete DTO menu", 1);
        MenuGridRow deleteRow = menuGridRow(deleteId, "GRID_DELETE_DTO", "Delete DTO menu", 1, true);

        var result = gridSaveExecutor.delete(
                List.of(deleteRow),
                menuRepository,
                MenuGridRow::getId,
                AdminMenu::getId,
                "존재하지 않는 메뉴가 포함되어 있습니다.",
                GridSaveFailureMode.SKIP_AND_MESSAGE);

        assertThat(result.getCreatedCount()).isZero();
        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getDeletedCount()).isOne();
        assertThat(result.getMessages()).isEmpty();

        mockMvc.perform(get("/api/jpa/menus/{id}", deleteId))
                .andExpect(status().isNotFound());
    }

    private Long createMenuThroughGridSave(String menuCode, String menuName, int sortOrder) throws Exception {
        mockMvc.perform(post("/api/jpa/menus/grid-save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdRows": [
                                    {
                                      "id": %d,
                                      "menuCode": "%s",
                                      "menuName": "%s",
                                      "parentMenuId": null,
                                      "sortOrder": %d,
                                      "enabled": true
                                    }
                                  ],
                                  "updatedRows": null,
                                  "deletedIds": null
                                }
                                """.formatted(nextMenuId(), menuCode, menuName, sortOrder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.deletedCount").value(0));

        String menuResponse = mockMvc.perform(get("/api/jpa/menus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var menus = objectMapper.readTree(menuResponse);
        for (var menu : menus) {
            if (menuCode.equals(menu.get("menuCode").asText())) {
                return menu.get("id").asLong();
            }
        }
        throw new AssertionError("생성된 메뉴를 찾을 수 없습니다. menuCode=" + menuCode);
    }

    private Long nextMenuId() {
        return MENU_ID_SEQUENCE.incrementAndGet();
    }

    private MenuGridRow menuGridRow(Long id, String menuCode, String menuName, int sortOrder, boolean enabled) {
        MenuGridRow row = new MenuGridRow();
        row.setId(id);
        row.setMenuCode(menuCode);
        row.setMenuName(menuName);
        row.setSortOrder(sortOrder);
        row.setEnabled(enabled);
        return row;
    }
}
