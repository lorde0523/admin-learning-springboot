package com.example.admin.user.api;

import com.example.admin.user.dto.UserDtos;
import com.example.admin.user.service.MyBatisAdminUserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mybatis/users")
public class MyBatisAdminUserController {

    private final MyBatisAdminUserService userService;

    public MyBatisAdminUserController(MyBatisAdminUserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserDtos.UserResponse> create(@Valid @RequestBody UserDtos.UserRequest request) {
        UserDtos.UserResponse response = userService.create(request);
        return ResponseEntity.created(URI.create("/api/mybatis/users/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public UserDtos.UserResponse find(@PathVariable Long id) {
        return userService.find(id);
    }

    @GetMapping
    public List<UserDtos.UserResponse> search(@RequestParam(required = false) String loginKeyword) {
        return userService.search(loginKeyword);
    }

    @PutMapping("/{id}")
    public UserDtos.UserResponse update(@PathVariable Long id, @Valid @RequestBody UserDtos.UserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/roles")
    public UserDtos.UserResponse assignRoles(
            @PathVariable Long id, @Valid @RequestBody UserDtos.RoleAssignmentRequest request) {
        return userService.assignRoles(id, request);
    }
}

