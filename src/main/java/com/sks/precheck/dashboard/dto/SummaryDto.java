package com.sks.precheck.dashboard.dto;

import lombok.Data;

import java.util.List;

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
