package com.example.admin.menu.repository;

import com.example.admin.menu.entity.AdminMenu;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminMenuRepository extends JpaRepository<AdminMenu, Long> {

    List<AdminMenu> findByParentMenuIdOrderBySortOrderAsc(Long parentMenuId);

    @Query("select menu from AdminMenu menu where upper(menu.menuName) like upper(concat('%', :keyword, '%')) order by menu.sortOrder")
    List<AdminMenu> searchByNameIgnoreCase(@Param("keyword") String keyword);

    Page<AdminMenu> findByMenuNameContainingIgnoreCase(String keyword, Pageable pageable);
}
