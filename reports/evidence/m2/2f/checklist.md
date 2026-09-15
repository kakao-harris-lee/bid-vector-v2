# M2/2F checklist.md

## 리뷰 요청 조건 점검

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 경로>`
      빈 출력(evidence 파일 신설분 제외, 커밋 전 기재).
- [x] scope.md의 acceptance_commands(S-0~S-9) 전부 exit 0 — commands.md.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — S-1(`--no-build-cache clean
      check`)이 ktlint·detekt·ArchUnit·sizeGate·cpdCheck·`contractGate`를, S-2(`buf lint
      && buf build`)가 계약 형태를, S-3(breaking 스윕)·S-9(현 태그 대비 breaking)가
      호환성을 각각 검증. S-4·S-5가 계약 규칙을 양쪽 언어에서, S-6이 교차 언어 배선을
      증명.
- [x] 변경된 fixture와 정책 version의 근거 기록 — `contracts/testdata/prediction/`
      5쌍 갱신 + 신설 1쌍(DERIVED+POSTERIOR_PREDICTIVE), 재현 절차는 commands.md.
      정책 값 변경 없음(`contract-policy.properties`는 종결 승인 커밋에서만 팀장이 편집).
- [x] 알려진 제한과 rollback 기록 — 아래 「알려진 제한」·`rollback.md`.
- [x] 누출 검사 통과 — commands.md 「누출 검사」.

## D-2F-1~7 대응표 — scope.md 「계약 고정 결정」과 그것을 증명하는 게이트/테스트

| ID | 판단 | 증명 |
| --- | --- | --- |
| D-2F-1 | 표본 축 = `AgencyIdFact`/`CategoryCodeFact` 재사용, 허용 사유 `NOT_COLLECTED_YET` 하나 | `features.proto` `CompetitionSample.agency_id`(8)·`category_code`(9) 주석 + `RequestMapping.kt`의 `toSampleAgencyIdFact()`/`toSampleCategoryCodeFact()`가 `NOT_COLLECTED_YET` 하나만 냄 + `PredictionAdditiveContractTest`「요청 표본의 agency_id category_code fact 는...」(값 1·missing 1) + Python `test_competition_samples_carry_agency_id_and_category_code_facts` |
| D-2F-2 | `ModelRelease.release_kind` enum additive + DERIVED 규약 | `prediction.proto` `ReleaseKind`+필드 6 + `ReleaseShapeValidation.hasValidReleaseShape`(kind 분기, `release_id` 접두 `distribution/` ↔ kind 일치 검사, **수신 집행 — Kotlin main**) + Kotlin 쪽 ARTIFACT/DERIVED 통과·UNSPECIFIED 거부·접두 불일치 거부 test 4건 + Python 쪽 대칭 test 4건(verifier r1 F-5 — Python 넷은 `test_prediction_contract.py` 안의 `_is_valid_release_shape`가 규칙을 **문서화**하는 순수 함수 단언이지 수신 경로 **집행**이 아니다, 그 docstring이 「실 validation 구현은 5E 몫」이라 자백) |
| D-2F-3 | 도메인 `IntervalSource.PosteriorPredictive` 추가, `null` 로 접지 않음 | `BidPredictionOutcome.kt` enum 값 추가 + `ParsedSuccessFields.toDomainOrNull()` 소진 `when`(컴파일 깨짐 자리, 값 추가로 갱신) + `PredictionAdditiveContractTest`·Python 양쪽 POSTERIOR_PREDICTIVE 파싱 test |
| D-2F-4 | D-2B-8 을 요청·응답 후보율 축에 한정, 표본 관측값은 `> 1` 허용 | `features.proto` `observed_bid_rate` 주석·`prediction.proto` `Candidate.bid_rate` 주석 + `reports/evidence/m2/2b/scope.md` 갱신 이력(2026-09-15) |
| D-2F-5 | `Weight` KDoc 을 「[0,1] fraction 가중치 일반」으로 확장, `shrinkage_weight` 재사용 | `prediction.proto` `Weight` 주석 갱신 + `Diagnostics.shrinkage_weight` 필드가 같은 타입 재사용(신 메시지 없음) |
| D-2F-6 | `release_kind` UNSPECIFIED 는 Kotlin 수신 거부, 과도기 암묵 해석 없음 | `hasValidReleaseShape`의 `RELEASE_KIND_UNSPECIFIED, UNRECOGNIZED -> false` + `release_kind UNSPECIFIED 는 hasValidReleaseShape 가 거부한다` test + testdata 5쌍 전부 명시값(과도기 실측 회피) |
| D-2F-7 | 승인 태그·`policy.version` 갱신은 종결 승인 커밋(팀장) | 이 slice 는 `contract-policy.properties` 무편집(in_scope 표에서 종결 승인 커밋만 대상으로 명시, 구현 레인은 안 건드림) |

## (2b) 값 획득 축 — scope 표 재확인

| 표면 | 판정 | 실측 |
| --- | --- | --- |
| proto 필드·enum 값 여섯 | 연다(계약) | S-2·S-9 형태 검증, 수신 검증은 위 규약 |
| 도메인 `IntervalSource.PosteriorPredictive`·`ModelReleaseRef.kind`·`CompetitionSample.agencyId/categoryCode` | 연다 — 없던 권한 아님(port 가 이미 나름) | `BidPredictionOutcome.kt`·`BidPredictionRequest.kt` — 기존 sealed/데이터 클래스에 값·프로퍼티 추가뿐, 새 진입점 없음 |
| `hasValidReleaseShape` | internal — 게이트 술어(kind 분기) | `ReleaseShapeValidation.kt`, `internal fun`(모듈 밖 미노출) — test 는 같은 모듈(`adapters` test 소스셋)에서만 호출 |
| `ModelReleaseRef.kind` 기본값 `Artifact` | embedding 쪽 기존 호출부(out_of_scope)가 무편집으로 계속 컴파일 | `EmbeddingResponseMapping.kt`·`OpportunityAnalysisFixtures.kt` 는 이 slice 가 손대지 않았고 컴파일 확인(S-1) |

## 위협 모델 — 2F 고유 경계 대응

**방어한다**(scope.md): (a) breaking 유입 → S-3·S-9(11/11 mutation 잡힘, 현 태그 대비 finding 0) (b) 값 위장 → `hasValidReleaseShape` kind 분기 + 접두 검사 test 4건 (c) 사유 지어내기 → `toSampleAgencyIdFact`/`toSampleCategoryCodeFact` 가 `NOT_COLLECTED_YET` 하나만 냄(코드 리뷰) (d) 소진 `when` 무음 통과 → `ParsedSuccessFields.kt`·`ResponseMapping.kt` 둘 다 컴파일이 `RELEASE_KIND_*`·`INTERVAL_SOURCE_*` 신설 값을 강제로 소진(else 없음) (e) 배포 순서 → testdata 5쌍 전부 명시값, milestone-2 2F 절(팀장 소유) 이 문면 기재 (f) 미지 필드 손실 → `ContractUnknownFieldPreservationTest`(기존, 999 필드, 스키마 변경과 무관하게 여전히 통과 — S-1 확인) (g) 표본 축 식별자 오용 → 주석 + 도메인 타입이 `AgencyId`/`CategoryCode`(fact 형) 뿐, `RequestMapping.kt`에 식별자 조회 없음.

**방어하지 않는다**(scope.md 명시): 3계층 소비의 옳음(5D-3) · Python 매핑(5E) · 정책 값 · `data-dictionary.md` 정합(`OPEN-2F-DICT-INTERVAL-SOURCE`).

## 우회 후보(scope (1)~(7) + 설계 검토 (8)~(12)) 대응

| # | 우회 | 막는 장치 |
| --- | --- | --- |
| (1) | `optional string` 로 표본 축을 넣어 미상·빈 값 합침 | `AgencyIdFact`/`CategoryCodeFact`(`oneof`) 재사용 — `optional string`이 애초에 계약에 없음(proto 형태 자체가 차단) |
| (2) | `dataset_id = "n/a"`로 ARTIFACT 위장 | DERIVED 규약 + `hasValidReleaseShape`가 kind 별 판정. `release_id` 접두 `distribution/` 와 kind 불일치를 별도로 거부(「ARTIFACT 인데 release_id 접두가 distribution 이면 거부된다」test) — `dataset_id`만 비우고 kind 는 ARTIFACT 라 주장해도 다섯 비공백 요구에서 `datasetId=""`가 그 자체로 거부됨 |
| (3) | `POSTERIOR_PREDICTIVE`를 client 가 `null`로 접음 | D-2F-3 — 도메인 enum 값 추가, `toDomainOrNull()`이 그 값을 `null`이 아니라 실제 값으로 매핑(test 로 고정) |
| (4) | enum 값 추가를 수신자 먼저 배포 | milestone-2 2F 절 문면(팀장) + testdata 5쌍이 새 값을 실어 Kotlin/Python 수신 test 가 이 커밋에서 이미 통과(수신자 준비 증거) |
| (5) | 표본 `missing`에 `NOT_APPLICABLE` | 주석 규약 + `toSampleAgencyIdFact`/`toSampleCategoryCodeFact` 가 `NOT_COLLECTED_YET` 리터럴 하나만 냄(코드 검토로 방어 — Python 5E 수신 거부는 인수, 이 slice 밖) |
| (6) | `Weight`에 1 초과 값 | 5D `Decimal` 검증(ml-engine, 이 slice 밖) + Kotlin 은 `Diagnostics` 를 도메인으로 소비하지 않아 [0,1] 검사 없음 — **알려진 제한 1로 등재**(proto 레벨 값 보존만) |
| (7) | 필드 번호 8·9 를 다른 타입으로 | `buf breaking` FILE(S-9 로 실측, `FIELD_SAME_TYPE` 규칙이 breaking-mutations.sh 의 `type-change` 케이스로 상시 검증) |
| (8) | `agency_id`에 DB 내부 id 문자열을 실음 | 계약이 강제 못함(값의 의미는 송신 어댑터 리뷰 몫) — `OPEN-2B-AGENCY-ID` M3 정본 + `features.proto` 주석이 「같은 정본」명시. **경계 밖**(송신 어댑터는 M4 후속, 이 slice 는 `RequestMapping.kt`가 도메인 `AgencyId` 값을 그대로 옮기기만 함 — 값을 짓지 않음) |
| (9) | DERIVED 응답의 `artifact_checksum`을 임의 문자열로 | 규약은 sha256(정책 YAML)이나 Kotlin 은 검증할 정책 YAML 이 없음(serving 소유) — `ReleaseCheck.releaseSatisfiesSelector`의 `Exact` selector 가 `release_id`+`checksum` 정확 일치를 요구하므로 운영자가 selector 에 준 값과 다르면 거부됨(`ReleaseCheckTest`, 무변경으로 계속 통과 — S-1). `LatestPromoted`는 promoted 메타데이터와 대조. 즉 위장은 selector 대조 단계에서 잡힌다(등재만, 새 test 불필요 — 기존 메커니즘) |
| (10) | `release_kind = DERIVED` 인데 `training_row_count > 0` | Kotlin 은 `Diagnostics`를 안 읽어 검증 없음(알려진 제한 1과 같은 축) — **Python 계약 test 로만 단언**: `test_derived_release_has_zero_training_row_count`. Kotlin 소비는 후속(`OPEN-2F-DIAGNOSTICS-DOMAIN`) |
| (11) | 표본 `category_code` 값이 `FeatureInputs.category_code` 어휘 밖 | 5D-3 소비 라운드의 어휘 검증(5B `Vocabulary` OOV) — **이 slice 범위 밖**, 인수 명시(설계 검토 원문 그대로) |
| (12) | 필드 번호 8·9 대신 기존 번호 재사용/타입 변경 | (7)과 동일 — `buf breaking` FILE 이 잡음(S-9 실측) |

## 알려진 제한

1. **`Diagnostics`는 Kotlin 도메인으로 소비되지 않는다**(`OPEN-2F-DIAGNOSTICS-DOMAIN`) —
   `PredictionAdditiveContractTest`가 proto 레벨 값 보존만 증명한다. `shrinkage_weight`의
   [0,1] 범위 검사·`agency_sample_count`/`agency_sample_below_threshold`의 도메인 소비는
   후속 slice(4D-1 후속) 몫이다.
2. **`data-dictionary.md` §6.5 는 `INTERVAL_SOURCE_POSTERIOR_PREDICTIVE`를 정의하지 않는다**
   (`OPEN-2F-DICT-INTERVAL-SOURCE`, discovery 문서 소유 — 후속, capability-map.md OPEN
   표 신설 등재).
3. **배포 순서는 문서 의존이다** — `release_kind`/`interval_source` 신설 값은 「제공자
   먼저 배포」(ADR 0010 D-7) 원칙을 milestone-2 2F 절(팀장 편집)의 문면으로만 강제한다.
   이 slice 자체는 실제 ml-engine 배포 순서를 게이트로 집행하지 않는다(Python wire
   매핑 자체가 5E 소관이라 아직 존재하지 않음 — 순서 위반의 실물 발생 경로가 없다).
4. **DERIVED release 의 `artifact_checksum` 계산 로직은 이 slice 가 검증하지 않는다** —
   규약(sha256(정규화 정책 YAML))은 문서화됐으나 실 계산은 ml-engine(5E) 소관이고, Kotlin
   은 selector 대조로만 간접 방어한다(우회 (9) 대응 참고).
5. **하나의 공유 검증 함수를 나누지 않았다** — 설계 검토 §(5)는 「`hasNonBlankRelease` →
   `hasValidReleaseShape`」로 **이름 교체**를 지시했으나, `hasNonBlankRelease`는
   `EmbeddingShapeValidation.kt`(embedding.release, 이 slice 범위 밖)도 함께 쓰는 공유
   함수임을 구현 중 확인했다. `embedding.proto`는 `release_kind`를 아직 나르지 않으므로
   (2F out_of_scope) 그 값을 kind 분기 함수로 검사하면 기존 embedding testdata 전부가
   `RELEASE_KIND_UNSPECIFIED`(값 미설정)로 거부돼 `EmbeddingContractTest`·
   `EmbeddingShapeFailClosedTest`가 깨진다(실측: 초기 구현에서 이 경로로 706개 test 중
   embedding 관련이 실패할 뻔함 — 설계와 다른 결정으로 `hasValidReleaseShape`를 **신규
   함수**로 추가하고 `hasNonBlankRelease`는 무변경 유지). 「판단이 갈린 지점」에 재등재.
6. **신설 필드·enum 값은 승인 태그 재발행 전까지 breaking 게이트에 안 보인다**(verifier
   r1 F-4) — 현 승인 태그(`contracts/v1-approved-2026-09-10`)는 `features.proto`·
   `prediction.proto` **파일**을 기준선에 담지만, 이번에 새로 연 **필드 자리**
   (`agency_id=8`·`category_code=9`·`Diagnostics` 3~6·`IntervalSource` 값 3→4·
   `release_kind` 6→7)는 아직 그 기준선에 없다. 실측: 그 필드들의 타입·번호를 바꿔도
   `buf breaking`(S-9)은 exit 0(통과)다 — 기준선에 있는 기존 필드(`dataset_id=5`)를
   지우면 exit 100(잡힘)과 대조된다. 「2E F-1(2E 는 신설 파일 전체가 무방비)이 2F 에
   없다」는 **파일 단위**로는 맞지만 **신설 필드 단위**로는 같은 창이 종결 승인 태그
   재발행까지 열려 있다(scope.md 「계약 갱신 이력」 2026-09-16 행이 정본). 보상 통제:
   testdata 7쌍(신설 1쌍 포함)의 `buf convert` 왕복 test 가 현재 형태를 고정하고, 종결
   승인 커밋의 태그 재발행이 이 창을 닫는다.
7. **`ResponseMapping.toDomain()`의 `error()`는 `mapSuccess` 경유로 도달 불가함을 실측
   확인했다**(verifier r1 F-6, production 경로 재현: `UNSPECIFIED` 입력은 throw 없이
   `Unavailable(ContractViolation)`으로 접힌다 — `hasValidReleaseShape`가 `mapSuccess`
   보다 먼저 그 값을 거부하기 때문). 그래도 같은 `ProtoReleaseKind`enum 위에서 fail-closed
   여과(`hasValidReleaseShape`)와 매핑(`toDomain()`)이 **두 개의 별도 함수**로 갈려
   있어, "여과를 통과한 값만 매핑에 도달한다"는 불변식이 타입으로 표현되지 않고
   `error()` 방어문으로만 남는다(표현 가능한 불가능 상태, `close-impossible-states-with-types`
   관례). 통합 후보(예: 여과가 `ReleaseKind`를 직접 반환해 `toDomain()`을 없애는 리팩터)는
   이 slice 범위 밖 — 후속 결정.

## 판단이 갈린 지점(설계와 다른 결정)

1. **`hasNonBlankRelease`를 `hasValidReleaseShape`로 교체하지 않고 신규 함수로 병존** —
   알려진 제한 5. 근거: in_scope Kotlin 파일 목록에 `EmbeddingShapeValidation.kt`가 없고,
   out_of_scope 절도 embedding 관련 변경을 언급하지 않는다 — 공유 함수의 의미를 바꾸면
   그 파일도 손대야 하는데 이는 scope 확장이다. 신규 함수 추가는 in_scope 파일
   (`ReleaseShapeValidation.kt`) 안에서 끝나고 기존 embedding 계약 test 전건을 무변경으로
   통과시킨다(S-1 확인, 706 tests 0 failures).
2. **`ModelReleaseRef.kind`에 기본값 `Artifact`를 둠**(설계 검토는 default 언급 없음) —
   `EmbeddingResponseMapping.kt`·`workflow/.../OpportunityAnalysisFixtures.kt`가 같은
   생성자를 `kind` 인자 없이 호출하고 있어(공유 도메인 타입), 필수 인자로 만들면 이
   out_of_scope 파일들의 컴파일이 깨진다. 기본값은 기존 동작(전부 ARTIFACT 취급)을 그대로
   보존한다.
3. **표본 축(`agency_id`/`category_code`) missing 사유를 `FeatureInputs`(`UNKNOWN`)와
   다른 리터럴(`NOT_COLLECTED_YET`)로 뒀다** — 설계와 일치(D-2F-1 이미 이렇게 결정됨),
   구현 중 재확인 사항으로만 기록. 두 축을 같은 확장 함수로 합치지 않은 이유이기도 하다
   (`toAgencyIdFact()`는 `FeatureInputs` 전용, `toSampleAgencyIdFact()`는 `CompetitionSample`
   전용 — 사유가 다르므로 공유하면 그 차이가 무너진다).

## 커밋 목록

**Phase 3 구현**(base `c669a71`) — 순서대로: `3a46393`(proto 둘 + testdata 5쌍 갱신·신설
1쌍) · `9bd1b5b`(Kotlin 수신자 — `ParsedSuccessFields.kt`·`ReleaseShapeValidation.kt`·
`RequestMapping.kt`·`ResponseMapping.kt`·`BidPredictionOutcome.kt`·`BidPredictionRequest.kt`
+ 신설 `PredictionAdditiveContractTest.kt`(9건) + `PredictionContractTest.kt` 분리 +
`MlTestFixtures.kt` 기본값) · `7ce4cd6`(Python 계약 test 10건 — 커밋 메시지 「12건」은 계수 오류, 「장부 정정」 참고) ·
`7bdb117`(gate 등재) ·
`8522221`(capability-map OPEN 표·2B scope 갱신 이력) · `693bc5a`(ktlint 자동 포맷, S-1
1차 실패 시정).

이 range의 첫 커밋(`7b4a9ef` — 착수 계약 고정, 세션 모델 단독 작성)은 이 구현 레인이
만든 것이 아니라 오케스트레이터(팀장)가 착수 시 이미 커밋해 둔 것이다(scope.md·
milestone-2.md 「Slice 2F」 절).

## 장부 정정

커밋 `7ce4cd6`의 메시지는 Python 신설 단언을 "12건"이라 적었다. 실측(rollback 임시
clone 대조, commands.md 「rollback 실측」): `test_prediction_contract.py`는 base(`def
test_` 35개) → HEAD(45개)로 **정확히 10건** 증가했다(diagnostics 1·posterior_predictive
1·derived training_row_count 1·표본 축 1·release_kind 넷 4·round-trip 1·objective/label
관련 무관 0). 「12」는 신설 test 함수 10개에 신설 헬퍼 함수(`_success_release`·
`_is_valid_release_shape`, test 아님) 둘을 더한 계수 오류로 보인다. 커밋 메시지는
재작성하지 않는다(이력 되쓰기 금지, 2026-08-22 관례) — 이 문서가 정본 수치다.

## 구현 레인 not-ready 판정 없음

이 slice 는 verifier·Codex 독립 리뷰 이전 구현 레인 단계다. S-0~S-9 전건 exit 0,
adapters+workflow 706 tests 0 failures, ml-engine 54 tests 0 failures. 알려진 제한 일곱
건 전부 등재(도메인 소비·문서 정의·배포 순서·checksum 계산·공유 함수 판단 변경·신설
필드 breaking 무방비 창·`toDomain()` 여과-매핑 분리) — 산출물 blocker/high 없음
(구현자 자체 판단, 최종 판정은 verifier 몫).

## verifier r1 수정 라운드(2026-09-16, 재작업 1/5)

not-ready — high 1(F-1)·medium 1(F-2)·low 4(F-3~F-6). 이 라운드에서 evidence 만
수정했다(코드·proto·Kotlin·Python 무변경 — verifier r1 「게이트 술어 무변경이라 S-1
head 실측·누출 0·rollback ⑥ 만 재확인한다」). 처방 근거는 팀장 커밋 `a0b8f04`
(`reports/evidence/m2/2f/scope.md` 「계약 갱신 이력」).

- **F-1(high)** — `checklist.md:17`·`commands.md:161`이 누출 패턴 목록의 영문 어휘
  하나를 그대로 담고 있던 문구를 「누출 검사」로 교체(baseline 등재가 아니라 어휘
  제거, 5D 전례 — 이 문서 자신도 그 어휘를 리터럴로 다시 적지 않는다, 이 항목처럼).
  재확인:
  `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m2/2f
  --exclude=scope.md` exit 1(매치 0) · `./gradlew --no-daemon :leakPatternGate`
  `BUILD SUCCESSFUL`.
- **F-2(medium)** — `rollback.md`에 확인 ⑥(되돌린 트리의 게이트 — 임시 clone 안에서
  `git add -A && git commit` 뒤 `clean check`) 신설, 확인 지점 번호를 ①~⑥ 원문자로
  통일.
- **F-3(low)** — `rollback.md`의 「12건 신설」→10, 「기존 42건만 남음」→「base 35건만
  남음」 정정. 확인 ⑤의 pytest 명령에 `uv sync --frozen --all-extras` 추가(`--extra
  dev`만으로는 fresh clone 에서 `No module named pytest`).
- **F-4(low)** — 알려진 제한 6(신설 필드 breaking 무방비 창) 신설, D-2F-2 대응표 갱신.
- **F-5(low)** — D-2F-2 대응표의 Python 넷 문면을 「규칙 문서화, 수신 집행은 5E」로 정정.
- **F-6(low)** — 알려진 제한 7(`toDomain()` 여과-매핑 분리) 신설.

S-1 head 재실행(아래 commands.md 「verifier r1 수정 뒤 S-1 재실행」)·S-0 재확인 결과는
그 절 참고.
