# commands.md — leak-baseline-coord

base_sha: a6ab6a8dc4c5304ce91f5ae5b591eb0e4c0a0b95
head_sha: aa22262387528d27c5d5bbc02e63083d3a8d0e42

`head_sha` 는 이 정정 라운드 착수 시점(2026-09-12) `git rev-parse HEAD` 실측값이다
(verifier N-3 — 3회차 재발). 이 값을 담는 커밋 자신의 해시는 커밋 전에는 원리적으로
알 수 없다(evidence-pack "목록은 자기를 담은 커밋을 가리킬 수 없다"와 같은 갈래) —
다음 라운드가 있다면 그 착수 시점에 다시 `git rev-parse HEAD` 로 갱신한다.

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

## 2026-09-12T01:23Z (commands.md/checklist.md/rollback.md 작성 전)
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/leak-baseline-coord/`
- exit: 1 (grep 관례상 매치 없음일 때 1)
- 핵심 결과: 이 시점엔 디렉터리에 `scope.md` 뿐이라 매치 없음.

## 2026-09-12T01:26Z (commands.md/checklist.md 작성 후 — 이 절 자신이 스캔 명령·"secret 스캔" 절 제목을 인용해 재매치)
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/leak-baseline-coord/`
- exit: 0
- 핵심 결과: 4건 매치 — 전부 이 문서 자신이 스캔 명령을 인용하거나("cmd: \`grep ...\`") 절 제목으로 "secret"을 담은 것(카테고리 a/b, baseline 머리말 참조). 실제 비밀값 아님. `leakPatternGate` 도 같은 4건을 새 매치로 잡았고, 검토 후 baseline 에 등재해 재실행 시 `new=0` 복귀시켰다(아래 항목). 육안 확인: telegram id·사업자 정보 없음.

## leakPatternGate 재실행 — 자기 인용 baseline 등재 뒤

## 2026-09-12T01:26Z
- cmd: `./gradlew leakPatternGate --no-daemon` (baseline 에 위 4건 키 추가 전)
- exit: 1
- 핵심 결과: 새 evidence 문서 자신의 "secret 스캔" 절 제목·명령 인용 4건이 `new:`로 잡힘(예상된 자기매치 — `LeakPatternGateTask` KDoc의 "자기매치 함정 (2)"과 동일 성격).
- cmd: 위 4개 키를 `config/quality/leak-pattern-baseline.txt` 에 등재 후 `./gradlew leakPatternGate --no-daemon`
- exit: 0
- 핵심 결과: `patterns=6 baseline=272 matches=294 keys=272 new=0 stale_baseline=0`.
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — evidence 커밋 포함 최종 상태에서 전건 재확인.

## 두 번째 자기매치 라운드 — 위 절 자체를 쓰면서 새로 생긴 인용 4건

commands.md 를 채우는 서술 자체가 "secret 스캔"·"cmd: `grep ...`" 같은 어휘를 담아 매
편집마다 `leakPatternGate` 가 새 키를 잡는다(이 문서가 자기 자신을 다루는 한 원리적으로
끝나지 않는 루프 — 그래서 이 라운드를 마지막으로 하고 이후 새 서술을 덧붙이지 않는다).

## 2026-09-12T01:27Z
- cmd: `./gradlew leakPatternGate --no-daemon` (위 블록 편집 직후)
- exit: 1
- 핵심 결과: 신규 4건 `new:`(편집한 줄의 내용이 바뀌어 새 키 발생 — S-6 과 같은 성질).
- cmd: 4개 신규 키를 baseline 에 추가 후 재실행
- exit: 0
- 핵심 결과: `new=0`, `stale_baseline=1`(이전 라운드에서 등재한 키 하나가 편집으로 내용이
  바뀌어 죽음 — 같은 slice 안에서 즉시 발생한 stale 이라 정리).
- cmd: 그 죽은 stale 키 한 줄을 baseline 에서 제거 후 재실행
- exit: 0
- 핵심 결과: `patterns=6 baseline=275 matches=298 keys=275 new=0 stale_baseline=0`.
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`(최종 확정 상태).

## rollback 검증 — 임시 clone

## 2026-09-12T01:23Z
- cmd: `git clone --no-hardlinks <repo> <scratch>/lbc/rollback-clone && git restore --source=a6ab6a8 --staged --worktree -- build-logic/src/main/kotlin/bidvector/buildlogic/LeakPatternGateChecks.kt build-logic/src/main/kotlin/bidvector/buildlogic/LeakPatternGateTask.kt build-logic/src/test/kotlin/bidvector/buildlogic/LeakPatternGateChecksTest.kt config/quality/leak-pattern-baseline.txt`
- exit: 0
- 핵심 결과: 4개 in_scope 파일 모두 base 와 `git diff --name-status a6ab6a8 -- <경로들>` 결과 없음(완전 일치).
- cmd: `./gradlew :build-logic:compileTestKotlin --no-daemon`
- exit: 0
- cmd: `./gradlew :build-logic:test --tests '*LeakPatternGateChecksTest*' leakPatternGate --no-daemon`
- exit: 0 (**정정 2026-09-12, verifier N-1** — 원 기록은 이 정본을 고치지 않은 채 `rollback.md`
  만 바로잡았다. 축어 재실행 실측치는 아래 참조)

## 2026-09-12 (N-1 재정정) — 위 exit 0 은 부정확했다, 임시 clone 재실행
- cmd: `git restore --source=a6ab6a8 --staged --worktree -- <in_scope 4>` (별도 임시 clone)
- exit: 0 — `git diff --name-status a6ab6a8 -- <4>` 무출력(완전 일치)
- cmd: `./gradlew :build-logic:compileTestKotlin --no-daemon`
- exit: 0
- cmd: `./gradlew :build-logic:test --tests '*LeakPatternGateChecksTest*' leakPatternGate --no-daemon`
- exit: **1** — `:build-logic:test` 는 통과, `:leakPatternGate` 가 FAILED. 위반 메시지에
  이 slice 자신의 `checklist.md`·`commands.md` 9줄이 `new` 로 나열됨(직접 재현·확인).
  되돌린 트리는 옛 baseline 을 쓰는데 이 slice 의 evidence 문서 자체가 base 에 없어
  대응 legacy 항목이 없기 때문 — rollback.md "알려진 제한" 절과 같은 원인.
- cmd: 보완 조건 — 위 상태에서 `rm -rf reports/evidence/m4/leak-baseline-coord` 후
  `./gradlew leakPatternGate --no-daemon` (직접 재현)
- exit: 0 — 보고서 `patterns=6 baseline=290 matches=290 new=0 stale_baseline=0`(직접 실측).
  clone 삭제로 뒷정리.

## F-6 시정 — 위반 메시지·보고서가 좌표를 각자 다시 계산하던 것을 한 곳으로 모음

`leakGateViolation`·`leakGateReportText` 가 각자 `matches.groupBy { leakBaselineKey(...) }` 를
다시 계산해, 둘이 어긋나면(예: 한쪽만 배선이 바뀌면) 위반 메시지의 좌표가 조용히 빈 `()` 로
열화하던 결함(verifier F-6). `LeakPatternGateTask.gate()` 에서 그룹핑을 한 번만 계산해
`coordinatesByKey: Map<String, List<LeakMatch>>` 로 두 함수에 그대로 넘기도록 고쳤다 — 실패
술어(`newLeakBaselineKeys`·`staleLeakBaselineEntries`)의 로직은 그대로다. 단위 test 에도
같은 맵을 두 함수에 넘기면 좌표가 일치함을 고정하는 회귀 test 1건을 추가했다
(`LeakPatternGateChecksTest.kt`).

## 2026-09-12T02:05Z — 표적 확인 ① 실제 게이트 재실행
- cmd: `./gradlew leakPatternGate --no-daemon`
- exit: 0
- 핵심 결과: `patterns=6 baseline=276 matches=299 keys=276 new=0 stale_baseline=0` — 시정 전과
  동일 수치, 회귀 없음.

## 2026-09-12T02:06Z — 표적 확인 ② 새 매치를 심어 위반 메시지의 좌표 확인
- cmd: `reports/evidence/harness/f6-probe/probe.md` 신규 생성(패턴 어휘를 담은 한 줄) 후
  `./gradlew leakPatternGate --no-daemon`
- exit: 1
- 핵심 결과: 위반 메시지에 `reports/evidence/harness/f6-probe/probe.md#bcc16ac5…  (reports/evidence/harness/f6-probe/probe.md:1)` —
  키와 함께 실제 좌표가 찍힘(시정 전 변이 상태에서 관측된 빈 `()` 열화가 재현되지 않음).
  확인 후 `reports/evidence/harness/f6-probe/` 디렉터리를 삭제(신규 파일이라 `git status
  --porcelain` 이 다시 clean 으로 돌아옴 — `checkout --` 불필요).

## 2026-09-12T02:07Z — detekt/ktlint 재확인 (함수 수 예산 불변 확인)
- cmd: `./gradlew :build-logic:test --tests '*LeakPatternGateChecksTest*' :build-logic:detekt :build-logic:ktlintMainSourceSetCheck :build-logic:ktlintTestSourceSetCheck --no-daemon`
- exit: 0
- 핵심 결과: 30 tests 0 failed, detekt/ktlint 통과 — `coordinatesByKey` 를 매개변수로 옮기고
  Task 쪽 계산을 인라인으로 처리해 `LeakPatternGateChecks.kt` 최상위 함수 수(11)를 늘리지
  않았다(TooManyFunctions 예산 그대로).

## 2026-09-12T02:09Z — 최종 acceptance 재확인 (장부층 정정 포함, 이 라운드 종결)
- cmd: `./gradlew leakPatternGate --no-daemon`
- exit: 0
- 핵심 결과: `patterns=6 baseline=276 matches=299 keys=276 new=0 stale_baseline=0` — checklist.md·
  commands.md 갱신 서술 자체가 새 자기매치를 내지 않았다(패턴 어휘 직접 인용을 피함).
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — 전 모듈 + build-logic included build 전건 통과.
- cmd: `git status --porcelain`(라운드 편집 중)
- 핵심 결과: in_scope 3개 코드 파일 + evidence 3개 문서(checklist·commands·rollback, 전부
  이미 존재하는 M)만 잡힘 — `config/quality/leak-pattern-baseline.txt` 는 이 라운드에서
  건드리지 않아 M 목록에 없다(자기매치 신규분 없음).

## 2026-09-12 (장부층 일괄 N-1~N-4·N-6 정정, `aa22262` 이후) — acceptance 재확인
직전 02:09Z 기록은 `ca4c8d4` 시점이었고 이후 `aa22262` 가 `checklist.md`·`rollback.md`
를 더 고쳤다(둘 다 게이트 스캔 대상). 이번 라운드도 `checklist.md`·`commands.md` 를
고치므로(N-1~N-4·N-6) 커밋 전 실제 HEAD 트리에서 재확인한다(verifier N-4).

- cmd: `./gradlew leakPatternGate --no-daemon`
- exit: 0
- 핵심 결과: `patterns=6 baseline=276 matches=299 keys=276 new=0 stale_baseline=0` — 이번
  라운드 편집(N-1~N-4·N-6, checklist.md·commands.md)이 새 자기매치를 내지 않았다(직접
  실측, 패턴 어휘 직접 인용 회피).
- cmd: `./gradlew :build-logic:test --tests '*LeakPatternGateChecksTest*' --no-daemon`
- exit: 0
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — 전 모듈 + build-logic included build 전건, `:leakPatternGate`
  UP-TO-DATE(위 재실행 반영)·`:build-logic:test` UP-TO-DATE·`:contractGate` 실행 확인.
- cmd: `git status --porcelain`
- 핵심 결과: `checklist.md`·`commands.md` 만 M — baseline 은 이번 라운드도 건드리지 않았다
  (자기매치 신규분 없음, 위 재확인과 일치).
