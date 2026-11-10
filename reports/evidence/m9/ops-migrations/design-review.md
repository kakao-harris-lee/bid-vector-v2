# M9 / ops-migrations — 설계 검토 (구현 전)

## (0) 위협 모델 경계 한 문장

이 slice 의 축이 막는 것은(계약 갱신으로 **둘**이 됐다 — DEC-08 제외) **「이 행을 무엇이·어느 정책으로·어느 릴리스가 만들었는가」를 DB 가 답하지
못하는 상태**다 — 악의적 운영자나 외부 침입이 아니라, 사후 감사에서 메모리에만 있던 사실(정책
version · 종단 사유 · 릴리스)이 영영 사라지는 것이 위협이다. 경계 밖: 값의 진위(릴리스 라벨이
jar 내용과 같은가 — `OPEN-6G-RELEASE-SHA-LABEL`) · 기존 행의 소급 복원(없는 사실을 짓지 않는다).

## (1) 열거인가 구성인가

셋 다 **열거**다. 세 어휘는 코드에 이미 닫힌 집합으로 있고(아래), 마이그레이션은 그 집합을 저장
층에 **다시 선언**한다(V6 의 `state` CHECK 가 `OutboxEntryState` 를 실은 것과 같은 관례).

| 축 | 코드의 닫힌 집합 | 저장 층 재선언 |
| --- | --- | --- |
| ~~DEC-08~~(제외) | `BaseAmountProvenance` 5 · `ProvenanceRuleId` 4 · `classificationFor` 의 결속 | — (표를 들이지 않았다) |
| OPS-05 | 종단 사유 7 (relay 가 이미 가른 처분, D-6F10-12 해석표) | V26 CHECK 둘(어휘 · 사유→상태 쌍) |
| OPS-10 | — (식별자, 어휘 아님) | V27 NOT NULL / INSERT 거부 트리거 |

**구성 축은 하나다**: 사유→상태 결속과 라벨→규칙 결속은 쌍의 **곱집합이 아니라 표**다. SQL 에
손으로 늘어놓지 않고, Kotlin 쪽 전수와의 **집합·사상 등식 test** 로 묶는다(문자열 grep 아님).

## (2) 우회 — 각 축 ≥5

### DEC-08 — **이 slice 에서 빠졌다**(팀장 결정 2026-11-06, 계약 갱신 ①)

아래 우회 여섯은 **구현되지 않은 축의 기록**이다. 빠진 근거: 출하 조립의 `MlAnalysisPort` 는
`UnavailableMlAnalysis` 고정이고 의존 게이트(「app.wiring 이 adapters.ml 에서 보는 집합」)가 그
사실을 잠그므로 `OpportunityAnalysis`·`JdbcCompetitionSampleSource` 가 출하에 생성되지 않는다 —
**분류가 출하에서 0 회 돈다**. 그 상태로 표를 들이면 우회 ① 「칸만 만들고 아무도 안 쓴다」를
e2e 조립으로 가린 것이 되고(측정은 서지만 출하 거동은 0), 남는 것은 되돌리기 어려운 스키마다.
ML 출하 배선은 사용자 보류 결정이라 이 slice 가 열 수 없다. 받는 자리는
`OPEN-ML-ANALYSIS-WIRING` — 저장 자리와 릴리스 칸을 **같은 자리에서** 정한다.

아래 검토가 **남는 값**: 받는 slice 가 다시 세울 때 우회 여섯과 (2b) 표의 표면 둘이 그대로
입력이다(특히 ②·⑤ — dry-run 분리와 정책 version 마다 새 행).

**— 이하 당시 검토의 기록(구현 없음) —**

#### DEC-08 (판정 표 신설) · 당시 검토

1. **칸만 만들고 아무도 안 쓴다** — 0 행인 표는 저장이 아니다. → 커밋 경로(`EvaluationCommitRun`)
   에 write port 를 꽂고, 커밋 run 뒤 **행 1**을 실 DB test 가 센다.
2. **dry-run·조회 경로가 쓴다** — 미리보기가 부작용을 낸다. → port 를 갈라 끼운다
   (`RecordingNotificationRequestPort` 관례): dry-run·bid-price 는 기록만 하는 구현, 커밋만 Jdbc.
   dry-run run 전후 **행 수 불변**을 test 가 센다.
3. **version 없이 라벨만 쓴다** — 「분류에 쓴 정책」을 되짚을 수 없다. → 정책 두 칸
   (`policy_source`·`policy_effective_from`)이 PK 안이고 NOT NULL 이다.
4. **라벨과 규칙이 어긋난 행** — `CLEAN` 인데 `SUSPECT_RATIO` 가 걸린 행. → 결속 CHECK 가 거부하고
   그 표가 `classificationFor` 전수와 같음을 test 가 잰다.
5. **같은 공고를 정책 바뀔 때마다 덮어쓴다** — 과거 판정이 사라진다. → PK 에 정책 두 축이 있어
   새 version 은 **새 행**이고, append-only 트리거가 UPDATE·DELETE 를 거부한다.
6. **분류기를 두 번째로 짓는다**(원본 해소 규칙 복제) → 기존 `provenanceLabelFor` 호출부를 그대로
   쓰고 **판정 봉투를 버리지 않게** 바꾼다(새 호출부 0, D-4B8-1 「두 번째 분류기 금지」).

**— 기록 끝 —**

### OPS-05 (V26 · `outbox.terminal_reason`)

1. **사유를 안 주고 종단으로 보낸다** → `OutboxCommand.Fail`·`Isolate`·`Deliver` 가 사유를 **인자로
   요구**한다(생성자). 사유 없는 전이는 **컴파일되지 않는다**.
2. **비종단 행에 사유가 붙는다**(`PENDING` 에 「실패」) → CHECK: 사유가 있으면 상태는 종단이다.
   역방향(종단이면 사유가 있다)은 기존 행 때문에 CHECK 가 아니라 **전이 트리거**가 진다.
3. **사유와 상태가 어긋난다**(`FAILED` + `ORPHANED_CLAIM`) → 사상 CHECK 가 7 쌍만 허용하고 그 표가
   Kotlin 전수와 같음을 test 가 잰다.
4. **어휘를 문자열로 새로 짓는다**(어댑터에서 `"FAILED_SOMEHOW"`) → 사유 타입은 `workflow.event`
   의 닫힌 집합이고 어댑터는 그 값의 `name` 만 바인딩한다(SQL 리터럴 0).
5. **미지 payload 를 다시 조용히 접는다** — V6 때 「어휘에 칸이 없어」 `ISOLATED` 로 접은 자리.
   → 이제 `UNKNOWN_PAYLOAD` 가 별 사유이고, 기존 `RelayReport.unknownPayload` 계수와 **같은 수**임을
   test 가 잰다(계수와 DB 가 어긋나지 않는다).
6. **고아 격리가 사유 없이 남는다** → `ORPHANED_CLAIM`. 「왜 ISOLATED 인가」 셋(미지 payload · 배달
   결과 불명 · 고아)이 DB 에서 갈린다.

### OPS-10 (V27 · 릴리스 칸 **둘** + 기본값 제거) — 아래 열 표의 「넷」은 당시 계획이다

1. **기본값이 다른 이름으로 돌아온다**(`"unknown"`·`"local"`·`""`) → 설정 칸에 기본값이 없고
   공백은 값 객체(`ReleaseIdentifier`)가 거부한다. **sentinel version 금지**(data-dictionary §4.1).
2. **칸은 생겼는데 쓰기 경로가 NULL 을 넣는다** → notice·opening_result 는 NOT NULL,
   collection_run·outbox 는 **INSERT/UPDATE 거부 트리거**(아래 (2b) 표의 「기존 행」 때문).
3. **기존 행에 지어낸 값을 채운다** → notice·opening_result 는 `observation_key →
   raw_observation.release_sha` 로 **실제 출처에서** backfill(지어내지 않는다). 그 링크가 없는
   collection_run·outbox 는 NULL 로 남기고 「모름」을 그대로 적는다.
4. **키를 셋으로 두어 한 프로세스가 두 릴리스를 적는다** → 키를 **하나**로 모은다
   (`bidvector.release.sha`). 수집·개찰수집의 옛 키 둘은 제거(중복 금지).
5. **compose·runbook 이 안 주어도 뜬다** → 기본값이 없어 **기동 실패**다. dev·운영 compose 와
   runbook 명령 전수를 같이 갱신하고, 그 목록을 evidence 에 적는다.
6. **test 배선이 기본값에 기대고 있었다** → 기본값 제거 커밋에서 깨지는 Spring 컨텍스트 전수를
   같은 커밋에서 갱신한다(안 돌린 게이트가 아니라 **CI job 명령 그대로**로 확인).

## (2b) 값 획득 축 — 새 public 표면 전수

| 새 표면 | 획득자가 할 수 있는 일 | 막는 것 |
| --- | --- | --- |
| `BaseAmountClassificationRecordPort`(workflow.evaluation, interface) | 임의 판정 행을 기록 | 인자가 `ProvenanceJudgement`(커널만 생산) + `PolicyVersion`. 지어낸 라벨·규칙 쌍은 결속 CHECK 가 거부 |
| `JdbcBaseAmountClassificationStore`(adapters) | 표에 INSERT | GRANT 는 SELECT·INSERT 만. append-only 트리거가 UPDATE·DELETE 거부 |
| `OutboxTerminalReason`(workflow.event, 닫힌 집합) | 사유를 골라 전이 | 사유만으로는 전이를 못 만든다 — `OutboxTransition` 생성자는 여전히 `internal`, `transitionOutbox` 도 `internal` |
| `OutboxCommand.{Deliver,Fail,Isolate}` 가 사유를 받는 data class 로 | 같음 | 같음(전이표를 지나야 port 에 닿는다) |
| `ReleaseIdentifier`(adapters.persistence, value class) | 릴리스 문자열을 감싼다 | 공백 거부. 「경계로 처리」 행 아님 — 실측: 공백·빈 문자열 생성이 `require` 로 거부됨을 test 가 잰다 |
| `bidvector.release.sha` 속성 | 임의 라벨 주입 | **의도된 주입점**이다(운영자가 jar SHA 를 댄다). 라벨↔jar 내용 동일성은 경계 밖(`OPEN-6G-RELEASE-SHA-LABEL`) |
| `object` 커널 주입 자리 | — | 새 `object` 커널 0. 정책 singleton(`SAMPLE_PROVENANCE_POLICY`)은 기존 자리 그대로 |

## (3) 과잉·미달

**과잉 후보**
- outbox 에 시도 계수(`attempts`) 칸 — 재시도 API 가 없어(전이표에 종단→비종단 간선 0) 셀 것이
  없다. **두지 않는다**(OPS-05 의 「시간 기반 janitor」도 계약이 등재만 하라고 적는다).
- `base_amount_classification` 에 입력 스냅샷 해시 — `DecisionProvenance` 의 축이고 이 slice 의
  요구가 아니다. **두지 않는다**(OPEN 으로 등재).

**미달 위험**
1. **커밋 경로 외의 판정은 기록되지 않는다** — bid-price 조회·dry-run 이 쓴 version 은 남지 않는다.
   조회 경로가 쓰면 안 되므로(우회 2) 이것은 **설계된 범위**다. 알려진 제한으로 적는다.
2. **릴리스 칸이 NULL 인 기존 행** — collection_run·outbox 의 pre-V27 행. 「모름」이 정직한 값이고
   트리거가 **이후 행**을 전부 막는다. 알려진 제한으로 적고 계수를 evidence 에 적는다.
3. **api_request_audit 에는 릴리스 칸이 없다** — 계약이 네 표만 지목했다. OPEN 으로 등재.

## 열 타입 · NULL · backfill · GRANT · 인덱스

| 마이그레이션 | 표 | 열 | 타입 | NULL | 기존 행 | GRANT | 인덱스 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ~~(당시 V26)~~ | ~~`base_amount_classification`(신설)~~ — **제외**, 번호는 OPS-05 가 받았다 | 공고 키 2 · 정책 2 · 라벨 · 규칙 · 원본 금액 3 · 릴리스 · 기록 시각 | TEXT·DATE·NUMERIC(20,0)·TIMESTAMPTZ | 규칙만 NULL 허용(= `UNKNOWN` 결속) | 없음(신설) | SELECT, INSERT | PK(공고 2 + 정책 2) |
| V26 | `outbox` | `terminal_reason` | TEXT | 허용 | **NULL 로 남긴다** — 기존 종단 행의 사유는 진실한 원천이 없다(로그에만 갔다). 「사유가 있으면 종단이다」는 CHECK 로, 「**새** 종단 전이는 사유를 요구한다」는 전이 트리거로 가른다(V18 의 `pii_masked_at` NULL→값 1회 트리거와 같은 형태) — 쌍 CHECK 로 접으면 기존 행이 적용을 막는다 | 변경 0 | 변경 0 |
| ~~(당시 계획)~~ | ~~`notice`·`opening_result`~~ — **철회**(아래 갱신 1): 조인으로 도달하고, backfill `UPDATE` 가 V2 의 판 번호·감사 트리거를 깨운다 | — | — | — | — | — | — |
| V27 | `collection_run`·`outbox` | `release_sha` | TEXT | 허용 | NULL(진실한 원천 없음) + INSERT/UPDATE 거부 트리거 | 변경 0 | 없음 |

**두 축이 같은 모양을 쓴다** — 「기존 행은 NULL(모름), 새 행·새 전이는 구조가 요구」. 지어낸 값으로
과거를 채우지 않고, 앞으로의 누락은 CHECK·트리거가 막는다.

## 구현 중 갱신 (같은 slice, 실측으로 갈린 둘)

1. **V27 의 표가 넷 → 둘.** 위 표는 `notice`·`opening_result` 에 열을 더하고 `observation_key` 로
   backfill 한다고 적었다. 두 사실이 그 계획을 접었다 — ① 그 두 표의 릴리스는 **조인으로 이미
   도달한다**(NOT NULL FK → `raw_observation.release_sha` NOT NULL): 열을 더하면 같은 사실이 두
   자리에 살고 한쪽만 바뀌는 날이 온다 ② backfill 의 `UPDATE` 가 V2 의 **판 번호 증가·감사 행 삽입
   트리거를 깨운다** — 되돌림이 아닌 변경을 감사 장부에 쌓는다. 그래서 칸은 되짚을 경로가 **아예
   없는** 두 표(`collection_run`·`outbox`)에만 두고, 조인 갈래의 전제 셋을 test 로 고정했다.
2. **`ReleaseIdentifier` 는 `value class` 가 아니다.** 인라인 클래스가 JVM 시그니처에서 `String`
   으로 지워져 Spring 타입 주입이 깨졌다(배선 test 일곱 RED). (2b) 표의 「공백 거부」 축은 그대로다.

3. **DEC-08 축 제외**(팀장 결정 ①) — 위 DEC-08 절 머리에 근거를 적었다. 번호를 당겨
   OPS-05 = V26 · OPS-10 = V27 이고, 그에 따라 (2b) 표의 DEC-08 표면 셋(기록 port · Jdbc store ·
   기록하지 않는 구현)은 **이 slice 의 표면이 아니다**.

**(당시 기록) (2b) 표에 더해졌던 DEC-08 표면 둘**: `JdbcBaseAmountClassificationStore`(adapters.ml — 의존
게이트가 정한 자리, `JdbcCompetitionSampleSource` 와 같은 사유) · `DiscardingBaseAmountClassificationRecordPort`(부작용 없는 조립이 끼우는 구현 — 획득자가 할 수 있는 일은 **아무것도 하지 않는 것**뿐).

4. **조인 한계의 정정**(verifier M-1) — 갱신 1 이 「조인으로 **이미 답해진다**」고 적었는데, 조인이
   주는 것은 「그 **원문을 수집한** 릴리스」다. `notice` 는 수집 run 이 원문과 canonical 을 같은 run
   에 쓰므로 그 둘이 같지만, **승격 러너**가 쓰는 `opening_result`·`opening_reserve_price` 는
   「이 행을 **쓴** 릴리스」가 아니다. 칸 추가의 비용(판 번호·감사 트리거)은 그대로여서 이 slice 는
   칸을 더하지 않고 `OPEN-M9-PROMOTED-ROW-RELEASE` 로 넘긴다 — 갱신 1 의 **근거 ②는 유효하고
   근거 ①이 좁아진** 형태다.
5. **새 필수 키의 기동점 census 는 이 slice 가 세우지 않는다**(팀장 판단) —
   `OPEN-M9-REQUIRED-KEY-ENTRYPOINT-CENSUS`. 그 부재는 **두 번** 실현됐다: cr H-1(여정 하네스)과
   v H-1(6G runbook 의 스냅숏 추출 갈래 — 키가 갈래 둘에만 적혀 있었다). 손으로 고치는 방식이
   같은 함정을 반복한다는 뜻이라, 그 runbook 은 키를 **공통 인자 블록 한 자리**로 모아 갈래가
   늘어도 함께 가게 했다. 음성 대조도 두 층으로 섰다 — **바인딩 층**(값이 없으면 조립이 뜨지
   않는다)과 **설정 원천 층**(출하 기동점 `productionApplication()` 이 준비한 환경에 그 키가 없다).
   둘째 층은 v M-3 이 세우고 **v N-1 이 고쳤다** — 자기 사본 `SpringApplication` 은 그 기동점이 거는
   default properties 를 지나지 않아, 그 맵에 기본값 한 줄을 넣으면 app 전건이 초록이었다. 이제 그
   기동점 호출 자체로 환경을 준비하므로 default properties·ConfigData·시스템 속성·명령행이 한
   단언에 걸린다. census 가 없으니 남은 자리는 문면이 든다: `tools/image-hygiene-check.sh` 의
   jvm-app `PROBE_ENV` 하나(v L-4, 오늘 무해 — capability map 의 그 OPEN 행이 적는다).
6. **설계 검토가 놓친 축 하나 — 마이그레이션의 잠금 대기**(cr H-1, 라운드 7 에서 더한다).
   (2b) 「값 획득 축」도 「우회 ≥5」도 **DDL 이 기동을 멈추는 자리**를 세지 않았다. 위협 모델 문장이
   행의 내용만 보고 **적용 순간의 가용성**을 보지 않은 결과다. 이 축의 우회는 둘이고 둘 다 막혔다 —
   ⓐ 상한 없이 ALTER 하면 열린 트랜잭션 뒤에서 **무한 대기**(기본 `lock_timeout` 0)이고 그 큐 뒤로
   그 표의 읽기까지 선다 → 두 파일이 `SET LOCAL lock_timeout = '5s'` 를 선언한다 ⓑ 다음
   마이그레이션이 그 줄을 빠뜨린다 → 파일 전수에서 모집단을 계산해 선언 집합과 대조하는 census 가
   잡는다(표적 세 자리로는 네 번째가 조용히 들어온다 — 이 결함이 리뷰까지 온 경로가 그것이다).
   **DB 를 건드리는 다음 slice 의 설계 검토는 이 축을 (2b) 표에 처음부터 둔다.**

   **ⓑ 의 처방을 라운드 8 에서 바꿨다**(v r6 V-1). 파일 선언을 세는 census 는 정규식이라
   평범한 표기에 빗나간다 — 심은 15 변이 중 10 이 초록이었다. 안전망을 **migrate 연결**로
   내리고(`PersistenceWiring.ownerMigration` 의 `initSql`) census 는 **관례 lint** 로 격하했다.
   일반형: **「전수를 덮는다」는 주장은 그 술어가 전수를 볼 수 있을 때만 쓴다** — 텍스트 술어로
   SQL·설정 같은 자유 형식을 셀 때는 「고른 자리를 본다」가 정직한 문면이고, 전수는 그 형식을
   해석하는 층(여기서는 연결)에 건다.

   **그 층이 까는 것도 「기본값」이지 「하한」이 아니다**(cr r4 M-1). `SET LOCAL lock_timeout` 은
   세션 값을 덮으므로 파일이 명시적으로 `'0'`·`RESET`·더 큰 값을 적으면 그 파일은 밖이다 — 연결이
   닫은 우회는 ⓑ(**선언을 잊는다**)이고, 「의도적으로 푼다」는 관례 lint 가 기존 표 세 모양에
   한해서만 본다. 일반형의 짝: **값을 까는 층과 그 값을 덮을 수 있는 층이 다르면, 덮는 쪽이 남는
   우회다** — 문면은 그 둘을 갈라 적는다.
