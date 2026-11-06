# M9 / ops-migrations — 명령과 결과

마지막 산출물 커밋 **`eaa23fe8`** — **판정 SHA 는 이 파일이 적지 않는다**(동결 보고가 갖는다. evidence 커밋은 늘 실측 뒤에 오므로 여기 적으면 자리표가 남거나 매 라운드 틀린다) · base `df4976cc` · 병합 0(이 브랜치는 main 을 합치지 않았다).

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
2. **적용 DB 되돌림 리허설을 로컬에서 돌리지 못했다**(container 금지 규율). 미적용 갈래는 실측했고
   적용 갈래는 `rollback.md` §2 가 forward-fix 의 모양을 적는다 — 그 실행 실측은 verifier·CI 몫이다.
3. **릴리스 라벨과 빌드의 동일성은 여전히 보장되지 않는다**(`OPEN-6G-RELEASE-SHA-LABEL`). 이 slice 가
   닫은 것은 「값이 없거나 지어낸 값」이고, 「운영자가 댄 값이 그 jar 인가」는 배포 절차의 몫이다.
4. **outbox 의 시도 계수·시간 기반 janitor 는 만들지 않았다**(계약이 등재만 하라고 적는다).
   회수 축은 임대 기반이고 그 잔여는 `OPEN-6F10-CLAIM-OBSERVABILITY` 가 계속 든다.

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
| `CleanMigration*Test` 래칫(paper-bid V25) | 계수를 **증분 주석**으로 적었다 — `collection_run` 13+1=14 · `outbox` 11+1+1=13. paper-bid 의 `notification_inbox` 5→7 과 다른 행이라 병합은 계수의 합이다 |

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

## 등재만 (cr L-5)

`ReleaseIdentifier.MAX_LENGTH = 128` 은 **Kotlin 에만** 있다 — 열은 TEXT 이고 길이 CHECK 가 없다.
쓰는 자리가 값 객체 경유 하나라 오늘 영향이 없고, 주석도 「설정 오타를 자르는 값」이라 적어 사실과
일치한다. 저장 층으로 내리지 않은 이유는 그것이 어휘가 아니라 식별자이기 때문이다(L-1 과 같은 축).
