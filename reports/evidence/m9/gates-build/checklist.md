# M9 / gates-build — 산출·알려진 제한·판정 전 점검

base `df4976cc`. 계약 `scope.md`, 설계 검토 `design-review.md`, 실측 `commands.md`.

## 산출 — 여덟 축의 처분

| 축 | 처분 | 자리 |
| --- | --- | --- |
| ① `OPEN-MIGRATION-ORDER-GATE` | **닫음** | 새 `migrationOrderGate` + `MigrationOrder` 순수 술어 + `migration-policy.properties` |
| ② `OPEN-CPD-GATE-RENAME-BYPASS` | **절반** — 낡은 리포트 축 닫음 · 이름 치환 축 **열린 채** | `CpdReportPresenceGateTask` 의 mtime 축 · `duplicate-policy.properties` 의 `normalize.*` 둘(오늘은 효과 없음, 그 사실을 정책 주석이 든다) |
| ③ `OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT` | **닫음(사각 셋)** — 수정 라운드 2 가 중첩 jar 축까지 | `jarContentGate` 입력이 Jar 타입 전수 · `jar.class-roots`·`jar.external-class-roots` · 비-공허 단언 |
| ④ `OPEN-HARNESS-GATE-TESTS-ORDER` | **닫음** | `GateRegistration.ledgerOrderViolations` + `gate-tests.properties` 블록 셋 정렬 |
| ⑤ `OPEN-KTLINT-UNUSED-IMPORTS-SILENT` | **닫음** | 새 `unusedImportGate`(모듈·included build·느슨한 스크립트 셋) + `UnusedImports` 순수 술어 |
| ⑥ `OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT` | **닫음** | 소스 층 패키지 범위 축 + `source.scoped-*` 정책 키 |
| ⑦ 완료 조건 2 「소스 축 상시 게이트 부재」 | **미구현 · 되돌려 보냄** | 없음 — 사유는 `design-review.md` ⑦ 와 아래 「판단이 필요한 것」 |
| ⑧ OPS-13 「환경 스니핑 0」 | **닫음(임계 0) — 정본은 바이트코드 층 · 모집단은 익명 클래스 포함**(수정 라운드 3) | 새 `environmentAccessGate` + `EnvironmentAccess` 순수 술어 + `bytecode.env-access.denied-members`(`*#멤버` 형태 포함). 소스 쌍 축은 1차 그물(`*.getenv` 추가) |

## 새 게이트가 찾은 기존 위반과 그 시정

**쓰이지 않는 import 169 줄 · 40 파일**(`unusedImportGate` 최초 실행). 모듈별: adapters 95 ·
workflow 45 · app 27 · procurement 2 · 그 밖 모듈·build-logic·느슨한 스크립트 0.

- adapters main 8 파일(`access/JdbcBrowserSessionStore` · `bidding/JdbcNoticeDeadlineQuery` ·
  `koneps/KonepsPageUriBuilder` · `notificationinbox/JdbcNotificationInbox` ·
  `snapshot/{FileAttemptLedger,JdbcSnapshotSource,RunStateDirectory,SnapshotWriter}`)
- adapters test 12 파일(`access/{AccessConcurrencyTest,AccessStorePersistenceTest}` ·
  `bidding/NotificationSweepCandidatesTest` · `koneps/{KonepsCallGateTest,KonepsLicenseLimitDocumentSourceTest}` ·
  `notice/ReadOnlyWitness` · `result/JdbcMyBidResultViewTest` · `retention/RetentionFixture` ·
  `snapshot/{JdbcSnapshotSourceCountsTest,JdbcSnapshotSourceSampleTest,RunStateDirectoryTest,SnapshotSourceTestBase}`)
- app main 3 · app test 5(`http/{BidPriceResponseStates,HttpTestSupport,EvaluationDryRunBidNowE2ETest}` ·
  `wiring/{CollectionWiring,OpeningCollectionWiring}` · `collection/{OpeningPromotionRunnerLockTest,SnapshotExtractionE2ETest}` ·
  `evaluation/EvaluationCommitRunnerTest`)
- procurement 2(`CollectionCallLedger` · `CollectionAttemptLedgerTest`)
- workflow main 4 · test 6(`bidding/{BidCaseWorkflow,BidCaseWorkflowTest}` ·
  `collection/{CollectOpeningResultsUseCase,SampleResolution,PromoteOpeningResultsUseCaseTest,PromoteOpeningTestSupport}` ·
  `strategy/EditSessionSnapshot` · `evaluation/{OutboxNotificationRequestPortTest,PredictionFactsTest}` ·
  `notification/RelayOutboxNotificationsTest`)
- 그 밖에 **내 리팩터가 남긴 셋**(`build-logic/.../SourceReferences.kt`)을 같은 게이트가 잡았고 ktlint 는
  같은 줄에 침묵했다 — 이 OPEN 의 문면을 현장에서 다시 확인한 자리다.

**거동 중립의 근거**: import 줄 삭제가 필요한 줄을 지웠으면 컴파일이 깨진다. 전 모듈
`compileKotlin`·`compileTestKotlin` exit 0 이고 네 모듈·build-logic 의 ktlint·detekt 가 초록이다.
`in_scope` 밖 모듈의 **production 거동 변경은 없다** — 선언 제거뿐이고 산출 클래스의 의미가 바뀌는
편집은 하지 않았다.

**마이그레이션 번호**: main 의 기존 결번 둘(10·11)은 **파일 번호를 다시 매기지 않았다** — 다시 매기면
adapters 의 적용 이력·checksum 이 바뀌어 production 거동 변경이 된다. 정책의 은퇴 목록으로 고정했다.

**`gate-tests.properties`**: 어긋나 있던 블록 셋(`app`·`decision`·`procurement`)을 정렬했다. 다중집합은
불변이고 행 순서만 바뀜을 HEAD 와 대조해 기계로 확인했다(키 집합 동일 · 다중집합 변화 0 · 순서 변화 3).

## 알려진 제한

1. **② 이름 치환 축은 열려 있다.** PMD 7.7.0 의 Kotlin CPD lexer 가 anonymize 속성을 받지 못한다
   (`createCpdLexer` 가 property bundle 을 쓰지 않는다 — 바이트코드 확인). 정책 키 둘은 배선이 옳아
   남겼고, 정책 주석 첫 문단이 「이 축은 닫히지 않았다」를 든다.
2. **② Type-3 은 범위 밖.** 판정식 자체를 바꾼 복제는 토큰 열이 달라져 정규화가 동작해도 안 잡힌다.
3. **② 낡은 리포트 축의 잔여**: 리포트를 `touch` 하면 통과한다(내용 무변). `didWork` 를 직접 보는 축은
   configuration-cache 아래에서 다른 task 의 상태를 실행 시점에 읽어야 해 쓰지 않았다.
4. **③ 면제 접두 밑은 내용 판정이 없다.** Boot launcher 클래스는 이 저장소의 소스가 아니라 대조 집합이
   없다. 면제 수를 리포트가 센다(래칫은 아니고 관측).
5. **⑤ wildcard import 는 대상이 아니다** — 도입 이름이 열거되지 않아 쓰임을 셀 수 없다.
6. **⑤ 같은 짧은 이름을 그 파일이 스스로 선언하면** import 가 쓰인 것으로 보인다(미탐 방향).
7. **⑤ 연산자·규약 면제는 FQN 집합**이다(수정 라운드 1 에서 짧은 이름에서 바뀌었다 — 앞 판 문면이
   고쳐진 사각을 그대로 적고 있었다, verifier L-2). 그 FQN 의 import 는 쓰임을 세지 않고, 「면제 ∖
   저장소 전수 도입 FQN == ∅」을 test 가 잠근다(죽은 면제 0).
8. **⑥ 허용 뿌리 목록이 두 자리에 산다** — 정책 데이터 + adapters **여덟** test 의 `ALLOWED_ROOTS`.
   어긋나면 두 층의 범위가 갈린다. 정본화(test 가 정책을 읽게)는 adapters test 술어라 out_scope.
   **수정 라운드 4 에서 이 제한이 실현됐다**: main 병합(PR #127)이 `RelayAdapterDependencyTest` 쪽에만
   `bidvector.adapters.notification` 을 더해 `:adapters:domainSourceReferenceGate` 가 붉었다. 값을
   복사해 맞췄고, **여덟 축 전부를 기계로 대조**했다(`relay` 하나만 어긋났고 나머지 일곱은 양방향
   차집합이 빈 집합). 그 대조는 **한 번 돌린 측정이지 상시 게이트가 아니다** — 다음 병합에서 같은
   어긋남이 또 날 수 있고, 그때도 전건 `check` 가 내는 자리다.
9. **⑧ 리플렉션 경로는 닫지 못한다.** 그 축은 `collection.reflection.*` 가 따로 든다.
10. **⑧ 소스 쌍 축은 글자를 센다 — 그래서 혼자서는 임계 0 을 지지 못한다**(수정 라운드 1 정정).
    앞 판은 「수신자 이름을 바꿔 가리는 경로는 좌표 축이 받는다」고 적었는데 **그것이 틀렸다**: 좌표
    축 ⓐ 는 정책이 고른 domain 모듈에서만 돌고 ⓑ 는 `bidvector.*` 자기 좌표만 본다. 환경 스니핑이
    실제로 쓰일 자리(`app`·`adapters`·`workflow`)를 받는 축이 없었다(verifier H-1 · 리뷰 HIGH 실측).
    지금 임계 0 을 지는 것은 **바이트코드 축**이고, 소스 쌍 축은 줄 좌표를 지목해 주는 1차 그물이다.
    같은 probe 의 차등: 소스 축 **2/6** · 바이트코드 축 **6/6**. `*.getProperty` 를 넣지 않은 사유
    (정당한 `Properties.getProperty`)는 그대로이고, 바이트코드 축은 선언 클래스가 완전수식이라 그
    과잉이 없다.
11. **① `@InputDirectory` 가 아니라 file collection** 이다 — 마이그레이션을 갖지 않는 아홉 모듈에 그
    디렉터리가 없어 `@InputDirectory` 는 task 가 돌기 전에 Gradle 자신이 거부한다. 경로 민감도는
    RELATIVE 이고(이름 변경이 입력 변경), 소유 모듈에서 비면 실패한다.
12. **전건은 로컬에서 돌리지 않았다** — `check`·`qualityBaseline`·container·S-20·Playwright 는 PR CI 가
    정본이다(레인 공통 규율). 로컬은 표적 test·해당 모듈 lint·게이트·변이만.
13. **③ 중첩 아카이브 축은 이름으로 견준다 — 바이트가 아니다.** 중첩 jar 는 아카이브 안에서 다시
    압축돼 있어 바이트 대조에 원본 파일이 필요하다. 그래서 「선언하지 않은 jar 는 runtime classpath
    해석 결과에 없다」는 성질에 기댄다 — **선언된 jar 를 그 이름으로 바꿔치우는 경로는 이 축이 닫지
    못한다**(그 축은 의존 검증·서명이 받는 일이고 이 slice 밖이다). 면제는 **정확 이름 집합** 하나이고
    (수정 라운드 3 에서 접두에서 바뀌었다 — 제한 17), 그 이름 밑은 내용 판정이 없다
    (`jar.external-class-roots` 의 한계와 같은 꼴). 「더하기」 경로(같은 이름의 둘째 아카이브)는 수정
    라운드 4 의 경로·다중도 축이 닫았으므로 **이 제한은 이제 바꿔치우기 하나**다.
14. **② 낡은 리포트 축은 로컬에서 `cp -p` 로 비껴간다**(verifier L-2, 정적 판독). mtime 술어라
    원본 mtime 을 보존한 복제가 `check -x cpdCheck` 에서 지나간다. CI 는 깨끗한 checkout 에서 늘 새
    리포트를 내므로 **로컬 한정**이다.
15. **③ `jarContentGate` 의 configuration-cache 문제 계수는 0 으로 쟀다**(수정 라운드 2·3).
    해석된 configuration 을 입력으로 받는 변경이 CC 를 흔들지 않았다 — `Configuration cache entry
    stored` 이고 문제 보고 줄 0 이다. `gradle.properties` 가 `problems=warn` 이라 **경고로 지나가는
    구조 자체는 그대로**이므로(0 이 아닌 날이 오면 조용하다) 계수를 라운드마다 다시 본다.
16. **⑧ 의 잔여는 리플렉션과 하위 프로세스 둘이다**(수정 라운드 3). 리플렉션은 전부터 선언돼 있고
    (제한 9), `Runtime#exec("printenv")`·`ProcessHandle.current().info()` 는 **이 축 밖**으로 판단했다 —
    환경 질의가 아니라 **프로세스 실행**이고 그 축은 **전송 표면 게이트**가 받는다
    (`collection.transport.surface-types` 가 `Process`·`ProcessBuilder`·`ProcessHandle`·`Runtime` 을
    열거하고 `TransportSurfaceGateTest` 가 쌍 등식으로 잰다 — 앞 판 문면의 「하위 프로세스 정책」은
    그런 이름의 축이 없는 말이었다, verifier L-3). 오늘 0 건이고, **이 축은 그 0 을 세지 않는다**(축
    밖이므로 0 이 1 이 되어도 이 게이트는 조용하다 — 세는 것은 전송 표면 게이트다). 거부 목록은 본래
    열거이므로 완전성을 주장하지 않는다. **이 판단은 팀장 확인이 필요한 자리로 보고했다.**
17. **③ 중첩 축의 면제는 정확 이름 하나**다. Boot 를 올리면 그 줄이 어긋나 게이트가 **붉는다** — 조용히
    넓어지는 면제보다 낫다는 판단이고, 카탈로그의 `spring-boot` 와의 등식을 test 가 잠근다. 면제 접두를
    쓰던 앞 판은 `…-evil.jar` 사칭이 통과했다(실측).
18. **이 게이트들은 `UP-TO-DATE` 로 조용해질 수 있다.** 병합 트리의 첫 재실행에서 전수 소스 주행 게이트
    넷이 UP-TO-DATE 로 돌아왔다. 선언 입력이 옳아도 그 상태의 초록은 **이번 트리의 측정이 아니다**.
    그래서 `--rerun` 으로 강제해 다시 쟀고, 「게이트가 병합분을 재판하는가」는 종료 코드가 아니라
    **움직인 수**(adapters `memberCalls` 4994 → 5042)로 봤다. 같은 함정이 다음 병합에서도 재현되므로
    **병합 뒤 재실측에는 `--rerun` 을 붙인다**.
19. **복원 원천이 착수 base 가 아니다.** 병합 뒤 `df4976cc` 는 경계로 쓸 수 없다(남의 레인 커밋까지
    센다 — 162 경로 중 82 가 내 것이 아니다). 원천·경계는 실제로 병합한 main 의 **고정 SHA** 이고
    `rollback.md` 가 그것을 든다. 라운드마다 옮긴다(지금은 다섯째 병합 `3101035f`).
20. **③ 뿌리 밖 판정은 zip 서명이다 — 포장을 바꾸면 안 보인다**(수정 라운드 4). 뿌리 밖 엔트리는 앞 네
    바이트가 zip 서명일 때만 중첩 아카이브로 센다. 그래서 ⓐ 앞에 몇 바이트를 덧붙인 zip(Boot 의
    중첩 판독은 끝에서 EOCD 를 찾으므로 여전히 읽힌다)과 ⓑ zip 이 아닌 포장은 **뿌리 밖에서** 미계수다.
    뿌리 **안**은 서명을 묻지 않고 전수라 이 제한이 없고, 뿌리 밖을 싣게 만드는 설정(런처 교체)은
    Main-Class 단언이 따로 받는다. 두 겹을 함께 뚫어야 한다는 것이 이 축의 현재 크기다.
21. **⑧ 의 명시적 허용 한 쌍은 운영자 확인 대상이다**(수정 라운드 4). 병합이 들여온
    `bidvector.app.wiring.RelayWiring -> java.lang.System#getProperty` 를
    `bytecode.env-access.allowed-callers` 로 열었다. 근거는 그 호출이 **환경에 거동을 맞추는 스니핑이
    아니라 환경을 보고 기동을 거부하는 안전 장치**(HttpClient 로그가 켜졌으면 알림 본문 누출 위험)이고
    JVM 속성을 읽지 않고는 쓸 수 없다는 것이다. 게이트 문면이 OPS-07 (a) 로 약속하던 경로를 실제로
    구현한 것이기도 하다. 대가: **임계 0 축에 허용 목록이 생겼다**. 조용히 자라지 못하게 ⓐ 와일드카드
    없음 ⓑ 대상 없는 허용은 위반 ⓒ 목록 전체를 test 가 값으로 고정 — 셋을 함께 걸었다.
22. **③·⑧ 의 task 층 변경 가운데 하나는 회귀로 잠겨 있지 않다**(수정 라운드 4, 리뷰 M-1 의 셋 중 ⓒ).
    중첩 판독기는 진짜 아카이브를 만드는 test 가, 환경 축 모집단은 등식이 받는다. 남은 하나는
    **`migrationOrderGate` 의 입력을 관례 소스 디렉터리로 되돌리는 변이**다 — `MigrationOrderTest` 는
    일부러 소스 층을 보므로 그 되돌림이 초록이다. 그 자리를 잠그려면 TestKit 이 필요하고, 이 라운드의
    범위 밖으로 두고 사실로 적는다.

23. **⑧ 의 허용은 두 층이 같은 키를 읽되 짝의 엄밀함이 다르다**(수정 라운드 4). 바이트코드 축은
    `귀속클래스->선언클래스#멤버` 전부가 맞아야 열리고, 소스 1차 그물은 **(파일이 담은 최상위 타입,
    멤버)** 로만 맞춘다 — 소스 층은 수신자의 선언 타입을 해석하지 못한다(그 `System` 이 어느 `System`
    인지 모른다). 그래서 같은 멤버 이름의 **다른** 선언 클래스를 그 타입에서 부르면 소스 층은 조용하고
    바이트코드 축이 잡는다. 임계 0 의 정본이 엄밀한 쪽이라 이 느슨함이 정본을 넓히지는 않는다.
    또 하나: 소스 층의 귀속은 **파일 이름에서 유도**하므로, 한 파일에 다른 이름의 최상위 타입을 두면
    그 타입의 호출은 허용에 걸리지 않는다(이 저장소 관례상 그런 파일은 없다).

## 판단이 필요한 것 (OPEN)

- **⑧ 의 허용 한 쌍**(제한 21) — 임계 0 축에 허용을 여는 판단은 운영자 몫이다. 대안은 ⓐ 그 호출을
  다른 모양으로 바꾸도록 str12 레인에 요청(그 거부 가드의 설계 변경) ⓑ 축에서 `System#getProperty`
  좌표를 빼기(축이 사실상 죽는다) 둘뿐이고, 둘 다 이 slice 보다 크다.

- **⑦ 완료 조건 2 의 소스 축** — 처방의 입력이 없다. ⓐ Kotlin 이 import 할 수 있는 legacy 좌표가 0
  (legacy 는 Python 저장소다) ⓑ 경로 리터럴 거부는 V2 의 **출처 인용 여섯**(KDoc 다섯 + 정책 provenance
  문자열 하나)을 지우게 만드는 과잉이다 ⓒ legacy `bid-vector` symlink 가 이 환경에 더 이상 없어 거부
  목록을 전수로 열거할 근거가 없다. **inert 한 정책 키를 남기지 않기로 했다**(② 에서 본 것처럼 「있는데
  아무것도 막지 않는 키」가 「닫혔다」로 읽힌다). Kotlin 축은 `group.forbidden`·`moduleDependencyGate`·
  `packageOwnershipGate` 가 이미 상시로 들고, Python 소스 축은 ml-engine 레인이 받는다.
- **② 의 남은 축**을 닫으려면 CPD 밖 도구 채택이거나 자체 토큰 정규화 중복 탐지 구현이다 — 별 slice ·
  범위 승인 대상이고 이 slice 에서 더 할 수 있는 것이 없다.

## 비활성화·되돌림

**정책 데이터 한 줄로 꺼지지 않는 축이 둘이다**(수정 라운드 4 정정 — 앞 판 문면 둘이 **실패하는
방향**을 적고 있었다, 리뷰 M-3).

- **① 마이그레이션 — 두 키를 함께 돌려야 한다.** `migration.module` 만 없는 모듈로 바꾸면 소유 단언이
  「그 모듈에 마이그레이션 22개가 있다」로 깨지고(수정 라운드 3, verifier L-1), `migration.classpath-location`
  만 빈 위치로 돌리면 **소유 모듈의 비-공허 단언**이 깨진다(두 키가 서로의 실패를 막지 못한다). 끄려면
  `migration.module` 을 없는 모듈로 **그리고** `migration.classpath-location` 을 빈 위치로 — 그러면
  소유자가 없고 비-소유 모듈도 아무것도 보지 못한다.
- **③ 중첩 축 — 면제를 넓히는 길이 닫혔다.** `jar.external-nested-jars` 에 이름을 더하면 ⓐ 카탈로그
  등식 test(`JarContentPolicyTest`)가 「목록은 정확히 한 항목」으로 붉고 ⓑ 더한 이름은 대상이 없어
  **죽은 면제**로 붉는다. `jar.nested-lib-roots` 를 비우는 길도 닫혔다(빈 값 거부). 이 축을 끄려면
  그 test 와 정책을 **함께** 고쳐야 한다 — 「정책 한 줄」이 아니다.

나머지는 정책 데이터 한 줄로 끈다 — ② `normalize.*=false`(오늘 거동은 어느 쪽이든 같다) ③ 아카이브
판정은 `jar.class-roots`·`jar.external-class-roots` 를 비움(단, 비우면 배포물 판정이 붉는다 — 그것이
의도다) ⑤ `source.import.syntax-fqns` 로 면제 확대 ⑥ `source.scoped-packages` 를 비움 ⑧ **두 축을
따로** — 소스 1차 그물은 `source.env-sniff.member-pairs`, **임계 0 의 정본은
`bytecode.env-access.denied-members`**(정본을 비우면 그 축이 공허해지고, 그 축소는
`EnvironmentAccessTest` 가 붉게 받으므로 그 test 의 좌표 목록도 함께 줄여야 한다).
전면 되돌림은 `rollback.md`.
