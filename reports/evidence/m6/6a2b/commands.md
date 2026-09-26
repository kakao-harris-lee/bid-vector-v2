# M6/6A-2b — 명령과 종료 코드

정본은 `scope.md` acceptance(= CI 워크플로 job 의 명령 그대로)다. 출력 전문은 싣지 않는다(핵심 결과 한 줄)
— 감사자는 명령을 다시 돌린다. **마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트다**(evidence 는
자기 마지막 커밋의 post-state 를 담을 수 없다).

변이는 전부 적용 직후 `git diff --numstat` 으로 **적용됐음을 확인한 뒤** 돌렸고, 측정 뒤 `git checkout --` 로
되돌려 `git diff --numstat` 이 빈 출력임을 확인했다.

## acceptance — `check` job

- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: 9 모듈 전건. `:app:test` 34 test class 전부 실행(`gateExecutionGate` 가 실행 자체를 확인한다)

- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: 게이트가 아니라 측정 — 산출물만 갱신

- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: Kotlin 전건 + Python 전건(`ml-engine` 무편집) 통과

## acceptance — `container` job (로컬 실측, 정본은 CI)

- cmd: `docker build -f docker/ml-serving.Dockerfile …` / `./gradlew :app:bootJar` / `docker build -f docker/app.Dockerfile …`
- exit: 0 / 0 / 0
- 핵심 결과: 두 이미지 생성

- cmd: `./tools/image-hygiene-check.sh` ×2(정책 파일 각각)
- exit: 0 / 0
- 핵심 결과: 두 이미지 모두 「위생 게이트 통과」 — 크기·base layer 판정 포함

- cmd: 앱 이미지 거부 스모크(S-22c, 워크플로에서 추출해 그대로 실행)
- exit: 0
- 핵심 결과: 「거부 스모크 통과(exit=1)」 — 운반 이름공간 조기 거부 문면 확인

- cmd: `docker compose -f docker/compose.yaml up -d` + healthy 수렴 대기
- exit: 0
- 핵심 결과: app·ml-serving·postgres 셋 다 healthy

- cmd: 컨테이너 스모크(S-23b, **이 slice 가 쓰기 왕복을 더한 판**)
- exit: 0
- 핵심 결과: 「스모크 통과」 — 새 절 둘이 실제로 돌았다:
  `begin → value → confirm → GET 반영`(candidateLimit·revision+1)과 `무인증 쓰기 401 · 미선언 메서드 405`

- cmd: `./gradlew --no-daemon :adapters:test --tests '*RealServerIntegrationTest*' -PrealServer=true`
- exit: 0
- 핵심 결과: 실 Kotlin gateway ↔ 컨테이너의 실 Python 서버 통과(무편집 축)

- cmd: `docker compose -f docker/compose.yaml down -v`
- exit: 0
- 핵심 결과: 볼륨까지 정리

`ml-engine` job 은 이 slice 가 Python 을 건드리지 않아 CI 초록으로 갈음한다(`scope.md` acceptance).

## 구현 뒤 표적 test

- cmd: `:app:test --tests '…StrategyEditEndpointTest'`
- exit: 0
- 핵심 결과: 15 test — 결과→상태 코드 표 전건과 우회 ②③④⑦⑧

- cmd: `:app:test --tests '…HttpSurfaceFormatGateTest'`
- exit: 0
- 핵심 결과: 3 test — 등록된 매핑 전수 × 미선언 메서드에서 500 이 0, 405/415/400 이 기대 코드

- cmd: `:app:test --tests '…AppHttpDependencyGateTest'`
- exit: 0
- 핵심 결과: 8 test — 도출 포트 집합 등식 · 주변/능력 분류 · production 양성 · 위반 표본 셋 음성 ·
  Throwable 통로 양성 대조 · 자격증명 참조자 집합 등식

- cmd: `:app:test --tests '…EditableFieldVocabularyGateTest'` / `'…OpenApiEditSessionContractTest'`
- exit: 0 / 0
- 핵심 결과: 어휘 정의역 집합 등식과 두 표의 짝 맞춤 / 문서 ↔ 구현 양방향 등식·상태 코드·필수 키

- cmd: `:adapters:test --tests '…StrategyEditTransactionAtomicityTest'`
- exit: 0
- 핵심 결과: 2 test — 장애 주입(전략 revision·세션 버전 불변, outbox 0행)과 정상 커밋(셋 다 남음)

- cmd: `:app:test --tests '…StrategyEditProductionE2ETest'`
- exit: 0
- 핵심 결과: 4 test — 출하 조립 왕복 · audit 주체 == outbox actor · 무자격 401 에 DB 행 0 · 실행기 빈 존재

## 변이 — 게이트가 RED 가 되는지 실측

| 변이 | 대상 게이트 | 결과 |
|---|---|---|
| M1 `GlobalErrorHandler` 의 메서드 불일치 핸들러 삭제(11줄) | 형식 게이트 | RED 2건(기계 전수 + 405 본문·`Allow`) |
| M2 `TransactionBoundary` 의 `autoCommit` 을 `true` 로 | 원자성 | RED 2건(장애 주입·정상 커밋 둘 다) |
| M3 필드 표에서 `MaxActiveBids` 제거(1줄) | 어휘 게이트·계약 | RED 2건(하위 타입 집합 등식 · 문서 enum 양방향) |
| M4 `StrategyReadController` 가 전략 포트를 다시 받음 | 의존 게이트 | RED 2건(app.http 의존 · 기존 포트 호출 쌍) |
| M6 임계 필드의 값 칸을 `NUMBER`→`COUNT` 로 | 두 표의 짝 | RED 2건(필드 전수 태우기 · 값 칸 넷 태우기) |
| M7 확인 command 의 행위자를 `System` 으로 | 우회 ⑧ | RED 6건(행위자 단언 포함 — 흐름 전체가 거부로 막힌다) |

**변이 없이 게이트가 낸 것 둘**(이 slice 의 실측 수확 — 계약이 예측하지 못한 자리):

- 새 의존 게이트가 **기존** 코드를 잡았다: dry-run 응답 조립이 어댑터 포트 구현을 컨트롤러에 내주고 있었다.
  값(공고 ID 목록)만 내도록 좁혔다.
- D-6A2b-9 의 「영향 0」이 **더는 참이 아니다**. 되살린 `TypeExcludeFilter` 가 `@TestConfiguration` 을
  컴포넌트 스캔에서 걷어내자 E2E 둘이 기록형 종료·fake ML 빈을 잃었고, production 종료 경로가 test JVM 을
  조용히 죽였다 — `gateExecutionGate` 가 「게이트 test class 가 실행되지 않았다」로 잡았다. 필터를 되돌리지
  않고(그 누출이 바로 그 필터의 존재 이유다) 두 test 가 그 빈을 명시 source 로 준다.

## 새 public 표면 전수 (`javap`, 계약 (2b))

- cmd: `javap` — 실행기·조회기·트랜잭션 경계·use case
- exit: 0
- 핵심 결과: **실행기에서 포트를 꺼낼 공개 경로 0.** 실행기의 공개 메서드는 command 를 받아 결과를 내는
  여섯뿐이고 저장소·`ConnectionSource`·`TransactionBoundary` 를 돌려주는 자리가 없다. 경계 구현은
  `inTransaction` 하나만 내고 생성자 인자에 대한 getter 가 없다. 경계가 빌려주는 use case 역시 포트
  getter 가 없다(생성자 `private val`) — 블록 안에서도 포트를 꺼낼 수 없다.
  합성 정적 접근자 하나(`access$getOPERATOR$cp`)가 보이는데 그것이 내는 값은 행위자 상수(값 객체)다.
- 부수 관측: Kotlin `internal` 최상위 함수(필드 토큰 표·초안 패치)는 JVM 에서 `public static` 이다
  (컴파일 시점 모듈 경계일 뿐이다). 새 권한은 아니다 — 둘 다 자유롭게 만들 수 있는 값(`StrategyDraft`)만
  변환한다. 저장·발행에 닿는 자리는 없다.

## privacy·누출 축

- cmd: `./gradlew --no-daemon check`(그 안의 누출 게이트 — evidence 를 스캔한다)
- exit: 0
- 핵심 결과: 게이트 통과

- cmd: 참조형 수동 스캔 `grep -rniE -f config/quality/leak-patterns.txt <이 slice 가 만진 경로>`
- exit: 0(일치 있음 — **전부 어휘 수준**)
- 핵심 결과: 일치는 ① 필드 이름 어휘(사전에 든 낱말 하나가 편집 필드 표에 자연히 나온다)
  ② Testcontainers 가 만든 test 고정값과 그 속성 이름 — 기존 boot test 넷이 이미 같은 형태다.
  **실제 비밀값 리터럴은 0.** 오류 본문은 코드·고정 문구·correlation id 셋뿐이고, 요청 본문을
  audit 에 싣는 경로를 만들지 않았다(필터 두 파일 diff 0)

## clean-tree 게이트

- cmd: `git status --porcelain -- <in_scope 개별 인자>`
- exit: 0
- 핵심 결과: 빈 출력. **양성 대조**: 계약 파일 끝에 개행 하나를 더하니 ` M` 이 뜨고, 절삭을 되돌리니
  다시 빈 출력이다(비파괴 — `checkout --` 를 쓰지 않았다)
