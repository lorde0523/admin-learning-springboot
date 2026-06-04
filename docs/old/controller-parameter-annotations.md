# Controller/API 파라미터 어노테이션 정리

Controller에서 자주 쓰는 파라미터는 `@RequestBody` DTO와 역할이 다릅니다. URL path, query string, header, cookie, form 값을 명확하게 구분해서 받습니다.

## 기본 매핑

```java
@GetMapping("/api/jpa/menus/{id}")
public MenuResponse find(@PathVariable Long id) {
    return menuService.find(id);
}
```

```java
@GetMapping("/api/jpa/menus")
public List<MenuResponse> search(
        @RequestParam(required = false) String nameKeyword) {
    return menuService.search(nameKeyword);
}
```

- `@PathVariable`: URL 경로 일부를 받습니다. 예: `/menus/{id}`
- `@RequestParam`: query string 또는 form parameter를 받습니다. 예: `/menus?nameKeyword=admin`
- `@RequestBody`: JSON body를 DTO로 받습니다.
- `@RequestHeader`: HTTP header 값을 받습니다.
- `@CookieValue`: cookie 값을 받습니다.
- `@ModelAttribute`: query/form 값을 DTO에 바인딩합니다. 검색 조건 DTO에 자주 씁니다.

권장:
- 단건 조회, 수정, 삭제의 식별자는 `@PathVariable`을 사용합니다.
- 검색 조건은 값이 많아지면 `@ModelAttribute SearchCondition`으로 묶습니다.
- JSON 저장 요청은 `@Valid @RequestBody SaveRequest`를 사용합니다.

## Controller 파라미터 Validation

Controller method parameter에 validation을 직접 적용하려면 Controller class에 `@Validated`를 붙입니다.

```java
@Validated
@RestController
public class JpaAdminMenuController {

    @GetMapping("/api/jpa/menus/{id}")
    public MenuResponse find(@PathVariable @Positive Long id) {
        return menuService.find(id);
    }

    @GetMapping("/api/jpa/menus")
    public List<MenuResponse> search(
            @RequestParam(required = false) @Size(max = 100) String nameKeyword) {
        return menuService.search(nameKeyword);
    }
}
```

- `@Positive`: path id가 양수인지 검증
- `@Size(max = N)`: 검색어 길이 제한
- `@Pattern`: 코드성 query parameter 형식 제한
- `@Min`, `@Max`: page, size 같은 숫자 범위 제한
- `@DateTimeFormat`: 문자열 날짜를 `LocalDate`, `LocalDateTime`으로 변환

권장:
- `@PathVariable Long id`에는 `@Positive`를 붙이는 편이 안전합니다.
- 검색어는 `@Size(max = N)`으로 길이를 제한합니다.
- query parameter가 필수면 `@RequestParam` 기본값인 `required = true`를 사용하고, 선택값이면 `required = false`를 명시합니다.

## 날짜 파라미터

```java
@GetMapping("/api/jpa/users")
public List<UserResponse> search(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom) {
    return userService.search(createdFrom);
}
```

- `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`: `yyyy-MM-dd` 형식
- `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)`: ISO date-time 형식
- `@PastOrPresent`, `@FutureOrPresent`: 날짜 범위 성격 검증

권장:
- API 날짜 query parameter는 ISO 형식을 기본으로 맞춥니다.
- 기간 검색은 `from`, `to` 두 값을 DTO로 묶고, `from <= to` 같은 상호 검증은 서비스 또는 커스텀 validator에서 처리합니다.

## 페이징과 정렬

Spring Data Pageable을 쓸 때:

```java
@GetMapping("/api/jpa/users")
public Page<UserResponse> search(
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
        Pageable pageable) {
    return userService.search(pageable);
}
```

- `Pageable`: `page`, `size`, `sort` query parameter를 자동 바인딩
- `@PageableDefault`: 기본 page size, sort 설정
- `@SortDefault`: 정렬 기본값을 더 세밀하게 설정

권장:
- 운영 API는 page size 상한을 둡니다.
- sort 허용 필드는 서비스나 repository 계층에서 명시적으로 제한하는 편이 안전합니다.
- ag-Grid 서버사이드 조회는 별도 검색 DTO로 `startRow`, `endRow`, `sortModel`, `filterModel`을 받는 방식이 더 명확할 수 있습니다.

## Header와 인증 보조 값

```java
@GetMapping("/api/jpa/users/me")
public UserResponse me(@RequestHeader("X-User-Id") String userId) {
    return userService.me(userId);
}
```

- `@RequestHeader("X-User-Id")`: 특정 header 값을 받습니다.
- `@RequestHeader(value = "X-Trace-Id", required = false)`: 선택 header
- `@AuthenticationPrincipal`: Spring Security 인증 principal을 받습니다.

권장:
- 실제 로그인 사용자는 header 직접 전달보다 Spring Security의 `@AuthenticationPrincipal` 또는 AuditorAware 연계를 우선합니다.
- trace id, tenant id처럼 gateway에서 주입하는 값은 `@RequestHeader`로 받을 수 있습니다.

## 검색 조건 DTO

query parameter가 많아지면 `@ModelAttribute` DTO로 묶습니다.

```java
@Getter
@Setter
@NoArgsConstructor
public class MenuSearchCondition {

    @Size(max = 100)
    private String nameKeyword;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createdFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createdTo;
}
```

```java
@GetMapping("/api/jpa/menus")
public List<MenuResponse> search(@Valid @ModelAttribute MenuSearchCondition condition) {
    return menuService.search(condition);
}
```

권장:
- 검색 DTO도 record가 아니라 Lombok class로 만듭니다.
- query parameter는 대부분 선택값이므로 필드에 `@NotNull`을 남발하지 않습니다.
- 필수 검색 조건이 있는 API에서만 `@NotNull`, `@NotBlank`를 사용합니다.

## 자주 쓰는 import

```java
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
```
