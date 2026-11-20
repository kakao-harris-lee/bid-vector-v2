# M14 / wf-today-start (14B) — 실행과 실측

base `056f405c` · 산출물 마지막 커밋은 `rollback.md` 가 적는다.

## acceptance — CI `web` job 전건 (`web/` 에서)

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| W-2 | `npm run lint` | exit 0 |
| W-3 | `npm run typecheck` | exit 0 |
| W-4 | `npm run gate:src-tree` | exit 0 |
| W-5 | `npm run api:types:check` | exit 0 |
| W-6 | `npm run test` | exit 0 · 70 파일 1718 통과 |
| W-7 | `npm run gate:tests` | exit 0 |
| W-8 | `npm run build` | exit 0 |
| W-9 | `npm run gate:boundary-canary` | exit 0 |
| W-11 | `npm run test:e2e` | exit 0 · 39 통과 · 건너뜀 0 · spec 4/4(등록부와 같다) |
| W-12 | `./gradlew --no-daemon :app:bootJar` | exit 0 |
| W-13 | `npm run test:e2e:journey` | exit 0 · 22 통과 · 건너뜀 0 · spec 8/8(등록부와 같다) |

Kotlin 정적 게이트(`ktlintCheck detekt cpdCheck leakPatternGate`)의 결과는 **evidence 를 담은
HEAD 에서** 따로 잰다 — 그 명령의 스캔 뿌리에 이 디렉터리가 들어 경로가 닿는다. 마지막 HEAD 의
정본은 verifier 와 PR 조치 코멘트다.

## 착수 실측 (계약 행 ↔ 코드)

| 잰 것 | 값 | 어떻게 |
| --- | --- | --- |
| 카드의 「방식」 칸 | **스키마에 없다**(nullable 도 아니다) | `NoticeSummary`·`SuggestedNotice` 생성 타입의 속성 전수 |
| 제안의 전체 건수 | 화면이 **이미 읽는다** | 상한 초과 공시가 같은 값을 쓴다 |
| 칩 축 | 셋 전부 **서버 파라미터** | `FILTER_CHIPS` 의 `field` 가 `NoticeSearchQuery` 의 키다 |
| 압축 금액의 재사용 가능성 | **없다** — 무손실 병기에는 「약」도 내림도 없다 | 그 함수의 주석과 짝 test 의 주장 |
| 금액 어휘 키 증분 | **0** | 금지 목록이 표 **넷의 키**에서 유도된다(함수를 더해도 안 움직인다) |
| 배치 게이트가 출하 레일을 보는가 | **보지 않는다** | 그 spec 의 `goto` 인자가 `'/'` 하나 |
| 터치 게이트가 홈 기본 상태를 보는가 | **보지 않는다** | 측정 **전에** 검색을 걸어 검색 결과 상태만 쟀다 |

## 터치 타깃 계수 (390px · 여정 레인)

| 칸 | 하한 | 실측 | 비고 |
| --- | --- | --- | --- |
| `home/기본` | 10 | **12** | 신설 축(M13 잔여 ⓗ). 제안 수가 계정 조건에 매여 흔들리므로 실측보다 낮게 둔다 |
| `home/검색 결과` | 8 | **49** | 하한은 **앞 판 그대로**(내리지 않았고 올리지도 않았다 — 실측을 박으면 카드 속내마다 흔들린다) |
| 그 밖 여덟 칸 | 앞 판 그대로 | 통과 | 계수가 **줄어든 칸 0**(팀장 판정 ⑥ 의 보고 조건에 걸리지 않는다) |

## 변이 — 바꿔치운 것과 그 결과

전부 **커밋된 트리 위에서** 변이하고 `git checkout -- <파일>` 로 되돌렸다(porcelain 빈 출력을
앞뒤로 확인). 더하기만 한 변이는 두지 않았다 — 전부 **바꿔치우기**다.

| # | 무엇을 바꿔치웠나 | 돌린 것 | 결과 |
| --- | --- | --- | --- |
| M1 | 압축의 내림을 **올림**으로(`Math.ceil`) | `moneyDisplay.test.ts` | **RED** 4 실패 |
| M2 | 「약」 표지를 **늘 떼기** | `moneyDisplay.test.ts` | **RED** 4 실패 |
| M3 | 압축에서 **정확 금액 아래층을 제거** | `TodayNoticesScreen.test.tsx` | **RED** 1 실패 |
| M4 | 「곧」 경계 상수 7 → **0** | `TodayNoticesScreen.test.tsx` | **RED** 1 실패 |
| M5 | 머리 요약의 **상한 가드 제거**(분자를 늘 붙인다) | `TodayNoticesScreen.test.tsx` | **RED** 1 실패 |
| M6 | 「전체」 칩의 눌림을 **반전** | `TodayNoticesScreen.test.tsx` | **RED** 2 실패 |
| M7 | 검색 결과 카드의 자격 슬롯을 **비우기** | `TodayNoticesScreen.test.tsx` | **RED** 1 실패 |
| M8 | 준비 중 절에 **버튼을 심기** | `OnboardingScreen.test.tsx` | **RED** 3 실패 |
| M9 | 절 알약을 **늘 완료**로 | `OnboardingScreen.test.tsx` | **RED** 1 실패 |
| M10 | 레일 미리보기의 **상한 초과 공시 제거** | `OnboardingScreen.test.tsx` | **RED** 1 실패 |
| M11 | `/start` 의 **레일 표지 제거**(두 단을 `div` 로) | `layout.spec.ts`(스모크) | **RED** 2 실패 — 비공허 하한이 받았다 |
| M12 | 두 단 컨테이너를 **`flex-row-reverse`** 로 | `layout.spec.ts`(스모크) | **RED** 3 실패 — `/start: 레일이 본문 왼쪽이다` |
| M13 | 홈 **기본 상태에만** 서는 작은 컨트롤(24×24)을 심기 | `touch-target.spec.ts`(여정) | **RED** — `home/기본: 터치 타깃의 변이 44px 에 못 미친다` |

**M13 이 ⓗ 의 닫힘을 잰다.** 심은 컨트롤은 `searching === false` 가지 안에만 있으므로, 측정 전에
늘 검색을 걸던 **앞 판 게이트는 그것을 볼 수 없었다** — 새 칸이 아니면 이 변이는 초록이다.

**M12 의 자리**: 같은 변이가 합성 마운트 축에서도 붉지만, 실패 메시지가 `/start` 를 **이름으로**
가리키는 것이 이 slice 가 넓힌 축의 산출이다(출하 레일의 좌표를 실제로 잰다).

## 음성 대조

| 무엇 | 결과 |
| --- | --- |
| `useTermHint` 에 표지를 **주지 않으면** 앞과 같은가 | `data-testid` 가 없다(짝 test) |
| 금액이 없는 공고에 압축 글자를 지어내는가 | 자리 자체가 없다(짝 test) · 레일 줄도 같다 |
| 「약」이 **정확히 떨어지는** 금액에 서는가 | 서지 않는다(짝 test 가 두 방향을 같은 자리에서 잰다) |
| 가로 넘침 축을 레일 축에 **함께** 걸면 | `/bids` 가 **거짓으로** 붉는다(그 줄은 `overflow-x-auto` 다) — 그래서 걸지 않았다 |

## 캡처

와이어프레임 · 앞 · 뒤 — 데스크톱(1280) · 모바일(390). 경로는 `checklist.md` 가 적는다.
앞은 base `056f405c` 의 버릴 worktree에서 떴고(`cp -al` 로 `node_modules` 공유), 뒤는 산출물
트리에서 떴다. 캡처 산출물은 in_scope 밖이라 **복사한 뒤 트리를 되돌렸다**(`git status --porcelain`
빈 출력으로 확인).
