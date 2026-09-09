# commands.md — M4/4C-1

명령과 종료 코드만 남긴다(출력 전문 금지, evidence-pack 스킬 규격). 감사자는 명령을 다시 돌린다.

## 2026-09-09T06:00Z — RED 확인
- cmd: `./gradlew --no-daemon :workflow:compileTestKotlin`(`event` 패키지 test 5개를 먼저
  쓰고, main 타입이 아직 없는 상태로 실행)
- exit: 1 — `Unresolved reference` 다수(`transitionOutbox`·`OutboxEntryState`·
  `OutboxTransitionOutcome` 등). RED 확정.

## 2026-09-09T06:03Z — main 타입 구현 뒤 컴파일·test 그린
- cmd: `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin`
- exit: 0
- cmd: `./gradlew --no-daemon :workflow:test`
- exit: 1(초기, `InboxDedupPropertyTest` — `Arb.string(1, 4)`가 공백만인 문자열을 뽑아
  `IdempotencyKey` 생성 불변식(빈/공백 문자열 거부) 위반) → `Arb.string(range, 영숫자
  alphabet)`로 정정 → exit: 0(56 tests, 0 failed).

## 2026-09-09T06:10Z — S-6(`gateExecutionGate`) 재실행 중 app 모듈 파급 발견
- cmd: `./gradlew --no-daemon :app:gateExecutionGate`
- exit: 1(초기) — `app/src/test/kotlin/bidvector/app/conformance/StrategyEditExecutors.kt`
  의 `RecordingEventSink`가 4A `EventSink`(4C-1 좁은 예외로 `publish(event, actor)`로
  넓어짐)의 새 시그니처를 구현하지 못해 컴파일 실패. **이 파일은 scope.md `in_scope`에
  없다** — `EventSink` 시그니처 확장(승인된 좁은 예외, `Ports.kt`/`EditStrategyWorkflow.kt`)
  의 기계적 파급이라 고쳤다(fake 시그니처만 맞춤, corpus 산출 값 무변경). 팀장에게 별도
  보고.
- 수정 뒤: exit 0.

## 2026-09-09T06:15Z — clean check 1차(ktlint·detekt·cpd 3건)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1 — ① `OutboxTransitionTableTest.kt:69` MaxLineLength(ktlint) ② `OutboxEventSinkTest.kt`
  `InMemoryOutboxPort`의 `markDelivered`/`markFailed`/`markIsolated` 세 빈 override
  (detekt `EmptyFunctionBlock`) ③ `EventEnvelope.kt`의 `restore`가 `newEnvelope`와 몸통
  22줄이 겹침(CPD `workflow` 모듈).
- 수정: ① `:workflow:ktlintFormat` ② 세 override 를 호출 기록 리스트로(`delivered`·
  `failed`·`isolated`) ③ `restore`가 `newEnvelope`에 위임하도록 재작성(경계 KDoc은
  「호출부를 섞지 않는다」로 유지 — 위임 자체는 `newEnvelope`가 여전히 `internal`이라
  경계를 열지 않는다는 근거를 KDoc에 남김).

## 2026-09-09T06:20Z — clean check 2차(전건)
- cmd: `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin :workflow:test :workflow:ktlintCheck :workflow:detekt :workflow:cpdCheck`
- exit: 0
- cmd: `./gradlew --no-build-cache clean check`(S-1)
- exit: 0 — `BUILD SUCCESSFUL in 35s`, 344 actionable tasks(320 executed·24 up-to-date).

## 2026-09-09T06:25Z — S-2~S-6 개별 재확인
- cmd: `./gradlew --no-daemon :workflow:test`(S-2) — exit: 0
- cmd: `./gradlew --no-daemon :workflow:moduleDependencyGate :workflow:sizeGate :workflow:cpdCheck`(S-3) — exit: 0
- cmd: `./gradlew --no-daemon :workflow:test --tests '*EventBoundaryTest*'`(S-3b) — exit: 0
- cmd: `./gradlew --no-daemon :app:test --tests '*Conformance*'`(S-4) — exit: 0 — 74 tests
  (기존과 동일 개수 — 이번 라운드는 fixture 무변경, `strategy-edit-*` corpus 는 actor 를
  투영하지 않으므로 산출 값도 무변경).
- cmd: `./gradlew --no-daemon qualityBaseline`(S-5) — exit: 0
- cmd: `./gradlew --no-daemon :app:gateExecutionGate`(S-6) — exit: 0

## 2026-09-09T06:30Z — S-0(임시 clone, 커밋 뒤 재실행)
- cmd: (커밋 뒤) `d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08 --single-branch <repo> "$d/repo" && (cd "$d/repo" && ./gradlew --no-build-cache clean check)`
- exit: 0 — `BUILD SUCCESSFUL`, 커밋된 head 기준 임시 clone에서 root `clean check` 전건 통과.

## 2026-09-09T06:35Z — 값 획득 축 실측(설계 검토 (2)) — internal 커널의 위조·획득 동시 차단
- cmd: 임시 clone(4C-1 커밋 포함 head)에
  `workflow/src/main/kotlin/bidvector/workflow/event/`를 직접 참조하는 시도를
  `app/src/test/kotlin/bidvector/app/verifyprobe/EnvelopeForgeProbe.kt`에 심는다 —
  `EventEnvelope(...)` 직접 생성자 호출(1차 위조), `newEnvelope(...)`/`forStrategyUpdated(...)`
  직접 호출(2차 획득), `transitionOutbox(...)` 직접 호출(3차 획득) 세 형태를 각각 별도
  파일로 시도하고 `./gradlew --no-daemon :app:compileTestKotlin`
- exit: 1(세 형태 전부) — 핵심 결과: `EventEnvelope(...)` → `Cannot access '<init>':
  it is internal`. `newEnvelope(...)`/`forStrategyUpdated(...)` → `Cannot access 'fun
  newEnvelope'/'fun forStrategyUpdated': it is internal in file`. `transitionOutbox(...)`
  → `Cannot access 'fun transitionOutbox': it is internal in file`. `OutboxTransition.
  ToDelivered(...)` 직접 생성자 호출도 `Cannot access '<init>': it is internal`로 거부
  (같은 clone, 네 번째 probe).
- 양성 대조: 위 probe 넷을 지우고 `./gradlew --no-daemon :app:compileTestKotlin
  :workflow:test :app:test --tests '*Conformance*'` — exit 0(`BUILD SUCCESSFUL`, 정상
  배선·corpus 다섯 무영향). probe 는 커밋하지 않았다(임시 clone 전용, evidence-pack
  「자기 검사 하네스 금지」— 이 실측 자체가 산출물이 아니라 명령 기록이다).

## 2026-09-09T06:45Z — secret 스캔
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4c1/ --exclude=commands.md`
- exit: 1(매치 없음)
- 핵심 결과: 0건. Telegram id·사업자 정보 없음(육안 확인 — corpus 는 4A 관례를 그대로
  쓴다, `corpus-operator`/`corpus-session`).

## 2026-09-09T06:48Z — 역방향 파급 grep
- cmd: `grep -rn "data-dictionary\.md:[0-9]\+\|data-dictionary:[0-9]\+" --include="*.md" --include="*.kt" --include="*.properties" .`
- exit: 0 — 매치 4건(`m1/1b/scope.md:274`·`m1/1e/scope.md:72`·`m1/1c/checklist.md:148`·
  `m0/0c/commands.md:223`), 전부 §2.2.5 삽입 지점(683행 부근)보다 **아래** 줄번호(960·980·
  1551·1554)를 가리켜 +18줄 삽입의 영향권 안이다. **그러나 넷 다 4C-1 in_scope 밖의 닫힌
  slice evidence**이고, 그중 `m1/1c/checklist.md:148` 자신이 이미 「닫힌 slice evidence
  의 좌표 셋이 낡았다」고 선언해 이 낡음을 **기존에 알려진 제한으로 인수**하고 있다 —
  4C-1이 새로 만드는 낡음이 아니다. 알려진 제한에 등재(checklist.md).
- cmd: `grep -rn "capability-map\.md:34[0-9][0-9]\|capability-map:34[0-9][0-9]" --include="*.md" --include="*.kt" .`
- exit: 0 — 매치 1건(`reports/evidence/harness/test-discovery-guard/commands.md:100`),
  가리키는 줄번호(3457)가 이번에 편집한 3427행이 아니고, 편집도 같은 줄 치환(`git diff
  --stat`으로 capability-map.md 는 `2 +-` — 1줄 교체, 줄 수 불변) 확인 — 영향 없음.

## 2026-09-09T06:50Z — clean-tree 게이트(경로 개별 인자, 양성 대조 포함)
- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0, 출력 없음(신규 디렉터리 `workflow/src/main/kotlin/bidvector/workflow/event/`·
  `workflow/src/test/kotlin/bidvector/workflow/event/`는 `??`로 별도 확인 뒤 커밋 포함).
- 양성 대조: `workflow/src/main/kotlin/bidvector/workflow/event/EventIdFactory.kt`에 개행
  한 줄 추가 후 그 경로만으로 재실행 → `M` 관측(exit 0, 비어있지 않음) → `head -n`으로
  추가한 줄만 절삭(4A 사고 이후 채택한 안전한 방식 — `git checkout --` 미사용) → 재확인
  (비어있음, exit 0).
