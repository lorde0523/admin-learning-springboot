# User-Scoped Page SQL Trace Design

## Goal

로그인 사용자가 Nexacro 화면에서 실행한 조회 SQL을 사용자와 페이지별로 로컬 JSONL 파일에 누적한다. SQL 목록 조회 시 현재 로그인 사용자의 해당 페이지 로그만 반환하고, 작성 정지 중에는 새 로그를 기록하지 않으며, 초기화 이후에는 이전 로그를 다시 표시하지 않는다.

## Confirmed Behavior

- 로그 소유 키는 `로그인 username + pageId`다.
- 사용자 ID는 Nexacro 파라미터나 헤더로 받지 않고 Spring Security 세션의 `LoginUsers.currentUsername()`에서 가져온다.
- `user1`이 만든 로그는 `user2`에게 반환하지 않는다.
- 동일 사용자의 동일 페이지에서 실행된 조회 SQL은 서버 파일에 계속 누적된다.
- `X-Sql-Capture-Paused: true`인 요청은 SQL 파일에 기록하지 않는다.
- 작성 정지 중에도 목록 API는 정지 전에 누적된 로그를 반환한다.
- 초기화는 실제 로그 파일을 삭제하지 않고 사용자·페이지별 `CLEAR` 이벤트를 기록한다.
- 목록 API는 가장 최근 `CLEAR` 이벤트 이후의 `QUERY`만 반환한다.
- 서버 재시작 후에도 초기화 기준은 유지된다.

## Request Contract

Nexacro 업무 조회 요청:

```http
X-Page-Id: page01
X-Sql-Capture-Paused: false
Cookie: JSESSIONID=...
```

서버는 요청마다 `requestId`를 생성해 응답 헤더로 반환한다.

```http
X-Request-Id: 2de4a7d7-1453-4652-85dd-ef8ddfa57467
```

규칙:

- 인증된 `LoginUser`가 없으면 SQL 추적 컨텍스트를 만들지 않는다.
- `X-Page-Id`가 없거나 공백이면 추적하지 않는다.
- `X-Sql-Capture-Paused`가 없으면 `false`다.
- 잘못된 정지 헤더 값은 `400 Bad Request`다.
- GET 조회 요청만 추적한다.
- SQL 로그 API 자체는 추적하지 않는다.

## Package Boundary

모든 기능 코드는 `com.example.admin.sqltrace` 아래에 둔다.

```text
com.example.admin.sqltrace
├── api
├── config
├── context
├── jdbc
└── storage
```

기존 업무 컨트롤러, 서비스, 저장소는 SQL 추적 타입을 직접 의존하지 않는다.

## SQL Capture

`SqlCaptureRequestFilter`가 요청마다 다음 컨텍스트를 설정한다.

```text
username
pageId
requestId
sqlCapturePaused
```

JDBC DataSource 프록시는 MyBatis와 JPA가 실행한 성공한 조회 SQL만 수집한다.

- `PreparedStatement.setXxx` 바인딩 값 저장
- `executeQuery()` 또는 결과셋을 반환한 `execute()` 시간 측정
- 실제 값이 들어간 SQL 전문 생성
- `SELECT`와 조회 CTE(`WITH`)만 기록
- `INSERT`, `UPDATE`, `DELETE`, DDL, 배치, 실패 SQL 제외

현재 마스킹은 비활성이다. `SqlValueMasker` 교체 지점은 유지한다.

## JSONL Storage

기능은 기본 비활성이며 로컬에서 명시적으로 활성화한다.

```yaml
admin:
  sql-trace:
    enabled: ${SQL_TRACE_ENABLED:false}
    directory: ${SQL_TRACE_DIRECTORY:./logs/sql-trace}
```

날짜별 파일:

```text
sql-trace-2026-06-25.jsonl
```

QUERY 예시:

```json
{"eventType":"QUERY","username":"user1","requestId":"2de4a7d7-1453-4652-85dd-ef8ddfa57467","pageId":"page01","occurredAt":"2026-06-25T14:20:31.245+09:00","sqlElapsedMillis":18,"sql":"select * from admin_menu where menu_name = '관리'"}
```

TIMING 예시:

```json
{"eventType":"TIMING","username":"user1","requestId":"2de4a7d7-1453-4652-85dd-ef8ddfa57467","pageId":"page01","occurredAt":"2026-06-25T14:20:31.300+09:00","clientApiElapsedMillis":35.2,"clientTotalElapsedMillis":48.7}
```

CLEAR 예시:

```json
{"eventType":"CLEAR","username":"user1","pageId":"page01","occurredAt":"2026-06-25T15:00:00+09:00"}
```

파일 접근 규칙:

- 프로세스 내부 append와 조회를 같은 잠금으로 직렬화한다.
- 목록 조회는 `sql-trace-*.jsonl` 파일을 날짜순으로 읽는다.
- 현재 사용자와 페이지의 가장 최근 `CLEAR` 이후 `QUERY`만 반환한다.
- 같은 `requestId`의 `TIMING`을 SQL 행에 결합한다.
- 손상된 행과 사용자 정보가 없는 과거 형식 행은 건너뛴다.
- 파일 저장 실패는 업무 조회 API를 실패시키지 않는다.

## APIs

### Accumulated List

```http
GET /api/sql-logs?pageId=page01
```

현재 로그인 사용자와 `pageId`가 일치하고 최근 초기화 이후인 SQL을 모두 반환한다.

```json
{
  "pageId": "page01",
  "logs": [
    {
      "requestId": "2de4a7d7-1453-4652-85dd-ef8ddfa57467",
      "pageId": "page01",
      "executedAt": "2026-06-25T14:20:31.245+09:00",
      "sqlElapsedMillis": 18,
      "clientApiElapsedMillis": 35.2,
      "clientTotalElapsedMillis": 48.7,
      "sql": "select * from admin_menu where menu_name = '관리'"
    }
  ]
}
```

### Client Timing

Nexacro는 업무 API 응답을 받은 후 서버가 반환한 `requestId`와 클라이언트 시간을 전송한다.

```http
POST /api/sql-logs/timing
Content-Type: application/json

{
  "requestId": "2de4a7d7-1453-4652-85dd-ef8ddfa57467",
  "pageId": "page01",
  "clientApiElapsedMillis": 35.2,
  "clientTotalElapsedMillis": 48.7
}
```

서버는 현재 로그인 사용자 소유의 동일 `requestId + pageId` QUERY가 있을 때만 TIMING을 기록한다.

### Clear

```http
DELETE /api/sql-logs?pageId=page01
```

현재 로그인 사용자와 `pageId`에 대한 CLEAR 이벤트를 기록한다. 다른 사용자와 다른 페이지에는 영향이 없다.

## Nexacro Flow

1. 조회 시작 시 클라이언트 시간을 기록한다.
2. 업무 API 요청에 `X-Page-Id`, `X-Sql-Capture-Paused`를 전달한다.
3. 업무 응답의 `X-Request-Id`를 읽는다.
4. 작성 정지가 아니면 클라이언트 API 시간과 전체 시간을 timing API로 보낸다.
5. 쿼리 목록 버튼은 `GET /api/sql-logs?pageId=...`를 호출한다.
6. 작성 정지 상태여도 목록 API는 호출 가능하며 기존 누적 로그가 보인다.
7. 초기화 버튼은 `DELETE /api/sql-logs?pageId=...`를 호출한 뒤 그리드를 비운다.

## Authentication Rules

- 모든 SQL 로그 API는 인증된 `LoginUser`가 필요하다.
- username을 요청 파라미터나 헤더로 받지 않는다.
- `usecookie=true`인 Nexacro가 로그인 세션의 `JSESSIONID`를 계속 전송한다는 전제다.
- 인증 사용자가 없으면 SQL 로그 API는 `401 Unauthorized`를 반환한다.

## Tests

- user1과 user2의 같은 pageId 로그가 서로 분리된다.
- 작성 정지 요청은 QUERY 이벤트를 남기지 않는다.
- 작성 정지 상태의 목록 조회는 기존 로그를 반환한다.
- 초기화 후 이전 QUERY가 반환되지 않는다.
- 초기화 후 새 QUERY만 반환된다.
- CLEAR 기준은 저장소를 다시 생성해도 유지된다.
- TIMING이 같은 requestId의 SQL 행에 결합된다.
- 다른 사용자의 requestId에 TIMING을 기록할 수 없다.
- 인증되지 않은 요청은 로그 API에서 401이다.
- MyBatis와 JPA 조회 모두 사용자별 누적 목록에 나타난다.

## Out of Scope

- 파일 서버와 다중 프로세스 파일 잠금
- 민감정보 마스킹 규칙
- 로그 보존 기간과 자동 삭제
- 관리자 통합 조회
- 사용자 간 로그 공유
