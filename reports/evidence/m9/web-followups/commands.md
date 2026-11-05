# M9 / web-followups — commands

base `df4976cc` · 브랜치 `feature/m9-web-followups` · 판정 SHA `50bc35c0`(마지막 산출물 커밋)

로컬에서 돌리는 것은 **web 레인 게이트와 표적 변이뿐**이다(공통 규율) — 전건 Kotlin `check` ·
컨테이너 · S-20 · Playwright 여정은 **PR CI 가 정본**이고 같은 명령이다.

## 게이트 — `web` job 의 명령 그대로 (판정 SHA 에서)

| 번호 | 명령 | exit |
| --- | --- | --- |
| W-2 | `npm run lint` | 0 |
| W-3 | `npm run typecheck` | 0 |
| W-4 | `npm run gate:src-tree` | 0 |
| W-5 | `npm run api:types:check` | 0 |
| W-6 | `npm run test` | 0 — `39 files · 810 passed` |
| W-7 | `npm run gate:tests` | 0 — 등록부 기준 skipped/todo/failed 0 |
| W-8 | `npm run build` | 0 |
| W-9 | `npm run gate:boundary-canary` | 0 |

base 의 수치는 `38 files · 756 passed` 였다(PR #125 판정). 이 slice 가 **test 파일 하나와 단언
54 개**를 더한다.

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
