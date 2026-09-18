실측 HEAD: 694fad4a04a99216067df4b70904307d8393842d

# M6/6F-5-a — rollback

base: `ede5d5b` (main, PR #36·#37 병합 뒤)

이 slice는 신규 배선을 어디에도 꽂지 않는다(`OPEN-6F-ASSEMBLY`, app DI 조립 없음) — 되돌리는
것은 곧 새로 만든 파일·스키마를 base 상태로 지우는 것이다. 별도 feature flag/route
비활성화 경로는 없다(꽂힌 것이 없으므로).

## 목록 산출 — 기계적 (base..HEAD, evidence 경로 제외)

```
git diff --name-status ede5d5b..694fad4a -- . ':!reports/evidence'
```

신규(A) 8개, 수정(M) 7개.

## 절차

### ① 신규 파일 — 삭제

```
git rm -f \
  adapters/src/main/kotlin/bidvector/adapters/qualification/JdbcRequirementStore.kt \
  adapters/src/main/kotlin/bidvector/adapters/qualification/RequirementRowMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/qualification/StoredRequirementLicenseGate.kt \
  adapters/src/main/resources/db/migration/V13__notice_requirement.sql \
  adapters/src/test/kotlin/bidvector/adapters/qualification/JdbcRequirementStoreTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/qualification/QualificationAdapterDependencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/qualification/QualificationGateRegistrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/qualification/StoredRequirementLicenseGateTest.kt
```

### ② 수정 파일 — 전부 공유 파일(scope.md 상시 절), 커밋 해시 hunk 격리

이 range에서 각 공유 파일을 만진 커밋은 **이 slice의 커밋 하나씩뿐**이다(`git log --oneline
ede5d5b..694fad4a -- <파일>`로 실측, 대조 커밋 없음 — 6F-4는 별도 브랜치라 이 워킹트리에
아직 없다). 그래도 공유 파일 관례대로 range 전체 revert가 아니라 **자기 커밋 해시로 hunk를
격리**해 역적용한다.

```
git diff dab9affe~1..dab9affe -- adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  | git apply -R

git diff 056d3a37~1..056d3a37 -- \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  | git apply -R

git diff 4bd5fdda~1..4bd5fdda -- config/quality/gate-tests.properties | git apply -R
```

### ③ `milestone-6.md` — 되돌리지 않는다

`milestone-6.md`는 이 range에서 **팀장(세션 모델)의 착수 계약 고정 커밋 `3a233938` 하나만**
건드렸다(`git log --oneline ede5d5b..694fad4a -- milestone-6.md`로 실측) — 이 구현 레인은
그 파일을 한 번도 편집하지 않았다. CLAUDE.md의 「하네스 경로·승인 문서의 하네스 레인
편집은 되돌리지 않는다」와 같은 취급으로 **이 rollback은 milestone-6.md를 대상에서
뺀다**. 완전 원복이 필요하면(운영자 판단) 같은 형태로 역적용한다:

```
git diff 3a233938~1..3a233938 -- milestone-6.md | git apply -R
```

## 확인 지점

- in_scope 경로(위 목록)의 `git diff ede5d5b -- <경로>`가 비어 있다.
- `milestone-6.md`는 HEAD(`3a233938` 상태) 그대로.
- `git status --porcelain`이 삭제(신규 8개)·수정 취소(공유 6개) 외 잔여가 없다.

## 마이그레이션 비대칭

없음 — 이 slice가 적용한 DB 인스턴스는 0개(Testcontainers 임시 컨테이너만 썼다, 6F-4·
6F-6이 각각 자기 인스턴스를 실측). V13 파일을 지우는 것 외에 별도 `DROP TABLE` 절차가
필요 없다(적용된 영구 스키마가 없다).

## 임시 clone 실측 (①~⑥)

`git clone --no-hardlinks` 로 만든 임시 clone에서 위 절차를 실행했다(HEAD `694fad4a`,
브랜치 `m6-6f5/2026-09-19`).

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① | `git rm -f <신규 8개>` | exit 0, 8 deletions staged |
| ② | `git apply -R`(공유 3묶음) | exit 0, conflict 없음 |
| ③ | (milestone-6.md 대상 제외 — 위 사유) | N/A |
| 확인 | `git diff ede5d5b -- <in_scope 21개>` | 빈 출력 |
| 확인 | `git status --porcelain` | D 8 · M 6(공유 6개), milestone-6.md·기타 없음 |
| ④ | `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` | exit 0 |
| ⑤ | `./gradlew --no-daemon :adapters:test --tests '*CleanMigration*Test*' --rerun-tasks` | exit 0(신규 표 기대치가 사라져 base 스키마와 다시 정합) |
| ⑥ | `./gradlew --no-daemon check` | exit 0 |

실측 명령·exit는 `commands.md`의 별도 절에 남기지 않는다(이 표가 그 기록이다, evidence
크기 게이트) — 임시 clone은 검증 뒤 삭제했다(디스크에 남기지 않는다).
