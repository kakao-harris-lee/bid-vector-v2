# M13 / ux-home-inbox (13E) — slice 계약

```yaml
milestone: M13
slice: ux-home-inbox
base_sha: bd5cd039eaf70f6423cf85e3bf1923bb367fa16e   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/src/screens/TodayNoticesScreen.tsx      # 기본 목록 = 제안 · 검색이 그것을 대신함 · 조건 칩
  - web/src/screens/TodayNoticesScreen.test.tsx # 그 축의 짝 test(이미 gate-tests 등재)
  - web/src/screens/NoticeCard.tsx              # 카드 둘 — 2층 라벨 펼침이 닿는 자리
  - web/src/screens/InboxScreen.tsx             # 링크로 읽음 · 「더 보기」 · 빈 화면 다음 걸음 (「준비 중」 절 제외)
  - web/src/screens/InboxScreen.test.tsx        # 그 축의 짝 test(이미 gate-tests 등재)
  - web/src/components/StateBadge.tsx           # 2층 라벨의 둘째 층을 터치에서 펼침
  - web/src/components/StateBadge.test.tsx      # 신설 — 만드는 커밋에서 gate-tests.txt 등재
  - web/e2e/journey/                            # 13E 여정 spec 신설 + 알림함·홈 술어 갱신(아래 「공유 파일」)
  - web/config/journey-specs.txt                # 그 spec 을 **같은 커밋에서** 등재(양방향 등식)
  - web/config/gate-tests.txt                   # 새 vitest 게이트 test 파일 등재
  - docs/discovery/capability-map.md            # §12.3 하위 절 하나 — 글자는 머지 차례에 정한다
  - reports/evidence/m13/ux-home-inbox/
out_of_scope:
  - web/src/screens/InboxScreen.tsx 의 「준비 중」 절   # 13A(문면). 13E 는 그 절의 글자를 바꾸지 않는다
  - web/src/display/**                          # 표시 문면·사유 문자열은 13A·13C 의 축(아래 단서 하나)
  - web/src/display/timeDisplay.ts              # 벽시계·KST 셈 — 추천 칩 설계는 편집 0 으로 선다
  - web/src/AppShell.tsx                        # 13B 산출물. 배지의 읽기 자리는 **확인**만 하고 고치지 않는다
  - web/src/api/approvedOperations.ts           # 승인 쌍 증분 0 (쓰는 operation 넷이 이미 목록에 있다)
  - web/src/api/queries.ts · queryKeys.ts       # 13D 의 증분 자리. 13E 는 기존 함수·키를 그대로 쓴다
  - web/src/screens/Worksheet* · BidBox* · Result*  # 13C·13D 소유(StateBadge 를 통한 간접 영향은 아래 「열린 물음」)
  - 서버(Kotlin)·openapi/**                     # 백엔드 변경 0 (승인 경계)
  - package.json 의 dependencies                # 새 의존성 0 (승인 경계)
  - .github/workflows/ci.yml                    # acceptance 는 오늘의 web job 명령 그대로
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # evidence 가 누출 패턴 게이트의 스캔 뿌리에 들어 경로가 닿는다 — evidence 편집 커밋의 HEAD 에서
rollback: rollback.md
```

**`display/**` 의 단서 하나** — 13A 가 갖는 것은 **오늘 있는 문자열**이다. 이 slice 가 새로 만드는
값(조건 칩의 축 이름 · 제안 상한 초과 문면 · 빈 화면의 다음 걸음 문면)의 문면은 계약 13A 행의
「새 값·새 표의 문면은 그 값을 쓰는 slice 가 자기 자리에서 쓴다」에 따라 13E 가 쓴다. 그 표를
`display/` 에 둘지 자기 화면 파일에 둘지는 「열린 물음 ⓔ」다.

## 받는 항목과 처분(제안)

| # | 항목 | 이 slice 의 처분(제안) |
| --- | --- | --- |
| 8 | 홈의 첫 화면이 검색 결과 차례다 | **닫힘** — 기본 목록은 제안(서버 순서 그대로), 검색하면 그 결과가 자리를 대신한다. 조건은 칩 |
| 9 | 알림함이 받은편지함에 못 미친다 | **부분 닫힘** — 링크로 읽음 · 「더 보기」는 닫는다. 탐색 배지는 13B 가 이미 세웠고 13E 는 그 자리가 **줄거나 그대로인 것**을 잠근다 |
| 11 | 점진적 공개가 거의 없다(2층 라벨) | **부분 닫힘** — 2층 라벨의 둘째 층이 터치에서 선다. 워크시트 절 접기는 13C 몫이다 |
| 7 | 빈 화면이 다음 걸음을 주지 않는다(홈·알림함) | **부분 닫힘** — 홈 둘(검색 결과·제안)과 알림함 하나. 워크시트·입찰함·결과는 13C·13D 몫 |

처분의 **근거와 잔여**는 `checklist.md` 가 정본이고, 착수 실측의 수치와 그것을 낸 조건은
`commands.md` 가 정본이다 — 여기 옮겨 적지 않는다.

## 착수 재측정 (계약 13E 행을 코드로 다시 잰 것)

정본은 `commands.md` 다. 판정이 걸린 다섯만 적는다.

1. **§6.2 B 「마감 임박 × 자격 통과」를 서버가 이미 주는가 — 준다.** `GET /api/suggestions` 의 응답이
   「마감 임박 순」이고 통과 집합이 곧 자격 축이다. 그러나 **질의 인자가 하나도 없다**(`query?: never`) —
   정렬·필터·쪽이 서버 표면에 없고, 표시 상한은 전략의 `candidateLimit` 이라 **상한 초과분에 가는 길이
   없다**. 「새 파라미터가 필요하면 승인 경계」의 자리가 바로 여기다(열린 물음 ⓐ).
2. **칩에 쓸 기존 파라미터가 실재하는가 — 셋 다 있다.** `agency`(기관) · `amountMin`/`amountMax`(금액대) ·
   `deadlineFrom`/`deadlineTo`(마감일, `date-time`). 앞 둘은 오늘 접힌 `<details>` 안의 입력칸이고
   **마감일 둘은 오늘 화면이 쓰지 않는다**(새 파라미터가 아니라 미사용 파라미터다).
3. **알림 「더 보기」 꼴 — 선다.** `page`(0 기반) · `size`(1~100, 기본 20) · `unreadOnly`(기본 false)가
   있고 `X-Total-Count`(필터 지난 전수)가 쪽 끝을 정한다. 홈의 `useInfiniteQuery` 와 **같은 꼴**이다.
   `unreadOnly` 는 계약 13E 행이 요구하지 않아 쓰지 않는다(열린 물음 ⓓ).
4. **셸 배지의 읽기 자리 — 바뀌지 않는다.** 배지는 `['notifications','unread']` 키로 `size=1` 을 보내고
   수는 `X-Unread-Count` 에서 읽는다. 알림함 목록 키는 그 **접두**(`['notifications']`)라 읽음 쓰기의
   `invalidateQueries` 가 배지까지 번진다 — 「성공이면 줄고 실패면 그대로」가 배선이 아니라 구조로 선다.
   13E 가 목록을 쪽 질의로 바꿔도 이 관계는 그대로다. **다만 여정 술어가 깨진다**(아래 「공유 파일」 ①).
5. **2층 라벨의 블라스트 반경 — 13 파일 45 자리(내 소유는 여섯).** `StateBadge` 를 펼침 가능하게 만들면 13E 소유 밖
   화면이 함께 변한다(`WorksheetGrounds` 13 · `ResultCard` 11 · `BidBoxCard` 3 · 그 밖 여덟 파일).
   **중첩 상호작용은 0** 이다(어느 자리도 `<a>`·`<button>` 안에 있지 않다 — 전수 확인). 열린 물음 ⓑ.

## 공유 파일 — 이 slice 가 고쳐야 하는 남의 자리

① **`web/e2e/journey/results.spec.ts` 의 알림함 술어**(13B 알려진 제한 10 이 13E 몫으로 넘긴 자리).
오늘 그 spec 은 화면의 읽기를 **「`size` 인자 부재」**로 배지의 읽기와 가른다. 알림함이 쪽 질의를
보내면 그 술어는 아무 응답도 집지 못하고 붉는다. 갈음 제안: **「`page` 인자 존재」**로 뒤집는다(배지는
쪽을 보내지 않는다 — 그 음성 쪽도 같은 자리에서 단언한다).

② **`web/e2e/journey/journey.spec.ts` 여정 ②**. 검색 **뒤에** `suggestions-empty` 가 보이는 것을
단언한다. 「검색 결과가 제안 목록을 대신한다」가 서면 그 단언은 거짓이 된다. 갈음 제안: 검색 **전**에
제안 얼굴을, 검색 **뒤**에 그 자리가 결과로 **대체된 것**을 단언한다(항목 8 의 새 축이 바로 그것이다).

③ `web/e2e/journey/settings.spec.ts` 의 제안 단언 셋은 검색을 지나지 않아 영향 없음으로 보인다 —
RED 에서 실측해 확인한다.

## 열린 물음 (팀장 판정 요청 — 「착수 판정」 절에 적어 확정해 주십시오)

- **ⓐ 제안 목록의 상한 초과분.** 서버에 쪽·정렬 파라미터가 없다. 제안은 **「더 보기」 없이** 서버가
  준 것만 그리고, 통과 전수 > 받은 수일 때 그 사실을 한 줄로 보이고 **설정(감시 조건)으로 가는 다음
  걸음**을 둔다 — 이것을 **범위 밖 유지 + 새 OPEN 등재**로 처분하는가(추천), 아니면 서버 파라미터
  추가를 승인 경계로 올리는가.
- **ⓑ 2층 라벨 펼침의 범위.** 추천은 **`StateBadge` 한 자리에서 전역**이다(`why` 가 있는 badge 만
  펼침 가능 · 그 badge 는 `min-h-11` 버튼이 되어 터치 타깃 게이트 모집단에 든다 · 펼친 문면은
  `aria-describedby` 가 가리키는 **같은 한 노드**라 두 번 낭독되지 않는다 · 그 대신 `title` 은
  걷는다 — 손가락·마우스 둘 다 같은 길을 쓴다). 비용: 13C·13D 소유 화면의 badge 가 44px 로
  **보기에 커진다**(워크시트 13 · 결과 카드 11). 대안은 호출자 opt-in prop 으로 13E 소유 화면에만
  세우고 항목 11 의 나머지를 잔여로 등재하는 것이다.
- **ⓒ 조건 칩의 모양.** 추천은 **칩 셋 각각이 자기 입력칸을 품은 disclosure**(기관 · 금액대 둘 ·
  마감일 둘)이고 값이 서면 칩에 그 값과 지우기가 보인다. 금액대·마감일을 **구간 프리셋**(「1억 미만」·
  「3일 내」)으로 두면 ⓐ 그 구간 값이 정책 상수가 되고(매직 넘버 금지 → versioned policy 자리 필요)
  ⓑ 마감일은 KST 날 경계 셈이 필요해 `display/timeDisplay.ts`(소유 목록 밖)를 열어야 한다. 입력칸
  칩은 기존 `localInputToIso` 로 서고 그 둘을 **둘 다 피한다**.
- **ⓓ 알림함 `unreadOnly`.** 서버에 있으나 계약 13E 행이 요구하지 않는다 — **쓰지 않는다**(추천)로
  확정해 주십시오. 「안읽음만」 칩은 받는 항목 9 의 세 축 밖이다.
- **ⓔ 새 표의 자리.** 조건 칩 축 이름 · 제안 상한 문면 · 빈 화면 다음 걸음 문면을 `display/` 의 새
  파일에 둘지(13A 소유 디렉터리이나 **새 파일**), 소유 화면 파일 안에 둘지. 추천은 **소유 화면 파일
  안**이다 — 13A 와 파일이 겹치지 않고, 문면 게이트의 모집단은 렌더된 DOM 이라 자리를 가리지 않는다.
- **ⓕ 읽음-후-이동의 구현 자리.** 링크를 누르면 쓰기를 **걸고 바로 이동**하므로 화면이 언마운트된
  뒤에 응답이 온다. `useMutation` 의 콜백은 그 실행에서 서는 것이 보장되지 않아, 추천은 **app 수준
  `queryClient` 를 쥔 평범한 호출 + 무효화**다(실패는 계약대로 **배지가 줄지 않는 것**으로만 보인다).
  기존 「읽음으로」 버튼은 그대로 둔다(머물러 있을 때는 실패 얼굴이 설 자리가 있다).

## 승인 경계 (계약 「승인 경계」 절의 이 slice 몫)

- 서버·OpenAPI 변경 **0**. 정렬·필터 파라미터가 필요해지면 멈추고 보고한다(ⓐ 가 그 자리다).
- 승인 쌍 증분 **0** — 쓰는 넷(`GET /api/notices` · `GET /api/suggestions` · `GET /api/notifications` ·
  `POST /api/notifications/read`)이 이미 목록에 있다. 늘어나면 멈추고 보고한다.
- 새 의존성 **0**. 흐름의 순서(§6.1 상태 기계)는 바꾸지 않는다 — 바뀌는 것은 목록의 **차례**다.
- **터치 타깃 게이트가 내 소유 밖 화면에서 붉으면** 멈추고 보고한다. 홈의 하한(8)은 **낮추지 않는다** —
  기본 목록이 제안으로 바뀌면 `/` 의 타깃 수가 줄 수 있고, 그 자리는 칩·빈 화면 다음 걸음으로 메운다.
- 다른 slice 소유 파일의 **문면**을 바꾸지 않는다.

## base 확인

`git rev-parse bd5cd039` · `git merge-base --is-ancestor bd5cd039 HEAD` 를 착수 커밋에서 기계로
확인했다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 갱신 이력

- 2026-11-18 초판(착수 보고와 함께).
