# M11 / 11B — commands

출력 전문을 붙이지 않는다(핵심 결과 한 줄). 감사자는 명령을 다시 돌린다.
형식: `명령` → exit · 핵심 결과. 변이가 어느 층에 걸리는지는 `checklist.md` §2 가 정본이다.

## RED — 등식이 없는 트리

- `:app:test --tests '…OpenApiBidCaseContractTest'` → **1** · 5 tests, 2 failed —
  「toState 칸이 enum 을 선언하지 않았다」 · 「`null`↔`nullable` 결합 음성 대조가 던지지 않았다」
- `npm run typecheck`(W-3, 생성 타입이 아직 `string`) → **2** · 오류 **넷 / 한 파일**
  (`caseDisplay.test`) — 좁음 가드 둘이 `never`(도착·출발) · 양방향 등식 둘이 `false`. 건 상태
  칸의 등식 줄은 그때도 초록이다(그 칸은 이미 `enum` 이었다 — 이 slice 가 더하는 것은 Kotlin 쪽
  등식이다). 아래 M1 의 「넷 / 세 파일」과 **수가 같고 자리가 다른 별개 측정**이다(cr r1 LOW 가
  가리킨 혼동) — 여기는 어휘가 아직 열린 트리라 fixture 가 좁은 유니온을 만나지 않는다
- `npx vitest run src/screens/WorksheetScreen.test.tsx`(렌더 잠금) → **1** · 1 failed,
  49 passed — 타임라인이 표 밖 상태에 **코드 원문**을 세웠다
- 질의 인자 축(계약 갱신 둘째)의 등식 줄은 **태생이 초록**이다(그 param 이 이미 `enum` 이고
  어휘가 같았다) — 그 자리는 RED 대신 변이 둘로 쟀다(아래)

## 착수·갱신 실측

- 전방 호환 ①: 그 응답을 읽는 자리 열거(생성 클라이언트 호출 하나 + 여정 e2e 의 손 표) → 저장소
  밖 소비자 0 · 둘째 생성 클라이언트 0
- 전방 호환 ③: 두 상태 칸의 wire 값 산출 자리 → 포트의 상태 열거에서 `.name` 뿐
- 어휘 사본 전수(`WATCHING` 선언 자리) → **다섯**. 등식을 가진 것은 대사 쪽 기록 상태 칸 하나였다
- 계약 갱신 둘째의 요구 재측정: 목록 질의 상태 param → **`enum` 이고 값 열넷** · 집합·차례가
  도메인 열거와 **같다**(양끝을 명령으로 냈다)

## GREEN — 판정 대상 트리에서 CI 명령 그대로

web job:

- `npm ci`(W-1) / `lint`(W-2) / `typecheck`(W-3) / `gate:src-tree`(W-4) / `api:types:check`(W-5)
  → 0 / 0 / 0 / 0 / 0 · W-5 는 생성 타입이 계약과 바이트까지 같다
- `npm run test`(W-6) → 0 · 42 files, 920 tests passed
- `gate:tests`(W-7) / `build`(W-8) / `gate:boundary-canary`(W-9) → 0 / 0 / 0
- `npx playwright install chromium`(W-10) / `npm run test:e2e`(W-11) → 0 / 0 · 2 passed

check job:

- `./gradlew --no-daemon check` → **0** · BUILD SUCCESSFUL 41m 13s · 424 actionable
  (63 executed) · `FAILED` 0 건 · 상태 축 계약 test 가 그 실행에 있다(결과 XML: tests 6 ·
  skipped 0 · failures 0 · errors 0)

**중단된 선행 실행 하나를 기록한다** — 같은 명령이 11A 레인의 전건 빌드와 경쟁하던 중 adapters 의
재시도·timeout test 하나가 `expected:<2> but was:<1>` 로 떨어졌다(내 in_scope 밖 · 원천과 바이트가
같은 파일). 계약 갱신 때문에 그 실행을 끊었고, **경쟁이 없는 위 전건에서 재현되지 않았다**.

## 변이 — 바꿔치우기(적용을 먼저 단언한다)

커밋 뒤 작업 트리에 적용하고 끝나면 `git reset --hard && git clean -fdq`(미커밋 evidence 는 레인
scratch 로 피신시킨 뒤). 적용 단언은 앵커 유일성 + 치환 후 대조이고 **아홉 전부 `APPLIED`** 다.

- M1 도착 칸 한 값 바꿔치움 + 재생성 → 계약 test **1** · W-5 **0**(재생성했으니 바이트 등식은
  초록) · W-3 오류 **넷 / 세 파일** — 그 가운데 **둘이 표 키 등식**(caseDisplay.test), 둘은 fixture
- M2 출발 칸 어휘에서 `null` 원소 걷음 → 계약 test **1** · W-3 **0**(생성 타입이 그대로 `null` 을
  낸다 — web 이 못 본다)
- M3 출발 칸 `nullable` 선언 걷음 → 계약 test **1** · W-3 **0**(같은 이유의 거울상)
- M4 도착 칸 `enum` 을 걷어 `type: string` + 재생성 → 계약 test **1**(던진다) · W-3 **2**
- M5 건 상태 칸 한 값 바꿔치움 + 재생성 → 계약 test **1**(이 slice 가 더한 단언) · W-3 오류가
  **14 파일**에 걸친다(그 칸에서 유도되는 표·화면·test 전반) — Kotlin 층에서 잡는 것이 새것이다
- M6 타임라인이 자기 폴백(맨 색인 + 코드 원문)으로 되돌아감 → W-2 **0** · W-3 **0** ·
  W-6 **1**(렌더 잠금만 잡는다)
- M7 표 키의 원천 별칭을 손 유니온으로 바꿔치움(그것만) → lint **0** · typecheck **0**
  (**음성 대조** — 별칭은 등식의 경로가 아니다)
- M7b 거기에 계약 어휘를 늘려 재생성 → W-3 **1** · 양방향 등식이 drift 를 본다
- M8 목록 질의 param 한 값 바꿔치움 → 계약 test **1** · 6 tests, 1 failed
- M9 목록 질의 param 이름 바꿔치움 → 계약 test **1** ·「하나로 선언되지 않았다」
- M10 화살표 줄의 **두 조회를 함께** 옛 폴백으로 되돌림 → W-3 **0** · W-6 **1**(그 잠금만)
- M10a **출발 쪽만** 되돌림 → W-6 **1** · 둘째 줄의 단언에서 멈춘다
- M10b **도착 쪽만** 되돌림 → W-6 **1** · 첫째 줄의 단언에서 멈춘다. M10a·M10b 가 따로 붉으므로
  **두 줄이 각자 한 조회를 든다**(한 줄로 양쪽을 표 밖에 두면 한쪽만 되돌려도 통과할 수 있다)
- 통제군(변이 없는 같은 트리, `--rerun`) → 계약 test **0** · 그 로그에 `FROM-CACHE` **0 건**
  (통제군이 실제로 실행됐다) · web `typecheck` **0** · 표적 vitest **0**(76 passed)

## 낡는 좌표 — 역방향 훑기

- 편집한 파일 stem 여덟에 `<stem>[A-Za-z.-]*:[0-9]+` 훑기(`command grep`, node_modules·build·dist
  제외) → 인용이 있는 stem 은 **둘**이고 둘 다 **인용 줄이 내 첫 변경 줄보다 앞**이다
  (capability map 3457 < 3859 · 계약 문서 874 < 2698 — 양끝을 명령으로 냈다). 생성 타입을 가리키는
  인용은 0 이다

## rollback · 위생

- 복원 목록·①~⑥·원천 교체의 셋째 단언은 `rollback.md` 가 정본이다
- `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m11/timeline-state-enum/`
  → **1**(매치 없음) · 최종 HEAD 의 결과 정본은 verifier 와 PR 조치 코멘트다
- `git status --porcelain -- <in_scope 개별 인자>` → 산출물 경로는 **빈 출력**
- 크기 게이트 — **쓴 계량은 줄 수이고 두 수를 그대로 적는다**:
  `git diff --numstat <원천>..<실측 HEAD> -- . ':!reports/evidence'` 가 **+386 / −27 = 413 바뀐
  줄** · evidence 네 파일 `wc -l` 은 그 커밋에서 센다. 판정은 **바뀐 줄** 기준이다
