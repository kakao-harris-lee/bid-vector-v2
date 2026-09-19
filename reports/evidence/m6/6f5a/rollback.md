실측 HEAD: d8e37fa33432545187a74fc614671d6e401f6000

# M6/6F-5-a — rollback

base: `ede5d5b` (main, PR #36·#37 병합 뒤)

이 slice는 신규 배선을 어디에도 꽂지 않는다(`OPEN-6F-ASSEMBLY`, app DI 조립 없음) — 되돌리는
것은 곧 새로 만든 파일·스키마를 base 상태로 지우는 것이다. 별도 feature flag/route
비활성화 경로는 없다(꽂힌 것이 없으므로).

## 목록 산출 — 기계적 (base..실측 HEAD, evidence 경로 제외)

```
git diff --name-status ede5d5b..d8e37fa3 -- . ':!reports/evidence'
```

신규(A) 8개, 수정(M) 6개 — **round 1~4(정정 라운드) 뒤에도 파일 목록 자체는 그대로**다
(round 2는 기존 test 파일 하나(`QualificationAdapterDependencyTest.kt`, 이미 A로 잡혀
있던 신규 파일)만, round 3·round 4는 기존 test 파일 하나(`CleanMigrationCheckTest.kt`,
이미 M으로 잡혀 있던 수정 파일)만 재편집했을 뿐 새 파일을 추가하지 않았다).

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
`Sql.kt`는 여전히 `dab9affe` 하나, `CleanMigrationTest.kt`·`CleanMigrationColumnTest.kt`·
`PersistenceTestSupport.kt`는 `056d3a37` 하나, `gate-tests.properties`는 `4bd5fdda`
하나뿐이다. `CleanMigrationCheckTest.kt`만 **셋**이다 — `056d3a37`(V13 축8 개수 반영,
추가만) + `584f226f`(round 3 D-6F5-16, 본문 단언 추가, 추가만 — 기존 test·기대값을
지우지 않았다, `git show 584f226f --stat` = `42 insertions(+)`) + **`d8e37fa3`**(round 4
D-6F5-21, `git show d8e37fa3 --stat` = `42 insertions(+), 18 deletions(-)` — 이번엔
**대체**다: 결합식 넷 존재 단언 test 하나를 지우고 집합 등식 test 하나로 바꿨다).
셋 다 이 slice 자신의 커밋이라 **다른 레인의 줄은 이 range 어디에도 없다**는 결론은
그대로다(`git log --oneline ede5d5b..d8e37fa3 -- <각 파일>`로 매번 실측 — 전부 이
slice의 커밋만 나온다, 6F-4는 별도 브랜치라 이 워킹트리에 아직 없다). round 2는 이
6개 중 어느 것도 건드리지 않았다(round 2 커밋 `97c44208`·`08397073`은 신규 파일
`QualificationAdapterDependencyTest.kt` 하나만 편집한다). 보존할 「남의 줄」이 없으므로
커밋별 hunk 격리 대신 **착수 경계(`ede5d5b`) 기준 단일 역적용**으로 건다 — `d8e37fa3`가
대체(삭제+추가)를 포함해도, 단일 역적용은 파일을 **base 상태로 통째로** 되돌리므로
중간 커밋이 추가였는지 대체였는지는 절차 결과에 영향이 없다(트리 동일성으로 아래
확인).

```
git restore --source=ede5d5b --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  config/quality/gate-tests.properties
```

`--source=ede5d5b`에 없는 경로(신규 8개, 위 ①)는 이 명령의 대상이 **아니다** — 다만 그
경로를 ②에 섞었을 때의 실제 거동은 verifier r2가 파이프 없이 종료 코드를 직접 받아
갈라 실측했다(정정, 아래 표 참고): **작업 트리에 아직 그 경로가 존재하면** `git restore
--source`는 오류 없이 **exit 0으로 조용히 삭제한다**(base에 없으므로 base 상태로
되돌린 결과가 "없음"이 되어서다). `did not match any file(s)` 오류(exit 1)는 그 경로가
**어디에도(작업 트리에도) 없을 때만** 난다 — 아래 표의 첫 시도가 그 경우였다: ①을
먼저 돌려 신규 8개를 이미 지운 뒤 ②에 그중 4개를 남겨 뒀기 때문이지, "base에 없어서
거부된다"가 아니다. 즉 **①과 ②를 나누는 이유는 「섞으면 오류로 잡히기 때문」이 아니라
「삭제와 복원의 의도를 문서에서 갈라 두기 위해서」다** — 순서를 지키지 않고 섞어도(아직
작업 트리에 있는 상태로) 오류 없이 조용히 지워질 뿐이라, 오류가 안전장치 역할을 하지
않는다. 그래도 ①과 ②는 섞지 않는다(evidence-pack 규격 「신규는 삭제, 편집은 restore」).

### ③ `milestone-6.md` — 되돌리지 않는다

`milestone-6.md`는 이 range에서 **팀장(세션 모델)의 커밋 셋**을 받았다 — 착수 계약 고정
(`3a233938`), 계약 갱신 (1)(`ddefc3c8`, OPEN-6F5A-RETENTION 등재 포함), r2·r3 판정 결과
등재(`51614deb`, 위치 술어의 종점은 타입이다). **D-6F5-23 정정(round 4)** — 앞
라운드까지의 문면은 `51614deb`를 놓쳐 커밋 **둘**로 적고 원복 범위를 `ddefc3c8`까지만
뒀다(milestone-6.md 자체는 대상 제외라 절차 결과는 틀리지 않았으나, 완전 원복 예시
명령이 낡아 있었다). **구조적 원인**(D-6F5-23) — 팀장 레인이 공유 문서를 커밋할
때마다 구현 레인의 §③이 낡는다: 구현 레인은 자기 커밋 뒤에 재산출하므로 그 뒤에 오는
팀장 커밋을 구조적으로 못 본다. 이 구현 레인은 milestone-6.md를 한 번도 편집하지
않았다(round 0~4 공통). CLAUDE.md의 「하네스 경로·승인 문서의 하네스 레인 편집은
되돌리지 않는다」와 같은 취급으로 **이 rollback은 milestone-6.md를 대상에서 뺀다**.
완전 원복이 필요하면(운영자 판단) 세 커밋 모두 단일 역적용으로 되돌린다 — 범위 상한을
이 라운드에서 확인된 마지막 팀장 커밋(`234079fd`, 계약 갱신 (4))까지 넓혀 둔다
(`234079fd` 자체는 milestone-6.md를 건드리지 않지만, 상한을 실제 마지막 지점에
맞춰 두면 diff 결과는 그대로 정확하고 다음 라운드가 또 좁은 범위로 낡지 않는다):

```
git diff 3a233938~1..234079fd -- milestone-6.md | git apply -R
```

**참고** — 같은 range에서 `reports/evidence/m6/6f5a/scope.md`도 팀장 레인 커밋
다섯(`3a233938`·`ddefc3c8`·`cdcfb014`·`353865c9`·`234079fd`)을 받았지만, scope.md는
evidence 경로라 위 목록 산출(`':!reports/evidence'`)에서 **항상** 제외되므로 이 §③이
scope.md는 별도로 다루지 않는다.

## 확인 지점

- in_scope 경로(위 목록)의 `git diff ede5d5b -- <경로>`가 비어 있다.
- `milestone-6.md`(팀장 레인, `51614deb` 이후 무편집)·`reports/evidence/m6/6f5a/scope.md`
  (팀장 레인, 계약 갱신 라운드마다 갱신 — round 4 시점 `234079fd`)는 이 rollback 대상이
  아니다(evidence 경로 제외 + 하네스/승인 문서 취급, 위 사유).
- `git status --porcelain`이 삭제(신규 8개)·수정 취소(공유 6개) 외 잔여가 없다.

## 마이그레이션 비대칭

없음 — 이 slice가 적용한 DB 인스턴스는 0개(Testcontainers 임시 컨테이너만 썼다, 6F-4·
6F-6이 각각 자기 인스턴스를 실측). V13 파일을 지우는 것 외에 별도 `DROP TABLE` 절차가
필요 없다(적용된 영구 스키마가 없다).

## 임시 clone 실측 — round 1 (HEAD `b7da46af`, 문서 정정의 근거)

`git clone --no-hardlinks` 로 만든 임시 clone에서 위 절차를 실행했다(HEAD `b7da46af`,
브랜치 `m6-6f5/2026-09-19`).

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① | `git rm -f <신규 8개>` | exit 0, 8 deletions staged |
| ② (첫 시도, 오류) | `git restore --source=ede5d5b`에 신규 4개를 잘못 섞음(①을 먼저 돌린 뒤라 그 4개는 작업 트리에도 없는 상태였다) | exit 1, `did not match any file(s)`(verifier r2가 원인을 정정 — MEDIUM-1) |
| ② (정정) | `git restore --source=ede5d5b`(수정 6개만, 경로 개별 인자) | exit 0 |
| ③ | (milestone-6.md 대상 제외 — 위 사유) | N/A |
| 확인 | `git diff ede5d5b -- <in_scope 21개>` | 빈 출력 |
| 확인 | `git status --porcelain` | D 8 · M 6, milestone-6.md·기타 없음 |
| 트리 동일성 | 되돌린 6개 파일 전부 `git hash-object`가 `ede5d5b`의 blob과 일치 | 6/6 일치 |
| ④ | `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` | exit 0 |
| ⑤ | `./gradlew --no-daemon :adapters:test --tests '*CleanMigration*Test*' --rerun-tasks` | exit 0(신규 표 기대치가 사라져 base 스키마와 다시 정합) |
| ⑥ | `./gradlew --no-daemon check` | exit 0 |

## 임시 clone 실측 — round 2 (HEAD `08397073`, verifier r2 D-6F5-14·D-6F5-15 뒤 재실측)

`git clone --no-hardlinks` 로 만든 별도 임시 clone에서 `08397073` 체크아웃 뒤 같은
절차를 한 번에(①→② 순서를 지켜) 실행했다 — 이번에는 신규·수정 목록을 섞지 않아
오류 없이 통과했다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① | `git rm -f <신규 8개>` | exit 0, 8 deletions staged |
| ② | `git restore --source=ede5d5b`(수정 6개만, 경로 개별 인자) | exit 0 |
| 확인 | `git diff ede5d5b -- adapters config` | 빈 출력 |
| 확인 | `git status --porcelain` | D 8 · M 6, milestone-6.md·기타 없음 |
| 트리 동일성 | 되돌린 6개 파일 전부 `git hash-object`가 `ede5d5b`의 blob과 일치 | **6/6 일치** |
| ④ | `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` | exit 0 |
| ⑤ | `./gradlew --no-daemon :adapters:test --tests '*CleanMigration*Test*' --rerun-tasks` | exit 0 |
| ⑥ | `./gradlew --no-daemon check` | exit 0 |

## 임시 clone 실측 — round 3 (HEAD `584f226f`, verifier r3 D-6F5-16 뒤 재실측)

`git clone --no-hardlinks` 로 만든 별도 임시 clone에서 `584f226f` 체크아웃 뒤 같은
절차를 한 번에(①→②) 실행했다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① | `git rm -f <신규 8개>` | exit 0, 8 deletions staged |
| ② | `git restore --source=ede5d5b`(수정 6개만, 경로 개별 인자) | exit 0 |
| 확인 | `git diff ede5d5b -- adapters config` | 빈 출력 |
| 확인 | `git status --porcelain` | D 8 · M 6, milestone-6.md·기타 없음 |
| 트리 동일성 | 되돌린 6개 파일 전부 `git hash-object`가 `ede5d5b`의 blob과 일치(`CleanMigrationCheckTest.kt`도 포함 — round 3이 더한 test 둘까지 정확히 걷힌다) | **6/6 일치** |
| ④ | `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` | exit 0 |
| ⑤ | `./gradlew --no-daemon :adapters:test --tests '*CleanMigration*Test*' --rerun-tasks` | exit 0 |
| ⑥ | `./gradlew --no-daemon check` | exit 0 |

## 임시 clone 실측 — round 4 (HEAD `d8e37fa3`, verifier r4 D-6F5-21 뒤 재실측)

`git clone --no-hardlinks` 로 만든 별도 임시 clone에서 `d8e37fa3` 체크아웃 뒤 같은
절차를 한 번에(①→②) 실행했다.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① | `git rm -f <신규 8개>` | exit 0, 8 deletions staged |
| ② | `git restore --source=ede5d5b`(수정 6개만, 경로 개별 인자) | exit 0 |
| 확인 | `git diff ede5d5b -- adapters config` | 빈 출력 |
| 확인 | `git status --porcelain` | D 8 · M 6, milestone-6.md·기타 없음 |
| 트리 동일성 | 되돌린 6개 파일 전부 `git hash-object`가 `ede5d5b`의 blob과 일치(`CleanMigrationCheckTest.kt`도 포함 — round 4가 대체한 test까지 정확히 base로 걷힌다) | **6/6 일치** |
| ④ | `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` | exit 0 |
| ⑤ | `./gradlew --no-daemon :adapters:test --tests '*CleanMigration*Test*' --rerun-tasks` | exit 0 |
| ⑥ | `./gradlew --no-daemon check` | exit 0 |

실측 명령·exit는 `commands.md`의 별도 절에 남기지 않는다(이 표가 그 기록이다, evidence
크기 게이트) — 네 임시 clone 모두 검증 뒤 삭제했다(디스크에 남기지 않는다).
