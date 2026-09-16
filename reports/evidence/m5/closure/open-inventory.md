<!-- 입력 재고: 읽기 전용 Explore 레인이 main @ 8799e05 에서 작성(2026-09-16). 판정은 checklist.md 가 정본이고 이 파일은 문면 조사 결과의 사본이다. -->

# M5 종결 재고 — OPEN 처분 표 · 완료 조건 ↔ 근거

기준: `main` @ `8799e05`(PR #20 병합, 5E-2). 읽기 전용 조사.

추출: `grep -rhoE "OPEN-5[A-Z0-9]*-[A-Z0-9-]+" milestone-5.md reports/evidence/m5 | sort -u`
→ **47 건**(5A 4 · 5B 4 · 5C 16 · 5C-2 3 · 5D 5 · 5D-2 5 · 5D-3 1 · 5E 6 · 5E-2 3).

## 판정 규칙

- 상태는 **문서 문면**(「종결」·「해소」·「닫힘」·「유지」·「승인 대기」·「이월」)으로만 판정했다.
  문면이 없으면 「명시 선언 없음」으로 적고 추측하지 않았다.
- 문서 사이에 상태가 어긋나면 양쪽을 적고 **충돌**로 표기했다.
- `docs/discovery/capability-map.md` OPEN 표는 자신을 「정본」으로 선언하므로 교차 마일스톤
  OPEN(5D·5D-2 의 wire 축 다섯)에서는 그 문면을 함께 실었다.
- 줄 번호는 적지 않는다(절 제목·식별자·결정 ID 로 가리킨다).

## 요약 계수 (문면 기준)

배타적 분류 — 합 47.

| 상태 | 건수 | 식별자(`OPEN-` 접두 생략) |
| --- | --- | --- |
| **종결·해소 선언 있음** | 16 | 5A-PY-CI · 5A-SERVING-GRPC-EXCEPTION · 5A-WHEEL-BUILD-HOOK · 5B-FEATURES-FORBIDDEN · 5B-OBSERVATION-DOMAIN · 5C-ARTIFACT-ROUNDTRIP · 5C-BUDGET-BAND-SOURCE · 5C-MATURITY-SOURCE · 5C-POLICY-VALUES · 5C-SEGMENT-PUBLISHED-FLOOR · 5D-DIAGNOSTICS-WIRE(wire 2F + Python 5E-2) · 5D-DISTRIBUTION-ENGINE · 5D-POLICY-VALUES · 5D2-INTERVAL-SOURCE-WIRE · 5D2-RELEASE-FOR-DISTRIBUTION · 5D2-SAMPLE-SEGMENT |
| **부분 종결 — 최종 선언 한 줄이 없음** | 1 | 5D-GOLDEN(13/14 → 011 은 5D-2 가 skip 해제, 「닫힘」 문면 없음) |
| **충돌 — 실질 종결이나 M5 문면이 낡음** | 1 | 5D2-BID-RATE-UPPER(capability-map·milestone-2 「닫힘 M2/2F」 vs 5D-2·5C-2 「정본 결정은 2F/5E 몫」) |
| **처분 포인터만**(「→ D-5C-x」, 종결 어휘 없음) | 5 | 5C-CALIBRATION-SCOPE · 5C-HYPERPARAM-LOCATION · 5C-MIN-TRAINING-ROWS · 5C-REPRO-SCOPE · 5C-ROW-REJECTION-POLICY |
| **미결 — 운영자 결정 대기** | 7 | 5B-POLICY-VALUES(값 승인, 튜닝 근거 대기) · 5C-OOF-TIME-DIRECTION · 5C2-POLICY-VALUES · 5D2-POLICY-VALUES · 5E-POLICY-VALUES · 5E2-CANDIDATE-RATE-UPPER · 5E2-FEATURE-SCHEMA-PARITY |
| **미결 — 후속 slice / 다른 마일스톤 이월** | 17 | 5A-MYPY-ALLOWLIST · 5B-FIXTURE-REEVAL · 5C-5A-TABLE-REASSIGN · 5C-ARTIFACT-CHECKSUM-PLACEMENT · 5C-CORPUS · 5C-REJECT-ACCOUNTING · 5C-YAML-ERROR-5D · 5C2-SERVING-PATH-PARITY · 5C2-UNLEARNED-GUARD · 5D-REAL-BOOSTER · 5D3-SENDER-PRECONDITION · 5E-CANCEL-GRANULARITY · 5E-EMBEDDING-MODEL · 5E-JOB-PERSISTENCE · 5E-JOB-QUEUE-BOUND · 5E-YAML-LOADER-INFERENCE · 5E2-CROSSLANG-REAL-SERVER |

---

# 표 1 — OPEN 처분 표 (47)

## 5A — 패키지·import boundary (4)

| 식별자 | 정의된 자리 | 한 줄 의미 | 현재 상태 | 소유자 |
| --- | --- | --- | --- | --- |
| `OPEN-5A-MYPY-ALLOWLIST` | `reports/evidence/m5/5a/scope.md` 「OPEN — 수령·신설」(신설 후보) | 이식 모듈의 mypy strict 예외 목록과 해소 slice | **미결·무변경.** `5a/checklist.md` OPEN 표: 「**초기 — 0건**, 이식 모듈 strict 예외는 없다 … **5D 가 K7 을 이식할 때 갱신**」. 그런데 **5D·5D-2·5D-3 evidence 어디에도 이 식별자 언급이 0 건**이다(5D checklist 는 「override 추가 0」만 적는다) — 소유 slice 가 지나갔는데 갱신·종결 선언이 없다 | 명목상 5D(이미 종결) → 실질 **미배정**. M6 이월 또는 「0건으로 종결」 선언이 필요 |
| `OPEN-5A-PY-CI` | `reports/evidence/m5/prep/m5-prep.md` OPEN 표 → `5a/scope.md` 신설 후보 | CI 러너에서 Python job 이 실제로 도는지(오프라인 wheel 캐시 포함) | **종결.** `reports/evidence/m5/5a/checklist.md` OPEN 표: 「**닫힘 2026-09-12** — PR #6 첫 러너 실행에서 `ml-engine` job pass(20s)·Kotlin `check` pass, run `34600268539`」 | — (종결) |
| `OPEN-5A-SERVING-GRPC-EXCEPTION` | `reports/evidence/m5/5a/checklist.md` 「알려진 제한」 1 + OPEN 표(신설) | `serving/grpc.py` 가 생기면 import-linter `ignore_imports` 한 줄을 넣어야 한다 | **종결.** `reports/evidence/m5/5e/scope.md` OPEN 표: 「pyproject (a) 로 **종결**」. `5e/checklist.md` 「계약과 어긋나 판단이 필요했던 자리」가 계약 배치를 정정해 두 진입점(`serving.grpc`·`training.jobs.servicer`)만 예외로 열었다고 기록 | — (종결) |
| `OPEN-5A-WHEEL-BUILD-HOOK` | `reports/evidence/m5/5a/checklist.md` 「알려진 제한」 5 + OPEN 표(verifier r2 N-1) | wheel 설치본에서 `ml_engine.contracts` 재수출이 성립하지 않는다 — 빌드 훅 필요 | **종결.** `reports/evidence/m5/5e/scope.md` ⑨·OPEN 표(「⑨ + S-11 로 **종결**」), `5e/commands.md` S-11 기록(저장소 밖 임시 venv 설치본에서 `ml_engine.contracts`·`bidvector.ml.v1.prediction_pb2_grpc`·servicer import 성립, exit 0), `.github/workflows/ci.yml` S-11 step 주석이 「종결 증거」로 명시. `milestone-5.md` 5E-1 병합 문단도 「종결」 | — (종결) |

## 5B — feature schema (4)

| 식별자 | 정의된 자리 | 한 줄 의미 | 현재 상태 | 소유자 |
| --- | --- | --- | --- | --- |
| `OPEN-5B-FEATURES-FORBIDDEN` | `reports/evidence/m5/5b/checklist.md` 「OPEN 갱신」(신설, verifier r1 L-3) + `5b/scope.md` 계약 갱신 이력 | import-linter DB/HTTP forbidden 이 `serving`·`inference` 만 겨눠 `features` 는 무방비 | **해소.** `reports/evidence/m5/5c/scope.md` ⑫·OPEN 표(「⑫ 로 **해소**(양성 fixture 동반)」), `5c/checklist.md` D-5C-13 행 + 「이번 구현이 만든 새 public 표면」(「이전에는 `features` 가 무방비였다, 해소」). `milestone-5.md` 5C-1 종결 문단도 「해소」 | — (해소) |
| `OPEN-5B-FIXTURE-REEVAL` | `reports/evidence/m5/5b/scope.md` OPEN 표(신설) | fixtures `ml-boundary-003/004`(`not_covered` 「M2 피처 계약 없음」)를 2B 계약 뒤 재평가 | **미결·무변경.** `5b/checklist.md` 「OPEN 갱신」: 「변경 없음(curator 소관, 5B 는 재평가 가능하다는 근거만 코드로 만들었다)」. 이후 slice 문서에 언급 0, `fixtures/manifest.yaml` 에도 이 식별자 없음 | **fixture-curator**(별도 레인) |
| `OPEN-5B-OBSERVATION-DOMAIN` | `reports/evidence/m5/5b/checklist.md` 「OPEN 갱신」(신설, verifier r2 low) | `AwardRateObservation.value` 에 도메인 검증이 없어 `agency_encoding` 이 `[0,1]` 밖으로 나갈 수 있다 | **해소.** `reports/evidence/m5/5c/scope.md` ② 와 OPEN 표: 「② 로 **해소**(닫힌 구간 [0,1], 근거 5B 스키마)」, D-5C-5. `milestone-5.md` 5C-1 종결 문단 「라벨 [0,1] 회계(`OPEN-5B-OBSERVATION-DOMAIN` 해소)」 | — (해소) |
| `OPEN-5B-POLICY-VALUES` | `reports/evidence/m5/5b/policy-values.md`(표제) + `5b/scope.md` OPEN 표 | 2단 수축 κ 둘(`agency_prior_strength 12.0`·`category_prior_strength 40.0`)의 값 | **값은 승인, OPEN 은 유지.** `5b/policy-values.md` 「지위: 승인」(사용자 승인 2026-09-12, legacy-behavior) + 「해소 조건: 5C 재학습이 κ 튜닝 근거(홀드아웃 지표)를 내면 이 표를 갱신」. `5b/checklist.md`: 「해소 조건 불변」. 5C-1 `policy-values.md` 는 「κ 둘은 새 키가 아니라 5B `SHIPPED_ENCODING_POLICY` 재사용」만 적고 튜닝 근거를 내지 않았다 | **운영자**(5C 재학습 지표 뒤) — 실질 M6/재학습 이월 |

## 5C — training/evaluation (16)

| 식별자 | 정의된 자리 | 한 줄 의미 | 현재 상태 | 소유자 |
| --- | --- | --- | --- | --- |
| `OPEN-5C-CALIBRATION-SCOPE` | `reports/evidence/m5/5c/scope.md` 「이 slice 가 하는 일」 ⑦ (「→ D-5C-1」) | legacy calibration(Platt·group 통계) 을 어디까지 이식하는가 | **처분 포인터만 — 명시 종결문 없음.** ⑦ 가 `→ D-5C-1` 로 넘기고, D-5C-1 은 「A 계보만 이식, B 계보 범위 밖」. `milestone-5.md` 5C-1 종결 문단이 「D-5C-0·1·2·4·7·9 추천안 확정」(사용자 승인 2026-09-13)이라 **결정은 확정**됐으나 이 식별자 자체의 「종결」 문면은 없다. 남은 물음은 `OPEN-ML-02` 로 이월(D-5C-1) | 결정 확정(운영자) · 잔여 물음은 `OPEN-ML-02` |
| `OPEN-5C-HYPERPARAM-LOCATION` | `5c/scope.md` 계약 고정 결정 D-5C-2 행 | 하이퍼파라미터를 코드 선언(`TrainingSpec`)에 둘지 YAML 에 둘지 | **처분 포인터만.** D-5C-2 (코드 선언 + canonical checksum)로 처분, `milestone-5.md` 가 D-5C-2 확정을 등재. 「종결」 문면 없음 | 결정 확정(운영자) |
| `OPEN-5C-MIN-TRAINING-ROWS` | `5c/scope.md` ③ (「→ D-5C-7」) | 최소 학습 표본 하한 값과 그 게이트 | **처분 포인터만 + 값 승인.** D-5C-7 `min_training_rows 500`(legacy-declared 미소비), `5c/policy-values.md` §1 등재·「지위: 승인」. 별도 「종결」 문면 없음 | 결정 확정(운영자) |
| `OPEN-5C-REPRO-SCOPE` | `5c/scope.md` ⑩ (「→ D-5C-12 (a)」) | 재현성의 정의 범위(같은 호스트·같은 스레드 수인가) | **처분 포인터만.** D-5C-12: 「(a) 같은 호스트·같은 `num_threads` 에서 바이트 동일, (b) metric 허용오차 재현은 5C-2」, 「계약 고정」. `5c/checklist.md` 알려진 제한 3(호스트 간 미보장) | 계약 고정 · 잔여는 알려진 제한 |
| `OPEN-5C-ROW-REJECTION-POLICY` | `5c/scope.md` ② (「→ D-5C-5」) | 거부 행을 버리는가 회계하는가 | **처분 포인터만.** D-5C-5: 거부는 사유별 회계로 결과에 실리고 학습은 승인 행으로 계속, 「계약 고정」 | 계약 고정 |
| `OPEN-5C-POLICY-VALUES` | `reports/evidence/m5/5c/policy-values.md`(표제) + `5c/scope.md` OPEN 표 | `training-v1.yaml` 값(`min_training_rows`)과 `TrainingSpec` 이식 값 | **종결.** `milestone-5.md` 5C-1 종결 문단: 「사용자 승인 「승인 push, pr 진행」 — D-5C-0·1·2·4·7·9 추천안 확정, **`OPEN-5C-POLICY-VALUES` 종결**」. `5c/policy-values.md` 표제 「지위: 승인」 | — (종결) |
| `OPEN-5C-5A-TABLE-REASSIGN` | `5c/scope.md` OPEN 표 + `5c/policy-values.md` §3 | 5A 정책 표의 #8·#31·#32·#33 배정이 A 계보 학습 경로 실측과 다르다 | **미결 — 반영 안 됨.** `5c/checklist.md` 인계 절: 「5A 표 자체는 5D 병합 뒤 **팀장이 정정**」. 실측: `reports/evidence/m5/5a/policy-values.md` 는 `change_history` 에 2026-09-12(D-5D-9)·2026-09-13(#27) 두 행만 있고, **#8 은 여전히 「5C」, #31 은 「5C·5D」, #32·#33 은 「5C」** 로 남아 있다(5C-1 제안은 각각 Kotlin·5D 만·미이식·미이식). `5c2/scope.md` OPEN 표: 「승계·무변경」 | **팀장**(문서 레인) — 종결 전 5A 표 정정 필요 |
| `OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT` | `5c/scope.md` D-5C-9·OPEN 표 | 같은 이름 `artifact_checksum` 이 두 정의(최종 bytes sha256 vs 블랭크 canonical bytes sha256)로 존재한다 | **미결 — 명시 「여전히 열려 있다」.** `5c/checklist.md` 인계 절: 「**여전히 열려 있다.** D-5C-9b 로 왕복 실패는 해소됐으나 … 하나로 합칠지 이름을 가를지는 이 slice 가 정하지 않는다 — 정합은 2F·5E·5D 후속 중 어디서 할지 팀장 결정 대상」. 같은 문서 verifier r3 L-1: 「바이트 안 값은 **어떤 층도 재계산 대조하지 않는다**」. `5c2/scope.md` 「승계·무변경」. 2F·5E-2 문서의 OPEN 처분 표에 이 식별자 없음 | **팀장 결정**(2F / 5E / 5D 후속 중 선택) |
| `OPEN-5C-ARTIFACT-ROUNDTRIP` | `5c/scope.md` OPEN 표 | 5C-1 `write_artifact` → 5D `load_artifact` 왕복 test | **종결.** `5c/checklist.md` 「닫힌 OPEN(이 rebase, 2026-09-13)」: 「~~`OPEN-5C-ARTIFACT-ROUNDTRIP`~~ — 닫힘. … `test_artifact_roundtrip.py` 로 왕복(fake trainer 1·실 LightGBM 1·변조·release_id 불일치·추가 필드 여덟·manifest 필드 전달)을 실측」. `5c/commands.md` 도 「종결」 | — (종결) |
| `OPEN-5C-BUDGET-BAND-SOURCE` | `reports/evidence/m5/5c2/scope.md` ②·OPEN 표(5C-2 가 인수·명명) | 금액대 밴드 경계값(1e8/5e8/1e9/5e9)의 출처 | **종결.** `5c2/scope.md` OPEN 표: 「② 로 **종결**(정책 데이터 `amount_band_edges`)」. `5c2/checklist.md` OPEN 표: 「scope ② 로 종결」. `5c2/policy-values.md` §1 에 값 등재(legacy-behavior) | — (종결, 단 `OPEN-5C2-POLICY-VALUES` 승인에 종속) |
| `OPEN-5C-MATURITY-SOURCE` | `5c/scope.md` D-5C-6·OPEN 표 | 성숙도(K7) 를 누가 계산하고 5C-2 가 무엇을 입력으로 받는가 | **종결.** `5c2/scope.md` D-5C2-2·OPEN 표: 「D-5C2-2 로 **종결**(입력) — K7 `features` 이전은 2F/5E 후보로 등재」. `5c2/checklist.md` 알려진 제한 1: 비율 3줄 중복은 남는다(등재) | — (종결) · 잔여 중복은 2F/5E 후보 |
| `OPEN-5C-SEGMENT-PUBLISHED-FLOOR` | `5c2/scope.md` out_of_scope·OPEN 표(5C-2 신설·즉시 처분) | 세그먼트 축에 `published_floor` 를 둘지 | **종결.** `5c2/scope.md` D-5C2-9·OPEN 표: 「D-5C2-9 로 **종결**(축 없음)」. `5c2/checklist.md` 알려진 제한 8: 「D-5C2-9 로 종결(축 자체가 존재하지 않음, 기능 누락이 아니라 설계 결정)」 | — (종결) |
| `OPEN-5C-OOF-TIME-DIRECTION` | `5c/scope.md` D-5C-4·OPEN 표 | 학습 구간 내부 OOF 폴드에 시간 방향을 요구할지 | **미결 — 알려진 제한으로 유지.** `5c/checklist.md` 알려진 제한 2: 「legacy 도 요구하지 않았고 이 slice 도 요구하지 않는다 — **5C-2 홀드아웃 수치 확인 뒤 재판단 대상**」. `5c2/scope.md` OPEN 표: 「승계·무변경」 — 5C-2 가 수치를 냈지만 재판단 기록은 없다 | **운영자**(5C-2 지표 기반 재판단) |
| `OPEN-5C-CORPUS` | `5c/scope.md` OPEN 표 | 학습·평가용 실 dataset fixture(`manifest.json`+`rows.jsonl`, authored-from-approved-spec) | **미결 — 승계.** `reports/evidence/m5/5c/golden-manifest.json`: 「fixture 소비 없음(OPEN-5C-CORPUS) — 이 slice 의 test 는 코드가 만든 합성 코퍼스만 … curator 소관으로 이월」. `5c2/golden-manifest.json` 같은 문면, `5c2/checklist.md` OPEN 표: 「승계 — 5C-2 test 는 전부 합성 코퍼스, 실코퍼스 evaluation fixture 는 curator 몫」 | **fixture-curator** |
| `OPEN-5C-REJECT-ACCOUNTING` | `5c/scope.md` 계약 갱신 이력(verifier r5 L-8 신설) | 재게이트 거부(`TrainingRejected`)가 사유별 분해를 나르지 않는다 | **미결 — 유지.** `5c2/checklist.md`·`5c2/scope.md` OPEN 표(2026-09-16 verifier r1 M-4 정정): 「5C-2 가 공시하는 것은 창별 `dropped_rows` 뿐 … 코퍼스 admission 단계의 `rejected_rows` 는 이 report 에 여전히 없다 — 5C-1 파일 편집이라 범위 밖, **미해소 유지(5E 전)**」. `5e/scope.md` OPEN 표: 「`TrainingRejected` 의 detail 을 `detail_code` 로 나름 — 사유별 분해는 여전히 없음, **유지**」. `5e2/checklist.md`: 「5E-2 무변경」 | **후속 slice**(5C-1 파일 편집 필요) / M6 이월 |
| `OPEN-5C-YAML-ERROR-5D` | `5c/scope.md` 계약 갱신 이력 + `5c/checklist.md` 「PR #13 인계」 | 5D `inference/policy.py` 에 같은 `yaml.YAMLError` 누출 구멍이 있을 가능성 | **미결 — 로더 자체는 무방비.** `5c2/scope.md` OPEN 표: 「승계·무변경」. `5e/checklist.md` 「열린 항목」: 「부팅 경로는 fix round 2 로 닫혔다 … **`inference/policy.py` 자체는 여전히 무방비**(팀장 지시로 범위 밖). 남은 것은 로더 자체 정정뿐」 → 후속 식별자 `OPEN-5E-YAML-LOADER-INFERENCE` 로 이어진다. **이 식별자에 대한 종결 선언은 없다** | **후속**(`OPEN-5E-YAML-LOADER-INFERENCE` 와 같은 자리) |

## 5C-2 — evaluation (3)

| 식별자 | 정의된 자리 | 한 줄 의미 | 현재 상태 | 소유자 |
| --- | --- | --- | --- | --- |
| `OPEN-5C2-POLICY-VALUES` | `reports/evidence/m5/5c2/policy-values.md`(표제) + `5c2/scope.md` OPEN 표 | `evaluation-v1.yaml` 임계 11(paired_t 2.58·maturity 0.70·min_evaluation_rows 100·max_origins 5·agency min_count 10·stability_seeds 5·amount_band_edges 4·segment_axes 2) | **미결·상태 불명확(충돌).** `5c2/policy-values.md` 표제는 여전히 「**승인 대기** 2026-09-15, 착수 시」이고 `change_history` 마지막 행은 「2026-09-15 구현 — 값 §1 표와 완전 일치」뿐. `milestone-5.md` 5C-2 착수 문단도 「운영자 확인 대기: D-5C2-1·3·7·9」(D-5C2-3 = 임계 전부 정책 파일). 그런데 **5C-2 병합 문단(PR #15)의 「남는 OPEN」 목록에 이 식별자가 없다** — 승인됐다는 문면도, 남았다는 문면도 없다. 값 자체는 전부 legacy 코드 상수 무변경 이식(지어낸 수치 0)이라고 문서가 선언 | **운영자** — 종결 판정 전에 명시 승인 또는 「승인 대기 유지」 선언이 필요 |
| `OPEN-5C2-SERVING-PATH-PARITY` | `5c2/scope.md` D-5C2-7·OPEN 표(신설) | 홀드아웃 예측(`booster.predict` 직접)과 서빙 경로(`predict_bid_rates`)의 수치 동일성 미증명 | **미결 — 대상 부재로 이월.** `5c2/checklist.md` 알려진 제한 2: 「직렬화 왕복 경로와 수치가 같다는 것은 **증명되지 않았다** — 5E 가 통합 test 로 확인해야 한다」. `5e/scope.md` OPEN 표: 「5E-2(매핑 뒤 통합 test)」. `5e2/scope.md`·`5e2/checklist.md` OPEN 처분: 「서빙 경로가 분포 단독(GBM 미서빙)이라 **대상 부재 — 알려진 제한 등재, GBM 서빙 slice(있다면) 로 이월**」 | **후속 GBM 서빙 slice**(존재하지 않음) → M6 이월 판정 필요 |
| `OPEN-5C2-UNLEARNED-GUARD` | `5c2/scope.md` out_of_scope·OPEN 표(신설) | 평가 경로가 5D `segment_availability`(미학습 공종 가드)를 지나지 않는다 | **미결 — 대상 부재로 이월.** `5c2/checklist.md` 알려진 제한 3(모델 쪽 미학습 공시 없음). `5e/scope.md`: 「5E-2」. `5e2/checklist.md` OPEN 처분: 「대상 부재로 유지(알려진 제한 4) — GBM 서빙 slice 로 이월」 | **후속 GBM 서빙 slice** → M6 이월 판정 필요 |

## 5D — inference kernels (5)

| 식별자 | 정의된 자리 | 한 줄 의미 | 현재 상태 | 소유자 |
| --- | --- | --- | --- | --- |
| `OPEN-5D-POLICY-VALUES` | `reports/evidence/m5/5d/policy-values.md`(표제) + `5d/scope.md` D-5D-8·OPEN 표 | `inference-v1.yaml` 값 17(z 1.2816·가중치 셋·clamp 0.7/1.4·κ 12/40·밴드 넷·임계 셋·window_days 7) | **종결.** `5d/checklist.md` OPEN 갱신: 「착수 시 승인 값 그대로 출하(`inference-v1.yaml`) — **해소**」. `5d/policy-values.md` 「지위: 승인」(사용자 승인 2026-09-12). `fixtures/manifest.yaml` 은 golden corpus 가 이 값을 하나도 주장하지 않는다고 기록(소유가 이 OPEN 이었음) | — (종결) |
| `OPEN-5D-DIAGNOSTICS-WIRE` | `5d/scope.md` D-5D-7·OPEN 표(신설) | `Diagnostics.shrinkage_weight`·`excluded_observations` 를 wire 에 싣는 계약 | **양쪽 종결.** wire: `docs/discovery/capability-map.md` OPEN 표 「**wire 측 닫힘 M2/2F, 2026-09-15**」(`prediction.proto` `Diagnostics` 필드 3·4) + `milestone-2.md` 2F 절. Python 매핑: `reports/evidence/m5/5e2/checklist.md` OPEN 처분 「**종결** — `_map_diagnostics` 가 여섯 필드 전부 옮긴다」. 도메인 소비는 별 식별자(`OPEN-2F-DIAGNOSTICS-DOMAIN`, capability-map 이 4D-3 닫힘으로 기록) | — (종결) |
| `OPEN-5D-DISTRIBUTION-ENGINE` | `5d/scope.md` 계약 갱신 이력(범위 판정) + `5d/checklist.md` OPEN 갱신(신설) | K5·K6·K7 → 후보 3 을 잇는 분포 엔진 조립 자리가 5D in_scope 에 없다 | **해소.** `5d2/scope.md` OPEN 표: 「후자 **이 slice 종결로 해소**」. `5d2/checklist.md` 사용자 승인 절: 「`OPEN-5D-DISTRIBUTION-ENGINE` **해소**」. `milestone-5.md` 5D-2 종결 문단도 「해소」 | — (해소) |
| `OPEN-5D-GOLDEN` | `5d/scope.md` ⑨·OPEN 표(신설, D-M5-7 (a)) | 커널 golden corpus(`ml-kernel-*`) 신설과 5D test 의 소비 | **부분 종결 — 최종 선언 없음.** `5d/checklist.md` OPEN 갱신: 「**13/14 해소**(M-3 통합) — `ml-kernel-011` 은 `OPEN-5D-DISTRIBUTION-ENGINE` 으로 이관(**그 slice 가 해소해야 완전히 닫힌다**)」. `5d-golden/checklist.md` 「6. 사용자 승인」: 14 case authoritative 승인 2026-09-12. `5d2/scope.md` ⑥ 「golden 011 해소」 + `5d2/checklist.md` 재검증 「golden 14/14 skip 0」. **그러나 「`OPEN-5D-GOLDEN` 닫힘」이라는 문면은 어느 문서에도 없다** | 실질 해소(5D-2) — 종결 선언 한 줄이 비어 있다 |
| `OPEN-5D-REAL-BOOSTER` | `5d/scope.md` D-5D-4·OPEN 표(신설) | 실 LightGBM booster 의 결정성 test(스레드 수 무관 동일값)는 5C artifact 도착 뒤 | **미결 — 소유 slice 가 다루지 않았다.** `5d/checklist.md` OPEN 갱신: 「변경 없음(5C artifact 도착 후)」, 알려진 제한 4. `milestone-5.md` 5D 종결 문단 인계: 「`OPEN-5D-REAL-BOOSTER`(5C)」. 실측: **5C-1·5C-2 evidence 어디에도 이 식별자 언급 0 건**(5C-1 은 자기 재현성 test 로 실 LightGBM 을 쓰지만 5D predict 경로의 결정성은 다루지 않는다). `fixtures/manifest.yaml`·`5d-golden/golden-manifest.json` 은 이 축을 `not_covered` 로 기록 | 명목상 5C(둘 다 종결) → 실질 **미배정**. M6 이월 또는 종결 판정 필요 |

## 5D-2 — 분포 엔진 (5)

| 식별자 | 정의된 자리 | 한 줄 의미 | 현재 상태 | 소유자 |
| --- | --- | --- | --- | --- |
| `OPEN-5D2-POLICY-VALUES` | `reports/evidence/m5/5d2/policy-values.md`(표제) + `5d2/scope.md` D-5D2-3·OPEN 표 | `assessment.agency_sample_threshold` 의 값(legacy 대응 상수 없음) | **미결 — 값 미정, 운영자 (c).** `5d2/policy-values.md`: 「지위: 키 신설 승인, **값 미정** … 운영자 (c): 5C 재학습 지표가 나오면 값을 정한다」(후보 (a) 40 · (b) 10 · (c) 5C 뒤). `5d3/policy-values.md`: 「여전히 미정 … 이 slice 는 그 OPEN 을 닫거나 건드리지 않는다」. **5E-2 가 결과를 고정**: `5e2/checklist.md` 알려진 제한 2 — 「출하 `policy/inference-v1.yaml` 로는 서빙이 되지 않는다」(test 가 그 사실을 고정). `5c2/scope.md` OPEN 표: 「5C-2 report 가 낼 지표로 값 근거를 만들 수 있게 됨 — 결정은 운영자, 5E 전」 | **운영자** — **M5 종결의 실질 차단 항목**(값 없이는 서빙이 영구 NOT_READY) |
| `OPEN-5D2-RELEASE-FOR-DISTRIBUTION` | `5d2/scope.md` D-5D2-5·OPEN 표(신설) | 아티팩트 없는 엔진이 `Success.release`(`ModelRelease` 필수 다섯)를 어떻게 채우는가 | **양쪽 종결.** wire: `capability-map.md` 「**wire 측 닫힘 M2/2F, 2026-09-15**」(`release_kind` + `ReleaseKind{ARTIFACT,DERIVED}`, DERIVED 는 `dataset_id` 공백 허용). Python: `5e2/checklist.md` OPEN 처분 「**종결** — `runtime.py::build_derived_release` 가 정책+code_version 에서 독립적으로 wire `ModelRelease` 를 만든다(D-5E2-1)」 | — (종결) |
| `OPEN-5D2-INTERVAL-SOURCE-WIRE` | `5d2/scope.md` D-5D2-8·OPEN 표(신설) | 사후예측분산 기반 구간의 wire `IntervalSource` 값이 없다 | **양쪽 종결.** wire: `capability-map.md` 「**wire 측 닫힘 M2/2F**」(`INTERVAL_SOURCE_POSTERIOR_PREDICTIVE = 3` + Kotlin 도메인 값). Python: `5e2/checklist.md` 「**종결** — `POSTERIOR_PREDICTIVE → INTERVAL_SOURCE_POSTERIOR_PREDICTIVE` 직접 매핑」 | — (종결) |
| `OPEN-5D2-SAMPLE-SEGMENT` | `5d2/scope.md` 계약 갱신 이력(구현 중 발견 2026-09-15) + `5d2/checklist.md` 알려진 제한 1 | wire `CompetitionSample` 에 표본별 기관·공종 축이 없어 3계층 수축을 서빙 경로에서 만들 수 없다 | **닫힘.** wire: `capability-map.md` 「**wire 측 닫힘 M2/2F**」(`CompetitionSample.agency_id`·`category_code`). 소비: `reports/evidence/m5/5d3/checklist.md` 「5D-2 알려진 제한 해소 등재」 — 「알려진 제한 1 **해소** · 알려진 제한 8 **해소** · `OPEN-5D2-SAMPLE-SEGMENT` **닫힘**(5D-2 문서는 편집하지 않는다)」. `milestone-5.md` 5D-3 종결 문단 「닫힘」. `5d3/rollback.md` 는 되돌리면 재개방됨을 기록 | — (닫힘) |
| `OPEN-5D2-BID-RATE-UPPER` | `5d2/scope.md` 계약 갱신 이력(verifier r2 뒤 신설 `fde9b2c`)·OPEN 표 | D-2B-8(`Rate.fraction > 1` = 계약 위반, 축 한정 없음) vs 표본 투찰비 밴드 상한 1.5 | **충돌 표기.** ① `docs/discovery/capability-map.md` OPEN 표(자칭 정본): 「**닫힘 M2/2F, 2026-09-15** — D-2F-4 로 D-2B-8 을 요청·응답 후보율 축에 한정, `CompetitionSample.observed_bid_rate` 는 `> 1` 허용」 + `milestone-2.md` 2F 절 「wire 측 해소」. ② `5d2/scope.md`·`5d2/checklist.md` 알려진 제한 11: 「정본 결정(D-2B-8 을 축 한정으로 개정할지, 밴드 상한을 1.0 으로 낮출지)은 **2F/5E 몫**」(5D-2 문서는 이후 갱신되지 않았다). ③ `5c2/scope.md` OPEN 표: 「5C-2 코퍼스 실측을 report 에 실어 근거 제공 — 결정은 2F」. **판정**: 2F 가 (a) 축 한정으로 닫았고 M5 쪽 문서 셋이 낡았다 — 문면 정합만 남았다. 관련 신규 축은 `OPEN-5E2-CANDIDATE-RATE-UPPER`(별 건) | 실질 종결(2F) · **M5 evidence 문면 정정** 필요 |

## 5D-3 — 3계층 소비 (1)

| 식별자 | 정의된 자리 | 한 줄 의미 | 현재 상태 | 소유자 |
| --- | --- | --- | --- | --- |
| `OPEN-5D3-SENDER-PRECONDITION` | `reports/evidence/m5/5d3/checklist.md` 알려진 제한 5(신설, verifier r1 F-4) | 표본 축 oneof 를 설정하지 않는 송신자는 전 표본이 거부돼 base 에서 `Success` 이던 요청이 `Unmeasurable` 이 된다 | **미결 — 5E 전제로 이월.** `5d3/checklist.md`: 「D-5D3-2 의 명시 결정에 따른 정상 동작이지 결함이 아니다. 현행 유일 송신자(Kotlin `RequestMapping`)는 … 항상 설정하므로 지금 이 경로로 깨지는 요청은 없다 — **5E(서빙 활성화) 전제로 이월**」. `milestone-5.md` 5D-3 종결 문단에 「신규 OPEN」으로 등재. **5E-2 문서에 이 식별자 언급 0 건** | 명목상 5E(5E-2 종결) → **미배정**. 전제 확인 또는 M6 이월 판정 필요 |

## 5E — gRPC serving (6)

| 식별자 | 정의된 자리 | 한 줄 의미 | 현재 상태 | 소유자 |
| --- | --- | --- | --- | --- |
| `OPEN-5E-POLICY-VALUES` | `reports/evidence/m5/5e/policy-values.md`(표제) + `5e/scope.md` D-5E-6·OPEN 표 | `serving-v1.yaml` 값 일곱(max_workers 8·max_concurrent_rpcs 32·shutdown_grace 10·job_workers 1·idempotency 256·embedding_text_max_chars·dataset_uri_schemes) | **미결·상태 불명확(5C-2 와 같은 패턴).** `5e/policy-values.md` 표제: 「지위: **승인 대기** 2026-09-16, 착수 시 … legacy 근거 0 — 전부 **보수적 초기값 + 측정 의무**(ADR 0010 D-1), M6 6C/6E 실측 뒤 갱신」. `5e/checklist.md` D-5E-6 행: 「값 자체는 팀장·운영자 승인 대상」(승인 대기 유지). **`milestone-5.md` 5E-1 병합 문단의 「남는 OPEN」 목록에 이 식별자가 없다** | **운영자** 승인 + M6 6C/6E 실측 갱신 |
| `OPEN-5E-EMBEDDING-MODEL` | `5e/scope.md` D-5E-2·OPEN 표(신설) | 임베딩 모델 실물의 선택·이식(해시 fallback 은 이식하지 않는다) | **미결 — 이월.** `5e/checklist.md` 알려진 제한 2: 「임베딩 모델 부재 — `OPEN-5E-EMBEDDING-MODEL`(5F/6C)」. `5e/scope.md` OPEN 표: 「모델 선택·이식 slice(5F)/이미지(6C) 결정」. `milestone-5.md` 5E-1 병합 문단 「남는 OPEN」에 등재. `5e2/scope.md` out_of_scope | **후속 slice 5F 또는 M6 6C** — 운영자 결정 |
| `OPEN-5E-JOB-PERSISTENCE` | `5e/scope.md` out_of_scope·OPEN 표(신설) | job 영속화·재시작 복구(프로세스 재시작 시 job 소실) | **미결 — M6 6B 이월.** `5e/scope.md` OPEN 표: 「job 영속·재시작 복구 — 6B」. `5e/checklist.md` 알려진 제한 3(+6 다중 인스턴스 공유 없음). `milestone-5.md` 5E-1 병합 문단 「남는 OPEN」 등재 | **M6 6B** |
| `OPEN-5E-CANCEL-GRANULARITY` | `5e/scope.md` ⑦·OPEN 표(신설) | `run_holdout` 내부 창 루프가 취소를 확인하지 않는다 | **미결 — 후속.** `5e/scope.md` OPEN 표: 「5C-2 창 루프 안 취소 확인 — 5C-2 파일 편집이라 후속」. `5e/checklist.md` 알려진 제한 4. `milestone-5.md` 5E-1 병합 문단 「남는 OPEN」 등재 | **후속 slice**(5C-2 파일 편집) / M6 |
| `OPEN-5E-JOB-QUEUE-BOUND` | `reports/evidence/m5/5e/checklist.md` 「열린 항목(이 라운드가 새로 낸 것)」(신설, verifier r1 L-6) | `JobRunner` 의 ThreadPoolExecutor 제출 큐가 무한이다(`WORKER_RESOURCE_EXHAUSTED` 생산 경로 없음) | **미결.** `5e/checklist.md` 알려진 제한 10: 「상한을 두려면 큐 정책(거부·대기·백프레셔) 설계가 먼저 필요해 이번 라운드에서 만들지 않았다(`OPEN-5E-JOB-QUEUE-BOUND`, **5E-2 또는 6B**)」. `5e2/scope.md` out_of_scope 에 명시 → 5E-2 가 다루지 않았다. `milestone-5.md` 5E-1 병합 문단 「남는 OPEN」 등재 | **M6 6B**(5E-2 가 처분 안 함) |
| `OPEN-5E-YAML-LOADER-INFERENCE` | `5e/checklist.md` 「열린 항목」(신설, verifier r2 잔존) | `inference/policy.py` 로더 자체가 `yaml.YAMLError` 를 `PolicyRejected` 로 감싸지 않는다 | **미결 — 호출부만 정규화.** `5e/checklist.md`: 「**부팅 경로는 fix round 2 로 닫혔다** … `app/server.py::_load_inference_policy_safe` 가 정규화해 없앴다 — **`inference/policy.py` 자체는 여전히 무방비**. 남은 것은 로더 자체 정정뿐 — training/evaluation/serving 세 곳은 이미 같은 버그가 반복돼(PR #13 HIGH-2·evaluation·5E-1 fix round 1) 왔으므로 넷을 공유 로더로 통합할지 개별 수정할지는 다음 라운드 또는 5E-2 착수 계약에서 결정」. `5e2/scope.md`·`5e2/checklist.md`: 「**유지**(5D-3 뒤 `inference/policy.py` 후속, 이 slice out_of_scope)」. `OPEN-5C-YAML-ERROR-5D` 와 같은 자리 | **후속 slice** — 같은 결함 계열 네 번째, 공유 로더 통합 여부는 운영자 결정 |

## 5E-2 — wire 매핑 (3)

| 식별자 | 정의된 자리 | 한 줄 의미 | 현재 상태 | 소유자 |
| --- | --- | --- | --- | --- |
| `OPEN-5E2-FEATURE-SCHEMA-PARITY` | `reports/evidence/m5/5e2/scope.md` 착수 조사·OPEN 표(신설) | Kotlin `ML_CALL_POLICY.featureSchemaVersion = "bidvector.ml.v1"` vs Python `SUPPORTED_FEATURE_SCHEMAS = {"award-rate-features-v2"}` | **미결 — 운영자 결정 대기.** `5e2/scope.md` OPEN 표: 「운영자 결정: **(a) 추천** Kotlin 값을 `award-rate-features-v2` 로(4D `policy-values.md` §3 갱신 + `MlCallPolicyData.kt` 한 줄, Kotlin 레인/6C) · (b) Python 이 패키지 식별자를 schema 로 받기(5B D-5B-1 위반 — 비추천)」. `5e2/checklist.md` 알려진 제한 3: 「실 배포에서 Kotlin 이 그 값을 보내면 servicer 는 계약대로 `UNSUPPORTED_SCHEMA` 를 낸다 … **배포가 그대로면 실서빙이 전부 실패한다**」 | **운영자** — **M5 종결의 실질 차단 항목**(교차 언어 불일치) |
| `OPEN-5E2-CANDIDATE-RATE-UPPER` | `5e2/scope.md` OPEN 표(신설, verifier r1 H-1) + `5e2/checklist.md` OPEN 처분 | 엔진 clamp 상한(`scenario.clamp_max = 1.4`, 5D 승인 값)이 계약의 응답 후보율 축(`Candidate.bid_rate ≤ 1`, D-2B-8·D-2F-4)과 충돌 | **미결 — 운영자 결정 대기.** `5e2/checklist.md` 알려진 제한 7: 「임계가 채워지고 `clamp_max` 가 지금처럼 `1.4` 인 채로 배포되면 **엔진에 잘못이 없는 입력에서도** 후보가 계약 위반이 되고 gRPC `INTERNAL` 을 낸다」. 선택지: 「(a) 정책 `scenario.clamp_max` 를 1 이하로(5D-2/정책 값 소관, 가장 작음) · (b) 계약 상한 확장(2F 재개정 + Kotlin `ParsedSuccessFields`) · (c) 새 `Unmeasurable` 사유로 재분류」. `OPEN-5D2-POLICY-VALUES` 가 먼저 풀려야 조건 자체가 도달 가능 — **두 OPEN 이 순서로 엮여 있다** | **운영자** — 선택지 셋 제시됨, 추천 (a) |
| `OPEN-5E2-CROSSLANG-REAL-SERVER` | `5e2/scope.md` ⑦·OPEN 표(신설) | 실 Kotlin gateway ↔ 실 Python 서버 통합 test 부재(현재는 Kotlin 규칙의 Python 미러까지) | **미결 — 6C 이월.** `5e2/scope.md` OPEN 표: 「실 … 통합 test 는 6C(컨테이너) — 5E-2 는 Kotlin 규칙 미러(D-5E2-7)까지」. `5e2/checklist.md` 알려진 제한 5·OPEN 처분 「미해결 — 6C 이월」 | **M6 6C** |

---

# 표 2 · 종결 판정에 걸리는 것 — 축약

재고 원문의 표 2(완료 조건 10 ↔ 근거)와 말미 정리 절은 `checklist.md` §1·§3 이 판정 정본으로 흡수했으므로 여기서는 두지 않는다(verifier r1 의견 — 표 1 은 §2.1 의 16건 근거를 유일하게 보관하므로 사본으로 남긴다).
