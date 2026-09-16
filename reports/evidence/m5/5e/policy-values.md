# M5/5E-1 정책 값 — `OPEN-5E-POLICY-VALUES`(**승인 대기 2026-09-16, 착수 시**)

> **지위: 잠정 승인(2026-09-16, 운영자 「둘 다 승인」 — M5 종결 판정 `reports/evidence/m5/closure/checklist.md` §3 ⑤, `OPEN-5E-POLICY-VALUES` 종결). 재승인 조건 = M6 6C/6E 실측.** 실물은 `ml-engine/policy/serving-v1.yaml`(평탄 키, `PolicyScalar` 제약, 목록은 평탄 인덱스 키 — 5C-2 관례), 로더 `load_serving_policy` 가 `known_keys` 전수·값 불변식으로 대조하고 test 가 이 표와 YAML 을 대조한다. **legacy 근거 0** — legacy 런타임은 FastAPI+Celery 라 gRPC 동시성·종료 유예에 대응물이 없다. 전부 **보수적 초기값 + 측정 의무**(ADR 0010 D-1) 층이며, M6 6C/6E 실측 뒤 갱신한다.

## §1 정책 값(YAML)

| 키(평탄) | 값 | 근거 | 층 |
| --- | --- | --- | --- |
| `version` | `serving-v1` | — | — |
| `max_workers` | `8` | `grpc.server` ThreadPoolExecutor 크기. 판정 RPC 는 5D-2 분포 엔진(ms 단위)이라 CPU 바운드 짧음; job 은 별 풀 | 보수적 초기값 |
| `max_concurrent_rpcs` | `32` | `maximum_concurrent_rpcs` — 초과 시 gRPC `RESOURCE_EXHAUSTED`(4D-1 이 백오프 재시도 대상으로 둠). `max_workers` 보다 커도 된다(큐잉) | 보수적 초기값 |
| `shutdown_grace_seconds` | `10` | `server.stop(grace)` — 4D-1 `deadlineCeiling=5s` 의 두 배(진행 중 RPC 가 deadline 안에 끝나거나 취소되도록) | 4D-1 정책 값 파생 |
| `job_workers` | `1` | training job 스레드 풀 — 학습은 LightGBM `num_threads=4`(5C-1 spec) 가 이미 코어를 쓰므로 동시 job 1 | 5C-1 spec 파생 |
| `idempotency_key_max_chars` | `256` | 2C 문면 「test 파일이 임의 상한(256자)으로 규칙 존재를 문서화」 — 그 값 그대로 | 2C test 값 |
| `embedding_text_max_chars` | `contract-policy.properties` `embedding.text.max-chars` 와 **같은 값**(구현 레인이 그 파일에서 읽어 옮기고, 경계 쌍 testdata 로 동일성 증명 — D-2D-6) | 2E ④ 정책 데이터 | Kotlin 정책 값 복제(두 파일 — 값 동일성은 test) |
| `dataset_uri_schemes.0` | `file` | D-5C-8·D-5E-5 — `file://` 만. object storage 는 6C(`OPEN-2C-DATASET-URI-SCHEME`) | 계약 고정 |

## §2 환경 값(env, 정책 파일 아님 — D-5E-7)

| env | 의미 | 기본값 |
| --- | --- | --- |
| `ML_ENGINE_BIND` | `host:port` | **없음**(미설정 = 부팅 거부) |
| `ML_ENGINE_INFERENCE_POLICY` / `ML_ENGINE_TRAINING_POLICY` / `ML_ENGINE_EVALUATION_POLICY` / `ML_ENGINE_SERVING_POLICY` | 정책 YAML 경로 넷 | 없음 |
| `ML_ENGINE_ARTIFACT_OUT_DIR` | job 산출물 루트(`file://`) | 없음 |
| `ML_ENGINE_CODE_VERSION` | 5C-1 `CodeVersion` 문자열(git 호출 없음 — 배포가 준다) | 없음 |

## §3 5A 환경 6 처분(5A `policy-values.md` #24~#26·#30 + 표 밖 둘)

| 5A # | 이름 | 처분 |
| --- | --- | --- |
| 24 | `PRICE_PREDICTION_PREFERRED_PREDICTOR` | **소멸** — 5D-2 운영자 결정 (b) 분포 단독, predictor 선택 축 없음 |
| 25 | `PRICE_PREDICTION_ENABLE_EXPERIMENTAL_PREDICTORS` | **소멸**(같은 이유) |
| 26 | `PRICE_PREDICTION_ENSEMBLE_MODEL_PATH` | **소멸** — ensemble 미이식(D-5C-1) |
| 30 | `PRICE_PREDICTION_AWARD_RATE_GBM_MODEL_PATH` | 5E-1 밖 — GBM 서빙 경로 없음(5D-2 (b)); artifact 는 job 산출물 디렉터리(§2)에 쓰이고 승격·로드는 후속 |
| (표 밖) | `ML_RELEASE_MANIFEST_DIR`·`ARCHIVE_DIR` | B 계보(승격 manifest) — 미이식 |

## §4 불변식(로더가 거부)
전부 유한값 · `max_workers ≥ 1` · `max_concurrent_rpcs ≥ 1` · `shutdown_grace_seconds ≥ 0` · `job_workers ≥ 1` · `idempotency_key_max_chars ≥ 1` · `embedding_text_max_chars ≥ 1` · `dataset_uri_schemes` 비어 있지 않음·중복 없음·소문자.

## change_history
| 일자 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-16 착수 | 표 등재(승인 대기) | D-5E-6·D-5E-7 |
| 2026-09-15 구현 | `ml-engine/policy/serving-v1.yaml` 실물 작성(표 값 그대로) — `embedding_text_max_chars=4000`이 Kotlin `config/quality/contract-policy.properties`의 `embedding.text.max-chars=4000`과 일치함을 `tests/serving/test_policy_kotlin_parity.py`로 실측(D-2D-6 경계 쌍). `load_serving_policy`가 §4 불변식 전부(≥1 넷·`dataset_uri_schemes` 비어 있지 않음·중복 없음·소문자)를 강제하는 것을 `tests/serving/test_serving_policy.py`로 확인 | D-5E-6 구현 완료, 값은 여전히 보수적 초기값(측정 의무 유지) |
