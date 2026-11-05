# M9 / web-followups — commands

base `df4976cc` · 브랜치 `feature/m9-web-followups` · 판정 SHA `bb2f1bae`(마지막 산출물 커밋)

판정 SHA 는 라운드마다 뒤로 간다 — `50bc35c0`(첫 판) → `68834ea2`(PR #132 수정 라운드) →
`bb2f1bae`(표적 재검증 L-2). 「게이트」 표는 **지금 판정 SHA** 의 수치다.

로컬에서 돌리는 것은 **web 레인 게이트와 표적 변이뿐**이다(공통 규율) — 전건 Kotlin `check` ·
컨테이너 · S-20 · Playwright 여정은 **PR CI 가 정본**이고 같은 명령이다.

## 게이트 — `web` job 의 명령 그대로 (판정 SHA 에서)

| 번호 | 명령 | exit |
| --- | --- | --- |
| W-2 | `npm run lint` | 0 |
| W-3 | `npm run typecheck` | 0 |
| W-4 | `npm run gate:src-tree` | 0 |
| W-5 | `npm run api:types:check` | 0 |
| W-6 | `npm run test` | 0 — `39 files · 821 passed` |
| W-7 | `npm run gate:tests` | 0 — 등록부 기준 skipped/todo/failed 0 |
| W-8 | `npm run build` | 0 |
| W-9 | `npm run gate:boundary-canary` | 0 |

base 의 수치는 `38 files · 756 passed` 였다(PR #125 판정). 이 slice 가 **test 파일 하나와 단언
65 개**를 더한다(앞 라운드 54 + 수정 라운드 11).

## 항목별 RED 선행과 변이 — 전부 실측

TDD 의 RED 는 **구현 전**에, 변이는 **커밋 뒤**에 돌리고 변이 파일만 되돌렸다.

### ② KST 달력일 D-day

| 단계 | 결과 |
| --- | --- |
| RED 선행 | 경계 test 추가 뒤 `4 failed / 28 passed` — **기존 daysUntil 단언 다섯은 그대로 통과**했다(앞 판의 답과 갈리지 않는 입력이다) |
| 구현 뒤 | `32 passed` |
| 변이(바꿔치우기) | 달력일 차 → 앞 판의 24시간 나눗셈 + 내림·올림 | `4 failed` |
| 원복 | `git restore --source=HEAD -- <그 파일>` · porcelain 빈 출력 · `32 passed` |

### ① 명령 어휘 표

| 단계 | 결과 |
| --- | --- |
| RED 선행 | 표 test 추가 뒤 `6 failed / 11 passed` |
| 구현 뒤 | `17 passed`(표) · `15 passed`(DOM 게이트) · `49 passed`(화면 C) |
| 변이 | 타임라인의 배지 → `{entry.command}` 원문 보간 | `4 failed` — 금지 단언 · 음성 대조 둘 · 화면 C 단언 |
| 원복 | porcelain 빈 출력 · 다시 초록 |

### ④ 시계 lint 범위 — 변이 셋

| 단계 | 결과 |
| --- | --- |
| RED 선행 | 새 대조 추가 뒤 `9 failed / 22 passed`(앞 판에서 **아홉이 전부 초록**이었다는 실측) |
| 구현 뒤 | `31 passed` |
| 변이 A | 표면 목록을 `['Date']` 로 좁히고 제외 디렉터리 복원 | `4 failed` |
| 변이 B | 인자 없는 서식 호출 선택자 제거 | `6 failed`(A 가 못 잡던 `Intl`·`f.format()` 둘이 더해진다) |
| 변이 C | 시계 블록의 네트워크 전역 목록 제거(앞 판의 꼴) | `2 failed` — 맨 `fetch`·`WebSocket` |
| 원복 | 셋 다 porcelain 빈 출력 · `31 passed` |

변이 A 가 **`Intl` 축을 잡지 못한 것**이 측정의 요점이다 — 그 축은 두 층(표면 이름 · 인자 없는
호출)이 받고, 각 층을 따로 떼어 보아야 어느 층이 사는지 보인다.

### ③ 모집단 심볼 그래프 — 변이 둘

| 단계 | 결과 |
| --- | --- |
| 구현 뒤 | `14 passed` · 의무 집합은 **앞 판과 같은 다섯**(등식 그대로) |
| 변이 A | 해소 → 앞 판의 호출 식별자 이름 술어 | `3 failed` — 별칭 · 네임스페이스 · 지역 변수 |
| 측정 결함 발견 | 그 변이에서 **「재수출 경유」 대조가 초록**이었다(같은 이름으로 재수출하면 호출 지점의 글자가 선언과 같다) → 대조를 **개명 재수출**로 고쳐 커밋 |
| 변이 B | 시그니처 해소를 빼고 지역 심볼만 | `7 failed`(등식 포함) |
| 원복 | porcelain 빈 출력 · `14 passed` |

### ⑤ 교차언어 집합 등식 — 변이 셋

| 변이 | 결과 |
| --- | --- |
| Kotlin `Basis` 에 값 하나 추가 | `1 failed`(basis 행) — **앞 판에서는 web 이 어디서도 붉어지지 않았다** |
| web 표와 유니온에서 `AWARD` 를 **함께** 제거(`satisfies` 를 지나는 꼴) | `1 failed` — 같은 변이에서 `tsc` 도 둘을 낸다(두 층) |
| e2e 라벨 표에서 provenance 한 행 제거 | `1 failed` — 앞 판은 이 자리가 **아무 계약에도 묶이지 않았다** |

변이마다 원천 파일을 `git restore --source=HEAD` 로 되돌리고 porcelain 빈 출력을 확인했다
(Kotlin 파일은 다른 레인 소유라 변이 직후 되돌렸다).

## 캡처 재촬영

| 단계 | 결과 |
| --- | --- |
| jar | `~/.local/bin/bv-build.sh ./gradlew --no-daemon :app:bootJar` | BUILD SUCCESSFUL 31s |
| 촬영 | `npm run capture:usecase` | 12 장 · 합 1,009,304 바이트(상한 2MiB) |
| 바뀐 장 | 여덟(b-notices 둘 · c-worksheet 둘 · e-results 둘 · inbox 둘) · a-start 와 d-bid-box 는 바이트 동일 |
| 눈으로 확인 | 워크시트 타임라인의 명령 토큰 일곱이 **문면 배지**로 섰고, 마감 칸이 `D-7` → `D-8` 로 바뀌었다(같은 공고·같은 마감 — 달력일 차) |

촬영 뒤 `pgrep -af GradleDaemon` 으로 남은 daemon 둘을 확인했고 **둘 다 다른 레인 소유**였다
(daemon 로그의 작업 디렉터리로 귀속 — gates-tests · ops-code). 내 빌드는 `--no-daemon` 이고 남긴
daemon 이 없다. 캡처 하네스의 컨테이너도 남지 않았다(남은 `bidvector-v2-prod-*` 는 운영 스택이고
이 레인이 만들지 않았다).

## 호스트

무거운 작업(jar 빌드) 전 별도 호출로 쟀다 — available 15.6GB · swap free 5.4GB · 도는 Gradle 빌드
없음. 빌드는 `bv-build.sh` 의 자리를 지나 돌았다.

## PR #132 수정 라운드 — 일곱 자리

v `ready-for-review` @`50bc35c0` · cr `approve` @`7506085e`, 둘 다 blocker/high 0. 팀장 지시로
**아래 일곱만** 고치고 나머지는 경계 문장을 사실에 맞게 좁혀 등재했다(checklist §3·§5).
**캡처는 다시 뜨지 않았다** — 이 라운드가 바꾼 것은 `title` 속성·판정 함수·게이트 술어뿐이라
그려지는 글자가 하나도 바뀌지 않는다.

| 자리 | RED 선행 | 변이(바꿔치우기) | 결과 |
| --- | --- | --- | --- |
| ① 구조 분해 축 (v M-1 = cr M-1) | 대조 추가 뒤 2 failed | 그 선택자 제거 | `1 failed` |
| ② 빈 인자 서식 호출 (v M-2 = cr M-2) | 같은 2 failed 에 포함 | `undefined`·spread 꼴 제거 | `1 failed` |
| ③ Kotlin 읽기가 던진다 (v M-4 = cr L-2) | 대조 추가 뒤 4 failed | 못 읽는 조각을 다시 버림 | `1 failed` |
| ④ 훑기 재귀·`.ts` (v M-3 값싼 둘) | — (범위 확장, 의무 집합 불변) | 재귀 제거 / `.ts` 제외 | 각 `1 failed` |
| ⑤ D-day 설명 문면 (cr M-3) | 화면 test 추가 뒤 1 failed | 옛 문면으로 되돌림 | `1 failed` |
| ⑥a 옛 test 주장 (cr L-1) | — (주장만 고친다 · 단언 불변) | — | — |
| ⑥b `Object.hasOwn` (cr L-3) | 대조 추가 뒤 1 failed | 그 한 줄 제거 | `1 failed` |
| ⑦ `DateTimeFormat` 단독 대조 (v L-3) | — (앞 판에서 그 이름을 빼도 31 전부 초록이던 자리) | 목록에서 `DateTimeFormat` 제거 | `1 failed` |

변이는 전부 원복 뒤 `git status --porcelain` 빈 출력을 확인했다. ⑦ 은 **대조 자신이 변이로만
의미를 얻는 꼴**이라 RED 선행이 없다 — 그 대신 변이가 그 대조 하나만 깬다는 것을 쟀다.

### ② 에서 측정으로 가린 자리

인자 꼴 선택자를 `[arguments.0.name='undefined']` 만으로 쓰면 **첫 인자가 식별자가 아닌 모든
호출**에 걸린다(없는 경로를 읽은 값이 그 글자와 같다고 쳐진다). 실측 — `format(1200)` ·
`format(o.d)` · `format(new Date(1))` · 인자 0 개가 전부 1 회. `[arguments.0.type='Identifier']`
를 함께 두면 넷이 전부 0 이 된다. 그래서 선택자를 그 꼴로 적었다.

## 표적 재검증(v-132-r2)의 마지막 둘

| 자리 | 한 것 | 확인 |
| --- | --- | --- |
| L-2 고아 JSDoc | 앞 라운드가 설명 문면 상수와 그 JSDoc 을 **배지 컴포넌트의 JSDoc 과 함수 사이에** 끼워 넣어 기존 JSDoc 이 어느 선언에도 붙지 않았다. 상수를 그 JSDoc **앞**으로 옮긴다 | `lint` 0 · `typecheck` 0 · `test` 0(`39 files · 821 passed`) — 글자 변화가 없어 변이는 두지 않는다 |
| L-1 등재 | TS 문법으로 감싼 `undefined`(`as never` · `!` · `satisfies`)를 `void 0` 과 **같은 계열**로 §3 ③ 과 design-review §④ 표에 적는다 | 술어 추가 **없음** |

같은 꼴(연달아 선 JSDoc 둘)이 `src/screens/BidBoxScreen.tsx` 에도 하나 있는데 **base 의 자리**이고
이 브랜치의 변경 목록 밖이라 건드리지 않았다 — 그 파일을 만지는 slice 가 받는다.

### 등재만 한 것

v L-1·L-2 · cr L-4~L-9 · cr OQ. 그 가운데 cr L-5(오탐과 탈출 경로)는 **네트워크 쪽과 같은
자리**(선택자 ① 주석)에도 적었다 — 처방이 지목한 자리가 거기다. cr L-8 의 비용 실측은
checklist §3 ⑪ 에 수치로 남겼다.
