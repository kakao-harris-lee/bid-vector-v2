# M13 / ux-case-exits (13D) — slice 계약

```yaml
milestone: M13
slice: ux-case-exits
base_sha: bd5cd039eaf70f6423cf85e3bf1923bb367fa16e   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/src/api/approvedOperations.ts   # 승인 쌍 +2 (POST …/defer · POST …/skip)
  - web/src/api/queries.ts              # deferCase·skipCase — 쌍과 호출이 같은 커밋(AST 등식)
  - web/src/display/bidBoxDisplay.ts    # 내려놓기 둘의 행동 종류·입력 꼴·출발 상태 표(새 값·새 표)
  - web/src/display/bidBoxDisplay.test.ts
  - web/src/screens/BidBoxCard.tsx      # 「나중에」·「건너뜀」 노출 + 건너뜀의 확인 단계
  - web/src/screens/BidBoxScreen.tsx    # 전송 분기 · 나중에 토스트의 실행 취소(= resume) · 빈 화면의 다음 걸음
  - web/src/screens/BidBoxScreen.test.tsx
  - web/src/screens/ResultsScreen.tsx   # 빈 화면의 다음 걸음(항목 7 의 결과 쪽)
  - web/src/screens/ResultsScreen.test.tsx
  - web/src/screens/ResultCard.tsx      # 소유 선언(이 slice 의 편집은 0 일 수 있다)
  - web/e2e/journey/case-exits.spec.ts  # 여정 둘 신설
  - web/e2e/journey/shared.ts           # 계약 갱신 (4) — 상태 문면 표에 빠진 셋(공유 자리, 덧붙이기만)
  - web/config/journey-specs.txt        # 그 spec 을 **같은 커밋에서** 등재(양방향 등식)
  - web/e2e/harness/fixtures/notice-list.json   # 이 여정의 **자기 공고** 한 건(§착수 보고 OPEN-1)
  - web/config/gate-tests.txt           # 새 게이트 test 등재
  - web/e2e/capture/usecase-captures.mjs  # 계약 갱신 (5) — 제출 전 묶음을 보이는 씨앗·화면(덧붙이기만)
  # 계약 갱신 (1) — 게이트가 요구한 자리들. 아래 「계약 갱신」 절이 사유를 든다.
  - web/scripts/bid-exit-transitions.test.mjs      # 신설 — 출발 집합·주체·상한 등식(export 0)
  - web/scripts/kotlin-source.mjs                  # 읽기 집합에 전이표 키 +1(비공개 표 · 공개 표면 불변)
  - web/scripts/kotlin-source-ownership.test.mjs   # 그 키 계수 단언 7 → 8
  - web/scripts/screen-vocabulary-registry.mjs     # 새 표시 함수 둘의 분류(전수 등식이 요구한다)
  - web/src/screens/BidBoxCard.test.tsx            # 신설 — 상태 전수 루프(설계 검토 우회 ⑤)
  - docs/discovery/capability-map.md    # §12.3 하위 절 하나 — 글자는 머지 차례에 정한다
  - reports/evidence/m13/ux-case-exits/
out_of_scope:
  - web/src/components/Toast.tsx        # **편집 0 이 목표**(계약 13D 행) — 실행 취소 분기가 이미 선다
  - web/src/display/caseDisplay.ts      # 워크시트의 다음 행동 표(`NEXT_ACTION`)는 13C 의 축
  - web/src/screens/Worksheet*.tsx      # 13C — 워크시트의 「나중에」·「건너뜀」은 이 slice 의 질의를 쓴다
  - web/src/screens/TodayNoticesScreen.tsx · NoticeCard.tsx · InboxScreen.tsx · StateBadge.tsx  # 13E
  - web/src/routes.tsx · ScreenPlaceholder · RouteError · Onboarding/SettingsScreen   # 13A
  - web/e2e/journey/touch-target.spec.ts · web/scripts/journey-screen-table.test.mjs  # 13B 의 게이트(§알려진 제한 1)
  - 서버(Kotlin)·openapi/**             # 백엔드 변경 0 — 착수 실측으로 확인(아래 표)
  - web/package.json 의 dependencies    # 새 의존성 0
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

## 받는 항목과 처분 (착수 제안 — 판정은 팀장)

| # | 항목 | 이 slice 의 처분 제안 |
| --- | --- | --- |
| 3 | 건을 내려놓을 수 없다 | **부분 닫힘** — 입찰함 카드에서 「나중에」·「건너뜀」이 선다. 워크시트 쪽 자리는 13C 가 이 slice 의 질의로 닫는다 |
| 4 | 실행 취소가 없다 | **닫힘** — 되돌림 전이가 있는 유일한 쌍(나중에 → 다시 보기)에 실행 취소가 선다. 그 밖의 쓰기에는 두지 않는다(D-7E-5) |
| 7 | 빈 화면이 다음 걸음을 주지 않는다 | **부분 닫힘** — 입찰함 탭과 결과 화면의 빈 얼굴이 `action` 을 받는다. 홈·알림함은 13E, 워크시트는 13C |

처분의 **근거와 잔여**는 `checklist.md` 가 정본이다. 착수 실측의 수치와 그것을 낸 조건은
`commands.md` 가 정본이고 여기 옮겨 적지 않는다.

## 착수 실측 — 계약 13D 행을 코드로 다시 잼

base `bd5cd039` 의 worktree 에서 쟀다. **백엔드 변경 0 이 선다** — 세 operation 이 계약·생성
타입·출하 컨트롤러 셋 모두에 이미 있다.

| 축 | 잰 것 | 결과 |
| --- | --- | --- |
| `/defer`·`/skip`·`/resume` 의 실재 | OpenAPI operationId · 생성 타입 `paths` 키 · Kotlin `@PostMapping` | **셋 다 있다**(서버 변경 0) |
| 요청 꼴 | 계약 스키마 | defer = `remindAt`(date-time, 필수) · skip = `reason`(필수, 500자 상한) · resume = **본문 없음** |
| 응답 꼴 | 계약 | 셋 다 `BidCase` 하나 — 전이 뒤 상태를 서버가 준다 |
| `DEFERRED` 전이 | 전이표(`BidTransitions.kt`) | DEFER: 관심·투찰가 받음 → 나중에 · RESUME: 나중에 → 관심 · SKIP: **제출 전 넷** → 건너뜀 |
| 승인 쌍의 증분 | 승인 목록 | **둘**이다 — `/resume` 은 이미 승인·배선돼 있다(`resumeCase` · `BID_BOX_ACTION.DEFERRED`) |
| `ToastHost` 실행 취소 분기 | 컴포넌트 | 있다(`undo` 선택, 없으면 **그려지지 않는다**) · 넘기는 호출부는 `src/**` 전수에서 **0** |
| 빈 얼굴의 `action` | `EmptyFace` 호출부 | 입찰함·결과 둘 다 `display`·`testId` 만 넘긴다(`action` 0) |

## 착수 보고의 OPEN (팀장 판정 대기 — 답을 받은 뒤 구현에 들어간다)

**OPEN-1 — 이 여정의 자기 공고.** 두 여정은 `WATCHING` 에서 출발해야 하고(DEFER 의 출발 집합),
하네스 공고 열둘 가운데 **마감이 남은 것은 다섯**이며 그 다섯이 모두 다른 여정·게이트의 자기
건이다(수치는 `commands.md`). 마감이 지난 공고는 담는 순간 `DEADLINE_PASSED_UNRECORDED` 라 DEFER
가 409 다(13B 가 같은 자리에서 밟았다). 그래서 `notice-list.json` 에 **공사 업종 한 건**을 더하기를
제안한다 — 수집 업종·승격 계수는 fixture 에서 유도되므로 그 기계는 흔들리지 않는다.

**OPEN-2 — 다음 행동 표의 모양.** 지금 `BidBoxActionRow` 는 `primary`·`secondary` 둘이고, 관심·
투찰가 받음에는 **셋째·넷째 행동**이 필요하다. 「내려놓기」를 `primary`/`secondary` 와 다른 축
(`exits`)으로 두어 §6.5 「다음 행동 하나가 화면을 지배한다」를 지키기를 제안한다.

**OPEN-3 — 교차 언어 등식 게이트(새 게이트 하나).** 화면이 전이표에 없는 출발 상태에서 「나중에」를
보이면 사용자는 409 를 본다. 출발 상태 집합을 `BidTransitions.kt` 에서 읽어 web 표와 등식으로
두기를 제안한다(선례 `money-vocab-cross-lang.test.mjs` · 읽기는 공유 `scripts/kotlin-source.mjs`
한 자리를 거쳐야 한다 — `kotlin-source-ownership` 가 그것을 센다). 술어를 **이 slice 의 두 명령에
한정**하므로 오늘의 production 코드를 붉히지 않는다. 기존 전이 넷까지 넓히는 것은 범위 밖 후보다.

## 알려진 제한 (착수 시점)

1. **터치 타깃 게이트가 이 slice 의 새 컨트롤을 보지 않는다.** 그 spec 의 입찰함 자리는
   `?tab=closed` 이고 내려놓기 둘은 제출 전 묶음에만 선다. `TOUCH_TARGET_SCREENS` 는 `slotId`
   유일성 단언이 있어 자리를 하나 더 둘 수 없다(그 게이트의 술어를 바꾸는 것은 새 결정이다).
   이 slice 의 새 컨트롤은 전부 공유 `Button` 이므로 크기의 정본(`BUTTON_SIZES` 표)과 그 짝
   test 가 그 축을 든다.
2. 여정 등록부의 입도는 「파일」이다(13B 승계) — spec **안에서** test 를 빼는 것은 그 등식이
   잡지 못한다.

## 승인 경계 (계약 「승인 경계」 절의 이 slice 몫)

- **승인 쌍 +2**(`/defer`·`/skip`)는 계약 13D 행이 미리 승인했다. 그 밖의 쌍 증가는 멈추고 보고한다.
- 서버·OpenAPI 변경, 새 의존성, 흐름 순서 변경은 멈추고 보고한다. 착수 실측으로 **서버 변경이
  필요하지 않다**는 것을 확인했다.
- 다른 slice 소유 파일은 열지 않는다. `Toast.tsx` 는 **편집 0** 이 목표다.
- 새 게이트(OPEN-3)가 이 slice 소유 밖의 오늘의 production 코드를 붉히면 멈추고 보고한다.

## base 확인

`git rev-parse bd5cd039` → `bd5cd039eaf70f6423cf85e3bf1923bb367fa16e` · `git merge-base
--is-ancestor bd5cd039 HEAD` 를 착수 커밋에서 기계로 확인했다(위 yaml 의 40자는 손으로 적은
값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 하네스 레인 변경 — test 하네스 (팀장 조건 (1), 2026-11-19)

위 절의 기계 술어는 **에이전트 하네스**(`CLAUDE.md`·`.claude/`)를 센다. 이 slice 는 그 자리를
건드리지 않았고(여전히 없음), 대신 **test 하네스** 한 자리를 바꿨으므로 따로 적는다 — 두 술어를
섞으면 위 절의 계수가 거짓이 된다.

- `web/e2e/capture/usecase-captures.mjs` — 담기만 하는 씨앗 하나 · `관심` 탭 화면 한 자리 ·
  적재 시점 탐침의 칸 하나. **덧붙이기만**이고 지우는 것이 없다.
- **왜 임시 패치가 아닌가**: 캡처는 `web/` 가 깨끗할 때만 돈다(그러지 않으면 manifest 의 head 가
  찍은 트리를 가리키지 못한다). 「패치 → 실행 → 되돌림」은 그 가드와 양립하지 않는다 — 실측으로
  밟았다. 그리고 **제출 전 묶음을 보이는 자리가 집합에 없었다**(씨앗 둘이 낙찰·투찰 기록됨까지
  밀리고 입찰함을 종료 탭으로 찍는다). 그 얼굴을 넣는 길이 이 추가뿐이다.
- `web/e2e/journey/shared.ts` 의 문면 셋도 같은 성질의 공유 자리 변경이다(계약 갱신 (4)).

## 계약 갱신

**(1) in_scope 에 `web/scripts/` 넷과 카드 짝 test 가 든다.** 구현이 만든 새 파일 둘(출발 집합
등식 게이트 · 카드의 상태 전수 루프)과, **오늘의 게이트가 요구해서** 열게 된 자리 셋이다.

- 읽기 집합의 키 +1 과 그 **계수 단언 7 → 8**: Kotlin 원문을 읽는 길이 공유 모듈 하나뿐이고
  (그 소유권을 게이트가 센다) 키 전수에 계수 단언이 걸려 있다. 팀장 승인 항목이다.
- **표시 함수 둘의 분류**: 어휘 등재의 전수 등식이 새 `export` 함수를 「분류하라」고 요구한다
  (착수 때 모르던 의무다 — 실측으로 알았다). 둘 다 보이는 글자를 만들지 않는 축으로 등재했다.
- **카드 짝 test 를 가른 이유**는 축이 다르기 때문이다(카드가 표를 따르는가 ↔ 화면이 요청을
  어떻게 보내는가). 크기 회피용 분할이 아니다 — 화면 test 에 몰면 그 파일이 권장 한도를 넘는다.

**(2) 새 파일 둘은 만드는 커밋에서 `gate-tests.txt` 에 등재했다**(양방향 등식).

**(3) `ResultCard.tsx` 편집 0 으로 확정**(팀장 승인). 그 카드에 화면 얼굴이 없고 칸 단위 빈
자리·미판정 문면뿐이며 이미 사유를 든다 — 항목 7 의 결과 쪽은 결과 **화면**의 얼굴이 닫는다.

**(4) in_scope 에 여정의 공유 모듈이 든다.** 그 파일의 상태 문면 표에 **상태 셋이 없었다**
(`DEFERRED`·`SKIPPED`·`마감 지남`). 화면이 그 상태를 만드는 길이 없던 동안 아무도 밟지 않았고,
호출부의 폴백(`?? state`)이 기대값을 **wire 토큰**으로 만들어 그 단언이 「화면이 틀렸을 때만 맞는」
꼴이었다 — 이 slice 의 첫 여정 실행이 그것을 붉혔다(실측). 덧붙이기만 했고 기존 행은 건드리지
않았다. 그 표의 **전수**는 이 slice 의 게이트가 계약 어휘와 등식으로 닫는다.

**(5) in_scope 에 캡처 도구가 든다.** 기본 집합의 씨앗 둘이 각각 낙찰·투찰 기록됨까지 밀려 있고
입찰함을 **종료 탭**으로 찍으므로, **제출 전 묶음을 보이는 자리가 집합에 아예 없다** — 내려놓기
둘이 그 묶음에만 서므로 이 slice 의 변경이 캡처에 한 장도 잡히지 않았다(팀장 지시는 그 얼굴을
반드시 넣는 것이다).

임시 패치로 찍고 되돌리는 길은 **그 도구와 양립하지 않는다**: 캡처는 `web/` 가 깨끗할 때만
돌고(그러지 않으면 manifest 의 head 가 찍은 트리를 가리키지 못한다) 그 가드가 설계다 — 실측으로
밟았다. 그래서 덧붙이기만 해서 **커밋한다**: 담기만 하는 씨앗 하나 · `관심` 탭 화면 한 자리 ·
적재 시점 탐침의 칸 하나. 지우는 것이 없어 다른 slice 의 캡처도 그대로 돈다.

씨앗 공고는 **앞·뒤 양쪽에 있는 기존 fixture** 다. 이 slice 가 더한 공고를 쓰면 앞 캡처가 검색
부재로 죽고 앞·뒤가 서로 다른 건을 보이게 되어 비교가 성립하지 않는다.

## 갱신 이력

- 2026-11-18 초판(착수 보고와 함께). OPEN 셋은 팀장 판정을 기다린다.
- 2026-11-18 팀장 착수 판정(OPEN-1·2·3 승인 · 사유 축 처분 셋) 뒤 §계약 갱신 (1)~(3).
