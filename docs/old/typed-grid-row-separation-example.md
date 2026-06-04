# rowStatus 요청 분리 후 기존 GridSaveExecutor 재사용 예시

작성일: 2026-06-02

## 목적

프론트에서는 실무에서 자주 쓰는 `rows + rowStatus` 방식으로 데이터를 보내고, 백엔드에서는 이 요청을 `createdRows`, `updatedRows`, `deletedIds`로 분리한 뒤 기존 `GridSaveExecutor`를 재사용하는 방식을 정리합니다.

이 방식은 새 `TypedGridMutationExecutor`를 만들지 않아도 되고, 이미 만들어둔 grid 저장 공통 로직을 유지할 수 있다는 장점이 있습니다.

## 전체 흐름

```text
화면
-> rows + rowStatus 요청
-> Controller
-> Service
-> GridRowSeparator로 상태별 분리
-> 기존 GridSaveExecutor 호출
-> JPA CUD 처리
-> GridSaveResult 응답
```

핵심 아이디어:

```text
외부 요청 형태: rows + rowStatus
내부 처리 형태: createdRows + updatedRows + deletedIds
저장 공통 로직: 기존 GridSaveExecutor 재사용
```

## 요청 JSON

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

삭제 row는 `id`만 사용합니다. 삭제 row에 다른 값이 넘어와도 저장 로직에서는 사용하지 않는 편이 안전합니다.

## rowStatus enum

```java
public enum GridRowStatus {
    CREATE,
    UPDATE,
    DELETE
}
```

실무 화면에서 `C`, `U`, `D`로 넘어온다면 Jackson 변환이나 별도 converter를 둘 수 있습니다.

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

DTO는 요청 데이터를 담는 역할만 담당합니다. DB 조회, 저장, 예외 정책은 DTO에 넣지 않습니다.

## DTO 안에서 바로 분리하는 방식

가장 단순한 방식은 request DTO에 분리 메서드를 두는 것입니다.

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuTypedGridSaveRequest {

    @Valid
    @NotNull(message = "저장할 메뉴 목록은 필수입니다.")
    private List<MenuTypedGridRow> rows;

    public List<MenuTypedGridRow> getCreatedRows() {
        return filterByStatus(GridRowStatus.CREATE);
    }

    public List<MenuTypedGridRow> getUpdatedRows() {
        return filterByStatus(GridRowStatus.UPDATE);
    }

    public List<Long> getDeletedIds() {
        return filterByStatus(GridRowStatus.DELETE).stream()
                .map(MenuTypedGridRow::getId)
                .toList();
    }

    private List<MenuTypedGridRow> filterByStatus(GridRowStatus status) {
        if (rows == null) {
            return List.of();
        }

        return rows.stream()
                .filter(row -> status == row.getRowStatus())
                .toList();
    }
}
```

장점:

```text
- 코드가 단순하다.
- Service에서 바로 request.getCreatedRows()처럼 사용할 수 있다.
- 별도 helper가 필요 없다.
```

단점:

```text
- getCreatedRows(), getUpdatedRows(), getDeletedIds()를 호출할 때마다 stream을 다시 돈다.
- 분리 검증이 늘어나면 DTO 책임이 무거워진다.
- 여러 API에서 같은 분리 로직을 재사용하기 어렵다.
```

따라서 단순한 화면 하나라면 DTO 메서드도 가능하지만, 공통화 관점에서는 helper 분리를 더 추천합니다.

## 공통 분리 결과 객체

```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GridSeparatedRows<C, U, ID> {

    private List<C> createdRows;

    private List<U> updatedRows;

    private List<ID> deletedIds;

    public static <C, U, ID> GridSeparatedRows<C, U, ID> empty() {
        return GridSeparatedRows.<C, U, ID>builder()
                .createdRows(List.of())
                .updatedRows(List.of())
                .deletedIds(List.of())
                .build();
    }
}
```

## 공통 분리 Helper

```java
@Component
public class GridRowSeparator {

    public <R, ID> GridSeparatedRows<R, R, ID> separate(
            List<R> rows,
            Function<R, GridRowStatus> statusReader,
            Function<R, ID> idReader
    ) {
        if (rows == null || rows.isEmpty()) {
            return GridSeparatedRows.empty();
        }

        List<R> createdRows = new ArrayList<>();
        List<R> updatedRows = new ArrayList<>();
        List<ID> deletedIds = new ArrayList<>();

        for (R row : rows) {
            GridRowStatus status = statusReader.apply(row);
            ID id = idReader.apply(row);

            if (status == null) {
                throw new BadRequestException("행 상태값은 필수입니다. ID: " + id);
            }

            if (id == null) {
                throw new BadRequestException("행 ID는 필수입니다. 상태: " + status);
            }

            switch (status) {
                case CREATE -> createdRows.add(row);
                case UPDATE -> updatedRows.add(row);
                case DELETE -> deletedIds.add(id);
            }
        }

        return GridSeparatedRows.<R, R, ID>builder()
                .createdRows(createdRows)
                .updatedRows(updatedRows)
                .deletedIds(deletedIds)
                .build();
    }
}
```

이 helper는 요청 row를 상태별로 나누는 책임만 가집니다. DB 존재 여부 판단이나 저장 정책은 기존 `GridSaveExecutor`에서 처리합니다.

## Service

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaAdminMenuService {

    private final AdminMenuRepository menuRepository;
    private final MenuMapper menuMapper;
    private final GridRowSeparator gridRowSeparator;
    private final GridSaveExecutor gridSaveExecutor;

    @Transactional
    public MenuGridSaveResponse saveTypedGrid(MenuTypedGridSaveRequest request) {
        GridSeparatedRows<MenuTypedGridRow, MenuTypedGridRow, Long> rows =
                gridRowSeparator.separate(
                        request.getRows(),
                        MenuTypedGridRow::getRowStatus,
                        MenuTypedGridRow::getId);

        gridSaveExecutor.validateRequestConflicts(
                rows.getCreatedRows(),
                rows.getUpdatedRows(),
                rows.getDeletedIds(),
                MenuTypedGridRow::getId,
                MenuTypedGridRow::getId,
                Function.identity());

        GridSaveResult result = GridSaveResult.empty()
                .merge(gridSaveExecutor.delete(
                        rows.getDeletedIds(),
                        menuRepository,
                        Function.identity(),
                        AdminMenu::getId,
                        "존재하지 않는 메뉴가 포함되어 있습니다."))
                .merge(gridSaveExecutor.create(
                        rows.getCreatedRows(),
                        menuRepository,
                        MenuTypedGridRow::getId,
                        AdminMenu::getId,
                        menuMapper::toEntity))
                .merge(gridSaveExecutor.update(
                        rows.getUpdatedRows(),
                        menuRepository,
                        MenuTypedGridRow::getId,
                        AdminMenu::getId,
                        menuMapper::updateEntity,
                        "존재하지 않는 메뉴가 포함되어 있습니다."));

        return MenuGridSaveResponse.from(result);
    }
}
```

Service 흐름은 다음처럼 읽힙니다.

```text
1. request rows를 상태별로 분리한다.
2. `GridSaveExecutor`의 create/update/delete 메서드 중 필요한 작업만 호출한다.
3. 결과를 response로 변환한다.
```

## 기존 GridSaveExecutor를 재사용하는 이유

이미 `GridSaveExecutor`에 다음 정책이 들어가 있다면 새 executor를 만들 필요가 없습니다.

```text
- null 그룹은 작업하지 않음
- null row 또는 null key는 요청 오류
- create 중복 처리
- update 대상 없음 처리
- delete 대상 없음 처리
- strict exception 또는 skip message 정책
- created/updated/deleted count 생성
- GridSaveMessage 생성
```

따라서 `rows + rowStatus` 요청은 저장 executor를 새로 만들기보다, 초반에 상태별로 분리해서 기존 executor를 재사용하는 방식이 더 안정적입니다.

## DTO와 Helper 책임 기준

DTO 안에 둬도 되는 것:

```text
- rows를 상태별로 나누기
- deletedIds 뽑기
- rows가 null이면 빈 리스트 반환
```

Helper 또는 Service에 두는 것이 좋은 것:

```text
- 중복 ID 검증
- create/update/delete 충돌 검증
- rowStatus null 검증
- ID null 검증
- DB 존재 여부 판단
- 예외 또는 skip message 정책
```

실무 추천:

```text
간단한 API 1개 -> DTO 메서드로 분리 가능
여러 grid API에서 재사용 -> GridRowSeparator helper 추천
```

## 통합 save 메서드 인자 수 문제

모든 것을 한 메서드에 넘기면 인자가 많아질 수 있습니다.

```java
typedGridMutationExecutor.save(
        rows,
        statusReader,
        idReader,
        finder,
        creator,
        saver,
        updater,
        deleter,
        afterSave,
        resourceName);
```

이 방식은 설명용으로는 좋지만 실무 코드에서는 가독성이 떨어질 수 있습니다.

반면 `GridRowSeparator` 방식은 기존 executor 시그니처를 유지하면서 프론트 요청만 변환합니다.

```text
rows + rowStatus
-> GridRowSeparator
-> createdRows / updatedRows / deletedIds
-> GridSaveExecutor
```

따라서 현재 프로젝트에 이미 `GridSaveExecutor`가 있다면, 새 `TypedGridMutationExecutor`를 만들기보다 separator를 추가하는 방식이 더 현실적입니다.

## 최종 추천

현재 구조에서는 다음 방향을 추천합니다.

```text
프론트 요청: rows + rowStatus
분리 위치: GridRowSeparator
저장 처리: 기존 GridSaveExecutor 재사용
후처리: GridSaveResult 또는 별도 context 기반으로 service에서 처리
```

이렇게 하면 실무에서 많이 쓰는 요청 형태를 받아들이면서도, 백엔드 저장 공통화는 기존 구조를 최대한 살릴 수 있습니다.
