# M6/6B-3 — checklist

base `e2232a75` · 산출물 커밋 14 · acceptance·변이 실측은 `commands.md` · 되돌림은 `rollback.md`.

## 리뷰 요청 조건

| 조건 | 상태 |
|---|---|
| 구현 diff 가 커밋되어 base/head 고정 | 산출물 커밋 14 · in_scope 밖 변경 0. **in_scope 와 대조**: 이 라운드가 만든 새 파일 다섯(`V19__retention_indexes.sql` · `NoticeAuditSnapshotAxis.kt` · `CleanMigrationIndexTest.kt` · `MaskingColumnEnumerationTest.kt` · `NoticeAuditPiiAxisTest.kt`)이 전부 in_scope 경로 안이다(V19 는 D-6B3-3 ⑥ 이 열었다) |
| 관련 test/lint/type/architecture/contract 통과 | `check --rerun-tasks` exit 0 · `qualityBaseline --rerun-tasks` exit 0(`commands.md`) |
| 변경된 fixture·정책 version 의 근거 | 정책 값 `RETENTION_POLICY`(90일·일괄 상한)의 출처는 운영자 결정 2026-09-26 ② + `scope.md` A-1~A-4 이고 그 문면이 `source` 에 적혀 있다. fixture 의 업체명 키는 **계약에서 읽어** 심는다(이름을 fixture 가 짓지 않는다) · PII 자리값은 형태를 흉내내지 않는다 |
| 알려진 제한과 rollback·비활성화 | 아래 「알려진 제한」 · `rollback.md` |
| 레인 동결 | **앞 라운드에서 한 번 깨졌다**(privacy P-1): 판정 SHA `575674d2` 를 보고한 뒤 팀장 지시로 test 셋이 늘어 `1dfd0094`·`5c622296` 가 쌓였고 검토 레인이 그것을 보지 못했다. 이 라운드는 그 둘을 흡수했고(vr F-7·F-8·F-10 이 그 커밋으로 이미 닫혔다 — 아래 대응표) 재작업 중에는 판정을 요청하지 않았다 |

## 검토 r1 의 지목 ↔ 무엇이 막는가 (대응표)

verifier 11 + code-review 10 + migration 5 + privacy 6 전수. 「무엇이 막는가」는 **게이트/test** 이고,
그것이 실제로 막는지는 변이로 쟀다(번호는 `commands.md` 변이 표).

| finding | 조치 | 무엇이 잰다 | 변이 |
|---|---|---|---|
| vr F-1 (high) 정책 값 **운반** 미잠금 | 조립 안의 정책 값을 **한 벌**로(러너가 보존 기간을 따로 받지 않고 `purge.policy` 를 읽는다) + 그 한 벌이 `RETENTION_POLICY` 해석값과 같음을 배선 test 가 단언 | 배선 test(컨텍스트를 띄워 러너 빈의 정책을 잰다) | V-1(컴파일 거부) · N-13 |
| vr F-2 (high) `notice_audit` 술어 미구현 | ⓐ `previous_row` 키 ⊆ `notice` 열을 **실제 감사 행**에서 실측 ⓑ `notice` 열 집합 ↔ 보존 분류가 아는 열 집합 **등식**(새 열은 분류 없이는 초록이 아니다) ⓒ FK 부재 실측 | `NoticeAuditPiiAxisTest`(음성 대조 포함) | V-3 |
| vr F-3 (medium) CASCADE 자식이 기한 안쪽에 지워진다 | CASCADE 간선에도 **기한 가드**(운영자 결정 2026-10-14) — 기한 안쪽 자식이 있으면 부모를 남긴다 | 「120일 전 헤더 · 오늘 행」 생존 + **수렴** test 둘 | N-7 |
| vr F-4 (medium) 표지를 미리 찍은 INSERT | 트리거에 **INSERT 관문**(표지는 적재가 찍지 않는다) + 음성 대조(보통의 적재는 지난다) | 가드 test 둘 · 트리거 **이벤트 집합 등재** | N-3 |
| vr F-5 (medium) 관문 ② 가 역할 이름 deny-list | **표 소유자 allow-list**(`pg_class.relowner`) — 제3 역할에 권한을 줘도 막힌다 | 가드 test(트랜잭션 안에서 역할을 실제로 만들고 권한을 준다) | N-2 |
| vr F-6 (medium) cutoff 타입 벽이 비어 있다 | `cutoffFrom` 과 `RetentionPolicyData` 의 생성자·`copy` 를 `workflow` `internal` 로 — 임의 cutoff 를 만드는 호출이 **컴파일되지 않는다** | 컴파일 + 기존 ArchUnit 등식 | V-1 |
| vr F-7 (low) 임대 키 서로소 미잠금 | 「동시에 쥘 수 있는가」를 양방향으로 잰다(상수 비교가 아니다) | `RetentionLeaseKeyTest` | V-2 |
| vr F-8 (low) 자식 가드의 수렴 미실측 | 둘째 run 단언 신설(비-CASCADE·CASCADE 각각) | 수렴 test 둘 | N-7 |
| vr F-9 (low) 비객체 투영 열이 run 을 실패시킨다 | **CHECK 제약**으로 들어올 수 없게(V18 `NOT VALID` + V19 검증) — 조회에서 피하면 그 행의 남김 집합이 조용히 빈다 | 가드 test(비객체 INSERT 거부) + CHECK 수 장부 | N-4 |
| vr F-10 (low) `payload` 읽기 0 이 수동 수집 | 기계 수집 test(공허 대조 둘 + 음성 대조) | `RawPayloadProductionReaderTest` | M-15(앞 라운드) |
| vr F-11 (비방어) 소유자의 임의 내용 주입 | 등재만 — 소유자 임의 SQL 은 비방어 축 | runbook 알려진 제한 9 | — |
| vr L-1·L-2·L-3 (장부) | `commands.md` skipped 수 정정 · (2b) 의 cutoff 행을 실측과 맞춤(이제 참이다) · 소유자 연결 행에 「`OPEN-6E2A` 와 같은 축」 명시 | 이 문서 | — |
| cr H-1 (high) `ISOLATED` 파기 | 파기 집합을 **`DELIVERED` 하나**로(D-6B3-3 ③) + test 이름과 거동을 맞춘다 | 상태 다섯 심고 **하나만** 사라짐 | N-8 |
| cr M-1 (medium) 판독 깊이 < 수집 상한 | 깊이를 수집 상한 이상으로 두고 그 **관계를 전 entry 에 대해** 잠근다 | `RawPayloadMaskingTest` 둘(관계 + 깊은 중첩이 폐기되지 않음) | N-11 |
| cr M-2 (medium) 정책 생성자·`copy` 공개 | `internal constructor` + `@ConsistentCopyVisibility` | 컴파일(app test 가 정책을 **만들 수 없다**) | V-1 |
| cr M-3 (medium) `notice_audit` 사유가 PII 축만 답한다 | 사유를 사실로 다시 쓴다(파기된 `notice` 스냅숏이 창 밖에 남고 표가 무한히 자란다) + 알려진 제한·새 OPEN | runbook §1.1 · 알려진 제한 8 · FK 부재 test | — |
| cr L-1 없는 test 클래스를 가리킴 | 실측 자리를 실존 test 로 정정 | — | — |
| cr L-2 표지만 찍는 UPDATE 통과 | **막지 않고 실측**한다(막으면 치환값이 원문과 같은 행에서 run 이 fail-stuck 이다) | 가드 test(그 성질을 잰다) · runbook 알려진 제한 9 | — |
| cr L-3 식별자 인용 없음 | `quoteIdent`(따옴표가 든 이름은 거부) | 전 문장이 그것을 지난다 | — |
| cr L-4 배선이 시스템 시계로 정책 해소 | 주입된 시계로 해소 | 배선 test(기준일이 시계에서 온다) | N-13 |
| cr L-5 일괄 종료가 계수한 수 | 종료 조건을 **후보 수**로(건너뜀 계수 신설, P-3 의 「건너뜀 N」) | use case test(가득 찬 일괄이 한 번 더 돈다) | N-9 |
| cr L-6 자기참조 FK → run 전체 `REFUSED` | 자기참조 간선을 순환에서 제외 | `DeletionOrderTest` | N-10 |
| mig H-1 (high) 적용 창이 전수·전차단·기동중 | 인덱스를 **V19 로 분리** — V18 은 메타데이터 변경만(밀리초), V19 는 `SHARE`(읽기는 지난다). `CONCURRENTLY` 는 **실측으로 기각**(아래 알려진 제한 11) + 적용 창·크기 측정 절차 등재 | runbook §1.3 · 인덱스 장부 | N-12 |
| mig M-1 (medium) forward-fix 서술 누락 | 되돌림 목록에 고아 함수 DROP · V19 객체 DROP · **표지 소실과 재적용 시 fail-closed 표식 소실** | runbook §1.2 · `rollback.md` | — |
| mig M-2 (medium) 파기 축 인덱스 부재 | cutoff 인덱스 아홉(V19) — `outbox` 는 (상태, 적재 시각) 복합으로(부분 술어면 종단 집합이 바뀌는 날 조용히 안 쓰인다) | 인덱스 장부 | N-12 |
| mig L-1 관문 ② 문자열 | vr F-5 와 같은 조치 | 같음 | N-2 |
| mig L-2 인덱스 장부 부재 | **축9 신설** — (표, 이름) 쌍 전수 + invalid 인덱스 0 | `CleanMigrationIndexTest` | N-12 |
| privacy F-1 (high) 업체명이 남는다 | **업체명을 마스킹 대상으로**(운영자 결정 2026-10-14) — 개념 축으로 빼고 투영 열도 마스킹 열로. 소비자 읽기 집합에 업체명이 없음을 기계 수집으로 실측(`commands.md`) | 마스킹 test 넷(두 열 각각 + 음성 대조) · 가드 test(두 열이 마스킹 열) | N-1 · N-5 |
| privacy F-2 (medium) r1 ① 술어 부재 | vr F-2 와 같은 조치 | 같음 | V-3 |
| privacy F-3 (medium) 자격 값을 argv 로 | 템플릿을 환경변수로 고치고 §1.4 를 가리킨다 | runbook §2 | — |
| privacy F-4 (low) 누출 스캔 범위 과대 진술 | 문장을 실측과 맞춘다(게이트는 evidence 만 본다) | `commands.md` | — |
| privacy F-5 (low) 첫 실행 전 계수 절차 부재 | 표별 `COUNT(*)` 절차 등재 | runbook §2.2 | — |
| privacy P-1 (절차) 동결 위반 | 위 「리뷰 요청 조건」 마지막 행에 사실로 등재 | — | — |

## 설계 검토 (2) 우회 ↔ 무엇이 막는가

| # | 우회 | 막는 것 | 변이 |
|---|---|---|---|
| 1 | 앱 풀 연결로 원문 UPDATE | ⓐ GRANT 미부여(권한 행렬 test — 유효 권한으로 본다) ⓑ 트리거의 **표 소유자 allow-list**. ⓑ 는 GRANT 를 트랜잭션 안에서 실제로 주고 재며, **제3 역할**에도 같은 거부가 나는 것을 잰다 | M-8 · N-2 |
| 2 | 소유자로 다시 UPDATE 해 원문 복원(값→값·값→NULL) | 트리거 관문 ④ — 표지가 NULL→값으로 **한 번만** | M-9 |
| 3 | 마스킹에 다른 열 변경을 끼워 넣기 | 트리거 관문 ⑤ — 마스킹 열 둘·표지를 뺀 **행 전체 jsonb 비교**. 그 성질을 **열 카탈로그 전수 열거**로 잰다(열 하나를 손으로 고르지 않는다 — 모르는 자료형이 나오면 실패한다) | M-10 · N-6 |
| 4 | 시계를 미래로 → 전부 파기 | 정책 불변식(0·음수 거부) + cutoff 생성 경로가 `workflow` `internal`. 시계 위조 자체는 **비방어** | 정책 test 3 · V-1 |
| 5 | outbox 미발송 행 파기로 알림 소실 | 파기 조건이 **`DELIVERED` 하나**이고, 상태 다섯을 전부 심어 **하나만** 사라지는 것을 잰다. 어휘는 CHECK 제약에서 읽어 등식으로 묶는다 | N-8 · M-2·M-3 |
| 6 | 부모를 먼저 지워 FK 실패 → 부분 파기 | ⓐ 순서를 FK 간선에서 위상 정렬(자기참조는 순환이 아니다) ⓑ **모든** 자식 간선에 가드 — 비-CASCADE 는 「자식이 있으면」, CASCADE 는 「**기한 안쪽** 자식이 있으면」 부모를 남긴다 ⓒ 그 대가가 **수렴**함을 둘 다 잰다 | M-12 · M-5 · N-7 · N-10 |
| 7 | 새 표가 분류 없이 들어옴 | 분류 ↔ DB 카탈로그 집합 등식(빌드 RED + 런타임 거부 두 겹) | M-4 |
| 8 | 보존 러너를 수집·relay 와 같은 프로세스에 켬 | 기존 `OneShotRunnerGuard` + 배선 test | — (기존 게이트) |
| 9 | 러너 둘을 동시에 띄워 이중 계수·교착 | 전용 키의 advisory lock 임대 · 그 키가 relay 키와 겹치지 않음을 「동시에 쥘 수 있는가」로 양방향 실측 | M-13 · V-2 |
| 10 | 마스킹 실패를 원문 그대로 두고 표지만 찍기 | fail-closed(값 전체 대체 + 따로 셈 + 종료 코드 2). **판독 깊이를 수집 상한과 묶어** 「받아들인 원문을 폐기」하는 경로를 닫았다 | M-7 · N-11 |
| 추가 1 | allow-list 의 근거(「`payload` 를 아무도 읽지 않는다」)가 사라짐 | 기계 수집 test(공허 대조 둘 + 음성 대조) | M-15 |
| 추가 2 | allow-list 반전이 deny-list 로 되돌아감 | 남길 키의 출처를 그 행의 투영 열로 둬 목록을 두 벌 두지 않는다 · 미지 키는 이름을 몰라도 빠진다 | M-6 |
| 추가 3 | **계약에 등재된 PII** 가 남김 집합에 들어옴(업체명) | 지울 키를 **개념 축**으로 선언하고 rawName 은 계약에 묻는다 — 계약 없는 개념은 **구성 오류로 던진다**(조용히 빈 집합이면 아무 일도 안 하면서 초록이다) | N-1 |
| 추가 4 | 뒤 마이그레이션이 보존 조회의 인덱스를 지움 | 인덱스 장부(축9) + invalid 인덱스 0 | N-12 |

## (2b) 값 획득 축 — 새 public 표면 전수와 그것이 허락하는 것

| 표면 | 밖에 무엇을 허락하는가 | 판정 |
|---|---|---|
| `RetentionRunner`(유일한 빈) | 기동 시 파기·마스킹 실행 | 경계로 처리 — `mode=once` 없이는 **빈이 아니다**(test) · 다른 일회 러너와 동거 불가 |
| `RetentionRunner.purge`(`internal`) | 같은 모듈에서 use case 읽기 | 무해 — **운반 등식을 재기 위한 표면**이고 app 밖에서 보이지 않는다 |
| `RetentionWiring` | 소유자 권한 연결 **생성** | 경계로 처리 — 소유자 `DataSource`·저장소·use case 가 **모두 빈이 아님**을 test 가 잰다(빈 0). `PGSimpleDataSource` 참조자 집합 == {`PersistenceWiring`, `RetentionWiring`}(ArchUnit 등식). **그 등식은 클래스 이름 술어다**(vr L-3): 소유자 자격은 `PersistenceProperties` 로 어떤 `DataSource` 타입으로도 얻을 수 있으므로 이 행은 `OPEN-6E2A-OWNER-CREDENTIAL-IN-APP` 비방어와 같은 축이다 |
| `JdbcRetentionStore`(유일한 공개 생성자) | 소유자 `DataSource` + **기준일**을 받아 파기 능력 생성 | 경계로 처리 — 위 등식이 쥔 자를 닫고, 쓰기는 임대 클로저 안에서만 손에 들어오며 모든 쓰기가 `RetentionCutoff` 를 요구한다. **계약 레지스트리를 받지 않는다**: 받으면 조립이 「원문 값 획득」 표면을 쥐게 되고 그것은 D-6F8-6 게이트가 닫아 둔 자리다(그 게이트가 실제로 막았다) |
| `RetentionCutoff` | cutoff 값 **읽기** | 생성자가 `workflow` `internal` **이고 그것을 만드는 함수도 `internal`**(vr F-6) — `cutoffFrom(임의 시각)` 이 모듈 밖에서 컴파일되지 않는다(앞 판은 생성자만 닫아 이 경로가 열려 있었다) |
| `RetentionPolicyData` | 보존 기간·일괄 상한 **읽기** | 생성자와 `copy` 가 `workflow` `internal`(cr M-2) — 밖에서 얻는 경로는 `RETENTION_POLICY` 해소 하나다. 생성 시 불변식(0·음수·일괄 0 거부)은 그대로 |
| `PurgeExpiredData.policy`(public 읽기) | 정책 값 읽기 | 무해(만들 수 없는 값의 getter) — **운반 등식의 피측정 자리**다 |
| `MaskBatchCounts.skipped` | 계수 읽기 | 무해. 종료 조건이 이 값을 포함한다(cr L-5) |
| `RetentionRule.maskedColumns`·`maskedRawNamesOf`(`internal`) | 마스킹 열 집합·지울 키 집합 | 무해(데이터). 열거가 아니라 **개념 → 계약** 유도이고 빈 집합은 던진다 |
| `NOTICE_SNAPSHOT_CLASSIFIED_COLUMNS`(`internal`) | 분류된 열 집합 읽기 | 무해(데이터). 카탈로그와 등식이라 새 열은 분류 없이는 초록이 아니다 |
| `RetentionExitCode`·로그 줄 facade | 종료 코드·문자열 | 무해. 로그에 **행 값이 없음**을 test 가 잰다 |
| V18 `pii_masked_at` 열 | 마스킹 표지 | 트리거 **다섯 관문**(DELETE·INSERT·소유자·일방향·마스킹 열 밖 불변) · 열 등재 · 트리거 → **함수 쌍**과 **이벤트 집합** 등재 |
| V18 CHECK `payload_fields` 객체 | — | 능력 아님(제약). CHECK 수 장부에 등재 |
| V19 인덱스 열 | 질의 계획 | 능력 아님. **인덱스 장부**(축9)와 invalid 0 |
| 분류 레지스트리(`internal`) | 읽기 | 데이터. 스키마와 집합 등식 |

## 면제 수 래칫 46 → 51 — 다섯 각각의 사유

이 라운드가 더한 app production 클래스는 **0** 이므로 래칫은 51 그대로다(`AppHttpDependencyGateTest`
초록). 다섯의 사유는 앞 라운드와 같다 — `RetentionWiring`(① 부팅·배선) · `RetentionRunner`·
`RetentionRunnerKt`·`RetentionExitCode`·`RetentionLinesKt`(③ 러너 레인, HTTP 도달 없음).

## 운영자 결정이 실제로 선 자리

| 결정 | 어디서 서는가 |
|---|---|
| A-1 파기 주체 = 소유자 비풀링 연결 · 앱 역할 경계 무변경 | V18·V19 에 **GRANT 문 0** · 배선이 풀을 받지 않는다 · 권한 행렬 test 무변경 |
| A-2 기준 시각 = 적재 시각 축 하나 | 레지스트리의 시각 열이 **실존하고 NOT NULL 인 TIMESTAMPTZ** 임을 카탈로그로 실측(CASCADE 자식의 기한 열까지 — 그 열이 nullable 이면 가드가 그 행을 못 본다) |
| A-3 `raw_observation` 마스킹만 · 표본 보존 | 파기 목록에 없다(행 수 불변 test) · `notice_audit` 은 구멍 없음 + **등식으로** PII 부재를 잠근다 |
| A-4 `outbox` 미발송 제외 · `edit_session` 포함 · 제외 여덟 | 상태 다섯 중 **`DELIVERED` 하나**만 사라짐(`FAILED`·`ISOLATED` 생존) · `edit_session` 시각 단일 술어 · 제외 표 행 수 불변 |
| 2026-10-14 ① 업체명은 마스킹 대상 | 두 열에서 사라짐 + 기한 안쪽 음성 대조 + 소비자 읽기 집합 실측 |
| 2026-10-14 ② CASCADE 자식 기한 가드 | 「120일 전 헤더 · 오늘 행」 생존 + 수렴 |

## 알려진 제한

1. **`outbox` 의 `FAILED`·`ISOLATED` 행은 영구히 남는다** — 둘 다 종단이지만 **미발송**이라 A-4 의
   제외에 들어간다. `ISOLATED` 는 알림을 영구히 잃은 사건의 유일한 흔적이고 운영 조사 경로가 그
   행을 읽는다. 그 행 수가 자라면 처분이 결정 축이다(`OPEN-6B3-OUTBOX-UNSENT-RETENTION`).
2. **기한 안쪽 자식이 있는 부모는 그 run 에서 남는다**(우회 6 의 대가, CASCADE 간선 포함). 자식이
   기한을 넘는 다음 run 에서 함께 사라지고 그 수렴을 test 가 잰다.
3. **실패한 run 의 부분 계수는 로그에 남지 않는다** — 예외는 원인 코드와 종료 코드로만 나간다.
4. **「이미 마스킹된 행 건너뜀」은 두 겹이고 각각을 단독으로 잠그는 test 가 없다**(M-14a·M-14b 생존).
   test 가 잠그는 것은 거동이고, 되돌릴 수 없음의 DB 층 보장은 트리거가 독립으로 진다(M-9).
5. **`payload` 읽기 자리 0 의 술어는 SQL 문자열을 본다** — 질의가 다른 형태로 조립되면 그 술어의
   모집단 밖이다. 공허 대조 둘과 음성 대조로 「술어가 아무것도 못 보는 상태」만 막았다.
6. **운영 DB 에서 한 번도 돌리지 않았다.** 모든 실행이 Testcontainers 위에서다 — 첫 실행의
   부풀음·소요와 V19 의 실제 적용 창은 측정되지 않았다(크기 측정 절차는 runbook §1.3·§2.2).
7. **수집 시점 마스킹은 하지 않는다** — 원문은 90일 동안 담당자 PII·업체명을 가진다(결정 문면 그대로).
8. **`notice_audit` 은 파기도 마스킹도 받지 않아 무한히 자란다**(cr M-3). 파기된 `notice` 행의 전체
   스냅숏이 90일 창 밖에도 남는다 — 담당자 PII 는 그 스냅숏에 없다(등식으로 잠근다).
9. **표지만 찍는 UPDATE 와 임의 내용 주입은 소유자에게 열려 있다**(cr L-2 · vr F-11). 막으면
   치환값이 원문과 같은 행에서 run 이 fail-stuck 이 되므로 **막지 않고 성질을 실측**했다. 소유자
   자격을 가진 주체의 임의 SQL 은 비방어 축이다(그 주체는 트리거도 DROP 할 수 있다).
10. **적재마다 트리거 함수가 한 번 더 돈다**(INSERT 관문). 분기 둘이라 행당 비용은 작지만 수집
    처리량 실측은 M7 축이다.
11. **`CREATE INDEX CONCURRENTLY` 를 쓰지 않았다 — 실측으로 기각했다.** Flyway 는 메타데이터
    연결에 트랜잭션을 열어 둔 채 마이그레이션을 다른 연결로 돌리므로, `executeInTransaction=false`
    를 줘도 `CONCURRENTLY` 가 동시 트랜잭션을 기다리며 **영구 대기**한다(실측: `idle in transaction`
    인 Flyway 연결과 `Lock/virtualxid` 로 막힌 마이그레이션). `flyway.postgresql.transactional.lock`
    을 끄면 풀리지만 **Flyway 호출 자리 넷이 전부** 그것을 알아야 하고 하나라도 잊으면 거기서
    실패가 아니라 **정지**한다. 그래서 V19 분리로 읽기 차단만 없애고, 남은 **쓰기 차단 창**은
    runbook §1.3 이 측정 절차와 함께 든다(`OPEN-6B3-INDEX-WINDOW`).
12. **시계는 신뢰 앵커다**(설계 검토 (0) 비방어).
13. **TDD 순서 — 이 라운드는 test 를 먼저 썼으나 RED 를 별도 run 으로 재지 않았다.** 산출물과 test
    가 같은 작업 단위에 있어 첫 실행이 GREEN 이었다(앞 라운드와 같은 이탈). 보상으로 **새 술어
    하나하나에 변이를 적용해 RED 를 실측**했다(`commands.md` 변이 표 — 바꿔치우기이고 되돌린 뒤
    porcelain 이 빈 것을 매번 확인했다).

## 받은 OPEN 의 처분

| OPEN | 처분 |
|---|---|
| `OPEN-6F6-PROFILE-RETENTION`(`operator_profile`) | **닫힘** — 싱글턴 설정이라 시간 파기 제외로 분류(A-4) |
| `OPEN-6F5A-RETENTION`(요건 표 둘) | **닫힘** — 헤더는 `created_at` 파기, 행은 FK CASCADE 로 함께. 단 **기한 안쪽 행은 헤더를 남긴다**(D-6B3-3 ②) |
| `OPEN-6F8-RAW-PII-RETENTION`(`payload`) | **닫힘** — 90일 뒤 마스킹. 담당자 키가 남지 않음을 production 경로에서 실측 |
| `OPEN-6B3-RAW-OBSERVATION-RETENTION` | **닫힘 — 마스킹으로(업체명 포함)**. 상호는 담당자 키와 함께 두 열에서 사라진다(운영자 결정 2026-10-14). 남는 것은 계약 등재 키에서 업체명을 뺀 집합이고 그 집합이 6G 소비자의 읽기 키를 덮는다 |
| `OPEN-6A2B-ABANDONED-SESSIONS` | **닫힘** — `edit_session` 시각 단일 술어(D-6B3-1 ②) |
| 6F-7 privacy-gate 권고(`outbox`) | **닫힘** — 발송된 행만 파기(미발송·격리는 남는다) |
| D-6A1-7(`api_request_audit`) | **닫힘** — `occurred_at` 기준 파기 |

## 새 OPEN

| ID | 내용 |
|---|---|
| `OPEN-6B3-OUTBOX-UNSENT-RETENTION` | `FAILED`·`ISOLATED` outbox 행의 처분(알려진 제한 1) — 운영자 결정 축. 앞 라운드의 `OPEN-6B3-OUTBOX-FAILED-RETENTION` 을 `ISOLATED` 까지 넓혀 대체한다 |
| `OPEN-6B3-BLOAT-AFTER-FIRST-PURGE` | 첫 실행이 90일 백로그를 지운 뒤의 부풀음·`VACUUM` 운영 반입(M7 축) |
| `OPEN-6B3-PARTIAL-COUNTS-ON-FAILURE` | 실패한 run 의 부분 계수 보고(알려진 제한 3) |
| `OPEN-6B3-NOTICE-AUDIT-SNAPSHOT-RETENTION` | `notice_audit` 의 무한 증가와 창 밖 스냅숏 보존(알려진 제한 8, cr M-3) — 보존 자체가 운영자 결정 축 |
| `OPEN-6B3-INDEX-WINDOW` | V19 의 쓰기 차단 창(알려진 제한 11) — 큰 표에서 `CONCURRENTLY` 가 필요해지면 Flyway 잠금 전략을 함께 바꾸는 결정이 선행한다 |
