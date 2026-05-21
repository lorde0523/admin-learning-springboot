package com.example.admin.role.service;

import com.example.admin.common.exception.ResourceNotFoundException;
import com.example.admin.menu.dto.MenuDtos;
import com.example.admin.menu.mapper.AdminMenuMapper;
import com.example.admin.role.dto.RoleDtos;
import com.example.admin.role.mapper.AdminRoleMapper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MyBatisAdminRoleService {

    private final AdminRoleMapper roleMapper;
    private final AdminMenuMapper menuMapper;
    private final AuditorAware<String> auditorAware;

    public MyBatisAdminRoleService(
            AdminRoleMapper roleMapper, AdminMenuMapper menuMapper, AuditorAware<String> auditorAware) {
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.auditorAware = auditorAware;
    }

    public RoleDtos.RoleResponse create(RoleDtos.RoleRequest request) {
        Long id = roleMapper.nextId();
        roleMapper.insert(id, request, auditor());
        return row(id).toResponse();
    }

    @Transactional(readOnly = true)
    public RoleDtos.RoleResponse find(Long id) {
        return row(id).toResponse();
    }

    @Transactional(readOnly = true)
    public List<RoleDtos.RoleResponse> findAll() {
        return roleMapper.findAll().stream().map(AdminRoleMapper.RoleRow::toResponse).toList();
    }

    public RoleDtos.RoleResponse update(Long id, RoleDtos.RoleRequest request) {
        if (roleMapper.update(id, request, auditor()) == 0) {
            throw new ResourceNotFoundException("Role " + id + " was not found.");
        }
        return find(id);
    }

    public void delete(Long id) {
        if (roleMapper.delete(id) == 0) {
            throw new ResourceNotFoundException("Role " + id + " was not found.");
        }
    }

    public RoleDtos.RoleMenusResponse assignMenus(Long id, RoleDtos.MenuAssignmentRequest request) {
        row(id);
        roleMapper.deleteMenus(id);
        roleMapper.insertMenus(id, request.menuIds());
        return new RoleDtos.RoleMenusResponse(id, menus(id));
    }

    @Transactional(readOnly = true)
    public Set<MenuDtos.MenuResponse> menus(Long id) {
        row(id);
        return menuMapper.findByRoleId(id).stream()
                .map(AdminMenuMapper.MenuRow::toResponse)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private AdminRoleMapper.RoleRow row(Long id) {
        AdminRoleMapper.RoleRow row = roleMapper.findById(id);
        if (row == null) {
            throw new ResourceNotFoundException("Role " + id + " was not found.");
        }
        return row;
    }

    private String auditor() {
        return auditorAware.getCurrentAuditor().orElse("system");
    }
}

