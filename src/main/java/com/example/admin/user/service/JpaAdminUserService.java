package com.example.admin.user.service;

import com.example.admin.common.exception.ResourceNotFoundException;
import com.example.admin.role.repository.AdminRoleRepository;
import com.example.admin.user.dto.UserDtos;
import com.example.admin.user.entity.AdminUser;
import com.example.admin.user.repository.AdminUserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class JpaAdminUserService {

    private final AdminUserRepository userRepository;
    private final AdminRoleRepository roleRepository;

    public JpaAdminUserService(AdminUserRepository userRepository, AdminRoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public UserDtos.UserResponse create(UserDtos.UserRequest request) {
        return UserDtos.UserResponse.from(userRepository.saveAndFlush(
                AdminUser.create(request.getLoginId(), request.getName(), request.getEnabled())));
    }

    public UserDtos.UserResponse find(Long id) {
        return UserDtos.UserResponse.from(userWithRoles(id));
    }

    public List<UserDtos.UserResponse> search(String loginKeyword) {
        List<AdminUser> users = StringUtils.hasText(loginKeyword)
                ? userRepository.findAll().stream()
                        .filter(user -> matchesLoginKeyword(user, loginKeyword))
                        .toList()
                : userRepository.findAll();
        return users.stream().map(UserDtos.UserResponse::from).toList();
    }

    @Transactional
    public UserDtos.UserResponse update(Long id, UserDtos.UserRequest request) {
        AdminUser user = userWithRoles(id);
        user.update(request.getName(), request.getEnabled());
        return UserDtos.UserResponse.from(user);
    }

    @Transactional
    public void delete(Long id) {
        userRepository.delete(userWithRoles(id));
    }

    @Transactional
    public UserDtos.UserResponse assignRoles(Long id, UserDtos.RoleAssignmentRequest request) {
        AdminUser user = userWithRoles(id);
        var roles = new LinkedHashSet<>(roleRepository.findAllById(request.getRoleIds()));
        if (roles.size() != request.getRoleIds().size()) {
            throw new ResourceNotFoundException("One or more roles do not exist.");
        }
        user.assignRoles(roles);
        return UserDtos.UserResponse.from(user);
    }

    private AdminUser userWithRoles(Long id) {
        return userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " was not found."));
    }

    private boolean matchesLoginKeyword(AdminUser user, String loginKeyword) {
        String regex = likePattern("%" + loginKeyword + "%");
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(user.getLoginId()).matches();
    }

    private String likePattern(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (int index = 0; index < pattern.length(); index++) {
            char character = pattern.charAt(index);
            if (character == '%') {
                regex.append(".*");
            } else if (character == '_') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(character)));
            }
        }
        return regex.toString();
    }
}
