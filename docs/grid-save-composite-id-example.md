# Grid Save 복합 ID 예제

이 문서는 `@EmbeddedId`에 3개 ID가 들어가는 경우의 grid-save 작성 예시입니다. 실제 도메인명만 바꾸면 같은 구조로 적용할 수 있습니다.

예시 상황:
- 사용자에게 권한을 메뉴 단위로 부여합니다.
- PK는 `userId`, `roleId`, `menuId` 3개입니다.
- ID는 오토 시퀀스가 아니라 화면 또는 다른 시스템에서 넘어온 값을 사용합니다.
- 등록하려는 key가 이미 있으면 예외가 아니라 skip하고 응답 `messages`에 안내합니다.

## EmbeddedId

```java
package com.example.admin.userrole.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Embeddable
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserRoleMenuId implements Serializable {

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "ROLE_ID")
    private Long roleId;

    @Column(name = "MENU_ID")
    private Long menuId;
}
```

복합 key는 `equals`, `hashCode`가 중요합니다. `GridSaveExecutor`가 중복과 충돌을 `Set`, `Map`으로 판단하기 때문입니다.

## Entity

```java
package com.example.admin.userrole.entity;

import com.example.admin.common.audit.AuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ADMIN_USER_ROLE_MENU")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminUserRoleMenu extends AuditEntity {

    @EmbeddedId
    private AdminUserRoleMenuId id;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled;

    @Column(name = "SORT_ORDER", nullable = false)
    private int sortOrder;

    private AdminUserRoleMenu(AdminUserRoleMenuId id, boolean enabled, int sortOrder) {
        this.id = id;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
    }

    public static AdminUserRoleMenu create(AdminUserRoleMenuId id, boolean enabled, int sortOrder) {
        return new AdminUserRoleMenu(id, enabled, sortOrder);
    }

    public void update(boolean enabled, int sortOrder) {
        this.enabled = enabled;
        this.sortOrder = sortOrder;
    }
}
```

## DTO

```java
package com.example.admin.userrole.dto.adminuserrolemenu;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleMenuGridRow {

    @NotNull
    @Positive
    private Long userId;

    @NotNull
    @Positive
    private Long roleId;

    @NotNull
    @Positive
    private Long menuId;

    @NotNull
    private Boolean enabled;

    @PositiveOrZero
    private int sortOrder;
}
```

```java
package com.example.admin.userrole.dto.adminuserrolemenu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleMenuGridSaveRequest {

    @Size(max = 1000)
    private List<@NotNull @Valid UserRoleMenuGridRow> createdRows = new ArrayList<>();

    @Size(max = 1000)
    private List<@NotNull @Valid UserRoleMenuGridRow> updatedRows = new ArrayList<>();

    @Size(max = 1000)
    private List<@NotNull @Valid UserRoleMenuKeyRequest> deletedIds = new ArrayList<>();
}
```

```java
package com.example.admin.userrole.dto.adminuserrolemenu;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleMenuKeyRequest {

    @NotNull
    @Positive
    private Long userId;

    @NotNull
    @Positive
    private Long roleId;

    @NotNull
    @Positive
    private Long menuId;
}
```

```java
package com.example.admin.userrole.dto.adminuserrolemenu;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleMenuGridSaveResponse {

    private int createdCount;
    private int updatedCount;
    private int deletedCount;
    private List<String> messages;
}
```

## Repository

```java
package com.example.admin.userrole.repository;

import com.example.admin.userrole.entity.AdminUserRoleMenu;
import com.example.admin.userrole.entity.AdminUserRoleMenuId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRoleMenuRepository
        extends JpaRepository<AdminUserRoleMenu, AdminUserRoleMenuId> {
}
```

## Mapper

```java
package com.example.admin.userrole.mapper;

import com.example.admin.userrole.dto.adminuserrolemenu.UserRoleMenuGridRow;
import com.example.admin.userrole.entity.AdminUserRoleMenu;
import com.example.admin.userrole.entity.AdminUserRoleMenuId;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserRoleMenuMapper {

    default AdminUserRoleMenu toEntity(UserRoleMenuGridRow row) {
        return AdminUserRoleMenu.create(toId(row), row.getEnabled(), row.getSortOrder());
    }

    default void updateEntity(@MappingTarget AdminUserRoleMenu entity, UserRoleMenuGridRow row) {
        entity.update(row.getEnabled(), row.getSortOrder());
    }

    default AdminUserRoleMenuId toId(UserRoleMenuGridRow row) {
        return new AdminUserRoleMenuId(row.getUserId(), row.getRoleId(), row.getMenuId());
    }
}
```

## Service

`saveGrid`에서 중요한 점은 `createdRows`, `updatedRows`, `deletedIds` 각각에서 동일한 key 타입인 `AdminUserRoleMenuId`를 만들어 넘기는 것입니다.

```java
package com.example.admin.userrole.service;

import com.example.admin.common.grid.GridSaveExecutor;
import com.example.admin.common.grid.GridSaveResult;
import com.example.admin.userrole.dto.adminuserrolemenu.UserRoleMenuGridSaveRequest;
import com.example.admin.userrole.dto.adminuserrolemenu.UserRoleMenuGridSaveResponse;
import com.example.admin.userrole.dto.adminuserrolemenu.UserRoleMenuKeyRequest;
import com.example.admin.userrole.entity.AdminUserRoleMenu;
import com.example.admin.userrole.entity.AdminUserRoleMenuId;
import com.example.admin.userrole.mapper.UserRoleMenuMapper;
import com.example.admin.userrole.repository.AdminUserRoleMenuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserRoleMenuService {

    private final AdminUserRoleMenuRepository repository;
    private final GridSaveExecutor gridSaveExecutor;
    private final UserRoleMenuMapper mapper;

    public UserRoleMenuService(
            AdminUserRoleMenuRepository repository,
            GridSaveExecutor gridSaveExecutor,
            UserRoleMenuMapper mapper) {
        this.repository = repository;
        this.gridSaveExecutor = gridSaveExecutor;
        this.mapper = mapper;
    }

    @Transactional
    public UserRoleMenuGridSaveResponse saveGrid(UserRoleMenuGridSaveRequest request) {
        GridSaveResult result = gridSaveExecutor.save(
                request.getCreatedRows(),
                request.getUpdatedRows(),
                request.getDeletedIds().stream()
                        .map(this::toId)
                        .toList(),
                repository,
                mapper::toId,
                mapper::toId,
                AdminUserRoleMenu::getId,
                mapper::toEntity,
                mapper::updateEntity,
                "존재하지 않는 사용자 권한 메뉴 매핑이 포함되어 있습니다.");

        return UserRoleMenuGridSaveResponse.builder()
                .createdCount(result.getCreatedCount())
                .updatedCount(result.getUpdatedCount())
                .deletedCount(result.getDeletedCount())
                .messages(result.getMessages())
                .build();
    }

    private AdminUserRoleMenuId toId(UserRoleMenuKeyRequest request) {
        return new AdminUserRoleMenuId(request.getUserId(), request.getRoleId(), request.getMenuId());
    }
}
```

## 요청 예시

```json
{
  "createdRows": [
    {
      "userId": 1,
      "roleId": 10,
      "menuId": 100,
      "enabled": true,
      "sortOrder": 1
    }
  ],
  "updatedRows": [
    {
      "userId": 1,
      "roleId": 10,
      "menuId": 200,
      "enabled": false,
      "sortOrder": 2
    }
  ],
  "deletedIds": [
    {
      "userId": 1,
      "roleId": 10,
      "menuId": 300
    }
  ]
}
```

이미 등록된 key가 포함되면 응답은 예외가 아니라 다음처럼 내려갑니다.

```json
{
  "createdCount": 0,
  "updatedCount": 0,
  "deletedCount": 0,
  "messages": [
    "id=AdminUserRoleMenuId(userId=1, roleId=10, menuId=100)는 이미 등록된 데이터입니다."
  ]
}
```

운영에서는 메시지에 노출할 key 형식을 더 예쁘게 만들고 싶을 수 있습니다. 그 경우 `@EmbeddedId`에 `toString`을 직접 구현하거나, `GridSaveExecutor`에 `Function<ID, String> keyMessageFormatter`를 추가하는 방식으로 확장하면 됩니다.
