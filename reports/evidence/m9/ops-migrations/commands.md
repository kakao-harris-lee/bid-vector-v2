# M9 / ops-migrations — 명령과 결과

판정 SHA: **`<동결 보고가 적는다>`** · 마지막 산출물 커밋 **`41358ce1`** · base `df4976cc` · 병합 0(이 브랜치는 main 을 합치지 않았다).

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

**container test 는 로컬에서 돌리지 않았다**(공통 규율 — PR CI 가 정본). 그 집합: 세 마이그레이션의
clean-migrate·CHECK·트리거·GRANT 실측(`CleanMigration*Test` · `OutboxTransitionSqlTest` ·
`ExecutionRowReleaseTest` · `BaseAmountClassificationStoreTest` · relay DB · e2e pipeline).

## RED 선행 — 축마다 하나

| 축 | RED 의 모양 |
| --- | --- |
| OPS-05 | 사유 전수 test 를 먼저 써서 `:workflow:compileTestKotlin` **실패**(타입 부재) → 구현 뒤 exit 0 |
| OPS-10 | 저장소 생성자에 릴리스를 요구하자 **호출부 전수가 컴파일 실패**(adapters·app test) → 전수 갱신 뒤 exit 0. 배선 쪽 RED 는 Spring 주입 실패로 드러났다(아래 「판단이 갈린 지점」) |
| ~~DEC-08~~ | **축이 빠졌다**(계약 갱신 ①) — 당시 RED 는 「기록 port 를 요구하자 조립 셋이 컴파일 실패」였고 그 커밋은 역적용됐다 |

## 변이(바꿔치우기) — 커밋 뒤, 원복은 변이 파일만

둘을 한 번에 심고 **서로 다른 test** 가 붉어지는지로 귀속했다. 더하기가 아니라 **바꿔치우기·삭제**다.

| 변이 | 실측 |
| --- | --- |
| 중복 생략의 사유를 `DUPLICATE_SKIPPED` → `DELIVERED` 로 접는다(어휘 바꿔치우기) | **RED** — relay 중복 자리 하나 |
| 분석에서 판정 기록 호출을 **지운다** | **RED** — 분석 기록 자리 하나 |
| 통제군(원복 뒤 `--rerun`) | **44 tests · failures 0**(XML 재작성 확인 — cache 적중이 아니다) |

container test 안의 축(사유↔상태 CHECK · 릴리스 관문 트리거 · 라벨↔규칙 결속 · 값 객체 공백 거부)은
로컬에서 변이를 **재지 못했다**(규율) — 그 축은 CI·verifier 몫이다.

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
| `docs/runbook/m6-6g-real-collection.md` · `m6-6e-operations.md` | 실행 명령의 키 이름과 속성 표 |

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
