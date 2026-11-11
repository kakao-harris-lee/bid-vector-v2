# M9 / ops-migrations — 명령과 결과

마지막 산출물 커밋 **`eac93b15`** — **판정 SHA 는 이 파일이 적지 않는다**(동결 보고가 갖는다. evidence 커밋은 늘 실측 뒤에 오므로 여기 적으면 자리표가 남거나 매 라운드 틀린다) · 착수 base `df4976cc` · **내 병합 넷** — main `56bd33ea`(라운드 6, paper-bid V25) · `3101035f`(라운드 9) · `70406318`(라운드 10, award-ingest) · `07140729`(라운드 10, ml-lifecycle). **rollback 복원 원천은 마지막으로 합친 `07140729`** 이고 아래 계수는 그 구간에서 센 값이다(착수 base 부터 세면 병합이 **51** 이고, 그 가운데 이 브랜치가 만든 것은 **넷**이다 — 나머지는 main 자신의 PR 병합 열둘과 그것들이 데려온 다른 레인의 병합이다).

## 로컬 실행 (공통 규율: 표적 `--tests` + 그 모듈 lint·detekt 만)

| 명령 | 결과 |
| --- | --- |
| `:workflow:compileTestKotlin`(RED 선행) | **FAILED** — `OutboxTerminalReason` 미해결(사유 타입 없이 쓴 test) |
| `:workflow:test --tests 'bidvector.workflow.event.*' --tests 'bidvector.workflow.notification.*'` | exit 0 |
| `:workflow:test --tests '…evaluation.OpportunityAnalysisTest' --tests '…PredictionFactsTest' --tests '…SampleEligibilityTest'` | exit 0 |
| `:app:test --tests 'bidvector.app.wiring.{Collection,OpeningCollection,Relay,EvaluationCommit,OpeningPromotion}WiringTest' --tests '…OneShotRunnerGuardTest' --tests '…NotificationSweepWiringTest'` | exit 0 |
| `:workflow:·:adapters:·:app:` × `ktlint{Main,Test}SourceSetCheck` · `detekt` | exit 0 |
| `:adapters:compileTestKotlin` · `:app:compileTestKotlin` | exit 0 |
| `tools/prod-env-keys-check.sh` | exit 0 — 「키 18개(템플릿 == compose 세 파일)」, 새 키 0 |

**container test 는 라운드 1·2 에서 로컬에 돌리지 않았다**(공통 규율). 라운드 3 에서 팀장이 허용한
자리(적용 DB 리허설)와 verifier 가 지정한 재현 표적을 돌렸다 — 아래 라운드 3 표가 그 전수다.

## RED 선행 — 축마다 하나

| 축 | RED 의 모양 |
| --- | --- |
| OPS-05 | 사유 전수 test 를 먼저 써서 `:workflow:compileTestKotlin` **실패**(타입 부재) → 구현 뒤 exit 0 |
| OPS-10 | 저장소 생성자에 릴리스를 요구하자 **호출부 전수가 컴파일 실패**(adapters·app test) → 전수 갱신 뒤 exit 0. 배선 쪽 RED 는 Spring 주입 실패로 드러났다(아래 「판단이 갈린 지점」) |
| ~~DEC-08~~ | **축이 빠졌다**(계약 갱신 ①) — 당시 RED 는 「기록 port 를 요구하자 조립 셋이 컴파일 실패」였고 그 커밋은 역적용됐다 |

## 변이(바꿔치우기) — 커밋 뒤, 원복은 변이 파일만

둘을 한 번에 심고 **서로 다른 test** 가 붉어지는지로 귀속했다. 더하기가 아니라 **바꿔치우기·삭제**다.

| 변이 | 축 | 실측 |
| --- | --- | --- |
| 중복 생략의 사유를 `DUPLICATE_SKIPPED` → `DELIVERED` 로 접는다 | OPS-05 | **RED** — relay 중복 자리 하나 |
| `JdbcCollectionRunStore` 의 `release.value` 바인딩을 상수로 바꿔치운다 | OPS-10 | **RED** — `ExecutionRowReleaseTest` 둘(쓰기 자리 · 「지울 수 없다」 자리) |
| `ReleaseProperties.sha` 에 기본값 `unversioned` 를 되살린다 | 설정 | **RED** — `ReleaseWiringTest` 의 「없으면 기동 실패」 하나 |
| 통제군(원복 뒤 `--rerun`) | — | 셋 다 초록 재확인(XML 재작성 확인 — cache 적중이 아니다) |

**축마다 하나씩 있다**(cr M-2 지적 반영) — 앞 판은 OPS-10 축에 변이가 0 이었고, 빠진 DEC-08 축의
변이를 적고 있었다. 지금 남는 미실측 변이 축: 쌍↔상태 CHECK 본문과 관문 트리거 **자체**를 바꿔치우는
변이(그 둘은 거부 실측·집합 등식 test 가 덮지만 변이로는 안 쳤다) — verifier 표적으로 남긴다.

## 핵심 결정과 그 근거(diff 에 안 보이는 것)

- **V27(OPS-10) 은 표 둘이다.** OPS-10 이 지목한 넷 가운데 `notice`·`opening_result` 는 `observation_key`
  (NOT NULL FK) → `raw_observation.release_sha`(NOT NULL) 조인으로 릴리스가 이미 도달한다. 열을
  베끼면 같은 사실이 두 자리에 살고 한쪽만 바뀐다. 보존 규칙도 그 조인을 깨지 않는다
  (`raw_observation` 은 파기 대상이 아니라 마스킹 대상). 전제 셋은 `ExecutionRowReleaseTest` 가 고정.
- **설정 키를 하나로 모았다.** 「기본값 제거」만 하면 같은 개념의 키가 셋(수집 둘 + 서버 부재)이 되고
  한 호스트가 두 릴리스를 적을 수 있다. 서버 프로세스도 전략 편집으로 outbox 를 쓴다.
- **기존 행은 NULL 로 남긴다**(세 축 공통). 사유·릴리스를 되짚을 원천이 없고 지어내지 않는다 —
  「새 행·새 전이는 구조가 요구」를 트리거가 지고, CHECK 로는 기존 행 때문에 적을 수 없다.

## 판단이 갈린 지점 — 실측으로 갈렸다

- **`ReleaseIdentifier` 를 `value class` 로 두지 않았다.** 인라인 클래스는 JVM 시그니처에서
  `String` 으로 지워져 Spring 의 타입 주입이 「`java.lang.String` 빈이 없다」로 실패한다(배선 test
  일곱이 붉었다). 그 사실을 KDoc 에 적어 두었다 — 「최적화」로 되돌리면 같은 자리에서 다시 깨진다.
- **`terminal_reason` 을 NOT NULL + backfill 로 두지 않았다.** 기존 종단 행의 사유는 로그에만 갔고,
  `notice`·`opening_result` 쪽 backfill 은 더 비쌌다 — 그 표의 갱신은 **판 번호 증가와 감사 행 삽입
  트리거를 깨운다**(V2). 설계 검토의 backfill 계획을 그 사실로 접었다(design-review.md 갱신 절).

## 알려진 제한

1. **DEC-08 은 이 slice 가 닫지 않는다**(계약 갱신 ①, 팀장 결정 2026-11-06). 출하 조립의
   `MlAnalysisPort` 가 `UnavailableMlAnalysis` 고정이라 분류가 출하에서 **0 회** 돈다 — e2e 조립만
   쓰는 표를 스키마에 남기지 않는다. 받는 자리는 `OPEN-ML-ANALYSIS-WIRING` 이고 capability map
   DEC-08 행이 그 포인터와 근거를 적는다. 커널의 판정 봉투는 M1/1D 그대로 남아 있다.
2. **적용 DB 되돌림 리허설은 라운드 3 에서 한 번 돌았다**(팀장 허용 1회, 버릴 clone·버릴 클러스터) —
   `rollback.md` §5 가 그 실측이고, 그 뒤 다시 돌리지 않은 근거(병합들이 들여온 겹치는 객체 0)도 같은
   절에 있다. **앞 판의 「로컬에서 돌리지 못했다」는 라운드 1·2 의 사실이고 지금은 낡았다**(라운드 10
   정정) — 미적용 갈래는 매 라운드 실측한다.
3. **릴리스 라벨과 빌드의 동일성은 여전히 보장되지 않는다**(`OPEN-6G-RELEASE-SHA-LABEL`). 이 slice 가
   닫은 것은 「값이 없거나 지어낸 값」이고, 「운영자가 댄 값이 그 jar 인가」는 배포 절차의 몫이다.
4. **outbox 의 시도 계수·시간 기반 janitor 는 만들지 않았다**(계약이 등재만 하라고 적는다).
   회수 축은 임대 기반이고 그 잔여는 `OPEN-6F10-CLAIM-OBSERVABILITY` 가 계속 든다.
5. **마이그레이션 잠금 상한은 「기본값」이고 파일이 덮을 수 있다**(cr r4 M-1 · v r7 L-2). 연결의
   `initSql` 은 「선언을 잊음」을 덮지만 `SET LOCAL lock_timeout = '0'`·`RESET`·더 큰 값을 **명시한**
   파일은 밖이다. 그 자리를 보는 것은 관례 lint 뿐이고 그 모집단은 **기존 표 세 모양**(V18 이후의
   `ALTER TABLE`·`CREATE INDEX … ON`·`CREATE TRIGGER … ON`)이다. 오늘 선언 열둘이 전부 `'5s'` 라
   거동 차이는 0 이다.
6. **lint 가 보는 값과 `MIGRATION_LOCK_TIMEOUT` 이 등식으로 묶여 있지 않다**(cr r4 LOW). lint 는
   `'[1-9]` 로 시작하는 값만 요구하므로 `'9h'` 같은 선언도 통과한다. 등식(`= '$MIGRATION_LOCK_TIMEOUT'`)
   으로 좁히는 처방은 **이 라운드에서 시행하지 않았다** — 게이트 술어를 바꾸면 severity 와 무관하게
   표적 재검증이 따라오고(CLAUDE.md), 이 라운드의 범위는 문면·단언이다. 후속 slice 의 입력으로 둔다.
7. **출하 호출 자리(`migrateAsOwner` → `ownerMigration`)가 구조로 묶여 있지 않다**(cr r4 LOW).
   오늘 `:app` main 에 `Flyway.configure()` 자리는 **하나**뿐이고(verifier r7 표적 ①도 그 수를 쟀다),
   둘째 자리가 생기면 그것이 `initSql` 없이 migrate 할 수 있다. 그 자리의 수를 세는 census 는
   두지 않았다 — 같은 후속의 입력이다.
8. **`tools/db-rehearsal.sh` 의 주석 한 줄이 이 변경으로 낡았다**(v r7 L-3). 「`PersistenceWiring.migrate()`
   와 같은 설정(전부 기본값)」이 더는 사실이 아니다(리허설 Flyway 에 `initSql` 없음). **in_scope 밖이라
   고치지 않았다** — 격리 컨테이너라 거동 차이는 관측되지 않고, 문면만 고치는 후속으로 둔다.
9. **CI `container` job 을 통째로 재현하지는 않았다**(라운드 10). 로컬에서 되살린 것은 그 job 이
   붉었던 **술어 하나** — `tools/db-backup.sh` 의 덤프 왕복 등식 — 이고, 측정 전에 그 질의가 저장소의
   것과 같은 문면임을 대조했다. 이미지 빌드 넷·위생/취약점 게이트·compose 스모크·`RealServerIntegrationTest`
   는 돌리지 않았다. 그 자리들은 CI 몫이고, **이 slice 가 그것들을 붉게 할 이유는 관측되지 않았다**
   (마이그레이션 둘과 릴리스 인자 외에 이미지·compose 표면을 건드린 것은 `scope.md` 가 승인한 다섯뿐).

## in_scope 밖으로 나간 파일 — 전수 열거

계약의 「기본값 제거로 깨지는 배선 전수 갱신」과 팀장 지시(「운영 compose 편집이 필요하면 열거하고
진행」)에 따라 만진 파일이고, 전부 `bidvector.release.sha` 부재로 **기동·스모크가 깨지는** 자리다.

| 파일 | 왜 |
| --- | --- |
| `docker/compose.yaml` | app 서비스가 릴리스 식별자를 받는다(운영 compose 는 이 파일을 상속 — 자기 편집 0) |
| `docker/prod.env.template` | `BIDVECTOR_RELEASE_TAG` 안내에 「두 자리에 쓰인다」 추가. **키 집합 불변**(등식 검사 통과) |
| `tools/local-tls-env.sh` | 로컬 기본값을 HEAD 짧은 SHA 로 — 로컬도 자기 릴리스를 댄다 |
| `.github/workflows/ci.yml` | compose 스모크 job env 하나 + 거부 스모크의 `-e` 하나(없으면 기동이 다른 사유로 깨져 스모크가 재는 것이 사라진다) |
| `web/e2e/harness/journey-up.mjs` | `COMMON_PROPERTIES` 한 줄 — 그 하네스의 jar 기동 셋을 덮는다 |

**이 표는 다섯이고 `scope.md` 의 승인 표와 같은 집합이다**(v L-2 정정). 앞 판은 여정 하네스를
빠뜨리고 **in_scope 안인** runbook 둘을 이 표에 넣고 있었다 — runbook 은 계약 in_scope 문면
(「runbook(릴리스 식별자 주입 …)」) 안이고, `config/quality/**`·`docs/discovery/**` 도 그렇다.

## 계약 갱신 뒤 다시 잰 것 (2026-11-06 라운드 2)

| 명령 | 결과 |
| --- | --- |
| `:workflow:·:adapters:·:app: compileTestKotlin` | exit 0 |
| `:workflow:test --tests 'bidvector.workflow.{event,notification,evaluation}.*'` | exit 0 |
| `:app:test --tests 'bidvector.app.wiring.*WiringTest'` | exit 0 |
| 세 모듈 × `ktlint{Main,Test}SourceSetCheck` · `detekt` | exit 0 |
| 마이그레이션 번호 전수(V1..V27) | 결번 **V10·V11**(기존) · **V25**(paper-bid 예약) — 내 번호 V26·V27 은 중복·결번 0 |

### 이 라운드가 잡은 결함 하나 — rename 은 경로 둘이다

번호 당김을 `git mv` 로 하고 **경로 한정 커밋에 새 이름만** 넣었더니 삭제 반쪽이 색인에 남아
HEAD 트리에 마이그레이션이 넷이 됐다. 그 가운데 **버전 27 이 둘**(옛 `V27__outbox_terminal_reason`
과 새 `V27__execution_row_release`)이고 Flyway 는 같은 버전 둘을 보면 기동을 거부한다. **작업
트리는 내내 둘이라 `ls`·빌드·lint 로는 보이지 않았고**, 되돌림 리허설이 「이 경로가 HEAD 에
있는가」를 물으면서 드러났다(`fix(m9-ops-migrations): 번호 당김의 삭제 반쪽을 커밋한다`).
교훈: 경로 한정 커밋에서 rename 은 **옛 이름도 함께** 받아야 하고, 「작업 트리 clean」은 「HEAD
트리 정상」이 아니다.

## 레인 간 겹침 — 머지 때 볼 자리(팀장 공지 2026-11-06)

| 겹침 | 이 slice 의 상태 |
| --- | --- |
| `EvaluationCommitRun`(ops-code 레인의 알림함 생산자 포트) | 내 변경은 생성자 **마지막 인자 하나**(`release`)와 `JdbcOutboxPort` 생성 한 줄뿐이다 — 주입 자리가 분리돼 있어 그쪽 포트와 줄이 겹치지 않는다 |
| `OutboxCommand` 종단 생성자(PR #127 Telegram) | **의도된 컴파일 강제**다 — #127 의 dispatch 결과 → 종단 전이 자리가 사유 인자를 대야 한다. 어휘 일곱 가운데 `DELIVERY_REJECTED`·`DELIVERY_SUPPRESSED`·`DELIVERY_OUTCOME_UNKNOWN` 이 그 자리의 셋이고 새 사유가 필요하면 enum 과 V26 CHECK 를 **같이** 넓혀야 한다(집합 등식 test 가 한쪽만 넓히는 것을 막는다) |
| `release-sha` 키 제거(PR #128 `AwardCollectionProperties`) | 그 클래스에 release 키·기본값이 있으면 **제거하고 `ReleaseIdentifier` 빈을 주입**받아야 한다(이 slice 의 두 수집 갈래와 같은 모양). 운영 env 키 등식은 표적 실측했다 — `tools/prod-env-keys-check.sh` exit 0, 키 18, **새 키 0**(운영 compose 는 `compose.yaml` 을 상속하므로 `compose.prod.yaml` 편집 0) |
| `CleanMigration*Test` 래칫(paper-bid V25) | **병합으로 확인됨** — 계수는 서로 다른 행이라 합집합으로 접혔다(`collection_run` 14 · `outbox` 13 · `notification_inbox` 7). 예측이 **빗나간 자리는 계수가 아니라 본문 집합**이었다: main 이 같은 라운드에 `collection_run` CHECK **본문 집합 등식**을 새로 세웠고 거기에 V27 의 칸이 없어, 자동 병합 결과가 「개수 14 · 본문 13」으로 어긋났다(라운드 6 표가 적는다) |

## 라운드 3 — container 표적 실측(팀장 허용분 + verifier 재현)

| 표적 | 결과 |
| --- | --- |
| `:adapters:test` — `CleanMigration*`(다섯) · `OutboxTransitionSqlTest` · `ExecutionRowReleaseTest` · `RelayVocabularyDatabaseTest` | exit 0 — **OQ-1 답**: 쌍 집합 등식 test(제약 본문에서 쌍을 뽑아 Kotlin 어휘 전수와 대조)가 **초록**이다. 래칫 세 축(CHECK 개수 증분 · 열 전수 · 트리거 목록)도 이 라운드에서 실측됐다 |
| `:adapters:test` — `bidvector.adapters.retention.*` · `RelayDatabaseTest` · `RelayLeaseLossDatabaseTest` · `RelayVocabularyDatabaseTest`(v B-1 재현) | exit 0 · **131 tests · 0 실패**(fixture 둘 수정 뒤). 수정 전에는 verifier 가 **46 실패**를 실측했다 |
| `:app:test` — `ReleaseWiringTest`(v M-2 신설) | exit 0 · 3 tests |
| 적용 DB 되돌림 리허설(버릴 clone · 버릴 클러스터) | 초록 — `rollback.md` §5 가 단계별 실측을 적는다 |
| 세 모듈 `compileTestKotlin` · `ktlint{Main,Test}SourceSetCheck` · `detekt` | exit 0 |
| `web` npm lint·typecheck | **미실행** — 이 worktree 에 `node_modules` 가 없다(설치는 레인 밖 비용). `journey-up.mjs` 한 줄은 `node --check` 로 문법만 확인했다 · CI `web` job 이 정본 |

### 전수 확인 — outbox 행을 직접 쓰는 test fixture

`command grep -rnE "INSERT INTO outbox|UPDATE outbox" adapters/src/test app/src/test web` → **문장
다섯 · 파일 넷**이고 전부 관문을 지난다: `EventTestSupport` INSERT 하나(라운드 4 에서 **상태·사유·
릴리스를 받는 한 함수**로 모았다 — test 소스의 유일한 outbox INSERT 다) · `RetentionFixture`
INSERT(상태에서 유도한 사유+릴리스) · `RelayDbSupport.forceOutboxState` UPDATE(유도 사유) ·
`OutboxTransitionSqlTest` UPDATE 둘(의도된 음성 하나 + 사유를 싣는 헬퍼 하나).

앞 판이 「셋」이라 적은 것은 **고친 파일 수**였고 문장 전수가 아니었다. 라운드 2 의 표적 선택
(`RelayVocabularyDatabaseTest` 만)이 그 가운데 둘을 지나갔고 verifier 가 먼저 찾았다 — **표적을
고르면 고른 만큼만 초록이다.** 같은 함정이 라운드 3 의 app 쪽에서 반복됐다(v B-1).

## 라운드 4 — verifier not-ready(@`41989335`) 재검증

| 명령 | 결과 |
| --- | --- |
| `:app:test --rerun`(**전체** — 앞 라운드가 표적만 돌려 놓친 축) | exit 0 · **1014 tests · 0 실패**(앞 판 1012 + 출하 기동 음성·양성 둘) |
| 재현: `:app:test --tests '…AppHttpDependencyGateTest' --rerun`(고치기 **전**) | **FAILED** — `ReleaseWiring` 참조 다섯이 위반(v B-1 재현) |
| `:adapters:test --tests '…{event,persistence,relay,retention}.*' --rerun` | exit 0 · **390 tests · 0 실패**(66 클래스 — 헬퍼를 모은 뒤 사용처 전수) |
| `:adapters:`·`:app:` × `ktlint{Main,Test}SourceSetCheck` · `detekt` | exit 0 |
| 정적: 6G runbook §2-3 ↔ 공통 인자 | 키가 공통 인자 **한 자리**이고 갈래 셋이 `<공통 인자>` 로 받는다 |
| 전건 `check` | **미실행** — PR CI 가 정본(공통 규율) |

### 변이 — verifier 가 초록으로 실측한 셋을 겨눈다

| 변이 | 표적 | 결과 |
| --- | --- | --- |
| `gap_all`(V26: INSERT 갈래→UPDATE 전용 + 트리거 이벤트 UPDATE 전용 + 요구 집합에서 `DELIVERED` 제거) | `OutboxTransitionSqlTest` | **RED 1** — 새 「종단 셋 전수」 test(앞 판은 adapters 전체 초록) |
| `m9_outbox_rel_updateonly`(V27 outbox 릴리스 트리거 `BEFORE UPDATE`) | `ExecutionRowReleaseTest` | **RED 1** — 새 「릴리스 없는 새 outbox 행」 test |
| `r2_resource_default`(`app/src/main/resources/application.properties` 에 `unversioned`) | `ReleaseWiringTest` | **RED 1 · 나머지 넷 초록** — 붉은 것은 ConfigData 를 지나는 새 test 하나다(앞 판 셋은 그 파일을 보지 못한다) |
| 통제군(원복 뒤 `--rerun`, 결과 XML 삭제 후 재생성 확인) | 위 셋 전부 | 초록 재확인(adapters 22 · app 26) |

변이 파일만 원복했고(산출물은 변이 전에 커밋), `r2` 가 만든 파일은 지웠다 — 작업 트리 미커밋 0.

## 라운드 5 — 승인 전 일괄(v N-1 medium · L-a low)

| 명령 | 결과 |
| --- | --- |
| `:app:test --tests '…ReleaseWiringTest' --tests '…AppHttpDependencyGateTest' --rerun` | exit 0 · **26 tests · 0 실패** |
| `:app:` `ktlint{Main,Test}SourceSetCheck` · `detekt` | exit 0 |
| 호스트 env 대조: 같은 표적을 `BIDVECTOR_RELEASE_SHA` 를 둔 채 | exit 0 · **5/5 초록** — L-a 의 거짓 RED 가 사라졌다 |

| 변이 | 표적 | 결과 |
| --- | --- | --- |
| `r3_dispatch_default`(출하 기동점의 default properties 에 `unversioned` 한 줄) | 위 두 클래스 | **RED 1** — 새 「출하 기동점의 어느 설정 원천에도」 test. **앞 판에서는 app 전건 1014 초록이었다** |
| `r4_env_kept`(env 제거를 `remove("no-such-source")` 로 무력화 + 호스트 env) | `ReleaseWiringTest` | **RED 2** — 제거가 하중을 지는 줄임을 반대 방향으로 실측 |
| 통제군(원복 뒤 `--rerun`, XML 삭제 후 재생성 확인) | 위 두 클래스 | 26 · 0 실패 |

**왜 자리 하나를 겨누지 않는가**: 출하 기동점의 환경에는 default properties · ConfigData · 시스템
속성 · 명령행이 **한 자리에** 쌓인다. 그래서 단언은 「이 환경에 그 키가 있는가」 하나이고, 어느
층에 기본값을 되살려도 같은 줄이 붉어진다(r2 셋과 r3 가 같은 단언에 걸린다).

## 라운드 6 — main `56bd33ea` 병합(paper-bid V25 뒤)

**자동 병합 충돌 0.** 그래서 접힌 본문을 계수로 찾았다 — 겹침 경로는 **열넷**이다(팀장 공지의
일곱 + ci.yml · `CleanMigration{Privilege,}Test` · 수집 설정 둘 · runbook 둘. 공지의
`web/e2e/journey/results.spec.ts` 는 내 쪽 변경이 0 이라 겹침이 아니다).

| 확인 | 결과 |
| --- | --- |
| 양쪽 추가줄이 병합본에 있는가 | 내 93 · main 405 줄 전수 있음(없는 줄은 내가 **의도로 고친** KDoc 한 줄뿐) |
| 양쪽 삭제가 반영됐는가 | 반영됨 — main 이 지운 `queryCheckBodies()` 는 사라지고 `queryConstraintDef` 는 main 의 새 본문(0 행·중복 행 명시 실패)이다 |
| 줄 수 합집합 등식(`base + main + mine`) | 겹침 열넷 가운데 **열셋이 정확히 일치**, `CleanMigrationCheckTest` 만 +6(손 수정 그대로) |
| 한쪽만 바꾼 경로가 조용히 되돌아갔는가 | main 만 바꾼 129 경로는 병합본이 main 과 **같고**, 나만 바꾼 97 경로는 내 쪽과 **같다**(차이 0·0) |
| 마이그레이션 번호 V1..V27 | 파일 25 · 중복 0 · 결번 V10·V11(기존) · V25(paper-bid)·V26·V27 존재 |
| 등록부·정책 중복 | `gate-tests.properties` 중복 넷은 세 ref 공통(기존) · 조립 tier 36+11+35=**82** = 면제 래칫 |
| capability map | 줄 수 3858+114+26=**3998** 로 닫힘 · OPEN 행 중복은 세 ref 공통 16 종 |
| `journey-up.mjs` | main 판(+179/−6, main 자신의 계수와 같다) + 내 네 줄. `COMMON_PROPERTIES` 정의 하나 · `node --check` 통과 · **main 의 새 기동 셋도 같은 헬퍼를 지나 키를 받는다** — 기동마다 0600 설정 파일 하나를 쓰고 `--spring.config.import` 로 넘기는 그 자리다 |
| `:adapters:test --rerun`(전체) | exit 0 · **1323 tests · 0 실패** · skip 4(192 클래스). 병합 커밋에서 쟀고 그 뒤 두 커밋은 adapters 를 **건드리지 않는다**(경로 차 0) |
| `:app:test --rerun`(전체) | exit 0 · **1029 tests · 0 실패**(126 클래스). **`a14f07f1` 에서 쟀다**(그 커밋이 배선 test 를 고쳤고, 그 뒤 산출물 커밋은 없다 — cr L-3) |
| `:app:`·`:adapters:`·`:workflow:` × `ktlint{Main,Test}SourceSetCheck`·`detekt` | exit 0 |
| `tools/prod-env-keys-check.sh` | exit 0 — 키 18(병합 뒤에도 등식 불변) |
| 전건 `check` · web npm | **미실행** — PR CI 가 정본(이 worktree 에 `node_modules` 가 없다) |

### 병합이 잡은 결함 둘 — 둘 다 「계수는 맞는데 거동이 어긋난」 자리

1. **CHECK 본문 집합 ↔ 개수 축의 어긋남.** main 이 이 라운드에 `collection_run` CHECK **본문
   집합 등식**을 세웠고(`OPEN-CHECK-BODY-PRESENCE-ASSERTIONS` 처방), 자동 병합은 개수(내 14)와
   본문(main 의 열셋)을 **각자** 남겼다. 본문 하나를 더해 닫았다 — 두 축이 같은 마이그레이션을
   각자 세어야 한다.
2. **남의 새 배선 test 가 릴리스 빈 없이 떴다.** `:app:test` 전건이 둘 붉었다 — main 의
   `KonepsRollbackArgumentsTest` 가 `CollectionWiring`·`OpeningCollectionWiring` 을 띄우는데 이
   slice 가 그 배선에 `ReleaseIdentifier` 를 요구하게 만들었다(계약이 적은 **의도된 컴파일
   강제**의 실현). 다른 배선 test 일곱과 같은 관례로 빈을 꽂았다. **그 파일의 음성 test 는 빈
   부재로 초록이었다** — 사유가 바뀐 통과이고, 빈을 꽂으면 셋이 자기 사유로 돌아온다.

**내가 더한 문면 하나가 병합으로 거짓이 됐다**(스스로 찾아 고쳤다): 여정 하네스 주석의 「출하
jar 를 **세 번** 띄운다」 — 개찰 하네스 병합으로 기동이 늘었다. 계수를 지우고 「이 블록을 지나는
기동 전부」로 구조로 적었다.

## 라운드 7 — v V-1 · cr H-1·M-1·M-2·L-1~L-3·OQ-1~2

### 적용된 DB 가 있는가 — **없다**(cr OQ-1, 수정 **전에** 확인)

V26·V27 은 이 **미머지 브랜치에만** 있으므로 그 파일을 고쳐도 굳은 checksum 을 깨지 않는다. 근거
넷: ① 브랜치가 머지된 적이 없어 그 둘을 담은 이미지가 없다 ② 이 레인의 DB 실행은 전부
Testcontainers(버릴 것)와 §5 의 버릴 클러스터다 — evidence 에 상시 dev 포트가 **0 회** 나온다
③ 여정 하네스는 자기 `docker run` 으로 **자기 postgres**(`bidvector_journey`)를 띄운다 ④ 상시 dev
컨테이너는 **멈춰 있고**(2026-11-06 00:02 정지) runbook 이 적는 그 DB 의 기대 상태는 **V17** 이다.
만에 하나 적용돼 있으면 다음 migrate 가 checksum mismatch 로 서고, 처방은 `repair` 또는 볼륨 폐기다.

### 결과

| 명령 | 결과 |
| --- | --- |
| `leakPatternGate --rerun`(v V-1 수정 뒤) | exit 0 · 참조형 히트 **1 → 0** |
| `:adapters:test --rerun`(전체) | exit 0 · **1327 tests · 0 실패** · skip 4(192 클래스). **마지막 산출물 커밋 `b5e7eef0` 에서 다시 쟀다** — 형식 수정이 adapters test 를 건드렸기 때문이다 |
| `:app:test --rerun`(전체) | exit 0 · **1029 tests · 0 실패**(126 클래스). `a53e7d25` 에서 쟀고 그 뒤 커밋은 **adapters test 한 파일씩**이라 app 경로 차 0 |
| `:adapters:`·`:app:` × `ktlint{Main,Test}SourceSetCheck`·`detekt` | **처음에 붉었다** — 새 test 두 파일의 줄 형식(ktlint 11 · detekt MaxLineLength 2). 고친 뒤 exit 0 |
| `MigrationLockTimeoutTest`(census + 거동 셋) | 4 tests · 0 실패(census 0.5s · V18 12.2s · **V26 8.2s** · V19 8.3s) |
| 표적 여덟 클래스(`CleanMigration*`·`OutboxTransitionSqlTest`·`ExecutionRowReleaseTest`) | 50 tests · 0 실패 |

**`migrationOrderGate` 는 이 브랜치에 없다** — 그 이름의 Gradle task 도, 마이그레이션 번호를 세는
게이트도 없다(번호 전수는 라운드 6 이 손으로 쟀고 여기서도 다시 쟀다: 파일 25 · 중복 0 · 결번
V10·V11 · V25·V26·V27 존재). 그 게이트는 다른 레인의 일이다.

### 변이 — 이 라운드가 세운 관문마다 하나

| 변이 | 표적 | 결과 |
| --- | --- | --- |
| V26 에서 `SET LOCAL lock_timeout` 줄 삭제 | `MigrationLockTimeoutTest` | **RED 2** — census 집합 등식 + V26 거동. 거동 쪽은 **37.9 s**(초록일 때 8.2 s)로, 상한이 없으면 **기다린다**는 verifier 의 25 s 측정이 그대로 재현된다 |
| `require_release_sha()` 의 UPDATE 갈래를 **고치기 전** 상태로 | `ExecutionRowReleaseTest` | **RED 1** — 「바꿔치울 수도 없다」(RED 선행 실측) |
| outbox 릴리스 트리거를 `BEFORE INSERT` 로 좁힘 | `ExecutionRowReleaseTest` | **RED 2** — 「지울 수 없다 — outbox 행」·「바꿔치울 수도 없다」. **앞 라운드에는 이 변이가 아무것도 붉히지 않았다**(cr M-2) |
| 출하 **초기화자**가 환경에 기본값을 더한다 | `ReleaseWiringTest` | **RED 1** — v L-1 의 층이 모집단 안으로 들어왔다는 실측(앞 판에서는 초록이었을 자리) |
| 통제군(원복 뒤 `--rerun`) | 위 표적 전부 | 초록 재확인 |

### 순서가 만든 비용 하나

표적 test 를 돌린 뒤 **lint 를 미루고 커밋**해서 전건에서야 형식 위반이 드러났고, 산출물 커밋이
하나 늘었다(그래서 adapters 전건을 다시 쟀다). 공통 규율의 「표적 `--tests` + **그 모듈** lint」는
한 라운드로 묶으라는 뜻이다.

### 등재만

- **cr OQ-2 — `search_path`**: 관례는 「`SECURITY DEFINER` 에만 고정한다」다. V2 의 감사 트리거는
  `SECURITY DEFINER SET search_path = public, pg_temp` 이고, V3·V18 의 **invoker** 트리거 함수는
  두지 않는다. 새 함수 둘도 invoker 이므로 **관례와 일치**한다 — 그대로 둔다. 스키마 CREATE 권한을
  가진 역할이 생기는 날에 이론적 표면이 되고, 오늘 그 역할은 없다(앱 역할은 NOLOGIN·최소 권한).
- **cr L-2 — `USING ERRCODE`**: 거동이 같아 cr 은 등재만 권했으나, 같은 라운드가 V27 의 두 RAISE 에
  그 줄을 쓰게 되어 V26 만 다르면 **이 slice 안에서 새 비대칭**이 된다. 네 자리 모두 명시했다.

## 라운드 8 — v r6 V-1(차단) · cr r3 M-1·L-1~L-3 · v r6 L-1

### 무엇이 틀렸던가

앞 라운드는 상한을 **파일마다** 적고 그 선언을 정규식 census 로 세면서 그것을 「구조로 — 모집단을
집합으로 닫는다」라고 적었다. 그 술어는 SQL 을 파싱하지 않으므로 평범한 표기에 빗나간다 —
verifier 가 심은 15 변이 가운데 **10 이 초록**이었다(`ALTER TABLE IF EXISTS` · `public.outbox` ·
따옴표 이름 · `EXECUTE format(...)` · `LOCK TABLE` · `DROP TRIGGER` · 블록 주석 · 값 `0` · DDL
뒤에 적은 선언 · 문자열 리터럴). **구조로 지켜지던 것은 거동 test 가 있는 V26 하나였다.**

### 처방 — 기본값을 연결로 내린다

`PersistenceWiring.ownerMigration` 이 출하 마이그레이션 구성 하나를 들고
`initSql("SET lock_timeout = '5s'")` 를 건다. **표기와 무관하게, 선언을 잊어도** 모든 마이그레이션이
이 값을 **기본값으로** 깔고 시작한다. census 가 설계상 제외한 FK 축(참조 표의
`SHARE ROW EXCLUSIVE`)도 함께 덮인다.

**깔개이지 하한이 아니다**(cr r4 M-1). `SET LOCAL lock_timeout`(또는 `SET`·`RESET`)은 세션 값을
덮으므로 파일이 `'0'`·`RESET`·더 큰 값을 명시하면 그 파일은 이 값 밖이다 — 연결이 덮는 것은
「잊음」이고 「의도적 덮어쓰기」는 관례 lint 가 **기존 표 세 모양**(`ALTER TABLE`·`CREATE INDEX … ON`·
`CREATE TRIGGER … ON`, V18 이후)에 한해 본다. 오늘 선언은 열둘 전부 `'5s'` 라 거동 차이가 0 이다.
파일의 `SET LOCAL` 은 **그대로 둔다** — 같은 값이라 중복이 무해하고 고치면 checksum 이 움직인다
(cr r3 는 SQL 지적 0 으로 approve 했다).

census 는 **관례 lint 로 격하**했다 — 「파일도 관례대로 선언하는가」. KDoc·evidence·설계 검토의
「구조로/안전망」 문면을 그 범위로 고쳤고, 빗나가는 표기 목록을 KDoc 에 그대로 적었다.

### 결과

| 명령 | 결과 |
| --- | --- |
| `:adapters:test --rerun`(전체) | exit 0 · **1327 tests · 0 실패** · skip 4(192 클래스) |
| `:app:test --rerun`(전체) | exit 0 · **1030 tests · 0 실패**(127 클래스 — 새 클래스 하나) |
| `:adapters:`·`:app:` × `ktlint{Main,Test}SourceSetCheck`·`detekt` | exit 0(새 test 의 형식 위반 셋을 **커밋 전에** 고쳤다 — 라운드 7 의 교훈을 적용) |
| `leakPatternGate` | exit 0 |
| `MigrationLockBoundTest`(새 — 출하 구성 그대로) | 1 test · 0 실패(12.9s) |
| `MigrationLockTimeoutTest`(lint + 거동 셋) | 4 tests · 0 실패 |

### 변이

| 변이 | 표적 | 결과 |
| --- | --- | --- |
| `ownerMigration` 에서 `initSql` 제거 | `MigrationLockBoundTest` | **RED** · **41.7s**(초록 12.9s) — 상한이 없으면 **기다린다**. 이 한 줄이 안전망임을 그 차이가 센다 |
| V27 의 `SET LOCAL` 선언 제거 | census | **RED**(관례 lint 가 운다). 같이 돌린 `MigrationLockBoundTest`·V18·V19·V26 거동은 초록이지만 **그 초록은 이 변이를 재지 않는다**(v r7 L-1 정정) — 그 test 들은 V27 을 지나지 않는다 |
| V27 선언의 값을 `'0'` 으로 | census | **RED**(앞 판에서는 초록이었다 — cr r3 M-1) |
| V27 선언을 ALTER **뒤**로 옮김 | census | **RED**(앞 판에서는 초록이었다 — cr r3 M-1) |
| 통제군(원복 뒤 전건) | 전부 | 아래 결과표 |

**새 test 가 고른 자리**: V14 는 기존 표(`notice`)를 `ALTER` 하면서 상한을 **선언하지 않는** 관례
이전 파일이다. 파일 census 로는 영원히 닿지 않고 파일 선언으로도 막히지 않으므로, 거기서 `55P03`
이 나오면 상한을 세운 것은 **연결 하나**다. 출하 구성을 test 가 **같이** 쓴다(자기 사본이면
`initSql` 을 지워도 초록이다).

**이 test 가 재는 범위**(v r7 L-1 정정): 그 한 자리 — **실행의 첫 파일**이다. 「실행 중간 파일에도
상한이 남는가」는 레인이 재지 않았고 **verifier r7 의 프로브가 실측**했다(PR #141 의 r7 판정
코멘트 표 A·B): 같은 실행 셋째 파일 V16(선언 없음)에서 `55P03`, `initSql` 제거 변이에서 RED.
「현재·미래 전부」는 그 둘이 함께일 때의 주장이고, 그 안에서도 **명시적 덮어쓰기**는 밖이다.

## 등재만 (cr L-5)

`ReleaseIdentifier.MAX_LENGTH = 128` 은 **Kotlin 에만** 있다 — 열은 TEXT 이고 길이 CHECK 가 없다.
쓰는 자리가 값 객체 경유 하나라 오늘 영향이 없고, 주석도 「설정 오타를 자르는 값」이라 적어 사실과
일치한다. 저장 층으로 내리지 않은 이유는 그것이 어휘가 아니라 식별자이기 때문이다(L-1 과 같은 축).

## 라운드 9 — 승인 전 일괄(cr r4 M-1·M-2·LOW · v r7 L-1~L-3) + main `3101035f` 병합

거동 변경 0 을 목표로 한 라운드다. 고친 것은 **문면·단언·병합 해소**이고 SQL 은 손대지 않았다
(`git diff --name-status 457378ff..HEAD` 에 `.sql` **0**).

### 병합 — 「충돌 일곱 · 조용히 접힌 자리 셋」

팀장 전언은 「겹침 계수 확인, 손 해소 줄 0」이었으나 **그렇게 되지 않았다**. 겹치는 경로는 13 이고
그 가운데 **일곱이 content 충돌**이다(relay 조립·배선과 그 test 넷, 면제 계수 하나).

| 자리 | 해소 |
| --- | --- |
| `NotificationRelayRun` 생성자·본문 | **합집합** — 내 `release` 와 main 의 `channels`·`clock` 을 함께 받고, 본문은 main 의 구조(채널 없으면 use case 를 만들지 않는다)에 `JdbcOutboxPort(boundary, release)` |
| `RelayWiring` · relay test 넷 | 같은 합집합(인자 둘 다 · import 둘 다) |
| `EXPECTED_EXEMPT_COUNT` | 양쪽이 **다른 수**(내 82 · main 87). 숫자를 옮기지 않고 세 tier 목록의 항목 이름 합집합을 기계로 셌다 — **39 + 11 + 38 = 88**(중복 0). 검산: 82 + 6 = 88. 두 레인의 근거 문단은 둘 다 남겼다 |

**더 위험한 것은 충돌이 아니라 충돌이 **나지 않은** 자리였다.** main 의 새 호출 **셋**
(`PipelineTelegramDeliveryE2ETest` 하나 · `RelayVocabularyDatabaseTest` 둘)이 `NotificationRelayRun(`
을 `release` 없이 조립한다. 표지가 서지 않으므로 git 은 조용했고 **컴파일만이 잡았다** — 셋 다
`release = TEST_RELEASE` 를 더했다. 합집합 함정의 교과서적 자리다.

그 밖의 병합 점검(전부 실측): properties 중복 키 **0**(architecture-policy 140 키 · gate-tests 33) ·
인접 중복 줄 **0**(capability map · 운영 runbook · CI) · CI job 이름 중복 **0**(6) ·
`MigrationLockBoundTest` 의 게이트 등록부 등재 **유지** · 양쪽 레인의 면제 이름이 둘 다 목록에 있음.

### 결과

| 명령 | 결과 |
| --- | --- |
| `:adapters:test --rerun`(전체) | exit 0 · **1394 tests · 0 실패** · skip 4(202 클래스) |
| `:app:test --rerun`(전체) | exit 0 · **1055 tests · 0 실패**(128 클래스) |
| `:app:gateRegistrationGate` | exit 0 |
| `leakPatternGate --rerun` | exit 0 |
| `:adapters:`·`:app:` × `ktlint{Main,Test}SourceSetCheck`·`detekt` | **처음에 붉었다** — `ktlintTestSourceSetCheck` 둘(새 단언의 체인 넷 · 병합이 더한 import 의 사전순). 고친 뒤 **exit 0** |

전건 한 번(35m41s)에 위를 함께 돌렸다. **그 실행의 셸 종료 코드는 `echo` 의 것이라 0 이었다** —
판정은 로그의 `BUILD FAILED` 와 `Task … FAILED` 두 줄이 냈다. 표적 실행에는 보이지 않던 형식
위반이 전건에서만 드러난 것도 같은 축이다.

**전건은 병합 커밋에서 돌았고 그 뒤 형식 커밋 하나가 test 파일 둘을 고쳤다** — 그 간극을 메운다:
`MigrationLockBoundTest` 는 변이 통제군이 마지막 산출물 커밋에서 초록(1·0 실패)이고,
`PipelineTelegramDeliveryE2ETest`(import 순서만 바뀐 쪽)도 같은 커밋에서 표적 재실행 **1 · 0 실패**다.
남은 전건과 그 커밋 사이의 차이는 그 두 파일뿐이다.

### 변이 — cr r4 M-2 의 하중 전제

| 변이 | 표적 | 결과 |
| --- | --- | --- |
| **V14 리소스에 `SET LOCAL lock_timeout = '5s';` 한 줄을 더한다**(바꿔치우기 — 「다음 파일은 선언하지 않는다」를 「선언한다」로) | `MigrationLockBoundTest` | **RED 1** — `V14__notice_title.sql 가 상한을 선언하면 … expected:<0> but was:<1>`. 전제가 깨지면 **test 가 먼저 운다** |
| 통제군(원복 뒤 `--rerun`) | 같은 test | 초록 1 · 0 실패. 원복 뒤 `git status --porcelain -- <V14>` **빈 출력** |

단언은 하중 전제 **둘**을 잡는다 — ㉠ 다음 파일에 선언 0 건(`--` 주석 걷은 본문) ㉡ 그 파일의
`ALTER TABLE` 대상이 **준비가 끝난 DB 에 이미 있다**(`to_regclass`). 다음 파일의 이름은 Flyway 의
`info()` 가 고르므로 이름 규칙을 test 가 다시 적지 않는다.

## 라운드 10 — V26 를 덤프 왕복 고정점으로 (CI `container` 붉음) + main `07140729` 병합

### 원인 한 문장

`(state, terminal_reason) IN ((…), …)` 는 **진리표가 같아도 문면이 왕복에서 변한다** — 파서가 그
문법을 왼쪽으로 깊게 중첩된 OR 트리로 저장하고 `pg_get_constraintdef` 가 그 중첩을 그대로 찍는데,
덤프 텍스트를 **다시 읽을 때** 파서는 같은 OR 사슬을 평평하게 접는다. `tools/db-rehearsal.sh` 의
복원 등식 `constraints` 가 그 한 줄로 붉어졌다(제약은 그대로이고 **문면만** 다르다). 처방은 같은
쌍을 처음부터 평평한 OR 로 적어 **자기 렌더링의 고정점**으로 만드는 것이다.

### 재현 — 리허설의 **술어 그대로**, 이미지 빌드 없이

버릴 postgres:16.4 컨테이너에 마이그레이션 전수를 적용하고 `pg_dump --format=custom` → `pg_restore`
한 뒤 세 지문을 견준다. 질의는 `tools/db-backup.sh` 의 `Q_CONSTRAINTS`·`Q_TRIGGERS`·`Q_FUNCTIONS`
**와 같은 문면**이고, 측정 전에 그 사실을 저장소에서 대조했다(두 술어 모두 일치). 역할
`bidvector_app` 은 마이그레이션 자신이 만들므로(V2) 미리 만들지 않고, 역할이 클러스터 전역이라
왕복마다 컨테이너를 새로 띄운다.

| 축 | 모집단 | 결과 |
| --- | --- | --- |
| ① 왕복 불변 — HEAD(평평한 OR) | 마이그레이션 **25** 적용 · 제약 **258** · 트리거 **31** | `constraints`·`triggers`·`functions` **전부 EQUAL** |
| ② 변이 — V26 의 CHECK 를 **옛 행 값 비교로 바꿔치운다** | 같은 모집단(적재 25 개 **완주**) | `constraints` **DIFF**, 키는 `outbox.outbox_terminal_reason_state_check` **하나**. `triggers`·`functions` 는 EQUAL |
| ③ 진리표 동치 — 새 식 ↔ 옛 식 | state **6**(어휘 다섯 + 어휘 밖 하나) × terminal_reason **9**(일곱 + 어휘 밖 하나 + NULL) = **54** | 판정 동일 **54** · **어긋남 0** · 수용 **13 = 13** |
| ③′ 양성 대조 | 위에서 **식 하나를 뺀 판**을 같은 전수에 건다 | 어긋남 **1** — 전수가 실제로 차이를 집는다 |
| ④ 실 제약의 거동 | 롤백하는 INSERT **여섯**(짝 맞음 둘 · 어휘 내 짝 어긋남 · 어휘 밖 · 종단 아닌 상태 · 사유 NULL) | 여섯 **전부 기대와 같다** |

②가 「적재가 깨졌다」가 아니라 「등식이 깨졌다」임을 하네스가 구별한다 — 적재 실패와 DIFF 의
종료 코드를 가르고, 변이판도 25 개를 끝까지 적용한 뒤에야 등식을 잰다. 하네스를 고치며 **같은
함정을 두 번 밟았다**: 첫 판은 역할을 미리 만들어 V2 가 「이미 있다」로 멈췄는데 변이 쪽이 그
실패를 「음성 대조 성립」으로 읽었고, 다음 판은 `docker exec -i` 가 heredoc 을 삼켜 ④의 여섯 중
하나만 돌고 초록이 났다. 둘 다 **측정이 조용히 줄어든** 자리다.

### 그 축이 다시 열릴 자리 — 전수와 구조

| 질문 | 실측 |
| --- | --- |
| 다른 마이그레이션에 같은 모양(행 값 비교)이 남아 있는가 | 본문 **0 건**(주석의 설명 문장만 남는다) |
| 그 성질을 무엇이 지키는가 | `CheckConstraintRenderingFixpointTest` — 스키마의 **CHECK 전수**를 탐침으로 다시 세워 렌더링이 자기 고정점인지 본다. grep 이 아니라 **카탈로그**가 모집단이라 새 문법에도 선다 |
| 그 test 가 게이트 등록부에 있는가 | **있다 — 만든 커밋(`4cda0103`)이 `config/quality/gate-tests.properties` 를 같이 고쳤다**(배선 클래스가 아니라 tier 등재는 해당 없음) |
| V26·V27 이 main 에 있는가 | **없다** — `git cat-file -e origin/main:<두 경로>` 둘 다 실패하고 main 의 최고 버전은 V25 다 |

### 병합 `07140729`(PR #139 ml-lifecycle)

마이그레이션 **0 개**를 들여온다(`git diff --name-status 3101035f..07140729 -- <migration dir>` 빈
출력) — rollback §5 의 「겹치는 객체 0」 근거가 그대로 선다. 겹치는 경로는 **27** 이고, 병합이 깬
둘을 `40e30cfb` 가 고쳤다: ⓐ `AwardCollectionWiringTest` 의 수동 조립이 새로 주입되는 릴리스 빈
없이 기동 실패 ⓑ 두 레인의 면제 KDoc 합집합이 파일 크기 한도를 넘김(519 → 452, 지운 것은 지나간
계수 이력뿐이고 근거는 레인별로 남겼다).

### 결과 — 판정 대상 트리에서 전건

CI `check` job 의 명령을 **그대로**, 마지막 산출물 커밋 `eac93b15`(clean tree)에서 돌렸다.

| 명령 | 결과 |
| --- | --- |
| `./gradlew --no-daemon check` | exit **0** · `BUILD SUCCESSFUL in 32m 25s` · 392 actionable(**42 실행** · 350 up-to-date) |
| `./gradlew --no-daemon qualityBaseline` | exit **0** |
| `./gradlew --no-daemon leakPatternGate --rerun` | exit **0** — `--rerun` 이라 **cache 적중이 아니다**(1 실행) |
| `./gradlew --no-daemon :adapters:test --rerun` | exit **0** · 4m 12s · **1 실행** — 아래 「up-to-date 를 믿지 않은 자리」 |

| 모듈 | 클래스 | tests | 실패 | 오류 | skip |
| --- | --- | --- | --- | --- | --- |
| `:adapters` | 204 | 1397 | 0 | 0 | 4 |
| `:app` | 131 | 1076 | 0 | 0 | 0 |
| `:workflow` | — | 746 | 0 | 0 | 0 |
| 도메인 여섯(`decision`·`procurement`·`strategy`·`settlement`·`qualification`·`bidding`) | — | 785 | 0 | 0 | 0 |
| **합** | | **4004** | **0** | **0** | **4** |

#### up-to-date 를 믿지 않은 자리

전건 `check` 는 392 중 **350 을 up-to-date 로 건너뛰었다** — 앞 레인이 같은 SHA 에서 돌리다 세션과
함께 끊긴 실행의 결과가 남아 있었기 때문이다. CI 는 빈 러너에서 시작하므로 **그 재사용은 CI 와
같은 측정이 아니다**. 그래서 두 가지를 했다.

1. **모듈마다 「그 결과가 난 뒤 그 모듈이 불변인가」를 대조했다** — `:adapters:test` 의 결과는
   `ff918882`(award-ingest 병합) 직후의 것이고, `git diff --name-status ff918882..HEAD -- adapters/`
   가 **빈 출력**이다(그 뒤 움직인 것은 ml-engine 45 · app 2 · docs · reports 뿐).
2. 그래도 **이 slice 의 핵심 test 가 전부 `:adapters:test` 에 있으므로** 그 과제만 `--rerun` 으로
   **강제 실행**했다. 결과 XML 전부가 그 실행 시각으로 다시 쓰였고(204 클래스), 핵심 넷이 그 안에
   있다: `CheckConstraintRenderingFixpointTest` 1 · `CleanMigrationCheckTest` 11 ·
   `MigrationLockTimeoutTest` 4 · `ExecutionRowReleaseTest` 9 · `OutboxTransitionSqlTest` 15, 실패 0.
   `MigrationLockBoundTest`(1)는 `:app` 에 있고 전건 `check` 의 `:app:test` 에서 **실행**됐다.

evidence 편집은 위 측정이 **전부 끝난 뒤** 시작했다. 편집 대상은 이 디렉터리의 `.md` 둘뿐이고 어느
Gradle 과제의 입력도 아니다.
