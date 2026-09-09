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
  1551·1554)를 가리켜 삽입의 영향권 안이다. **정정(verifier B-6, 2026-09-09)** — 삽입은
  「+18줄」이 아니라 **+19줄·-4줄(순증 +15줄)**이다(재산출: `git diff --numstat
  4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599..3752b49 -- docs/discovery/data-dictionary.md`
  → `19  4`, 4C-1 자신의 기여만 격리해 잰다 — 이 시점 이후 4B-1 이 같은 파일에 §3.6·§13.2
  를 더 편집해 `HEAD` 기준 diff 는 다른 값을 낸다). **그러나 넷 다 4C-1 in_scope 밖의 닫힌
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

# 수정 라운드 1 — verifier 판정 not-ready(H-1), 재작업 1/5 (2026-09-09)

verifier 리포트: `_workspace/m4-4c1/03_verifier_report.md`(핀 `3752b49`). 처리: H-1/L-3
(같은 뿌리, 배치 변경) · M-1(rollback.md 줄 단위 조항) · L-1(수렴 property 민감화) ·
L-2(알려진 제한 등재) · B-1~B-3(장부층, 이 절 및 scope.md·rollback.md에서 정정).
**verifier가 이미 닫혔다고 판정한 넷은 재작업하지 않았다**(B-4·B-5·B-6은 이전 라운드
`52c94e6`·`3752b49`에서 이미 닫혔고, 이번 라운드는 B-1·B-2·B-3만 남았다).

## 2026-09-09T10:58Z — RED 확인(OutboxEntry.restore)
- cmd: `./gradlew --no-daemon :workflow:compileTestKotlin`(`OutboxEntryTest`를 먼저 쓰고
  `ClaimedOutboxRow`·`OutboxEntry.restore`가 아직 없는 상태로 실행)
- exit: 1 — `Unresolved reference 'ClaimedOutboxRow'` 등. RED 확정.

## 2026-09-09T11:00Z — H-1/L-3 시정 구현 뒤 컴파일·test
- cmd: `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin`
- exit: 0
- cmd: `./gradlew --no-daemon :workflow:test`
- exit: 0(전건 초록 — `OutboxEntryTest` 신설 1건 포함).

## 2026-09-09T11:02Z — L-1 시정 — 수렴 property에 처리 횟수 단언 추가, 변이로 민감도 실측
- cmd: `decideInbox`를 `InboxDecision.Process` 상수 반환으로 바꾼 변이로
  `./gradlew --no-daemon :workflow:test --tests '*InboxDedupPropertyTest*'`
- exit: 1 — **2건 FAILED**(수정 전에는 최종 집합만 비교해 이 변이에서도 초록이었다 —
  verifier L-1이 지적한 그 결함이 재현됨을 먼저 확인). 처리 횟수 단언(`foldProcessedCount`
  가 고유 key 수와 일치)을 추가한 뒤 같은 변이로 재실행 → **exit 1, 2건 FAILED**(수정된
  test가 변이를 실제로 잡음 — `AssertionFailedError: expected:<1> but was:<2>`).
- cmd: 변이를 `diff`로 원상 복구 확인(백업 파일과 바이트 단위 일치, `diff /tmp/InboxPort.kt.bak
  workflow/.../InboxPort.kt` → 원본 한 줄만 다름 확인 후 복구) → 재실행 exit 0.

## 2026-09-09T11:05Z — 전체 재검증(clean check, ktlint 자동정리 포함)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1(초기) — `OutboxEntryTest.kt` 줄바꿈(ktlint, 자동 수정 가능) 다수.
- 수정: `:workflow:ktlintFormat`.
- cmd: `./gradlew --no-build-cache clean check`(재실행)
- exit: 0 — `BUILD SUCCESSFUL in 33s`, 344 actionable tasks(320 executed·24 up-to-date).

## 2026-09-09T11:07Z — S-2~S-6 개별 재확인
- cmd: `./gradlew --no-daemon :workflow:test`(S-2) — exit 0
- cmd: `./gradlew --no-daemon :workflow:moduleDependencyGate :workflow:sizeGate :workflow:cpdCheck`(S-3) — exit 0
- cmd: `./gradlew --no-daemon :workflow:test --tests '*EventBoundaryTest*'`(S-3b) — exit 0
- cmd: `./gradlew --no-daemon :app:test --tests '*Conformance*'`(S-4) — exit 0, 74 tests(무변경)
- cmd: `./gradlew --no-daemon qualityBaseline`(S-5) — exit 0
- cmd: `./gradlew --no-daemon :app:test :app:gateExecutionGate`(S-6) — exit 0. **정정(L-7,
  verifier r2)** — 이전 판이 「필터 쓰면 거짓 실패」라 적었는데 부정확하다. verifier
  실측: 이건 게이트의 거짓 실패가 아니라 **오용**이고, 정확한 조건은 「**같은 gradle
  호출**에 `--tests` 필터와 `gateExecutionGate`를 함께 넣으면 `ArchitectureGate*` 미실행
  으로 exit 1」이다. `scope.md`의 S-4(`:app:test --tests '*Conformance*'`)와 S-6은
  **별도 호출**이라 안전하다 — S-4를 지우거나 S-6과 한 호출로 합치면 안 된다.

## 재산출 — B-1·B-2·B-3 (verifier 지시: 「수치는 전부 명령으로 재산출」)
- cmd: `git add -N workflow/.../ClaimedOutboxRow.kt workflow/.../OutboxEntryTest.kt && git diff
  --name-status 4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599 -- <in_scope 12개 경로>`
- exit: 0 — **A 21 · M 8**(라운드 1이 A에 둘 추가: `ClaimedOutboxRow.kt`·`OutboxEntryTest.kt`).
  rollback.md의 두 수치(머리말 19/8·확인 지점 18/9 불일치, B-1)를 이 값 하나로 통일했다.
- cmd: `git merge-base --is-ancestor de99ddb 13cf0f63da18e13f0e2befd519710a8635742004 && echo ancestor`
- exit: 0 — `ancestor` 출력. **B-3 확인** — `de99ddb`는 `13cf0f6`의 **조상**(이전)이지 이후가
  아니다. scope.md의 「이후」 서술을 정정했다.
- cmd: `git log --oneline 4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599..HEAD -- CLAUDE.md .claude/`
- exit: 0 — 1건(`de99ddb`). **B-2 확인** — rollback.md 머리말이 「0건」이라 적었던 것을
  scope.md와 같은 값(1건)으로 통일했다.

## 2026-09-09T11:20Z — 커밋(H-1/L-3/M-1/L-1/L-2/B-1~B-3 시정, head `7469bce`)
- cmd: `git add <in_scope 경로 12개 개별 인자> && git commit -m ... -- <같은 경로>`
- exit: 0 — 12 files changed(350 insertions·62 deletions). 4B-1 경로(`decision/**`·
  `fixtures/verdict-*`·`app/.../VerdictExecutors.kt`)는 포함하지 않았다(팀장 지시 —
  두 slice 를 한 커밋에 섞지 않는다) — `git status --porcelain` 커밋 직후 clean, 이후
  4B-1 흔적 0건 확인.

## 2026-09-09T11:22Z — S-0(임시 clone, `7469bce`에 pin)
- cmd: `git clone --quiet --no-hardlinks <repo> "$d/repo" && (cd "$d/repo" && git checkout
  --quiet 7469bce) && (cd "$d/repo" && ./gradlew --no-build-cache clean check)`
- exit: 0 — `BUILD SUCCESSFUL in 46s`, 353 actionable tasks 전부 executed(캐시 없는 임시
  clone, `7469bce` 고정).

## 2026-09-09T11:25Z — 값 획득 축 재실측(H-1 재작업) — 위조 8형태 전부 거부(restore·OutboxEntry 포함)
- cmd: 위 clone(`7469bce`)의 `app/src/test/kotlin/bidvector/app/verifyprobe/`에 여덜
  형태(① `EventEnvelope` 직접 생성자 ② `.copy(...)` ③ **`EventEnvelope.restore(...)`
  (H-1 핵심 — 이번 라운드에서 internal)** ④ `newEnvelope` ⑤ `forStrategyUpdated`
  ⑥ `transitionOutbox` ⑦ `OutboxTransition.ToDelivered` 직접 생성자 ⑧ **`OutboxEntry`
  직접 생성자(L-3)**)를 한 파일에 심고 `./gradlew --no-daemon :app:compileTestKotlin`
- exit: 1(여덜 형태 전부) — 핵심 결과(원문 그대로): `③ Cannot access 'fun <P>
  restore(...)': it is internal in 'bidvector.workflow.event.EventEnvelope.Companion'`·
  `⑧ Cannot access 'constructor(id: OutboxEntryId, envelope: EventEnvelope<*>, state:
  OutboxEntryState): OutboxEntry': it is internal in 'bidvector.workflow.event.
  OutboxEntry'` — 나머지 여섯도 각각 `it is internal in file`/`it is internal in
  '...'`로 거부. **H-1이 지목한 구멍(③)이 이번엔 다섯 형태 목록에 포함돼 있고, 실제로
  거부됨을 확인했다.**
- 양성 대조: probe 디렉터리 삭제 뒤 `./gradlew --no-daemon :app:compileTestKotlin
  :workflow:test :app:test --tests '*Conformance*'` — exit 0(`BUILD SUCCESSFUL`, 정상
  배선·corpus 74 tests 무영향). probe는 커밋하지 않았다.

## 2026-09-09T11:30Z — rollback 5단계 실측(같은 clone, `7469bce`) — 공유 파일 셋 줄 단위(M-1 시정 실제 적용)
- cmd: A 항목(21개) + M 중 4C-1 전용 넷(+4a/scope.md)을 `git restore --source=4ec4e504...`
  로 전체 복원. 공유 파일 셋(`gate-tests.properties`·`data-dictionary.md`·
  `capability-map.md`)은 **파일 전체 restore를 쓰지 않고**, 4C-1이 넣은 블록/행만 base
  형태로 텍스트 교체(Python 스크립트, hunk 경계가 물리적으로 분리돼 있어 안전 — rollback.md
  §「공유 파일」 표 참고)했다.
- exit: 0(전 단계) — ① `git status --porcelain` **D 21 · M 8**(rollback.md 목록과 일치)
  ② 공유 파일 확인: `gate-tests.properties`의 `gate.tests.workflow`에 `event.*` 매치
  0건(4C-1 몫 제거) + `gate.tests.decision` 8개 class **그대로**(4B-1 몫 보존) ·
  `data-dictionary.md` §2.2.5가 「어휘 자리만 둔다」 placeholder로 복귀 + §3.6(4B-1)
  **그대로** · `capability-map.md`의 `OPEN-OPS-10` 행이 원래 형태로 복귀 +
  `OPEN-DIC-03` 행(4B-1) **그대로** ③ 신규 디렉터리 잔여 파일 0건 ④ `./gradlew
  --no-daemon :workflow:compileKotlin :decision:compileKotlin :app:compileTestKotlin`
  exit 0(`BUILD SUCCESSFUL`, 29 actionable tasks — **`:decision:compileKotlin`이 여전히
  성공한다는 것 자체가 4B-1 무영향의 증거**) ⑤ `./gradlew --no-daemon :workflow:test
  :decision:test :app:test --tests '*Conformance*'` exit 0(`BUILD SUCCESSFUL`, 37
  actionable tasks — `:decision:test`도 초록).
- clone 삭제(`rm -rf`), 되돌리지 않은 원 worktree에는 영향 없음.

## 2026-09-09T11:35Z — secret 스캔 재확인
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4c1/ --exclude=commands.md --exclude=checklist.md`
- exit: 1(매치 없음).

## 2026-09-09T11:36Z — clean-tree 게이트(경로 개별 인자, 양성 대조 포함, `git checkout --` 미사용)
- cmd: `git status --porcelain -- <in_scope 경로 12개 개별 인자>`
- exit: 0, 출력 없음(구현 커밋 `7469bce` 대상).
- 양성 대조: `workflow/src/main/kotlin/bidvector/workflow/event/ClaimedOutboxRow.kt`
  (이 시점 클린)에 개행 한 줄 추가 → `M` 관측(exit 0, 비어있지 않음) → `head -n`으로
  추가한 줄만 절삭(`git checkout --` 미사용) → 재확인(비어있음, exit 0). 실측 완료.

## 2026-09-09T11:38Z — 하네스 레인 변경 재확인(수정 라운드 1 완료 시점)
- cmd: `git log --oneline 4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599..HEAD -- CLAUDE.md .claude/`
- exit: 0 — **2건**(`de99ddb`·`ea79355`, 1건 추가) — `scope.md`·`rollback.md` 양쪽에
  반영해 두 문서의 건수를 통일했다(verifier B-2 재발 방지 — 매 라운드 리뷰 요청 시점에
  이 명령을 다시 돌리고 두 문서를 함께 갱신한다).

## 2026-09-09T12:30Z — 장부층 일괄(재검증 r2, L-4~L-7) — 코드 무변경, 회귀만 확인
- verifier 재검증 판정 **ready-for-review**(`_workspace/m4-4c1/04_verifier_report_r2.md`),
  산출물층 blocker/high 0. 장부층 L-4~L-7 을 한 커밋으로 반영.
- **L-4(신규 표적, 등재만)**: `OutboxEventSink` 자기-조립 잔여 확인 — 임시 probe(`app`
  모듈, `workflow`와 다른 모듈)에 self-supplied `FakeOutboxPort`·`FakeEventIdFactory`·
  `FakeClock`을 심고 `OutboxEventSink(fake, fake, fake)` 직접 생성.
  cmd: `./gradlew --no-daemon :app:compileTestKotlin` — exit 0(컴파일 성공, 결함이 아니라
  well-formed 발행 경로가 열려 있다는 확인). probe 삭제 후 `git status --porcelain` —
  공백(clean). checklist.md 「알려진 제한」 10번에 등재.
- **L-5(문면 정정)**: checklist.md의 「어댑터는 `EventEnvelope`를 전혀 다루지 않는다」를
  「만들지 않는다」로 — `register`는 어댑터 자신의 메서드라 `EventEnvelope`를 인자로
  받는다. (코드 KDoc `OutboxPort.kt`은 이 라운드 대상이 아니다 — 코드 무변경.)
- **L-6(문면 정정)**: `git log -p -- config/quality/gate-tests.properties`로 재확인 —
  `gate.tests.workflow` 키·블록은 **4A**가 만들었다(`harness(m4-4a): gate.tests.workflow
  신설` 커밋 실측). 4C-1은 그 블록에 `event.*` 줄 여섯만 끼워 넣었다(`strategy.*` 줄
  여섯은 무접촉). rollback.md 표의 「블록 전체(신설 축)」를 정정 — **실제 되돌림 실행
  (line-level, `event.*` 여섯 줄만 걷기)은 이미 옳았다**, 서술만 틀렸었다.
- **L-7(acceptance 규정 문면)**: 이전 판의 「필터 쓰면 거짓 실패」서술을 정정 — 정확한
  조건은 「**같은 gradle 호출**에 `--tests` 필터와 `gateExecutionGate`를 함께 넣으면
  `ArchitectureGate*` 미실행으로 exit 1」이다. `scope.md`의 S-4·S-6은 별도 호출이라
  안전 — 뭉뚱그리면 뒤 slice가 S-4를 지울 위험이 있어 정확히 적었다.
- cmd: `./gradlew --no-daemon :workflow:test` — exit 0.
- cmd: `./gradlew --no-daemon :app:test`(별도 호출, 필터 없음) — exit 0 —
  `SharedKernelCorpusConformanceTest tests="86" failures="0"`(4B-1 승격 반영, 코드
  무변경이므로 4C-1 자체 회귀 없음 확인).
- cmd: `./gradlew --no-daemon qualityBaseline` — exit 0.
- S-0 전건 재실행 없음(코드 무변경, 팀장 지시).
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"
  reports/evidence/m4/4c1/ --exclude=commands.md --exclude=checklist.md` — exit 1(매치
  없음, 자기참조 파일 둘 제외 시 실 비밀값 0건, 기존 패턴과 동일).
