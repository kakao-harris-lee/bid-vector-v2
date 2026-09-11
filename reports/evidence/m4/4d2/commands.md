# M4 / 4D-2 — commands.md

base_sha: `caee26c` · head_sha: 이 문서 작성 시점의 `git rev-parse HEAD`(값을 박지 않는다 —
`git log --oneline caee26c..HEAD` 로 확인).

## 2026-09-10T23:15:00Z
- cmd: `./gradlew --offline -q :workflow:compileKotlin :workflow:compileTestKotlin :adapters:compileKotlin :adapters:compileTestKotlin`
- exit: 0
- 핵심 결과: 컴파일 통과(main+test, 4모듈)

## 2026-09-10T23:19:22Z
- cmd: `./gradlew --offline -q :workflow:test :adapters:test`
- exit: 0
- 핵심 결과: workflow 145 tests, adapters 471 tests, 실패·오류·skip 0 (test-results XML 집계)

## 2026-09-10T23:20:00Z — S-1 1차 (수정 전)
- cmd: `./gradlew --offline --no-build-cache clean check`
- exit: 1 (FAILED)
- 핵심 결과: `:adapters:ktlintTestSourceSetCheck` FAILED(`EmbeddingShapeFailClosedTest.kt`·
  `EmbeddingTestFixtures.kt` 포맷 위반 9건) · `:adapters:detekt` FAILED(`MagicNumber` 4건,
  전부 신설 `MlCallPolicyPlaceholder.kt`)

## 2026-09-10T23:21:00Z
- cmd: `./gradlew --offline :adapters:ktlintFormat :workflow:ktlintFormat`
- exit: 0
- 핵심 결과: ktlint 포맷 위반 자동 수정(로직 변경 없음, 개행·공백만)

## 2026-09-10T23:22:00Z — MagicNumber 수정
- 조치: `MlCallPolicyPlaceholder.kt`의 리터럴을 named `private const val`로 치환(코드 수정,
  명령 아님)

## 2026-09-10T23:25:22Z — S-1 2차 (수정 후, 워킹트리)
- cmd: `./gradlew --offline --no-build-cache clean check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 345 tasks(321 executed·24 up-to-date). cpd 중복 0
  (`adapters/build/reports/cpd/adapters.xml`의 `<duplication>` 0건) · detekt issue 0
  (`adapters/build/reports/detekt/detekt.xml`의 `<error>` 0건)

## 2026-09-10T23:27:00Z — S-2~S-7 개별 호출
- cmd: `./gradlew --offline :adapters:test`
- exit: 0 (UP-TO-DATE — 직전 S-1 2차의 신선한 실행을 그대로 반영, 카운트는 위 XML 집계와 동일)
- cmd: `./gradlew --offline :workflow:test`
- exit: 0 (UP-TO-DATE, 위와 동일 근거)
- cmd: `./gradlew --offline :adapters:moduleDependencyGate :adapters:sizeGate :adapters:cpdCheck :adapters:contractGate`
- exit: 1 — **scope.md 오타 발견**: `contractGate`는 root 레벨 task이지 `:adapters:` 소속이
  아니다(`Cannot locate tasks that match ':adapters:contractGate'`). scope.md에 보고(알려진
  제한 참고).
- cmd: `./gradlew --offline :adapters:moduleDependencyGate :adapters:sizeGate :adapters:cpdCheck contractGate`
- exit: 0 — 위 오타를 교정한 형태. 4개 게이트 전부 통과(3개 UP-TO-DATE, `contractGate` 신선 실행)
- cmd: `./gradlew --offline :app:test`
- exit: 0 (UP-TO-DATE, app 129 tests — 아래 S-0 fresh clone 실행 카운트와 동일)
- cmd: `./gradlew --offline qualityBaseline`
- exit: 0 (UP-TO-DATE)
- cmd: `./gradlew --offline :app:gateExecutionGate`
- exit: 0 (UP-TO-DATE)

## 2026-09-10T23:29:00Z — S-0 (임시 clone, 신선 실행)
- cmd: `git clone . <scratch>/s0-clone && cd <scratch>/s0-clone && ./gradlew --offline --no-build-cache clean check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 354 tasks(이 실행에서는 354 executed·0 up-to-date였다 —
  **verifier F-7(low) 정정**: 이 재현 수치는 로컬 `~/.gradle` 캐시 상태에 따라 갈린다.
  verifier 는 격리된 `GRADLE_USER_HOME`에서 같은 명령을 돌려 `320 executed·34 up-to-date`
  를 얻었다 — 「전건 executed」는 이 실행 하나의 관측이지 보장된 불변량이 아니다. 결론
  자체(cpd·detekt·in_scope 관련 게이트가 신선 실행됐다는 것)는 두 실행 모두에서 cpd
  중복 0·detekt issue 0·test 수치 동일로 선다). workflow 145 / adapters 471 / app 129
  tests, 실패·오류·skip 0 (fresh test-results XML 집계, S-1 2차와 동일 수치)

## 2026-09-10T23:33:00Z — 4D-1 test 무편집 확인
- cmd: `git diff --stat caee26c..HEAD -- <4D-1 test 13파일>` (BreakerTest·
  DeadlineCancellationRetryTest·GrpcBidPredictionGatewayTest·MlAdapterDependencyTest·
  MlCallPolicyDataTest·MlGateRegistrationTest·MlTestFixtures·ReleaseCheckTest·
  ResponseMappingTest·SuccessShapeFailClosedTest·UnavailableMlAnalysisTest·
  PredictionBoundaryTest·PredictionValueTest)
- exit: 0
- 핵심 결과: 출력 없음(diff 0줄) — 13파일 전부 byte-for-byte 무편집(팀장이 지목한 7파일의
  상위집합)

## 2026-09-10T23:34:00Z — secret 스캔
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4d2/`
- exit: 1 (매치 없음 = 통과, **이 파일 작성 전** 실행분 — 이 문서 자체가 스캔 명령 문자열을
  담고 있어 이 문서 작성 후 같은 스캔을 돌리면 이 줄들이 자기 인용으로 매치된다. 육안
  확인: 위 매치는 전부 이 절의 명령 텍스트 인용이며 실제 비밀값이 아니다)
- cmd: `git diff caee26c..HEAD | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 1 (매치 없음 = 통과, commit 시점 diff 대상)

## 2026-09-10T23:36:00Z — MlGateRegistrationTest
- cmd: `cat adapters/build/test-results/test/TEST-bidvector.adapters.ml.MlGateRegistrationTest.xml`
- 핵심 결과: `tests="2" failures="0" errors="0"` — 신규 test class 7건 등재 완전성 확인

## 2026-09-10T23:40:00Z — rollback 실측(임시 clone, 1차)
- cmd: `git restore --source=caee26c --staged --worktree -- <in_scope 비공유 경로 18파일 + 2디렉터리 = 인자 20개>`
  (**verifier F-8(a) 정정** — 이전 표기 「18개」는 파일만 센 것이고 디렉터리 인자 둘을
  빠뜨렸다. rollback.md ①의 명령이 실제 정본이며 인자 수는 20이다)
- exit: 0
- cmd: `git diff <이 slice의 gate-tests.properties 커밋>~1..<같은 커밋> -- config/quality/gate-tests.properties | git apply -R`
- exit: 0
- cmd: `git diff caee26c -- <in_scope 코드 경로 한정, evidence 제외>` (되돌린 뒤)
- exit: 0, 출력 0줄 (**verifier F-8(b) 정정** — evidence 경로까지 포함해 돌리면 0줄이
  아니다(의도적으로 되돌리지 않는다). 「코드 경로 한정」임을 명시한다)
- cmd: `./gradlew --offline :workflow:compileKotlin :workflow:compileTestKotlin :adapters:compileKotlin :adapters:compileTestKotlin :workflow:test :adapters:test`
- exit: 0
- 핵심 결과: 되돌린 트리 컴파일·테스트 초록(workflow 134 / adapters 449 tests, 4D-2 추가분만큼
  감소 — 4D-1 골격은 그대로 살아 있음을 확인)

## 2026-09-11T00:30:00Z — verifier not-ready 수정 라운드 1(F-1~F-8)

F-1(high) 처방 뒤 재컴파일·재테스트:
- cmd: `./gradlew --offline --no-daemon :adapters:compileTestKotlin :workflow:compileTestKotlin`
- exit: 0
- cmd: `./gradlew --offline --no-daemon :adapters:test :workflow:test`
- exit: 0
- 핵심 결과: workflow 146(+1) / adapters 481(+10) tests, 실패·오류·skip 0(fresh XML 집계) —
  F-1 실 gateway 회귀(NaN·+Inf·-Inf·혼합 4 case)·F-2 EmbeddingVector 비유한 값 test·F-3
  EMBEDDING_NORM_EPSILON 값 고정 test·F-4 회귀 8건(개수 경계 2 + gateway 6) 전부 통과
  포함(개별 testcase 이름은 XML에서 확인)

## 2026-09-11T00:45:00Z — S-0 재실행 1차(수정 라운드 1 반영, 임시 clone) — 실패
- cmd: `git clone . <scratch>/s0-clone-r2 && cd <scratch>/s0-clone-r2 && ./gradlew --offline --no-daemon --no-build-cache clean check`
- exit: 1 (FAILED)
- 핵심 결과: `:adapters:detekt` FAILED — `MaxLineLength` 3건, 전부 F-4 회귀로 추가한
  `GrpcEmbeddingGatewayTest.kt`의 새 줄(schema mismatch·GetEmbeddingMetadata 예외 test).
  compile+test만 재확인하고 `check`(ktlint+detekt 포함) 전건을 다시 안 돌린 것이 원인 —
  이후 acceptance는 매번 `check` 전건으로 재확인한다.

## 2026-09-11T00:50:00Z — 포맷 수정
- cmd: `./gradlew --offline --no-daemon :adapters:ktlintFormat`
- exit: 0
- 핵심 결과: 긴 줄 자동 개행(로직 변경 없음)

## 2026-09-11T00:52:00Z — S-1 재실행(워킹트리, 포맷 수정 반영)
- cmd: `./gradlew --offline --no-daemon --no-build-cache clean check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, cpd 중복 0 · detekt issue 0 · workflow 146 / adapters 481 /
  app 129 tests, 실패·오류·skip 0(fresh XML 집계) — `EmbeddingShapeFailClosedTest` 5→8 ·
  `GrpcEmbeddingGatewayTest` 9→15 · `EmbeddingCallPolicyTest` 3→4(팀장 재확인 수치와 일치)

## 2026-09-11T00:55:00Z — rollback 목록 재산출(수정 라운드 1 반영)
- cmd: `git diff --name-status caee26c..HEAD`
- 핵심 결과: 파일 목록 불변(수정 라운드가 **기존 파일만** 고쳤다 — 신규 경로 0,
  `milestone-4.md`가 새로 공유 파일 집합에 들어온 것만 차이). rollback.md ①·② 갱신
  (milestone-4.md 커밋 해시 hunk 격리 절 추가).

## 2026-09-11T01:30:00Z — 독립 코드 리뷰 request_changes(F-A high, F-B~F-E medium, F-F~F-H low) 수정

F-A(high) 처방 뒤 결정성 실측:
- cmd: `./gradlew --offline --no-daemon --rerun-tasks :adapters:test --tests "bidvector.adapters.ml.EmbeddingBreakerTest"` ×3 (수정 직후)
- exit: 0 / 0 / 0 — `tests=2 failures=0` 3연속(개별 timestamp는 test-results XML)
- cmd: 같은 명령 ×3 (F-B~F-E 전부 반영한 최종 head 에서 재확인)
- exit: 0 / 0 / 0 — `tests=2 failures=0` 3연속, 총 6/6

F-B~F-E 컴파일·테스트:
- cmd: `./gradlew --offline --no-daemon :adapters:compileTestKotlin :workflow:compileKotlin :workflow:compileTestKotlin`
- exit: 0(1차 시도는 `EmbeddingCallPolicy.kt` KDoc의 중첩 주석 문법 오류로 실패 → 문구 수정 후 재시도 exit 0)
- cmd: `./gradlew --offline --no-daemon :adapters:test :workflow:test`
- exit: 0
- cmd: `./gradlew --offline --no-daemon --rerun-tasks :adapters:test :workflow:test`
- exit: 0 — workflow 146 / adapters 483 tests, 실패·오류·skip 0(32/32 task 강제 재실행,
  UP-TO-DATE 없음)

4D-1 test 무편집 재확인(F-E가 4D-1 production 파일 셋을 추가로 건드린 뒤):
- cmd: `git diff --stat caee26c..HEAD -- <4D-1 test 13파일>`
- exit: 0, 출력 0줄

## 2026-09-11T01:40:00Z — S-1 재실행(코드 리뷰 수정 반영, 임시 clone 아님 — 워킹트리, 이후 S-0 로 독립 재확인)
- cmd: `./gradlew --offline --no-daemon --no-build-cache clean check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, cpd 중복 0 · detekt issue 0 · workflow 146 / adapters 483 /
  app 129 tests, 실패·오류·skip 0(fresh XML 집계)

## 2026-09-11T01:46:00Z — rollback 재실측(임시 clone, 코드 리뷰 수정 반영)
- cmd: `git restore --source=caee26c --staged --worktree -- <in_scope 비공유 경로 21파일 + 2디렉터리 = 인자 23개>`
  (F-E로 `ParsedSuccessFields.kt`·`ReleaseCheck.kt`·`RetryRules.kt` 3개가 추가되고
  `EmbeddingReleaseShapeValidation.kt`는 신설+삭제로 net diff에서 빠져 순감소 없이 +3)
- exit: 0
- cmd: `git diff 6371f28~1..6371f28 -- config/quality/gate-tests.properties | git apply -R` / `git diff 181892b~1..181892b -- milestone-4.md | git apply -R`
- exit: 0 / 0
- cmd: `grep -c "4D-2 착수 2026-09-10(임베딩 gateway" milestone-4.md` (내 문단 사라짐) / `grep -c "4D-1 종결\|4B-4 착수" milestone-4.md` (남의 문단 남음)
- 결과: 0 / 3(둘 다 이전 라운드와 동일)
- cmd: `git diff caee26c -- <in_scope 코드 경로 전체>` (되돌린 뒤)
- exit: 0, 출력 0줄
- cmd: `./gradlew --offline --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin :adapters:compileKotlin :adapters:compileTestKotlin :workflow:test :adapters:test`
- exit: 0
- 핵심 결과: workflow 134 / adapters 449 tests — 1차·2차 rollback 실측과 완전히 동일
  수치(4D-1 골격 보존 재확인)

## 2026-09-11T02:00:00Z — 최종 acceptance S-0~S-7(최종 head, 독립 임시 clone)

- cmd: `git clone . <scratch>/s0-r2-final && cd <scratch>/s0-r2-final && ./gradlew --offline --no-daemon --no-build-cache clean check`(S-0·S-1)
- exit: 0 — BUILD SUCCESSFUL, **354 tasks 전건 executed**(fresh clone, UP-TO-DATE 0).
  cpd 중복 0 · detekt issue 0 · workflow 146 / adapters 483 / app 129 tests, 실패·오류·
  skip 0(fresh XML 집계)
- 같은 clone 에서 `EmbeddingBreakerTest`만 필터해 `--rerun-tasks` 3연속 재확인 —
  exit 0/0/0, `tests=2 failures=0` 3연속(F-A 최종 확증, 오늘 세 번째 clone에서 총 9/9)
- cmd: `./gradlew --offline --no-daemon :adapters:test` (S-2) — exit 0
- cmd: `./gradlew --offline --no-daemon :workflow:test` (S-3) — exit 0
- cmd: `./gradlew --offline --no-daemon :adapters:moduleDependencyGate :adapters:sizeGate :adapters:cpdCheck contractGate` (S-4, `contractGate`는 root task) — exit 0
- cmd: `./gradlew --offline --no-daemon :app:test` (S-5, `:app:test`와 별도 호출) — exit 0
- cmd: `./gradlew --offline --no-daemon qualityBaseline` (S-6) — exit 0
- cmd: `./gradlew --offline --no-daemon :app:gateExecutionGate` (S-7, S-5와 별도 호출) — exit 0
- **측정 정직성**: S-2·S-3·S-5·S-6·S-7은 S-0 직후 같은 clone에서 돌려 UP-TO-DATE였다 —
  그 개별 재실행 자체는 「이미 실행됨」의 재확인이고, 진짜 신선 실행 근거는 S-0의
  354/354 executed다. `--tests` 필터(F-A 3연속 확인)는 별도 단일 task 호출로만 걸어
  다른 acceptance task와 섞이지 않았다.
- `MlGateRegistrationTest`: 이 라운드는 신규 test class 를 추가하지 않았다(기존 파일
  수정·삭제만) — 등재 완전성 재검증 대상이 아니다. S-0의 fresh adapters 483 tests
  0 failures 안에 이 test 도 포함돼 통과가 확인된다(개별 XML은 이후 필터 실행으로
  덮어써져 이 문서 작성 시점엔 남아 있지 않다 — 알려진 문서 한계, evidence 크기
  때문에 재실행하지 않는다).
- secret 스캔: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4d2/` — 매치는 이 문서 자신의 스캔 명령 인용뿐(육안 확인, 위와 같은 자기 인용 패턴)
