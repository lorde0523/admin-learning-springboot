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

    @Transactional
    public MenuGridSaveResponse saveGrid(MenuGridSaveRequest request) {
        GridSaveResult result = gridSaveExecutor.save(
                request.getCreatedRows(),
                request.getUpdatedRows(),
                request.getDeletedIds(),
                menuRepository,
                MenuGridRow::getId,
                AdminMenu::getId,
                menuMapper::toEntity,
                menuMapper::updateEntity,
                "존재하지 않는 메뉴가 포함되어 있습니다.");

        return MenuGridSaveResponse.builder()
                .createdCount(result.getCreatedCount())
                .updatedCount(result.getUpdatedCount())
                .deletedCount(result.getDeletedCount())
                .build();
    }

    private AdminMenu menu(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("메뉴를 찾을 수 없습니다. id=" + id));
    }
}

