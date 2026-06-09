package com.example.admin.common.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentLoginUserController {

    @GetMapping("/api/auth/me")
    public Map<String, Object> me(@AuthenticationPrincipal LoginUser loginUser) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("authenticated", loginUser != null);
        response.put("principal", loginUser);
        response.put("securityContextUser", LoginUsers.current().orElse(null));
        return response;
    }
}
