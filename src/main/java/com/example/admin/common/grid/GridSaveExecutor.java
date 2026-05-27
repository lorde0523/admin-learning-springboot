package com.example.admin.common.grid;

import com.example.admin.common.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GridSaveExecutor {

    /**
     * ag-Grid에서 전달된 등록, 수정, 삭제 row를 하나의 트랜잭션 안에서 처리하기 위한 공통 저장 진입점입니다.
     *
     * <p>row 그룹이 null이면 작업 없음으로 보고 건너뜁니다. 등록, 수정, 삭제 key는 단일 ID와
     * {@code @EmbeddedId} 같은 복합 ID를 모두 허용합니다.
     */
    public <ID, CREATE_ROW, UPDATE_ROW, ENTITY> GridSaveResult save(
            List<CREATE_ROW> createdRows,
            List<UPDATE_ROW> updatedRows,
            List<ID> deletedIds,
            JpaRepository<ENTITY, ID> repository,
            Function<CREATE_ROW, ID> createKeyReader,
            Function<UPDATE_ROW, ID> updateKeyReader,
            Function<ENTITY, ID> entityKeyReader,
            Function<CREATE_ROW, ENTITY> createMapper,
            BiConsumer<ENTITY, UPDATE_ROW> updateApplier,
            String missingResourceMessage) {

        List<ID> validDeletedKeys = uniqueKeys(nullToEmpty(deletedIds), "deletedIds");
        Map<ID, CREATE_ROW> createRowsByKey = createRowsByKey(nullToEmpty(createdRows), createKeyReader);
        Map<ID, UPDATE_ROW> updateRowsByKey = updateRowsByKey(nullToEmpty(updatedRows), updateKeyReader);
        rejectDeleteUpdateConflicts(validDeletedKeys, updateRowsByKey);
        rejectCreateUpdateDeleteConflicts(createRowsByKey, updateRowsByKey, validDeletedKeys);

        int deletedCount = deleteRows(validDeletedKeys, repository, missingResourceMessage);
        CreateRowsResult<ENTITY> createRowsResult =
                createRows(createRowsByKey, repository, entityKeyReader, createMapper);
        List<ENTITY> updatedEntities =
                updateRows(updateRowsByKey, repository, entityKeyReader, updateApplier, missingResourceMessage);

        return GridSaveResult.builder()
                .createdCount(createRowsResult.getEntities().size())
                .updatedCount(updatedEntities.size())
                .deletedCount(deletedCount)
                .messages(createRowsResult.getMessages())
                .build();
    }

    /**
     * ag-Grid에서 특정 row 그룹 자체를 null로 보낸 경우 해당 작업을 하지 않는 빈 목록으로 변환합니다.
     */
    private <T> List<T> nullToEmpty(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    /**
     * 삭제 key 목록처럼 row 객체 없이 key만 넘어오는 목록의 null과 중복을 검증합니다.
     */
    private <ID> List<ID> uniqueKeys(List<ID> keys, String fieldName) {
        Set<ID> uniqueKeys = new LinkedHashSet<>();
        for (ID key : keys) {
            if (key == null) {
                throw badRequest(fieldName + "에 null key가 포함될 수 없습니다.");
            }
            if (!uniqueKeys.add(key)) {
                throw badRequest(fieldName + "에 중복 key가 포함될 수 없습니다.");
            }
        }
        return List.copyOf(uniqueKeys);
    }

    /**
     * 등록 row 목록을 key 기준 Map으로 변환하면서 null row, null key, 요청 내부 key 중복을 검증합니다.
     */
    private <ID, CREATE_ROW> Map<ID, CREATE_ROW> createRowsByKey(
            List<CREATE_ROW> createdRows,
            Function<CREATE_ROW, ID> createKeyReader) {
        Map<ID, CREATE_ROW> rowsByKey = new LinkedHashMap<>();
        for (CREATE_ROW row : createdRows) {
            if (row == null) {
                throw badRequest("createdRows에 null row가 포함될 수 없습니다.");
            }

            ID key = createKeyReader.apply(row);
            if (key == null) {
                throw badRequest("createdRows key는 null일 수 없습니다.");
            }
            if (rowsByKey.put(key, row) != null) {
                throw badRequest("createdRows key가 중복될 수 없습니다.");
            }
        }
        return rowsByKey;
    }

    /**
     * 수정 row 목록을 key 기준 Map으로 변환하면서 null row, null key, 요청 내부 key 중복을 검증합니다.
     */
    private <ID, UPDATE_ROW> Map<ID, UPDATE_ROW> updateRowsByKey(
            List<UPDATE_ROW> updatedRows,
            Function<UPDATE_ROW, ID> updateKeyReader) {
        Map<ID, UPDATE_ROW> rowsByKey = new LinkedHashMap<>();
        for (UPDATE_ROW row : updatedRows) {
            if (row == null) {
                throw badRequest("updatedRows에 null row가 포함될 수 없습니다.");
            }

            ID key = updateKeyReader.apply(row);
            if (key == null) {
                throw badRequest("updatedRows key는 null일 수 없습니다.");
            }
            if (rowsByKey.put(key, row) != null) {
                throw badRequest("updatedRows key가 중복될 수 없습니다.");
            }
        }
        return rowsByKey;
    }

    /**
     * 같은 key가 삭제와 수정에 동시에 들어온 경우 처리 순서를 해석하기 어렵기 때문에 잘못된 요청으로 막습니다.
     */
    private <ID, UPDATE_ROW> void rejectDeleteUpdateConflicts(
            List<ID> deletedKeys,
            Map<ID, UPDATE_ROW> updateRowsByKey) {
        for (ID deletedKey : deletedKeys) {
            if (updateRowsByKey.containsKey(deletedKey)) {
                throw badRequest("deletedIds와 updatedRows key가 서로 겹칠 수 없습니다.");
            }
        }
    }

    /**
     * 등록 key가 수정 또는 삭제 key와 겹치는 경우 신규 등록인지 기존 row 처리인지 모호하므로 잘못된 요청으로 막습니다.
     */
    private <ID, CREATE_ROW, UPDATE_ROW> void rejectCreateUpdateDeleteConflicts(
            Map<ID, CREATE_ROW> createRowsByKey,
            Map<ID, UPDATE_ROW> updateRowsByKey,
            List<ID> deletedKeys) {
        for (ID createKey : createRowsByKey.keySet()) {
            if (updateRowsByKey.containsKey(createKey)) {
                throw badRequest("createdRows와 updatedRows key가 서로 겹칠 수 없습니다.");
            }
            if (deletedKeys.contains(createKey)) {
                throw badRequest("createdRows와 deletedIds key가 서로 겹칠 수 없습니다.");
            }
        }
    }

    /**
     * 삭제 대상 key가 모두 DB에 존재하는지 확인한 뒤 JPA batch delete로 삭제합니다.
     */
    private <ID, ENTITY> int deleteRows(
            List<ID> deletedKeys,
            JpaRepository<ENTITY, ID> repository,
            String missingResourceMessage) {
        if (deletedKeys.isEmpty()) {
            return 0;
        }

        List<ENTITY> deleteTargets = repository.findAllById(deletedKeys);
        if (deleteTargets.size() != deletedKeys.size()) {
            throw new ResourceNotFoundException(missingResourceMessage);
        }
        repository.deleteAllByIdInBatch(deletedKeys);
        return deletedKeys.size();
    }

    /**
     * 등록 row 중 이미 DB에 존재하는 key는 예외가 아니라 skip 메시지로 반환하고, 신규 key만 저장합니다.
     */
    private <CREATE_ROW, ENTITY, ID> CreateRowsResult<ENTITY> createRows(
            Map<ID, CREATE_ROW> createRowsByKey,
            JpaRepository<ENTITY, ID> repository,
            Function<ENTITY, ID> entityKeyReader,
            Function<CREATE_ROW, ENTITY> createMapper) {
        if (createRowsByKey.isEmpty()) {
            return new CreateRowsResult<>(List.of(), List.of());
        }

        Set<ID> alreadyRegisteredKeys = new LinkedHashSet<>();
        for (ENTITY entity : repository.findAllById(createRowsByKey.keySet())) {
            alreadyRegisteredKeys.add(entityKeyReader.apply(entity));
        }

        List<GridSaveMessage> messages = alreadyRegisteredKeys.stream()
                .map(key -> GridSaveMessage.builder()
                        .operation("CREATE")
                        .result("SKIPPED")
                        .key(String.valueOf(key))
                        .message("이미 등록된 데이터입니다.")
                        .build())
                .toList();
        List<ENTITY> createdEntities = createRowsByKey.entrySet().stream()
                .filter(entry -> !alreadyRegisteredKeys.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(createMapper)
                .toList();

        return new CreateRowsResult<>(repository.saveAll(createdEntities), messages);
    }

    /**
     * 수정 대상 key가 모두 DB에 존재하는지 확인한 뒤 managed entity에 update 함수를 적용합니다.
     */
    private <ID, UPDATE_ROW, ENTITY> List<ENTITY> updateRows(
            Map<ID, UPDATE_ROW> updateRowsByKey,
            JpaRepository<ENTITY, ID> repository,
            Function<ENTITY, ID> entityKeyReader,
            BiConsumer<ENTITY, UPDATE_ROW> updateApplier,
            String missingResourceMessage) {
        if (updateRowsByKey.isEmpty()) {
            return List.of();
        }

        List<ENTITY> updateTargets = repository.findAllById(updateRowsByKey.keySet());
        if (updateTargets.size() != updateRowsByKey.size()) {
            throw new ResourceNotFoundException(missingResourceMessage);
        }

        for (ENTITY entity : updateTargets) {
            updateApplier.accept(entity, updateRowsByKey.get(entityKeyReader.apply(entity)));
        }
        return updateTargets;
    }

    /**
     * 공통 grid 저장 검증 실패를 400 Bad Request로 변환합니다.
     */
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 등록 저장 결과와 skip 메시지를 함께 전달하기 위한 내부 결과 객체입니다.
     */
    private static class CreateRowsResult<ENTITY> {

        /**
         * 실제로 신규 저장된 entity 목록입니다.
         */
        private final List<ENTITY> entities;

        /**
         * 등록 요청 중 skip된 row에 대해 클라이언트가 판단할 수 있도록 내려주는 메시지 목록입니다.
         */
        private final List<GridSaveMessage> messages;

        private CreateRowsResult(List<ENTITY> entities, List<GridSaveMessage> messages) {
            this.entities = entities;
            this.messages = messages;
        }

        private List<ENTITY> getEntities() {
            return entities;
        }

        private List<GridSaveMessage> getMessages() {
            return messages;
        }
    }
}
