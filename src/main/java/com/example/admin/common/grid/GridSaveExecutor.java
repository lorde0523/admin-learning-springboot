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

    private <T> List<T> nullToEmpty(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

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

    private <ID, UPDATE_ROW> void rejectDeleteUpdateConflicts(
            List<ID> deletedKeys,
            Map<ID, UPDATE_ROW> updateRowsByKey) {
        for (ID deletedKey : deletedKeys) {
            if (updateRowsByKey.containsKey(deletedKey)) {
                throw badRequest("deletedIds와 updatedRows key가 서로 겹칠 수 없습니다.");
            }
        }
    }

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

        List<String> messages = alreadyRegisteredKeys.stream()
                .map(key -> "id=" + key + "는 이미 등록된 데이터입니다.")
                .toList();
        List<ENTITY> createdEntities = createRowsByKey.entrySet().stream()
                .filter(entry -> !alreadyRegisteredKeys.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(createMapper)
                .toList();

        return new CreateRowsResult<>(repository.saveAll(createdEntities), messages);
    }

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

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static class CreateRowsResult<ENTITY> {

        private final List<ENTITY> entities;
        private final List<String> messages;

        private CreateRowsResult(List<ENTITY> entities, List<String> messages) {
            this.entities = entities;
            this.messages = messages;
        }

        private List<ENTITY> getEntities() {
            return entities;
        }

        private List<String> getMessages() {
            return messages;
        }
    }
}
