# Validation 어노테이션 정리

DTO는 Lombok class로 만들고, 가능한 값 검증은 `jakarta.validation` 어노테이션으로 먼저 처리합니다. 서비스에서는 DB 존재 여부, 권한, 상태 충돌처럼 저장소 조회가 필요한 검증만 담당합니다.

## 공통 사용 규칙

- Controller의 request body에는 `@Valid @RequestBody`를 붙입니다.
- List 내부 DTO 검증은 `List<@NotNull @Valid RowDto>`처럼 요소 타입에 붙입니다.
- row 그룹 자체가 `null`이면 grid-save에서는 작업 없음으로 처리할 수 있습니다.
- row 그룹 내부의 `null` 요소는 `@NotNull`로 막습니다.
- ID/key 누락은 DTO에서 `@NotNull`로 막고, 복합 key 조립 결과가 null인 경우는 공통 저장 로직에서 한 번 더 막습니다.

## 문자열

```java
@NotBlank
@Size(max = 50)
private String menuCode;
```

- `@NotBlank`: null, 빈 문자열, 공백 문자열 금지
- `@NotEmpty`: null, 빈 문자열 금지, 공백은 허용
- `@Size(min = 1, max = 50)`: 길이 제한
- `@Pattern(regexp = "...")`: 코드, 전화번호, 영문/숫자 조합 등 정규식 검증
- `@Email`: 이메일 형식 검증

권장:
- 이름, 코드, 제목처럼 필수 문자열은 `@NotBlank`
- DB 컬럼 길이가 정해진 값은 `@Size(max = 컬럼길이)`
- 업무 코드 형식이 있으면 `@Pattern`

## 숫자

```java
@NotNull
@Positive
private Long id;

@PositiveOrZero
private int sortOrder;
```

- `@NotNull`: null 금지
- `@Positive`: 0보다 큰 값
- `@PositiveOrZero`: 0 이상
- `@Min(0)`, `@Max(999)`: 최소/최대값
- `@DecimalMin("0.0")`, `@DecimalMax("100.0")`: BigDecimal, double 계열 범위
- `@Digits(integer = 10, fraction = 2)`: 정수/소수 자릿수 제한

권장:
- ID/key는 `@NotNull`을 기본으로 사용합니다.
- 양수 ID 정책이면 `@Positive`를 추가합니다.
- 정렬 순서처럼 0을 허용하면 `@PositiveOrZero`를 사용합니다.

## Boolean

```java
@NotNull
private Boolean enabled;
```

- `@NotNull`: true/false를 반드시 선택해야 하는 값
- `@AssertTrue`: 반드시 true여야 하는 동의 항목
- `@AssertFalse`: 반드시 false여야 하는 항목

권장:
- 화면에서 체크박스 값이 반드시 넘어와야 하면 primitive `boolean`보다 `Boolean + @NotNull`을 사용합니다.

## 날짜와 시간

```java
@NotNull
@FutureOrPresent
private LocalDate startDate;
```

- `@Past`: 과거 날짜
- `@PastOrPresent`: 과거 또는 현재
- `@Future`: 미래 날짜
- `@FutureOrPresent`: 미래 또는 현재

권장:
- 등록일, 수정일 같은 감사 필드는 DTO에서 받지 말고 JPA auditing으로 처리합니다.
- 업무 시작일/종료일처럼 화면 입력값일 때만 DTO에 검증을 둡니다.

## 컬렉션

```java
@Size(max = 1000)
private List<@NotNull @Valid MenuGridRow> createdRows;
```

- `@NotEmpty`: null 또는 빈 컬렉션 금지
- `@Size(min = 1, max = 1000)`: 개수 제한
- `@Valid`: 내부 DTO까지 검증
- 요소 `@NotNull`: 컬렉션 안 null 요소 금지

권장:
- grid-save row 그룹은 null이면 작업 없음으로 볼 수 있으므로 필드 자체에는 `@NotNull`을 붙이지 않습니다.
- row 그룹 내부 요소에는 `@NotNull @Valid`를 붙입니다.
- 대량 저장 API는 `@Size(max = N)`으로 한 번에 들어오는 row 수를 제한하는 편이 안전합니다.

## 중첩 DTO

```java
@Valid
@NotNull
private SearchCondition condition;
```

- `@Valid`: 중첩 객체의 validation 실행
- `@NotNull`: 중첩 객체 자체의 null 금지

권장:
- 조건 객체가 없어도 되는 검색 API는 `@NotNull` 없이 `@Valid`만 둡니다.
- 저장 API에서 필수 하위 객체라면 `@NotNull @Valid`를 같이 둡니다.

## Grid 저장 DTO 예시

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuGridRow extends MenuRequest {

    @NotNull
    @Positive
    private Long id;
}
```

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuGridSaveRequest {

    @Size(max = 1000)
    private List<@NotNull @Valid MenuGridRow> createdRows = new ArrayList<>();

    @Size(max = 1000)
    private List<@NotNull @Valid MenuGridRow> updatedRows = new ArrayList<>();

    @Size(max = 1000)
    private List<@NotNull @Positive Long> deletedIds = new ArrayList<>();
}
```

복합 ID를 화면에서 각각 받는 경우:

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleGridRow {

    @NotNull
    private Long userId;

    @NotNull
    private Long roleId;
}
```

이 경우 DTO는 각 key 필드를 검증하고, 서비스에서는 `row -> new UserRoleId(row.getUserId(), row.getRoleId())`처럼 복합 key 객체를 조립합니다.
