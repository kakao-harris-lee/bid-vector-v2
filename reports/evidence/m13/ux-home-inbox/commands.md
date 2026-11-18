# M13 / ux-home-inbox (13E) — 실행한 명령과 실측

수치의 정본은 이 파일이다. 결과는 **핵심 한 줄**로 적고 출력 전문을 싣지 않는다. 수를 낸
**조건**(대상 집합·부하)은 같은 줄에 적는다.

## Phase 1 — 착수 (base `bd5cd039`, 2026-11-18)

| # | 무엇을 잰 것 | 명령(요지) | 결과 |
| --- | --- | --- | --- |
| K-1 | base 가 조상인가 | `git rev-parse bd5cd039` · `git merge-base --is-ancestor bd5cd039 HEAD` | exit 0 — 40자 `bd5cd039eaf70f6423cf85e3bf1923bb367fa16e` |
| K-2 | 하네스 레인 변경 | `git log --oneline bd5cd039..HEAD --no-merges -- CLAUDE.md .claude/` | 빈 출력(착수 때 없음) |
| K-3 | 제안 목록의 서버 표면 | 생성 타입 `listSuggestions` 의 `parameters` | `query?: never` — 쪽·정렬·필터 인자 **0**. 응답은 「마감 임박 순」, `X-Total-Count` 는 **표시 상한 적용 전** 통과 전수 |
| K-4 | 제안의 표시 상한이 어디서 오나 | `ListSuggestionsUseCase` KDoc(STR-06) | 전략의 `candidateLimit` — 계산 예산과 독립. 상한 초과분에 가는 질의 표면이 없다 |
| K-5 | 칩에 쓸 공고 검색 파라미터 | `searchNotices` 의 `query` 전수 | `q` · `agency` · `amountMin` · `amountMax` · `deadlineFrom` · `deadlineTo`(둘 다 `date-time`) · `page` · `size`(기본 20, 상한 100). **마감 둘은 오늘 화면이 쓰지 않는다**(미사용, 신설 아님) |
| K-6 | 알림함의 쪽 표면 | `listNotifications` 의 `query` 전수 | `page`(0 기반) · `size`(1~100, 기본 20) · `unreadOnly`(기본 false). 쪽 끝은 `X-Total-Count` 로 정해진다 — 홈의 무한 질의와 **같은 꼴** |
| K-7 | 셸 배지가 읽는 자리 | `AppShell.tsx` 의 질의 · `queryKeys.ts` | 키 `['notifications','unread']`, `listNotifications({ size: 1 })`, 수는 `X-Unread-Count`. 목록 키 `['notifications']` 의 **접두 아래**라 읽음 쓰기의 무효화가 배지까지 번진다 |
| K-8 | 여정이 화면의 읽기를 배지와 가르는 술어 | `e2e/journey/results.spec.ts` 의 `waitForResponse` 술어 | **`searchParams.get('size') === null`** — 알림함이 쪽 인자를 보내면 이 술어가 아무 응답도 집지 못한다(13B 알려진 제한 10 이 넘긴 자리) |
| K-9 | 여정 ② 가 검색 뒤에 제안을 단언하나 | `e2e/journey/journey.spec.ts` 여정 ② | 검색 **뒤**에 `suggestions-empty` 가 보이는 것을 단언한다 — 「결과가 제안을 대신한다」가 서면 거짓이 된다 |
| K-10 | 2층 라벨의 블라스트 반경 | `grep -ro '<StateBadge' src --include=*.tsx` (출하 트리 전수) | **45 자리 · 13 파일**. 내 소유(`NoticeCard`·`InboxScreen`)는 **여섯**, 나머지 39 는 13A·13C·13D 소유 화면 |
| K-11 | badge 가 상호작용 요소 안에 중첩됐나 | 그 13 파일에서 `<Link`·`<Button`·`<StateBadge` 의 자리 대조 | **0** — 어느 badge 도 `<a>`·`<button>` 안에 없다. 펼침 버튼이 되어도 중첩 상호작용이 생기지 않는다 |
| K-12 | `why` 가 있는 표시 항목의 비중 | `display/*.ts` 의 `why:` 대 `label:` 계수 | 표마다 다르다 — 워크시트 16/59 · 내 결과 24/28 · 알림 7/16 · 공고 4/15. **펼침은 `why` 가 있는 badge 만** 받으므로 화면마다 수가 갈린다(런타임 값에 달려 RED 에서 화면별로 실측한다) |
| K-13 | 터치 타깃 게이트의 모집단에 `summary` 가 드나 | `e2e/journey/touch-target.spec.ts` 의 선택자 | **든다**(`summary`·`select`·`input[type=checkbox|radio]` 까지). `<details>` 로 펼침을 세워도 44px 의무를 피하지 못한다 |
| K-14 | 홈·알림함의 화면별 하한 | 같은 spec 의 화면 표 | 홈 8 · 알림함 12. 하한은 **낮추지 않는다** — 홈의 기본 목록이 제안으로 바뀌면 `/` 의 타깃 수가 줄 수 있어 칩·빈 화면 다음 걸음으로 메운다(RED 에서 실측) |
| K-15 | 여정 fixture 의 홈이 제안을 보이나 | `journey.spec.ts` 97~99 · `settings.spec.ts` 182·198·217 | 주 여정의 홈은 `WATCH_NOT_CONFIGURED` 빈 얼굴이고, 설정 여정이 조건을 세운 뒤에만 제안 카드가 선다 |
| K-16 | 쓰는 operation 이 승인 목록에 있나 | `approvedOperations.ts` 대조 | 넷 전부 있다(`GET /api/notices` · `GET /api/suggestions` · `GET /api/notifications` · `POST /api/notifications/read`) — **승인 쌍 증분 0** |
| K-17 | 새 vitest 파일이 수집되나 | `vite.config.ts` 의 `test.include` | `src/**/*.test.tsx` 가 받으므로 `components/StateBadge.test.tsx` 는 **설정 편집 없이** 수집된다(등록부 `config/gate-tests.txt` 한 줄은 만드는 커밋에서 더한다) |

## Phase 2 — RED 선행

| # | 잰 것 | 명령(요지) | 결과 |
| --- | --- | --- | --- |
| R-1 | 새 축이 **먼저 붉은가** | 구현 전 트리에서 `npx vitest run` 세 파일 | **28 failed · 67 passed** — 붉은 28 이 전부 이 slice 가 더한 축이다(실패 이름 전수를 대조했다) |
| R-2 | 붉음이 의도한 자리인가 | 같은 실행의 실패 이름 전수 | 2층 라벨 6 · 홈(기본·대체·칩·상한·걸음) 14 · 알림함(링크·쪽·걸음) 8 |

## Phase 3 — 구현 뒤 전건

| # | 잰 것 | 명령 | 결과 |
| --- | --- | --- | --- |
| G-1 | 영향 범위 vitest | `npx vitest run` 세 파일 | 세 파일 **전부 초록**(2층 라벨 9 · 홈 45 · 알림함 41) |
| G-2 | web 전건 vitest | `npm run test` | **52 파일 · 1210 test** 통과. base 는 51 · 1169 — 이 slice 가 파일 1 · test 41 을 더했다 |
| G-3 | 같은 명령의 안정성 | `npm run test` **연속 3회** | 세 번 다 `52/1210` 초록. 레이블 축을 고치기 전 한 번 `1 failed` 가 났고, 고친 뒤 같은 수로 수렴하는 것을 따로 쟀다 |
| G-4 | lint · typecheck | `npm run lint` · `npm run typecheck` | 둘 다 **exit 0** |
| G-5 | 레이블 축 게이트가 내 새 자리를 본다 | `npx vitest run scripts/touch-target-labels.test.mjs` | 칩 칸의 레이블이 **종류를 증명하지 못하는 입력**(`type` 이 표에서 온다)을 감싸 같은 의무를 진다 — 채우면 초록, 빼면 붉다(M-6) |
| G-6 | 여정 전건 | `flock … bv-build.sh npm run test:e2e:journey` | **20 passed · flaky 0 · 건너뜀 0 · spec 7/7**(등록부와 같다) |
| G-7 | acceptance 전건(CI `web` job W-1~W-13) | `bash <web-acceptance.sh>; echo EXIT=$?` — `tools/one-command-check.sh` 의 web 절을 **그대로 잘라** 온 스크립트(축약·새 플래그 0) | **EXIT=0.** vitest 52/1210 · 게이트 test 등록부 기준 통과 · 스모크 4 · 여정 20 · spec 7/7 |

**G-6 의 첫 실행은 붉었다(3 failed).** 그 셋이 가르쳐 준 것을 커밋 `5aaa5dfd` 가 담는다 — 배지의
읽기를 놓치는 장부 자리 · 부분 일치 이름으로 둘을 집는 셸 링크 조회 · 마감이 지난 공고로는 「투찰가
요청」이 서지 않는다는 fixture 사실. 셋 다 **술어·spec 의 결함**이고 출하 코드는 그대로였다.

## Phase 3b — 바꿔치우기 변이 (적용 단언 → 표적 실행 → 되돌림)

변이는 **바꿔 끼운다**(더하기만 하는 변이는 초록이 쉽다). 적용 전 트리는 커밋되어 깨끗했고, 각
변이는 원문이 **정확히 하나**일 때만 들어갔으며(`APPLY=OK`) 끝나면 `git checkout --` 로 되돌렸다.

| # | 바꿔 끼운 것 | 표적 | 결과 |
| --- | --- | --- | --- |
| M-1 | 2층 라벨을 앞 판(글자 + 툴팁 + 시각 은폐 문면)으로 | 짝 test | **6 RED** |
| M-2 | 펼친 자리에 문면을 **사본**으로 또 그린다 | 짝 test | **1 RED** — 「노드가 하나」 축만 붉는다(정확히 그 자리) |
| M-3 | 조건이 없어도 공고를 긁는다(`enabled: true`) | 홈 test | **2 RED** |
| M-4 | 마감일 변환을 화면이 지어낸다(표시층을 지나지 않음) | 홈 test | **1 RED** |
| M-5 | 제안 빈 화면의 걸음을 지운다 | 홈 test | **1 RED** |
| M-6 | 칩 칸 레이블의 터치 하한을 뺀다 | 레이블 축 게이트 | **1 RED** |
| M-7 | 알림함 읽기에서 쪽 인자를 뺀다 | 알림함 test | **2 RED** |
| M-8 | 쓰기 **실패에서도** 무효화한다 | 알림함 test | **1 RED** |
| M-9 | 링크 읽음을 낙관 갱신으로 만든다 | 알림함 test | **2 RED** |
| M-10 | 「더 보기」를 지운다 | 알림함 test | **2 RED** |
| M-11 | 알림함 빈 화면의 걸음을 지운다 | 알림함 test | **1 RED** |
| M-12 | 알림함 읽기에서 쪽 인자를 뺀다(M-7 과 같은 변이) | **여정 전건** | **1 RED** — 화면의 읽기를 집는 포착이 2.0분 기다리다 멈춘다 |

**M-9 는 두 번 쟀다.** 첫 판은 화면이 들이지 않은 식별자를 썼고 그것은 **거동 0 이 아니라 컴파일
0** 이다 — 네 test 가 함께 붉은 것이 그 신호였다. 임포트 없이 같은 낙관 갱신을 심어 다시 재니
**둘**이 붉었고(응답 전 · 실패 뒤), 그 둘이 이 축의 자리다.

**술어의 다른 방향도 실측됐다.** G-6 첫 실행에서 「배지는 쪽을 보내지 않는다」가 **0 건으로** 붉었다
— 장부를 로그인 뒤에 걸어 배지의 첫 읽기를 놓친 탓인데, 그 붉음이 이 음성 단언이 공허하지 않다는
증거다. M-12 가 반대 방향을 잰다.

## Phase 3c — 레인 밖 화면에 미치는 영향 (팀장 요청)

390px 여정 fixture 에서 **한 화면의 한 상태**를 잰 값이다(13B 알려진 제한 13 승계).

| 화면 | 2층 라벨 펼침 버튼(보이는 것) | 그 화면의 타깃 전수 · 하한 |
| --- | --- | --- |
| 워크시트(13C 소유) | **3** | 10 · 7 |
| 결과 확인(13D 소유) | **12** | 21 · 7 |
| 알림함(13E 소유) | **3** | 21 · 12 |
| 설정 | **3** | 30 · 27 |
| 시작하기 · 홈 · 입찰함 · 로그인 · 없는 주소 | **0** | 16·13 / 33·8 / 11·11 / 1·1 / 5·5 |

- **하한은 하나도 낮추지 않았고 전부 통과**했다. 홈이 33 인 것은 그 게이트가 홈을 **검색 결과
  상태**로 재기 때문이다(13B 가 그렇게 세웠다) — 기본 목록을 제안으로 바꾼 변경이 이 게이트의 홈
  계수를 줄이지 않은 이유다. 그 대신 **홈의 기본 상태는 이 게이트 밖**이다(checklist 의 알려진 제한).
- 숫자가 말하는 것: 배지가 버튼이 되면서 **내 소유 밖 두 화면에서 15 자리**(워크시트 3 · 결과 12)가
  44px 상자를 얻는다. 레이아웃은 그만큼 세로로 자란다.

## Phase 4 — rollback 실측

`rollback.md` 가 정본이다(실측 HEAD · ①~⑥ · 되돌림 목록). 요지: 복원 exit 0 · **D 4 · M 10** ·
base 와의 diff **0 바이트** · typecheck 0 · test **51 파일 1169**(base 와 같다) · 게이트 셋 exit 0.
