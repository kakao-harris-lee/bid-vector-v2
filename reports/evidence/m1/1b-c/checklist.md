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
| money-basis-006 | value | `$.vatTreatment`, `$.provenance` | 입력 다섯 성분으로 `BaseAmount` 구성 |
| money-basis-003 | 대상 아님(insufficient-evidence) | — | 전용 filter test 가 집합이 정확히 이 하나임을 확인 |

**dispatch 표 완결성**은 `dispatch 표 밖의 authoritative case 가 없다`(전용 test)가
기계로 확인한다 — 새 case 가 authoritative 로 올라와도 표에 없으면 그 case 자신과 이
전용 test 둘 다 FAILED(변이 실측 ②, `commands.md`).

**`verified_projections` 술어 넷(is-present·differs-from-path·differs-from-case·
not-equals)은 이 runner 에 없다** — 최종 committed manifest(`6ef8fb5`) 의 10 case 어느
쪽도 `verified_projections` 를 쓰지 않아(전부 `verified_paths` 정확 비교로 좁혀짐, curator
정정) 구현할 대상 자체가 없었다. 팀장 확인(대화 로그) — 구현 누락이 아니라 계약 자체의
사실이다.

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

## D5 기제 변경 (b′)→(d) — 요지만, 정본은 `commands.md`

`java-test-fixtures` 채택 뒤 `packageOwnershipGate`·`sourceSetLayoutGate`·
`ArchitectureGateTest`(ArchUnit 프로덕션 스캔) 셋이 실측 4 failures 로 깨졌다(전건
`build-logic` 수정 필요, scope.md 밖). 팀장 결정(운영자 사후 확인)으로 (d) 공개 API 동등
비교로 전환했다 — shared-kernel 은 fixture 12(`src/test`) 밖 완전 무접촉. 상세 근거·게이트
셋 실측·회귀 확인은 `commands.md` 「D5 기제 변경」 절.

## acceptance 전건 — 결과 요약(정본은 `commands.md`)

C-0(격리 worktree) · C-1(`clean check`) · C-2(`:shared-kernel:test :app:test`) · C-3(domain
gate 둘) · C-4(`qualityBaseline`) 전부 exit 0. C-5~C-10(corpus 레인)은 `commands.md` 상단
절 그대로. 변이 실측 둘(값 변이 · dispatch 표 제거) 둘 다 기대대로 FAILED 뒤 원복,
`git diff` 빈 것 확인.

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

- 구현 diff 커밋 완료: `441e43a`(fixture 12) · `50ed9cd`(runner+배선) · 이 evidence 커밋.
- 관련 test/lint/type/architecture/contract 명령 전건 통과(위 「acceptance 전건」).
- 변경된 fixture 근거·정책 version: 해당 없음(이 커밋 셋은 fixture 재추출을 하지 않는다 —
  그것은 corpus 레인 커밋 몫, `fixtures.md` §9 참고).
- 알려진 제한·rollback: 위 「알려진 제한」·`rollback.md`.
- **종결 조건(운영자 지시 2026-09-04, CLAUDE.md)**: 코드 slice 는 Codex 심판 대상이 아니다
  — **verifier ready-for-review + 사용자 승인**으로 완료한다. Codex 는 대상 아님.
