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
  - web/e2e/                                 # 여정 단언 갱신(접기·확인) · 측정 공용 모듈 · 합성 대조 spec(스모크 레인)
  - web/config/journey-specs.txt             # 여정 spec 을 더하면 같은 커밋에서(이 라운드는 증분 0)
  - web/config/smoke-specs.txt               # 셸 스모크 등록부 신설 — 아래 「계약 갱신」 (6)
  - web/scripts/smoke-lane.mjs               # 그 레인의 상수 한 자리(+ run-begin · 게이트 · 짝 test)
  - web/scripts/journey-results.mjs          # 판정 술어를 두 레인이 함께 쓴다(사유 문면만 인자로) — 공유 파일
  - web/playwright.config.ts                 # json 리포터를 켠다(그 레인의 판정 산출물)
  - web/package.json                         # `test:e2e` 체인만 — dependencies 증분 0(승인 경계 유지)
  - web/scripts/screen-vocabulary-registry.mjs  # 어휘 장부 분류 둘 — 아래 「계약 갱신」 (1)
  - web/src/screens/screenVocabulary.test.tsx   # 넘김 카드 마운트 등재 — 공유 파일(팀장 승인)
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
| 넘김 여덟의 처분 | **다섯 줄 전부 받는다**(v M-7b+cr Q-2+Q-3① · M-8+L-4 · M-11 · 제한 27+L-10 · L-9). 게이트 술어를 바꾸는 것은 설계 검토 선행 |
| 확인 단계의 자리 | **「투찰 예정 초안」에만**. 「제출함」은 §6.2 C 5 의 「한 번으로 ④ 완료」라 더하지 않는다 |
| 접기 범위 | **넷**(판정·근거·자격·고지) — 받는 항목 11 기준. 구현은 **다섯**(3안 자리 포함)이고 그 이탈은 팀장 추인(아래 「계약 이탈」) |
| 하한 여유의 축 | 착수 판정은 「(입력 − 하한)/하한」이었고 **수정 라운드 1 에서 율 축으로 바뀌었다**(verifier H-1 · 팀장 판정 2026-11-18): 임계 사정률 = 투찰율 ÷ 하한율. 정본이 「하한 제약은 율 축에서만」으로 못박은 자리다 |
| 과세 축 | 둘 다 알려지고 **같을 때만** 계산 · 아니면 빈 자리 + 사유(0%·대시로 접지 않는다) |
| 공유 파일 셋 | 승인 — 여정 spec 의 펼침·확인 클릭(단언을 약하게 하지 않는다) · 터치 타깃 하한은 **올리기만** · 어휘 카드 목록 등재 |
| 복사의 측정 자리 | 두 갈래는 vitest · 여정은 존재와 터치 타깃만(공유 Playwright 설정 편집 0) |
| 새 공유 컴포넌트 | **0** — 접기·복사 조각은 `Worksheet*` 안에 둔다 |
| 요소 생성 호출의 경계 | **닫는다**(2026-11-18). 수정 라운드 1 에서 참조 축까지 닫혔다 |
| 44px 어휘 | **넓히지 않는다** — 13B 제한 20 의 「좁다 · 탈출구 없음」을 그대로 둔다 |

### 계약 이탈 한 건 (팀장 추인 2026-11-18)

접기가 판정 **넷**이 아니라 **다섯**이다 — 「투찰가 3안 자리」가 더 들었다. 같은 꼴의 카드라 넷만
접으면 그 하나가 예외로 남고, 요약 줄이 상태 배지를 들어 **접어도 사실이 숨지 않는다**. 근거는
`checklist.md` 알려진 제한 18 이고 verifier 도 「성립한다」고 봤다.

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

## 계약 갱신

**(1) `web/scripts/screen-vocabulary-registry.mjs` 가 in_scope 에 든다.** 새 표시 모듈과 새 함수는
어휘 장부에 **분류**되어야 하고(그러지 않으면 모집단 게이트가 붉는다), 그 장부가 이 파일이다.
분류는 둘이다 — 한글 병기는 **어휘**(통화 표의 라벨을 싣는다), 하한 여유는 **다른 축**(비율
글자이고 금지 어휘를 그리지 않는다).

**(2) `web/src/screens/screenVocabulary.test.tsx` 가 in_scope 에 든다.** 병기를 어휘로 분류한
결과로 넘김 카드가 **마운트 의무**를 지고, 그 목록이 이 파일이다(팀장 착수 판정 §3-5 가 미리
승인한 공유 파일 셋 가운데 하나다).

**(3) 새 spec 하나가 셸 스모크 레인에 선다**(`web/e2e/touch-measure.spec.ts`). 터치 타깃 측정의
합성 대조이고 하네스가 필요 없어 **여정 레인이 아니다** — 그래서 여정 등록부 증분이 0 이다
(그 등록부는 여정 레인의 집합과 등식을 건다).

**(4) 측정 함수를 공용 모듈로 옮겼다**(`web/e2e/journey/touchMeasure.ts`). 합성 대조가 게이트와
**같은 함수**를 보게 하려는 것이고, 사본을 만들지 않는 것이 요점이다.

**(5) `web/src/index.css` 편집이 섰다** — 13B 착수 판정 ⓗ 의 「편집 0」 묶음은 13B 기준이고,
13C 는 제한 27 의 구조 해법(훑기 뿌리 묶기)을 받았다.

**(6) 셸 스모크 레인에 등록부와 결과 판정이 섰다**(수정 라운드 2 · verifier R1-M5). 신설 파일
다섯(`web/config/smoke-specs.txt` · `web/scripts/smoke-lane.mjs` · `smoke-run-begin.mjs` ·
`check-smoke-results.mjs` · `check-smoke-results.test.mjs`)과 편집 셋(`playwright.config.ts` 의
리포터 · `package.json` 의 `test:e2e` 체인 · `scripts/journey-results.mjs` 의 사유 문면 인자)이
in_scope 에 든다.

**레인 간 영향** — 이 셋은 다른 slice 가 만지는 자리다.
- `web/config/smoke-specs.txt`: 셸 스모크 레인에 spec 을 더하거나 이름을 바꾸는 slice 는 **같은
  커밋에서** 이 파일을 고쳐야 한다. 그러지 않으면 `npm run test:e2e` 가 「등록부에 없는 spec 이
  돌았다」로 붉는다(여정 등록부와 같은 규약이고 같은 술어다).
- `web/scripts/journey-results.mjs`: 셋째 인자(건너뛰지 않는 사유 문면)가 늘었다. 기본값이 여정
  문면이라 **여정 레인의 거동은 그대로**이고(acceptance W-13 재측정으로 확인), 호출 자리도 그대로다.
- `web/package.json`: `test:e2e` 가 세 명령 체인이 됐다. **CI 의 명령 글자는 그대로**이고
  (`npm run test:e2e`), 부분 실행(`-- <인자>`)은 이제 그 게이트가 거절한다 — 여정 레인이 이미
  그 꼴이다.

## 갱신 이력

- 2026-11-18 초판(착수 보고와 함께).
- 2026-11-18 팀장 착수 판정(§1~§3)을 받아 구현 착수 · 설계 검토 보고(`design-review.md`).
- 2026-11-18 계약 갱신 (1)~(5) — 구현 라운드가 만진 파일을 in_scope 와 대조해 등재.
- 2026-11-18 계약 갱신 (6) — 수정 라운드 2 의 신설 파일 다섯·편집 셋을 in_scope 와 대조해 등재
  (셸 스모크 등록부). 레인 간 영향 셋을 같은 절에 적었다.
