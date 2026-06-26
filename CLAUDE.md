# elastic-data-pipeline

> **ES Blue-Green 적재 전략 검증 프로젝트**
> 핵심 질문: _"월 10만 건 배터리 연구 데이터를 ES에 안전하게 업데이트하는 최적의 방법은 무엇인가"_

📖 **Source of Truth**: [Elasticsearch blue-green](https://app.notion.com/p/386bb2b54f548025b8f0f69735c1ed6b)
참고: [적재 설계](https://app.notion.com/p/383bb2b54f5480278b80cdced5f4be1e) · [업데이트 전략 비교](https://app.notion.com/p/384bb2b54f5481f9a3b7c20712b0e401) · [인덱스 전략](https://app.notion.com/p/383bb2b54f5480e3a3ecc0231914c236) · [🧪 ES 정합성 갭 문제 기반 실험 설계](https://app.notion.com/p/386bb2b54f54816aa1e9f104de5a8cca)

---

## 🩸 배경

배터리 연구 데이터를 **월 1회 약 10만 건**씩 Elasticsearch에 업데이트하는 정기 작업을 운영 중이다.

### 기존 방식의 문제

```
데이터 → ES 단일 인덱스 직접 업데이트
```

- 작업 도중 오류 발생 → **일부 데이터만 반영**되는 정합성 문제
- 실패 시 전체 데이터를 **처음부터 재적재**해야 하는 비효율
- ES는 트랜잭션이 없어 **부분 반영된 상태로는 롤백 불가능**

### 해결 방향

**Blue-Green 배포 패턴**을 ES 인덱스에 적용:

```
1. 기존 인덱스 유지 (Green — 현재 서비스 중)
2. 신규 인덱스 별도 생성 (Blue — 새로 적재)
3. Reindex API로 기존 데이터를 신규 인덱스에 복제
4. 신규 인덱스에 업데이트 수행
5. 검증 완료 → alias를 신규 인덱스로 원자적 전환
6. 오류 발생 → 기존 인덱스 그대로 유지 (롤백 0초)
```

---

## 🧪 실험 설계

> 출처: [Elasticsearch blue-green (Notion)](https://app.notion.com/p/386bb2b54f548025b8f0f69735c1ed6b)

### 비교 대상

|  | 실험 1: Direct ES (기존) | 실험 2: Blue-Green (RDB 경유) |
| --- | --- | --- |
| **경로** | 데이터 → ES 직접 | 데이터 → RDB → ES (Blue-Green) |
| **인덱스 전략** | 단일 인덱스 직접 업데이트 | Reindex API + alias swap |
| **정합성 검증** | 별도 검증 없음 | **샘플링 확인**으로 정합성 보강 |
| **실패 대응** | 전체 재적재 | 기존 인덱스 유지, 신규 인덱스만 삭제 |

### 확인 포인트

1. **속도** — Blue-Green의 Reindex + 업데이트 + swap 총 소요 시간 vs 직접 업데이트
2. **정합성** — RDB를 경유할 때 샘플링 검증으로 정합성 보장 수준
3. **중간 오류 시 데이터 안정성** — 의도적 장애 주입 후 복구 방식 비교

---

## 🧩 모듈 구성

```
src/
├── main/java/com/example/
│   ├── sdf/                  # SDF 화합물 데이터 파이프라인
│   │   ├── domain/           # SdfRecord, SdfMetadata, SdfRepository
│   │   └── infra/            # SdfRepositoryImpl (download/extract/parse)
│   │
│   ├── pipeline/             # 파이프라인 오케스트레이션
│   │   ├── PipelineResult.java
│   │   ├── RunPipelineService.java
│   │   └── PipelineController.java
│   │
│   ├── storage/              # RDB 저장 (EAV 모델)
│   │   ├── domain/           # StoredData, BulkLoadService, StoredDataRepository
│   │   └── infra/            # JPA Entity, Spring Data 구현체
│   │
│   ├── common/               # ES 공통 유틸
│   │   ├── ElasticsearchClientProvider.java
│   │   └── EsIndexManager.java  # 인덱스 생성/삭제/alias swap
│   │
│   ├── experiment1/          # 실험 1: Direct ES (기존 플로우)
│   │   ├── domain/           # EsCompoundDocument
│   │   ├── application/      # DirectEsLoadService
│   │   ├── infrastructure/   # DirectElasticsearchConfig
│   │   └── presentation/     # Experiment1Controller
│   │
│   └── experiment2/          # 실험 2: Blue-Green (RDB 경유 + 정합성 검증)
│       ├── domain/           # IndexAlias, VerificationResult
│       ├── application/      # BlueGreenService, SamplingVerifier
│       ├── infrastructure/   # BlueGreenElasticsearchConfig
│       └── presentation/     # Experiment2Controller
│
├── main/resources/
│   ├── application.yml
│   ├── application-experiment1.yml
│   └── application-experiment2.yml
│
└── test/java/com/example/
    ├── experiment1/
    │   ├── DirectEsLoadServiceTest.java
    │   └── Experiment1IntegrationTest.java
    └── experiment2/
        ├── BlueGreenServiceTest.java
        ├── SamplingVerifierTest.java
        └── Experiment2IntegrationTest.java
```

---

## 🔬 실험 1 — Direct ES (기존 플로우 · 베이스라인)

```
데이터 → ES 직접 적재 (단일 인덱스 업데이트)
```

### 아키텍처
- SDF 데이터를 ES에 직접 `_bulk` 인덱싱
- 기존 인덱스에 `_update` 또는 `index` (덮어쓰기)
- 별도 정합성 검증 없음

### 측정 항목
| 항목 | 측정 방식 |
| --- | --- |
| **전체 적재 시간** | 10만 건 기준 |
| **중간 오류 시 상태** | Consumer kill → 인덱스 상태 확인 |
| **데이터 일치율** | RDB vs ES 무작위 100건 샘플링 비교 |

### 실패 시나리오
- 적재 중 오류 → 부분 반영된 상태, 전체 재적재 필요
- 롤백 불가능 (ES 트랜잭션 없음)

---

## 🔬 실험 2 — Blue-Green (RDB 경유 + 정합성 검증)

```
데이터 → RDB 저장 → 신규 ES 인덱스에 Reindex → 샘플링 검증 → alias swap
                                                       ├─ 성공 → swap
                                                       └─ 실패 → 기존 인덱스 유지
```

### Blue-Green 동작 방식

```
        ┌─────────────────────────────────┐
        │          ES Cluster             │
        │                                 │
        │  products_v1 ← products (alias) │  ← 현재 서비스 중 (Green)
        │  products_v2 (신규 생성)         │  ← 업데이트 진행 중 (Blue)
        │                                 │
        └─────────────────────────────────┘

1. products_v2 생성 (mapping 동일)
2. Reindex API: products_v1 → products_v2 (기존 데이터 복제)
3. products_v2에 신규 데이터 _bulk 업데이트
4. RDB vs products_v2 샘플링 검증 (100건)
5. 검증 통과 → alias 'products'를 products_v2로 원자적 전환
   검증 실패 → products_v2 삭제, products_v1 그대로 유지
```

### 정합성 보강 — 샘플링 검증

```
RDB (StoredData)               ES (products_v2)
     │                               │
     └── 무작위 100건 sampling ──→  필드 단위 비교
                                     │
                               일치율 100% → swap 승인
                               일치율 < 100% → swap 거부, 차이 보고
```

### 측정 항목
| 항목 | 측정 방식 |
| --- | --- |
| **Reindex 소요 시간** | products_v1 → products_v2 (데이터 크기별) |
| **_bulk 업데이트 소요 시간** | 신규 10만 건 적재 |
| **샘플링 검증 소요 시간** | 100건 무작위 추출 + 필드 비교 |
| **전체 Blue-Green 사이클** | 생성 → Reindex → 업데이트 → 검증 → swap |
| **중간 오류 시 안정성** | 각 단계에서 kill → 기존 인덱스 영향 여부 |
| **데이터 일치율** | swap 후 RDB vs ES 전체 비교 |

### 장애 시나리오별 동작

| 장애 발생 시점 | 결과 |
| --- | --- |
| Reindex 중 오류 | products_v2 삭제, products_v1 untouched |
| _bulk 업데이트 중 오류 | products_v2 삭제, products_v1 untouched |
| 샘플링 검증 실패 | swap 거부, 차이 보고 |
| swap 직전 오류 | products_v1 그대로 서비스 |

---

## 📊 평가지표

| 지표 | 실험 1 (Direct) | 실험 2 (Blue-Green) | 목표 |
| --- | --- | --- | --- |
| **전체 적재 시간** | 측정 | 측정 | Blue-Green이 Direct의 2배 이내 |
| **데이터 일치율** | 측정 | 측정 (샘플링 + 전체) | 99.9% 이상 |
| **오류 시 롤백 시간** | 측정 (전체 재적재) | 0초 (alias 유지) | 1분 이하 |
| **오류 시 데이터 유실** | 있음 (부분 반영) | 없음 (기존 인덱스 보존) | 0건 |
| **서비스 중단 시간** | 측정 | alias swap 순간만 (< 1초) | 1초 이하 |

---

## 🎯 실험 후 결정 매트릭스

| 실험 결과 | 판단 | 결정 |
| --- | --- | --- |
| Blue-Green 속도 ≤ Direct ×2, 정합성 우세 | 안정성 + 정합성 우위 | **Blue-Green 채택** |
| Blue-Green 속도 >> Direct ×2 | 배치 윈도우 부족 | Direct 유지 + 정합성 보강 방안 검토 |
| Blue-Green 정합성 ≈ Direct | RDB 경유 이점 없음 | Direct 유지, 샘플링만 추가 |

---

## 🔧 기술 스택

| 구분 | 기술 | 비고 |
| --- | --- | --- |
| **Language** | Java 21 | Gradle toolchain |
| **Framework** | Spring Boot 3.4.1 | Web, Data JPA, Validation |
| **Build** | Gradle (Groovy DSL) | |
| **RDB** | H2 (기본), PostgreSQL (프로파일) | docker-compose로 PG 제공 |
| **ES Client** | Elasticsearch Java Client 8.16 | Reindex API, Alias API, Bulk API |
| **Test** | JUnit 5 + Spring Boot Test | |

---

## 🚀 프로젝트 실행

```bash
# PostgreSQL 실행 (docker-compose)
docker compose up -d

# 실험 1: Direct ES
./gradlew bootRun --args='--spring.profiles.active=experiment1'

# 실험 2: Blue-Green
./gradlew bootRun --args='--spring.profiles.active=experiment2'

# 전체 테스트
./gradlew test
```

---

## ⚙️ 코딩 컨벤션

- 모든 코드와 주석은 **한국어**
- 실험 1 / 실험 2 모듈은 **서로 의존하지 않음**
- 공통 유틸리티는 `com.example.common` 패키지에 위치
- DDD 계층 구조: `domain → application → infrastructure → presentation`
