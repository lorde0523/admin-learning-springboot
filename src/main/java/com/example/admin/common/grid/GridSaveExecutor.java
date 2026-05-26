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
            Function<UPDATE_ROW, ID> updateIdReader,
            Function<ENTITY, ID> entityIdReader,
            Function<CREATE_ROW, ENTITY> createMapper,
            BiConsumer<ENTITY, UPDATE_ROW> updateApplier,
            String missingResourceMessage) {

        List<ID> validDeletedIds = uniqueIds(nullToEmpty(deletedIds), "deletedIds");
        List<CREATE_ROW> validCreatedRows = validRows(nullToEmpty(createdRows), "createdRows");
        Map<ID, UPDATE_ROW> updateRowsById = updateRowsById(nullToEmpty(updatedRows), updateIdReader);
        rejectDeleteUpdateConflicts(validDeletedIds, updateRowsById);

        int deletedCount = deleteRows(validDeletedIds, repository, missingResourceMessage);
        List<ENTITY> createdEntities = createRows(validCreatedRows, repository, createMapper);
        List<ENTITY> updatedEntities =
                updateRows(updateRowsById, repository, entityIdReader, updateApplier, missingResourceMessage);

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
                throw badRequest(fieldName + " must not contain null rows.");
            }
        }
        return rows;
    }

    private <ID> List<ID> uniqueIds(List<ID> ids, String fieldName) {
        Set<ID> uniqueIds = new LinkedHashSet<>();
        for (ID id : ids) {
            if (id == null) {
                throw badRequest(fieldName + " must not contain null ids.");
            }
            if (!uniqueIds.add(id)) {
                throw badRequest(fieldName + " must not contain duplicate ids.");
            }
        }
        return List.copyOf(uniqueIds);
    }

    private <ID, UPDATE_ROW> Map<ID, UPDATE_ROW> updateRowsById(
            List<UPDATE_ROW> updatedRows,
            Function<UPDATE_ROW, ID> updateIdReader) {
        Map<ID, UPDATE_ROW> rowsById = new LinkedHashMap<>();
        for (UPDATE_ROW row : updatedRows) {
            if (row == null) {
                throw badRequest("updatedRows must not contain null rows.");
            }

            ID id = updateIdReader.apply(row);
            if (id == null) {
                throw badRequest("updatedRows.id must not be null.");
            }
            if (rowsById.put(id, row) != null) {
                throw badRequest("updatedRows.id must not contain duplicate ids.");
            }
        }
        return rowsById;
    }

    private <ID, UPDATE_ROW> void rejectDeleteUpdateConflicts(
            List<ID> deletedIds,
            Map<ID, UPDATE_ROW> updateRowsById) {
        for (ID deletedId : deletedIds) {
            if (updateRowsById.containsKey(deletedId)) {
                throw badRequest("deletedIds and updatedRows.id must not overlap.");
            }
        }
    }

    private <ID, ENTITY> int deleteRows(
            List<ID> deletedIds,
            JpaRepository<ENTITY, ID> repository,
            String missingResourceMessage) {
        if (deletedIds.isEmpty()) {
            return 0;
        }

        List<ENTITY> deleteTargets = repository.findAllById(deletedIds);
        if (deleteTargets.size() != deletedIds.size()) {
            throw new ResourceNotFoundException(missingResourceMessage);
        }
        repository.deleteAllByIdInBatch(deletedIds);
        return deletedIds.size();
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
            Map<ID, UPDATE_ROW> updateRowsById,
            JpaRepository<ENTITY, ID> repository,
            Function<ENTITY, ID> entityIdReader,
            BiConsumer<ENTITY, UPDATE_ROW> updateApplier,
            String missingResourceMessage) {
        if (updateRowsById.isEmpty()) {
            return List.of();
        }

        List<ENTITY> updateTargets = repository.findAllById(updateRowsById.keySet());
        if (updateTargets.size() != updateRowsById.size()) {
            throw new ResourceNotFoundException(missingResourceMessage);
        }

        for (ENTITY entity : updateTargets) {
            updateApplier.accept(entity, updateRowsById.get(entityIdReader.apply(entity)));
        }
        return updateTargets;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
