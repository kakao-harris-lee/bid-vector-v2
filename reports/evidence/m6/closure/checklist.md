# M6 종결 판정 — 완료 조건 아홉 대조 (2026-11-02)

**판정: 종결.** 계약(`scope.md`)의 판정 규칙대로 아홉을 **지금의 main 에서 다시 쟀다**(「당시 통과」를 근거로
쓰지 않는다). **사용자 결정·승인 2026-11-03**(§5)이 남아 있던 셋을 처분해, 결과는
**충족 6 · 좁혀짐 3 · 미측정 0 · 미충족 0** 이다.

좁혀진 셋은 전부 **결정을 인용한다**(`scope.md` 규칙).

- **조건 3** — 판정 대상이 **성립 15 + G1 7** 이다. 운영자 결정 2026-10-15·10-24 가 1차로 좁혔고,
  **사용자 결정 ①(2026-11-03)** 이 `SET-01` 을 `ML-01` 과 같은 꼴로 모수에서 분리했다. 남은 일곱은 전부
  닫는 slice 가 머지됐고, ⓐ 가 아닌 넷은 선언 예외(셋)와 ⓑ(하나)가 덮는다(§2).
- **조건 5** — 조건 문면의 **둘째 가지**(「사용자 승인된 명시적 예외」)로 선다. **사용자 결정 ②(2026-11-03)**
  가 「slice 표적 변이 실측으로 갈음한다」를 명시적 예외로 승인했다. 전수 자동 mutation 이 아니므로 범위가
  좁혀진 것으로 적는다.
- **조건 6** — 모델 version 축에 대상이 없다(운영자 「결정 ④ 개정 2026-09-27」의 보류).

충족 여섯 가운데 **조건 9** 는 verifier r2 `ready-for-review` 와 **사용자 release 승인 ③(2026-11-03)** 으로
닫혔다. 조건 1 은 충족이되 **필수 `check` job 이 main 에서 간헐적으로 붉다**(2026-10-31 이후 7 run 중 2) —
하네스 경합이고 §3 ⑤ 로 올린다. 이 결함은 승인으로 닫히지 않는다.

**M7 은 조건 3 이 성립하지 않은 채 종결됐다**(2026-11-02, PR #114·#115). 운영자 결정 2026-10-24 는 「조건 3 은
M7 종결 전까지 성립해야 한다」고 적었는데 그렇게 되지 않았다 — **사용자가 그 사실을 알고 승인했다**(결정 ③,
§4 ⑥). 결정 ① 이 모수를 조정한 것은 **승인 시점(2026-11-03)이고 M7 종결 시점을 소급하지 않는다.**

이 slice 는 판정이라 아무것도 고치지 않았다(D-M6C-3) — §3 의 후속 넷은 등재만 한다. **6G 는 판정하지
않는다**(D-M6C-1, 운영자 결정 2026-10-03 의 분리) — 「6G 종결」은 이 종결과 별개로 남고, 현재 상태는
`open-inventory.md` §3 이다.

## §1 완료 조건 아홉 ↔ 근거

| # | 완료 조건 | 판정 | 근거(정본 위치) |
| --- | --- | --- | --- |
| 1 | 새 checkout/clean database 에서 one-command build/test 가능 | **충족(한정 둘 — 장비 전제 · 필수 job 의 간헐 실패)** | **acceptance 정본은 판정 base `4d5d3d73` 의 main CI run** 이다 — 필수 `check` job 의 step 「one-command-check.sh — 완료 조건 1 (S-20)」이 **같은 스크립트 전건**(W-13 포함)을 돌고 **success** 다. 하네스가 「acceptance 는 CI 워크플로 job 의 명령 그대로」로 정하므로 그 run 이 자격 있는 기록이다(cr M-1). 레인의 버릴 clone 실행은 **보강 실측**이다: 작업 단계 25 가 순서대로 돌았고(Kotlin 2 · Python 10 · web 13) Kotlin `check` 통과(**test 태스크 tests 4,156 · 실패 0 · 건너뜀 4**, `compatibilitySmokeTest` 9 를 더하면 4,165) · Python 열 단계 · web W-12 까지 통과, **W-13 에서 비-0 으로 멈췄다**. **한정**: ⓐ 「새 checkout 에서 e2e 까지」는 Playwright 시스템 패키지가 있는 장비를 전제하고 여정 e2e 는 docker 를 전제한다(운영자 결정 2026-10-15 문면 · 7A 알려진 제한) ⓑ **그 필수 job 은 main 에서 간헐적으로 붉다** — 경합 수정 뒤 7 run 중 2(`957587c4` · `783817a5`)가 같은 W-13 자리에서 같은 서명으로 실패했다. 조건 1 의 문면(「가능」)은 서지만 **상시 초록은 아니다** → §3 ⑤. 잰 대상이 **Kotlin 전건 + Python 전건 + `web` 전건**인 것은 운영자 결정 2026-10-15(「완료 조건」 아래 결정 블록, 「조건 1(one-command)」)의 확장이다 |
| 2 | 기존 `bid-vector` import · symlink · DB schema 에 runtime dependency 없음 | **충족** | 저장소에 legacy symlink 가 없다(`bid-vector` 는 `.gitignore` 항목이라 작업 트리 밖 참조물이다). Kotlin·Gradle 소스의 `bid-vector` 문자열은 셋뿐이고 전부 **legacy 출처 주석·provenance 문자열**이다(`SampleEligibility`·`SampleConversion`·`LicensePolicy` 의 `legacy-behavior:` 줄) — import 0. `settings.gradle.kts` 의 `rootProject.name` 은 V2 저장소 자신의 이름이다. DB 는 V2 자신의 Flyway 이력이고 legacy schema 를 읽는 자리가 없다. **Python 축도 같다** — `ml-engine` 에는 legacy 출처를 적은 provenance docstring 이 있으나 legacy 패키지 import 는 0 이다(`git grep -nE '^\s*(from\|import) (app\|bid_vector)\b' -- '*.py'` → 0). 컨테이너 축은 6C 가 빌드 컨텍스트를 실측해 legacy 경로 0 을 단언했다(`6c/commands.md` 컨텍스트 전송 줄). **한정**: 상시 게이트는 이미지 컨텍스트 축뿐이고 **소스 축(Kotlin·Python 둘 다)은 이 판정의 재실측**이다 |
| 3 | M0 필수 capability E2E 전체 통과 | **좁혀짐** | 판정 대상은 **성립 15 + G1 7** 이다 — 운영자 결정 2026-10-15 · 2026-10-24 가 `V2 필수` 61 을 세 갈래로 가르고 `ML-01` 을 6G 종속으로 뺐으며(D-M6C-2), **사용자 결정 ①(2026-11-03)** 이 `SET-01` 을 같은 꼴로 분리했다(「ML-01 과 같은 꼴, 조건 3 판정 대상 = 성립 15 + G1 7, SET-01 은 `OPEN-SET01-AWARD-NAME-INGEST` 로 넘김」). 남은 **일곱은 닫는 slice 가 전부 머지됐고**(§2) ⓐ 가 아닌 넷 가운데 셋(`COL-03`·`DEC-05`·`DEC-02`)은 **운영자 선언 예외**가 덮으며 `DEC-10` 은 ⓑ(경계로 처리)다. `DEC-02` 의 전제 정정은 **결정 ③ 이 추인**했다(§3 ⑥). **성립 15** 는 2026-10-15 결정이 이름 붙인 집합이고 이 판정이 다시 세지 않는다 |
| 4 | 실제 외부 effect 없이 dry-run acceptance 가능 | **충족** | dry-run 전용 조립이 출하에 있다(6A-3+6F-3, PR #44 — ML 자리지킴·dry-run 전용). `bidvector.app.http.EvaluationDryRunE2ETest` · `EvaluationDryRunBidNowE2ETest` · `OpenApiDryRunContractTest` 와 조립 분리 게이트 `bidvector.app.architecture.DryRunCommitSeparationGateTest`. M7/7F 의 release·반입도 같은 축이다 — dry-run → 운영자 확인 → apply(`reports/evidence/m7/close/checklist.md` 1). **한정 — 컨테이너 축의 등식은 기대어 쓰지 않는다**: CI `container` job 의 왕복 step 이 outbox 전후 행 수 등식으로 「쓰지 않음」을 단언하지만, 6E-1 verifier 가 그 단언을 **공허**로 판정했다(후보 0 이라 커밋 포트로 바꿔치워도 초록). `OPEN-6E1-G4-NONVACUOUS`(후보 ≥1 seed — ML 배선 뒤)가 열려 있다. 그래서 「외부 effect 없음」의 정본은 **구조 게이트** `DryRunCommitSeparationGateTest` 이고, 등식은 보조다 |
| 5 | 중요 mutation 생존 0 또는 사용자 승인된 명시적 예외 | **좁혀짐** | 조건 문면의 **둘째 가지로 선다** — **사용자 결정 ②(2026-11-03)**: 「**표적 변이로 갈음 승인** — 명시적 예외, `OPEN-ADR-07` 은 후속」. 첫째 가지(「생존 0」)는 전수 모수가 없어 여전히 말할 수 없으므로, 충족이 아니라 **범위가 결정으로 좁혀진 것**으로 적는다(`scope.md` 규칙의 결정 인용은 이 결정이다). 아래가 그 예외의 재료다. **자동 mutation 도구는 채택되지 않았다**(`OPEN-ADR-07`, M1 소유 · `docs/adr/0007` 「`OPEN-ADR-07` · mutation 도구」). 그 자리를 하네스가 **slice 표적 변이 실측**으로 대신한다(`CLAUDE.md` 검증 절 「verifier 의 최고 가치는 우회 고안·변이 실측이다」). G1 다섯 slice 전부 변이 기록을 보유한다 — `g1t/checklist.md` 변이 절(바꿔치기·`numstat`·통제군), `g1dec10/checklist.md` 의 우회 ↔ 막는 자리 대응표 열 행(M·V·N·e 계열 변이가 전부 RED), `g1c03`·`g1set01`·`g1dec05` 각 checklist. **남은 빈자리**: 「중요 mutation」의 **전수 모수**가 정의된 적이 없고 저장소 전건 자동 mutation 은 측정된 적이 없다 → `OPEN-ADR-07` 이월 |
| 6 | 계약/모델/policy version 으로 결과 재현 | **좁혀짐** | 계약 축: proto 계약 게이트와 OpenAPI 생성 타입 **바이트 등식**(CI `web` job 의 생성 타입 check). policy 축: 정책이 versioned data 이고 `PolicyVersion` 이 출처를 나른다 — `bidvector.settlement.AwardMatchingPolicyDataTest` · `bidvector.decision.menu.BidMenuPolicyDataTest` · `bidvector.app.policy.FloorTrustBandParityTest`(Kotlin 출하 밴드 ↔ Python 백테스트 값 일치). 실행 축: `bidvector.adapters.e2e.PipelineReproducibilityE2ETest`(같은 입력이면 같은 답). **좁혀진 부분 — 모델 version 축에 대상이 없다**: 출하 조립이 `MlAnalysisPort` 를 자리지킴으로 고정해(D-6A3-2) 모델 release 로 결과가 갈리는 production 경로가 0 이다. **범위를 조정한 결정은 「결정 ④ 개정 2026-09-27」**(운영자 — `OPEN-ML-ANALYSIS-WIRING` 배선을 가격 경로 백테스트 뒤로 보류)이고, 그 보류를 2026-10-03 결정이 「6G 종결」로 분리했다. 홀더 `OPEN-ML-ANALYSIS-WIRING` |
| 7 | rollback/restore rehearsal 증거 존재 | **충족** | M6/6B-2(PR #61) 가 백업·복원·마이그레이션 되돌림 리허설을 냈다 — 수용 기준 D-1~D-6 판정(`6b2/checklist.md`): 백업 산출물 비밀값 0(참조형 스캔) · 복원 뒤 등식 셋(Flyway 이력 · 표별 행 수·내용 해시 · 축9 권한 행렬) · 되돌림 (i)(ii) exit 0. CI `container` job 에 같은 리허설 step 이 상주한다. 착수 당시 비어 있던 「CI 초록」은 그 뒤 PR 체크가 닫았다. M7 축이 같은 조건을 운영 host 에서 겹쳐 잰다 — 7F(PR #112)가 백업·복원 리허설과 반입·되돌림 스크립트를 냈고(이미지 되돌림 ≠ DB 되돌림을 스크립트가 판별·정지한다), 7F-3(PR #113)이 승인 전 안전 후속을, 실반입 기록이 dry-run → apply 를 적는다 |
| 8 | 보안·비밀값 스캔 통과(조건 문면의 어휘는 축어로 적지 않는다 — 누출 스캔 어휘와 겹친다) | **충족** | 비밀값 축: `leakPatternGate`(참조형 어휘 파일, Kotlin `check` 안). 이미지 축: 위생 게이트 + **trivy SBOM·CVE 스캔**이 CI `container` job 에 상주하고, 그 채택이 `OPEN-6C-IMAGE-VULN-SCAN` 을 **해소**했다(6E-2b, `6e2b/checklist.md` — 「흉내가 아닌 스캔임을 재는 술어 여섯」). 실반입 축: M7/7F 가 위생 4/4 · 취약점 4/4(trivy 핀) · allowlist 등재 0 을 기록. **한정**: 이 장비에 `privacy-gate` 정의가 없어 **이름 붙은 개인정보 판정은 없다** — M7/7D 가 verifier 표적으로 대체를 선언했고(`m7/close/checklist.md` 조건 표) 그 한정은 M6 조건 8 문면 밖이다(조건 8 은 보안·비밀값 축이고 개인정보 축은 M7 문면이다) |
| 9 | verifier 최종 `ready-for-review` + 사용자 release 승인 | **충족** | 운영자 결정 2026-10-03(「다음 마일스톤」 절)이 이 조건의 모수를 **6G 를 제외한 M6 산출물**로 정했다. 선행이던 「7C② 의 『아직 산출 안 함』 상태가 선 뒤」(운영자 결정 2026-10-24)는 7E-1(PR #102)·7C②(PR #94·#103)로 섰다. **두 가지가 모두 섰다** — ⓐ verifier r2 **`ready-for-review`**(판정 SHA `37f5aee8`, high·medium 0 · 남은 low 셋은 장부층) ⓑ **사용자 release 승인 ③(2026-11-03)**. ⓐ 뒤의 커밋 둘은 그 low 셋의 조치와 이 결정 반영이고 **전부 판정 문서**다 — 코드·게이트·test 변경 0 이라 verifier 가 잰 대상이 움직이지 않았다(§4 ⑦) |

「근거 없음」 0. 조건 아홉 전부에 담당 근거가 있다.

## §2 조건 3 — G1 행별 판정 (모수 7 + 분리된 1)

**모수는 G1 7 이다**(사용자 결정 ①, 2026-11-03). 아래 표는 여덟 행을 그대로 두되 `SET-01` 은 **모수 밖**으로
표시한다 — 그 행의 실측은 분리 결정의 근거이므로 지우지 않는다.

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
| `SET-01` **(모수 밖 — 결정 ①)** | **#85**(M6/G1-SET01, 2026-10-23) | **ⓒ** | `bidvector.settlement.AwardMatchingKernelTest` · `CompanyNameNormalizationTest` · `AwardOutcomeRecordingTest` · `AwardMatchingPolicyDataTest` · `CompileFailureHarnessTest`(금액 인자를 더한 호출이 **컴파일되지 않는다**) | **모수 밖(결정 ① — 그 전까지는 「미충족 · 선언 예외 없음」이었다).** 판정 커널은 섰고 운영자 **상호 입력** 칸은 M7/7C④(PR #90·#104·#106)가 채웠다. 남은 빈자리는 **낙찰자 상호의 canonical 적재** 하나다 — `bidwinnrNm` 은 낙찰 목록 끝점에만 있고 6G 는 그 끝점을 걷지 않는다. 운영자 결정 2026-10-22 이 (a) 적재를 **승인**했으나 「착수는 6G 라이브 수집 종료 뒤」로 미뤘고, **이틀 뒤 2026-10-24 결정은 `SET-01` 을 G1 8 에 그대로 두었다**(ML-01 처럼 모수에서 빼지 않았다). 그 2026-10-22 결정의 문면은 **`milestone-6.md` 에 없다** — 정본은 `g1set01/checklist.md` 처분표 P-23 행과 `docs/discovery/capability-map.md` 의 그 OPEN 행이다. **처분(사용자 결정 ①, 2026-11-03)**: 이 행을 `ML-01` 과 같은 꼴로 **조건 3 모수에서 분리**하고 `OPEN-SET01-AWARD-NAME-INGEST` 로 넘긴다. 그래서 조건 3 의 판정 대상은 **성립 15 + G1 7** 이고, 이 행은 그 OPEN 이 받는다(`open-inventory.md` §2) |
| `DEC-05` | **#93**(M6/G1-DEC05, 2026-10-27) | **ⓒ 유지** | `bidvector.decision.menu.BidMenuKernelTest` · `MenuCompileFailureHarnessTest` · `BidMenuPolicyDataTest` · `bidvector.workflow.prediction.StanceRateMappingTest` · `bidvector.app.http.BidPriceRequestResponseTest`(7C②) | **좁혀짐(선언 예외).** 운영자 결정 2026-10-24(「완료 조건」 아래 결정 블록, docs PR #88): 「**`DEC-05`·`DEC-02` 는 추적표 ⓐ 술어 ② 의 선언 예외로 판정한다**」 — 판정 근거는 「커널·구조 성립 + 화면의 『아직 산출 안 함』 상태 표시가 test 로 잠김」이고, **값 출처 축과 그 값에서만 생기는 축**은 `ML-01` 과 함께 6G 백테스트 판정 뒤로 간다. 상태 표시는 7C②(PR #94)가 세웠다 |
| `DEC-02` | **#93** | **ⓒ 유지** | `bidvector.decision.menu.BidMenuKernelTest`(binding test 일곱) · `bidvector.sharedkernel.ArithmeticTest` · `CompileFailureHarnessTest` · `bidvector.app.http.BidPriceRequestResponseTest` | **좁혀짐(선언 예외).** 같은 2026-10-24 결정이 덮는다. 그 결정 블록이 요구한 확인(「`roundedWith` 의 하한 없는 호출이 메뉴 값 종속인지 실측해 행에 적는다. 종속이 아니면 그 빈자리는 이 예외 밖」)은 **수행됐고 결과가 적혔다** — 남은 빈자리는 「출하에 제출용 투찰 금액을 내는 경로가 없다」이고 **메뉴·`ML-01` 에 종속**이라는 것이 그 답이다(이분법의 「종속」 가지). 함께 전제 정정이 하나 있다 — 결정이 지목한 `roundedWith` 호출부는 비율↔금액 왕복의 중간값이라 **애초에 하한이 적용될 자리가 아니었다**. 점수 축의 물음은 별 건 `OPEN-DEC02-SCORE-AXIS-FLOOR` 로 갈라 **DEC-02 의 ⓐ 전환을 막지 않는다**고 추적표 DEC-02 행이 적는다. 그 전제 정정의 운영자 추인은 §3 ⑥ |
| `DEC-10` | **#99**(M6/G1-DEC10, 2026-10-29) | **ⓒ → ⓑ** | `bidvector.decision.PublishedFloorTrustTest` · `FloorTrustCompileFailureHarnessTest` · `bidvector.app.architecture.FloorTrustGateTest` · `FloorTrustGateCatchesViolationsTest` · `bidvector.app.policy.FloorTrustBandParityTest` · `bidvector.workflow.evaluation.FloorExclusionChainTest` | **충족(경계로 처리).** 운영자 결정 2026-10-24(D-DEC10-1)가 개연 밴드 `[0.30, 0.995]` 양 끝 포함을 Kotlin 출하 정책 데이터로 확정했다. **게이트 자체는 배선 전에 구조로 닫혔다** — 통과 값이 원값과 **다른 타입**이고 생성 경로가 게이트 함수 하나다(바이트코드 등식 + 컴파일 실패 fixture). ⓐ 가 아닌 이유는 세 가격 경로가 출하 배선에서 돌지 않는다는 실측이고 홀더는 `OPEN-ML-ANALYSIS-WIRING` 이다. `OPEN-DEC-10` 은 이 slice 가 닫지 않는다 — 그 OPEN 이 소유하는 것은 **DEC-03 법정 하한율 표 값**이다 |

**계수(모수 G1 7)**: ⓐ 3(`COL-02`·`DEC-06`·`OPS-01`) · ⓑ 1(`DEC-10`) · 선언 예외로 좁혀진 ⓒ 3
(`COL-03`·`DEC-05`·`DEC-02`) = **7**. **선언 예외 없는 잔여 0.** 모수 밖 1(`SET-01` — 결정 ①).

**성립 15** 는 2026-10-15 결정이 이름 붙인 집합이고 이 판정이 다시 세지 않는다(그 모수의 정본은 추적표이며,
`V2 필수` 모수가 61 → 62 로 움직인 것은 M7/7B 의 `BID-01` 등재다 — 성립 15 의 수는 불변이다).
G1 의 수는 **10 → 9**(2026-10-15 Q-2, DEC-03 을 G2 로) **→ 8**(2026-10-24, ML-01 분리)
**→ 7**(2026-11-03 결정 ①, SET-01 분리)로 움직였고, 세 번 모두 **승인된 결정**이 근거다.

## §3 처분과 후속 (이 slice 는 고치지 않는다 — D-M6C-3)

**①②⑥ 은 사용자 결정 2026-11-03 으로 처분됐다**(각 행의 「처분」 칸). **남아서 넘기는 것은 ③④⑤ 셋**이고
그 가운데 **⑤ 만 오늘 게이트를 붉힌다**.

| # | 자리 | 무엇이 비었나 | 제안 · 처분 |
| --- | --- | --- | --- |
| ① | 조건 3 / `SET-01` | 낙찰자 상호의 canonical 적재(`OPEN-SET01-AWARD-NAME-INGEST`). 운영자 결정 2026-10-22(정본은 `g1set01/checklist.md` P-23 · capability map OPEN 행)이 (a) 를 승인하면서 착수를 「6G 라이브 수집 종료 뒤」로 미뤘고, **6G 라이브 수집은 2026-10-29(day28)에 총예산 소진으로 끝났다**(`open-inventory.md` §3) — 그 전제는 이미 도달했다 | **사용자 결정 둘 중 하나**: (a) `ML-01` 과 같은 꼴로 `SET-01` 을 G1 8 에서 빼 6G 종결 뒤 판정으로 옮긴다 · (b) 낙찰 목록 끝점 적재 slice 를 열어 닫는다(**새 외부 호출**이라 범위·쿼터 승인 대상). **추천 (a)** — `DEC-05`·`DEC-02`·`ML-01` 이 이미 같은 사유(출하 배선 부재)로 분리됐고, (b) 는 수집 쿼터 재신청에 종속이다. **처분 — 사용자 결정 ①(2026-11-03): (a) 채택.** 「ML-01 과 같은 꼴, 조건 3 판정 대상 = 성립 15 + G1 7, SET-01 은 `OPEN-SET01-AWARD-NAME-INGEST` 로 넘김」. ⑥ 의 추인도 함께 받았으므로 **조건 3 = 좁혀짐**이 섰다. 남는 일은 그 OPEN 이 받는 **적재 slice** 이고 **새 외부 호출이라 범위·쿼터 승인이 별도로 필요하다** — 이 승인이 그것을 허용하지 않는다 |
| ② | 조건 5 | 조건 문면의 두 가지 중 어느 쪽도 서지 않았다 — 「생존 0」은 전수 모수가 없어 말할 수 없고, 「사용자 승인된 명시적 예외」는 그 **승인이 없다**(`OPEN-ADR-07`, M1 소유·slice 미지목) | **처분 — 사용자 결정 ②(2026-11-03): 승인.** 「표적 변이로 갈음 승인 — 명시적 예외, `OPEN-ADR-07` 은 후속」. 조건 5 는 그 둘째 가지로 **좁혀짐**이 섰다. **`OPEN-ADR-07`(도구 채택)은 닫히지 않고 후속으로 남는다** — 승인된 것은 갈음이지 전수 측정이 아니다. 받는 자리는 그 OPEN(M1 소유·slice 미지목) |
| ③ | 조건 6 | 모델 version 축에 대상이 없다 — 출하 조립이 ML 분석 port 를 자리지킴으로 고정 | `OPEN-ML-ANALYSIS-WIRING` 배선 slice 가 함께 받는다(그 slice 의 acceptance 에 `ML-02`·`ML-04` 재판정이 이미 걸려 있다 — 운영자 결정 2026-10-24) |
| ④ | 조건 2 | 소스 축에 **상시 게이트가 없다**(legacy import·경로 금지를 재는 자리는 이미지 컨텍스트뿐) | 하네스 레인 소폭 — 금지 경로 술어를 의존·바이트코드 층에 거는 것이 선례와 같은 처방이다(「게이트 술어는 문자열이 아니라 구조로」). 오늘 거동 영향 0 |
| ⑤ | 조건 1 / 여정 e2e W-13 | **필수 `check` job 을 간헐적으로 막는 현행 결함이다 — 가설이 아니다.** 체크리스트 되돌림 단언의 응답 포착이 질의 URL·메서드로만 걸러, 재진입 때 걸린 **앞선 응답**을 잡는다. 그 spec 의 주석이 같은 증상을 이미 실측으로 적어 두었고 앞 라운드의 화면 상태 단언은 창을 **좁혔을 뿐** 닫지 못했다. **CI 발화 실측**: 경합 수정 뒤 main push run 7 중 2 가 `check` job 의 S-20 step 에서 실패했다 — `783817a5`(M7 종결 머지 자신, 2026-11-02)와 `957587c4`(2026-10-31), 둘 다 `onboarding.spec.ts` 여정 ①·같은 받은 배열. 후자는 **여정 ③(`journey.spec.ts`)도 함께** 붉었다 | **web 레인 — 우선순위 높음**(「소폭」이 아니다). 포착을 「지우기 요청 자신의 응답」으로 못박는다(지우기 호출의 응답을 직접 받거나 요청 식별로 거른다). 거동 변경 0, test 전용. 조건 1 의 판정을 바꾸지는 않지만 **상시 붉은 게이트도, 안 돌린 게이트도 아무것도 막지 못한다** — 필수 job 이 간헐적으로 붉으면 다음 붉음이 진짜인지 아닌지 아무도 모른다. 여정 ③ 의 1회 실패도 같은 자리에서 등재한다 |
| ⑥ | 조건 3 / `DEC-02` (비차단) | 2026-10-24 결정은 이분법을 걸었다 — 「`roundedWith` 의 하한 없는 호출이 메뉴 값 **종속인지** 실측해 적는다. **종속이 아니면** 그 빈자리는 예외 밖이고 DEC-02 는 조건 3 에서 따로 닫아야 한다」. 추적표는 **「종속」 가지에 답했다** — 남은 빈자리는 「출하에 제출용 투찰 금액을 내는 경로가 없다」이고 **메뉴·`ML-01` 에 종속**이라고 적는다. 다만 그 답에 이르는 길에 **전제 정정**이 하나 있다: 결정이 지목한 `roundedWith` 호출부는 애초에 빈자리가 아니었다(비율↔금액 왕복의 중간값이라 하한이 적용될 자리가 아니다). 논증은 그 slice 의 r2 L-5 리뷰를 지났으나 **운영자가 그 전제 정정을 추인한 기록은 없다** | **처분 — 사용자 결정 ③(2026-11-03): 추인.** release 승인 문면이 「DEC-02 처리 방식 추인(남은 빈자리를 메뉴·`ML-01` 종속)」을 담았다. 그래서 `DEC-02` 의 선언 예외는 전제 정정까지 **승인된 근거** 위에 선다. 받는 자리는 `OPEN-ML-ANALYSIS-WIRING`(값 출처 축) 그대로다 |

## §4 사실 선언

- ① **조건 1 의 acceptance 정본은 CI run 이고 레인 실행은 보강이다**(cr M-1). 레인은 버릴 clone
  (`docs/m6-close` 의 clone, 공유 worktree 가 아니다)에서 빌드 자리 래퍼로 전건을 돌렸고 그 실행은 W-13 에서
  비-0 으로 끝났다. **전건의 종료 코드를 받을 자리를 두지 않은 것은 이 레인의 실수**다(`nohup` 으로 띄우며
  래퍼의 코드를 잡지 않았다) — 비-0 이라는 것은 멈춘 자리와 완료 표지 부재로 안다. 핵심 결과는 `commands.md`.
- ②' **r1 에서 고친 사실 셋.** 초판은 W-13 실패를 「재현되지 않는다 · 부하 높은 1회차에서만 · 같은 base CI
  초록」으로 적었다. **셋 다 틀렸다** — CI 러너에서 7 중 2 로 재현되고(§3 ⑤), 「같은 base 초록」은 맞지만
  **바로 앞 main 커밋의 붉은 run 이 빠져 있었다**. 표본 하나(레인 1회차)로 「재현되지 않는다」를 말한 것이
  오류의 뿌리다. 원인 판단(제품 결함이 아니라 하네스 경합)은 r1 검증에서도 유지됐다.
- ② 이 slice 는 **코드·게이트·스키마·test·capability-map 을 편집하지 않는다**(판정이다). §3 의 여섯은 전부
  후속·결정 등재이고 이 브랜치에서 고치지 않았다.
- ③ **이 판정이 `SET-01` 을 미충족으로 올린 근거는 운영자 결정 둘의 날짜 순서였다** — 2026-10-22 가 착수를
  미뤘고 2026-10-24 가 그 행을 G1 8 에 그대로 두었다. 둘이 모순이라는 뜻이 아니라, **모수에서 빼는 결정이
  따로 필요하다**는 뜻이었다(`ML-01` 은 그 결정을 받았고 `SET-01` 은 받지 않았다). **사용자 결정 ①
  (2026-11-03)이 그 빠진 결정을 했다** — 그래서 지금의 판정은 좁혀짐이고, 이 항목은 그 경로의 기록으로 둔다.
- ④ 조건 4·6·7 의 일부 근거는 M7/7F 의 리허설·실반입 기록이다(`scope.md` 판정 규칙이 허용한 자리). M6 조건
  문면과 어긋나는 부분은 위 표의 「좁혀진 부분」·「한정」 칸이 적는다.
- ⑤ `open-inventory.md` 는 M6 범위의 `OPEN-` 204건을 **기계로 추출**했으나 **건별 처분은 이 판정이 재지
  않는다** — 그 사유와 보정 대조 결과는 그 문서 §1 이 적는다. 모수가 문서에 따라 움직이므로 추출 명령을
  **판정 base 트리에 고정**했다(그 문서 §1).
- ⑥ **M7 은 조건 3 이 성립하지 않은 채 종결됐다 — 사용자가 그 사실을 알고 승인했다**(결정 ③). 운영자 결정
  2026-10-24 블록은 「조건 3 은 **M7 종결 전까지** 성립해야 한다」고 적었는데, M7 종결(2026-11-02,
  PR #114·#115) 시점에 `SET-01` 은 닫히지 않은 상태였다. M7 각 slice 의 착수가 운영자 예외로 열린 것과는
  **다른 사안**이다 — 그 예외는 착수를 허락했지 조건 3 의 성립 시점을 옮기지 않았다. **결정 ① 의 모수 조정은
  2026-11-03 자이고 M7 종결 시점을 소급하지 않는다** — 즉 조건 3 은 「지금 섰다」이지 「그때도 섰다」가 아니다.
- ⑦ **verifier 판정 SHA 와 이 문서의 HEAD 는 다르다**(정상). verifier r2 `ready-for-review` 는 `37f5aee8`
  에 대한 것이고, 그 뒤 커밋은 ⓐ r2 low 셋 조치 ⓑ 이 결정 반영 둘이다. **둘 다 이 판정 문서만 고치고**
  코드·게이트·test·정책 값은 건드리지 않았다(`git diff --name-only 37f5aee8..HEAD` ⊆ closure evidence +
  `milestone-6.md` 종결 문단). 그래서 verifier 가 잰 대상은 움직이지 않았다.

## §5 사용자 결정·승인 2026-11-03

판정이 올린 넷(§3 ①②⑥ · §4 ⑥)에 대한 답이다. 받은 문면을 축어로 적는다.

| # | 물음 | 답 | 효과 |
| --- | --- | --- | --- |
| ① | 조건 3 — `SET-01` 을 모수에서 분리할지(추천 (a)), 적재 slice 를 열지 | **「모수에서 분리」** — `ML-01` 과 같은 꼴 · 조건 3 판정 대상 = **성립 15 + G1 7** · `SET-01` 은 `OPEN-SET01-AWARD-NAME-INGEST` 로 넘김 | 조건 3 **미충족 → 좁혀짐** |
| ② | 조건 5 — 표적 변이 갈음을 명시적 예외로 승인할지 | **「표적 변이로 갈음 승인」** — 명시적 예외 · `OPEN-ADR-07` 은 후속 | 조건 5 **미충족 → 좁혀짐** |
| ③ | M6 release 승인(+ `DEC-02` 추인 · M7 사실 인지) | **「승인」** — `DEC-02` 처리 방식 추인(남은 빈자리를 메뉴·`ML-01` 종속으로 둔다) · 「M7 이 조건 3 미성립 상태로 종결」 사실 인지 포함 | 조건 9 **미충족 → 충족** · §3 ⑥ 처분 · §4 ⑥ 인지 |

**이 승인이 허용하지 않는 것**(`milestone-6.md` 「완료 후 별도 승인 사항」 절 그대로): 실 KONEPS·LLM·
Telegram·email 호출 · 운영 DB 또는 credential 사용 · public 배포 · 기존 `bid-vector` 중지·삭제 · 원격
merge/push. **`SET-01` 적재 slice 의 새 외부 호출과 쿼터도 별도 승인 대상**이다(§3 ①). **`OPEN-ADR-07`
(mutation 도구)도 닫히지 않는다** — 승인된 것은 갈음이지 전수 측정이 아니다(§3 ②).

**6G 종결은 이 승인에 들어 있지 않다** — 운영자 결정 2026-10-03 의 분리가 그대로이고 백테스트 판정은
아직 없다(`open-inventory.md` §3).
