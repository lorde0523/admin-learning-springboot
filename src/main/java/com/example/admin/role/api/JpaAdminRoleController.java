package com.example.admin.role.api;

import com.example.admin.menu.dto.MenuDtos;
import com.example.admin.role.dto.RoleDtos;
import com.example.admin.role.service.JpaAdminRoleService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jpa/roles")
public class JpaAdminRoleController {

    private final JpaAdminRoleService roleService;

    public JpaAdminRoleController(JpaAdminRoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    public ResponseEntity<RoleDtos.RoleResponse> create(@Valid @RequestBody RoleDtos.RoleRequest request) {
        RoleDtos.RoleResponse response = roleService.create(request);
        return ResponseEntity.created(URI.create("/api/jpa/roles/" + response.getId())).body(response);
    }

    @GetMapping("/{id}")
    public RoleDtos.RoleResponse find(@PathVariable Long id) {
        return roleService.find(id);
    }

    @GetMapping
    public List<RoleDtos.RoleResponse> findAll() {
        return roleService.findAll();
    }

    @PutMapping("/{id}")
    public RoleDtos.RoleResponse update(@PathVariable Long id, @Valid @RequestBody RoleDtos.RoleRequest request) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/menus")
    public RoleDtos.RoleMenusResponse assignMenus(
            @PathVariable Long id, @Valid @RequestBody RoleDtos.MenuAssignmentRequest request) {
        return roleService.assignMenus(id, request);
    }

    @GetMapping("/{id}/menus")
    public Set<MenuDtos.MenuResponse> menus(@PathVariable Long id) {
        return roleService.menus(id);
    }
}

