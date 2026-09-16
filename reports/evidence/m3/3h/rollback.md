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

## V7 마이그레이션 롤백 — 경로 둘(Codex 1차 request_changes high 시정)

V7 은 `notice`에 `ADD COLUMN` 넷만 했다 — 컬럼을 걷어내도 데이터 손실이 없다(다른
컬럼·행을 건드리지 않고, 이 컬럼들 자체가 비어 있는 상태에서 되돌린다면 값 손실도
없다. 이미 채워진 뒤라면 그 네 컬럼의 값만 사라진다 — 도메인 fact 재수집으로 복구
가능, 원본은 `raw_observation.payload`에 원문 그대로 남아 있다). **이 데이터 손실
조건은 아래 두 경로 어느 쪽으로 되돌리든 같다.**

경로는 **DB 에 V7 이 이미 적용됐는가**로 갈린다 — `V7` 파일을 지우고 되돌리는 방식은
**DB 미적용**일 때만 성립한다. 이미 적용된 뒤 같은 방식을 쓰면 로컬 migration 파일
집합에서 V7 이 사라지는데 `flyway_schema_history`엔 V7 행이 남아, `CleanMigrationTest`
의 `flyway validate 가 통과한다` test(파일↔이력 불일치를 실패로 고정)가 실패한다 —
되돌린 코드가 자기 게이트 위에 서지 못한다(Codex 1차 심판 `codex-review-20260916T162509Z.json`
finding, high).

### (a) DB 미적용 — 오늘 상태(프로덕션 배포 전, 운영 데이터 0)

`V7__notice_agency.sql` 을 포함해 위 in_scope 목록 전체를 `git restore`로 되돌린다
(아래 「임시 clone 실측」 절 그대로 — 이미 실측 완료). `flyway_schema_history`에 V7
행 자체가 없으므로 파일 삭제가 이력과 어긋나지 않는다.

### (b) DB 적용 후(롤포워드) — V7 파일은 지우지 않는다

**`flyway repair`는 대안이 아니다** — `repair`는 `flyway_schema_history`를 로컬
migration 파일 집합과 다시 맞추는 명령이지, **없어진 파일에 대응하는 이력 행을
지우지 않는다**(공식 동작 — 체크섬 재계산·실패 항목 정리가 전부이지 applied-but-
missing 행 삭제가 아니다). 이력 행을 직접 `DELETE`하는 것도 채택하지 않는다 —
운영자 승인 없는 이력 편집이다.

절차: ① 코드는 in_scope 목록에서 **`V7__notice_agency.sql` 을 뺀 나머지**만
`git restore`로 되돌린다 — 즉 V7 파일은 그대로 두고 V7 이 편집한 Kotlin 코드
(persistence·도메인)만 base 로 되돌린다. ② 보상 마이그레이션
`V8__drop_notice_agency.sql`(`ALTER TABLE notice DROP COLUMN` ×4, V7 이 더한 컬럼
그대로 되돌림)을 새로 만든다. ③ 적용: V1~V6 는 원래 이력과 그대로 일치하고, V7 은
파일이 남아 있어 이력의 V7 행과 체크섬이 계속 맞는다(validate 통과). V8 은 처음
보는 신규 이력이라 정상적으로 `migrate`된다. 되돌린 코드가 참조하는 스키마(기관
컬럼 없음)와 V8 이후 실제 스키마가 일치한다.

## 임시 clone 실측 — (a) 경로(`git clone --no-hardlinks`, `/tmp/3h1-rollback-check2`)

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

## 임시 clone 실측 — (b) 경로(`git clone --no-hardlinks`, `/tmp/3h1-rollback-b-check`)

절차: ① 코드 in_scope 22 경로 중 `V7__notice_agency.sql` 을 **뺀 21 경로**만
`git restore --source=0ad8e597ff08e4fb4e23d422659fb33a116b5ab1 --staged --worktree --`
로 되돌린다 — exit 0, `git status --short` 결과 `M` 16 · `D` 5(V7 은 목록에서 빠져
그대로 남는다). ② `git diff 0ad8e597.. -- <되돌린 21 경로> | wc -l` → **0**(V7 은
대조 대상에서 제외 — base 자체에 없던 파일이라 애초에 diff 가 없다). ③ V7 파일이
여전히 있는지 `ls adapters/src/main/resources/db/migration/` 로 확인 — `V7__notice_
agency.sql` 존재. ④ **실측용 임시 파일**
`adapters/src/main/resources/db/migration/V8__drop_notice_agency.sql`(`ALTER TABLE
notice DROP COLUMN demand_agency_code, DROP COLUMN demand_agency_name, DROP COLUMN
notice_agency_code, DROP COLUMN notice_agency_name;`, 이 브랜치에는 커밋하지 않는다
— 내용은 이 문서에 전문 보존) 을 추가한다. ⑤ `./gradlew --no-build-cache --no-daemon
clean check`(되돌린 트리 + V8, 전건) → **exit 0**. BUILD SUCCESSFUL(355 actionable
tasks, 1m 3s, 355 개 전부 실행 — 신선 clone). `CleanMigrationTest`의 `flyway
validate 가 통과한다` test GREEN(6 tests 중 포함, 0 failed) — V1~V6 는 이력과
그대로 일치, V7 은 파일이 남아 있어 체크섬이 맞고, V8 은 신규 이력으로 정상
적용된다. `CleanMigrationColumnTest`(3 tests, 0 failed)도 GREEN — V8 이 V7 의
컬럼 넷을 되돌려 최종 스키마가 base 의 컬럼 행렬과 다시 일치하고, 되돌린 코드
(기관 컬럼 참조 0)와 어긋나지 않는다. ⑥ 확인 뒤 `V8__drop_notice_agency.sql` 을
포함해 임시 clone 을 통째로 삭제했다(`rm -rf /tmp/3h1-rollback-b-check`) — V8 파일은
어디에도 커밋되지 않았다.

(b) 경로가 실제 운영 rollback 을 수행할 때는 ④의 `V8__drop_notice_agency.sql` 을
그대로(위 SQL 문면 그대로) 새 마이그레이션 파일로 커밋·배포한다 — 이 evidence
실측은 그 파일이 기존 이력과 충돌 없이 적용되고 최종 상태가 게이트를 통과함을
미리 증명해 둔 것이다.

## 하네스 레인 변경

없음. `git log --oneline 0ad8e597ff08e4fb4e23d422659fb33a116b5ab1..HEAD -- CLAUDE.md
.claude/` 결과 0건(단일 역적용 뒤 재등재 대상 없음).

## 예상 복구 시간

원격 상태 변경 없음, 로컬 Postgres 마이그레이션(V7, 컬럼 넷 추가)만. (a) DB 미적용
— `git restore` 한 명령 + 재빌드 시간(로컬 실측 ~1분) 이내. (b) DB 적용 후(롤포워드)
— 21 경로 `git restore` + `V8__drop_notice_agency.sql` 신설·배포 한 마이그레이션,
재빌드 포함 ~1분 이내(위 실측과 같은 규모).
