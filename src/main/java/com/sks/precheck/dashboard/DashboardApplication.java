package com.sks.precheck.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * PreCheck Dashboard 애플리케이션 진입점.
 *
 * 역할:
 * - Dashboard 웹 애플리케이션과 설정 바인딩을 함께 기동한다.
 *
 * 설계 이유:
 * - `@ConfigurationPropertiesScan`을 함께 사용해 파일 기반 설정을 별도 수동 등록 없이 읽도록 구성했다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DashboardApplication {

	/**
	 * Spring Boot 애플리케이션을 시작한다.
	 *
	 * @param args 실행 인자다.
	 */
	public static void main(String[] args) {
		SpringApplication.run(DashboardApplication.class, args);
	}
}
