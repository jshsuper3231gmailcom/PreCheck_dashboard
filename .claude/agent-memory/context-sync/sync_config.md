---
name: sync-config
description: context_org 소스 디렉토리와 precheck_collect 하위 디렉토리 간 동기화 대상 파일 매핑
metadata:
  type: project
---

소스 디렉토리: `C:\Users\20200161\Desktop\PreCheck_work\Precheck_project\Precheck_SKSCh1\context_org`
대상 베이스: `C:\Users\20200161\Desktop\PreCheck_work\Precheck_project\Precheck_SKSCh1\precheck_collect`

context_org 내 대부분의 파일(프로그램 명세서, 정의서 등 *.md)은 precheck_collect 어디에도 동일 이름 파일이 없어 매칭되지 않음 (정상 - 무시 대상).

실제 동기화 대상으로 매칭되는 파일 3개:
- `PreCheck_AnalyzeLogs_Schedule.conf` -> `precheck_collect/analyze/schedule_sample/PreCheck_AnalyzeLogs_Schedule.conf`
- `PreCheck_AnalyzePolicy.conf` -> `precheck_collect/analyze/analyze_sample/PreCheck_AnalyzePolicy.conf`
- `PreCheck_CollectLogs_Schedule.conf` -> `precheck_collect/collect/schedule_sample/PreCheck_CollectLogs_Schedule.conf`

**Why:** context_org는 설계/정의 문서 + 샘플 설정파일(conf) 보관용 마스터 디렉토리이고, precheck_collect 하위 sample 디렉토리들은 실제 프로젝트에서 사용되는 conf 복사본 위치.

**How to apply:** 향후 context-sync 비교 작업 시 위 3개 conf 파일만 확인하면 됨. md 문서들은 대상 없음(스킵) 처리.

[[exclude_patterns]]
</content>
