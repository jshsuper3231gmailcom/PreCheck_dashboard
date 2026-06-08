package com.sks.precheck.dashboard.config;

import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class MyBatisConfig {

    @Bean
    public VendorDatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.setProperty("PostgreSQL", "postgresql");
        properties.setProperty("Altibase", "altibase");
        provider.setProperties(properties);
        return provider;
    }
}
