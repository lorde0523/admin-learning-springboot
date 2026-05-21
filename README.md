# Admin Learning

Java 21, Gradle, Oracle, Spring MVC, JPA, MyBatis를 한 프로젝트에서 비교하는 관리자 학습용 예제입니다.  
도메인은 사용자, 역할, 메뉴이고 관계는 사용자-역할과 역할-메뉴까지 포함합니다.

## 1. 실행 준비

1. Java 21과 Gradle을 준비합니다.
2. Oracle에 예제 스키마 계정을 만듭니다.
3. [`database/oracle/schema.sql`](database/oracle/schema.sql)을 실행합니다.
4. [`database/oracle/data.sql`](database/oracle/data.sql)을 실행합니다.
5. Oracle 접속 정보를 환경 변수로 넘깁니다.

```powershell
$env:ORACLE_URL='jdbc:oracle:thin:@localhost:1521/FREEPDB1'
$env:ORACLE_USERNAME='admin_learning'
$env:ORACLE_PASSWORD='admin_learning'
.\gradlew bootRun
```

Swagger UI는 `http://localhost:8080/swagger-ui.html`에서 확인합니다.

## 2. 패키지 구조

```text
com.example.admin
|- common : auditing, security, exception, OpenAPI, MyBatis config
|- user   : 사용자 API, JPA repository, MyBatis mapper
|- role   : 역할 API, 역할-메뉴 권한 매핑
`- menu   : 메뉴 API, 계층 메뉴, bulk insert
```

각 기능 패키지 안에서 `api`, `service`, `dto`, `entity`, `repository`, `mapper`를 나눕니다.  
새 관리자 기능이 생기면 `project2` 같은 Gradle 모듈보다 기능 패키지를 추가하는 방식으로 확장합니다.

## 3. 실습 순서

1. `/api/jpa/users`와 `/api/mybatis/users`로 같은 사용자 CRUD를 비교합니다.
2. 사용자 검색에서 대소문자 무시 검색을 확인합니다.
   - JPA: [`AdminUserRepository`](src/main/java/com/example/admin/user/repository/AdminUserRepository.java)의 `UPPER()` native query
   - MyBatis: [`AdminUserMapper.xml`](src/main/resources/mybatis/AdminUserMapper.xml)의 SQL
3. 사용자에게 역할을 부여합니다.
   - `PUT /api/jpa/users/{id}/roles`
   - `PUT /api/mybatis/users/{id}/roles`
4. 메뉴를 만들고 역할에 메뉴 접근 권한을 연결합니다.
   - `PUT /api/jpa/roles/{id}/menus`
   - `PUT /api/mybatis/roles/{id}/menus`
5. MyBatis bulk insert를 실행합니다.
   - `POST /api/mybatis/menus/bulk`
6. `X-User-Id` 헤더를 바꿔 등록자/수정자 값이 어떻게 들어가는지 확인합니다.
7. Spring Security 인증을 붙인 요청에서는 SecurityContext의 username이 헤더보다 우선하는지 확인합니다.

## 4. API 트랙

| 기능 | JPA | MyBatis |
| --- | --- | --- |
| 사용자 CRUD | `/api/jpa/users` | `/api/mybatis/users` |
| 역할 CRUD | `/api/jpa/roles` | `/api/mybatis/roles` |
| 메뉴 CRUD | `/api/jpa/menus` | `/api/mybatis/menus` |
| 사용자-역할 | `PUT /api/jpa/users/{id}/roles` | `PUT /api/mybatis/users/{id}/roles` |
| 역할-메뉴 | `GET`, `PUT /api/jpa/roles/{id}/menus` | `GET`, `PUT /api/mybatis/roles/{id}/menus` |
| 메뉴 bulk insert | 비교 대상 아님 | `POST /api/mybatis/menus/bulk` |

요청과 응답은 엔티티를 직접 노출하지 않고 DTO를 사용합니다. Validation 실패는 `VALIDATION_ERROR`, 없는 리소스는 `NOT_FOUND` 공통 오류 응답으로 내려갑니다.

## 5. Auditing 비교

JPA는 [`AuditEntity`](src/main/java/com/example/admin/common/audit/AuditEntity.java)와 `@EnableJpaAuditing`으로 등록자, 수정자, 등록일, 수정일을 채웁니다.

`CurrentAuditorAware`의 사용자 선택 순서는 다음과 같습니다.

1. Spring Security 인증 username
2. 요청 헤더 `X-User-Id`
3. `system`

MyBatis는 엔티티 리스너가 없으므로 서비스가 현재 auditor를 읽어 XML Mapper의 insert/update 파라미터로 넘깁니다. 이 차이가 JPA auditing과 SQL 매퍼 방식의 핵심 비교 지점입니다.

## 6. 스키마 방식 비교

기본은 직접 실행하는 Oracle SQL입니다.

- SQL 기본: `database/oracle/schema.sql`, `database/oracle/data.sql`
- JPA DDL 실습: `--spring.profiles.active=jpa-ddl`
- Flyway 실습: `--spring.profiles.active=flyway`

JPA DDL 프로필은 버려도 되는 학습 스키마에서만 사용합니다. Flyway 프로필은 `src/main/resources/db/migration`의 V1, V2 스크립트를 사용합니다.

## 7. Bulk insert 메모

Oracle용 MyBatis bulk insert는 [`AdminMenuMapper.xml`](src/main/resources/mybatis/AdminMenuMapper.xml)에 `INSERT ALL`로 둡니다.  
자동 테스트는 H2에서 돌기 때문에 MyBatis `databaseId` 설정으로 H2 전용 multi-values SQL도 함께 둡니다. 같은 서비스 API가 DB별 SQL 차이를 어떻게 감추는지 보는 예제입니다.

## 8. 테스트

```powershell
.\gradlew test
```

테스트는 H2 Oracle mode를 사용합니다. Oracle 실접속은 기본 실행 설정과 SQL 스크립트 적용으로 확인합니다.

## 9. DTO record 어노테이션 메모

이 프로젝트의 DTO는 Java `record`를 기본으로 사용합니다. `record`는 생성자, 값 접근자, `equals`, `hashCode`, `toString`을 기본으로 제공하므로 단순 요청/응답 DTO에 잘 맞습니다.

```java
public record UserRequest(
        @NotBlank String loginId,
        @NotBlank String name,
        @NotNull Boolean enabled) {
}
```

`record` DTO에서 자주 보는 어노테이션은 다음과 같습니다.

| 어노테이션 | 주로 쓰는 곳 | 의미 |
| --- | --- | --- |
| `@NotNull` | 숫자, Boolean, 객체 | `null`이면 안 될 때 사용합니다. 빈 문자열은 막지 않습니다. |
| `@NotBlank` | `String` | `null`, `""`, 공백 문자열을 모두 막습니다. 이름, 코드, 로그인 ID에 자주 씁니다. |
| `@NotEmpty` | 컬렉션, 배열, 문자열 | 값이 비어 있으면 안 될 때 사용합니다. ID 목록 요청에 자주 씁니다. |
| `@Size` | 문자열, 컬렉션 | 최소/최대 길이 또는 개수를 제한합니다. |
| `@Email` | `String` | 이메일 형태를 검사합니다. |
| `@Pattern` | `String` | 정규식 규칙을 적용합니다. 코드 형식, 전화번호 형식 등에 씁니다. |
| `@Positive` | 숫자 | `0`보다 커야 할 때 사용합니다. |
| `@PositiveOrZero` | 숫자 | `0` 이상이어야 할 때 사용합니다. 정렬 순서에 쓸 수 있습니다. |
| `@Min`, `@Max` | 숫자 | 허용 숫자 범위를 제한합니다. |
| `@Valid` | 중첩 DTO | DTO 안의 DTO나 컬렉션 원소 검증까지 이어서 실행합니다. |

예를 들어 bulk insert 요청은 목록 자체도 비어 있으면 안 되고, 목록 안의 메뉴 요청도 각각 검증해야 합니다.

```java
public record MenuBulkRequest(
        @NotEmpty List<@Valid MenuRequest> menus) {
}
```

문자열 길이와 형식을 같이 제한할 수도 있습니다.

```java
public record RoleRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Z0-9_]+$")
        String roleCode,

        @NotBlank
        @Size(max = 100)
        String roleName,

        @NotNull Boolean enabled) {
}
```

Swagger 설명을 DTO에 더 붙이고 싶을 때는 `springdoc-openapi`가 읽는 `@Schema`를 사용할 수 있습니다.

```java
public record UserSearchRequest(
        @Schema(description = "대소문자 무시 로그인 ID 검색어", example = "admin")
        String loginKeyword) {
}
```

JSON 필드 이름을 Java 이름과 다르게 받고 싶을 때는 Jackson `@JsonProperty`를 사용할 수 있습니다. 다만 API 필드 이름을 자주 바꾸면 DTO를 읽기 어려워지므로 필요한 경우에만 씁니다.

```java
public record UserResponse(
        @JsonProperty("userId") Long id,
        String loginId) {
}
```

검증 어노테이션을 붙여도 Controller에서 `@Valid`를 빼면 요청 검증이 실행되지 않습니다.

```java
@PostMapping
public UserResponse create(@Valid @RequestBody UserRequest request) {
    return userService.create(request);
}
```

## 10. Lombok 어노테이션 메모

Lombok은 반복 코드를 줄일 때 유용하지만, 엔티티와 DTO에서 무조건 같은 방식으로 쓰지는 않습니다.  
이 프로젝트는 DTO는 `record`, JPA 엔티티는 필요한 Lombok만 선택해서 사용합니다.

| 어노테이션 | 언제 쓰나 | 주의점 |
| --- | --- | --- |
| `@Getter` | 읽기 접근자가 필요한 class | 엔티티에서 비교적 안전하게 자주 씁니다. |
| `@Setter` | 외부에서 값을 직접 바꿔야 할 때 | 엔티티 전체에 붙이면 상태 변경 지점이 흐려질 수 있습니다. |
| `@Data` | 값 객체에 getter, setter, `equals`, `hashCode`, `toString`을 한 번에 줄 때 | JPA 엔티티에는 보통 피합니다. 연관관계와 식별자 때문에 의도치 않은 비교/출력이 생길 수 있습니다. |
| `@Builder` | 생성 인자가 많거나 테스트/응답 객체 조립이 많을 때 | 필수값이 빠져도 빌드될 수 있으니 검증 책임을 같이 봅니다. |
| `@NoArgsConstructor` | 프레임워크가 기본 생성자를 요구할 때 | JPA 엔티티는 보통 `PROTECTED` 접근 수준으로 둡니다. |
| `@AllArgsConstructor` | 모든 필드를 받는 생성자가 필요할 때 | 엔티티에서 공개 생성자로 남발하지 않습니다. |
| `@RequiredArgsConstructor` | `final` 필드 생성자 주입 | Spring service/controller 생성자 주입에 자주 씁니다. |
| `@ToString` | 로그/디버깅 출력이 필요할 때 | 연관관계 필드는 제외하지 않으면 순환 참조나 과한 출력이 생길 수 있습니다. |
| `@EqualsAndHashCode` | 값 비교 규칙을 명시할 때 | JPA 엔티티는 ID 생성 시점 문제를 고려해야 합니다. |
| `@Slf4j` | 클래스 안에서 logger가 필요할 때 | Log4j2를 쓰더라도 facade로 SLF4J logger를 쓸 수 있습니다. |

가장 단순한 class DTO는 다음처럼 만들 수 있습니다.

```java
@Getter
@Setter
public class UserSearchCondition {
    private String loginKeyword;
    private Boolean enabled;
}
```

생성할 때 읽기 쉬운 객체가 필요하면 `@Builder`를 붙일 수 있습니다.

```java
@Getter
@Builder
public class UserSummary {
    private final Long id;
    private final String loginId;
    private final String name;
}
```

Spring service에서 생성자 주입 반복을 줄일 때는 `@RequiredArgsConstructor`가 편합니다.

```java
@Service
@RequiredArgsConstructor
public class UserQueryService {
    private final AdminUserRepository userRepository;
}
```

JPA 엔티티는 모든 필드에 setter를 열기보다 의미 있는 메서드로 상태를 바꾸는 편이 좋습니다.

```java
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminUser {
    private String name;
    private boolean enabled;

    public void updateProfile(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }
}
```

정리하면 다음 기준으로 시작하면 됩니다.

- 요청/응답 DTO가 단순한 값 전달이면 `record`를 먼저 고려합니다.
- DTO에 선택 필드가 많고 조립 코드가 길어지면 class + `@Builder`를 검토합니다.
- JPA 엔티티는 `@Getter`, `@NoArgsConstructor(access = PROTECTED)` 정도부터 시작하고 `@Setter`, `@Data`는 이유가 있을 때만 씁니다.
- service/controller 생성자 주입 반복은 `@RequiredArgsConstructor`로 줄일 수 있습니다.

## 11. Bulk insert, update, delete 메모

관리자 화면에서는 여러 행을 한 번에 처리하는 기능이 자주 나옵니다.

- 메뉴 여러 건 등록
- 사용자 여러 명 사용 여부 변경
- 권한 매핑 여러 건 등록/삭제
- 선택한 메뉴 여러 건 삭제

이런 기능은 반복 API 호출보다 bulk API 하나로 묶는 편이 트랜잭션과 실패 처리를 설명하기 쉽습니다. 다만 무조건 bulk SQL이 더 좋은 것은 아닙니다. 처리 건수가 작고 엔티티 이벤트나 검증 로직이 중요하면 일반 CRUD 반복이 더 명확할 수 있습니다.

### 11.1 공통 API와 DTO 형태

처음에는 bulk 결과를 "몇 건 처리했는지" 중심으로 반환하면 다루기 쉽습니다.

```java
public record BulkIdsRequest(
        @NotEmpty Set<Long> ids) {
}

public record BulkEnabledUpdateRequest(
        @NotEmpty Set<Long> ids,
        @NotNull Boolean enabled) {
}

public record BulkResultResponse(
        int affectedCount) {
}
```

API 이름도 기능이 드러나게 둡니다.

```text
POST   /api/mybatis/menus/bulk
PATCH  /api/jpa/users/bulk/enabled
PATCH  /api/mybatis/users/bulk/enabled
DELETE /api/jpa/menus/bulk
DELETE /api/mybatis/menus/bulk
```

bulk 요청을 받을 때 서비스 계층에서 먼저 볼 것은 다음입니다.

1. 요청 ID나 요청 목록이 비어 있지 않은지 검증합니다.
2. 처리 대상 건수가 너무 크면 제한하거나 chunk로 나눌지 결정합니다.
3. 등록자/수정자 같은 감사값을 한 번 확보해 SQL 또는 엔티티 생성에 반영합니다.
4. 트랜잭션 경계를 bulk 작업 전체로 둘지 chunk 단위로 둘지 정합니다.
5. 요청 건수와 실제 처리 건수가 다를 때 성공, 부분 성공, 실패 중 어떤 정책인지 정합니다.

처음 학습할 때는 다음 기본값이 무난합니다.

- 하나의 bulk 요청은 하나의 트랜잭션으로 처리합니다.
- 요청한 ID 일부가 없으면 예외로 실패시킵니다.
- 응답은 `affectedCount`를 반환합니다.
- 매우 큰 파일 업로드성 대량 처리는 별도 배치나 chunk 처리 주제로 분리합니다.

### 11.2 Bulk insert

#### JPA에서 insert

JPA에서 여러 엔티티를 저장할 때는 `saveAll`로 시작할 수 있습니다.

```java
@Transactional
public BulkResultResponse createMenus(List<MenuRequest> requests) {
    List<AdminMenu> menus = requests.stream()
            .map(request -> AdminMenu.create(
                    request.menuCode(),
                    request.menuName(),
                    request.parentMenuId(),
                    request.sortOrder(),
                    request.enabled()))
            .toList();

    menuRepository.saveAll(menus);
    return new BulkResultResponse(menus.size());
}
```

이 방식은 엔티티 생성 규칙과 JPA auditing을 그대로 타기 쉽습니다. 다만 대량 insert 성능을 높이려면 ID 전략, JDBC batch 설정, flush/clear 시점까지 함께 봐야 합니다.

JPA insert 메모:

- `saveAll`은 "한 SQL"을 보장하는 말이 아니라 "여러 엔티티 저장 요청"입니다.
- Oracle sequence와 Hibernate batch 동작은 설정과 ID 전략 영향을 받습니다.
- 수천, 수만 건 이상이면 메모리 사용량과 flush 주기를 확인합니다.

#### MyBatis에서 insert

MyBatis는 SQL 모양을 직접 보여 주기 좋아 bulk insert 학습에 잘 맞습니다. Oracle에서는 이 프로젝트처럼 `INSERT ALL` 패턴을 예시로 둘 수 있습니다.

```xml
<insert id="bulkInsert" databaseId="oracle">
    INSERT ALL
    <foreach collection="menus" item="menu">
        INTO ADMIN_MENU (
            MENU_ID, MENU_CODE, MENU_NAME, SORT_ORDER,
            CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT
        ) VALUES (
            #{menu.id}, #{menu.menuCode}, #{menu.menuName}, #{menu.sortOrder},
            #{auditor}, SYSTIMESTAMP, #{auditor}, SYSTIMESTAMP
        )
    </foreach>
    SELECT 1 FROM DUAL
</insert>
```

MyBatis insert 메모:

- Oracle SQL과 테스트 DB SQL이 다를 수 있으므로 `databaseId` 분리가 유용합니다.
- sequence ID를 여러 건 만들 때 ID 발급 방식과 MyBatis 캐시 동작을 확인합니다.
- 입력값마다 감사값이 같다면 서비스에서 auditor를 한 번 구해 Mapper 파라미터로 넘깁니다.

### 11.3 Bulk update

#### JPA에서 update

여러 행을 같은 값으로 바꾸는 bulk update는 JPQL update가 자주 쓰입니다.

```java
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AdminUser user
               set user.enabled = :enabled
             where user.id in :ids
            """)
    int updateEnabledByIds(@Param("ids") Set<Long> ids, @Param("enabled") boolean enabled);
}
```

```java
@Transactional
public BulkResultResponse updateEnabled(BulkEnabledUpdateRequest request) {
    int updatedCount = userRepository.updateEnabledByIds(request.ids(), request.enabled());
    return new BulkResultResponse(updatedCount);
}
```

JPA bulk update는 엔티티를 하나씩 수정하는 방식과 다릅니다.

- 영속성 컨텍스트를 건너뛰고 DB에 바로 update SQL을 실행합니다.
- 이미 조회해 둔 엔티티 값과 DB 값이 달라질 수 있어 `clearAutomatically` 같은 정리 전략이 중요합니다.
- JPA auditing의 `@LastModifiedDate`, `@LastModifiedBy`가 자동으로 기대한 방식대로 갱신되지 않을 수 있습니다.
- 감사 컬럼까지 바꿔야 하면 query에 `updatedBy`, `updatedAt` 갱신을 명시하는 편이 분명합니다.

#### MyBatis에서 update

MyBatis는 SQL에 감사 컬럼까지 같이 쓰면 흐름이 명확합니다.

```xml
<update id="bulkUpdateEnabled">
    UPDATE ADMIN_USER
       SET ENABLED = #{enabled},
           UPDATED_BY = #{auditor},
           UPDATED_AT = SYSTIMESTAMP
     WHERE USER_ID IN
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</update>
```

MyBatis update 메모:

- 같은 값으로 여러 행을 바꿀 때는 `WHERE IN` bulk update가 단순합니다.
- 행마다 다른 값으로 바꾸려면 `CASE WHEN` SQL, 임시 테이블, 반복 batch 실행 중 무엇이 맞는지 따로 판단합니다.
- Oracle `IN` 목록이 지나치게 커지면 chunk 분리나 다른 적재 방식을 검토합니다.

### 11.4 Bulk delete

#### JPA에서 delete

조건 기반 bulk delete도 JPQL로 만들 수 있습니다.

```java
public interface AdminMenuRepository extends JpaRepository<AdminMenu, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AdminMenu menu where menu.id in :ids")
    int deleteByIds(@Param("ids") Set<Long> ids);
}
```

```java
@Transactional
public BulkResultResponse deleteMenus(BulkIdsRequest request) {
    int deletedCount = menuRepository.deleteByIds(request.ids());
    return new BulkResultResponse(deletedCount);
}
```

JPA delete 메모:

- bulk delete도 엔티티를 하나씩 지우는 흐름이 아닙니다.
- 엔티티 remove 이벤트, cascade 처리 기대, soft delete 정책이 있으면 먼저 확인합니다.
- 관계 테이블 FK가 있으면 삭제 순서나 매핑 정리가 필요할 수 있습니다.

#### MyBatis에서 delete

MyBatis에서는 삭제 대상 SQL이 바로 보입니다.

```xml
<delete id="bulkDelete">
    DELETE FROM ADMIN_MENU
     WHERE MENU_ID IN
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</delete>
```

관리자 시스템에서는 실제 삭제보다 사용 여부 변경이나 soft delete를 선택하는 경우도 많습니다. 감사 추적이 중요하면 `DELETE`보다 `ENABLED = 0`, `DELETED_AT`, `DELETED_BY` 같은 정책이 더 맞을 수 있습니다.

### 11.5 JPA와 MyBatis 선택 기준

| 상황 | 먼저 볼 방식 |
| --- | --- |
| 엔티티 생성 규칙과 auditing을 그대로 타는 여러 건 등록 | JPA `saveAll` |
| Oracle SQL을 직접 제어해야 하는 대량 insert | MyBatis bulk insert |
| 여러 행을 같은 값으로 빠르게 변경 | JPA JPQL bulk update 또는 MyBatis update |
| 감사 컬럼을 SQL에서 명확히 제어 | MyBatis update/delete 또는 JPA query에 감사 컬럼 명시 |
| 삭제 cascade, 도메인 이벤트, soft delete 정책이 중요 | bulk delete 전에 도메인 정책부터 확인 |

처음 구현할 때는 "몇 건인지", "각 행 값이 같은지", "엔티티 생명주기 로직이 필요한지", "감사값을 어디서 채울지" 네 가지를 먼저 보면 JPA와 MyBatis 중 어느 쪽 예제가 더 자연스러운지 판단하기 쉽습니다.
