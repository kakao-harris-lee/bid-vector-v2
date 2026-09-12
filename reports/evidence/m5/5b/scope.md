# Slice 계약 — M5 / 5B · feature schema — **착수 계약 2026-09-12(운영자 승인: D-5B-2 (a)·D-5B-7 legacy 값)**

> **지위**: 운영자 결정 2026-09-12(「모두 추천으로 진행」 — 5A 종결 뒤 다음 slice = 5B). 세션 모델 단독 작성. 조사 digest
> `_workspace/m5-5b/01_digest.md`(읽기 전용 탐색 2026-09-12 — legacy 커널·training/serving·M2 계약·승인 문서·5A 실물·prep·fixtures).
> **Phase 2.5 설계 검토 대상**(prep `:70` — feature schema 계약): `_workspace/m5-5b/02_design-review.md`. 아래 「위협 모델」·「우회 후보」는 검토 입력.
> 레인: worktree `bid-vector-v2-m4e`, 브랜치 `m5-5b/2026-09-12`. `ml-engine/**` 만 — Kotlin lane 과 소스 겹침 0, `ci.yml` 무편집.

```yaml
milestone: m5
slice: 5b-feature-schema
base_sha: cc90f7060c3df2a002450420d1fc3de952d9b612   # PR #9 머지 커밋 = origin/main(4B-6b·5A 실물 포함)
head_sha: 리뷰 시점의 HEAD
in_scope:
  - ml-engine/src/ml_engine/features/__init__.py            # 공개 표면 재수출(아래 ①~⑥ 이름만)
  - ml-engine/src/ml_engine/features/schema.py              # FeatureSchema·FeatureColumn·FEATURE_SCHEMA_V2·verify_feature_names (D-5B-1·D-5B-8)
  - ml-engine/src/ml_engine/features/facts.py               # FeatureFacts.from_proto(FeatureInputs) → FeatureFacts | FactRejected (D-5B-3·D-5B-4)
  - ml-engine/src/ml_engine/features/vocabulary.py          # Vocabulary(정렬 tuple)·코드화(위치)·미지 → MissingCoordinate (D-5B-2·D-5B-3)
  - ml-engine/src/ml_engine/features/normalize.py           # normalize_feature_key — legacy textfmt 함수 하나 이식·타입 좁힘 (D-5B-9)
  - ml-engine/src/ml_engine/features/shrinkage.py           # pseudo_count_weight·shrink_toward 이식(K5 원시 연산, 5D 가 소비) (D-5B-6)
  - ml-engine/src/ml_engine/features/encoding.py            # AgencyTargetEncoding·build_agency_target_encoding 이식(2단 수축, MappingProxy 봉인) (D-5B-6·D-5B-7)
  - ml-engine/src/ml_engine/features/rows.py                # AwardRateFeatureSpace·build_row → FeatureRow(values, provenance) | RowRejected (D-5B-3)
  - ml-engine/src/ml_engine/features/manifest.py            # FeatureManifest·canonical_json·sha256 checksum·verify_manifest (D-5B-5)
  - ml-engine/tests/features/**                             # unit + hypothesis(property) + golden(schema v2 열·checksum 벡터·legacy 산식 대조는 legacy-behavior 라벨)
  - ml-engine/pyproject.toml                                # 변경이 필요할 때만(예: hypothesis 프로파일·mypy override 0 유지). import-linter·래칫·extras 무편집
  - milestone-5.md, reports/evidence/m5/5b/**                # reuse.md(이식 4 모듈 출처 표)·policy-values.md(OPEN-5B-POLICY-VALUES)
out_of_scope:
  - LightGBM 학습·부스터·OOF·artifact 조립·dataset manifest 내용(5C) · predict adapter·커널(5D) · servicer(5E) · Kotlin 쪽 · contracts/proto · `ci.yml`
  - 정책 값 33 의 값 · `sample_scope` 값 자체(5C 가 manifest 에 싣는다 — 5B 는 「기본값 금지」 규율만 검증 함수로)
  - legacy `reliable_base` 4값 enum 이식(D-5B-2 로 어휘가 wire 라벨 5값으로 바뀌어 **이식 대상에서 제외** — prep D-M5-5 문면 개정 대상)
  - fixtures `ml-boundary-003/004` 재평가(curator, `OPEN-5B-FIXTURE-REEVAL`) · `features` 도메인 fixture 신설
acceptance_commands:
  # 정본 = CI `ml-engine` job 이 돌리는 명령 그대로(전건). 이 slice 는 ml-engine 만 닿고 워크플로가 두 job 의 소스 독립을 선언하므로 Kotlin `check` job 은 돌리지 않는다(evidence-pack 2026-09-12 규율).
  - "(cd ml-engine && uv sync --frozen --all-extras)"                                                 # S-1
  - "(cd ml-engine && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do ! uv run --no-sync python -c \"import $m\" || exit 1; done && uv sync --frozen --all-extras)"   # S-1b
  - "(cd ml-engine && uv run ruff check . && uv run ruff format --check .)"                            # S-2
  - "(cd ml-engine && uv run mypy --strict src/ml_engine)"                                            # S-3 — features 모듈 override 0
  - "(cd ml-engine && uv run lint-imports)"                                                           # S-4 — features 는 contracts 만 본다(layers)
  - "(cd ml-engine && uv run python -m pytest tests -q)"                                              # S-5 — 5A 게이트 7 + 5B tests
  - "(cd ml-engine && uv run python tools/design_ratchet.py --check)"                                 # S-6 — 함수 50/파일 500/약한 경계 0, allowlist 0 유지
  - "(cd ml-engine && uv run python tools/reuse_provenance_check.py && ! uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md)"   # S-7 — 이식 4 모듈 docstring ↔ reuse.md 양방향
  - "(cd ml-engine && uv run python -c \"import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python']\")"   # S-9
rollback: |
    **정본 `reports/evidence/m5/5b/rollback.md`**(구현 레인). 신설 경로 삭제 + `features/__init__.py`·`pyproject.toml`(편집 시) base 로 restore.
    공유 파일은 `milestone-5.md`(팀장 문서 레인 — 목록 제외 선언)·`pyproject.toml`(5A 소유 파일 편집 시 hunk 격리). 임시 clone 실측 → in_scope diff 0 → `pytest tests -q`(5A 125+31 초록).
```

작성: 2026-09-12, 세션 모델 단독. 근거: `milestone-5.md` 5B 다섯 항목·완료 조건(「training/serving 동일 transform·schema」·「오류·최소 표본이 0점/성공으로 변환되지 않음」) ·
`v2-지침서.md` §3.2(`features/` = 공용 순수 변환)·§5 · `data-dictionary.md` §6.3(피처 계약은 M2 스키마 소유·어휘 = 피처 공간·누수 차단 시그니처 계승·결측 사유 provenance·「학습과 서빙에서 한 축의 의미가 다르면 그 피처를 쓰지 않는다」) ·
ADR 0001(skew 방지 구조 유지)·0003(schema/code/dataset/checksum)·0009 D-6(출처 양방향)·0010 A-6(`feature_schema_version` 은 패키지 version 과 다른 축) · 2B D-2B-1 (a)(wire 는 원 fact, 이름·순서 계약은 5B)·2C ②⑦(checksum sha256 hex 64, manifest 내용은 5C) · prep D-M5-5·`:83`·조사 §c-2~c-5 · D-M2-10(`denominator_source` 축 전환 = 재학습).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시 없음. 리뷰 요청 시점에 재실행.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **versioned feature schema** — `FeatureSchema(version: str, columns: tuple[FeatureColumn, ...])`, `FeatureColumn(name, kind: CATEGORICAL\|CONTINUOUS, range: ClosedRange\|None, missing: MissingPolicy)`. **`FEATURE_SCHEMA_V2`** = 열 다섯 `category`·`log_amount`·`agency_encoding`·`agency_sample_count`·`denominator_source`(legacy 순서 계승 — 순서가 계약, digest §1) + `version = "award-rate-features-v2"`(D-5B-1). 스키마 registry `SUPPORTED_FEATURE_SCHEMAS: Mapping[str, FeatureSchema]`(frozen) — 미지원 version 은 `UnsupportedSchema(version)` 결과(예외 아님). `verify_feature_names(artifact_names, schema) -> Verified \| NameMismatch(expected, actual)`(legacy `:200-206` fail-closed 대조 이식, D-5B-8) | 5B 「versioned feature name/order/type/range」 · data-extract §4 ML 「미지원 version」 · ADR 0010 A-6 |
| ② | **wire fact → 타입 fact** — `FeatureFacts.from_proto(FeatureInputs) -> FeatureFacts \| FactRejected(reason)`: fact 넷 각각 `Present(value) \| Missing(MissingReason)`(oneof 미설정·`MissingReason.UNSPECIFIED` → `FactRejected.Malformed`). **unit/basis validation**(D-5B-4): `base_amount` 는 `Money(currency KRW, basis BASE_AMOUNT, provenance ≠ UNSPECIFIED, amount_won > 0)` 아니면 `FactRejected.BasisMismatch \| NonPositiveAmount \| UnspecifiedEnum` — legacy `max(amount, 1.0)` 접힘 제거 | 5B 「unit/basis validation」 · 2B ② · §6.3 |
| ③ | **결측·미지의 좌표화**(D-5B-3, 신규 설계 지점 — digest 충돌 2) — `FeatureRow(values: tuple[float, ...], provenance: RowProvenance)`. `values` 는 LightGBM 행렬 한 행, `provenance` 는 열마다 `Observed \| Missing(MissingReason) \| OutOfVocabulary(key)` 를 나른다(행렬 밖 — §6.3 「모든 피처가 결측 사유를 provenance 로」). 좌표 규칙: 범주 열(`category`·`denominator_source`) 결측·어휘 밖 → **`NaN`**(sentinel `-1.0` 폐기; LightGBM 은 NaN 을 결측으로 취급) + provenance; `agency` 결측(wire Missing) → `agency_encoding = NaN`·`agency_sample_count = NaN`; `agency` **미관측**(어휘에 있는 요청 값이나 학습 표본 0) → 공종 평균·`log1p(0) = 0.0`(**표본 0 은 진짜 0** — 결측과 구별, `Observed`) ; `base_amount` 결측 → **행 자체 `RowRejected(MissingFact.base_amount)`**(금액 없이 예측하지 않는다 — legacy 의 `log10(1)=0` 접힘 제거); `denominator_source` 결측 → `RowRejected(MissingFact.denominator_source)`(라벨 `UNKNOWN` 은 값이고 결측은 거부) | §6.3 「결측 사유 provenance」 · 5B 「missing/unknown 명시 처리」 · 완료 조건 「0점/성공으로 변환되지 않음」 · 조사 §c-4 R-ML-09 |
| ④ | **어휘·부호화 이식** — `Vocabulary(values: tuple[str, ...])`(정렬·중복 없음 불변식, 학습이 만든다)·`code_of(key) -> int \| OutOfVocabulary`. **`denominator_source` 어휘 = wire `BaseAmountProvenanceLabel` 5값 이름**(`CLEAN`·`DERIVED_YEGA`·`DERIVED_VAT`·`SUSPECT_RATIO`·`UNKNOWN`, `UNSPECIFIED` 제외 — D-5B-2, 닫힌 enum 을 `contracts` 재수출에서 읽어 고정, 자유 문자열 아님). `AgencyTargetEncoding`·`build_agency_target_encoding(observations, policy: EncodingPolicy)` 이식(2단 pseudo-count 수축·`MappingProxyType` 봉인·`category_sample_count` 유도) ; κ 둘은 **`EncodingPolicy(agency_prior_strength, category_prior_strength)`** 인자로 외부화(D-5B-7, 값은 policy-values.md). `shrink_toward`·`pseudo_count_weight` 는 `features/shrinkage.py`(D-5B-6) | 5B 「변환 코드 이식 — 공용 단일화」 · prep D-M5-5 · 조사 §a-6 ①④ |
| ⑤ | **feature manifest + checksum**(D-5B-5, 신규) — `FeatureManifest(schema_version, columns(name·kind), vocabularies(categories, denominator_sources), encoding(agency_means 정렬 배열·category_means 정렬·global_mean), encoding_policy)` → `canonical_json(manifest) -> bytes`(키 정렬·공백 없음·float repr 고정) → `checksum = sha256 hex 소문자 64`(2C 규약과 같은 형태). `verify_manifest(manifest, expected_checksum) -> Verified \| ChecksumMismatch`. **artifact/dataset manifest 자체는 5C** — 5B 는 feature manifest 와 checksum 함수를 내고 5C 가 artifact manifest 안에 `feature_manifest_checksum` 으로 싣는다 | 5B 「dataset/feature manifest·checksum」 · 2C ②⑦(sha256 hex 64) · ADR 0003 · data-extract §4 「checksum 불일치」 |
| ⑥ | **`sample_scope` 기본값 금지 규율 이식**(D-5B-8) — `require_declared(field: str, value: T \| None) -> T \| Undeclared(field)` 헬퍼를 `schema.py` 에 두고 5C 의 artifact 로더가 쓴다(legacy `artifact_contracts.py:200-208` 사유 계승). 5B test 가 「None → Undeclared, 값 → 통과」 고정 | 조사 §c-5 「이식할 것 둘」 |

**만들지 않는 것**: 부스터 학습·예측 · artifact/dataset manifest 실물 · YAML 정책 실물 · legacy 4값 enum · 시각 의존(누수 차단 시그니처 계승 — `build_row` 는 시각을 받지 않는다) · pandas.

---

## 계약 고정 결정

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-5B-1** | 스키마 version 은 **`"award-rate-features-v2"`** 신설 — legacy `artifact_version "award-rate-gbm-v1"` 은 피처 공간 계약을 겸했으나 V2 는 어휘(D-5B-2)·결측 좌표(D-5B-3)·기초금액 거부(D-5B-4)가 달라 **같은 스키마가 아니다**. wire `PredictionEnvelope.feature_schema_version`·`ModelRelease.feature_schema_version` 이 이 문자열을 나른다. 미지원 version 은 결과 타입(`UnsupportedSchema`) — 서빙(5E)이 `UNSUPPORTED_SCHEMA` 로 옮긴다 | digest 충돌 3 · ADR 0010 A-6 | 계약 고정 |
| **D-5B-2** | `denominator_source` 어휘 = **wire `BaseAmountProvenanceLabel` 5값**(`UNSPECIFIED` 제외). legacy 4값 `ReliableBaseSource` 는 **이식하지 않는다**(prep D-M5-5 「enum 만 들어올림」 문면 개정). 어휘가 곧 범주 코드 위치라 **재학습 동반** — D-M2-10 이 이미 전제 | digest 충돌 1 · 2B `BaseAmountProvenanceLabelFact`(「GBM denominator_source 자리」) · §6.3 「도메인 어휘가 바뀌면 피처 공간이 바뀐다」 | **(a) 확정 2026-09-12**(운영자 「추천 방식으로 진행」). (b) legacy 4값 유지 + 손매핑은 「한 축의 의미가 학습·서빙에서 다른」 자리를 만들어 기각 |
| **D-5B-3** | 결측·미지는 **NaN + provenance**, 기초금액·분모 라벨 결측은 **행 거부**. sentinel `-1.0`·`max(amount,1.0)`·falsy→`""` 접힘 셋 제거. `agency` 「미관측(표본 0)」과 「결측」을 구별(NaN vs 0.0) | §6.3 · 조사 §c-4 5행 · 완료 조건 | 계약 고정 |
| **D-5B-4** | `Money` 다섯 성분 검증 — `basis == BASE_AMOUNT`·`currency == KRW`·`provenance ≠ UNSPECIFIED`·`amount_won > 0`. 위반은 `FactRejected` | 2B `Money` 「다섯 성분 전부가 계약」 · 조사 §c-3 | 계약 고정 |
| **D-5B-5** | feature manifest checksum = sha256(canonical JSON) hex 64 소문자, canonical 규칙(키 정렬·구분자 `(",", ":")`·float 는 `repr` — Python 3.12 shortest-repr 결정적)을 test 가 고정. artifact/dataset manifest 는 5C | 2C ②⑦ · ADR 0003 | 계약 고정 |
| **D-5B-6** | 수축 원시 연산(`pseudo_count_weight`·`shrink_toward`)은 **`features/shrinkage.py`**(최하층) — 5D 의 K5 `assessment_shrinkage` 가 import 해 재사용(중복 금지). κ 는 인자 | import-linter layers(`inference > features`) · 조사 K3→K5 의존 | 계약 고정 |
| **D-5B-7** | κ 둘(`agency_prior_strength 12.0`·`category_prior_strength 40.0`)은 `EncodingPolicy` 인자로 외부화, 출하 값은 **legacy-behavior**(근거 주석 없음 — 조사 §c-2 「정책 2」) → `OPEN-5B-POLICY-VALUES` | 조사 §c-2 · v2-지침서 §5 매직넘버 | **값 승인 2026-09-12** — legacy 값 그대로(12.0·40.0, `policy-values.md` §1), 5C 재학습이 튜닝 근거를 만들면 갱신 |
| **D-5B-8** | 이식할 규율 둘 — `feature_names` fail-closed 대조(`verify_feature_names`)·`sample_scope` 기본값 금지(`require_declared`). 둘 다 결과 타입, 예외 없음 | 조사 §c-5 | 계약 고정 |
| **D-5B-9** | `normalize_feature_key(value: str) -> str` = `strip().lower()` 만. `None` 은 타입으로 결측(②), 빈 문자열은 `FactRejected.EmptyKey`. alias 표 없음(legacy `_NO_ALIASES` 계승) | 조사 §a-5 textfmt · §c-4 | 계약 고정 |

---

## 위협 모델 — 5B 고유 경계

**방어한다**: (a) 결측·미지의 조용한 접힘(sentinel·0 대입) — NaN+provenance 또는 거부 (b) 단위·기준 혼입 — `Money` 성분 검증 (c) 피처 순서·이름 드리프트 — 스키마 상수 + `verify_feature_names` + manifest checksum (d) 어휘 드리프트 — 어휘는 정렬 tuple, manifest 에 실림, 분모 라벨은 닫힌 enum (e) 시간 누수 — `build_row` 시그니처에 시각 없음(계승) (f) 매직넘버 — κ 인자·정책 값 문서 (g) 학습·서빙 skew — 변환 함수 하나(`build_row`)를 5C·5D 가 같은 모듈에서 import(layers 가 강제) (h) 미지원 스키마 version 의 조용한 수용 — registry 조회 결과 타입.
**방어하지 않는다**: 부스터의 옳음·재학습 결과(5C·5D) · wire 필드 채움의 옳음(M4 4D-1 매핑) · 정책 값 내용(승인) · fixture 재평가(curator).

**우회 후보(≥5)**: (1) 어휘 밖 공종을 `-1.0` 로 접기 → 타입에 sentinel 상수 없음, `OutOfVocabulary` provenance + NaN, test 가 「값 NaN ∧ provenance 기록」 둘 다 단언 (2) 금액 0·음수 → `FactRejected.NonPositiveAmount`(행 없음) (3) `basis = ESTIMATED` 인 Money → `BasisMismatch` (4) artifact `feature_names` 순서 바뀜 → `NameMismatch` (5) manifest 의 어휘 한 항목 바꿈 → checksum 불일치 (6) κ 를 코드 상수로 되돌림 → 래칫이 아니라 리뷰 항목(경계: 매직넘버는 게이트가 못 잡음 — 정책 값 test 가 `EncodingPolicy` 필수 인자임을 단언) (7) `sample_scope=None` 통과 → `Undeclared` (8) `features` 가 `bidvector.ml.v1` 직접 import → 5A forbidden (9) `features` 가 `numpy`/`lightgbm` 을 import → 허용은 되나(extras) **의도적으로 쓰지 않음**(순수 Python `math`·`tuple`) — layers 가 막지 못하는 부분은 설계 검토 (3) 과잉 판정 (10) 학습이 정렬 안 된 어휘를 만듦 → `Vocabulary.__post_init__` 거부.

---

## (2b) 값 획득 축 (Python)

| 표면 | 판정 |
| --- | --- |
| `FeatureSchema`·`FEATURE_SCHEMA_V2`·`SUPPORTED_FEATURE_SCHEMAS` | 연다(읽기, frozen) |
| `FeatureFacts.from_proto` | 연다 — 입력 검증 진입점, 결과 타입 |
| `AwardRateFeatureSpace.build_row` | 연다 — 유일 변환 진입점(5C·5D 공용) |
| `FeatureRow`·`RowProvenance` | frozen, 생성은 `build_row` 만(모듈 밖 생성은 컨벤션 — Python 은 가시성 강제 불가, 설계 검토 (2b) 등재) |
| `build_agency_target_encoding`·`EncodingPolicy` | 연다 — κ 인자 필수(기본값 없음) |
| `canonical_json`·`checksum`·`verify_manifest`·`verify_feature_names`·`require_declared` | 연다 — 순수 함수 |

---

## OPEN — 수령·신설

| OPEN | 처리 |
| --- | --- |
| `OPEN-5B-POLICY-VALUES` | D-5B-7 κ 둘(12.0·40.0, legacy-behavior) — **착수 시 승인(2026-09-12)**, 종결 시 구현이 그 값을 출하하는지 test 대조만 |
| `OPEN-5B-FIXTURE-REEVAL` | `ml-boundary-003/004`(insufficient-evidence, `not_covered` 「M2 피처 계약 없음」) — 2B 계약이 났으므로 curator 재평가 대상. 5B test 가 같은 명제(결측 사유 provenance·누수 차단 시그니처)를 코드로 고정 |
| `OPEN-ML-05` | 수령 유지 — 5B 는 κ 만 정책 인자로, 33 값은 5C·5D |
| prep D-M5-5 문면 | 「`award_rate_features`(5B, `reliable_base` enum 만 들어올림)」 → 「enum 이식 없음, 어휘 = wire 라벨」 — 착수 커밋에서 팀장이 `prep/m5-prep.md` 갱신 |
