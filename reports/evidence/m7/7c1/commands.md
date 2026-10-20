# M7/7C① — 실행 명령과 종료 코드

acceptance 는 `.github/workflows/ci.yml` 의 `check` job 과 `web` job 이 돌리는 명령 **그대로**다.
두 job 의 명령 전부(그리고 `ml-engine` job)를 한 번에 돌리는 자리가 `check` job 자신의 step
`./tools/one-command-check.sh`(S-20)이라 그 하나를 돌려 두 job 을 함께 잰다 — 그 스크립트의 머리
주석이 「세 job 의 명령 그대로, CI 전용 step 만 뺀다」를 선언한다.

호스트 규율: 모든 Gradle·Testcontainers·npm 명령은 `flock ~/.local/bid-vector-build.lock <명령>`
으로 감쌌다(레인 넷 병렬). 점검 셋(`pgrep`·`free -m`·`ps --sort=-rss`)은 무거운 빌드를 **시작하기
전에 별도 호출**로 돌렸고, 시작 시점마다 available > 6GB · swap free > 2GB 였다. 아래 acceptance 는
다른 레인이 lock 을 쥐고 있어 **큐에 걸린 뒤 lock 이 풀리는 시점에 시작**했다 — 직렬화가 그 시점의
여유를 만든다(그 레인의 JVM 이 먼저 끝난다). 00:30~03:00 창에는 시작하지 않았다.

## 2026-10-15T22:40Z — RED 먼저 (거부 규칙표를 빈 목록으로 바꿔치움)

- cmd: `flock … ./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.notice.NoticeSearchParametersTest'`
- exit: 1
- 핵심 결과: 11 tests, 4 failed — 상한 초과 size · 역전 범위 · 긴 텍스트 · 형식 오류 넷이 RED

## 2026-10-15T22:43Z — 복원 뒤 GREEN

- cmd: 같은 명령
- exit: 0
- 핵심 결과: 11 tests, 0 failed (복원은 사본에서 했고 그 파일의 `git diff --numstat` 이 빈 출력)

## 2026-10-15T22:48Z — 어댑터 test(실 PostgreSQL)

- cmd: `flock … ./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.notice.*'`
- exit: 0
- 핵심 결과: 21 tests(9 + 12), 0 failed — test 결과 XML 의 `tests`/`failures` 속성으로 재확인

## 2026-10-15T22:55Z — 생성 타입 재생성

- cmd: `flock … npm ci` (web)
- exit: 0
- 핵심 결과: 잠금 파일 고정판 설치
- cmd: `flock … npm run api:types` (web)
- exit: 0
- 핵심 결과: 생성물 +107줄(새 operation·새 스키마), 저장소 생성기 그대로

## 2026-10-15T22:58Z — app test

- cmd: `flock … ./gradlew --no-daemon :app:test --tests 'bidvector.app.http.NoticeSearchEndpointTest' --tests 'bidvector.app.http.OpenApiNoticeSearchContractTest' --tests 'bidvector.app.http.OperatorAuthenticationTest' --tests 'bidvector.app.http.OpenApiContractTest'`
- exit: 0
- 핵심 결과: 27 tests(8 + 6 + 4 + 9), 0 failed

## 2026-10-15T23:00Z — 변이 아홉 (각각 적용 → 표적 재실행 → 사본 복원)

적용·복원마다 `git diff --numstat` 을 찍었다: 적용 직후 **삭제 줄 ≥ 1**(더하기만 한 변이가 아니라
바꿔치우기), 복원 직후 **빈 출력**. 복원은 사본에서 했고 `git checkout --` 을 쓰지 않았다.
대응표는 `checklist.md` 의 「우회 ↔ test ↔ 변이」 절에 있다.

- cmd: 변이별 `flock … ./gradlew --no-daemon :adapters:test …` (다섯) · `flock … ./gradlew --no-daemon :app:test …` (넷)
- exit: 아홉 전부 1
- 핵심 결과: 아홉 변이가 각자 의도한 test 를 RED 로 만들었고(무관한 test 는 초록) 복원 뒤 전부 GREEN

## 2026-10-15T23:12Z — 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 산출물 경로들> reports/evidence/m7/7c1/`
- exit: 0
- 핵심 결과: **비밀값 0**. 스캔 경로는 이 slice 가 만진 산출물 경로 전부와
  `reports/evidence/m7/7c1/` 다. 패턴에 걸리는 줄은 있고 전부 **값이 아니라 낱말**이다 —
  파일 다섯에 열 줄이며 그 가운데 **이 slice 가 추가한 줄은 셋**이다(verifier r1 L-2 가
  「새 코드 0건」 오기를 지적했다): 실 DB E2E test 의 컨테이너 자격 설정 둘(기존 E2E test
  들과 같은 고정 문구이고 운영 값이 아니다)과 전략 응답 KDoc 이 인용한 함수 이름 하나.
  나머지 일곱은 이 slice 가 만들지 않은 기존 줄이다(계약 문서의 보안 스킴 선언 · 비교 구조
  게이트 · 게이트 계약의 비밀값 절). `leakPatternGate` 의 스캔 루트는 `reports/evidence`
  라 그 줄들은 게이트 대상 밖이고, **evidence 안에서는 0건**이다. 패턴 어휘는 이 문서에
  축어로 적지 않는다 — 참조형으로만 실행한다. 육안 확인: 응답 스키마에 연락처·사업자 정보
  칸이 없고 공고 요약은 공개 공고 필드뿐이다.

## 2026-10-15T23:13Z — clean-tree 게이트

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자 19개>`
- exit: 0
- 핵심 결과: 빈 출력. **양성 대조 1회** — in_scope 파일 하나의 끝에 줄을 하나 붙이니 같은 명령이
  그 파일을 ` M` 으로 잡았고, 심기 전 사본에서 `head -n <원래 줄수>` 로 절삭 복원해 다시 빈 출력이
  됐다(비파괴, `git checkout --` 미사용).

## 2026-10-16T00:53Z — acceptance: `check` job + `web` job (마지막 산출물 커밋에서)

- cmd: `flock … ./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: 단계 24 전부 통과 — Kotlin `check`(게이트 전건) · `qualityBaseline` · Python
  S-1~S-11(1605 passed) · web W-1~W-11(생성 타입 바이트 등식 통과 · 게이트 test 실행 확인 ·
  번들 경계 카나리 · Playwright 스모크 2 passed). 스크립트 마지막 줄이 「Kotlin 전건 + Python
  전건 + web 전건 통과」다.

### 그 앞 라운드에서 전건이 잡은 것 (수정 뒤 위 결과)

전건을 돌리지 않았으면 지나갔을 것들이고, 전부 **억제 없이 구조로** 고쳤다(각각 별도 커밋).
- `adapters.persistence` 의 `bidvector.workflow` 참조 금지 — 검색 어댑터를 자기 패키지로
  옮기고 그 패키지의 의존 게이트를 신설했다(금지를 넓히지 않았다).
- `app.http` 의존 허용 목록에 조회기 누락 · 면제 수 고정값 · `Intrinsics.areEqual` 허용 목록 ·
  KDoc 자리(ktlint) · 파일당 함수 수와 반환 수(detekt).

## 2026-10-16T01:15Z — rollback ①~⑥ (임시 clone, 되돌린 트리)

- cmd: `flock … ./gradlew --no-daemon check` (되돌린 트리에서)
- exit: 0
- 핵심 결과: 되돌린 트리가 컴파일·test·게이트 전부 통과(④⑤⑥ 한 명령이 셋을 든다).
  ①②③ 과 보존 계수는 `rollback.md`.

## r2 수정 라운드 (verifier r1 F-1·F-2 · low · 장부)

### 2026-10-16T18:20Z — 변이 여섯 (적용 → 표적 재실행 → 사본 복원)

적용 직후 `git diff --numstat` 의 삭제 줄 ≥ 1, 복원 직후 빈 출력. 대응표는 `checklist.md`.

- cmd: 변이별 `flock … ./gradlew --no-daemon :adapters:test …` (셋) · `:workflow:test …` (둘) · `:app:test …` (하나)
- exit: 여섯 전부 1
- 핵심 결과: 읽기 전용 문장·호출, 제어 문자 규칙, 이스케이프 글자, 반복 판정, 정수 술어를
  각각 바꿔치우니 의도한 test 가 RED 가 됐다(복원 뒤 전부 GREEN).

### 2026-10-16T18:50Z — acceptance: `check` job + `web` job (마지막 산출물 커밋 `431b2ad3`)

- cmd: `flock … ./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: 단계 24 전부 통과 — Kotlin `check` 게이트 전건 · Python 1605 passed · web
  W-1~W-11(생성 타입 바이트 등식 통과 · 게이트 test 실행 확인 · Playwright 2 passed).

### 2026-10-16T18:54Z — `container` job (L-1, 같은 커밋)

- cmd: ci.yml `container` job 의 **run 블록 16 개를 파일에서 꺼내 순서대로** 실행(`uses:` 셋은
  CI 전용이라 제외, job 수준 `env:` 와 `$GITHUB_ENV`·`$GITHUB_PATH` 는 러너와 같게 흉내냈다).
  compose 프로젝트 이름만 레인용으로 갈랐다(`COMPOSE_PROJECT_NAME`, 6B-4 선례).
- exit: 0
- 핵심 결과: `TOTAL steps=16 failed=0` — 이미지 둘 빌드 · 위생·취약점 게이트 · 거부 스모크 ·
  healthy 수렴 · 컨테이너 스모크(dry-run 왕복, 쓰기 0) · 백업·복원 리허설 · 실 서버 교차언어 ·
  정리 뒤 이 레인 이름의 컨테이너 0 · 볼륨 0. verifier r1 A3 이 앞 산출물 SHA 에서 같은
  결과(`failed=0`)를 냈고, 이 줄은 **마지막 산출물 커밋에서 레인이 다시 잰 것**이다.

### 2026-10-16T18:56Z — rollback ①~⑥ 재실측

- cmd: 임시 clone 에서 복원 명령 + `flock … ./gradlew --no-daemon check`
- exit: 둘 다 0
- 핵심 결과: 복원 뒤 상태 계수가 그 시점 기계 목록과 일치 · base 대비 diff 빈 출력 · 되돌린
  트리 전건 초록. **수는 `rollback.md` 가 갖는다**(D-7C1-4 ⓑ — 이 문서는 수를 적지 않는다).

### 전건이 r2 에서 잡은 것 (수정 뒤 위 결과)

- 반복 매개변수 판정이 app 모듈에 **새 바깥 패키지**를 들여 전송 표면 게이트가 붉었다 →
  이미 쓰는 서블릿 타입으로 같은 사실을 읽게 바꿨다(게이트 허용 집합을 넓히지 않았다).
- 제어 문자 거부가 서면서 문면 등식 test 의 `U+0000` 표본이 조건 조립까지 오지 못했다 →
  표본을 이스케이프 글자가 섞인 값으로 바꿨다.
- ktlint 둘(규칙표 람다 형식 · test import 순서).

## r2 후속 라운드 (verifier r2 R2-M-1 · R2-M-2) — `1346cbc7` · `28ff8e19` · `91c14e9d`

이 라운드는 **레인 교대로 명령 기록이 남지 않았다**(세션이 끊겨 다른 레인이 이어받았다).
그 HEAD 의 측정을 지어내지 않고 **verifier r3 의 것을 가리킨다** — `91c14e9d` 에서
`./tools/one-command-check.sh` exit 0(Kotlin `check` BUILD SUCCESSFUL · pytest 1605 passed ·
생성 타입 바이트 등식 · vitest 17 · Playwright 2)와 `container` job 16 run 블록 `failed=0` 이다.

- `1346cbc7` — R2-M-2: 잘못된 퍼센트 인코딩을 400 으로(원인 사슬 술어 + 폴백 갈래).
- `28ff8e19` — R2-M-1: 기록 프록시로 「검색의 모든 문장이 읽기 전용 트랜잭션 안에서 돈다」를 잠금.
- `91c14e9d` — ktlint(`when` 분기 중괄호).

verifier r3 가 이 셋을 대상으로 변이 열(a·b·c·c2·x·y·z·w·m1~m4)을 쟀고, 그 가운데 y·z·w 가
초록이어서 R3-L-1 로, 이스케이프 문법 오류가 500 이어서 R3-M-1 로 남았다.

## r3 후속 라운드 (verifier r3 R3-M-1 · R3-L-1 · R3-L-4) — `00e6544d` · `487e24ec` · `9820c508` · `8bcb3ee7` · `2705d828`

뒤 둘(`8bcb3ee7`·`2705d828`)은 **R3-M-1 의 재설계와 그 뒷마무리**다 — 전건 `check` 가 첫 구현을
**두 번** 되돌렸고(아래 04:38·05:55 줄), 그때마다 게이트를 넓히는 대신 설계를 바꿨다. 아래
04:0x 줄들은 첫 구현 시점의 측정이고, 05:45·06:05 줄이 같은 표적을 다시 잰다.

### 2026-10-19T03:56Z — RED 먼저 (R3-M-1, 수정 전 HEAD)

- cmd: `flock … ./gradlew --no-daemon :app:test --tests '…NoticeSearchEndpointTest' --rerun`
- exit: 1
- 핵심 결과: 15 tests, 1 failed — 새 test 가 `?q=%` 에서 `expected:<400> but was:<500>`.
  이 꼴은 `URI.create` 가 먼저 거부하므로 소켓으로 원문을 보내야 서버까지 간다(그래서 test
  하네스에 소켓 GET 을 하나 뒀다).

### 2026-10-19T04:03Z — GREEN (첫 구현: 원인 사슬 술어)

- cmd: 같은 명령
- exit: 0
- 핵심 결과: 15 tests, 0 failed. 첫 구현은 원인 사슬에서 **디코딩 실패 계열 둘**을 보는
  것이었고, 두 부류가 모두 400 이 됐다.

### 2026-10-19T04:38Z — 전건이 그 구현을 되돌렸다 (설계가 바뀐 자리)

- cmd: `flock … ./tools/one-command-check.sh`
- exit: **1**
- 핵심 결과: 전송 표면 게이트가 붉었다 — `bidvector.app.http.ErrorBodyKt -> java.io`(모듈별
  바깥 참조는 허용 패키지 집합 안뿐, 기본 거부). 디코딩 실패 계열 하나가 `java.io` 에 살아서
  HTTP 층이 그 **이름**을 들 수 없었다. 허용 패키지에 `java.io` 를 더하는 길이 있었지만 그러면
  app 모듈 전체가 그 패키지를 참조할 수 있게 된다 — 게이트를 넓히는 대신 **읽는 자리를 옮겼다**
  (verifier r3 가 제시한 첫 처방: 「매개변수 접근을 한 자리로 모아 그 자리의 실패를 400 으로」).
  바뀐 뒤에는 바깥 참조가 0 이고, 전역 오류 경로는 r2 이전으로 돌아갔으며(폴백은 다시 500 만
  낸다), 거부는 이 operation 하나에만 닿는다.

### 2026-10-19T04:08Z — 실 DB E2E(출하 조립 + 실 PostgreSQL)

- cmd: `flock … ./gradlew --no-daemon :app:test --tests '…NoticeSearchProductionE2ETest' --rerun`
- exit: 0
- 핵심 결과: 6 tests, 0 failed — 두 부류 11 꼴이 모두 400 이고 **감사 행 11 개가 전부 400** 이다
  (앞 판은 문법 꼴이 500 이었고 감사 행도 500 이었다).

### 2026-10-19T04:12Z — 어댑터 test(R3-L-1) · 두 모듈 lint

- cmd: `flock … ./gradlew --no-daemon :adapters:test --tests '…JdbcNoticeSearchTest' --rerun :app:ktlintCheck :adapters:ktlintCheck :app:detekt :adapters:detekt`
- exit: 0
- 핵심 결과: 17 tests, 0 failed. 처음에는 ktlint 가 붉었고(여러 줄 `when` 분기 사이 빈 줄),
  억제 없이 형식을 고쳐 다시 초록이 됐다. 음성 대조의 쓰기를 `status` 변경으로 두었을 때는
  `guard_notice_status` 가 거부해 **막힌 쓰기**가 됐다 — 우회 재현이 되지 않으므로 값을 바꾸지
  않는 쓰기로 바꿨다(그 트리거의 존재 자체가 이 자리의 측정이다).

### 2026-10-19T04:20Z — 생성 타입 재생성

- cmd: `flock … npm run api:types` (web)
- exit: 0
- 핵심 결과: 생성물 1 줄(400 description) — OpenAPI 문면 변경과 바이트 등식.

### 2026-10-19T05:45Z — 재설계 뒤 표적 재실행

- cmd: `flock … ./gradlew --no-daemon :app:test --tests '…NoticeSearchEndpointTest' --tests
  '…NoticeSearchProductionE2ETest' --tests '…OpenApiNoticeSearchContractTest' --tests
  '…TransportSurfaceGateTest' --rerun :app:ktlintCheck :app:detekt`
- exit: 0
- 핵심 결과: 15 + 6 + 6 + 19 tests, 0 failed. **전송 표면 게이트를 표적에 넣었다** — 이 라운드의
  설계를 되돌린 게이트라서 표적 재검증 대상이다. 중간에 detekt `SwallowedException` 이 한 번
  붉었고, 잡은 실패를 거부에 **원인으로 달아** 억제 없이 고쳤다(응답에는 닿지 않는다).

### 2026-10-19T05:55Z — 전건이 그 원인 전달 방식도 되돌렸다

- cmd: `flock … ./tools/one-command-check.sh`
- exit: **1**
- 핵심 결과: 원인을 **생성자 매개변수**로 받자 주입 표면 게이트 둘이 붉었다 —
  「`InvalidNoticeSearchException` 이 주입받는다 → `java.lang.Throwable`」과 주입 표면 집합
  등식. 그 게이트는 app class 의 **생성자 매개변수**를 주입 표면으로 읽으므로, 예외의 cause 도
  주입으로 보인다. 주입 허용 목록에 `Throwable` 을 올리는 대신 **`initCause` 로 달았다** —
  생성자는 문구 하나만 받고, 게이트 계약은 여전히 한 줄도 넓히지 않았다.

### 2026-10-19T06:05Z — 그 수정의 표적 재검증

- cmd: `flock … ./gradlew --no-daemon :app:test --tests '…NoticeSearchEndpointTest' --tests
  '…AppHttpDependencyGateTest' --tests '…TransportSurfaceGateTest' --rerun :app:detekt
  :app:ktlintCheck`
- exit: 0
- 핵심 결과: 15 + 20 + 19 tests, 0 failed · detekt·ktlint 초록. 주입 표면 집합 등식이 다시 성립한다(`Throwable` 이 그 집합에 들어가지 않는다).

### 2026-10-19T04:17Z~ · 06:0xZ — 변이 아홉 (버릴 clone, 적용 → 표적 재실행 → 복원)

적용 직후 `git diff --numstat` 과 복원 직후 빈 출력을 매번 찍었다. 대응표는 `checklist.md`.
음성 대조(무변경)는 두 표적 모두 exit 0 이다.

| 변이 | 표적 | exit | 삭제 줄 | 복원 |
|---|---|---|---|---|
| m5 읽기를 번역 밖으로(= 핸들러 밖 읽기로 되돌림) | app 둘 | **1**(4 failed) | 5 | 빈 출력 |
| m6 읽기 실패를 빈 값으로 삼킴 | app 둘 | **1**(4 failed) | 1 | 빈 출력 |
| y `prepareCall` 로 먼저 쓰기 | adapters | **1** | 1 | 빈 출력 |
| z `unwrap` 원 연결로 먼저 쓰기 | adapters | **1** | 1 | 빈 출력 |
| w 문구를 SQL 주석으로 단 쓰기 | adapters | **1** | 1 | 빈 출력 |
| w′ w + 면제를 옛 부분 문자열로 | adapters | **1** | 4 | 빈 출력 |
| y′ y + 지문 단언을 항진식으로 | adapters | **0**(의도) | 1 | 빈 출력 |

y·z·w 는 처음에 **삽입** 꼴로도 한 번 쟀고(셋 다 exit 1), 「바꿔치우기」 규율에 맞춰 같은
우회를 한 줄 치환으로 다시 재어 위 표에 적었다. y′ 가 **초록**인 것이 이 배치의 핵심 역대조다
— 지문 단언 한 줄을 무력화하면 `prepareCall` 우회가 다시 지나간다.

adapters 쪽 다섯은 `9820c508` 에서 쟀고, R3-M-1 재설계 뒤 커밋 둘은 그 셋
(`JdbcNoticeSearch.kt`·`ReadOnlyWitness.kt`·`JdbcNoticeSearchTest.kt`)을 건드리지 않았다
(`git diff --name-only 9820c508..2705d828 -- <셋>` 빈 출력) — 그래서 그 측정이 유효하다.
app 쪽 둘은 재설계 뒤 마지막 산출물 커밋 `2705d828` 에서 **새로** 쟀다(앞 판의 m5·m6 는
사라진 술어를 겨눴다).

### 2026-10-19T06:58Z — 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <이 배치가 만진 경로들>` · 같은 명령을
  `reports/evidence/m7/7c1/` 에
- exit: 0
- 핵심 결과: **이 배치가 더한 줄 가운데 걸리는 줄 0**(`git diff 7faf1e3b..2705d828` 의 `+` 줄만
  걸러 같은 패턴으로 다시 돌렸다 — 0 행). evidence 안에서도 0 이다. 파일 단위로는 기존 줄
  일곱이 여전히 걸리는데 전부 **값이 아니라 낱말**이고 이 배치가 만든 줄이 아니다. 패턴 어휘는
  이 문서에 축어로 적지 않는다.
- 같은 자리에서 **낡는 좌표**도 쟀다 — evidence 네 파일에 `<파일>:<줄번호>` 꼴 **0 건**이고,
  역방향 파급(`grep -rn '<stem>:[0-9]'` 를 `reports/`·`docs/` 에)도 0 건이다.

### 2026-10-19T06:58Z — clean-tree 게이트

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자 — 목록은 `rollback.md` 의 기계 산출>`
- exit: 0
- 핵심 결과: 빈 출력. **양성 대조 1회** — in_scope 파일 하나 끝에 빈 줄을 하나 붙이니 같은
  명령이 그 파일을 ` M` 으로 잡았고, 심기 전 사본에서 `head -n <원래 줄수>` 로 절삭 복원해 다시
  빈 출력이 됐다(비파괴, `git checkout --` 미사용).

### 2026-10-19T04:28Z — rollback ①~⑥ (실측 HEAD `9820c508` — 재설계 **전** 판)

- cmd: 버릴 clone 에서 복원 명령 + `flock … ./gradlew --no-daemon check`
- exit: 둘 다 0
- 핵심 결과: 복원 뒤 상태 계수가 기계 목록과 일치 · base 대비 diff 빈 출력 · 되돌린 트리 전건
  초록(BUILD SUCCESSFUL in 9m 22s, 모듈 11 compile · 모듈 10 test). r2 판 목록의
  구멍(`ReadOnlyWitness.kt` 누락)은 기계 산출을 다시 돌려 닫혔다. 이 판은 **재설계로 산출물
  커밋이 더 생겨 유효성을 잃었고**, 아래에서 다시 쟀다.

### 2026-10-19T06:17Z — rollback ①~⑥ 재실측 (실측 HEAD `2705d828` — STR-16 **전** 판)

- cmd: 같은 절차를 새 clone 에서
- exit: 둘 다 0
- 핵심 결과: 복원 뒤 상태 계수가 기계 목록과 일치(재설계가 새 파일을 만들지 않아 목록이
  그대로였다) · base 대비 diff 빈 출력 · 되돌린 트리 전건 초록(BUILD SUCCESSFUL in 9m 24s).
  이 판도 **STR-16 산출물 커밋이 뒤에 와서** 유효성을 잃었고, 아래 r5 에서 다시 쟀다.

### 2026-10-19T06:30Z — acceptance: `check` job + `web` job (마지막 산출물 커밋 `2705d828`)

- cmd: `flock … ./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: 단계 24 전부 통과 — Kotlin `check`(게이트 전건) · `qualityBaseline` · Python 1605 passed · web(생성 타입 바이트 등식 · vitest 17 · Playwright 2). 스크립트 마지막 줄이 「Kotlin 전건 + Python 전건 + web 전건 통과」다.

### 2026-10-19T06:47Z — `container` job (출하 jar 가 바뀌었다)

- cmd: ci.yml `container` job 의 run 블록 16 개를 순서대로 실행(compose 프로젝트 이름만 레인용
  으로 갈랐다). 이 라운드는 컨트롤러·오류 매핑표를 바꿔 **이미지 축에 닿는다**.
- exit: 0
- 핵심 결과: `TOTAL steps=16 failed=0` — 이미지 둘 빌드 · 위생·취약점 게이트 · 거부 스모크 · healthy 수렴 · 컨테이너 스모크 · 백업·복원 리허설(측정 10가지 일치, 누출 0) · 실 서버 교차언어 · 정리 뒤 이 레인 이름의 컨테이너 0 · 볼륨 0.

## r5 라운드 (D-7C1-4 — 추적표 STR-16) — `2620afe7`

### 2026-10-19T10:36Z — 추적표가 닿는 게이트 확인

- cmd: `grep -rln 'acceptance-trace' --include=*.kt …` · `grep -rn 'reports/evidence' --include=*.kt`
- exit: 0
- 핵심 결과: 추적표를 **읽는** test 는 없다. 다만 누출 패턴 게이트가 `reports/evidence/` **트리
  전체**를 스캔 입력으로 들므로 이 파일 변경은 **그 게이트에 닿는다** — 그래서 아래에서 Kotlin
  전건 종료 코드를 쟀다. 추가한 줄의 패턴 일치는 0 이고 능력 행 수도 그대로다.

### 2026-10-19T10:41Z — rollback ①~⑥ 재실측 (실측 HEAD `2620afe7`)

- cmd: 버릴 clone 에서 복원 명령 + `flock … ./gradlew --no-daemon check`
- exit: 둘 다 0
- 핵심 결과: 복원 뒤 상태 계수가 기계 목록과 일치하고, **`comm` 대조에서 양쪽 전용 줄이 0**
  이다(수가 아니라 집합이 같다는 증명). base 대비 diff 빈 출력 · 되돌린 트리 전건 초록(BUILD
  SUCCESSFUL in 9m 46s, 모듈 11 compile · 모듈 10 test · 태스크 줄 443). **수는 `rollback.md`.**

### 2026-10-19T10:5xZ — Kotlin 전건 (게이트에 닿아서)

- cmd: `flock … ./gradlew --no-daemon check qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(23s, 태스크 줄 443 · 모듈 10 test). 바뀐 파일이 누출 패턴 게이트의 스캔 입력이라 그 태스크(`leakPatternGate`)가 이 실행에서 실제로 돌았고 초록이다. 추적표는 **문서**라 컴파일·test 에는 닿지 않는다 — 게이트만 다시 잰 이유다.

## 마지막 HEAD 의 게이트 결과

이 문서의 명령 표는 **evidence 커밋 직전까지**를 담는다. 마지막 HEAD(이 문서를 담은 커밋)의
`check` 결과 정본은 **verifier 와 PR 조치 코멘트**다 — evidence 가 자기 마지막 커밋의 post-state 를
담으려면 커밋이 하나 더 필요하고 그 커밋이 다시 같은 줄을 요구한다.
