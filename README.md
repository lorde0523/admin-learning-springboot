# Admin Learning

Java 21, Spring Boot, Oracle, JPA, MyBatis 3.x를 함께 다루는 관리자 예제입니다.  
현재 구조의 기준은 명확합니다.

- 등록, 수정, 삭제, 관계 매핑 저장은 JPA로 처리합니다.
- 단순 조회도 우선 JPA로 처리합니다.
- MyBatis는 SQL로 표현하는 편이 더 명확한 복잡 조회에만 사용합니다.
- QueryDSL은 사용하지 않습니다.
- Entity와 DTO는 `record`를 사용하지 않고 Lombok class로 작성합니다.

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
|- user   : 사용자 API, JPA repository, query-only MyBatis mapper
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
| 메뉴 등록/수정/삭제 | `/api/jpa/menus` | JPA |
| 메뉴 ag-Grid 저장 | `POST /api/jpa/menus/grid-save` | JPA |
| 복잡 조회 | MyBatis mapper | MyBatis select only |

MyBatis의 기존 쓰기 API와 쓰기 SQL은 제거했습니다. `src/main/resources/mybatis`에는 조회 SQL만 남아야 합니다.

## DTO와 Entity 작성 기준

이 프로젝트는 Entity와 DTO 모두 Lombok class를 사용합니다.

- Entity와 DTO에 Java `record`를 사용하지 않습니다.
- JPA Entity에는 `@Data`를 사용하지 않습니다.
- JPA Entity는 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 정적 팩토리 메서드, 의미 있는 상태 변경 메서드를 기본으로 둡니다.
- Request DTO는 JSON 바인딩을 위해 `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`를 사용합니다.
- Response DTO는 `@Getter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`를 우선 사용합니다.
- MyBatis mapper projection도 `record` 대신 Lombok class로 작성합니다.

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
2. `updatedRows.id`의 null, 중복, 삭제 대상과의 충돌을 검증합니다.
3. 삭제 대상은 JPA로 삭제합니다.
4. 등록 row는 Entity로 만들어 `saveAll` 합니다.
5. 수정 row는 id로 Entity를 조회한 뒤 managed entity의 상태 변경 메서드를 호출합니다.
6. 처리 건수를 응답합니다.

이 방식은 ag-Grid의 delta 모델과 잘 맞고, JPA auditing과 entity lifecycle을 그대로 사용할 수 있습니다.

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
rg -n "MyBatisAdmin.*Service|MyBatisAdmin.*Controller|/api/mybatis" src
.\gradlew test
```

기대 결과:

- main Java source에 `record`가 없어야 합니다.
- MyBatis XML에 쓰기 SQL이 없어야 합니다.
- MyBatis 쓰기 Service, Controller, `/api/mybatis` endpoint가 없어야 합니다.
- 전체 테스트가 통과해야 합니다.
