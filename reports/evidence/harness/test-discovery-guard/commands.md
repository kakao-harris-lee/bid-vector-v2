# commands.md — harness/test-discovery-guard

base `7581106ecf7c52bbf8bb032adc233e90a8f1eb9b` · 구현 커밋 `c7aaf5c`(게이트 본체) ·
`fe7d133`(breaking-mutations.sh B-1~B-4) · `94dac38`(detekt ReturnCount 수정) ·
`6752791`(evidence 문서). head 는 리뷰 요청 시점 `HEAD` 그대로.

## RED — 구현 전

- cmd: `./gradlew -p build-logic test --tests '*TestShapes*'`
- exit: 1(compile 실패)
- 핵심 결과: `Unresolved reference 'TestShapes'`/`'TestShapePolicy'` — 미구현 상태의 정상 실패.

## S-2 — TestShapes 단위 test

- cmd: `./gradlew -p build-logic test --tests '*TestShapes*'`
- exit: 0
- 핵심 결과: `TestShapesTest` 16/16 통과(위반 4·정당 3·factory 2·suspend 2·private 2·비대상 1·복수위반 1).

## 게이트 자체 회귀 확인 — 음성(잡는다) → 원복(잡지 않는다)

- cmd: `adapters/.../PredictionContractTest.kt` 의 2B 원 사례 test 를 일시 식 본문(`= runBlocking { run { … } }`)으로 바꾸고 `./gradlew :adapters:testShapeGate`
- exit: 1
- 핵심 결과: `PredictionContractTest.kt:100 … — 식 본문 test 메서드는 반환 타입이 Unit 이 아니면 JUnit 이 discover 하지 않는다`.
- cmd(원복 뒤): `git diff -- adapters/src/test/kotlin/bidvector/adapters/contract/PredictionContractTest.kt | wc -l` → `0`(out_of_scope 파일 무변경 확인) 이어서 `./gradlew :adapters:testShapeGate`
- exit: 0

## detekt 회귀(S-0 첫 실행에서 발견) → 수정 → 재확인

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`(HEAD `fe7d133`)
- exit: 1
- 핵심 결과: `build-logic:detekt` — `TestShapes.kt:83 ReturnCount`(3 > 2). 커밋 `94dac38` 로 수정.
- cmd(수정 뒤): `./gradlew -p build-logic detekt test --tests '*TestShapes*'`
- exit: 0

## S-3 — check 배선 증거

- cmd: `./gradlew check --dry-run | grep -c 'testShapeGate'`
- exit: 0, 출력 `9`
- **주의**: 문면상 「9 모듈 + 루트 = 10」이나 루트 task 이름이 scope.md 지정대로
  `buildLogicTestShapeGate`(대문자 T)라 대소문자 구분 grep 은 9(모듈)만 센다. acceptance
  는 exit 0(매치 존재)로 충족되나 실제 배선 증거는 보조 명령으로 확인:
  `./gradlew check --dry-run | grep -ci 'testShapeGate'` → `10`(9 모듈 + 루트). 아래 known-limitations 에 등재.

## S-4 — breaking-mutations.sh

- cmd: `(cd contracts && ./tools/breaking-mutations.sh)`
- exit: 0
- 핵심 결과: `11/11 mutation 잡힘, 최소 요구 11`. `git status --porcelain -- contracts/testdata/breaking/` 결과 없음(표 무변경).

## S-5 — breaking-mutations-selftest.sh

- cmd: `(cd contracts && ./tools/breaking-mutations-selftest.sh)`
- exit: 0
- 핵심 결과: F-21(행 누락 → 2, 표류한 표 → 2, 완전한 표 → 0)·F-22(표류 → 1(파일은 갱신됨)· `--accept-drift` → 0 · 최초 실행 → 0)·F-23(147331B 합성 출력에서 `has_compile_error` 참, `first_rule_type` = `FIELD_NO_DELETE`) 전부 기대대로. `FAILURES` 0건.

## S-6 — qualityBaseline

- cmd: `./gradlew qualityBaseline`
- exit: 0

## verifier r1 F-7 반영 뒤 재실행 — `files=` → `scanned=` 라벨 변경(판정 불변)

- cmd: `./gradlew -p build-logic test --tests '*TestShapes*'`(S-2)
- exit: 0 — `TestShapesTest` 16/16 그대로.
- cmd: `./gradlew check --dry-run | grep -c 'testShapeGate'`(S-3)
- exit: 0, 출력 `9`(불변).
- cmd: `./gradlew :shared-kernel:testShapeGate` 뒤 `cat shared-kernel/build/reports/test-shape-gate/test-shape-gate.txt`
- 핵심 결과: `scanned=8` `violations=0` — 라벨만 바뀌고 판정 값은 이전과 동일.

## S-0/S-1 — clean check(정본/공유 트리)

- cmd: `git worktree add --detach <dir> HEAD(6752791) && (cd <dir> && ./gradlew --no-build-cache clean check)`
- exit: 1, 실패 사유 = `testShapeGate` 가 procurement `AccountingTest.kt`(3A 커밋 `b8d4c4e`, out_of_scope)의 식 본문 `@Test` 1건을 검출 — **게이트가 실제 위반을 잡은 첫 실측**(설계 의도대로 동작, 구현 결함 아님). `build-logic:check`·`contractGate`·`qualityBaseline` 등 나머지 전부 통과. 상세는 checklist.md 「게이트가 잡은 실제 위반(범위 밖)」.
- cmd: `./gradlew --no-build-cache clean check`(공유 트리)
- exit: 1
- 핵심 결과: 위와 동일한 단일 실패(`procurement:testShapeGate`) — S-0 과 S-1 이 같은 원인으로 일치, 다른 실패 없음.
- cmd(귀속 증거, 우회 아님 — team-lead 지시 2026-09-07): `./gradlew --no-build-cache clean check -x :procurement:testShapeGate`(HEAD `4a73268` worktree, out_of_scope 위반 1건만 제외)
- exit: 0
- 핵심 결과: 그 1건을 빼면 이 slice 의 나머지 8 모듈 + build-logic + 루트 게이트 전부 초록 — S-0/S-1 의 유일한 원인이 procurement 그 1건임을 귀속한다(이 명령은 acceptance 판정을 대신하지 않는다, S-0/S-1 정본은 그대로 exit 1).

## S-0 재실행 — 3A 수정(`b72b712`) 뒤, team-lead 지시(2026-09-07)

- cmd: `git worktree add --detach <dir> HEAD(9baef6b) && (cd <dir> && ./gradlew --no-build-cache clean check)`
- exit: 0 — `BUILD SUCCESSFUL`(347 tasks). 3A 가 `b72b712`(「F-1 blocker — COL-06 property test 를 JUnit 이 discover 하게 정정」)로 `AccountingTest.kt` 를 고친 뒤 S-0 정본이 처음으로 초록. worktree 는 확인 뒤 제거(`git worktree list` 본 저장소 한 줄만 재확인).

## secret 스캔

- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" <이 slice 편집 파일 8개>`
- exit: 0(매치 있음, 아래 확인)
- 핵심 결과: 매치 4건 전부 오탐 — `KtTokens`(import·상수명, `TestShapes.kt`)와 `minimumTokenCount`(CPD 옵션명, `bidvector.kotlin-conventions.gradle.kts`, 이 slice 편집분 아님·인접 문맥). 실제 secret 없음(육안 확인).
- cmd: `grep -rniE "..." reports/evidence/harness/test-discovery-guard/`(evidence 디렉터리 자체)
- exit: 0
- 핵심 결과: 매치는 이 문서들이 grep 패턴/코드 식별자를 인용문으로 담아 자기 참조로 나온 것뿐(M2/2D F-11 과 같은 클래스) — `checklist.md`·`commands.md` 자신의 "secret 스캔" 절 서술, 실제 secret 없음.

## 역방향 파급 grep

- cmd: `grep -rn 'checklist\.md:[0-9]' --include='*.md' --include='*.kt' --include='*.properties' .` (편집 전)
- 결과: `reports/evidence/m2/2d/checklist.md:11~13` 을 가리키는 외부 `file:line` 인용 없음(전부 다른 마일스톤의 `checklist.md` — `m0/0a`·`m0/0a2`·`m0/0a3`). 영향 없음.
- cmd: `grep -rn 'capability-map\.md:[0-9]' --include='*.md' --include='*.kt' --include='*.properties' .` (편집 전)
- 결과: `capability-map.md:3457`(OPEN-2B 행)을 가리키는 인용 없음. 편집도 같은 줄 치환(줄 수 불변, `git diff -U0` 단일 hunk 확인)이라 그 아래 좌표도 밀리지 않는다.

## 2026-09-07 — 알려진 제한 1 닫힘 (3A 종결 `b9dd07c` 뒤 병합)
- cmd: `./gradlew buildLogicGateExecutionGate`
- exit: 0
- 핵심 결과: `gate.tests.build-logic` 에 `bidvector.buildlogic.TestShapesTest` 등재(23개) 뒤 통과 — 결과 XML `TEST-bidvector.buildlogic.TestShapesTest.xml` tests="16"
