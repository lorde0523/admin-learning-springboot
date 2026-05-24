package com.example.admin.menu.service;

import com.example.admin.common.exception.ResourceNotFoundException;
import com.example.admin.menu.dto.MenuDtos;
import com.example.admin.menu.entity.AdminMenu;
import com.example.admin.menu.repository.AdminMenuRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
        List<Long> deletedIds = request.getDeletedIds() == null ? List.of() : request.getDeletedIds();
        List<MenuDtos.MenuRequest> createdRows =
                request.getCreatedRows() == null ? List.of() : request.getCreatedRows();
        List<MenuDtos.MenuGridRow> updatedRows =
                request.getUpdatedRows() == null ? List.of() : request.getUpdatedRows();

        if (!deletedIds.isEmpty()) {
            menuRepository.deleteAllByIdInBatch(deletedIds);
        }

        List<AdminMenu> createdMenus = createdRows.stream()
                .map(this::entity)
                .toList();
        menuRepository.saveAll(createdMenus);

        Map<Long, MenuDtos.MenuGridRow> updateRowsById = updatedRows.stream()
                .collect(Collectors.toMap(MenuDtos.MenuGridRow::getId, Function.identity()));

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

