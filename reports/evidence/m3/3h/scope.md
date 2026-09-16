# Slice 계약 — M3 / 3H-1 · 발주기관 fact 수집·저장·도메인(`OPEN-2B-AGENCY-ID` 1/2)

> **지위**: 운영자 결정 2026-09-16 「추천대로」 — ① P-14 등재 + 엔진 키 = 수요기관코드 ② V7 마이그레이션 + migration-reviewer + Codex 1 라운드(범위·비용 승인) ③ 채움률 read-only 실호출 1회 승인(3H-2 착수 전 실행) ④ 3H-1/3H-2 분할. D-3H-1·2·4·6·8 의 「운영자 결정 대기」는 전부 **결정**으로 읽는다. 세션 모델 단독 작성. Phase 2.5 설계 검토는 아래 「위협 모델 경계·우회·(2b)」 절.
> 레인: worktree `bid-vector-v2-m4e`, 브랜치 `m3-3h/2026-09-16`, base = `origin/main`(PR #29 병합 뒤). M3 후속은 `main` 병행(3G 선례).
> 구현 `kotlin-implementer`(sonnet) → `verifier`(opus) → 리뷰 레인(`code-reviewer` + **`migration-reviewer`**) → **Codex**(마이그레이션 — 되돌리기 어려운 경로, 운영자 범위·비용 승인 뒤).
> 다른 레인(M5 Python)과 소스 겹침 0 — 단, 3H-2 가 M5 레인에 `OPEN-3H-SAMPLE-MISSING-REASON` 을 건넨다.

## 착수 조사 실측(2026-09-16 — `_workspace/m3-agency/01_scout_v2.md`·`02_scout_legacy.md`)
| 사실 | 위치 | 귀결 |
| --- | --- | --- |
| 발주기관 축이 V2 에 **없다** — 필드 계약 레지스트리 24 키·`FieldConcept`·`notice` 스키마·`Notice`·요청/표본 조립 어디에도 기관 키 0. 3A policy-values §1.1~§1.11 에 기관 행 0(§1.8 셈이 legacy 60 키를 덮는다고 적지만 legacy 가 소비한 기관 키 둘이 표에 없다) | `procurement/CollectionPolicy.kt`·`FieldContract.kt` · `adapters/db/migration/V1__schema.sql` · `reports/evidence/m3/3a/policy-values.md` | 수집→저장→도메인 세 층을 이 slice 가, 조립 층은 3H-2 가 |
| 조달청 참고자료(입찰공고정보서비스 1.2, `authoritative`)의 응답 항목: `ntceInsttCd` 공고기관코드(크기 7, **옵션**) · `ntceInsttNm` 공고기관명(400, **필수**) · `dminsttCd` 수요기관코드(7, **옵션**) · `dminsttNm` 수요기관명(400, 옵션). 코드는 「행자부코드가 있으면 행자부코드, 없으면 조달청 부여 코드」. 「공고기관과 수요기관이 동일할 수 있음」 | `bid-vector/docs/조달청_OpenAPI참고자료_나라장터_입찰공고정보서비스_1.2.docx`(read-only) 응답 항목 표 | 등재 근거는 문서 문면(P-1 격) — D-3H-1. 코드 **채움률은 문서로 알 수 없다** — D-3H-8 |
| 같은 응답에 담당자 이름·전화·이메일(`ntceInsttOfclNm`·`…TelNo`·`…EmailAdrs`, `dminsttOfcl…`)이 있다 | 같은 문서 | **등재하지 않는다**(개인정보) — D-3H-7 |
| legacy 는 코드를 한 번도 수집하지 않았고 이름 문자열(`dminsttNm`·`ntceInsttNm`)만 `String(255)` 두 컬럼에 저장, 역할 우선순위가 경로마다 달랐고(ML 정본은 개찰수요>수요>공고, 홀드아웃은 공고 우선 — legacy 자신이 **누수**로 명시), 같은 컬럼에 정규화기 셋(공백 제거 여부·법인격 토큰 제거 여부가 갈림), 기관 마스터 없음, 계층은 substring 근사 | `_workspace/m3-agency/02_scout_legacy.md` | 역할 혼용·정규화기 복수·별칭 표는 **비채택**(D-3H-2·3) |
| data-dictionary §6.3.1: 피처 키 동일성은 strip+소문자화 하나, 별칭·계층 사전 없음, 기관 키의 Kotlin 정본은 「기관 fact 가 생길 때 `CategoryCode.of` 와 같은 함수」 | `docs/discovery/data-dictionary.md` §6.3.1 | 정규화 결정은 이미 승인됨 — 재결정 없이 적용 |
| wire `MissingReason` 은 `UNSPECIFIED`·`UNKNOWN`·`NOT_APPLICABLE`·`NOT_COLLECTED_YET` 넷. 엔진 표본 축 허용 결측 사유는 `{NOT_COLLECTED_YET}` 뿐(5D-3), 요청 축은 열린 집합. 오늘 요청 `agencyId = null → UNKNOWN`, 표본 `null → NOT_COLLECTED_YET` | `contracts/proto/bidvector/ml/v1/common.proto` · `adapters/ml/RequestMapping.kt` · 5D-3 scope | 수집 뒤 「원천에 없음」 표본은 `NOT_COLLECTED_YET` 이 거짓이 된다 — 3H-2 가 M5 레인에 `OPEN-3H-SAMPLE-MISSING-REASON` 을 건넨다(D-3H-6) |
| 원문은 `raw_observation.payload`(TEXT) 에 전체 보존, `payload_fields` 투영은 등재 키만 | `procurement/RawObservationStore.kt` · 3A | 정규화 전 저장 행의 백필은 원문에서 가능 — 운영 데이터 0 이라 `OPEN-3H-AGENCY-BACKFILL`(D-3H-5) |
| 마이그레이션 선례: V5 가 `ALTER TABLE … ADD COLUMN`(nullable) 로 열을 더했고, 점유 가드 트리거는 컬럼별·append-only 는 `raw_observation`·`notice_audit` 전용. `CleanMigrationColumnTest` 가 컬럼 행렬을 잠근다. D-4B7-6 이 이 축의 마이그레이션을 migration-reviewer + Codex 대상으로 분류 | `adapters/db/migration/V5__opening_complete_axis.sql` · `adapters/.../CleanMigrationColumnTest.kt` · 4B-7 scope | V7 은 nullable 컬럼 넷 추가만(D-3H-4) |
| 4B-7 표본 조회는 키만 SELECT 하고 `NoticeRepository` 로 복원(N+1) | `adapters/ml/JdbcCompetitionSampleSource.kt` | 3H-2 에서 SQL 무변경 — 복원된 `Notice` 의 기관 fact 가 표본 축으로 흐른다 |
| 도메인 관례: `CategoryCode` private 생성자 + `of(raw)`(trim + `Char.lowercaseChar`, Locale 게이트) + `@ConsistentCopyVisibility` | `procurement/BusinessCategory.kt`(4B-8) | `AgencyCode.of` 가 같은 함수를 재사용(D-3H-3) |

```yaml
milestone: M3
slice: 3h1-agency-fact-collection-persistence
base_sha: 0ad8e597ff08e4fb4e23d422659fb33a116b5ab1
head_sha: ec03ddb1d5b31ebc36d2ed6fcc7ad1bb71351121
in_scope:
  - procurement/src/main/kotlin/bidvector/procurement/FieldContract.kt                 # FieldConcept 토큰 넷(DEMAND_AGENCY_CODE·DEMAND_AGENCY_NAME·NOTICE_AGENCY_CODE·NOTICE_AGENCY_NAME)
  - procurement/src/main/kotlin/bidvector/procurement/CollectionPolicy.kt              # 레지스트리 초기값 행 넷(문서 문면 — 크기·nullability)
  - procurement/src/main/kotlin/bidvector/procurement/Agency.kt                        # 신설 — AgencyCode.of·AgencyName·Agency(D-3H-3)
  - procurement/src/main/kotlin/bidvector/procurement/BusinessCategory.kt              # normalizeCategoryKey 를 공용 이름으로(내용 불변) — 필요 시
  - procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt                   # Notice.demandAgency·noticeAgency(둘 다 nullable)
  - procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt                  # businessCategoryFrom 관례로 기관 둘 조립
  - procurement/src/test/kotlin/bidvector/procurement/**                               # FieldContractTest·CanonicalizeTest·AgencyTest(신설)
  - adapters/src/main/resources/db/migration/V7__notice_agency.sql                     # 신설 — ALTER TABLE notice ADD COLUMN ×4(nullable TEXT)
  - adapters/src/main/kotlin/bidvector/adapters/persistence/{NoticeRow,Sql,NoticeReconstruction,JdbcNoticeRepository,NoticeRowMerge}.kt   # 갱신 3 — 컬럼 넷 bind·존재 가드 병합은 같은 층의 구조적 필수 부분
  - procurement/src/main/kotlin/bidvector/procurement/{KonepsAgencyFieldContracts,KonepsPresentInSets}.kt   # 갱신 3 — sizeGate(500줄)·detekt TooManyFunctions 가 CollectionPolicy.kt·Canonicalize.kt 에 걸려 내용 이동만으로 분리(KonepsOpeningCompleteFieldContracts.kt 선례)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/**                         # CleanMigrationColumnTest 행 넷·JDBC 왕복 test
  - config/quality/gate-tests.properties                                               # 신설 test 등재만
  - reports/evidence/m3/3a/policy-values.md                                            # §1.3 기관 행 넷 + P-14 행(팀장)
  - docs/discovery/data-dictionary.md                                                  # 기관 fact 정의 + §6.3.1 「기관 정본」 갱신(팀장)
  - docs/discovery/capability-map.md                                                   # OPEN 표(OPEN-2B-AGENCY-ID 상태·신설 OPEN)(팀장)
  - milestone-3.md                                                                     # 3H 절 착수·종결(팀장)
  - reports/evidence/m3/3h/**
out_of_scope:
  - workflow/**                                                                         # 요청·표본 축 채움은 3H-2(별도 계약) — 3H-1 뒤에도 agencyId 는 null
  - adapters/src/main/kotlin/bidvector/adapters/ml/**                                   # RequestMapping·표본 조회 SQL 무변경
  - contracts/** · ml-engine/**                                                         # wire·엔진 무변경(표본 축 결측 사유는 M5 레인 OPEN)
  - 담당자 키(ntceInsttOfcl*·dminsttOfcl*) 등재 · 기관 마스터/계층/별칭 표 · 이름 정규화기 추가 · 백필 실행
  - fixtures/** · app/src/test/kotlin/bidvector/app/conformance/**                      # corpus 신설 0(4E D-4E-6 선례) — 필요가 verifier 로 드러나면 OPEN-3H-FIXTURE
acceptance_commands:
  - ./gradlew --no-build-cache --no-daemon clean check
rollback: in_scope 경로 한정 restore(rollback.md). V7 은 파일 삭제로 되돌림(적용된 DB 는 `ALTER TABLE notice DROP COLUMN` ×4 — 데이터 손실 없음, 컬럼이 비어 있음). 공유 파일(policy-values·data-dictionary·capability-map·milestone-3·gate-tests)은 hunk 격리
```

## 결정
| ID | 결정 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-3H-1** | 기관 키 넷(`ntceInsttCd`·`ntceInsttNm`·`dminsttCd`·`dminsttNm`)을 3A 필드 계약에 `authoritative` 로 등재 — 크기 7/400/7/400, 필수는 `ntceInsttNm` 만. **P-14**(운영자 승인 항목, P-1 격) | 참고자료 응답 항목 표 문면 | **운영자 결정 대기** |
| **D-3H-2** | 엔진 키 `agency_id` = **수요기관코드**(`dminsttCd`) 정규화값. 역할 간 폴백 없음(공고기관 값으로 대체 금지 — legacy 누수 재현 금지), 이름 키 불채택(이름은 표기 흔들림이 크고 §6.3.1 이 별칭 표를 금지 — 코드가 그 자리를 채운다), 별칭·계층 사전 없음. 코드 결측 = 기관 fact 결측(정직). 이름 둘은 **표시·감사용**으로 저장 | data-dictionary §6.3.1 · legacy 실측(정규화기 셋·역할 혼용의 누수) · 문서 「코드 = 행자부/조달청 부여 식별자」 | **운영자 결정 대기**(대안 (b) 이름 키 — 채움률은 높으나 표기 흔들림을 별칭 없이 감수) |
| **D-3H-3** | `procurement` 에 `AgencyCode`(private 생성자, `of(raw)` = `CategoryCode.of` 와 **같은 정규화 함수**), `AgencyName`(원문 trim, 정규화 없음, 표시용), `Agency(code: AgencyCode?, name: AgencyName?)`(`init`: 둘 다 null 금지). `Notice.demandAgency: Agency?`·`Notice.noticeAgency: Agency?` — 역할별 자기 필드, 접지 않는다 | 4B-8 관례 · §6.3.1 「같은 함수」 | 계약 |
| **D-3H-4** | V7: `ALTER TABLE notice ADD COLUMN demand_agency_code TEXT, demand_agency_name TEXT, notice_agency_code TEXT, notice_agency_name TEXT`(nullable, 제약·인덱스 없음 — 인덱스는 3H-2/M6 `OPEN-4B7-QUERY-INDEX` 와 함께). `NoticeRow`·`Sql`·`NoticeReconstruction`·`CleanMigrationColumnTest` 행 넷. **migration-reviewer + Codex**(운영자 범위·비용 승인) | V5 선례 · D-4B7-6 분류 | **운영자 결정 대기**(Codex 비용) |
| **D-3H-5** | 백필 없음 — 운영 데이터 0. 정규화 전/기관 전 저장 행은 `raw_observation.payload` 원문에서 채울 수 있음을 등재 → `OPEN-3H-AGENCY-BACKFILL`(`OPEN-4B8-CATEGORY-BACKFILL` 과 같은 성격) | 운영 데이터 0 | 계약 |
| **D-3H-6** | 범위 분할: **3H-1**(이 계약) = 수집·저장·도메인·복원. **3H-2**(별도 계약) = `predictionRequestFor`·`SampleConversion` 의 `agencyId` 채움(수요기관코드), 요청 축 결측 `UNKNOWN` 유지, 표본 축 「수집했으나 원천에 없음」 사유 허용은 M5 레인 `OPEN-3H-SAMPLE-MISSING-REASON`(엔진 `{NOT_COLLECTED_YET}` 집합 확장 — 5D-3 계약 갱신), 엔진 교차 실측(`segment_support = DIRECT` 첫 도달) | 마이그레이션 slice 와 조립 slice 를 가르면 Codex 라운드가 3H-1 에만 걸리고 3H-2 는 verifier 만 | **운영자 결정 대기** |
| **D-3H-7** | 담당자 개인정보 키(`ntceInsttOfclNm`·`…TelNo`·`…EmailAdrs`·`dminsttOfcl…`)는 등재하지 않는다 — `FieldConcept` 에 토큰 없음이 구조적 증거. 기관명·코드는 기관 식별 정보(개인정보 아님) — PR 에서 privacy-gate 로 그 판정을 남긴다(새 컬럼 넷) | 원문 전체 보존은 3A 의 기존 결정(이 slice 가 바꾸지 않는다) | 계약 |
| **D-3H-8** | `dminsttCd` 채움률은 문서(옵션)로 알 수 없고 fixture corpus 에도 기관 필드가 없다 — 운영자 승인 하 **read-only 실호출 1회**(3A §1.9.7 선례)로 목록 응답 n 건의 코드 채움률을 재어 3H-2 착수 전 D-3H-2 를 재확인. 3H-1 은 이 실측 없이 진행 가능(등재·저장은 채움률과 무관) | 「측정 전 잠정」 규율(P-5 선례) | **운영자 결정 대기**(실호출 승인) |

## 위협 모델 경계·우회·(2b)(Phase 2.5, 세션 모델)
**방어하는 것**: 등재 없는 키 소비(§5.3 — 계약 없는 키는 소비 함수에 못 들어간다), 정규화 안 된 기관 코드의 표현, 역할 혼용(공고기관 값이 수요기관 자리에), 개인정보 키의 조용한 등재, 기존 행·제약을 건드리는 마이그레이션.
**방어하지 않는 것**: KONEPS 가 코드를 실제로 채우는 비율(D-3H-8 실측 대상) · 이름 표기 흔들림(키가 아니므로 무해) · 원문 payload 에 담당자 정보가 남는 것(3A 기존 결정, 보존·파기 축은 M6).

| # | 우회 | 닫힘 |
| --- | --- | --- |
| (1) | `Canonicalize` 가 레지스트리를 거치지 않고 원문 키를 직접 읽는다 | `businessCategoryFrom` 관례(`registry.contractsFor(concept)` 경유만) · `CanonicalizeTest`: 등재를 지우면 fact 가 null |
| (2) | 정규화 안 된 코드로 `AgencyCode` 를 짓는다 | private 생성자 + `of` 유일 경로(컴파일 거부, 4B-8 변이 실측 승계) · `CategoryCode` 와 교차 대조 test(같은 입력 → 같은 문자열) |
| (3) | 공고기관 값을 수요기관 필드에 접는다(폴백) | 두 concept 가 각자 자기 필드로만 — `Canonicalize` 에 폴백 코드 0 · test: 수요기관 키 결측 + 공고기관 키 존재 → `demandAgency == null` |
| (4) | 담당자 키를 함께 등재한다 | `FieldConcept` 토큰 넷 고정 · `FieldContractTest` 가 레지스트리 rawName 집합에 `Ofcl` 계열 0 을 단언 |
| (5) | V7 이 기존 컬럼·제약·트리거를 건드린다 | `ADD COLUMN` nullable 넷만 · `CleanMigration{Column,Trigger,Check}Test` 행렬(기존 행 불변 + 신규 행 넷) · migration-reviewer |
| (6) | 3H-1 에서 이름을 몰래 요청 축 키로 쓴다 | `workflow/**` out_of_scope — diff 로 확인 · `agencyId = null` 두 자리 불변 |
| (7) | 이름을 정규화해 저장한다(표시용 원문 손실) | `AgencyName` 은 trim 만 · JDBC 왕복 test 가 원문 보존 단언 |
| (8) | 빈 문자열 코드/이름이 fact 로 선다 | `AgencyCode.of("")`·`AgencyName("")` 거부(sealed 결과 또는 null 반환 — `Money` 경계 관례) · `Agency.init` 둘 다 null 금지 |

(2b) 값 획득 축:
| 표면 | 밖에 허락하는 것 | 없던 권한인가 | 결과가 쓴 값을 나르는가 | 처분 |
| --- | --- | --- | --- | --- |
| `AgencyCode.of`·`AgencyName`·`Agency` (public 값 타입) | 값 짓기 | 아니오(`CategoryCode.of` 와 같은 격) | 아니오 | — |
| `Notice.demandAgency`·`noticeAgency` 프로퍼티 | 읽기 | 아니오 | 아니오 | — |
| `FieldConcept` 토큰 넷 | 레지스트리 조회 | 아니오(정책 데이터) | 아니오 | — |
| `object` 커널 주입 자리 | 해당 없음 | — | — | — |
| `KONEPS_AGENCY_FIELD_ROWS`(`KonepsAgencyFieldContracts.kt`, `internal val`) — 갱신 3 분리로 생긴 모듈 내 표면 | 같은 모듈에서 계약 행 넷 읽기 | 아니오(불변 `val`, `procurement` 밖으로 안 나간다) | 아니오 | — (verifier r1 F-3 등재) |
| `NOTICE_IDENTIFIER_PRESENT_IN`(`KonepsPresentInSets.kt`) — `private` → `internal` 로 가시성 확대 | 같은 모듈에서 presentIn 집합 읽기 | 아니오(불변 `val`, 내용 이동 — verifier 가 집합·순서 동일 실측) | 아니오 | — (F-3) |
수정 라운드마다 갱신. — r1 수정 라운드(M-1 test·장부층·F-2 test): 새 public 표면 0(test 만).

## 종결 조건
P-14 승인 등재(policy-values §1.3 행 넷 + P 표) · `FieldContractTest`(토큰 넷·담당자 0) · `CanonicalizeTest`(역할별 조립·폴백 없음·정규화) · `AgencyTest`(`of` 정규화 = `CategoryCode.of`, 빈 값 거부) · JDBC 왕복(컬럼 넷 저장·복원, 원문 보존) · `CleanMigration*Test` 행렬 · 우회 (1)~(8) 실측 · 전건 `check` · verifier `ready-for-review` · migration-reviewer 판정 · Codex approve(운영자 승인 범위) · 사용자 승인. `OPEN-2B-AGENCY-ID` 는 3H-2 종결 시 닫힘(3H-1 뒤 상태 「수집·저장 완료, 조립 대기」로 갱신), `OPEN-3H-AGENCY-BACKFILL`·`OPEN-3H-SAMPLE-MISSING-REASON` 등재.

## 하네스 레인 변경
없음(리뷰 요청 시점에 재확인).

## 계약 갱신 이력
| # | 일시 | 갱신 | 사유 |
| --- | --- | --- | --- |
| 1 | 2026-09-16 | 운영자 결정 「추천대로」 — D-3H-1·2·4·6·8 확정(지위 문단) | 운영자 승인 |
| 2 | 2026-09-16 | `AgencyName` 도 `of(raw)` 생성 경로(빈 값은 null) — D-3H-3 문면의 「`AgencyName`(원문 trim)」을 같은 관례로 | 구현 레인이 `AgencyCode.of` 와 대칭으로 둔 것을 흡수(권한 변화 없음) |
| 3 | 2026-09-17 | in_scope 에 `JdbcNoticeRepository.kt`·`NoticeRowMerge.kt`(persistence 층 bind·병합) + `KonepsAgencyFieldContracts.kt`·`KonepsPresentInSets.kt`(sizeGate·detekt 로 내용 이동만 분리) 추가 | 구현 레인 보고 — D-3H-4 를 실제로 컴파일·저장되게 하려면 같은 층의 두 파일이 구조적으로 필요했고, `CollectionPolicy.kt`·`Canonicalize.kt` 가 sizeGate 500줄·TooManyFunctions 11 에 걸렸다. 계약 in_scope 가 파일을 손으로 열거해 생긴 누락(4B-7 F-8 과 같은 갈래) — verifier 표적: 분리 파일은 **내용 이동만**인지(`git diff -M` 유사도) |
