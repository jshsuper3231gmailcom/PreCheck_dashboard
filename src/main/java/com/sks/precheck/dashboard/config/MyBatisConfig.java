package com.sks.precheck.dashboard.config;

import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * MyBatis DB 식별자 설정 클래스.
 *
 * 역할:
 * - 운영 Altibase와 테스트 PostgreSQL을 구분하는 `databaseId`를 매핑한다.
 *
 * 설계 이유:
 * - 같은 매퍼 XML 안에서 DB별 LIMIT 문법을 분기해야 하므로 벤더명을 명시적으로 고정한다.
 * - 화면 조회 로직은 동일하게 유지하고 SQL 차이만 XML에서 흡수하기 위해 분리했다.
 */
@Configuration
public class MyBatisConfig {

    /**
     * DB 벤더명을 MyBatis `databaseId`로 정규화한다.
     *
     * 반환값 의미:
     * - PostgreSQL은 `postgresql`, Altibase는 `altibase`로 매퍼 XML 분기 조건에 사용된다.
     */
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
