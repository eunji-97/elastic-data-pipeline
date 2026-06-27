package com.example.pipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * PubChem FTP 서버 접속 설정.
 * application.yml의 pubchem.ftp 하위 프로퍼티를 바인딩한다.
 *
 * <p>두 가지 데이터 모드를 지원한다:
 * <ul>
 *   <li><b>Full</b> — CURRENT-Full/SDF/ 아래 전체 화합물 데이터</li>
 *   <li><b>Monthly</b> — Monthly/{YYYY-MM-DD}/SDF/ 아래 가장 최근 월간 업데이트</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "pubchem.ftp")
public class PubChemFtpProperties {

    /** FTP 서버 호스트명 (기본값: ftp.ncbi.nlm.nih.gov) */
    private String host = "ftp.ncbi.nlm.nih.gov";

    /** 전체 데이터 SDF 디렉토리 경로 */
    private String fullPath = "/pubchem/Compound/CURRENT-Full/SDF/";

    /** 월간 업데이트 기준 디렉토리 경로 (하위에 YYYY-MM-DD/SDF/ 구조) */
    private String monthlyBasePath = "/pubchem/Compound/Monthly/";

    /** 접속 프로토콜 (https 권장 — Java의 FTP URL 핸들러는 불안정) */
    private String protocol = "https";

    /** 연결 타임아웃 */
    private Duration connectTimeout = Duration.ofSeconds(30);

    /** 읽기 타임아웃 (대용량 파일 다운로드를 고려) */
    private Duration readTimeout = Duration.ofSeconds(300);

    // -- 접근자 --

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public String getMonthlyBasePath() {
        return monthlyBasePath;
    }

    public void setMonthlyBasePath(String monthlyBasePath) {
        this.monthlyBasePath = monthlyBasePath;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * 전체 데이터 SDF 디렉토리 URL.
     * 예: https://ftp.ncbi.nlm.nih.gov/pubchem/Compound/CURRENT-Full/SDF/
     */
    public String getFullDirectoryUrl() {
        return protocol + "://" + host + fullPath;
    }

    /**
     * 월간 업데이트 기준 URL.
     * 예: https://ftp.ncbi.nlm.nih.gov/pubchem/Compound/Monthly/
     */
    public String getMonthlyBaseUrl() {
        return protocol + "://" + host + monthlyBasePath;
    }
}
