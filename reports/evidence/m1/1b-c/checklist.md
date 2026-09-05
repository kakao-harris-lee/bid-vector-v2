# checklist — M1 / 1B-c

④ 레인(conformance runner)이 슬라이스 마감에 쓴다(`fixtures.md` 「이 파일은 corpus 레인
(①②③) 전용이다」 — checklist·rollback 은 이 레인 몫). 출력 전문·라운드 이력 절 없음
(`evidence-pack` 규격).

## M1 완료 조건 대조 — 「승인된 authoritative corpus 전체 통과」의 1B 축

`milestone-1.md` §완료 조건 — *"승인된 authoritative corpus 전체 통과"*. 1B 축(`rate-unit`·
`money-basis`)은 착수 시점 열하나 전부 `insufficient-evidence` 라 이 조건의 1B 모집단이
비어 있었다(`reports/evidence/m1/1b/fixtures.md` §1). 이 slice 뒤:

- **authoritative 10 / insufficient-evidence 1**(`money-basis-003`, `OPEN-1BC-STR16` 이월 —
  검색 경로 타입이 계약에 없어 이 slice `out_of_scope`).
- **10 case 전부 `check` 안에서 실제 실행되고 대조된다** —
  `bidvector.app.conformance.SharedKernelCorpusConformanceTest`(`gate.tests.app` 등재,
  `gateExecutionGate` 강제). Python 스윕(C-5~C-10)은 계약의 **강건성**(위반 변이체가
  통과하지 않는다)만 재고, V2 산출을 기대값과 대조하는 것은 이 test 가 유일하다.
- **판정: 1B 축은 이제 「승인된 authoritative corpus 전체 통과」를 처음으로 충족으로 적을
  수 있다** — 단 아래 「알려진 제한」의 사각(`OPEN-1BC-ELIGIBILITY`·`OPEN-1BC-SOURCE-UNIT`
  ·`OPEN-1BC-TESTFIXTURES-GATE`·`OPEN-1BC-STR16`)은 이 판정 밖의 별도 축이고 각자의
  이관처가 닫는다.

## executor 종류 대조 (case 별)

| case | executor | verified_paths(최종, `6ef8fb5`) | 부르는 것 |
| --- | --- | --- | --- |
| rate-unit-001 | `Rate` 값 동등(`RATE_EXECUTORS`) | `$.rate.fraction` | `Rate.ofPercent(87.5)` |
| rate-unit-002 | 〃 | `$.rate.fraction` | `Rate.ofFraction(0.875)` |
| rate-unit-005 | 〃 | `$.rate.fraction` | `Rate.ofPercent(0.875)` |
| rate-unit-003 | compile-fixture 12 위임 | `$.representable` | fixture 12(이 레인이 신설) |
| rate-unit-004 | compile-fixture 12 위임 | `$.representable` | fixture 12 |
| money-basis-001 | compile-fixture 11 위임 | `$.representable` | fixture 11(1B 기존) |
| money-basis-004 | compile-fixture 11 위임 | `$.representable` | fixture 11 |
| money-basis-002 | value(`VALUE_EXECUTORS`) | `$.fact`, `$.comparedBases` | `compareKnownVat(BaseAmount, BaseAmount)` |
| money-basis-005 | value | `$.fact`, `$.reasonCode` | `compareKnownVat` → `Absent(VAT_TREATMENT_MISMATCH)` |
| money-basis-006 | value(**선언 값 왕복** — verifier r1 M-1 처리 뒤) | `$.vatTreatment`, `$.provenance` | 입력이 **명시 선언**한 미상 두 값으로 `BaseAmount` 구성 → 되읽기 |
| money-basis-003 | 대상 아님(insufficient-evidence) | — | 전용 filter test 가 집합이 정확히 이 하나임을 확인 |

**dispatch 표 완결성**은 `dispatch 표 밖의 authoritative case 가 없다`(전용 test)가
기계로 확인한다 — 새 case 가 authoritative 로 올라와도 표에 없으면 그 case 자신과 이
전용 test 둘 다 FAILED(변이 실측 ②, `commands.md`).

**`verified_projections` 술어 넷(is-present·differs-from-path·differs-from-case·
not-equals)은 이 runner 에 없다** — 최종 committed manifest(`6ef8fb5`) 의 10 case 어느
쪽도 `verified_projections` 를 쓰지 않아(전부 `verified_paths` 정확 비교로 좁혀짐, curator
정정) 구현할 대상 자체가 없었다. 팀장 확인(대화 로그) — 구현 누락이 아니라 계약 자체의
사실이다.

## verifier r1 finding 처리 (`_workspace/m1-1b-c/02_verifier_r1.md`, verdict ready-for-review)

runner 몫(M-1~M-3, L-1~L-3)을 이 slice 가 처리했다. M-4·M-5(scope.md·capability-map 계약
갱신)는 팀장이 처리했다(`75b912d`) — 여기는 포인터만 잡는다.

| id | 요지 | 처리 | 커밋 |
| --- | --- | --- | --- |
| M-1 | `money-basis-006` 의 두 `verified_path` 를 계약이 아니라 runner 의 null→UNKNOWN/ Undeclared 접기가 정하고 있었다 | curator 가 입력을 명시 선언(`UNKNOWN`·`Undeclared`)으로 정렬, runner 는 접기를 없애고 명시 선언을 요구(없으면 `require` 실패) — **선언 값 왕복**으로 성질을 좁혔다. **compile fixture 7 위임은 걷었다** — curator 재실측(`fixtures.md` §7-8)으로 fixture 7(`vat-fixed-money-no-vat-arg`/`explicit-vat`)이 `YegaAmount` 의 명시 VAT 인자 거부를 재는 것이지 `BaseAmount` 의 `vatTreatment`/`provenance` 기본값 부재를 재지 않음이 드러났다(둘 다 `YegaAmount` 생성자를 부른다 — `BaseAmount` 가 아니다). **`BaseAmount(won, currency)` 가 컴파일 실패함을 재는 fixture 는 지금 없다** — 새 fixture 신설은 이 slice `out_of_scope` 라 알려진 제한(아래)으로만 등재한다 | `58a3c6e`(curator 입력) · `00434fb`(runner 접기 제거) · `ae0d9ad`(fixture 7 위임 철회) |
| M-2 | `rate-unit-001`·`002`·`005` 의 percent/fraction 갈래가 case 별 하드코딩이라 `rate-unit-005` 의 "선언이 개연성을 이긴다"를 기계가 재지 못했다 | `rateFromDeclaredUnit` 신설 — 입력 `$.rawRate.declaredUnit` 이 갈래를 정한다 | `00434fb` |
| M-3 | 존재하지 않는 `verified_paths` 항목이 missing==missing 으로 공허하게 통과할 수 있었다(잠재, 오늘 거짓 초록은 없음) | `assertPathEquals` 가 값 비교 전 actual·expected 양쪽에 경로 실존을 먼저 단언 | `00434fb` |
| L-1 | `COMPILE_DELEGATION_FIXTURES` 번호와 manifest `contract_binding.type_path` 의 "fixture N" 표기가 어긋나도 잡히지 않았다 | `assertFixtureNumberMatchesContractBinding` 신설 — 대조 후 `assertCompileFixtureFamilyExists` 호출 | `00434fb` |
| L-2 | `commands.md` 가 명령에 `--offline` 을 붙였으나 scope.md 문면엔 없다 · C-0 기록 HEAD 가 최종이 아니었다 | `--offline` 갈래를 별도 문장으로 명시(로컬 실행 편의 대 계약 문면), C-0~C-4 를 최종 HEAD 로 재실행·재기록(`commands.md`) | 이 evidence 커밋 |
| L-3 | `rollback.md` 임시 clone 리허설이 최종 HEAD 가 아니었다 | 최종 HEAD(`ae0d9ad`)에서 재리허설 — exit 0, M 24/D 11, base 대비 빈 diff | 이 evidence 커밋 |
| M-4 | `docs/adr/0007`·`capability-map` OPEN-ADR-06 행이 in_scope 문면과 어긋나게 분류 | scope.md in_scope 명시 승격 + rollback.md 「되돌리지 않는 것」에 등재(팀장 처리) | `75b912d` |
| M-5 | `capability-map.md` §14.2 `OPEN-1B-CONTRACT`·`OPEN-1B-CORPUS` 행 상태 서술이 낡음 | 2026-09-05 로 갱신, 종결은 사용자 승인 대상으로 명시(팀장 처리) | `75b912d` |

**레인 혼입 커밋 `51f820c`** — corpus 레인의 evidence 편집이 이 레인 커밋에 잘못 실린
사고. `fixtures.md` §2 「레인 혼입 선언」과 `38e0640` 이 이미 선언했다 — 이력을 되쓰지
않는다(내용 손실 없음, 세 파일이 바이트로 같음).

## 알려진 제한 · 이월

- **`$.fact`(rate-unit-001·002·005)·`$.conversionDivisor`(전건) 는 나르기만 한다** —
  `Rate.ofPercent`/`ofFraction` 은 `Fact` 로 감싸지 않고 제수(`private const`)를 방출하지
  않는다. 그 상태를 실제로 방출하는 자리는 1C 이후 어댑터다(`OPEN-1BC-SOURCE-UNIT`).
- **money-basis-006 의 적격성 축 셋**(`$.autoTaggedFromDefinitionDefault`·
  `$.eligibleForAuthoritativeCorpus`·`$.reasonCode`)은 계약 산출이 아니라 `verified_paths`
  밖이다 — 소유는 `OPEN-1BC-ELIGIBILITY`(1D, provenance first-match rule). 그 대가:
  적격성 불리언 단독 뒤집기 변이(B7 셋째 갈래)가 지금은 걸리지 않는다(`commands.md` C-7).
- **`OPEN-1BC-TESTFIXTURES-GATE` 확장** — `moduleDependencyGate` 의 `testFixtures*` 버킷
  사각(조사 §8)뿐 아니라, **이번 실측으로 셋이 더 드러났다**: `packageOwnershipGate`·
  `sourceSetLayoutGate` 의 `expectedSourceSets` 하드코딩(`build-logic/src/main/kotlin/
  bidvector.kotlin-conventions.gradle.kts:134,191`)과 ArchUnit `DoNotIncludeTests()` 가
  `testFixtures` source set 을 인식하지 못하는 사실. 넷 다 하네스 slice 1A-b 가
  `java-test-fixtures` 를 채택하려는 향후 slice 를 위해 닫아야 한다. 이 slice 는 D5(d)로
  전환해 shared-kernel 을 무접촉으로 되돌려 **회피**했다 — 근본 수정은 아니다.
- **money-basis-003 이월** — `OPEN-1BC-STR16`(strategy slice 소유). 이 slice `out_of_scope`.
- **`Rate` 값 동등 비교의 전제** — `Rate.ofFraction`/`ofPercent` 가 둘 다 `normalized` 를
  거쳐 「값 동등 == 정규화 표현 위의 구조적 equals」(운영자 결정 2026-09-04 A5)라는 1B
  설계 결정에 이 비교가 기댄다. 그 결정이 바뀌면 이 executor 도 재검토가 필요하다.
- **`BaseAmount` 의 기본값 부재를 재는 컴파일 fixture 가 없다**(verifier r1 M-1 처리 중
  curator 실측, `fixtures.md` §7-8). 여섯 금액 타입이 `vatTreatment`·`provenance` 를
  기본값 없는 필수 파라미터로 받는 것은 `Money.kt` 선언이 지지만, 인자를 빠뜨린 호출이
  컴파일에 실패함을 보이는 음성 fixture 는 지금 없다(있는 fixture 7 은 `YegaAmount` 의
  명시 VAT 인자 **거부**를 재는 다른 명제다). 메우려면 `BaseAmount(won, currency)` 가
  *"no value passed for parameter"* 로 실패하는 음성 fixture 가 필요하고, **새 fixture
  신설은 이 slice 의 `out_of_scope`** 라 만들지 않고 등재만 한다.

## D5 기제 변경 (b′)→(d) — 요지만, 정본은 `commands.md`

`java-test-fixtures` 채택 뒤 `packageOwnershipGate`·`sourceSetLayoutGate`·
`ArchitectureGateTest`(ArchUnit 프로덕션 스캔) 셋이 실측 4 failures 로 깨졌다(전건
`build-logic` 수정 필요, scope.md 밖). 팀장 결정(운영자 사후 확인)으로 (d) 공개 API 동등
비교로 전환했다 — shared-kernel 은 fixture 12(`src/test`) 밖 완전 무접촉. 상세 근거·게이트
셋 실측·회귀 확인은 `commands.md` 「D5 기제 변경」 절.

## acceptance 전건 — 결과 요약(정본은 `commands.md`, 최종 HEAD `ae0d9ad`)

C-0(격리 worktree) · C-1(`clean check`) · C-2(`:shared-kernel:test :app:test`) · C-3(domain
gate 둘) · C-4(`qualityBaseline`) 전부 exit 0 — `app:test` 12/12 · `shared-kernel:test`
(`CompileFailureHarnessTest`) 21/21. C-5~C-10(corpus 레인)은 `commands.md` 상단 절 그대로.
**변이 실측 다섯**(verifier r1 라운드 포함) 전부 기대대로 FAILED 뒤 원복, `git diff` 빈
것 확인 — ① 값 변이(`rate-unit-005` 기대값) ② dispatch 표 제거(`rate-unit-005`) ③ M-1
재현(`declaredVatTreatment: null` → `require` 실패) ④ M-2 재현(`declaredUnit` 뒤집기 →
`rate-unit-005` FAILED) ⑤ M-3 재현(`$.bogusNotThere` 추가 → `money-basis-002` FAILED,
이전엔 초록).

## (b)·(c) 불채택 사유 — snakeyaml 카탈로그 결정에 병기(팀장 요청)

D5(b′)(testFixtures) 는 위 게이트 3종 실측으로 기각. snakeyaml 도입 대안 (b) YAML 파서
없이 manifest.yaml 을 정규식/줄 단위로 직접 파싱 — 「검증된 표준 라이브러리로 해결되는
문제를 직접 구현하지 않는다」원칙에 반하고 curator 가 문서 포맷(들여쓰기·folded scalar
표기)을 바꾸면 조용히 깨질 위험이 있어 불채택. (c) app/build.gradle.kts 에서 카탈로그를
안 거치고 리터럴 좌표(`"org.yaml:snakeyaml:2.6"`)로 직접 선언 — 이 저장소의 다른 모든
의존이 카탈로그를 거치는 관례와 어긋나고 버전이 카탈로그 밖에 남아 불채택. 채택된 (a)의
실측 근거: `./gradlew :app:dependencies --configuration testCompileClasspath --offline` 에
`org.yaml:snakeyaml:2.6` 이 Boot 4.1.1 BOM 관리 하에 이미 transitively 해석돼 있고, app 은
domain 층이 아니라 `moduleDependencyGate` 의 `group.forbidden` 대상이 아니다(1차 게이트
재확인, C-3 도메인 게이트와 별도로 `moduleDependencyGate` 도 통과).

## 리뷰 요청 조건

- 구현 diff 커밋 완료(④ 레인, base `a5ea955`): `441e43a`(fixture 12) · `50ed9cd`(runner+배선)
  · `2049fd9`+`757d2e4`(1차 evidence) · `00434fb`(verifier r1 M-1~M-3·L-1) ·
  `e1b230d`(크기 한도 분리) · `ae0d9ad`(fixture 7 위임 철회) · 이 evidence 커밋(r1 L-2·L-3
  마감). corpus 레인(①②③) 커밋은 `fixtures.md` §2 표.
- 관련 test/lint/type/architecture/contract 명령 전건 통과(위 「acceptance 전건」).
- 변경된 fixture 근거·정책 version: 해당 없음(이 커밋 셋은 fixture 재추출을 하지 않는다 —
  그것은 corpus 레인 커밋 몫, `fixtures.md` §9 참고).
- 알려진 제한·rollback: 위 「알려진 제한」·`rollback.md`.
- **종결 조건(운영자 지시 2026-09-04, CLAUDE.md)**: 코드 slice 는 Codex 심판 대상이 아니다
  — **verifier ready-for-review + 사용자 승인**으로 완료한다. Codex 는 대상 아님.
