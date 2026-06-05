package com.sks.precheck.dashboard.mapper;

import com.sks.precheck.dashboard.dto.AnalyzeResultDto;
import com.sks.precheck.dashboard.dto.CollectLogDto;
import com.sks.precheck.dashboard.dto.SummaryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {
    SummaryDto selectSummary(@Param("today") String today);

    List<AnalyzeResultDto> selectErrorWarningList(
            @Param("today") String today,
            @Param("serverId") String serverId,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    List<AnalyzeResultDto> selectNormalInfoList(
            @Param("today") String today,
            @Param("serverId") String serverId,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    int countErrorWarning(
            @Param("today") String today,
            @Param("serverId") String serverId
    );

    int countNormalInfo(
            @Param("today") String today,
            @Param("serverId") String serverId
    );

    List<Map<String, Object>> selectServerList(@Param("today") String today);

    List<AnalyzeResultDto> selectHistoryData(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("serverId") String serverId,
            @Param("logId") String logId
    );

    List<Map<String, Object>> selectResourceData(@Param("today") String today);

    AnalyzeResultDto selectInfoData(
            @Param("today") String today,
            @Param("serverId") String serverId,
            @Param("logId") String logId
    );

    CollectLogDto selectRawLog(@Param("collectLogId") Long collectLogId);
}
