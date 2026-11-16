# M13 / ux-foundation (13B) — slice 계약

```yaml
milestone: M13
slice: ux-foundation
base_sha: a338b55bd10625b68cfe867d6cab688b2adc3270   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/src/components/                 # 공유 Button·Card 신설 + 기존 넷의 버튼·링크 이관(WriteFailure 의 state.from 포함 — 착수 판정 ⓑ)
  - web/src/AppShell.tsx                # 하단 탭 · 상단 줄 · 안 읽음 배지 · 로그아웃 · 문서 제목 · 포커스
  - web/src/LoginScreen.tsx             # 색 토큰화 · 로그인 뒤 원래 자리 복귀
  - web/src/RouteError.tsx              # 색 토큰화만(문면은 13A)
  - web/src/ScreenPlaceholder.tsx       # 색 토큰화만(문면은 13A)
  - web/src/routes.tsx                  # 탐색 분류 필드(문자열 0 — 라벨 문면은 13A)
  - web/src/screens/                    # 버튼·카드 마크업 이관(모양만) + 그 선택자를 보는 test
  - web/src/api/approvedOperations.ts   # 승인 쌍 +1 (DELETE /api/session)
  - web/src/api/queries.ts              # signOut() — 쌍과 호출이 같은 커밋
  - web/src/api/queryKeys.ts            # 셸 배지의 질의 키(X-Unread-Count)
  - web/src/index.css                   # md 중단점이 토큰을 요구할 때만
  - web/e2e/journey/                    # 터치 타깃 게이트 spec 신설(+ 필요한 공통 helper)
  - web/scripts/                        # 화면 표 등식 · 비-상호작용 태그 핸들러 금지 게이트 test(컴파일 경계상 src 밖)
  - web/config/journey-specs.txt        # 그 spec 을 **같은 커밋에서** 등재
  - web/config/gate-tests.txt           # 새 vitest 게이트 test 파일 등재
  - docs/discovery/capability-map.md    # §12.3 하위 절 하나 — 글자는 머지 차례에 정한다
  - reports/evidence/m13/ux-foundation/
out_of_scope:
  - web/src/display/**                  # 표시 문면·값 — 13A·13C 의 축
  - web/src/components/StateBadge.tsx 의 2층 라벨 거동   # 13E(터치 펼침). 13B 는 이 파일의 버튼·카드 마크업만
  - web/src/screens/Worksheet*.tsx 의 새 기능          # 13C(복사·한글 병기·확인·하한 여유·접기)
  - web/src/screens/BidBoxCard.tsx 의 새 전이          # 13D(/defer·/skip·실행 취소)
  - web/src/screens/TodayNoticesScreen.tsx 의 목록 순서·칩  # 13E
  - 서버(Kotlin)·openapi/**             # 백엔드 변경 0 (승인 경계)
  - package.json 의 dependencies        # 새 UI 의존성 0 (승인 경계)
  - adapters/** · app/** · ml-engine/** # 이 slice 는 web 만 만진다
  - .github/workflows/ci.yml            # acceptance 는 오늘의 web job 명령 그대로
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # evidence 가 누출 패턴 게이트의 스캔 뿌리에 들어 경로가 닿는다 — evidence 편집 커밋의 HEAD 에서
rollback: rollback.md
```

## 받는 항목과 처분

| # | 항목 | 이 slice 의 처분 |
| --- | --- | --- |
| 5 | 모바일 사용감이 약하다 | **닫힘** — 하단 탭(`md` 미만)·상단 줄(이상)·터치 타깃 ≥ 44px 전수·가로 넘침 0 을 터치 타깃 게이트가 잠근다 |
| 6 | 공유 버튼·카드가 없다 | **닫힘** — 공유 `Button`·`Card` 를 세우고 모든 화면의 버튼·카드를 옮긴다(모양만, 동작 0) |
| 10 | 셸과 세션 | **닫힘** — 로그아웃·복귀·문서 제목·머리 포커스·색 토큰화 다섯 축 전부. 복귀 대상은 **앱 내부 경로만**(착수 판정 ⓑ) |

처분의 **근거와 잔여**는 `checklist.md` 가 정본이다. 착수 실측의 수치와 그것을 낸 조건은
`commands.md` 가 정본이고 여기 옮겨 적지 않는다.

## 착수 판정 (팀장 2026-11-16 — 이 slice 의 범위 밖 판정과 게이트 술어 확정)

계약 `milestone-13.md` 「착수 판정」 절의 이 slice 몫이다. 팀장이 종결 때 같은 문면을 그 절에 올린다.

| 물음 | 판정 |
| --- | --- |
| ⓐ 하단 탭의 칸 수 | **다섯 칸 = A·B·D·E + 「더 보기」**(알림함·설정). 계약의 「다섯」은 탭 칸 수로 읽는다. C 워크시트는 경로 인자가 있어 전역 탐색 밖이다. 「더 보기」의 **현재 자리 표시와 키보드 도달**은 설계 검토가 든다 |
| ⓑ 「로그인 뒤 원래 자리」의 원천 | `components/WriteFailure.tsx` 의 링크 둘이 `state.from` 을 싣는다 → in_scope. **조건: 복귀 대상은 앱 내부 경로(pathname+search)만** — 외부 URL · `//` 시작 · `/login` 자신은 `/` 로 떨어뜨리고 그 셋을 test 로 잠근다 |
| ⓒ `Toast.tsx` 의 버튼 둘 | 13B 가 공유 `Button` 으로 옮긴다. 계약 13D 행의 「편집 0」은 13D 기준이다 |
| ⓓ `routes.tsx` 편집 | **분류 필드 하나만, 문자열 0.** 문서 제목은 `slot.label` 에서 유도한다(13A 의 라벨 변경이 자동으로 흐른다) |
| ⓔ 셸 배지의 질의 | 키 `['notifications','unread']`(기존 `NOTIFICATIONS_QUERY_KEY` 접두 아래) + 접두 test · `/login` 에선 걸지 않는다. **조건: `size` 가 계약에 있는지 확인** — 확인함(`listNotifications` 의 `size`, 1~100, 기본 20) |
| ⓕ 터치 타깃 게이트의 자리 | `e2e/journey/touch-target.spec.ts`(390×844) + **같은 커밋**에서 `config/journey-specs.txt` 등재 |
| ⓖ 로그아웃의 여정 영향 | 쓰기 경로는 vitest(목 + 승인 쌍 등식), 여정은 **버튼 존재와 터치 타깃만** |
| ⓗ `index.css` | 편집 **0** — Tailwind v4 가 `md` 를 기본으로 낸다 |
| ⓘ sr-only 를 모집단에서 빼는 술어 | **computed style(`clip`·`clip-path`)로 판정**한다. 클래스 이름 술어 금지. 음성 대조: 보이는 버튼에 은폐를 걸면 **화면별 계수 하한**이 붉는다. 더해 **건너뛰기 링크는 포커스됐을 때 ≥ 44px** 인지 한 단언을 따로 둔다 |
| ⓙ 카드 안 본문 링크 | `a` **전수 유지**. 블록 타깃화는 13B 몫이다(13B 가 혼자 먼저 머지되므로 13C·13D·13E 소유 침범이 아니다). **조건: 이동 대상·동작 0 변경을 test 로 잠그고, 문장 속 인라인 링크가 나오면 블록화하지 말고 보고한다** |
| 터치 타깃 하한 N | A 10 · B 8 · C 6 · D 10 · E 7 · 알림함 12 · 설정 18 · 로그인 1 · 없는 주소 5. 단서 둘 승인 — **게이트 spec 은 자기 fixture 로 알림함 N 을 고정**하고, **로그인 화면에는 하단 탭을 그리지 않는다** |
| 비-상호작용 태그 핸들러 금지(설계 검토 판정) | **승인.** `src/**` JSX 에서 포인터·키 핸들러를 든 비-상호작용 내장 태그가 0 임을 AST 로 센다(착수 실측 0 — 오늘 어느 production 도 붉히지 않는다). 조건 셋: ① **`role="button"` 을 단 div 도 금지**한다(공유 `Button` 을 쓰게) — 허용 목록을 둘 거면 지금 0 이어야 한다 ② **경계**(사용자 정의 컴포넌트에 넘긴 `onClick` 은 이 축이 보지 않는다)를 알려진 제한에 적는다 ③ 양성 대조(심은 `<div onClick>` 하나가 붉음) · 음성 대조(`<button onClick>`·`<summary>` 는 안 붉음). **이 축은 13C·13D·13E 에 구속력을 갖는다** |

## 승인 경계 (계약 「승인 경계」 절의 이 slice 몫)

- **승인 쌍 +1**(`DELETE /api/session`)은 계약이 미리 승인했다(13B 행 「로그아웃(`DELETE /api/session`
  승인 쌍 추가)」). 그 밖의 쌍 증가는 멈추고 보고한다.
- **터치 타깃 게이트가 13B 이관 뒤에도 남는 작은 버튼을 찾으면** 멈추고 보고한다(계약 승인 경계).
  자기 소유 자리를 고쳐 초록으로 만드는 것은 이 slice 의 일이다.
- 서버·OpenAPI 변경, 새 의존성, 흐름 순서 변경은 멈추고 보고한다.
- 다른 slice 소유 파일의 **문면**을 바꾸지 않는다 — 13B 가 그 파일을 열더라도 바꾸는 것은
  마크업·색·구조뿐이다.

## base 확인

`git rev-parse a338b55b` · `git merge-base --is-ancestor a338b55b HEAD` 를 착수 커밋에서 기계로
확인했다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 계약 갱신

**(1) in_scope 에 `web/src/components/WriteFailure.tsx` 가 든다**(착수 판정 ⓑ). `/login` 으로 가는
길이 그 파일의 링크 둘뿐이라 「원래 자리」를 아는 자리가 거기 말고 없다. 그 파일은 버튼·링크 이관
대상이기도 하다.

**(2) 새 게이트 test 파일이 셋 선다**(계획) — 공유 컴포넌트의 짝 test 둘과 화면 표 등식 하나.
세 파일 모두 **만드는 커밋에서** `web/config/gate-tests.txt` 에 등재한다. 화면 표 등식이
`web/scripts/` 에 서는 이유는 컴파일 경계다: `web/src` 안의 test 가 `e2e/**` 를 import 하면 W-4 의
「프로그램 전수가 `web/src` ∪ `web/node_modules` 안」 단언이 붉는다(그 게이트의 술어를 바꾸는 것은
새 결정이다).

**(3) 여정 spec 하나가 는다** — `web/e2e/journey/touch-target.spec.ts`. 같은 커밋에서
`web/config/journey-specs.txt` 에 적는다(양방향 등식).

## 갱신 이력

- 2026-11-16 초판(착수 보고와 함께).
- 2026-11-16 팀장 착수 판정 열 건을 §착수 판정에 옮겨 적고 §받는 항목의 처분을 고정 ·
  §계약 갱신 (1)~(3).
- 2026-11-16 설계 검토 판정(비-상호작용 태그 핸들러 금지 AST 축, 조건 셋)을 §착수 판정에 등재.
