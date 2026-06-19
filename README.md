# elastic-data-pipeline

## 핵심 질문

두 가지 ES 적재 전략을 **정합성 / 속도 / 안정성** 측면에서 비교 평가한다.

---

## 1. ES 업데이트 전략 (CDC Incremental)

```
RDB 변경 → Debezium 감지 → Kafka 토픽 → Consumer가 ES에 부분 업데이트
```

| 차원 | 평가 |
| --- | --- |
| **정합성** | ⚠️ ES는 트랜잭션 없음 → CDC 순서가 꼬이면 stale 데이터 가능. Kafka `exactly-once`  • `_seq_no` 활용해 보완 필요 |
| **속도** | ✅ 변경분만 전송. 1건 변경에 수 ms~수십 ms. 거의 실시간 |
| **안정성** | ⚠️ CDC 커넥터 장애, Kafka lag, Consumer 죽음 등 실패 지점이 많음. 실패 시 **부분 반영**된 상태라 롤백 어려움 |

**실패 시나리오**

- Debezium 커넥터가 죽으면 변경분 유실 → 카프카 리텐션 내 복구 or 소스 재조회
- Consumer 장애 시 카프카 lag 증가 → 복구 후 따라잡는 시간 필요
- 중간에 데이터 꼬이면 부분만 반영되어 원복 까다로움

## 2. ES Blue-Green 전략 (Reindex Swap)

```
RDB → ES 직접 Bulk Indexing (전체 적재)
       ├─ 성공 → alias swap (v1 → v2)
       └─ 실패 → 기존 인덱스 유지, 새 인덱스 삭제
```

| 차원 | 평가 |
| --- | --- |
| **정합성** | ✅ RDB 스냅샷 기반 전체 복사 → 적재 중 데이터 변경 없음. 인덱스 통째로 교체하므로 **완전한 스냅샷 일관성** |
| **속도** | ⚠️ 전체 재색인 → 대용량일수록 오래 걸림. `_bulk`  • `refresh_interval=-1` 튜닝 필요 |
| **안정성** | ✅ 실패해도 기존 인덱스 untouched → **롤백 0초**. alias swap은 원자적 |

**alias swap 동작 방식**

1. `products_v1` (현재 운영 인덱스) ← `products` alias 연결
2. `products_v2` (새 인덱스) 생성 → 전체 Reindex
3. Reindex 완료 → `products` alias를 `products_v2`로 원자적 전환
4. 실패 시 → alias 그대로 `products_v1` 유지, `products_v2` 삭제

**왜 Kafka와 CDC가 빠졌는가**

- **CDC (Debezium)** 는 "어떤 row가 바뀌었는지" 변경분을 실시간 감지하는 도구다. Blue-Green은 "지금 시점의 전체 데이터"를 통째로 복사하기 때문에 변경 감지 자체가 무의미하다.
- **Kafka** 는 CDC가 발생시킨 변경 이벤트를 버퍼링하고 분배하는 스트리밍 큐다. 전체 스냅샷을 한 번 옮기는 배치 작업에서는 중간에 데이터를 잠깐 들고 있는 것 외엔 역할이 없다. Producer가 RDB를 읽어 Kafka에 넣는 순간 이미 RDB 부하가 발생하므로, RDB 부하 분산 효과도 없다. → 불필요한 홉(Hop)일 뿐이다.
- 즉, **Kafka는 1번안(CDC Incremental)에서 변경 이벤트 스트림을 처리할 때** 빛을 발한다. 2번안에서는 오버엔지니어링이다.
