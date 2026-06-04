# Admin Learning

Java 21, Spring Boot, Oracle, JPA, MyBatis 3.x를 함께 다루는 관리자 예제입니다.  
현재 구조의 기준은 명확합니다.

- 등록, 수정, 삭제, 관계 매핑 저장은 JPA로 처리합니다.
- 단순 조회도 우선 JPA로 처리합니다.
- MyBatis는 SQL로 표현하는 편이 더 명확한 복잡 조회에만 사용합니다.
- QueryDSL은 사용하지 않습니다.
- Entity와 DTO는 `record`를 사용하지 않고 Lombok class로 작성합니다.

세부 정책은 [관리자 데이터 변경과 Grid 저장 기준](docs/admin-data-mutation-grid-policy.md)을 따릅니다. 이 문서는 validation, JPA/MyBatis 경계, CRUD 정책, ag-Grid 저장, 복합 ID, `rows + rowStatus` 어댑터 방향을 하나로 묶은 현재 기준입니다.

## 실행

```powershell
$env:ORACLE_URL='jdbc:oracle:thin:@localhost:1521/FREEPDB1'
$env:ORACLE_USERNAME='admin_learning'
$env:ORACLE_PASSWORD='admin_learning'
.\gradlew bootRun
```

Swagger UI는 `http://localhost:8080/swagger-ui.html`에서 확인합니다.

테스트는 H2 Oracle mode를 사용합니다.

```powershell
.\gradlew test
```

## 패키지 구조

```text
com.example.admin
|- common : auditing, security, exception, OpenAPI, MyBatis config
|- user   : 사용자 API, JPA repository, query-only MyBatis store
|- role   : 역할 API, 역할-메뉴 권한 매핑
`- menu   : 메뉴 API, 계층 메뉴, ag-Grid 저장
```

각 기능 패키지 안에는 필요에 따라 `api`, `service`, `dto`, `entity`, `repository`, `mapper`를 둡니다.

## API 기준

| 기능 | API | 저장 방식 |
| --- | --- | --- |
| 사용자 등록/수정/삭제 | `/api/jpa/users` | JPA |
| 사용자-역할 저장 | `PUT /api/jpa/users/{id}/roles` | JPA |
| 역할 등록/수정/삭제 | `/api/jpa/roles` | JPA |
| 역할-메뉴 저장 | `PUT /api/jpa/roles/{id}/menus` | JPA |
| 메뉴 조회 | `GET /api/jpa/menus`, `GET /api/jpa/menus/{id}`, `GET /api/jpa/menus/children` | JPA |
| 메뉴 페이징 조회 | `GET /api/jpa/menus/page` | JPA |
| 메뉴 페이징 조회 | `GET /api/mybatis/menus/page` | MyBatis select only |
| 메뉴 ag-Grid 저장 | `POST /api/jpa/menus/grid-save` | JPA |
| 복잡 조회 | MyBatis store | MyBatis select only |

MyBatis의 기존 쓰기 API와 쓰기 SQL은 제거했습니다. `src/main/resources/mybatis`에는 조회 SQL만 남아야 합니다. MyBatis controller/service는 조회 전용 API에만 사용합니다.

메뉴 도메인은 조회와 ag-Grid 저장 예제로 둡니다. 메뉴의 등록, 수정, 삭제는 단건 API를 따로 열지 않고 `POST /api/jpa/menus/grid-save`에서 처리합니다. 사용자와 역할 도메인은 기존처럼 등록, 수정, 삭제, 조회 API를 모두 유지합니다.

## DTO와 Entity 작성 기준

이 프로젝트는 Entity와 DTO 모두 Lombok class를 사용합니다.

- Entity와 DTO에 Java `record`를 사용하지 않습니다.
- JPA Entity에는 `@Data`를 사용하지 않습니다.
- JPA Entity는 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 정적 팩토리 메서드, 의미 있는 상태 변경 메서드를 기본으로 둡니다.
- Request DTO는 JSON 바인딩을 위해 `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`를 사용합니다.
- Response DTO는 `@Getter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`를 우선 사용합니다.
- MyBatis store projection도 `record` 대신 Lombok class로 작성합니다.

예시:

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
```

```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class UserResponse {
    private Long id;
    private String loginId;
    private String name;
    private boolean enabled;
}
```

```java
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminUser {
    private String name;
    private boolean enabled;

    public void update(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }
}
```

## JPA와 MyBatis 사용 경계

MyBatis를 검토하기 전에 다음 JPA 방법을 먼저 검토합니다.

- Spring Data repository method
- JPQL `@Query`
- `@EntityGraph`
- interface/class projection
- `Pageable`, `Slice`, `Page`
- `Specification`
- Criteria API

QueryDSL은 사용하지 않습니다.

MyBatis는 다음 경우에만 사용합니다.

- 여러 테이블과 집계가 섞여 JPQL보다 SQL이 명확한 조회
- Oracle 전용 분석 함수, 계층 쿼리, 복잡한 동적 조건이 필요한 조회
- 화면 요구사항과 SQL 결과 shape가 거의 일치하는 관리자 조회

쓰기 작업은 MyBatis로 만들지 않습니다.

## QueryDSL 없이 조회하는 방법 예시

### 1. Repository method

```java
List<AdminRole> findByEnabledTrueOrderByRoleCode();
```

간단한 조건과 정렬에 적합합니다.

### 2. JPQL

```java
@Query("""
        select role
          from AdminRole role
         where role.roleCode like concat('%', :keyword, '%')
        """)
List<AdminRole> searchByRoleCode(@Param("keyword") String keyword);
```

Entity 중심 조회에 적합합니다.

### 3. EntityGraph

```java
@EntityGraph(attributePaths = "menus")
Optional<AdminRole> findWithMenusById(Long id);
```

연관관계를 함께 읽어야 할 때 사용합니다.

### 4. Projection

```java
public interface UserSummaryProjection {
    Long getId();
    String getLoginId();
    String getName();
}
```

목록 화면에서 필요한 컬럼만 가져올 때 사용합니다.

### 5. Specification

```java
public static Specification<AdminUser> enabledEquals(Boolean enabled) {
    return (root, query, cb) -> enabled == null
            ? null
            : cb.equal(root.get("enabled"), enabled);
}
```

검색 조건이 선택적으로 늘어나는 화면에 적합합니다.

### 6. Criteria API

Criteria API는 문자열 JPQL보다 장황하지만 동적 조건을 코드로 조립할 수 있습니다.  
현재 사용자 loginId 검색은 QueryDSL 없이 Criteria API와 DB 함수를 조합하는 예시를 포함합니다.

## ag-Grid 저장 전략

현재 메뉴 grid 저장 API는 프론트엔드에서 변경 row를 상태별로 나눠 보냅니다.

```json
{
  "createdRows": [],
  "updatedRows": [],
  "deletedIds": []
}
```

백엔드는 하나의 service transaction 안에서 처리합니다.

1. `deletedIds`의 중복과 존재 여부를 검증합니다.
2. `updatedRows`의 key null, 중복, 삭제 대상과의 충돌을 검증합니다.
3. 삭제 대상은 JPA로 삭제합니다.
4. 등록 row는 Entity로 만들어 `saveAll` 합니다.
5. 수정 row는 id로 Entity를 조회한 뒤 managed entity의 상태 변경 메서드를 호출합니다.
6. 처리 건수를 응답합니다.

이 방식은 ag-Grid의 delta 모델과 잘 맞고, JPA auditing과 entity lifecycle을 그대로 사용할 수 있습니다.

프론트엔드가 `rows + rowStatus` 단일 배열 형태를 보내야 하는 경우에도 기존 `GridSaveExecutor`를 대체하지 않습니다. 별도 `GridRowSeparator` 같은 어댑터에서 `createdRows`, `updatedRows`, `deletedIds`로 분리한 뒤 현재 저장 흐름을 재사용합니다.

## ag-Grid 저장 명명 규칙

새로운 ag-Grid 저장 기능을 만들 때는 도메인 이름을 앞에 두고 같은 패턴으로 맞춥니다.

### Entity

Entity는 기존 JPA 명명 규칙을 따릅니다.

```text
Admin{Domain}
```

예시:

- `AdminMenu`
- `AdminUser`
- `AdminRole`

Entity에는 grid 전용 이름을 붙이지 않습니다. grid는 화면 저장 방식일 뿐이고, Entity는 도메인 모델입니다.

### DTO

DTO는 API Java 파일 기준 패키지 아래에 개별 Java 파일로 둡니다.

```text
{domain}/api/{ApiFile}.java
{domain}/dto/{api-package}/...
```

예시:

```text
menu/api/JpaAdminMenuController.java
menu/dto/adminmenu/MenuRequest.java
menu/dto/adminmenu/MenuGridRow.java
menu/dto/adminmenu/MenuGridSaveRequest.java
menu/dto/adminmenu/MenuGridSaveResponse.java
menu/dto/adminmenu/MenuResponse.java
```

DTO 파일 예시:

```java
public class MenuRequest {
    // 등록에 필요한 필드
}

public class MenuGridRow extends MenuRequest {
    private Long id;
}

public class MenuGridSaveRequest {
    private List<MenuGridRow> createdRows;
    private List<MenuGridRow> updatedRows;
    private List<Long> deletedIds;
}

public class MenuGridSaveResponse {
    private int createdCount;
    private int updatedCount;
    private int deletedCount;
    private List<GridSaveMessage> messages;
}
```

기본 원칙:

- DTO 패키지는 API Java 파일 단위로 만든다.
- 같은 API 파일에서 사용하는 request, response, row DTO는 같은 DTO 패키지에 둔다.
- DTO는 nested class가 아니라 개별 Java 파일로 만든다.
- `createdRows`와 `updatedRows`는 화면에서 넘어온 key가 포함된 `{Domain}GridRow`를 사용합니다.
- 등록, 수정, 삭제 key는 단일 ID와 `@EmbeddedId` 같은 복합 ID를 모두 허용합니다.
- `{Domain}GridRow`는 `{Domain}Request`를 상속하고 `id`를 추가합니다.
- `deletedIds`는 단일 ID 또는 복합 ID key 목록만 받습니다.
- row 그룹 자체가 `null`이면 작업 없음으로 처리합니다.
- row 그룹 안의 `null` row나 등록/수정 row의 `null` key는 잘못된 요청으로 처리합니다.
- 등록하려는 key가 이미 DB에 있으면 예외를 던지지 않고 해당 row를 건너뛰며 `messages`에 안내 문구를 담습니다.

등록 시에도 외부 id가 반드시 필요한 도메인은 별도 DTO를 만듭니다.

```text
{Domain}CreateGridRow
{Domain}UpdateGridRow
```

이 경우에도 이름은 역할이 드러나게 분리하고, 모든 도메인에 억지로 같은 DTO를 적용하지 않습니다.

### Mapper

JPA Entity와 DTO 변환은 도메인별 mapper가 담당합니다.

```text
{Domain}Mapper
```

예시:

```java
@Mapper(componentModel = "spring")
public interface MenuMapper extends BaseMapper<MenuGridRow, AdminMenu> {

    @Override
    default AdminMenu toEntity(MenuGridRow request) {
        // DTO -> Entity
    }

    @Override
    default void updateEntity(@MappingTarget AdminMenu menu, MenuGridRow row) {
        // grid 수정 row -> managed entity 변경
    }

    default void updateEntity(@MappingTarget AdminMenu menu, MenuRequest request) {
        // 일반 수정 요청 -> managed entity 변경
    }

    default MenuResponse toResponse(AdminMenu menu) {
        // Entity -> DTO
    }
}
```

mapper 명명 규칙:

- `BaseMapper<D, E>`를 상속하는 경우 같은 시그니처의 메서드에는 `@Override`를 붙입니다.
- DTO 타입이 달라지는 메서드는 override가 아니라 overload이므로 `@Override`를 붙이지 않습니다.
- `toEntity`: 등록 DTO를 Entity로 변환합니다.
- `updateEntity`: 수정 DTO 값을 managed entity에 반영합니다.
- `toResponse`: Entity를 응답 DTO로 변환합니다.
- 단순 필드 복사라도 Entity의 상태 변경은 setter가 아니라 도메인 메서드를 호출합니다.

### Store

MyBatis와 연결되는 Java interface는 mapper가 아니라 store로 둡니다.

```text
{Domain}/store/MyBatisAdmin{Domain}Store
```

예시:

```text
user/store/MyBatisAdminUserStore
role/store/MyBatisAdminRoleStore
menu/store/MyBatisAdminMenuStore
```

store 명명 규칙:

- `store`: MyBatis, 외부 API, 파일 등 외부 저장소/조회 기술 연결부입니다.
- `mapper`: MapStruct `@Mapper(componentModel = "spring")` 기반 DTO와 Entity 변환 전용입니다.
- `repository`: Spring Data JPA 접근 전용입니다.
- MyBatis XML의 `namespace`는 store interface의 fully qualified name과 맞춥니다.
- MyBatis XML의 `resultType`도 store 내부 row projection을 가리킵니다.

### Service

Service 메서드는 화면 동작을 기준으로 이름을 붙입니다.

```text
saveGrid
```

예시:

```java
@Transactional
public MenuGridSaveResponse saveGrid(MenuGridSaveRequest request) {
    gridSaveExecutor.validateRequestConflicts(
            request.getCreatedRows(),
            request.getUpdatedRows(),
            request.getDeletedIds(),
            MenuGridRow::getId,
            MenuGridRow::getId,
            Function.identity());

    GridSaveResult result = GridSaveResult.empty();

    if (hasRows(request.getDeletedIds())) {
        result = result.merge(gridSaveExecutor.delete(
                request.getDeletedIds(),
                menuRepository,
                Function.identity(),
                AdminMenu::getId,
                "존재하지 않는 메뉴가 포함되어 있습니다."));
    }

    if (hasRows(request.getCreatedRows())) {
        result = result.merge(gridSaveExecutor.create(
                request.getCreatedRows(),
                menuRepository,
                MenuGridRow::getId,
                AdminMenu::getId,
                menuMapper::toEntity));
    }

    if (hasRows(request.getUpdatedRows())) {
        result = result.merge(gridSaveExecutor.update(
                request.getUpdatedRows(),
                menuRepository,
                MenuGridRow::getId,
                AdminMenu::getId,
                menuMapper::updateEntity,
                "존재하지 않는 메뉴가 포함되어 있습니다."));
    }

    return MenuGridSaveResponse.builder()
            .createdCount(result.getCreatedCount())
            .updatedCount(result.getUpdatedCount())
            .deletedCount(result.getDeletedCount())
            .messages(result.getMessages())
            .build();
}
```

Service는 저장 순서와 트랜잭션 경계를 표현하고, DTO 변환 세부 로직은 mapper에 맡깁니다.

### Common Grid

공통 grid 저장 클래스는 도메인 이름을 붙이지 않습니다.

```text
GridSaveExecutor
GridSaveResult
```

공통 클래스의 책임:

- null row 그룹 skip
- row 그룹 내부 null 검증
- 등록 key 중복/null 검증
- 삭제 key 중복/null 검증
- 수정 row key 중복/null 검증
- 등록/수정/삭제 key 충돌 검증
- 삭제/수정 key 충돌 검증
- 삭제 대상 존재 확인
- 이미 등록된 key skip 및 메시지 반환
- 신규 등록 저장
- 수정 대상 존재 확인
- 삭제, 등록, 수정 count 반환

저장 실패 처리 방식:

- `GridSaveFailureMode.SKIP_AND_MESSAGE`: 이미 등록된 등록 row, 존재하지 않는 수정/삭제 key를 예외로 보지 않고 건너뛰며 `GridSaveMessage`로 반환합니다.
- `GridSaveFailureMode.STRICT_EXCEPTION`: DB 상태와 맞지 않는 row가 있으면 예외를 던져 전체 저장을 중단합니다.
- 요청 자체가 이상한 경우(null, 중복, 등록/수정/삭제 key 충돌)는 두 방식 모두 400 예외로 처리합니다.

공통 grid 저장은 단일 ID와 `@EmbeddedId` 같은 복합 ID를 모두 같은 방식으로 처리합니다. 핵심은 각 row와 entity에서 비교 가능한 key 객체를 만들어 넘기는 것입니다.

단일 ID create 예시:

```java
gridSaveExecutor.create(
        request.getCreatedRows(),
        menuRepository,
        MenuGridRow::getId,
        AdminMenu::getId,
        menuMapper::toEntity);
```

단일 ID update 엄격 예외 모드 예시:

```java
gridSaveExecutor.update(
        request.getUpdatedRows(),
        menuRepository,
        MenuGridRow::getId,
        AdminMenu::getId,
        menuMapper::updateEntity,
        "존재하지 않는 메뉴가 포함되어 있습니다.",
        GridSaveFailureMode.STRICT_EXCEPTION);
```

복합 ID delete 예시:

```java
gridSaveExecutor.delete(
        request.getDeletedIds(),
        userRoleRepository,
        row -> new AdminUserRoleId(row.getUserId(), row.getRoleId()),
        AdminUserRole::getId,
        "존재하지 않는 사용자 권한 매핑이 포함되어 있습니다.");
```

3개 이상의 key 필드를 가진 `@EmbeddedId` 예시는 [Grid Save 복합 ID 예제](docs/old/grid-save-composite-id-example.md)를 참고합니다.

공통 클래스가 알지 않아야 하는 것:

- 도메인별 필드 이름
- DTO와 Entity의 상세 변환 규칙
- Entity 상태 변경 메서드의 내부 내용
- 응답 DTO의 최종 shape

## ag-Grid 저장 방식 선택지

### 방식 A. 상태별 배열 분리

```json
{
  "createdRows": [],
  "updatedRows": [],
  "deletedIds": []
}
```

현재 적용한 방식입니다.

- 장점: 백엔드 처리 순서가 명확하고 검증이 쉽습니다.
- 장점: 삭제 row는 전체 데이터를 다시 보내지 않아도 됩니다.
- 단점: 프론트엔드가 row 상태를 정확히 관리해야 합니다.
- 추천: 관리자 grid에서 등록, 수정, 삭제가 한 번에 저장되는 일반적인 경우.

### 방식 B. 단일 배열과 row state

```json
{
  "rows": [
    { "state": "CREATED" },
    { "state": "UPDATED" },
    { "state": "DELETED" }
  ]
}
```

- 장점: 요청 배열이 하나라 화면 모델과 맞추기 쉽습니다.
- 장점: row 순서를 그대로 유지할 수 있습니다.
- 단점: 삭제 row에도 불필요한 필드가 섞이기 쉽습니다.
- 추천: row 순서 자체가 업무 의미를 갖는 grid.

### 방식 C. 전체 스냅샷 저장

```json
{
  "rows": []
}
```

백엔드가 기존 DB 상태와 요청 전체를 비교해 생성, 수정, 삭제를 계산합니다.

- 장점: 프론트엔드 구현이 단순합니다.
- 장점: 서버가 최종 상태를 책임집니다.
- 단점: 데이터가 많으면 요청과 비교 비용이 커집니다.
- 단점: 동시 수정 충돌 처리가 중요합니다.
- 추천: row 수가 작고 최종 상태 동기화가 더 중요한 설정 화면.

### 방식 D. command 목록

```json
{
  "commands": [
    { "type": "CREATE" },
    { "type": "UPDATE" },
    { "type": "DELETE" }
  ]
}
```

- 장점: 처리 순서와 사용자 의도가 가장 명확합니다.
- 장점: 일부 command만 재시도하거나 로그로 남기기 좋습니다.
- 단점: DTO와 검증 구조가 복잡합니다.
- 추천: 업무 이력, 승인, 재처리 같은 command 추적이 중요한 화면.

처음에는 방식 A를 추천합니다. ag-Grid의 변경 row 모델과 잘 맞고, JPA 저장 흐름도 가장 단순합니다.

## 저장 성능 메모

JPA write 기준으로 시작합니다.

- 소량 저장: managed entity 조회 후 상태 변경, `saveAll`.
- 같은 값으로 다건 수정: JPQL bulk update를 검토합니다.
- 대량 등록: Hibernate JDBC batch 설정, ID 전략, flush/clear 주기를 함께 봅니다.
- 대량 삭제: cascade, orphanRemoval, soft delete 정책을 먼저 확인합니다.

JPQL bulk update/delete는 영속성 컨텍스트를 우회합니다. 이미 조회된 Entity와 DB 값이 달라질 수 있으므로 `clearAutomatically`, `flushAutomatically` 같은 정리 전략을 같이 검토합니다.

## Auditing

JPA는 `AuditEntity`와 `@EnableJpaAuditing`으로 등록자, 수정자, 등록일, 수정일을 채웁니다.

auditor 선택 순서는 다음과 같습니다.

1. Spring Security 인증 username
2. 요청 헤더 `X-User-Id`
3. `system`

쓰기 작업은 JPA로 통일했기 때문에 MyBatis XML에 auditor를 넘겨 insert/update 하는 방식은 더 이상 사용하지 않습니다.

## 최종 확인 명령

```powershell
rg -n "public record| record " src\main\java
rg -n "<insert|<update|<delete" src\main\resources\mybatis
rg -n "<insert|<update|<delete" src\main\resources\mybatis
.\gradlew test
```

기대 결과:

- main Java source에 `record`가 없어야 합니다.
- MyBatis XML에 쓰기 SQL이 없어야 합니다.
- MyBatis controller/service는 조회 전용이어야 합니다.
- 전체 테스트가 통과해야 합니다.
