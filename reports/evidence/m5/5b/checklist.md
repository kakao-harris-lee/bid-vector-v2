# M5/5B — checklist.md

## D-5B-1~9 충족 근거

| ID | 결정 | 충족 근거 |
| --- | --- | --- |
| D-5B-1 | schema version `"award-rate-features-v2"` 신설, 미지원은 결과 타입 | `schema.py::FEATURE_SCHEMA_V2.version` · `SUPPORTED_FEATURE_SCHEMAS`(frozen `MappingProxyType`) · `resolve_schema` → `UnsupportedSchema`(예외 아님) · `tests/features/test_schema.py::test_resolve_schema_unknown_version_is_result_type_not_exception` |
| D-5B-2 | `denominator_source` 어휘 = wire `BaseAmountProvenanceLabel` 5값(UNSPECIFIED 제외), legacy 4값 미이식 | `vocabulary.py::denominator_source_vocabulary`(enum `.keys()`에서 직접 도출, 리터럴 나열 없음) · `test_vocabulary.py::test_denominator_source_vocabulary_derived_from_enum_not_literal`(구조 대조) |
| D-5B-3 | 결측·미지 = NaN + provenance, 기초금액·분모 결측 = 행 거부, `agency` 결측≠미관측 구별 | `rows.py::AwardRateFeatureSpace.build_row`(`_categorical_column`·`_agency_columns`) · `test_rows.py`(규칙표 전건 + `test_nan_position_equals_provenance_missing_or_oov_position` + `test_build_row_unobserved_agency_sample_count_is_zero_not_missing`) |
| D-5B-4 | `Money` 5성분 검증(currency KRW·basis BASE_AMOUNT·provenance≠UNSPECIFIED·amount_won>0) | `facts.py::_resolve_base_amount` · `test_facts.py`(basis/currency/provenance/amount 각각 개별 거부 test 4개 + legacy clamp 제거 확인) |
| D-5B-5 | checksum = sha256(canonical JSON) hex 소문자 64, canonical = 키 정렬·구분자 고정·float repr, **입력 순서 무관**(verifier r1 M-1) | `manifest.py::canonical_json`·`compute_checksum`·`FeatureManifest.__post_init__`(`categories`·`denominator_sources`·`agency_means`·`category_means`를 키 기준 정렬) · `test_manifest.py`(결정성·키 순서 무관·float 민감·hex 64 소문자 + `test_manifest_sorts_agency_and_category_means_regardless_of_input_order`·`test_manifest_post_init_stores_sorted_tuples`·`test_manifest_different_content_still_yields_different_checksum` 3개 신규) |
| D-5B-6 | 수축 원시 연산(`pseudo_count_weight`·`shrink_toward`)은 `features/shrinkage.py`(최하층, 5D 재사용 대상) | `shrinkage.py`(legacy `assessment_shrinkage.py` 원시 연산 2개만 이식) · `encoding.py`가 그 모듈을 import(중복 없음, layers 게이트가 `features`끼리의 내부 import는 제한하지 않음 — `contracts`만 금지 대상) |
| D-5B-7 | κ 둘은 `EncodingPolicy` 필수 인자(기본값 없음), 출하값은 legacy-behavior 승인값 | `encoding.py::EncodingPolicy`(dataclass, 기본값 없음) · `SHIPPED_ENCODING_POLICY = EncodingPolicy(12.0, 40.0)` · `test_encoding.py::test_encoding_policy_requires_both_kappas_no_defaults`(`inspect.signature` 로 기본값 부재 확인) · `policy-values.md` §1 값과 일치(`test_shipped_encoding_policy_matches_policy_values_md`) |
| D-5B-8 | `verify_feature_names`(fail-closed 대조) · `require_declared`(`sample_scope` 기본값 금지) | `schema.py::verify_feature_names` → `Verified \| NameMismatch` · `require_declared` → `T \| Undeclared` · `test_schema.py`(양방향 대조 3개) · `test_require_declared.py`(None/값/falsy 0 구분 3개) |
| D-5B-9 | `normalize_feature_key = strip().lower()`만, alias 표 없음, 빈 키는 `FactRejected.EmptyKey` | `normalize.py`(legacy `_NO_ALIASES` 계승 — alias 인자 자체를 제거) · `facts.py::_resolve_text_fact`(정규화 후 빈 문자열 판정) · `test_facts.py::test_from_proto_empty_category_key_rejected`·`test_from_proto_empty_agency_key_rejected` |

## (2b) 값 획득 축 — 5B 가 여는 public 표면

| 표면 | 판정 |
| --- | --- |
| `FeatureFacts`(frozen dataclass) 직접 생성 | **연다**(Python 가시성 한계, 설계 검토 우회 (11) 수용) — 의미는 「`from_proto`를 통과한 fact」. 계약 강제는 `from_proto`만 검증 진입점이라는 docstring 관례와 test 뿐, 타입 시스템이 막지 못한다. `build_row`는 `Money` 성분을 다시 검증하지 않으므로, 손으로 만든 `FeatureFacts`를 넘기면 검증 우회가 가능하다 — 알려진 제한 ① |
| `FeatureRow`·`RowProvenance` 직접 생성 | 연다(같은 한계) — 5C·5D 는 `build_row` 결과만 소비하는 것이 관례(리뷰 항목), 강제 없음 |
| `AwardRateFeatureSpace`(categories·denominator_sources·agency_encoding) 직접 생성 | 연다 — `Vocabulary.__post_init__`만 정렬·중복을 강제, 어휘 내용의 도메인 정합(학습 코퍼스와 일치)은 타입이 못 본다(5C 소관) |
| `EncodingPolicy` | 연다 — 값 필수(기본값 없음, D-5B-7). κ 값 자체의 타당성(양수·유한)은 검증하지 않는다 — 알려진 제한 ② |
| `Vocabulary` | `__post_init__` 불변식(정렬·중복 없음) — **닫는다** |
| `FeatureManifest`·`ManifestColumn`·`ManifestAgencyMean`·`ManifestCategoryMean` | 연다(원시 데이터 구조 직접 생성 자체는 막지 않는다) — 다만 `__post_init__`이 `categories`·`denominator_sources`·`agency_means`·`category_means`를 강제 정렬해 **입력 순서와 무관하게 같은 checksum**을 내도록 닫는다(verifier r1 M-1, `Vocabulary`와 같은 갈래). `canonical_json`이 유일한 직렬화 진입점 |
| **`EncodingOutcome`(`Built`\|`NoObservations`, 신규 — verifier r1 M-2)** | `build_agency_target_encoding`의 반환이 여는 표면 — 연다(호출부가 `Built`를 풀지 않고 `AgencyTargetEncoding(agency_means={}, category_means={}, global_mean=0.0)`을 직접 만들면 `NoObservations` 가드를 그대로 우회할 수 있다, ①과 같은 Python 가시성 한계). 5C의 빈 코퍼스 가드가 이 결과 타입을 실제로 분기하는지는 5C verifier 대상 |
| 함수 전부(`build_row`·`from_proto`·`build_agency_target_encoding`·`canonical_json`·`verify_manifest`·`verify_feature_names`·`require_declared`) | 순수·결과 타입 반환 — 예외는 프로그래밍 오류(잘못된 타입 전달 시 `TypeError`)만, 표 안의 도메인 오류는 전부 결과 타입 |

**새로 여는 것 중 별도 게이트가 없는 자리**: `FeatureFacts`·`FeatureRow`·`AwardRateFeatureSpace`
직접 생성·`EncodingOutcome`의 `NoObservations` 우회(위 표) — 타입 자체가 막지 못하고
test·리뷰가 관례를 지킨다. 5C·5D 구현 시 `build_row`/`from_proto` 결과만 소비하는지,
`build_agency_target_encoding`의 `NoObservations`를 실제로 분기하는지 verifier가 확인해야
한다(설계 검토 (2b) 그대로, 5B 시점 신규 위반 없음 — S-5의 5B 전용 70개 test 전부가 공개
진입점만 통해 값을 만든다).

**`FeatureColumn.range`(스키마 사실, 정책 값 아님)**: `log_amount`·`agency_sample_count`는
`[0, ∞)`(둘 다 `log10`/`log1p`의 치역), `agency_encoding`은 `[0, 1]`(낙찰률 축 자체가
비율), 범주 열(`category`·`denominator_source`)은 `None`. 이 값은 승인 대상 정책이 아니라
연산의 정의역·치역에서 나오는 수학적 사실이라 `policy-values.md`에 싣지 않는다(verifier
r1 L-1 — `test_rows.py`의 `_assert_observed_values_within_schema_range`가 `Observed` 값
전부를 이 범위로 실제 단언한다).

## 알려진 제한

1. **Python은 가시성을 강제하지 못한다** — `FeatureFacts`·`FeatureRow` 등 결과 타입의
   직접 생성을 막을 수 없다(위 (2b) 표). 강제는 `from_proto`/`build_row` 진입점 관례 +
   test뿐이다. 5C·5D 구현 시 이 경계를 우회하지 않는지 verifier가 확인해야 한다.
2. **재학습 필요** — `denominator_source` 어휘가 legacy 4값에서 wire 5값으로 바뀌어
   (D-5B-2) 범주 코드(=위치)가 달라진다. 기존 학습 아티팩트는 새 스키마와 호환되지
   않는다(D-M2-10이 이미 전제). 5C의 재학습이 이 slice의 완료 조건이 아니다.
3. **fixtures 재평가 대기** — `ml-boundary-003/004`는 2B 계약 이전 `not_covered`로
   강등됐다(digest §7). 5B가 그 명제(결측 사유 provenance·누수 차단 시그니처)를 코드로
   고정했으므로 재평가 가능해졌지만, 실제 재평가는 curator 소관(`OPEN-5B-FIXTURE-REEVAL`).
4. **legacy-behavior 라벨 test는 정답 판정이 아니다** — `test_shrinkage.py`의 legacy 산식
   대조(`n==κ`→0.5 등)는 산식 이식이 legacy와 같음을 확인하는 재현 test이지, legacy 출력을
   acceptance 기준으로 삼지 않는다(CLAUDE.md 운영자 지시). 평가 기준은 승인된 도메인 명세
   (scope.md 위협 모델·완료 조건)와 이 slice가 새로 고정한 규칙표다.
5. **`EncodingPolicy`의 κ 값 유효성 미검증** — 음수·0 κ를 넣어도 `pseudo_count_weight`가
   0으로 나누지 않는 한 예외 없이 계산된다(수학적으로 κ>0 가정). 값 검증은 out_of_scope
   (정책 값 내용은 승인 사항, 코드가 재판정하지 않는다).
6. **관측 0 은 이제 결과 타입이지만 강제되지 않는다**(verifier r1 M-2) — `build_agency_
   target_encoding([], policy=...)`은 `NoObservations`를 내 「낙찰률 0%로 수축」과 「학습
   데이터가 아예 없다」를 값·provenance 차원에서 갈랐다. 그러나 `AgencyTargetEncoding`을
   직접 생성(①과 같은 Python 가시성 한계)하면 `global_mean=0.0`인 표를 여전히 만들 수
   있다 — 강제는 5C가 `Built`/`NoObservations`를 실제로 분기하는 관례뿐이다. 5C 구현 시
   verifier 표적.
7. **`FeatureColumn.range`는 rows.py 안에서 재검증하지 않는다** — `_categorical_column`·
   `_agency_columns`는 범위를 참조하지 않고 산식으로만 값을 낸다. test가 결과값이
   schema 의 range 안에 있음을 사후 단언할 뿐, `build_row`가 범위를 벗어난 값을 능동적으로
   거부하지는 않는다(수학적으로 벗어날 수 없는 값들이라 거부 로직 자체가 불필요하다는
   판단 — `log1p`/`log10`/수축 평균 모두 정의상 그 range 안에 떨어진다).

## OPEN 갱신

- `OPEN-5B-POLICY-VALUES`: 착수 시 승인된 값(12.0·40.0)을 `SHIPPED_ENCODING_POLICY`가 그대로
  출하 — `test_shipped_encoding_policy_matches_policy_values_md`로 대조 고정. 해소 조건
  불변(5C 재학습이 튜닝 근거를 내면 갱신).
- `OPEN-5B-FIXTURE-REEVAL`: 변경 없음(curator 소관, 5B는 재평가 가능하다는 근거만 코드로
  만들었다).
- `OPEN-ML-05`: 변경 없음(5B는 κ만 정책 인자로 뽑았고, 33값 자체는 5C·5D).
- **`OPEN-5B-FEATURES-FORBIDDEN`(신설, verifier r1 L-3)**: import-linter의 DB/HTTP forbidden
  계약(`pyproject.toml`)이 `source_modules = ["ml_engine.serving", "ml_engine.inference"]`만
  겨눠 `ml_engine.features`에 `sqlalchemy`/`requests`/`lightgbm`을 심어도 통과한다(실측,
  5 kept 0 broken). `pyproject.toml`은 5A 소유 파일이라 5B out_of_scope — **5C 착수 계약이
  `features`·`training`·`evaluation`을 그 forbidden 계약의 `source_modules`에 추가**해야
  한다. 해소 조건: 5C 착수 계약에 이 OPEN을 인수 항목으로 명시.
