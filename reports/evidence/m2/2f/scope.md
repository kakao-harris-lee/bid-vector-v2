# Slice 계약 — M2 / 2F · v1 additive 묶음(5D·5D-2 wire 인수 다섯) — **착수 계약 2026-09-15**

> **지위**: 운영자 결정 2026-09-15(「2F 추천으로 진행」 — D-2F-1 (a)·D-2F-2 (b)·D-2F-3 (i)·D-2F-4 (a)). M2 완료 뒤 두 번째 additive 후속 slice(첫째는 2E). 5D·5D-2 가 wire 에 없어
> 내부 타입으로 둔 것을 `bidvector.ml.v1` 안에서 **추가만**(ADR 0010 D-7 — breaking 은 `v2` 패키지, 같은 패키지에서는 필드·enum 값 추가만)으로 올린다.
> 조사 digest `_workspace/m2-2f/01_digest.md`(2E 절차·현 proto 번호·breaking 실측·Kotlin/Python 소비자·승인 문면·OPEN 정본). 세션 모델 단독 작성. **Phase 2.5 설계 검토 대상**(계약형): `_workspace/m2-2f/02_design-review.md`.
> **breaking 실측(조사, scratchpad 사본)**: 네 additive 를 한꺼번에 넣어 `buf breaking --against contracts/v1-approved-2026-09-10` exit 0·finding 0(`FILE` 카테고리 — buf.yaml 에 `breaking:` 절 없음 = 기본 최엄격). 2E 위협 F-1(신설 파일 무방비)은 2F 에 없다 — 두 proto 다 기준선에 있다.
> 레인: worktree `bid-vector-v2-m4e`, 브랜치 `m2-2f/2026-09-15`. 겹침 축: `gate-tests.properties`(`gate.tests.adapters`)·`capability-map.md`·`milestone-2.md`.

```yaml
milestone: m2
slice: 2f-v1-additive-inference-bundle
base_sha: c669a71affabed165c1afbd8b11c8245b81a3a5a   # PR #14 머지 커밋 = origin/main(5D·5D-2 실물 포함)
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — 값을 박지 않는다.
branch: m2-2f/2026-09-15
in_scope:
  - contracts/proto/bidvector/ml/v1/features.proto        # CompetitionSample: `AgencyIdFact agency_id = 8`·`CategoryCodeFact category_code = 9`(D-2F-1) + D-2B-8 축 주석(D-2F-4)
  - contracts/proto/bidvector/ml/v1/prediction.proto      # Diagnostics: `Weight shrinkage_weight = 3`·`uint32 excluded_observations = 4`·`uint32 agency_sample_count = 5`·`bool agency_sample_below_threshold = 6` · IntervalSource: `INTERVAL_SOURCE_POSTERIOR_PREDICTIVE = 3` · ModelRelease: `ReleaseKind release_kind = 6` + enum `ReleaseKind { UNSPECIFIED=0; ARTIFACT=1; DERIVED=2 }` + DERIVED 규약 주석(D-2F-2) · Candidate.bid_rate 주석(D-2F-4)
  - contracts/testdata/prediction/**                      # 표본 5쌍 JSON 갱신(표본 축·diagnostics 넷·release_kind·POSTERIOR_PREDICTIVE 표본 1) + `buf convert` 로 binpb 재생성·왕복
  - adapters/src/main/kotlin/bidvector/adapters/ml/ParsedSuccessFields.kt   # IntervalSource 소진 when — 유일 컴파일 깨짐 자리(D-2F-3 (i))
  - adapters/src/main/kotlin/bidvector/adapters/ml/ReleaseShapeValidation.kt # hasNonBlankRelease → release_kind 분기: ARTIFACT 는 다섯 비공백, DERIVED 는 dataset_id 공백 허용·나머지 넷 비공백, UNSPECIFIED/UNRECOGNIZED 는 거부(게이트 술어 변경 — verifier 표적)
  - adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt        # CompetitionSample.toProto() 에 표본 축 둘(도메인 필드 → Fact)
  - workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionOutcome.kt   # IntervalSource.PosteriorPredictive · ModelReleaseRef 에 kind(ARTIFACT|DERIVED) — init: DERIVED 면 datasetId 공백 허용
  - workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionRequest.kt   # CompetitionSample 도메인에 agencyId: AgencyId?·categoryCode: CategoryCode?(결측 = Fact missing NOT_COLLECTED_YET)
  - adapters/src/test/kotlin/bidvector/adapters/contract/**                 # 신설 단언: diagnostics 넷·interval_source 3값·표본 축·release_kind(ARTIFACT/DERIVED/UNSPECIFIED 거부)·미지 필드 보존(999) — gate.tests.adapters 등재
  - adapters/src/test/kotlin/bidvector/adapters/ml/**                       # ReleaseShapeValidation·ParsedSuccessFields 회귀 test
  - ml-engine/tests/test_prediction_contract.py                            # Python 쪽 같은 단언(현재 diagnostics·interval_source 단언 0)
  - config/quality/contract-policy.properties                              # 종결 승인 커밋에서 approved.tag → contracts/v1-approved-<date>, policy.version 3→4
  - config/quality/gate-tests.properties · milestone-2.md(2F 절) · docs/discovery/capability-map.md(OPEN 표) · reports/evidence/m2/2b/scope.md(D-2B-8 갱신 이력) · reports/evidence/m2/2f/**
out_of_scope:
  - ml-contract/**(생성물 VCS 밖) · contracts/tools/breaking-mutations.sh(무변경 — 내용 앵커 sed, 2F 신설 파일 없음) · Python wire↔내부 매핑(5E) · 3계층 소비 라운드(5D-3) · Kotlin gateway 의 Diagnostics 도메인 소비(읽는 코드 0 — 후속) · 정책 값 · 4D-1 `MlCallPolicyData` 변경
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0 — 격리 트리(종결 승인 커밋에서는 태그 부재로 미실행 — 태그 뒤 팀장 재실행)
  - "./gradlew --no-build-cache --no-daemon clean check"                                              # S-1 — contractGate 포함(같은 조건)
  - "(cd contracts && buf lint && buf build)"                                                        # S-2
  - "(cd contracts && ./tools/breaking-mutations.sh)"                                                # S-3 — 양성 대조(태그 부재 시 미실행)
  - "./gradlew --no-daemon :adapters:test --tests '*Contract*'"                                       # S-4
  - "(cd ml-engine && uv run python -m pytest tests/test_prediction_contract.py tests/test_contract_roundtrip.py -q)"   # S-5
  - "./tools/contract-crosslang-smoke.sh"                                                             # S-6
  - "./gradlew --no-daemon :adapters:gateExecutionGate :workflow:gateExecutionGate"                   # S-7
  - "./gradlew qualityBaseline"                                                                        # S-8
  - "(cd contracts && buf breaking --against '../.git#tag=contracts/v1-approved-2026-09-10,subdir=contracts')"   # S-9 — 현 승인 태그 대비 breaking 0(태그 신설 전 정본 증거)
rollback: |
    **정본 `reports/evidence/m2/2f/rollback.md`**(구현 레인). proto 둘·testdata·adapters/workflow 편집 파일·test 를 base 로 restore(신설 파일 삭제). 공유 파일(`gate-tests.properties`·`capability-map.md`·`milestone-2.md`·`contract-policy.properties`) hunk 격리(`--3way` 실패 시 수동 절차). 문서 커밋과 목록 커밋 분리. 임시 clone 실측 → in_scope diff 0 → `clean check`(태그 갱신 전이라 approved.tag 원값) 초록.
```

작성: 2026-09-15, 세션 모델 단독. 근거: `milestone-2.md` additive 규정·완료 조건 · ADR 0003 D-6(번호 재사용 금지·breaking 은 versioning) · ADR 0010 D-7(같은 패키지 추가만·enum 값 추가는 **제공자 먼저 배포**)·A-6(호환성 축 분리) · 2B D-2B-3(식별자 없음)·D-2B-6(같은 이름 다른 뜻 금지)·D-2B-8·⑥(`ModelRelease` 다섯)·위협 우회 (5) · `data-dictionary.md` §6.5 · `capability-map.md` ML-04 ②·`OPEN-2B-AGENCY-ID` · 5D D-5D-7 · 5D-2 D-5D2-5·8·계약 갱신 이력 ④·`OPEN-5D2-BID-RATE-UPPER` · 2E scope(절차·병합 결정 절).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시 없음. 리뷰 요청 시점 재실행.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **표본 축**(`features.proto`) — `CompetitionSample` 에 `AgencyIdFact agency_id = 8`·`CategoryCodeFact category_code = 9`(기존 `oneof { value, missing }` 메시지 재사용). 주석: 「식별자가 아니라 fact(D-2B-3 유지 — 표본 식별·중복 제거는 여전히 불가), 값의 정본은 `FeatureInputs` 의 같은 fact 와 동일(`OPEN-2B-AGENCY-ID` M3), 허용 `MissingReason` 은 `NOT_COLLECTED_YET` 하나(송신 어댑터가 사유를 지어내지 않는다)」. 2B 위협 우회 (5) 가 지목한 자리임을 명시 | `OPEN-5D2-SAMPLE-SEGMENT` · ML-04 「3계층 수축」 · D-2B-3 |
| ② | **Diagnostics 넷**(`prediction.proto`) — `Weight shrinkage_weight = 3`(주석: `Weight` KDoc 「시나리오 혼합 계수 축」을 「fraction 형태의 [0,1] 가중치 일반」으로 **확장** — 5D `Decimal` 가중치와 형태 일치) · `uint32 excluded_observations = 4`(총계) · `uint32 agency_sample_count = 5` · `bool agency_sample_below_threshold = 6`. D-2B-6 유지: `training_row_count` 는 아티팩트 학습 행 수(DERIVED 는 0) | `OPEN-5D-DIAGNOSTICS-WIRE` · ML-04 ② 「수축 가중치가 응답 근거에」 |
| ③ | **`IntervalSource.INTERVAL_SOURCE_POSTERIOR_PREDICTIVE = 3`** — 주석: 「사후예측분산 기반(분포 엔진) — 잔차 기반 두 값과 다른 축; §6.5 에 정의 없음 → `data-dictionary.md` 는 2F 가 갱신하지 않고 알려진 제한 등재(문서 소유 M0/discovery — 후속)」. **제공자 먼저 배포**(ADR 0010 D-7 — enum 값 추가는 수신 fail-closed 때문에 순서 필요) 를 milestone-2 2F 절에 명시 | `OPEN-5D2-INTERVAL-SOURCE-WIRE` · ADR 0010 D-7 |
| ④ | **`ModelRelease.release_kind`**(D-2F-2 (b)) — enum `ReleaseKind { RELEASE_KIND_UNSPECIFIED = 0; RELEASE_KIND_ARTIFACT = 1; RELEASE_KIND_DERIVED = 2; }`, 필드 `ReleaseKind release_kind = 6`. 규약 주석: ARTIFACT — 다섯 전부 비공백(기존) · DERIVED(아티팩트 없는 엔진) — `release_id = "distribution/<inference policy version>"`·`artifact_checksum = "sha256:" + sha256(정규화 정책 YAML bytes)`·`feature_schema_version = 요청 envelope 값 에코`·`code_version = ml-engine 패키지 version`·`dataset_id = ""`(DERIVED 에서만 허용) · UNSPECIFIED 는 수신 거부(2A ⑥ fail-closed). **기존 표본·수신자 호환**: 미설정(=0) 은 기존 응답이 보낼 값 → 수신자는 「UNSPECIFIED 거부」를 **제공자 배포 뒤** 켠다(과도기 규칙: 2F Kotlin 은 UNSPECIFIED 를 ARTIFACT 로 읽지 않고 **거부**하되, ml-engine 5E 가 `release_kind` 를 항상 채우므로 실제 충돌은 없음 — testdata 5쌍 전부 `release_kind` 명시) | `OPEN-5D2-RELEASE-FOR-DISTRIBUTION` · 2B ⑥ · 5D-2 위협 (h) 값 위장 금지 |
| ⑤ | **D-2B-8 축 한정**(D-2F-4 (a)) — `features.proto` `CompetitionSample.observed_bid_rate` 주석: 「과거 표본의 **관측값** — `> 1` 허용(100% 초과 투찰 실존), 엔진 정책 밴드(5D `bid_ratio.plausible`)가 정제; D-2B-8 의 `> 1 = INVALID_REQUEST` 는 **대상 공고 요청·응답 후보율 축**(`FeatureInputs`·`Candidate.bid_rate`)에 한정」 + `prediction.proto` `Candidate.bid_rate` 주석에 같은 축 문면 + `reports/evidence/m2/2b/scope.md` 갱신 이력 행. 5E 요청 검증이 이 구분을 따른다 | `OPEN-5D2-BID-RATE-UPPER` · D-2B-8 |
| ⑥ | **Kotlin 소비자** — `ParsedSuccessFields` `when` 에 `POSTERIOR_PREDICTIVE -> DomainIntervalSource.PosteriorPredictive`(도메인 enum 값 추가, D-2F-3 (i)) · `ReleaseShapeValidation.hasNonBlankRelease` → `hasValidReleaseShape(release)`: kind 별 판정(게이트 술어 변경) · `ModelReleaseRef` 에 `kind`(도메인 sealed 또는 enum) + init 이 DERIVED 면 `datasetId` 공백 허용 · `RequestMapping.toProto()` 표본 축 둘(도메인 `CompetitionSample.agencyId: AgencyId?`·`categoryCode: CategoryCode?` → 있으면 `value`, 없으면 `missing = NOT_COLLECTED_YET`) · `Diagnostics` 는 읽지 않음 유지(후속) | 2A ⑥ 제3 변환 금지 · 4D-1 `ReleaseCheck` 대조 규칙(`release_id`+`checksum` 둘) 불변 |
| ⑦ | **testdata·계약 test** — 표본 5쌍 갱신(요청: 표본에 축 둘 — 값 표본과 missing 표본 각 1 · 응답: diagnostics 넷·`release_kind` ARTIFACT 4·DERIVED 1(POSTERIOR_PREDICTIVE 동반)) → `buf convert` binpb → 역방향 왕복 `jq -S` 동일. Kotlin `*Contract*` 단언 신설: diagnostics 넷 파싱(값 보존 — 도메인 소비 없이 proto 레벨)·`interval_source` 3값 매핑·표본 축 왕복·`release_kind` ARTIFACT/DERIVED 통과·UNSPECIFIED 거부·미지 필드 999 보존. Python `test_prediction_contract.py` 같은 단언 | 2E ⑦ 관례 · M2 완료 조건 |
| ⑧ | **문서·정책** — `milestone-2.md` 2F 절(다섯 additive·배포 순서·D-2B-8 축 한정) · `capability-map.md` OPEN 표(다섯 OPEN 해소 표기·`OPEN-2F-DICT-INTERVAL-SOURCE` 신설) · 종결 승인 커밋에서 `contract-policy.properties` `approved.tag = contracts/v1-approved-<date>`·`policy.version = 4` — 태그는 **팀장이 종결 승인 커밋 SHA 에** 찍고 `contractGate` 재실행이 「태그 갱신 자체가 breaking 0」 정본 증거(2E 병합 결정 절 관례) | 2E 「병합 결정」 · D-2D-1 |

**만들지 않는 것**: Python wire↔내부 매핑(5E) · 3계층 소비(5D-3) · Kotlin `Diagnostics` 도메인 타입·소비(후속) · `data-dictionary.md` §6.5 편집(discovery 소유 — OPEN) · breaking(번호 재사용·제거·타입 변경) · `v2` 패키지.

---

## 계약 고정 결정

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-2F-1** | 표본 축 = `AgencyIdFact`·`CategoryCodeFact` 재사용(`oneof value/missing`), 허용 사유 `NOT_COLLECTED_YET` 하나, 식별자 아님 | features.proto 관례 · `optional` → Fact 는 breaking(역 불가) · D-2B-6 · 2A ⑥(미상 vs 빈 값) | **확정 (a) 2026-09-15** |
| **D-2F-2** | `ModelRelease.release_kind` enum additive + DERIVED 규약(`dataset_id` 공백은 DERIVED 만) | 5D-2 위협 (h) · 2B ⑥ · Kotlin 게이트가 「지어낸 값」을 못 막으므로 형태로 1급화 | **확정 (b)** |
| **D-2F-3** | 도메인 `IntervalSource.PosteriorPredictive` 추가(workflow 편집) — `null` 로 접어 거부하지 않음 | 2A ⑥ 제3 변환 금지 정신 · 엔진의 정직한 답을 client 가 버리면 안 됨 | **확정 (i)** |
| **D-2F-4** | D-2B-8 을 요청·응답 후보율 축으로 한정, 표본 관측값은 > 1 허용(엔진 밴드가 정제) | 100% 초과 투찰 표본 실존(밴드 상한 1.5 의 존재 이유) · 5D-2 `OPEN-5D2-BID-RATE-UPPER` | **확정 (a)** |
| **D-2F-5** | `Weight` 메시지 KDoc 을 「[0,1] fraction 가중치 일반」으로 확장해 `shrinkage_weight` 에 재사용(새 메시지 신설 대신) | 형태 동일(`string fraction`) · 메시지 수 최소 · 의미는 필드 주석이 나름 | 계약 고정 |
| **D-2F-6** | `release_kind` UNSPECIFIED 는 Kotlin 수신 거부(fail-closed) — 기존 표본은 전부 명시값으로 갱신, 과도기 「UNSPECIFIED = ARTIFACT」 암묵 해석 없음 | 2A ⑥ · ADR 0010 D-7 제공자 먼저 | 계약 고정 |
| **D-2F-7** | 승인 태그 `contracts/v1-approved-<종결일>` 는 종결 승인 커밋에, `policy.version` 3→4 | 2E 관례 · D-2D-1 | 계약 고정 |

---

## 위협 모델 — 2F 고유 경계

**방어한다**: (a) breaking 유입 — `buf breaking FILE` + mutations 양성 대조(S-3·S-9) (b) 값 위장 — `release_kind` 로 DERIVED 1급, `dataset_id` 공백은 DERIVED 만(Kotlin `hasValidReleaseShape` kind 분기 test) (c) 사유 지어내기 — 표본 `missing` 허용 값 하나(주석 + Kotlin `toProto()` 가 그 값만 냄) (d) 소진 `when` 무음 통과 — 컴파일(값 추가 → 실패 → 갱신) (e) 배포 순서 — milestone-2 문면 + testdata 가 새 값을 실어 수신 test 가 먼저 통과해야 함 (f) 미지 필드 손실 — 999 보존 test 유지 (g) 표본 축을 식별자로 오용 — 주석 + 도메인 타입이 `AgencyId`/`CategoryCode`(fact 타입) 뿐.
**방어하지 않는다**: 3계층 소비의 옳음(5D-3) · Python 매핑(5E) · 정책 값 · `data-dictionary.md` 정합(OPEN).

**우회 후보(≥5)**: (1) `optional string` 로 넣어 미상·빈 값 합침 → D-2F-1 형태 (2) `dataset_id = "n/a"` 로 ARTIFACT 위장 → DERIVED 규약 + `release_kind` 필수, Kotlin 은 ARTIFACT 면 다섯 비공백이라 통과하나 **`release_id` 접두 `distribution/` 와 kind 불일치를 Kotlin 이 거부**(test) (3) `POSTERIOR_PREDICTIVE` 를 client 가 `null` 로 접음 → D-2F-3 (4) enum 값 추가를 수신자 먼저 배포 → 문서 + testdata (5) 표본 `missing` 에 `NOT_APPLICABLE` → 주석 규약 + Kotlin `toProto()` 단일 값(Python 5E 는 수신 시 거부 — 인수) (6) `Weight` 에 1 초과 → 5D `Decimal` 검증 + Kotlin 파싱 시 [0,1] 검사(Diagnostics 미소비라 2F 는 proto 레벨 값 보존만 — 알려진 제한) (7) 필드 번호 8·9 를 다른 타입으로 → ADR 0003 D-6 + breaking gate.

---

## (2b) 값 획득 축
| 표면 | 판정 |
| --- | --- |
| proto 필드·enum 값 여섯 | 연다(계약) — 수신 검증은 위 규약 |
| 도메인 `IntervalSource.PosteriorPredictive`·`ModelReleaseRef.kind`·`CompetitionSample.agencyId/categoryCode` | 연다 — 없던 권한 아님(port 가 이미 나름) |
| `hasValidReleaseShape` | internal — 게이트 술어(kind 분기) |

---

## OPEN — 수령·신설
| OPEN | 처리 |
| --- | --- |
| `OPEN-5D2-SAMPLE-SEGMENT`·`OPEN-5D-DIAGNOSTICS-WIRE`·`OPEN-5D2-INTERVAL-SOURCE-WIRE`·`OPEN-5D2-RELEASE-FOR-DISTRIBUTION`·`OPEN-5D2-BID-RATE-UPPER` | **이 slice 종결로 wire 측 해소** — Python 매핑(5E)·3계층 소비(5D-3) 는 별도 |
| `OPEN-2F-DICT-INTERVAL-SOURCE` | `data-dictionary.md` §6.5 에 `interval_source` 정의 없음 — discovery 문서 갱신(후속, 문서 소유) |
| `OPEN-2F-DIAGNOSTICS-DOMAIN` | Kotlin gateway 가 `Diagnostics` 를 도메인으로 소비하지 않음(읽는 코드 0) — 4D-1 후속 |
| `OPEN-2B-AGENCY-ID` | 수령 유지 — 표본 축도 같은 정본 |
