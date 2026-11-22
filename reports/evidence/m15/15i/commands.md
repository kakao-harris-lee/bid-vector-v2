# M15 / 15I — 실행 명령과 종료 코드

base: `ff8e0cad42c5c114ee30b42ca4318252bbf35c5f`

## ⓔ 착수 실측 — 후보 꼴이 오늘 무엇에 걸리는가

어휘를 여기 축어로 적지 않는다. 후보 꼴의 정본은 `config/quality/leak-patterns.txt` 의 마지막
줄이고 아래 명령들은 그 파일을 **참조형**으로 쓴다.

## 2026-11-23T05:45+09:00
- cmd: `git ls-files -z | xargs -0 grep -nEI '<후보 꼴>'`
- exit: 0
- 핵심 결과: 추적 파일 3403 중 매치 **20 건 · 파일 10 개**. 낱말 경계를 넣은 판과 넣지 않은
  판의 결과가 같다(차집합 0).

매치 20 건의 성격(전수, 파일별):

| 파일 | 건 | 무엇인가 |
| --- | --- | --- |
| `web/src/display/profileDisplay.test.ts` | 5 | 서식 함수의 고정물(받는 꼴·거부 꼴) |
| `web/src/screens/SettingsScreen.test.tsx` | 4 | 화면 test 의 입력 고정물 |
| `workflow/.../BusinessProfileValuesTest.kt` | 2 | 값 타입의 받는/거부 꼴 |
| `app/.../ProfileRequestsTest.kt` | 2 | 요청 조립 고정물(한 줄은 **거부되는 꼴** 표본) |
| `app/.../ProfileEndpointTest.kt` | 2 | 끝점 test 의 본문·부재 단언 |
| `adapters/.../BusinessProfileSchemaGuardTest.kt` | 1 | 스키마 가드의 거부 목록 |
| `web/src/screens/OnboardingScreen.test.tsx` | 1 | 「그 값이 서지 않는다」 단언 |
| `ml-engine/tests/evaluation/test_backtest_snapshot.py` | 1 | 스냅샷 고정물 |
| `reports/evidence/m15/15c/scope.md` | 1 | 두 꼴의 대조표(계약 문서) |
| `reports/evidence/m0/0c/commands.md` | 1 | 값 형태 표본 한 줄 |

**판정: 전부 합성이다** — 값 자체가 연속·반복 숫자이고 모든 자리가 test 고정물·거부 꼴 표본·
문서의 꼴 대조표다. 그래서 「멈추고 측정만 보고한다」가 아니라 **패턴을 더하고 합성 한 건을
등재한다**로 간다. 스캔 루트가 `reports/evidence/` 이고 `scope.md` 는 파일 이름으로 제외되므로,
이 패턴이 실제로 등재를 요구하는 것은 위 표의 **마지막 한 줄**뿐이다.

## 2026-11-23T05:47+09:00
- cmd: 게이트와 같은 셈(경로 + 정규화 줄 해시)으로 evidence 트리를 훑는 검산 스크립트 — ① 현
  패턴 파일 그대로 ② 후보 꼴 단독
- exit: 0
- 핵심 결과: ① `matches=301 · new=0`(게이트의 오늘 상태를 그대로 재현 — 셈이 맞다) ②
  `matches=1 · new=1`(등재해야 할 키 하나).

## ⓔ 게이트 실측 — 음성·양성 대조 (2026-11-23T05:51~05:54+09:00)

명령은 모두 `bv-build.sh ./gradlew --no-daemon --no-build-cache leakPatternGate` 하나다. 심음과
되돌림은 **파일 복사본**으로 했다.

| 상태 | exit | 핵심 결과 |
| --- | --- | --- |
| 패턴 1 줄 + 허용 1 줄 더한 그대로 | 0 | **음성 대조** — 등재한 합성 한 건이 통과한다 |
| 스캔 루트 안에 **실 꼴 번호 한 줄**을 심음 | 1 | **양성 대조** — 「허용 목록 밖 새 매치」로 잡힌다 |
| 심음 그대로 + **패턴 파일에서 새 줄만 뺌** | 0 | 붉음이 **새 패턴**에서 왔다 — 옛 여섯 줄로는 조용하다 |
| 심음 걷고 + **허용 목록에서 새 키만 뺌** | 1 | 등재가 **공허하지 않다** — 그 줄은 실제로 매치한다 |

`grep -rniEa -f config/quality/leak-patterns.txt <합성 probe>` → exit 0:
`tools/db-rehearsal.sh` 가 쓰는 **GNU grep ERE** 도 새 줄을 받는다(패턴 파일에 주석을 넣지 않는
규율이 그래서 유지된다 — `#` 줄은 그 자리에서 패턴이 된다).

## 변이(RED) 표 — 각 고침이 실제로 지탱하는가

| # | 축 | 변이(바꿔치움) | 측정 | 결과 |
| --- | --- | --- | --- | --- |
| ⓑ-1 | 술어가 속성을 **얻는다** | 술어의 훑는 속성 모집단에 다섯째를 더한다 | 여정 `settings.spec.ts` | **RED** — 집합 등식이 「모집단이 대조 목록과 갈렸다」로 멈춘다 |
| ⓑ-2 | 같은 변이의 **통제군** | ⓑ-1 + 새 집합 등식 단언만 걷는다 | 같은 spec | **GREEN** — 고침이 없으면 「얻는 쪽」이 통째로 조용하다(OPEN 행 ⓑ 의 실측 재현) |
| ⓒ-1 | 진단을 버린다 | 걸음 감싸개가 `detail` 을 `null` 로 돌려준다 | `journey-teardown.test.mjs` | **RED** — 10 중 3 실패 |
| ⓒ-2 | `cause` 사슬을 버린다 | 사슬 추적 상한을 1 로 내린다 | 같은 파일 | **RED** — 10 중 2 실패 |
| ⓓ-1 | 네 속성 중 **둘**이 모집단에서 빠진다 | 술어의 수집 줄이 두 속성을 빈 문자열로 접는다(상수는 그대로) | 여정 `settings.spec.ts` | **RED** — 한 실패에 **두 속성이 함께** 선다 |
| ⓓ-2 | 같은 변이의 **통제군** | ⓓ-1 + 단언을 루프 안으로 되돌린다 | 같은 spec | **RED 이되 첫 속성 하나만** 문면에 선다 — 둘째는 그 실행에서 미측정 |

변이는 전부 **파일 복사본에서 되돌렸고**(`git checkout --` 금지) 되돌린 뒤 `git status --porcelain`
이 빈 출력이다.

## acceptance — CI job 의 명령 그대로

`web` job(`working-directory: web`) — **전부 exit 0**:

| step | cmd | 핵심 결과 |
| --- | --- | --- |
| W-1~W-5 | `npm ci` · `npm run lint` · `npm run typecheck` · `npm run gate:src-tree` · `npm run api:types:check` | 경고 0 · 경계 전제 성립 · 생성 타입 바이트 등식 |
| W-6 | `npm run test` | 76 파일 · 2038 test 통과 |
| W-7 | `npm run gate:tests` | 등록부의 게이트 test 가 수집에 있고 전부 통과 |
| W-8·W-9 | `npm run build` · `npm run gate:boundary-canary` | 빌드 성공 · 번들 경계 활성 |
| W-10·W-11 | `npx playwright install chromium` · `npm run test:e2e` | 39 통과 · flaky 0 · 건너뜀 0 |
| W-12 | `./gradlew --no-daemon :app:bootJar` | 배포물 빌드(로컬은 `bv-build.sh` · `--no-build-cache`) |
| W-13 | `npm run test:e2e:journey` | 33 통과 · flaky 0 · 건너뜀 0 · spec 10/10 |

`check` job — `./gradlew --no-daemon check --continue` → **exit 0**,
`BUILD SUCCESSFUL in 37m 36s` · 433 task(394 실행) · `FAILED` 0.

`prettier` 는 이 저장소의 게이트가 아니다(의존에도 CI 에도 없다 — 포맷 축은 W-2). `container`
job 의 S-23c(`tools/db-rehearsal.sh`)는 같은 패턴 파일을 읽지만 로컬에서 돌리지 않았다 — 사유와
영향 판정은 `scope.md` 알려진 제한 4.

## 역방향 좌표 파급

## 2026-11-23T06:45+09:00
- cmd: 이 slice 가 편집한 파일 여덟의 stem 으로 `grep -rn '<stem>:[0-9]'`(문서·설정·소스 전수)
- exit: 0
- 핵심 결과: 추적 파일에서 **0 건**(걸린 둘은 `web/test-results/`·`web/playwright-report/` 로
  gitignore 대상이다). `capability-map.md` 를 가리키는 좌표 넷은 **전부 이 slice 가 편집한
  줄보다 위**이고, 그 편집은 **한 줄 치환**이라(파일 줄 수 4975 불변) 아래 좌표도 밀지 않는다.

## 2026-11-23T06:46+09:00
- cmd: `pgrep -af GradleDaemon` · `pgrep -af 'agent-a9ab6e9023fcafcd3'`
- exit: 0
- 핵심 결과: 이 레인의 잔존 프로세스 **0**. 남은 daemon 둘은 cwd·기동 시각이 **다른 레인**의
  것이라 멈추지 않았다(`./gradlew --stop` 은 쓰지 않는다).
