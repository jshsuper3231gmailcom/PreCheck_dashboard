package com.sks.precheck.dashboard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard 전용 파일 기반 설정 바인딩 클래스.
 *
 * 역할:
 * - 수집/분석 스케줄 정의서 경로를 보관한다.
 * - 주요 데이터 카드에 표시할 서버구분과 LOG_ID 매핑을 보관한다.
 *
 * 설계 이유:
 * - 1차 버전은 관리자 화면이 없으므로, 대시보드 표시 대상도 설정 파일로 관리한다.
 * - 업무 용어와 LOG_ID 매핑을 코드 하드코딩 대신 설정으로 분리해 운영 변경 범위를 줄인다.
 */
@Data
@ConfigurationProperties(prefix = "precheck")
public class InfoDataConfig {
    private String collectSchedulePath;
    private String analyzeSchedulePath;
    private int historyDays = 7;
    private int refreshIntervalSeconds = 300;
    private List<InfoDataItem> infoData = new ArrayList<>();

    /**
     * 점검현황 상세(에러/경고, 정상/정보/미분석 탭)와 상단 요약 카운트에서
     * 완전히 제외할 LOG_ID 목록이다.
     *
     * 설계 이유:
     * - 실시간 접속자수처럼 상태 분류(에러/경고/정상/정보/미분석) 판정 자체가 의미 없는
     *   LOG_ID를 목록/집계에서 숨기기 위함이다. 수집·분석·전용 카드(스파크라인 등) 표시에는 영향을 주지 않는다.
     */
    private List<String> detailExcludeLogIds = new ArrayList<>();

    /**
     * 주요 데이터 카드 1건의 표시 대상을 정의한다.
     *
     * 역할:
     * - 화면 표시명, 조회할 서버구분, 조회할 LOG_ID를 한 묶음으로 보관한다.
     *
     * 설계 이유:
     * - 같은 LOG_ID라도 서버구분이 다르면 다른 업무 의미를 가질 수 있어 조합 단위로 관리한다.
     */
    @Data
    public static class InfoDataItem {
        private String name;
        private String serverId;
        private String logId;
    }
}
