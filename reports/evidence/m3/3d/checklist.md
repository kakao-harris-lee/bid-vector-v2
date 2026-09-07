# M3/3D — checklist.md

base `01ecbba69e21e4b85ee8303416fe06a77b919fd6` · head `15f3325`(수정 라운드 3, verifier r3
P-1 대응 — F-1~F-8·N-1~N-5는 `2e31d4e` 시점과 변경 없음). base..HEAD 사이 `9089bd4`는
3C(다른 slice) 착수 커밋으로 `milestone-3.md`·`reports/evidence/m3/3c/scope.md`만
건드린다 — 3D in_scope 밖, 레인 혼입 아님(commands.md 「레인 혼입」 절 참고). 정본 순서:
`scope.md` → `_workspace/m3-3d/01_design-review.md`(Phase 2.5) →
`_workspace/m3-3d/02_verifier_report.md`(verifier r1) →
`_workspace/m3-3d/03_verifier_report_r2.md`(verifier r2) →
`_workspace/m3-3d/04_verifier_report_r3.md`(verifier r3) → 이 문서.

## 완료 조건 대응표 — scope.md 「이 slice 가 하는 일」

| # | 요구 | 구현 | test |
| --- | --- | --- | --- |
| ① | Flyway가 스키마 단일 출처, 4영역 | `V1__schema.sql`(raw_observation·notice/opening_result/qualification_text·notice_audit/rejected_write·collection_run) | `CleanMigrationTest`(D-3D-6 여덟 축 전부 대조) |
| ② | raw/canonical 분리, 원본 보존 | `JdbcRawObservationStore.append`가 canonical fold보다 먼저(별도 트랜잭션) raw 행을 만든다 | `RawAppendOnlyTest` 8건 |
| ③ | 멱등 upsert/versioning | `ObservationKey`(값 타입, procurement) + `ObservationKeyDerivation`(SHA-256 유도, adapters) + `observation_key` PK(`ON CONFLICT DO NOTHING`) + notice `revision` 트리거 증가 | `NoticeVersioningTest`, `F2CollisionRegressionTest` |
| ④ | 점유 가드 = DB constraint + write 규칙 | `guard_authoritative_slot()` 트리거(V2, 값·provenance·동반 컬럼 중 하나라도 바뀌면 검사 — 존재·점유·신선도 셋, N-1·N-2 뒤 재개정) + `guard_existence_and_freshness('status')` + Kotlin `mergeNoticeRow`(`mayOverwrite`와 동일 술어) | `PrecedenceMutationTest` 11건 + `PrecedenceParityTest`(Kotlin/DB parity) |
| ⑤ | 항목 단위 원자성·배치 회계 분리 | `JdbcNoticeRepository.persist`가 connection 하나 = 트랜잭션 하나. `JdbcCollectionRunStore.record`는 완전히 별도 호출/트랜잭션 | `ItemAtomicityTest` 3건 |
| ⑥ | 감사 보존 | 원본=raw_observation, 판정=canonical `*_provenance`, 오류=`collection_run.drop_reasons`+`rejected_write`. `notice_audit`는 SECURITY DEFINER 트리거로만 생긴다(app 역할 INSERT 권한 없음) | `NoticeVersioningTest`(audit 행), `RawAppendOnlyTest`(위조 INSERT 거부 + 정상 경로 성공) |
| ⑦ | PostgreSQL 전용, 조용한 no-op 금지 | JSONB·PL/pgSQL 트리거·`to_jsonb` | S-0~S-5 전건 실제 PostgreSQL 16.4 컨테이너 위에서 실행 |
| ⑧ | clean 재현 | `CleanMigrationTest` — 여덟 축을 코드 안 기대 목록과 대조(D-3D-6) | 위와 동일, 변이 셋으로 실측 — commands.md 「F-3 변이 재재현」(verifier r2 N-7 뒤 이 절을 채웠다) |

## finding 대응표 — verifier r1(수정 라운드 1)

| finding | 요지 | 대응 | 재검증 |
| --- | --- | --- | --- |
| F-1(high) | 값만 바뀌고 provenance는 그대로인 write가 통과 | 트리거가 「값이 실제로 바뀔 때만」 검사하고, observation_key가 그대로면 거부(신선도 가드) — **N-1(verifier r2)이 이 단락 자체의 회귀를 지적, 아래 참고** | `PrecedenceMutationTest`, 직접 SQL(commands.md) |
| F-2(high) | `ObservationKey` 32비트 hashCode 충돌("Aa"/"BB")로 관측 유실 | 유도를 어댑터로 옮기고 SHA-256으로 교체(`ObservationKeyDerivation`) | `F2CollisionRegressionTest` |
| F-3(high) | S-5가 D-3D-6 여덟 축 중 둘(테이블·트리거)만 대조 | `CleanMigrationTest`를 여덟 축 전부 대조로 재작성 | `CleanMigrationTest` 13건, 변이 셋 재현으로 FAIL 확인(commands.md 「F-3 변이 재재현」) |
| F-4(medium) | 비권위→비권위 write가 통과(Kotlin·DB 술어 불일치) | 트리거를 「이미 값 있으면 유입이 권위 있어야」로 통일(기존 권위는 안 봄, mayOverwrite와 동일) | `PrecedenceMutationTest`(단일 재현) + `PrecedenceParityTest`(12쌍 parity) |
| F-5(medium) | opening_result·qualification_text에 DB 가드 없음 | `guard_existence_and_freshness()`를 세 컬럼에 추가(존재+신선도, 점유 없음 — provenance 축 자체가 없어서) | `OpeningQualificationRepositoryTest`(F-5 재현 3건) |
| F-6(medium) | app 역할이 notice_audit에 위조 INSERT 가능 | INSERT 권한 제거 + 트리거 함수 SECURITY DEFINER | `RawAppendOnlyTest` 2건 |
| F-7(medium) | raw payload가 미등재 필드 값을 버림(원문 전체 아님) | **닫힘 — 3A·3B·3D 몫 전부 완료** | `RawObservationSourceTextRoundTripTest`, `KonepsOpenApiNoticeSourceTest`(F-7 3B 몫) |
| F-8(medium) | 재구성 폴백이 조용히 Open을 냄, find() 왕복 test 부재 | `EVENT_PATH_TO_STATUS.getValue` → **N-5(verifier r2) 뒤 exhaustive `when`(`eventPathFor`)으로 재개정**, 아래 참고 | `NoticeFindRoundTripTest`(왕복), `NoticeReconstructionTest`(N-5, 술어 커버리지) |

## finding 대응표 — verifier r2(수정 라운드 2)

| finding | 요지 | 대응 | 재검증 |
| --- | --- | --- | --- |
| N-1(high, 회귀) | F-1 수정의 "값이 실제로 바뀔 때만 검사" 단락이 "값은 그대로, provenance만 강등"을 다시 열었다 | `guard_authoritative_slot()`의 단락 조건을 「값과 provenance 가 둘 다 안 바뀔 때만」 통과로 좁혔다 | `PrecedenceMutationTest`(N-1 재현), probe(commands.md `(2c)/N-1`) |
| N-2(medium) | 가드가 축마다 값 컬럼 하나만 봐 통화·과세·status가 무방비 | `guard_authoritative_slot()`에 동반 컬럼(TG_ARGV[2..]) 축 판정 추가 + `guard_notice_status` 신설 | `PrecedenceMutationTest`(N-2 vat·status 재현) |
| N-3(medium) | 신선도 가드가 app 역할이 스스로 만든 raw 행 하나로 지나감 | **미수정 — 알려진 제한으로 등재**(raw INSERT 권한이 정상 수집 경로에 필요, 완전 차단은 경계 밖) | probe(commands.md), 아래 「알려진 제한」 |
| N-4(medium) | 존재 가드가 부분 관측(일부 컬럼 NULL 유입)을 항목 실패로 만듦 | `UPSERT_OPENING_RESULT`에 `COALESCE(EXCLUDED.col, opening_result.col)` | `OpeningQualificationRepositoryTest`(M-c 재현) |
| N-5(medium, 커버리지) | F-8이 바꾼 술어(표 밖 상태 → 예외)를 지키는 test가 없었다 | `EVENT_PATH_TO_STATUS` 맵을 exhaustive `when`(`eventPathFor`)으로 교체 — 분기 누락이 컴파일 실패가 된다(런타임 test보다 이른 방어) | `NoticeReconstructionTest`(양성 경로 고정) |
| N-6(low) | "sourceText 리터럴을 공유해 하나의 값으로 잇는다"는 말이 실물보다 강함(진짜 공유 상수가 아니라 같은 문자열을 손으로 두 번 쓴 것) | **미수정 — 표현을 완화**(아래 F-7 절 문구 정정) | checklist.md 「F-7」 절 |
| N-7(low, 장부) | evidence head 선언·rollback 커밋 수 주장·checklist ⑧ 대시 참조·위협모델 과대진술 | 이 evidence 갱신 자체 | commands.md·rollback.md·checklist.md(이 문서) |

## finding 대응표 — verifier r3(수정 라운드 3)

| finding | 요지 | 대응 | 재검증 |
| --- | --- | --- | --- |
| P-1(medium) | 가드 축 밖 라벨 컬럼 다섯(`*_provenance_detail`·`estimated_amount_source_key`·`floor_rate_origin_kind`/`_detail`)이 미선언인 채 열려 있었다 | `guard_authoritative_slot`·`guard_existence_and_freshness` 트리거 인자(동반 컬럼)에 다섯 컬럼 추가(함수 본문 무변경) | `PrecedenceLabelColumnTest`(위조 넷 재현 + 정상 fold 통과), `CleanMigrationTriggerTest`(가드 인자 완전성 대조) |
| P-2(low, 경계 미선언) | `notice.status`가 3D 쓰기 경로에서 한 번 쓰고 끝(Open 고정)이라는 사실이 어디에도 없었다 | **미수정 — 경계 문장에 명시**(코드 변경 없음, M4 소유 상태 전이와 무관) | checklist.md 위협 모델 경계 문장(위) |
| P-3(low, 장부) | 경계 문장의 `business_category` 사유("변경 경로 자체가 없다")가 사실과 다름(실물은 UPDATE_NOTICE가 매번 SET, 위조도 통과) | 사유 정정(「가드 축이 아니다」만 유지) | checklist.md 경계 문장(위) |
| P-4(low, 장부) | evidence 선언 head가 한 커밋 낡음(`6332158`→실제 HEAD `2e31d4e`), `2e31d4e`가 evidence 3종만 만졌다는 사실이 어디에도 없음 | head 재고정 + 이 사실 commands.md에 명시 | commands.md 머리말 |
| P-5(low, 규격) | `rollback.md`에 라운드 이력 절("이전 실측 — 결과 동일, 기록 보존")이 있어 evidence-pack 「라운드 이력 절 금지」 위반 | 절 삭제, 이번 실측 하나만 남김 | rollback.md |

### F-7 — 3A·3B·3D 몫 전부 닫힘(운영자 결정 2026-09-08 원 결정 + 3B 확장 (a))

**3A·3D(커밋 `eb82bb8`)**: `RawNoticeObservation`에 `sourceText: String?`(저장 전용, 도메인
소비 함수 무열람)을 추가만 했다 — 기존 생성자 호출처·corpus 27/27·3A test 불변(default
`null`). `raw_observation.payload`를 TEXT로 바꿔 `sourceText`를 재직렬화 없이 그대로 싣고,
이전 payload(계약 등재 필드만의 JSONB 투영)는 `payload_fields`로 옮겼다.
`ObservationKeyDerivation.of`의 재료에 `sourceText`를 더해 "등재분은 같은데 원문이 다른"
관측을 구분한다. `RawObservationSourceTextRoundTripTest`가 「저장 → 조회 → 바이트 동일」을
불규칙 공백·키 순서를 일부러 둔 원문으로 잰다(재직렬화를 거치면 이 형태가 사라진다).
`sourceText`가 없는 관측(koneps 밖 호출부·구 fixture)은 `payload_fields`로 만든 문자열을
대신 싣는다 — 그 경우 바이트 동일은 보장하지 않는다(알려진 제한, 아래).

**첫 실측 — 인터페이스 부족을 확인하고 정지**: `mapRawItem`이 받는 `JsonValue.JsonObject`
(`KonepsJson.kt`)가 원문 안 자기 위치(span)를 전혀 갖지 않고, 원문 전체(`body: String`)도
`mapRawItem` 호출 경로에 전달되지 않으며, mapper 파일 하나로 문자열을 만드는 유일한 기존
수단(`JsonValue.render()`)은 그 자신이 "재직렬화"라 이름 붙인 함수라 "재직렬화 금지"가
정확히 겨눈 것과 같았다 — "mapper 한 파일만" 경계 안에서 "재직렬화 금지 + 바이트 동일"을
동시에 만족할 길이 없어 코드를 더 고치지 않고 운영자에게 보고했다(브리프 지시대로).

**3B 확장 결정 (a, 2026-09-08) 뒤 닫힘**: `KonepsJson.kt`에 `JsonObject.sourceText: String`
필드를 추가만 했다(다른 필드·다른 koneps 파일 무편집, `render()` 미사용 유지) —
`JsonReader.readObject()`가 이미 갖고 있던 `CharCursor.position`을 object 시작 직전과 짝
`}` 직후에 한 번씩 읽어 원문 substring을 그대로 싣는다(재직렬화 없음). `mapRawItem`은
`item.sourceText`를 `RawNoticeObservation.ofRawValues`에 그대로 전달한다.
`KonepsOpenApiNoticeSourceTest`에 새 test(F-7 3B 몫) — 불규칙 공백·키 순서를 일부러 둔
항목을 mock server로 서빙하고 `sourceText`가 그 바이트와 정확히 같음을 잰다.
`RawObservationSourceTextRoundTripTest`(3D)도 같은 문자열 리터럴을 사람이 손으로 똑같이
옮겨 적어 써서 「서빙 → 관측 → 저장」 전 구간의 바이트 동일성을 같은 값으로 잰다 —
**N-6(verifier r2, low)**: 이 둘은 진짜 공유 상수가 아니다(두 파일에 같은 문자열이 각각
따로 적혀 있다). 한쪽만 바뀌면 조율 없이 조용히 끊길 수 있다 — 두 test 모듈(koneps는
adapter 어댑터 경계, persistence는 저장 계층)이 서로 다른 관심사라 상수 하나로 억지로
묶기보다 이 사실을 여기 명시로 남긴다(알려진 제한, 아래).

## 판단이 갈린 지점(evidence 필수 절, verifier가 재확인한 5건 + 뒤 추가)

1. **notice만 revision+audit+정밀 provenance 가드를 받는다.** opening_result·qualification_text는
   provenance 축이 없어 존재+신선도 가드만(F-5 뒤에도 이 구분은 유지 — verifier 판단 1이
   「부분 동의」로 확인). `ON CONFLICT ... WHERE observed_at >= ... RETURNING (xmax = 0)` 한
   SQL 문으로 insert/update/no-op을 전부 판별한다.
2. **`ObservationKey`(값)는 procurement, 유도(`ObservationKeyDerivation`)는 adapters.**
   F-2 뒤 새로 갈린 지점 — SHA-256(`java.security.MessageDigest`)이 domain 허용 목록 밖이라
   procurement는 결정 규칙을 가질 수 없다. verifier가 정확히 이 형태를 제안했다(§3 "지목만").
3. **raw_observation.payload는 원문(sourceText)이 있으면 원문, 없으면 등재분 투영으로
   대체한다**(F-7 — 3A·3B·3D 몫 전부 닫힘, 위).
4. **`releaseSha`는 구성 근이 주입하는 문자열**, 실제 출처는 M6 6C 소관(verifier 동의).
5. **`Notice.reconstructNotice`는 `collected()`+`applyEvent()` 이벤트 체인**, 표 밖 상태는
   이제 `Map.getValue`가 예외로 던진다(F-8 뒤 「조용한 Open」 제거, verifier 부분 동의를
   완전 동의로 좁혔다).

## 위협 모델 대응표 — scope.md 「방어한다」(F-1~F-6 뒤 갱신, verifier r2 N-1·N-2·r3 P-1 뒤 재정정)

**경계 문장(verifier r2 N-2·N-7, r3 P-1·P-2·P-3 뒤 명시)** — (a)가 실제로 방어하는 범위는
「금액 축 셋(base/estimated/allocated)의 값·provenance kind·provenance detail·동반 컬럼
(통화·과세, allocated는 통화·과세 자체가 없음)」·「estimated_amount_source_key」·
「floor_rate_fraction과 그 출처 라벨(origin kind·detail)」·「status」다(P-1 뒤 라벨
컬럼까지 전부 포함). **방어하지 않는 것**: `deadline_at`(위조 가능, 알려진 제한) ·
raw_observation을 app 역할이 스스로 새로 만들어 그 key로 값을 바꾸는 경로(N-3, raw
INSERT 권한이 정상 경로에 필요해 경계 밖) · `business_category_code`/`_label`(가드 축이
아니다 — **정정, P-3**: 「최초 기재 뒤 변경 경로 자체가 없다」는 틀렸다. 정상 fold도
`Sql.UPDATE_NOTICE`가 이 두 컬럼을 매번 SET하고, 직접 SQL 위조도 통과한다. 가드 축이
아니라는 결론만 맞다).

**P-2(verifier r3, low, 경계 미선언) — `status`는 이 slice의 쓰기 경로에서 한 번 쓰고
끝이다.** `NoticeCollected.toNoticeRow()`가 `status = NoticeStatus.Open.name`으로
고정하고 `Sql.UPDATE_NOTICE`는 `status`를 SET하지 않는다 — `NoticeRepository`에는
`persist`·`find`뿐이라 상태 전이를 적용할 port가 3D에는 없다. 즉 **canonical status는
3D를 통해 `Open` 말고 다른 값이 될 수 없다**(상태 전이는 M4 소유, 결함 아님). 신설된
`guard_notice_status`의 신선도 분기는 그래서 정상 경로에서 발동할 자리가 없고, 직접
SQL 위조를 막는 방어로만 실효를 갖는다 — 이 사실이 이전 판 어디에도 적혀 있지 않았다.

| 방어 항목 | 메커니즘 | 증거 |
| --- | --- | --- |
| (a) 파생/비권위 write가 권위 자리를 덮음, 값·provenance kind/detail·동반 컬럼(통화·과세)·estimated source key·floor_rate origin·status 변경 | `guard_authoritative_slot()`(존재+점유+신선도, 값·provenance·동반 컬럼 중 하나라도 바뀌면 적용, P-1 뒤 라벨 컬럼 포함) + `guard_existence_and_freshness()`(status·floor_rate origin, P-1 뒤 대칭 확장) | `PrecedenceMutationTest` 11건, `PrecedenceLabelColumnTest` 5건, `CleanMigrationTriggerTest`(가드 인자 완전성) |
| (b) raw·audit의 갱신·삭제·위조 INSERT | `reject_mutation()` 트리거 + GRANT 미부여 + SECURITY DEFINER(audit INSERT는 트리거만) | `RawAppendOnlyTest` 8건 |
| (c) 재수집 중복 효과, 32비트 해시 충돌에 의한 유실 | `observation_key`(SHA-256) UNIQUE + `ON CONFLICT DO NOTHING` | `NoticeVersioningTest`, `F2CollisionRegressionTest` |
| (d) 배치 통째 소실·반쯤 commit | 항목 하나 = 트랜잭션 하나 | `ItemAtomicityTest` |
| (e) 엔진 대체의 조용한 no-op | JSONB·PL/pgSQL, dialect 분기 없음 | 코드 검사, PostgreSQL 16.4 컨테이너로만 실행 |
| (f) ORM 스키마 표류 | migration만 스키마를 만든다 | `CleanMigrationTest`(여덟 축) |
| (g) 차수의 정수 컬럼 | `notice_round TEXT CHECK` | V1 CHECK, `CleanMigrationTest` 축8 |
| (h) opening_result·qualification_text 직접 SQL 위조 | `guard_existence_and_freshness()`(부분 관측은 COALESCE로 정상 통과, N-4) | `OpeningQualificationRepositoryTest` 6건 |

## 우회 후보 실측(scope 7 + 추가, F-1·F-4·F-5·F-6·N-1·N-2 뒤 재확인)

| # | 우회 시도 | 방어 | 실측 |
| --- | --- | --- | --- |
| 1 | 백필 스크립트가 직접 `UPDATE notice SET base_amount_won=…`(같은 observation_key) | 신선도 가드(값이 실제로 바뀌었는데 observation_key 미변경 → 거부) | `PrecedenceMutationTest`(F-1 재현) |
| **1'(N-1, 회귀 뒤 재확인)** | **값은 그대로 두고 `base_amount_provenance`만 강등**(같은 observation_key) | 점유 가드 — 축 판정을 「값 또는 provenance 변경」으로 넓혀 재차단 | `PrecedenceMutationTest`(N-1 재현) |
| 2 | provenance를 `PUBLISHED`로 위조 | 도메인 조립 단계(`ResolvedBaseAmount.Direct`가 `Provenance.Published` 타입만 받음)에서 경계 밖 | 코드 검사 |
| 3 | raw를 건너뛰고 canonical만 write | FK NOT NULL | `ItemAtomicityTest` |
| 4 | `revision`을 되감아 옛 값을 덮음 | `notice_revision_bump()`가 입력을 무시 | `NoticeVersioningTest`, probe(commands.md) |
| 5 | 배치를 한 트랜잭션으로 묶어 성능 개선 | API에 그 경로 없음 | `ItemAtomicityTest` |
| 6 | H2로 test를 돌려 초록 | dialect 분기 없음, Testcontainers PostgreSQL만 | 코드 검사 |
| 7 | `ddl-auto=update`로 스키마 보정 | ORM 미채택 | 코드 검사 |
| 8 | 애플리케이션 역할로 `provenance_authority` UPDATE | `GRANT SELECT`만 | `CleanMigrationTest`, probe |
| 9 | TRUNCATE/COPY로 트리거 우회 | TRUNCATE 권한 미부여 | `RawAppendOnlyTest`, probe |
| 10 | audit를 지워 이력 소거 | append-only 트리거 + 권한 미부여 | `RawAppendOnlyTest` |
| 추가(F-4) | 비권위→비권위 직접 SQL | 「이미 값 있으면 유입이 권위 있어야」 | `PrecedenceParityTest`(단일 + 12쌍 parity) |
| 추가(F-5) | opening_result/qualification_text 직접 SQL 위조 | `guard_existence_and_freshness()` | `OpeningQualificationRepositoryTest` |
| 추가(F-6) | notice_audit 위조 INSERT | INSERT 권한 제거 + SECURITY DEFINER | `RawAppendOnlyTest` |
| 추가 | `SET session_replication_role = 'replica'`(트리거 일괄 무력화) | 권한 없음(계약 밖 우회였으나 이미 닫혀 있음, verifier 실측) | probe(commands.md) |
| **추가(N-2, 신설 방어)** | 값·provenance 동일 · `base_amount_vat`만 변경(같은 key) | 축 판정에 동반 컬럼 포함 + 신선도 가드가 축 전체를 본다 | `PrecedenceMutationTest`(N-2 vat 재현) |
| **추가(N-2, 신설 방어)** | `status`만 직접 위조(같은 key) | `guard_existence_and_freshness('status')` | `PrecedenceMutationTest`(N-2 status 재현) |
| **추가(N-3, 알려진 제한 — 열려 있음)** | app 역할이 새 raw 행을 스스로 만들고 그 key로 값 변경 | **미방어** — raw INSERT 권한이 정상 수집 경로에 필요해 완전 차단은 경계 밖(설계 트레이드오프) | probe(commands.md), checklist 「알려진 제한」 |
| **추가(알려진 제한 — 열려 있음)** | `deadline_at`만 직접 위조(같은 key) | **미방어** — M-a(N-2 대응)는 통화·과세·status만 다뤘다 | probe(commands.md), checklist 「알려진 제한」 |
| **추가(N-4, 신설 방어)** | winningRate만 실은 더 늦은 부분 관측 → derivedBaseAmount가 NULL로 지워져 항목 실패 | `UPSERT_OPENING_RESULT`의 `COALESCE(EXCLUDED.col, opening_result.col)` — NULL 유입은 기존 값 유지, 항목 성공 | `OpeningQualificationRepositoryTest`(M-c 재현) |
| **추가(N-5, 커버리지 신설)** | `NoticeStatus`에 새 값이 추가되고 상태-경로 표에서 분기가 빠짐 | `eventPathFor`가 exhaustive `when` — 분기 누락은 런타임이 아니라 컴파일 실패 | `NoticeReconstructionTest`(양성 경로), 컴파일 자체(구조적 방지) |

## 알려진 제한

- **F-7은 3A·3B·3D 몫 전부 닫혔다**(위). koneps 밖 호출부·구 fixture처럼 `sourceText`가
  없는 관측은 여전히 payload가 등재분 투영과 같아지고 바이트 동일을 보장하지 않는다 —
  이는 "원문이 없으면 원문을 낼 수 없다"는 구조적 한계로 남는다(설계상 수용, 위 F-7 절).
- **Docker 부재 시 실제 실패 재현을 완결하지 못했다** — commands.md 참고. 코드 구조로
  「스킵이 아니라 실패」를 보장하나, 이 공유 개발 머신에서 라이브 재현은 안 됐다(verifier
  독립 레인도 같은 결론).
- **`OPEN-DIC-09`(fold 순서 술어)는 여전히 열려 있다** — `observed_at` 잠정 ⓐ, conflict
  구분 없음(마지막 유효 write 적용).
- **`OPEN-DIC-06`은 계약대로 구현** — Undeclared는 빈 자리만 채운다(`AllocatedBudget` 축).
- **`bidvector_app`은 NOLOGIN** — 실제 운영 배선(`SET ROLE`을 언제 거는지)은 M6 6C 소관,
  3D는 test에서만 그 전환을 실증한다.
- **N-3(verifier r2, medium) — 미방어, 열려 있음**: app 역할이 `raw_observation`에 새 행을
  스스로 만들고(정상 수집 경로에 필요한 권한) 그 key로 금액 축을 바꾸면 신선도 가드를
  지난다. 방어의 실제 명제는 「값 변경 거부」가 아니라 「값 변경은 raw 근거를 동반해야
  한다」다 — 원문(raw payload)의 정직성 자체를 검증하는 것은 3B가 3D 경계 밖으로 미룬
  자리라(위협 모델), 이 slice 안에서 완전 차단은 만들지 않는다.
- **`deadline_at` 위조 — 미방어, 열려 있음**: N-2 대응(M-a)은 통화·과세·status만 다뤘다.
  `deadline_at`은 provenance 축도 companion 컬럼 취급도 받지 않는다 — 직접 SQL로 같은
  key인 채 위조 가능하다(probe 실측, commands.md).
- **N-6(verifier r2, low)** — `KonepsOpenApiNoticeSourceTest`와
  `RawObservationSourceTextRoundTripTest`의 원문 리터럴은 같은 문자열을 두 파일에 각각
  손으로 적은 것이지 진짜 공유 상수가 아니다(위 F-7 절 정정). 한쪽만 바뀌면 조율 없이
  조용히 끊길 수 있다 — 두 test가 서로 다른 모듈 경계(koneps 어댑터 vs persistence 저장
  계층)를 검증하므로 상수 하나로 억지로 묶지 않고 이 사실만 등재한다.

## 재작업 누계: 3회(수정 라운드 1 F-1~F-8, 수정 라운드 2 N-1·N-2·N-4·N-5, 수정 라운드 3 P-1), 상한 5(v2-slice-pipeline). 운영자 결정: P-1 수정 + r4 표적 재검증 뒤 종결.
