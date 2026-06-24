# Page SQL Trace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture parameter-complete MyBatis and JPA query SQL into request-scoped local JSONL files and expose it through a separate lookup API without changing existing business API response bodies.

**Architecture:** All production code lives below `com.example.admin.sqltrace`, split into `api`, `config`, `context`, `jdbc`, and `storage`. A `OncePerRequestFilter` creates request context from headers, a wrapped `DataSource` observes successful JDBC query executions, and a date-partitioned JSONL store persists and retrieves rows by `requestId` and `pageId`.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring MVC, JDBC dynamic proxies, Jackson, JUnit 5, AssertJ, MockMvc, H2

---

## File Structure

Create only the following production package tree:

```text
src/main/java/com/example/admin/sqltrace/
├── api/
│   ├── SqlTraceController.java
│   ├── SqlTraceLogResponse.java
│   └── SqlTraceLogRow.java
├── config/
│   ├── SqlTraceConfiguration.java
│   ├── SqlTraceDataSourceBeanPostProcessor.java
│   └── SqlTraceProperties.java
├── context/
│   ├── SqlCaptureContext.java
│   ├── SqlCaptureContextHolder.java
│   └── SqlCaptureRequestFilter.java
├── jdbc/
│   ├── NoOpSqlValueMasker.java
│   ├── SqlParameterRenderer.java
│   ├── SqlTraceConnectionHandler.java
│   ├── SqlTraceDataSource.java
│   ├── SqlTracePreparedStatementHandler.java
│   ├── SqlTraceRecorder.java
│   └── SqlValueMasker.java
└── storage/
    ├── JsonLineSqlTraceStore.java
    ├── SqlTraceEntry.java
    └── SqlTraceStore.java
```

Tests live in matching packages below `src/test/java/com/example/admin/sqltrace`. Existing `menu`, `user`, `role`, and `common` production packages remain unchanged.

### Task 1: Request Context and Filter

**Files:**
- Create: `src/main/java/com/example/admin/sqltrace/context/SqlCaptureContext.java`
- Create: `src/main/java/com/example/admin/sqltrace/context/SqlCaptureContextHolder.java`
- Create: `src/main/java/com/example/admin/sqltrace/context/SqlCaptureRequestFilter.java`
- Test: `src/test/java/com/example/admin/sqltrace/context/SqlCaptureRequestFilterTests.java`

- [ ] **Step 1: Write failing filter tests**

Create tests covering:

```java
@Test
void createsContextAndResponseHeaderForTrackedGetRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
    request.addHeader("X-Page-Id", "page01");
    request.addHeader("X-Sql-Capture-Paused", "false");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<SqlCaptureContext> observed = new AtomicReference<>();

    filter.doFilter(request, response, (req, res) ->
            observed.set(SqlCaptureContextHolder.current().orElseThrow()));

    assertThat(observed.get().pageId()).isEqualTo("page01");
    assertThat(observed.get().sqlCapturePaused()).isFalse();
    assertThat(response.getHeader("X-Request-Id")).isEqualTo(observed.get().requestId());
    assertThat(SqlCaptureContextHolder.current()).isEmpty();
}

@Test
void doesNotCreateContextWithoutPageId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) ->
            assertThat(SqlCaptureContextHolder.current()).isEmpty());

    assertThat(response.getHeader("X-Request-Id")).isNull();
}

@Test
void rejectsInvalidPauseHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/jpa/menus/page");
    request.addHeader("X-Page-Id", "page01");
    request.addHeader("X-Sql-Capture-Paused", "invalid");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(SqlCaptureContextHolder.current()).isEmpty();
}

@Test
void skipsSqlLogEndpointAndNonGetRequests() throws Exception {
    assertThat(runAndObserve("GET", "/api/sql-logs", "page01")).isEmpty();
    assertThat(runAndObserve("POST", "/api/jpa/menus/grid-save", "page01")).isEmpty();
}
```

- [ ] **Step 2: Run the filter tests and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "*SqlCaptureRequestFilterTests"
```

Expected: compilation failure because the context and filter classes do not exist.

- [ ] **Step 3: Implement immutable context and holder**

Use:

```java
public record SqlCaptureContext(
        String requestId,
        String pageId,
        boolean sqlCapturePaused) {
}
```

`SqlCaptureContextHolder` must expose only:

```java
public static Optional<SqlCaptureContext> current()
public static void set(SqlCaptureContext context)
public static void clear()
```

Back it with a private static `ThreadLocal<SqlCaptureContext>`.

- [ ] **Step 4: Implement the request filter**

`SqlCaptureRequestFilter` must:

```java
public static final String PAGE_ID_HEADER = "X-Page-Id";
public static final String PAUSED_HEADER = "X-Sql-Capture-Paused";
public static final String REQUEST_ID_HEADER = "X-Request-Id";
```

Behavior:

```java
if (!"GET".equalsIgnoreCase(request.getMethod())
        || request.getRequestURI().equals("/api/sql-logs")) {
    filterChain.doFilter(request, response);
    return;
}

String pageId = request.getHeader(PAGE_ID_HEADER);
if (!StringUtils.hasText(pageId)) {
    filterChain.doFilter(request, response);
    return;
}

String pausedHeader = request.getHeader(PAUSED_HEADER);
boolean paused;
if (pausedHeader == null || pausedHeader.isBlank()) {
    paused = false;
} else if ("true".equalsIgnoreCase(pausedHeader) || "false".equalsIgnoreCase(pausedHeader)) {
    paused = Boolean.parseBoolean(pausedHeader);
} else {
    response.sendError(HttpStatus.BAD_REQUEST.value(), "X-Sql-Capture-Paused must be true or false.");
    return;
}

String requestId = UUID.randomUUID().toString();
response.setHeader(REQUEST_ID_HEADER, requestId);
SqlCaptureContextHolder.set(new SqlCaptureContext(requestId, pageId.trim(), paused));
try {
    filterChain.doFilter(request, response);
} finally {
    SqlCaptureContextHolder.clear();
}
```

- [ ] **Step 5: Run filter tests and verify GREEN**

Run:

```powershell
.\gradlew.bat test --tests "*SqlCaptureRequestFilterTests"
```

Expected: all filter tests pass.

- [ ] **Step 6: Commit**

Use the Lore commit format and record the targeted test command in `Tested:`.

### Task 2: SQL Parameter Rendering and Masking Extension

**Files:**
- Create: `src/main/java/com/example/admin/sqltrace/jdbc/SqlValueMasker.java`
- Create: `src/main/java/com/example/admin/sqltrace/jdbc/NoOpSqlValueMasker.java`
- Create: `src/main/java/com/example/admin/sqltrace/jdbc/SqlParameterRenderer.java`
- Test: `src/test/java/com/example/admin/sqltrace/jdbc/SqlParameterRendererTests.java`

- [ ] **Step 1: Write failing renderer tests**

Cover:

```java
@Test
void replacesParametersWithoutTouchingQuotedOrCommentQuestionMarks() {
    String sql = """
            select * from admin_menu
            where menu_name = ?
              and note = 'literal ?'
              and enabled = ?
              -- ignored ?
              and id = ?
            """;

    String rendered = renderer.render(sql, Map.of(
            1, "O'Brien",
            2, true,
            3, 42L));

    assertThat(rendered).contains("menu_name = 'O''Brien'");
    assertThat(rendered).contains("note = 'literal ?'");
    assertThat(rendered).contains("enabled = true");
    assertThat(rendered).contains("id = 42");
}

@Test
void rendersNullTemporalBinaryAndStreamValues() {
    Map<Integer, Object> values = new HashMap<>();
    values.put(1, null);
    values.put(2, LocalDate.of(2026, 6, 25));
    values.put(3, new byte[3]);
    values.put(4, new ByteArrayInputStream(new byte[] {1}));

    assertThat(renderer.render("select ?, ?, ?, ?", values))
            .isEqualTo("select NULL, '2026-06-25', '<BINARY length=3>', '<STREAM>'");
}

@Test
void leavesUnboundPlaceholdersVisible() {
    assertThat(renderer.render("select * from t where a = ? and b = ?", Map.of(1, "x")))
            .isEqualTo("select * from t where a = 'x' and b = ?");
}
```

- [ ] **Step 2: Run renderer tests and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "*SqlParameterRendererTests"
```

Expected: compilation failure because renderer types do not exist.

- [ ] **Step 3: Implement masking interfaces**

Use:

```java
public interface SqlValueMasker {
    Object mask(String sql, int parameterIndex, Object value);
}

public class NoOpSqlValueMasker implements SqlValueMasker {
    @Override
    public Object mask(String sql, int parameterIndex, Object value) {
        // Replace NoOpSqlValueMasker when sensitive-value masking rules are defined.
        return value;
    }
}
```

- [ ] **Step 4: Implement the SQL scanner and literal renderer**

`SqlParameterRenderer.render(String sql, Map<Integer, Object> values)` must scan character-by-character with states:

```java
NORMAL
SINGLE_QUOTE
DOUBLE_QUOTE
LINE_COMMENT
BLOCK_COMMENT
```

Only replace `?` in `NORMAL`. Handle doubled single quotes (`''`) and doubled double quotes (`""`) without leaving the quoted state.

Literal rules:

```java
null -> "NULL"
Number/Boolean -> value.toString()
byte[] -> "'<BINARY length=" + bytes.length + ">'"
InputStream/Reader -> "'<STREAM>'"
TemporalAccessor/java.util.Date/Enum/Character/String -> quoted escaped string
other -> quoted escaped String.valueOf(value)
```

Call `masker.mask(sql, index, value)` before converting each value.

- [ ] **Step 5: Run renderer tests and verify GREEN**

Run:

```powershell
.\gradlew.bat test --tests "*SqlParameterRendererTests"
```

Expected: all renderer tests pass.

- [ ] **Step 6: Commit**

Use the Lore commit format and include the renderer edge cases in `Tested:`.

### Task 3: Date-Partitioned JSONL Storage

**Files:**
- Create: `src/main/java/com/example/admin/sqltrace/config/SqlTraceProperties.java`
- Create: `src/main/java/com/example/admin/sqltrace/storage/SqlTraceEntry.java`
- Create: `src/main/java/com/example/admin/sqltrace/storage/SqlTraceStore.java`
- Create: `src/main/java/com/example/admin/sqltrace/storage/JsonLineSqlTraceStore.java`
- Test: `src/test/java/com/example/admin/sqltrace/storage/JsonLineSqlTraceStoreTests.java`

- [ ] **Step 1: Write failing file-store tests**

Use `@TempDir`, a fixed `Clock`, and the application `ObjectMapper`.

```java
@Test
void appendsAndFindsOnlyMatchingRequestAndPage() {
    store.append(entry("request-1", "page01", "select 1"));
    store.append(entry("request-1", "page02", "select 2"));
    store.append(entry("request-2", "page01", "select 3"));

    assertThat(store.find("request-1", "page01"))
            .extracting(SqlTraceEntry::sql)
            .containsExactly("select 1");
}

@Test
void readsTodayAndPreviousDayFiles() {
    writeEntryFor(LocalDate.of(2026, 6, 24), entry("request-old", "page01", "select old"));
    writeEntryFor(LocalDate.of(2026, 6, 25), entry("request-new", "page01", "select new"));

    assertThat(store.find("request-old", "page01")).hasSize(1);
    assertThat(store.find("request-new", "page01")).hasSize(1);
}

@Test
void skipsMalformedLines() {
    Files.writeString(todayFile(), "{broken}\n", UTF_8, CREATE, APPEND);
    store.append(entry("request-1", "page01", "select 1"));

    assertThat(store.find("request-1", "page01")).hasSize(1);
}

@Test
void concurrentWritesRemainOneJsonObjectPerLine() throws Exception {
    runConcurrentAppends(50);

    List<String> lines = Files.readAllLines(todayFile());
    assertThat(lines).hasSize(50);
    assertThat(lines).allSatisfy(line ->
            assertThatCode(() -> objectMapper.readTree(line)).doesNotThrowAnyException());
}
```

- [ ] **Step 2: Run store tests and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "*JsonLineSqlTraceStoreTests"
```

Expected: compilation failure because storage types do not exist.

- [ ] **Step 3: Implement properties and storage contract**

`SqlTraceProperties`:

```java
@ConfigurationProperties("admin.sql-trace")
public class SqlTraceProperties {
    private Path directory = Path.of("./logs/sql-trace");
    // getter and setter
}
```

`SqlTraceEntry`:

```java
public record SqlTraceEntry(
        String requestId,
        String pageId,
        OffsetDateTime executedAt,
        long elapsedMillis,
        String sql) {
}
```

`SqlTraceStore`:

```java
void append(SqlTraceEntry entry) throws IOException;
List<SqlTraceEntry> find(String requestId, String pageId) throws IOException;
```

- [ ] **Step 4: Implement synchronized JSONL append and bounded lookup**

`JsonLineSqlTraceStore` must:

- create the configured directory
- use `Clock` for date selection
- write `sql-trace-yyyy-MM-dd.jsonl`
- guard append with one private `ReentrantLock`
- serialize one entry per UTF-8 line
- search only today and yesterday
- ignore missing files
- deserialize each line independently
- warn and skip malformed lines
- return only exact `requestId` and `pageId` matches

Use `Files.writeString(..., CREATE, WRITE, APPEND)` and `Files.newBufferedReader`.

- [ ] **Step 5: Run store tests and verify GREEN**

Run:

```powershell
.\gradlew.bat test --tests "*JsonLineSqlTraceStoreTests"
```

Expected: all storage tests pass.

- [ ] **Step 6: Commit**

Use the Lore commit format and note that retention and network storage remain out of scope.

### Task 4: Request-Aware SQL Recorder

**Files:**
- Create: `src/main/java/com/example/admin/sqltrace/jdbc/SqlTraceRecorder.java`
- Test: `src/test/java/com/example/admin/sqltrace/jdbc/SqlTraceRecorderTests.java`

- [ ] **Step 1: Write failing recorder tests**

Use a fake `SqlTraceStore`.

```java
@Test
void recordsRenderedSqlForActiveContext() {
    SqlCaptureContextHolder.set(new SqlCaptureContext("request-1", "page01", false));

    recorder.record(
            "select * from admin_menu where menu_name = ?",
            Map.of(1, "Admin"),
            12_400_000L,
            OffsetDateTime.parse("2026-06-25T14:20:31+09:00"));

    assertThat(store.entries()).singleElement().satisfies(entry -> {
        assertThat(entry.requestId()).isEqualTo("request-1");
        assertThat(entry.pageId()).isEqualTo("page01");
        assertThat(entry.elapsedMillis()).isEqualTo(12);
        assertThat(entry.sql()).contains("menu_name = 'Admin'");
    });
}

@Test
void skipsPausedOrMissingContext() {
    recorder.record("select 1", Map.of(), 1_000_000L, now);
    assertThat(store.entries()).isEmpty();

    SqlCaptureContextHolder.set(new SqlCaptureContext("request-1", "page01", true));
    recorder.record("select 1", Map.of(), 1_000_000L, now);
    assertThat(store.entries()).isEmpty();
}

@Test
void storageFailureDoesNotEscape() {
    store.failWith(new IOException("disk full"));
    SqlCaptureContextHolder.set(new SqlCaptureContext("request-1", "page01", false));

    assertThatCode(() -> recorder.record("select 1", Map.of(), 1_000_000L, now))
            .doesNotThrowAnyException();
}
```

Clear the context in `@AfterEach`.

- [ ] **Step 2: Run recorder tests and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "*SqlTraceRecorderTests"
```

Expected: compilation failure because `SqlTraceRecorder` does not exist.

- [ ] **Step 3: Implement recorder**

`SqlTraceRecorder.record(...)` must:

```java
Optional<SqlCaptureContext> current = SqlCaptureContextHolder.current();
if (current.isEmpty() || current.get().sqlCapturePaused()) {
    return;
}

SqlTraceEntry entry = new SqlTraceEntry(
        current.get().requestId(),
        current.get().pageId(),
        executedAt,
        TimeUnit.NANOSECONDS.toMillis(elapsedNanos),
        renderer.render(sql, parameters));

try {
    store.append(entry);
} catch (IOException exception) {
    log.warn("Failed to append SQL trace. requestId={} pageId={}",
            entry.requestId(), entry.pageId(), exception);
}
```

- [ ] **Step 4: Run recorder tests and verify GREEN**

Run:

```powershell
.\gradlew.bat test --tests "*SqlTraceRecorderTests"
```

Expected: all recorder tests pass.

- [ ] **Step 5: Commit**

Use the Lore commit format and record paused/missing-context/storage-failure coverage.

### Task 5: JDBC DataSource and PreparedStatement Proxies

**Files:**
- Create: `src/main/java/com/example/admin/sqltrace/jdbc/SqlTraceDataSource.java`
- Create: `src/main/java/com/example/admin/sqltrace/jdbc/SqlTraceConnectionHandler.java`
- Create: `src/main/java/com/example/admin/sqltrace/jdbc/SqlTracePreparedStatementHandler.java`
- Test: `src/test/java/com/example/admin/sqltrace/jdbc/SqlTraceJdbcProxyTests.java`

- [ ] **Step 1: Write failing JDBC proxy tests**

Use an H2 `JdbcDataSource` wrapped by `SqlTraceDataSource` and a capturing recorder.

```java
@Test
void capturesSuccessfulExecuteQueryWithBoundValues() throws Exception {
    try (Connection connection = dataSource.getConnection();
         PreparedStatement statement = connection.prepareStatement(
                 "select id from test_menu where name = ?")) {
        statement.setString(1, "Admin");
        statement.executeQuery();
    }

    assertThat(recorder.calls()).singleElement().satisfies(call -> {
        assertThat(call.sql()).contains("where name = ?");
        assertThat(call.parameters()).containsEntry(1, "Admin");
        assertThat(call.elapsedNanos()).isPositive();
    });
}

@Test
void capturesExecuteOnlyWhenItReturnsAResultSet() throws Exception {
    execute("select id from test_menu", true);
    execute("update test_menu set name = 'Changed'", false);

    assertThat(recorder.calls()).hasSize(1);
}

@Test
void doesNotCaptureFailedQueryOrUpdateMethods() {
    assertThatThrownBy(() -> executeBrokenSelect()).isInstanceOf(SQLException.class);
    executeUpdate();

    assertThat(recorder.calls()).isEmpty();
}

@Test
void clearParametersRemovesPreviouslyBoundValues() throws Exception {
    // bind index 1, clear, bind a new value, execute, then assert only the new value was captured
}
```

- [ ] **Step 2: Run JDBC proxy tests and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "*SqlTraceJdbcProxyTests"
```

Expected: compilation failure because JDBC proxy classes do not exist.

- [ ] **Step 3: Implement the DataSource and Connection proxy**

`SqlTraceDataSource` extends `AbstractDataSource`, delegates `getConnection`, and returns a JDK proxy implementing `Connection`.

`SqlTraceConnectionHandler` must intercept every `prepareStatement(String, ...)` overload:

```java
Object prepared = invokeDelegate(method, args);
if (prepared instanceof PreparedStatement statement && args[0] instanceof String sql) {
    return Proxy.newProxyInstance(
            statement.getClass().getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            new SqlTracePreparedStatementHandler(statement, sql, recorder));
}
return prepared;
```

Delegate `unwrap`, `isWrapperFor`, `equals`, `hashCode`, and `toString` correctly.

- [ ] **Step 4: Implement PreparedStatement interception**

Maintain `Map<Integer, Object> parameters = new HashMap<>()`.

Binding rules:

- any method named `set...` whose first argument is `Integer` stores argument 2
- `setNull(index, sqlType)` stores `null`
- stream/reader setters store the stream/reader object
- `clearParameters()` clears the map after successful delegate invocation
- `addBatch()` is delegated and never recorded

Execution rules:

```java
executeQuery(): record only after successful return
execute(): record only after successful return of Boolean.TRUE
executeUpdate()/executeLargeUpdate()/executeBatch(): never record
```

Measure with `System.nanoTime()` immediately around the delegated execution. Copy the parameter map before passing it to the recorder.

- [ ] **Step 5: Run JDBC proxy tests and verify GREEN**

Run:

```powershell
.\gradlew.bat test --tests "*SqlTraceJdbcProxyTests"
```

Expected: all JDBC proxy tests pass.

- [ ] **Step 6: Commit**

Use the Lore commit format and state that successful result-producing prepared statements are the capture boundary.

### Task 6: Spring Registration in the Feature Package

**Files:**
- Create: `src/main/java/com/example/admin/sqltrace/config/SqlTraceDataSourceBeanPostProcessor.java`
- Create: `src/main/java/com/example/admin/sqltrace/config/SqlTraceConfiguration.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/example/admin/sqltrace/config/SqlTraceConfigurationTests.java`

- [ ] **Step 1: Write failing Spring registration test**

Use `ApplicationContextRunner` with a test `DataSource` bean:

```java
@Test
void wrapsApplicationDataSourceExactlyOnce() {
    contextRunner
            .withBean(DataSource.class, this::h2DataSource)
            .run(context -> {
                DataSource dataSource = context.getBean(DataSource.class);
                assertThat(dataSource).isInstanceOf(SqlTraceDataSource.class);
                assertThat(((SqlTraceDataSource) dataSource).getDelegate())
                        .isNotInstanceOf(SqlTraceDataSource.class);
            });
}

@Test
void registersFilterAndDefaultLocalDirectory() {
    contextRunner.run(context -> {
        assertThat(context).hasSingleBean(SqlCaptureRequestFilter.class);
        assertThat(context.getBean(SqlTraceProperties.class).getDirectory())
                .isEqualTo(Path.of("./logs/sql-trace"));
    });
}
```

- [ ] **Step 2: Run configuration tests and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "*SqlTraceConfigurationTests"
```

Expected: compilation or assertion failure because Spring registration does not exist.

- [ ] **Step 3: Implement DataSource BeanPostProcessor**

`SqlTraceDataSourceBeanPostProcessor` must use `ObjectProvider<SqlTraceRecorder>` and:

```java
if (bean instanceof DataSource dataSource && !(dataSource instanceof SqlTraceDataSource)) {
    return new SqlTraceDataSource(dataSource, recorderProvider.getObject());
}
return bean;
```

Do not modify `MyBatisConfig`, JPA repositories, or business services.

- [ ] **Step 4: Implement feature configuration**

`SqlTraceConfiguration` must:

- use `@Configuration`
- use `@EnableConfigurationProperties(SqlTraceProperties.class)`
- register `Clock.systemDefaultZone()`
- register `NoOpSqlValueMasker`
- register `SqlParameterRenderer`
- register `JsonLineSqlTraceStore`
- register `SqlTraceRecorder`
- register `SqlCaptureRequestFilter`
- register the DataSource post-processor

Add:

```yaml
admin:
  sql-trace:
    directory: ${SQL_TRACE_DIRECTORY:./logs/sql-trace}
```

to `application.yml`.

- [ ] **Step 5: Run configuration tests and verify GREEN**

Run:

```powershell
.\gradlew.bat test --tests "*SqlTraceConfigurationTests"
```

Expected: registration tests pass without duplicate DataSource wrapping.

- [ ] **Step 6: Commit**

Use the Lore commit format and mention that all registration remains within `com.example.admin.sqltrace`.

### Task 7: Separate SQL Log Lookup API

**Files:**
- Create: `src/main/java/com/example/admin/sqltrace/api/SqlTraceLogRow.java`
- Create: `src/main/java/com/example/admin/sqltrace/api/SqlTraceLogResponse.java`
- Create: `src/main/java/com/example/admin/sqltrace/api/SqlTraceController.java`
- Test: `src/test/java/com/example/admin/sqltrace/api/SqlTraceControllerTests.java`

- [ ] **Step 1: Write failing controller tests**

Use `@WebMvcTest(SqlTraceController.class)` and a mocked `SqlTraceStore`.

```java
@Test
void returnsRowsMatchingRequestAndPage() throws Exception {
    given(store.find(REQUEST_ID, "page01")).willReturn(List.of(entry()));

    mockMvc.perform(get("/api/sql-logs")
                    .param("requestId", REQUEST_ID)
                    .param("pageId", "page01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
            .andExpect(jsonPath("$.pageId").value("page01"))
            .andExpect(jsonPath("$.logs[0].sql").value("select 1"))
            .andExpect(jsonPath("$.logs[0].elapsedMillis").value(4));
}

@Test
void rejectsNonUuidRequestId() throws Exception {
    mockMvc.perform(get("/api/sql-logs")
                    .param("requestId", "not-a-uuid")
                    .param("pageId", "page01"))
            .andExpect(status().isBadRequest());
}

@Test
void returnsEmptyArrayWhenNoRowsExist() throws Exception {
    given(store.find(REQUEST_ID, "page01")).willReturn(List.of());
    // assert 200 and $.logs is empty
}

@Test
void returnsServerErrorWhenFileReadFails() throws Exception {
    given(store.find(REQUEST_ID, "page01")).willThrow(new IOException("read failed"));
    // assert 500
}
```

- [ ] **Step 2: Run controller tests and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "*SqlTraceControllerTests"
```

Expected: compilation failure because API classes do not exist.

- [ ] **Step 3: Implement API records**

Use:

```java
public record SqlTraceLogRow(
        String pageId,
        OffsetDateTime executedAt,
        long elapsedMillis,
        String sql) {
}

public record SqlTraceLogResponse(
        String requestId,
        String pageId,
        List<SqlTraceLogRow> logs) {
}
```

- [ ] **Step 4: Implement controller validation and error mapping**

Controller signature:

```java
@GetMapping("/api/sql-logs")
public SqlTraceLogResponse find(
        @RequestParam String requestId,
        @RequestParam String pageId)
```

Validate with `UUID.fromString(requestId)` and `StringUtils.hasText(pageId)`. Throw:

```java
new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId must be a UUID.");
new ResponseStatusException(HttpStatus.BAD_REQUEST, "pageId is required.");
```

Convert `IOException` to:

```java
new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Failed to read SQL trace logs.",
        exception);
```

- [ ] **Step 5: Run controller tests and verify GREEN**

Run:

```powershell
.\gradlew.bat test --tests "*SqlTraceControllerTests"
```

Expected: all controller tests pass.

- [ ] **Step 6: Commit**

Use the Lore commit format and include UUID/page validation and read-failure coverage.

### Task 8: MyBatis and JPA End-to-End Capture

**Files:**
- Create: `src/test/java/com/example/admin/sqltrace/SqlTraceIntegrationTests.java`
- Modify: `src/test/resources/application.yml`

- [ ] **Step 1: Write failing integration tests**

Configure an isolated directory:

```yaml
admin:
  sql-trace:
    directory: ./build/test-sql-trace
```

Before each test, delete only `build/test-sql-trace` using Java test setup and recreate it.

Test MyBatis:

```java
MvcResult result = mockMvc.perform(get("/api/mybatis/menus/page")
                .header("X-Page-Id", "page01")
                .header("X-Sql-Capture-Paused", "false")
                .param("nameKeyword", "Paging")
                .param("page", "0")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andReturn();

String requestId = result.getResponse().getHeader("X-Request-Id");

mockMvc.perform(get("/api/sql-logs")
                .param("requestId", requestId)
                .param("pageId", "page01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.logs").isNotEmpty())
        .andExpect(jsonPath("$.logs[0].sql").value(not(containsString("?"))));
```

Test JPA with `/api/jpa/menus/page` and assert at least one returned SQL contains `admin_menu`.

Test pause:

```java
MvcResult paused = performTrackedGet("/api/mybatis/menus/page", "page01", true);
assertLookupIsEmpty(paused, "page01");

MvcResult resumed = performTrackedGet("/api/mybatis/menus/page", "page01", false);
assertLookupIsNotEmpty(resumed, "page01");
assertLookupFor(paused, "page01").isStillEmpty();
```

Also assert:

- business paging JSON fields are unchanged
- request without `X-Page-Id` has no `X-Request-Id`
- SQL log endpoint does not create a new trace header
- a POST grid-save request with trace headers does not create a trace context

- [ ] **Step 2: Run integration tests and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "*SqlTraceIntegrationTests"
```

Expected: failures until the proxy registration and API are fully connected; confirm failures concern missing trace behavior rather than test data.

- [ ] **Step 3: Fix only integration defects**

Allowed fixes are limited to `com.example.admin.sqltrace` and SQL trace configuration. Do not modify business response DTOs, menu services, repositories, or mapper XML.

Likely integration checks:

- Hibernate or MyBatis may call `PreparedStatement.execute()` rather than `executeQuery()`
- proxied `unwrap` and `isWrapperFor` must delegate correctly
- the JSONL append must complete synchronously before the business response returns
- parameter collection must include `setObject`, `setString`, `setLong`, and `setNull`

- [ ] **Step 4: Run integration tests and verify GREEN**

Run:

```powershell
.\gradlew.bat test --tests "*SqlTraceIntegrationTests"
```

Expected: MyBatis, JPA, pause/resume, header, and unchanged-response tests pass.

- [ ] **Step 5: Commit**

Use the Lore commit format and identify both ORM paths in `Tested:`.

### Task 9: Full Verification and Documentation Alignment

**Files:**
- Modify only if behavior differs: `docs/superpowers/specs/2026-06-25-page-sql-trace-design.md`
- Modify: `docs/superpowers/plans/2026-06-25-page-sql-trace.md` to check completed steps during execution

- [ ] **Step 1: Verify package isolation**

Run:

```powershell
rg -n "SqlTrace|SqlCapture" src/main/java/com/example/admin --glob "!sqltrace/**"
```

Expected: no production implementation references outside `com.example.admin.sqltrace`.

- [ ] **Step 2: Verify no business API contract changes**

Run:

```powershell
git diff 472896e -- src/main/java/com/example/admin/menu src/main/java/com/example/admin/user src/main/java/com/example/admin/role
```

Expected: empty diff.

- [ ] **Step 3: Run targeted SQL trace tests**

Run:

```powershell
.\gradlew.bat test --tests "*sqltrace*"
```

Expected: all SQL trace unit, MVC, and integration tests pass.

- [ ] **Step 4: Run the complete test suite**

Run:

```powershell
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Inspect generated JSONL**

Run:

```powershell
Get-ChildItem build\test-sql-trace
Get-Content build\test-sql-trace\sql-trace-*.jsonl | Select-Object -First 5
```

Expected: each line is one JSON object containing `requestId`, `pageId`, `executedAt`, `elapsedMillis`, and parameter-complete `sql`.

- [ ] **Step 6: Run static diff checks**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only planned files are changed.

- [ ] **Step 7: Commit final alignment changes**

If no alignment changes are needed, do not create an empty commit. Otherwise use a Lore commit with exact verification evidence.
