package com.example.admin.menu.service;

import com.example.admin.menu.dto.adminmenu.MenuResponse;
import com.example.admin.menu.store.MyBatisAdminMenuStore;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MyBatisAdminMenuService {

    private final MyBatisAdminMenuStore menuStore;

    public MyBatisAdminMenuService(MyBatisAdminMenuStore menuStore) {
        this.menuStore = menuStore;
    }

    public Page<MenuResponse> searchPage(String nameKeyword, Pageable pageable) {
        List<MenuResponse> content = menuStore
                .searchPage(nameKeyword, pageable.getPageSize(), pageable.getOffset())
                .stream()
                .map(MyBatisAdminMenuStore.MenuRow::toResponse)
                .toList();
        long total = menuStore.countSearch(nameKeyword);
        return new PageImpl<>(content, pageable, total);
    }
}
