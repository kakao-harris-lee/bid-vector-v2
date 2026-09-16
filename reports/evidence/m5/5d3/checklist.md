# M5/5D-3 — checklist.md

## D-5D3-1~7 근거(코드 위치 — 함수명·클래스명, `file:line` 아님)

| ID | 판단 | 근거(코드) |
| --- | --- | --- |
| D-5D3-1 | 계층 매칭은 정규화 문자열 동일만 | `distribution._matches`가 `Present`끼리만 비교(둘 중 하나라도 `Missing`이면 `False`). 양쪽 축은 같은 `resolve_text_fact`(`ml_engine.features`)를 거친 문자열이다 — 분포 엔진 안에 두 번째 정규화 없음(`_resolve_segment`가 유일한 판독 호출부). |
| D-5D3-2 | 표본 축 결측 사유는 `NOT_COLLECTED_YET`만 수용, 그 밖은 표본 거부 | `distribution._resolve_segment` — `resolve_text_fact(..., allowed_missing_reasons=_ALLOWED_SEGMENT_MISSING_REASONS)`(집합 원소 하나) 거부 시 `SampleRejected(SampleRejectionReason.SEGMENT_REASON_NOT_ALLOWED)`. `distribution._observe_all`이 관측 게이트(`observe_sample`)와 세그먼트 게이트를 각각 보고 표본 하나당 한 번만 `rejected_count`에 센다. |
| D-5D3-3 | 요청 축 `Missing`이면 그 계층 매칭 불가(표본 축 값 무관) | `distribution._matches` — `isinstance(request_axis, Present)` 조건이 없으면 항상 `False`. |
| D-5D3-4 | agency = CLEAN∩agency 일치, category = CLEAN∩category 일치(agency 일치 포함), global = CLEAN 전체 | `distribution._resolve_levels` — `_matched_level`을 agency·category 각각 독립 호출(같은 `paired` 목록, 서로 다른 술어). `global_level = aggregate_level_observation(inputs.clean_levels)`(매칭 무관 전체). `agency_sample_count`/`agency_sample_below_threshold`는 `_resolve_diagnostics`가 그대로(5D-2 그대로, 인자만 실값으로 바뀜). |
| D-5D3-5 | 표본 축 전부 `Missing(NOT_COLLECTED_YET)`이면 5D-2 결과와 비트 동일 | `_matches`가 표본 축 `Missing`에 항상 `False`를 내므로 `agency_level`·`category_level`이 구조적으로 `None` — `resolve_assessment_posterior(agency=None, category=None, ...)` 호출이 5D-2와 동일 인자가 된다. `tests/inference/test_distribution.py::TestPredictDistribution::test_success_with_eight_clean_samples`(기본 세그먼트가 `_MISSING_SEGMENT`)가 5D-2 기존 단언 그대로 초록. |
| D-5D3-6 | `SampleSegment(agency: FactValue[str], category: FactValue[str])`, `SegmentMissing` 삭제, 판독은 5B `resolve_text_fact` 공개 승격 하나 | `features/facts.py::resolve_text_fact`(구 `_resolve_text_fact` 승격, `allowed_missing_reasons` 인자화) — `features/__init__.py`가 재수출. `distribution.py`의 `SampleSegment` 필드가 `FactValue[str]` 둘, `SegmentMissing` 클래스 자체가 파일에서 삭제됨(`inference/__init__.py` 재수출도 제거). |
| D-5D3-7 | 서빙 경로(wire 구동)에서 ML-04 ② 도달 | `tests/inference/test_engine.py::test_serve_bid_rates_wire_driven_direct_segment_support_matches_golden_011` — golden `ml-kernel-011` 입력의 계층 표본 수(5/10/500)·정책을 읽어 합성 `CompetitionSample` 500개를 `serve_bid_rates`에 통과시키고 `diagnostics`(segmentSupport DIRECT·agencySampleCount 5·agencySampleBelowThreshold true·shrinkageWeight 0.25)를 011 expected 와 대조. golden corpus 파일은 미편집(읽기만). |

## 위협·우회 (1)~(13) 대응표 (`_workspace/m5-5d3/02_design-review.md` 정본)

| # | 우회 | 막는 게이트/test |
| --- | --- | --- |
| (1)~(6), (8) | 5D-2 승계 항목(관측 게이트·가용성·인터벌소스·GBM 미import·정책 임계·환산 미이식·global 0) | 5D-2 checklist.md 표 그대로 — 이 slice 는 손대지 않음(회귀 test `test_success_with_eight_clean_samples`·golden 14/14로 무변경 확인). |
| (7) | agency 일치인데 category 불일치 표본이 어느 쪽에도 들거나 안 듦 | `distribution._matched_level`이 축마다 독립 필터 — `test_distribution.py::TestPredictDistribution::test_category_match_without_agency_match_is_parent_category`(agency 불일치·category 일치 4건이 category 집합에만 듦, agency_sample_count 0). |
| (9) | 요청 축 결측인데 표본이 전부 같은 값이라 「그 기관이겠지」로 추론 | `distribution._matches`가 `request_axis`도 `Present` 요구 — `test_missing_request_agency_never_matches_even_if_samples_share_a_value`. |
| (10) | 비-CLEAN 매칭 표본이 계층 집합에 편입 | 집합은 `inputs.clean_levels`(CLEAN 이후)에서만 구성 — `test_non_clean_matching_samples_do_not_enter_the_agency_set`(매칭 값을 가진 비-CLEAN 3건이 agency_sample_count에 안 들고 `excluded_observations`에만 듦). |
| (11) | 표본 축 판독 거부를 `Unmeasurable`로 올려 요청 전체를 죽임 | 표본 단위 `SampleRejected(SEGMENT_REASON_NOT_ALLOWED)` — `distribution._observe_all`이 그 표본만 빼고 `rejected_count`에 센다, `test_segment_axis_rejection_is_counted_even_if_reserve_draw_is_valid`(2건 거부돼도 요청 자체는 계속 평가되다가 availability 게이트에서 정상적으로 `Unmeasurable`). |
| (12) | 계층 표본 수를 가용성 게이트에 반영 | `distribution_availability` 호출 인자는 `len(inputs.clean_levels)`(전역 CLEAN 수)·`len(inputs.ratio_samples)` 그대로 — agency/category 표본 수를 넘기지 않는다(코드 확인, 5D-2 그대로). |
| (13) | `SegmentMissing` 삭제가 외부를 깨는가 | 착수 전 grep 실측(아래) — src 사용처는 `distribution.py` 자신뿐이었고 그 자리를 이번 구현이 대체했다. |

## (2b) 값 획득 축 — 실측

| 표면 | 실측 |
| --- | --- |
| `SampleSegment` 필드 타입 변경(`str`→`FactValue[str]`), `SegmentMissing` 삭제 | grep 재확인(아래) — 삭제 뒤 `SegmentMissing` 남은 참조는 `distribution.py` 안의 설명 주석 한 줄뿐(마커를 두지 않는다는 문면), 코드 심볼 아님. |
| `resolve_text_fact`(`features` 신규 public) | 임의 `*Fact`(`CategoryCodeFact`/`AgencyIdFact`)를 정규화된 `Present`/`Missing`/`FactRejected`로만 낸다 — 입력을 그대로 정규화할 뿐 값을 지어내지 않는다. `FactRejectionReason.MISSING_REASON_NOT_ALLOWED` 신설(열거 확장, 계수 축만). |
| `SampleRejectionReason.SEGMENT_REASON_NOT_ALLOWED`(열거 +1) | `observations.py`에 값만 추가 — `observe_sample` 자신은 이 사유를 내지 않는다(`distribution._resolve_segment`가 낸다, 사유 어휘의 단일 소유는 `observations` 모듈). |
| `DistributionRequest.from_proto` | 유일 생성 경로 그대로, 이제 표본마다 `_resolve_segment`로 세그먼트를 즉시 판독(정책 불필요한 순수 매핑이라 관측 게이트보다 먼저 끝낼 수 있다). |
| `ALL_MISSING_REASONS`(`features.facts`, `features.__all__` 재수출) | 기존 기본 동작(요청 축이 `UNSPECIFIED` 외 결측 사유를 전부 받아들이던 것)에 이름을 붙인 읽기 전용 `frozenset` — 없던 권한이 아니고 값을 나르지 않는다(verifier r1 F-5). |
| 「경계로 처리」 행 | 없음 — 매칭·집계는 전부 `distribution.py` 내부(private 함수: `_matches`·`_matched_level`·`_resolve_levels`). |

## 착수 grep 실측

명령: `grep -rn "SegmentMissing" ml-engine/src ml-engine/tests` · `grep -rn "SampleSegment\b"
ml-engine/src ml-engine/tests`(구현 전, 설계 검토 구현 지시 1). src 사용처는 `distribution.py`
(클래스 정의·`SegmentedSample.segment` 타입 주석·`from_proto` 본문)와 `inference/__init__.py`
(재수출 import·`__all__`)뿐이었고 다른 src 모듈에는 없었다 — 착수 조건 충족, 멈추지 않고
진행했다. 구현 뒤 같은 명령 재실행 — `SampleSegment`는 `distribution.py`(정의)·
`inference/__init__.py`(재수출)·`tests/inference/test_distribution.py`(단위 test)에만
남았고, `SegmentMissing`은 클래스·재수출 모두 삭제되어 남은 매치는 `distribution.py`의
설명 주석 한 줄(「별도 `SegmentMissing` 마커를 두지 않는다」, 코드 심볼 아님)뿐이다.

## 알려진 제한

1. **표본 값의 진위는 이 slice 밖** — `agency_id`/`category_code` 값이 실제 그 기관/공종을
   가리키는지는 송신 어댑터(M4) 책임(`OPEN-2B-AGENCY-ID`). 이 slice는 정규화된 문자열
   동일성만 본다.
2. **표본 중복 제거 없음**(D-2B-3, 5D-2 알려진 제한 3과 같은 갈래) — 같은 공고 표본이 두 번
   들어와도 판별 불가, 계층 매칭도 그 중복을 그대로 계층 표본 수에 반영한다.
3. **별칭·계층 사전 없음**(D-5D3-1) — "Agency A"와 "Agency A (branch)"는 다른 키다. 의도된
   범위 밖(과잉으로 뺀 항목, 설계 검토 (3)).
4. **`award_rate` 파싱 실패 사유 미분리**(5D-2 알려진 제한 9, 변경 없음) — 세그먼트 게이트와
   무관, 그대로 승계.
5. **`OPEN-5D3-SENDER-PRECONDITION`**(verifier r1 F-4) — 표본 축(`agency_id`/`category_code`)
   oneof 를 설정하지 않는 송신자(2F 이전 클라이언트, 또는 이 필드를 모르는 재구현)는 전
   표본이 `SampleRejected(SEGMENT_REASON_NOT_ALLOWED)`로 거부돼, base(5D-2)에서 `Success`
   이던 요청이 `Unmeasurable(INSUFFICIENT_SAMPLES, TOO_FEW_OBSERVATIONS)`가 된다(D-5D3-2의
   명시 결정에 따른 정상 동작이지 결함이 아니다). 현행 유일 송신자(Kotlin `RequestMapping`)는
   도메인 값이 없을 때 `MISSING_REASON_NOT_COLLECTED_YET`을 항상 설정하므로 지금 이 경로로
   깨지는 요청은 없다 — 5E(서빙 활성화) 전제로 이월한다.
6. **요청 축 결측 사유 허용 집합이 이 slice로 좁아졌다**(scope.md 계약 갱신 이력, 팀장 표적
   11) — `FeatureFacts.from_proto`(요청 축)가 이전에는 `MISSING_REASON_UNSPECIFIED`만 거부
   하고 그 밖의 정수(열거값 밖 포함)는 전부 `Missing(raw)`로 수용했다. 이제는 선언된
   `MissingReason` 값 셋(`UNKNOWN`·`NOT_APPLICABLE`·`NOT_COLLECTED_YET`, `ALL_MISSING_
   REASONS`)만 수용하고 그 밖(`UNSPECIFIED`·enum 밖 정수)은 `FactRejected(MISSING_REASON_
   NOT_ALLOWED)`다 — base 대비 `missing = 99`(enum 밖) 같은 입력의 거동이 `Missing(99)`
   수용에서 거부로 바뀐다(fail-closed 방향, 팀장이 구현을 계약으로 채택).

## 5D-2 알려진 제한 해소 등재

- **알려진 제한 1(`OPEN-5D2-SAMPLE-SEGMENT`)** — **해소.** 서빙 경로가 이제 표본 축을
  판독·매칭한다(D-5D3-1~6). golden `ml-kernel-011`의 `segment_support == DIRECT` 갈래가
  wire 구동 경로에서도 재현된다(D-5D3-7, `test_serve_bid_rates_wire_driven_direct_segment_
  support_matches_golden_011`).
- **알려진 제한 8**(「슬롯에 값을 채워도 조립기가 안 읽는다」) — **해소.** `_observe_all`·
  `_resolve_levels`·`predict_distribution`이 `segment` 슬롯을 실제로 읽고 K5 호출의
  `agency=`/`category=` 인자를 채운다(하드코딩된 `None` 제거).
- `OPEN-5D2-SAMPLE-SEGMENT` — **닫힘**(5D-2 scope.md·checklist.md는 편집하지 않는다, 이
  문서와 milestone-5.md 5D-3 종결 문단에서 참조).

## 재검증 명령

`reports/evidence/m5/5d3/commands.md` S-1~S-9 전건.
