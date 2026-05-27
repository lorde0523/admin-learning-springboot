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
            Function<UPDATE_ROW, ID> updateKeyReader,
            Function<ENTITY, ID> entityKeyReader,
            Function<CREATE_ROW, ENTITY> createMapper,
            BiConsumer<ENTITY, UPDATE_ROW> updateApplier,
            String missingResourceMessage) {

        List<ID> validDeletedIds = uniqueKeys(nullToEmpty(deletedIds), "deletedIds");
        List<CREATE_ROW> validCreatedRows = validRows(nullToEmpty(createdRows), "createdRows");
        Map<ID, UPDATE_ROW> updateRowsByKey = updateRowsByKey(nullToEmpty(updatedRows), updateKeyReader);
        rejectDeleteUpdateConflicts(validDeletedIds, updateRowsByKey);

        int deletedCount = deleteRows(validDeletedIds, repository, missingResourceMessage);
        List<ENTITY> createdEntities = createRows(validCreatedRows, repository, createMapper);
        List<ENTITY> updatedEntities =
                updateRows(updateRowsByKey, repository, entityKeyReader, updateApplier, missingResourceMessage);

        return GridSaveResult.builder()
                .createdCount(createdEntities.size())
                .updatedCount(updatedEntities.size())
                .deletedCount(deletedCount)
                .build();
    }

    private <T> List<T> nullToEmpty(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private <T> List<T> validRows(List<T> rows, String fieldName) {
        for (T row : rows) {
            if (row == null) {
                throw badRequest(fieldName + "에 null row가 포함될 수 없습니다.");
            }
        }
        return rows;
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

    private <CREATE_ROW, ENTITY, ID> List<ENTITY> createRows(
            List<CREATE_ROW> createdRows,
            JpaRepository<ENTITY, ID> repository,
            Function<CREATE_ROW, ENTITY> createMapper) {
        if (createdRows.isEmpty()) {
            return List.of();
        }

        List<ENTITY> createdEntities = createdRows.stream()
                .map(createMapper)
                .toList();
        return repository.saveAll(createdEntities);
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
}
