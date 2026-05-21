package com.example.admin.user.service;

import com.example.admin.common.exception.ResourceNotFoundException;
import com.example.admin.role.mapper.AdminRoleMapper;
import com.example.admin.user.dto.UserDtos;
import com.example.admin.user.mapper.AdminUserMapper;
import java.util.List;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MyBatisAdminUserService {

    private final AdminUserMapper userMapper;
    private final AdminRoleMapper roleMapper;
    private final AuditorAware<String> auditorAware;

    public MyBatisAdminUserService(
            AdminUserMapper userMapper, AdminRoleMapper roleMapper, AuditorAware<String> auditorAware) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.auditorAware = auditorAware;
    }

    public UserDtos.UserResponse create(UserDtos.UserRequest request) {
        Long id = userMapper.nextId();
        userMapper.insert(id, request, auditor());
        return find(id);
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse find(Long id) {
        AdminUserMapper.UserRow user = row(id);
        return user.toResponse(roleMapper.findByUserId(id).stream().map(AdminRoleMapper.RoleRow::toSummary).toList());
    }

    @Transactional(readOnly = true)
    public List<UserDtos.UserResponse> search(String loginKeyword) {
        return userMapper.search(loginKeyword).stream()
                .map(row -> row.toResponse(roleMapper.findByUserId(row.id()).stream()
                        .map(AdminRoleMapper.RoleRow::toSummary)
                        .toList()))
                .toList();
    }

    public UserDtos.UserResponse update(Long id, UserDtos.UserRequest request) {
        if (userMapper.update(id, request, auditor()) == 0) {
            throw new ResourceNotFoundException("User " + id + " was not found.");
        }
        return find(id);
    }

    public void delete(Long id) {
        if (userMapper.delete(id) == 0) {
            throw new ResourceNotFoundException("User " + id + " was not found.");
        }
    }

    public UserDtos.UserResponse assignRoles(Long id, UserDtos.RoleAssignmentRequest request) {
        row(id);
        userMapper.deleteRoles(id);
        userMapper.insertRoles(id, request.roleIds());
        return find(id);
    }

    private AdminUserMapper.UserRow row(Long id) {
        AdminUserMapper.UserRow row = userMapper.findById(id);
        if (row == null) {
            throw new ResourceNotFoundException("User " + id + " was not found.");
        }
        return row;
    }

    private String auditor() {
        return auditorAware.getCurrentAuditor().orElse("system");
    }
}

