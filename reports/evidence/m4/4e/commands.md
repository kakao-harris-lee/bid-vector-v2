# commands.md — M4/4E

## 2026-09-09T12:54:10Z
- cmd: `./gradlew --no-daemon :workflow:compileTestKotlin`
- exit: 1
- 핵심 결과: RED 확인 — notification 패키지 test 일곱 파일이 main 소스 부재로 컴파일 실패(Unresolved reference 다수).

## 2026-09-09T12:56:03Z
- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.notification.*'`
- exit: 1
- 핵심 결과: main 구현 뒤 첫 실행 — 36 tests, 2 failed(`MaskedTargetTest` 경계값 둘, `mask()`가 「길이 ≤ suffix면 전부 별표」 대신 짧은 원문을 그대로 노출).

## 2026-09-09T12:57:25Z
- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.notification.*'`
- exit: 0
- 핵심 결과: `MaskedTarget.mask` 수정(길이 ≤ suffix 전부 별표) 뒤 재실행 — 36 tests, 0 failed, GREEN.

## 2026-09-09T12:57:59Z (S-3, 수정 전)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt workflow/src/main/kotlin/bidvector/workflow/notification workflow/src/test/kotlin/bidvector/workflow/notification`
- exit: 0
- 핵심 결과: 자기매치 2건 — 메인 소스의 legacy 함수명 인용과 test의 표본 문자열이 각각 정책 파일의 영단어 패턴에 걸림. 인용은 한국어 서술로, 표본 문자열은 다른 문자열로 교체.

## 2026-09-09T12:57:59Z (S-3b, 양성 대조 — 최초 실측)
- cmd: scope.md `acceptance_commands` S-3b 그대로(양성 대조용 표본을 임시 파일에 심어 스캔, 원문은 scope.md 참고 — 그 표본 문자열을 이 문서에는 옮기지 않는다, S-3c 자기매치 회피)
- exit: 0
- 핵심 결과: 심은 표본이 매치됨(통과) — `leak-patterns.txt`의 인증 헤더 패턴이 걸린다.

## 2026-09-09T12:58:29Z (S-3, 수정 후)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt workflow/src/main/kotlin/bidvector/workflow/notification workflow/src/test/kotlin/bidvector/workflow/notification`
- exit: 1
- 핵심 결과: 매치 0 — GREEN. 위 자기매치 둘을 한국어 서술·다른 표본 문자열로 교체해 해소.

## 2026-09-09T12:58:33Z
- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.notification.*'`
- exit: 0
- 핵심 결과: S-3 수정(표본 문자열 rename)이 test 를 깨지 않음 재확인 — GREEN.

## 2026-09-09T12:58:53Z (S-3c)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m4/4e/ --exclude=scope.md`
- exit: 1
- 핵심 결과: 매치 0 — GREEN. commands.md의 S-3/S-3b 이력 서술에서 영단어 리터럴을 걷어낸 뒤 통과.

## 2026-09-09T13:00:50Z (S-4)
- cmd: `./gradlew --no-daemon :workflow:gateExecutionGate`
- exit: 0
- 핵심 결과: gate-tests.properties에 등재한 notification test 일곱이 실제로 실행되고 실패·건너뜀 0임을 확인.

## 2026-09-09T13:01:03Z (S-5)
- cmd: `./gradlew --no-daemon :workflow:test --tests '*NotificationBoundaryTest*'`
- exit: 0
- 핵심 결과: allow-list 소스 스캔 + 양성 대조 통과.

## 2026-09-09T13:01:39Z (S-6)
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: 전 모듈 baseline 산출 성공(app·adapters 포함 컴파일 통과).

## 2026-09-09T13:02:00Z (S-1, 1차)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1
- 핵심 결과: `:workflow:detekt`가 MaxLineLength 11건(`DispatchNotificationTest.kt` 10·`SenderContractTest.kt` 1)으로 실패. 나머지 게이트(ktlint·cpd·sizeGate·moduleDependencyGate 등)는 통과.

## 2026-09-09T13:04:04Z (detekt 재실행, 수정 1차)
- cmd: `./gradlew --no-daemon :workflow:detekt`
- exit: 1
- 핵심 결과: `routesWith` 헬퍼 도입 + 줄바꿈으로 10건 해소, 1건(`DispatchNotificationTest.kt:100`) 잔존.

## 2026-09-09T13:04:24Z (detekt+test 재실행, 수정 2차)
- cmd: `./gradlew --no-daemon :workflow:detekt :workflow:test --tests 'bidvector.workflow.notification.*'`
- exit: 0
- 핵심 결과: 잔존 1건 줄바꿈으로 해소, detekt·test 전건 GREEN.

## 2026-09-09T13:04:50Z (S-1, 2차 — clean check 전건)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1
- 핵심 결과: `:workflow:ktlintMainSourceSetCheck`가 `DeliveryPlan.kt`의 중첩 `when`(일부 분기만 멀티라인) 10건으로 실패. 다른 모듈의 CPD observed 경고(procurement·qualification·shared-kernel·workflow)는 기존 관측 축이라 무관.

## 2026-09-09T13:05:26Z (수정 뒤 좁은 재검증)
- cmd: `./gradlew --no-daemon :workflow:ktlintMainSourceSetCheck :workflow:detekt :workflow:test --tests 'bidvector.workflow.notification.*'`
- exit: 0
- 핵심 결과: `planOutcomeOf`의 중첩 `when`을 전 분기 중괄호로 통일 + 보조 함수(`environmentOutcomeOf`)로 분리해 해소. GREEN.

## 2026-09-09T13:05:56Z (S-1, 3차 — clean check 전건)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: 저장소 전체(app·adapters 포함) `check` GREEN — ktlint·detekt·cpd·sizeGate·moduleDependencyGate·gateExecutionGate·qualityBaseline·conventionCoverageGate·contractGate 전부 통과.

## 2026-09-09T13:07:25Z (커밋)
- cmd: `git commit -- workflow/src/main/kotlin/bidvector/workflow/notification workflow/src/test/kotlin/bidvector/workflow/notification` (구현) 및 `git commit -- config/quality/gate-tests.properties config/quality/leak-patterns.txt` (게이트 등재)
- exit: 0
- 핵심 결과: 두 커밋 완료(`89e3fe3`·`9622952`), base(`f600909`) 대비 in_scope 경로만 반영.

## 2026-09-09T13:08:21Z (S-0)
- cmd: `git worktree add --detach <임시 디렉터리> HEAD && (cd <임시 디렉터리> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: 커밋된 HEAD(`9622952`)를 격리 worktree에서 clean checkout 뒤 저장소 전체 `check` GREEN(353 tasks). worktree는 이후 `git worktree remove --force`로 정리.

## 2026-09-09T13:09:36Z (우회 (1)(8) 컴파일 거부 실측)
- cmd: 임시 clone에서 `app/src/test/.../LeakProbe.kt`(다른 모듈)에 `MaskedTarget("raw-value")`·`DeliveryPlan(PolicyVerdict.Allowed, EnvironmentVerdict.Allowed)` 직접 생성자 호출을 심고 `./gradlew --no-daemon :app:compileTestKotlin`
- exit: 1
- 핵심 결과: 둘 다 `Cannot access '...': it is internal in '...'`로 컴파일 거부 — 우회 (1)(8)이 컴파일 층에서 닫힘을 실측. 임시 clone은 이후 삭제.

## 2026-09-09T13:14:02Z (rollback 실측 1/2 — restore + compile)
- cmd: 임시 clone(HEAD `cd73e40`)에서 rollback.md의 restore 명령 셋(A 항목 `git restore --source=f600909...`·gate-tests.properties `git apply -R`·scope.md `git restore`) 실행 뒤 `git status --porcelain`·`git diff f600909... -- config/quality/gate-tests.properties reports/evidence/m4/4e/scope.md`·`./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin`
- exit: 0 (전 단계)
- 핵심 결과: restore 세 명령 전부 exit 0, `git status --porcelain` A 20 중 19 삭제(rollback.md 자신 제외)·M 2(gate-tests.properties·scope.md) 정확히 일치, 두 M 파일의 base 대비 diff 빈 값, `:workflow:compileKotlin`·`:workflow:compileTestKotlin` exit 0.

## 2026-09-09T13:14:15Z (rollback 실측 2/2 — test)
- cmd: 같은 임시 clone에서 `./gradlew --no-daemon :workflow:test`
- exit: 0
- 핵심 결과: notification 패키지 제거 뒤 남은 event·strategy test 전건 GREEN — 되돌린 트리가 컴파일·테스트 모두 선다. 임시 clone은 이후 삭제.

## 2026-09-09T13:16:00Z (clean-tree 게이트, 양성 대조 포함)
- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`(사전) → `Channel.kt`에 빈 줄 1개 append 뒤 재확인(양성 대조) → append한 줄만 제거 뒤 재확인
- exit: 0 / (양성 대조 중 `M` 표시로 감지) / 0
- 핵심 결과: 사전·사후 모두 결과 없음(clean), 양성 대조에서 `Channel.kt`가 정확히 잡힘 — 술어가 늘 통과만 하는 회귀가 아님을 확인.
