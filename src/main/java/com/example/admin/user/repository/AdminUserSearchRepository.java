package com.example.admin.user.repository;

import com.example.admin.user.entity.AdminUser;
import java.util.List;

public interface AdminUserSearchRepository {

    List<AdminUser> searchByLoginIdIgnoreCase(String keyword);
}
