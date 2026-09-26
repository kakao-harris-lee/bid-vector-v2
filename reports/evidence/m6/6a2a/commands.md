# M6/6A-2a — 명령과 종료 코드

정본: `scope.md` acceptance. 출력 전문은 싣지 않는다(핵심 결과 한 줄) — 감사자는 명령을 다시 돌린다.
**마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트다**(evidence 는 자기 마지막 커밋의
post-state 를 담을 수 없다).

변이·되돌림 실측은 전부 **버릴 worktree**에서 돌렸다. 아래 「변이」 절의 각 행은 적용 직후
`git diff --numstat` 으로 변이가 실제로 적용됐음을 확인한 뒤의 결과다.

## RED — 구현 전

- cmd: `./gradlew --no-daemon :app:compileTestKotlin`
- exit: 1
- 핵심 결과: 새 test 둘이 `lockManagementSurface`·`productionApplication`·actuator 좌표 부재로 컴파일 불가

- cmd: `./gradlew --no-daemon :app:test --tests '…ManagementSurfaceLockTest' --tests '…ManagementSurfaceBootRefusalTest' --tests '…PersistencePropertiesTest'` (접두사 거부 구현 전)
- exit: 1
- 핵심 결과: 19 test 중 **9 실패** — 거부 단언 전부(환경변수 그룹 세부 · 명령행 · 평탄화 JSON ·
  대괄호 색인 · `spring.jmx` · 적대 부팅 두 채널 · `SPRING_APPLICATION_JSON` 부팅)와 자격 `toString`.
  **양성 대조와 포트 판정은 전부 통과** — 실패가 「아무 것이나 거부/실패」가 아니라 그 축에서만 났다

- cmd: `:app:test`(관리 표면 셋 + 늦은 소스 신규 test, 늦은 재검사 구현 전)
- exit: 1
- 핵심 결과: 25 test 중 **6 실패** — 판정 데이터 리터럴 둘 · 운반 이름공간 두 채널 · 대괄호 map 원소 ·
  관리 주소 양성 대조 · **refresh 를 지나는 거부**. 양성 대조와 기존 거부 단언은 전부 초록이라
  실패가 그 축에서만 났다. 같은 실행에서 「재검사 시점 readiness 는 503, 기동 완료 뒤 200」은
  이미 초록이다 — Boot 의 발행 순서를 재는 단언이라 구현 전에도 참이고, 그래서 **양성 대조**로 쓴다

## 구현 뒤 표적 test

- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.ManagementSurfaceLockTest' --tests 'bidvector.app.management.ManagementHealthSurfaceTest'`
- exit: 1 → 0
- 핵심 결과: 첫 실행은 집계 health 본문이 키 둘(`status`+`groups`)이라 RED — Boot 4.1.1 이 세부
  설정과 무관하게 그룹 이름을 싣는다는 실측. 단언을 「키 하나」에서 **실측한 모양 고정**으로
  바꾼 뒤 그 시점의 표적 test(12) 전건 통과 — 현재 집합은 27 이다(아래 변이 절)

- cmd: 같은 명령 + `--tests 'bidvector.app.http.ProductionAssemblyAuthAuditTest'`
- exit: 0
- 핵심 결과: 기존 production 조립 test 가 `productionApplication()` 공유 형태에서 그대로 초록

## 관리 표면 값 획득 축 실측 (계약 (2b) 요구 · D-6A2a-10)

- cmd: `ManagementSurfaceLockTest` 의 「잠금 전」 단언 — 환경변수·명령행 source 를 세운 뒤 값 조회
- exit: 0
- 핵심 결과: **잠금 전에는 환경변수·명령행이 노출·세부를 넓힌다**(계약이 가정한
  `defaultProperties` 자리는 가장 낮은 우선순위다). `addFirst` 잠금은 자기가 **이름 댄 키**에
  대해서는 둘 다 못 이기게 한다 — 그러나 그것으로는 부족했다(아래)

- cmd: 잠금 밖 형제 키 실측(r1 세 레인) — 그룹 단위 `show-details`/`show-components` · 새 그룹
  `include` · `status.http-mapping.down` · `probes.add-additional-paths` · `group.readiness.exclude` ·
  `validate-group-membership`
- exit: (관측)
- 핵심 결과: **여섯 축 모두 환경 한두 줄로 열렸다** — 세부·구성 요소 이름·드라이버 예외 문면이
  인증 없는 관리 포트에 실리고, readiness 의 참/거짓이 뒤집힌다. 열거 잠금의 사각이다

- cmd: 접두사 거부 뒤 같은 축 — `:app:test`(표적 셋)
- exit: 0
- 핵심 결과: 20 test 전건 통과. 여섯 축이 **기동 거부**로 닫힌다(출하 조립을 실제로 부팅한
  단언 포함). 양성 대조 둘(`management.server.port` 를 환경변수·명령행으로 주면 통과)도 초록 —
  거부가 무차별이 아니다

- cmd: 거부 문면의 값 부재 단언
- exit: 0
- 핵심 결과: 문면에 **키 이름만** 실린다(심은 표지 값이 문면에 없음을 단언). 관리 포트 값 하나만
  환경이 정하고, 그 자유의 상한은 `ManagementPortType` 분리 판정이다

## `spring.web.error` 확대 실측 (D-6A2a-17 ①, privacy-gate r2 L-4)

- cmd: 출하 조립을 `spring.web.error.include-exception=true`·`include-message=always`·
  `include-stacktrace=always`·`include-binding-errors=always` 로 띄우고 관리 포트 `/error` 와 비 GET 을 조회
  (일회용 test, 판정 뒤 제거)
- exit: 0(관측)
- 핵심 결과: 관리 포트 `GET /error` 본문 키가 **셋에서 넷으로** 늘었다(`message` 가 붙고 값은
  일반 문면이다). 비 GET 500 본문은 우리 오류 처리기의 모양이라 바뀌지 않았다. 관리 child 의 오류
  endpoint 가 이 이름공간의 바인딩을 읽어 예외 클래스·스택 포함까지 정한다는 것은 바이트코드로
  확인했다 — 그래서 **거부 이름공간에 넣는다**(환경이 관리 포트 응답을 넓히는 경로를 남기지 않는다).
  verifier r2 가 같은 축을 `server.error.*` 로 쟀는데 Boot 4 는 그 자리를 `spring.web.error` 로
  옮겼다(그래서 그 측정은 「새는 것 없음」이 나왔다)

## 이미지 · 위생 게이트

- cmd: `./gradlew --no-daemon :app:bootJar`
- exit: 0
- 핵심 결과: `app/build/libs/app.jar` 52.1MB(평범한 `app-plain.jar` 도 그대로 공존, D-6A1-28)

- cmd: `docker build -f docker/app.Dockerfile -t bidvector/app:local .`
- exit: 0
- 핵심 결과: layer 넷(`dependencies`/`spring-boot-loader`/`snapshot-dependencies`/`application`)으로
  풀려 `/application/{application.jar,lib}` 구성 · 이미지 339.4MB · `Config.User=bidvector`(10001)

- cmd: `./tools/image-hygiene-check.sh bidvector/app:local config/quality/image-hygiene-policy-app.properties`
- exit: 0
- 핵심 결과: 실프로세스 uid 10001 · 베이스 layer 접두 일치 · 금지 실행 파일 9 전건 부재 ·
  **필수 실행 파일 1 존재** · 의존 layer 97 항목(하한 50) 중 금지 좌표 13 전건 0 · 339.4MB ≤ 450MB

- cmd: layer 소유권·쓰기 가능성 실측(`--chown` 제거 뒤) — 이미지 안에서 `id -u`·`ls -ld`·쓰기 시도
- exit: 0
- 핵심 결과: 프로세스 uid 10001 · `/application` 과 배포물이 **root 소유·전체 읽기** ·
  앱 사용자의 쓰기 시도가 거부된다 · `Config.ExposedPorts` 는 **null**(`EXPOSE` 제거 확인) ·
  컨테이너는 그대로 healthy 로 수렴한다(아래 `container` job)

- cmd: `./tools/image-hygiene-check.sh bidvector/ml-serving:local config/quality/image-hygiene-policy.properties`
- exit: 0
- 핵심 결과: 정책 인자화 뒤에도 python-serving 갈래가 그대로 선다(금지 import 5 전건 0) —
  이 개발 호스트의 기존 이미지는 336.1MB(상한 400MB)

- cmd: `docker build` 양성 대조 — `docker/app.Dockerfile` 에 제외 대상 경로 COPY 한 줄을 심어 빌드
- exit: 1
- 핵심 결과: `.dockerignore` 가 실제로 컨텍스트를 줄인다(그 경로를 "not found" 로 대며 실패).
  심은 줄은 사본 복원으로 비파괴 절삭(`checkout --` 을 쓰지 않았다)

- cmd: 위생 게이트 프로브 컨테이너 수명 실측(`docker run -d` → `docker inspect`/`docker top`)
- exit: 0
- 핵심 결과: TEST-NET-1 주소로 DB 를 가리키면 pid 1 이 t=10s 에도 살아 있고 uid 10001 ·
  `no-new-privileges` 아래 정상 기동 — (1) 의 프로세스 표집 창이 확보된다

## 위생 정책 값 모양 검증 (code-review r1 MEDIUM·LOW)

- cmd: 목록 원소에 공백을 섞은 정책 파일로 게이트 실행(변이 적용을 `git diff --numstat` 으로 확인)
- exit: 2
- 핵심 결과: 「원소에 공백이 섞여 있다」로 **그 자리에서 끊는다**. 고치기 전에는 첫 원소만 판정하고
  나머지 여덟을 조용히 껐다(두 판정 축이 모두 「부재」로 읽는다 — 컨테이너 생성 실패와 PATH 부재)

- cmd: 키 대조가 리터럴인지 — 정책 파일에 `sizeXcapYbytes=1` 한 줄을 더해 게이트 실행
- exit: 1
- 핵심 결과: **중복 키로 읽지 않는다**(「중복 키」 메시지 0건). 고치기 전에는 키를 정규식으로 읽어
  `.` 가 와일드카드였다. 남은 exit 1 은 그 측정에서 이미지가 없었던 것뿐이다

- cmd: 같은 파일에 **실제** 중복 `size.cap.bytes=1` 을 더해 게이트 실행
- exit: 2
- 핵심 결과: 중복 키로 정확히 끊는다 — 리터럴 대조가 진짜 중복을 놓치지 않는다(양성 대조)

- cmd: 필수 실행 파일 축 변이 — 파생 이미지에서 `curl` 을 지우고 앱 게이트 실행
- exit: 1
- 핵심 결과: 「필수 실행 파일 'curl' 이 이 이미지의 PATH 에 없다」 위반 1. 파생 이미지는 측정 뒤 삭제

- cmd: 허용 문자 집합으로 바꾼 뒤 세 정책 사본으로 재측정(NBSP 원소 · 쉼표 뒤 공백 · 무수정)
- exit: 2 · 2 · 0
- 핵심 결과: **NBSP 가 섞인 원소도 거부된다**(앞 판은 통과시켰다 — 공백 종류 열거의 사각). 쉼표 뒤
  공백도 그대로 거부. 무수정 정책은 게이트 전건 통과(exit 0)이므로 「아무 것이나 거부」가 아니다.
  이 판정은 docker 를 타기 전에 끊는다

## compose · 스모크

- cmd: `docker compose -f docker/compose.yaml config` (환경 변수 없이)
- exit: 1
- 핵심 결과: `app` 의 모든 값이 `${VAR:?}` 라 값 없이는 그 자리에서 실패한다

- cmd: 같은 명령(환경 변수 채운 뒤)
- exit: 0
- 핵심 결과: API 포트는 `host_ip: 127.0.0.1` 로만 publish · 관리 포트 publish 0 ·
  `no-new-privileges` · healthcheck 은 관리 포트 readiness · 수집·ML 변수 0

- cmd: `docker compose -f docker/compose.yaml up -d` + 세 서비스 healthy 수렴 대기
- exit: 0
- 핵심 결과: app·ml-serving·postgres 세 서비스가 세 번째 폴링(약 9초)에 healthy

- cmd: CI `container` job 의 스모크 블록을 로컬에서 그대로 실행
- exit: 0
- 핵심 결과: 프로브 둘 200·키 하나(`status`,UP) · **집계 `/actuator/health` 200·키 둘
  (`groups`,`status`)·그룹 이름 둘** · 무인증 401 · 인증 `/api/strategy` 200 · API 포트 actuator
  네 경로가 무인증·인증 둘 다 401/404 · 관리 포트 health 외 **열두** 경로 404(구성 요소 경로 포함) ·
  **관리 포트 `/error` 200·키 셋 고정** · 앱 로그에 수집 시작 줄 0

- cmd: 수집 판정 술어의 양성·음성 대조 — 2MB 로그(첫 줄에 일치)와 일치 없는 로그를 옛 형태
  (`printf | grep -q`, `pipefail`)와 새 형태(`case`)에 각각 넣는다
- exit: 0
- 핵심 결과: **옛 형태는 일치를 놓쳤다**(대형 로그에서 MISSED — `grep -q` 가 첫 일치에서 나가
  파이프를 닫고 `pipefail` 이 파이프라인을 비-0 으로 만든다). 새 형태는 CAUGHT/MISSED 를 정확히
  가른다. 술어가 공허하지 않음과 고침이 실제로 그 축을 닫음을 같은 측정이 함께 든다

- cmd: 자격 값 argv 노출 양성·음성 대조(요청 300회를 돌리며 프로세스 표와 `/proc/*/cmdline` 전수 조회)
- exit: 0
- 핵심 결과: `printf | curl --config -` 형태 **0회 관측**, 같은 값을 헤더 인자로 넘기는 형태
  **26회 관측**. 첫 측정은 조회기 자신의 인자에 값이 들어가 자기검출(50회)을 냈고, 패턴을 파일로
  넘기는 형태로 고쳐 다시 쟀다

- cmd: `docker compose -f docker/compose.yaml down -v`
- exit: 0
- 핵심 결과: 컨테이너·네트워크·볼륨 정리(이 호스트의 다른 프로젝트 컨테이너는 건드리지 않았다 —
  전용 project 이름으로 띄웠다)

- cmd: CI 스모크 S-22c 를 **고치기 전 이미지**에 걸기(음성 대조)
- exit: 1
- 핵심 결과: 컨테이너는 비-0 으로 끝나지만(DB 부재) 스모크가 「거부 사유가 아니다」로 실패한다 —
  **종료 코드만 보는 스모크는 아무것도 재지 않는다**는 사실의 실측이다

- cmd: 같은 스모크를 고친 이미지에 걸기(양성)
- exit: 0
- 핵심 결과: 컨테이너 exit 1 + 문면이 결정 ID 와 운반 이름공간 키를 말한다. verifier r2 가 출하
  이미지에서 쓴 환경변수 **그 한 줄**이 기동을 거부시킨다

## 변이 ①~⑦

계약이 요구한 일곱 축이다. ①~④ 는 r1 이 test 를 값 축에서 거부 축으로 바꾼 **뒤** 다시 쟀다(앞
라운드 수치를 옮기지 않았다). 표적은 그 시점의 관리 표면 test 셋이고 기준선은 **27 test 전건 통과**
였다 — r2 가 test 를 더해 지금 기준선은 35 이므로, 아래 수치의 분모는 **그 측정 시점의 집합**이다.
r2 가 바꾼 술어의 재측정은 아래 「변이 — 늦은 재검사」 절에 있다.

| 변이 | 명령 | numstat | exit | 핵심 결과 |
|---|---|---|---|---|
| ①a 관리 포트 기본값을 API 포트 기본값과 같게 | `:app:test`(표적 셋) | 1/1 | 1 | 27 중 2 FAILED — 출하 기본값 분리 단언 + 우선순위 단언(둘 다 기본값으로 조립한다) |
| ①b 분리 판정 가드 제거 | 같음 | 0/5 | 1 | 27 중 3 FAILED — 포트 거부 단언 셋(같은 포트·미설정·음수) |
| ② 노출에 `env` 추가 | 같음 | 1/1 | 1 | 27 중 3 FAILED — 런타임 endpoint 집합 ≠ {health} + 잠금 값 단언 둘 |
| ③ `show-details=always` | 같음 | 1/1 | 1 | 27 중 1 FAILED — 우선순위 단언의 값 단언. **약한 변이임을 실측했다**: `show-components=never` 가 남아 응답 본문이 바뀌지 않는다(구성 요소가 여전히 숨는다) |
| ③' `show-components=always`(③ 의 더 센 형제) | 같음 | 1/1 | 1 | 27 중 **4 FAILED** — 프로브 본문 키 · 집계 모양 · 구성 요소 경로 404 탐침 · DB 정지 축. 본문 축을 실제로 여는 것은 이 값이다 |
| ④ readiness 에서 DB 제거 | 같음 | 1/1 | 1 | 27 중 1 FAILED — 「DB 가 사라지면 readiness 는 DOWN」 |
| ⑤ Dockerfile `USER` 제거 | 앱 위생 게이트 | 이미지 빌드 | 1 | 위반 2 — `Config.User` 미지정 · 실프로세스 uid 0 |
| ⑥ test 전용 좌표를 의존 layer 에 실제로 넣기 | 앱 위생 게이트 | 이미지 빌드 | 1 | 위반 1 — 금지 좌표가 의존 layer 에 있다(항목수 97→98) |
| ⑦ JDK 베이스로 교체 | 앱 위생 게이트 | 이미지 빌드 | 1 | 위반 11 — 금지 실행 파일 9 전건 + 베이스 layer 체인 불일치 + 크기 상한(500.4MB > 450MB) |

**변이 ⑦ 이 게이트 사각을 하나 찾았다.** 첫 측정에서 위반이 10 이었고 `serialver` 만 빠졌다 —
JDK 베이스에 그 파일이 있는데 `--version` 을 거부해(exit 1) 실행 시도 축이 「부재」로 읽었다.
PATH 조회 축을 더하고(셸 부재는 판정 불가로 실패) 다시 재 11 건이 됐다. 이 시정은 별 커밋이다.

- cmd: 변이 없는 기준선 — `:app:test`(표적 셋)
- exit: 0
- 핵심 결과: **27 test 전건 통과**(변이 RED 가 의미를 갖는 전제)

## 변이 — 접두사 거부·배선·자격 축 (D-6A2a-10~13)

표적은 잠금 순수 환경 test·적대 부팅 test(자격 축은 자격 `toString` test)이고, 분모 20 은 **r1
시점의 집합**이다. 각 행은 적용 직후 `git diff --numstat` 으로 변이가 실제로 적용됐음을 확인한 뒤의
결과이고, 측정 뒤 사본 복원 + `git diff --quiet` 로 복원을 확인했다.

| 변이 | numstat | exit | 핵심 결과 |
|---|---|---|---|
| 접두사 거부 `check` 블록 삭제 | 0/6 | 1 | 20 중 **8 FAILED** — 거부 단언 전부(순수 환경 5 + 부팅 3). 양성 대조·포트 판정은 초록 |
| 허용 목록을 `management` 이름공간 전체로 넓힘(집합 + 판정 둘) | 2/2 | 1 | 20 중 **8 FAILED** — 리터럴 집합 단언이 먼저 붉고, 거부 단언 일곱이 함께 붉다 |
| 잠금 심기를 `addFirst` → `addLast` | 1/1 | 1 | 20 중 **1 FAILED** — 「잠금 뒤에 실체가 채워지는 source 보다 잠금이 우선한다」. 접두사 거부가 그 시점의 소스만 보므로 **순서를 잠그는 단언이 따로 필요하다**는 것이 이 측정이다 |
| 조립에서 초기화자를 떼고 잠금을 `.properties(...)` 최저 우선순위로 | 3/2 | 1 | 20 중 **3 FAILED** — 부팅 단언 셋만 붉다. 순수 환경 test 는 배선을 보지 않으므로 초록이다(r1 이 잡은 사각 그 자체) |
| `PersistenceProperties` 를 `data class` 로 되돌림 | 1/1 | 1 | 자격 `toString` 단언 FAILED |
| `curl` 을 지운 파생 이미지 | 이미지 빌드 | 1 | 위생 게이트 위반 1(필수 실행 파일 부재) |
| 정책 목록 원소에 공백 | 1/1 | 2 | 정책 오류로 끊는다 |

## 변이 — 늦은 재검사 (D-6A2a-14)

표적은 관리 표면 test 넷 + 출하 조립 부팅 test 둘이고 기준선은 **35 test 전건 통과**다. 각 행은 적용
직후 `git diff --numstat` 으로 변이가 실제로 적용됐음을 확인하고, 측정 뒤 복원과 빈 `git status` 를
확인한 뒤의 결과다.

| 변이 | numstat | exit | 핵심 결과 |
|---|---|---|---|
| 조립에서 **늦은 재검사 listener 를 떼어냄** | 0/1 | 1 | 35 중 **1 FAILED** — refresh 를 지나는 거부 test 하나만 붉다. 다른 test 집합은 이 축을 못 본다(그래서 이 test 를 게이트 감시 집합에 등재했다) |
| 재검사를 **readiness 발행 뒤로 이동**(같은 event 대신 준비 상태 변경 event 를 듣게) | 4/4 | 1 | 35 중 **3 FAILED** — 「재검사는 readiness 가 트래픽을 받기 전에 돈다」 + 출하 조립을 끝까지 띄우는 부팅 test 둘. 셋 다 재검사 자신의 문면(「검사 자리가 readiness 발행보다 뒤로 옮겨졌다」)으로 실패한다 — 자리 이동을 **구조로** 잡는다. 적대 test 하나는 이 변이에서도 초록이므로, 그것만으로는 자리 이동을 볼 수 없다 |
| 조기 거부 `check` 삭제(현재 술어에서 재측정) | 0/1 | 1 | 35 중 **9 FAILED** — 순수 환경 5 + 부팅 축 4. 늦은 재검사는 refresh 전에 끝나는 test 를 구할 수 없다(그래서 두 자리가 서로를 대신하지 않는다) |
| 운반 이름공간(`server.servlet.context-parameters`)을 거부 집합에서 제거 | 1/1 | 1 | 35 중 **4 FAILED** — 리터럴 집합 단언 + 두 채널 + 대괄호 map 원소. **refresh 를 지나는 거부 test 는 초록이다** — 보조 잠금이 사라져도 주 잠금이 그 위협을 닫는다는 실측이고, 보조가 보조임을 이 행이 보여 준다 |

- cmd: 변이 없는 기준선 — `:app:test`(위 표적)
- exit: 0
- 핵심 결과: **35 test 전건 통과**

## `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT` 실측

- cmd: Boot 기본 `excludeFilters` 둘을 명시로 되살린 트리에서 `:app:test`(그 시점의 표적 둘)
- exit: 0
- 핵심 결과: 되살려도 결과가 같다 — 거동 영향 0. 구조적 근거는 `checklist.md`(자동 구성 등재 파일
  부재 · `TypeExcludeFilter` 빈 0). 계약대로 **필터를 되살리지 않고** OPEN 을 6A-2b 로 넘긴다

## 경계 무편집 실측 (`out_of_scope`)

- cmd: `git diff --stat <base>..HEAD --` 로 네 경계 각각 조회 — 인증 필터 두 파일 · `openapi/` ·
  마이그레이션 디렉터리 둘 · `tools/one-command-check.sh`
- exit: 0
- 핵심 결과: **네 경계 모두 빈 출력**(diff 0). D-6A2a-1·4·9 문면 그대로

- cmd: 이미지 비밀값 표면 조회 — Dockerfile 의 `ENV`/`ARG` · 이미지 `Config.Env` · `docker history`
- exit: 0
- 핵심 결과: `ENV` 0 · `ARG` 하나(jar 의 공개 경로) · `Config.Env` 는 베이스 6 항목뿐 ·
  history 의 build 인자는 베이스 이미지 것 둘뿐 · `app` 에 `src/main/resources` 자체가 없다

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m6/6a2a/`
- exit: 1
- 핵심 결과: 매치 없음 = 통과. 산출물 쪽 동일 스캔은 새 test 파일에서 2건 —
  둘 다 **Testcontainers API 자신의 멤버 이름**이고 기존 `ProductionAssemblyAuthAuditTest` 가
  같은 형태를 이미 갖는다. 루트 게이트의 scanRoot 는 `reports/evidence` 라 판정 대상 밖
- 육안 확인: Telegram id·사업자 정보 0(이 slice 는 그 축을 만들지 않는다)

## clean-tree 게이트

- cmd: `git status --porcelain -- <in_scope 산출물 경로 개별 인자>` (evidence 디렉터리 제외)
- exit: 0
- 핵심 결과: 빈 출력. **양성 대조 1회** — in_scope 파일 하나에 줄을 심으면 ` M` 로 잡히고,
  심은 줄은 `head -n <원래 줄수>` 절삭으로 비파괴 복원했다(`checkout --` 을 쓰지 않았다)
- evidence 디렉터리 자신의 마지막 HEAD clean 상태는 **verifier 가 그 HEAD 에서 직접 잰다**
  (evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다)

## acceptance

`check` job 명령 그대로를 **버릴 worktree**에서 마지막 산출물 커밋(`8e385cea`)에 대해 한 번씩.

- cmd: `./gradlew --no-daemon check --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 339 task 전건 실행(컴파일·ktlint·detekt·sizeGate·typeShapeGate·
  jarContentGate·domainDependencyGate·architecture test·compatibilitySmoke·contractGate·
  누출 패턴 게이트·gateExecutionGate 등)

- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(게이트가 아니라 측정)

- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: Kotlin 전건 + Python 전건 통과

**r2 에서도 전건 `check` 가 표적 test 로는 보이지 않는 것을 두 번 잡았다.** ① `jarContentGate` —
판정 시퀀스의 `filterIsInstance` 가 reified inline 이라 stdlib 의 람다 클래스가 이 모듈 아카이브에
복사되고, 게이트는 아카이브의 모든 클래스가 **게이트를 통과한 소스**에서 나왔음을 요구한다
(`mapNotNull { it as? … }` 로 바꿨다 — 거동 동일). ② `typeShapeGate` — 늦은 재검사 listener 가
인터페이스 둘을 구현해 인터페이스 수 래칫(상한 1)을 넘었다(`@Order` 로 우선순위를 주고 supertype 을
하나로 줄였다 — `AnnotationAwareOrderComparator` 가 둘을 같게 읽는다). **표적 test 로는 둘 다
초록이었다.**

r1 에서 전건 `check` 가 잡은 것 둘. ① actuator 좌표가 test 전용 조립에
`RequestMappingHandlerMapping` 빈을 둘로 만들어 `OperatorAuthenticationTest` 4 test 를
`NoUniqueBeanDefinitionException` 으로 깨뜨렸다(시정은 그 조립에서 관리 서버를 끄는 별 커밋,
`checklist.md` 「actuator 좌표가 만든 부수 효과」). ② 새 코드의 ktlint 위반 둘(체인 줄바꿈 · super
type 줄바꿈). 둘 다 표적 test 만으로는 초록이었다 — 이것이 부분 게이트를 금지하는 이유의 실측이다.

**`container` job — ci.yml 의 step 을 그 파일에서 뽑아 같은 순서로 한 번**(버릴 worktree
`8e385cea`). 러너 전용 step 셋(checkout·setup-java·setup-gradle)만 건너뛰고, 값 생성 step 둘은
같은 명령(`openssl rand`)으로 **실행 셸의 메모리에만** 뒀다(`$GITHUB_ENV` 가 없는 자리다 — 파일을
만들지 않는다). 컨테이너·compose project 는 전용 접두사를 썼다.

- cmd: `container` job 의 실행 step 열둘 전부(r2 가 거부 스모크 하나를 더했다)
- exit: **전건 0**
- 핵심 결과: 자격 값 생성 → ml-serving 이미지 → 앱 배포물 → 앱 이미지 → 위생 둘 통과(앱: uid 10001 ·
  금지 실행 파일 9 · 필수 실행 파일 1 · 의존 97/50 · 금지 좌표 13 · 339.4MB / ml-serving 336.1MB) →
  **거부 스모크 통과**(출하 이미지가 verifier 의 환경변수 한 줄을 거부, 사유까지 확인) → 세 서비스
  healthy → 스모크 통과(집계·`/error`·구성 요소 경로 포함, 고친 전송 오류 helper 로) → S-24 통과 →
  볼륨까지 정리. 정리 뒤 이 slice 가 만든 컨테이너·네트워크·볼륨 0, 이 호스트의 다른 프로젝트
  컨테이너는 무접촉. 실행 로그에 40·48자 hex **단독 토큰 0**(64자 단독 토큰 열은 이미지 다이제스트)이고
  임시 파일은 판정 뒤 파기했다

기준은 CI job 이 정본이고 PR CI 의 `container` 초록이 필요조건이다.

**새로 빌드한 ml-serving 이미지도 336.1MB 로 나왔다**(정책 상한 400MB) — 6C evidence 의
113.7MB 기록이 낡았다는 뜻이고, 이 slice 는 그 정책을 건드리지 않는다(`checklist.md` 알려진 제한).
