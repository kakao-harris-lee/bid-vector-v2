# M14 / wf-today-start (14B) — slice 계약

```yaml
milestone: M14
slice: wf-today-start
base_sha: 056f405c9e5659b9a068288f1104f9e04a6de016   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/src/screens/TodayNoticesScreen.tsx   # 머리 요약 · 칩 줄 · 배치(PageHeader)
  - web/src/screens/NoticeCard.tsx           # 카드 재배치 · 모바일 압축 · 자격 슬롯 상태 문면
  - web/src/screens/OnboardingScreen.tsx     # 번호 절 다섯 · 상태 알약 · 레일(미리보기)
  - web/src/display/moneyDisplay.ts          # 압축 금액 꼴(신설 · 키를 더하지 않는다)
  - web/src/display/moneyDisplay.test.ts     # 그 꼴의 짝 test(문면 · 내림 방향 · 경계)
  - web/src/screens/TodayNoticesScreen.test.tsx   # 머리 요약 · 칩 · 카드 배치의 짝 test
  - web/src/screens/OnboardingScreen.test.tsx     # 절 다섯 · 알약 · 레일의 짝 test
  - web/e2e/journey/touch-target.spec.ts     # M13 잔여 ⓗ — 홈 **기본 상태**를 모집단에 넣는다
  - web/e2e/layout.spec.ts                   # 출하 레일이 선 화면을 레일 축이 실제로 재게 한다
  - web/config/gate-tests.txt                # 새 게이트 test 를 **만드는 커밋에서** 등재
  - docs/discovery/capability-map.md         # §12.3 하위 절 하나 — 글자는 머지 차례에 정한다
  - reports/evidence/m14/wf-today-start/
out_of_scope:
  - web/src/screens/Worksheet*.tsx                 # 14C
  - web/src/screens/BidBox*.tsx · Result*.tsx      # 14D
  - web/src/AppShell.tsx · routes.tsx · index.css  # 14A(머지됨) — 셸·토큰은 건드리지 않는다
  - web/src/components/ 의 **거동 변경**           # 14A 소유. 더하는 쪽으로 넓히는 것만, 짝 test 를 같은 커밋에서
  - web/src/api/generated/** · openapi/** · Kotlin # 14E
  - 「자격 통과」·「내 지역」 칩                     # 계약 「범위 밖」(새 판정 축 · 인자 없음)
  - 검색 결과 카드의 자격 **판정**                 # 계약 「범위 밖」 — 카드는 상태 문면만 보인다
  - 모바일 하단 탭을 셋으로 줄이는 것              # 계약의 이탈(13B 의 다섯 칸 유지)
  - 제안 operation 의 쪽·정렬 인자                 # 승인 경계(서버 표면)
  - .github/workflows/ci.yml                       # acceptance 는 오늘의 web job 명령 그대로
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew --no-daemon ktlintCheck detekt cpdCheck leakPatternGate
      # evidence 가 누출 패턴 게이트의 스캔 뿌리에 들어 경로가 닿는다 — evidence 편집 커밋의 HEAD 에서.
      # 이 slice 는 Kotlin 을 만지지 않으므로 `check` 전건이 아니라 정적 게이트를 이름으로 돈다(팀장 지시).
rollback: rollback.md
```

## 받는 항목과 처분

| 항목 | 이 slice 의 처분 |
| --- | --- |
| B 오늘의 공고(대조표 5행) | **정렬 + 데이터로 막힌 칸 하나** — 머리 요약(「감시 조건 통과」 · 분모 `total`) · 칩 줄 · 카드 재배치 · 모바일 압축 카드. 카드의 「방식」 칸은 응답에 **필드가 없다**(아래 착수 재실측) |
| 압축 금액 꼴 | **신설(14B 소유)** — 「약」 표지 · 내림 · 카드에서 정확 금액에 닿는 길 · 금액 어휘 표에 **키 0 증분** |
| 검색 결과 카드의 자격(범위 밖 승계) | **범위 밖 + 상태 문면** — 「제안 목록에서만 판정합니다」 |
| A 시작하기(대조표 4행) | **정렬** — 번호 절 다섯 + 상태 알약 + 레일 「지금 맞는 공고 미리보기」(기존 제안 질의). G3 두 절은 그 모양으로 그리되 「준비 중」 |
| M13 잔여 ⓐ(제안 상한 초과 공시) | **유지** — 머리 요약이 대신하지 않는다 |
| M13 잔여 ⓗ(홈 기본 상태가 터치 게이트 밖) | **닫는다** — 홈을 **두 상태**(기본 = 제안 · 검색 결과)로 잰다 |

처분의 근거와 잔여는 `checklist.md` 가 정본이다. 착수 실측의 수치와 조건은 `commands.md` 가
정본이고 여기 옮겨 적지 않는다.

## 착수 재실측 (계약 행 ↔ 코드)

| 계약이 말한 것 | 착수 실측 | 처분 |
| --- | --- | --- |
| 머리 요약의 분모 = `listSuggestions` 의 `total` | 화면이 이미 그 값을 읽는다(상한 초과 공시가 쓴다) | 정렬 — 새 질의 0 |
| 「상한 초과는 기존 공시가 말한다」 | 공시는 `total > rows.length` 일 때만 선다 | 유지(머리 요약과 **다른 자리**) |
| 칩은 「인자에 닿는 것만」 | 오늘의 칩 축 셋(기관 · 금액대 · 마감일)이 **이미 전부 서버 파라미터**다. 와이어프레임의 프리셋 꼴(「이번 주」·「1억~30억」)은 13E 가 사유를 적어 거절한 모양이다 | 축은 그대로 · 「전체」 칩만 더한다(아래 OPEN ②) |
| 카드의 「방식」(적격심사 · 복수예가) | `NoticeSummary`·`SuggestedNotice` 에 **그 칸이 없다**(nullable 도 아니다 — 스키마에 선언이 없다) | **데이터로 막힘** — 칸을 그리지 않는다(아래 OPEN ③) |
| 「압축 금액 꼴은 재사용이 아니다」 | `koreanAmountTextOf` 는 무손실 읽기이고 「약」도 내림도 없다 | 신설 확정 |
| 「금액 어휘 표에 키를 더하지 않는다」 | `MONEY_TOKENS` 는 표 **넷의 키**에서 유도된다 — 함수를 더해도 키 집합은 움직이지 않는다 | 증분 0 |
| 시작하기의 「다섯 절」 | 오늘의 쓰기 단계도 **다섯**이지만 **신원이 와이어프레임과 다르다**(아래 OPEN ①) | 팀장 판정 대기 |
| 「레일을 붙이면 배치 게이트가 자동으로 든다」(14A 인계) | **오늘은 서지 않는다** — 레일 축이 `page.goto('/')` 한 자리만 돌고 거기서 합성 마운트를 꽂는다. `/start` 의 출하 레일은 그 눈 밖이다 | 게이트가 **출하 레일 화면을 돈다**(모집단 넓히기 · 술어 불변) |
| M13 잔여 ⓗ 「홈 기본 상태가 게이트 밖」 | 맞다 — 홈 행이 측정 전에 `search(…)` 를 불러 **검색 결과 상태만** 잰다 | 두 상태로 넓힌다(하한은 올리기만) |
| 화면 모집단 등식(`journey-screen-table`) | 자리 이름 전수 **== 게이트 행 전수**라 행을 더하면 붉는다 | 행을 더하지 않고 **한 행 안에서 두 상태**를 돈다 |

## 착수 OPEN (팀장 판정 요청)

**① 시작하기 「번호 절 다섯」의 신원.** 두 읽기가 있다.
- (A) 오늘의 쓰기 단계 다섯(사업 정보 · 자격 사실 · 시공능력평가액 · 감시 금액대 · 감시 관심
  업종)에 번호와 상태 알약을 붙이고, G3 두 절은 번호 밖의 「준비 중」 묶음으로 남긴다.
- (B) **와이어프레임의 다섯 신원** 그대로 — 1 사업자번호 확인(준비 중) · 2 나라장터 준비 ·
  3 자격과 관심 조건(한 곳에서) · 4 알림 받는 곳(준비 중) · 5 지금 맞는 공고 미리보기. 쓰기 단계
  넷이 절 3 안에 묶인다.

**추천: (B).** 이 마일스톤의 목적이 IA 정렬이고 완료 조건 4 가 나란히 놓고 보는 것이다. **쓰기
단위는 바꾸지 않는다** — 절 3 안에서 기존 단계 버튼(`onboarding-step-*`)과 편집 세션 잠금을 그대로
쓰므로 여정 spec 의 앵커가 움직이지 않는다(실측: 그 spec 이 쓰는 앵커는 단계 버튼 · `onboarding-state-*` ·
`onboarding-prerequisite-*` · `onboarding-complete` · `onboarding-banner` 이고 전부 살아남는다).

**② 칩 줄에 「전체」를 더하는가.** 와이어프레임의 맨 왼쪽 칩이고, 뜻은 이미 있는 「조건 지우기」다.
**추천: 더한다** — 조건이 없을 때 눌린 얼굴로 서고 누르면 `clearAll` 이다. 새 질의 인자 0, 새
정책 상수 0. 「이번 주」·「1억~30억」 같은 **구간 프리셋은 더하지 않는다**(13E 가 적은 사유 그대로:
그 값이 화면에 사는 정책 상수가 되고 KST 날 경계 셈을 화면이 다시 짓는다).

**③ 카드의 「방식」 칸.** 응답에 필드가 없다. **추천: 칸을 그리지 않는다** — 「주어지지 않음」은
「응답은 왔고 그 칸이 비었다」는 뜻인데, 여기서는 **계약에 칸 자체가 없다**. 사유를 지어내 그리면
화면이 「서버가 방식을 빚졌다」고 주장하게 된다. 대조표 B 행의 처분에 「방식 = 데이터로 막힘」을
적어 닫는다.

**④ 머리 요약의 분자.** 와이어프레임은 「자격 통과 7건 중 **3건이 이번 주 마감**」이다. 분자는
**상한 안의 m 장**에서만 셀 수 있는데 분모는 `total` 이라, 상한을 넘은 계정에서 「n / total」이
거짓이 된다. **추천: 분자를 분모에 걸지 않는다** — 요약은 「감시 조건 통과 {total}건」이고, 마감이
가까운 수는 **상한 초과가 없을 때만**(`total === rows.length`) 「그 가운데 {n}건이 곧 마감」으로
덧붙인다. 「이번 주」의 경계 값은 표시층 상수 하나로 두고 짝 test 가 그 방향을 잠근다.

**⑤ 압축 금액이 닿는 자리.** `journey.spec` 이 `notice-amount` 를 **정확 금액 축어**로 단언한다
(`${won.toLocaleString('ko-KR')}원`). **추천: 그 노드를 건드리지 않는다** — 압축 꼴은 **모바일
압축 배치에서만** 서고 자기 testId 를 쥔다. 「정확 금액에 닿는 길」은 14A 의 2층 라벨
(`useTermHint`)로, 압축 글자를 누르면 아래층에 정확 금액이 선다. 그래서 기존 게이트 술어 변경 0.

**⑥ 터치 타깃 하한의 방향.** 카드 재배치가 홈·검색 상태의 계수를 **줄일 수 있다**(담기 버튼은
그대로지만 배치가 바뀐다). 구현 중 실측해 **줄면 멈추고 보고**한다. 홈 기본 상태는 **새 칸**이라
하한을 새로 세우는 것이고 내리는 것이 아니다.

## 승인 경계 (계약 「승인 경계」 절의 이 slice 몫)

- **기존 게이트 술어를 바꾸지 않는다.** 모집단을 **넓히는 것**(홈 두 상태 · 레일 축이 출하 화면을
  돈다)은 넓히기이고, `notice-amount`·칩 앵커·`onboarding-step-*` 의 **주장을 바꾸는 것**은 술어
  변경이라 멈추고 보고한다.
- **터치 타깃 하한의 계수가 줄어드는 변경**은 멈추고 보고한다.
- 서버·OpenAPI·`api/generated` 변경 0, 새 의존성 0, 흐름 **순서** 변경 0.
- 14A 원시 요소는 **더하는 쪽으로만** 넓힌다(기존 호출부가 그대로 컴파일 · 짝 test 를 같은 커밋에서).
  거동 변경은 팀장 판정 대상이다.
- 표시 계약의 표를 바꾸지 않는다.

## base 확인

`git rev-parse 056f405c` · `git merge-base --is-ancestor 056f405c HEAD` 를 착수 커밋에서 기계로
확인했다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 갱신 이력

- 2026-11-20 초판(착수 보고와 함께).
