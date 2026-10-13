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
| **파기** | `outbox` | `inserted_at` | **종단 상태만**(`DELIVERED`·`ISOLATED`) |
| **부모와 함께** | `notice_requirement_row` | — | 헤더가 지워질 때 FK `ON DELETE CASCADE` 로 |
| **마스킹만** | `raw_observation` | `inserted_at` | `pii_masked_at IS NULL` 인 행 |
| **제외** | `notice_audit` · `inbox` · `collection_run` · `operator_strategy` · `operator_profile` · `operator_strategy_revision` · `provenance_authority` · `flyway_schema_history` | — | 분류 레지스트리가 사유를 함께 싣는다 |

분류는 코드 한 자리(`RETENTION_TABLE_RULES`)에 있고 **스키마의 표 집합과 등식**이다. 새 마이그레이션이
표를 더하면 그 등식이 깨지고, 러너는 **아무것도 지우지 않고** 종료 코드 4 로 멈춘다(§3).

### 1.1 「공고 90일」이 원문까지 지우지는 않는다

`raw_observation` 은 파기 대상이 아니다(A-3) — 6G 백테스트 표본을 남기기 위해서다. 그 표에서
사라지는 것은 **담당자 키**(`payload` 안의 이름·전화·전자우편)이고, 남는 것은 그 행의
`payload_fields` 가 등재한 키뿐이다. 마스킹은 **한 방향**이다: 표지(`pii_masked_at`)가 NULL 에서
값으로 한 번만 가고, 되돌리는 UPDATE 는 DB 트리거가 거부한다.

`notice_audit` 도 파기하지 않고 마스킹도 하지 않는다 — 그 표의 `previous_row` 는 `notice` 행의
스냅숏이고 담당자 열이 `notice` 에 없어 담당자 PII 가 들어올 경로가 없다. 그래서 그 표의
append-only 트리거는 **손대지 않았다**(구멍은 `raw_observation` 하나다).

### 1.2 되돌릴 수 없는 것

파기된 행과 마스킹된 값은 **백업 복원으로만** 돌아온다(`m6-6b2-backup-restore.md`). V18 자체의
되돌림은 forward-fix 다(새 V 로 트리거를 원복하고 열을 DROP) — 그렇게 해도 사라진 행은 돌아오지
않는다.

## 2. 실행

러너는 기본 꺼짐이고 `bidvector.retention.mode=once` 하나로 켜진다. 다른 일회 러너와 같은
프로세스에 켜면 **기동하지 않는다**(`OneShotRunnerGuard`).

```
SPRING_MAIN_WEB_APPLICATION_TYPE=none \
java -jar app.jar \
  --bidvector.retention.mode=once \
  --bidvector.persistence.jdbc-url=<URL> \
  --bidvector.persistence.username=<소유자> \
  --bidvector.persistence.credential=<값은 환경에서>
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

### 2.2 한 run 이 얼마나 지우는가

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
| 2 | `INCOMPLETE` | 판독할 수 없는 원문이 있어 값 전체를 버렸다 | §3.1 |
| 3 | `LEASE_BUSY` | 다른 보존 러너가 돌고 있다 | **장애가 아니다.** 다음 주기를 기다린다 |
| 4 | `REFUSED` | 분류와 스키마가 어긋났거나 FK 에 순환이 있다. **한 행도 지우지 않았다** | §3.3 |

마침 줄은 표별 계수를 싣는다(`<표>:purged=N,masked=N,failClosed=N`) — 어느 표가 몇 행인지 보고,
제외 표가 `exempt=[...]` 목록에 그대로 있는지 확인한다. 줄에는 **행 값이 없다**(표 이름·계수·cutoff
시각뿐이다).

### 3.1 `failClosed` 가 0 이 아니면

상류 응답 형태가 바뀌었다는 신호다 — 그 행들의 `payload` 는 판독되지 않아 **값 전체가 표지로
대체**됐다(부분 치환을 하지 않는다). 수집 축(`KonepsJsonParser`·필드 계약)을 먼저 본다. 이미
대체된 값은 돌아오지 않는다(§1.2).

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

1. **`outbox` 의 `FAILED` 행은 영구히 남는다.** 종단이지만 「미발송」이라 A-4 의 제외에 들어간다 —
   잃은 알림의 유일한 흔적이라 남기는 쪽을 골랐다. 그 행 수가 자라면 처분이 결정 축이다.
2. **기한 안쪽 자식이 있는 부모는 그 run 에서 남는다.** `opening_reserve_price` 가 부모보다 늦게
   생길 수 있어 그 조합이 실재한다 — 자식이 기한을 넘는 다음 run 에서 함께 사라진다.
3. **수집 시점 마스킹은 하지 않는다.** 원문은 90일 동안 담당자 PII 를 가진다(결정 문면 그대로).
4. **백업 산출물 속 과거 행**은 이 절차의 밖이다(`m6-6b2-backup-restore.md` · M7).
5. **시계는 신뢰 앵커다.** 호스트 시계가 미래로 틀어지면 cutoff 도 미래로 움직인다. 정책 값이
   0·음수를 거부하는 것까지가 이 slice 의 방어다.
6. **6G 실수집 표본의 첫 파기 대상 시점**은 2026-12-31 이후다(적재가 2026-10-02 이후). 그 전에
   스냅숏 추출이 끝나므로 이 slice 가 6G 를 막지 않는다.
7. `VACUUM`/부풀음 운영 반입은 M7 축이다(§2.2).
