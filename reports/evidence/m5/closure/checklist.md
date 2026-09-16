# M5 종결 판정 — 완료 조건 대조 · OPEN 47 처분 · 운영자 결정 (2026-09-16)

**판정: 조건부 종결.** 구현 slice 열(5A·5B·5C-1·5C-2·5D·5D-golden·5D-2·5D-3·5E-1·5E-2)은 전부 `main` 에 병합됐고
완료 조건 열 항목 모두에 담당 근거가 있다. 그러나 **서빙이 실제로 서려면 운영자 결정 셋**(§3 ①②③)이 필요하고,
정책 값 표 둘의 승인 문면이 비어 있다(§3 ④⑤). 다섯이 처분되면 M5 는 무조건 종결이고, 처분 전까지는
「구현 완료·운영 미가용」 상태다. 5E-3(결정 불요 후속 둘)은 병행 진행 중이며 이 문서의 「5E-3 조건부」 행은 그 병합 뒤 확정한다.

> **갱신 2026-09-16(같은 날, 위 문단은 작성 시점 기록으로 둔다)** — ① 5E-3 이 PR #21(`845e29b`)로 병합돼 §2.3 셋은 **종결 확정**. ② 운영자가 §3 다섯을 전부 결정했다(「승인」·선택지 답변: ① (a) · ② (a) · ③ (a) · ④⑤ 「둘 다 승인」). ④⑤ 는 문서 한 줄이라 **이 slice 가 실행**했다(`5c2/policy-values.md`·`5e/policy-values.md` 표제 — 계약 갱신 (2)). ①③ 은 정책 slice **5F-1**(`inference-v1.yaml` 두 값 + 5D/5D-2 승인 값 갱신 + 5E-2 test), ② 는 Kotlin slice **5F-2**(`MlCallPolicyData.kt` 한 줄 + 4D policy-values §3)로 착수한다. **판정은 「종결(결정 완료)」** — 잔여는 실행 slice 둘의 병합뿐이고, 그 둘이 닫히면 §2.4 는 비고 M5 는 무조건 종결이다. **계수(갱신 시점)**: 종결 30(기존 16 + 이 문서 9 + 5E-3 3 + ④⑤ 2) · 결정 완료·실행 대기 3(①②③) · 이월 14 = 47.
>
> **최종 2026-09-16 — M5 무조건 종결.** 5F-1(PR #24)·5F-2(PR #25) 병합으로 ①②③ 셋이 닫혀 **종결 33 · 결정 대기 0 · 이월 14 = 47**. 완료 조건 열 항목 전부 근거 있음(범위 좁혀진 셋의 원인 「GBM 미서빙」은 M5 범위 밖 결정으로 그대로). 출하 정책 그대로 gate READY 이고 Kotlin 송신값이 Python 지원 집합과 같다 — **운영 가용 조건이 M5 안에서 닫혔다.** 잔여는 §2.5 이월 14 와 후속 소폭 다섯뿐이며 전부 받는 자리가 있다. §3 의 서두 문단과 「다섯이 전부 처분되면」 문단은 **작성 시점 기록**으로 그대로 둔다(결정 전 문면).

입력: `open-inventory.md`(읽기 전용 재고, 문면 기준 47건). 판정 규칙: 상태는 문서 문면으로만 잡고, 이 문서가 새로
닫는 항목은 **닫는 근거를 코드·evidence 경로로** 적는다. 앞 slice 문서는 편집하지 않는다.

## §1 완료 조건 열 항목 ↔ 근거

| # | 완료 조건 | 판정 | 근거(정본 위치) | 조건에 걸린 OPEN |
| --- | --- | --- | --- | --- |
| 1 | training/serving 동일 feature transform·schema | **충족(범위 좁혀짐)** | 변환 진입점 `AwardRateFeatureSpace.build_row` 하나, 5C-1 학습 행렬은 그 결과만(`5c/scope.md` ⑤), import-linter layers 가 features 공유를 강제(`5b/scope.md` 위협 (g)). **좁혀진 범위**: 서빙은 GBM 이 아니라 분포 엔진 단독(5D-2 D-5D2-1 (b))이라 5B 피처 벡터를 쓰는 서빙 경로가 아직 없다 — `feature_schema_version` 은 wire 해석 규약 축으로만 검증(5E-2 D-5E2-8) | `OPEN-5E2-FEATURE-SCHEMA-PARITY`(§3 ②) |
| 2 | clean env 에서 동일 manifest/seed → 재현 가능한 artifact/metric | **충족(호스트·스레드 한정)** | artifact: 같은 입력 두 번 → `ArtifactBytes.bytes` 동일(`5c/checklist.md` D-5C-12) · metric: report canonical bytes 동일(`5c2/checklist.md` 「재현 가능한 metric」). 한정: 같은 호스트·같은 `num_threads`(D-5C-12 (a), 5C-1 알려진 제한 3) | `OPEN-5C-REPRO-SCOPE`(계약 고정 — 종결) · `OPEN-5C-CORPUS`(합성 코퍼스로만 확인 — 이월) |
| 3 | serving 패키지에 DB driver·ORM 없음 | **충족** | serving extras 설치본에서 금지 패키지 다섯 각각 import 실패 실측(`5a/scope.md` S-1b) + 실행 시점 게이트 `tests/gates/test_serving_purity.py`(5E-1 이 서브프로세스 격리로 collection 의존 gap 을 닫음) | 없음 |
| 4 | 금지 import mutation 이 CI 에서 실패 | **충족** | layers+forbidden 계약 + 양성 fixture `bad_features_db/` 가 `lint-imports` 를 붉힘을 test 가 단언, 계약 블록 존재를 실제 `pyproject.toml` 에서 읽어 확인(`5c/checklist.md` D-5C-13) | 없음 |
| 5 | artifact checksum 불일치 시 readiness/inference fail-closed | **충족(문면 대체)** | inference: `load_artifact` 가 sha256·schema·feature_names·manifest checksum·`sample_scope` 중 하나라도 어긋나면 객체를 만들지 않음(변조 11건 — 정본은 `5d/checklist.md` 의 `load_artifact` 행과 `5d/scope.md` ⑦) · dataset: `load_dataset` 거부 사유 다섯(`5c/scope.md` ①). **대체**: 서빙 경로에 artifact 가 없어(분포 엔진 단독) readiness 를 좌우하는 checksum 은 **정책 checksum**이다(`5e/scope.md` ② · `5e2` DERIVED release 의 `artifact_checksum` = 정책 값 canonical JSON sha256). artifact checksum 이 readiness 를 막는 경로는 GBM 서빙 slice 가 생길 때 다시 잰다 | `OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT`(GBM 서빙 묶음으로 이월) |
| 6 | M2 Kotlin consumer ↔ provider contract 통과 | **충족(운영 경로는 결정 대기)** | S-12 2D fake 서버 스모크 무편집 통과 + S-12b 실 servicer socket 자동화(`tests/app/test_server_prediction.py`) + Kotlin 소비자 규칙 다섯의 Python 미러(`tests/serving/test_kotlin_rules_parity.py`). **그러나 현 Kotlin 송신값(`bidvector.ml.v1`)으로는 실 서빙이 전량 `UNSUPPORTED_SCHEMA`**(`5e2/checklist.md` 알려진 제한 3) | `OPEN-5E2-FEATURE-SCHEMA-PARITY`(§3 ②) · `OPEN-5E2-CANDIDATE-RATE-UPPER`(§3 ①) · `OPEN-5E2-CROSSLANG-REAL-SERVER`(6C) |
| 7 | 오류·최소 표본이 0점/성공으로 변환되지 않음 | **충족** | 결측·미지는 NaN+provenance 또는 행 거부(5B) · 표본 미달 `TrainingRejected`(5C-1) · 커널 `Unmeasurable(reason, detail)`(5D) · 평가 `NotEvaluable`(5C-2 3값) · 매핑 불변식 위반은 `MappingRejected` 를 예외(`RuntimeError`)로 올려 gRPC 오류 상태(미처리 예외 — servicer 주석은 INTERNAL/UNKNOWN)로 실패시키고 `Unmeasurable` 로 위장하지 않음(5E-2 D-5E2-6) | `OPEN-5C-REJECT-ACCOUNTING`(사유별 분해는 `RejectedRowAccounting` 에 있고 artifact 에 직렬화되지만 `TrainingRejected` 와 evaluation report 에는 없음 — 후속 소폭) |
| 8 | 승인된 metric threshold 충족 또는 `not-promotable` 명시 | **「명시」 충족 · 「충족」 미측정** | 임계는 `evaluation-v1.yaml` 에만(낱개 인자·CLI·env 없음, `test_public_signatures.py`) · 판정 3값 `Passed|Failed|NotEvaluable`, `Promotable` 은 `Passed` 에서만 파생(`derive_promotion`). **실 코퍼스가 없어 「충족」 자체는 측정된 적이 없다** — 이것은 M5 범위 밖(운영 artifact 승격은 「범위 밖」 절)이고 재학습 slice 의 몫 | `OPEN-5C-CORPUS`(curator) · `OPEN-5C2-POLICY-VALUES`(§3 ④) · `OPEN-5C-OOF-TIME-DIRECTION`(실 코퍼스 뒤 재판단 — 이월) |
| 9 | 이식 모듈마다 출처(파일·commit)·수정·튜닝 내역 기록 | **충족** | `tools/reuse_provenance_check.py` 가 모듈 docstring `Reuse: <경로>@<commit>` ↔ 각 slice `reuse.md` 를 양방향 대조(S-7, CI `ml-engine` job 안) — `5a`·`5b`·`5c`·`5c2`·`5d`·`5d2`·`5d3`·`5e`·`5e2` 전부 `reuse.md` 보유 | 없음 |
| 10 | 이식 코드가 신규 코드와 동일한 lint/typecheck/import boundary/래칫 통과 | **충족** | 모든 slice acceptance 가 CI `ml-engine` job 명령 그대로(S-2 ruff · S-3 mypy strict · S-4 lint-imports · S-6 design ratchet), baseline 완화 채택 없음(D-5A-3). mypy override 는 `bidvector.*`(5A 자신의 생성 stub 예외) 하나뿐이고 이식 모듈 예외 0(`tests/gates/test_mypy_allowlist.py`) | `OPEN-5A-MYPY-ALLOWLIST`(§2 에서 0건 종결) · 래칫 사각(클래스 본문 annotation 미검사, `5c/checklist.md` verifier r3 L-2 — 등재만, 5A 소유 후속 소폭) |

「근거 없음」 0. 문면 그대로 충족 6(3·4·7·9·10 + 2 의 한정은 계약 D-5C-12 가 정의한 범위) · 범위 좁혀진 충족 3(1·5·6) · 부분 미측정 1(8).
한정 하나: **#9·#10 은 완료 조건 문면을 인용한 담당 등재가 어느 slice 에도 없다** — 근거는 5A 계약의 §3.2·ADR 0009 D-6(#9)·§5 품질 도구(#10) 인용으로 걸려 있고, 이 표가 처음으로 완료 조건 문면과 잇는다.
좁혀진 셋의 공통 원인은 하나다 — **M5 는 GBM 을 서빙하지 않는다**(5D-2 결정, 분포 엔진 단독). GBM 서빙 slice 가 생기면 1·5·6 의
좁혀진 부분과 §2 「GBM 서빙 묶음」 OPEN 넷을 그 slice 가 함께 받는다.

## §2 OPEN 47 배타 처분

합 47 = 종결 선언 기존 16 + **이 문서로 종결 9** + **5E-3 조건부 종결 3** + 운영자 결정 대기 5 + 이월 14.

### 2.1 종결 선언이 이미 있는 것 — 16 (변경 없음, 위치는 `open-inventory.md` 표 1)

`OPEN-5A-PY-CI` · `OPEN-5A-SERVING-GRPC-EXCEPTION` · `OPEN-5A-WHEEL-BUILD-HOOK` · `OPEN-5B-FEATURES-FORBIDDEN` · `OPEN-5B-OBSERVATION-DOMAIN` ·
`OPEN-5C-ARTIFACT-ROUNDTRIP` · `OPEN-5C-BUDGET-BAND-SOURCE`(값 승인은 §3 ④ 에 종속) · `OPEN-5C-MATURITY-SOURCE` · `OPEN-5C-POLICY-VALUES` ·
`OPEN-5C-SEGMENT-PUBLISHED-FLOOR` · `OPEN-5D-DIAGNOSTICS-WIRE` · `OPEN-5D-DISTRIBUTION-ENGINE` · `OPEN-5D-POLICY-VALUES` ·
`OPEN-5D2-INTERVAL-SOURCE-WIRE` · `OPEN-5D2-RELEASE-FOR-DISTRIBUTION` · `OPEN-5D2-SAMPLE-SEGMENT`.

### 2.2 이 문서로 종결 — 9 (닫는 근거를 여기 적는다; 앞 slice 문서는 편집하지 않는다)

| 식별자 | 종결 근거 |
| --- | --- |
| `OPEN-5D-GOLDEN` | 14/14 authoritative 승인(2026-09-12, `5d-golden/checklist.md` 「사용자 승인」) + 5D-2 가 011 skip 해제(`5d2/checklist.md` 재검증 「golden 14/14 skip 0」). 「닫힘」 한 줄만 비어 있었다 — **닫힘** |
| `OPEN-5D2-BID-RATE-UPPER` | 2F 가 D-2F-4 로 D-2B-8 을 요청·응답 후보율 축에 한정하고 `CompetitionSample.observed_bid_rate > 1` 을 허용(`docs/discovery/capability-map.md` OPEN 표 「닫힘 M2/2F」, `milestone-2.md` 2F 절). 5D-2·5C-2 문서의 「정본 결정은 2F/5E 몫」은 그 결정 이전 문면이다 — **닫힘(2F)**. 후속 축은 별 건 `OPEN-5E2-CANDIDATE-RATE-UPPER` |
| `OPEN-5C-CALIBRATION-SCOPE` | D-5C-1(A 계보만, B 계보 범위 밖) — 사용자 승인 2026-09-13(`milestone-5.md` 5C-1 종결 문단 「D-5C-0·1·2·4·7·9 추천안 확정」). 잔여 물음은 `OPEN-ML-02`(M0 정본) — **닫힘** |
| `OPEN-5C-HYPERPARAM-LOCATION` | D-5C-2(코드 선언 + canonical checksum), 같은 승인 — **닫힘** |
| `OPEN-5C-MIN-TRAINING-ROWS` | D-5C-7 `min_training_rows 500`, `5c/policy-values.md` §1 「지위: 승인」 — **닫힘** |
| `OPEN-5C-REPRO-SCOPE` | D-5C-12 (a) 계약 고정, 호스트 간 미보장은 알려진 제한 3 으로 등재 — **닫힘**(§1 #2 의 한정이 이것) |
| `OPEN-5C-ROW-REJECTION-POLICY` | D-5C-5 계약 고정(사유별 회계 + 승인 행으로 학습 계속) — **닫힘**. 사유별 **분해**의 부재는 별 건 `OPEN-5C-REJECT-ACCOUNTING` |
| `OPEN-5C-5A-TABLE-REASSIGN` | 이 slice 가 `5a/policy-values.md` #8·#31·#32·#33 「소비 예정」을 5C-1 §3 실측대로 정정(Kotlin guardrail / 5D 만 / 미이식 / 미이식) + change_history 1행. `5c/checklist.md` 인계 「팀장이 정정」의 이행 — **닫힘** |
| `OPEN-5A-MYPY-ALLOWLIST` | 이식 slice 전부(5B~5E-2)가 mypy strict override 를 더하지 않았다. 유일한 override 는 `bidvector.*`(5A 자신의 생성 stub 예외, `ml-engine/pyproject.toml` 「reason/resolve」 주석) 이고 `tests/gates/test_mypy_allowlist.py` 가 사유 주석을 강제한다 — **0건으로 닫힘**. 소유 slice(5D)가 갱신 없이 지나간 것은 값이 0 이라 갱신할 것이 없었기 때문이다 |

### 2.3 5E-3 조건부 종결 — 3 (`m5-5e3/2026-09-16` 병합 시 확정, 병합 뒤 이 절 아래 한 줄 추가)

**확정 2026-09-16 — PR #21(`845e29b`) 병합, 셋 전부 종결.** verifier r3 ready-for-review(MEDIUM-1 닫힘 실측) + code-reviewer r1·r2 머지 가능 + 사용자 「승인」. 잔여 MEDIUM-2(예외 모듈 안 yaml 이중 별칭 import)는 5E-3 알려진 제한 8 + 후속 소폭(죽은 `except` 절 제거) — 이 표의 이월 §2.5 「후속 소폭」에 더한다.

| 식별자 | 5E-3 이 닫는 방식 |
| --- | --- |
| `OPEN-5E-YAML-LOADER-INFERENCE` | 네 로더가 공유하는 `registry/policy.py::load_policy` 에서 `yaml.YAMLError → PolicyError`(뿌리 처방) + 로더 기계 수집 게이트 test + 5E-1 호출부 래퍼 제거 |
| `OPEN-5C-YAML-ERROR-5D` | 같은 자리(위 뿌리 처방이 `inference/policy.py` 를 포함한 네 로더 전부를 덮는다) |
| `OPEN-5E-CANCEL-GRANULARITY` | `run_holdout(should_stop=)` 창 단위 취소, 결과 타입 `HoldoutCancelled` → pipeline `PipelineCancelled` |

### 2.4 운영자 결정 대기 — 5 (종결 차단, §3)

`OPEN-5E2-CANDIDATE-RATE-UPPER` · `OPEN-5E2-FEATURE-SCHEMA-PARITY` · `OPEN-5D2-POLICY-VALUES` · `OPEN-5C2-POLICY-VALUES` · `OPEN-5E-POLICY-VALUES`.

**갱신 2026-09-16 — 다섯 전부 결정됨(§3 결정 열).** `OPEN-5C2-POLICY-VALUES`·`OPEN-5E-POLICY-VALUES` 는 이 slice 의 표제 갱신으로 **종결**. `OPEN-5E2-CANDIDATE-RATE-UPPER`·`OPEN-5D2-POLICY-VALUES` 는 정책 slice 5F-1, `OPEN-5E2-FEATURE-SCHEMA-PARITY` 는 Kotlin slice 5F-2 병합 시 종결 — 그때까지 「결정 완료·실행 대기」.

**확정 2026-09-16 — 5F-1 PR #24(`fa0c81b`)·5F-2 PR #25(`91f6acb`) 병합(사용자 「PR #24·#25 병합 승인」), 셋 전부 종결.** 이 절은 비었다. 5F-1: verifier r1 ready(변이 셋·접힘 probe) + reviewer 머지 가능, 재작업 0. 5F-2: verifier r1 변이 B(요청 proto 무보호) → D-5F2-4 test → r2 ready(변이 다섯, test·detekt·컴파일 세 층) + reviewer 머지 가능, 재작업 0.

### 2.5 이월 — 14 (받는 자리를 명시; M5 종결을 막지 않는다)

| 받는 자리 | 식별자 | 이월 사유 |
| --- | --- | --- |
| **GBM 서빙 slice 묶음**(M6 이후, 존재 여부는 운영자 결정) | `OPEN-5C2-SERVING-PATH-PARITY` · `OPEN-5C2-UNLEARNED-GUARD` · `OPEN-5D-REAL-BOOSTER` · `OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT` | 넷 다 **GBM 예측이 서빙 경로에 실릴 때만 대상이 생긴다**(5E-2 「대상 부재」). 지금 만들면 소비자 없는 test·정합이 된다. 5D predict 경로의 실 booster 결정성(`OPEN-5D-REAL-BOOSTER`)도 같은 이유 — 5C-1 은 학습 재현성만 잰다. checksum 두 정의의 통합/분리는 그 slice 착수 계약에서 결정 |
| **M6 6B**(job 운영) | `OPEN-5E-JOB-PERSISTENCE` · `OPEN-5E-JOB-QUEUE-BOUND` | 전자는 5E-1 계약(`5e/scope.md` OPEN 표)이 6B 로 명시. 후자의 원문은 `5e/checklist.md` 알려진 제한 10 「5E-2 또는 6B」이고 5E-2 가 out_of_scope 로 다루지 않았으므로 **6B 는 이 문서의 결정**이다(큐 정책 설계가 먼저 필요 — job 운영 slice 의 몫) |
| **M6 6C**(컨테이너·실 통합) | `OPEN-5E2-CROSSLANG-REAL-SERVER` · `OPEN-5D3-SENDER-PRECONDITION` · `OPEN-5E-EMBEDDING-MODEL` | 실 Kotlin↔실 Python 통합이 6C 몫이고, 송신자 전제(표본 축 oneof 설정)는 그 통합 test 가 실측한다(현 유일 송신자 Kotlin `RequestMapping` 은 항상 설정 — 5D-3 알려진 제한 5). 임베딩 모델 실물은 M5 에 slice 가 없고 이미지 작업이 6C 라 **6C 추천**(5F 신설 대안은 운영자 몫) |
| **fixture-curator**(별도 레인) | `OPEN-5B-FIXTURE-REEVAL` · `OPEN-5C-CORPUS` | curator 소관으로 각 slice 가 명시. 실 코퍼스는 §1 #8 「충족」 측정의 전제 |
| **재학습 slice**(실 코퍼스 뒤) | `OPEN-5B-POLICY-VALUES`(κ 둘 — 값은 승인, 튜닝 근거 대기) · `OPEN-5C-OOF-TIME-DIRECTION`(5C-2 수치는 합성 코퍼스라 재판단 근거가 아님) | 둘 다 「실 코퍼스 홀드아웃 지표」가 입력 |
| **후속 소폭**(별도 slice 아님, 다음 5C-1 파일 편집 기회에) | `OPEN-5C-REJECT-ACCOUNTING` | `TrainingRejected` 에 사유별 분해 추가 — 결정 불요, 5C-1 파일 편집 |
| **후속 소폭**(식별자 없음 — 5E-3 알려진 제한 8, 계수 밖) | 5E-3 verifier r3 MEDIUM-2 | training·evaluation·serving 로더 셋의 죽은 `except yaml.YAMLError` 절 제거 → import-linter 예외가 뿌리 한 줄로 수렴하고 AST 검사 대상·이중 별칭 import 도달 경로가 함께 소멸. 다음에 그 파일을 만지는 slice 에서 |
| **후속 소폭**(식별자 없음 — 5F-1 알려진 제한, 계수 밖) | 5F-1 verifier r1 MEDIUM-3 | 5D 로더 불변식 `scenario.clamp_max ≤ 1`(계약 후보율 축) — 지금은 값 고정 test + 5E-2 런타임 fail-closed 두 층뿐이라 구성상 닫히지 않음. 로더 한 줄 + test, 5D 로더 파일을 다음에 만지는 slice 에서 |
| **후속 소폭**(식별자 없음, 계수 밖) | 5F-1 reviewer LOW · verifier 참고 · 5F-2 verifier 참고 | `tests/inference/golden/_adapter.py` 낡은 docstring·placeholder `update`(5D-2 소유) · Python 미러 `_is_acceptable_success_shape` 가 Kotlin `hasValidDiagnosticsShape` 를 옮기지 않음(5E-2 소유) · feature schema 값 선언 셋(Kotlin 정책·Python 집합·2F testdata `award-rate-v1` — 예시값)을 한자리에 모으는 문서 없음(6C 실 통합 test 와 함께) |

## §3 운영자 결정 다섯 — 선택지와 추천

셋은 서빙 가용성을 막고(①②③), 둘은 승인 문면만 비어 있다(④⑤). 순서: ③ 이 풀려야 ① 의 조건이 도달 가능하다.

| # | OPEN | 무엇이 걸려 있나 | 선택지 | 추천 · 실행 단위 |
| --- | --- | --- | --- | --- |
| ① | `OPEN-5E2-CANDIDATE-RATE-UPPER` | 출하 `inference-v1.yaml` `scenario.clamp_max: 1.4` vs 계약 후보율 ≤ 1(D-2B-8·D-2F-4). 임계가 채워진 뒤 관측 표본이 1 을 넘으면 적법한 입력이 gRPC 오류 상태(`MappingRejected` 예외, INTERNAL/UNKNOWN)를 받는다(값을 자르지 않는 fail-closed) | (a) 정책 `clamp_max ≤ 1.0` · (b) 계약 상한 확장(2F 재개정 + Kotlin `ParsedSuccessFields`) · (c) 새 `Unmeasurable` 사유 | **(a)** — 정책 값 한 줄, 5D `policy-values.md` 승인 값 갱신. 정책 slice(문서 + YAML + test 대조) |
| ② | `OPEN-5E2-FEATURE-SCHEMA-PARITY` | Kotlin `ML_CALL_POLICY.featureSchemaVersion = "bidvector.ml.v1"` vs Python `SUPPORTED_FEATURE_SCHEMAS = {"award-rate-features-v2"}`. 그대로 배포하면 실 서빙 전량 `UNSUPPORTED_SCHEMA` | (a) Kotlin 값을 `award-rate-features-v2` 로(4D `policy-values.md` §3 + `MlCallPolicyData.kt` 한 줄) · (b) Python 이 패키지 식별자를 schema 로 받기(5B D-5B-1 위반) | **(a)** — Kotlin 레인 한 줄 slice(4D 승인 값 갱신) |
| ③ | `OPEN-5D2-POLICY-VALUES` | `assessment.agency_sample_threshold` 출하 값 부재 → gate 영구 NOT_READY(5E-2 test 가 고정). 5D-2 후보 (a) 40 · (b) 10 · (c) 5C 재학습 뒤 | (a) 잠정값 지정(5D-3 golden 011 case 값 계열, 「잠정·재학습 뒤 재승인」을 정책 파일 주석과 `policy-values.md` 에 명기) · (b) 현 결정(c) 유지 — 실 코퍼스 뒤까지 NOT_READY | **(a)** — 5E-2 까지 끝난 지금 READY 를 막는 유일한 값이고, 실 코퍼스 도착 시점이 정해져 있지 않다. 정책 slice(① 과 같은 커밋 가능) |
| ④ | `OPEN-5C2-POLICY-VALUES` | `evaluation-v1.yaml` 임계 11 — 전부 legacy 상수(`ed4b06c`) 무변경 이식, 지어낸 수치 0. 표제가 「승인 대기」인 채 병합됨 | 승인 / 값 변경 요구 | **승인** — 값 근거는 legacy-behavior, 재조정은 재학습 slice. 실행: `5c2/policy-values.md` 표제 「승인」 갱신 한 줄(문서 커밋) |
| ⑤ | `OPEN-5E-POLICY-VALUES` | `serving-v1.yaml` 값 일곱 — legacy 대응물 0, 보수적 초기값 + 측정 의무(ADR 0010 D-1), M6 6C/6E 실측 뒤 갱신 | 승인(잠정) / 값 변경 요구 | **잠정 승인** — 실행: `5e/policy-values.md` 표제 갱신 한 줄. 6C/6E 실측이 재승인 조건 |

다섯이 전부 처분되면: §2.4 가 비고 M5 는 **무조건 종결**(잔여는 이월 14 뿐). 「M5 는 서빙 골격까지, 운영 가용은 M6」로 읽고
결정을 M6 착수 계약에 넘기는 것도 가능하다 — 그 경우 이 문서의 판정은 「조건부 종결」로 남고 M6 착수 계약이 다섯을 승계한다.

**운영자 결정 2026-09-16(선택지 답변, 전부 추천안)**: ① (a) `clamp_max ≤ 1.0` — 확정 값 `1.0` · ② (a) Kotlin 값 → `award-rate-features-v2` · ③ 선택지 (a) 「잠정값 지정」 — **확정 값 `10`**(5D-2 후보 열의 (b) golden synthetic 011 값; 후보 열의 (a) 40 은 GBM 미학습 가드 #31 축이라 채택하지 않음 — 두 「(a)」는 다른 표의 기호다)(잠정·재학습 뒤 재승인 명기) · ④⑤ 「둘 다 승인」(5C-2 승인, 5E 잠정 승인 — 6C/6E 실측이 재승인 조건). 실행: ④⑤ 이 slice(표제 한 줄씩) · ①③ 정책 slice **5F-1**(같은 정책 파일·같은 커밋 가능, 5D·5D-2 policy-values 승인 값 갱신 + 5E-2 「출하 정책으로 NOT_READY」 test 를 READY 로 뒤집음) · ② Kotlin slice **5F-2**(`MlCallPolicyData.kt` 한 줄 + 4D policy-values §3, Kotlin `check`). 5F-1·5F-2 는 서로 독립(파일 겹침 0)이라 병렬.

## §4 사실 선언

- 5E-3 은 이 문서 작성 시점에 병행 진행 중(base 같은 `8799e05`). `milestone-5.md` 는 두 브랜치가 다른 절에 문단을 더한다(5E-3 은 5E 절 끝, 이 slice 는 「## 완료 조건」 아래). **갱신**: 5E-3 이 PR #21 로 먼저 병합돼 이 브랜치를 `845e29b` 위로 rebase 했다(충돌 0). rebase 로 커밋 해시가 바뀌었다 — 아래 S-10 행의 `a2dcf19`·`e3c61d4` 는 rebase 전 해시이고 같은 내용이 `7d587df`·`9bb51bf` 다. 최종 HEAD 의 S-10 은 PR 코멘트(조치)에 남긴다. **두 번째 rebase**: 5F-1·5F-2 병합(+ 다른 세션 PR #26) 뒤 `91f6acb` 위로(충돌 0, 해시 재변경 — `170e518` → `2acfd9a`).
- 앞 slice 문서 무편집 원칙의 예외 둘: `5a/policy-values.md` 4행(5C-1 인계 「팀장이 정정」의 이행)과 `5c2/policy-values.md`·`5e/policy-values.md` 표제(운영자 승인 등재 — 승인 문면의 정본 자리가 그 표제라 다른 자리에 적으면 두 문서가 다른 말을 한다). 둘 다 값·분류 무변경, 문면만.
- `_workspace/` 는 gitignore 대상이라 재고 원문은 `open-inventory.md` 로 evidence 안에 복사했다(사본임을 머리에 표시).
- 이 slice 는 코드·계약·정책 YAML 을 편집하지 않는다. S-10 은 evidence 편집 커밋의 leak 스캔 자기참조 확인용이다.
- S-10 실측: `./gradlew --no-daemon check` @ `a2dcf19` exit 0(저작 레인) · verifier r1 이 같은 HEAD 에서 재실측 exit 0, `:leakPatternGate --rerun` exit 0. 마지막 evidence 커밋은 자기 post-state 를 스스로 적지 못하므로 그 HEAD 의 S-10 은 PR 코멘트(조치)에 남긴다.
- verifier r1 ready-for-review(산출물층 0, 장부층 MEDIUM 1·LOW 5) → 승인 전 일괄 커밋 1 로 처리(6B 이월 사유를 「이 문서의 결정」으로 · slice 수 열 · #5 정본 포인터 · `MappingRejected` 의 gRPC 상태 표현 · #7 사유별 분해의 실제 공백 위치 · #9·#10 한정 · `open-inventory.md` 를 표 1 만 남기고 축약).

## §5 사용자 승인

(PR 병합 시 기입)
