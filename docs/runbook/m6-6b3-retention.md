# M6/6B-3 보존 runbook — 90일 파기 · 원문 PII 마스킹 (2026-10-14)

운영자 결정 2026-09-26 ②(「보존 기간 90일 — 공고·평가 이력·audit」)와 6B-3 운영자 결정 A-1~A-4 를
실행하는 절차다. 설정·종료 코드의 일반 관례는 `m6-6e-operations.md` §1·§3 이 정본이고, 이 문서는
보존 축만 더한다.

**실행은 별도 승인이다.** 이 문서는 절차를 적을 뿐이고, 운영 DB 에 파기를 돌리는 것은
`milestone-6.md` 「완료 후 별도 승인 사항」에 걸린다. 6B-3 은 Testcontainers 위에서만 실행했다.

## 1. 무엇이 사라지고 무엇이 남는가

기준 시각은 **적재 시각 축 하나**다(A-2) — 표마다 DB 가 적재 때 찍는 열이고, cutoff 는
`시계 - 보존기간` 한 값이다(한 run 안에서 표마다 다르지 않다).

| 처분 | 표 | 기준 시각 열 | 조건 |
|---|---|---|---|
| **파기** | `notice` · `opening_result` · `opening_reserve_price` · `qualification_text` · `notice_requirement` | `created_at` | 시각만 |
| **파기** | `rejected_write` | `recorded_at` | 시각만 |
| **파기** | `api_request_audit` | `occurred_at` | 시각만 |
| **파기** | `edit_session` | `created_at` | 시각만(버려진 비종단 행을 포함한다 — A-4) |
| **파기** | `outbox` | `inserted_at` | **`DELIVERED` 만**(발송된 행 하나 — `FAILED`·`ISOLATED` 는 미발송이라 제외) |
| **부모와 함께** | `notice_requirement_row` | `created_at` | 헤더가 지워질 때 FK `ON DELETE CASCADE` 로. **단 기한 안쪽 행이 있으면 헤더를 남긴다** |
| **마스킹만** | `raw_observation` | `inserted_at` | `pii_masked_at IS NULL` 인 행 |
| **제외** | `notice_audit` · `inbox` · `collection_run` · `operator_strategy` · `operator_profile` · `operator_strategy_revision` · `provenance_authority` · `flyway_schema_history` | — | 분류 레지스트리가 사유를 함께 싣는다 |

분류는 코드 한 자리(`RETENTION_TABLE_RULES`)에 있고 **스키마의 표 집합과 등식**이다. 새 마이그레이션이
표를 더하면 그 등식이 깨지고, 러너는 **아무것도 지우지 않고** 종료 코드 4 로 멈춘다(§3).

### 1.1 「공고 90일」이 원문까지 지우지는 않는다

`raw_observation` 은 파기 대상이 아니다(A-3) — 6G 백테스트 표본을 남기기 위해서다. 그 표에서
사라지는 것은 **담당자 키**(`payload` 안의 이름·전화·전자우편)와 **업체 식별을 나르는 등재 개념
전부**(운영자 결정 2026-10-14)다. 그 개념은 셋이다 — 투찰업체명 · 최종낙찰업체명 · **개찰결과 목록의
합성 문자열**(수집 시점 마스킹이 사업자등록번호·대표자명을 버리고 상호·투찰금액·투찰율을 남기므로
상호가 그 안에 산다). 셋째는 앞 판에서 빠져 있었고, 그 누락은 **사후에 고칠 수 없다**(마스킹된 행은
표지가 찍혀 둘째 마스킹이 거부된다) — 그래서 지금은 「어느 등재 개념이 업체 식별을 나르는가」가
**한 자리에 선언되고 `FieldConcept` 전수와 분할 등식**이다. 새 개념은 분류 없이 빌드를 지나지 못한다.

업체 식별 키는 담당자 키와 달리 필드 계약에 등재되어 투영 열에도 실리므로 마스킹이 **두 열**
(`payload`·`payload_fields`)을 함께 줄인다. **남길 키의 출처는 계약 레지스트리**다(그 행의 투영 열이
아니다) — 전 개정의 rawName 합집합에서 업체 식별을 뺀 집합이고, 그래서 ⓐ 적재자가 투영에 담당자 키를
실어도 그 키는 남지 않고 ⓑ 계약이 개정돼 옛 키가 사라져도 묵은 행의 옛 키가 지울 집합에 그대로 남는다.
6G 백테스트·스냅숏 추출·수집 축 조회가 읽는 키 집합에 업체 식별 개념이 없음을 기계 수집으로
확인했다(evidence).

마스킹은 **한 방향**이다: 표지(`pii_masked_at`)가 NULL 에서 값으로 한 번만 가고, 되돌리는 UPDATE 는
DB 트리거가 거부한다. 마스킹을 정하는 **세 입력 전부가 적재자 손을 벗어나 있다**: 표지는 적재가 미리
찍을 수 없고(INSERT 관문), 남길 키는 계약에서 오고, **적재 시각은 DB 시계가 소유한다**(비소유자 적재의
`inserted_at` 은 트리거가 `now()` 로 바꿔 쓴다 — 미래 시각으로 마스킹 후보에서 영구히 빠지는 길을
닫는다. 출하 수집 경로는 그 열을 쓰지 않으므로 비용이 0 이다).

판독 깊이 상한은 **수집 상한과 같다**. 수집이 받아들인 원문을 마스킹이 「형태가 다르다」로 버리는
일이 없게 두 값의 관계를 test 가 잠근다(상한을 넘는 원문만 fail-closed 다 — §3.1).

`notice_audit` 도 파기하지 않고 마스킹도 하지 않는다. 사유를 사실대로 적는다: 그 표의
`previous_row` 는 `notice` 행의 **전체 스냅숏**이고 `notice` 로 가는 FK 가 없다 — 그래서 `notice` 행이
파기돼도 그 스냅숏(금액 축·분류·마감)은 **90일 창 밖에도 남고 그 표는 무한히 자란다**(알려진 제한 8).
담당자 PII 는 그 스냅숏에 없다(`notice` 에 담당자 열이 없다 — 그 사실을 「`notice` 열 집합 ↔ 보존
분류가 아는 열 집합」 등식이 잠근다. 새 열은 분류를 거치지 않고는 빌드를 지나지 못한다). 그래서 그
표의 append-only 트리거는 **손대지 않았다** — 구멍은 `raw_observation` 하나다.

### 1.2 되돌릴 수 없는 것

파기된 행과 마스킹된 값은 **백업 복원으로만** 돌아온다(`m6-6b2-backup-restore.md`). V18·V19 자체의
되돌림은 forward-fix 다 — 순서대로 ⓐ 새 V 로 트리거를 V3 의 `reject_mutation` 으로 재지정
(먼저 해야 한다: 역순이면 함수 본문의 `OLD.pii_masked_at` 이 깨진다. DELETE 거부는 첫 분기라
살아남아 fail-closed 다). **그 한 줄이 함께 잃는 것 둘**(mig L-3): 적재의 **표지 관문**과
**`inserted_at` 소유**(비소유자 적재의 DB 시계 강제) — §1.1 이 「세 입력」으로 선언한 것 가운데
둘이 그 재지정에 달려 있다 ⓑ `DROP FUNCTION reject_mutation_except_masking()`(빠뜨리면 고아 함수가
남고 트리거→함수 등재는 초록이다 — 되돌림이 반쯤 된 상태가 장부에 안 보인다) ⓒ V19 의 인덱스
열 개와 CHECK 제약 `raw_observation_payload_fields_object` DROP ⓓ `pii_masked_at` DROP.

**ⓓ 는 「이 행은 마스킹했다」를 영구히 지운다.** `payload`·`payload_fields` 는 이미 줄어든 채 남고
표지가 없으니 원문 행과 **구별되지 않는다** — 되돌린 뒤 V18 을 다시 적용하면 모든 행이 다시 후보가
되고, fail-closed 표식(`{"pii_masked":"fail_closed"}`)은 평평한 객체라 다시 판독되어 남김 집합이 비고
`payload` 가 `{}` 가 된다(「판독할 수 없었다」는 사실이 사라진다). 그래서 되돌림 전에 **백업 복원
여부를 먼저 결정한다**.

### 1.3 V18·V19 적용 창 — 무엇이 얼마나 막히는가

마이그레이션은 **기동의 부수효과**로 돈다(`DataSource` 빈 생성). 그래서 적용 창은 곧 기동 창이고,
적용 전에 그 창의 모양을 알고 있어야 한다. 아래 숫자는 **110만 행(`raw_observation` 36만 · 292MB)**
에서 독립 레인이 실측한 값이다.

| 파일 | 쥐는 잠금 | 막히는 것 | 창의 길이 |
|---|---|---|---|
| **V18** | `ACCESS EXCLUSIVE`(`raw_observation`) | 그 표의 읽기·쓰기 | 작업 자체는 **밀리초**(열 추가 nullable·DEFAULT 없음 → rewrite 없음 · CHECK `NOT VALID` → 스캔 없음 · 함수·트리거 교체). 그러나 **창 = 잠금 획득 대기 + 작업**이다: 그 표에 열린 트랜잭션이 하나라도 있으면 `ALTER TABLE` 이 큐에서 기다리고 **그 뒤의 평범한 읽기까지 전부 큐에 선다**. 그래서 첫 문장이 `SET LOCAL lock_timeout` 이다 — 대기가 길면 **실패**하고(기동 실패 → 재시도) 무한히 서지 않는다 |
| **V19** | 표별 `SHARE`(인덱스 빌드) + `raw_observation` 에 `SHARE UPDATE EXCLUSIVE`(검증) | **쓰기만**(읽기는 지난다) | **열 표 전부가 V19 전 구간 동안** 막힌다 — 한 트랜잭션이라 잠금이 COMMIT 까지 유지된다(실측: 모든 빌드가 끝나고 트랜잭션만 열려 있는 동안에도 `INSERT INTO notice` 가 `lock_timeout` 을 소진했다). 제일 작은 표도 `raw_observation` 빌드가 끝날 때까지 쓰기가 막히므로 수집·relay·편집 세션이 전부 그 창에 든다. 순수 작업은 3M 행에서 1.7s |

인덱스를 V18 에서 떼어낸 이유가 이 표다: 한 트랜잭션에 두면 V18 의 `ACCESS EXCLUSIVE` 가 빌드 내내
유지되어 **읽기까지** 막힌다.

**한 트랜잭션의 다른 면**: 마지막 문장에서 실패하면 **인덱스 열 개가 전부 사라진다**(실측) — 재시도는
전체 빌드 비용을 다시 낸다. 반쯤 적용된 상태나 invalid 인덱스가 남지 않는 것이 이득이고, 그 대가가
이것이다.

**적용 전 점검 셋.** 소유자 연결로 돈다.

```sql
-- ⓐ 창의 길이를 가늠한다(표 크기).
SELECT relname, pg_size_pretty(pg_total_relation_size(oid)) AS size, reltuples::bigint AS approx_rows
  FROM pg_class
 WHERE relname IN ('raw_observation','notice','opening_result','opening_reserve_price','qualification_text',
                   'notice_requirement','notice_requirement_row','rejected_write','edit_session','outbox')
 ORDER BY pg_total_relation_size(oid) DESC;

-- ⓑ **V19 를 실패시키는 유일한 조건** — 0 이 아니면 적용하지 않는다(그 행을 먼저 조사한다).
--    0 이 아닌 채 적용하면 실패하는 것은 보존이 아니라 **앱 기동**이다.
SELECT count(*) FROM raw_observation WHERE jsonb_typeof(payload_fields) <> 'object';

-- ⓒ 긴 트랜잭션 — V18 의 잠금 대기를 만드는 쪽이다(읽기까지 큐에 세운다).
SELECT pid, state, now() - xact_start AS age, left(query, 60) AS q
  FROM pg_stat_activity WHERE xact_start IS NOT NULL ORDER BY xact_start;
```

큰 표에서는 수집 cron 을 멈춘 정비 창에 적용한다(읽기는 막히지 않으므로 조회 API 는 살아 있다).

**`CONCURRENTLY` 를 쓰지 않는다 — 실측 때문이다.** `CREATE INDEX CONCURRENTLY` + 형제 `.sql.conf`
(`executeInTransaction=false`)는 Flyway 아래에서 **끝나지 않았다**(Testcontainers 실측: `idle in
transaction` 인 Flyway 메타데이터 연결과 `Lock/virtualxid` 로 막힌 마이그레이션이 14분간 그대로).

메커니즘은 「동시 트랜잭션 전부를 기다린다」가 아니다 — **스냅숏을 쥔 트랜잭션**을 기다린다(독립 레인
실측: advisory 잠금만 쥔 `idle in transaction` 은 `CONCURRENTLY` 를 막지 않았고, `REPEATABLE READ` 로
스냅숏을 쥔 트랜잭션은 막았다). 즉 그 정지는 Flyway 의 메타데이터 연결이 **스냅숏을 쥐고** 있었다는
뜻이다. `flyway.postgresql.transactional.lock=false` 로 풀리지만 **Flyway 를 부르는 자리 넷이 전부**
그것을 알아야 하고, 하나라도 잊으면 거기서 실패가 아니라 **정지**한다. 실패한 `CONCURRENTLY` 는
INVALID 인덱스를 남기므로(실측) 재시도에 정리가 따라붙는다. 창을 줄이려고 더 나쁜 실패 모양을 사지
않았다.

**CHECK 검증 실패**(`raw_observation_payload_fields_object`)는 재시도의 문제가 아니다 — 적용 **전에
이미** 객체가 아닌 `payload_fields` 행이 있었다는 뜻이고(위 ⓑ 가 그것을 미리 센다), 그 행을 조사하는
것이 답이다(쓰기 경로는 늘 평평한 객체를 쓴다). 멈추고 보고한다(§4).

## 2. 실행

러너는 기본 꺼짐이고 `bidvector.retention.mode=once` 하나로 켜진다. 다른 일회 러너와 같은
프로세스에 켜면 **기동하지 않는다**(`OneShotRunnerGuard`).

**자격 값은 환경변수로만 준다** — 명령행 인자로 적으면 `/proc/<pid>/cmdline` 에 들어가 같은 호스트의
아무 사용자나 `ps` 로 읽는다. 그 값은 **DB 소유자** 자격이고(트리거를 DROP 할 수 있는 주체다) 이
절차가 그 사용을 매일 도는 cron 으로 만든다. 규약은 `m6-6e-operations.md` §1.4 가 정본이다.
`jdbc-url`·`username` 은 자격 값이 아니므로 인자로 두어도 된다.

**값을 셸 줄에 놓지도 않는다**(cr L-6) — 같은 줄에 `VAR=<값> java …` 로 쓰면 argv 는 깨끗해도 셸
이력과 cron 래퍼 파일에 그대로 남는다(argv 를 거부한 사유의 절반이 그것이다). 래퍼가 자격을 **읽어
export** 한다:

```sh
# /opt/bidvector/run-retention.sh — 값은 이 파일에도 적지 않는다(읽어 온다).
set -eu
BIDVECTOR_PERSISTENCE_CREDENTIAL="$(cat /etc/bidvector/owner.credential)"   # 0400, 운영자 소유
export BIDVECTOR_PERSISTENCE_CREDENTIAL
SPRING_MAIN_WEB_APPLICATION_TYPE=none exec java -jar /opt/bidvector/app.jar \
  --bidvector.retention.mode=once \
  --bidvector.persistence.jdbc-url="$BIDVECTOR_JDBC_URL" \
  --bidvector.persistence.username="$BIDVECTOR_DB_OWNER"
```

접속 사용자는 **표 소유자**여야 한다 — 파기는 런타임 풀을 지나지 않는다(A-1). 풀은 물리 연결마다
`SET ROLE bidvector_app` 으로 전환되고 그 역할에는 대상 표 어디에도 DELETE·UPDATE 권한이 없다.
풀로 돌리면 모든 파기가 권한 오류로 끝난다 — 그 오류가 경계가 살아 있다는 증거다.

`pg_stat_activity.application_name` 에서 이 세션은 `bidvector-retention` 이다(앱 풀 `bidvector-app`·
migration `bidvector-migration` 과 가른다).

### 2.1 cron

주기는 외부 cron 이 진다(러너는 상주 루프가 아니다 — `m6-6e-operations.md` §1.3 관례).
하루 한 번, 수집·relay 와 **겹치지 않는 시간**에 둔다(한 프로세스에 둘을 켤 수 없으므로 시간대를
가른다).

```
# 매일 04:10 (KST) — 보존 파기·마스킹
10 4 * * * /opt/bidvector/run-retention.sh >> /var/log/bidvector/retention.log 2>&1
```

러너 둘이 겹쳐도 **둘째는 아무것도 하지 않는다**(DB advisory lock 임대 — 종료 코드 3).

### 2.2 첫 실행 전에 **세어 본다** (되돌림이 백업 복원뿐이므로)

§4.3 의 멈춤 조건(「파기 수가 예상과 자릿수로 다를 때」)은 **예상이 있어야** 발화한다. 러너에는 세기만
하는 모드가 없으므로(설계대로 `mode=once` 하나다) 운영 DB 첫 실행 **전에** 소유자 연결로 같은 술어의
`COUNT(*)` 를 돌려 수를 기록하고 그 수를 예상으로 쓴다. 보존 기간은 승인된 값(90일)이다.

```sql
-- 파기 대상(표마다 기준 시각 열이 §1 의 표와 같아야 한다)
SELECT 'notice', count(*) FROM notice WHERE created_at < now() - interval '90 days'
UNION ALL SELECT 'opening_result', count(*) FROM opening_result WHERE created_at < now() - interval '90 days'
UNION ALL SELECT 'opening_reserve_price', count(*) FROM opening_reserve_price
    WHERE created_at < now() - interval '90 days'
UNION ALL SELECT 'qualification_text', count(*) FROM qualification_text WHERE created_at < now() - interval '90 days'
UNION ALL SELECT 'notice_requirement', count(*) FROM notice_requirement WHERE created_at < now() - interval '90 days'
UNION ALL SELECT 'rejected_write', count(*) FROM rejected_write WHERE recorded_at < now() - interval '90 days'
UNION ALL SELECT 'api_request_audit', count(*) FROM api_request_audit WHERE occurred_at < now() - interval '90 days'
UNION ALL SELECT 'edit_session', count(*) FROM edit_session WHERE created_at < now() - interval '90 days'
UNION ALL SELECT 'outbox', count(*) FROM outbox
    WHERE inserted_at < now() - interval '90 days' AND state = 'DELIVERED'
-- 마스킹 대상
UNION ALL SELECT 'raw_observation(mask)', count(*) FROM raw_observation
    WHERE inserted_at < now() - interval '90 days' AND pii_masked_at IS NULL;
```

자식 가드 때문에 **실제 파기 수는 이 수보다 적을 수 있다**(기한 안쪽 자식이 있는 부모는 남는다 —
알려진 제한 2). 자릿수가 다르면 멈춘다.

### 2.3 한 run 이 얼마나 지우는가

cutoff 는 run 시작에 고정되고, 러너는 표별로 일괄 상한(정책 값)만큼 반복해 **그 cutoff 기준
백로그를 비운다**. 도중에 실패하면 앞서 커밋된 일괄은 남고 **다음 run 이 이어받는다**(이어받기에
상태가 필요 없다 — cutoff 가 시각으로 정해진다).

첫 실행은 90일 치 백로그를 한 번에 지우므로 그 트랜잭션들이 만드는 부풀음(bloat)을 뒤에 봐야
한다 — `VACUUM` 운영 반입은 M7 축이다(알려진 제한).

## 3. 종료 코드로 읽는다

| 코드 | 이름 | 뜻 | 처방 |
|---|---|---|---|
| 0 | `COMPLETE` | 기한 지난 행이 처분되고 판독 실패가 없다 | 없음 |
| 1 | `FAILED` | 실행이 예외로 끝났다 | 마침 줄의 `causeCode` 를 보고 §3.2 |
| 2 | `INCOMPLETE` | 판독할 수 없는 원문이 있어 값 전체를 버렸다(`maskFailClosed`), **또는 후보로 고른 행을 처리하지 못했다**(`maskSkipped`) | §3.1 |
| 3 | `LEASE_BUSY` | 다른 보존 러너가 돌고 있다 | **장애가 아니다.** 다음 주기를 기다린다 |
| 4 | `REFUSED` | 분류와 스키마가 어긋났거나 FK 에 순환이 있다. **한 행도 지우지 않았다** | §3.3 |

마침 줄은 표별 계수를 싣는다(`<표>:purged=N,masked=N,failClosed=N,skipped=N`) — 어느 표가 몇 행인지 보고,
제외 표가 `exempt=[...]` 목록에 그대로 있는지 확인한다. 줄에는 **행 값이 없다**(표 이름·계수·cutoff
시각뿐이다).

### 3.1 `failClosed` 가 0 이 아니면

상류 응답 형태가 바뀌었다는 신호다 — 그 행들의 `payload` 는 판독되지 않아 **값 전체가 표지로
대체**됐다(부분 치환을 하지 않는다). 수집 축(`KonepsJsonParser`·필드 계약)을 먼저 본다. 이미
대체된 값은 돌아오지 않는다(§1.2).

### 3.1b `maskSkipped` 가 0 이 아니면

후보로 **골랐는데 처리하지 못한** 행이다(마스킹 UPDATE 가 0 행을 돌렸다). 그 run 은 백로그를 비우지
못했고, 같은 일괄이 다음 run 에도 같은 모양으로 올 수 있다. 오늘 그 수가 0 이 아닐 구조적 경로는
없다(`observation_key` 가 PK 이고 조회 술어가 `pii_masked_at IS NULL` 이다) — 그래서 0 이 아니면
**스키마나 질의가 전제와 달라진 것**이고, 다음 run 을 돌리기 전에 그 행을 본다. 러너는 진척이 없는
일괄에서 **루프를 끝낸다**(임대를 쥔 채 영구히 돌지 않는다).

### 3.2 `causeCode` 읽기

`m6-6e-operations.md` §3.5 와 같은 규약이다 — 클래스 이름 + SQLSTATE 5자리까지이고 메시지는 없다.
`sqlState=42501`(권한 부족)이면 **풀 연결로 돌렸을** 가능성이 먼저다(§2).

### 3.3 `REFUSED` 가 났을 때

마침 줄이 어느 표가 어긋났는지 적는다.
- `missingClassification=[...]` — 새 마이그레이션이 표를 더했고 분류가 없다. `RETENTION_TABLE_RULES`
  에 처분을 적고(운영자 결정이 필요하면 받고) 배포한다. 코드를 고치지 않고 돌릴 길은 없다 —
  의도된 것이다.
- `missingFromSchema=[...]` — 분류에 있는 표가 스키마에 없다(낡은 분류). 분류를 지운다.

## 4. 멈춤 조건 — 이때는 고치지 말고 보고한다

1. **운영 DB 에서의 첫 실행** — 별도 승인 사항이다. 승인 없이 돌리지 않는다.
2. `REFUSED` 가 났고 새 표의 처분이 운영자 결정 축일 때(무엇을 지우는가는 운영자가 정한다).
3. 파기 수가 예상과 **자릿수로** 다를 때 — cutoff·시계·기준 시각 열 가운데 하나가 어긋난 것이다.
   되돌릴 수 없으므로 다음 run 을 돌리기 전에 멈춘다.
4. `failClosed` 가 전체 마스킹 대상에 가까울 때 — 상류 형태가 통째로 바뀐 것이고, 계속 돌리면
   원문을 계속 버린다.
5. 트리거가 파기·마스킹을 거부할 때(`sqlState=P0001`) — append-only 계약과 어긋난 UPDATE 를
   보내고 있다는 뜻이고, 그것은 코드 결함이다.

## 5. 알려진 제한

1. **`outbox` 의 `FAILED`·`ISOLATED` 행은 영구히 남는다.** 둘 다 종단이지만 **한 번도 보내지지
   않은** 상태라 A-4 의 「미발송 제외」에 들어간다 — 잃은 알림의 유일한 흔적이고(`ISOLATED` 는
   `m6-6e-operations.md` §3.6 의 조사 경로가 읽는 행이다) 남기는 쪽을 골랐다. 그 행 수가 자라면
   처분이 결정 축이다.
2. **기한 안쪽 자식이 있는 부모는 그 run 에서 남는다.** `opening_reserve_price` 가 부모보다 늦게
   생길 수 있어 그 조합이 실재한다 — 자식이 기한을 넘는 다음 run 에서 함께 사라진다.
   **`ON DELETE CASCADE` 자식도 같다**(운영자 결정 2026-10-14): 헤더 upsert 가 `created_at` 을
   유지하고 행은 새 `created_at` 을 받으므로 「120일 전 헤더 · 오늘 행」이 실재하고, 가드가 없으면
   cascade 가 기한 안쪽 행을 함께 지웠다. 자식이 기한을 넘을 때 둘이 함께 사라진다.
3. **수집 시점 마스킹은 하지 않는다.** 원문은 90일 동안 담당자 PII **와 업체 식별 키**를 가진다
   (결정 문면 그대로 — §1.1 과 같은 문면이다, cr L-5).
4. **백업 산출물 속 과거 행**은 이 절차의 밖이다(`m6-6b2-backup-restore.md` · M7).
5. **시계는 신뢰 앵커다.** 호스트 시계가 미래로 틀어지면 cutoff 도 미래로 움직인다. 정책 값이
   0·음수를 거부하는 것까지가 이 slice 의 방어다.
6. **6G 실수집 표본의 첫 파기 대상 시점**은 2026-12-31 이후다(적재가 2026-10-02 이후). 그 전에
   스냅숏 추출이 끝나므로 이 slice 가 6G 를 막지 않는다.
7. `VACUUM`/부풀음 운영 반입은 M7 축이다(§2.3).
8. **`notice_audit` 은 파기도 마스킹도 받지 않아 무한히 자란다.** 그 표의 `previous_row` 는 파기된
   `notice` 행의 전체 스냅숏(금액 축 셋·분류·마감)이고 `notice` 로 가는 FK 가 없으므로, 「공고 90일」
   뒤에도 그 스냅숏은 남는다. 담당자 PII 는 그 스냅숏에 없다(§1.1 의 등식). 보존 자체를 바꾸는 것은
   운영자 결정 축이다(`OPEN-6B3-NOTICE-AUDIT-SNAPSHOT-RETENTION`).
9. **소유자 마스킹 UPDATE 는 키 제거가 아닌 임의 내용 주입도 통과한다**(표지만 찍고 `payload` 를
   그대로 두는 UPDATE 포함) — 소유자 자격을 가진 주체의 임의 SQL 은 비방어다
   (`OPEN-6E2A-OWNER-CREDENTIAL-IN-APP`). 출하 경로는 늘 치환값을 함께 쓴다.
10. **적재마다 트리거 함수가 한 번 더 돈다**(INSERT 관문). 분기 셋이라 행당 비용은 마이크로초
    단위이고, 그 대가로 마스킹을 정하는 입력 둘(표지·적재 시각)이 적재자 손을 벗어난다. 수집 처리량
    실측은 M7 축이다. 그 강제는 **조용하다**(cr L-6): 비소유자 적재의 `inserted_at` 을 거부 대신
    `now()` 로 **말없이 바꿔 쓴다**. 거부를 고르지 않은 이유는 측정에 있지만(트랜잭션의 `now()` 는
    시작 시각이라 허용 오차 거부가 긴 트랜잭션의 정상 적재를 막는다), 앞으로 그 열을 **뜻을 담아**
    쓰는 적재자가 생기면 그 값은 오류도 계수도 없이 사라진다.
11. **`raw_observation` 을 쓰는 `SECURITY DEFINER` 함수를 더하지 않는다**(mig L-2). 그런 함수에
    `EXECUTE` 를 주면 앱 역할이 그 안에서 소유자가 되어 **관문 ③ 밖**에서 원문을 쓸 수 있다. 이
    저장소에는 이미 그 패턴의 선례가 있다(V2 의 audit 함수) — 마이그레이션 검토에서 이 줄을 본다.
12. **소유자의 `TRUNCATE` 는 행 트리거 밖이다**(mig I-1). 「파기하지 않는다」는 행 트리거의 주장이고
    `TRUNCATE raw_observation CASCADE` 는 그 밖에서 표를 비운다(앱 역할은 권한이 없어 거부된다).
    V3 때부터의 성질이고 `OPEN-6E2A` 와 같은 축이다.
13. **`outbox` 파기는 일괄마다 만료 행 전체를 다시 정렬한다**(mig L-1). 술어가 `state = ANY(배열)`
    라 플래너가 인덱스 순서를 쓰지 못한다 — 종단 집합이 한 값인 동안은 `= ?` 로 쓰면 정렬이 사라지지만
    집합 타입을 유지하는 한 이 모양은 구조적이다. 비용 축이고 거동은 같다.
14. **마스킹된 `payload` 는 엄격 JSON 이 아닐 수 있다**(cr L-7). 재직렬화가 쓰는 render 는 escape 가
    최소이고(제어문자를 그대로 낸다) 키 축만 안전하다(남길 키가 계약 rawName 이라 영문·숫자뿐이다).
    오늘 그 열의 production 독자는 0 이므로 실해가 없다 — 6G 표본을 엄격 파서로 읽는 소비자가 생기면
    그때는 고칠 수 없다(관문 ④). 공유 render 를 이 slice 에서 바꾸지 않은 이유는 호출부 둘이 더
    있어서다.
15. **`outbox` 의 `FAILED`·`ISOLATED` 영구 잔류의 PII 축은 아무도 재지 않는다**(privacy F-9). 오늘 그
    행이 담는 것은 전략 개정 번호·정책 버전·공고 id·판정 사유·운영자 자신이고 **제3자 PII 0** 이다.
    payload 타입이 늘고 그 타입이 제3자 이름을 담으면 그 행은 파기도 마스킹도 받지 않는다(타입 추가가
    fail-closed 코드 변경을 요구하므로 사람이 그 자리를 지난다).
17. **분할 등식은 개념 단위다 — rawName 단위가 아니다**(vr R3-F2). 업체 식별을 나르는 새 rawName 이
    **기존 비PII 개념 아래로** 등재되면 그 키는 남김 집합에 들어오고 아무것도 붉어지지 않는다
    (`opengCorpInfo` 가 바로 그 꼴이었다 — 합성 문자열이 기존 개념 아래 있었다). 오늘 계약 rawName
    전수에 그런 키는 없다. 사람이 값 모양을 보게 되는 자리는 **개념을 새로 만들 때**이고, 기존 개념에
    키를 더할 때는 강제되지 않는다.
18. **자유텍스트 개념의 비PII 분류는 「키의 의미」 축이다 — 값의 내용은 보지 않는다**(cr L-1 ·
    privacy F-11). `NON_PII_CONCEPTS` 에는 공고명·적용기준내용·시공능력요건·업무분류라벨처럼 상류가
    쓰는 **자유 문자열**이 든다. 그 키가 담기로 한 것은 사람 이름도 상호도 아니지만, 담겨 **들어오면**
    그 문자열은 `raw_observation` 이 파기 대상이 아니므로(A-3) 90일 뒤에도 남고 마스킹 뒤에도 남는다
    (표지가 찍히므로 영구). 측정이 아니라 **가정**이고, 그 가정을 운영 첫 실행 전에 실물 표본으로
    재확인한다(`OPEN-6B3-FREE-TEXT-PII-AXIS`).
19. **마스킹은 키 이름으로 고른다 — 값에 심은 상호는 두 열 모두에 남는다**(privacy F-10 의 닫을 수
    없는 절반). 계약 키의 **값**에 상호·이름을 넣으면 키 축 allow-list 로는 걸러지지 않는다. 값 축은
    **비방어**다(수집 시점 마스킹 부재와 같은 축 — 제한 3).

16. **CASCADE 기한 가드는 한 단계다**(vr R2-F7). 파기 대상 부모의 **직계** CASCADE 자식만 본다 —
    CASCADE 손자나 「마스킹만」으로 분류된 CASCADE 자식이 생기면 그 행은 기한 안쪽이어도 부모와 함께
    사라진다. 오늘 스키마에 둘 다 없고 **그 부재를 빌드가 센다**(그런 간선이 들어오면 RED).
