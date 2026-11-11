# M10 / 10B — commands

출력 전문을 붙이지 않는다(핵심 결과 한 줄). 감사자는 명령을 다시 돌린다.
형식: `명령` → exit · 핵심 결과.

## RED — 등식이 없는 트리

- `./gradlew --no-daemon :app:test --tests '…OpenApiBidCaseContractTest' --rerun` → **1** ·
  2 tests, 1 failed — 「command 칸이 enum 을 선언하지 않았다」(음성 대조는 통과)
- `npm run typecheck`(W-3, 생성 타입이 아직 `string`) → **2** · 오류 둘 — 좁음 가드
  TS2322(`never` 에 배정 불가) · 화면 어휘 게이트 모집단 TS7053

## GREEN — web job 명령 그대로 (판정 대상 트리)

- `npm ci`(W-1) → 0 · 277 packages
- `npm run lint`(W-2) / `typecheck`(W-3) / `gate:src-tree`(W-4) / `api:types:check`(W-5) →
  0 / 0 / 0 / 0 · W-5 는 생성 타입이 계약과 바이트까지 같다
- `npm run test`(W-6) → 0 · 40 files, 857 tests passed
- `npm run gate:tests`(W-7) / `build`(W-8) / `gate:boundary-canary`(W-9) → 0 / 0 / 0 ·
  등록부 기준 skipped·todo·failed 0 · 번들 경계가 메인·워커 두 그래프에서 활성
- `npx playwright install chromium`(W-10) / `npm run test:e2e`(W-11) → 0 / 0 · 2 passed

## 전건 — `check` job 의 명령 그대로

- `./gradlew --no-daemon check` → **0** · BUILD SUCCESSFUL 37m 36s. 새 계약 test 가 그 실행에
  있다(결과 XML: tests 2 · skipped 0 · failures 0 · errors 0) — 게이트 등록부 양방향 등식도
  같은 실행이 든다.

## 변이 — 바꿔치우기(더하기만 한 변이는 세지 않는다)

버릴 clone 에서. 받는 층의 표는 `checklist.md` §2 다.

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
- 통제군(변이 없는 트리, 같은 명령 `--rerun`) → **0** · 그 로그 전체에 `FROM-CACHE` **0 건**
  (통제군이 실제로 실행됐다)

## 낡는 좌표 — 역방향 훑기

- 편집한 파일 stem 일곱에 `<stem>[A-Za-z.-]*:[0-9]` 훑기(`command grep`, node_modules·build 제외)
  → 0 · 인용이 있는 stem 은 둘이고 **최대 인용 줄이 내 첫 변경 줄보다 앞**이다(capability-map
  3457 < 3746 · 계약 문서 874 < 2703 — 양끝을 명령으로 냈다). 나머지 다섯 stem 은 인용 0.

## 위생

- `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m10/timeline-command-enum/`
  → 1(매치 없음)
- `git status --porcelain -- <in_scope 9 경로>` → 빈 출력 · 양성 대조 1회(비파괴 절삭)
- 크기 게이트: `git diff --numstat <base>..HEAD -- . ':!reports/evidence'` 와 evidence `wc -l`
  → evidence 가 산출물보다 작다
