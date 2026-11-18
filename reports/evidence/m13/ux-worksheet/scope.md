# M13 / ux-worksheet (13C) — slice 계약

```yaml
milestone: M13
slice: ux-worksheet
base_sha: bd5cd039eaf70f6423cf85e3bf1923bb367fa16e   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/src/screens/WorksheetScreen.tsx      # 복사·확인 단계·접기·담기·빈 화면의 다음 걸음
  - web/src/screens/WorksheetRecord.tsx      # 나라장터 넘김 카드(복사·한글 병기·하한 여유)
  - web/src/screens/WorksheetGrounds.tsx     # 판정·근거·자격·고지 절의 접기
  - web/src/screens/WorksheetScreen.test.tsx # 짝 test(기존 등록부에 이미 있다)
  - web/src/display/moneyDisplay.ts          # 한글 금액 병기(억·만) — 금액 어휘의 한 자리
  - web/src/display/                         # 하한 여유의 10진 계산 모듈 신설 + 그 짝 test
  - web/config/gate-tests.txt                # 새 vitest 게이트 test 를 만드는 커밋에서 등재
  - web/e2e/journey/                         # 여정 단언 갱신(접기·확인 단계) · 화면 C 하한 N
  - web/config/journey-specs.txt             # 여정 spec 을 더하면 같은 커밋에서
  - reports/evidence/m13/ux-worksheet/
  # 아래 넷은 **팀장이 넘김 항목을 13C 범위로 승인할 때만** 든다(아래 「넘김 항목」).
  - web/scripts/jsx-handler-tags.mjs         # v M-7b · cr Q-2 · Q-3① (게이트 술어 — 설계 검토 선행)
  - web/scripts/touch-target-labels.mjs      # v M-11 (게이트 술어 — 설계 검토 선행)
  - web/src/index.css                        # 제한 27 (Tailwind 훑기 뿌리) — 13B 착수 판정 ⓗ 의 묶음이 13C 엔 없다
  - docs/discovery/capability-map.md         # 새 OPEN 이 서면 §12.3 하위 절 하나 — 글자는 머지 차례에
out_of_scope:
  - 워크시트의 「나중에」·「건너뜀」 배선      # **보류 자리** — 13D 머지 뒤 팀장 호출로 그 시점 main 위에서(팀장 2026-11-18)
  - web/src/api/queries.ts                   # 담기는 이미 있는 호출(addBidCase)과 승인 쌍을 재사용 — 편집 0 이 목표다
  - web/src/api/approvedOperations.ts        # 승인 쌍 증분 0 (새 쌍이 필요해지면 승인 경계)
  - web/src/screens/BidBoxCard.tsx · ResultsScreen.tsx · ResultCard.tsx   # 13D
  - web/src/screens/TodayNoticesScreen.tsx · NoticeCard.tsx · InboxScreen.tsx · components/StateBadge.tsx  # 13E
  - web/src/display/** 의 3안 사유 문면       # 13A(BID_PRICE_MENU_REASON_DISPLAY 등)
  - web/src/components/                      # 공유 버튼·카드는 **쓰기만** 한다(새 공유 컴포넌트를 두지 않는다)
  - 서버(Kotlin) · openapi/**                # 백엔드 변경 0 (승인 경계)
  - package.json 의 dependencies             # 새 의존성 0 (승인 경계)
  - .github/workflows/ci.yml                 # acceptance 는 오늘의 web job 명령 그대로
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # evidence 가 누출 패턴 게이트의 스캔 뿌리에 들어 경로가 닿는다 — evidence 편집 커밋의 HEAD 에서
rollback: rollback.md
```

## 받는 항목과 처분 (제안 — 판정은 팀장)

| # | 항목 | 이 slice 의 처분 제안 |
| --- | --- | --- |
| 2 | 나라장터 넘김이 덜 됐다 | **닫힘** — 금액 복사(성공·거부 피드백 · 거부 시 선택 가능한 원문) · 한글 금액 병기 · 「이 금액으로 투찰하시겠습니까?」 확인 · 하한 대비 여유(USABLE 에서만) 넷 전부 |
| 11 | 점진적 공개가 거의 없다 (워크시트 자리) | **부분 닫힘** — 워크시트의 판정·근거·자격·고지 절이 요약 한 줄 → 펼침이 된다. 2층 라벨의 둘째 층은 13E |
| 3 | 건을 내려놓을 수 없다 (워크시트 자리) | **부분 닫힘 + 잔여** — 워크시트 안 「담기」는 이 라운드에, 「나중에」·「건너뜀」은 13D 머지 뒤 팀장 호출로(보류 자리) |
| 7 | 빈 화면이 다음 걸음을 주지 않는다 (워크시트 자리) | **부분 닫힘** — 워크시트의 「그 공고를 찾지 못했습니다」와 「입찰함에 없습니다」 두 자리가 다음 걸음을 든다. `EmptyFace` 의 `action` 을 쓰는 첫 호출부다(착수 실측 0/6) |

처분의 **근거와 잔여**는 `checklist.md` 가 정본이다. 착수 실측의 수치와 그것을 낸 조건은
`commands.md` 가 정본이고 여기 옮겨 적지 않는다.

## 착수 판정 (팀장 — 이 절이 범위 밖 판정과 술어 확정의 정본)

| 물음 | 판정 |
| --- | --- |
| — | (착수 보고의 답을 받으면 여기에 적는다) |

## 넘김 항목 (13B → 13C, 팀장 2026-11-18 지시)

처분 후보는 착수 보고에 적었고 **판정은 팀장**이다. 받은 항목 가운데 **게이트 술어를 바꾸는
것**은 구현 전 `design-review.md` 를 보고하고 판정을 받는다.

| 넘김 | 성격 | 받으면 할 일 |
| --- | --- | --- |
| v M-7b · cr Q-2 · Q-3① | 게이트 술어(핸들러 축) | 참조 축으로 뒤집기 + 수신자 기본값 뒤집기 — 설계 검토 선행 |
| v M-8 · L-4 | 게이트 술어(터치 타깃 측정) | 조상 잘라내기의 가시 사각형 교집합 · 스크롤 예외의 `touch-action` |
| v M-11 | 게이트 술어(레이블 축) | min-height 가 서는 display 어휘 요구 |
| 제한 27 · v L-10 | 구조(훑기 뿌리) | `index.css` 에서 class 후보 뿌리를 출하 소스로 묶는다 + 죽은 규칙 하나 |
| v L-9 | 장부 | 13C 가 그 축을 고치면 **13C 의 checklist** 가 새 사실을 적는다(남의 evidence 를 고치지 않는다) |

## 승인 경계 (계약 「승인 경계」 절의 이 slice 몫)

- **승인 쌍 증분 0 이 목표다.** 담기는 이미 승인된 쌍(`POST /api/bid-cases`)과 이미 있는 호출을
  재사용한다. 새 쌍이 필요해지면 멈추고 보고한다.
- **공유 파일을 고쳐야 할 때는 고치기 전에 보고한다** — 여정 `journey.spec.ts`(접기·확인 단계로
  단언이 갈린다) · 터치 타깃 spec 의 화면 C 하한 N · `screenVocabulary.test.tsx` 의 카드 목록.
- **하한 여유는 화면 계산이다.** 서버가 그 값을 내는 표면이 계약에 없다(실측). 계산을 쓰는 조건
  (`USABLE` + 기초금액 + 입력 금액)을 벗어나면 **빈 자리**이고, 값을 지어내거나 접지 않는다.
- 서버·OpenAPI 변경, 새 의존성, 흐름 순서 변경은 멈추고 보고한다.
- 재작업 상한 5(not-ready + request_changes 합산)에 닿으면 진단을 붙여 보고한다.

## base 확인

`git rev-parse bd5cd039` · `git merge-base --is-ancestor bd5cd039 HEAD` 를 착수 커밋에서 기계로
확인했다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 갱신 이력

- 2026-11-18 초판(착수 보고와 함께).
