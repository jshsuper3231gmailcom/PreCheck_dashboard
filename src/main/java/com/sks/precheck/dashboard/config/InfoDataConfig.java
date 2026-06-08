package com.sks.precheck.dashboard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "precheck")
public class InfoDataConfig {
    private String collectSchedulePath;
    private String analyzeSchedulePath;
    private List<InfoDataItem> infoData = new ArrayList<>();

    @Data
    public static class InfoDataItem {
        private String name;
        private String serverId;
        private String logId;
    }
}
