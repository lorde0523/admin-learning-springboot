package com.example.admin.menu.api;

import com.example.admin.menu.dto.adminmenu.MenuGridSaveRequest;
import com.example.admin.menu.dto.adminmenu.MenuGridSaveResponse;
import com.example.admin.menu.dto.adminmenu.MenuResponse;
import com.example.admin.menu.service.JpaAdminMenuService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jpa/menus")
public class JpaAdminMenuController {

    private final JpaAdminMenuService menuService;

    public JpaAdminMenuController(JpaAdminMenuService menuService) {
        this.menuService = menuService;
    }

    @PostMapping("/grid-save")
    public MenuGridSaveResponse saveGrid(@Valid @RequestBody MenuGridSaveRequest request) {
        return menuService.saveGrid(request);
    }

    @GetMapping("/{id}")
    public MenuResponse find(@PathVariable Long id) {
        return menuService.find(id);
    }

    @GetMapping
    public List<MenuResponse> search(@RequestParam(required = false) String nameKeyword) {
        return menuService.search(nameKeyword);
    }

    @GetMapping("/children")
    public List<MenuResponse> children(@RequestParam Long parentMenuId) {
        return menuService.children(parentMenuId);
    }
}
