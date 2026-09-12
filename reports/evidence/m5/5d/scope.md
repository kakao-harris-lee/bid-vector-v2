# Slice 계약 — M5 / 5D · inference kernels — **착수 계약 2026-09-12(운영자 결정 대기: D-M5-7·8·9 확정, D-5D-5~9)**

> **지위**: 2026-09-07 초안 → 2026-09-12 착수판(세션 모델 단독). 조사 digest `_workspace/m5-5d/01_digest.md`(legacy 커널 K5·K6·K7·predictor·2B proto 축어·5B 표면·ADR 0001 §4.1·정책 표·fixtures).
> 초안과 실측이 어긋난 일곱 자리를 이 판이 처분한다(「계약 갱신 이력」). **Phase 2.5 설계 검토 대상**(fail-closed 가드·결과 타입 불변식): `_workspace/m5-5d/02_design-review.md`.
> 착수 전건: **D-M5-7 (a)**(커널 golden 은 curator 가 `authored-from-approved-spec` 으로 신설 — 5D 와 병행 레인, 별도 worktree) · **D-M5-8 (a)**(win-proxy 둘 5D 밖) ·
> **D-M5-9 (a)**(이식 목록 8 → 6 개정: 반사 KDE·곡선 빌더는 도달 경로 생길 때 — ADR 0001 §4.1 표·`milestone-5.md` 5D 「KDE density/optimization」 문면 개정을 **이 착수 커밋이 동반**).
> 레인: worktree `bid-vector-v2-m4e`, 브랜치 `m5-5d/2026-09-12`. `ml-engine/**` + 문서 셋(ADR 0001·milestone-5·prep) — Kotlin lane 과 소스 겹침 0.

```yaml
milestone: m5
slice: 5d-inference-kernels
base_sha: f5020aa982e6c5ade01f1660c0568dcd75c2fa7e   # PR #10 머지 커밋 = origin/main(5A·5B 실물 포함)
head_sha: 리뷰 시점의 HEAD
in_scope:
  - ml-engine/src/ml_engine/inference/__init__.py           # 공개 표면 재수출
  - ml-engine/src/ml_engine/inference/results.py            # KernelResult = Success | Unmeasurable(reason, detail) · Candidate(label, bid_rate: Decimal, weight: Decimal, weight_policy_version) · PriceFitness(NewType) · Uncertainty · Diagnostics(+shrinkage_weight) · UnmeasurableDetail(StrEnum)
  - ml-engine/src/ml_engine/inference/reserve_draw.py       # K6 이식 — exact_draw_mean_distribution · draw_mean_moments → 결과 타입(ValueError 셋 → Unmeasurable)
  - ml-engine/src/ml_engine/inference/assessment.py         # K5 이식 — LevelObservation · AssessmentPosterior(level_weights 3필드) · resolve_assessment_posterior + **provenance 게이트**(D-5D-5) — 5B `shrink_toward`·`pseudo_count_weight` 재사용(κ 는 사정률 축 별도 정책)
  - ml-engine/src/ml_engine/inference/maturity.py           # K7 이식 — SettlementObservation · MaturityWindow · Maturity = Observed | NoObservation(0/0 접힘 제거) · KST 주 경계(zoneinfo)
  - ml-engine/src/ml_engine/inference/scenario.py           # 후보 3 산출(z·가중치·클램프 밴드는 InferencePolicy) · Decimal 경계 변환(D-5D-1)
  - ml-engine/src/ml_engine/inference/predict.py            # LightGBM predict adapter — BoosterLike Protocol · 가용성 게이트(UNTRAINED_SEGMENT/INSUFFICIENT_SAMPLES, 임계 max(1,·) 클램프) · 예측 경로가 같은 판정 함수 사용
  - ml-engine/src/ml_engine/inference/policy.py             # InferencePolicy frozen dataclass + load_inference_policy(path) -> InferencePolicy | PolicyRejected(5A load_policy 의 PolicyError 를 경계에서 결과 타입으로) · known_keys 전수 · SHIPPED_INFERENCE_POLICY_VERSION
  - ml-engine/src/ml_engine/registry/artifact.py            # ArtifactManifestV1 read model(5D 소유, 5C 가 writer) · load_artifact(bytes, expected: ModelReleaseRef) -> LoadedArtifact | ArtifactRejected — sha256 불일치·schema version·feature_names·feature manifest checksum·sample_scope 미선언 전부 로드 거부(객체 미생성)
  - ml-engine/policy/inference-v1.yaml                      # 정책 값 실물(D-5D-8, OPEN-5D-POLICY-VALUES 승인 값) — 저장소 첫 정책 YAML
  - ml-engine/tests/inference/**                            # property(hypothesis)·경계·규칙표·legacy_parity 마커(판정 아님)·golden 소비(curator case 가 오면 tests/inference/golden 이 fixtures/expected 를 읽음)
  - ml-engine/tests/registry/**
  - ml-engine/pyproject.toml                                # ruff select 에 "BLE" 추가(D-5D-6) · pytest 마커 legacy_parity 등록 · hypothesis 프로파일 위치(5B 인수) — 게이트 계약·래칫 한도 무편집
  - docs/adr/0001-*.md §4.1 · milestone-5.md 5D 문면 · reports/evidence/m5/prep/m5-prep.md D-M5-9   # D-M5-9 (a) 개정(팀장 문서 레인, 착수 커밋)
  - milestone-5.md, reports/evidence/m5/5d/**                # reuse.md(K5·K6·K7·scenario·predictor 출처)·policy-values.md
out_of_scope:
  - 학습·OOF·artifact **writer**·dataset manifest(5C — 단 5D 가 정한 `ArtifactManifestV1` 형태를 5C 가 써야 한다) · servicer·readiness 표현·wire 매핑(5E) · Kotlin
  - win-proxy 커널 둘(D-M5-8 (a)) · 반사 KDE·곡선 빌더(D-M5-9 (a)) · `ensemble.py`·`historical/**`(도달 경로 밖, 함수 50줄 초과 일곱의 소재)
  - 업무 법정 하한·자격·최종 결정·guardrail 10필드·가격 반올림(Kotlin) — 정책 표 #1~6·9~14·21~23 은 5D 가 소비하지 않음(5A 표 「5D」 배정 정정)
  - 합성 `confidence`(§6.5) · `except Exception` 폴백(c-2) · Platt 자격 라벨(D-M2-11) · `Diagnostics.shrinkage_weight` 의 **wire 추가**(M2 additive → `OPEN-5D-DIAGNOSTICS-WIRE`, 5E 전 2F)
  - fixtures 신설(curator 병행 레인, D-M5-7 (a)) — 5D test 는 case 가 도착하면 소비만
acceptance_commands:
  # 정본 = CI `ml-engine` job 전건(하네스 2026-09-12 규율). Kotlin job 은 소스 무접촉이라 돌리지 않는다.
  - "(cd ml-engine && uv sync --frozen --all-extras)"                                                 # S-1
  - "(cd ml-engine && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do ! uv run --no-sync python -c \"import $m\" || exit 1; done && uv sync --frozen --all-extras)"   # S-1b
  - "(cd ml-engine && uv run ruff check . && uv run ruff format --check .)"                            # S-2 — BLE 포함
  - "(cd ml-engine && uv run mypy --strict src/ml_engine)"                                            # S-3 — K7 strict 승격 포함, override 추가 0
  - "(cd ml-engine && uv run lint-imports)"                                                           # S-4 — inference 는 features·contracts(+registry) 만, grpc 금지
  - "(cd ml-engine && uv run python -m pytest tests -q)"                                              # S-5 — 5A 게이트 + 5B + 5D(legacy_parity 포함 실행되되 판정 근거 아님)
  - "(cd ml-engine && uv run python tools/design_ratchet.py --check)"                                 # S-6
  - "(cd ml-engine && uv run python tools/reuse_provenance_check.py && ! uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md)"   # S-7
  - "(cd ml-engine && uv run python -c \"import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']\")"   # S-9
rollback: |
    **정본 `reports/evidence/m5/5d/rollback.md`**(구현 레인). `inference/**`·`registry/artifact.py`·`policy/`·tests 신설 삭제 + `pyproject.toml` hunk 격리(5A 소유 파일). 문서 셋(ADR·milestone·prep)은 팀장 문서 레인 — 목록 제외 선언.
    임시 clone 실측 → in_scope diff 0 → `pytest tests -q`(5A+5B 233 초록).
```

작성: 2026-09-07 초안 · 2026-09-12 착수판 — 세션 모델 단독. 근거: `milestone-5.md` 5D 다섯·완료 조건 · `v2-지침서.md` §3.2·§4.4·§5 · `capability-map.md` ML-01~05·09·11 · `data-dictionary.md` §6.4·§6.5 · ADR 0001 §4.1·D-6 · ADR 0006 D-7 · ADR 0009 · ADR 0010 D-3 · 2B ③~⑦·D-2B-4·6·8 · `error.proto` `UnmeasurableReason`(3값)+`detail_code` · prep D-M5-5~9 · 5B D-5B-6(수축 원시 연산 재사용).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시 없음. 리뷰 요청 시점 재실행.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **순수 커널 셋 이식**(수학 유지·결합 제거·경계 결과 타입) — **K6** `reserve_draw`: 닫힌식 그대로, `ValueError` 셋(`draw_count < 1`·비유한/≤0 값·`len < draw_count`)을 `Unmeasurable(INSUFFICIENT_SAMPLES \| FEATURE_ABSENT, detail)` 결과 타입으로 · **K5** `assessment`: `resolve_assessment_posterior` 그대로 + `level_weights: dict[str,float]` → 3필드 dataclass + **provenance 게이트**(D-5D-5) · **K7** `maturity`: 계산만, `0/0 → Maturity.NoObservation`, `app.core.time` 대신 `zoneinfo("Asia/Seoul")`. 이식 대상 파일 최대 함수 40줄 — **분해 대상 없음**(초안 ② 정정). K7 strict 승격. 출처 docstring `Reuse:` + `reuse.md` | ML-11.1 · ML-04 · §6.4 · ADR 0009 · `OPEN-ML-01` 해소 |
| ② | **LightGBM predict adapter + 가용성 게이트** — `BoosterLike` Protocol(`predict(rows) -> Sequence[float]`) 뒤에 LightGBM 을 두고 test 는 fake · **미학습 공종 가드**: `segment_rows == 0 → UNTRAINED_SEGMENT(detail NEVER_TRAINED)`, `0 < rows < max(1, policy.min_category_rows) → INSUFFICIENT_SAMPLES(detail SHALLOW_SEGMENT)` — enum 으로 구별, 임계 하한 1 클램프는 코드 불변식(D-5D-3), **가용성 게이트와 예측 경로가 같은 판정 함수** · 입력 행은 5B `build_row` 결과만(`FeatureRow`; `RowRejected` → `Unmeasurable(FEATURE_ABSENT)`) | ML-02 셋 · M5 완료 조건 · D-2B-8 |
| ③ | **결과 타입** — `KernelResult = Success(candidates: tuple[Candidate,3], fitness: PriceFitness, uncertainty: Uncertainty, diagnostics: Diagnostics) \| Unmeasurable(reason: UnmeasurableReason, detail: UnmeasurableDetail)`. `UnmeasurableReason` 은 wire 3값 미러(`INSUFFICIENT_SAMPLES`·`UNTRAINED_SEGMENT`·`FEATURE_ABSENT`), `detail` 은 닫힌 `StrEnum`(`NEVER_TRAINED`·`SHALLOW_SEGMENT`·`NON_FINITE_INPUT`·`DEGENERATE_VARIANCE`·`TOO_FEW_DRAWS`·`ROW_REJECTED`) → wire `detail_code`(D-5D-2 갱신). 예외로 실패를 나르지 않는다(`BLE001`). 후보 순서 `CONSERVATIVE·BASE·AGGRESSIVE` 고정, `bid_rate`·`weight` 는 **`Decimal`**(경계에서 한 번, scale 정책) · `Candidate.weight_policy_version = InferencePolicy.version` | ML-01 · 2B ③④ · ADR 0010 D-3 |
| ④ | **불확실성 성분 셋 + 출처** — `sample_size`(실제 쓰인 표본)·`dispersion`·`estimate_margin`(모두 `Decimal`)·`interval_source ∈ {CROSS_VALIDATION_RESIDUAL, TIME_HOLDOUT_RESIDUAL}`. 합성 `confidence` 세 자리 **이식하지 않음**. §6.5 분기 경고(`sample_size == 1` 이면 legacy 는 margin = std 재산출) — **`sample_size < policy.min_samples_for_variance` 이면 `Unmeasurable(INSUFFICIENT_SAMPLES, DEGENERATE_VARIANCE)`**(margin 을 지어내지 않음) | §6.5 · ML-03 |
| ⑤ | **경계 거동 규칙표**(property test) — 최소 표본 미만(`min_reserve_records 8`·`min_bid_ratio_samples 3`·`draw_count`) → `INSUFFICIENT_SAMPLES` · 분산 0 → `DEGENERATE_VARIANCE` · NaN/Inf 입력 → `FEATURE_ABSENT(NON_FINITE_INPUT)` · 0/음수 금액은 5B `facts` 가 이미 거부(`RowRejected` 전파) · K7 `opened_count == 0 → NoObservation` · 관측 밴드(`assessment 0.8~1.2`·`bid ratio 0.5~1.5`) 밖 관측은 **입력 정제 단계에서 제외되되 제외 수가 `Diagnostics` 에 실림**(조용한 drop 금지) | 5D 「최소 표본, singular, NaN/Inf」 · §6.4 · 조사 c-3 |
| ⑥ | **objective 별 후보·diagnostics** — `SCENARIO_TRIPLE` 하나: `bid_rate_i = clamp(scale·(center + sign_i·z·std), band)`(legacy `scenario_spec.py` 산식 그대로, 괄호 위치 포함), z·가중치·밴드·자릿수는 `InferencePolicy`(D-5D-8). `Diagnostics(training_row_count, segment_support ∈ {DIRECT, PARENT_CATEGORY, GLOBAL}, shrinkage_weight: Decimal, excluded_observations: int)` — `shrinkage_weight`·`excluded_observations` 는 wire 에 없어 Python 결과 타입에만(`OPEN-5D-DIAGNOSTICS-WIRE`) | 5D 「objective 별 후보와 diagnostics」 · ML-04 「수축 가중치가 응답 근거에」 · §5 매직넘버 |
| ⑦ | **artifact 무결성 게이트**(`registry/artifact.py`) — `ArtifactManifestV1`(5D 소유 read model: `manifest_schema_version`·`release: {release_id, artifact_checksum, feature_schema_version, code_version, dataset_id}`·`feature_manifest_checksum`·`feature_names`·`sample_scope`(기본값 없음 — 5B `require_declared`)·`residual_std`·`training_row_count`·`booster_model`·`reproducibility: {seed, num_threads, deterministic}`). `load_artifact(raw: bytes, expected: ModelReleaseRef) -> LoadedArtifact \| ArtifactRejected(reason)` — **sha256(raw) ≠ expected.artifact_checksum → 거부, 객체 미생성** · `feature_schema_version` 미지원 → 거부 · `verify_feature_names` 불일치 → 거부 · `feature_manifest_checksum` 불일치 → 거부 · `sample_scope` 미선언 → 거부. **5C 는 이 형태로 쓴다**(계약 인수 — 5C 착수 계약이 이 read model 을 정본으로) | M5 완료 조건 「checksum 불일치 fail-closed」·「재현성 파라미터 manifest 기록」 · ML-05 · 2C ⑦ |
| ⑧ | **정책 값 실물** — `policy/inference-v1.yaml`(D-5D-8: 아래 표) + `load_inference_policy` (5A `load_policy(known_keys=전수)` → `PolicyError` 를 **경계에서** `PolicyRejected` 결과 타입으로; 값 범위 불변식(z > 0·가중치 합 1·밴드 하한 < 상한·임계 ≥ 1) 위반도 `PolicyRejected`) · `SHIPPED_INFERENCE_POLICY_VERSION = "inference-v1"` 이 `Candidate.weight_policy_version` 으로 | ADR 0006 D-7 · `OPEN-ML-05` · 5A D-M5-6 |
| ⑨ | **golden 소비 자리** — `tests/inference/golden/` 이 `fixtures/expected/ml-kernel-*` 를 읽어 후보 율을 **문자열 비교**(scale 보존). curator case 가 도착하기 전에는 test 가 `skip(reason="curator case 대기 — OPEN-5D-GOLDEN")` 로 **명시 skip**(조용한 통과 아님) · legacy 출력 대조는 `@pytest.mark.legacy_parity` — 회귀 관측, 판정 아님 | D-M5-7 (a) · §3.2 |

**만들지 않는 것**: 변환(5B) · 학습·writer(5C) · servicer·readiness(5E) · 가격 산출·법정 하한(Kotlin) · win-proxy 둘·KDE·곡선 빌더 · 합성 신뢰도 · 폴백 · `ensemble`·`historical` · wire 필드 추가.

---

## 계약 고정 결정

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-5D-1** | 커널 내부 `float`, 경계에서 `Decimal` 한 번(`bid_rate` scale = `policy.bid_rate_digits 4`, `weight`·성분은 `repr` 그대로 `Decimal(str(x))` 뒤 quantize 없음 — 정보 손실 없이 scale 보존) | D-M2-6 · `Rate.fraction` scale 보존 | 계약 고정 |
| **D-5D-2** | `UnmeasurableReason` 은 wire 3값 미러(추가 없음), 세분 사유는 `detail: UnmeasurableDetail(StrEnum)` → `Unmeasurable.detail_code` — M2 additive 불필요 | `error.proto` `detail_code` 존재 | 계약 고정(초안의 「값 추가 시 2A 호환 추가」 폐기) |
| **D-5D-3** | 미학습 가드 임계는 정책, 하한 1 클램프는 코드 불변식(`max(1, ·)`), 정책 파일이 0 을 줘도 1 | ML-02 ③ | 계약 고정 |
| **D-5D-4** | 결정성 — seed·threads 는 5C 소유(`reproducibility` 를 manifest 로 받아 **대조만**); predict 는 스레드 수 무관 동일값 test(fake booster 로 구조 test + LightGBM 실 booster 1건은 5C artifact 가 있어야 하므로 `OPEN-5D-REAL-BOOSTER`) | ML-05 | 계약 고정 |
| **D-5D-5** | **K5 provenance 게이트 신설**(초안 「결합 절단이 전부」의 두 번째 예외) — 타입으로: `CleanAssessmentSample`(provenance == `CLEAN` 인 행만 생성 가능한 frozen dataclass, 생성 함수 `admit_clean(samples: Iterable[AssessmentSample]) -> (tuple[CleanAssessmentSample,...], excluded: int)`)만 `LevelObservation` 집계 함수가 받는다 — 「값이 정수라서 통과하는 경로가 없다」 | ML-04 acceptance 축어 | **운영자 확인**(추천 위 · 대안: 호출부 필터 유지 = legacy 그대로, ML-04 문면 미충족) |
| **D-5D-6** | ruff `select` 에 **`BLE`** 추가(`pyproject.toml` 5A 소유 편집 — in_scope) → 위협 (a) 의 `except Exception` 방어 성립. 기존 코드에 위반 있으면 5D 가 고친다(예상 0) | 조사 digest §4 | **운영자 확인**(추천 채택) |
| **D-5D-7** | `Diagnostics.shrinkage_weight`·`excluded_observations` 는 Python 결과 타입에만 — wire 추가는 `OPEN-5D-DIAGNOSTICS-WIRE`(5E 전 M2 additive slice 2F; 2A~2E 절차·태그) | 2B `Diagnostics` 2필드 | **운영자 확인**(추천 위 · 대안: 5D 가 proto 편집 — contracts 는 M2 소유라 비추천) |
| **D-5D-8** | 정책 값(`inference-v1.yaml`, `OPEN-5D-POLICY-VALUES`): 아래 표 — 전부 legacy-behavior, 5D 가 지어낸 수치 없음. `PolicyScalar` 제약으로 **평탄 키**(`scenario.conservative.z_sign=-1`, `scenario.conservative.weight=0.24` …). K5 κ 는 **사정률 축 별도 키**(5B 낙찰률 축 `SHIPPED_ENCODING_POLICY` 재사용 금지 — legacy 의도적 분리) | §5 매직넘버 · ADR 0006 D-7 · digest §6·§7 | **값 승인 대기**(추천: legacy 값) |
| **D-5D-9** | 정책 표 배정 정정 — `#31 MIN_CATEGORY_ROWS`(40) 는 5D 소비(5A 표 「5C」 → 「5C·5D」), `#28`·`#29` 5D 소비, 「5D」 24행 중 guardrail·가격 18행은 Kotlin 소유로 재배정(5A `policy-values.md` 표 갱신 — 팀장 문서 레인) | digest §6 | **운영자 확인**(추천 채택) |

### D-5D-8 정책 값 표(승인 대상)

| 키(평탄) | 값 | legacy 좌표 |
| --- | --- | --- |
| `scenario.z` | `1.2816` | `scenario_spec.py` `SCENARIO_INTERVAL_Z` |
| `scenario.{conservative,base,aggressive}.weight` | `0.24 / 0.52 / 0.24` | `CANDIDATE_SCENARIOS` |
| `scenario.{conservative,base,aggressive}.z_sign` | `-1 / 0 / +1` | 같은 곳 |
| `scenario.clamp_min` / `scenario.clamp_max` | `0.7` / `1.4` | `historical/statistics.py` `clamp_bid_rate` |
| `scenario.bid_rate_digits` | `4` | `_BID_RATE_DIGITS` |
| `assessment.agency_prior_strength` / `assessment.category_prior_strength` | `12.0` / `40.0` | `assessment_shrinkage.py:45-46`(사정률 축 — 5B 낙찰률 축과 별도 선언) |
| `assessment.min_predictive_std` | `0.002` | `MIN_PREDICTIVE_STD` |
| `assessment.min_samples_for_variance` | `2` | `MIN_SAMPLES_FOR_VARIANCE` |
| `assessment.plausible_min` / `assessment.plausible_max` | `0.8` / `1.2` | `core/constants.py:414-415` |
| `reserve.draw_count` / `reserve.expected_price_count` | `4` / `15` | K6 |
| `reserve.min_reserve_records` | `8` | 5A 표 #28 |
| `bid_ratio.min_samples` | `3` | 5A 표 #29 |
| `bid_ratio.plausible_min` / `bid_ratio.plausible_max` | `0.5` / `1.5` | `distribution_extraction.py:47-48` |
| `gbm.min_category_rows` | `40` | 5A 표 #31 |
| `maturity.window_days` | `7` | K7 `MATURITY_WINDOW_LENGTH` |
| `version` | `inference-v1` | — |

---

## 위협 모델 — 5D 고유 경계

**방어한다**: (a) 실패의 폴백 접힘 — 결과 타입 + `BLE001`(D-5D-6) (b) 두 사유 합침 — enum + detail 분리 test (c) 가드 비활성화 — 클램프 불변식(정책 0 → 1) (d) 피처 순서 불일치 통과 — `verify_feature_names` 정확 일치 (e) 합성 신뢰도 재유입 — 닫힌 dataclass, 필드 없음 (f) `dict[str, Any]` 재유입 — 래칫 0(`level_weights` 3필드화) (g) 매직넘버 — 정책 부재 시 `PolicyRejected`, 코드 기본값 없음 (h) checksum 불일치 artifact 로드 — `load_artifact` 가 검증 실패 시 객체를 만들지 않는다(생성자는 모듈 private, 검증 통과 증거 타입 `VerifiedBytes` 만 받음) (i) `0/0` 비율화 — `Maturity` sealed (j) provenance 우회 — `CleanAssessmentSample` 만 집계 입력(D-5D-5) (k) 조용한 관측 drop — `excluded_observations` 계수.
**방어하지 않는다**: 수학의 옳음(승인 명세·golden) · 학습 데이터 편향(5C) · wire 매핑(5E) · 정책 값 내용 · Python 가시성(직접 생성 컨벤션 — 5B 와 같은 한계, (2b) 등재).

**우회 후보(≥5)**: (1) `Unmeasurable` 대신 후보 3 을 0 율 → `Candidate.bid_rate > 0` 불변식 (2) 예외 삼켜 `Success` → `BLE001` + 결과 타입 test (3) 정책 임계 0 → 클램프 (4) `feature_names` 부분집합 통과 → 정확 일치 (5) NaN → 0.0 치환 → 규칙표 property (6) `confidence` 를 diagnostics 에 → 닫힌 dataclass (7) legacy_parity 를 판정으로 승격 → 마커·문면 (8) checksum 검사 뒤 무시 → `VerifiedBytes` 타입 (9) `NoObservation` 을 0.0 으로 접는 소비자 → sealed `match` 소진 (10) 비-`CLEAN` 행을 `CleanAssessmentSample` 로 직접 생성 → 생성자 컨벤션 + `admit_clean` 유일 진입점(Python 한계, 등재) (11) 정책 YAML 에 미지 키 → `known_keys` 전수 → `PolicyRejected` (12) 가중치 합 ≠ 1 → `PolicyRejected`.

---

## (2b) 값 획득 축 (Python)

| 표면 | 판정 |
| --- | --- |
| `KernelResult`·`Candidate`·`Uncertainty`·`Diagnostics` frozen | 연다(읽기) — 직접 생성은 컨벤션(Python 한계) |
| `predict_bid_rates(facts, artifact, policy) -> KernelResult` | 연다 — 유일 추론 진입점 |
| `load_artifact(raw, expected) -> LoadedArtifact \| ArtifactRejected` | 연다 — `LoadedArtifact` 는 이 함수만 만든다(`VerifiedBytes` private) |
| `load_inference_policy(path) -> InferencePolicy \| PolicyRejected` | 연다 — 값 필수, 기본값 없음 |
| `admit_clean(...)` | 연다 — `CleanAssessmentSample` 유일 진입점(컨벤션) |
| `BoosterLike` Protocol | 연다 — test fake 주입 자리(판정 함수는 주입하지 않음) |

---

## OPEN — 수령·신설

| OPEN | 처리 |
| --- | --- |
| `OPEN-5D-POLICY-VALUES` | D-5D-8 표 — 착수 시 승인(추천 legacy 값) |
| `OPEN-5D-DIAGNOSTICS-WIRE` | `shrinkage_weight`·`excluded_observations` wire 추가 — 5E 전 M2 additive(2F) |
| `OPEN-5D-GOLDEN` | curator 병행 레인이 `ml-kernel-*` case 신설(D-M5-7 (a)) — 도착 전 golden test 는 명시 skip |
| `OPEN-5D-REAL-BOOSTER` | 실 LightGBM booster 결정성 test 는 5C artifact 뒤 |
| `OPEN-5B-OBSERVATION-DOMAIN`·`OPEN-5B-FEATURES-FORBIDDEN` | 수령 유지(5C) — 5D 는 `features` 소비만 |
| `OPEN-ML-03` | `PriceFitness` NewType 분리로 승계(확률 자리에 대입 불가) |
| `OPEN-ML-05`·`OPEN-ML-06`·`OPEN-SET-06` | D-M5-6·8 확정 반영 · 성숙도 값·판정은 Kotlin |
| ADR 0001 §4.1 · milestone-5 5D 문면 | D-M5-9 (a) 개정 — 착수 커밋 |

---

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-12 (verifier r1 뒤) | **⑦ 보강** — `LoadedArtifact` 생성은 `VerifiedBytes`(모듈 private) 를 **필수 인자**로 받아 manifest 만으로는 생성이 타입 오류(mypy strict 가 거부). **⑧ 보강** — `PolicyRejected` 불변식에 `clamp_min > 0`·`weight ≥ 0`·`min_predictive_std > 0`·`plausible_min < plausible_max` 추가(커널이 예외를 던질 정책 값은 로더가 거부). **D-5D-10 신설** — 경계 `Decimal` quantize 는 `ROUND_HALF_UP`(scale 4); legacy float `round()` 와 어긋나는 예(center 0.87465 → V2 0.8747) 는 **의도된 갈림**(legacy 출력은 정답이 아님) — checklist 알려진 제한. **③ 보강** — `UnmeasurableDetail` 에 `NO_GLOBAL_SAMPLES`(K5 global 표본 0) 추가, 추첨 detail 재사용 금지. **⑦ 보강** — manifest `residual_std`·수치 필드 비유한 → `ArtifactRejected`. **⑨ 보강** — golden test 는 case 마다 `verified_paths` 를 **단언**(`assert cases` 만은 무검증 초록), case 파일은 **평면 경로** `fixtures/expected/ml-kernel-NNN.json`(curator 관례). **acceptance 갱신** — S-5 는 `-m "not legacy_parity"` 로 판정, `legacy_parity` 는 별도 S-8(관측, exit 무관·기록만). **`pyproject.toml` mypy 에 `warn_unreachable = true`**(5A 소유 파일 hunk — `dict[str, JsonScalar]` 가 죽은 코드를 만든 것을 게이트가 잡도록), artifact.py 는 TypedDict/dataclass 파싱으로 죽은 구간 제거 | verifier r1 M-1(직접 생성 checksum 0회)·M-2(clamp_min ≤ 0 → 커널 ValueError 누출)·M-3(golden 무검증 초록)·M-4(mypy 죽은 코드 — `verify_feature_names` 호출이 unreachable)·L-1~L-4 |
| 2026-09-12 (범위 판정) | **`OPEN-5D-DISTRIBUTION-ENGINE` 신설** — K5·K6·K7 → 후보 3 을 잇는 분포 엔진 조립(legacy `distribution.py`)은 5D in_scope 에 없다(verifier 판정: 계약 미충족이 아니라 범위 공백). ML-04 둘째 acceptance(수축 가중치가 응답 근거에)·golden 011·`segment_support` 폴백은 그 조립 slice 가 만족시킨다. 처분은 운영자 결정(추천: 5E 전 소형 slice 5D-2, predictor 선택 축 `PREFERRED_PREDICTOR` 포함) | verifier r1 범위 판정 · 구현 보고 #8·#9 |
