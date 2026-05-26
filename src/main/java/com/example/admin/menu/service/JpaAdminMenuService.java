package com.example.admin.menu.service;

import com.example.admin.common.exception.ResourceNotFoundException;
import com.example.admin.common.grid.GridSaveExecutor;
import com.example.admin.common.grid.GridSaveResult;
import com.example.admin.menu.dto.MenuDtos;
import com.example.admin.menu.entity.AdminMenu;
import com.example.admin.menu.mapper.MenuMapper;
import com.example.admin.menu.repository.AdminMenuRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class JpaAdminMenuService {

    private final AdminMenuRepository menuRepository;
    private final GridSaveExecutor gridSaveExecutor;
    private final MenuMapper menuMapper;

    public JpaAdminMenuService(
            AdminMenuRepository menuRepository,
            GridSaveExecutor gridSaveExecutor,
            MenuMapper menuMapper) {
        this.menuRepository = menuRepository;
        this.gridSaveExecutor = gridSaveExecutor;
        this.menuMapper = menuMapper;
    }

    @Transactional
    public MenuDtos.MenuResponse create(MenuDtos.MenuRequest request) {
        return menuMapper.toResponse(menuRepository.save(menuMapper.toEntity(request)));
    }

    public MenuDtos.MenuResponse find(Long id) {
        return menuMapper.toResponse(menu(id));
    }

    public List<MenuDtos.MenuResponse> search(String nameKeyword) {
        List<AdminMenu> menus = StringUtils.hasText(nameKeyword)
                ? menuRepository.searchByNameIgnoreCase(nameKeyword)
                : menuRepository.findAll();
        return menus.stream().map(menuMapper::toResponse).toList();
    }

    public List<MenuDtos.MenuResponse> children(Long parentMenuId) {
        return menuRepository.findByParentMenuIdOrderBySortOrderAsc(parentMenuId).stream()
                .map(menuMapper::toResponse)
                .toList();
    }

    @Transactional
    public MenuDtos.MenuResponse update(Long id, MenuDtos.MenuRequest request) {
        AdminMenu menu = menu(id);
        menuMapper.updateEntity(menu, request);
        return menuMapper.toResponse(menu);
    }

    @Transactional
    public void delete(Long id) {
        menuRepository.delete(menu(id));
    }

    @Transactional
    public MenuDtos.GridSaveResponse saveGrid(MenuDtos.MenuGridSaveRequest request) {
        GridSaveResult result = gridSaveExecutor.save(
                request.getCreatedRows(),
                request.getUpdatedRows(),
                request.getDeletedIds(),
                menuRepository,
                MenuDtos.MenuGridRow::getId,
                AdminMenu::getId,
                menuMapper::toEntity,
                menuMapper::updateEntity,
                "One or more menus do not exist.");

        return MenuDtos.GridSaveResponse.builder()
                .createdCount(result.getCreatedCount())
                .updatedCount(result.getUpdatedCount())
                .deletedCount(result.getDeletedCount())
                .build();
    }

    private AdminMenu menu(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu " + id + " was not found."));
    }
}

