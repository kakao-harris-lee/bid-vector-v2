# M14 / wf-shell (14A) — slice 계약

```yaml
milestone: M14
slice: wf-shell
base_sha: af6d9c0f4c0d83469ecbb1314eb6d30848f485b2   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/src/index.css                      # ① 캔버스 팔레트 토큰(라이트·다크) — 어조 다섯 쌍은 값만
  - web/src/AppShell.tsx                   # ② md 이상 왼쪽 사이드바(항목 여섯 · 배지 둘 · 사업자명) · md 미만 하단 탭 다섯 칸 유지
  - web/src/routes.tsx                     # 셸이 읽는 자리 표(차례 축은 팀장 판정 대기 — 아래 OPEN ①)
  - web/src/components/                    # ③ 배치 원시 요소 신설(PageHeader·TwoColumn·SummaryTile·StatusPill·KeyValueRow) + 짝 test
  - web/src/display/timeDisplay.ts         # ④ 부재값 어휘 — ABSENT_INSTANT 폐기, 사유를 지닌 타입
  - web/src/display/noticeDisplay.ts       # ④ ABSENT_AMOUNT 폐기
  - web/src/display/absence.ts             # ④ 부재 사유 타입의 **한 자리**(신설)
  - web/src/screens/InstantText.tsx        # ④ 부재 얼굴을 그 타입에서 받는다
  - web/src/components/StateBadge.tsx      # ④ Field 의 부재 자리 · M13 잔여 ⓖ(용어 힌트 터치 경로)
  - web/src/screens/NoticeCard.tsx         # ④ 문면만(배치는 14B)
  - web/src/screens/WorksheetGrounds.tsx   # ④ 문면만(배치는 14C)
  - web/src/screens/WorksheetRecord.tsx    # ④ 문면만(배치는 14C)
  - web/src/screens/ResultCard.tsx         # ④ 문면만(배치는 14D)
  - web/src/screens/InboxScreen.tsx        # ④ 문면만 + 「응답 전」이 「없다」와 다른 값임을 지킨다
  - web/src/screens/strategyEdit.tsx       # ④ 주석 안의 옛 어휘 한 줄(착수 실측이 찾은 모집단 밖 자리)
  - web/src/api/queries.ts                 # 셸 배지·사업자명이 **기존** 함수를 쓴다(새 호출 0 — 아래 실측)
  - web/src/api/queryKeys.ts               # 셸 배지 둘의 질의 키
  - web/e2e/journey/                       # ⑤ 배치 단언 spec 신설 · 터치 타깃 가로 축(M13 잔여 ⓑ)
  - web/e2e/harness/                       # ⑤ 배치 단언·대비 게이트의 순수 술어와 그 짝 test
  - web/scripts/                           # ⑤ 대비 게이트(index.css 파싱) · 부재값 전수 grep 게이트
  - web/config/gate-tests.txt              # 새 vitest 게이트 test 를 **만드는 커밋에서** 등재
  - web/config/journey-specs.txt           # 새 여정 spec 을 같은 커밋에서 등재
  - web/src/**/*.test.ts(x)                # ④ 사전 승인된 예외 — 등록부 test 일곱의 **주장**을 사유 문면으로
  - web/e2e/journey/shared.ts              # ④ 여정 공유 상수 하나
  - web/e2e/journey/onboarding.spec.ts     # ④ 사전 승인된 예외 — 여정 spec 둘의 주장
  - web/e2e/journey/settings.spec.ts       # ④ 같음
  - docs/discovery/capability-map.md       # §12.3 하위 절 하나 — 글자는 머지 차례에 정한다
  - reports/evidence/m14/wf-shell/
out_of_scope:
  - web/src/screens/TodayNoticesScreen.tsx 의 배치·칩·머리 요약   # 14B
  - web/src/screens/OnboardingScreen.tsx                        # 14B
  - web/src/screens/Worksheet*.tsx 의 배치·레일·절 순서          # 14C
  - web/src/screens/BidBox*.tsx · Result*.tsx 의 배치·타일·탭    # 14D
  - web/src/display/moneyDisplay.ts 의 압축 금액 꼴              # 14B
  - web/src/api/generated/** · openapi/** · Kotlin               # 14E(그 「값 없음」 한 건은 14E 몫 · 승인 경계)
  - package.json 의 dependencies                                 # 새 의존성 0(서체는 설치된 것에만 기댄다)
  - 자체 호스팅 서체 자산                                        # 착수 판정 「별도 결정」
  - .github/workflows/ci.yml                                     # acceptance 는 오늘의 web job 명령 그대로
  - adapters/** · app/** · ml-engine/**                          # 이 slice 는 web 만 만진다
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # evidence 가 누출 패턴 게이트의 스캔 뿌리에 들어 경로가 닿는다 — evidence 편집 커밋의 HEAD 에서
rollback: rollback.md
```

## 받는 항목과 처분

| 항목 | 이 slice 의 처분 |
| --- | --- |
| 공통 셸(대조표 1행) | **정렬 + 이탈 하나** — md 이상 왼쪽 사이드바, 항목 **여섯**(시작하기 포함), 배지 둘(입찰함 = REAL 계수 · 알림함), 아래 사업자명. md 미만은 13B 의 다섯 칸 그대로 |
| 배치(대조표 2행) | **원시 요소만 정렬** — `TwoColumn` 이 본문 + 300px 레일을 세우고 md 미만에서 레일이 본문 **뒤**. 각 화면의 적용은 14B·14C·14D |
| 알림함 · 설정 · 없는 주소(대조표 7행) | **셸·카드 문법만 따른다** — 이 slice 의 토큰·원시 요소가 그 문법이다 |
| 부재값 어휘(14A ④) | **닫힘(측정)** — 좁힌 전수 grep 이 0. 「아직 모른다(적재 중)」는 「없다」와 **다른 값**으로 남는다 |
| M13 잔여 ⓑ 배지 버튼의 가로 축 | 터치 타깃 게이트에 **가로 축**을 더한다(하한은 올리기만) |
| M13 잔여 ⓖ 용어 힌트의 터치 경로 | `Field` 의 용어 줄이 속성 하나로만 말하지 않게 한다 |

처분의 **근거와 잔여**는 `checklist.md` 가 정본이다. 착수 실측의 수치와 그것을 낸 조건은
`commands.md` 가 정본이고 여기 옮겨 적지 않는다.

## 착수 재실측 (계약 행 ↔ 코드)

| 계약이 말한 것 | 착수 실측 | 처분 |
| --- | --- | --- |
| 부재값 전수 35 · 생성 타입 제외 34 · 제외분 1 | **같다** | 기준선 확정 |
| 모집단 = 상수 넷 · 호출부 다섯 · 여정 공유 하나 · 등록부 test 일곱 · 여정 spec 둘 | **한 자리 더 있다** — `screens/strategyEdit.tsx` 의 주석 한 줄 | in_scope 에 등재(열거가 아니라 grep 이 acceptance 이므로 계약 위반이 아니다) |
| 사이드바 배지 = `listBidCases` REAL 계수 | 그 함수는 **`kind` 필수 · 쪽 인자 없음 · 배열 반환**이라 계수는 길이다 | 정렬 |
| 셸이 `listBidCases`·`readProfile` 을 부르면 승인 쌍 등식이 움직이는가 | **움직이지 않는다** — 등식은 `queries.ts` 의 호출 집합 대 `APPROVED_OPERATIONS` 이고 두 쌍 모두 이미 등재돼 있다. 셸은 기존 함수를 부른다 | 증분 0 |
| 사업자명의 원천 | `Profile.tradeName`(null = 미설정). `GET /api/profile` 은 승인 쌍에 있다 | 정렬 |
| 터치 타깃 게이트가 가로를 재는가 | **재지 않는다** — 측정 함수가 `width` 를 내지만 단언은 `height` 하나다 | M13 잔여 ⓑ 의 자리 확인 |
| 캔버스 팔레트가 AA 를 넘는가 | **셋이 못 미친다**(아래 OPEN ②) | 팀장 판정 대기 |

## 착수 OPEN (팀장 판정 요청)

**① 사이드바에서 「시작하기」의 차례.** 계약은 맨 아래로 내리는 것을 허용하되, 자리 표가 차례를
배열 순서 하나로 들어 **모바일 탭 차례도 함께 움직인다**고 적었다. **추천: 내리지 않는다** —
배열 순서를 그대로 두면 새 축이 0 이고 13B·13E 가 잠근 모바일 탭 차례가 움직이지 않는다. 내리기로
하면 **셸(md 이상)만 읽는 둘째 축**을 더해 모바일을 고정한다.

**② 캔버스 팔레트의 대비 미달 셋.** 계산한 값은 `commands.md` 가 정본이고, 여기에는 처분만 적는다.
어조 둘(좋음 · 미판정)의 글자색은 **한 단 내리면** AA 를 넘는다(M13 CR L-1 이 `absent` 에 한 같은
처분). 남는 것은 **카드 경계선**이다 — 새 팔레트는 카드와 바탕이 서로 1.09:1 이라 그 둘을 가르는
것이 선 하나인데, 캔버스의 선 값은 비텍스트 기준 3:1 에 크게 못 미친다. **추천: 선을 3:1 까지
내리고 그 값을 캔버스 팔레트 안의 더 어두운 회색에서 고른다**(새 색을 지어내지 않는다). 카드
**안**의 가름선은 양쪽이 같은 면이라 별 토큰으로 두고 재지 않는다 — 와이어프레임도 그 둘을 다른
값으로 선언한다.

## 승인 경계 (계약 「승인 경계」 절의 이 slice 몫)

- **부재값 test 아홉의 주장 변경**은 계약이 미리 승인했다(14A ④). 그 밖의 **기존 게이트 술어**
  변경은 멈추고 보고한다.
- **터치 타깃 하한의 계수가 줄어드는 변경**은 멈추고 보고한다. 가로 축을 더하는 것은 **올리기**다.
- 서버·OpenAPI·`api/generated` 변경 0, 새 의존성 0, 흐름 순서 변경 0.
- 다른 slice 소유 파일(`NoticeCard`·`Worksheet*`·`ResultCard`)은 **문면만** 고친다. 배치는 그 소유
  slice 의 일이고, 뒤 레인은 이 문면을 되돌리지 않는다.
- 표시 계약의 표를 바꾸지 않는다.

## base 확인

`git rev-parse af6d9c0f` · `git merge-base --is-ancestor af6d9c0f HEAD` 를 착수 커밋에서 기계로
확인했다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 갱신 이력

- 2026-11-20 초판(착수 보고와 함께).
