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

**test 전용 Spring 구성의 production 미유출 실측(수정 라운드 1, D-6A3-17(a) 거동 다리)** —
`BidNowFakeMlAnalysisTestConfiguration`(`@Profile("evaluation-bidnow-fake")`)은 이 라운드가 새로 연 test 전용
배선이라 (2b) 대상이다. 이 profile을 활성화하지 않는 기존 E2E(`EvaluationDryRunE2ETest`·
`ProductionAssemblyAuthAuditTest`)를 재실행해 두 곳 모두 여전히 `UnavailableMlAnalysis`만 응답한다는 것(review
판정 유지, bean override 없음)을 확인했다 — production 배선(`EvaluationWiring`)에 새 주입 자리가 열리지 않았다.

**`object`/companion 주입 자리** — scope.md 예상대로 **없음**. `EvaluationDryRunResponse.Companion.from()`·
`ResultBuckets.Companion.of()`는 정적 팩토리이지만 전역 가변 상태가 없고 인자만으로 결정된다(주입 가능한
자리가 아니다).

## 알려진 제한

1. **`maxActiveBids` 편집 HTTP 경로가 없다**(scope.md 명시, `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT`, 6A-2 소관).
   이 slice는 저장된 값을 읽기만 한다 — E2E test는 DB 직접 INSERT로 전략을 준비했다.
2. **후보별 사유 상세가 응답에 없다**(`OPEN-6A3-EVALUATION-DETAIL`, D-6A3-6이 이미 결정으로 등재 — 미달이 아니다).
3. **`EvaluationWiring`의 `@Bean` 메서드를 직접 부르는 test가 없다** — Spring 컨테이너 경유(E2E)로만 구동을
   실측했다. `open fun`이라 구조적으로는 가능하지만 이번 slice가 그 경로를 쓰지 않았다.
4. **`runBlocking`의 취소 전파 — verifier L-6 재실측으로 서술 정정(수정 라운드 1).**
   `EvaluationDryRunController.evaluate()`가 `runBlocking { run.useCase.evaluate() }`로 suspend 경계를 다리
   놓는다. 이전 판은 「`runBlocking`이 취소 신호를 안으로 전파하지 않는다」고 적었으나 부정확했다 — **실측
   결과 요청 스레드가 interrupt되면 취소는 전파된다**(안쪽 코루틴은 `JobCancellationException`, 바깥은
   `InterruptedException`을 받았고 531ms에 끝났다). **전파되지 않는 것은 클라이언트 연결 종료**다 — servlet
   컨테이너가 그 경우 스레드를 interrupt하지 않기 때문이다. 「오늘 무해」라는 결론 자체는 맞지만 이유가
   다르다: 오늘 무해한 것은 취소가 전파되지 않아서가 아니라 **어떤 포트도 실제로 suspend하지 않기
   때문**이다(JDBC 동기 호출, `UnavailableMlAnalysis`의 즉시 반환). 실 ML 배선(`OPEN-ML-ANALYSIS-WIRING`)이
   이 다리를 그대로 쓰면 클라이언트 연결 종료가 gRPC 호출을 못 끊는 문제는 여전히 남는다 — 그 배선 slice가
   클라이언트 연결 종료 축의 구조적 취소 전파(예: `withContext`+타임아웃)를 재검토해야 한다.
5. **`app`이 main 컴파일 의존으로 `:decision`·`:qualification`·`:procurement`를 갖는다**(팀장 D-6A3-16 verifier
   표적 — ArchUnit 의존 방향과의 정합). `ArchitectureGateTest`의 `의존 방향이 한 방향이다` test(
   `layeredArchitecture()` — `DOMAIN`은 `APPLICATION`·`ADAPTERS`·`APP` 세 층 전부에게 접근을 허용한다)가
   이번 실측에서 계속 green이었다 — `app`(최외곽 층)이 domain 모듈을 직접 참조하는 것은 그 규칙이 이미
   허용한 방향이다(6A-1의 `:strategy` 선례와 같은 층 관계). 별도 정책 변경 없이 기존 규칙 안에서 닫힌다.
6. **변이 5가 실측한 게이트 사각**(팀장 계약 갱신 (4)에 이미 등재) — 새 endpoint의 200 응답에 대한 키 집합
   대조가 처음에는 없어 OpenAPI 필드 하나를 빼는 변이가 초록이었다. `4c2d041e`에서 추가해 닫았다 — 「있는지만
   보는 게이트」류의 재발.
7. **evidence 크기(수정 라운드 1 재실측, verifier L-3 시정 — 「이 문서 하단 크기 확인 절」 부재 자체를
   고친다)** — 산출물 diff(이 slice, evidence·milestone-6.md 제외) `git diff --stat febad567..HEAD` =
   3154줄 추가·49줄 삭제(파일 55개: A 25·M 30). evidence 네 파일(scope.md 387 + checklist.md 129 +
   commands.md 101 + rollback.md 84, `wc -l` 실측, milestone-6.md 제외) 합계 701줄 — 산출물(3154줄
   추가분)보다 작다(크기 게이트 통과).
8. **`PinnedStrategyRepository.save()`가 `Nothing`을 반환하는 것 자체는 실행으로 잴 수 없다**(D-6A3-18,
   PinnedStrategyRepositoryTest KDoc에도 같은 근거) — `AppliedStrategy`의 생성자가 `workflow` 모듈
   `internal`이라 `adapters`(그리고 `app`도 마찬가지 — internal은 모듈 경계다) 밖에서는 그 값을 지을
   방법이 없다. 반환 타입 `Nothing`(정상 반환 경로가 없는 시그니처) 자체가 컴파일 층의 증거다 — 실행
   test는 이 gate 밖(`NoticeIdTest`의 `NoticeRound` 폐쇄와 같은 관례).
9. **in_scope 밖 파일 5개(수정 라운드 1, 발견 즉시 보고)** — D-6A3-17(a)·(c)의 양성 대조 fixture 다섯
   (`app/src/test/kotlin/bidvector/archfixture/violating/app/RogueNotificationPortImplementor.kt`·
   `RogueOutboxNotificationReferencer.kt`·`RogueOutboxPortReferencer.kt`·`RogueMlGatewayReferencer.kt`·
   `app/src/test/kotlin/bidvector/archfixture/violating/app/http/RoguePortBypassReferencer.kt`)는
   scope.md의 `app/src/test/kotlin/bidvector/archfixture/violating/evaluation/**`(D-6A3-16이 편입한
   경로, `evaluation` 서브패키지 고정)와 다른 서브패키지(`app`·`app.http`)에 있어 그 glob에 안 걸린다.
   `app/src/test/kotlin/bidvector/app/**`도 `bidvector.archfixture.*`가 아니라 `bidvector.app.*`만 매치해
   역시 안 걸린다. 팀장 지시("양성 대조 fixture는 `archfixture/violating/**` 관례를 따른다")와 기존
   D-6A3-9 fixture(`archfixture/violating/evaluation/RogueAssembleKernelCaller.kt`)의 **같은 관례를
   다른 서브패키지로 확장**한 것이지만, scope.md `in_scope:` 블록의 리터럴 glob은 `evaluation` 하나만
   적는다 — **사후 흡수가 필요하다**(D-6A3-15·16과 같은 절차: 결과는 옳으나 절차는 「멈추고 보고」였어야
   한다). test 전용·production classpath 미도달(빌드 산출물로 실측)이라 위험은 낮지만, scope.md
   `in_scope:`를 `app/src/test/kotlin/bidvector/archfixture/violating/**`(하위 전부)로 넓히거나
   `.../app/**`·`.../app/http/**` 두 줄을 추가하는 계약 갱신이 필요하다 — 오케스트레이터 보고 사항.

## 새 파일 ↔ in_scope 대조

`git diff --name-status febad567..HEAD`(evidence·milestone-6.md 제외) 신규 파일(`A`) 25개·변경 파일(`M`) 30개
전부 in_scope glob과 대조했다(수작업, 자기 검사 하네스 아님). **M 30개는 전부 매치.** **A 25개 중 20개
매치, 5개 불일치** — 위 알려진 제한 9의 fixture 다섯이다. 계약 갱신 (1)~(4)가 편입한 네 항목(D-6A3-12
workflow 파일군, D-6A3-14 CleanMigration 두 파일, D-6A3-15 app/build.gradle.kts, D-6A3-16 archfixture
경로)은 갱신된 in_scope에 전부 등재돼 있다.

## D-6A3-14 — V16 전/후 CleanMigration 두 test 의 RED→GREEN(verifier L-4 시정)

`CleanMigrationColumnTest`·`CleanMigrationCheckTest`의 `max_active_bids` 추가(컬럼 2·CHECK 2, 이전 라운드
커밋)가 실제로 게이트 역할을 하는지 버릴 worktree에서 **역방향 변이**로 실측했다 — V16을 지우는 대신, 이
두 test의 기대 목록에서 D-6A3-14 추가분만 되돌렸다(컬럼 목록에서 `max_active_bids` 행 삭제,
CHECK 개수를 16→15·15→14로 되돌림). DB에는 V16이 그대로 적용된 상태이므로 이것은 "기대가 실제보다 적게
세는" 변이다.

- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.persistence.CleanMigrationColumnTest'
  --tests 'bidvector.adapters.persistence.CleanMigrationCheckTest'`(버릴 worktree, 위 되돌림 변이 적용)
- exit: 1 — 둘 다 FAILED. `CleanMigrationCheckTest`: `operator_strategy`=16·`operator_strategy_revision`=15
  실제 vs 기대 15·14(불일치). `CleanMigrationColumnTest`: `Some elements were unexpected: [ColumnSpec(table=
  operator_strategy, column=max_active_bids, ...), ColumnSpec(table=operator_strategy_revision, column=
  max_active_bids, ...)]`.
- 결론: 두 test는 V16의 실제 컬럼·CHECK를 정확히 세고 있다(공허한 통과가 아니다) — 원복(비파괴, diff 0줄)
  확인 후 정상 상태로 복귀.

## OPEN 표 이관

- **privacy R-2(합성 `toString()` 원문 노출 위험)** — `WatchSubject`·`NoticeTitle`류의 합성 `toString()`이
  원문을 낸다는 성질은 이 slice에서 새 출력 경로를 얻지 않았다(privacy-gate ③-④ 실측: 로거·`println`·
  `printStackTrace` 0건). `OPEN-6F-ASSEMBLY` 재확인 ④가 이 우려를 실었지만 그 OPEN 항목 자체는 scope.md에서
  이미 「수령·닫음(dry-run)」이다 — 그래서 이 잔여 우려를 그 안에 두지 않고 **로거를 처음 들이는 slice 또는
  `OPEN-ML-ANALYSIS-WIRING`**(scope.md OPEN 표에 이미 신설 등재)으로 옮겨 적는다. 둘 중 먼저 오는 slice가
  재확인한다.
- **contract-keeper `OPEN-6A3-EVALUATION-DETAIL` 권고** — 후보별 사유 상세 응답을 열 때는 D-6A1-38 평탄
  게이트를 무제한 재귀 허용이 아니라 **「깊이 정확히 1」**(해소한 스키마 하나에만 기존 스칼라 술어를 적용,
  `$ref`·object·object 배열·배열의 배열·`additionalProperties`는 해소 스키마 안에서도 여전히 RED) 방식으로
  넓히는 것을 권고안으로 유지한다. scope.md OPEN 표에 이미 신설 등재돼 있다 — 이 checklist는 그 권고의 구체
  방식(깊이 1)만 보충한다.

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
