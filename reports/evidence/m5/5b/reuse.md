# M5/5B — 재활용 출처 (ADR 0009 D-6 오른쪽 자리)

> 왼쪽 자리(모듈 옆 최소 포인터)는 각 모듈 docstring 첫 줄 `Reuse: <원본 경로>@<commit>`.
> 이 표는 그 이식이 **수행한 수정·튜닝 내역**을 담는다(D-6.1 #3). 조인 키는 `module`
> (저장소 루트 기준 POSIX 경로) — `ml-engine/tools/reuse_provenance_check.py`(S-7)가
> 이 표와 docstring 포인터를 대조한다. `schema.py`·`facts.py`·`manifest.py`·`vocabulary.py`는
> 신규 작성(포인터 없음 — 조사 §c-5 함의: "「versioned feature schema + checksum」은 이식이
> 아니라 신규 작성")이라 이 표에 행이 없다(S-7은 포인터가 있는 모듈만 대상으로 한다).

| module | original_path | commit | 수행한 수정·튜닝 |
| --- | --- | --- | --- |
| ml-engine/src/ml_engine/features/normalize.py | bid-vector/app/utils/textfmt.py | ed4b06c | `normalize_lookup_key(value, aliases)`에서 별칭 해소 부분을 걷어내고 시그니처를 `str \| None → str`에서 `str → str`로 좁혔다(D-5B-9). `None` 처리는 이 함수에 닿기 전에 `facts.py`가 `Missing` 타입으로 이미 갈라내고, 정규화 후 빈 문자열 판정(`FactRejected.EmptyKey`)도 이 함수가 아니라 호출부 책임이다 — 이 함수는 `strip().lower()`만 한다. |
| ml-engine/src/ml_engine/features/shrinkage.py | bid-vector/app/domain/assessment_shrinkage.py | ed4b06c | `pseudo_count_weight`·`shrink_toward` 원시 연산 둘만 가져왔다. legacy 파일의 3계층 `resolve_assessment_posterior`(사정률 축 전용 조립 — 분산 혼합·`AssessmentPosterior` 등)와 그 축 전용 κ 상수(`AGENCY_PRIOR_STRENGTH=12.0`·`CATEGORY_PRIOR_STRENGTH=40.0`, 사정률 축 값)는 가져오지 않았다 — 낙찰률 축 κ는 `features/encoding.py`의 `EncodingPolicy`가 별도로 선언한다(legacy 관례 계승, 두 축의 κ를 혼용하지 않는다). |
| ml-engine/src/ml_engine/features/encoding.py | bid-vector/app/domain/award_rate_features.py | ed4b06c | `AgencyTargetEncoding`·`AwardRateObservation`·`build_agency_target_encoding`·`_ObservationTotals`·`_collect_totals`·`_shrunk_category_means`·`_shrunk_agency_means`·`_accumulate`를 가져왔다. 수정: (1) κ 둘의 기본값을 제거하고 `EncodingPolicy` 필수 인자 하나로 외부화(D-5B-7, legacy는 모듈 상수를 함수 기본값으로 썼다). (2) 정규화(`normalize_feature_key`)를 이 모듈에서 제거 — `facts.py`가 이미 정규화된 키를 낸다(관심사 분리). (3) `encode`의 `category` 인자를 `str \| None`으로 넓혀 category 결측 시 falsy→빈문자열 접힘 없이 전역 평균으로 직행하게 함(legacy는 `normalize_feature_key(None)==""`로 우연히 폴백시켰다 — 그 접힘을 제거하고 타입으로 같은 결과를 낸다). `build_row`·`AwardRateFeatureSpace`·`category_training_rows`·`_vocabulary_code`·상수(`UNKNOWN_CATEGORY_CODE`·`SERVING_DENOMINATOR_SOURCE`·`AWARD_RATE_*_PRIOR_STRENGTH`·`_MIN_FEATURE_AMOUNT`·`_NO_ALIASES`)는 이 파일이 아니라 `rows.py`·`vocabulary.py`·`SHIPPED_ENCODING_POLICY`로 갈라 옮겼다(아래 rows.py 행 참조). |
| ml-engine/src/ml_engine/features/rows.py | bid-vector/app/domain/award_rate_features.py | ed4b06c | `AwardRateFeatureSpace.build_row`(조립 단일 지점 원칙)를 이어받되 입력·출력을 전부 바꿨다. (1) 입력이 원시 값(`amount: float`·`category: str \| None` 등)이 아니라 검증된 `FeatureFacts` 하나 — `build_row`는 `Money` 성분을 다시 보지 않는다. (2) 반환값이 `tuple[float, ...]`가 아니라 `FeatureRow \| RowRejected` — 기초금액·분모 출처 결측은 행 자체가 거부된다(legacy는 `max(amount,1.0)`·빈 문자열 분모로 조용히 접었다). (3) 어휘 밖 코드는 `sentinel -1.0`이 아니라 `NaN` + `OutOfVocabulary` provenance(`vocabulary.py`로 이동). (4) `agency` 결측(wire Missing)과 미관측(어휘엔 있으나 표본 0)을 구별 — legacy는 둘 다 `log1p(0)=0.0`으로 겹쳐 표현했으나 V2는 전자를 NaN, 후자를 `Observed`인 진짜 `0.0`으로 가른다. `category_training_rows`(서빙 가용성 판정)는 이식하지 않았다 — 5D(serving) 소관. |
