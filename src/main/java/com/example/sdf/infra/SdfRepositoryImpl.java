package com.example.sdf.infra;

import com.example.sdf.domain.SdfMetadata;
import com.example.sdf.domain.SdfRecord;
import com.example.sdf.domain.SdfRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

@Component
class SdfRepositoryImpl implements SdfRepository {

    private static final Logger log = LoggerFactory.getLogger(SdfRepositoryImpl.class);
    private static final Path DOWNLOAD_DIR = Path.of(System.getProperty("java.io.tmpdir"), "sdf-pipeline");

    @Override
    public SdfMetadata download(String sourceUrl) {
        try {
            Files.createDirectories(DOWNLOAD_DIR);

            String fileName = extractFileName(sourceUrl);
            Path targetPath = DOWNLOAD_DIR.resolve(fileName);

            log.info("Downloading {} -> {}", sourceUrl, targetPath);

            URI uri = URI.create(sourceUrl);
            try (InputStream in = uri.toURL().openStream()) {
                long size = Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Downloaded: {} bytes", size);
                return new SdfMetadata(sourceUrl, targetPath, size);
            }
        } catch (IOException e) {
            throw new RuntimeException("Download failed: " + sourceUrl, e);
        }
    }

    @Override
    public Stream<SdfMetadata> extract(SdfMetadata archive) {
        // TODO: 실제 압축 해제 구현 (.zip, .gz, .tar.gz)
        log.info("Extracting: {}", archive.archivePath());
        return Stream.empty();
    }

    @Override
    public Stream<SdfRecord> parse(SdfMetadata sdfFile) {
        // TODO: 실제 SDF 파싱 구현
        log.info("Parsing: {}", sdfFile.archivePath());
        return Stream.empty();
    }

    private String extractFileName(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1);
        // 쿼리 파라미터 제거
        int queryIdx = name.indexOf('?');
        return queryIdx > 0 ? name.substring(0, queryIdx) : name;
    }
}
