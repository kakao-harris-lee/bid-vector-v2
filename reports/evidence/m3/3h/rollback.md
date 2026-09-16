# rollback.md — M3 / 3H-1

## 대상

in_scope 경로 한정 restore. `base_sha = 0ad8e597ff08e4fb4e23d422659fb33a116b5ab1`.
목록은 `git diff --name-status <base_sha>..HEAD -- <in_scope 경로>`(수동 기입 금지 —
라운드마다 재실행)로 낸다. `reports/evidence/m3/3h/**`(이 문서 포함)·`milestone-3.md`·
`reports/evidence/m3/3a/policy-values.md`는 이 rollback 대상이 아니다 — 팀장 레인
소유(`milestone-3.md`·`policy-values.md`는 `git log --oneline <base>..HEAD -- <path>`로
저자가 팀장 커밋(`84426a2`)임을 확인했다).

**scope.md 갱신 필요 — 두 파일이 문면상의 in_scope 밖이다.** `checklist.md` 「판단이
갈린 지점」 참고 — `JdbcNoticeRepository.kt`·`NoticeRowMerge.kt`(D-3H-4 저장·복원의
구조적 필수 부분)와 `KonepsAgencyFieldContracts.kt`·`KonepsPresentInSets.kt`·
`AgencyCanonicalizeTest.kt`(sizeGate/detekt 가 강제한 기계적 분리, 전례 있음)를 아래
목록에 포함한다 — rollback 이 실제 diff 전부를 되돌려야 하므로 이 목록이 scope.md
문면보다 우선한다.

```
M  adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcNoticeRepository.kt
M  adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt
M  adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRow.kt
M  adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRowMerge.kt
M  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt
A  adapters/src/main/resources/db/migration/V7__notice_agency.sql
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeReconstructionTest.kt
M  config/quality/gate-tests.properties
A  procurement/src/main/kotlin/bidvector/procurement/Agency.kt
M  procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt
M  procurement/src/main/kotlin/bidvector/procurement/CollectionPolicy.kt
M  procurement/src/main/kotlin/bidvector/procurement/FieldContract.kt
A  procurement/src/main/kotlin/bidvector/procurement/KonepsAgencyFieldContracts.kt
A  procurement/src/main/kotlin/bidvector/procurement/KonepsPresentInSets.kt
M  procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt
A  procurement/src/test/kotlin/bidvector/procurement/AgencyCanonicalizeTest.kt
A  procurement/src/test/kotlin/bidvector/procurement/AgencyTest.kt
M  procurement/src/test/kotlin/bidvector/procurement/CanonicalizeTest.kt
M  procurement/src/test/kotlin/bidvector/procurement/CollectionPolicyTest.kt
M  procurement/src/test/kotlin/bidvector/procurement/FieldContractTest.kt
```

(`M` 16 · `A` 6 — 라운드마다 이 목록을 다시 산출한다.)

## 공유 파일 판별

`config/quality/gate-tests.properties`는 이 range(`0ad8e597..ec03ddb`)에서 이 slice의
커밋(`d483af9`·`ec03ddb`) 둘만 만졌다(`git log --oneline 0ad8e597..HEAD --
config/quality/gate-tests.properties`로 확인) — 다른 slice와 겹치지 않아 hunk 격리
없이 `base..HEAD` 전체 역적용으로 충분하다.

## 절차(in_scope 경로 개별 인자, `A` 항목 포함 — 삭제는 restore 가 처리)

```bash
git restore --source=0ad8e597ff08e4fb4e23d422659fb33a116b5ab1 --staged --worktree -- \
  procurement/src/main/kotlin/bidvector/procurement/FieldContract.kt \
  procurement/src/main/kotlin/bidvector/procurement/CollectionPolicy.kt \
  procurement/src/main/kotlin/bidvector/procurement/Agency.kt \
  procurement/src/main/kotlin/bidvector/procurement/KonepsAgencyFieldContracts.kt \
  procurement/src/main/kotlin/bidvector/procurement/KonepsPresentInSets.kt \
  procurement/src/main/kotlin/bidvector/procurement/NoticeFacts.kt \
  procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt \
  procurement/src/test/kotlin/bidvector/procurement/AgencyCanonicalizeTest.kt \
  procurement/src/test/kotlin/bidvector/procurement/AgencyTest.kt \
  procurement/src/test/kotlin/bidvector/procurement/CanonicalizeTest.kt \
  procurement/src/test/kotlin/bidvector/procurement/CollectionPolicyTest.kt \
  procurement/src/test/kotlin/bidvector/procurement/FieldContractTest.kt \
  adapters/src/main/resources/db/migration/V7__notice_agency.sql \
  adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRow.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcNoticeRepository.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeRowMerge.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeReconstructionTest.kt \
  config/quality/gate-tests.properties
```

`git checkout <base> --`는 쓰지 않는다(신규 경로마다 pathspec 오류로 exit 1 — 1A 16차
high 재발 방지). 신규 파일 여덟 개(`A`)는 `--source`에 해당 경로가 없으므로 restore가
작업 트리에서 삭제한다.

## V7 마이그레이션 롤백(운영 DB 적용 시)

V7 은 `notice`에 `ADD COLUMN` 넷만 했다 — 컬럼을 걷어내도 데이터 손실이 없다(다른
컬럼·행을 건드리지 않고, 이 컬럼들 자체가 비어 있는 상태에서 되돌린다면 값 손실도
없다. 이미 채워진 뒤라면 그 네 컬럼의 값만 사라진다 — 도메인 fact 재수집으로 복구
가능, 원본은 `raw_observation.payload`에 원문 그대로 남아 있다).

```sql
ALTER TABLE notice
    DROP COLUMN demand_agency_code,
    DROP COLUMN demand_agency_name,
    DROP COLUMN notice_agency_code,
    DROP COLUMN notice_agency_name;
```

파일 삭제(위 restore가 처리)와 별개로, 이미 migrate 된 DB 인스턴스에는 이 DDL을
별도로 실행해야 한다(Flyway는 파일이 사라져도 이미 적용된 마이그레이션 이력을
스스로 되돌리지 않는다 — `flyway_schema_history`에서 V7 행을 지우는 것은 이 slice
rollback의 범위 밖이며, 운영 DB 조작은 사용자 승인 없이 실행하지 않는다).

## 임시 clone 실측(`git clone --no-hardlinks`, `/tmp/3h1-rollback-check2`)

① `git diff --name-status 0ad8e597..HEAD -- <in_scope>` — 위 목록과 일치(기계 산출,
재확인).
② `git restore --source=0ad8e597ff08e4fb4e23d422659fb33a116b5ab1 ...` 실행 —
`git status --short` 결과: `M` 16 · `D` 6(신규 여섯 개가 삭제로 나타남 — `A`→`D`,
restore의 정상 동작). exit 0.
③ `git diff 0ad8e597ff08e4fb4e23d422659fb33a116b5ab1 -- <in_scope 코드 경로> | wc -l`
→ **0** — in_scope 경로가 base와 바이트 동일.
④ `./gradlew --no-daemon :procurement:compileKotlin :procurement:compileTestKotlin
:adapters:compileKotlin :adapters:compileTestKotlin` → exit 0.
⑤ `./gradlew --no-daemon :procurement:test :adapters:test` → exit 0.
⑥ `./gradlew --no-build-cache --no-daemon clean check`(되돌린 트리 전건) → **exit 0**.
BUILD SUCCESSFUL(355 actionable tasks, 55s — 신선한 clone이라 캐시 재사용이 적어
worktree 실측보다 task 수가 많다). 되돌린 트리가 게이트를 붉히지 않는다 — evidence
디렉터리(`reports/evidence/m3/3h/**`)·`milestone-3.md`·`policy-values.md`는
되돌리지 않았고 그것이 게이트에 걸리지 않음도 이 실행으로 확인됐다.

임시 clone은 확인 뒤 삭제했다(`rm -rf /tmp/3h1-rollback-check2`).

## 하네스 레인 변경

없음. `git log --oneline 0ad8e597ff08e4fb4e23d422659fb33a116b5ab1..HEAD -- CLAUDE.md
.claude/` 결과 0건(단일 역적용 뒤 재등재 대상 없음).

## 예상 복구 시간

원격 상태 변경 없음, 로컬 Postgres 마이그레이션(V7, 컬럼 넷 추가)만 — 적용된 DB 가
있으면 위 `ALTER TABLE ... DROP COLUMN` 넷 추가 실행, 없으면 `git restore` 한 명령 +
재빌드 시간(로컬 실측 ~1분) 이내.
