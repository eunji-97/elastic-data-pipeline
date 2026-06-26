package com.example.sdf.infra;

import com.example.sdf.domain.SdfMetadata;
import com.example.sdf.domain.SdfRecord;
import com.example.sdf.domain.SdfRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.stream.Stream;

/**
 * SdfRepository 구현체.
 * PubChem FTP 등에서 .sdf.gz 파일을 다운로드하고, 압축을 풀고, SDF 포맷을 파싱한다.
 */
@Component
class SdfRepositoryImpl implements SdfRepository {

    private static final Logger log = LoggerFactory.getLogger(SdfRepositoryImpl.class);
    private static final Path DOWNLOAD_DIR = Path.of(System.getProperty("java.io.tmpdir"), "sdf-pipeline");

    /** <a href="..."> 링크에서 href 속성 값을 추출하기 위한 정규식 */
    private static final Pattern HREF_PATTERN =
            Pattern.compile("<a\\s+(?:[^>]*?\\s+)?href=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    @Override
    public List<String> discoverSdfUrls(String directoryUrl) {
        if (directoryUrl.startsWith("file://")) {
            return discoverLocalDir(directoryUrl);
        }
        return discoverRemoteDir(directoryUrl);
    }

    /**
     * 로컬 디렉토리에서 .sdf.gz 파일 목록을 조회한다.
     */
    private List<String> discoverLocalDir(String directoryUrl) {
        try {
            Path dir = Path.of(URI.create(directoryUrl));
            if (!Files.isDirectory(dir)) {
                throw new IOException("디렉토리가 아닙니다: " + dir);
            }
            try (var files = Files.list(dir)) {
                List<String> urls = files
                        .filter(Files::isRegularFile)
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .filter(name -> name.endsWith(".sdf.gz"))
                        .sorted()
                        .map(name -> dir.resolve(name).toUri().toString())
                        .toList();
                log.info("로컬 디렉토리에서 .sdf.gz 파일 {}개 발견: {}", urls.size(), dir);
                return urls;
            }
        } catch (IOException e) {
            throw new RuntimeException("로컬 디렉토리 목록 조회 실패: " + directoryUrl, e);
        }
    }

    /**
     * 원격 HTTP 디렉토리 리스팅 페이지에서 .sdf.gz 파일 URL 목록을 추출한다.
     * HTML 페이지를 읽어 {@code <a href="...">} 링크 중 .sdf.gz로 끝나는 파일만 수집한다.
     */
    private List<String> discoverRemoteDir(String directoryUrl) {
        try {
            // 디렉토리 리스팅 페이지를 문자열로 읽기 (PubChem 기준 수 KB로 소량)
            String baseUrl = directoryUrl.endsWith("/") ? directoryUrl : directoryUrl + "/";
            byte[] bytes;
            URI uri = URI.create(directoryUrl);
            try (InputStream in = uri.toURL().openStream()) {
                bytes = in.readAllBytes();
            }
            String html = new String(bytes, StandardCharsets.UTF_8);

            // <a href="..."> 링크 파싱 → .sdf.gz 파일만 필터링 → 절대 URL로 변환
            List<String> urls = new ArrayList<>();
            Matcher matcher = HREF_PATTERN.matcher(html);
            while (matcher.find()) {
                String href = matcher.group(1);
                String fileName = href.contains("/")
                        ? href.substring(href.lastIndexOf('/') + 1)
                        : href;
                if (fileName.endsWith(".sdf.gz")) {
                    String absoluteUrl = URI.create(baseUrl).resolve(href).toString();
                    urls.add(absoluteUrl);
                }
            }

            // 결정적 처리 순서를 위해 정렬
            urls.sort(Comparator.naturalOrder());
            log.info("원격 디렉토리에서 .sdf.gz 파일 {}개 발견: {}", urls.size(), directoryUrl);
            return urls;
        } catch (IOException e) {
            throw new RuntimeException("디렉토리 목록 조회 실패: " + directoryUrl, e);
        }
    }

    @Override
    public SdfMetadata download(String sourceUrl) {
        try {
            // file:// URL은 로컬 파일 직접 사용 (복사 X → 디스크 절약)
            if (sourceUrl.startsWith("file://")) {
                Path localPath = Path.of(URI.create(sourceUrl));
                long fileSize = Files.size(localPath);
                log.info("Local file: {} ({} bytes)", localPath, fileSize);
                return new SdfMetadata(sourceUrl, localPath, fileSize);
            }

            Files.createDirectories(DOWNLOAD_DIR);
            String fileName = extractFileName(sourceUrl);
            Path targetPath = DOWNLOAD_DIR.resolve(fileName);

            log.info("Downloading {} -> {}", sourceUrl, targetPath);

            URI uri = URI.create(sourceUrl);
            try (InputStream in = uri.toURL().openStream()) {
                long fileSize = Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Downloaded: {} bytes", fileSize);
                return new SdfMetadata(sourceUrl, targetPath, fileSize);
            }
        } catch (IOException e) {
            throw new RuntimeException("Download failed: " + sourceUrl, e);
        }
    }

    @Override
    public Stream<SdfMetadata> extract(SdfMetadata metadata) {
        // .gz 파일은 parse()에서 직접 스트리밍 처리하므로 extract는 통과만 시킨다.
        // (디스크 절약: 중간 .sdf 파일 생성하지 않음)
        log.info("Extract (pass-through): {}", metadata.archivePath());
        return Stream.of(metadata);
    }

    @Override
    public Stream<SdfRecord> parse(SdfMetadata metadata) {
        List<SdfRecord> records = new ArrayList<>();
        parseAndConsume(metadata, records::add);
        return records.stream();
    }

    @Override
    public void parseAndConsume(SdfMetadata metadata, java.util.function.Consumer<SdfRecord> consumer) {
        log.info("Parsing: {}", metadata.archivePath());
        Path path = metadata.archivePath();
        boolean isGz = path.toString().endsWith(".gz");
        int totalRecords = 0;
        int skippedRecords = 0;

        try (BufferedReader reader = isGz
                ? new BufferedReader(new java.io.InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(path))))
                : Files.newBufferedReader(path)) {

            List<String> block = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("$$$$")) {
                    totalRecords++;
                    if (!block.isEmpty()) {
                        try {
                            consumer.accept(parseRecordBlock(block));
                        } catch (Exception e) {
                            skippedRecords++;
                            log.warn("Skipping malformed record at record #{}: {}",
                                    totalRecords, e.getMessage());
                        }
                    }
                    block.clear();
                } else {
                    block.add(line);
                }
            }

            if (!block.isEmpty()) {
                totalRecords++;
                try {
                    consumer.accept(parseRecordBlock(block));
                } catch (Exception e) {
                    skippedRecords++;
                    log.warn("Skipping malformed final record: {}", e.getMessage());
                }
            }

            log.info("Parsed: {} records ({} skipped) from {}",
                    totalRecords - skippedRecords, skippedRecords, path.getFileName());
        } catch (IOException e) {
            throw new RuntimeException("SDF parse failed: " + path, e);
        }
    }

    /**
     * SDF 레코드 블록(라인 리스트)을 파싱하여 SdfRecord를 생성한다.
     *
     * SDF 포맷 구조:
     * - 라인 0: compoundId (분자명)
     * - 라인 1-3: MOL 헤더 (I/O 프로그램명, 코멘트, 카운트 라인)
     * - 이후: atom/bond 블록 (counts line에 명시된 수만큼)
     * - 그 뒤: "> <PropertyName>" + 값 라인 형태의 프로퍼티
     */
    private SdfRecord parseRecordBlock(List<String> block) {
        if (block.isEmpty()) {
            throw new IllegalArgumentException("Empty record block");
        }

        String compoundId = block.get(0).trim();
        List<SdfRecord.Property> properties = new ArrayList<>();

        // MOL 헤더 3줄 이후, atom/bond 블록을 건너뛰고 프로퍼티 영역으로 진입
        int idx = 1;

        // header line 1: program/date — skip
        if (idx < block.size()) idx++;
        // header line 2: comment — skip
        if (idx < block.size()) idx++;
        // header line 3: counts line (aaabbblllfffccc...)
        if (idx < block.size()) idx++;

        // atom/bond 블록 건너뛰기
        // counts line의 앞 3자리 = atom 수, 다음 3자리 = bond 수
        // 단순화: "> <" 프로퍼티 마커가 나올 때까지 skip
        while (idx < block.size() && !block.get(idx).trim().startsWith("> <")) {
            idx++;
        }

        // 프로퍼티 파싱
        while (idx < block.size()) {
            String currentLine = block.get(idx).trim();

            if (currentLine.startsWith("> <")) {
                // 프로퍼티 이름: "> <PropertyName>" 형식에서 추출
                String name = currentLine.substring(3);
                if (name.endsWith(">")) {
                    name = name.substring(0, name.length() - 1);
                }
                idx++;

                // 프로퍼티 값: 다음 "> <" 또는 블록 끝까지
                StringBuilder valueBuilder = new StringBuilder();
                while (idx < block.size() && !block.get(idx).trim().startsWith("> <")) {
                    if (!valueBuilder.isEmpty()) {
                        valueBuilder.append("\n");
                    }
                    valueBuilder.append(block.get(idx));
                    idx++;
                }

                String value = valueBuilder.toString().trim();
                if (!name.isEmpty()) {
                    properties.add(new SdfRecord.Property(name, value));
                }
            } else {
                idx++;
            }
        }

        return new SdfRecord(compoundId, properties);
    }

    /**
     * URL에서 파일명을 추출한다. 쿼리 파라미터는 제외.
     */
    private String extractFileName(String url) {
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        int queryIndex = fileName.indexOf('?');
        if (queryIndex > 0) {
            fileName = fileName.substring(0, queryIndex);
        }
        return fileName;
    }
}
