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

## 2026-09-09T06:12Z — 커밋(head `13cf0f6`)
- cmd: `git add <in_scope 경로 개별 인자> && git commit -m ... -- <같은 경로>`
- exit: 0 — 25 files changed(1191 insertions·14 deletions). 경로를 커밋 명령에 명시해
  인덱스 다른 항목 혼입을 막았다(evidence-pack 스킬 「공유 워킹트리의 커밋」).

## 2026-09-09T06:14Z — S-0(임시 clone, `13cf0f6`에 pin)
- cmd: `git clone --quiet --no-hardlinks <repo> "$d/repo" && (cd "$d/repo" && git checkout
  --quiet 13cf0f6) && (cd "$d/repo" && ./gradlew --no-build-cache clean check)` — 착수
  뒤 이 브랜치에 다른 세션(팀장)의 후속 커밋 둘(`de99ddb` 하네스·`4e07682` 4B-1 계약)이
  더 붙어 「브랜치 clone」만으로는 4C-1 자신의 head 를 못 집는다 — `13cf0f6`으로 명시
  pin.
- exit: 0 — `BUILD SUCCESSFUL in 57s`, 353 actionable tasks 전부 executed(캐시 없는
  임시 clone, `13cf0f6` 고정).

## 2026-09-09T06:16Z — 값 획득 축 실측(설계 검토 (2)) — internal 커널의 위조·획득 5형태 전부 거부
- cmd: 위 clone(`13cf0f6`)에 `app/src/test/kotlin/bidvector/app/verifyprobe/`아래
  probe 셋을 순서대로 심고 지우며 `./gradlew --no-daemon :app:compileTestKotlin`:
  (1) `EventEnvelope(...)` 직접 생성자 호출(위조) (2) `newEnvelope(...)`·
  `forStrategyUpdated(...)` 직접 호출(획득, 같은 파일) (3) `transitionOutbox(...)` 직접
  호출·`OutboxTransition.ToDelivered(...)` 직접 생성자 호출(획득+위조, 같은 파일)
- exit: 1(세 라운드 전부) — 핵심 결과: `Cannot access 'constructor(...): EventEnvelope<P>':
  it is internal in 'bidvector.workflow.event.EventEnvelope'` · `Cannot access 'fun
  newEnvelope(...)': it is internal in file` · `Cannot access 'fun forStrategyUpdated(...)':
  it is internal in file` · `Cannot access 'fun transitionOutbox(...)': it is internal in
  file` · `Cannot access 'constructor(entryId: OutboxEntryId): OutboxTransition.ToDelivered':
  it is internal in 'bidvector.workflow.event.OutboxTransition.ToDelivered'` — 다섯 형태
  전부 그 줄에서 정확히 거부됨을 원문으로 확인.
- 양성 대조: probe 디렉터리 삭제 뒤 `./gradlew --no-daemon :app:compileTestKotlin
  :workflow:test :app:test --tests '*Conformance*'` — exit 0(`BUILD SUCCESSFUL`, 정상
  배선·corpus 다섯 무영향). probe 는 커밋하지 않았다(임시 clone 전용, evidence-pack
  「자기 검사 하네스 금지」— 이 실측 자체가 산출물이 아니라 명령 기록이다).

## 2026-09-09T06:22Z — rollback 5단계 실측(같은 clone, `13cf0f6`)
- cmd: `git restore --source=4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599 --staged --worktree
  -- <in_scope 경로 12개 개별 인자>`(rollback.md 정본 명령)
- exit: 0 — ① `git status --porcelain` D 19 · M 8(rollback.md 목록과 일치) ② M 대상 8개
  경로의 `git diff <base> -- <경로>` 출력 0줄(빈 diff) ③ 신규 디렉터리(`event` 패키지 둘·
  `reports/evidence/m4/4c1`) 잔여 파일 0건(`find` 확인) ④ `./gradlew --no-daemon
  :workflow:compileKotlin :app:compileTestKotlin` exit 0(`BUILD SUCCESSFUL`, 29 actionable
  tasks) ⑤ `./gradlew --no-daemon :workflow:test :app:test --tests '*Conformance*'`
  exit 0(`BUILD SUCCESSFUL`). 다섯 확인 전부 통과 — M3/3B-2 verifier r2 교훈(「exit 0 =
  성공」만으로는 부족)에 따라 ④⑤(compile·test)까지 실측했다.
- clone 삭제(`rm -rf`), 되돌리지 않은 원 worktree에는 영향 없음(별도 clone).

## 2026-09-09T06:45Z — secret 스캔
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4c1/ --exclude=commands.md`
- exit: 0(매치 1건) — `checklist.md:14`의 「secret 스캔 통과 — 매치 0」 문장 자신이
  패턴 `secret`에 자기참조로 걸림(4A·M0 선례와 같은 갈래 — commands.md를 뺀 것만으로는
  체크리스트 문장까지는 못 뺀다). 실제 값이 아니라 이 절 문면 재스캔.
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4c1/ --exclude=commands.md --exclude=checklist.md`
- exit: 1(매치 없음) — 자기참조 제외 뒤 실제 매치 0건. Telegram id·사업자 정보 없음
  (육안 확인 — corpus는 4A 관례를 그대로 쓴다, `corpus-operator`/`corpus-session`).

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

## 2026-09-09T06:20Z — 하네스 레인 변경 재확인(리뷰 요청 시점, scope.md 상시 절 갱신)
- cmd: `git log --oneline 4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599..HEAD -- CLAUDE.md .claude/`
- exit: 0 — 1건(`de99ddb harness(evidence-pack): 양성 대조에서 git checkout -- 금지,
  비파괴 절삭으로`) — 이 slice 산출물 커밋(`13cf0f6`) **이후** 다른 세션이 붙인 하네스
  개정, in_scope 밖·rollback 대상 아님. `4e07682`(4B-1 계약 신설)는 하네스 경로가 아니라
  다음 slice 계약이라 이 절 대상이 아님. `reports/evidence/m4/4c1/scope.md`에 반영.

## 2026-09-09T06:24Z — clean-tree 게이트 최종(경로 개별 인자, 양성 대조 포함, `git checkout --` 미사용)
- cmd: `git status --porcelain -- <in_scope 경로 12개 개별 인자>`
- exit: 0, 출력 없음(구현 커밋 `13cf0f6` 대상 — `scope.md`·`rollback.md`의 리뷰 요청 시점
  갱신 두 건은 이 문서 자신의 산출물이라 이 확인 뒤 별도 커밋한다, evidence-pack 「낡는
  좌표」 규약처럼 이 evidence 커밋이 스스로를 가리키는 자기참조를 피한다).
- 양성 대조: `workflow/src/main/kotlin/bidvector/workflow/event/EventIdFactory.kt`
  (이 시점 클린)에 개행 한 줄 추가 → `M` 관측(exit 0, 비어있지 않음) → `head -n`으로
  추가한 줄만 절삭(4A 사고 이후 채택한 안전한 방식 — `git checkout --` 미사용) → 재확인
  (비어있음, exit 0). 실측 완료.
