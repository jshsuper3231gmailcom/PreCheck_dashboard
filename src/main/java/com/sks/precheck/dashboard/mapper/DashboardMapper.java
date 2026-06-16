package com.sks.precheck.dashboard.mapper;

import com.sks.precheck.dashboard.dto.AnalyzeResultDto;
import com.sks.precheck.dashboard.dto.CollectLogDto;
import com.sks.precheck.dashboard.dto.SummaryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Dashboard 조회 전용 MyBatis 매퍼.
 *
 * 역할:
 * - 대시보드 화면에 필요한 수집, 분석, 통보 조회 SQL을 제공한다.
 *
 * 설계 이유:
 * - Dashboard는 조회 전용이므로 쓰기 SQL 없이 화면 단위 조회 쿼리만 분리한다.
 * - Altibase/PostgreSQL 호환 차이는 XML에서 분기하고, 자바 인터페이스는 업무 의미 중심으로 유지한다.
 */
@Mapper
public interface DashboardMapper {
    /**
     * 오늘 기준 상단/하단 요약 스트립 집계값을 조회한다.
     *
     * @param today 조회 기준 일자(`yyyyMMdd`)다.
     * @return 분석 레벨별 건수와 수집/분석 현황 집계다.
     */
    SummaryDto selectSummary(@Param("today") String today);

    /**
     * 수집 실패 또는 수집 제외 사유를 서버별로 조회한다.
     *
     * @param today 조회 기준 일자다.
     * @param status `FAIL`, `SKIP` 중 하나다.
     * @return 서버구분과 실패 사유를 담은 목록이다.
     */
    List<Map<String, Object>> selectCollectFailReasons(
            @Param("today") String today,
            @Param("status") String status
    );

    /**
     * 분석 실패 사유를 서버별로 조회한다.
     *
     * @param today 조회 기준 일자다.
     * @param status 현재는 `FAIL`만 사용한다.
     * @return 서버구분과 실패 사유를 담은 목록이다.
     */
    List<Map<String, Object>> selectAnalyzeFailReasons(
            @Param("today") String today,
            @Param("status") String status
    );

    /**
     * 에러/경고 탭 목록을 페이지 단위로 조회한다.
     *
     * @param today 조회 기준 일자다.
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다.
     * @param offset 페이징 시작 위치다.
     * @param pageSize 한 페이지에 보여줄 건수다.
     * @return 에러/경고 분석 결과 목록이다.
     */
    List<AnalyzeResultDto> selectErrorWarningList(
            @Param("today") String today,
            @Param("serverId") String serverId,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    /**
     * 정상/정보/미분석 탭 목록을 페이지 단위로 조회한다.
     *
     * @param today 조회 기준 일자다.
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다.
     * @param offset 페이징 시작 위치다.
     * @param pageSize 한 페이지에 보여줄 건수다.
     * @return 정상/정보/미분석 분석 결과 목록이다.
     */
    List<AnalyzeResultDto> selectNormalInfoList(
            @Param("today") String today,
            @Param("serverId") String serverId,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    /**
     * 에러/경고 탭 전체 건수를 조회한다.
     *
     * @param today 조회 기준 일자다.
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다.
     * @return 에러/경고 전체 건수다.
     */
    int countErrorWarning(
            @Param("today") String today,
            @Param("serverId") String serverId
    );

    /**
     * 정상/정보/미분석 탭 전체 건수를 조회한다.
     *
     * @param today 조회 기준 일자다.
     * @param serverId 특정 서버만 조회할 때 사용하는 서버구분이다.
     * @return 정상/정보/미분석 전체 건수다.
     */
    int countNormalInfo(
            @Param("today") String today,
            @Param("serverId") String serverId
    );

    /**
     * 서버 리스트 카드 표시용 서버별 요약 정보를 조회한다.
     *
     * @param today 조회 기준 일자다.
     * @return 서버구분, 최근 수집/분석 시각, 에러/경고 건수 목록이다.
     */
    List<Map<String, Object>> selectServerList(@Param("today") String today);

    /**
     * 히스토리 그래프용 기간 내 분석 결과를 조회한다.
     *
     * @param startDate 조회 시작일이다.
     * @param endDate 조회 종료일이다.
     * @param serverId 조회 대상 서버구분이다.
     * @param logId 조회 대상 LOG_ID다.
     * @return 시계열 그래프 계산에 사용할 분석 결과 목록이다.
     */
    List<AnalyzeResultDto> selectHistoryData(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("serverId") String serverId,
            @Param("logId") String logId
    );

    /**
     * 서버별 리소스 도넛 차트용 최신 분석 결과를 조회한다.
     *
     * @param today 조회 기준 일자다.
     * @return 서버별 최신 리소스 수치와 임계치 목록이다.
     */
    List<Map<String, Object>> selectResourceData(@Param("today") String today);

    /**
     * 주요 데이터 카드 1건에 대응하는 최신 분석 결과를 조회한다.
     *
     * @param today 조회 기준 일자다.
     * @param serverId 조회 대상 서버구분이다.
     * @param logId 조회 대상 LOG_ID다.
     * @return 카드 표시용 최신 분석 결과 1건이다.
     */
    AnalyzeResultDto selectInfoData(
            @Param("today") String today,
            @Param("serverId") String serverId,
            @Param("logId") String logId
    );

    /**
     * UC 실시간 접속자수 오늘 전체 시계열을 조회한다.
     *
     * @param today 조회 기준 일자(`yyyyMMdd`)다.
     * @param logId `UC_TOTAL_COUNT`, `UC_HTS_COUNT`, `UC_MTS_COUNT` 중 하나다.
     * @return 시간순 정렬된 시계열 포인트 목록이다.
     */
    List<Map<String, Object>> selectUcSparkData(
            @Param("today") String today,
            @Param("logId") String logId
    );

    /**
     * 원본 로그 모달에 표시할 수집 로그 1건을 조회한다.
     *
     * @param collectLogId 조회 대상 수집 로그 식별자다.
     * @return 원문 정규화 로그와 수집 메타 정보를 담은 데이터다.
     */
    CollectLogDto selectRawLog(@Param("collectLogId") Long collectLogId);
}
