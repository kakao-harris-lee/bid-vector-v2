# M7/7E-1 실행 명령과 종료 코드

acceptance 는 `.github/workflows/ci.yml` 의 **job 명령 그대로**다(계약 「acceptance」) — `web` job
전건(여정 e2e 포함)과 Kotlin `check` · `qualityBaseline`. `ml-engine` 은 입력 변경 0 이라 돌리지
않는다(그 job 의 입력 경로를 이 slice 가 건드리지 않는다). `container` 는 web 이미지 입력이
바뀌면 도는데, 이 slice 는 `web/src` 와 설정만 바꾸고 `docker/web.Dockerfile` 의 입력 규약을
바꾸지 않는다 — 다만 번들 내용이 바뀌므로 **승인 전 그 job 의 판정은 PR 체크가 든다**.

호스트는 레인 다섯이 공유하므로 Gradle 명령은 `~/.local/bin/bv-build.sh <명령>`(자리 둘 + 획득
순간 메모리 재확인)으로 감쌌다. 아래 `cmd` 는 그 래퍼 **안쪽** 명령이다.

**마지막 HEAD 의 결과 정본은 이 문서가 아니라 verifier 와 PR 조치 코멘트다**(evidence 는 자기
마지막 커밋의 post-state 를 담을 수 없다).

## Phase 1 착수 실측 (구현 전 — 하네스가 설 수 있는가)

- cmd: `java -jar app.jar --bidvector.collection.mode=once …`(fake KONEPS loopback)
- exit: 0
- 핵심 결과: 수집 **8.9s** · `/api/notices` 가 `X-Total-Count: 4` · `X-Result-State: MATCHED` 와
  제목·기관·기초금액+provenance·마감을 낸다 — 수집 → 저장 → API 가 한 사슬이고 DB 직접 INSERT 0

- cmd: `CollectionWiringTest` 의 loopback 허용 test(출하 test, 읽기만)
- exit: —
- 핵심 결과: `requireSafeKonepsBaseUri` 가 `http://127.0.0.1:…` 를 받는다(설정 키는
  `bidvector.koneps.base-url`) → **출하 코드 변경 0 으로 fake 를 꽂는다**

- cmd: 전략 편집 세션 begin → value → confirm(자격 헤더) 뒤 `POST /api/bid-price-requests`
- exit: 0
- 핵심 결과: 여력 상한이 **없으면 409 `MAX_ACTIVE_BIDS_NOT_CONFIGURED`**, 심은 뒤 **200** 이고
  G2 13 행이 요구하는 상태·사유·고지가 전부 응답에 있다(D-7E-8 의 근거)

- cmd: 로그인 → 담기 → 초안 → 제출함 → 타임라인(출하 API, 브라우저 세션)
- exit: 0
- 핵심 결과: `WATCHING` → `BID_PLANNED` → `BID_RECORDED` · 타임라인 3행 `actorKind: USER` ·
  기록 뒤 `nextCheckpointKind: NONE`·`reconciliationCandidate: true`

## 구현 중 표적 실측

- cmd: `npm run test`(상태 표시 표 test 넷을 **구현 전에** 먼저)
- exit: 1
- 핵심 결과: RED 4 파일(모듈 부재) / 28 통과 — 구현 뒤 11 파일 **107** 통과

- cmd: `npm run test`(화면 B·C test 를 구현 전에)
- exit: 1
- 핵심 결과: RED 2 파일 → 구현 뒤 전부 통과. 그 과정에서 **자기 test 가 한 번 정정을 요구**했다:
  「3안 자리에 숫자 0」을 카드 전체에서 재면 제목의 도메인 용어(「3안」)가 붉히므로 **값 자리**에
  표지를 따로 두고 그 자리에서만 잰다

- cmd: `npm run test`(testing-library 자동 cleanup 부재 실측)
- exit: 1
- 핵심 결과: `globals: false` 라 자동 cleanup 이 등록되지 않아 앞 test 의 DOM 이 남고 같은 표지가
  둘이 된다 — `afterEach(cleanup)` 을 직접 건다

- cmd: `npm run typecheck`(경로 칸 타입)
- exit: 2
- 핵심 결과: 계약이 건 id 를 **경로에서는 문자열**로 선언한다(본문은 int64) — 공개 인자는 본문
  쪽에 맞추고 경로에 넣는 자리에서만 문자열로 적는다. 되돌림 전이는 **본문이 없다**

## 게이트 술어 추가 — 정적 데이터 입구 (양성 대조 포함)

- cmd: probe A — `src/__probe-data.json` + 그 import
- exit: typecheck **2**(TS2732) · W-4 **1**
- 핵심 결과: 두 층이 각각 잡는다. **그 전에 술어를 한 번 고쳤다** — `resolveJsonModule` 을
  **생략**하는 것은 「꺼짐」이 아니다(`moduleResolution: bundler` 에서 같은 probe 가 typecheck
  exit 0). 그래서 W-4 는 「파일에 명시된 `false`」를 본다

- cmd: probe B — `?raw` 로 `node_modules` 의 데이터 파일을 **엔트리에서 닿게** 함
- exit: typecheck **0** · build **1**
- 핵심 결과: TS 층은 `*?raw` 주변 선언 때문에 못 본다 — 번들 층이 그 자리를 받고 **그 실경로를
  적는다**. 첫 측정에서 probe 가 엔트리에서 닿지 않아 build 가 초록이었다(죽은 코드는 번들에
  들어오지 않는다 — 양성 대조의 전제)

- cmd: 두 probe 를 걷은 뒤 게이트 여섯
- exit: 0
- 핵심 결과: typecheck · W-4 · build · 카나리 · vitest · lint 전부 0

## 변이 (계약 §3 — 각각 RED)

| 변이 | 바꿔치운 것 | 잡은 층 | exit |
| --- | --- | --- | --- |
| M1 화면이 값을 리터럴로 | 카드 제목을 **단위 test fixture 와 같은** 리터럴로 | 여정 e2e(DOM ↔ 같은 실행의 API 대조) — 단위는 **초록** | vitest 0 · journey **1** |
| M2a exhaustive 표에서 행 제거 | 게시 하한율 상태 표의 한 행 | typecheck(`satisfies`) + 표 키 집합 test | typecheck **2** · vitest **1** |
| M2b 표 조회에 기본 분기 | 조회 자리에 폴백 추가 | 「표 밖의 값에 문면을 지어내지 않는다」 test(typecheck 는 지나간다) | typecheck 0 · vitest **1** |
| M3 「아직 산출 안 함」 자리에 숫자 | 값 자리에 금액 한 조각 | 값 자리 숫자 부재 단언(단위 + e2e) | vitest **1** · journey **1** |
| M4 생성 클라이언트 밖 호출 | 화면에서 직접 서버 호출 | eslint(보조 층) — typecheck·vitest 는 지나간다 | lint **1** |

**M1 이 하네스의 구멍을 드러냈다**: 첫 측정에서 여정 e2e 가 **초록**이었다(journey exit 0).
변이가 안 잡힌 것이 아니라 **재지 않은 것**이다 — `vite preview` 는 빌드 산출물을 섬길 뿐이고
하네스가 빌드를 돌리지 않아 바꾸기 전 번들을 봤다. 하네스가 직접 짓게 하고(실측 2.23s) 같은
변이를 다시 재니 **exit 1**. CI 는 build step 이 앞에 있어 가려져 있던 자리다.

## 여정 e2e (하네스 전건)

- cmd: `npm run test:e2e:journey`
- exit: 0
- 핵심 결과: **33.0s** — 하네스(묵은 컨테이너 수거 · 버릴 Postgres · fake KONEPS · 수집 러너
  exit 0 · 계정 명령 exit 0 · 전략 개정 1 · 번들 빌드 · preview) + 여정 3건 통과. fake KONEPS
  요청 **2건**(공고 목록 오퍼레이션 하나 × 쪽 하나 × 재조회 0)

- cmd: 같은 명령 뒤 잔존 계수
- exit: 0
- 핵심 결과: 컨테이너 **0** · 하네스·fake·preview 프로세스 **0** · OS 임시의 맥락 파일 **0**.
  첫 판은 실행마다 컨테이너가 하나씩 남았다(세 개 실측) — Playwright 가 기본으로 webServer
  프로세스 그룹을 SIGKILL 하기 때문이고, `gracefulShutdown`(SIGTERM) + 시작 때 수거 +
  동기 종료 핸들러로 닫았다

- cmd: 산출물 누출 스캔(참조형 — `-f config/quality/leak-patterns.txt`)
- exit: 1(일치 0건)
- 핵심 결과: 성공 실행의 산출물은 실행 요약 하나뿐이고 trace 0(실패 시에만) · 스캔 일치 **0**

- cmd: 빌드 산출물에서 e2e·하네스 어휘 계수
- exit: 0
- 핵심 결과: preview 입구 두 환경변수 이름 · 하네스 스크립트 이름 둘 · DB 사용자 · 전략 칸
  이름 · 자격 헤더 이름이 **각각 0 건**, `process.env` 참조 0 건

## CI 와 one-command 축어 일치

- cmd: `ci.yml` `web` job 의 `run` 명령과 `one-command-check.sh` web 절을 기계 대조
- exit: 0
- 핵심 결과: CI 전용(브라우저 시스템 패키지 설치) 하나를 뺀 **13 명령이 순서까지 같다**

- cmd: `bash tools/workflow-step-refs-check.sh .github/workflows/ci.yml`
- exit: 0
- 핵심 결과: job 4 · id 15 · `steps.<id>` 참조 18 · 매달린 참조 0

## acceptance 전건

- cmd: `web` job 명령 **W-1 ~ W-11** 순서 그대로(`npm ci` · lint · typecheck · gate:src-tree ·
  api:types:check · test · gate:tests · build · gate:boundary-canary · 브라우저 설치 · 셸 스모크)
- exit: **전부 0**
- 핵심 결과: vitest **11 파일 109** · 생성 타입 바이트 등식(146,848 bytes) · 경계 전제 성립
  (`resolveJsonModule=false 명시` · `src` 안 정적 데이터 파일 0 · 프로그램 파일 229) ·
  게이트 test 전부 수집·통과 · 번들 경계 **활성**(메인·워커 두 그래프) · 셸 스모크 2 통과

- cmd: `npm run test:e2e:journey`(W-13)
- exit: 0
- 핵심 결과: 여정 3건 통과 **52.6s**(같은 호스트에서 다른 레인 둘이 빌드 중 — 단독 실행 실측은
  33.0s). 잔존 컨테이너·프로세스 0

- cmd: `./gradlew --no-daemon check qualityBaseline`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL in 29m 45s` · 401 actionable(226 실행 · 136 캐시 · 39 up-to-date) ·
  `:check` · `:qualityBaseline` · `:contractGate` 가 모두 돌았다. 이 slice 는 Kotlin `src` 를
  건드리지 않으므로 그 전건은 **회귀 부재의 확인**이다 — 바뀐 입력은 `ci.yml`·`one-command`
  두 공유 파일뿐이고 그 둘을 읽는 게이트가 `check` 안에 있다(워크플로 step 참조 검사)

- cmd: `./gradlew --no-daemon :app:bootJar`(W-12)
- exit: 0
- 핵심 결과: **17s · 39 tasks 전부 up-to-date** — Kotlin 입력이 바뀌지 않았다는 사실이 이 계수로
  선다(이 slice 는 app.jar 의 **소비자**이고 입력이 아니다)

- cmd: `npm run test:e2e:journey`(여정 독립성 수정 뒤 최종)
- exit: 0
- 핵심 결과: 3건 통과 **31.6s** · 잔존 컨테이너·프로세스·맥락 파일 **0**

`container` job 은 돌리지 않았다 — 그 job 의 web 쪽 단언은 **서버 응답**(마운트 지점 ·
번들 자산 200·content-type · SPA fallback)이고 이 slice 는 `index.html` 과 자산 규약을 바꾸지
않는다. 이미지에 들어가는 것은 빌드 산출물뿐이라 새 devDependency 는 런타임 단계에 없다
(Dockerfile 의 runtime 단계가 `dist` 만 복사한다). 그 job 의 판정은 PR 체크가 든다.

## 동결 전 main 병합 라운드 (팀장 지시 2026-10-29)

main 이 **`bb6a187e`** 가 됐다(PR #101 — caddy 직접 빌드 · app 베이스 상향). base `78d97c59` 의
`container` job 은 취약점 DB 갱신으로 붉으므로 그 수정을 **병합해 받는다**.

- cmd: `git merge origin/main`
- exit: 0
- 핵심 결과: 충돌 0. **합집합 검증**(충돌 0 자동 병합이 같은 본문을 접는 함정) — 내 `web` job
  표지 **8 그대로** · 남의 `container` job 주석 **1** · YAML 파싱 job 4 · `web` step 20 ·
  두 job 의 timeout 30 · step 참조 검사 매달린 참조 0. 병합이 들여온 경로 20 개 중 내 in_scope
  와의 교집합은 `ci.yml` **한 건**이고 그 hunk 는 `container` job 주석이라 겹치지 않는다

- cmd: `./gradlew --no-daemon check qualityBaseline`(병합 트리)
- exit: 0
- 핵심 결과: **17s · 392 actionable 중 357 up-to-date · `qualityBaseline` UP-TO-DATE** —
  Gradle 의 up-to-date 회계가 「병합이 Kotlin 게이트 입력을 바꾸지 않았다」를 스스로 말한다
  (경로 교집합 계수와 같은 결론: Kotlin 이 읽는 `config/quality` 열 파일 ∩ 병합이 가져온 다섯
  파일 = **0**)

- cmd: `web` job 명령 재측정(W-2~W-9 · W-11 · W-12 · W-13)
- exit: **전부 0**
- 핵심 결과: 축어 일치 **True**(13 명령) · 여정 3건 통과 **35.5s** · 잔존 0

- cmd: 되돌림 재측정(버릴 clone, 실측 HEAD `8117a921`)
- exit: 0
- 핵심 결과: **복원 기준이 둘로 갈렸다** — 내 파일 18 은 base, 공유 `ci.yml` 은 병합된 main
  고정 SHA. 두 diff 모두 exit 0 이고 「내 표지 0 · 남의 표지 1」 계수가 선다. 되돌린 뒤
  typecheck · vitest(5 파일 28) · 게이트 여섯 · 7A 셸 스모크 전부 0
