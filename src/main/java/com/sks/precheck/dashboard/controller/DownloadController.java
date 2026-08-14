package com.sks.precheck.dashboard.controller;

import com.sks.precheck.dashboard.dto.DownloadListDto;
import com.sks.precheck.dashboard.security.AdminUserPrincipal;
import com.sks.precheck.dashboard.service.DownloadRootNotFoundException;
import com.sks.precheck.dashboard.service.DownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Download 화면과 파일 목록/다운로드 API 진입점을 제공하는 컨트롤러.
 *
 * 역할:
 * - 실행폴더 하위 download 디렉터리를 탐색하는 화면을 반환한다.
 * - 폴더 목록 조회 API와 개별 파일 다운로드를 제공한다.
 *
 * 설계 이유:
 * - 경로 검증은 전부 DownloadService에 위임하고, 컨트롤러는 예외를 화면이 다루기 쉬운
 *   형태(ApiResponse 또는 HTTP 상태코드)로만 변환한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DownloadController {

    private final DownloadService downloadService;

    /**
     * Download 화면을 반환한다.
     */
    @GetMapping("/dashboard/download")
    public String page(@AuthenticationPrincipal AdminUserPrincipal principal, Model model) {
        model.addAttribute("loginUserName", principal.getAdminUser().getUserName());
        model.addAttribute("loginUserRole", principal.getAdminUser().getRole());
        return "dashboard/download";
    }

    /**
     * 폴더 목록 조회 API.
     *
     * @param path 루트 기준 상대경로다(생략 시 루트).
     * @return 정규화된 현재 경로와 하위 폴더/파일 목록 응답이다.
     */
    @ResponseBody
    @GetMapping("/dashboard/api/download/list")
    public ApiResponse<DownloadListDto> list(
            @RequestParam(value = "path", required = false, defaultValue = "") String path
    ) {
        try {
            return ApiResponse.ok(downloadService.list(path));
        } catch (DownloadRootNotFoundException e) {
            return ApiResponse.fail("download 디렉터리가 존재하지 않습니다.");
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail("잘못된 경로입니다.");
        } catch (Exception e) {
            log.error("[DownloadController] list 조회 중 오류", e);
            return ApiResponse.fail("조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 개별 파일을 다운로드한다.
     *
     * @param path 루트 기준 상대경로다.
     * @return 파일을 첨부파일로 담은 스트림 응답이다.
     */
    @GetMapping("/dashboard/download/file")
    public ResponseEntity<Resource> downloadFile(@RequestParam("path") String path) {
        try {
            Path resolved = downloadService.resolveFileForDownload(path);
            Resource resource = new InputStreamResource(Files.newInputStream(resolved));
            String filename = resolved.getFileName().toString();
            // Content-Disposition 헤더는 ASCII만 허용되므로, filename="..." 폴백은 비ASCII 문자를
            // '_'로 치환하고 실제 파일명은 filename*=UTF-8''(RFC 5987)로만 전달한다.
            String asciiFallback = filename.replaceAll("[^\\x20-\\x7E]", "_");
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(resolved))
                    .body(resource);
        } catch (DownloadRootNotFoundException | NoSuchFileException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("[DownloadController] file 다운로드 중 오류", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
