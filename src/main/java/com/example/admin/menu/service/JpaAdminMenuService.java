package com.example.admin.menu.service;

import com.example.admin.common.exception.ResourceNotFoundException;
import com.example.admin.menu.dto.MenuDtos;
import com.example.admin.menu.entity.AdminMenu;
import com.example.admin.menu.repository.AdminMenuRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class JpaAdminMenuService {

    private final AdminMenuRepository menuRepository;

    public JpaAdminMenuService(AdminMenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    @Transactional
    public MenuDtos.MenuResponse create(MenuDtos.MenuRequest request) {
        return MenuDtos.MenuResponse.from(menuRepository.save(entity(request)));
    }

    public MenuDtos.MenuResponse find(Long id) {
        return MenuDtos.MenuResponse.from(menu(id));
    }

    public List<MenuDtos.MenuResponse> search(String nameKeyword) {
        List<AdminMenu> menus = StringUtils.hasText(nameKeyword)
                ? menuRepository.searchByNameIgnoreCase(nameKeyword)
                : menuRepository.findAll();
        return menus.stream().map(MenuDtos.MenuResponse::from).toList();
    }

    public List<MenuDtos.MenuResponse> children(Long parentMenuId) {
        return menuRepository.findByParentMenuIdOrderBySortOrderAsc(parentMenuId).stream()
                .map(MenuDtos.MenuResponse::from)
                .toList();
    }

    @Transactional
    public MenuDtos.MenuResponse update(Long id, MenuDtos.MenuRequest request) {
        AdminMenu menu = menu(id);
        menu.update(request.getMenuName(), request.getParentMenuId(), request.getSortOrder(), request.getEnabled());
        return MenuDtos.MenuResponse.from(menu);
    }

    @Transactional
    public void delete(Long id) {
        menuRepository.delete(menu(id));
    }

    @Transactional
    public MenuDtos.GridSaveResponse saveGrid(MenuDtos.MenuGridSaveRequest request) {
        // ag-Grid는 등록/수정/삭제 row를 각각 다른 배열로 보낸다.
        // null 배열은 "변경 없음"으로 보고 빈 목록으로 정규화해 아래 로직을 단순하게 유지한다.
        List<Long> requestedDeletedIds = request.getDeletedIds() == null ? List.of() : request.getDeletedIds();
        List<MenuDtos.MenuRequest> createdRows =
                request.getCreatedRows() == null ? List.of() : request.getCreatedRows();
        List<MenuDtos.MenuGridRow> updatedRows =
                request.getUpdatedRows() == null ? List.of() : request.getUpdatedRows();

        // 같은 요청 안에서 같은 id를 두 번 처리하면 실제 화면 상태와 DB 상태가 어긋날 수 있다.
        // 삭제 id는 중복/null을 제거하지 않고 요청 오류로 돌려주고, 수정 row는 id 기준 Map으로 바꾼다.
        List<Long> deletedIds = uniqueIds(requestedDeletedIds, "deletedIds");
        Map<Long, MenuDtos.MenuGridRow> updateRowsById = updateRowsById(updatedRows);

        // 하나의 row가 삭제와 수정에 동시에 들어오면 어떤 상태가 최종 의도인지 모호하므로 저장 전에 막는다.
        rejectDeleteUpdateConflicts(deletedIds, updateRowsById);

        if (!deletedIds.isEmpty()) {
            // deleteAllByIdInBatch는 존재하지 않는 id를 조용히 지나칠 수 있으므로 먼저 전부 조회해 검증한다.
            List<AdminMenu> deleteTargets = menuRepository.findAllById(deletedIds);
            if (deleteTargets.size() != deletedIds.size()) {
                throw new ResourceNotFoundException("One or more menus do not exist.");
            }
            // 삭제는 수정/등록보다 먼저 처리한다. 같은 트랜잭션 안에서 FK/unique 제약 충돌 가능성을 줄이기 위함이다.
            menuRepository.deleteAllByIdInBatch(deletedIds);
        }

        // 등록 row는 id가 아직 없으므로 DTO를 새 Entity로 변환해 JPA가 식별자와 auditing을 처리하게 둔다.
        List<AdminMenu> createdMenus = createdRows.stream()
                .map(this::entity)
                .toList();
        menuRepository.saveAll(createdMenus);

        // 수정 row는 managed entity를 조회한 뒤 도메인 메서드로 상태를 바꾼다.
        // 이렇게 해야 dirty checking, auditing, entity 변경 규칙을 JPA 흐름 안에서 유지할 수 있다.
        List<AdminMenu> updateTargets = updateRowsById.isEmpty()
                ? List.of()
                : menuRepository.findAllById(updateRowsById.keySet());

        // 요청한 수정 대상이 일부라도 없으면 부분 성공으로 처리하지 않고 전체 트랜잭션을 실패시킨다.
        if (updateTargets.size() != updateRowsById.size()) {
            throw new ResourceNotFoundException("One or more menus do not exist.");
        }

        for (AdminMenu menu : updateTargets) {
            MenuDtos.MenuGridRow row = updateRowsById.get(menu.getId());
            menu.update(row.getMenuName(), row.getParentMenuId(), row.getSortOrder(), row.getEnabled());
        }

        return MenuDtos.GridSaveResponse.builder()
                .createdCount(createdMenus.size())
                .updatedCount(updateTargets.size())
                .deletedCount(deletedIds.size())
                .build();
    }

    private List<Long> uniqueIds(List<Long> ids, String fieldName) {
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null) {
                throw badRequest(fieldName + " must not contain null ids.");
            }
            if (!uniqueIds.add(id)) {
                throw badRequest(fieldName + " must not contain duplicate ids.");
            }
        }
        return List.copyOf(uniqueIds);
    }

    private Map<Long, MenuDtos.MenuGridRow> updateRowsById(List<MenuDtos.MenuGridRow> updatedRows) {
        Map<Long, MenuDtos.MenuGridRow> rowsById = new LinkedHashMap<>();
        for (MenuDtos.MenuGridRow row : updatedRows) {
            if (row == null || row.getId() == null) {
                throw badRequest("updatedRows.id must not be null.");
            }
            if (rowsById.put(row.getId(), row) != null) {
                throw badRequest("updatedRows.id must not contain duplicate ids.");
            }
        }
        return rowsById;
    }

    private void rejectDeleteUpdateConflicts(List<Long> deletedIds, Map<Long, MenuDtos.MenuGridRow> updateRowsById) {
        for (Long deletedId : deletedIds) {
            if (updateRowsById.containsKey(deletedId)) {
                throw badRequest("deletedIds and updatedRows.id must not overlap.");
            }
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private AdminMenu entity(MenuDtos.MenuRequest request) {
        return AdminMenu.create(
                request.getMenuCode(),
                request.getMenuName(),
                request.getParentMenuId(),
                request.getSortOrder(),
                request.getEnabled());
    }

    private AdminMenu menu(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu " + id + " was not found."));
    }
}

