package com.example.admin.role.service;

import com.example.admin.common.exception.ResourceNotFoundException;
import com.example.admin.menu.dto.MenuDtos;
import com.example.admin.menu.repository.AdminMenuRepository;
import com.example.admin.role.dto.RoleDtos;
import com.example.admin.role.entity.AdminRole;
import com.example.admin.role.repository.AdminRoleRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JpaAdminRoleService {

    private final AdminRoleRepository roleRepository;
    private final AdminMenuRepository menuRepository;

    public JpaAdminRoleService(AdminRoleRepository roleRepository, AdminMenuRepository menuRepository) {
        this.roleRepository = roleRepository;
        this.menuRepository = menuRepository;
    }

    @Transactional
    public RoleDtos.RoleResponse create(RoleDtos.RoleRequest request) {
        return RoleDtos.RoleResponse.from(roleRepository.save(
                AdminRole.create(request.roleCode(), request.roleName(), request.enabled())));
    }

    public RoleDtos.RoleResponse find(Long id) {
        return RoleDtos.RoleResponse.from(role(id));
    }

    public List<RoleDtos.RoleResponse> findAll() {
        return roleRepository.findAll().stream().map(RoleDtos.RoleResponse::from).toList();
    }

    @Transactional
    public RoleDtos.RoleResponse update(Long id, RoleDtos.RoleRequest request) {
        AdminRole role = role(id);
        role.update(request.roleName(), request.enabled());
        return RoleDtos.RoleResponse.from(role);
    }

    @Transactional
    public void delete(Long id) {
        roleRepository.delete(role(id));
    }

    @Transactional
    public RoleDtos.RoleMenusResponse assignMenus(Long id, RoleDtos.MenuAssignmentRequest request) {
        AdminRole role = roleWithMenus(id);
        var menus = new LinkedHashSet<>(menuRepository.findAllById(request.menuIds()));
        if (menus.size() != request.menuIds().size()) {
            throw new ResourceNotFoundException("One or more menus do not exist.");
        }
        role.assignMenus(menus);
        return menusResponse(role);
    }

    public Set<MenuDtos.MenuResponse> menus(Long id) {
        return roleWithMenus(id).getMenus().stream()
                .map(MenuDtos.MenuResponse::from)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private RoleDtos.RoleMenusResponse menusResponse(AdminRole role) {
        return new RoleDtos.RoleMenusResponse(role.getId(), menus(role.getId()));
    }

    private AdminRole role(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role " + id + " was not found."));
    }

    private AdminRole roleWithMenus(Long id) {
        return roleRepository.findWithMenusById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role " + id + " was not found."));
    }
}

