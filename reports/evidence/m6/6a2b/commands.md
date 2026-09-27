# M6/6A-2b — 명령과 종료 코드

정본은 `scope.md` acceptance(= CI 워크플로 job 의 명령 그대로)다. 출력 전문은 싣지 않는다(핵심 결과 한 줄)
— 감사자는 명령을 다시 돌린다. **마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트다**(evidence 는
자기 마지막 커밋의 post-state 를 담을 수 없다).

변이는 전부 적용 직후 **적용됐음을 확인**한 뒤 돌렸고(추적 파일은 `git diff --numstat`, 새 파일은 존재 자체),
측정 뒤 되돌려 빈 diff 를 확인했다.

## acceptance — `check` job

- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: 9 모듈 전건. 이 slice 의 게이트·거동 test 아홉이 `gateExecutionGate` 에 등재돼 **실행
  자체**가 확인된다(통과 수가 아니다 — 그중 하나에 `@Disabled` 를 달면 RED 임을 실측했다)

- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: 게이트가 아니라 측정 — 산출물만 갱신

- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: Kotlin 전건 + Python 전건(`ml-engine` 무편집) 통과

## acceptance — `container` job (로컬 실측, 정본은 CI)

- cmd: 두 이미지 빌드(`ml-serving`·앱) + `./gradlew :app:bootJar`
- exit: 0
- 핵심 결과: 이미지 생성. 앱 이미지는 이 라운드의 `bootJar` 로 다시 만들었다

- cmd: `./tools/image-hygiene-check.sh` ×2(정책 파일 각각)
- exit: 0 / 0
- 핵심 결과: 「위생 게이트 통과」 — 크기·base layer 판정 포함

- cmd: 앱 이미지 거부 스모크(S-22c, 워크플로에서 추출해 그대로 실행)
- exit: 0
- 핵심 결과: 「거부 스모크 통과」 — 운반 이름공간 조기 거부 문면 확인

- cmd: `docker compose up -d` + healthy 수렴 → 컨테이너 스모크(S-23b) → `down -v`
- exit: 0
- 핵심 결과: 셋 다 healthy, 「스모크 통과」 — 쓰기 왕복(`begin → value → confirm → GET 반영`)과
  「무인증 쓰기 401 · 미선언 메서드 405」 절이 실제로 돌았다

- cmd: `./gradlew --no-daemon :adapters:test --tests '*RealServerIntegrationTest*' -PrealServer=true`
- exit: 0
- 핵심 결과: 실 Kotlin gateway ↔ 컨테이너의 실 Python 서버 통과(무편집 축)

`ml-engine` job 은 이 slice 가 Python 을 건드리지 않아 CI 초록으로 갈음한다.

## 표적 test (핵심만)

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `:workflow:test --tests '…EditSessionBaseRevisionTest'` | 0 | 4 — 기준 revision 기록·교차 세션 거부·되돌아가 재확인·낡은 행 fail-closed |
| `:app:test --tests '…StrategyEditProductionE2ETest'` | 0 | 6 — 출하 조립 왕복 · **교차 세션 409 와 앞 값 보존** · 무효 값의 영속 0 · audit==actor · 무자격 401 |
| `:app:test --tests '…AppHttpDependencyGateTest'` | 0 | 13 — 도출 포트 집합 등식 · **대상 집합(app 전체 − 면제)** · 면제가 실재 클래스만 가리키는지 · production 양성 · 위반 표본 여섯 |
| `:app:test --tests '…HttpSurfaceCensusTest'` | 0 | 6 — 두 서블릿 컨텍스트 · API·관리 각각의 handler·Filter·Servlet 집합 등식 · 문서 등식 모집단 완전성 |
| `:app:test --tests '…ProductionHttpSurfaceTest'` | 0 | 7 — 매핑 전수 405/415/400/406 · 두드림∪제외 등식 · 문서↔매핑 집합 등식 · 응답 키 집합 |
| `:app:test --tests '…StrategyEditExecutorRaceTest'` | 0 | 2 — 읽기 사이 끼어듦 거부와 끼어듦 없을 때의 양성 대조 |
| `:app:test --tests '…StrategyEditEndpointTest'` | 0 | 21 — 결과→상태 코드 표, 우회 ②③④⑦⑧, 알 수 없는 키·십진 상한·세션 필드 강제 |
| `:app:test --tests '…ManagementHealthSurfaceTest'` | 0 | 관리 포트 미디어 타입 축(200/406/노출 집합 불변) 포함 |
| `:strategy:test --tests '…StrategyExportTest'` | 0 | 2 — 왕복 등식과 정의역 등식 |
| `:adapters:test --tests '…StrategyEditTransactionAtomicityTest'` | 0 | 2 — 장애 주입(전략·세션 불변, outbox 0행)과 정상 커밋 |

## 변이 — 게이트가 RED 가 되는지 실측

| 변이 | 대상 게이트 | 결과 |
|---|---|---|
| 확인의 기준 revision 축 제거 | 동시 세션 회귀 | RED 2(교차 세션 · 낡은 행 fail-closed) |
| 메서드 불일치 핸들러 삭제 | 형식 게이트 | RED 2(기계 전수 · 405 본문·`Allow`) |
| 트랜잭션 경계 `autoCommit=true` | 원자성 | RED 2(장애 주입·정상 커밋) |
| 필드 표에서 한 필드 제거 | 어휘 게이트·계약 | RED 2(하위 타입 등식 · 문서 enum 등식) |
| 읽기 컨트롤러가 전략 포트를 다시 받음 | 의존 게이트 | RED 2(의존 · 포트 호출 쌍) |
| 임계 필드의 값 칸 오배정 | 두 표의 짝 | RED 2(필드 전수 · 값 칸 태우기) |
| 확인 command 의 행위자를 `System` 으로 | 우회 ⑧ | RED 6 |
| `toDraft` 에서 필드 하나 누락 | 전수성 게이트 | RED 1(왕복 등식) |
| **MU1** HTTP 층에 JDBC 클라이언트 지름길(production 소스) | 의존 게이트 | RED 1 |
| **MU2** 경계 빈을 쥔 헬퍼를 컨트롤러가 참조(production 소스) | 의존 게이트 | RED 2(의존 · 대상 집합) |
| **MU2b** 다른 패키지의 진짜 컨트롤러 + JDBC(production 소스) | 의존 게이트 | RED 2(의존 · 대상 집합) |
| **MU4** 수집에서 경로 변수 매핑 제외 | 표면 게이트 | RED 2(매핑 앵커 · 문서↔매핑 등식) |
| **F-r2-1 ①** `@Bean RouterFunction` (production 소스) | 의존 게이트 · 표면 실측 | RED / RED |
| **F-r2-1 ②** 빈 이름 URL 매핑 `HttpRequestHandler` | 의존 게이트 · 표면 실측 | RED / RED |
| **F-r2-1 ③** 인증보다 앞선 `OncePerRequestFilter` | 의존 게이트 · 표면 실측 | RED / RED |
| **면제 클래스의 `@Bean RouterFunction`** | 의존 게이트 **통과** · 표면 실측 RED | 두 축이 함께 필요한 이유의 실측 |
| value 시점 기준 대조 제거 | 읽기 사이 끼어듦 회귀 | RED 1 |
| 등재된 게이트 test 에 `@Disabled` | `gateExecutionGate` | RED(「건너뛰어졌다」) |

MU1·MU2·MU2b 는 **위반 fixture 로도 영구 고정**했다(같은 규칙 값에 평가 루트만 바꿔 음성 대조).
production 소스 변이는 측정 뒤 지웠고 `git status` 빈 출력으로 확인했다.

이 라운드의 변이 셋은 `bidvector.app.rogue` 에 두었다가 측정 뒤 지웠고, 면제 클래스 변이는 되돌린 뒤
`git diff --numstat` 빈 출력으로 확인했다.

## 새 public 표면 전수 (`javap`)

- 실행기·트랜잭션 경계·use case: **포트를 꺼낼 공개 경로 0**. 경계 구현은 `inTransaction` 하나이고
  getter 가 없으며, use case 는 필드가 전부 `private final` 이라 빌려준 블록 안에서도 포트를 꺼낼 수 없다.
- **크기 분할이 만든 JVM 공개 표면 둘**(Kotlin `internal` 최상위 함수는 JVM 에서 `public static` 이다):
  판정 순서 술어 넷과 JSON 원시 읽기 여섯. 새 권한은 아니다 — 전자는 이미 공개 생성자를 가진 결과
  타입만 내고(`Applied`·`AppliedStrategy` 는 내지 못한다) 인자로 `EditSession`(internal constructor)을
  요구하며, 후자는 `JsonNode` 만 읽는다.
- `EditSessionState.WaitingForConfirmation` 에 `baseRevision` 이, `EditCommand.ProvideValue` 에
  `baseRevision` 이 붙었다 — 값 하나씩 늘었을 뿐 생성 경로는 그대로다. command 는 원래 public 타입이라
  밖에서 만들 수 있었고, 그 값을 지어내면 value 시점 대조가 `StaleRevision` 으로 막는다(알려진 제한 ⑥).
- 406 핸들러가 `ResponseEntity<Void>` 를 낸다 — 본문 없음이 계약이라 `ErrorBody` 를 만들지 않는다.
- 커널의 `apply`·`beginSession`·`expireIfDue` 는 이 slice 이전부터 JVM public 이다(Kotlin `internal`) —
  이번에 나뉜 판정 술어 넷도 같은 형태이고 새 권한을 주지 않는다.

## privacy·누출 축

- cmd: `./gradlew --no-daemon check`(그 안의 누출 게이트 — evidence 를 스캔한다)
- exit: 0
- 핵심 결과: 게이트 통과. 방어 메시지에서 제출 값을 뺐다(요청 본문을 문자열에 넣던 유일한 자리)

- cmd: 참조형 수동 스캔 `grep -rniE -f config/quality/leak-patterns.txt <이 slice 가 만진 경로>`
- exit: 0(일치 있음 — **전부 어휘 수준**)
- 핵심 결과: 일치는 필드 이름 어휘와 Testcontainers 가 만든 test 고정값뿐이다(기존 boot test 넷이
  이미 같은 형태). 실제 비밀값 리터럴 0

## clean-tree 게이트

- cmd: `git status --porcelain -- <in_scope 개별 인자>`
- exit: 0
- 핵심 결과: 빈 출력. **양성 대조**: 계약 파일 끝에 개행 하나를 더하니 ` M` 이 뜨고, 절삭을 되돌리니
  다시 빈 출력이다(비파괴 — `checkout --` 를 쓰지 않았다)
