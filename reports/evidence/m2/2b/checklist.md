# M2/2B checklist.md

## 리뷰 요청 조건 점검

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain <in_scope 17개 경로>`
      빈 출력, 양성 대조 통과(commands.md 「clean-tree 게이트」).
- [x] scope.md의 acceptance_commands(S-0~S-5) 전부 exit 0 — commands.md.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — S-1(`--no-build-cache clean
      check`)이 ktlint·detekt·ArchUnit·moduleDependencyGate를, S-2(`buf lint && buf
      build`)가 계약 형태를 각각 검증. S-3·S-4가 계약 규칙(fail-closed·oneof·3후보·
      Unmeasurable 사유 분리)을 양쪽 언어에서 증명.
- [x] 변경된 fixture와 정책 version의 근거 기록 — `contracts/testdata/prediction/*`는
      round-trip·fake servicer 표본(fixture corpus와 별개 축, scope.md in_scope 주석).
      값 근거는 `_workspace/m2-prep/01_scout_ml_interface.md`·
      `docs/discovery/data-dictionary.md` §6.5. 정책 version 신규 없음(2B는 wire 형태만
      고정 — `weight_policy_version`은 값이 아니라 참조 필드).
- [x] 알려진 제한과 rollback 기록 — 아래 「알려진 제한」·`rollback.md`(임시 clone 실측
      완료, commands.md).
- [x] secret 스캔 통과 — commands.md. evidence 디렉토리 자체 스캔은 `commands.md`가 그
      grep 패턴 문자열을 인용하는 자리 1건만 매치(실제 secret 아님 — 판독 규칙, 2A
      CLAUDE.md 이력의 developer-구간 오탐과 같은 계열).

## ①~⑨ 대응표 — scope.md 「이 slice 가 하는 일」과 그것을 증명하는 게이트/테스트

| # | 항목 | 증명 |
| --- | --- | --- |
| ① | `CalculateOptimalBid` 입력 — `PredictionEnvelope` + `FeatureInputs`(원 fact 넷) + `repeated CompetitionSample` + `OptimizationObjective`. `opened_on`·floor rate는 `FeatureInputs`에 없다 | `features.proto` `FeatureInputs`/`CompetitionSample` 정의(전자에 `opened_on` 필드 없음, `CompetitionSample`에만 존재) + `PredictionContractTest`/`test_prediction_contract.py`의 request round-trip 및 fact 테스트 |
| ② | 결측은 fact마다 사유 — `oneof { value, MissingReason missing }`, sentinel(`-1`·`0`) 없음 | `features.proto` 4개 Fact 메시지(oneof) + 양쪽 test의 「fact 미설정 거부」 4건×2(Kotlin+Python) |
| ③ | 응답 = `oneof result`(Success/Unmeasurable/ApplicationFailure), `UNTRAINED_SEGMENT`≠`INSUFFICIENT_SAMPLES` | `prediction.proto` `CalculateOptimalBidResponse` + 양쪽 test의 「Unmeasurable 두 사유는 다른 값이다」 |
| ④ | 후보는 율뿐(`Candidate{label, bid_rate, origin, weight, weight_policy_version}`) — 원 단위 가격 없음 | `prediction.proto` `Candidate` 메시지(가격 필드 없음, `int64`/`double` 없음) + testdata의 후보 3건이 전부 `Rate`(decimal string)만 나름 |
| ⑤ | 불확실성 성분 셋(`sample_size`·`dispersion`·`estimate_margin`) + `interval_source`, 합성 `confidence` 없음 | `prediction.proto` `Uncertainty` + `PriceFitness`(probability·win 없음) + 양쪽 test의 `sample_size ≥ 1` 불변식 |
| ⑥ | 모델 식별 구조화(`ModelRelease` 다섯 성분, `metric` 제외) + release 일치 규칙(exact/latest_promoted) | `prediction.proto` `ModelRelease` + 양쪽 test의 `releaseSatisfiesSelector`/`_release_satisfies_selector`(2건씩) |
| ⑦ | diagnostics 타입 있는 필드만(`training_row_count`·`segment_support`), 자유 문자열 없음 | `prediction.proto` `Diagnostics`(dict·string 자유필드 없음) |
| ⑧ | `GetModelMetadata` — 선택자 없는 요청, `promoted`+지원 schema 목록+`Readiness` | `prediction.proto` `GetModelMetadataRequest`/`Response` + 양쪽 test의 `latest_promoted` 대조 test가 이 응답을 씀 |
| ⑨ | consumer/provider fake servicer test — 3후보 고정·`Unmeasurable` 구별·release 일치·`UNSPECIFIED` 거부 | `PredictionContractTest`(Kotlin, in-process `BidPredictionServiceCoroutineImplBase`) 27건 + `test_prediction_contract.py`(Python, 직접 호출) 28건 |

## 위협 모델 「방어한다」 목록과의 대응(scope.md)

(a) 업무 판정 필드 재유입 → `features.proto`·`prediction.proto` 어디에도
`review_required`·guardrail·granularity·regime·선택 필드가 없다(out_of_scope 20필드
전부 미포함, 육안 대조). (b) `Unmeasurable` 두 사유 합침 → `UnmeasurableReason`(2A)
그대로 사용 + 양쪽 test가 값 분리를 실측. (c) 후보 수·순서 위반 → 변이 실측 1(commands.md).
(d) 합성 신뢰도·확률 이름 재유입 → `PriceFitness.score`·`Uncertainty`에 `probability`·
`confidence`·`win` 명칭 없음(grep 대조). (e) DB id 유입 → `CompetitionSample`에
식별자 필드 없음(`historical_data_id`·`project_id`·`tender_result_id` 미포함),
`agency_id`는 불투명 문자열로 주석 명시. (f) 결측 sentinel 접힘 → oneof, 변이 실측 6.
(g) 다른 release 응답 → release 일치 규칙(⑥), 변이 실측 3.

**방어하지 않는다**(scope.md 명시, 이 slice가 실측하지 않음): Kotlin 어댑터의 표본
정제·매핑 정직성(M4) · ml-engine이 실제로 `UNTRAINED_SEGMENT`를 내는가(M5 5D) ·
가중치·시나리오 값의 옳음(정책 데이터, 이 slice는 형태만) · `agency_id`의 안정성(M3).

## 우회 후보(≥5, scope.md) 대응

| # | 우회 | 막는 장치 |
| --- | --- | --- |
| (1) | `Diagnostics`에 `map<string,string>` | buf lint STANDARD가 `map` 필드를 특별히 금지하지는 않으나, 이 slice가 `map` 필드를 **아예 쓰지 않음**(정적 판독 — `grep -n "map<" contracts/proto/bidvector/ml/v1/{features,prediction}.proto` 매치 0). 실제 강제는 리뷰 항목(scope.md 명시) |
| (2) | `Success.candidates`를 1개만 | 변이 실측 1 — `hasExactlyThreeOrderedCandidates`/`_has_three_ordered_candidates`가 `false` |
| (3) | `Unmeasurable` 대신 `sample_size=0`인 `Success` | 변이 실측 5 — `isAcceptableSuccess`/`_is_acceptable_success`가 `false` |
| (4) | `release`를 요청과 다르게 | 변이 실측 3 |
| (5) | `CompetitionSample`에 `optional string source_id` 추가(호환 변경) | breaking gate 밖(2D 소관) — **알려진 제한**으로만 등재, 이 slice는 막지 않는다 |
| (6) | `OptimizationObjective` 미지 값 | 변이 실측 7 |
| (7) | `bid_rate.fraction = "87.995"` | 변이 실측 4 |

## 알려진 제한

1. **우회 (1) `map` 필드 금지는 리뷰 항목이지 게이트가 아니다** — buf lint STANDARD
   ruleset에 map 필드 자체를 막는 규칙이 없다(2A도 같은 한계, checklist.md에 등재
   안 됐던 항목). 이 slice는 정적 판독(grep 0건)으로만 확인했고, 2D가 breaking
   gate 확장 시 재검토 대상.
2. **우회 (5) `optional` 필드 추가는 breaking gate가 못 잡는다**(scope.md 명시) — 필드
   추가는 proto 호환 변경이라 2D의 breaking gate(필드 번호 재사용·제거만 탐지)가
   구조적으로 막을 수 없다. 계약에 업무 판정 필드가 `optional`로 슬쩍 추가되는 경로는
   **리뷰(사람) 항목**으로 남는다.
3. **실제 socket·deadline·cancel은 2D 몫** — 이 slice의 consumer/provider test는 전부
   in-process(Kotlin) 또는 직접 호출(Python)이다. 실제 gRPC 서버-클라이언트 소켓
   왕복·deadline 전파·취소는 검증하지 않았다(ADR 0010 D-2·D-4, scope.md 명시).
4. **`conftest.py`(2A)는 편집하지 않았다** — scope.md in_scope가
   `ml-engine/tests/test_prediction_contract.py` 파일 하나만 지정하고 `tests/**`가
   아니다. 이 slice는 새 파일 안에 독립 module fixture(`features.proto`·
   `prediction.proto` + `grpc_python_out` 생성)를 두어 범위를 지켰다 — `common_pb2`·
   `error_pb2` 생성 로직이 두 파일(`conftest.py`·`test_prediction_contract.py`)에
   중복 존재한다(작게, 각 ~15줄). 5A가 `ml-engine` 패키지 구조를 완성할 때 이 중복을
   정리할 후보(D-M2-3 인계 연장).
5. **`ReserveDrawObservation`의 개수(예비가격 15·번호 4)는 계약이 강제하지 않는다** —
   `repeated` 필드라 개수는 문서화(주석)뿐이고 wire 검증은 없다. 실제 개수 검증은
   Kotlin 어댑터(M4)·ml-engine(M5) 각자의 소관(scope.md D-2B-3 본문).
6. **`opened_on`을 ISO-8601 문자열로 선택** — `google.type.Date`(BSR 의존 신설) 대신
   자체 타입 없는 문자열을 택했다(판단 근거는 「판단이 갈린 지점」). 날짜 형식 자체의
   유효성(달력 범위 등)은 계약이 검증하지 않는다 — 파싱은 소비자 소관.
7. **`OPEN-2B-OBJECTIVE-VALUES`·`OPEN-2B-AGENCY-ID`는 이 slice가 닫지 않는다**
   (scope.md 신설 후보 그대로 인계 — 전자는 M5 5D, 후자는 M3 소관).
8. **2A `checklist.md` 알려진 제한 7(결과 봉투 oneof의 첫 실물)은 이 slice가 닫는다** —
   `CalculateOptimalBidResponse.result`가 그 실물이고, `GetModelMetadataResponse`도
   같은 패턴(`metadata`/`failure`)을 따른다(scope.md ⑧ 권고 채택).

## 판단이 갈린 지점

1. **`opened_on`을 ISO-8601 문자열로** — scope.md는 `google.type.Date`·자체
   `Date{year,month,day}`·ISO 문자열 셋을 후보로 열었다. `google.type.Date`는 BSR
   의존(`buf.build/googleapis/googleapis`)이 필요해 `contracts/buf.yaml`의 "BSR 의존이
   없다" 축을 깬다. 자체 `Date{year,month,day}` 메시지도 동등한 후보였으나, 날짜
   하나만 필요한 이 자리에 새 메시지 타입을 만드는 것보다 `Rate.fraction`과 같은
   문자열 표현 관례(scale 보존 decimal string)를 날짜에도 그대로 적용하는 편이
   testdata JSON 작성·round-trip에서 더 단순했다.
2. **`Weight`를 `Rate`와 별도 메시지 타입으로** — scope.md 지시문의 "Weight weight
   (decimal string)"라는 표기(대문자 타입명 + 소문자 필드명, `Rate bid_rate`와 같은
   패턴)를 따라 `message Weight { string fraction = 1; }`을 신설했다. `Rate`를 그대로
   재사용하지 않은 이유는 `common.proto`의 관례("율의 축은 필드·메시지 이름이
   나른다")를 따르면 `Weight`는 투찰율 축이 아니라 시나리오 혼합 계수 축이라 다른
   타입으로 구분하는 것이 교차 대입을 막는다고 판단했기 때문이다. `dispersion`·
   `estimate_margin`·`score`는 scope.md가 소문자 "string"으로 명시해 그대로 plain
   string 필드로 두었다(비대칭 처리 근거).
3. **`CompetitionSample.origin`을 명시 필드로 유지** — `observed_bid_rate`라는 이름
   자체가 축을 암시하지만, `Candidate.origin`과 대칭을 이루도록(2A D-2A-7 관례) 명시
   필드를 두었다. 상수 값(`BID_RATE_ORIGIN_OBSERVED`)이라 계약이 검증하지 않으면
   무의미해질 수 있다는 우려가 있었으나, 리뷰 항목으로 남기고 필드는 유지했다.
4. **`GetModelMetadataResponse`에 `oneof result { metadata, failure }` 채택** —
   scope.md가 "권고"로 제시한 형태를 그대로 따랐다(`Unmeasurable` 가지 없음 — 메타데이터
   조회는 측정 불가인 도메인 결과가 없다는 판단).

## 커밋 목록

**Phase 3 구현**(base `55ecdcc`) — 순서대로: `cb5c612`(`features.proto`·
`prediction.proto` — BidPredictionService 계약, buf lint/build 확인) · `07af74f`
(`PredictionContractTest` 27건 + `contracts/testdata/prediction/` 6쌍, gate-tests.properties
등재) · `fd98ccf`(`test_prediction_contract.py` 28건) · `2ce838b`(ktlint/detekt 정리 —
줄 길이·when 분기 스타일, `--no-build-cache clean check` 재확인).

이 range의 나머지 커밋(`8cc7e19` — M3/M4/M5 준비 문서 리뷰 반영)은 병행 레인 산출물이며
commands.md 「하네스 레인 변경」 절이 사유를 갖는다.

## 사용자 승인 대기

verifier 검증 전. 운영자 지시(CLAUDE.md 2026-09-04)에 따라 이 slice는 코드 slice이므로
Codex 리뷰 대상이 아니다 — 완료 조건은 **verifier ready-for-review + 사용자 승인**이다.
