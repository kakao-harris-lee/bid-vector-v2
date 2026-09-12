# commands.md — leak-baseline-coord

base_sha: a6ab6a8dc4c5304ce91f5ae5b591eb0e4c0a0b95
head_sha: 1ef5d73a024c25a95555a0f210cfe21c0bea9606

## RED — 새 API 로 갱신한 test 가 기존 프로덕션 코드에서 컴파일 실패

## 2026-09-12T01:11Z
- cmd: `./gradlew :build-logic:test --tests '*LeakPatternGateChecksTest*' --no-daemon`
- exit: 1
- 핵심 결과: 프로덕션 코드 변경 전 — `compileTestKotlin` 컴파일 실패(신설 함수·시그니처 미해결 다수), 의도된 RED.

## 구현 후 — 단위 test

## 2026-09-12T01:11Z
- cmd: `./gradlew :build-logic:test --tests '*LeakPatternGateChecksTest*' --no-daemon`
- exit: 0
- 핵심 결과: `LeakPatternGateChecksTest` 29 tests, 0 failed(신규 API 반영 후 첫 GREEN).

## 실제 게이트 재배선 — 옛 형식 baseline 그대로 상태에서 명시 실패 확인 (D-LBC-2 사전 확인)

## 2026-09-12T01:12Z
- cmd: `./gradlew leakPatternGate --no-daemon`
- exit: 1
- 핵심 결과: baseline 이 아직 `경로:줄번호`(옛 형식) 그대로라 legacy 위반으로 명시 실패 — 290개 항목 전부 사유에 나열됨(D-LBC-2 실제 동작 확인).

## D-LBC-4 마이그레이션 — 기계 산출(임시 생성기, 커밋하지 않음)

## 2026-09-12T01:14Z
- cmd: `./gradlew :build-logic:test --tests '*TmpLeakBaselineMigrationGeneratorTest*' --no-daemon` (head 트리, 임시 test — 실제 프로덕션 함수 `leakMatchesInFile`·`leakBaselineKey` 호출)
- exit: 0
- 핵심 결과: matchLines=290, distinctKeys=268, multiLineKeyCount=9, linesCoveredByMultiLineKeys=31 — 설계 검토(D-LBC-4)의 예측(268 항목, 9 항목이 31줄 덮음)과 정확히 일치.

## S-1 실측 — base 와 head 두 트리의 매치 좌표 대조

## 2026-09-12T01:16Z
- cmd: `git worktree add --detach <scratch>/lbc/base-wt a6ab6a8` 후 그 트리에서 `./gradlew :build-logic:test --tests '*TmpBaseMatchDumpTest*' --no-daemon` (base 원본 `leakMatchesInFile`가 `path:line` 문자열을 직접 반환 — 매치 판정 코드 자체는 base·head 동일)
- exit: 0
- 핵심 결과: base 매치 좌표 290개 산출.
- cmd: `diff base-coordinates.txt head-coordinates.txt`
- exit: 0
- 핵심 결과: 두 파일 각 290줄, **차이 없음(identical)** — S-1(매치 집합 불변) 충족.
- 뒷정리: `git worktree remove --force <scratch>/lbc/base-wt`, 임시 생성기 test 파일 두 개(head·base) 삭제 확인(`git status --porcelain` 무변화).

## baseline 교체 뒤 — 실제 게이트

## 2026-09-12T01:17Z
- cmd: `./gradlew leakPatternGate --no-daemon`
- exit: 0
- 핵심 결과: 보고서 `patterns=6 baseline=268 matches=290 keys=268 new=0 stale_baseline=0` — S-2 충족.

## S-3~S-6 — 실제 게이트 레벨 재현(임시 편집 후 원복, 매번 `git status --porcelain` 로 clean 확인)

## 2026-09-12T01:18Z
- cmd: `reports/evidence/harness/test-discovery-guard/checklist.md` 맨 위에 줄 삽입 후 `./gradlew leakPatternGate --no-daemon`
- exit: 0
- 핵심 결과: 좌표가 밀렸는데도(`:26`→`:27`류) `new=0 stale_baseline=0` 그대로 — S-3 충족. 원복 후 `diff`/`git status --porcelain` 로 원본과 바이트 단위 동일 확인.

## 2026-09-12T01:19Z
- cmd: `reports/evidence/harness/test-discovery-guard/checklist.md:26`(승인된 매치 줄)의 문구를 그대로 복사해 신규 파일 `reports/evidence/harness/s4-probe/probe.md` 에 넣고 `./gradlew leakPatternGate --no-daemon`
- exit: 1
- 핵심 결과: `new: reports/evidence/harness/s4-probe/probe.md#f62df1e5...  (reports/evidence/harness/s4-probe/probe.md:1)` — 같은 내용이라도 다른 파일이면 막힘(S-4). 위반 메시지가 키와 좌표를 함께 냄(D-LBC-3). probe 디렉터리 삭제 후 `git status --porcelain` clean 확인.

## 2026-09-12T01:20Z
- cmd: baseline 사본을 뜬 뒤 `config/quality/leak-pattern-baseline.txt` 에 `reports/evidence/probe.md:99`(옛 형식) 한 줄 추가 후 `./gradlew leakPatternGate --no-daemon`
- exit: 1
- 핵심 결과: `옛 형식(경로:줄번호) 항목이 남아 있다 ... reports/evidence/probe.md:99` — D-LBC-2/S-5 충족. baseline 원본 복사로 원복, `diff` 로 동일 확인.

## 2026-09-12T01:21Z
- cmd: `checklist.md:26` 줄 끝에 문구 추가(내용 변경) 후 `./gradlew leakPatternGate --no-daemon`
- exit: 1
- 핵심 결과: 새 키(`...3cf3d4e0...`)로 잡힘 — 문장이 바뀌면 다시 본다(S-6). 원복 후 `git status --porcelain` clean 확인.

## detekt / ktlint 래칫

## 2026-09-12T01:19Z (최초, 함수 수 초과 발견)
- cmd: `./gradlew --no-daemon check`
- exit: 1
- 핵심 결과: `build-logic:detekt` — `LeakPatternGateChecks.kt` TooManyFunctions(14 > 11), `LeakPatternGateTask.kt` ThrowsCount(3 > 2). 프로덕션 코드를 11개 함수로 재구성(해시 계산을 `leakBaselineKey` 내부로, legacy 필터를 `leakBaselineLegacyFormatViolation` 내부로 흡수, `LeakMatch` 확장함수 제거)하고 Task 의 throw 두 곳을 `?:` 체인으로 한 곳에 모아 해결.

## 2026-09-12T01:19Z (재수정 후)
- cmd: `./gradlew :build-logic:test --tests '*LeakPatternGateChecksTest*' :build-logic:detekt :build-logic:ktlintMainSourceSetCheck :build-logic:ktlintTestSourceSetCheck --no-daemon`
- exit: 0
- 핵심 결과: 29 tests 0 failed, detekt/ktlint 통과.

- cmd: `./gradlew :build-logic:ktlintFormat --no-daemon` (chain-method-continuation 8건 자동 수정)
- exit: 0

## 최종 acceptance — 전건

## 2026-09-12T01:22Z
- cmd: `./gradlew :build-logic:test --tests '*LeakPatternGateChecksTest*' --no-daemon`
- exit: 0
- 핵심 결과: 29 tests, 0 failed.

## 2026-09-12T01:22Z
- cmd: `./gradlew leakPatternGate --no-daemon`
- exit: 0
- 핵심 결과: `patterns=6 baseline=268 matches=290 keys=268 new=0 stale_baseline=0`.

## 2026-09-12T01:23Z
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — 전 모듈(app/adapters/shared-kernel/…) + build-logic included build 포함 전건 통과(S-8).

## 커밋 후 — clean-tree 확인(양성 대조 포함)

## 2026-09-12T01:23Z
- cmd: `git status --porcelain` (커밋 직후)
- exit: 0
- 핵심 결과: 출력 없음 — clean. (양성 대조는 위 S-3~S-6 각 단계의 편집 직후 `git status --porcelain` 이 변경을 잡아낸 것으로 갈음 — in_scope 파일 편집이 실제로 검출됨을 매 라운드 확인했다.)

## secret 스캔 — evidence 디렉토리

## 2026-09-12T01:23Z
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/leak-baseline-coord/`
- exit: 1 (grep 관례상 매치 없음일 때 1)
- 핵심 결과: 매치 없음 — 통과. 육안 확인: 본 문서는 SHA-256 hex(공개 알고리즘 출력물)와 파일 경로만 담고, telegram id·사업자 정보 없음.

## rollback 검증 — 임시 clone

## 2026-09-12T01:23Z
- cmd: `git clone --no-hardlinks <repo> <scratch>/lbc/rollback-clone && git restore --source=a6ab6a8 --staged --worktree -- build-logic/src/main/kotlin/bidvector/buildlogic/LeakPatternGateChecks.kt build-logic/src/main/kotlin/bidvector/buildlogic/LeakPatternGateTask.kt build-logic/src/test/kotlin/bidvector/buildlogic/LeakPatternGateChecksTest.kt config/quality/leak-pattern-baseline.txt`
- exit: 0
- 핵심 결과: 4개 in_scope 파일 모두 base 와 `git diff --name-status a6ab6a8 -- <경로들>` 결과 없음(완전 일치).
- cmd: `./gradlew :build-logic:compileTestKotlin --no-daemon`
- exit: 0
- cmd: `./gradlew :build-logic:test --tests '*LeakPatternGateChecksTest*' leakPatternGate --no-daemon`
- exit: 0
- 핵심 결과: 되돌린 트리에서 옛(좌표 키) 코드 + 옛 baseline 조합으로 compile·test·게이트 전부 정상 통과 — rollback 이 실제로 서는 상태로 되돌림을 확인. clone 삭제로 뒷정리.
