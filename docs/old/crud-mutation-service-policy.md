# 등록/수정/삭제 서비스 공통화 정리

작성일: 2026-06-02

## 목적

실무 저장 로직에서 등록, 수정, 삭제가 여러 service에 반복되고 있습니다. 현재 흐름은 JPA `save`일 수도 있고 MyBatis `insert/update/delete`일 수도 있기 때문에, 저장 기술을 하나로 고정하지 않으면서도 공통 기준을 맞추는 방향을 정리합니다.

이 문서는 코드에 바로 반영하기 전, service 저장 로직의 기준과 공통화 후보를 정리하기 위한 문서입니다.

## 기본 판단 기준

등록, 수정, 삭제는 다음 기준으로 맞춥니다.

```text
create
- DTO에서 기준 key를 추출한다.
- 이미 등록된 데이터가 있으면 예외 처리한다.
- 없으면 DTO를 Entity 또는 MyBatis Param으로 변환해 등록한다.

update
- DTO에서 기준 key를 추출한다.
- 기존 데이터가 있으면 수정한다.
- 기존 데이터가 없으면 예외 처리를 권장한다.

delete
- ID 또는 DTO에서 기준 key를 추출한다.
- 기존 데이터가 있으면 삭제한다.
- 기존 데이터가 없으면 단건 API는 예외 처리를 권장한다.
- grid batch 저장에서는 skip message 방식도 가능하다.
```

추천 정책:

```text
단건 API create/update/delete -> 예외
grid batch create/update/delete -> row별 message 또는 업무 정책에 따라 예외
```

## 공통 흐름

등록, 수정, 삭제는 결국 비슷한 뼈대를 갖습니다.

```text
1. DTO 또는 ID에서 기준 key 추출
2. 기준 Entity 또는 존재 여부 조회
3. 존재 여부에 따라 예외 처리
4. DTO -> Entity/Param 변환 또는 기존 Entity 수정
5. JPA save/delete 또는 MyBatis insert/update/delete 실행
6. 끝난 뒤 후처리 실행
```

따라서 공통화 기준은 다음 흐름으로 잡는 것이 좋습니다.

```text
before validate
-> find/check target
-> mutate
-> persist
-> after hook
```

## JPA 기준 예시

### 등록

```java
@Transactional
public void create(MenuCreateRequest request) {
    Long id = request.getId();

    if (menuRepository.existsById(id)) {
        throw new BadRequestException("이미 등록된 메뉴입니다. ID: " + id);
    }

    AdminMenu menu = menuMapper.toEntity(request);
    menuRepository.save(menu);
}
```

등록에서는 가능하면 `toEntity` 전에 key를 먼저 확인합니다. Entity 생성에 기본값 세팅이나 검증이 들어갈 수 있기 때문에, 등록 가능 여부를 먼저 판단하는 흐름이 더 명확합니다.

### 수정

```java
@Transactional
public void update(MenuUpdateRequest request) {
    AdminMenu menu = menuRepository.findById(request.getId())
            .orElseThrow(() -> new NotFoundException("수정할 메뉴가 존재하지 않습니다. ID: " + request.getId()));

    menuMapper.updateEntity(menu, request);
}
```

JPA 수정은 영속 상태 Entity를 조회한 뒤 `updateEntity`로 값을 반영하면 dirty checking으로 처리됩니다. 이 경우 별도 `save`를 호출하지 않아도 됩니다.

### 삭제

```java
@Transactional
public void delete(Long id) {
    AdminMenu menu = menuRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("삭제할 메뉴가 존재하지 않습니다. ID: " + id));

    menuRepository.delete(menu);
}
```

삭제에서 없는 데이터를 무시할 수도 있지만, 단건 업무 API에서는 사용자가 잘못된 대상을 삭제하려 한 상황을 알 수 있도록 예외를 권장합니다.

## MyBatis 기준 예시

MyBatis는 JPA dirty checking이 없기 때문에 `insert/update/delete` 결과 count를 확인하는 편이 안전합니다.

### 등록

```java
@Transactional
public void create(MenuCreateRequest request) {
    Long id = request.getId();

    if (menuStore.existsById(id)) {
        throw new BadRequestException("이미 등록된 메뉴입니다. ID: " + id);
    }

    int insertedCount = menuStore.insert(menuMapper.toInsertParam(request));

    if (insertedCount == 0) {
        throw new BadRequestException("메뉴 등록에 실패했습니다. ID: " + id);
    }
}
```

### 수정

```java
@Transactional
public void update(MenuUpdateRequest request) {
    Long id = request.getId();

    if (!menuStore.existsById(id)) {
        throw new NotFoundException("수정할 메뉴가 존재하지 않습니다. ID: " + id);
    }

    int updatedCount = menuStore.update(menuMapper.toUpdateParam(request));

    if (updatedCount == 0) {
        throw new NotFoundException("수정할 메뉴가 존재하지 않습니다. ID: " + id);
    }
}
```

`exists`를 먼저 했더라도 `updatedCount == 0`을 다시 확인하는 이유는 조회 후 수정 사이에 다른 트랜잭션이 데이터를 삭제할 수 있기 때문입니다.

### 삭제

```java
@Transactional
public void delete(Long id) {
    if (!menuStore.existsById(id)) {
        throw new NotFoundException("삭제할 메뉴가 존재하지 않습니다. ID: " + id);
    }

    int deletedCount = menuStore.delete(id);

    if (deletedCount == 0) {
        throw new NotFoundException("삭제할 메뉴가 존재하지 않습니다. ID: " + id);
    }
}
```

## 공통화 추천 방향

JPA와 MyBatis가 섞여 있으므로 공통 클래스가 특정 repository나 store에 직접 의존하면 재사용성이 떨어집니다. 대신 key 추출, 조회, 변환, 실행, 후처리를 함수로 넘기는 방식이 좋습니다.

추천 이름:

```text
CrudMutationExecutor
```

의미:

```text
CRUD 중 상태 변경 명령(create/update/delete)을 실행하는 공통 executor
```

## 공통 Executor 예시

### 등록 공통화

```java
@Component
public class CrudMutationExecutor {

    public <D, ID, E, R> R create(
            D dto,
            Function<D, ID> idReader,
            Function<ID, Optional<E>> finder,
            Function<D, R> mutation,
            Consumer<R> afterCreate,
            String resourceName
    ) {
        ID id = idReader.apply(dto);

        finder.apply(id).ifPresent(entity -> {
            throw new BadRequestException("이미 등록된 " + resourceName + "입니다. ID: " + id);
        });

        R result = mutation.apply(dto);

        if (afterCreate != null) {
            afterCreate.accept(result);
        }

        return result;
    }
}
```

JPA 사용:

```java
@Transactional
public AdminMenu create(MenuCreateRequest request) {
    return crudMutationExecutor.create(
            request,
            MenuCreateRequest::getId,
            menuRepository::findById,
            dto -> menuRepository.save(menuMapper.toEntity(dto)),
            menu -> historyService.writeCreateHistory(menu.getId()),
            "메뉴");
}
```

MyBatis 사용:

```java
@Transactional
public Integer create(MenuCreateRequest request) {
    return crudMutationExecutor.create(
            request,
            MenuCreateRequest::getId,
            menuStore::findById,
            dto -> {
                int count = menuStore.insert(menuMapper.toInsertParam(dto));
                if (count == 0) {
                    throw new BadRequestException("메뉴 등록에 실패했습니다. ID: " + dto.getId());
                }
                return count;
            },
            count -> historyService.writeCreateHistory(request.getId()),
            "메뉴");
}
```

### 수정 공통화

```java
public <D, ID, E, R> R update(
        D dto,
        Function<D, ID> idReader,
        Function<ID, Optional<E>> finder,
        BiFunction<E, D, R> mutation,
        Consumer<R> afterUpdate,
        String resourceName
) {
    ID id = idReader.apply(dto);

    E entity = finder.apply(id)
            .orElseThrow(() -> new NotFoundException("수정할 " + resourceName + "가 존재하지 않습니다. ID: " + id));

    R result = mutation.apply(entity, dto);

    if (afterUpdate != null) {
        afterUpdate.accept(result);
    }

    return result;
}
```

JPA 사용:

```java
@Transactional
public AdminMenu update(MenuUpdateRequest request) {
    return crudMutationExecutor.update(
            request,
            MenuUpdateRequest::getId,
            menuRepository::findById,
            (menu, dto) -> {
                menuMapper.updateEntity(menu, dto);
                return menu;
            },
            menu -> historyService.writeUpdateHistory(menu.getId()),
            "메뉴");
}
```

MyBatis 사용:

```java
@Transactional
public AdminMenu update(MenuUpdateRequest request) {
    return crudMutationExecutor.update(
            request,
            MenuUpdateRequest::getId,
            menuStore::findById,
            (menu, dto) -> {
                int count = menuStore.update(menuMapper.toUpdateParam(dto));
                if (count == 0) {
                    throw new NotFoundException("수정할 메뉴가 존재하지 않습니다. ID: " + dto.getId());
                }
                return menu;
            },
            menu -> historyService.writeUpdateHistory(menu.getId()),
            "메뉴");
}
```

### 삭제 공통화

```java
public <ID, E, R> R delete(
        ID id,
        Function<ID, Optional<E>> finder,
        Function<E, R> mutation,
        Consumer<R> afterDelete,
        String resourceName
) {
    E entity = finder.apply(id)
            .orElseThrow(() -> new NotFoundException("삭제할 " + resourceName + "가 존재하지 않습니다. ID: " + id));

    R result = mutation.apply(entity);

    if (afterDelete != null) {
        afterDelete.accept(result);
    }

    return result;
}
```

JPA 사용:

```java
@Transactional
public AdminMenu delete(Long id) {
    return crudMutationExecutor.delete(
            id,
            menuRepository::findById,
            menu -> {
                menuRepository.delete(menu);
                return menu;
            },
            menu -> historyService.writeDeleteHistory(menu.getId()),
            "메뉴");
}
```

MyBatis 사용:

```java
@Transactional
public AdminMenu delete(Long id) {
    return crudMutationExecutor.delete(
            id,
            menuStore::findById,
            menu -> {
                int count = menuStore.delete(menu.getId());
                if (count == 0) {
                    throw new NotFoundException("삭제할 메뉴가 존재하지 않습니다. ID: " + menu.getId());
                }
                return menu;
            },
            menu -> historyService.writeDeleteHistory(menu.getId()),
            "메뉴");
}
```

## 공통화가 가져갈 책임

```text
- key 추출
- 기존 데이터 조회
- create 중복 예외
- update 대상 없음 예외
- delete 대상 없음 예외
- after hook 호출
```

## Service에 남겨야 하는 책임

```text
- JPA save인지 MyBatis insert/update/delete인지 선택
- DTO -> Entity/Param 변환
- 업무별 추가 조회와 값 보정
- history, cache clear 등 후처리 내용
- transaction 경계
```

## 후처리 hook 기준

후처리는 공통 executor가 내용을 알면 안 됩니다. service가 필요한 후처리를 lambda로 넘기는 방식이 좋습니다.

예:

```java
menu -> historyService.writeCreateHistory(menu.getId())
menu -> cacheService.evictMenu(menu.getId())
menu -> eventPublisher.publishEvent(new MenuChangedEvent(menu.getId()))
```

후처리가 필요 없으면 `null`을 넘길 수도 있지만, 실무에서는 `NoOp` 유틸을 두거나 overload를 추가하는 방식이 더 읽기 좋습니다.

```java
public <D, ID, E, R> R update(
        D dto,
        Function<D, ID> idReader,
        Function<ID, Optional<E>> finder,
        BiFunction<E, D, R> mutation,
        String resourceName
) {
    return update(dto, idReader, finder, mutation, null, resourceName);
}
```

## grid batch와의 차이

단건 API는 예외 중심이 좋습니다.

```text
create 중복 -> 예외
update 없음 -> 예외
delete 없음 -> 예외
```

grid batch는 row별 결과를 화면에 보여줘야 할 수 있으므로 예외 대신 메시지 정책도 가능합니다.

```text
create 중복 -> SKIPPED 또는 예외
update 없음 -> SKIPPED 또는 예외
delete 없음 -> SKIPPED 또는 예외
```

따라서 `CrudMutationExecutor`는 단건 저장 기준으로 두고, grid batch는 기존 `GridSaveExecutor` 또는 별도 batch executor에서 처리하는 편이 역할이 명확합니다.

## 최종 의견

지금 단계에서 가장 좋은 방향은 전체 service를 무리하게 한 번에 바꾸는 것이 아니라, 다음 순서로 가는 것입니다.

1. 등록, 수정, 삭제의 예외 정책을 먼저 통일합니다.
2. 각 service 내부 private method로 반복 흐름을 정리합니다.
3. 같은 패턴이 3개 이상 반복되면 `CrudMutationExecutor`를 도입합니다.
4. JPA/MyBatis 차이는 executor 내부에서 숨기지 말고 service lambda로 드러냅니다.

이렇게 하면 공통화가 너무 커지지 않으면서도, 반복되는 저장 기준은 안정적으로 맞출 수 있습니다.
