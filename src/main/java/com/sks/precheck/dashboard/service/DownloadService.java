package com.sks.precheck.dashboard.service;

import com.sks.precheck.dashboard.config.InfoDataConfig;
import com.sks.precheck.dashboard.dto.DownloadEntryDto;
import com.sks.precheck.dashboard.dto.DownloadListDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Download 화면에서 실행폴더 하위 download 디렉터리를 탐색·다운로드하는 로직을 담당한다.
 *
 * 역할:
 * - 사용자 입력 경로를 download 루트 기준으로 안전하게 해석한다(경로 순회 방지).
 * - 디렉터리 목록 조회와 다운로드 대상 파일 확인을 제공한다.
 *
 * 설계 이유:
 * - 파일시스템 경로를 사용자 입력으로 다루는 유일한 지점이므로, 정규화 후 루트 포함관계
 *   검증과 심볼릭 링크 실경로 검증을 한 곳에 모아 컨트롤러가 검증을 생략할 여지를 없앤다.
 */
@Service
@RequiredArgsConstructor
public class DownloadService {

    private final InfoDataConfig infoDataConfig;

    /**
     * download 루트 디렉터리를 정규화된 절대경로로 반환한다.
     *
     * @return 존재가 확인된 루트 디렉터리 경로다.
     */
    private Path resolveRoot() {
        Path root = Path.of(infoDataConfig.getDownloadDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new DownloadRootNotFoundException();
        }
        return root;
    }

    /**
     * 사용자 입력 경로를 루트 기준으로 안전하게 해석한다.
     *
     * 검증 순서:
     * - 절대경로/드라이브경로는 즉시 거부한다.
     * - 루트에 결합 후 정규화한 절대경로가 루트 하위인지 확인한다(문자열 검사가 아닌 경로 비교).
     * - 대상이 실제 존재하면 실경로(toRealPath)까지 재확인해 심볼릭 링크를 통한 루트 이탈을 막는다.
     *
     * @param root 신뢰된 루트 디렉터리다.
     * @param userPath 사용자가 전달한 루트 기준 상대경로다.
     * @return 검증을 통과한 절대경로다.
     */
    private Path resolveWithinRoot(Path root, String userPath) {
        String raw = (userPath == null) ? "" : userPath.trim().replace('\\', '/');
        if (raw.isEmpty()) {
            return root;
        }

        Path candidate = Path.of(raw);
        if (candidate.isAbsolute()) {
            throw new IllegalArgumentException("absolute path not allowed: " + userPath);
        }

        Path joined = root.resolve(candidate).normalize();
        if (!joined.equals(root) && !joined.startsWith(root)) {
            throw new IllegalArgumentException("path escapes download root: " + userPath);
        }

        if (Files.exists(joined)) {
            try {
                Path real = joined.toRealPath();
                Path realRoot = root.toRealPath();
                if (!real.equals(realRoot) && !real.startsWith(realRoot)) {
                    throw new IllegalArgumentException("symlink escapes download root: " + userPath);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("cannot resolve real path: " + userPath, e);
            }
        }

        return joined;
    }

    /**
     * 주어진 경로의 하위 폴더/파일 목록을 조회한다.
     *
     * @param path 루트 기준 상대경로다(빈 값이면 루트).
     * @return 정규화된 현재 경로와 디렉터리 우선·이름순 정렬된 목록이다.
     */
    public DownloadListDto list(String path) {
        Path root = resolveRoot();
        Path dir = resolveWithinRoot(root, path);
        if (!Files.exists(dir)) {
            throw new IllegalArgumentException("path not found: " + path);
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("not a directory: " + path);
        }

        List<DownloadEntryDto> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                entries.add(new DownloadEntryDto(
                        p.getFileName().toString(),
                        attrs.isDirectory(),
                        attrs.isDirectory() ? null : attrs.size(),
                        LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault())
                ));
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to list directory: " + path, e);
        }

        entries.sort(Comparator.comparing(DownloadEntryDto::isDirectory).reversed()
                .thenComparing(DownloadEntryDto::getName, String.CASE_INSENSITIVE_ORDER));

        String normalizedPath = root.relativize(dir).toString().replace('\\', '/');
        return new DownloadListDto(normalizedPath, entries);
    }

    /**
     * 다운로드 대상 파일을 확인하고 경로를 반환한다.
     *
     * @param path 루트 기준 상대경로다.
     * @return 존재가 확인된 파일 경로다.
     * @throws NoSuchFileException 대상이 존재하지 않는 경우다.
     */
    public Path resolveFileForDownload(String path) throws NoSuchFileException {
        Path root = resolveRoot();
        Path file = resolveWithinRoot(root, path);
        if (!Files.exists(file)) {
            throw new NoSuchFileException(path);
        }
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("cannot download a directory: " + path);
        }
        return file;
    }
}
