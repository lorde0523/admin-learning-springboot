package com.example.admin.menu.api;

import com.example.admin.menu.dto.adminmenu.MenuResponse;
import com.example.admin.menu.service.MyBatisAdminMenuService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mybatis/menus")
public class MyBatisAdminMenuController {

    private final MyBatisAdminMenuService menuService;

    public MyBatisAdminMenuController(MyBatisAdminMenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/page")
    public Page<MenuResponse> searchPage(
            @RequestParam(required = false) String nameKeyword,
            @PageableDefault(size = 20, sort = {"sortOrder", "id"}) Pageable pageable) {
        return menuService.searchPage(nameKeyword, pageable);
    }
}
