# M9 / gates-tests — adapters test 술어 · 판정 타입 가시성
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천 `df4976cc`, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음.

## 대상
| OPEN | 처방 |
| --- | --- |
| `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` | `PersistenceAdapterDependencyTest` 의 소스 텍스트 import 정규식을 형제 여덟과 같은 **상수 풀 술어**로 이식 |
| `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS` | `CleanMigrationCheckTest` 의 `checkBodies.any { contains }` 셋을 같은 파일 선례의 「표 범위 본문 집합 등식」으로, `queryConstraintDef` 0 행이면 명시 실패 |
| `OPEN-VERDICT-CONSTRUCTION-VISIBILITY` | `qualification` 의 `LicenseVerdict` subtype 생성자를 `internal` + `@ConsistentCopyVisibility`; test 조립은 testFixtures factory 로 |
| `OPEN-6F4W-UNAVAILABLE-PRODUCER` | `WatchSubjectUnavailable` 의 production 생산자 0 — `NoticeWatchSubjectPort` 가 그 갈래를 실제로 낼 수 있는 경로를 세우거나(조회 실패를 값으로), 불가하면 어휘 정리 판단을 evidence 에 |

## in_scope
`adapters/src/test/kotlin/**`(해당 test) · `qualification/**` · 커널 둘·testFixtures · `adapters/src/main/kotlin/bidvector/adapters/evaluation/NoticeWatchSubjectPort.kt`(+test) · `config/quality/gate-tests.properties` · 해당 OPEN 문서 행 · evidence
## acceptance
각 술어 양성·음성 대조, 바꿔치우기 변이 RED. 전건 PR CI.

## 계약 갱신 2026-11-06 (팀장)

- **항목 3(`OPEN-VERDICT-CONSTRUCTION-VISIBILITY`)은 이 slice 에서 뺀다(①).** 처방 「test 조립은 testFixtures factory 로」가 저장소 전역 차단과 충돌한다 — build-logic conventions 가 `java-test-fixtures` 를 전 모듈에서 거부하고 그 자리가 `OPEN-1BC-TESTFIXTURES-GATE`(「면제·순수성 규칙을 ADR 로 먼저 정한 뒤에 연다」)를 가리킨다 · `module.expected-source-sets=main,test` · `sourceSetLayoutGate` 의 compile task 집합 · `TestFixturesGateTest`. 그리고 `internal` 은 testFixtures source set 에서도 보이지 않아 `associateWith` 배선이 필요하다. 생성자를 `internal` 로 내리면 **`qualification` 모듈 밖 test 열여섯 파일**이 컴파일되지 않는다. ADR 없이 여는 것은 그 OPEN 의 우회다.
- 받는 자리: **testFixtures 개방 ADR slice**(`OPEN-1BC-TESTFIXTURES-GATE` 선행) — ADR + build-logic + 정책 + **16 test** 가 한 묶음. capability map 의 OPEN 행 갱신은 M9 장부 후속이 받는다.
- 항목 4 의 필드 축을 `EvaluationAdapterDependencyTest` 에 둔 것은 in_scope 「(+test)」 읽기로 승인.
- **영향 16 test 파일**(v-134 L-1 시정 — 앞 판의 「14」는 *이 slice 의 in_scope 밖*만 센 수였고, 받는 slice 가 고쳐야 하는 수는 **`qualification` 모듈 밖 전수**다. 모집단 술어: `git grep -ln 'LicenseVerdict\.\(Eligible\|Ineligible\|Uncertain\)(' -- '*.kt' | grep -v '^qualification/'). 받는 slice 가 이 목록을 입력으로 쓴다.
  - `app/src/test` **열**: `evaluation/`{`DryRunCommitParityTest`·`FloorExclusionTallyTest`} · `http/`{`BidPriceRequestResponseTest`·`BidPriceRequestTestSupport`·`HttpTestSupport`·`OpenApiBidPriceVocabularyTest`·`OpenApiSuggestionContractTest`·`StrategyBudgetClearEndpointTest`·`SuggestionEndpointTest`} · `wiring/EvaluationCommitWiringTest`
  - `workflow/src/test` **넷**: `evaluation/`{`EvaluateCandidatesScreeningCharacterizationTest`·`EvaluateCandidatesUseCaseTest`·`EvaluationTestFixtures`} · `suggestion/ListSuggestionsUseCaseTest`
  - `adapters/src/test` **둘**(L-1 이 지목한 자리 — 이 slice 의 in_scope 안이지만 **항목 3 을 받는 slice 가 고친다**): `qualification/StoredRequirementLicenseGateTest` · `ml/UnavailableMlAnalysisTest`
  - **세지 않은 둘**: `qualification/src/test` 의 둘(같은 모듈이라 `internal` 이 보인다) · `adapters/.../qualification/QualificationAdapterDependencyTest`(match 가 KDoc 두 줄뿐 — 컴파일되지 않는다). `copy()` 사용은 전 저장소 **0** 이라 `@ConsistentCopyVisibility` 가 더 넓히는 파일은 없다(실측).
- 항목 2 의 DB 단언은 **팀장 승인(2026-11-06)으로 로컬 Testcontainers 를 `CleanMigrationCheckTest` 한 클래스에 한해 실행**해 실측했다(앞 줄의 「첫 실측은 PR CI」를 그 승인이 대체한다) — 결과는 commands.md.
