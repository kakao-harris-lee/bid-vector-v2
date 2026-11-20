# M14 / wf-bidbox-results (14D) — slice 계약

```yaml
milestone: M14
slice: wf-bidbox-results
base_sha: 056f405c9e5659b9a068288f1104f9e04a6de016   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/src/screens/BidBoxScreen.tsx        # 탭 줄 · 「페이퍼 따로 보기」 · 레일 둘 · 머리
  - web/src/screens/BidBoxCard.tsx          # 카드 재배치(제목 + 상태 알약 / 기관 · 내 투찰 / 다음 확인)
  - web/src/screens/BidBoxScreen.test.tsx   # 위 둘의 짝 test
  - web/src/screens/BidBoxCard.test.tsx     # 같음
  - web/src/screens/ResultsScreen.tsx       # 요약 타일 넷 · 잠정 1위 경고 카드 · 「다루지 않는 자리」
  - web/src/screens/ResultCard.tsx          # 결과 카드 재배치 · 빈 자리 + 사유
  - web/src/screens/ResultsAggregate.tsx    # 타일 ③ 과 같은 두 축을 쓰는 자리(사본을 만들지 않는다)
  - web/src/screens/ResultsScreen.test.tsx  # 위 셋의 짝 test
  - web/src/display/bidBoxDisplay.ts        # 탭 분할 · 「이번 주 일정」 · 「상태 뜻」(표시 계약 — 아래 OPEN ①)
  - web/src/display/bidBoxDisplay.test.ts   # 그 표의 짝 test
  - web/src/display/resultDisplay.ts        # 열린 결정 ⓖ — 배지 문면을 사유와 맞춘다(`kind` 불변)
  - web/src/display/resultDisplay.test.ts   # 「LATER 가 0」·「남은 둘이 영구 빈 자리」 단언의 짝
  - web/src/display/myBidResultDisplay.ts   # 타일 ①③ 이 읽는 자리(읽기 위주 · 더하는 쪽으로만)
  - web/src/display/myBidResultDisplay.test.ts
  - web/scripts/screen-write-reach.mjs      # 새 게이트 — 화면 모듈 → 질의 import 그래프
  - web/scripts/screen-write-reach.test.mjs # 그 술어의 짝 test(양성 대조 포함)
  - web/config/gate-tests.txt               # 새 게이트 test 를 **만드는 커밋에서** 등재
  - web/src/screens/screenCopy.test.tsx     # 새 카드·타일이 드는 문면 게이트 모집단
  - web/src/screens/screenVocabulary.test.tsx # 같음(어휘 DOM 게이트)
  - web/e2e/journey/results.spec.ts         # 여정이 집는 자리가 배치 변경으로 움직이는 만큼
  - web/e2e/journey/journey.spec.ts         # 같음
  - web/e2e/journey/touch-target.spec.ts    # 탭이 늘어 하한이 **올라가는** 만큼
  - web/e2e/capture/usecase-captures.mjs    # 캡처의 양성 표지(배치가 바뀌면 움직인다)
  - docs/discovery/capability-map.md        # §12.3 하위 절 하나 — 글자는 머지 차례에 정한다
  - reports/evidence/m14/wf-bidbox-results/
out_of_scope:
  - web/src/index.css · AppShell.tsx · routes.tsx · components/   # 14A(원시 요소는 **쓰기만** 한다)
  - web/src/screens/TodayNoticesScreen.tsx · NoticeCard.tsx · OnboardingScreen.tsx  # 14B
  - web/src/display/moneyDisplay.ts 의 압축 금액 꼴                # 14B
  - web/src/screens/Worksheet*.tsx                                 # 14C
  - web/src/api/generated/** · openapi/** · Kotlin · ml-engine     # 14E 와 서버(승인 경계)
  - 서버가 내지 않는 값(실현 하한 · 예정가 · bp · 적격 계수 · 「고른 안」)  # 계약 「데이터」 — 빈 자리 + 사유
  - package.json 의 dependencies                                   # 새 의존성 0
  - .github/workflows/ci.yml                                       # acceptance 는 오늘의 web job 명령 그대로
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew --no-daemon ktlintCheck detekt cpdCheck leakPatternGate   # evidence 가 누출 패턴 게이트의 스캔 뿌리에 든다 — evidence 편집 커밋의 HEAD 에서
rollback: rollback.md
```

## 받는 항목과 처분

| 항목 | 이 slice 의 처분 |
| --- | --- |
| D 입찰함(대조표 6행) | **정렬 — 14E 투영 위에서.** 상태별 개수 탭 · 「페이퍼 따로 보기」 · 카드(제목 + 상태 알약 / 기관 · 내 투찰 / 다음 확인) · 레일 「이번 주 일정」·「상태 뜻」 |
| E 결과(대조표 7행) | **부분 정렬.** 타일 ① 정렬 · ③ 두 축 병기 · ②④ 빈 자리 + 사유 · 잠정 1위 경고 카드 · 결과 카드. 실현 하한 · bp · 「고른 안」은 **데이터로 막힘** |
| 열린 결정(13A ⓖ) | 배지 문면을 **사유 쪽에** 맞춘다. `kind` 는 그 기능 마일스톤이 값을 낼 때 정한다 |
| 새 게이트 「읽기 화면은 쓰기를 부르지 않는다」 | 화면 모듈 → 질의 import 그래프 단언 + 양성 대조 |

처분의 **근거와 잔여**는 `checklist.md` 가 정본이다. 착수 실측의 수치와 조건은 `commands.md` 가
정본이고 여기 옮겨 적지 않는다.

## 착수 재실측 (계약 행 ↔ 코드)

| 계약이 말한 것 | 착수 실측 | 처분 |
| --- | --- | --- |
| 탭 분할의 정본은 `display/bidBoxDisplay.ts` 의 표 · 와이어프레임 **여덟 탭**과 먼저 대조 | 표는 **여섯**이다. 와이어프레임의 여덟 = 「전체」 + 상태 일곱. 갈리는 자리는 둘 — ⒜ 「전체」가 표에 없다 ⒝ 「투찰 예정」이 표에서 「투찰가 받음」에 **접혀 있다** | **표시 계약 변경 보고**(아래 OPEN ①) |
| 레일의 사건 낱말은 `nextCheckpointKind` 열거에서만 | 열거는 여섯(마감·개찰·서류 기한·통보 예정·리마인드·없음). 와이어프레임의 다섯 줄 가운데 **「참가신청 마감」이 열거 밖**이고 나머지 넷은 열거에 있다 | 「참가신청」은 **그리지 않는다**(계약이 미리 금지한 자리) |
| 카드에 공고명이 없다 → 14E 투영 | base 가 그 투영을 담는다(`noticeKnown`·`noticeTitle`·`demandAgencyName`·`noticeAgencyName`). 7 fixture 계수가 이미 `1,1,1,3,1,1,1`(합 9) | 정렬. 머지 차례에 재계수 |
| 타일 ① 투찰 | `aggregate.sampleSize`(투찰 기록이 있는 실투찰 건 전부). **와이어프레임의 「최근 90일」은 서버가 선언하지 않는다** | `tileValue` + 기존 표본 정의 문면. 90일은 **적지 않는다** |
| 타일 ③ 낙찰 | `rateAxesOf` 가 이미 **길이 둘의 튜플**을 낸다 — `tileTwoAxis` 의 인자 모양과 같다 | 정렬(사본 0) |
| 타일 ②④ | `aggregate` 에 적격 계수가 없고 `MyBidCase` 에 하한·bp 칸이 없다 | `tileUnavailable` |
| 「읽기 화면은 쓰기를 부르지 않는다」를 오늘의 등식이 못 잡는다 | **못 잡는다** — 승인 쌍 등식은 `queries.ts` 를 **한 집합**으로 재고 화면별로 나누지 않는다. 각 질의 함수가 `apiClient.<메서드>('<경로 리터럴>')` 를 **정확히 하나** 들어 함수 → (메서드, 경로) 사상이 AST 로 선다 | **측정 가능**(아래 OPEN ③) |
| 배치 게이트의 레일 축이 출하 화면의 레일을 자동으로 든다 | **오늘은 들지 않는다** — 그 spec 이 가는 자리는 로그인 화면 하나이고 레일은 **합성 마운트**다. 술어의 모집단은 「그 문서의 표지 전수」가 맞지만 **문서에 출하 레일이 없다** | 팀장 판정 요청(아래 OPEN ④) |
| 터치 타깃 하한 | 입찰함 11 · 결과 7. 탭이 늘고 「페이퍼 따로 보기」가 서면 **올라간다** | 올리기만(내려가는 변경 0) |

## 착수 OPEN (팀장 판정 요청)

**① 탭 분할 — 표시 계약 변경 둘.** 와이어프레임 여덟 ↔ 표 여섯의 차이다.

- ⒜ **「전체」 탭**: 상태 → 탭 표의 원소가 아니다(어느 상태도 그리로 가지 않는다). **추천:
  `STATE_TAB` 을 건드리지 않고 「전체」를 *다른 축*으로 세운다** — 고른 것이 「전체」면 거르지
  않고, 아니면 지금처럼 표 조회로 거른다. 표의 `satisfies` 전수성이 그대로 선다.
  **수의 뜻**: 와이어프레임은 사이드바 배지(9)와 「전체」(9)를 같게 두고 「종료 12」를 따로
  세운다 — 즉 그 「전체」는 **종단을 뺀 수**다. 14A 가 배지를 그 뜻으로 이미 출하했다.
  라벨이 「전체」인데 종료를 빼면 거짓말이므로, **추천: 수는 전수(종료 포함)로 세고 그래서
  배지와 다른 수가 된다는 사실을 레일 「상태 뜻」이 말한다.** 반대 추천(배지와 같은 수)을
  고르면 라벨을 「진행 중」 같은 참말로 바꿔야 한다.
- ⒝ **「투찰 예정」 탭**: 표가 `BID_PLANNED` 를 「투찰가 받음」에 접어 둔다(M7/7E-2 의 팀장
  문면 판정 — 「둘 다 제출 전이고 투찰가를 쥔 단계」). 와이어프레임은 둘을 가른다. 이것은
  **표를 바꾸는 변경**이라 계약 「승인 경계」의 보고 대상이다. **추천: 가른다** — 와이어프레임이
  정보 구조의 정본이고, 두 상태는 운영자가 할 일이 다르다(투찰가를 받은 건 ↔ 낼 값을 정한 건).
  `BID_BOX_TABS` 에 한 줄, `STATE_TAB` 의 한 값만 움직이고 `satisfies` 가 전수를 계속 든다.
  **다만 파급이 둘 있다**: 그 탭을 쓰는 여정·터치 spec 의 경로와, `ResultCard` 의 「입찰함에서
  기록」 링크(`?tab=`)다 — 둘 다 표에서 유도하므로 사본은 생기지 않는다.

**② 열린 결정 ⓖ 의 고치는 자리.** 배지는 `kind` 하나로 갈리고(`LATER → 「준비 중」` ·
`PERMANENT → 「다루지 않음」`) 오늘 두 자리가 모두 `PERMANENT` 다. **추천: `kind` 는 그대로 두고
`PERMANENT` 의 배지 문면만 사유와 맞춘다** — 그러면 표가 한 자리로 남고 diff 가 줄 하나다.
대안(자리마다 배지 문면을 들게 하는 것)은 표를 둘로 쪼개고 `kind` 의 뜻을 더 비운다. 함께 고칠
짝은 **표시 test 의 「LATER 가 0 이다」 단언과 「남은 둘이 영구 빈 자리다」 단언**이다(§12.3 (v)
ⓖ 가 그 둘을 이름으로 적어 두었다).

**③ 새 게이트의 설계 검토를 도는가.** 이 slice 가 세우는 것은 **게이트 술어**다. 공통 지침은
게이트 slice 에 Phase 2.5(위협 모델 → 열거/구성 → 우회 ≥5 → 값 획득 축 → 과잉·미달)를 요구하고,
팀장 브리프는 그 목록에 14A 의 넷만 적었다. **추천: 돈다 — 단 이 게이트 하나로 범위를 좁혀
짧게.** 화면 배치·타일은 설계 검토 대상이 아니다.

**④ 출하 레일의 상자 좌표를 누가 재는가.** 배치 게이트는 레일을 **합성으로** 꽂고 로그인
화면에서만 잰다(14A scope.md 의 「계약 갱신 (2)」가 그 사실을 적어 두었고, 「뒤 레인이 붙이면
같은 단언이 자동으로 든다」는 **그 spec 이 그 화면에 가야** 참이다). 14D 가 출하 레일을 처음
세운다. **추천: 이 slice 에서 닫지 않고 OPEN 으로 등재한다** — 스모크 레인은 세션이 없어
`/bids`·`/results` 가 로그인 얼굴을 내고, 여정 레인으로 옮기는 것은 14A 가 일부러 피한 비용이다.
팀장이 「닫아라」로 판정하면 여정 레인에 레일 좌표 단언을 더한다(그 경우 in_scope 에 여정 spec
하나가 더 든다).

## 승인 경계 (계약 「승인 경계」 절의 이 slice 몫)

- **표시 계약의 표**(`bidBoxDisplay` 탭 분할) 변경은 위 OPEN ① 의 판정 없이 하지 않는다.
- **기존 게이트 술어** 변경 0. 새 게이트를 **더하는** 것은 술어 변경이 아니다.
- 터치 타깃 하한의 계수가 **줄어드는** 변경은 멈추고 보고한다.
- 서버·OpenAPI·`api/generated` 변경 0, 새 의존성 0, 흐름 순서 변경 0.
- 다른 slice 소유 파일(`components/` 원시 요소 · `AppShell` · `index.css`)은 **쓰기만** 한다.
  넓혀야 하면 14A 인계의 두 조건(기존 호출부가 그대로 컴파일 · 짝 test 를 같은 커밋에서)을 지키고
  거동 변경은 팀장 판정을 받는다.

## base 확인

`git rev-parse 056f405c` · `git merge-base --is-ancestor 056f405c HEAD` 를 착수 커밋에서 기계로
확인했다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 계약 갱신 (착수 뒤 팀장 판정과 구현 실측으로 드러난 것)

**(1) 팀장 판정 넷**(2026-11-20 · 착수 보고의 OPEN ①~④). ① 「전체」는 **다른 축**으로 세우고
수는 **종단까지** 센다(배지와 다른 수인 까닭을 레일이 한 줄로 말한다) · 「투찰 예정」을 **가른다**
(D-7E2-8 의 접음을 뒤집는 **표시 계약 변경**) · ② `kind` 는 그대로 두고 **배지 문면만** 사유와
맞춘다 · ③ 설계 검토를 **그 게이트 하나로** 돌았다(`design-review.md`) · ④ 출하 레일의 상자
좌표는 **14B 몫**이다 — 14D 는 그 spec 을 건드리지 않고 OPEN 도 등재하지 않는다.

**(2) 설계 검토 뒤 팀장이 건 조건 셋**(승인 코멘트). ① `apiClient` 직접 수입은 **부르는
자리**에서 메서드로 가린다(비리터럴은 위반) · ② 자리 표의 지정자·태그가 해소되지 않으면
**던진다** · ③ 새 게이트 test 를 **만드는 커밋에서** 등록부에 적는다. 셋 다 반영했고 각각
변이로 쟀다(`commands.md`).

**(3) in_scope 밖이었던 경로 다섯**(기계 재산출로 등재). 아래는 `git diff --name-only
<base>..HEAD` 와 착수 목록을 대조해 **기계로** 낸 것이다.

| 경로 | 왜 열렸나 |
| --- | --- |
| `web/src/display/caseDisplay.ts` | 「상태 뜻」이 상태 표의 `why` 에서 오므로 잠정 1위에 그 칸을 더했다(키 집합 불변) · 공고 행 부재 문면을 **한 자리**로 뺐다 |
| `web/src/screens/InboxScreen.tsx` | 그 한 자리를 쓰는 **둘째 자리**. 같은 사실을 두 곳에 적지 않으려는 한 줄 교체(거동 0) |
| `web/scripts/screen-vocabulary-registry.mjs` | 새 표시 선언 넷의 등재(계약 「겹치는 자리」가 미리 적은 자리) |
| `web/vite.config.ts` | **변이에서만** 건드렸고 커밋에 들어가지 않는다(등록부가 짐을 지는지 재는 자리) |
| `docs/usecase/captures/` | 캡처 도구의 산출물. **커밋하지 않는다** — 찍고 레인 scratchpad 로 복사한 뒤 트리를 되돌렸다 |

**(4) 「내 투찰」은 결과 조회에서 읽는다**(계약 14D 행의 카드 칸). `BidCase` 에 금액 칸이 없다 —
그 칸을 **빈 자리 + 사유**로 두는 대신 **이미 승인된 읽기**에서 읽어 채웠다. 새 operation 0 ·
쓰기 0 이라 승인 쌍 등식이 움직이지 않는다. 한 건에 행이 여럿이면 **고르지 않는다**(모르는 것을
금액으로 보이지 않는다).

**(5) 레일 제목이 「이번 주 일정」이 아니다 — 와이어프레임 이탈**(사유를 남긴다). 「이번 주」를
말하려면 지금이 언제인지 알아야 하는데 화면·표시층의 벽시계 읽기는 lint 가 막는다(D-1,
`timeDisplay` 하나가 예외). 브라우저 시계로 주를 가르면 사용자마다 답이 다르고 서버의 `overdue`
판정과 어긋난 말을 하게 된다. 그래서 **거르지 않고 이른 것부터** 늘어놓고 제목을 「다음 확인
시점」으로 한다 — 카드의 같은 칸과 **같은 낱말**이다.

**(6) 결과 카드의 머리도 공고명으로 바꿨다**(계약 14D 행의 「결과 카드」). 14E 투영이 선 뒤로
D·E 두 화면이 같은 카드 문법을 쓴다. 앞 판은 공고번호가 가장 큰 글자였다.

**(7) 수정 라운드 1 이 더한 자리**(v not-ready H-1 · cr request_changes HIGH ×2 · 팀장 판정).

| 경로 | 왜 열렸나 |
| --- | --- |
| `web/src/AppShell.tsx` | 배지 셈을 표시층 한 자리로 옮기고 셸이 그것을 부른다(cr M-2 · 거동 0) |
| `web/e2e/journey/touch-target.spec.ts` | 터치 하한을 **실측으로** 올린다(cr M-1 — 계약 「올리기만」) |

## 갱신 이력

- 2026-11-20 초판(착수 보고와 함께).
- 2026-11-20 팀장 판정 넷과 설계 검토 조건 셋을 (1)·(2) 로, 기계 재산출과 구현 실측을
  (3)~(6) 으로 등재.
- 2026-11-20 수정 라운드 1 — 새로 연 자리 둘을 (7) 로 등재.
