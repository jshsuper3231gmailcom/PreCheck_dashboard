---
name: exclude-patterns
description: precheck_collect 디렉토리 탐색 시 제외해야 할 빌드/도구 디렉토리 목록
metadata:
  type: project
---

precheck_collect 하위 재귀 탐색 시 다음 디렉토리는 제외해야 함 (파일 수가 매우 많아 출력 폭주):
- `.git`, `.gradle`, `.apt_generated`, `.apt_generated_tests`, `build`, `.vscode`, `.trae`, `.claude`, `node_modules`

**Why:** analyze, collect, dashboard 등 각 모듈에 Gradle 빌드 산출물(.gradle, build, .apt_generated*)이 존재하여 전체 디렉토리 리스트 출력 시 응답이 매우 커짐.

**How to apply:** find 명령에 `-not -path "*/.git/*" -not -path "*/.gradle/*" -not -path "*/.apt_generated*/*" -not -path "*/build/*" -not -path "*/.vscode/*" -not -path "*/.trae/*" -not -path "*/.claude/*"` 추가. 디렉토리 전체 나열보다 소스 파일명 기준으로 `find . -name "<filename>"`을 개별 실행하는 방식이 효율적.

[[sync-config]]
</content>
