# M5/5D-2 — checklist.md

## verifier r1 반영 (2026-09-15, 재작업 1/5, 계약 갱신 `fb4e001`)

**F-1(high, 산출물 — 차단, 수정)** `observe_sample`이 `observed_bid_rate.fraction`을
`Decimal()`로 무관문 호출해 미설정(`""`)·비수치 문자열에서 `decimal.InvalidOperation`이
`serve_bid_rates` 밖으로 새던 결함. `parse_rate(fraction) -> Decimal | None`(신규,
빈/비수치/비유한 거부) 신설, `observed_bid_rate`·`award_rate`(optional, 있으면) 둘 다
이 관문을 거친다. 신규 사유 `BID_RATE_UNPARSEABLE`. 재현 둘(`ClearField`·`"abc"`) +
`"NaN"`·`"Infinity"`·`"-Infinity"`·`"-0.5"`(파싱은 되지만 밴드 밖 → `BID_RATE_OUT_OF_BAND`,
`UNPARSEABLE`이 아님을 구별) 전부 test 로 고정. `parse_rate`는 `<=0`·`>1` 자체를 거부
사유로 삼지 않는다 — 파싱 가능한 유한값은 기존 `policy.bid_ratio_plausible_*`(0.5~1.5)
밴드 판정에 그대로 넘겨(단일 판정 경로 유지, 상한을 두 곳에서 따로 재정의하지 않는다).

**F-2(medium, 산출물, 수정)** `reserve_prices`의 각 `Money`가 `amount_won > 0`만 보던
것을 `_validate_money_amount`(base_amount 와 같은 4성분 규칙 — basis·currency·
provenance·amount_won, 함수 하나 공유로 중복 금지)로 확장했다. 사유 `NON_POSITIVE_PRICE`
를 `RESERVE_PRICE_INVALID`로 개명·포섭(amount_won>0 도 그 규칙의 일부). test 3종
(basis·currency·provenance 위반 각 1).

**F-4(low, 장부, 수정)** golden `ml-kernel-011` test 자신의 docstring에 「서빙 경로를
거치지 않는다」 명시(이전에는 `distribution.py`·checklist 에만 있었다).

F-3·F-5·F-6은 등재 확인만(코드 변경 없음, 아래 알려진 제한·reuse.md 참고). F-7은
rollback.md 갱신(별도 커밋). F-8은 알려진 제한 1에 한 줄 보강(아래).

## 계약 고정 결정 근거 (D-5D2-1~8)

| ID | 근거(코드 위치) |
| --- | --- |
| D-5D2-1 (b) 분포 단독 | `engine.py::serve_bid_rates` 하나뿐 — `predict_bid_rates`(GBM)를 import하지 않는다. `test_engine.py::test_engine_module_does_not_import_gbm_predict_module`(AST)·`test_engine_module_does_not_have_predict_in_sys_modules_dependency`(런타임) 둘 다 고정. |
| D-5D2-2 | `engine.py`의 `ENGINE = "DISTRIBUTION"` 코드 상수 — 정책 파일에 선택 축 없음. |
| D-5D2-3 | `policy.py`에 `assessment.agency_sample_threshold` 필수 키 신설(`_KNOWN_KEYS`·`_POSITIVE_THRESHOLD_KEYS`), 값은 출하 YAML 에 넣지 않았다 — `test_policy.py::test_shipped_policy_file_is_rejected_missing_agency_sample_threshold`가 그 자체를 고정(`PolicyRejected`, reason 에 키 이름 포함). |
| D-5D2-4 | `distribution.py::predict_distribution` — `sample_size = len(clean_levels)`(clean 관측 행 수), `dispersion = pstdev(ratio_samples)`, `estimate_margin = z·predictive_std/√sample_size`(§6.5 반폭). |
| D-5D2-5 | `results.py::DistributionRelease`(내부 타입, `Success.release`에 대응하지 않음) — `test_results.py::test_distribution_release_is_frozen_and_has_no_wire_mapping`. |
| D-5D2-6 | `bid_ratio = median(observed_bid_rate_i/center_i)`(`distribution.py`) — `bid_to_assessment_ratio` 환산 함수 자체가 코드베이스에 없다(grep 으로 확인 가능한 부재). |
| D-5D2-7 | `availability.py::distribution_availability` — 가용성 조회·조립기(`predict_distribution` 첫 단계)가 **같은 함수 객체**를 호출. `test_availability.py::test_direct_call_with_one_sample_is_rejected_same_as_gate`. |
| D-5D2-8 | `results.py::IntervalSource.POSTERIOR_PREDICTIVE`(내부 전용, wire 매핑 없음) — `distribution.py`가 `resolve_uncertainty`에 이 값을 고정 전달. |

## (2b) 값 획득 축 — 실측

| 표면 | 실측 |
| --- | --- |
| `serve_bid_rates`·`predict_distribution` | 진입점 둘, `engine.py`가 `distribution.py`를 부른다(그 반대 없음) |
| `DistributionRequest.from_proto` | 유일 생성 경로(컨벤션) — `base_amount`/`agency`/`category`는 5B `FeatureFacts.from_proto` 검증을 거친 `FactValue`이고, 조립기는 셋 다 소비하지 않는다(OPEN-5D2-SAMPLE-SEGMENT, 아래) |
| `SegmentedSample`·`SampleSegment`·`SegmentMissing` | 신규(계약 갱신 `623baa1`) — `from_proto`가 표본마다 `SegmentMissing()`을 낸다. 직접 `SampleSegment(...)`를 만들 생산 경로가 코드에 없다(test 전용 컨벤션과 같은 갈래, Python 가시성 한계) |
| `Diagnostics.agency_sample_count`·`agency_sample_below_threshold` | `distribution._resolve_diagnostics`(private) 하나만 채운다 — GBM 경로(`predict._direct_diagnostics`)는 상수 `0`/`False` |
| `IntervalSource.POSTERIOR_PREDICTIVE` | `distribution.py`가 `resolve_uncertainty` 호출 시 고정 전달 — 다른 값을 대입하는 경로 없음(grep 확인) |
| `observations.parse_rate` | verifier r1 F-1 신설 — 저장소에서 wire `Rate.fraction`을 파싱하는 유일한 자리(재수출은 안 함, `observations` 모듈 내부 소비만) |
| `SampleRejectionReason.{BID_RATE_UNPARSEABLE,RESERVE_PRICE_INVALID}` | verifier r1 F-1·F-2 신설(F-2 는 `NON_POSITIVE_PRICE` 개명·포섭) — 총 7→8 사유, `test_observations.py::test_rejection_reasons_are_eight` |

## 위협 모델 우회 후보(1)~(8) 대응표

| # | 우회 | 막는 게이트/test |
| --- | --- | --- |
| (1) | `SampleRejected`를 `continue`로 접고 계수 안 함 | `distribution._prepare_estimation_inputs`가 `rejected_count`를 명시 누적, `test_distribution.py::test_rejected_samples_are_excluded_and_counted` |
| (2) | 표본 1건으로 조립기 직접 호출 | `distribution_availability`(D-5D2-7), `test_availability.py::test_direct_call_with_one_sample_is_rejected_same_as_gate` |
| (3) | `interval_source`에 wire 값 대입 | `distribution.py`가 `IntervalSource.POSTERIOR_PREDICTIVE` 상수만 전달(하드코딩, 조건 분기 없음) |
| (4) | GBM `predict_bid_rates` import | `test_engine.py`의 AST·런타임 두 test(위 D-5D2-1 근거) |
| (5) | `agency_sample_threshold` 기본값 | 로더가 `_KNOWN_KEYS` 미선언 → `PolicyRejected`(기본값 코드 없음) |
| (6) | `bid_ratio`를 legacy 환산으로 | `bid_to_assessment_ratio` 함수 자체가 코드베이스에 없음(grep 0건) |
| (7) | `center` 밴드 밖을 clamp | `observations.py::_resolve_reserve_draw`가 `CENTER_OUT_OF_BAND`로 거부(clamp 없음), `test_observations.py::test_center_out_of_band_is_rejected` |
| (8) | 3계층 중 global 0 | `resolve_assessment_posterior`(K5, 5D 기존)가 `NO_GLOBAL_SAMPLES` — 이 slice 에서는 `distribution_availability`가 관측 수 ≥8 을 먼저 보장해 global 이 항상 채워진다(구조적으로 도달하지 않음, 아래 알려진 제한) |

## 5D-2 고유 우회 후보(9)~(13, 설계 검토) 대응

| # | 우회 | 채택 |
| --- | --- | --- |
| (9) | 같은 표본 중복 유입 | 채택하지 않음(중복 제거 없음) — wire 가 식별자를 나르지 않아 판별 불가(D-2B-3, 알려진 제한) |
| (10) | `observed_bid_rate` 문자열→float 정밀도 | `observations.py::observe_sample`이 `Decimal(fraction)`으로 읽고 float 변환은 한 번(`test_observed_bid_rate_is_read_via_decimal_not_bare_float`) |
| (11) | `base_amount` basis 상이 | `_validate_money_amount`가 매 표본 basis·currency·provenance·amount_won 넷을 검증(5B 규칙 재현) — verifier r1 F-2 뒤 `reserve_prices`의 각 `Money`에도 같은 함수로 적용 |
| (12) | agency/category 정규화 | OPEN-5D2-SAMPLE-SEGMENT 로 wire 축 자체가 없어 이 slice 에서 적용 대상이 없음(알려진 제한) |
| (13) | `center_i` 0 → 나눗셈 폭발 | `CENTER_OUT_OF_BAND`가 밴드(0.8~1.2)로 0 을 선차단 — `bid_ratio` 계산 전에 이미 거부됨 |

## 알려진 제한

1. **`OPEN-5D2-SAMPLE-SEGMENT`(계약 갱신 `623baa1`, 2026-09-15)** — M2 wire `CompetitionSample`
   에 표본별 기관·공종 축이 없다. 조립기는 **global 레벨만** 채우고 agency/category
   레벨은 항상 `None`(`segment_support`는 항상 `GLOBAL`, `agency_sample_count`는 항상
   `0`, `agency_sample_below_threshold`는 항상 `true`). ML-04 ②(기관 표본 임계 미만
   시 수축 가중치 노출)는 **서빙 경로에서 도달 불가** — golden `ml-kernel-011`이 K5
   (`resolve_assessment_posterior`)와 `distribution._resolve_diagnostics`를 직접 호출해
   검증한다. `DistributionRequest.samples`에 `segment: SampleSegment | SegmentMissing`
   슬롯을 미리 두어(항상 `SegmentMissing`) M2 2F(`CompetitionSample.agency_id`·
   `category_code` 추가)가 오면 조립기 시그니처 변경 없이 소비를 **시작할 수 있는 자리를
   만들어 둔다** — 그 자리에 값이 오면 자동으로 3계층이 켜진다는 뜻은 아니다(아래
   알려진 제한 8, verifier r1 F-8).
2. **`ratio_sample_count == observation_count` 항상 성립** — `realized_assessment_ratio`
   (진단용, `observations.py`)이 이 slice의 `bid_ratio` 산식(`observed_bid_rate_i/
   center_i`)에 소비되지 않아(design review 결정 (1) 「현재 미사용」), 모든 CLEAN 관측
   행이 그대로 비율 표본이 된다. `distribution_availability`의 두 임계(관측 수 8·비율
   표본 수 3) 중 관측 수가 항상 먼저/유일하게 binding — `TOO_FEW_RATIO_SAMPLES`는
   `availability.py`의 단위 test 로만 도달 가능하고, `predict_distribution` 파이프라인
   에서는 구조적으로 도달하지 않는다.
3. **표본 중복 제거 없음** — wire `CompetitionSample`에 식별자가 없다(D-2B-3, 5D-2 설계
   검토 우회 (9)). 같은 공고 표본이 두 번 들어와도 판별할 수 없다.
4. **출하 정책 파일의 `agency_sample_threshold` 값 미정**(`OPEN-5D2-POLICY-VALUES`,
   운영자 결정 2026-09-13 (c) — 5C 재학습 지표 뒤). 서빙(5E)이 켜지려면 그 전에 값이
   승인돼 `inference-v1.yaml`에 반영돼야 한다 — 지금 이 값 없이는 로더가 shipped YAML
   을 항상 거부한다(의도된 fail-closed, `test_policy.py`가 고정).
5. **`ModelRelease` wire 매핑 미정**(`OPEN-5D2-RELEASE-FOR-DISTRIBUTION`) — `Success.
   release`는 이 slice 의 Python 결과 타입에 없다(2B `prediction.proto`의 `Success.
   release`는 5E/2F 가 `DistributionRelease`에서 옮겨 채운다).
6. **`interval_source` wire 매핑 미정**(`OPEN-5D2-INTERVAL-SOURCE-WIRE`) — wire
   `IntervalSource`에 `POSTERIOR_PREDICTIVE`에 대응하는 값이 아직 없다(2F additive).
7. **origin(`BidRateOrigin`) 필드 미검증** — scope.md ①의 명시 관문 목록에
   `origin == BID_RATE_ORIGIN_OBSERVED` 검증이 없다. proto 주석은 "항상 OBSERVED"라고
   적지만 이 slice 는 그 불변식을 강제하지 않는다(Kotlin 송신측 책임으로 남김).
8. **`SegmentedSample.segment`에 `SampleSegment`를 손으로 채워도 3계층 경로는 동작하지
   않는다**(verifier r1 F-8, 실측) — `distribution.py`의 어떤 함수도 `segment` 슬롯을
   읽지 않는다(`_observe_all`이 `segmented.sample`만 꺼내고, K5 호출은 `agency=None,
   category=None`이 하드코딩돼 있다). 그 결과 슬롯에 실제 값을 넣어도 산출은
   global-only 와 완전히 같다. M2 2F 가 wire 축을 추가한 뒤, 이 조립기의 본문(`_clean_
   statistics`·`predict_distribution`의 `resolve_assessment_posterior` 호출부)을 직접
   바꿔야 3계층이 켜진다 — 알려진 제한 1의 「소비를 시작할 자리」는 **배선 지점**이지
   **동작하는 기능**이 아니다.

## 재검증 명령

`reports/evidence/m5/5d2/commands.md` S-1~S-9 전건. 결과: 전부 exit 0, 402 passed
(4 deselected legacy_parity), golden 14/14 skip 0.
