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
        List<Long> requestedDeletedIds = request.getDeletedIds() == null ? List.of() : request.getDeletedIds();
        List<MenuDtos.MenuRequest> createdRows =
                request.getCreatedRows() == null ? List.of() : request.getCreatedRows();
        List<MenuDtos.MenuGridRow> updatedRows =
                request.getUpdatedRows() == null ? List.of() : request.getUpdatedRows();

        List<Long> deletedIds = uniqueIds(requestedDeletedIds, "deletedIds");
        Map<Long, MenuDtos.MenuGridRow> updateRowsById = updateRowsById(updatedRows);

        if (!deletedIds.isEmpty()) {
            List<AdminMenu> deleteTargets = menuRepository.findAllById(deletedIds);
            if (deleteTargets.size() != deletedIds.size()) {
                throw new ResourceNotFoundException("One or more menus do not exist.");
            }
            menuRepository.deleteAllByIdInBatch(deletedIds);
        }

        List<AdminMenu> createdMenus = createdRows.stream()
                .map(this::entity)
                .toList();
        menuRepository.saveAll(createdMenus);

        List<AdminMenu> updateTargets = updateRowsById.isEmpty()
                ? List.of()
                : menuRepository.findAllById(updateRowsById.keySet());

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

