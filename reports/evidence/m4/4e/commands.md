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
- 핵심 결과: `routesWith` 헬퍼 도입 + 줄바꿈으로 10건 해소, 1건(`차단 환경은 sender 를 호출하지 않고 Suppressed(EnvironmentBlocked) 를 낸다` test 본문) 잔존.

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
- 핵심 결과: restore 세 명령 전부 exit 0, `git status --porcelain`의 D·M 수가 rollback.md 「목록 산출」 명령(`git diff --name-status f600909..HEAD -- <in_scope>`)이 낸 A·M 수와 일치(A 중 rollback.md 자신만 D로 안 잡힘)·M 2(gate-tests.properties·scope.md)는 두 파일 다 base 대비 diff 빈 값, `:workflow:compileKotlin`·`:workflow:compileTestKotlin` exit 0. **verifier r1 L-1 시정 — 앞서 「A 20 중 19」로 적었던 개수 자체가 실물(A 24)과 어긋났었다(원인: rollback.md 나열 목록 4항목 합을 20으로 오산). 이후 개수는 산문에 박지 않고 위 명령이 낸 값을 가리킨다.**

## 2026-09-09T13:14:15Z (rollback 실측 2/2 — test)
- cmd: 같은 임시 clone에서 `./gradlew --no-daemon :workflow:test`
- exit: 0
- 핵심 결과: notification 패키지 제거 뒤 남은 event·strategy test 전건 GREEN — 되돌린 트리가 컴파일·테스트 모두 선다. 임시 clone은 이후 삭제.

## 2026-09-09T13:16:00Z (clean-tree 게이트, 양성 대조 포함)
- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`(사전) → `Channel.kt`에 빈 줄 1개 append 뒤 재확인(양성 대조) → append한 줄만 제거 뒤 재확인
- exit: 0 / (양성 대조 중 `M` 표시로 감지) / 0
- 핵심 결과: 사전·사후 모두 결과 없음(clean), 양성 대조에서 `Channel.kt`가 정확히 잡힘 — 술어가 늘 통과만 하는 회귀가 아님을 확인.

---

## verifier r1 finding 일괄 수정 (M-1·M-2·L-1~L-4)

## 2026-09-09T13:37:54Z (M-1 수정 — DeliveryOutcome internal)
- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.notification.*'`
- exit: 0
- 핵심 결과: `DeliveryOutcome.Suppressed`·`Attempted`를 `@ConsistentCopyVisibility` + `internal constructor`로 닫은 뒤 재실행 — GREEN(기존 test는 `dispatch()` 반환값만 소비해 무영향).

## 2026-09-09T13:38:19Z (M-1 검증 — 컴파일 거부 재현)
- cmd: 임시 clone에서 현재 작업트리의 `DispatchNotification.kt`(M-1 수정본)를 덮어쓰고, `app/src/test/.../DeliveryOutcomeProbe.kt`(다른 모듈)에 `DeliveryOutcome.Attempted(DeliveryResult.Delivered(...))` 직접 생성을 심어 `./gradlew --no-daemon :app:compileTestKotlin`
- exit: 1
- 핵심 결과: `Cannot access 'constructor(result: DeliveryResult): DeliveryOutcome.Attempted': it is internal in 'bidvector.workflow.notification.DeliveryOutcome.Attempted'` — verifier r1 M-1 probe와 동일한 형태로 컴파일 거부 확인. 임시 clone은 이후 삭제.

## 2026-09-09T13:40:44Z (M-2 수정 — 출하 정책 단언 추가)
- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.notification.*'`
- exit: 0
- 핵심 결과: `NotificationPolicyDataTest`에 `NOTIFICATION_DELIVERY_POLICY.resolve(...)`를 직접 호출해 environmentModes 전사상·suffix≥1을 단언하는 test 추가 — 37 tests(기존 36 + 신규 1), GREEN.

## 2026-09-09T13:40:57Z (M-2 검증 — verifier 변이 재현)
- cmd: `NOTIFICATION_DELIVERY_POLICY`의 map에서 `RuntimeEnvironment.Development` 항목 한 줄을 삭제한 뒤 `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.notification.*'`
- exit: 1
- 핵심 결과: verifier r1 T-7(b) 변이(`Development` 한 줄 제거)가 이제 즉시 실패 — `NotificationPolicyDataTest.출하 정책 NOTIFICATION_DELIVERY_POLICY 는 환경 전 값을 덮고 suffix가 1 이상이다`가 `ExceptionInInitializerError`(원인 `IllegalArgumentException: environmentModes는 RuntimeEnvironment 전 값을 덮어야 한다`)로 실패. 수정 전에는 이 변이로도 `:workflow:test`·`check` 전건 GREEN이었다(M-2 finding 본문). 변이는 즉시 원상복구(diff 확인 결과 base와 바이트 동일).

## 2026-09-09T13:42:33Z (S-1 — M-1·M-2 반영 뒤 전체 재검증)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: 저장소 전체 GREEN(344 tasks, 320 executed).

## 2026-09-09T13:43:40Z (S-4 재검증)
- cmd: `./gradlew --no-daemon :workflow:gateExecutionGate`
- exit: 0
- 핵심 결과: notification test 일곱 포함 전건 실행 확인, GREEN.

## 2026-09-09T13:43:48Z (S-2 재검증)
- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.notification.*'`
- exit: 0
- 핵심 결과: 37 tests(M-2 신설 1건 포함), 0 failed, GREEN.

## L-1~L-4 정정 사항(코드 실행 없이 문서만 수정, 명령 없음)
- L-1: rollback.md·commands.md의 하드코딩된 개수(「A 20」·「19 삭제」)를 걷고 「목록 산출」 명령의 출력을 가리키는 서술로 교체(evidence-pack 「명령이 내는 셈을 산문에 옮겨 적지 않는다」 규격 준수).
- L-2: commands.md가 `DispatchNotificationTest.kt`의 편집 대상 줄 번호를 인용하던 자리를 그 test 이름으로 교체(파일명+숫자 좌표 형태 제거) — `grep -rnE '[A-Za-z0-9_./-]+\.(md|kt|properties|txt|yaml|kts):[0-9]+' reports/evidence/m4/4e/` 재실행 결과 매치 0.
- L-3: checklist.md 첫 항목의 「양성 대조 포함 예정 — 마지막 단계에서 실측」을 「실측 완료」로 정정.
- L-4: checklist.md 인증값 스캔 항목에 육안 확인 결과(evidence 5파일 실 Telegram id·메일·사업자번호·인증값 0, 7자↑ 숫자열은 전부 커밋 SHA)를 추가 — 이메일 정규식·7자↑ 숫자열·「사업자/등록번호/주민」 키워드로 재확인, 실 식별자 매치 0(7자↑ 숫자열은 전부 커밋 SHA 조각 `9622952`·`681539831`).

## 2026-09-09T13:45:00Z (L-1~L-4 반영 뒤 S-3c·L-2 재확인)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m4/4e/ --exclude=scope.md` 및 `grep -rnE '[A-Za-z0-9_./-]+\.(md|kt|properties|txt|yaml|kts):[0-9]+' reports/evidence/m4/4e/`
- exit: 1 / 1
- 핵심 결과: 둘 다 매치 0. L-2 수정 문장 자신이 옛 좌표를 인용해 L-2 검사기에 재매치되는 자기지시 문제를 1차로 만들었다가(재현) 좌표 형태를 완전히 걷어 해소.

## 2026-09-09T13:45:26Z (커밋 전 최종 S-1)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: M-1·M-2·L-1~L-4 반영 최종 상태에서 저장소 전체 GREEN(344 tasks). 이 상태로 일괄 커밋한다.
