# M10 / 10B — commands

출력 전문을 붙이지 않는다(핵심 결과 한 줄). 감사자는 명령을 다시 돌린다.
형식: `명령` → exit · 핵심 결과.

## RED — 등식이 없는 트리

- `./gradlew --no-daemon :app:test --tests '…OpenApiBidCaseContractTest' --rerun` → **1** ·
  2 tests, 1 failed — 「command 칸이 enum 을 선언하지 않았다」(음성 대조는 통과)
- `npm run typecheck`(W-3, 생성 타입이 아직 `string`) → **2** · 오류 둘 — 좁음 가드
  TS2322(`never` 에 배정 불가) · 화면 어휘 게이트 모집단 TS7053
- 비문자 열거 음성 대조(cr r0 L-3) — 대조 test 만 올린 트리에서
  `:app:test --tests '…OpenApiBidCaseContractTest' --rerun` → **1**(3 tests, 1 failed: 그 대조만) ·
  헬퍼를 고친 뒤 같은 명령 → **0**
- 별칭을 거치지 않는 타입 등식(v M-1) — 단언이 없는 트리에서는 아래 **변이 5·6 이 전부 초록**
  이었고, 단언을 올린 뒤 그 둘이 RED 다. 그 측정이 「변이」 절에 있다

## GREEN — 판정 대상 트리에서 CI 명령 그대로

web job:

- `npm ci`(W-1) → 0 · 277 packages
- `npm run lint`(W-2) / `typecheck`(W-3) / `gate:src-tree`(W-4) / `api:types:check`(W-5) →
  0 / 0 / 0 / 0 · W-5 는 생성 타입이 계약과 바이트까지 같다(192593 bytes)
- `npm run test`(W-6) → 0 · 40 files, 858 tests passed
- `npm run gate:tests`(W-7) / `build`(W-8) / `gate:boundary-canary`(W-9) → 0 / 0 / 0 ·
  등록부 기준 skipped·todo·failed 0 · 번들 경계가 메인·워커 두 그래프에서 활성
- `npx playwright install chromium`(W-10) / `npm run test:e2e`(W-11) → 0 / 0 · 2 passed

check job:

- `./gradlew --no-daemon check` → **0** · BUILD SUCCESSFUL 40m 39s · 424 actionable
  (41 executed) · 새 계약 test 가 그 실행에 있다(결과 XML: tests 3 · skipped 0 · failures 0 ·
  errors 0) — 게이트 등록부 양방향 등식도 같은 실행이 든다

**W-6 의 수집에 호스트 부하에 민감한 게이트 test 하나가 있다**(이 slice 의 in_scope 밖 — 원천과
바이트가 같다). 그 파일의 첫 test 가 ESLint 를 처음 세우는 비용을 지고 vitest 기본 timeout 은
5000ms 다. 같은 명령으로 잰 그 test 의 벽시각: **격리 1257ms** · **다른 전건 `check` 하나와
경쟁 2009~2279ms**. 경쟁을 더 올리면 그 한 칸이 먼저 닿는다 — 위 W-6 은 그 경쟁 아래에서도
858/858 이다.

## 병합 — 공유 파일 하나의 충돌과 합집합 함정

- `git merge <고정 main SHA>` → **1** · `docs/discovery/capability-map.md` 한 파일 충돌(겹치는
  경로가 그 하나 — `comm -12` 실측). 손 해소: (f) 앞, (h) 뒤
- 병합 합집합 함정 셋 → 절 문자 (d)→(e)→(f)→(h) · (f) 행 **5** · (h) 행 **3**(종결 2 + 신설 1) ·
  **어느 쪽 본문도 접히지 않았다**(충돌 파일을 뺀 내 경로가 내 head 와 0 줄 · main 경로가 원천과
  0 줄)
- `./gradlew --no-daemon testClasses`(병합 뒤 **컴파일 먼저**) → **0**

## 변이 — 바꿔치우기(더하기만 한 변이는 세지 않는다)

버릴 clone 에서, 판정 대상 트리에. 받는 층의 표는 `checklist.md` §2 다.

- 변이 1(Kotlin 어휘가 늘고 계약은 그대로) → **1** · 2 tests, 1 failed. **컴파일은 통과**하고
  등식에서 멈춘다
- 변이 2(계약을 고치고 재생성하지 않음) → `api:types:check` **1** · 커밋본과 재생성본의 바이트
  가 다르다
- 변이 2b(계약 어휘가 Kotlin 과 갈림 + **재생성** — W-5 는 초록) → **1** · W-5 를 통과한 뒤에도
  등식이 잡는다
- 변이 3(web 표의 키를 바꿔치움 — **표와 test 목록을 함께**) → `typecheck` **2** ·
  TS2353(표의 키가 계약 유니온에 없다) + TS7053
- 변이 4(계약의 `enum` 을 걷어 `type: string`) → 위 **RED** 두 줄이 그 측정이다(그 트리가 곧 이
  변이다)
- 변이 5(별칭을 **손 유니온으로 바꿔치움** + 계약 어휘 하나 늘려 **재생성**) — 유도 별칭 매치
  **0**(바꿔치움이 선 것) · 생성 타입에 늘린 값 매치 **1** → `lint` **0** · `api:types:check`
  **0** · `test` **0** · `typecheck` **2**(TS2322). **네 게이트 가운데 하나만 잡는다** — 별칭을
  거치지 않는 등식이고, 그것이 없으면 그 뒤 계약에 명령이 늘 때 web 이 조용히 degrade 한다
- 변이 6(별칭은 유도로 두고 `Exclude<…>` 로 좁히고 **표·test 목록 동반 축소**) → `typecheck`
  **2**(TS2322, 변이 5 와 같은 자리) · 양방향 등식의 **역방향**이 잡는다(한쪽 포함만 재면 초록)
- 통제군(변이 없는 같은 트리, 같은 명령) → `typecheck` **0** · Kotlin 쪽 통제군은 그 로그 전체에
  `FROM-CACHE` **0 건**(통제군이 실제로 실행됐다)

## 낡는 좌표 — 역방향 훑기

- 편집한 파일 stem 여덟에 `<stem>[A-Za-z.-]*:[0-9]` 훑기(`command grep`, node_modules·build·dist
  제외) → 인용이 있는 stem 은 **둘**이고 **최대 인용 줄이 내 첫 변경 줄보다 앞**이다
  (capability-map 3457 < 3771 · 계약 문서 874 < 2703 — 양끝을 명령으로 냈다). 생성 타입 stem 의
  매치 1 은 **그 계약 문서를 가리키는 같은 인용**이고 생성 타입을 가리키는 인용은 0 이다

## rollback — v M-1·L-1 뒤 재측정

실측 HEAD 와 완료 계수는 `rollback.md` 가 정본이다.

- 셋째 단언(`git rev-parse -q --verify <sha>:<path> || echo ABSENT`, 복원 9 경로) → **여덟 같음 ·
  하나 다름**(`capability-map.md` — 원천을 교체해야 하는 바로 그 이유)
- `git rev-list --merges <원천>..<실측 HEAD>` → 내 병합 커밋 **하나**
- 복원 목록 양끝 = 원천·실측 HEAD → 13 경로, evidence 넷을 빼 **9**
- ①②③ → exit **0** · D **1** · M **8** · 복원 9 경로 diff **0 줄**
- 되돌린 트리 ↔ **원천 트리** 전수 대조 → 차이가 **내 evidence 네 파일(A)뿐** — ④⑤ 와 나머지
  Kotlin 게이트를 트리 동일성으로 갈음한 근거(앵커는 그 main 커밋의 CI)
- 되돌린 트리 `leakPatternGate --rerun` → **0** · web 게이트 여덟 → 전부 **0**

## 위생

- `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m10/timeline-command-enum/`
  → 1(매치 없음) · 최종 HEAD 의 `leakPatternGate` 결과 정본은 verifier 와 PR 조치 코멘트다
- `git status --porcelain -- <in_scope 10 경로>` → 빈 출력 · 양성 대조 1회(비파괴 절삭)
- 크기 게이트 — **쓴 계량은 줄 수이고 두 수를 그대로 적는다**(cr r0 L-2):
  `git diff --numstat <원천>..HEAD -- . ':!reports/evidence'` 가 **+267 / -72 = 339 바뀐 줄** ·
  evidence 네 파일 `wc -l` 은 그 커밋에서 센다. 판정은 **바뀐 줄** 기준이고 더한 줄만 세면
  267 로 갈린다. **지금도 초과**이고(v M-1 축의 변이 둘과 W-6 부하 민감 한 칸이 더해졌다)
  예외 승인과 사유는 `checklist.md` §4 다
