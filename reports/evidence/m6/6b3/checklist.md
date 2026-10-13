# M6/6B-3 — checklist

판정 SHA 후보 `5c622296` · base `e2232a75` · acceptance·변이 실측은 `commands.md`
(`575674d2` 에서 한 번 동결을 보고한 뒤 팀장 지시로 test 셋이 늘었다 — 그 사이 production diff 0).

## 리뷰 요청 조건

| 조건 | 상태 |
|---|---|
| 구현 diff 가 커밋되어 base/head 고정 | 산출물 커밋 9(`c7ea5b7b`…`5c622296`) · in_scope 밖 변경 0(`architecture-policy.properties` 는 D-6B3-2 로 확정) |
| 관련 test/lint/type/architecture/contract 통과 | `check --rerun-tasks` **350/350 실행** exit 0 · `qualityBaseline --rerun-tasks` exit 0 |
| 변경된 fixture·정책 version 의 근거 | 정책 값 `RETENTION_POLICY`(90일·일괄 상한)의 출처는 운영자 결정 2026-09-26 ② + `scope.md` A-1~A-4 이고 그 문면이 `source` 에 적혀 있다. fixture 는 신설이고 PII 자리값이 형태를 흉내내지 않는다 |
| 알려진 제한과 rollback·비활성화 | 아래 「알려진 제한」 · `rollback.md` |

## 설계 검토 (2) 우회 ↔ 무엇이 막는가

검토가 든 우회 10 각각에 **게이트/test** 를 붙이고, 그것이 실제로 막는지는 변이로 쟀다
(변이 번호는 `commands.md` 표).

| # | 우회 | 막는 것 | 변이 |
|---|---|---|---|
| 1 | 앱 풀 연결로 원문 UPDATE | ⓐ GRANT 미부여(권한 행렬 test — 유효 권한으로 본다) ⓑ 트리거의 역할 술어. ⓑ 는 **GRANT 를 트랜잭션 안에서 실제로 주고** 재서, 뒤 마이그레이션이 권한을 주더라도 남는 방어임을 실측했다 | M-8 |
| 2 | 소유자로 다시 UPDATE 해 원문 복원(값→값·값→NULL) | 트리거 관문 ③ — 표지가 NULL→값으로 **한 번만** | M-9 |
| 3 | 마스킹에 다른 열 변경을 끼워 넣기 | 트리거 관문 ④ — 마스킹 열·표지를 뺀 **행 전체 jsonb 비교**(열 이름 열거가 아니라 V19 의 새 열도 자동 포함) | M-10 |
| 4 | 시계를 미래로 → 전부 파기 | 정책 불변식(보존 기간 0·음수 거부 → cutoff 가 현재·미래가 되는 입력을 받지 않는다). 시계 위조 자체는 **비방어**(설계 검토 (0)) | 정책 test 3 |
| 5 | outbox 미발송 행 파기로 알림 소실 | 파기 조건이 **종단 상태 집합**(`DELIVERED`·`ISOLATED`)이고, 상태 다섯을 전부 심어 **둘만** 사라지는 것을 잰다. 어휘 자체는 CHECK 제약에서 읽어 등식으로 묶는다 | M-2·M-3 |
| 6 | 부모를 먼저 지워 FK 실패 → 부분 파기 + 실패 종료 | ⓐ 순서를 **FK 간선에서 위상 정렬**로 도출 ⓑ 부모 파기에 「살아 있는 비-CASCADE 자식」 가드를 카탈로그에서 생성해 단다(기한 안쪽 자식이 있으면 부모를 남긴다) ⓒ 그 대가가 **수렴**함을 잰다 — 자식이 기한을 넘는 다음 run 에서 같은 run 안에 둘이 함께 사라진다 | M-12 · M-5 |
| 7 | 새 V19 표가 분류 없이 들어옴 | 분류 ↔ **DB 카탈로그** 집합 등식. 두 겹이다 — 빌드에서 test 가 붉고, 런타임에서는 러너가 **아무것도 지우지 않고** 거부한다(종료 코드 4) | M-4 |
| 8 | 보존 러너를 수집·relay 와 같은 프로세스에 켬 | 기존 `OneShotRunnerGuard`(`ApplicationRunner` 빈 **정의**가 둘이면 기동 실패) + 배선 test(켜도 러너 빈은 1) | — (기존 게이트) |
| 9 | 러너 둘을 동시에 띄워 이중 계수·교착 | 전용 키의 DB advisory lock — 둘째는 `LeaseBusy`(종료 코드 3)이고 한 행도 건드리지 않는다. **그 키가 relay 임대 키와 겹치지 않음**을 「동시에 쥘 수 있는가」로 양방향 실측(상수 비교가 아니다 — relay 키 하나는 `private`) | M-13 · M-16 |
| 10 | 마스킹 실패를 원문 그대로 두고 표지만 찍기 | fail-closed — 형태 불일치는 **값 전체를 표지로 대체**하고 **따로 센다**(종료 코드 2) | M-7 |

추가 1 — **allow-list 를 그 행의 투영 열로 둔 선택의 근거가 사라지는 것**: 그 근거는 「production 에서 `raw_observation.payload` 를 읽는 자리가 0」이고, 읽기가 생기면 포함 검사가 필요해진다. 그 사실 자체를 기계 수집으로 잠갔다(공허 대조 둘 + 음성 대조 하나) — 읽기가 생기면 RED(M-15).

추가 2 — **allow-list 반전이 deny-list 로 되돌아가는 것** — 상류가 새 담당자 키를
더하면 열거는 조용히 열린다. 남길 키의 출처를 **그 행의 투영 열**로 두어 키 목록을 두 벌 두지
않았고, 「미지 키는 이름을 몰라도 빠진다」를 test 로 잠갔다(M-6).

## (2b) 값 획득 축 — 새 public 표면 전수와 그것이 허락하는 것

| 표면 | 밖에 무엇을 허락하는가 | 판정 |
|---|---|---|
| `RetentionRunner`(유일한 빈) | 기동 시 파기·마스킹 실행 | 경계로 처리 — `mode=once` 없이는 **빈이 아니다**(test) · 다른 일회 러너와 동거 불가 |
| `RetentionWiring` | 소유자 권한 연결 **생성** | 경계로 처리 — 소유자 `DataSource`·저장소·use case 가 **모두 빈이 아님**을 test 가 잰다(빈 0). `PGSimpleDataSource` 를 참조하는 production 클래스 집합 == {`PersistenceWiring`, `RetentionWiring`}(ArchUnit 등식) |
| `JdbcRetentionStore`(public 생성자) | 소유자 `DataSource` 를 받아 파기 능력 생성 | **경계로 처리 — 실측 행.** 근거는 「소유자 `DataSource` 를 손에 쥔 자만 이 생성자로 무엇을 할 수 있다」이고, 그 쥔 자의 집합이 닫혀 있음을 ArchUnit **등식**으로 잰다: `PGSimpleDataSource` 를 참조하는 production 클래스 == {`PersistenceWiring`, `RetentionWiring`}(셋째가 생기면 RED). 그 위에 쓰기는 임대 클로저 안에서만 손에 들어오고, 모든 쓰기 함수가 `RetentionCutoff` 를 요구한다(그 생성자는 `workflow` 밖에서 호출 0) |
| `RetentionCutoff` | cutoff 값 **읽기** | 생성자가 `workflow` 의 `internal` — **cutoff 생성자를 부르는 production 클래스가 `workflow` 밖에 0**(ArchUnit, 바이트코드). `now` 를 cutoff 로 넘기는 호출이 컴파일되지 않는다 |
| `PurgeExpiredData`·포트 넷·보고·열거 | 값과 계약 | 능력 없음 |
| `RETENTION_POLICY`·`RetentionPolicyData` | 보존 기간·일괄 상한 읽기 | 생성 시 불변식(0·음수·일괄 0 거부) |
| `RetentionExitCode`·로그 줄 facade | 종료 코드·문자열 | 무해. 로그에 **행 값이 없음**을 test 가 잰다(예외 자리값이 줄에 없다) |
| V18 `pii_masked_at` 열 | 마스킹 표지 | 트리거 네 관문 · 열 등재(`CleanMigrationColumnTest`) · 트리거 → **함수 쌍** 등재(몸통 교체가 이름 집합에 보이지 않던 자리) |
| 분류 레지스트리(`internal`) | 읽기 | 데이터. 스키마와 집합 등식 |

## 면제 수 래칫 46 → 51 — 다섯 각각의 사유

`AppHttpDependencyGateTest` 의 면제 목록 크기는 래칫이다(새 이름이 눈에 띄지 않게 늘지 않게).
다섯이 늘었고 **전부 HTTP 로 닿지 않는 자리**다 — 컨트롤러·② 층이 이 타입을 참조하면 의존 방향
규칙이 따로 붉는다.

| # | 클래스 | 층 | 왜 면제인가 |
|---|---|---|---|
| 1 | `bidvector.app.wiring.RetentionWiring` | ① 부팅·배선 | 조립이 일이다 — 어댑터 구체 클래스(`JdbcRetentionStore`)와 소유자 `DataSource` 를 쥔다. ① 층은 전면 면제이고, 이 클래스가 HTTP 층에 없음은 같은 test 의 다른 단언이 잰다 |
| 2 | `bidvector.app.retention.RetentionRunner` | ③ 러너 레인 | `ApplicationRunner` 구현. use case 를 부르는 유일한 production 자리이고 cron 이 프로세스로 돌린다(HTTP 진입점 없음) |
| 3 | `bidvector.app.retention.RetentionRunnerKt` | ③ | 그 파일의 top-level 함수(`exitCodeOf` — 판독 실패 수를 종료 코드로 옮기는 **순수 함수**). 러너 안에 인라인하면 정책표 변이로 직접 칠 수 없어 뽑았다(relay 의 `RelayBootDecisionKt` 와 같은 사유) |
| 4 | `bidvector.app.retention.RetentionExitCode` | ③ | 종료 코드 열거. 값과 이름이 한 쌍으로 간다 |
| 5 | `bidvector.app.retention.RetentionLinesKt` | ③ | 로그 줄 facade. 행 값을 싣지 않음이 test 로 잠긴다 |

`RetentionWiring$Companion`(이름표 상수)은 중첩 클래스라 이 목록의 대상이 아니다(게이트가 최상위
클래스만 센다 — 면제가 실재하는 클래스만 가리키는지는 같은 test 의 `exempt - allTopLevel` 단언이 잰다).

## 운영자 결정이 실제로 선 자리

| 결정 | 어디서 서는가 |
|---|---|
| A-1 파기 주체 = 소유자 비풀링 연결 · 앱 역할 경계 무변경 | V18 에 **GRANT 문 0** · 배선이 풀을 받지 않는다 · 권한 행렬 test 무변경 |
| A-2 기준 시각 = 적재 시각 축 하나 | 레지스트리의 시각 열이 **실존하고 NOT NULL 인 TIMESTAMPTZ** 임을 카탈로그로 실측(nullable 이면 그 행이 영원히 남는다) |
| A-3 `raw_observation` 마스킹만 · 표본 보존 | 파기 목록에 없다(행 수 불변 test) · 투영 열 불변 · `notice_audit` 은 D-6B3-1 로 구멍 없음(그 표의 마스킹 UPDATE 도 거부되는 것을 잰다) |
| A-4 `outbox` 종단만 · `edit_session` 포함 · 제외 여덟 | 상태 다섯 중 둘만 사라짐 · `edit_session` 시각 단일 술어 · 제외 표 행 수 불변 |
| D-6B3-1 ④ 포함 실측 | `commands.md` 「D-6B3-1 ④」 세 축 |

## 알려진 제한

1. **`outbox` 의 `FAILED` 행은 영구히 남는다** — 종단이지만 「미발송」이라 A-4 의 제외에 들어간다.
   잃은 알림의 유일한 흔적이라 남기는 쪽을 골랐다. 그 행 수가 자라면 처분이 결정 축이다.
2. **기한 안쪽 자식이 있는 부모는 그 run 에서 남는다**(우회 6 ⓑ 의 대가). 자식이 기한을 넘는
   다음 run 에서 함께 사라진다.
3. **실패한 run 의 부분 계수는 로그에 남지 않는다** — 예외는 원인 코드와 종료 코드로만 나간다
   (앞서 커밋된 일괄은 남고 다음 run 이 이어받는다).
4. **「이미 마스킹된 행 건너뜀」은 두 겹이고 각각을 단독으로 잠그는 test 가 없다**(M-14a·M-14b 생존).
   test 가 잠그는 것은 거동이고, 되돌릴 수 없음의 DB 층 보장은 트리거가 독립으로 진다(M-9).
9. **`payload` 읽기 자리 0 의 술어는 SQL 문자열을 본다** — 질의가 다른 형태로 조립되면(표 이름을
   변수로 넘기는 보존 경로처럼) 그 술어의 모집단 밖이다. 공허 대조 둘과 음성 대조로 「술어가
   아무것도 못 보는 상태」만 막았고, 모든 조립 형태를 덮지는 않는다.
5. **운영 DB 에서 한 번도 돌리지 않았다.** 모든 실행이 Testcontainers 위에서다 — 운영 실행은
   별도 승인 사항이고 첫 실행의 부풀음(bloat)·소요는 측정되지 않았다.
6. **수집 시점 마스킹은 하지 않는다** — 원문은 90일 동안 담당자 PII 를 가진다(결정 문면 그대로).
7. **시계는 신뢰 앵커다**(설계 검토 (0) 비방어). 소유자 자격을 가진 주체의 임의 SQL 도 비방어이며
   그 주체는 트리거를 DROP 할 수 있다(`OPEN-6E2A-OWNER-CREDENTIAL-IN-APP`).
8. **TDD 순서 이탈** — `workflow` 층은 test 와 구현을 같은 턴에 써서 첫 실행이 GREEN 이었다
   (RED 먼저가 아니다). 보상으로 그 층의 단언마다 변이를 적용해 RED 를 실측했다(M-2w·M-4·M-12).
   `adapters`·`app` 층은 구현이 이미 있는 상태에서 test 를 더했으므로 같은 성질이고, 그 층의
   잠금은 전부 변이로 쟀다.

## 받은 OPEN 의 처분

| OPEN | 처분 |
|---|---|
| `OPEN-6F6-PROFILE-RETENTION`(`operator_profile`) | **닫힘** — 싱글턴 설정이라 시간 파기 제외로 분류(A-4). 「언제 지우는가」의 답은 「지우지 않는다, 그 이유는 레지스트리에 적혀 있다」 |
| `OPEN-6F5A-RETENTION`(요건 표 둘) | **닫힘** — 헤더는 `created_at` 파기, 행은 FK CASCADE 로 함께(카탈로그로 CASCADE 실측) |
| `OPEN-6F8-RAW-PII-RETENTION`(`payload`) | **닫힘** — 90일 뒤 마스킹. 담당자 키가 남지 않음을 production 경로에서 실측 |
| `OPEN-6B3-RAW-OBSERVATION-RETENTION` | **닫힘** — raw 관측은 파기하지 않고 마스킹만(A-3). 6G 표본은 투영 열로 남는다 |
| `OPEN-6A2B-ABANDONED-SESSIONS` | **닫힘** — `edit_session` 시각 단일 술어(D-6B3-1 ②), 버려진 비종단 행이 그 부분집합 |
| 6F-7 privacy-gate 권고(`outbox`) | **닫힘** — 종단 상태만 파기 |
| D-6A1-7(`api_request_audit`) | **닫힘** — `occurred_at` 기준 파기 |

## 새 OPEN

| ID | 내용 |
|---|---|
| `OPEN-6B3-OUTBOX-FAILED-RETENTION` | `FAILED` outbox 행의 처분(알려진 제한 1) — 운영자 결정 축 |
| `OPEN-6B3-BLOAT-AFTER-FIRST-PURGE` | 첫 실행이 90일 백로그를 지운 뒤의 부풀음·`VACUUM` 운영 반입(M7 축) |
| `OPEN-6B3-PARTIAL-COUNTS-ON-FAILURE` | 실패한 run 의 부분 계수 보고(알려진 제한 3) |
