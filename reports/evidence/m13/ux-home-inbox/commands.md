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
