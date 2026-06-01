# rowStatus 기반 그리드 저장 API 예시

작성일: 2026-06-02

## 목적

실무 화면에서는 ag-Grid, Nexacro, WebSquare처럼 row 단위 상태값을 가진 그리드가 자주 사용됩니다. 이 경우 등록, 수정, 삭제 목록을 따로 나누기보다 하나의 `rows` 리스트에 `rowStatus`를 담아 서버로 보내는 방식이 흔합니다.

이 문서는 `rows + rowStatus` 요청이 Controller부터 Service, 공통 Executor, Mapper, Entity, Repository까지 어떻게 실행되는지 예시로 정리합니다.

## 전체 흐름

```text
화면
-> rows + rowStatus 요청
-> Controller validation
-> Service transaction 시작
-> TypedGridMutationExecutor 호출
-> rowStatus 기준으로 삭제/등록/수정 처리
-> Mapper로 DTO -> Entity 또는 기존 Entity update
-> Repository 또는 Store로 DB 반영
-> GridSaveMessage 응답
```

추천 처리 순서:

```text
DELETE -> CREATE -> UPDATE
```

삭제를 먼저 처리하면 같은 key를 삭제 후 다시 등록하는 케이스나 부모/자식 관계 충돌을 줄이기 쉽습니다. 다만 업무상 화면 순서대로 처리해야 한다면 executor 옵션으로 순서를 바꿀 수 있게 두는 것이 좋습니다.

## 요청 JSON 예시

```json
{
  "rows": [
    {
      "rowStatus": "CREATE",
      "id": 10,
      "menuCode": "USER",
      "menuName": "사용자 관리",
      "parentMenuId": 1,
      "sortOrder": 10,
      "enabled": true
    },
    {
      "rowStatus": "UPDATE",
      "id": 2,
      "menuCode": "ROLE",
      "menuName": "권한 관리 수정",
      "parentMenuId": 1,
      "sortOrder": 20,
      "enabled": true
    },
    {
      "rowStatus": "DELETE",
      "id": 3
    }
  ]
}
```

삭제 row는 `id`만 사용합니다. 삭제 row에 다른 컬럼이 같이 넘어와도 백엔드에서는 믿지 않는 편이 안전합니다.

## rowStatus enum

```java
public enum GridRowStatus {
    CREATE,
    UPDATE,
    DELETE
}
```

화면에서 `C`, `U`, `D`로 넘어온다면 enum을 다음처럼 둘 수도 있습니다.

```java
@Getter
@RequiredArgsConstructor
public enum GridRowStatus {
    CREATE("C"),
    UPDATE("U"),
    DELETE("D");

    private final String code;
}
```

## Request DTO

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuTypedGridSaveRequest {

    @Valid
    @NotNull(message = "저장할 메뉴 목록은 필수입니다.")
    private List<MenuTypedGridRow> rows;
}
```

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuTypedGridRow {

    @NotNull(message = "행 상태값은 필수입니다.")
    private GridRowStatus rowStatus;

    @NotNull(message = "메뉴 ID는 필수입니다.")
    private Long id;

    private String menuCode;

    private String menuName;

    private Long parentMenuId;

    private Integer sortOrder;

    private Boolean enabled;
}
```

실무에서는 `CREATE`, `UPDATE`, `DELETE` 상태별 필수값이 다를 수 있습니다. 이 경우 Bean Validation group을 쓰거나, executor 진입 전에 service에서 상태별 검증을 추가합니다.

예:

```text
CREATE -> id, menuCode, menuName 필수
UPDATE -> id, menuName 등 수정에 필요한 값 필수
DELETE -> id만 필수
```

## Response DTO

```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypedGridSaveResponse {

    private int createdCount;

    private int updatedCount;

    private int deletedCount;

    private List<GridSaveMessage> messages;
}
```

```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GridSaveMessage {

    private String operation;

    private String result;

    private String key;

    private String message;
}
```

응답 예시:

```json
{
  "createdCount": 1,
  "updatedCount": 1,
  "deletedCount": 1,
  "messages": [
    {
      "operation": "DELETE",
      "result": "SUCCESS",
      "key": "3",
      "message": "삭제되었습니다. ID: 3"
    },
    {
      "operation": "CREATE",
      "result": "SUCCESS",
      "key": "10",
      "message": "등록되었습니다. ID: 10"
    },
    {
      "operation": "UPDATE",
      "result": "SUCCESS",
      "key": "2",
      "message": "수정되었습니다. ID: 2"
    }
  ]
}
```

## Controller

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/menus")
public class AdminMenuApi {

    private final JpaAdminMenuService menuService;

    @PostMapping("/grid/typed-save")
    public TypedGridSaveResponse saveTypedGrid(
            @Valid @RequestBody MenuTypedGridSaveRequest request
    ) {
        return menuService.saveTypedGrid(request);
    }
}
```

Controller는 요청 검증과 service 호출만 담당합니다. 저장 순서, 존재 여부 판단, Entity 변환은 service 이하에서 처리합니다.

## Entity

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminMenu {

    @Id
    private Long id;

    private String menuCode;

    private String menuName;

    private Long parentMenuId;

    private Integer sortOrder;

    private Boolean enabled;

    public static AdminMenu create(MenuSaveCommand command) {
        AdminMenu menu = new AdminMenu();
        menu.id = command.getId();
        menu.menuCode = command.getMenuCode();
        menu.menuName = command.getMenuName();
        menu.parentMenuId = command.getParentMenuId();
        menu.sortOrder = command.getSortOrder();
        menu.enabled = command.getEnabled();
        return menu;
    }

    public void update(MenuSaveCommand command) {
        this.menuName = command.getMenuName();
        this.parentMenuId = command.getParentMenuId();
        this.sortOrder = command.getSortOrder();
        this.enabled = command.getEnabled();
    }
}
```

Entity는 setter를 열기보다 `create`, `update` 같은 도메인 메서드로 상태 변경을 제한하는 편이 좋습니다.

## Command

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuSaveCommand {

    private Long id;

    private String menuCode;

    private String menuName;

    private Long parentMenuId;

    private Integer sortOrder;

    private Boolean enabled;
}
```

컬럼이 많은 실무 테이블에서는 DTO를 Entity에 바로 넣기보다 command를 중간에 두면 `create`, `update` 메서드 인자가 길어지는 문제를 줄일 수 있습니다.

## Mapper

```java
@Mapper(componentModel = "spring")
public interface MenuMapper {

    MenuSaveCommand toCommand(MenuTypedGridRow row);

    default AdminMenu toEntity(MenuTypedGridRow row) {
        return AdminMenu.create(toCommand(row));
    }

    default void updateEntity(@MappingTarget AdminMenu menu, MenuTypedGridRow row) {
        menu.update(toCommand(row));
    }
}
```

Mapper는 DTO 값을 command로 변환하고, Entity의 `create`, `update` 메서드를 호출하는 얇은 역할만 담당합니다.

## Repository

```java
public interface AdminMenuRepository extends JpaRepository<AdminMenu, Long> {
}
```

복잡 조회는 MyBatis `store`로 분리하더라도 등록, 수정, 삭제가 JPA라면 Repository를 사용합니다.

## 공통 Executor

```java
@Component
public class TypedGridMutationExecutor {

    public <R, ID, E> TypedGridSaveResponse save(
            List<R> rows,
            Function<R, GridRowStatus> statusReader,
            Function<R, ID> idReader,
            Function<ID, Optional<E>> finder,
            Function<R, E> creator,
            Consumer<E> saver,
            BiConsumer<E, R> updater,
            Consumer<E> deleter,
            Consumer<TypedGridSaveContext<ID, E>> afterSave,
            String resourceName
    ) {
        if (rows == null || rows.isEmpty()) {
            return TypedGridSaveResponse.builder()
                    .createdCount(0)
                    .updatedCount(0)
                    .deletedCount(0)
                    .messages(List.of())
                    .build();
        }

        validateRows(rows, statusReader, idReader);

        List<R> deleteRows = filterRows(rows, statusReader, GridRowStatus.DELETE);
        List<R> createRows = filterRows(rows, statusReader, GridRowStatus.CREATE);
        List<R> updateRows = filterRows(rows, statusReader, GridRowStatus.UPDATE);

        List<E> createdEntities = new ArrayList<>();
        List<E> updatedEntities = new ArrayList<>();
        List<E> deletedEntities = new ArrayList<>();
        List<GridSaveMessage> messages = new ArrayList<>();

        for (R row : deleteRows) {
            ID id = idReader.apply(row);
            E entity = finder.apply(id)
                    .orElseThrow(() -> new NotFoundException("삭제할 " + resourceName + "가 존재하지 않습니다. ID: " + id));

            deleter.accept(entity);
            deletedEntities.add(entity);
            messages.add(successMessage("DELETE", id, "삭제되었습니다. ID: " + id));
        }

        for (R row : createRows) {
            ID id = idReader.apply(row);

            if (finder.apply(id).isPresent()) {
                throw new BadRequestException("이미 등록된 " + resourceName + "입니다. ID: " + id);
            }

            E entity = creator.apply(row);
            saver.accept(entity);
            createdEntities.add(entity);
            messages.add(successMessage("CREATE", id, "등록되었습니다. ID: " + id));
        }

        for (R row : updateRows) {
            ID id = idReader.apply(row);
            E entity = finder.apply(id)
                    .orElseThrow(() -> new NotFoundException("수정할 " + resourceName + "가 존재하지 않습니다. ID: " + id));

            updater.accept(entity, row);
            updatedEntities.add(entity);
            messages.add(successMessage("UPDATE", id, "수정되었습니다. ID: " + id));
        }

        TypedGridSaveContext<ID, E> context = TypedGridSaveContext.<ID, E>builder()
                .createdEntities(createdEntities)
                .updatedEntities(updatedEntities)
                .deletedEntities(deletedEntities)
                .messages(messages)
                .build();

        if (afterSave != null) {
            afterSave.accept(context);
        }

        return TypedGridSaveResponse.builder()
                .createdCount(createdEntities.size())
                .updatedCount(updatedEntities.size())
                .deletedCount(deletedEntities.size())
                .messages(messages)
                .build();
    }

    private <R, ID> void validateRows(
            List<R> rows,
            Function<R, GridRowStatus> statusReader,
            Function<R, ID> idReader
    ) {
        Set<ID> ids = new HashSet<>();

        for (R row : rows) {
            GridRowStatus status = statusReader.apply(row);
            ID id = idReader.apply(row);

            if (status == null) {
                throw new BadRequestException("행 상태값은 필수입니다.");
            }

            if (id == null) {
                throw new BadRequestException("행 ID는 필수입니다. 상태: " + status);
            }

            if (!ids.add(id)) {
                throw new BadRequestException("같은 ID가 여러 작업에 포함되어 있습니다. ID: " + id);
            }
        }
    }

    private <R> List<R> filterRows(
            List<R> rows,
            Function<R, GridRowStatus> statusReader,
            GridRowStatus status
    ) {
        return rows.stream()
                .filter(row -> status == statusReader.apply(row))
                .toList();
    }

    private GridSaveMessage successMessage(String operation, Object id, String message) {
        return GridSaveMessage.builder()
                .operation(operation)
                .result("SUCCESS")
                .key(String.valueOf(id))
                .message(message)
                .build();
    }
}
```

위 예시는 단건 오류가 있으면 전체를 예외 처리하는 방식입니다. grid 저장에서 row별 skip을 허용하려면 `orElseThrow` 대신 `GridSaveMessage`를 추가하고 다음 row로 넘어가는 방식으로 바꿀 수 있습니다.

## 후처리 Context

```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypedGridSaveContext<ID, E> {

    private List<E> createdEntities;

    private List<E> updatedEntities;

    private List<E> deletedEntities;

    private List<GridSaveMessage> messages;

    public List<ID> getChangedIds(Function<E, ID> idReader) {
        return Stream.of(createdEntities, updatedEntities, deletedEntities)
                .flatMap(List::stream)
                .map(idReader)
                .toList();
    }
}
```

후처리에서는 이력 저장, 캐시 삭제, 이벤트 발행 등을 처리합니다.

```java
context -> {
    historyService.writeGridHistory(
            context.getCreatedEntities(),
            context.getUpdatedEntities(),
            context.getDeletedEntities());

    cacheService.evictMenus(context.getChangedIds(AdminMenu::getId));
}
```

## Service

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaAdminMenuService {

    private final AdminMenuRepository menuRepository;
    private final MenuMapper menuMapper;
    private final TypedGridMutationExecutor typedGridMutationExecutor;
    private final MenuHistoryService historyService;
    private final MenuCacheService cacheService;

    @Transactional
    public TypedGridSaveResponse saveTypedGrid(MenuTypedGridSaveRequest request) {
        return typedGridMutationExecutor.save(
                request.getRows(),
                MenuTypedGridRow::getRowStatus,
                MenuTypedGridRow::getId,
                menuRepository::findById,
                menuMapper::toEntity,
                menuRepository::save,
                menuMapper::updateEntity,
                menuRepository::delete,
                context -> {
                    historyService.writeGridHistory(
                            context.getCreatedEntities(),
                            context.getUpdatedEntities(),
                            context.getDeletedEntities());

                    cacheService.evictMenus(context.getChangedIds(AdminMenu::getId));
                },
                "메뉴");
    }
}
```

Service는 전체 저장 흐름을 조립합니다.

Service에 남기는 책임:

```text
- 어떤 repository 또는 store를 사용할지
- 어떤 mapper를 사용할지
- 저장 후 어떤 후처리를 할지
- transaction 경계
```

Executor가 가져가는 책임:

```text
- rowStatus별 분기
- key null 검증
- 중복 key 검증
- create 중복 예외
- update 대상 없음 예외
- delete 대상 없음 예외
- count/message 생성
- afterSave hook 호출
```

## MyBatis 저장이 필요한 경우

등록, 수정, 삭제가 MyBatis일 수도 있다면 service에서 넘기는 lambda만 바꿉니다.

```java
@Transactional
public TypedGridSaveResponse saveTypedGrid(MenuTypedGridSaveRequest request) {
    return typedGridMutationExecutor.save(
            request.getRows(),
            MenuTypedGridRow::getRowStatus,
            MenuTypedGridRow::getId,
            menuStore::findById,
            menuMapper::toEntity,
            menu -> {
                int count = menuStore.insert(menuMapper.toInsertParam(menu));
                if (count == 0) {
                    throw new BadRequestException("메뉴 등록에 실패했습니다. ID: " + menu.getId());
                }
            },
            (menu, row) -> {
                int count = menuStore.update(menuMapper.toUpdateParam(row));
                if (count == 0) {
                    throw new NotFoundException("수정할 메뉴가 존재하지 않습니다. ID: " + row.getId());
                }
            },
            menu -> {
                int count = menuStore.delete(menu.getId());
                if (count == 0) {
                    throw new NotFoundException("삭제할 메뉴가 존재하지 않습니다. ID: " + menu.getId());
                }
            },
            context -> historyService.writeGridHistory(
                    context.getCreatedEntities(),
                    context.getUpdatedEntities(),
                    context.getDeletedEntities()),
            "메뉴");
}
```

다만 CUD는 가능하면 JPA로 통일하는 것이 좋습니다. MyBatis CUD는 레거시 유지나 특수 SQL이 필요한 경우에만 남기는 방향을 권장합니다.

## 예외 정책

단건 오류가 있으면 전체 저장을 실패시키는 정책:

```text
CREATE 대상 ID가 이미 있음 -> 400 예외
UPDATE 대상 ID가 없음 -> 404 예외
DELETE 대상 ID가 없음 -> 404 예외
요청 rowStatus 없음 -> 400 예외
요청 ID 없음 -> 400 예외
같은 ID가 여러 작업에 포함됨 -> 400 예외
```

row별 skip 정책을 쓰는 경우:

```text
CREATE 대상 ID가 이미 있음 -> SKIPPED message
UPDATE 대상 ID가 없음 -> SKIPPED message
DELETE 대상 ID가 없음 -> SKIPPED message
요청 형식 자체가 잘못됨 -> 400 예외
```

실무 추천:

```text
업무 정합성이 중요함 -> 예외로 전체 롤백
사용자가 row별 결과를 봐야 함 -> skip message
```

## 최종 추천

실무에서 자주 쓰이는 grid 저장 API는 다음 방향을 추천합니다.

```text
요청: rows + rowStatus
처리: TypedGridMutationExecutor
순서: DELETE -> CREATE -> UPDATE
응답: createdCount, updatedCount, deletedCount, messages
후처리: 전체 처리 후 context 기반 일괄 실행
```

이 구조는 화면 개발자가 row 상태를 그대로 넘길 수 있고, 백엔드는 공통 executor에서 일관된 예외/메시지 정책을 적용할 수 있어서 실무 적용성이 좋습니다.
