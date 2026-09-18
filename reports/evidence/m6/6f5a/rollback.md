실측 HEAD: b7da46afb5a20b83a3f8ef79baffbec87b481f16

# M6/6F-5-a — rollback

base: `ede5d5b` (main, PR #36·#37 병합 뒤)

이 slice는 신규 배선을 어디에도 꽂지 않는다(`OPEN-6F-ASSEMBLY`, app DI 조립 없음) — 되돌리는
것은 곧 새로 만든 파일·스키마를 base 상태로 지우는 것이다. 별도 feature flag/route
비활성화 경로는 없다(꽂힌 것이 없으므로).

## 목록 산출 — 기계적 (base..실측 HEAD, evidence 경로 제외)

```
git diff --name-status ede5d5b..b7da46af -- . ':!reports/evidence'
```

신규(A) 8개, 수정(M) 6개 — **round 1(정정 라운드) 뒤에도 파일 목록 자체는 그대로**다(새
파일 추가 없음, 기존 파일 재편집만 있었다).

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

### ② 수정 파일 — 단일 역적용(착수 경계 기준, 2026-09-16 규율)

**수정(M) 파일은 정확히 6개다** — `Sql.kt`·`CleanMigrationTest.kt`·
`CleanMigrationColumnTest.kt`·`CleanMigrationCheckTest.kt`·`PersistenceTestSupport.kt`·
`gate-tests.properties`. **round 1(정정 라운드)이 이 중 둘을 이 slice 자신의 커밋으로
다시 건드렸다** — 지금 6개 목록에는 없지만 **round 1이 새로 건드린 것은 이 6개가
아니라 신규 파일 넷**(`JdbcRequirementStore.kt`·`RequirementRowMapping.kt`·
`QualificationAdapterDependencyTest.kt`·`StoredRequirementLicenseGateTest.kt`, 전부 base
대비 A라 위 ①의 삭제 대상이다 — **restore 대상이 아니다**, 헷갈리기 쉬운 자리라 못 박는다).
`Sql.kt`는 여전히 `dab9affe` 하나, 나머지 다섯 M 파일은 전부 `056d3a37`·`4bd5fdda` 각
하나뿐이다. **다른 레인의 줄은 이 range 어디에도 없다**(`git log --oneline
ede5d5b..b7da46af -- <각 파일>`로 매번 실측 — 전부 이 slice의 커밋만 나온다, 6F-4는
별도 브랜치라 이 워킹트리에 아직 없다). 보존할 「남의 줄」이 없으므로 커밋별 hunk 격리
대신 **착수 경계(`ede5d5b`) 기준 단일 역적용**으로 건다.

```
git restore --source=ede5d5b --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  config/quality/gate-tests.properties
```

`--source=ede5d5b`에 없는 경로(신규 8개, 위 ①)는 이 명령의 대상이 **아니다** — `git
restore --source`는 그 경로를 base에서 찾지 못하면 `did not match any file(s)` 오류를
내며 **명령 전체가 실패한다**(①·② 실측 중 실제로 재현 — 처음 이 문서를 쓸 때 신규 4개를
실수로 이 목록에 섞었다가 그 오류로 잡혔다). ①과 ②를 섞지 않는다(evidence-pack 규격
「신규는 삭제, 편집은 restore」).

### ③ `milestone-6.md` — 되돌리지 않는다

`milestone-6.md`는 이 range에서 **팀장(세션 모델)의 커밋 둘만** 건드렸다 — 착수 계약 고정
(`3a233938`)과 계약 갱신 (1)(`ddefc3c8`, OPEN-6F5A-RETENTION 등재 포함). 이 구현 레인은 그
파일을 한 번도 편집하지 않았다(round 0·round 1 공통). CLAUDE.md의 「하네스 경로·승인
문서의 하네스 레인 편집은 되돌리지 않는다」와 같은 취급으로 **이 rollback은
milestone-6.md를 대상에서 뺀다**. 완전 원복이 필요하면(운영자 판단) 두 커밋 모두 단일
역적용으로 되돌린다:

```
git diff 3a233938~1..ddefc3c8 -- milestone-6.md | git apply -R
```

## 확인 지점

- in_scope 경로(위 목록)의 `git diff ede5d5b -- <경로>`가 비어 있다.
- `milestone-6.md`·`reports/evidence/m6/6f5a/scope.md`는 HEAD(`ddefc3c8` 상태) 그대로.
- `git status --porcelain`이 삭제(신규 8개)·수정 취소(공유 6개) 외 잔여가 없다.

## 마이그레이션 비대칭

없음 — 이 slice가 적용한 DB 인스턴스는 0개(Testcontainers 임시 컨테이너만 썼다, 6F-4·
6F-6이 각각 자기 인스턴스를 실측). V13 파일을 지우는 것 외에 별도 `DROP TABLE` 절차가
필요 없다(적용된 영구 스키마가 없다).

## 임시 clone 실측 (①~⑥)

`git clone --no-hardlinks` 로 만든 임시 clone에서 위 절차를 실행했다(HEAD `b7da46af`,
브랜치 `m6-6f5/2026-09-19`).

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① | `git rm -f <신규 8개>` | exit 0, 8 deletions staged |
| ② (첫 시도, 오류) | `git restore --source=ede5d5b`에 신규 4개를 잘못 섞음 | exit 1, `did not match any file(s)`(문서 정정의 근거) |
| ② (정정) | `git restore --source=ede5d5b`(수정 6개만, 경로 개별 인자) | exit 0 |
| ③ | (milestone-6.md 대상 제외 — 위 사유) | N/A |
| 확인 | `git diff ede5d5b -- <in_scope 21개>` | 빈 출력 |
| 확인 | `git status --porcelain` | D 8 · M 6, milestone-6.md·기타 없음 |
| 트리 동일성 | 되돌린 6개 파일 전부 `git hash-object`가 `ede5d5b`의 blob과 일치 | 6/6 일치 |
| ④ | `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` | exit 0 |
| ⑤ | `./gradlew --no-daemon :adapters:test --tests '*CleanMigration*Test*' --rerun-tasks` | exit 0(신규 표 기대치가 사라져 base 스키마와 다시 정합) |
| ⑥ | `./gradlew --no-daemon check` | exit 0 |

실측 명령·exit는 `commands.md`의 별도 절에 남기지 않는다(이 표가 그 기록이다, evidence
크기 게이트) — 임시 clone은 검증 뒤 삭제했다(디스크에 남기지 않는다).
