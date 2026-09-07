# M3/3D — checklist.md

base `01ecbba69e21e4b85ee8303416fe06a77b919fd6` · head `782c4ae`(수정 라운드 1, F-7 3A·3B·3D
몫 전부 이어붙임 — F-1~F-6·F-8은 `a79a6d6` 시점과 변경 없음). 정본 순서: `scope.md` →
`_workspace/m3-3d/01_design-review.md`(Phase 2.5) → `_workspace/m3-3d/02_verifier_report.md`
(verifier r1) → 이 문서.

## 완료 조건 대응표 — scope.md 「이 slice 가 하는 일」

| # | 요구 | 구현 | test |
| --- | --- | --- | --- |
| ① | Flyway가 스키마 단일 출처, 4영역 | `V1__schema.sql`(raw_observation·notice/opening_result/qualification_text·notice_audit/rejected_write·collection_run) | `CleanMigrationTest`(D-3D-6 여덟 축 전부 대조) |
| ② | raw/canonical 분리, 원본 보존 | `JdbcRawObservationStore.append`가 canonical fold보다 먼저(별도 트랜잭션) raw 행을 만든다 | `RawAppendOnlyTest` 8건 |
| ③ | 멱등 upsert/versioning | `ObservationKey`(값 타입, procurement) + `ObservationKeyDerivation`(SHA-256 유도, adapters) + `observation_key` PK(`ON CONFLICT DO NOTHING`) + notice `revision` 트리거 증가 | `NoticeVersioningTest`, `F2CollisionRegressionTest` |
| ④ | 점유 가드 = DB constraint + write 규칙 | `guard_authoritative_slot()` 트리거(V2, 값 변경 시에만 검사 — 존재·점유·신선도 셋) + Kotlin `mergeNoticeRow`(`mayOverwrite`와 동일 술어) | `PrecedenceMutationTest` 8건(Kotlin/DB parity 포함) |
| ⑤ | 항목 단위 원자성·배치 회계 분리 | `JdbcNoticeRepository.persist`가 connection 하나 = 트랜잭션 하나. `JdbcCollectionRunStore.record`는 완전히 별도 호출/트랜잭션 | `ItemAtomicityTest` 3건 |
| ⑥ | 감사 보존 | 원본=raw_observation, 판정=canonical `*_provenance`, 오류=`collection_run.drop_reasons`+`rejected_write`. `notice_audit`는 SECURITY DEFINER 트리거로만 생긴다(app 역할 INSERT 권한 없음) | `NoticeVersioningTest`(audit 행), `RawAppendOnlyTest`(위조 INSERT 거부 + 정상 경로 성공) |
| ⑦ | PostgreSQL 전용, 조용한 no-op 금지 | JSONB·PL/pgSQL 트리거·`to_jsonb` | S-0~S-5 전건 실제 PostgreSQL 16.4 컨테이너 위에서 실행 |
| ⑧ | clean 재현 | `CleanMigrationTest` — 여덟 축을 코드 안 기대 목록과 대조(D-3D-6) | 위와 동일, 변이 셋으로 실측(commands.md) |

## finding 대응표 — verifier r1(수정 라운드 1)

| finding | 요지 | 대응 | 재검증 |
| --- | --- | --- | --- |
| F-1(high) | 값만 바뀌고 provenance는 그대로인 write가 통과 | 트리거가 「값이 실제로 바뀔 때만」 검사하고, observation_key가 그대로면 거부(신선도 가드) | `PrecedenceMutationTest`, 직접 SQL(commands.md) |
| F-2(high) | `ObservationKey` 32비트 hashCode 충돌("Aa"/"BB")로 관측 유실 | 유도를 어댑터로 옮기고 SHA-256으로 교체(`ObservationKeyDerivation`) | `F2CollisionRegressionTest` |
| F-3(high) | S-5가 D-3D-6 여덟 축 중 둘(테이블·트리거)만 대조 | `CleanMigrationTest`를 여덟 축 전부 대조로 재작성 | `CleanMigrationTest` 13건, 변이 셋 재현으로 FAIL 확인(commands.md) |
| F-4(medium) | 비권위→비권위 write가 통과(Kotlin·DB 술어 불일치) | 트리거를 「이미 값 있으면 유입이 권위 있어야」로 통일(기존 권위는 안 봄, mayOverwrite와 동일) | `PrecedenceMutationTest`(단일 재현 + 12쌍 parity test) |
| F-5(medium) | opening_result·qualification_text에 DB 가드 없음 | `guard_existence_and_freshness()`를 세 컬럼에 추가(존재+신선도, 점유 없음 — provenance 축 자체가 없어서) | `OpeningQualificationRepositoryTest` 3건 |
| F-6(medium) | app 역할이 notice_audit에 위조 INSERT 가능 | INSERT 권한 제거 + 트리거 함수 SECURITY DEFINER | `RawAppendOnlyTest` 2건 |
| F-7(medium) | raw payload가 미등재 필드 값을 버림(원문 전체 아님) | **닫힘 — 3A·3B·3D 몫 전부 완료** | `RawObservationSourceTextRoundTripTest`, `KonepsOpenApiNoticeSourceTest`(F-7 3B 몫) |
| F-8(medium) | 재구성 폴백이 조용히 Open을 냄, find() 왕복 test 부재 | `EVENT_PATH_TO_STATUS.getValue`(표에 없으면 예외)로 교체 | `NoticeFindRoundTripTest` |

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
항목을 mock server로 서빙하고 `sourceText`가 그 바이트와 정확히 같음을 잰다. 이 test가
쓰는 원문 리터럴을 `RawObservationSourceTextRoundTripTest`(3D)도 그대로 써서 「서빙 →
관측 → 저장」 전 구간의 바이트 동일성을 하나의 값으로 잇는다.

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

## 위협 모델 대응표 — scope.md 「방어한다」(F-1~F-6 뒤 갱신)

| 방어 항목 | 메커니즘 | 증거 |
| --- | --- | --- |
| (a) 파생/비권위 write가 권위 자리를 덮음, 값만 바뀌는 write | `guard_authoritative_slot()`(존재+점유+신선도 셋, 값 변경 시에만) | `PrecedenceMutationTest` 8건 |
| (b) raw·audit의 갱신·삭제·위조 INSERT | `reject_mutation()` 트리거 + GRANT 미부여 + SECURITY DEFINER(audit INSERT는 트리거만) | `RawAppendOnlyTest` 8건 |
| (c) 재수집 중복 효과, 32비트 해시 충돌에 의한 유실 | `observation_key`(SHA-256) UNIQUE + `ON CONFLICT DO NOTHING` | `NoticeVersioningTest`, `F2CollisionRegressionTest` |
| (d) 배치 통째 소실·반쯤 commit | 항목 하나 = 트랜잭션 하나 | `ItemAtomicityTest` |
| (e) 엔진 대체의 조용한 no-op | JSONB·PL/pgSQL, dialect 분기 없음 | 코드 검사, PostgreSQL 16.4 컨테이너로만 실행 |
| (f) ORM 스키마 표류 | migration만 스키마를 만든다 | `CleanMigrationTest`(여덟 축) |
| (g) 차수의 정수 컬럼 | `notice_round TEXT CHECK` | V1 CHECK, `CleanMigrationTest` 축8 |
| (h) opening_result·qualification_text 직접 SQL 위조 | `guard_existence_and_freshness()` | `OpeningQualificationRepositoryTest` 3건 |

## 우회 후보 실측(scope 7 + 추가 3, F-1·F-4·F-5·F-6 뒤 재확인)

| # | 우회 시도 | 방어 | 실측 |
| --- | --- | --- | --- |
| 1 | 백필 스크립트가 직접 `UPDATE notice SET base_amount_won=…`(같은 observation_key) | 신선도 가드(observation_key 미변경 → 거부) — CHECK는 provenance 자체를 지울 때만 별도로 걸린다 | `PrecedenceMutationTest`(F-1 재현 + CHECK test) |
| 2 | provenance를 `PUBLISHED`로 위조 | 도메인 조립 단계(`ResolvedBaseAmount.Direct`가 `Provenance.Published` 타입만 받음)에서 경계 밖 | 코드 검사 |
| 3 | raw를 건너뛰고 canonical만 write | FK NOT NULL | `ItemAtomicityTest` |
| 4 | `revision`을 되감아 옛 값을 덮음 | `notice_revision_bump()`가 입력을 무시 | `NoticeVersioningTest`, probe(commands.md) |
| 5 | 배치를 한 트랜잭션으로 묶어 성능 개선 | API에 그 경로 없음 | `ItemAtomicityTest` |
| 6 | H2로 test를 돌려 초록 | dialect 분기 없음, Testcontainers PostgreSQL만 | 코드 검사 |
| 7 | `ddl-auto=update`로 스키마 보정 | ORM 미채택 | 코드 검사 |
| 8 | 애플리케이션 역할로 `provenance_authority` UPDATE | `GRANT SELECT`만 | `CleanMigrationTest`, probe |
| 9 | TRUNCATE/COPY로 트리거 우회 | TRUNCATE 권한 미부여 | `RawAppendOnlyTest`, probe |
| 10 | audit를 지워 이력 소거 | append-only 트리거 + 권한 미부여 | `RawAppendOnlyTest` |
| 추가(F-4) | 비권위→비권위 직접 SQL | 「이미 값 있으면 유입이 권위 있어야」 | `PrecedenceMutationTest`(단일 + 12쌍 parity) |
| 추가(F-5) | opening_result/qualification_text 직접 SQL 위조 | `guard_existence_and_freshness()` | `OpeningQualificationRepositoryTest` |
| 추가(F-6) | notice_audit 위조 INSERT | INSERT 권한 제거 + SECURITY DEFINER | `RawAppendOnlyTest` |
| 추가 | `SET session_replication_role = 'replica'`(트리거 일괄 무력화) | 권한 없음(계약 밖 우회였으나 이미 닫혀 있음, verifier 실측) | probe(commands.md) |

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

## 재작업 누계: 1회(수정 라운드 1, F-1~F-8), 상한 5(v2-slice-pipeline).
