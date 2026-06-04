# 관리자 데이터 변경과 Grid 저장 기준

작성일: 2026-06-04

## 목적

이 문서는 기존 `docs`에 흩어진 관리자 데이터 변경 기준을 하나로 묶은 현재 프로젝트의 기준 문서입니다. 등록, 수정, 삭제, validation, JPA/MyBatis 경계, ag-Grid 저장, 복합 ID, `rows + rowStatus` 요청 처리 방향을 한 흐름으로 정리합니다.

## 기본 아키텍처

- 등록, 수정, 삭제, 관계 매핑 저장은 JPA로 처리합니다.
- 단순 조회도 먼저 JPA repository method, JPQL, EntityGraph, Projection, Pageable, Specification, Criteria API를 검토합니다.
- MyBatis는 SQL로 표현하는 편이 더 명확한 복잡 조회에만 사용합니다.
- MyBatis controller/service는 조회 전용 API에만 둡니다.
- 목표 구조에서 MyBatis XML은 조회 전용입니다. `<insert>`, `<update>`, `<delete>` SQL을 추가하지 않습니다.
- QueryDSL은 사용하지 않습니다.
- Entity와 DTO는 Java `record` 대신 Lombok class로 작성합니다.

## Controller와 DTO 기준

- Controller의 request body에는 `@Valid @RequestBody`를 붙입니다.
- List 내부 DTO 검증은 `List<@NotNull @Valid RowDto>`처럼 요소 타입에 붙입니다.
- Request DTO는 JSON 바인딩을 위해 `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`를 사용합니다.
- Response DTO는 `@Getter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`를 우선 사용합니다.
- DTO는 가능하면 nested class가 아니라 API 단위 DTO 패키지 아래 개별 Java 파일로 둡니다.
- DTO는 값 형식 검증을 담당합니다. DB 존재 여부, 권한, 상태 충돌처럼 저장소 조회가 필요한 검증은 service에서 처리합니다.

권장 validation 기준:

- 필수 문자열: `@NotBlank`
- 길이 제한: `@Size(max = N)`
- 필수 ID: `@NotNull`
- 양수 ID 정책: `@Positive`
- 필수 Boolean: wrapper `Boolean`과 `@NotNull`
- row 목록 크기 제한: `@Size(max = N)`
- 감사 필드: request DTO에서 받지 않고 JPA auditing으로 처리

## Entity와 Mapper 기준

- JPA Entity에는 `@Data`를 사용하지 않습니다.
- JPA Entity는 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 정적 팩토리 메서드, 의미 있는 상태 변경 메서드를 기본으로 둡니다.
- Entity 상태 변경은 setter 전면 개방보다 도메인 메서드로 처리합니다.
- DTO와 Entity 변환은 도메인별 MapStruct mapper가 담당합니다.
- mapper에는 업무 검증 로직을 넣지 않습니다.
- 단순 필드 복사라도 Entity 변경은 mapper에서 Entity의 상태 변경 메서드를 호출하는 방식으로 유지합니다.
- response 변환 책임은 DTO의 `from(Entity)`보다 mapper의 `toResponse(Entity)`로 통일합니다.

## 등록, 수정, 삭제 정책

단건 API 기준:

- 등록: key를 먼저 추출하고, 이미 존재하면 예외 처리합니다.
- 수정: 기존 데이터를 조회한 뒤 없으면 예외 처리하고, 있으면 managed entity를 변경합니다.
- 삭제: 기존 데이터를 조회한 뒤 없으면 예외 처리하고, 있으면 삭제합니다.

grid batch 기준:

- 요청 자체가 잘못된 경우에는 400 예외로 처리합니다.
- DB 상태와 맞지 않는 row는 업무 정책에 따라 skip 메시지 또는 strict 예외로 처리합니다.
- 현재 공통 기본값은 `GridSaveFailureMode.SKIP_AND_MESSAGE`입니다.

공통화 기준:

- 공통 executor가 특정 domain repository나 MyBatis store에 직접 의존하지 않게 합니다.
- key 추출, Entity 조회, DTO 변환, Entity 변경은 service에서 lambda로 넘깁니다.
- JPA/MyBatis 차이를 공통 executor 안에 숨기지 않습니다.
- 현재 저장 표준은 JPA이므로 grid 저장 공통화도 JPA repository 기반 `GridSaveExecutor`를 사용합니다.

## Grid 저장 표준

현재 백엔드 내부 표준 요청 구조는 상태별 배열 분리 방식입니다.

```json
{
  "createdRows": [],
  "updatedRows": [],
  "deletedIds": []
}
```

현재 유지할 API:

```text
POST /api/jpa/menus/grid-save
```

응답 구조:

```json
{
  "createdCount": 0,
  "updatedCount": 0,
  "deletedCount": 0,
  "messages": []
}
```

처리 순서:

1. `deletedIds`의 null과 중복을 검증합니다.
2. `createdRows`, `updatedRows`의 null row, null key, 중복 key를 검증합니다.
3. create/update/delete key 충돌을 검증합니다.
4. 삭제 대상은 JPA로 삭제합니다.
5. 등록 row는 Entity로 만들어 `saveAll` 합니다.
6. 수정 row는 id로 Entity를 조회한 뒤 managed entity의 상태 변경 메서드를 호출합니다.
7. 처리 건수와 skip 메시지를 응답합니다.

`GridSaveExecutor`의 책임:

- null row 그룹은 작업 없음으로 처리
- row 그룹 내부 null 검증
- 등록, 수정, 삭제 key의 null/중복 검증
- 등록/수정/삭제 key 충돌 검증
- 삭제/수정 대상 존재 확인
- 이미 등록된 key skip 및 메시지 반환
- 삭제, 등록, 수정 count 반환

`GridSaveExecutor`가 알지 않아야 하는 것:

- 도메인별 필드 이름
- DTO와 Entity의 상세 변환 규칙
- Entity 상태 변경 메서드의 내부 내용
- 응답 DTO의 최종 shape

## 복합 ID 기준

단일 ID와 `@EmbeddedId` 복합 ID는 같은 방식으로 처리합니다. 핵심은 각 row와 entity에서 비교 가능한 key 객체를 만들어 `GridSaveExecutor`에 넘기는 것입니다.

단일 ID create 예시:

```java
gridSaveExecutor.create(
        request.getCreatedRows(),
        menuRepository,
        MenuGridRow::getId,
        AdminMenu::getId,
        menuMapper::toEntity);
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

복합 key 필드는 DTO에서 각각 `@NotNull`로 검증하고, key 객체 조립 결과는 service 또는 공통 저장 로직에서 한 번 더 확인합니다.

## rows + rowStatus 요청 처리 방향

프론트엔드가 실무 화면에서 다음처럼 단일 배열과 row 상태를 보내는 경우가 있을 수 있습니다.

```json
{
  "rows": [
    { "rowStatus": "CREATE" },
    { "rowStatus": "UPDATE" },
    { "rowStatus": "DELETE" }
  ]
}
```

이 경우 새 저장 executor를 만들지 않습니다. 별도 adapter/helper에서 요청을 내부 표준 구조로 분리한 뒤 기존 `GridSaveExecutor`를 재사용합니다.

후속 구현 후보:

- `GridRowStatus`
- `GridSeparatedRows<C, U, ID>`
- `GridRowSeparator`
- `{Domain}TypedGridSaveRequest`
- `{Domain}TypedGridRow`

권장 흐름:

```text
rows + rowStatus request
-> GridRowSeparator
-> createdRows / updatedRows / deletedIds
-> GridSaveExecutor
-> JPA CUD
```

기존 `POST /api/jpa/menus/grid-save` API는 깨지지 않게 유지합니다. `rows + rowStatus`를 도입할 때는 새 endpoint를 추가하거나 controller 내부 변환 계층으로 연결합니다.

## 검증 명령

```powershell
rg -n "public record| record " src\main\java
rg -n "<insert|<update|<delete" src\main\resources\mybatis
.\gradlew test
```

기대 결과:

- main Java source에 `record`가 없어야 합니다.
- MyBatis XML에 쓰기 SQL이 없어야 합니다.
- MyBatis controller/service는 조회 전용이어야 합니다.
- 전체 테스트가 통과해야 합니다.
