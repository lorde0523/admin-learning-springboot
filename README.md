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
