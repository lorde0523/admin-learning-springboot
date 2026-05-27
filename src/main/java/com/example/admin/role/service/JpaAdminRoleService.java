package com.example.admin.role.service;

import com.example.admin.common.exception.ResourceNotFoundException;
import com.example.admin.menu.dto.adminmenu.MenuResponse;
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
                AdminRole.create(request.getRoleCode(), request.getRoleName(), request.getEnabled())));
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
        role.update(request.getRoleName(), request.getEnabled());
        return RoleDtos.RoleResponse.from(role);
    }

    @Transactional
    public void delete(Long id) {
        roleRepository.delete(role(id));
    }

    @Transactional
    public RoleDtos.RoleMenusResponse assignMenus(Long id, RoleDtos.MenuAssignmentRequest request) {
        AdminRole role = roleWithMenus(id);
        var menus = new LinkedHashSet<>(menuRepository.findAllById(request.getMenuIds()));
        if (menus.size() != request.getMenuIds().size()) {
            throw new ResourceNotFoundException("존재하지 않는 메뉴가 포함되어 있습니다.");
        }
        role.assignMenus(menus);
        return menusResponse(role);
    }

    public Set<MenuResponse> menus(Long id) {
        return roleWithMenus(id).getMenus().stream()
                .map(MenuResponse::from)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private RoleDtos.RoleMenusResponse menusResponse(AdminRole role) {
        return RoleDtos.RoleMenusResponse.builder()
                .roleId(role.getId())
                .menus(menus(role.getId()))
                .build();
    }

    private AdminRole role(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("권한을 찾을 수 없습니다. id=" + id));
    }

    private AdminRole roleWithMenus(Long id) {
        return roleRepository.findWithMenusById(id)
                .orElseThrow(() -> new ResourceNotFoundException("권한을 찾을 수 없습니다. id=" + id));
    }
}

