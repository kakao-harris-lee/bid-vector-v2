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
- 핵심 결과: `D 15 · M 11`(기계 목록과 일치) · base 대비 diff 빈 출력 · 되돌린 트리 전건 초록.
  자세한 계수는 `rollback.md`.

### 전건이 r2 에서 잡은 것 (수정 뒤 위 결과)

- 반복 매개변수 판정이 app 모듈에 **새 바깥 패키지**를 들여 전송 표면 게이트가 붉었다 →
  이미 쓰는 서블릿 타입으로 같은 사실을 읽게 바꿨다(게이트 허용 집합을 넓히지 않았다).
- 제어 문자 거부가 서면서 문면 등식 test 의 `U+0000` 표본이 조건 조립까지 오지 못했다 →
  표본을 이스케이프 글자가 섞인 값으로 바꿨다.
- ktlint 둘(규칙표 람다 형식 · test import 순서).

## 마지막 HEAD 의 게이트 결과

이 문서의 명령 표는 **evidence 커밋 직전까지**를 담는다. 마지막 HEAD(이 문서를 담은 커밋)의
`check` 결과 정본은 **verifier 와 PR 조치 코멘트**다 — evidence 가 자기 마지막 커밋의 post-state 를
담으려면 커밋이 하나 더 필요하고 그 커밋이 다시 같은 줄을 요구한다.
