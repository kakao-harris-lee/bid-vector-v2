# M13 / ux-copy (13A) — slice 계약

```yaml
milestone: M13
slice: ux-copy
base_sha: bd5cd039eaf70f6423cf85e3bf1923bb367fa16e   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  # ── 문면을 바꾸는 자리(오늘 있는 문자열만) ──────────────────────────────────
  - web/src/routes.tsx                        # 자리 표 라벨의 머리 글자. 문서 제목은 13B 가 라벨에서 유도하므로 함께 흐른다
  - web/src/ScreenPlaceholder.tsx             # 빈 자리 본문의 마일스톤·slice 표기
  - web/src/RouteError.tsx                    # 서버·런타임 원문 노출 → 사용자 문면 + 다시 시도
  - web/src/display/resultDisplay.ts          # 영구 빈 자리 둘의 사유(capability ID 표기)
  - web/src/display/worksheetDisplay.ts       # 투찰가 3안 사유 문면 + ML 미가용 사유 한 행 — 13C 와 겹침(아래 §겹치는 자리)
  - web/src/display/myBidResultDisplay.ts     # 대사 사유 두 행 — **팀장 판정 3ⓐ 로 들어왔다**(아래 §계약 갱신 (1))
  - web/src/screens/OnboardingScreen.tsx      # 「준비 중」 행의 사유
  - web/src/screens/SettingsScreen.tsx        # 「준비 중」 행의 사유
  - web/src/screens/InboxScreen.tsx           # **「준비 중」 절만** — 13E 와 겹침(아래 §겹치는 자리)
  # ── 문면 축어를 쥔 짝 test·여정 locator ─────────────────────────────────────
  - web/src/display/resultDisplay.test.ts     # 바뀐 문면의 축어 단언
  - web/src/display/worksheetDisplay.test.ts  # 같음
  - web/src/screens/OnboardingScreen.test.tsx # 같음
  - web/src/screens/SettingsScreen.test.tsx   # 같음
  - web/src/screens/InboxScreen.test.tsx      # 같음
  - web/e2e/journey/                          # 라벨을 축어로 쥔 `getByRole('link', …)` 자리 — 겹침(팀장 보고 대상)
  # ── 문면 게이트(신설) ───────────────────────────────────────────────────────
  - web/scripts/                              # 금지 어휘 유도·완전성 단언과 그 양성·음성 대조(컴파일 경계상 src 밖)
  - web/src/screens/                          # 렌더한 DOM 층의 게이트 test 하나(자리와 이름은 설계 검토에서 확정)
  - web/config/gate-tests.txt                 # 새 게이트 test 를 **만드는 커밋에서** 등재
  - docs/discovery/capability-map.md          # §12.3 하위 절 하나 — 글자는 머지 차례에 정한다
  - reports/evidence/m13/ux-copy/
out_of_scope:
  - web/src/components/                       # 공유 컴포넌트의 모양·거동 — 13B 가 머지했고 13A 는 재사용만 한다
  - web/src/AppShell.tsx · LoginScreen.tsx     # 셸·로그인 — 13B
  - web/src/screens/Worksheet*.tsx            # 워크시트 화면의 문면·기능 — 13C
  - web/src/screens/BidBox*.tsx · Result*.tsx · ResultCard.tsx  # 입찰함·결과 — 13D
  - web/src/screens/TodayNoticesScreen.tsx · NoticeCard.tsx · StateBadge.tsx  # 홈·알림함·2층 라벨 — 13E
  - web/src/display/** 의 **새 값·새 표**        # 13A 는 오늘 있는 문자열의 문면만 바꾼다
  - 서버(Kotlin)·openapi/**                    # 백엔드 변경 0 (승인 경계)
  - package.json 의 dependencies               # 새 의존성 0 (승인 경계)
  - web/src/index.css                          # 토큰 한 자리 — 13A 는 색·크기를 바꾸지 않는다
  - .github/workflows/ci.yml                   # acceptance 는 오늘의 web job 명령 그대로
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # evidence 가 누출 패턴 게이트의 스캔 뿌리에 들어 경로가 닿는다 — evidence 편집 커밋의 HEAD 에서
  # Playwright 를 부르는 걸음(W-10·W-11·W-13)과 `capture:usecase` 는 병렬 넷의 포트 충돌을 피해
  # `flock ~/.local/bid-vector-web-e2e.lock ~/.local/bin/bv-build.sh …` 로 감싼다(팀장 2026-11-18).
  # 스크립트 자신의 종료 코드를 `bash <script>; echo "EXIT=$?"` 로 적는다 — 중간에 끊긴 걸음은 미실행이다.
rollback: rollback.md
```

## 받는 항목과 처분

계약 `milestone-13.md` 「받는 항목」의 1 행 하나가 이 slice 의 몫이다.

| # | 항목 | 이 slice 의 처분 |
| --- | --- | --- |
| 1 | 개발 용어가 화면에 보인다 | **부분 닫힘 + 잔여 등재** — 개발 코드는 두 층이 구조로 막고, 내부 용어는 **고친 자리만** 축어로 잠갔다 |

처분의 **근거와 잔여**는 `checklist.md` 가 정본이다. 착수 실측의 수치와 그것을 낸 조건은
`commands.md` 가 정본이고 여기 옮겨 적지 않는다.

## 착수 판정 (팀장 — 이 slice 의 범위 밖 판정과 게이트 술어 확정)

계약 `milestone-13.md` 「착수 판정」 절의 이 slice 몫이다. 팀장이 종결 때 같은 문면을 그 절에 올린다.

| 물음 | 판정 |
| --- | --- |
| 여정 spec 의 라벨 locator | **수정 허가** — locator 이름 문자열만. 합집합·새 spec 의 라벨은 머지 차례에 뒤 레인이 흡수한다 |
| 새 라벨 문면 | 「시작하기 · 오늘의 공고 · 공고 워크시트 · 내 입찰함 · 결과 확인」 **승인** |
| 소유가 갈리던 넷 | ⓐ 대사 사유 「법인 표기 토큰」 둘 **13A 가 닫는다** · ⓑ ML 미가용 사유 「스키마」 **13A 에 넣는다**(편집 줄을 그 표 행으로 한정) · ⓒ 워크시트 근거 절 「판정 어휘 version」 **13C 로 넘긴다**(범위 밖 아님) |
| 유지 다섯 | **승인**(제품 이름 · 「역산」 셋 · 「KST」). 사유는 `checklist.md` |
| 라우트 오류 「다시 시도」 | **승인** — 공유 `Button`, 기존 오류 얼굴과 같은 말. 거동 하나 증가를 scope 에 적고 vitest 로 재시도를 잠근다. 터치 타깃 미측정은 알려진 제한 |
| 범위 밖 셋 | **승인**(부트스트랩 던지기 · 도달 불가 전이 오류 · 표 밖 값의 코드 노출 — 기록된 결정) |
| 새 OPEN 후보 | ⓐ 상호작용 뒤 문면 · ⓑ `alt`·`placeholder` · ⓒ 어조 혼용 **셋 다 등재**. ⓓ 는 3ⓒ 판정으로 소멸 |
| 커밋 트레일러 | 세션의 attribution 리마인더를 따른다(작성 모델이 맞게 적히는 것이 기준). 만든 커밋은 고쳐 쓰지 않는다 |
| **금지 판정 = 꼴 / 등록부 = 포함 단언** | **승인**(설계 검토 판정). 계약 「등록부 ∪ 꼴」 문면과의 차이는 팀장이 종결 때 계약 「착수 판정」 절에 적는다 |
| **공고번호 제3안** | **승인** — 치환 0 · 값 술어 0 · `notice-list.json` 편집 0. fixture 어휘 단언은 스윕 앞, 비공허 짝 포함 |
| 번호 없는 ID 토막 | **등재가 아니라 닫는다**(2026-11-18) — 등록부 접두사에 묶은 꼴을 하나 더 두고, 그 접두사 집합과 §0.6 의 표를 양방향 등식으로 묶는다 |
| 13C 가 본 서버 값의 OPEN ID | **13C 가 적대적 입력 대조로 잠근다.** 13A 는 그 정정을 evidence 에 적고 OPEN 후보 ⓕ 를 등재한다. 서버 상수는 M13 에서 건드리지 않는다 |

## 겹치는 자리 — 남의 소유 파일을 여는 근거

계약 「소유와 공유 파일」 절이 정본이고, 이 slice 가 실제로 여는 자리는 다음 넷이다.

1. **`display/worksheetDisplay.ts`** — 13A 는 **투찰가 3안 자리의 사유 문면**만 바꾼다(계약 13A 행
   「`BID_PRICE_MENU_REASON_DISPLAY`」). 13C 가 같은 파일에 하한 여유·한글 병기를 더하므로 편집
   범위를 그 표 둘로 묶는다.
2. **`display/moneyDisplay.ts`** — 계약이 13A·13C 공유로 적었다. 착수 실측 결과 이 slice 가 바꿀
   문면이 없었고(금액 출처 표의 「개찰 역산값」은 유지 판정) **편집 0 으로 끝났다**(복원 목록에 없다).
3. **`screens/InboxScreen.tsx`** — 「준비 중」 절만. 나머지는 13E.
4. **`web/e2e/journey/**`** — 자리 표 라벨을 **축어로** 쥔 `getByRole('link', …)` 가 여정 spec 네
   파일에 있다. 라벨 문면을 바꾸면 그 자리가 함께 바뀌어야 여정이 선다. **팀장 보고 대상**이고,
   판정 전에는 손대지 않는다.

## base 확인

`git rev-parse bd5cd039` · `git merge-base --is-ancestor bd5cd039 HEAD` 를 착수 커밋에서 기계로
확인했다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 계약 갱신

**(1) in_scope 에 `web/src/display/myBidResultDisplay.ts` 가 든다** — 팀장 판정 3ⓐ(대사 사유의
「법인 표기 토큰」 둘을 13A 가 닫는다). 착수 때 scope 에 없던 파일이고, **복원 목록을 기계로
산출해 in_scope 와 대조하면서** 드러났다.

**(2) 새 게이트 파일이 둘 선다** — 소스 층(`web/scripts/`)과 렌더 층(`web/src/screens/`). 만드는
커밋에서 `web/config/gate-tests.txt` 에 함께 등재했다.

**(3) 금지 꼴의 정본이 데이터 파일이 아니라 렌더 층의 선언이다.** 처음에는 `web/config/` 의 데이터
파일로 두었는데 **typecheck 가 막았다** — `src` 의 TS 프로그램에 node 타입이 없어 그 층이 파일을
읽을 수 없다. 그 경계를 넓히는 것은 새 결정이라, 사본을 두는 대신 **한 선언을 두 층이 나눠
쓴다**(소스 층이 AST 로 읽는다). 그 데이터 파일은 같은 range 안에서 생겼다 사라졌으므로 복원
목록에 없다.

**(4) 거동이 하나 는다** — 라우트 오류 자리의 「다시 시도」(팀장 판정 5). 문면 slice 치고는 거동
0 이 아니며, 그 자리가 터치 타깃 게이트의 화면 표에 없어 44px 이 **측정되지 않는다**(알려진 제한).

**(5) 여정 spec 의 locator 를 `exact: true` 로 바꿨다** — 라벨이 짧아지며 생긴 모호성이고, 팀장이
수락했다. 바꾼 것은 locator 의 이름 맞춤 방식이며 이동 대상·동작은 그대로다.

## 갱신 이력

- 2026-11-18 초판(착수 보고와 함께).
- 2026-11-18 팀장 착수 판정·설계 검토 판정을 §착수 판정에 옮겨 적고 §받는 항목의 처분을 고정 ·
  §계약 갱신 (1)~(5).
- 2026-11-19 번호 없는 ID 토막의 처분과 13C 건의 정정을 §착수 판정에 등재.
