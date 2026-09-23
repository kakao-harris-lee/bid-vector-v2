# M6/6A-3+6F-3 checklist

## (2b) 값 획득 축 — 새 public 표면 전수 (구현 레인 실측)

scope.md가 예상한 행 + 구현 중 실제로 늘어난 행을 함께 적는다(수정 라운드 없이 1회 실측,
전부 이 slice가 처음 냄).

| 이름 | 위치 | 처분 | 실측 근거 |
|---|---|---|---|
| `EvaluationDryRunController`·`EvaluationDryRunRequest`·`EvaluationDryRunResponse` | app.http | 닫는다 | 컨트롤러는 `factory.forRequest(...).evaluate()`만 부른다(코드 자체가 그 두 호출뿐). Request는 `currentActiveBids` 하나. Response는 `EvaluateCandidatesUseCase.evaluate()` 결과를 옮길 뿐 새 계산값이 없다(분류만, D-6A3-6). |
| `EvaluationDryRunFactory` | app.wiring | 경계로 처리 | `forRequest(currentActiveBids: Int)`이 유일한 진입점, `currentActiveBids` 외 인자를 밖에서 받지 않는다(생성자는 싱글턴 협력자만). |
| `RequestCapacityPort` | adapters.evaluation | 닫는다 | 생성자 인자(`currentActiveBids`·`maxActiveBids`)가 값의 전부, `snapshot()`은 그대로 옮긴다(`RequestCapacityPortTest`). |
| `RecordingNotificationRequestPort` | adapters.evaluation | 닫는다 | `request()`는 모으기만, `requested()`는 읽기 전용 스냅숏(`RecordingNotificationRequestPortTest`). |
| `MaxActiveBids` | strategy | 닫는다 | `validate()` 경유만(`OperatorStrategy internal constructor`), `init { require(value > 0) }`. |
| `EvaluationWiring` 빈 메서드 | app.wiring | 경계로 처리(부분 실측) | `open fun`이라 구조적으로 test가 직접 부를 수 있다(컴파일 확인) — 그러나 이번 slice의 test는 그 경로를 **직접 호출하지 않고** Spring 컨테이너 경유(E2E, `EvaluationDryRunE2ETest`)로만 구동했다. 직접-호출 test는 없음 — 알려진 제한에 등재. |
| ArchUnit 규칙(`assembleCallersMustBeAllowedSet`)·정책 키(`app.allowed.assemble-callers`) | app.architecture / config | 게이트 | `ArchitectureGateTest`(production 0건) + `ArchitectureGateCatchesViolationsTest`(fixture 1건 실제로 잡힘). |
| **(예상 밖 추가 1) `PinnedStrategyRepository`** | adapters.evaluation | 닫는다 | `load()`는 고정값만 반환(delegate.load() 재호출 안 함, `PinnedStrategyRepositoryTest`), `save()`는 위임 — 새 계산 없음. |
| **(예상 밖 추가 2) `EvaluationDryRunRun`** | app.wiring | 닫는다 | `useCase`·`notifications`·`strategy` 세 필드를 옮기는 값 객체, 메서드 없음. |
| **(예상 밖 추가 3) `MaxActiveBidsNotConfiguredException`·`InvalidEvaluationRequestException`** | app.wiring / adapters.evaluation | 닫는다 | 둘 다 고정 메시지 예외(D-6A3-4·D-6A3-5 fail-closed 신호), 페이로드 없음. |
| **(예상 밖 추가 4) `EvaluationProperties`** | app.wiring | 닫는다 | `candidateCap: Int` 하나, `@ConfigurationProperties` 바인딩만(기본값 없음 — 미설정 시 기동 실패). |

**`object`/companion 주입 자리** — scope.md 예상대로 **없음**. `EvaluationDryRunResponse.Companion.from()`·
`ResultBuckets.Companion.of()`는 정적 팩토리이지만 전역 가변 상태가 없고 인자만으로 결정된다(주입 가능한
자리가 아니다).

## 알려진 제한

1. **`maxActiveBids` 편집 HTTP 경로가 없다**(scope.md 명시, `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT`, 6A-2 소관).
   이 slice는 저장된 값을 읽기만 한다 — E2E test는 DB 직접 INSERT로 전략을 준비했다.
2. **후보별 사유 상세가 응답에 없다**(`OPEN-6A3-EVALUATION-DETAIL`, D-6A3-6이 이미 결정으로 등재 — 미달이 아니다).
3. **`EvaluationWiring`의 `@Bean` 메서드를 직접 부르는 test가 없다** — Spring 컨테이너 경유(E2E)로만 구동을
   실측했다. `open fun`이라 구조적으로는 가능하지만 이번 slice가 그 경로를 쓰지 않았다.
4. **`runBlocking`의 취소 전파**(팀장 D-6A3-16 verifier 표적) — `EvaluationDryRunController.evaluate()`가
   `runBlocking { run.useCase.evaluate() }`로 suspend 경계를 다리 놓는다. `runBlocking`은 자신만의 최상위
   `Job`을 새로 열어 Spring MVC 요청 스레드의 취소·타임아웃 신호를 안으로 전파하지 않는다 — 지금은
   `UnavailableMlAnalysis`가 즉시 반환해 무해하지만(실측: `evaluate()` 왕복이 실 I/O 대기 없음), 실 ML
   배선(`OPEN-ML-ANALYSIS-WIRING`)이 이 다리를 그대로 쓰면 클라이언트 재시도·연결 종료가 gRPC 호출을 못
   끊는다. 그 배선 slice가 이 경계를 구조적 취소 전파(예: Spring 6 coroutine 지원의 suspend 핸들러 직접
   사용, 또는 `withContext`+타임아웃)로 재검토해야 한다.
5. **`app`이 main 컴파일 의존으로 `:decision`·`:qualification`·`:procurement`를 갖는다**(팀장 D-6A3-16 verifier
   표적 — ArchUnit 의존 방향과의 정합). `ArchitectureGateTest`의 `의존 방향이 한 방향이다` test(
   `layeredArchitecture()` — `DOMAIN`은 `APPLICATION`·`ADAPTERS`·`APP` 세 층 전부에게 접근을 허용한다)가
   이번 실측에서 계속 green이었다 — `app`(최외곽 층)이 domain 모듈을 직접 참조하는 것은 그 규칙이 이미
   허용한 방향이다(6A-1의 `:strategy` 선례와 같은 층 관계). 별도 정책 변경 없이 기존 규칙 안에서 닫힌다.
6. **변이 5가 실측한 게이트 사각**(팀장 계약 갱신 (4)에 이미 등재) — 새 endpoint의 200 응답에 대한 키 집합
   대조가 처음에는 없어 OpenAPI 필드 하나를 빼는 변이가 초록이었다. `4c2d041e`에서 추가해 닫았다 — 「있는지만
   보는 게이트」류의 재발.
7. **evidence 크기** — 산출물 diff(이 slice, evidence·milestone-6.md 제외) 1968줄 추가·18줄 삭제 = 1986줄.
   evidence 세 파일(scope.md는 팀장 소유, 계산 제외) 합계는 이 문서 하단 크기 확인 절 참고.

## 새 파일 ↔ in_scope 대조

`git diff --name-status febad567..HEAD`(evidence·milestone-6.md 제외) 신규 파일(`A`) 20개 전부 in_scope
glob과 대조했다 — 전부 매치. 계약 갱신 (1)~(4)가 편입한 네 항목(D-6A3-12 workflow 파일군, D-6A3-14
CleanMigration 두 파일, D-6A3-15 app/build.gradle.kts, D-6A3-16 archfixture 경로)도 갱신된 in_scope에
전부 등재돼 있다 — `git diff --name-status`와 scope.md `in_scope:` 블록을 직접 대조한 실측(수작업, 자기
검사 하네스 아님).

## 리뷰 요청 조건 점검

- [x] 구현 diff 커밋, base/head 고정 — clean-tree 게이트(아래 commands.md) exit 0 + 양성 대조 1회.
- [x] scope.md acceptance 전부 commands.md에 exit 기록.
- [x] check job 전체(부분 게이트 아님) + container job — commands.md.
- [x] 변경 fixture·정책 version 근거 — 이 slice는 `fixtures/**`를 만지지 않았다(0단계 실측 확인,
      `StrategyDraft(` 호출이 전부 named-arg라 필드 추가가 fixture를 요구하지 않았다). 정책 version:
      V16은 새 version이 아니라 기존 `operator_strategy`/`operator_strategy_revision` 표에 열 추가(DEFAULT
      없음, D-6F1-5 관례).
- [x] 알려진 제한·rollback — 위 절 + rollback.md.
- [x] 비밀값 스캔 — commands.md(참조형 grep, exit 0 = 매치 있음 → 전부 false positive로 육안 확인 기록).
