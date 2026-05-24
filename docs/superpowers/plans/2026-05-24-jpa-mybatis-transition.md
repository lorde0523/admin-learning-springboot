# JPA MyBatis 전환 구현 플랜

> **에이전트 작업자 필수 지침:** 이 플랜을 실행할 때는 `superpowers:subagent-driven-development`를 권장 방식으로 사용한다. 대안으로 `superpowers:executing-plans`를 사용할 수 있다. 각 단계는 진행 상태를 추적할 수 있도록 체크박스(`- [ ]`) 문법을 사용한다.

**목표:** 현재 JPA/MyBatis 병렬 학습 구조를 정리해서, 등록/수정/삭제는 JPA로 처리하고, 단순 조회도 JPA로 처리하며, 복잡한 조회만 MyBatis 3.x로 남기는 구조로 전환한다.

**아키텍처:** Controller는 Application Service를 호출하고, 트랜잭션은 Service 계층에서 관리한다. JPA Repository와 Entity는 모든 쓰기 작업과 단순 조회를 담당한다. MyBatis Mapper는 SQL로 표현하는 것이 더 명확한 복잡 조회 전용 어댑터로만 남긴다.

**기술 스택:** Java 21, Spring Boot, Spring MVC, Spring Data JPA, Hibernate, MyBatis 3.x, Lombok, H2 Oracle mode 테스트, Oracle SQL 호환성.

---

## 결정 사항

- Entity와 DTO는 Java `record`를 사용하지 않고 Lombok class로 작성한다.
- JPA Entity는 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 정적 팩토리 메서드, 의미 있는 상태 변경 메서드를 기본으로 사용한다.
- Request DTO는 화면/JSON 바인딩을 위해 `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`를 사용할 수 있다.
- Response DTO는 `@Getter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`를 우선 사용한다.
- MyBatis Mapper 내부 projection 객체도 가능하면 `record` 대신 Lombok static class를 사용한다.
- 등록, 수정, 삭제, 관계 매핑 저장, ag-Grid 저장 command는 모두 JPA로 처리한다.
- MyBatis XML은 최종 구조에서 조회 SQL만 포함한다.
- QueryDSL은 사용하지 않는다.

## 대상 파일 구조

- 수정: `src/main/java/com/example/admin/user/dto/UserDtos.java`
  - nested record를 Lombok static class로 변환한다.
- 수정: `src/main/java/com/example/admin/role/dto/RoleDtos.java`
  - nested record를 Lombok static class로 변환한다.
- 수정: `src/main/java/com/example/admin/menu/dto/MenuDtos.java`
  - nested record를 Lombok static class로 변환하고 ag-Grid 저장 DTO를 추가한다.
- 수정: `src/main/java/com/example/admin/common/response/ErrorResponse.java`
  - record를 Lombok class로 변환한다.
- 수정: `src/main/java/com/example/admin/user/repository/AdminUserRepository.java`
  - 단순 native query를 JPQL 또는 derived query로 대체한다.
  - 동적 조건이 필요해질 경우 Specification을 사용할 수 있도록 확장한다.
- 수정: `src/main/java/com/example/admin/menu/repository/AdminMenuRepository.java`
  - 단순 JPQL 검색을 유지하고 필요한 경우 grid 저장 보조 쿼리를 추가한다.
- 수정: `src/main/java/com/example/admin/role/repository/AdminRoleRepository.java`
  - `@EntityGraph` 기반 관계 조회를 유지한다.
- 수정: `src/main/java/com/example/admin/user/service/JpaAdminUserService.java`
  - DTO 변환 후 getter 접근 방식으로 수정하고 쓰기 작업은 JPA로 유지한다.
- 수정: `src/main/java/com/example/admin/role/service/JpaAdminRoleService.java`
  - DTO 변환 후 getter 접근 방식으로 수정하고 쓰기 작업은 JPA로 유지한다.
- 수정: `src/main/java/com/example/admin/menu/service/JpaAdminMenuService.java`
  - DTO 변환 후 getter 접근 방식으로 수정하고 ag-Grid bulk save를 추가한다.
- 수정: `src/main/java/com/example/admin/user/mapper/AdminUserMapper.java`
  - insert/update/delete/관계 변경 메서드를 제거하고 조회 전용 메서드만 남긴다.
- 수정: `src/main/java/com/example/admin/role/mapper/AdminRoleMapper.java`
  - insert/update/delete/관계 변경 메서드를 제거하고 조회 전용 메서드만 남긴다.
- 수정: `src/main/java/com/example/admin/menu/mapper/AdminMenuMapper.java`
  - insert/update/delete/bulk insert 메서드를 제거하고 조회 전용 메서드만 남긴다.
- 수정: `src/main/resources/mybatis/AdminUserMapper.xml`
  - insert/update/delete SQL을 제거한다.
- 수정: `src/main/resources/mybatis/AdminRoleMapper.xml`
  - insert/update/delete SQL을 제거한다.
- 수정: `src/main/resources/mybatis/AdminMenuMapper.xml`
  - insert/update/delete/bulk insert SQL을 제거한다.
- 삭제 또는 폐기: `src/main/java/com/example/admin/user/service/MyBatisAdminUserService.java`
- 삭제 또는 폐기: `src/main/java/com/example/admin/role/service/MyBatisAdminRoleService.java`
- 삭제 또는 폐기: `src/main/java/com/example/admin/menu/service/MyBatisAdminMenuService.java`
- 삭제 또는 폐기: `src/main/java/com/example/admin/user/api/MyBatisAdminUserController.java`
- 삭제 또는 폐기: `src/main/java/com/example/admin/role/api/MyBatisAdminRoleController.java`
- 삭제 또는 폐기: `src/main/java/com/example/admin/menu/api/MyBatisAdminMenuController.java`
- 수정: `src/test/java/com/example/admin/api/JpaAdminApiTests.java`
  - JPA CRUD와 관계 저장 흐름을 검증한다.
- 수정: `src/test/java/com/example/admin/api/MyBatisAdminApiTests.java`
  - MyBatis 조회 전용 테스트로 바꾸거나, 조회 전용 API가 아직 없으면 삭제한다.
- 수정: `README.md`
  - record DTO 안내를 Lombok DTO 안내로 교체한다.
  - JPA/MyBatis 병렬 학습 안내를 목표 아키텍처 안내로 교체한다.

---

### 작업 1: 공통 응답 DTO를 Lombok class로 변환

**파일:**
- 수정: `src/main/java/com/example/admin/common/response/ErrorResponse.java`
- 테스트: `src/test/java/com/example/admin/api/JpaAdminApiTests.java`

- [ ] **1단계: 현재 record 사용 위치 확인**

실행:

```powershell
rg -n "ErrorResponse|\\.code\\(\\)|\\.message\\(\\)" src\main\java src\test\java
```

예상 결과: `ErrorResponse`를 생성하거나 record accessor를 사용하는 위치가 모두 확인된다.

- [ ] **2단계: `ErrorResponse`를 Lombok class로 교체**

다음 형태로 작성한다.

```java
package com.example.admin.common.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private String code;
    private String message;
    private LocalDateTime timestamp;

    public static ErrorResponse of(String code, String message) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
```

- [ ] **3단계: API 오류 처리 테스트 실행**

실행:

```powershell
.\gradlew test --tests "*JpaAdminApiTests"
```

예상 결과: 컴파일과 테스트가 통과한다. 테스트에서 record accessor를 사용하고 있다면 getter 접근으로 바꾼다.

- [ ] **4단계: 커밋**

실행:

```powershell
git add src/main/java/com/example/admin/common/response/ErrorResponse.java src/test/java/com/example/admin/api/JpaAdminApiTests.java
git commit -m "refactor: convert error response dto to lombok class"
```

---

### 작업 2: 메뉴 DTO를 Lombok class로 변환하고 ag-Grid 저장 API 추가

**파일:**
- 수정: `src/main/java/com/example/admin/menu/dto/MenuDtos.java`
- 수정: `src/main/java/com/example/admin/menu/service/JpaAdminMenuService.java`
- 수정: `src/main/java/com/example/admin/menu/api/JpaAdminMenuController.java`
- 테스트: `src/test/java/com/example/admin/api/JpaAdminApiTests.java`

- [ ] **1단계: 실패하는 grid 저장 API 테스트 작성**

JPA 메뉴 API에 등록 row 1건, 수정 row 1건, 삭제 id 1건을 한 번에 보내는 테스트를 추가한다.

```java
@Test
void savesMenuGridChangesThroughJpa() throws Exception {
    String existing = mockMvc.perform(post("/api/jpa/menus")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "menuCode": "GRID_OLD",
                              "menuName": "Old menu",
                              "parentMenuId": null,
                              "sortOrder": 1,
                              "enabled": true
                            }
                            """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long existingId = objectMapper.readTree(existing).get("id").asLong();

    String deleted = mockMvc.perform(post("/api/jpa/menus")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "menuCode": "GRID_DELETE",
                              "menuName": "Delete menu",
                              "parentMenuId": null,
                              "sortOrder": 2,
                              "enabled": true
                            }
                            """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long deletedId = objectMapper.readTree(deleted).get("id").asLong();

    mockMvc.perform(post("/api/jpa/menus/grid-save")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "createdRows": [
                                {
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
                            """.formatted(existingId, deletedId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdCount").value(1))
            .andExpect(jsonPath("$.updatedCount").value(1))
            .andExpect(jsonPath("$.deletedCount").value(1));
}
```

- [ ] **2단계: 실패 테스트 실행**

실행:

```powershell
.\gradlew test --tests "*JpaAdminApiTests.savesMenuGridChangesThroughJpa"
```

예상 결과: `/api/jpa/menus/grid-save`가 없어서 실패한다.

- [ ] **3단계: `MenuDtos`를 Lombok class로 변환**

nested record를 static class로 바꾼다. 응답 매핑은 getter와 builder를 사용하고, 요청 DTO는 setter 기반 바인딩이 가능하게 둔다.

```java
package com.example.admin.menu.dto;

import com.example.admin.menu.entity.AdminMenu;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class MenuDtos {

    private MenuDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuRequest {
        @NotBlank
        private String menuCode;

        @NotBlank
        private String menuName;

        private Long parentMenuId;

        @PositiveOrZero
        private int sortOrder;

        @NotNull
        private Boolean enabled;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuGridRow extends MenuRequest {
        private Long id;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuResponse {
        private Long id;
        private String menuCode;
        private String menuName;
        private Long parentMenuId;
        private int sortOrder;
        private boolean enabled;
        private String createdBy;
        private LocalDateTime createdAt;

        public static MenuResponse from(AdminMenu menu) {
            return MenuResponse.builder()
                    .id(menu.getId())
                    .menuCode(menu.getMenuCode())
                    .menuName(menu.getMenuName())
                    .parentMenuId(menu.getParentMenuId())
                    .sortOrder(menu.getSortOrder())
                    .enabled(menu.isEnabled())
                    .createdBy(menu.getCreatedBy())
                    .createdAt(menu.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuBulkRequest {
        @NotEmpty
        private List<@Valid MenuRequest> menus = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuGridSaveRequest {
        private List<@Valid MenuRequest> createdRows = new ArrayList<>();
        private List<@Valid MenuGridRow> updatedRows = new ArrayList<>();
        private List<Long> deletedIds = new ArrayList<>();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GridSaveResponse {
        private int createdCount;
        private int updatedCount;
        private int deletedCount;
    }
}
```

- [ ] **4단계: 서비스 accessor 수정**

`request.menuCode()` 같은 record accessor를 `request.getMenuCode()` 형태로 바꾼다.

```java
private AdminMenu entity(MenuDtos.MenuRequest request) {
    return AdminMenu.create(
            request.getMenuCode(),
            request.getMenuName(),
            request.getParentMenuId(),
            request.getSortOrder(),
            request.getEnabled());
}
```

- [ ] **5단계: JPA grid save 서비스 메서드 추가**

`JpaAdminMenuService`에 다음 메서드를 추가한다.

```java
@Transactional
public MenuDtos.GridSaveResponse saveGrid(MenuDtos.MenuGridSaveRequest request) {
    List<Long> deletedIds = request.getDeletedIds() == null ? List.of() : request.getDeletedIds();
    List<MenuDtos.MenuRequest> createdRows =
            request.getCreatedRows() == null ? List.of() : request.getCreatedRows();
    List<MenuDtos.MenuGridRow> updatedRows =
            request.getUpdatedRows() == null ? List.of() : request.getUpdatedRows();

    if (!deletedIds.isEmpty()) {
        menuRepository.deleteAllByIdInBatch(deletedIds);
    }

    List<AdminMenu> createdMenus = createdRows.stream()
            .map(this::entity)
            .toList();
    menuRepository.saveAll(createdMenus);

    Map<Long, MenuDtos.MenuGridRow> updateRowsById = updatedRows.stream()
            .collect(Collectors.toMap(MenuDtos.MenuGridRow::getId, Function.identity()));

    List<AdminMenu> updateTargets = updateRowsById.isEmpty()
            ? List.of()
            : menuRepository.findAllById(updateRowsById.keySet());

    if (updateTargets.size() != updateRowsById.size()) {
        throw new ResourceNotFoundException("One or more menus do not exist.");
    }

    for (AdminMenu menu : updateTargets) {
        MenuDtos.MenuGridRow row = updateRowsById.get(menu.getId());
        menu.update(row.getMenuName(), row.getParentMenuId(), row.getSortOrder(), row.getEnabled());
    }

    return MenuDtos.GridSaveResponse.builder()
            .createdCount(createdMenus.size())
            .updatedCount(updateTargets.size())
            .deletedCount(deletedIds.size())
            .build();
}
```

필요한 import:

```java
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
```

- [ ] **6단계: Controller endpoint 추가**

`JpaAdminMenuController`에 다음 메서드를 추가한다.

```java
@PostMapping("/grid-save")
public MenuDtos.GridSaveResponse saveGrid(@Valid @RequestBody MenuDtos.MenuGridSaveRequest request) {
    return menuService.saveGrid(request);
}
```

- [ ] **7단계: grid save 테스트 실행**

실행:

```powershell
.\gradlew test --tests "*JpaAdminApiTests.savesMenuGridChangesThroughJpa"
```

예상 결과: PASS.

- [ ] **8단계: 커밋**

실행:

```powershell
git add src/main/java/com/example/admin/menu/dto/MenuDtos.java src/main/java/com/example/admin/menu/service/JpaAdminMenuService.java src/main/java/com/example/admin/menu/api/JpaAdminMenuController.java src/test/java/com/example/admin/api/JpaAdminApiTests.java
git commit -m "feat: save menu grid changes with jpa"
```

---

### 작업 3: 사용자 DTO를 Lombok class로 변환하고 사용자 쓰기 작업을 JPA로 유지

**파일:**
- 수정: `src/main/java/com/example/admin/user/dto/UserDtos.java`
- 수정: `src/main/java/com/example/admin/user/service/JpaAdminUserService.java`
- 수정: `src/main/java/com/example/admin/user/api/JpaAdminUserController.java`
- 테스트: `src/test/java/com/example/admin/api/JpaAdminApiTests.java`

- [ ] **1단계: JPA 사용자 관계 저장 테스트 확인**

다음 흐름이 테스트되는지 확인한다.

```text
POST /api/jpa/users
PUT /api/jpa/users/{id}
PUT /api/jpa/users/{id}/roles
DELETE /api/jpa/users/{id}
```

실행:

```powershell
rg -n "/api/jpa/users|assignRoles|roles" src\test\java
```

예상 결과: 기존 테스트가 흐름을 커버한다. 없으면 production code 변경 전에 테스트를 추가한다.

- [ ] **2단계: `UserDtos`를 Lombok class로 변환**

다음 구조를 사용한다.

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public static class UserRequest {
    @NotBlank
    private String loginId;

    @NotBlank
    private String name;

    @NotNull
    private Boolean enabled;
}

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public static class RoleAssignmentRequest {
    @NotEmpty
    private Set<Long> roleIds;
}

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class UserResponse {
    private Long id;
    private String loginId;
    private String name;
    private boolean enabled;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private List<RoleDtos.RoleSummary> roles;

    public static UserResponse from(AdminUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .loginId(user.getLoginId())
                .name(user.getName())
                .enabled(user.isEnabled())
                .createdBy(user.getCreatedBy())
                .createdAt(user.getCreatedAt())
                .updatedBy(user.getUpdatedBy())
                .updatedAt(user.getUpdatedAt())
                .roles(user.getRoles().stream().map(RoleDtos.RoleSummary::from).toList())
                .build();
    }
}
```

- [ ] **3단계: 사용자 Service/Controller accessor 수정**

다음 record accessor를:

```java
request.loginId()
request.name()
request.enabled()
request.roleIds()
response.id()
```

다음 getter로 바꾼다.

```java
request.getLoginId()
request.getName()
request.getEnabled()
request.getRoleIds()
response.getId()
```

- [ ] **4단계: JPA 사용자 API 테스트 실행**

실행:

```powershell
.\gradlew test --tests "*JpaAdminApiTests"
```

예상 결과: PASS.

- [ ] **5단계: 커밋**

실행:

```powershell
git add src/main/java/com/example/admin/user/dto/UserDtos.java src/main/java/com/example/admin/user/service/JpaAdminUserService.java src/main/java/com/example/admin/user/api/JpaAdminUserController.java src/test/java/com/example/admin/api/JpaAdminApiTests.java
git commit -m "refactor: convert user dtos to lombok classes"
```

---

### 작업 4: 역할 DTO를 Lombok class로 변환하고 역할 쓰기 작업을 JPA로 유지

**파일:**
- 수정: `src/main/java/com/example/admin/role/dto/RoleDtos.java`
- 수정: `src/main/java/com/example/admin/role/service/JpaAdminRoleService.java`
- 수정: `src/main/java/com/example/admin/role/api/JpaAdminRoleController.java`
- 테스트: `src/test/java/com/example/admin/api/JpaAdminApiTests.java`

- [ ] **1단계: JPA 역할 관계 저장 테스트 확인**

다음 흐름이 테스트되는지 확인한다.

```text
POST /api/jpa/roles
PUT /api/jpa/roles/{id}
PUT /api/jpa/roles/{id}/menus
GET /api/jpa/roles/{id}/menus
DELETE /api/jpa/roles/{id}
```

실행:

```powershell
rg -n "/api/jpa/roles|assignMenus|menus" src\test\java
```

예상 결과: 기존 테스트가 흐름을 커버한다. 없으면 production code 변경 전에 테스트를 추가한다.

- [ ] **2단계: `RoleDtos`를 Lombok class로 변환**

다음 구조를 사용한다.

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public static class RoleRequest {
    @NotBlank
    private String roleCode;

    @NotBlank
    private String roleName;

    @NotNull
    private Boolean enabled;
}

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class RoleSummary {
    private Long id;
    private String roleCode;
    private String roleName;

    public static RoleSummary from(AdminRole role) {
        return RoleSummary.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .build();
    }
}

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class RoleResponse {
    private Long id;
    private String roleCode;
    private String roleName;
    private boolean enabled;

    public static RoleResponse from(AdminRole role) {
        return RoleResponse.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .enabled(role.isEnabled())
                .build();
    }
}

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public static class MenuAssignmentRequest {
    @NotEmpty
    private Set<Long> menuIds;
}

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class RoleMenusResponse {
    private Long roleId;
    private Set<MenuDtos.MenuResponse> menus;
}
```

- [ ] **3단계: 역할 Service/Controller accessor 수정**

record accessor를 Lombok getter로 바꾼다.

```java
roleRepository.save(AdminRole.create(
        request.getRoleCode(),
        request.getRoleName(),
        request.getEnabled()));
```

- [ ] **4단계: JPA 역할 API 테스트 실행**

실행:

```powershell
.\gradlew test --tests "*JpaAdminApiTests"
```

예상 결과: PASS.

- [ ] **5단계: 커밋**

실행:

```powershell
git add src/main/java/com/example/admin/role/dto/RoleDtos.java src/main/java/com/example/admin/role/service/JpaAdminRoleService.java src/main/java/com/example/admin/role/api/JpaAdminRoleController.java src/test/java/com/example/admin/api/JpaAdminApiTests.java
git commit -m "refactor: convert role dtos to lombok classes"
```

---

### 작업 5: 단순 JPA native query를 JPQL 또는 derived query로 교체

**파일:**
- 수정: `src/main/java/com/example/admin/user/repository/AdminUserRepository.java`
- 수정: `src/main/java/com/example/admin/user/service/JpaAdminUserService.java`
- 테스트: `src/test/java/com/example/admin/user/repository/AdminUserRepositoryTests.java`

- [ ] **1단계: 대소문자 무시 login 검색 테스트 확인**

실행:

```powershell
rg -n "searchByLoginIdIgnoreCase|loginKeyword|upper" src\test\java
```

예상 결과: repository 또는 API 테스트에서 대소문자 무시 검색이 검증된다.

- [ ] **2단계: native SQL을 JPQL로 교체**

`nativeQuery = true` 대신 JPQL을 사용한다.

```java
@Query("""
        select user
          from AdminUser user
         where upper(user.loginId) like upper(concat('%', :keyword, '%'))
        """)
List<AdminUser> searchByLoginIdIgnoreCase(@Param("keyword") String keyword);
```

- [ ] **3단계: repository 테스트 실행**

실행:

```powershell
.\gradlew test --tests "*AdminUserRepositoryTests"
```

예상 결과: PASS.

- [ ] **4단계: 커밋**

실행:

```powershell
git add src/main/java/com/example/admin/user/repository/AdminUserRepository.java src/test/java/com/example/admin/user/repository/AdminUserRepositoryTests.java
git commit -m "refactor: use jpql for simple user search"
```

---

### 작업 6: QueryDSL 없는 JPA 동적 검색 선택지 추가

**파일:**
- 수정: `src/main/java/com/example/admin/user/repository/AdminUserRepository.java`
- 생성: `src/main/java/com/example/admin/user/repository/AdminUserSpecifications.java`
- 수정: `src/main/java/com/example/admin/user/service/JpaAdminUserService.java`
- 테스트: `src/test/java/com/example/admin/user/repository/AdminUserRepositoryTests.java`

- [ ] **1단계: Repository에 `JpaSpecificationExecutor` 추가**

```java
public interface AdminUserRepository
        extends JpaRepository<AdminUser, Long>, JpaSpecificationExecutor<AdminUser> {
}
```

import 추가:

```java
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
```

- [ ] **2단계: Specification 클래스 생성**

```java
package com.example.admin.user.repository;

import com.example.admin.user.entity.AdminUser;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class AdminUserSpecifications {

    private AdminUserSpecifications() {
    }

    public static Specification<AdminUser> loginIdContains(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            return cb.like(
                    cb.upper(root.get("loginId")),
                    "%" + keyword.toUpperCase() + "%");
        };
    }

    public static Specification<AdminUser> enabledEquals(Boolean enabled) {
        return (root, query, cb) -> enabled == null
                ? null
                : cb.equal(root.get("enabled"), enabled);
    }
}
```

- [ ] **3단계: 조건이 여러 개일 때만 Specification 사용**

API 조건이 loginKeyword 하나뿐이면 JPQL 메서드를 유지한다. `enabled`, `roleId`, 기간 조건 등이 추가되면 다음 방식으로 전환한다.

```java
List<AdminUser> users = userRepository.findAll(
        Specification.where(AdminUserSpecifications.loginIdContains(loginKeyword))
                .and(AdminUserSpecifications.enabledEquals(enabled)));
```

- [ ] **4단계: repository 테스트 실행**

실행:

```powershell
.\gradlew test --tests "*AdminUserRepositoryTests"
```

예상 결과: PASS.

- [ ] **5단계: 커밋**

실행:

```powershell
git add src/main/java/com/example/admin/user/repository/AdminUserRepository.java src/main/java/com/example/admin/user/repository/AdminUserSpecifications.java src/main/java/com/example/admin/user/service/JpaAdminUserService.java src/test/java/com/example/admin/user/repository/AdminUserRepositoryTests.java
git commit -m "feat: add querydsl-free user specifications"
```

---

### 작업 7: MyBatis 쓰기 Service와 Controller 폐기

**파일:**
- 삭제 또는 폐기: `src/main/java/com/example/admin/user/service/MyBatisAdminUserService.java`
- 삭제 또는 폐기: `src/main/java/com/example/admin/role/service/MyBatisAdminRoleService.java`
- 삭제 또는 폐기: `src/main/java/com/example/admin/menu/service/MyBatisAdminMenuService.java`
- 삭제 또는 폐기: `src/main/java/com/example/admin/user/api/MyBatisAdminUserController.java`
- 삭제 또는 폐기: `src/main/java/com/example/admin/role/api/MyBatisAdminRoleController.java`
- 삭제 또는 폐기: `src/main/java/com/example/admin/menu/api/MyBatisAdminMenuController.java`
- 테스트: `src/test/java/com/example/admin/api/MyBatisAdminApiTests.java`

- [ ] **1단계: endpoint 호환성 방식 결정**

다음 둘 중 하나를 선택한다.

```text
방식 A: /api/mybatis/* 쓰기 endpoint를 삭제하고 command endpoint는 /api/jpa/*만 유지한다.
방식 B: /api/mybatis/* 읽기 endpoint는 임시 유지하고 쓰기 endpoint는 405로 응답한다.
```

권장: 구조를 명확히 하기 위해 방식 A를 사용한다.

- [ ] **2단계: MyBatis 쓰기 API 테스트 제거**

다음 endpoint를 호출하는 테스트를 삭제한다.

```text
POST /api/mybatis/users
PUT /api/mybatis/users/{id}
DELETE /api/mybatis/users/{id}
POST /api/mybatis/roles
PUT /api/mybatis/roles/{id}
DELETE /api/mybatis/roles/{id}
POST /api/mybatis/menus
POST /api/mybatis/menus/bulk
PUT /api/mybatis/menus/{id}
DELETE /api/mybatis/menus/{id}
```

- [ ] **3단계: MyBatis 조회 테스트만 유지**

MyBatis 조회 API가 남는다면 테스트는 읽기 endpoint만 호출한다.

```text
GET /api/query/users
GET /api/query/roles/{id}/menus
GET /api/query/menus
```

- [ ] **4단계: 컴파일 실행**

실행:

```powershell
.\gradlew test
```

예상 결과: 삭제한 MyBatis 쓰기 class와 endpoint 참조 때문에만 실패한다.

- [ ] **5단계: 오래된 bean 참조와 import 제거**

실행:

```powershell
rg -n "MyBatisAdmin.*Service|MyBatisAdmin.*Controller|/api/mybatis" src
```

정리 후 예상 결과: MyBatis 쓰기 Service/Controller 참조가 남지 않는다.

- [ ] **6단계: 테스트 실행**

실행:

```powershell
.\gradlew test
```

예상 결과: PASS.

- [ ] **7단계: 커밋**

실행:

```powershell
git add src/main/java src/test/java
git commit -m "refactor: retire mybatis write endpoints"
```

---

### 작업 8: MyBatis Mapper를 조회 전용으로 전환

**파일:**
- 수정: `src/main/java/com/example/admin/user/mapper/AdminUserMapper.java`
- 수정: `src/main/java/com/example/admin/role/mapper/AdminRoleMapper.java`
- 수정: `src/main/java/com/example/admin/menu/mapper/AdminMenuMapper.java`
- 수정: `src/main/resources/mybatis/AdminUserMapper.xml`
- 수정: `src/main/resources/mybatis/AdminRoleMapper.xml`
- 수정: `src/main/resources/mybatis/AdminMenuMapper.xml`
- 테스트: `src/test/java/com/example/admin/api/MyBatisAdminApiTests.java`

- [ ] **1단계: 쓰기 method 선언 제거**

다음 이름의 mapper method를 제거한다.

```text
nextId
insert
update
delete
deleteRoles
insertRoles
deleteMenus
insertMenus
bulkInsert
```

- [ ] **2단계: 쓰기 XML statement 제거**

다음 XML 태그를 제거한다.

```xml
<insert id="insert">
<insert id="bulkInsert">
<update id="update">
<delete id="delete">
<delete id="deleteRoles">
<insert id="insertRoles">
<delete id="deleteMenus">
<insert id="insertMenus">
```

- [ ] **3단계: Mapper projection record를 Lombok class로 변환**

Mapper interface 내부 static class는 조회 projection 용도일 때만 남긴다.

```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
class MenuRow {
    private Long id;
    private String menuCode;
    private String menuName;
    private Long parentMenuId;
    private int sortOrder;
    private boolean enabled;

    MenuDtos.MenuResponse toResponse() {
        return MenuDtos.MenuResponse.builder()
                .id(id)
                .menuCode(menuCode)
                .menuName(menuName)
                .parentMenuId(parentMenuId)
                .sortOrder(sortOrder)
                .enabled(enabled)
                .build();
    }
}
```

- [ ] **4단계: MyBatis 조회 테스트 실행**

실행:

```powershell
.\gradlew test --tests "*MyBatisAdminApiTests"
```

예상 결과: 조회 endpoint가 남아 있다면 PASS. MyBatis API를 모두 제거했다면 이 테스트 class는 삭제하고 이후 복잡 조회 화면에서 mapper 테스트를 새로 만든다.

- [ ] **5단계: 커밋**

실행:

```powershell
git add src/main/java/com/example/admin/user/mapper src/main/java/com/example/admin/role/mapper src/main/java/com/example/admin/menu/mapper src/main/resources/mybatis src/test/java/com/example/admin/api/MyBatisAdminApiTests.java
git commit -m "refactor: make mybatis mappers query only"
```

---

### 작업 9: README에 ag-Grid 저장 전략과 JPA/MyBatis 경계 문서화

**파일:**
- 수정: `README.md`

- [ ] **1단계: record DTO 안내 교체**

DTO는 `record`를 우선 고려한다는 내용을 제거하고 다음 내용을 추가한다.

```markdown
## DTO와 Entity 작성 기준

이 프로젝트는 Entity와 DTO 모두 Lombok class를 사용한다.

- JPA Entity는 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 정적 팩토리 메서드, 도메인 변경 메서드를 사용한다.
- Request DTO는 `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`를 사용한다.
- Response DTO는 `@Getter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`를 사용한다.
- Entity와 DTO에는 Java `record`를 사용하지 않는다.
- JPA Entity에는 `@Data`를 사용하지 않는다.
```

- [ ] **2단계: ag-Grid 저장 전략 추가**

다음 내용을 추가한다.

````markdown
## ag-Grid 저장 전략

권장 요청 구조는 row 상태별 분리 방식이다.

```json
{
  "createdRows": [],
  "updatedRows": [],
  "deletedIds": []
}
```

백엔드는 하나의 Service-level transaction 안에서 요청을 처리한다.

1. id 기준 삭제 row를 삭제한다.
2. 등록 row를 JPA로 저장한다.
3. 수정 row의 id로 Entity를 조회하고 managed entity를 변경한다.
4. 처리 건수를 반환한다.

이 방식은 프론트엔드의 delta 모델이 명확하고, JPA auditing과 entity lifecycle 규칙을 그대로 사용할 수 있다.
````

- [ ] **3단계: JPA/MyBatis 경계 추가**

다음 내용을 추가한다.

```markdown
## JPA와 MyBatis 사용 경계

- 등록, 수정, 삭제, 관계 매핑 저장, 단순 조회는 JPA를 사용한다.
- MyBatis를 검토하기 전에 JPA repository method, JPQL, EntityGraph, Pageable, Projection, Specification, Criteria API를 먼저 검토한다.
- MyBatis는 SQL로 작성하는 것이 더 명확하거나 안전한 복잡 조회에만 사용한다.
- 목표 구조에서 MyBatis XML은 조회 전용이어야 한다.
```

- [ ] **4단계: 커밋**

실행:

```powershell
git add README.md
git commit -m "docs: document jpa mybatis boundary and lombok dto style"
```

---

### 작업 10: 최종 검증

**파일:**
- 전체 source/test/docs 파일을 검증한다.

- [ ] **1단계: 남아 있는 record 검색**

실행:

```powershell
rg -n "public record| record " src\main\java
```

예상 결과: entity/dto package에는 record가 없어야 한다. Mapper projection record도 별도 결정이 없다면 제거되어야 한다.

- [ ] **2단계: MyBatis 쓰기 SQL 검색**

실행:

```powershell
rg -n "<insert|<update|<delete" src\main\resources\mybatis
```

예상 결과: 조회 전용 MyBatis XML에는 쓰기 SQL이 남아 있지 않다.

- [ ] **3단계: MyBatis 쓰기 endpoint 검색**

실행:

```powershell
rg -n "PostMapping|PutMapping|DeleteMapping|/api/mybatis" src\main\java\com\example\admin
```

예상 결과: MyBatis 쓰기 endpoint가 남아 있지 않다.

- [ ] **4단계: 전체 테스트 실행**

실행:

```powershell
.\gradlew test
```

예상 결과: `BUILD SUCCESSFUL`.

- [ ] **5단계: git diff 검토**

실행:

```powershell
git diff --stat
git diff -- src/main/java src/main/resources src/test/java README.md
```

예상 결과: diff는 DTO Lombok 변환, JPA 쓰기 경로, MyBatis 조회 전용 정리, 테스트, 문서 변경만 포함한다.

- [ ] **6단계: 필요한 경우 최종 정리 커밋**

실행:

```powershell
git add src README.md
git commit -m "chore: finalize jpa mybatis transition"
```
