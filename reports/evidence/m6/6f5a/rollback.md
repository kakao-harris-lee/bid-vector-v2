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

`milestone-6.md`는 이 range에서 **팀장(세션 모델)의 커밋 넷**을 받았다 — 착수 계약 고정
(`3a233938`), 계약 갱신 (1)(`ddefc3c8`, OPEN-6F5A-RETENTION 등재 포함), r2·r3 판정 결과
등재(`51614deb`, 위치 술어의 종점은 타입이다), **계약 갱신 (5) + OPEN 여섯째 등재**
(`48c66aba`, `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS`를 milestone에 옮김, D-6F5-24). **round
5 정정(D-6F5-24, 팀장 실측)** — 앞 라운드까지의 문면은 `51614deb`까지만 반영해 커밋
**셋**으로 적었는데(D-6F5-23이 예측한 것과 같은 구조로 `48c66aba`가 또 낡게 했다),
실제로는 **넷**이다. **구조적 원인**(D-6F5-23·D-6F5-24) — 팀장 레인이 공유 문서를 커밋할
때마다 구현 레인의 §③이 낡는다: 구현 레인은 자기 커밋 뒤에 재산출하므로 그 뒤에 오는
팀장 커밋을 구조적으로 못 본다. 이 구현 레인은 milestone-6.md를 한 번도 편집하지
않았다(round 0~5 공통). CLAUDE.md의 「하네스 경로·승인 문서의 하네스 레인 편집은
되돌리지 않는다」와 같은 취급으로 **이 rollback은 milestone-6.md를 대상에서 뺀다**.
완전 원복이 필요하면(운영자 판단) 넷 모두 단일 역적용으로 되돌린다 — 범위 상한을 이
라운드에서 확인된 마지막 팀장 커밋(`48c66aba`, 계약 갱신 (5))까지 넓힌다:

```
git diff 3a233938~1..48c66aba -- milestone-6.md | git apply -R
```

**이번엔 상한 확대가 diff를 실제로 바꾼다** — `48c66aba`가 `milestone-6.md`를 직접
편집했으므로(`OPEN-CHECK-BODY-PRESENCE-ASSERTIONS` 등재), 상한을 `234079fd`에 둔 채로
두면 이 등재가 완전 원복 diff에서 빠진다(실측: `git diff 3a233938~1..234079fd --
milestone-6.md`와 `git diff 3a233938~1..48c66aba -- milestone-6.md`가 서로 다르다).
**앞 두 라운드(round 3→4, round 4→5 이전 문면)의 「상한을 넓혀 두면 다음 라운드가 또
낡지 않는다」는 일반화는 조건부였다** — 그때는 상한 커밋(`08397073`·`234079fd`)이
`milestone-6.md`를 안 건드려 무해했을 뿐이다(실측: `git diff 3a233938~1..234079fd --
milestone-6.md`와 `git diff 3a233938~1..51614deb -- milestone-6.md`는 같다). **상한을
그 라운드에서 확인된 마지막 팀장 커밋으로 맞추는 것은 매번 필요하고, 그 상한이 해당
파일을 직접 건드렸는지는 매번 실측해야 한다** — 「한 번 넓혀 두면 끝」이 아니다.

**참고** — 같은 range에서 `reports/evidence/m6/6f5a/scope.md`는 팀장 레인 커밋
**여섯**(`3a233938`·`ddefc3c8`·`cdcfb014`·`353865c9`·`234079fd`·`48c66aba`)을 받았지만,
scope.md는 evidence 경로라 위 목록 산출(`':!reports/evidence'`)에서 **항상** 제외되므로
이 §③이 scope.md는 별도로 다루지 않는다.

## 확인 지점

- in_scope 경로(위 목록)의 `git diff ede5d5b -- <경로>`가 비어 있다.
- `milestone-6.md`(팀장 레인, `48c66aba` 이후 무편집)·`reports/evidence/m6/6f5a/scope.md`
  (팀장 레인, 계약 갱신 라운드마다 갱신 — round 5 시점 `48c66aba`)는 이 rollback 대상이
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

## 병합 뒤 비대칭 (D-6F5-28, 계약 갱신 (6))

운영자 지시로 PR #38(6F-6, V12)이 먼저 `main`에 들어가(main의 병합 커밋 `edeaa9a3`) 이
브랜치가 충돌했다. `main`을 이 브랜치로 merge해 병합 커밋 `8ae5014f`로 흡수했다(rebase가
아니다 — 6F-1에서 rebase가 상대 레인 줄을 지우는 것을 실측했다). 그 병합이 `sizeGate`
둘(`CleanMigrationTest.kt` 501/500줄, `Sql.kt` 타입 멤버 31/30개, 각자는 한도 안이었으나
두 slice의 정당한 추가 합이 한계를 넘겼다)을 만들어 새 파일 둘이 생겼는데, **둘의 되돌림
처분이 갈린다**:

- **`RequirementSql.kt`** — **이 slice(6F-5-a) 자신의 상수 여섯**(`SELECT_REQUIREMENT_STATUS`
  등, `bidvector.adapters.persistence.Sql`에서 뽑아낸 것)만 담는다. **삭제 대상**이다 —
  더해 `bidvector.adapters.persistence.Sql`의 KDoc에 이 분리를 설명하는 문단 한 블록도
  되돌려야 한다(그 문단이 `RequirementSql`을 인용하므로). 팀장 실측(계약 갱신 (6)
  D-6F5-26 근거)과 이 구현 레인이 직접 대조한 `diff <(git show edeaa9a3:.../Sql.kt)
  adapters/.../Sql.kt`가 같은 결론이다 — `main`(6F-6 반영판, `edeaa9a3`) 대비 `Sql.kt`의
  차이는 **그 KDoc 문단 하나뿐**, 그 밖의 코드(6F-6의 `PROFILE_*` 상수 포함)는 손대지
  않았다.
- **`CleanMigrationPrivilegeTest.kt`** — **이 slice 이전부터 있던 축9**(유효 권한 행렬,
  M3/3G)를 옮겨 담은 파일이다. 이 slice가 새로 지은 test가 아니다 — 단언·기대 행렬·주석
  근거를 축어 그대로 옮겼을 뿐이다(구현 레인 보고: diff로 값 변경 0 확인). **삭제하면
  안 된다** — 삭제는 축9 전체를 지우는 것과 같다. 되돌리려면 그 내용을
  `CleanMigrationTest.kt`로 **도로 넣어야** 하고, `config/quality/gate-tests.properties`의
  `CleanMigrationPrivilegeTest` 등재도 함께 걷어야 한다(등재만 남기면 존재하지 않는
  class를 참조해 `gateExecutionGate`가 깨진다).

**기존 ①~⑥ 실측은 그대로 유효하다.** 위쪽 `실측 HEAD: d8e37fa3`와 목록(신규 8·수정 6)·
round 1~4의 트리 동일성 6/6은 **병합 전 이 브랜치 기준**이고, 그 시점(`d8e37fa3`)에
대해서는 지금도 참이다 — 이 절이 그 수치를 고치지 않는다.

**재실측은 하지 않는다.** 병합 뒤에는 되돌림 대상이 이 slice(6F-5-a) 하나가 아니라
6F-5-a·6F-6 **두 slice에 걸쳐** 있다 — 위 두 새 파일의 갈린 처분이 그 증거다. 이것은
더 이상 「이 slice의 rollback」이 아니다: in_scope 경로만 골라 base로 복원하는 지금까지의
절차(①~⑥)는 정의상 slice 하나의 범위에서만 성립한다. 병합 자체를 되돌려야 하는 경우의
절차는 이 절차가 아니라 **`main`의 병합 커밋(V12/PR #38을 흡수한 `edeaa9a3`) revert**다
— 이 브랜치의 병합 커밋 `8ae5014f`는 그 `main` 병합을 이 브랜치로 가져온 자리일 뿐,
되돌림의 대상은 `main` 쪽이다. 그 판단·실행은 이 slice의 rollback 범위 밖이다.
