# 리팩토링 제안서

작성일: 2026-05-28

## 목적

현재 구조는 JPA CUD, MyBatis 복잡 조회, ag-Grid 공통 저장, MapStruct mapper 방향까지 잡혀 있습니다. 앞으로 실무 테이블의 컬럼 수가 많아지고 복합 ID, grid 저장 케이스가 늘어날 때 유지보수 비용을 줄이기 위한 리팩토링 후보를 정리합니다.

## 현재 구조 요약

- 조회
  - 단순 조회는 JPA repository/specification 사용
  - 복잡 조회는 MyBatis store 사용
- 저장
  - 등록, 수정, 삭제는 JPA 사용
  - ag-Grid 저장은 `GridSaveExecutor`로 공통화
- DTO
  - record 미사용
  - Lombok class 사용
  - API 기준 DTO 패키지 분리
- Mapper
  - MyBatis 연결 interface는 `store`
  - DTO와 Entity 변환은 `mapper`
  - MapStruct `@Mapper(componentModel = "spring")` 사용 가능

## 리팩토링 후보

### 1. GridSaveExecutor 파라미터 객체화

현재 `GridSaveExecutor.save(...)`는 인자가 많습니다.

```java
gridSaveExecutor.save(
        createdRows,
        updatedRows,
        deletedIds,
        repository,
        createKeyReader,
        updateKeyReader,
        entityKeyReader,
        createMapper,
        updateApplier,
        missingResourceMessage,
        failureMode);
```

컬럼 수나 도메인이 늘어나는 것과 별개로 호출부가 길어져 가독성이 떨어질 수 있습니다.

제안:

```java
GridSaveCommand<Id, CreateRow, UpdateRow, Entity> command =
        GridSaveCommand.<Id, CreateRow, UpdateRow, Entity>builder()
                .createdRows(request.getCreatedRows())
                .updatedRows(request.getUpdatedRows())
                .deletedIds(request.getDeletedIds())
                .repository(repository)
                .createKeyReader(mapper::toId)
                .updateKeyReader(mapper::toId)
                .entityKeyReader(Entity::getId)
                .createMapper(mapper::toEntity)
                .updateApplier(mapper::updateEntity)
                .failureMode(GridSaveFailureMode.SKIP_AND_MESSAGE)
                .missingResourceMessage("존재하지 않는 데이터가 포함되어 있습니다.")
                .build();

GridSaveResult result = gridSaveExecutor.save(command);
```

효과:
- 호출부 의미가 명확해집니다.
- create/update key reader가 왜 2개인지 이름으로 드러납니다.
- 옵션이 늘어나도 메서드 시그니처가 계속 길어지지 않습니다.

우선순위:
- 높음
- grid 저장 API가 3개 이상 늘어나기 전에 적용하는 편이 좋습니다.

### 2. GridSaveExecutor 내부 역할 분리

현재 `GridSaveExecutor`는 다음 책임을 모두 갖습니다.

- null 그룹 처리
- key 중복 검증
- created/updated/deleted 충돌 검증
- DB 존재 여부 확인
- 저장/수정/삭제 실행
- 메시지 생성
- 실패 정책 분기

제안:

```text
GridSaveExecutor
GridSaveValidator
GridSaveMessageFactory
GridSaveCommand
GridSaveResult
```

다만 지금 당장 모두 분리하면 파일 수가 늘어납니다. 먼저 `GridSaveCommand`만 추가하고, 이후 executor가 더 커질 때 validator/message factory를 분리하는 순서가 좋습니다.

우선순위:
- 중간
- 지금은 큰 문제라기보다 성장 대비 개선입니다.

### 3. Service 생성자 주입을 Lombok으로 통일

현재 실제 서비스 일부는 직접 생성자 주입이고, 문서 예제는 `@RequiredArgsConstructor`로 정리했습니다.

제안:

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaAdminMenuService {

    private final AdminMenuRepository menuRepository;
    private final GridSaveExecutor gridSaveExecutor;
    private final MenuMapper menuMapper;
}
```

효과:
- 반복 생성자 코드 제거
- 문서 예제와 실제 코드 스타일 일치

주의:
- 생성자에 로직이 필요한 서비스는 직접 생성자를 유지합니다.
- 단순 DI만 하는 경우에만 `@RequiredArgsConstructor`를 사용합니다.

우선순위:
- 중간
- 기능 영향이 작으므로 별도 작은 커밋으로 진행하기 좋습니다.

### 4. Response 변환 위치 통일

현재 `MenuResponse`에는 `from(AdminMenu)`가 있고, `MenuMapper`에도 `toResponse(AdminMenu)`가 있습니다. 같은 변환 책임이 DTO와 mapper 양쪽에 존재합니다.

제안:
- DTO는 순수 데이터 구조로 둡니다.
- Entity 변환은 mapper로 통일합니다.
- 기존 `from(...)`은 점진적으로 제거합니다.

권장 방향:

```java
menuMapper.toResponse(menu);
roleMapper.toResponse(role);
userMapper.toResponse(user);
```

효과:
- 변환 책임 위치가 명확해집니다.
- MapStruct 적용 범위가 자연스럽게 넓어집니다.
- DTO가 Entity를 import하지 않아 계층 의존성이 줄어듭니다.

우선순위:
- 높음
- MapStruct를 본격적으로 쓸 거라면 먼저 정리하는 편이 좋습니다.

### 5. DTO 구조를 도메인별로 점진 분리

Menu는 API 기준 DTO 패키지 아래 개별 파일로 정리되어 있습니다. User, Role은 아직 `UserDtos`, `RoleDtos` nested class 구조입니다.

제안:
- 새 API 또는 크게 수정하는 API부터 개별 DTO 파일 방식으로 전환합니다.
- 기존 User, Role은 한번에 다 바꾸기보다 작업이 생길 때 옮깁니다.

우선순위:
- 중간
- 사용자 실무 스타일에 맞추려면 장기적으로 전환하는 편이 좋습니다.

## MapStruct 적용 의견

### 결론

컬럼이 약 40개라면 MapStruct 사용을 추천합니다. 수동으로 `create(...)`, `update(...)`, `builder()`에 40개 필드를 계속 나열하면 가독성도 떨어지고, 컬럼 추가/삭제 때 누락 위험도 커집니다.

다만 Entity에 무조건 setter를 열어서 MapStruct가 직접 값을 밀어 넣게 하는 방식은 추천하지 않습니다. JPA Entity의 상태 변경은 도메인 메서드로 유지하는 편이 안전합니다.

추천 방식은 다음입니다.

```text
DTO -> MapStruct -> Command/Values -> Entity.create(command), Entity.update(command)
Entity -> MapStruct -> Response
```

### 권장 패턴 1. MapStruct로 Command 객체 생성

컬럼이 많은 등록/수정 DTO를 바로 Entity에 넣지 말고, 중간 command 객체를 둡니다.

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuSaveCommand {

    private Long id;
    private String menuCode;
    private String menuName;
    private Long parentMenuId;
    private int sortOrder;
    private Boolean enabled;
}
```

Mapper:

```java
@Mapper(componentModel = "spring")
public interface MenuMapper extends BaseMapper<MenuGridRow, AdminMenu> {

    MenuSaveCommand toCommand(MenuGridRow row);

    MenuResponse toResponse(AdminMenu menu);

    @Override
    default AdminMenu toEntity(MenuGridRow row) {
        return AdminMenu.create(toCommand(row));
    }

    @Override
    default void updateEntity(@MappingTarget AdminMenu menu, MenuGridRow row) {
        menu.update(toCommand(row));
    }
}
```

Entity:

```java
public static AdminMenu create(MenuSaveCommand command) {
    return new AdminMenu(
            command.getId(),
            command.getMenuCode(),
            command.getMenuName(),
            command.getParentMenuId(),
            command.getSortOrder(),
            command.getEnabled());
}

public void update(MenuSaveCommand command) {
    this.menuName = command.getMenuName();
    this.parentMenuId = command.getParentMenuId();
    this.sortOrder = command.getSortOrder();
    this.enabled = command.getEnabled();
}
```

장점:
- 40개 컬럼을 mapper에서 자동 매핑할 수 있습니다.
- Entity는 여전히 도메인 메서드로만 상태가 변경됩니다.
- update 메서드 인자가 40개로 길어지는 문제를 막습니다.

주의:
- command class가 너무 Entity와 1:1이면 DTO가 하나 더 늘어난 느낌이 들 수 있습니다.
- 그래도 40개 컬럼이면 중간 객체를 두는 편이 유지보수에 유리합니다.

### 권장 패턴 2. Response는 MapStruct 자동 매핑

Response는 Entity 상태를 외부로 보여주는 단방향 변환이라 MapStruct와 잘 맞습니다.

```java
@Mapper(componentModel = "spring")
public interface MenuMapper {

    MenuResponse toResponse(AdminMenu menu);
}
```

필드명이 같으면 별도 코드를 쓰지 않아도 됩니다. 필드명이 다르면 `@Mapping`만 추가합니다.

```java
@Mapping(source = "id", target = "menuId")
MenuResponse toResponse(AdminMenu menu);
```

40개 컬럼 response에서는 이 방식이 수동 builder보다 훨씬 낫습니다.

### 권장 패턴 3. 부분 수정은 명시적으로 제한

MapStruct에는 `@MappingTarget`으로 update가 가능합니다.

```java
void updateEntityFromRow(MenuGridRow row, @MappingTarget AdminMenu menu);
```

하지만 이 방식은 Entity에 setter가 필요하거나 필드 접근이 열려야 합니다. 현재처럼 Entity가 도메인 메서드로 상태를 바꾸는 구조에서는 다음 방식이 더 낫습니다.

```java
MenuUpdateCommand toUpdateCommand(MenuGridRow row);

default void updateEntity(@MappingTarget AdminMenu menu, MenuGridRow row) {
    menu.update(toUpdateCommand(row));
}
```

부분 수정에서 null을 무시해야 한다면 다음 설정도 검토할 수 있습니다.

```java
@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
```

다만 grid-save는 보통 row 전체 값이 넘어오는 구조라 null 무시 업데이트보다 전체 row 반영 방식이 더 명확합니다.

### MapperConfig 도입

MapStruct를 본격적으로 쓰려면 공통 설정을 두는 편이 좋습니다.

```java
@MapperConfig(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface AdminMapperConfig {
}
```

사용:

```java
@Mapper(config = AdminMapperConfig.class)
public interface MenuMapper {
}
```

장점:
- 매핑 누락을 컴파일 시점에 잡을 수 있습니다.
- 모든 mapper가 같은 Spring component 설정을 공유합니다.

주의:
- 초반에는 `ReportingPolicy.ERROR`가 부담될 수 있습니다.
- 기존 코드 전환 중에는 `WARN`으로 시작하고, 안정화 후 `ERROR`로 올리는 것도 방법입니다.

## MapStruct 사용 시 피해야 할 방식

### 1. Entity setter 전면 개방

40개 컬럼을 자동 업데이트하려고 Entity에 모든 setter를 열면, 서비스나 테스트 어디서든 Entity 상태를 우회 변경할 수 있습니다.

권장하지 않습니다.

### 2. Mapper에 업무 검증 로직 넣기

Mapper는 변환 책임만 가져야 합니다.

예:
- 문자열 trim
- 대문자 변환
- 복합 ID 생성
- DTO -> command 변환

Service에 남겨야 하는 것:
- DB 존재 여부 확인
- 권한 검증
- 현재 로그인 사용자 기준 보정
- 다른 테이블 조회 후 값 결정

### 3. Entity 생명주기와 충돌하는 자동 매핑

JPA Entity 생성 시 반드시 factory를 거쳐야 하는데 MapStruct가 기본 생성자와 setter로 우회하면 도메인 규칙이 깨질 수 있습니다.

Entity 생성은 다음 중 하나로 제한합니다.

```text
mapper default method -> Entity.create(command)
@ObjectFactory -> Entity.create(...)
service -> Entity.create(mapper.toCommand(dto))
```

## 값 결정 위치별 예시

저장 전에 어떤 값을 어디서 결정해야 하는지는 다음 기준으로 나누는 편이 좋습니다.

```text
DTO 값만 보고 결정 가능 -> mapper 또는 command 변환
DB 조회가 필요함 -> service
Entity가 항상 지켜야 하는 기본값/상태값 -> entity create/update 또는 @PrePersist
DB default를 그대로 써야 함 -> DB default 유지 + JPA에서는 값을 세팅하지 않도록 설계
```

### 1. DTO 값만 보고 결정 가능한 경우

요청 DTO 안의 값만으로 결정할 수 있는 값은 mapper 또는 command 변환에서 처리합니다. 예를 들어 화면에서 넘어온 메뉴 코드를 trim 하고 대문자로 바꾸거나, Y/N 값을 Boolean으로 바꾸는 정도는 DB 조회가 필요 없습니다.

DTO:

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuGridRow {

    private Long id;
    private String menuCode;
    private String menuName;
    private String enabledYn;
}
```

Command:

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuSaveCommand {

    private Long id;
    private String menuCode;
    private String menuName;
    private Boolean enabled;
}
```

Mapper:

```java
@Mapper(componentModel = "spring")
public interface MenuMapper {

    @Mapping(target = "menuCode", expression = "java(normalizeMenuCode(row.getMenuCode()))")
    @Mapping(target = "enabled", expression = "java(toBoolean(row.getEnabledYn()))")
    MenuSaveCommand toCommand(MenuGridRow row);

    default String normalizeMenuCode(String menuCode) {
        if (menuCode == null) {
            return null;
        }
        return menuCode.trim().toUpperCase();
    }

    default Boolean toBoolean(String enabledYn) {
        if (enabledYn == null) {
            return null;
        }
        return "Y".equalsIgnoreCase(enabledYn.trim());
    }

    default AdminMenu toEntity(MenuGridRow row) {
        return AdminMenu.create(toCommand(row));
    }
}
```

이런 처리는 service에 두면 저장 흐름이 길어지고, 여러 API에서 같은 보정이 반복될 가능성이 큽니다.

### 2. DB 조회가 필요한 경우

다른 테이블의 값이나 현재 DB 상태를 봐야 결정할 수 있는 값은 service에서 처리합니다. mapper 안에서 repository를 호출하지 않습니다.

예를 들어 화면에서는 `parentMenuCode`만 넘어오고, 실제 Entity에는 `parentMenuId`를 넣어야 하는 경우입니다.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaAdminMenuService {

    private final AdminMenuRepository menuRepository;
    private final MenuMapper menuMapper;

    @Transactional
    public void create(MenuGridRow row) {
        Long parentMenuId = null;

        if (row.getParentMenuCode() != null) {
            parentMenuId = menuRepository.findByMenuCode(row.getParentMenuCode())
                    .map(AdminMenu::getId)
                    .orElseThrow(() -> new BadRequestException("존재하지 않는 상위 메뉴입니다."));
        }

        MenuSaveCommand command = menuMapper.toCommand(row);
        command.setParentMenuId(parentMenuId);

        AdminMenu menu = AdminMenu.create(command);
        menuRepository.save(menu);
    }
}
```

이 기준은 MyBatis insert에서 `case`로 값을 골라 넣던 로직을 옮길 때도 중요합니다. 조건 판단에 DB 조회가 필요하면 service에서 먼저 조회하고, mapper에는 조회 결과가 반영된 command만 넘깁니다.

조회된 데이터의 값을 여러 개 넣어야 하는 경우도 service에서 조회 후 command에 채워 넣습니다. 예를 들어 화면에서는 `parentMenuCode`만 넘어오지만, 저장할 때는 상위 메뉴의 `id`, `depth`, `menuPath`를 이용해야 하는 경우입니다.

Command:

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuSaveCommand {

    private Long id;
    private String menuCode;
    private String menuName;
    private Long parentMenuId;
    private int depth;
    private String menuPath;
}
```

Service:

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaAdminMenuService {

    private final AdminMenuRepository menuRepository;
    private final MenuMapper menuMapper;

    @Transactional
    public void createWithParentInfo(MenuGridRow row) {
        MenuSaveCommand command = menuMapper.toCommand(row);

        if (row.getParentMenuCode() != null) {
            AdminMenu parentMenu = menuRepository.findByMenuCode(row.getParentMenuCode())
                    .orElseThrow(() -> new BadRequestException("존재하지 않는 상위 메뉴입니다."));

            command.setParentMenuId(parentMenu.getId());
            command.setDepth(parentMenu.getDepth() + 1);
            command.setMenuPath(parentMenu.getMenuPath() + "/" + command.getMenuCode());
        } else {
            command.setDepth(1);
            command.setMenuPath(command.getMenuCode());
        }

        AdminMenu menu = AdminMenu.create(command);
        menuRepository.save(menu);
    }
}
```

이 방식의 핵심은 mapper가 DB를 모르고, service가 조회 결과를 이용해 저장 command를 완성한다는 점입니다. 조회한 엔티티의 값을 그대로 복사하는 수준이면 위처럼 service에서 채우고, 계산 규칙이 모든 저장 경로에서 동일하게 적용되어야 한다면 일부 계산은 Entity의 `create(...)`로 옮기는 것도 가능합니다.

### 3. Entity가 항상 지켜야 하는 기본값 또는 상태값

어떤 API로 저장하든 항상 지켜야 하는 기본값은 Entity 안에 둡니다. 예를 들어 등록 시 기본 사용 여부가 `true`여야 하거나, 삭제는 실제 delete가 아니라 상태값을 `DELETED`로 바꾸는 규칙이라면 Entity 메서드가 담당하는 편이 안전합니다.

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminMenu {

    @Id
    private Long id;

    private String menuName;

    private Boolean enabled;

    private String status;

    public static AdminMenu create(MenuSaveCommand command) {
        AdminMenu menu = new AdminMenu();
        menu.id = command.getId();
        menu.menuName = command.getMenuName();
        menu.enabled = command.getEnabled();
        menu.status = "ACTIVE";
        return menu;
    }

    public void update(MenuSaveCommand command) {
        this.menuName = command.getMenuName();
        this.enabled = command.getEnabled();
    }

    public void delete() {
        this.status = "DELETED";
        this.enabled = false;
    }

    @PrePersist
    private void prePersist() {
        if (this.enabled == null) {
            this.enabled = true;
        }
        if (this.status == null) {
            this.status = "ACTIVE";
        }
    }
}
```

`@PrePersist`는 누락 방지용으로 좋지만, 업무적으로 의미가 큰 값은 `create(...)`에서 명시적으로 채우는 편이 코드 흐름을 읽기 쉽습니다.

### 4. DB default를 그대로 써야 하는 경우

DB의 `default` 값을 그대로 사용해야 한다면 JPA에서 해당 컬럼 값을 넣지 않도록 설계해야 합니다. 단순히 Java 필드를 `null`로 두는 것만으로는 insert SQL에 null이 포함되어 DB default가 적용되지 않을 수 있습니다.

가장 단순한 방법은 JPA가 insert/update 대상에서 해당 컬럼을 제외하는 것입니다.

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminMenu {

    @Id
    private Long id;

    private String menuName;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(insertable = false)
    private String createdBy;

    public static AdminMenu create(MenuSaveCommand command) {
        AdminMenu menu = new AdminMenu();
        menu.id = command.getId();
        menu.menuName = command.getMenuName();
        return menu;
    }
}
```

이 방식은 DB default를 믿고 가는 대신, JPA Entity 저장 직후에는 해당 값이 메모리 객체에 바로 반영되지 않을 수 있습니다. 저장 후 응답에 default 값을 즉시 내려야 한다면 flush 이후 재조회하거나, 애초에 Entity에서 값을 세팅하는 방향을 선택하는 것이 좋습니다.

## 추천 적용 순서

1. `MenuResponse.from(...)` 제거 방향 정리
   - 변환 책임을 `MenuMapper`로 통일

2. `AdminMapperConfig` 추가
   - MapStruct 공통 정책 관리

3. 40개 컬럼 도메인부터 command 객체 도입
   - `{Domain}SaveCommand`
   - `{Domain}UpdateCommand` 또는 하나의 `{Domain}Values`

4. `GridSaveCommand` 도입
   - `GridSaveExecutor.save(...)` 긴 파라미터 정리

5. Service 생성자 주입 Lombok 통일
   - `@RequiredArgsConstructor`

6. User, Role DTO를 API 기준 개별 파일로 점진 전환

## 최종 의견

현재 구조는 방향이 나쁘지 않습니다. 다만 컬럼이 많은 실무 테이블을 고려하면 수동 mapper는 곧 부담이 됩니다.

가장 추천하는 방향은 다음입니다.

```text
Controller -> DTO validation
Service -> 트랜잭션, DB 검증, GridSaveExecutor 호출
Mapper(MapStruct) -> DTO <-> Command/Response 변환
Entity -> create(command), update(command)로 상태 변경
Repository -> JPA CUD
Store -> MyBatis 복잡 조회
```

이렇게 가면 MapStruct의 장점인 반복 필드 매핑 제거를 얻으면서도, Entity의 상태 변경 규칙은 유지할 수 있습니다.
