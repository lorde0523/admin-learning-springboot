package com.example.admin.common.config;

import java.util.Properties;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisConfig {

    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties vendors = new Properties();
        vendors.setProperty("Oracle", "oracle");
        vendors.setProperty("H2", "h2");
        provider.setProperties(vendors);
        return provider;
    }
}

