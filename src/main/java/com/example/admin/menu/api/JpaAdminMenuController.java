package com.example.admin.menu.api;

import com.example.admin.menu.dto.MenuDtos;
import com.example.admin.menu.service.JpaAdminMenuService;
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
@RequestMapping("/api/jpa/menus")
public class JpaAdminMenuController {

    private final JpaAdminMenuService menuService;

    public JpaAdminMenuController(JpaAdminMenuService menuService) {
        this.menuService = menuService;
    }

    @PostMapping
    public ResponseEntity<MenuDtos.MenuResponse> create(@Valid @RequestBody MenuDtos.MenuRequest request) {
        MenuDtos.MenuResponse response = menuService.create(request);
        return ResponseEntity.created(URI.create("/api/jpa/menus/" + response.getId())).body(response);
    }

    @PostMapping("/grid-save")
    public MenuDtos.GridSaveResponse saveGrid(@Valid @RequestBody MenuDtos.MenuGridSaveRequest request) {
        return menuService.saveGrid(request);
    }

    @GetMapping("/{id}")
    public MenuDtos.MenuResponse find(@PathVariable Long id) {
        return menuService.find(id);
    }

    @GetMapping
    public List<MenuDtos.MenuResponse> search(@RequestParam(required = false) String nameKeyword) {
        return menuService.search(nameKeyword);
    }

    @GetMapping("/children")
    public List<MenuDtos.MenuResponse> children(@RequestParam Long parentMenuId) {
        return menuService.children(parentMenuId);
    }

    @PutMapping("/{id}")
    public MenuDtos.MenuResponse update(@PathVariable Long id, @Valid @RequestBody MenuDtos.MenuRequest request) {
        return menuService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
