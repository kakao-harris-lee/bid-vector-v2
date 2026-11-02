# M6 종결 판정 — 완료 조건 아홉 대조 (2026-11-02)

**판정: 조건부 종결.** 계약(`scope.md`)의 판정 규칙대로 아홉을 **지금의 main 에서 다시 쟀다**(「당시 통과」를
근거로 쓰지 않는다). 결과는 **충족 5 · 좁혀짐 3 · 미측정 0 · 미충족 1** 이다. 미충족 하나는 조건 9(verifier 최종
ready-for-review + 사용자 release 승인)이고 **이 판정 자신이 그 전제**다 — 이 slice 의 verifier 와 사용자 승인으로
닫힌다. 좁혀진 셋(3·5·6)은 전부 **받는 자리가 있다**. 그 밖에 **조건 3 안에 선언 예외가 없는 잔여 한 행**(`SET-01`)이
있어 §3 후속 ① 로 올린다 — 이 slice 는 판정이라 고치지 않는다(D-M6C-3). 조건 1 전건의 1회차는 **마지막
단계(여정 e2e)에서 멈췄다** — 재현되지 않는 e2e 하네스 경합이고 §3 후속 ⑤ 로 올린다.

6G 는 판정하지 않는다(D-M6C-1). 현재 상태는 `open-inventory.md` §3 이다.

## §1 완료 조건 아홉 ↔ 근거

| # | 완료 조건 | 판정 | 근거(정본 위치) |
| --- | --- | --- | --- |
| 1 | 새 checkout/clean database 에서 one-command build/test 가능 | **충족(한정 둘 — 장비 전제 · 1회차 flake)** | 버릴 clone 에서 `./tools/one-command-check.sh` 전건 재실행. **선언된 작업 단계 25 가 전부 순서대로 돌았고**(Kotlin 2 · Python 10 · web 13) Kotlin 은 `check` 통과(**tests 4,156 · 실패 0 · 건너뜀 4**, 결과 XML 집계) · Python 열 단계 통과 · web 은 W-12 까지 통과했다. **마지막 단계 W-13(여정 e2e)에서 1회차가 멈췄다**(1 failed · 10 passed → 전건 비-0). 그 실패는 production 거동이 아니라 **e2e 하네스의 응답 포착 경합**이고 **재현되지 않는다** — 표적 재실행 둘이 11 passed(둘째는 exit 0 포착), 같은 base 의 CI 도 초록이다. 자세한 것은 `commands.md` A·B 와 §3 후속 ⑤. 운영자 결정 2026-10-15(「완료 조건」 아래 결정 블록, 「조건 1(one-command)」)이 이 스크립트를 **Kotlin 전건 + Python 전건 + `web` 전건**으로 확장했고 조건 1 은 그 확장된 스크립트로 잰다. **한정**은 그 결정 문면이 이미 적은 것 그대로다 — 「새 checkout 에서 e2e 까지」는 Playwright 시스템 패키지가 있는 장비를 전제하고, 여정 e2e 는 docker 를 전제한다(7A checklist 알려진 제한) |
| 2 | 기존 `bid-vector` import · symlink · DB schema 에 runtime dependency 없음 | **충족** | 저장소에 legacy symlink 가 없다(`bid-vector` 는 `.gitignore` 항목이라 작업 트리 밖 참조물이다). Kotlin·Gradle 소스의 `bid-vector` 문자열은 셋뿐이고 전부 **legacy 출처 주석·provenance 문자열**이다(`SampleEligibility`·`SampleConversion`·`LicensePolicy` 의 `legacy-behavior:` 줄) — import 0. `settings.gradle.kts` 의 `rootProject.name` 은 V2 저장소 자신의 이름이다. DB 는 V2 자신의 Flyway 이력이고 legacy schema 를 읽는 자리가 없다. 컨테이너 축은 6C 가 빌드 컨텍스트를 실측해 legacy 경로 0 을 단언했다(`6c/commands.md` 컨텍스트 전송 줄). **한정**: 상시 게이트는 이미지 컨텍스트 축뿐이고 **소스 축은 이 판정의 재실측**이다 |
| 3 | M0 필수 capability E2E 전체 통과 | **좁혀짐** | 운영자 결정 2026-10-15 · 2026-10-24 가 판정 대상을 **성립 15 + G1 8** 로 정했다(ML-01 은 6G 종속으로 분리 — D-M6C-2). 그 여덟의 닫는 slice 는 **전부 머지됐다**(§2). ⓐ 가 아닌 넷 가운데 셋(`COL-03`·`DEC-05`·`DEC-02`)은 **운영자 선언 예외**가 덮고, `DEC-10` 은 ⓑ(경계로 처리)다. **`SET-01` 한 행만 선언 예외 없이 잔여**가 남는다 → §3 후속 ① |
| 4 | 실제 외부 effect 없이 dry-run acceptance 가능 | **충족** | dry-run 전용 조립이 출하에 있다(6A-3+6F-3, PR #44 — ML 자리지킴·dry-run 전용). `bidvector.app.http.EvaluationDryRunE2ETest` · `EvaluationDryRunBidNowE2ETest` · `OpenApiDryRunContractTest` 와 조립 분리 게이트 `bidvector.app.architecture.DryRunCommitSeparationGateTest`. 컨테이너 축은 CI `container` job 의 왕복 step 이 재고, **쓰지 않음**을 outbox 전후 행 수 등식으로 단언한다(6E-1 G-4). M7/7F 의 release·반입도 같은 축이다 — dry-run → 운영자 확인 → apply(`reports/evidence/m7/close/checklist.md` 1) |
| 5 | 중요 mutation 생존 0 또는 사용자 승인된 명시적 예외 | **좁혀짐** | **자동 mutation 도구는 채택되지 않았다**(`OPEN-ADR-07`, M1 소유 · `docs/adr/0007` 「`OPEN-ADR-07` · mutation 도구」). 그 자리를 하네스가 **slice 표적 변이 실측**으로 대신한다(`CLAUDE.md` 검증 절 「verifier 의 최고 가치는 우회 고안·변이 실측이다」). G1 다섯 slice 전부 변이 기록을 보유한다 — `g1t/checklist.md` 변이 절(바꿔치기·`numstat`·통제군), `g1dec10/checklist.md` 의 우회 ↔ 막는 자리 대응표 열 행(M·V·N·e 계열 변이가 전부 RED), `g1c03`·`g1set01`·`g1dec05` 각 checklist. **좁혀진 부분**: 「중요 mutation」의 **전수 모수**가 정의된 적이 없고 저장소 전건 자동 mutation 은 측정된 적이 없다 → `OPEN-ADR-07` 이월 |
| 6 | 계약/모델/policy version 으로 결과 재현 | **좁혀짐** | 계약 축: proto 계약 게이트와 OpenAPI 생성 타입 **바이트 등식**(CI `web` job 의 생성 타입 check). policy 축: 정책이 versioned data 이고 `PolicyVersion` 이 출처를 나른다 — `bidvector.settlement.AwardMatchingPolicyDataTest` · `bidvector.decision.menu.BidMenuPolicyDataTest` · `bidvector.app.policy.FloorTrustBandParityTest`(Kotlin 출하 밴드 ↔ Python 백테스트 값 일치). 실행 축: `bidvector.adapters.e2e.PipelineReproducibilityE2ETest`(같은 입력이면 같은 답). **좁혀진 부분 — 모델 version 축에 대상이 없다**: 출하 조립이 `MlAnalysisPort` 를 자리지킴으로 고정해(D-6A3-2) 모델 release 로 결과가 갈리는 production 경로가 0 이다. 홀더 `OPEN-ML-ANALYSIS-WIRING` |
| 7 | rollback/restore rehearsal 증거 존재 | **충족** | M6/6B-2(PR #61) 가 백업·복원·마이그레이션 되돌림 리허설을 냈다 — 수용 기준 D-1~D-6 판정(`6b2/checklist.md`): 백업 산출물 비밀값 0(참조형 스캔) · 복원 뒤 등식 셋(Flyway 이력 · 표별 행 수·내용 해시 · 축9 권한 행렬) · 되돌림 (i)(ii) exit 0. CI `container` job 에 같은 리허설 step 이 상주한다. 착수 당시 비어 있던 「CI 초록」은 그 뒤 PR 체크가 닫았다. M7 축이 같은 조건을 운영 host 에서 겹쳐 잰다 — 7F(PR #112)가 백업·복원 리허설과 반입·되돌림 스크립트를 냈고(이미지 되돌림 ≠ DB 되돌림을 스크립트가 판별·정지한다), 7F-3(PR #113)이 승인 전 안전 후속을, 실반입 기록이 dry-run → apply 를 적는다 |
| 8 | 보안·비밀값 스캔 통과(조건 문면의 어휘는 축어로 적지 않는다 — 누출 스캔 어휘와 겹친다) | **충족** | 비밀값 축: `leakPatternGate`(참조형 어휘 파일, Kotlin `check` 안). 이미지 축: 위생 게이트 + **trivy SBOM·CVE 스캔**이 CI `container` job 에 상주하고, 그 채택이 `OPEN-6C-IMAGE-VULN-SCAN` 을 **해소**했다(6E-2b, `6e2b/checklist.md` — 「흉내가 아닌 스캔임을 재는 술어 여섯」). 실반입 축: M7/7F 가 위생 4/4 · 취약점 4/4(trivy 핀) · allowlist 등재 0 을 기록. **한정**: 이 장비에 `privacy-gate` 정의가 없어 **이름 붙은 개인정보 판정은 없다** — M7/7D 가 verifier 표적으로 대체를 선언했고(`m7/close/checklist.md` 조건 표) 그 한정은 M6 조건 8 문면 밖이다(조건 8 은 보안·비밀값 축이고 개인정보 축은 M7 문면이다) |
| 9 | verifier 최종 `ready-for-review` + 사용자 release 승인 | **미충족(이 판정이 전제)** | 운영자 결정 2026-10-03(「다음 마일스톤」 절)이 이 조건의 모수를 **6G 를 제외한 M6 산출물**로 정했다. 선행이던 「7C② 의 『아직 산출 안 함』 상태가 선 뒤」(운영자 결정 2026-10-24)는 7E-1(PR #102)·7C②(PR #94·#103)로 섰다. 남은 것은 **이 slice 의 verifier 판정과 사용자 승인**이다 |

「근거 없음」 0. 조건 아홉 전부에 담당 근거가 있다.

## §2 조건 3 — G1 8 행별 판정

판정 규칙(`scope.md`): 각 행을 **닫은 PR · test 이름 · 그 test 가 지금 main 에서 도는지**로 판정한다. 추적표
(`reports/evidence/m6/6e1/acceptance-trace.md`)가 ⓐ/ⓑ/ⓒ 의 정본이다. 아래 test 는 **전부 main 에 파일이 있고**
게이트 등재 장부(`config/quality/gate-tests.properties`)에 올라 있다 — 그 장부의 등식은 큐레이션이 아니라
**「`check` 에서 도는 컴파일된 test 클래스 전수 ∖ 제외 == 등재」**이고 `gateRegistrationGate` 가 양방향으로 잰다.

| 행 | 닫은 PR | 추적표 | 재는 test(대표) | 판정 |
| --- | --- | --- | --- | --- |
| `COL-02` | **#77**(M6/G1-T, 2026-10-19) | **ⓐ** | `bidvector.adapters.persistence.BaseAmountMergeGuardTest`(신설) · `PrecedenceParityTest` · `PrecedenceMutationTest` · `bidvector.procurement.ResolvedBaseAmountTest` | **충족.** 첫 bullet 이 2026-10-16 운영자 승인으로 「개찰 pass」 → 「**재관측**(어느 수집 경로든)」으로 개정됐고, 신설 test 가 그 개정 문면을 persist 경로에서 세 걸음으로 잰다 |
| `DEC-06` | **#77** | **ⓐ** | `bidvector.app.evaluation.DryRunCommitParityTest`(신설) · `bidvector.decision.VerdictLadderTest` · `VerdictLadderPropertyTest` · `bidvector.adapters.event.OutboxPayloadCodecTest` | **충족.** 신설 test 가 **두 조립 메서드를 각각 불러** dry-run ↔ commit 의 액션·reason code·알림 처분 등식과 저장된 payload 등식을 잰다 |
| `OPS-01` | **#77** | **ⓐ** | `bidvector.adapters.event.LeaseHolderTerminationTest`(신설) · `PostgresAdvisoryLockLeaseTest` · `bidvector.app.relay.RelayExitCodeTest` · `bidvector.adapters.e2e.PipelineRestartConvergenceE2ETest` | **충족.** 신설 test 가 본문이 **도는 중에** 홀더 백엔드를 끊고 다른 연결이 기다림 없이 lease 를 받는지를 재며, 끊지 않는 **음성 대조**를 같은 파일에 둔다 |
| `COL-03` | **#83**(M6/G1-C03, 2026-10-23) | **ⓒ 유지** | `bidvector.workflow.collection.OpeningDetailFetchGateTest` · `OpeningQuotaHaltResumptionTest` · `CollectOpeningResultsUseCaseTest` · `bidvector.procurement.CollectionAttemptLedgerTest` | **좁혀짐(선언 예외).** 운영자 결정 (나)+(라)(D-G1C03-1, `g1c03/scope.md`): 「이 slice 는 **age 절반을 6G 경로 밖으로 선언**하고 재조회·보유 게이트까지만 세운다. COL-03 은 ⓒ 로 남는다」. 전제는 계약 데이터의 구조적 사실이다 — 개찰 일시 키의 계약 행이 개찰결과 목록 끝점을 선언하지 않아 **첫 상세 호출 이전에 개찰 시각을 아는 경로가 없다**. 그 사실을 조용한 `null` 이 아니라 **이름 있는 선언**으로 두고, 선언이 age 판정을 세우지 않는다는 것 자체를 test 가 잠근다. 받는 자리: 별 slice 「개찰 축 끝점 범위 필드 행」 |
| `SET-01` | **#85**(M6/G1-SET01, 2026-10-23) | **ⓒ** | `bidvector.settlement.AwardMatchingKernelTest` · `CompanyNameNormalizationTest` · `AwardOutcomeRecordingTest` · `AwardMatchingPolicyDataTest` · `CompileFailureHarnessTest`(금액 인자를 더한 호출이 **컴파일되지 않는다**) | **미충족(선언 예외 없음).** 판정 커널은 섰고 운영자 **상호 입력** 칸은 M7/7C④(PR #90·#104·#106)가 채웠다. 남은 빈자리는 **낙찰자 상호의 canonical 적재** 하나다 — `bidwinnrNm` 은 낙찰 목록 끝점에만 있고 6G 는 그 끝점을 걷지 않는다. 운영자 결정 2026-10-22 이 (a) 적재를 **승인**했으나 「착수는 6G 라이브 수집 종료 뒤」로 미뤘고, **이틀 뒤 2026-10-24 결정은 `SET-01` 을 G1 8 에 그대로 두었다**(ML-01 처럼 모수에서 빼지 않았다). 그래서 이 행은 조건 3 의 의무로 남아 있고 닫히지 않았다 → §3 후속 ① |
| `DEC-05` | **#93**(M6/G1-DEC05, 2026-10-27) | **ⓒ 유지** | `bidvector.decision.menu.BidMenuKernelTest` · `MenuCompileFailureHarnessTest` · `BidMenuPolicyDataTest` · `bidvector.workflow.prediction.StanceRateMappingTest` · `bidvector.app.http.BidPriceRequestResponseTest`(7C②) | **좁혀짐(선언 예외).** 운영자 결정 2026-10-24(「완료 조건」 아래 결정 블록, docs PR #88): 「**`DEC-05`·`DEC-02` 는 추적표 ⓐ 술어 ② 의 선언 예외로 판정한다**」 — 판정 근거는 「커널·구조 성립 + 화면의 『아직 산출 안 함』 상태 표시가 test 로 잠김」이고, **값 출처 축과 그 값에서만 생기는 축**은 `ML-01` 과 함께 6G 백테스트 판정 뒤로 간다. 상태 표시는 7C②(PR #94)가 세웠다 |
| `DEC-02` | **#93** | **ⓒ 유지** | `bidvector.decision.menu.BidMenuKernelTest`(binding test 일곱) · `bidvector.sharedkernel.ArithmeticTest` · `CompileFailureHarnessTest` · `bidvector.app.http.BidPriceRequestResponseTest` | **좁혀짐(선언 예외).** 같은 2026-10-24 결정이 덮는다. 그 결정 블록이 요구한 확인(「`roundedWith` 의 하한 없는 호출이 메뉴 값 종속인지 실측해 행에 적는다. 종속이 아니면 그 빈자리는 이 예외 밖」)은 **수행됐고 결과가 적혔다** — 그 호출부는 비율↔금액 왕복의 중간값이라 하한이 적용될 자리가 아니며, 점수 축의 물음은 별 건 `OPEN-DEC02-SCORE-AXIS-FLOOR` 로 갈라 **DEC-02 의 ⓐ 전환을 막지 않는다**고 추적표 DEC-02 행이 적는다 |
| `DEC-10` | **#99**(M6/G1-DEC10, 2026-10-29) | **ⓒ → ⓑ** | `bidvector.decision.PublishedFloorTrustTest` · `FloorTrustCompileFailureHarnessTest` · `bidvector.app.architecture.FloorTrustGateTest` · `FloorTrustGateCatchesViolationsTest` · `bidvector.app.policy.FloorTrustBandParityTest` · `bidvector.workflow.evaluation.FloorExclusionChainTest` | **충족(경계로 처리).** 운영자 결정 2026-10-24(D-DEC10-1)가 개연 밴드 `[0.30, 0.995]` 양 끝 포함을 Kotlin 출하 정책 데이터로 확정했다. **게이트 자체는 배선 전에 구조로 닫혔다** — 통과 값이 원값과 **다른 타입**이고 생성 경로가 게이트 함수 하나다(바이트코드 등식 + 컴파일 실패 fixture). ⓐ 가 아닌 이유는 세 가격 경로가 출하 배선에서 돌지 않는다는 실측이고 홀더는 `OPEN-ML-ANALYSIS-WIRING` 이다. `OPEN-DEC-10` 은 이 slice 가 닫지 않는다 — 그 OPEN 이 소유하는 것은 **DEC-03 법정 하한율 표 값**이다 |

**계수**: ⓐ 3 · ⓑ 1 · 선언 예외로 좁혀진 ⓒ 3 · 선언 예외 없는 ⓒ **1**(`SET-01`).
**성립 15** 는 2026-10-15 결정이 이름 붙인 집합이고 이 판정이 다시 세지 않는다(그 모수의 정본은 추적표이며,
`V2 필수` 모수가 61 → 62 로 움직인 것은 M7/7B 의 `BID-01` 등재다 — 성립 15 와 G1 8 의 수는 불변이다).

## §3 미충족·좁혀짐의 후속 (이 slice 는 고치지 않는다 — D-M6C-3)

| # | 자리 | 무엇이 비었나 | 제안 |
| --- | --- | --- | --- |
| ① | 조건 3 / `SET-01` | 낙찰자 상호의 canonical 적재(`OPEN-SET01-AWARD-NAME-INGEST`). 운영자 결정 2026-10-22 이 (a) 를 승인하면서 착수를 「6G 라이브 수집 종료 뒤」로 미뤘고, **6G 라이브 수집은 2026-10-29(day28)에 총예산 소진으로 사실상 끝났다**(`open-inventory.md` §3) — 그 전제는 이미 도달했다 | **운영자 결정 둘 중 하나**: (a) `ML-01` 과 같은 꼴로 `SET-01` 을 G1 8 에서 빼 6G 종결 뒤 판정으로 옮긴다(조건 3 모수 = 성립 15 + G1 7) · (b) 낙찰 목록 끝점 적재 slice 를 열어 닫는다(**새 외부 호출**이라 범위·쿼터 승인 대상). **추천 (a)** — `DEC-05`·`DEC-02`·`ML-01` 이 이미 같은 사유(출하 배선 부재)로 분리됐고, (b) 는 수집 쿼터 재신청에 종속이다 |
| ② | 조건 5 | 「중요 mutation」의 전수 모수가 정의된 적이 없고 저장소 전건 자동 mutation 측정이 0 이다(`OPEN-ADR-07`, M1 소유·slice 미지목) | 도구 채택 또는 **「표적 변이 실측으로 갈음한다」의 명시 승인**. 후자면 조건 5 문면의 「사용자 승인된 명시적 예외」 가지가 그 자리다 |
| ③ | 조건 6 | 모델 version 축에 대상이 없다 — 출하 조립이 ML 분석 port 를 자리지킴으로 고정 | `OPEN-ML-ANALYSIS-WIRING` 배선 slice 가 함께 받는다(그 slice 의 acceptance 에 `ML-02`·`ML-04` 재판정이 이미 걸려 있다 — 운영자 결정 2026-10-24) |
| ④ | 조건 2 | 소스 축에 **상시 게이트가 없다**(legacy import·경로 금지를 재는 자리는 이미지 컨텍스트뿐) | 하네스 레인 소폭 — 금지 경로 술어를 의존·바이트코드 층에 거는 것이 선례와 같은 처방이다(「게이트 술어는 문자열이 아니라 구조로」). 오늘 거동 영향 0 |
| ⑤ | 조건 1 / 여정 e2e W-13 | 체크리스트 되돌림 단언의 **응답 포착 경합이 닫히지 않았다** — 홈을 거쳐 들어오면 같은 질의의 응답이 둘이고, 기다림을 **거는 순간**에도 앞선 재진입 응답이 날아오는 중일 수 있다. 그 spec 의 주석이 같은 증상을 이미 실측으로 적어 두었고 앞 라운드의 화면 상태 단언은 창을 **좁혔을 뿐** 닫지 못했다. 부하 높은 1회차에서만 발화 | **web 레인 소폭** — 포착을 「지우기 요청 자신의 응답」으로 못박는다(질의 URL·메서드로 거르거나, 지우기 호출의 응답을 직접 받는다). 거동 변경 0, test 전용. 조건 1 의 판정을 바꾸지 않지만 **상시 붉어질 수 있는 자리**라 남겨 두지 않는 편이 낫다 |

## §4 사실 선언

- ① **조건 1 실측** — 버릴 clone(`docs/m6-close` 의 clone, 공유 worktree 가 아니다)에서 빌드 자리 래퍼를 통해
  `./tools/one-command-check.sh` 를 돌렸다. 핵심 결과는 `commands.md` 가 정본이다. **전건의 종료 코드를 받을
  자리를 두지 않은 것은 이 레인의 실수**다(`nohup` 으로 띄우며 래퍼의 코드를 잡지 않았다) — 비-0 이라는 것은
  멈춘 자리와 완료 표지 부재로 알고, 표적 재실행에서는 코드를 잡았다.
- ② 이 slice 는 **코드·게이트·스키마·test·capability-map 을 편집하지 않는다**(판정이다). §3 의 넷은 전부 후속
  등재이고 이 브랜치에서 고치지 않았다.
- ③ `SET-01` 행의 판정(미충족)은 **운영자 결정 둘의 날짜 순서**에서 나온다 — 2026-10-22 가 착수를 미뤘고
  2026-10-24 가 그 행을 G1 8 에 그대로 두었다. 둘이 모순이라는 뜻이 아니라, **모수에서 빼는 결정이 따로
  필요하다**는 뜻이다(ML-01 은 그 결정을 받았고 SET-01 은 받지 않았다).
- ④ 조건 4·6·7 의 일부 근거는 M7/7F 의 리허설·실반입 기록이다(`scope.md` 판정 규칙이 허용한 자리). M6 조건
  문면과 어긋나는 부분은 위 표의 「좁혀진 부분」·「한정」 칸이 적는다.
- ⑤ `open-inventory.md` 는 M6 범위의 `OPEN-` 204건을 **기계로 추출**했으나 **건별 처분은 이 판정이 재지
  않는다** — 그 사유와 보정 대조 결과는 그 문서 §1 이 적는다.

## §5 사용자 승인

(PR 병합 시 기입)
