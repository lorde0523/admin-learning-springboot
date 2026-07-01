package com.example.admin.menu.service;

import com.example.admin.common.exception.ResourceNotFoundException;
import com.example.admin.common.grid.GridSaveExecutor;
import com.example.admin.common.grid.GridSaveResult;
import com.example.admin.menu.dto.adminmenu.MenuGridRow;
import com.example.admin.menu.dto.adminmenu.MenuGridSaveRequest;
import com.example.admin.menu.dto.adminmenu.MenuGridSaveResponse;
import com.example.admin.menu.dto.adminmenu.MenuRequest;
import com.example.admin.menu.dto.adminmenu.MenuResponse;
import com.example.admin.menu.entity.AdminMenu;
import com.example.admin.menu.mapper.MenuMapper;
import com.example.admin.menu.repository.AdminMenuRepository;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public MenuResponse find(Long id) {
        return menuMapper.toResponse(menu(id));
    }

    public List<MenuResponse> search(String nameKeyword) {
        List<AdminMenu> menus = StringUtils.hasText(nameKeyword)
                ? menuRepository.searchByNameIgnoreCase(nameKeyword)
                : menuRepository.findAll();
        return menus.stream().map(menuMapper::toResponse).toList();
    }

    public List<MenuResponse> children(Long parentMenuId) {
        return menuRepository.findByParentMenuIdOrderBySortOrderAsc(parentMenuId).stream()
                .map(menuMapper::toResponse)
                .toList();
    }

    public Page<MenuResponse> searchPage(String nameKeyword, Pageable pageable) {
        Page<AdminMenu> menus = StringUtils.hasText(nameKeyword)
                ? menuRepository.findByMenuNameContainingIgnoreCase(nameKeyword, pageable)
                : menuRepository.findAll(pageable);
        return menus.map(menuMapper::toResponse);
    }

    @Transactional
    public MenuGridSaveResponse saveGrid(MenuGridSaveRequest request) {
        gridSaveExecutor.validateRequestConflicts(
                request.getCreatedRows(),
                request.getUpdatedRows(),
                request.getDeletedIds(),
                MenuGridRow::getId,
                MenuGridRow::getId,
                Function.identity());

        GridSaveResult result = gridSaveExecutor.delete(
                request.getDeletedIds(),
                menuRepository,
                Function.identity(),
                AdminMenu::getId,
                "존재하지 않는 메뉴가 포함되어 있습니다.")
                .merge(gridSaveExecutor.create(
                        request.getCreatedRows(),
                        menuRepository,
                        MenuGridRow::getId,
                        AdminMenu::getId,
                        menuMapper::toEntity))
                .merge(gridSaveExecutor.update(
                        request.getUpdatedRows(),
                        menuRepository,
                        MenuGridRow::getId,
                        AdminMenu::getId,
                        menuMapper::updateEntity,
                        "존재하지 않는 메뉴가 포함되어 있습니다."));

        return MenuGridSaveResponse.builder()
                .createdCount(result.getCreatedCount())
                .updatedCount(result.getUpdatedCount())
                .deletedCount(result.getDeletedCount())
                .messages(result.getMessages())
                .build();
    }

    private AdminMenu menu(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("메뉴를 찾을 수 없습니다. id=" + id));
    }
}

