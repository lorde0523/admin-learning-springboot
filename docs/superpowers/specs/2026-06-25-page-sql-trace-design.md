# Page SQL Trace Design

## Goal

조회 API에서 실행된 MyBatis 및 JPA `SELECT` SQL을 요청별로 추적하고, 별도 SQL 로그 API를 통해 페이지 탭의 그리드에 표시한다.

그리드에는 다음 값이 포함된다.

- 클라이언트가 전달한 `pageId`
- 파라미터 값이 치환된 SQL 전문
- SQL 실행시간
- 클라이언트가 계산한 원 업무 API 왕복시간
- 클라이언트 조회 시작부터 SQL 로그 행 생성 완료까지의 전체 시간

## Confirmed Behavior

- 동일한 `pageId` 화면은 동시에 한 탭만 열린다고 가정한다.
- SQL 그리드 목록은 열린 탭의 React 메모리 상태가 소유한다.
- SQL 그리드 목록은 React Query 캐시나 전역 상태에 저장하지 않고 탭 컴포넌트의 로컬 상태로만 관리한다.
- 탭이 유지되는 동안 새 SQL 행을 기존 목록 뒤에 누적한다.
- 탭을 닫으면 해당 목록은 폐기된다.
- `page01` 탭에서 3개 행이 누적된 뒤 탭을 닫고 새 `page01` 탭을 열면 목록은 빈 배열에서 시작한다.
- 서버 DB, Redis, `localStorage`, `sessionStorage`에는 그리드 목록을 저장하지 않는다.
- SQL 수집 일시정지는 기존 목록을 숨기거나 삭제하지 않는다.
- 일시정지 상태에서 실행된 SQL은 SQL 전용 파일에 기록하지 않는다.
- 일시정지 중 실행된 SQL은 체크 해제 후에도 소급해서 추가하지 않는다.
- 체크 해제 후 새로 실행된 조회 SQL부터 다시 기록하고 목록에 추가한다.

## Request Contract

클라이언트는 SQL 추적이 필요한 모든 업무 조회 요청에 다음 헤더를 전달한다.

```http
X-Page-Id: MENU_001
X-Sql-Capture-Paused: false
```

서버는 요청마다 UUID 형식의 `requestId`를 생성하고 응답 헤더로 반환한다.

```http
X-Request-Id: 2de4a7d7-1453-4652-85dd-ef8ddfa57467
```

규칙:

- `X-Page-Id`가 없거나 공백이면 SQL 추적 대상이 아니다.
- `X-Sql-Capture-Paused`가 없으면 `false`로 처리한다.
- 잘못된 boolean 문자열은 `400 Bad Request`로 처리한다.
- SQL 로그 조회 API 자체는 추적 대상에서 제외한다.

## Server Architecture

### Request Context Filter

`SqlCaptureRequestFilter`는 `OncePerRequestFilter`를 상속한다.

필터 책임:

1. 요청 헤더를 읽는다.
2. 추적 대상 요청이면 `requestId`를 생성한다.
3. `pageId`, `requestId`, `sqlCapturePaused`를 현재 요청 스레드의 `SqlCaptureContext`에 설정한다.
4. `X-Request-Id` 응답 헤더를 설정한다.
5. 필터 체인 종료 시 `finally`에서 컨텍스트를 제거한다.

컨트롤러마다 `@RequestHeader`를 반복하지 않는다. 필터와 JDBC 수집기는 동일한 요청 컨텍스트를 사용한다.

### JDBC SQL Collector

MyBatis와 JPA를 각각 가로채지 않고 애플리케이션 `DataSource`를 JDBC 프록시로 감싼다.

프록시 책임:

- `Connection.prepareStatement(sql)`의 원본 SQL을 보관한다.
- `PreparedStatement.setXxx(index, value)` 호출의 바인딩 값을 보관한다.
- `executeQuery()` 실행 전후 시간을 `System.nanoTime()`으로 측정한다.
- 실행이 성공한 조회 SQL만 기록한다.
- `?` 위치에 SQL 리터럴로 변환된 실제 파라미터를 순서대로 치환한다.
- 현재 요청 컨텍스트가 없거나 일시정지 상태이면 기록하지 않는다.

수집 대상:

- MyBatis에서 실행된 조회 SQL
- JPA/Hibernate에서 실행된 조회 SQL
- JDBC `PreparedStatement.executeQuery()`로 실행된 SQL

수집 제외:

- `INSERT`, `UPDATE`, `DELETE`, DDL
- 배치 실행
- 애플리케이션 시작 및 마이그레이션 SQL
- SQL 로그 조회 API
- 일시정지 요청
- 조회 실패 SQL

JPA의 지연 로딩이 원 HTTP 요청 스레드 밖에서 실행되면 해당 SQL은 요청 컨텍스트와 연결할 수 없으므로 기록하지 않는다. 현재 설정의 `open-in-view=false`와 서비스 트랜잭션 내부 조회를 기준으로 한다.

### SQL Rendering

SQL 리터럴 변환 규칙:

- `null` -> `NULL`
- 숫자 및 boolean -> 따옴표 없이 기록
- 문자열, 문자, enum -> 작은따옴표로 감싸고 내부 작은따옴표를 두 번 기록
- 날짜 및 시간 -> ISO 형식 문자열을 작은따옴표로 기록
- byte 배열 -> `'<BINARY length=123>'` 형식으로 기록
- 입력 스트림 및 리더 -> `'<STREAM>'` 형식으로 기록

SQL의 문자열 리터럴 및 주석 안에 포함된 `?`는 바인딩 자리로 취급하지 않는다. 실제 바인딩 인덱스와 SQL 토큰 위치를 대응해 치환한다.

### Masking Extension

현재 버전은 파라미터를 마스킹하지 않는다.

SQL 렌더링 직전에 `SqlValueMasker` 인터페이스를 호출한다. 기본 구현인 `NoOpSqlValueMasker`는 입력값을 그대로 반환한다. 향후 비밀번호, 토큰, 주민번호 등의 규칙이 정해지면 해당 구현만 교체한다.

확장 지점에는 다음 취지의 짧은 주석을 남긴다.

```java
// Replace NoOpSqlValueMasker when sensitive-value masking rules are defined.
```

비활성 코드를 통째로 주석 처리해 보관하지 않는다.

## File Storage

현재 범위에서는 로컬 파일 시스템만 지원한다.

기본 경로:

```text
./logs/sql-trace/
```

경로는 설정으로 외부화한다.

```yaml
admin:
  sql-trace:
    directory: ${SQL_TRACE_DIRECTORY:./logs/sql-trace}
```

파일은 날짜별 JSON Lines 형식으로 저장한다.

```text
sql-trace-2026-06-25.jsonl
```

한 줄은 하나의 실행 SQL이다.

```json
{"requestId":"2de4a7d7-1453-4652-85dd-ef8ddfa57467","pageId":"MENU_001","executedAt":"2026-06-25T14:20:31.245+09:00","elapsedMillis":18,"sql":"select id, menu_name from admin_menu where menu_name like '%관리%' order by sort_order"}
```

기록은 UTF-8 append 방식이며 프로세스 내부 동시 쓰기를 직렬화한다. 디렉터리가 없으면 생성한다. 향후 개발·운영에서 네트워크 마운트 경로가 결정되면 설정값만 변경한다.

로그 파일 기록 실패는 업무 조회 API를 실패시키지 않는다. 애플리케이션 일반 로그에 경고를 남기고 업무 응답은 정상 반환한다.

## SQL Log API

```http
GET /api/sql-logs?requestId={requestId}&pageId={pageId}
```

응답:

```json
{
  "requestId": "2de4a7d7-1453-4652-85dd-ef8ddfa57467",
  "pageId": "MENU_001",
  "logs": [
    {
      "pageId": "MENU_001",
      "executedAt": "2026-06-25T14:20:31.245+09:00",
      "elapsedMillis": 18,
      "sql": "select id, menu_name from admin_menu where menu_name like '%관리%' order by sort_order"
    }
  ]
}
```

조회 규칙:

- `requestId`는 UUID 형식이어야 한다.
- `pageId`는 필수이며 파일 행의 `pageId`와 정확히 일치해야 한다.
- 서버가 관리하는 날짜별 파일만 읽고 클라이언트가 파일 경로를 지정할 수 없게 한다.
- 현재 날짜 파일부터 읽으며, 요청이 자정을 걸칠 수 있으므로 전날 파일도 확인한다.
- 일치하는 로그가 없으면 `200 OK`와 빈 `logs` 배열을 반환한다.
- 손상된 JSON 한 줄은 건너뛰고 일반 로그에 경고를 남긴다.
- 파일 읽기 실패 시 `500 Internal Server Error`를 반환한다.

## Client Flow

1. 탭은 `sqlCapturePaused`와 누적 `sqlLogs`를 탭 컴포넌트의 `useState`로 관리한다.
2. 탭 컴포넌트가 마운트될 때 `sqlLogs`는 항상 빈 배열로 초기화한다.
3. 탭이 언마운트되면 별도 저장 없이 `sqlLogs` 상태를 폐기한다.
4. 조회 버튼 클릭 시 `performance.now()`로 전체 조회 시작 시간을 저장한다.
5. React Query의 `queryFn`이 업무 조회 API에 `X-Page-Id`와 `X-Sql-Capture-Paused`를 전달한다.
6. 업무 응답 수신 직후 `clientApiElapsedMillis`를 계산한다.
7. 응답의 `X-Request-Id`를 읽는다.
8. 일시정지가 아니면 별도 SQL 로그 API를 `requestId`와 현재 탭의 `pageId`로 호출한다.
9. 반환된 SQL을 그리드 행으로 변환한다.
10. SQL 로그 조회, 응답 변환 및 행 생성이 끝난 직후 `clientTotalElapsedMillis`를 계산한다.
11. 이번 요청의 각 SQL 행에 동일한 `clientApiElapsedMillis`와 `clientTotalElapsedMillis`를 결합하고 한 번의 상태 갱신으로 기존 목록 뒤에 추가한다.
12. 일시정지이면 SQL 로그 API를 호출하지 않고 기존 목록을 그대로 둔다. 이때 전체 시간은 업무 응답 처리와 일시정지 분기 처리가 끝난 시점에 계산하지만 새 SQL 행이 없으므로 그리드에는 추가하지 않는다.

API 한 번에 SQL이 여러 개 실행되면 각 SQL을 별도 행으로 표시하고 동일한 원 업무 API 왕복시간을 반복 표시한다.

클라이언트의 SQL 로그 API 호출 시간은 `clientApiElapsedMillis`에 포함하지 않는다.

시간 정의:

```text
clientApiElapsedMillis =
  원 업무 API 응답 수신 시각 - 원 업무 API 전송 직전 시각

clientTotalElapsedMillis =
  SQL 로그 행 생성 완료 시각 - 조회 버튼 처리 시작 시각
```

`clientTotalElapsedMillis`에는 원 업무 API 왕복, SQL 로그 API 왕복, 응답 변환 및 SQL 행 생성을 포함한다. React 렌더링과 브라우저 화면 그리기 시간은 포함하지 않는다.

## Grid Columns

| Column | Source |
| --- | --- |
| 페이지 ID | SQL 로그의 `pageId` |
| SQL 전문 | SQL 로그의 `sql` |
| SQL 실행시간(ms) | SQL 로그의 `elapsedMillis` |
| API 왕복시간(ms) | 클라이언트의 `clientApiElapsedMillis` |
| 클라이언트 전체시간(ms) | 클라이언트의 `clientTotalElapsedMillis` |
| 실행시각 | SQL 로그의 `executedAt` |

## Error Handling

- SQL 파일 기록 실패: 업무 API 성공, SQL 로그 누락, 서버 경고 로그
- SQL 로그 조회 실패: 기존 탭 목록 유지, 클라이언트에서 조회 실패 표시
- 업무 API 실패: SQL 로그 API를 호출하지 않음
- 응답에 `X-Request-Id`가 없음: SQL 로그 API를 호출하지 않고 클라이언트 경고 처리
- 일시정지 요청: 정상 업무 응답, 파일 미기록, 기존 목록 유지

## Test Strategy

### Filter Tests

- 헤더가 있으면 요청 컨텍스트와 `X-Request-Id`가 생성된다.
- 헤더가 없으면 추적 컨텍스트를 만들지 않는다.
- 일시정지 헤더가 컨텍스트에 반영된다.
- 잘못된 boolean 헤더는 `400`을 반환한다.
- 정상 및 예외 응답 후 컨텍스트가 제거된다.

### JDBC Collector Tests

- MyBatis `SELECT`의 실제 파라미터가 치환되고 실행시간이 기록된다.
- JPA `SELECT`의 실제 파라미터가 치환되고 실행시간이 기록된다.
- 문자열의 작은따옴표와 SQL 내부의 리터럴 `?`를 올바르게 처리한다.
- 일시정지 상태에서는 파일 저장소를 호출하지 않는다.
- 컨텍스트가 없으면 저장하지 않는다.
- 변경 SQL과 실패한 조회 SQL은 저장하지 않는다.
- 파일 저장 실패가 업무 조회 결과를 실패시키지 않는다.

### File Store Tests

- 날짜별 JSONL 파일을 생성하고 여러 행을 append한다.
- `requestId`와 `pageId`가 모두 일치하는 행만 반환한다.
- 오늘과 전날 파일을 검색한다.
- 손상된 행을 건너뛴다.
- 동시에 기록해도 JSON 행이 섞이지 않는다.

### API Tests

- 업무 조회 응답에 `X-Request-Id`가 포함된다.
- 별도 SQL 로그 API가 MyBatis와 JPA 조회 로그를 반환한다.
- 일시정지 요청은 업무 조회를 수행하지만 로그 API 결과는 빈 배열이다.
- 일시정지 해제 후 새 요청의 SQL만 반환되고 이전 일시정지 SQL은 나타나지 않는다.
- 기존 페이징 응답 형식은 변경되지 않는다.

### Client Contract Tests

프론트엔드 프로젝트에서 다음 동작을 검증한다. 현재 서버 저장소에서는 테스트 코드를 추가하지 않는다.

- 조회 버튼 처리 시작부터 SQL 로그 행 생성 완료까지 `clientTotalElapsedMillis`를 계산한다.
- 원 업무 API 왕복시간에는 SQL 로그 API 호출시간이 포함되지 않는다.
- 한 업무 요청에서 여러 SQL 행이 반환되면 같은 API 왕복시간과 클라이언트 전체시간을 표시한다.
- 탭이 열린 동안 후속 조회 결과가 기존 목록 뒤에 누적된다.
- `page01` 탭에서 3개 행을 만든 뒤 탭을 닫고 새 `page01` 탭을 열면 SQL 목록이 0개로 시작한다.
- React Query의 업무 데이터 캐시가 남아 있어도 SQL 그리드 목록은 복원되지 않는다.

## Out of Scope

- Kibana 및 Elasticsearch 연동
- 파일 서버 또는 네트워크 마운트 검증
- 서버 측 SQL 목록 누적
- 브라우저 영구 저장
- 민감정보 마스킹 규칙
- 로그 보존 기간과 자동 삭제
- 다중 애플리케이션 인스턴스 간 파일 동기화
- 프론트엔드 프로젝트 코드 구현
