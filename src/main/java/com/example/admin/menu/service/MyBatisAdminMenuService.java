package com.example.admin.menu.service;

import com.example.admin.common.exception.ResourceNotFoundException;
import com.example.admin.menu.dto.MenuDtos;
import com.example.admin.menu.mapper.AdminMenuMapper;
import java.util.List;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MyBatisAdminMenuService {

    private final AdminMenuMapper menuMapper;
    private final AuditorAware<String> auditorAware;

    public MyBatisAdminMenuService(AdminMenuMapper menuMapper, AuditorAware<String> auditorAware) {
        this.menuMapper = menuMapper;
        this.auditorAware = auditorAware;
    }

    public MenuDtos.MenuResponse create(MenuDtos.MenuRequest request) {
        Long id = menuMapper.nextId();
        menuMapper.insert(id, request, auditor());
        return find(id);
    }

    public MenuDtos.BulkInsertResponse bulkInsert(MenuDtos.MenuBulkRequest request) {
        List<AdminMenuMapper.MenuInsertRow> rows = request.menus().stream()
                .map(menu -> new AdminMenuMapper.MenuInsertRow(menuMapper.nextId(), menu))
                .toList();
        return new MenuDtos.BulkInsertResponse(menuMapper.bulkInsert(rows, auditor()));
    }

    @Transactional(readOnly = true)
    public MenuDtos.MenuResponse find(Long id) {
        AdminMenuMapper.MenuRow row = row(id);
        return row.toResponse();
    }

    @Transactional(readOnly = true)
    public List<MenuDtos.MenuResponse> search(String nameKeyword) {
        return menuMapper.search(nameKeyword).stream().map(AdminMenuMapper.MenuRow::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MenuDtos.MenuResponse> children(Long parentMenuId) {
        return menuMapper.children(parentMenuId).stream().map(AdminMenuMapper.MenuRow::toResponse).toList();
    }

    public MenuDtos.MenuResponse update(Long id, MenuDtos.MenuRequest request) {
        if (menuMapper.update(id, request, auditor()) == 0) {
            throw new ResourceNotFoundException("Menu " + id + " was not found.");
        }
        return find(id);
    }

    public void delete(Long id) {
        if (menuMapper.delete(id) == 0) {
            throw new ResourceNotFoundException("Menu " + id + " was not found.");
        }
    }

    private AdminMenuMapper.MenuRow row(Long id) {
        AdminMenuMapper.MenuRow row = menuMapper.findById(id);
        if (row == null) {
            throw new ResourceNotFoundException("Menu " + id + " was not found.");
        }
        return row;
    }

    private String auditor() {
        return auditorAware.getCurrentAuditor().orElse("system");
    }
}
