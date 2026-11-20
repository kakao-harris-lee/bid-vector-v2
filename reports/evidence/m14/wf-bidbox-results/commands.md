# M14 / wf-bidbox-results (14D) — 실행한 명령과 실측

판정 대상 SHA 는 freeze 보고가 정본이다. 아래는 **그 SHA 의 트리**에서 돌린 결과이고, evidence
편집 커밋이 뒤에 오므로 누출 패턴 게이트는 그 HEAD 에서 다시 쟀다(마지막 결과의 정본은 verifier
와 PR 조치 코멘트다).

## acceptance — ci.yml `web` job 전건

`web/` 에서 돌았다. 종료 코드는 **명령 자신의 것**을 읽었다(파이프 끝의 줄이 아니다).

| 단계 | 명령 | exit | 핵심 결과 한 줄 |
| --- | --- | --- | --- |
| W-1 | `npm ci` | 0 | 잠금 파일 고정판 설치 |
| W-2 | `npm run lint` | 0 | 경고 0(`--max-warnings 0`) |
| W-3 | `npm run typecheck` | 0 | 진단 0 |
| W-4 | `npm run gate:src-tree` | 0 | 호출 능력 간선 집합 == `{src/api/queries.ts}` 그대로 |
| W-5 | `npm run api:types:check` | 0 | 생성 타입 바이트 등식 성립(이 slice 는 계약을 건드리지 않는다) |
| W-6 | `npm run test` | 0 | 파일 71 · test **1735** 통과(착수 1733 + 이 slice 의 증분) |
| W-7 | `npm run gate:tests` | 0 | 등록부 전수가 수집에 있고 skipped/todo/failed 0 |
| W-8 | `npm run build` | 0 | 번들 생성 |
| W-9 | `npm run gate:boundary-canary` | 0 | 두 그래프에서 경계 플러그인이 fixture 실경로를 거부 |
| W-10 | `npx playwright install chromium` | 0 | — |
| W-11 | `npm run test:e2e` | 0 | 스모크 36 통과 · flaky 0 · spec 4/4(등록부와 같다) |
| W-12 | `./gradlew --no-daemon :app:bootJar` | 0 | 여정 레인의 입력 |
| W-13 | `npm run test:e2e:journey` | 0 | 여정 22 통과 · flaky 0 · spec 8/8(등록부와 같다) |

**W-7 의 첫 판이 한 번 붉었다**(재현되지 않음). `scripts/kotlin-read-guard.test.mjs` 가 전건
병렬 수집에서 1건 실패했고, **그 파일만** 돌리면 25/25 통과, `gate:tests` 를 다시 돌리면 exit 0
이었다. 그 test 는 Kotlin 원문을 읽는 축이라 이 slice 의 변경과 닿는 자리가 없다 — **이 레인이
만든 것이 아니며** 알려진 제한에 적었다.

## Kotlin 정적 게이트

web 전용 slice 라 전건 `check` 대신 이름으로 돌렸다(팀장 지시). `leakPatternGate` 가 evidence 를
스캔 뿌리에 들므로 **evidence 편집 커밋의 HEAD 에서** 다시 쟀다.

| 명령 | exit | 비고 |
| --- | --- | --- |
| `./gradlew --no-daemon ktlintCheck detekt cpdCheck leakPatternGate` | 0 | `leakPatternGate` 가 **실행**됐다(UP-TO-DATE 접미 없음) |

누출 어휘는 축어로 적지 않는다 — 참조형으로만 센다:
`grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m14/wf-bidbox-results/` → 0 건.

## 착수 실측 — 기준선

| 무엇 | 수 | 조건 |
| --- | --- | --- |
| `queries.ts` 의 operation 보유 최상위 선언 | 37 | 전부 호출이 **정확히 하나**(함수 → (메서드, 경로) 가 총함수) |
| 결과 화면이 닿는 모듈 / 질의 내보내기 / operation / **쓰기** | 27 / 8 / 4 / **0** | 착수 시점에 이미 0 — 이 게이트는 고치는 것이 아니라 **래칫**이다 |
| 다른 화면의 쓰기 도달(참고) | 입찰함 7 · 워크시트 7 · 홈 10 · 설정 9 · 시작하기 9 · 알림함 2 · 셸 24 | 정책이 `WRITES` 라 의무가 서지 않는다 |
| 와이어프레임 D 의 탭 ↔ 표시 계약의 표 | 8 ↔ 6 | 갈리는 자리 둘(「전체」가 표에 없다 · 「투찰 예정」이 접혀 있다) |
| `nextCheckpointKind` 어휘 ↔ 와이어프레임 레일의 사건 낱말 | 6 ↔ 5 | 「참가신청 마감」이 **열거 밖** — 그리지 않는다 |
| 터치 타깃 하한(입찰함 · 결과) | 11 · 7 | 탭이 늘고 버튼이 서므로 **올라간다**(내려가는 변경 0) |

## 변이 — 아홉 개, 전부 **바꿔치우기**

커밋 뒤에 심었고(미커밋 산출물을 함께 지우지 않도록), 심기 전 `git status --porcelain` 이 빈
출력이었다. 되돌림은 `git checkout -- <그 파일>` 이고 매번 porcelain 이 다시 비었다.

| # | 무엇을 바꿔치웠나 | 무엇이 붉었나 |
| --- | --- | --- |
| 1 | 결과 화면에 쓰기 질의 수입·사용을 심는다 | 새 게이트 4건 — `results … 가 쓰기 operation 에 닿는다: POST /api/bid-price-requests` |
| 2 | 새 게이트 test 를 vitest 수집에서 뺀다 | `gate:tests` exit **1** — 「수집에 없다 … 아무것도 막지 못한다」. **등록부 한 줄이 짐을 진다**는 실측 |
| 3 | 빈 자리 배지를 옛 문면으로 되돌린다 | 표시 test 1 + 화면 test 1 (13A OPEN ⓖ 의 회귀 자리) |
| 4 | `BID_PLANNED` 를 「투찰가 받음」에 도로 접는다 | 표시 test 2(빈 탭이 생기는 것까지 함께) |
| 5 | 레일의 사건 낱말을 지어낸 말로 바꾼다 | 표시 test 1 + 화면 test 1 — 열거 밖 낱말 |
| 6 | 상태 탭의 거르기를 지운다(늘 참) | **19건** — 상태 전수 루프가 전부 붉는다 |
| 7 | 「공고 행이 없다」를 부재 어휘로 접는다 | 화면 test 1 — 두 사실이 한 문면이 된다 |
| 8 | 두 축 병기를 `beside` 한 축으로 접는다 | 화면 test 1 + `tsc` 가 미사용 수입을 든다 |
| 9 | 없는 값 타일을 `0` 으로 그린다 | 화면 test 1 — 숫자 칸이 서면 안 되는 자리다 |

**통제군**: 아홉을 전부 되돌린 뒤 `npm run test` 가 exit 0 · 71 파일 · 1733 통과(그때의 전수).
결과 카드 머리를 고친 뒤의 전건은 1735 다.

**음성 대조**(게이트 test 안): 같은 자리에 **읽기** 질의를 심으면 **초록**이다. 이것이 없으면
「수입 한 줄을 더하면 무엇이든 붉는다」와 구별되지 않는다 — 붉음이 **쓰기 축**에서 왔음을 이
대조가 가른다. 같은 꼴로 `apiClient` 직접 수입도 `GET` 이면 초록이다.

**공허 대조**: 모집단이 비거나 자리 표의 지정자가 해소되지 않으면 **던진다**(조용한 초록 0).

## 캡처

`npm run capture:usecase` 를 **앞**(base `056f405c` 의 버릴 worktree)과 **뒤**(판정 트리)에서
각각 돌렸다. 도구가 `docs/usecase/captures/` 를 덮으므로 찍은 뒤 레인 scratchpad 로 복사하고
`git checkout -- docs/usecase/captures/` 로 트리를 되돌렸다 — 그 산출물은 **커밋하지 않는다**.
앞 worktree 는 측정 뒤 제거했다.
