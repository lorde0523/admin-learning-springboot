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
     * ag-Grid에서 전달된 등록, 수정, 삭제 row의 key 충돌을 저장 전에 검증합니다.
     *
     * <p>row 그룹이 null이면 작업 없음으로 보고 건너뜁니다. 등록, 수정, 삭제 key는 단일 ID와
     * {@code @EmbeddedId} 같은 복합 ID를 모두 허용합니다.
     */
    public <ID, CREATE_ROW, UPDATE_ROW, DELETE_ROW> void validateRequestConflicts(
            List<CREATE_ROW> createdRows,
            List<UPDATE_ROW> updatedRows,
            List<DELETE_ROW> deletedRows,
            Function<CREATE_ROW, ID> createKeyReader,
            Function<UPDATE_ROW, ID> updateKeyReader,
            Function<DELETE_ROW, ID> deleteKeyReader) {
        Map<ID, CREATE_ROW> createRowsByKey = createRowsByKey(nullToEmpty(createdRows), createKeyReader);
        Map<ID, UPDATE_ROW> updateRowsByKey = updateRowsByKey(nullToEmpty(updatedRows), updateKeyReader);
        List<ID> validDeletedKeys = rowKeys(nullToEmpty(deletedRows), deleteKeyReader, "deletedRows");

        rejectDeleteUpdateConflicts(validDeletedKeys, updateRowsByKey);
        rejectCreateUpdateDeleteConflicts(createRowsByKey, updateRowsByKey, validDeletedKeys);
    }

    /**
     * 등록 row를 저장합니다.
     */
    public <ID, CREATE_ROW, ENTITY> GridSaveResult create(
            List<CREATE_ROW> createdRows,
            JpaRepository<ENTITY, ID> repository,
            Function<CREATE_ROW, ID> createKeyReader,
            Function<ENTITY, ID> entityKeyReader,
            Function<CREATE_ROW, ENTITY> createMapper,
            GridSaveFailureMode failureMode) {
        CreateRowsResult<ENTITY> createRowsResult = createRows(
                createRowsByKey(nullToEmpty(createdRows), createKeyReader),
                repository,
                entityKeyReader,
                createMapper,
                failureMode);

        return GridSaveResult.builder()
                .createdCount(createRowsResult.getEntities().size())
                .updatedCount(0)
                .deletedCount(0)
                .messages(createRowsResult.getMessages())
                .build();
    }

    /**
     * 등록 row를 기본 skip message 방식으로 저장합니다.
     */
    public <ID, CREATE_ROW, ENTITY> GridSaveResult create(
            List<CREATE_ROW> createdRows,
            JpaRepository<ENTITY, ID> repository,
            Function<CREATE_ROW, ID> createKeyReader,
            Function<ENTITY, ID> entityKeyReader,
            Function<CREATE_ROW, ENTITY> createMapper) {
        return create(
                createdRows,
                repository,
                createKeyReader,
                entityKeyReader,
                createMapper,
                GridSaveFailureMode.SKIP_AND_MESSAGE);
    }

    /**
     * 수정 row를 저장합니다.
     */
    public <ID, UPDATE_ROW, ENTITY> GridSaveResult update(
            List<UPDATE_ROW> updatedRows,
            JpaRepository<ENTITY, ID> repository,
            Function<UPDATE_ROW, ID> updateKeyReader,
            Function<ENTITY, ID> entityKeyReader,
            BiConsumer<ENTITY, UPDATE_ROW> updateApplier,
            String missingResourceMessage,
            GridSaveFailureMode failureMode) {
        UpdateRowsResult<ENTITY> updateRowsResult = updateRows(
                updateRowsByKey(nullToEmpty(updatedRows), updateKeyReader),
                repository,
                entityKeyReader,
                updateApplier,
                missingResourceMessage,
                failureMode);

        return GridSaveResult.builder()
                .createdCount(0)
                .updatedCount(updateRowsResult.getEntities().size())
                .deletedCount(0)
                .messages(updateRowsResult.getMessages())
                .build();
    }

    /**
     * 수정 row를 기본 skip message 방식으로 저장합니다.
     */
    public <ID, UPDATE_ROW, ENTITY> GridSaveResult update(
            List<UPDATE_ROW> updatedRows,
            JpaRepository<ENTITY, ID> repository,
            Function<UPDATE_ROW, ID> updateKeyReader,
            Function<ENTITY, ID> entityKeyReader,
            BiConsumer<ENTITY, UPDATE_ROW> updateApplier,
            String missingResourceMessage) {
        return update(
                updatedRows,
                repository,
                updateKeyReader,
                entityKeyReader,
                updateApplier,
                missingResourceMessage,
                GridSaveFailureMode.SKIP_AND_MESSAGE);
    }

    /**
     * 삭제 row 또는 삭제 key 목록을 저장합니다. 삭제 대상이 DTO인 경우 deleteKeyReader로 key를 추출합니다.
     */
    public <ID, DELETE_ROW, ENTITY> GridSaveResult delete(
            List<DELETE_ROW> deletedRows,
            JpaRepository<ENTITY, ID> repository,
            Function<DELETE_ROW, ID> deleteKeyReader,
            Function<ENTITY, ID> entityKeyReader,
            String missingResourceMessage,
            GridSaveFailureMode failureMode) {
        DeleteRowsResult deleteRowsResult = deleteRows(
                rowKeys(nullToEmpty(deletedRows), deleteKeyReader, "deletedRows"),
                repository,
                entityKeyReader,
                missingResourceMessage,
                failureMode);

        return GridSaveResult.builder()
                .createdCount(0)
                .updatedCount(0)
                .deletedCount(deleteRowsResult.getDeletedCount())
                .messages(deleteRowsResult.getMessages())
                .build();
    }

    /**
     * 삭제 row 또는 삭제 key 목록을 기본 skip message 방식으로 저장합니다.
     */
    public <ID, DELETE_ROW, ENTITY> GridSaveResult delete(
            List<DELETE_ROW> deletedRows,
            JpaRepository<ENTITY, ID> repository,
            Function<DELETE_ROW, ID> deleteKeyReader,
            Function<ENTITY, ID> entityKeyReader,
            String missingResourceMessage) {
        return delete(
                deletedRows,
                repository,
                deleteKeyReader,
                entityKeyReader,
                missingResourceMessage,
                GridSaveFailureMode.SKIP_AND_MESSAGE);
    }

    /**
     * ag-Grid에서 특정 row 그룹 자체를 null로 보낸 경우 해당 작업을 하지 않는 빈 목록으로 변환합니다.
     */
    private <T> List<T> nullToEmpty(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    /**
     * row 목록에서 key를 추출하면서 null row, null key, 중복 key를 검증합니다.
     */
    private <ID, ROW> List<ID> rowKeys(List<ROW> rows, Function<ROW, ID> keyReader, String fieldName) {
        Set<ID> uniqueKeys = new LinkedHashSet<>();
        for (ROW row : rows) {
            if (row == null) {
                throw badRequest(fieldName + "에 null row가 포함될 수 없습니다.");
            }

            ID key = keyReader.apply(row);
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
     * 삭제 대상 key를 DB에서 조회한 뒤, 실패 처리 방식에 따라 예외 또는 skip 메시지로 처리합니다.
     */
    private <ID, ENTITY> DeleteRowsResult deleteRows(
            List<ID> deletedKeys,
            JpaRepository<ENTITY, ID> repository,
            Function<ENTITY, ID> entityKeyReader,
            String missingResourceMessage,
            GridSaveFailureMode failureMode) {
        if (deletedKeys.isEmpty()) {
            return new DeleteRowsResult(0, List.of());
        }

        List<ENTITY> deleteTargets = repository.findAllById(deletedKeys);
        if (failureMode == GridSaveFailureMode.STRICT_EXCEPTION && deleteTargets.size() != deletedKeys.size()) {
            throw new ResourceNotFoundException(missingResourceMessage);
        }

        Set<ID> foundKeys = new LinkedHashSet<>();
        for (ENTITY entity : deleteTargets) {
            foundKeys.add(entityKeyReader.apply(entity));
        }

        List<ID> missingKeys = deletedKeys.stream()
                .filter(key -> !foundKeys.contains(key))
                .toList();
        List<GridSaveMessage> messages = missingKeys.stream()
                .map(key -> skippedMessage("DELETE", key, "삭제 대상 데이터가 없습니다."))
                .toList();
        List<ID> existingKeys = deletedKeys.stream()
                .filter(foundKeys::contains)
                .toList();
        repository.deleteAllByIdInBatch(existingKeys);
        return new DeleteRowsResult(existingKeys.size(), messages);
    }

    /**
     * 등록 row 중 이미 DB에 존재하는 key를 실패 처리 방식에 따라 예외 또는 skip 메시지로 처리하고, 신규 key만 저장합니다.
     */
    private <CREATE_ROW, ENTITY, ID> CreateRowsResult<ENTITY> createRows(
            Map<ID, CREATE_ROW> createRowsByKey,
            JpaRepository<ENTITY, ID> repository,
            Function<ENTITY, ID> entityKeyReader,
            Function<CREATE_ROW, ENTITY> createMapper,
            GridSaveFailureMode failureMode) {
        if (createRowsByKey.isEmpty()) {
            return new CreateRowsResult<>(List.of(), List.of());
        }

        Set<ID> alreadyRegisteredKeys = new LinkedHashSet<>();
        for (ENTITY entity : repository.findAllById(createRowsByKey.keySet())) {
            alreadyRegisteredKeys.add(entityKeyReader.apply(entity));
        }

        if (failureMode == GridSaveFailureMode.STRICT_EXCEPTION && !alreadyRegisteredKeys.isEmpty()) {
            throw badRequest("이미 등록된 데이터가 포함되어 있습니다.");
        }

        List<GridSaveMessage> messages = alreadyRegisteredKeys.stream()
                .map(key -> skippedMessage("CREATE", key, "이미 등록된 데이터입니다."))
                .toList();
        List<ENTITY> createdEntities = createRowsByKey.entrySet().stream()
                .filter(entry -> !alreadyRegisteredKeys.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(createMapper)
                .toList();

        return new CreateRowsResult<>(repository.saveAll(createdEntities), messages);
    }

    /**
     * 수정 대상 key를 DB에서 조회한 뒤, 실패 처리 방식에 따라 예외 또는 skip 메시지로 처리합니다.
     */
    private <ID, UPDATE_ROW, ENTITY> UpdateRowsResult<ENTITY> updateRows(
            Map<ID, UPDATE_ROW> updateRowsByKey,
            JpaRepository<ENTITY, ID> repository,
            Function<ENTITY, ID> entityKeyReader,
            BiConsumer<ENTITY, UPDATE_ROW> updateApplier,
            String missingResourceMessage,
            GridSaveFailureMode failureMode) {
        if (updateRowsByKey.isEmpty()) {
            return new UpdateRowsResult<>(List.of(), List.of());
        }

        List<ENTITY> updateTargets = repository.findAllById(updateRowsByKey.keySet());
        if (failureMode == GridSaveFailureMode.STRICT_EXCEPTION && updateTargets.size() != updateRowsByKey.size()) {
            throw new ResourceNotFoundException(missingResourceMessage);
        }

        Set<ID> foundKeys = new LinkedHashSet<>();
        for (ENTITY entity : updateTargets) {
            ID key = entityKeyReader.apply(entity);
            foundKeys.add(key);
            updateApplier.accept(entity, updateRowsByKey.get(key));
        }

        List<GridSaveMessage> messages = updateRowsByKey.keySet().stream()
                .filter(key -> !foundKeys.contains(key))
                .map(key -> skippedMessage("UPDATE", key, "수정 대상 데이터가 없습니다."))
                .toList();
        return new UpdateRowsResult<>(updateTargets, messages);
    }

    /**
     * 특정 작업이 예외 없이 건너뛰어진 경우의 표준 메시지를 만듭니다.
     */
    private <ID> GridSaveMessage skippedMessage(String operation, ID key, String message) {
        return GridSaveMessage.builder()
                .operation(operation)
                .result("SKIPPED")
                .key(String.valueOf(key))
                .message(message)
                .build();
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

    /**
     * 삭제 결과 count와 skip 메시지를 함께 전달하기 위한 내부 결과 객체입니다.
     */
    private static class DeleteRowsResult {

        /**
         * 실제로 삭제된 key 수입니다.
         */
        private final int deletedCount;

        /**
         * 삭제 요청 중 skip된 key에 대해 클라이언트가 판단할 수 있도록 내려주는 메시지 목록입니다.
         */
        private final List<GridSaveMessage> messages;

        private DeleteRowsResult(int deletedCount, List<GridSaveMessage> messages) {
            this.deletedCount = deletedCount;
            this.messages = messages;
        }

        private int getDeletedCount() {
            return deletedCount;
        }

        private List<GridSaveMessage> getMessages() {
            return messages;
        }
    }

    /**
     * 수정 저장 결과와 skip 메시지를 함께 전달하기 위한 내부 결과 객체입니다.
     */
    private static class UpdateRowsResult<ENTITY> {

        /**
         * 실제로 수정된 entity 목록입니다.
         */
        private final List<ENTITY> entities;

        /**
         * 수정 요청 중 skip된 row에 대해 클라이언트가 판단할 수 있도록 내려주는 메시지 목록입니다.
         */
        private final List<GridSaveMessage> messages;

        private UpdateRowsResult(List<ENTITY> entities, List<GridSaveMessage> messages) {
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
