# M6/6E-1 — capability acceptance 추적표 (C-1, 2026-10-07)

`docs/discovery/capability-map.md` 의 **`V2 필수` 61 전건**에 대해 그 capability 의 acceptance scenario 를
**무엇이 재는가**를 하나씩 붙인다. 이 표가 없으면 `milestone-6.md` 완료 조건 3(「M0 필수 capability E2E 전체
통과」)은 **판정 대상이 정의되지 않은 조건**이다 — 이 문서가 그 판정 대상이다.

**r1 수정**: verifier r1 R1-H-3 이 ⓐ 표본 7 중 3 의 규칙 위반을 찾았고, 그 기준으로 **ⓐ 29 행 전수를
bullet 단위로 재대조**했다. 위반 **9 행**(`COL-02`·`COL-03`·`ML-05`·`ML-07`·`DEC-02`·`DEC-06`·`SET-06`·
`OPS-01`·`OPS-09`)을 ⓒ 로 내리고 비어 있는 bullet 을 행마다 이름으로 적었다. 집계가 ⓐ 29 → **20** 으로
움직였고 성립 범위는 31 → **22/61** 이다 — 완료 조건 3 의 결론(「오늘 성립하지 않는다」)은 그대로다.

## 집계

| 분류 | 건수 |
|---|---:|
| ⓐ acceptance 의 무조건 항목 **전부**를 등재 식별자가 잰다 | **20** |
| ⓑ 경계로 처리 — 잴 대상이 설계로 없다 | **2** |
| ⓒ 미구현 — 무조건 항목 하나 이상에 재는 자리가 없다 | **39** |
| **계** | **61** |

| 축 | ⓐ | ⓑ | ⓒ | 계 |
|---|---:|---:|---:|---:|
| COL | 4 | 1 | 3 | 8 |
| STR | 3 | 0 | 7 | 10 |
| QUAL | 3 | 0 | 4 | 7 |
| ML | 4 | 0 | 3 | 7 |
| DEC | 4 | 0 | 5 | 9 |
| NOTI | 1 | 1 | 3 | 5 |
| SET | 0 | 0 | 3 | 3 |
| OPS | 1 | 0 | 11 | 12 |
| **계** | **20** | **2** | **39** | **61** |

**완료 조건 3 의 오늘 답**: `V2 필수` 61 가운데 acceptance 가 **전부 측정되는 것은 20 건**, 잴 대상이 설계로
없는 것이 2 건, **무조건 항목 하나 이상을 잴 자리가 없는 것이 39 건**이다. 그래서 「M0 필수 capability E2E
전체 통과」는 **오늘 성립하지 않는다.** 성립하는 범위는 ⓐ 20 + ⓑ 2 = 22 건이고, 그 범위를 이 표가 고정한다.

**ⓒ 가 「아무것도 안 돈다」를 뜻하지 않는다** — 39 건 가운데 대부분은 acceptance 의 여러 항목이 이미
측정되고 있고 **한둘이 비어 있다**. 그 비어 있는 항목과 이미 측정되는 부분을 행마다 적었다. 부분 측정이
강한 행 `COL-04` · `QUAL-02` · `ML-05` · `ML-07` · `DEC-06` · `SET-06` · `OPS-01` · `OPS-03` · `OPS-05` · `OPS-07` · `OPS-09` · `OPS-12` · `OPS-13` 은 「부분 측정(강함)」으로 표시했다.

## 분류 규칙 — 이 표를 재현하는 방법

| 표식 | 조건 |
|---|---|
| **ⓐ** | acceptance 의 **결정 무관(무조건) 항목 전부**를 등재 식별자가 잰다. 미측정 잔여 0. |
| **ⓑ** | 그 acceptance 가 **코드 구조상 잴 대상을 갖지 않는다** — 표면·개체가 설계로 없고 **그 부재 자체가 요구를 만족시킨다**. 사유를 적는다. |
| **ⓒ** | 무조건 항목 가운데 **하나라도** 재는 자리가 없다. 무엇이 비었는지와 후속·OPEN ID 를 적고, 이미 측정되는 부분은 「부분 측정」으로 그 식별자를 함께 적는다. |

**ⓒ 는 두 갈래다** — 「무조건 항목이 **구현에 없다**」와 「구현은 성립하고 **측정만 없다**」. 뒤쪽은 OPEN
이 아니라 **test 신설**이 후속이므로 OPEN 칸에 `후속: test 신설` 을 적는다. 그 구분이 완료 조건 3 의
처분을 가른다 — 앞쪽은 기능 결정이 선행하고 뒤쪽은 test 한 벌이면 ⓐ 로 올라온다.

**「부분 측정(강함)」의 술어**: 그 capability 의 무조건 bullet 가운데 **과반**이 등재 식별자로 측정되고
비어 있는 것이 **둘 이하**인 ⓒ 행이다. 판정(ⓐ/ⓑ/ⓒ)에 쓰이지 않는 보조 표식이고, 어느 bullet 이 비었는지는
행 본문이 이름으로 적는다(verifier r1 G-8).

**조건부·잠정 묶음은 ⓐ 판정의 요건이 아니다**(capability-map §0.5 — 결정 전 임시 시나리오다). 다만 그것까지
측정되는 경우(`QUAL-03`·`ML-03`·`OPS-09`)는 「무엇을 재는가」에 적었다.

**식별자의 어휘는 둘뿐이다** — ① Kotlin test 클래스 FQN(`config/quality/gate-tests.properties` 등재 집합
안, 349) ② CI step 이름 축어(`.github/workflows/ci.yml` 의 `name:`, 25). Python 쪽은 등재 레지스트리가
없으므로 **CI step 이름**이 식별자이고 재는 모듈 경로는 본문에 적는다. 두 집합과의 대조 명령은
`reports/evidence/m6/6e1/commands.md` 「문서 등식」 절의 `E-1`~`E-3` 이다.

## 후속이 **test 신설**인 ⓒ — 5 건

구현은 성립하고 **측정만** 비어 있는 행이다. OPEN 을 신설할 대상이 아니라 test 한 벌이 필요한 자리이고,
그 test 가 들어오면 ⓐ 로 올라온다. 각 행 본문이 **어느 bullet 이 비었는지**를 이름으로 적는다.

`COL-02` · `COL-03` · `DEC-06` · `OPS-01` · `OPS-09`

## OPEN·후속이 **들고 있지 않은** 미구현 — 14 건

아래 capability 는 `V2 필수`인데 무조건 항목이 비어 있고, 그 사실을 들고 있는 OPEN·후속 ID 가
capability-map §12/§14 에도 `milestone-6.md` OPEN 목록에도 **없다**. 완료 조건 3 이 가리지 못하는 자리이므로
**OPEN 신설이 필요하다** — 이 slice 는 신설하지 않고 보고한다(OPEN 에스컬레이션, `checklist.md`).

`STR-06` · `STR-07` · `STR-10` · `STR-16` · `QUAL-08` · `ML-05` · `ML-07` · `ML-08` · `DEC-02` · `DEC-05` · `DEC-10` · `SET-06` · `OPS-08` · `OPS-13`

## 표

| capability | 이름 | 판정 | 재는 자리(등재 식별자) | 무엇을 재는가 / 무엇이 비었는가 | OPEN·후속 |
|---|---|---|---|---|---|
| **COL-01** | 당일 등록된 신규 공고를 자동으로 받는다 | ⓐ | `bidvector.adapters.koneps.KonepsOpenApiNoticeSourceTest` · `bidvector.app.wiring.CollectionWiringTest` · `bidvector.procurement.NoticeIdTest` · `bidvector.adapters.persistence.CleanMigrationCheckTest` · `bidvector.adapters.persistence.NoticeFindRoundTripTest` | 탈락 회계는 `COL-01` 을 축어로 인용하는 test 둘이, KST 기준일은 「오늘은 KST 달력일이다 — UTC 날짜와 갈리는 시각에도」가 잰다. 제로패딩 차수의 「수집·저장·재조회 전 구간」은 세 층으로 닫힌다 — 타입 왕복(「원문 문자열 그대로 왕복한다」) · DB 의 `notice_round` 형식 CHECK · 그 차수를 **조회 키로 쓰는** 저장 왕복(키가 보존되지 않으면 find 가 빗나가 그 단언들이 붉어진다). | — |
| **COL-02** | 어떤 공고가 얼마에 누구에게 낙찰됐는지 받는다 | ⓒ | — | 첫 bullet 「실 기초금액이 **없는** 개찰 pass 를 흘려도 저장된 기초금액이 덮이지 않는다」를 재는 자리가 없다 — `mayOverwrite` 규율은 **값이 있는** 관측의 권위 비교이고, 결측 재관측이 지우지 않음을 재는 test 는 발주기관·공고명 둘에만 있다(기초금액 축 0). 구현은 있고 **측정만 비어 있다** → test 신설. 부분 측정 — 권위 비교는 `bidvector.procurement.ResolvedBaseAmountTest`·`bidvector.adapters.persistence.PrecedenceParityTest`·`bidvector.adapters.persistence.PrecedenceMutationTest`, 예정가 파생은 `bidvector.procurement.NoticeTest`(「계산식은 없다」)·`bidvector.procurement.OpeningResultFactSlotsTest` 가 잰다. | **후속: test 신설** |
| **COL-03** | 복수예비가격 15개와 추첨번호를 확보한다 | ⓒ | — | 셋째 bullet 「상세 조회가 429 로 **실패**해도 그 공고는 예비가격 없이 저장되고 나머지 수집은 계속되며 실패 건수·사유가 회계에 실린다」를 재는 자리가 없다 — 인용 가능한 429 test 는 전부 「연속 실패 뒤 **성공**(bounded retry)」이고 **재시도 소진 → 예가 없이 저장 → 계속**을 잇는 test 가 0 이다(verifier r1 R1-H-3). 구현은 있고 측정만 비어 있다 → test 신설. 부분 측정 — 저장분 0회·age-gate 건너뜀·넘긴 뒤 정확히 1회는 `bidvector.procurement.DetailFetchTest`, 15행 자식 저장은 `bidvector.adapters.persistence.OpeningReservePriceRepositoryTest`, 회계 항등식은 `bidvector.procurement.AccountingTest` 가 잰다. | **후속: test 신설** |
| **COL-04** | 공고의 참가자격 원문과 게시 낙찰하한율을 확보한다 | ⓒ | — | 자격 수집 대상 집합의 **정렬**(감시 조건 매칭 공고가 마감 임박보다 앞선다)이 없다 — 요건을 채우는 경로 자체가 미정이다. 부분 측정(강함) — 업종제한 유무의 호출 0/1 은 `COL-04` 를 축어로 인용하는 `bidvector.procurement.PortsTest`, 재포함(부분 성공 → 다음 창)은 `bidvector.procurement.DetailFetchTest` 의 recheck-gate, 「요건 없음 vs 수집 실패」 구분은 `bidvector.adapters.koneps.KonepsLicenseLimitDocumentSourceTest`·`bidvector.adapters.qualification.StoredRequirementLicenseGateTest` 가 잰다. | `OPEN-6F5-EXTRACTION-FILL` |
| **COL-05** | 공고와 개찰 결과가 같은 사업으로 묶인다 | ⓑ | — | V2 에 「사업(project)」 집계 개체가 **없다** — 동일성은 `NoticeId`(공고번호+차수) 하나이고 제목 기반 휴리스틱 매칭 코드가 production 에 0 이다. 셋째 bullet 은 잴 대상이 존재하지 않고, 앞 둘은 식별자 동일성이 구조로 보장한다(부분 측정 `bidvector.procurement.NoticeIdTest`·`bidvector.adapters.persistence.NoticeVersioningTest`). | — |
| **COL-06** | 수집이 무엇을 얼마나 놓쳤는지 알 수 있다 (수집 회계) | ⓐ | `bidvector.procurement.AccountingTest` · `bidvector.adapters.koneps.KonepsOpenApiNoticeSourceTest` · `bidvector.adapters.persistence.CleanMigrationCheckTest` | `received = normalized + duplicate + dropped` 항등식은 생성 시점·DB CHECK 두 층이, `totalCount` 부재 + 같은 쪽 반복의 유한 종료는 `truncated` 로 잰다. | — |
| **COL-07** | KONEPS 응답 필드의 변화를 조용히 삼키지 않는다 | ⓐ | `bidvector.procurement.FieldContractTest` · `bidvector.procurement.CanonicalizeTest` · `bidvector.adapters.koneps.KonepsOpenApiNoticeSourceTest` · `bidvector.adapters.koneps.KonepsLicenseLimitDocumentSourceTest` | `COL-07` 을 축어로 인용하는 test 넷이 미지 키 계수 + 비소비를, `expectedRange`/`RANGE` 위반 거부가 scale·basis 어긋남을 잰다. | — |
| **COL-08** | 업무구분 코드와 표시 라벨을 확보한다 | ⓐ | `bidvector.procurement.BusinessClassificationCanonicalizeTest` · `bidvector.adapters.persistence.NoticeBusinessClassificationPersistenceTest` · `bidvector.adapters.persistence.NoticeReconstructionTest` · `bidvector.adapters.koneps.KonepsSourceDivisionTest` | 코드/라벨 분리 저장과 「응답에서 추측하지 않는다」, 어휘 밖 라벨의 DB 거부가 두 bullet 을 덮는다. | — |
| **STR-01** | 내가 입찰할 만한 공고만 골라서 본다 (감시 조건) | ⓐ | `bidvector.strategy.WatchRulesTest` · `bidvector.buildlogic.ModuleDependencyPolicyTest` | `STR-01 1`~`STR-01 4` 를 축어로 인용하는 test 다섯이 네 bullet 과 1:1 로 붙는다. 넷째(「ML port 를 0회 호출」)는 그 test 가 결정성만 보므로 **실제 잠금은 모듈 의존 게이트**다 — `strategy` 모듈이 ML 좌표를 참조할 수 없어 호출 경로가 컴파일되지 않는다(verifier r1 R1-L-4). | — |
| **STR-02** | 키워드 매칭 대상 텍스트 범위 제어 (오탐 차단) | ⓐ | `bidvector.strategy.WatchRulesTest` · `bidvector.strategy.WatchTextAssemblyTest` | `STR-02 1`~`STR-02 4` 축어 인용 넷 + 대상 텍스트 조립. | — |
| **STR-03** | 액션 임계치 편집 (즉시투찰 / 검토 / 보류 분기) | ⓐ | `bidvector.strategy.StrategyValidationTest` · `bidvector.decision.VerdictLadderPolicyDataTest` | `STR-03 1`~`3` 축어 인용 넷. 「모든 경로」는 V2 의 쓰기 경로가 편집 세션 하나뿐이라 그 커널 검증이 전수다 — 사다리 정책 데이터도 같은 부등식을 독립으로 거부한다. | — |
| **STR-04** | 알림 범위 제어 (`notify_only_high_priority`) | ⓒ | — | 알림 범위를 좁히는 전략 필드가 **없다** — 편집 가능 필드 13 에 `notify_only_high_priority` 대응물이 없다. 「억제된 후보를 억제 사유와 함께 조회」할 자리도 없다. | `OPEN-6F3-BID-RECORD` |
| **STR-06** | 추천 후보 수 상한 | ⓒ | — | 후보 **표시** 표면이 없다(운영자 API 에 후보·제안 목록 operation 0). `candidateLimit` 과 분석 예산(`OPPORTUNITY_POLICY` 의 embedding·prediction 예산)의 독립을 재는 자리도 없다. 부분 측정 — `bidvector.workflow.evaluation.OpportunityAnalysisTest` 가 예산의 출처가 정책 값임을 잰다. | **없음 — 신설 필요** |
| **STR-07** | 전략을 저장하면 후보 목록이 갱신된다 | ⓒ | — | 후보 스냅숏과 `stale` 표시·재계산 디스패치가 **없다**. 부분 측정 — 「계산 중 저장은 채택되지 않는다」축은 `bidvector.app.wiring.StrategyEditExecutorRaceTest`(끼어든 커밋이면 값 제출 거부)와 요청당 전략 1회 읽기(`bidvector.app.wiring.EvaluationDryRunFactoryTest`)가 잰다. | **없음 — 신설 필요** |
| **STR-08** | 감시 실행 이력과 신규/지속/이탈 후보 diff | ⓒ | — | 감시 실행 이력 표와 신규/지속/이탈 diff, 중복 억제 **판정 기록**이 없다. 부분 측정 — 배달 층 중복 제거는 `bidvector.workflow.event.InboxDedupPropertyTest`·`bidvector.adapters.e2e.PipelineRedeliveryE2ETest` 가 잰다(그러나 run 이력을 입력으로 삼는 억제가 아니다). | `OPEN-6F3-BID-RECORD` |
| **STR-09** | 주기 자동 감시 | ⓒ | — | 주기 실행이 **없다** — 러너는 전부 `mode=once` 1회성이고 주기는 외부 cron 이 진다. 「수동 실행과 주기 실행이 같은 후보 집합」을 잴 두 번째 트리거가 존재하지 않는다. | `OPEN-6F10-SCHEDULER` |
| **STR-10** | 명시적 재계산 요청과 신선도 표시 | ⓒ | — | 재계산 요청 표면과 진행/실패 상태, 고아 running 회수가 **없다**. 고아 회수는 outbox 층에만 있고 재계산 상태 기계가 아니다. | **없음 — 신설 필요** |
| **STR-16** | 입찰 목록 검색과 투찰가 요청 (운영자의 주 경로) | ⓒ | — | 입찰 목록 **검색** operation 이 없다(운영자 API 는 전략 조회·편집 세션·평가 dry-run 뿐). 금액 범위 검색의 basis 선언, 빈 결과의 두 뜻 구분도 잴 자리가 없다. 부분 측정 — 「요청 시 산출」축은 `bidvector.app.http.EvaluationDryRunE2ETest`·`bidvector.app.http.EvaluationDryRunBidNowE2ETest` 가, 「같은 입력이면 같은 답」은 `bidvector.adapters.e2e.PipelineReproducibilityE2ETest` 가 잰다. | **없음 — 신설 필요** |
| **QUAL-01** | 이 공고에 우리가 참가할 수 있는가, 없다면 무엇이 없어서인가 | ⓐ | `bidvector.qualification.LicenseEligibilityTest` · `bidvector.qualification.LicenseEligibilityPropertyTest` · `bidvector.adapters.qualification.StoredRequirementLicenseGateTest` · `bidvector.adapters.koneps.KonepsLicenseLimitDocumentSourceTest` · `bidvector.adapters.e2e.PipelineOneLineE2ETest` | `Uncertain` 네 사유, 「보유 면허는 사유에 없다」, property(사유 ⊆ 실제 미보유), 파싱 불가 행의 `rowIdentifierIndeterminate` 계수가 다 붙는다. 첫 bullet 의 뒷부분(「게이트에서 **제외되지 않는다**」)을 실제로 재는 것은 e2e 다 — 요건 행을 seed 하지 않은 공고가 `Uncertain(RequirementDataAbsent)` 로 판정되고도 outbox 까지 간다고 단언한다(verifier r1 R1-M-2). | — |
| **QUAL-02** | 자격 없는 공고를 추천 후보에서 제외한다 (게이트) | ⓒ | — | 제외 **건수·사유 분포**를 run 단위로 내는 자리가 없다(dry-run 응답은 평탄하고 후보별 사유가 없다). 게이트를 정책 값으로 켜고 끄는 경로도 없다 — 기본 ON 고정이다. 부분 측정(강함) — 첫 bullet(「`Uncertain` 판정이 후보 제외로 이어지지 않는다」)은 **측정된다**: `bidvector.adapters.e2e.PipelineOneLineE2ETest` 가 요건 행 없는 공고를 `Uncertain(RequirementDataAbsent)` 로 보내고도 outbox 까지 간다고 단언하므로, production 의 `Eligible`·`Uncertain` 통과 분기를 제외로 바꾸면 그 단언이 붉어진다(앞 판의 「그 분기를 고정하는 test 가 없다」는 **거짓이었고 철회한다** — verifier r1 R1-M-2). 게이트가 Ineligible 을 떨어뜨리는 쪽은 `bidvector.workflow.evaluation.EvaluateCandidatesUseCaseTest` 가 잰다. | `OPEN-6A3-EVALUATION-DETAIL` |
| **QUAL-03** | 그룹 AND/OR 자격 경로 판정 | ⓐ | `bidvector.qualification.LicenseEligibilityTest` · `bidvector.qualification.LicenseEligibilityPropertyTest` · `bidvector.adapters.koneps.KonepsLicenseLimitDocumentSourceTest` | 그룹 간 OR·그룹 내 AND·`lmtGrpNo` 결측 폴딩·정책 version 봉투·`permsnIndstrytyList` 포함이 모두 붙고, **조건부(`OPEN-QUAL-11`) 임시 시나리오까지** 「허용업종만 보유하면 결합 규칙 미결로 Uncertain」으로 잰다. | — |
| **QUAL-04** | 기술부문·협회 가입 자격 축 | ⓒ | — | 기술부문·협회 가입 자격 축의 matcher 어휘가 **없다**. 3분(요건 없음/미충족/검증 불가)도 property(단독 차단 금지)도 잴 대상이 없다. | `OPEN-QUAL-07` |
| **QUAL-05** | 판정 provenance (어떤 원문 → 어떤 규칙 → 어떤 결론) | ⓐ | `bidvector.qualification.LicenseEligibilityTest` · `bidvector.sharedkernel.PolicyTest` | 사유 파생은 `missingByGroup`(실제 미보유만) + 정책 version 봉투가, 어휘 version 의 시점 적용은 `EffectiveDatedPolicy` 의 「기준일 이하 최대 엔트리」·「엔트리 없으면 소급하지 않는다」가 잰다. | — |
| **QUAL-08** | 지역 자격 | ⓒ | — | 구조화 지역제한 **필드**로 판정하는 자격 축이 없다. 전략의 지역 어휘는 watch rule 의 텍스트 매칭이고 acceptance 가 금지한 쪽이다 — 「지역 자격 없음」과 「지역 점수 낮음」을 가르는 결과 타입도 없다. | **없음 — 신설 필요** |
| **QUAL-11** | 금액 capacity 게이트 (도급한도 / 시공능력평가액) | ⓒ | — | 금액 capacity 축이 **없다** — 운영자 보유액 필드도(프로필은 업종·면허·지역 셋만), 두 금액의 basis 대조도 없다. 부분 측정 — 마지막 무조건 bullet(「값 크기로 단위를 구분하는 경로가 없다」)은 `bidvector.sharedkernel.RateTest`·`bidvector.sharedkernel.CompileFailureHarnessTest` 가 타입으로 닫는다. | `OPEN-QUAL-09` · `OPEN-QUAL-10` |
| **ML-01** | 투찰율 추천 타점과 시나리오 3후보 | ⓐ | `bidvector.adapters.contract.PredictionContractTest` · `bidvector.workflow.prediction.PredictionValueTest` · `bidvector.adapters.ml.SuccessShapeFailClosedTest` · `bidvector.adapters.ml.ResponseMappingTest` | 3후보·순서·`bid_rate ≤ 1`·origin 전수와 「후보 2개면 계약 위반」이, 라벨 순서 어긋남의 `ContractViolation` 이 붙는다. 「bid/review/skip 을 담지 않는다」는 계약 타입에 그 칸이 없는 쪽으로 닫힌다. | — |
| **ML-02** | 배우지 않은 공종에 답하지 않는다 (미학습 가드) | ⓐ | `pytest — 게이트 test 포함, test 수는 이 step 출력에 남는다 (S-5)` | 재는 자리는 `ml-engine/tests/inference/test_availability.py` — 「표본 얕음」 둘(`TooFewObservations`·`TooFewRatioSamples`)의 구분, 게이트가 비율 게이트보다 먼저, 경계값 포함, **직접 호출도 게이트와 같은 판정**. 비활성화 불가는 `ml-engine/tests/inference/test_policy.py` 의 「임계 1 미만은 거부」가 닫는다. **주의** — GBM 축의 `min_category_rows=0` 은 정책이 거부하지 않는다(같은 파일의 test 가 그 사실을 고정한다). 그 축은 ML-05 몫이다. | — |
| **ML-03** | 불확실성·신뢰도 표시 | ⓐ | `pytest — 게이트 test 포함, test 수는 이 step 출력에 남는다 (S-5)` · `bidvector.workflow.evaluation.EvidenceLinesTest` · `bidvector.decision.priority.PriorityCompositionTest` | 「표본 부족이 confidence 0 이 아니라 사유 있는 측정 불가」는 `Unavailable`/`Absent` 타입과 「전부 Absent 면 0 이 아니라 Unavailable」이, 「가격 적합도로 명시하고 낙찰 확률로 표기하지 않는다」는 성분 다섯에 **확률 축이 없다**(`ComponentExhaustiveTest`)가 잰다. 조건부(`OPEN-ML-03`)는 타입 분리 쪽으로 이미 닫혀 있다. | — |
| **ML-04** | 예정가 분포 추정 (4/15 추첨 + 계층 수축) | ⓐ | `pytest — 게이트 test 포함, test 수는 이 step 출력에 남는다 (S-5)` · `bidvector.workflow.evaluation.SampleEligibilityTest` · `bidvector.workflow.evaluation.EvidenceLinesTest` | 재는 자리는 `ml-engine/tests/inference/test_reserve_draw.py`·`test_distribution.py`·`ml-engine/tests/features/test_shrinkage.py`. `clean` 이외 provenance 배제는 Kotlin 쪽 표본 자격(`라벨 — … SuspectRatio`)이 입력 경계에서, 수축 가중치 공시는 「기관 표본이 임계 미만이면 골든 줄에 임계 미만 표기가 실린다」가 잰다. | — |
| **ML-05** | 낙찰률 GBM (공종 × 금액대 × 발주기관) | ⓒ | — | 셋째 bullet 「학습 표본 정의가 바뀌면 `model_version` 이 올라간다」가 **구현에 없다** — `model_version` 이라는 축이 ml-engine 전체에 0 이다(아티팩트는 release/checksum 축으로 식별한다). 부분 측정(강함) — 피처 이름 **순서** 불일치 거부·피처 manifest 체크섬 불일치 거부·**재현성 칸 부재 거부**는 `ml-engine/tests/registry/test_artifact.py` 가, 학습 spec 은 `ml-engine/tests/training/test_spec.py`·`test_booster.py` 가 잰다(CI step 축어는 `pytest — 게이트 test 포함, test 수는 이 step 출력에 남는다 (S-5)`). | **없음 — 신설 필요** |
| **ML-07** | 모델 승격 게이트 (사전 선언 판정식 + 검정력 공시) | ⓒ | — | 셋째 bullet 이 요구하는 「**effective date**·승인 근거를 가진 정책 산출물」에서 **effective date 칸이 없다**(`ml-engine/policy/evaluation-v1.yaml` 은 `version` 만 있다), 다섯째 bullet 「정책 version 이 바뀌면 이전 판정과 이후 판정이 구분된다」를 재는 자리도 없다(verifier r1 R1-H-3). 부분 측정(강함) — `NotEvaluable(reason)` 이 `Passed` 가 될 수 없는 **타입**이고 `required_row_count` 를 함께 내는 것, 성숙한 창이 없으면 `NO_EVALUABLE_WINDOW` 가 안정성 판정보다 먼저 이기는 것, **공개 진입점에 맨 임계 파라미터가 없는 것**(CLI·환경변수·요청 어느 경로로도 완화 못 한다)은 `ml-engine/tests/evaluation/test_verdict.py` 가 잰다. | **없음 — 신설 필요** |
| **ML-08** | 아티팩트 무결성·서명·릴리스 롤아웃 | ⓒ | — | **서명(signing) 축이 없다** — 레지스트리에 서명 생성·검증 코드가 0 이라 「서명 키가 산출물·로그에 남지 않는다」가 공허하게만 참이다. 부분 측정 — 「검증 안 함 ≠ 통과」는 `ml-engine/tests/registry/test_artifact.py` 의 「검증된 바이트 인자를 요구한다」·체크섬 불일치 거부가, 「예약 학습이 릴리스를 만들지 않는다」는 `ml-engine/tests/training/test_residual_release.py` 계열이 잰다(CI step `pytest — 게이트 test 포함, test 수는 이 step 출력에 남는다 (S-5)`). | **없음 — 신설 필요** |
| **DEC-01** | 투찰 기준금액 해석과 provenance 표시 | ⓐ | `bidvector.procurement.ResolvedBaseAmountTest` · `bidvector.procurement.CollectionPolicyTest` · `bidvector.sharedkernel.MoneyTest` · `bidvector.sharedkernel.UndeclaredProvenanceTest` · `bidvector.adapters.contract.ContractRoundTripTest` | 폴백이 표시되는 쪽(`FallbackFromBudget` 은 `FilledFromBudgetKey` 가 아니면 거부 · 해석 순서 P-3), basis 미표기 금액의 구분(`Undeclared` 면 파생이 전부 `Unmeasurable`), 금액+provenance 한 값 이동(`UNSPECIFIED` provenance 는 wire 에서 거부)이 세 bullet 과 붙는다. | — |
| **DEC-02** | 추천 투찰가 산정과 제약(clamp) 적용 | ⓒ | — | 첫 bullet 「최종 추천가가 **어느 제약에 binding 됐는지가 결과에 실린다**」가 **구현에 없다** — `roundedWith` 는 성공 경로에서 `Derived<BidAmount>` 만 내고 어느 제약이 걸렸는지의 기록 칸이 없다(하한 미달은 `ROUNDED_BELOW_FLOOR` **실패 사유**로만 나타난다). 부분 측정 — 둘째 bullet(반올림이 하한 미만을 조용히 내지 않는다)은 `DEC-02 무조건 acceptance` 를 축어로 인용하는 `bidvector.sharedkernel.ArithmeticTest`, 셋째(basis 교차 금지)는 `bidvector.sharedkernel.CompileFailureHarnessTest`(1·11)가 잰다. 잠정(`OPEN-DEC-03`) 항목은 예규 확인 뒤 재결정 대상이라 세지 않는다. | **없음 — 신설 필요** |
| **DEC-03** | 법정 낙찰하한율 해석 (우선순위 + 적용 범위) | ⓒ | — | 법정 하한율 **해석기**가 없다 — 기관 유형×시행일×추정가격 구간을 하한율로 바꾸는 versioned policy 와 그 적용 범위 묶음이 production 에 0 이다. `FloorUnmeasurableReason` 에 `FloorRateUnresolved`·`FloorModelNotApplicable` **사유만** 있고 그 값을 만드는 자리가 없다(하한율은 호출부가 준다). 부분 측정 — 소급 금지·시행일 경계 축은 `bidvector.sharedkernel.PolicyTest` 의 `EffectiveDatedPolicy` 가 일반형으로 잰다. | `OPEN-DEC-10` |
| **DEC-04** | 하한 미달 빈도 표시와 판정 불가 사유 (정직 명세) | ⓐ | `bidvector.decision.FloorShortfallKernelTest` · `bidvector.sharedkernel.RateArithmeticTest` | 경계 등가 비미달(strictly-greater)·149/150/151 표본 경계·밴드 밖 표본의 분모 제외와 그로 인한 `Unmeasurable` 전이·`Frequency` 가 (분자, 분모) 묶음이고 `policyVersion` 이 함께 남는 것 — 다섯 bullet 전부가 한 클래스에 있다. | — |
| **DEC-05** | 투찰가 메뉴 3안 (recommended / aggressive / safe) | ⓒ | — | 투찰가 **메뉴** 타입이 없다 — (태도, 리스크 노트, 근거) 세 칸을 요구하는 옵션 타입도, 「밴드 붕괴」 상태도 production 에 없다. 부분 측정 — 세 후보의 순서 강제는 `bidvector.workflow.prediction.PredictionValueTest`, 역전 응답의 `ContractViolation` 은 `bidvector.adapters.ml.SuccessShapeFailClosedTest` 가 잰다. | **없음 — 신설 필요** |
| **DEC-06** | bid_now / review / skip 액션 결정과 근거 (reason code) | ⓒ | — | 넷째 bullet 「같은 공고를 **어느 경로로 평가해도** 액션과 근거가 동일하다」를 재는 자리가 없다 — dry-run 과 커밋이 같은 use case 를 쓴다는 **구조**는 architecture gate 가 잠그지만, 같은 공고를 두 경로로 흘려 판정을 **대조하는** test 가 0 이다. (acceptance 가 드는 셋째 경로 백테스트는 V2 에서 액션을 만들지 않는다 — Python 백테스트는 투찰율 전략과 채점만 낸다.) 구현은 성립하고 측정만 비어 있다 → test 신설. 부분 측정(강함) — (코드, payload) 영속·재렌더링은 `bidvector.adapters.event.OutboxPayloadCodecTest`, 소진 검사는 `bidvector.decision.MlUnavailableReasonTest`·`bidvector.workflow.evaluation.NotificationReasonSerializationTest`, 사다리 전 분기는 `bidvector.decision.VerdictLadderTest`·`bidvector.decision.VerdictLadderPropertyTest`, 문구 자동 추종은 `bidvector.workflow.evaluation.EvidenceLinesTest`, 조립 분리는 `bidvector.app.architecture.DryRunCommitSeparationGateTest` 가 잰다. | **후속: test 신설** |
| **DEC-08** | 기초금액 provenance 분류 | ⓐ | `bidvector.decision.ProvenanceRulesTest` · `bidvector.workflow.evaluation.PredictionFactsTest` · `bidvector.workflow.evaluation.SampleEligibilityTest` · `bidvector.adapters.persistence.PrecedenceLabelColumnTest` | first-match 가 정수 판정보다 앞서는 것, 복구 추정치가 원본 필드에 기록되지 않는 것(「원본 참조는 판정을 거쳐도 보존된다 — 복구 추정치와 분리」), 15행 중 하나라도 결손이면 `RESERVE_PRICE_MISSING`, 정책 version 동반 저장 — 네 bullet 전부. | — |
| **DEC-09** | rate scale 정규화 (percent ↔ fraction) | ⓐ | `bidvector.sharedkernel.RateTest` · `bidvector.sharedkernel.RateArithmeticTest` · `bidvector.sharedkernel.CompileFailureHarnessTest` · `bidvector.sharedkernel.RegressionExampleTest` | 「단위 선언 없이는 `Rate` 가 만들어지지 않는다 — 공개 경로가 이름이 단위인 둘뿐」 + 교차 축 대입이 **컴파일되지 않는다**(2·12) + 「변환은 생성 지점 한 곳뿐이고 크기로 추측하지 않는다」. 모든 소비 경로의 같은 해석은 `fraction` 고정 + 단일 생성 지점이 구조로 진다. | — |
| **DEC-10** | 게시 낙찰하한율 신뢰 게이트 | ⓒ | — | 게시 하한율의 **계수**(`floor_implausible`)가 없고, 게이트에 걸린 값과 안 걸린 값을 다른 타입으로 가르는 자리도 없다. 부분 측정 — 개연 밴드 판정(밴드 안 수용 · 아래/위 모두 사유와 함께 거부 · 경계 포함)은 `bidvector.decision.FloorOverrideValidationTest` 가 전수로 잰다. | **없음 — 신설 필요** |
| **NOTI-01** | 고우선순위 투찰 판단을 채널로 즉시 받는다 | ⓒ | — | **기록 1건** 축이 없다 — 판정의 영속 흔적은 outbox 행뿐이라 「억제는 채널 억제이지 기록 억제가 아니다」를 잴 기록 표가 없다. 부분 측정 — 채널 쪽은 `bidvector.workflow.evaluation.EvaluateCandidatesUseCaseTest`(BidNow 면 알림 요청 1, 아니면 0)·`bidvector.adapters.e2e.PipelineLadderBoundaryE2ETest`(임계 아래는 outbox 에 닿지 않는다)가 잰다. | `OPEN-6F3-BID-RECORD` |
| **NOTI-03** | 배달 경로 판정과 target 마스킹 | ⓐ | `bidvector.workflow.notification.DeliveryPlanTableTest` · `bidvector.workflow.notification.MaskedTargetTest` · `bidvector.workflow.notification.RouteKeyTest` · `bidvector.workflow.notification.DispatchNotificationTest` | 「정책 허용 + 환경 불가」가 서로 다른 필드로 남는 것(정책 3 × 환경 3 전수 아홉 행), 끝 4자 마스킹 property, 원문 chat id·봇 비밀값·메일 주소 모양이 예외 메시지에 나타나지 않는 것, 차단 사유가 열거된 `Suppressed` status 인 것 — 세 bullet 전부. | — |
| **NOTI-04** | 낙찰/패찰 결과 자동 통지 | ⓒ | — | 낙찰/패찰 **통지** 경로가 없다 — 결과 판정을 통지에 싣는 자리도, 통지 기록의 성공·억제·실패 구분도, 뒤늦은 확정의 정정도 production 에 0 이다. 조건부 둘(`OPEN-NOTI-01`·`OPEN-NOTI-08`)도 미결이다. | `OPEN-NOTI-01` · `OPEN-NOTI-08` |
| **NOTI-07** | 외부 webhook 인증 | ⓑ | — | V2 는 **인바운드 webhook 표면을 두지 않는다** — 운영자 API 의 operation 전수에 webhook 이 없고 선언 밖 경로는 전부 `unmatchedPath` 로 간다. legacy 의 「공유 비밀값 미설정 상태에서 미인증 호출을 통과시킴」·「식별자 평문 노출」 결함은 재현될 자리가 없다. 표면 전수는 `bidvector.app.http.HttpSurfaceCensusTest`·`bidvector.app.http.ProductionHttpSurfaceTest`·`bidvector.app.http.OpenApiContractTest` 가 잠근다. | — |
| **NOTI-10** | 앱 알림 중복 억제 | ⓒ | — | **앱 알림함이 없다** — 「알림함 항목이 1개」를 잴 표가 없고, 제목 문구 변경에 중복 억제가 흔들리지 않는지도 잴 대상이 없다. 부분 측정 — 멱등 키가 제목이 아니라 `noticeId` 로 고정되는 것은 `bidvector.workflow.evaluation.OutboxNotificationRequestPortTest`, 키 단위 수렴은 `bidvector.workflow.event.InboxDedupPropertyTest` 가 잰다. | `OPEN-6F3-BID-RECORD` |
| **SET-01** | 실투찰 개찰 결과 자동 대사 (낙찰/패찰 확정) | ⓒ | — | 운영자 **상호** 필드가 없다(프로필은 업종·면허·지역 셋) — 법인 표기 차이 흡수도, 「상호 미설정이면 판정하지 않고 외부 조회도 하지 않는다」도 잴 대상이 없다. 부분 측정 — 「낙찰가만 있고 상호가 없으면 3-state」 축은 `bidvector.procurement.OpeningCompleteAxisTest`·`bidvector.adapters.persistence.OpeningCompleteAxisRepositoryTest` 의 `RankMissing` 이 같은 모양으로 잰다(투찰금액으로 재계산하지 않는다). | `OPEN-SET-03` · `OPEN-SET-05` |
| **SET-02** | 개찰 1위(잠정) 수집 — 낙찰 확정 이전의 조기 신호 | ⓒ | — | **실투찰 레코드가 없다** — 「실투찰 공고 여러 건의 마감일이 같으면 1콜로 묶는다」·「실투찰 레코드가 여러 건인 공고도 후보에 1회만」을 잴 개체가 없다. 부분 측정 — 잠정 1위가 확정 필드를 바꾸지 않고 같은 행에 병합되며 참가자수·개찰시각이 보존되는 축과 복합 문자열의 즉시 구조체 승격은 `bidvector.adapters.persistence.OpeningCompleteAxisRepositoryTest`·`bidvector.procurement.OpeningResultFactSlotsTest`·`bidvector.app.collection.OpeningBudgetE2ETest` 가 잰다. | `OPEN-6F3-BID-RECORD` |
| **SET-06** | 정산 성숙도(관측 가능성) → 평가 창 embargo | ⓒ | — | **시간축 provenance** 축이 구현에 없다 — `ml-engine` 의 평가 모듈 전체에 시간축 출처·`Unmeasurable` 어휘가 0 이라 세 bullet(① 각 행의 시간축 값에 시각 종류와 정책 version 이 함께 남는다 ② 시각을 모르는 행은 `Unmeasurable(사유)` 로 분류되고 그 건수가 리포트에 별도 항목 ③ 대체 출처를 쓴 행의 비율과 그 행 제외 시의 주별 성숙도를 함께 산출)을 잴 대상이 없다. 부분 측정(강함) — 주 성숙도 끝 경계 제외, 관측 0 인 주의 미성숙 처리, 행 수 미달·학습 행 부재의 **제외 사유**와 「가장 이른 사유가 이긴다」, 성숙 창 겹침 거부와 홀드아웃 겹침을 **값이 아니라 위치**로 세는 것, 「비율 기반 분할 API 가 존재하지 않는다」는 `ml-engine/tests/evaluation/test_windows.py` 가 잰다. | **없음 — 신설 필요** |
| **OPS-01** | 단일비행(lease) — 중복 실행 억제와 억제 기록 | ⓒ | — | 셋째 bullet 「홀더 프로세스를 **강제 종료**하면 lease 가 즉시 해제된다」를 재는 자리가 없다 — 인용 가능한 test 는 본문 종료·예외 종료(`finally` 경로)와 Busy 이고, 세션 강제 종료(연결 끊김·`pg_terminate_backend`)를 재는 test 가 0 이다(verifier r1 R1-H-3). advisory lock 의 세션 종료 해제는 PostgreSQL 의 성질이라 구현은 성립하고 측정만 비어 있다 → test 신설. 부분 측정(강함) — 「조용히 사라지지 않는다」는 `bidvector.app.relay.RelayExitCodeTest`(`LEASE_BUSY 3` 이 0 이 아니다)·`bidvector.app.collection.CollectionRunnerTest`, 임대 배타성과 해제는 `bidvector.adapters.event.PostgresAdvisoryLockLeaseTest`·`bidvector.adapters.event.PoolerLeaseProbeTest`, 살아 있는 홀더가 막는 동안의 Busy 는 `bidvector.adapters.e2e.PipelineRestartConvergenceE2ETest` 가 잰다. | **후속: test 신설** |
| **OPS-02** | lease 경합 시 재시도 예산 (공정성) | ⓒ | — | lease 경합의 **재시도 예산**이 없다 — 못 쥐면 `LEASE_BUSY` 로 끝나고 재시도 창도, 소스 간 공정성 분배도 production 에 0 이다. 「재시도 창 최댓값 < 고아 판정 임계」 property 도 잴 두 값이 없다. | `OPEN-OPS-10` |
| **OPS-03** | outbox — 트랜잭션 경계를 넘는 durable 전달 | ⓒ | — | 「놓친 배달이 **앱 알림함에서 조회된다**」가 잴 대상을 갖지 않는다 — 내구성을 질 DB 표가 없다. 「최대 시도를 소진한 행」도 시도 계수 칸이 없어 다른 형태(한 번 claim 뒤 종단)로 구현돼 있다. 부분 측정(강함) — 같은 트랜잭션 커밋·롤백은 `bidvector.adapters.persistence.OutboxTransactionAtomicityTest`·`bidvector.adapters.event.JdbcOutboxPortTest`, dedupe 수렴은 `bidvector.adapters.event.JdbcInboxPortTest`·`bidvector.adapters.event.OutboxClaimConcurrencyTest`, 종단 무한점유 없음은 `bidvector.workflow.event.OutboxTransitionTableTest`·`bidvector.adapters.event.OutboxTransitionSqlTest`, at-most-once 는 `bidvector.workflow.notification.RelayOutboxNotificationsTest`·`bidvector.adapters.e2e.PipelineRestartConvergenceE2ETest`, 선언 없는 부작용의 등록 거부는 `bidvector.adapters.event.OutboxPayloadCodecTest` 가 잰다. | `OPEN-6F3-BID-RECORD` |
| **OPS-04** | 큐 깊이 배압 관측 | ⓒ | — | backlog **깊이를 재는 자리가 없다** — 측정 불가/정상/임계 초과 세 값도, 표시 계층의 중립 표현도, 검출 지연 SLO 도 존재하지 않는다. 「깊이 관측이 큐 상태를 바꾸지 않는다」도 관측자가 없어 공허하다. | `OPEN-OPS-10` · `OPEN-OPS-03` · `OPEN-6F10-CLAIM-OBSERVABILITY` |
| **OPS-05** | 고아 작업 회수 | ⓒ | — | **원래 오류 문맥이 보존되지 않는다** — outbox 에 실패 사유·시도 계수 칸이 없고(원인 코드는 로그로만 간다) 「실행 상한 안」을 재는 시간 기준 janitor 도 없다(회수 축은 임대 기반이다). 부분 측정(강함) — 강제 종료 뒤 1회 실행으로 종단화는 `bidvector.adapters.e2e.PipelineRestartConvergenceE2ETest`, 멱등 재적용은 같은 클래스의 「격리 뒤 한 번 더 재기동하면 보고가 전부 0」, 살아 있는 행 미회수는 같은 클래스의 Busy 행이 잰다. | `OPEN-6F10-CLAIM-OBSERVABILITY` |
| **OPS-07** | dry-run — 세 가지 서로 다른 의미 | ⓒ | — | 세 뜻 가운데 둘이 비어 있다 — **새 채널 등록**이 없고(실 sender 부재) **일일 발송 예산**이 없어 「dry-run 이 예산을 소비하지 않는다」를 잴 예산이 없다. 부분 측정(강함) — 「정책상 차단 vs 환경상 불가」는 `bidvector.workflow.notification.DeliveryPlanTableTest`·`bidvector.workflow.notification.DispatchNotificationTest`, 「미리보기 뒤 **outbox 불변**」(dry-run 도 `api_audit` 행은 **쓴다** — fail-closed 감사가 모든 요청에 붙는다)은 CI step `컨테이너 스모크 — 준비 상태·인증 경계·health 격리·수집 꺼짐 (S-23b)` 의 dry-run 왕복(outbox 행 수 전후 등식)과 `bidvector.app.architecture.DryRunCommitSeparationGateTest` 가 잰다. | `OPEN-STR-12` |
| **OPS-08** | rate limit — 세 층 | ⓒ | — | 세 층 가운데 발송 예산 층이 **없다**. 외부 API 층의 「연속 호출 간 최소 간격 보장」도 재는 자리가 없다(상한·일 한도는 계수 기반이고 간격 기반이 아니다). 부분 측정 — 호출 상한·KST 일 경계는 `bidvector.adapters.koneps.KonepsCallGateTest`·`bidvector.procurement.CollectionCallBudgetTest`·`bidvector.app.wiring.CollectionLedgerSurfaceTest`, 분석 예산 소진은 `bidvector.workflow.evaluation.EvaluateCandidatesUseCaseIsolationTest` 가 잰다. | **없음 — 신설 필요** |
| **OPS-09** | 외부 호출 실패의 구조적 분류와 재시도 정책 | ⓒ | — | 셋째 bullet 「**차단(403 등) 응답은 재시도하지 않는다**」를 재는 자리가 없다 — KONEPS 어댑터에 HTTP 403·차단 어휘가 0 이고(분류는 응답 봉투 코드 축으로 돈다) 그 상태 코드의 비재시도를 고정하는 test 도 0 이다. 그 상태 코드가 실제로 비재시도로 가는지도 **확인되지 않았다** → test 신설 + 구현 확인. 부분 측정(강함) — HTTP 200 + 에러 봉투가 실패로 가는 것(XML·JSON 같은 표), 429 와 일 한도가 다른 축인 것, 「미지 `resultCode` 는 `Unclassified` 로 **비재시도** 실패」와 「`resultCode` 부재도 `Unclassified`」, 버킷별 재시도 가능성의 정책 선언은 `bidvector.adapters.koneps.KonepsGatewayErrorEnvelopeTest`·`bidvector.adapters.koneps.KonepsOpenApiNoticeSourceTest`·`bidvector.adapters.ml.MlCallPolicyDataTest`·`bidvector.procurement.KonepsCollectionPolicyDataTest` 가 잰다. | **후속: test 신설** |
| **OPS-10** | 감사 추적 / 증적 | ⓐ | `bidvector.adapters.audit.ApiAuditStoreTest` · `bidvector.app.http.RequestAuditFilterTest` · `bidvector.app.http.ProductionAssemblyAuthAuditTest` · `bidvector.adapters.persistence.RawAppendOnlyTest` · `bidvector.adapters.persistence.NoticeVersioningTest` · `bidvector.workflow.evaluation.NotificationReasonSerializationTest` · `pytest — 게이트 test 포함, test 수는 이 step 출력에 남는다 (S-5)` | 릴리스 식별자 동반은 `release-sha` 가 원장·raw 행에 실리는 것과 append-only 보장이, 체크섬 없는 아티팩트의 별도 verdict 와 「예약 학습이 릴리스를 만들지 않는다」는 `ml-engine/tests/registry/test_artifact.py`·`training/test_residual_release.py` 가, reason code 집합 변경 시 계약 test 실패는 직렬화 축어 골든이 잰다. 감사 행 자체는 fail-closed 로 응답 전에 기록된다. | — |
| **OPS-11** | 재수집 / 수동 운영 액션 | ⓒ | — | **예약 트리거가 없다** — 「수동 트리거와 예약 트리거가 같은 결과를 만들고 다른 것은 trigger source 뿐」을 잴 두 번째 트리거가 존재하지 않는다. 부분 측정 — 재수집 멱등(canonical 1건·알림 1회)은 `bidvector.app.collection.CollectionRunnerE2ETest`·`bidvector.adapters.e2e.PipelineFailureInjectionE2ETest`·`bidvector.adapters.persistence.NoticeVersioningTest` 가 잰다. | `OPEN-6F10-SCHEDULER` |
| **OPS-12** | 설정(configuration) | ⓒ | — | 「활성 스케줄과 그 파라미터(주기·큐·수명)가 관측 엔드포인트에서 조회 가능」을 잴 대상이 없다 — 스케줄이 없고 노출된 actuator endpoint 는 `health` 하나다. 부분 측정(강함) — 유도 관계 위반 조합의 기동 실패는 `bidvector.app.wiring.CollectionWiringTest`·`bidvector.app.wiring.OpeningCollectionWiringTest`(총 상한 < 일 상한 거부)·`bidvector.app.wiring.RelayWiringTest`·`bidvector.app.wiring.EvaluationCommitWiringTest`·`bidvector.app.wiring.OneShotRunnerGuardTest`·`bidvector.app.ManagementSurfaceLockTest` 가 전수로 잰다. | `OPEN-6F10-SCHEDULER` |
| **OPS-13** | 설계 래칫 (비대화 방지) | ⓒ | — | 「함수 줄 수는 한도 안이나 **복잡도**가 한도를 넘는 변이」를 잡는 게이트가 없다(크기 축은 줄 수·파일 수이고 복잡도 축의 독립 래칫을 찾지 못했다). 부분 측정(강함) — fan-in/fan-out·순환·의존 방향은 `bidvector.buildlogic.ModuleDependencyPolicyTest`·`bidvector.app.architecture.ArchitectureGateTest`(+ 음성 대조 `ArchitectureGateCatchesViolationsTest`), public API 예산은 `bidvector.buildlogic.PublicApiTypesTest`·`bidvector.buildlogic.ApiTypePolicyTest`, 복제 helper 는 `bidvector.buildlogic.DuplicatePolicyTest`·`bidvector.buildlogic.CpdReportPresenceTest`, 크기 한도와 baseline 느슨화 금지는 `bidvector.buildlogic.KotlinFunctionLengthsTest`·`bidvector.buildlogic.TypeShapeRatchetPolicyTest`, 프로덕션 엔진 전용 경로는 `bidvector.adapters.persistence.CleanMigrationTest` 계열이 잰다. 「실행 환경을 스니핑해 부작용을 바꾸는 비교가 0 건」을 잠그는 게이트도 찾지 못했다. | **없음 — 신설 필요** |

## 이 표가 공허해지는 길과 막는 것

| 우회 | 막는 것 |
|---|---|
| 61 행을 전부 ⓑ 로 채운다 | ⓐ/ⓑ/ⓒ 건수를 머리에 적는다. ⓑ 는 **2** 뿐이고 둘 다 「그 표면이 설계로 없다」를 표면 전수 게이트(`HttpSurfaceCensusTest`·`OpenApiContractTest`) 또는 식별자 타입으로 잠근 경우다 |
| ⓐ 칸에 존재하지 않는 test 이름을 적는다 | 기계 대조 — 식별자 ⊆ (gate-tests 등재 349 ∪ ci.yml step 25). 본문에 인용한 FQN 도 같은 대조를 받는다 |
| 이름이 비슷한 test 로 ⓐ 를 채운다 | 「무엇을 재는가」가 acceptance 문면의 **어느 bullet 을 어느 단언이** 재는지 적는다. 그 칸이 bullet 을 가리키지 못하면 ⓒ 다 |
| 미구현을 ⓐ 로 분식한다 | 분류 규칙이 「무조건 항목 **전부**」를 요건으로 둔다. 부분 측정은 ⓒ 의 본문으로만 적힌다 |
| 없는 OPEN ID 를 적어 미구현을 덮는다 | 기계 대조 — OPEN ID ⊆ (capability-map ∪ milestone-6). 닫힌 OPEN 도 홀더가 아니다(`OPEN-1BC-STR16`·`OPEN-6F2-CANDIDATE-BOUND` 는 닫혀 있어 쓰지 않았다). 홀더가 없으면 **「없음 — 신설 필요」**로 적고 위 절에 열거한다 |
| 분류 집계가 capability-map 과 어긋난다 | 행 수 == 61 == capability-map §10 의 `V2 필수` 수(기계 집계, 분류 줄 파싱) |
