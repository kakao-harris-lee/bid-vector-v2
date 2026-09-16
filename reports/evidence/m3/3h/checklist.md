# checklist.md — M3 / 3H-1 리뷰 요청 조건 점검

- [x] 구현 diff 가 커밋되어 base/head 고정 — `base_sha 0ad8e597ff08e4fb4e23d422659fb33a116b5ab1`,
      `head_sha ec03ddb1d5b31ebc36d2ed6fcc7ad1bb71351121`(4 커밋). `git status --porcelain`
      결과 없음(scope.md·commands.md·checklist.md·rollback.md 커밋 전 상태 제외 — evidence
      경로만 남는다). 양성 대조 1회: `Agency.kt`에 주석 한 줄을 추가해 `git status --porcelain`
      이 `M`으로 잡는 것을 확인 → `git restore --source=HEAD --staged --worktree`로 절삭 복원.
- [x] scope.md `acceptance_commands` 전부 exit 0 — `commands.md`의 최종 전건 실측(HEAD
      `ec03ddb`, 재실행 두 번) GREEN(346 actionable tasks, 46~47s).
- [x] test/lint/type/architecture/contract 관련 명령 통과 — 부분 게이트가 아니라
      `./gradlew --no-build-cache --no-daemon clean check` 전건(워크플로 job `check` 그대로)을
      돌렸다. `procurement`·`adapters` 의 sizeGate·ktlint·detekt·cpdCheck·아키텍처/계약
      게이트 전부 이 안에서 돈다.
- [x] 변경된 fixture 와 정책 version 의 근거 기록 — fixture corpus 신설 0(out_of_scope,
      D-3H-1 종결 조건 그대로). 정책 값 근거는 참고자료 응답 항목 표 문면(scope.md 착수
      조사 표)이고, 이 slice 가 새 policy version 슬롯을 열지 않는다(V7 은 스키마
      마이그레이션이지 `KonepsCollectionPolicyData` 버전이 아니다 — `EffectiveFrom.Initial`
      그대로).
- [x] 알려진 제한과 rollback 또는 비활성화 방법 기록 — 아래 「알려진 제한」·`rollback.md`.
- [x] 비밀값 스캔 통과 — `commands.md` 「비밀값 스캔」 절, exit 1(매치 없음). 담당자
      이름·전화·이메일은 test·evidence 어디에도 없다(지어낸 기관 코드·이름만 사용).

## 종결 조건 대조(scope.md)

| 조건 | 충족 근거 |
| --- | --- |
| P-14 승인 등재(policy-values §1.3 행 넷 + P 표) | 팀장 커밋 `84426a2`(이 slice 착수 전, 별도 레인) |
| `FieldContractTest`(토큰 넷·담당자 0) | `AgencyFieldContractTest`(`FieldContractTest.kt`) 「발주기관 넷이 정확한 개념으로 등재된다」·「담당자 키는 어떤 rawName 에도 없다」 |
| `CanonicalizeTest`(역할별 조립·폴백 없음·정규화) | `AgencyCanonicalizeTest`(`CanonicalizeTest.kt`에서 sizeGate 분리) 4건 |
| `AgencyTest`(`of` 정규화 = `CategoryCode.of`, 빈 값 거부) | `AgencyTest` 10건 |
| JDBC 왕복(컬럼 넷 저장·복원, 원문 보존) | `NoticeFindRoundTripTest` 신설 2건(값 왕복 + 결측 왕복) |
| `CleanMigration*Test` 행렬 | `CleanMigrationColumnTest`에 컬럼 넷 행 추가(`CleanMigrationCheckTest`·`CleanMigrationTriggerTest`는 제약·트리거 신설이 없어 갱신 불필요 — D-3H-4 「제약·인덱스·트리거 없음」과 일치) |
| 우회 (1)~(8) 실측 | 아래 「우회 대조표」 |
| 전건 `check` | `commands.md` 최종 GREEN |
| verifier `ready-for-review` | 검증 레인 소관(별도 레인) |
| migration-reviewer 판정 | 검증 레인 소관(운영자 범위·비용 승인 뒤 별도 레인) |
| Codex approve(운영자 승인 범위) | 운영자 승인 뒤 별도 레인(D-3H-4, migration-reviewer + Codex) |
| 사용자 승인 | 팀장·사용자 소관 |

## 우회 대조표(scope.md 위협 모델, Phase 2.5 대응)

| # | 우회 | 막는 게이트/test | 실측 |
| --- | --- | --- | --- |
| (1) | `Canonicalize`가 레지스트리를 거치지 않고 원문 키를 직접 읽는다 | `agencyFrom`(`Agency.kt`)이 `registry.contractsFor(concept)` 경유만 | `AgencyCanonicalizeTest`: 「등재 없는 레지스트리로는 발주기관 키가 원문에 있어도 fact 가 null 이다」 — `dminsttCd` 계약을 레지스트리에서 지운 뒤 원문에 값이 있어도 `demandAgency.code`가 null |
| (2) | 정규화 안 된 코드로 `AgencyCode`를 짓는다 | private 생성자 + `of` 유일 경로 | 변이 실측(`commands.md`): `AgencyCode(value = "...")` 직접 호출이 `Cannot access 'constructor'...: it is private`로 컴파일 거부. `AgencyTest`: 「AgencyCode of 는 CategoryCode of 와 같은 문자열을 낸다」(4 입력 교차) |
| (3) | 공고기관 값을 수요기관 필드에 접는다(폴백) | `demandAgencyFrom`·`noticeAgencyFrom`이 각자 자기 concept 만 읽는다 — 코드에 폴백 분기 0 | `AgencyCanonicalizeTest`: 「수요기관 키가 결측이고 공고기관 키만 있으면 demandAgency 는 null 이다」 |
| (4) | 담당자 키를 함께 등재한다 | `FieldConcept` 토큰 넷 고정(`DEMAND_AGENCY_CODE`·`_NAME`·`NOTICE_AGENCY_CODE`·`_NAME`만) | `AgencyFieldContractTest`: 「담당자 키는 어떤 rawName 에도 없다」(`rawNames.none { it.contains("Ofcl") }`) |
| (5) | V7 이 기존 컬럼·제약·트리거를 건드린다 | `ADD COLUMN` nullable 넷만(`V7__notice_agency.sql`), 제약·인덱스·트리거 0 | `CleanMigrationColumnTest`(컬럼 넷 존재·타입 text·nullable) GREEN, `CleanMigrationCheckTest`·`CleanMigrationTriggerTest`는 diff 없이 그대로 GREEN(기존 제약·트리거 개수 불변) |
| (6) | 3H-1 에서 이름을 몰래 요청 축 키로 쓴다 | `workflow/**`·`adapters/ml/**` out_of_scope | `commands.md` 「우회 (6) 확인」 — `git diff --name-status` 5개 경로 전부 0 |
| (7) | 이름을 정규화해 저장한다(표시용 원문 손실) | `AgencyName.of`는 trim 만 | `AgencyTest`: 「AgencyName of 는 원문을 trim 만 하고 정규화하지 않는다」(내부 공백 보존) · `NoticeFindRoundTripTest`: 저장 전 `"  수요 기관  "` → 복원 후 `"수요 기관"`(trim 만, 내부 공백·글자 순서 보존) |
| (8) | 빈 문자열 코드/이름이 fact 로 선다 | `AgencyCode.of`·`AgencyName.of`가 빈/공백 원문에 `null` 반환(예외 아님), `Agency.init`이 code·name 둘 다 null 을 거부 | `AgencyTest`: 「빈 문자열은 예외 없이 null 이다」×2(코드·이름 각각) + 공백만 있는 경우 각 1건 · 「Agency 는 code 와 name 이 둘 다 null 이면 거부된다」 |

## (2b) 값 획득 축 — 갱신 없음

scope.md (2b) 표(`AgencyCode.of`·`AgencyName`·`Agency`·`Notice.demandAgency`·`noticeAgency`·
`FieldConcept` 토큰 넷·`object` 커널 주입 자리)는 착수 시점에 이미 실제 구현과 정확히
일치했다 — 구현 중 새 public 표면이 추가로 생기지 않았다(`agencyFrom`·`demandAgencyFrom`·
`noticeAgencyFrom`은 `internal`/`private`, `KONEPS_AGENCY_FIELD_ROWS`·`NOTICE_IDENTIFIER
_PRESENT_IN`도 `internal` — 모듈 밖 신규 조립 경로 없음).

## 판단이 갈린 지점 — scope.md 갱신 필요(사후 보고)

- **`adapters/persistence/JdbcNoticeRepository.kt`·`NoticeRowMerge.kt`를 scope.md 의
  in_scope 목록 밖에서 건드렸다.** scope.md in_scope 는 `{NoticeRow,Sql,NoticeReconstruction}.kt`
  셋만 명시했으나, D-3H-4(「저장·복원」)를 실제로 구현하려면 `Sql.INSERT_NOTICE`·
  `UPDATE_NOTICE` 에 늘어난 컬럼 넷을 바인딩하는 `JdbcNoticeRepository.bindNoticeColumns`
  와, upsert 병합 규칙을 정하는 `NoticeRowMerge.mergeNoticeRow`(업무구분 코드·라벨과
  같은 존재 가드 — provenance 축 없음, D-3H-3)를 함께 고치지 않고는 컴파일도, 저장도
  되지 않는다 — 같은 층(persistence)의 같은 파일 묶음이 원래도 함께 움직이는 관계다
  (기존 `businessCategoryCode`·`floorRateFraction` 등 모든 notice 컬럼이 이 넷의
  파일에 함께 걸쳐 있다). 변경은 기계적(컬럼 넷 bind 추가, merge 존재 가드 추가)이고
  새 결정·새 정책 값을 도입하지 않는다 — 그래도 scope.md 문면과 실제 diff 가 어긋나므로
  팀장 검토 시 in_scope 목록에 두 파일을 추가해 갱신해야 한다.
- **`KonepsAgencyFieldContracts.kt`·`KonepsPresentInSets.kt`·`AgencyCanonicalizeTest.kt`
  세 파일을 새로 만들었다** — scope.md in_scope 의 명시 파일 목록에 없다. 셋 다 `sizeGate`
  (500줄)·`detekt`(`TooManyFunctions` 11개)가 `CollectionPolicy.kt`·`Canonicalize.kt`·
  `CanonicalizeTest.kt`에서 실제로 걸려(`commands.md` 15:05·15:10·15:13 라운드) 생긴
  **기계적 분리**다 — 내용은 그대로 옮겼을 뿐 새 로직이 아니다(`KonepsOpeningComplete
  FieldContracts.kt` 분리와 같은 전례, 그 파일의 KDoc이 이미 이 전례를 인용한다). 팀장
  검토 시 in_scope 목록 갱신 대상.

## 알려진 제한

- **`OPEN-2B-AGENCY-ID`는 이 slice로 닫히지 않는다.** 3H-1은 수집·저장·도메인까지다 —
  요청/표본 축 조립(3H-2)이 별도 계약으로 남아 있다. scope.md 종결 조건 문면 그대로다.
- **`OPEN-3H-AGENCY-BACKFILL`**(D-3H-5) — 정규화 전/기관 전 저장 행 없음(운영 데이터 0),
  백필 미실행. `raw_observation.payload` 원문에서 채울 수 있다는 사실만 등재한다.
- **`OPEN-3H-SAMPLE-MISSING-REASON`** — 3H-2 가 M5 레인에 건넬 항목(D-3H-6), 이 slice
  에서는 아직 열지 않는다(팀장 소관 등재).
- **채움률 실측(D-3H-8) 미실행** — scope.md 가 3H-1 자체는 이 실측 없이 진행 가능하다고
  명시한다(등재·저장은 채움률과 무관). read-only 실호출 1회는 3H-2 착수 전 별도 승인
  절차다.
- **migration-reviewer·Codex 리뷰 미실행** — 운영자 범위·비용 승인 뒤 별도 레인 소관
  (scope.md 「레인」 절). 이 slice 는 구현·자체 검증까지다.
