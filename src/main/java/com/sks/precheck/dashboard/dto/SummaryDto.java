package com.sks.precheck.dashboard.dto;

import lombok.Data;

import java.util.List;

/**
 * 대시보드 상단 요약 스트립 표시용 집계 DTO.
 *
 * 역할:
 * - 분석 레벨별 건수와 비율, 수집/분석 성공 현황, 장애 사유 목록을 함께 보관한다.
 *
 * 설계 이유:
 * - 요약 영역은 여러 테이블 집계를 섞어 보여주므로 화면 바인딩 단위를 하나로 고정하는 편이 단순하다.
 * - 실패/제외 사유를 별도 목록으로 보관해 툴팁과 건수 표시를 동시에 지원한다.
 */
@Data
public class SummaryDto {
    private int errorCnt;
    private int warnCnt;
    private int normalCnt;
    private int infoCnt;
    private int unknownCnt;

    private double errorRatio;
    private double warnRatio;
    private double normalRatio;
    private double infoRatio;
    private double unknownRatio;

    private int collectSuccess;
    private int collectTotal;
    private int collectFail;
    private int collectSkip;

    private int analyzeSuccess;
    private int analyzeTotal;
    private int analyzeFail;

    private double collectRatio;
    private double analyzeRatio;

    private List<String> collectFailReasons;
    private List<String> collectSkipReasons;
    private List<String> analyzeFailReasons;
}
