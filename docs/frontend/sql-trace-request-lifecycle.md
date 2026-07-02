# 탭 기반 SQL 추적 요청 생명주기

## 목적

탭 화면에서 발생한 API 요청을 사용자와 화면 단위로 구분하고, 해당 요청이 실행한
SELECT SQL과 서버·클라이언트 처리시간을 Redis에 저장한 뒤 조회하는 전체 흐름을
현재 프로젝트 코드 기준으로 설명한다.

## 전체 흐름

```text
애플리케이션 시작
  → DataSource를 SQL 추적 프록시로 래핑

사용자 화면 이동
  → Zustand에 탭과 활성 탭 저장
  → React Query가 GET API 호출
  → Axios가 활성 탭 메타데이터를 헤더에 추가
  → Spring Security가 사용자 인증
  → SQL 추적 Filter가 요청 컨텍스트 생성
  → Controller → Service → Repository 실행
  → JDBC PreparedStatement가 SQL 파라미터 수집
  → SELECT 실행 및 시간 측정
  → 완성 SQL을 Redis List에 저장
  → Filter가 서버 처리시간을 Redis 데이터에 추가
  → 프런트가 렌더링 완료 후 클라이언트 시간을 전송
  → Redis 데이터에 클라이언트·전체 시간 추가
  → SQL 로그 조회 API로 화면에 표시
```

## 1. SQL 추적 기능 활성화

`src/main/resources/application.yml`에서 기능을 활성화한다.

```yaml
admin:
  sql-trace:
    enabled: ${SQL_TRACE_ENABLED:false}
```

기본값은 `false`이므로 실행 환경에 다음 값이 필요하다.

```text
SQL_TRACE_ENABLED=true
```

활성화되면 `SqlTraceConfiguration`에서 다음 Bean을 생성한다.

- `StringRedisStore`
- `RedisSqlTraceStore`
- `SqlParameterRenderer`
- `SqlTraceRecorder`
- `SqlCaptureRequestFilter`
- `SqlTraceDataSourceBeanPostProcessor`

## 2. DataSource 프록시 설치

`SqlTraceDataSourceBeanPostProcessor`는 기존 `DataSource` Bean을
`SqlTraceDataSource`로 감싼다.

```java
if (bean instanceof DataSource dataSource
        && !(dataSource instanceof SqlTraceDataSource)) {
    return new SqlTraceDataSource(dataSource, recorderProvider.getObject());
}
```

따라서 JPA와 MyBatis 코드를 수정하지 않아도 JDBC 호출을 공통으로 추적할 수 있다.

```text
JPA / MyBatis
  → SqlTraceDataSource
  → 실제 Oracle DataSource
```

## 3. 탭 생성 및 활성화

`AppLayout`은 현재 URL에 대응하는 화면 정보를 조회해 Zustand의 `openTab()`을
호출한다.

```javascript
const screen = getWorkspaceScreen(location.pathname);
if (screen) openTab(screen);
```

화면 정보는 다음 형태다.

```javascript
{
  id: 'users',
  title: '사용자',
  path: '/users',
  uiId: 'users-workbench'
}
```

`useTabStore.openTab()`은 같은 `path`의 탭이 존재하면 해당 탭을 활성화한다.
존재하지 않으면 `tabs`에 추가하고 `activeTabId`를 갱신한다.

현재 `uiId`는 탭 인스턴스 ID가 아니라 화면 ID에 가깝다. 동일 화면을 여러 탭으로
열어야 하는 현업 구조에서는 다음 값을 분리해야 한다.

```text
screenId = users-workbench
tabId    = 탭 생성 시 발급한 UUID
```

## 4. Axios 요청 인터셉터

모든 프런트 API 호출은 `frontend/src/api/httpClient.js`의 Axios 인스턴스를 사용한다.

인터셉터는 먼저 요청 시작 시간을 저장한다.

```javascript
config.requestStartedAt = now();
```

현재 구현은 GET 요청만 추적한다.

```javascript
if (config.method?.toLowerCase() !== 'get') {
  return config;
}
```

GET 요청이면 Zustand에서 현재 활성 탭을 조회하고 헤더를 추가한다.

```javascript
const uiId = getActiveTab()?.uiId ?? getUiId(getPathname());

config.headers.set('X-Trace-Type', 'query');
config.headers.set('X-Ui-Id', uiId);
```

전송되는 요청 예시는 다음과 같다.

```http
GET /api/jpa/users
X-Trace-Type: query
X-Ui-Id: users-workbench
```

SQL 로그 조회 요청에는 재귀 추적 방지를 위해 다음 헤더도 추가한다.

```http
X-Sql-Capture-Paused: true
```

## 5. 인증과 필터 실행 순서

권장 실행 순서는 다음과 같다.

```text
Spring Security 인증 필터
  → SecurityContext에 인증 사용자 저장
  → SqlCaptureRequestFilter
  → DispatcherServlet
  → Controller
```

별도 사용자 attribute 필터를 사용하는 경우에는 다음 순서가 필요하다.

```text
Spring Security
  → LoginUserRequestFilter
  → SqlCaptureRequestFilter
  → Controller
```

핵심 불변식은 `SqlCaptureRequestFilter`가 실행되기 전에 인증 사용자 정보가
확정되어 있어야 한다는 것이다.

현재 `SqlTraceUserId`는 다음 요청 attribute만 확인한다.

```java
request.getAttribute("USER_ID")
```

테스트에서는 이 값을 직접 설정하지만 운영 코드에는 해당 attribute를 생성하는 필터가
없다. 운영 적용 시 다음 중 하나를 선택해야 한다.

1. 인증 필터 다음에 `USER_ID` attribute를 설정하는 필터를 등록한다.
2. 권장 방식으로 `LoginUsers.currentUsername()`을 사용해 `SecurityContext`에서 직접
   사용자 ID를 조회한다.

두 번째 방식은 별도 attribute와 필터 순서 의존성을 줄인다.

## 6. SQL 추적 Filter 검증

`SqlCaptureRequestFilter`는 다음 순서로 요청을 검사한다.

### 6.1 추적 대상 요청 확인

다음 요청만 추적한다.

- HTTP 메서드가 GET
- 경로가 `/api/sql-logs`가 아님

조건을 만족하지 않으면 API 요청은 정상 실행하지만 SQL 추적은 하지 않는다.

### 6.2 Trace Type 검증

`X-Trace-Type` 헤더를 `SqlTraceType`으로 변환한다. 현재 허용값은 `query` 하나다.
헤더가 없거나 잘못되면 요청은 정상 실행하고 추적만 생략한다.

### 6.3 UI ID 검증

`X-Ui-Id`는 다음 정규식을 만족해야 한다.

```regex
[A-Za-z0-9._-]{1,100}
```

### 6.4 사용자 확인

`SqlTraceUserId.resolve(request)`로 사용자 ID를 찾는다. 사용자 ID가 없으면 요청은
정상 실행하고 추적만 생략한다.

### 6.5 일시정지 헤더 검증

`X-Sql-Capture-Paused`는 미지정, `true`, `false`만 허용한다. 다른 값이면
400 응답을 반환한다.

## 7. 요청 컨텍스트 생성

검증이 완료되면 Filter는 API 시작 시각과 서버 타이머를 생성한다.

```java
OffsetDateTime apiStartedAt = OffsetDateTime.now(clock);
long startedNanos = nanoTime.getAsLong();
```

응답 헤더에도 API 시작 시각을 기록한다.

```http
X-Api-Started-At: 2026-07-03T10:30:15.100+09:00
```

요청 메타데이터는 `ThreadLocal` 기반 `SqlCaptureContextHolder`에 저장한다.

```java
new SqlCaptureContext(
    traceType,
    userId,
    apiStartedAt,
    uiId,
    sqlCapturePaused
)
```

Filter는 RedisKey를 만들지 않는다. Filter의 책임은 요청 메타데이터를 검증하고
요청 범위 컨텍스트를 만드는 데까지다.

## 8. Controller와 Repository 실행

Filter가 `filterChain.doFilter()`를 호출하면 일반적인 Spring 요청 흐름이 실행된다.

```text
Controller
  → Service
  → JPA Repository 또는 MyBatis Mapper
  → DataSource
  → JDBC Connection
  → PreparedStatement
```

Controller와 Service는 탭 추적 로직을 알 필요가 없다. 각 API에
`@RequestHeader("X-Ui-Id")`를 반복해서 선언하지 않는다.

단, SQL 로그 조회·삭제 API는 대상 화면을 선택해야 하므로 `uiId`를 요청 파라미터로
받는다.

## 9. Connection과 PreparedStatement 프록시

`SqlTraceDataSource`는 실제 Connection을 `SqlTraceConnectionHandler` 프록시로
감싼다.

Connection에서 `prepareStatement(sql)`이 호출되면 반환된 PreparedStatement도
`SqlTracePreparedStatementHandler`로 감싼다.

```text
Connection.prepareStatement(sql)
  → 실제 PreparedStatement 생성
  → SqlTracePreparedStatementHandler로 래핑
```

## 10. SQL 파라미터 수집

JPA 또는 MyBatis가 다음 메서드를 호출한다고 가정한다.

```java
statement.setString(1, "mint");
statement.setBoolean(2, true);
```

PreparedStatement 프록시는 `set`으로 시작하는 바인딩 메서드를 감지해 파라미터를
저장한다.

```java
{
    1: "mint",
    2: true
}
```

`clearParameters()`가 호출되면 저장된 파라미터도 제거한다.

## 11. SQL 실행과 소요시간 측정

`executeQuery()` 호출 시 실제 SQL 실행 시간을 측정한다.

```java
long startedAt = System.nanoTime();
Object result = invokeDelegate(method, args);
long elapsedNanos = System.nanoTime() - startedAt;
```

실행이 완료되면 SQL, 파라미터, 소요시간, 실행 시각을 `SqlTraceRecorder`에 전달한다.
현재 구현에서는 실행 중 예외가 발생한 SQL은 저장하지 않는다.

## 12. 저장 대상 SQL 판별

`SqlTraceRecorder`는 다음 조건이면 저장하지 않는다.

- 요청 컨텍스트가 없음
- `X-Sql-Capture-Paused`가 `true`
- SELECT 또는 WITH 쿼리가 아님

따라서 현재 INSERT, UPDATE, DELETE SQL은 Redis에 저장하지 않는다.

## 13. 파라미터가 반영된 SQL 생성

`SqlParameterRenderer`가 SQL의 `?` 위치에 수집한 값을 넣는다.

원본 SQL:

```sql
select *
from admin_user
where login_id = ?
  and enabled = ?
```

완성 SQL:

```sql
select *
from admin_user
where login_id = 'mint'
  and enabled = true
```

문자열, 주석, Oracle 대체 인용문 내부의 `?`는 파라미터로 처리하지 않는다.

현재 `SqlValueMasker` 구현은 `NoOpSqlValueMasker`이므로 민감한 파라미터도 그대로
저장된다. 운영 적용 전 비밀번호, 토큰, 주민번호 등은 반드시 마스킹해야 한다.

## 14. Redis 저장 데이터 생성

최초 생성되는 `SqlTraceEntry`는 다음과 같은 형태다.

```json
{
  "traceType": "query",
  "userId": "mint",
  "apiStartedAt": "2026-07-03T10:30:15.100+09:00",
  "uiId": "users-workbench",
  "occurredAt": "2026-07-03T10:30:15.130+09:00",
  "sqlElapsedMillis": 8,
  "serverTimeMillis": null,
  "clientTimeMillis": null,
  "totalTimeMillis": null,
  "sql": "select * from admin_user where login_id = 'mint'"
}
```

## 15. RedisKey 생성

`RedisSqlTraceStore`가 저장 직전에 다음 값으로 RedisKey를 만든다.

```java
new RedisKey(traceType.namespace(), userId, uiId);
```

현재 실제 키 형식은 다음과 같다.

```text
query:{userId}:{uiId}
```

예:

```text
query:mint:users-workbench
```

현업에서 동일 화면을 여러 탭으로 허용한다면 다음 구조가 적합하다.

```text
query:{userId}:{tabId}
```

`screenId`는 Redis 값 내부의 메타데이터로 보관한다.

## 16. Redis List 저장

`StringRedisStore`는 Lua 스크립트로 값을 저장한다.

```lua
redis.call('LPUSH', KEYS[1], ARGV[1])
redis.call('EXPIRE', KEYS[1], ARGV[2])
```

현재 동작은 다음과 같다.

- 최신 SQL을 List 앞에 저장
- 동일 사용자와 화면의 SQL을 하나의 List에 누적
- TTL 24시간 설정
- 값을 추가할 때마다 TTL을 다시 24시간으로 갱신

현재 List 길이 제한은 없다. 운영 환경에서는 `LTRIM`을 추가해 사용자·탭별 최신
100~200건만 유지하는 것이 안전하다.

## 17. 서버 전체 처리시간 갱신

Controller 실행이 끝나면 Filter의 `finally`가 실행된다.

먼저 요청 컨텍스트를 제거한다.

```java
SqlCaptureContextHolder.clear();
```

톰캣 스레드는 재사용되므로 `ThreadLocal.remove()`가 누락되면 다른 요청에 이전
사용자의 컨텍스트가 섞일 수 있다.

그다음 전체 서버 처리시간을 계산한다.

```java
long serverTimeMillis =
    TimeUnit.NANOSECONDS.toMillis(
        nanoTime.getAsLong() - startedNanos
    );
```

Redis List에서 같은 `apiStartedAt`을 가진 항목을 찾아 `serverTimeMillis`를 갱신한다.
한 API에서 여러 SQL을 실행했다면 같은 API 시작 시각을 공유하는 모든 SQL이 함께
갱신된다.

## 18. Axios 응답과 클라이언트 시간

Axios 응답 인터셉터는 API 통신 시간을 계산하고 응답 헤더의
`X-Api-Started-At`을 읽는다.

브라우저 렌더링이 끝난 후 다음 요청을 별도로 전송한다.

```http
POST /api/sql-logs/timing
Content-Type: application/json
```

```json
{
  "traceType": "query",
  "apiStartedAt": "2026-07-03T10:30:15.100+09:00",
  "uiId": "users-workbench",
  "clientTimeMillis": 14,
  "totalTimeMillis": 49
}
```

이 요청은 공통 `httpClient`가 아닌 기본 Axios로 전송하므로 추적 인터셉터를 다시
통과하지 않는다.

## 19. 클라이언트 시간 갱신

`POST /api/sql-logs/timing`은 다음 항목을 검증한다.

- 인증 사용자 존재
- `traceType`이 `query`
- `apiStartedAt`이 ISO-8601 offset date-time
- `uiId` 형식 정상
- 시간값이 유한한 숫자
- `totalTimeMillis >= clientTimeMillis`

Redis List에서 `apiStartedAt`이 일치하는 항목을 찾아 다음 필드를 추가한다.

```json
{
  "clientTimeMillis": 14,
  "totalTimeMillis": 49
}
```

## 20. SQL 로그 조회

요청 검사 패널을 열면 다음 API가 호출된다.

```http
GET /api/sql-logs?traceType=query&uiId=users-workbench
```

백엔드는 요청 파라미터로 사용자 ID를 받지 않는다. 인증 정보에서 사용자 ID를 구하고
다음 RedisKey를 조회한다.

```text
query:mint:users-workbench
```

`LRANGE 0 -1`로 전체 값을 읽은 뒤 JSON을 응답 DTO로 변환한다.

## 21. SQL 로그 삭제

초기화 버튼은 다음 API를 호출한다.

```http
DELETE /api/sql-logs?traceType=query&uiId=users-workbench
```

백엔드는 인증 사용자 ID와 요청의 `uiId`로 RedisKey를 만들고 해당 키 전체를
삭제한다.

## 운영 적용 전 필수 보완사항

### 사용자 ID 출처 통일

현재 테스트는 `USER_ID` request attribute를 사용하지만 운영 코드에는 생성 필터가
없다. `SecurityContext`의 인증 사용자로 통일하는 것을 권장한다.

### `screenId`와 `tabId` 분리

현재 `uiId`는 화면 ID다. 동일 화면의 다중 탭을 지원하려면 탭 생성 시 UUID를 발급하고
다음 헤더를 분리한다.

```http
X-Screen-Id: users-workbench
X-Tab-Id: 550e8400-e29b-41d4-a716-446655440000
```

### 민감정보 마스킹

완성 SQL에는 실제 파라미터 값이 포함된다. 운영 환경에서는 컬럼명, 파라미터 위치 또는
값 패턴을 기준으로 민감정보를 마스킹해야 한다.

### Redis List 길이 제한

TTL만으로는 하나의 키가 24시간 동안 무제한으로 커질 수 있다. `LPUSH` 뒤에 다음
명령을 추가한다.

```lua
redis.call('LTRIM', KEYS[1], 0, 199)
```

### 비동기 실행 컨텍스트

현재 컨텍스트는 `ThreadLocal` 기반이다. `@Async`, 별도 Executor, 비동기 이벤트로
SQL을 실행하면 요청 컨텍스트가 자동 전파되지 않는다. 비동기 SQL도 추적해야 한다면
`TaskDecorator` 등으로 컨텍스트를 명시적으로 복사하고 반드시 제거해야 한다.

## 책임 분리 요약

| 구성요소 | 책임 |
|---|---|
| Zustand | 열린 탭과 활성 탭 관리 |
| Axios 인터셉터 | 활성 탭 메타데이터와 요청 시작 시각 전달 |
| Spring Security | 신뢰 가능한 사용자 인증 |
| `SqlCaptureRequestFilter` | 헤더 검증과 요청 컨텍스트 생성·정리 |
| Controller/Service | 비즈니스 로직 수행 |
| JDBC 프록시 | SQL, 바인딩 값, 실행시간 수집 |
| `SqlParameterRenderer` | 파라미터가 반영된 SQL 생성 |
| `RedisSqlTraceStore` | RedisKey 생성과 추적 데이터 직렬화 |
| `StringRedisStore` | Redis List 저장·조회·갱신·삭제 |
| `SqlTraceController` | 로그 조회·삭제와 클라이언트 시간 갱신 |
