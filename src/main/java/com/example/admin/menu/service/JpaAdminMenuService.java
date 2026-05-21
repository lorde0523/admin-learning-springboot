package com.example.admin.menu.service;

import com.example.admin.common.exception.ResourceNotFoundException;
import com.example.admin.menu.dto.MenuDtos;
import com.example.admin.menu.entity.AdminMenu;
import com.example.admin.menu.repository.AdminMenuRepository;
import java.util.List;
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
        menu.update(request.menuName(), request.parentMenuId(), request.sortOrder(), request.enabled());
        return MenuDtos.MenuResponse.from(menu);
    }

    @Transactional
    public void delete(Long id) {
        menuRepository.delete(menu(id));
    }

    private AdminMenu entity(MenuDtos.MenuRequest request) {
        return AdminMenu.create(
                request.menuCode(),
                request.menuName(),
                request.parentMenuId(),
                request.sortOrder(),
                request.enabled());
    }

    private AdminMenu menu(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu " + id + " was not found."));
    }
}

