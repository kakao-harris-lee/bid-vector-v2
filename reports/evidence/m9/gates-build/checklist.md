# M9 / gates-build — 산출·알려진 제한·판정 전 점검

base `df4976cc`. 계약 `scope.md`, 설계 검토 `design-review.md`, 실측 `commands.md`.

## 산출 — 여덟 축의 처분

| 축 | 처분 | 자리 |
| --- | --- | --- |
| ① `OPEN-MIGRATION-ORDER-GATE` | **닫음** | 새 `migrationOrderGate` + `MigrationOrder` 순수 술어 + `migration-policy.properties` |
| ② `OPEN-CPD-GATE-RENAME-BYPASS` | **절반** — 낡은 리포트 축 닫음 · 이름 치환 축 **열린 채** | `CpdReportPresenceGateTask` 의 mtime 축 · `duplicate-policy.properties` 의 `normalize.*` 둘(오늘은 효과 없음, 그 사실을 정책 주석이 든다) |
| ③ `OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT` | **닫음(사각 둘)** | `jarContentGate` 입력이 Jar 타입 전수 · `jar.class-roots`·`jar.external-class-roots` · 비-공허 단언 |
| ④ `OPEN-HARNESS-GATE-TESTS-ORDER` | **닫음** | `GateRegistration.ledgerOrderViolations` + `gate-tests.properties` 블록 셋 정렬 |
| ⑤ `OPEN-KTLINT-UNUSED-IMPORTS-SILENT` | **닫음** | 새 `unusedImportGate`(모듈·included build·느슨한 스크립트 셋) + `UnusedImports` 순수 술어 |
| ⑥ `OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT` | **닫음** | 소스 층 패키지 범위 축 + `source.scoped-*` 정책 키 |
| ⑦ 완료 조건 2 「소스 축 상시 게이트 부재」 | **미구현 · 되돌려 보냄** | 없음 — 사유는 `design-review.md` ⑦ 와 아래 「판단이 필요한 것」 |
| ⑧ OPS-13 「환경 스니핑 0」 | **닫음(임계 0)** | 소스 층 수신자·멤버 쌍 축 + `source.env-sniff.member-pairs` |

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
7. **⑤ 연산자·규약 면제는 이름 집합**이다. 그 이름의 import 는 쓰임을 세지 않는다.
8. **⑥ 허용 뿌리 목록이 두 자리에 산다** — 정책 데이터 + adapters 네 test 의 `ALLOWED_ROOTS`. 어긋나면
   두 층의 범위가 갈린다. 정본화(test 가 정책을 읽게)는 adapters test 술어라 out_scope.
9. **⑧ 리플렉션 경로는 닫지 못한다.** 그 축은 `collection.reflection.*` 가 따로 든다.
10. **⑧ 수신자 쌍은 마지막 세그먼트로 견준다** — `System.getProperty` 는 쌍으로 들었지만 `*.getProperty`
    는 넣지 않았다(설정 파일을 읽는 정당한 `Properties.getProperty` 를 잡으면 과잉이다). 그래서 수신자
    이름을 바꿔 가리는 경로는 이 축이 아니라 좌표 축(별칭 import 의 원 FQN)이 받는다.
11. **① `@InputDirectory` 가 아니라 file collection** 이다 — 마이그레이션을 갖지 않는 아홉 모듈에 그
    디렉터리가 없어 `@InputDirectory` 는 task 가 돌기 전에 Gradle 자신이 거부한다. 경로 민감도는
    RELATIVE 이고(이름 변경이 입력 변경), 소유 모듈에서 비면 실패한다.
12. **전건은 로컬에서 돌리지 않았다** — `check`·`qualityBaseline`·container·S-20·Playwright 는 PR CI 가
    정본이다(레인 공통 규율). 로컬은 표적 test·해당 모듈 lint·게이트·변이만.
13. **이 게이트들은 `UP-TO-DATE` 로 조용해질 수 있다.** 병합 트리의 첫 재실행에서 전수 소스 주행 게이트
    넷이 UP-TO-DATE 로 돌아왔다. 선언 입력이 옳아도 그 상태의 초록은 **이번 트리의 측정이 아니다**.
    그래서 `--rerun` 으로 강제해 다시 쟀고, 「게이트가 병합분을 재판하는가」는 종료 코드가 아니라
    **움직인 수**(adapters `memberCalls` 4994 → 5042)로 봤다. 같은 함정이 다음 병합에서도 재현되므로
    **병합 뒤 재실측에는 `--rerun` 을 붙인다**.
14. **복원 원천이 착수 base 가 아니다.** 병합 뒤 `df4976cc` 는 경계로 쓸 수 없다(남의 레인 커밋까지
    센다 — 162 경로 중 82 가 내 것이 아니다). 원천·경계는 실제로 병합한 main 의 고정 SHA `3ad4f3f6`
    이고 `rollback.md` 가 그것을 든다.

## 판단이 필요한 것 (OPEN)

- **⑦ 완료 조건 2 의 소스 축** — 처방의 입력이 없다. ⓐ Kotlin 이 import 할 수 있는 legacy 좌표가 0
  (legacy 는 Python 저장소다) ⓑ 경로 리터럴 거부는 V2 의 **출처 인용 여섯**(KDoc 다섯 + 정책 provenance
  문자열 하나)을 지우게 만드는 과잉이다 ⓒ legacy `bid-vector` symlink 가 이 환경에 더 이상 없어 거부
  목록을 전수로 열거할 근거가 없다. **inert 한 정책 키를 남기지 않기로 했다**(② 에서 본 것처럼 「있는데
  아무것도 막지 않는 키」가 「닫혔다」로 읽힌다). Kotlin 축은 `group.forbidden`·`moduleDependencyGate`·
  `packageOwnershipGate` 가 이미 상시로 들고, Python 소스 축은 ml-engine 레인이 받는다.
- **② 의 남은 축**을 닫으려면 CPD 밖 도구 채택이거나 자체 토큰 정규화 중복 탐지 구현이다 — 별 slice ·
  범위 승인 대상이고 이 slice 에서 더 할 수 있는 것이 없다.

## 비활성화·되돌림

게이트마다 정책 데이터 한 줄로 끈다 — ① `migration.module` 을 없는 모듈로 ② `normalize.*=false`(오늘
거동은 어느 쪽이든 같다) ③ `jar.class-roots`·`jar.external-class-roots` 를 비움(단, 비우면 배포물 판정이
붉는다 — 그것이 의도다) ⑤ 정책 키의 면제 집합 ⑥ `source.scoped-packages` 를 비움 ⑧
`source.env-sniff.member-pairs` 를 비움. 전면 되돌림은 `rollback.md`.
