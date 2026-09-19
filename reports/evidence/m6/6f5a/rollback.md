실측 HEAD: 51b517bdec67d8dfef78b69b165bc328d8e5f98b

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

## 복원 기준의 정의 (D-6F5-30)

**공유 파일의 복원 기준은 고정 SHA가 아니라 「이 브랜치가 분기해 나온 현재 `main`」이다.**
팀장 커밋이든 병합이든 범위가 이 slice 레인 밖에서 움직이면 기준이 **따라 움직인다** —
「무엇 뒤에 재산출하는가」를 라운드마다 한 칸씩 열거하지 않는다(D-6F5-23·D-6F5-24가 그
열거를 한 칸씩 늘렸고, D-6F5-29가 그 한계를 실측했다 — 병합은 「한 칸」이 아니라 기준
자체를 바꾼다).

**기준 SHA를 구하는 법**(다음 사람이 문서를 읽고 스스로 산출할 수 있어야 한다):

```
git merge-base HEAD origin/main
```

착수 시점에는 이 값이 `ede5d5b`였다 — 이 slice가 `ede5d5b` 뒤에서 분기했기 때문이다.
PR #38(6F-6, V12)이 이 slice보다 먼저 `main`에 병합돼(main 병합 커밋 `edeaa9a3`) 이
브랜치가 그것을 흡수한 뒤(이 브랜치의 병합 커밋 `8ae5014f`)로는 이 값이 **`edeaa9a3`**로
바뀐다 — 이 구현 레인이 직접 실행해 확인했다(`git merge-base HEAD origin/main` =
`edeaa9a3...`, 2026-09-19). 아래 §①②는 **현재 기준(`edeaa9a3`)** 을 쓴다.

## 절차

### ① 신규 파일 — 삭제 (10개, D-6F5-29 보정)

**8 → 10** — 병합이 `sizeGate` 회피로 만든 새 파일 둘(`RequirementSql.kt`·
`CleanMigrationPrivilegeTest.kt`)이 더해진다. 원래 8개와 성격이 다르다: 원래 8개는 이
slice가 처음부터 새로 지은 파일이고, 새 둘은 **병합이 만들었다**(§ 「병합 뒤 비대칭」
참고 — `RequirementSql.kt`는 이 slice 몫 상수 이전, `CleanMigrationPrivilegeTest.kt`는
이 slice 이전부터 있던 축9를 옮긴 것). 그래도 **기준이 `edeaa9a3`로 바뀌면 열 다
`edeaa9a3`에 없는 파일**이라 삭제 대상은 같다.

```
git rm -f \
  adapters/src/main/kotlin/bidvector/adapters/qualification/JdbcRequirementStore.kt \
  adapters/src/main/kotlin/bidvector/adapters/qualification/RequirementRowMapping.kt \
  adapters/src/main/kotlin/bidvector/adapters/qualification/RequirementSql.kt \
  adapters/src/main/kotlin/bidvector/adapters/qualification/StoredRequirementLicenseGate.kt \
  adapters/src/main/resources/db/migration/V13__notice_requirement.sql \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationPrivilegeTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/qualification/JdbcRequirementStoreTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/qualification/QualificationAdapterDependencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/qualification/QualificationGateRegistrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/qualification/StoredRequirementLicenseGateTest.kt
```

### ② 수정 파일 — 단일 역적용(복원 기준 = 현재 `main`, D-6F5-29 보정)

**수정(M) 파일은 정확히 6개다** — `Sql.kt`·`CleanMigrationTest.kt`·
`CleanMigrationColumnTest.kt`·`CleanMigrationCheckTest.kt`·`PersistenceTestSupport.kt`·
`gate-tests.properties`(목록 자체는 round 1~4와 같다). **round 1~4의 「다른 레인의 줄이
이 range에 없다」는 결론은 병합 전 얘기다** — 그때는 이 6개 파일에 6F-6의 줄이 없었다.
병합 뒤에는 이 6개 전부에 6F-6의 줄이 섞여 있다(`Sql.kt`의 `PROFILE_*`,
`CleanMigrationTest.kt`·`CleanMigrationColumnTest.kt`의 `operator_profile` 항목,
`PersistenceTestSupport.kt`의 TRUNCATE 목록, `gate-tests.properties`의
`adapters.profile.*` 등재). **그래서 착수 경계(`ede5d5b`)로 단일 역적용하면 그 줄이
전부 사라진다** — verifier r6 실측(D-6F5-29): `PROFILE_*` 3→0·등재 4→0·TRUNCATE의
`operator_profile` 2→0, exit 0·stderr 없음(`ede5d5b`가 6F-6 이전이라 조용히 지워진다).

**보정** — 위 「복원 기준의 정의」대로, 단일 역적용의 기준을 **`ede5d5b`가 아니라
현재 `main`(`edeaa9a3`)** 으로 쓴다. `edeaa9a3`는 이미 6F-6을 담고 있으므로, 그
기준으로 되돌리면 6F-6의 줄은 **그대로 남고** 이 slice가 얹은 줄만 사라진다.

```
git restore --source=edeaa9a3 --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  config/quality/gate-tests.properties
```

이 복원은 **축9(유효 권한 행렬)도 자동으로 되돌린다** — `edeaa9a3`의 `CleanMigrationTest
.kt`는 이 slice가 축9를 `CleanMigrationPrivilegeTest.kt`로 분리하기 이전 상태라 축9가
그 파일 안에 그대로 있다. **D-6F5-28의 정정(D-6F5-29)** — 앞 절(「병합 뒤 비대칭」)이
「축9를 `CleanMigrationTest.kt`로 손으로 되돌려 넣어야 한다」고 적은 것은 **①②를
`ede5d5b` 기준으로 실행한다는 전제**였다. 이 보정 절차(기준 `edeaa9a3`)에서는 위 ①이
`CleanMigrationPrivilegeTest.kt`를 지우고 위 ②가 `CleanMigrationTest.kt`를 축9가 든
`edeaa9a3` 상태로 되돌리므로 **수동 재삽입이 필요 없다**. 같은 이유로 `Sql.kt`의
KDoc 되돌림(`RequirementSql.kt`를 인용하는 문단 제거)도 ②의 `Sql.kt` 복원이 자동으로
한다.

**round 1의 MEDIUM-1 교훈(①과 ②를 나누는 이유)은 기준이 바뀌어도 그대로 유효하다** —
`--source=<기준>`에 없는 경로를 ②에 섞으면, 작업 트리에 아직 있으면 오류 없이
조용히 지워지고 어디에도 없을 때만 `did not match any file(s)`로 잡힌다(verifier r2
실측). ①과 ②는 여전히 섞지 않는다(evidence-pack 규격 「신규는 삭제, 편집은
restore」).

### ③ `milestone-6.md` — 되돌리지 않는다

`milestone-6.md`를 이 range에서 만진 커밋은 **일곱**이다 — 이 구현 레인 넷(착수 계약
고정 `3a233938`, 계약 갱신 (1) `ddefc3c8`, r2·r3 판정 결과 등재 `51614deb`, 계약 갱신
(5) `48c66aba`) + **6F-6 레인 둘**(6F-6 착수 계약 고정 `73624ab0`, verifier r2 장부층
등재 `fa9cc90b`) + **병합 커밋 하나**(`8ae5014f`, 두 레인의 milestone-6.md 편집을
자동 병합) — `git log --oneline ede5d5b..92c83a0b -- milestone-6.md`로 직접 세어
확인했다. **정정(D-6F5-31/LOW-2)** — 앞 라운드까지의 문면은 이 구현 레인 몫만 세어
「넷」으로 적었는데, 병합 뒤 범위 설명으로는 불완전했다 — 병합이 6F-6 레인의 커밋
둘과 병합 커밋 자체를 이 range에 끌어들였다. **구조적 원인은 여전히 D-6F5-23·
D-6F5-24와 같다**(팀장 레인이 공유 문서를 커밋할 때마다, 이번엔 다른 레인의 병합까지
포함해, 구현 레인의 §③이 낡는다). 이 구현 레인은 milestone-6.md를 한 번도 편집하지
않았다(round 0~5 공통). CLAUDE.md의 「하네스 경로·승인 문서의 하네스 레인 편집은
되돌리지 않는다」와 같은 취급으로 **이 rollback은 milestone-6.md를 대상에서 뺀다**.

**완전 원복 명령 자체는 정정 대상이 아니다** — 아래 명령은 `3a233938`부터 `48c66aba`까지
(이 구현 레인만의 범위)를 되돌리고, `73624ab0`·`fa9cc90b`·`8ae5014f`는 `48c66aba`의
조상이 아니라(별도 브랜치에서 나중에 병합됐다) 이 diff 범위 밖이다. 그래도 이 구현
레인이 만든 문단들은 서로 겹치지 않는 위치에 있어, 이 patch는 **현재(병합 뒤)
`milestone-6.md`에도 깨끗이 역적용된다** — 직접 확인했다(`git diff 3a233938~1..48c66aba
-- milestone-6.md`를 저장해 `git apply -R --check`로 현재 HEAD에 대해 검사, exit 0):

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

**병합 뒤(D-6F5-29 보정 절차, 기준 `edeaa9a3`)**:

- in_scope 경로(위 ①② 목록)의 `git diff edeaa9a3 -- adapters config`가 비어 있다.
- 6F-6 줄이 살아 있다 — `Sql.kt`의 `PROFILE_*`, `gate-tests.properties`의
  `adapters.profile.*` 등재, `PersistenceTestSupport.kt` TRUNCATE의 `operator_profile`.
- 이 slice 줄이 사라졌다 — `Sql.kt`의 `REQUIREMENT_*`, `CleanMigrationTest.kt`·
  `CleanMigrationColumnTest.kt`·`CleanMigrationCheckTest.kt`의 `notice_requirement*`,
  `gate-tests.properties`의 `adapters.qualification.*` 등재, TRUNCATE의
  `notice_requirement`.
- `milestone-6.md`(팀장 레인 + 6F-6 레인 + 병합, `48c66aba`·`fa9cc90b`·`8ae5014f` 이후
  무편집)·`reports/evidence/m6/6f5a/scope.md`(팀장 레인, 계약 갱신 라운드마다 갱신 —
  round 5 시점 `e1d90134`)는 이 rollback 대상이 아니다(evidence 경로 제외 +
  하네스/승인 문서 취급, 위 사유).
- `git status --porcelain`이 삭제(신규 **10**개)·수정 취소(공유 6개) 외 잔여가 없다.

**병합 전(round 1~4, 기준 `ede5d5b`, 아래 임시 clone 실측 표들의 근거) — 참고로 남긴다**:
그 시점에는 신규 8개·기준 `ede5d5b`였고, 그 실측(`실측 HEAD: d8e37fa3`)은 지금도 그
시점에 대해 참이다.

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
크기 게이트) — 네 임시 clone 모두 검증 뒤 삭제했다(디스크에 남기지 않는다). 이 넷은
**병합 전**(기준 `ede5d5b`) 실측이다.

## 임시 clone 실측 — round 5 (D-6F5-29 보정 절차, 병합 뒤 기준 `edeaa9a3`)

**정정(계약 갱신 (8)) — 실측 HEAD를 다시 잰다.** 첫 실측은 `e1d90134`에서 돌았는데,
`51b517bd`(D-6F5-32 KDoc 정정, in_scope 코드 파일 편집이라 **내용 커밋**이다 —
`git log -1 ede5d5b..HEAD -- . ':!reports/evidence' ':!milestone-6.md'` = `51b517bd`)가
그 뒤에 나와 실측 HEAD가 판정 대상과 어긋났다. **KDoc이 담긴 파일은 절차가 삭제하는
파일이라 결과가 같을 것이라는 논증은 실측을 갈음하지 않는다** — 새 clone에서 다시
쟀다.

`git clone --no-hardlinks`로 만든 별도 임시 clone(HEAD `51b517bd`)에서 위 보정된
①②(신규 10개 삭제, 기준 `edeaa9a3`로 공유 6개 복원)를 실행했다. **verifier 결과를
옮기지 않고 이 구현 레인이 직접 측정했다** — 수치마다 **무엇을 세는 grep인지**를
같이 적는다(선언·출현·등재 줄을 구분한다).

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① | `git rm -f <신규 10개>` | exit 0, 10 deletions staged |
| ② | `git restore --source=edeaa9a3`(공유 6개만, 경로 개별 인자) | exit 0 |
| 6F-6 줄 생존 — `Sql.kt`의 `PROFILE` **선언**(`grep -cE '(private )?const val [A-Za-z_]*PROFILE[A-Za-z_]* =' Sql.kt`) | **3**(`PROFILE_COLUMNS`·`SELECT_PROFILE`·`UPSERT_PROFILE`) |
| 6F-6 줄 생존 — `Sql.kt`의 `PROFILE` **문자열 출현 총수**(`grep -o PROFILE Sql.kt \| wc -l`, 선언+참조 전부) | **5**(참고 수치 — 이전 라운드가 적은 「4」는 `grep -c`로 **일치하는 줄 수**를 센 것이라 서로 다른 척도였다) |
| 6F-6 줄 생존 — `gate-tests.properties`의 `adapters.profile.*` **등재 줄**(`grep -c 'bidvector.adapters.profile\.' gate-tests.properties`) | **3** |
| 6F-6 줄 생존 — `PersistenceTestSupport.kt` TRUNCATE의 `operator_profile` **문자열 출현**(`grep -o operator_profile PersistenceTestSupport.kt \| wc -l`) | **2** |
| 이 slice 줄 소멸 — `Sql.kt`의 `REQUIREMENT` 출현·`CleanMigrationTest/Column/CheckTest.kt`의 `notice_requirement` 출현(각 파일)·`gate-tests.properties`의 `adapters.qualification.*` 등재 줄·TRUNCATE의 `notice_requirement` 출현 | **전부 0** |
| 확인 | `git diff edeaa9a3 -- adapters config` | 빈 출력 |
| 확인 | `git status --porcelain` | D 10 · M 6, milestone-6.md·기타 없음 |
| 트리 동일성 | 되돌린 6개 파일 전부 `git hash-object`가 `edeaa9a3`의 blob과 일치 | **6/6 일치** |
| 축9 자동 복귀 | `grep -c '축 9\|effectivePrivileges' CleanMigrationTest.kt` = 4(존재) · `wc -l` = 466줄(sizeGate 500 안) | 수동 재삽입 불필요 확인 |
| ④ | `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` | exit 0 |
| ⑤ | `./gradlew --no-daemon :adapters:test --tests '*CleanMigration*Test*' --tests '*ProfileAdapterDependencyTest*' --tests '*ProfileGateRegistrationTest*' --tests '*JdbcOperatorProfileRepositoryTest*' --rerun-tasks` | exit 0 |
| ⑥ | `./gradlew --no-daemon check` | exit 0 |

임시 clone은 검증 뒤 삭제했다(디스크에 남기지 않는다).

## 병합 뒤 비대칭 (D-6F5-28, 계약 갱신 (6) — D-6F5-29/30으로 정정됨, 계약 갱신 (7))

운영자 지시로 PR #38(6F-6, V12)이 먼저 `main`에 들어가(main의 병합 커밋 `edeaa9a3`) 이
브랜치가 충돌했다. `main`을 이 브랜치로 merge해 병합 커밋 `8ae5014f`로 흡수했다(rebase가
아니다 — 6F-1에서 rebase가 상대 레인 줄을 지우는 것을 실측했다). 그 병합이 `sizeGate`
둘(`CleanMigrationTest.kt` 501/500줄, `Sql.kt` 타입 멤버 31/30개, 각자는 한도 안이었으나
두 slice의 정당한 추가 합이 한계를 넘겼다)을 만들어 새 파일 둘이 생겼는데, **둘의 되돌림
처분이 갈린다**:

- **`RequirementSql.kt`** — **이 slice(6F-5-a) 자신의 상수 여섯**(`SELECT_REQUIREMENT_STATUS`
  등, `bidvector.adapters.persistence.Sql`에서 뽑아낸 것)만 담는다. **삭제 대상**이다(위
  ①의 신규 10개에 포함). `bidvector.adapters.persistence.Sql`의 KDoc에 이 분리를
  설명하는 문단 한 블록도 되돌려야 하는데, **위 ②(기준 `edeaa9a3`)가 `Sql.kt`를
  통째로 복원하므로 자동으로 걷힌다** — 별도 수동 편집이 필요 없다(D-6F5-29 보정 뒤
  정정). 이 구현 레인이 직접 대조한 `diff <(git show edeaa9a3:.../Sql.kt)
  adapters/.../Sql.kt`가 `main`(6F-6 반영판, `edeaa9a3`) 대비 `Sql.kt`의 차이는 **그
  KDoc 문단 하나뿐**임을 확인했다 — 그 밖의 코드(6F-6의 `PROFILE_*` 상수 포함)는 손대지
  않았다.
- **`CleanMigrationPrivilegeTest.kt`** — **이 slice 이전부터 있던 축9**(유효 권한 행렬,
  M3/3G)를 옮겨 담은 파일이다. 이 slice가 새로 지은 test가 아니다 — 단언·기대 행렬·주석
  근거를 축어 그대로 옮겼을 뿐이다(구현 레인 보고: diff로 값 변경 0 확인). **삭제
  대상이다**(위 ①의 신규 10개에 포함) — 삭제만으로는 축9가 사라지지만, **위 ②(기준
  `edeaa9a3`)가 `CleanMigrationTest.kt`를 축9가 아직 안 분리된 `edeaa9a3` 상태로
  복원하므로 축9가 자동으로 돌아온다**. `gate-tests.properties`의
  `CleanMigrationPrivilegeTest` 등재도 그 파일이 `edeaa9a3`에 없어 같은 ②의 복원이
  걷어낸다. **정정(D-6F5-29)** — 이 문단이 처음 쓰였을 때(계약 갱신 (6))는 「도로
  손으로 넣어야 한다」고 적었다. 그것은 §①②가 아직 옛 기준(`ede5d5b`)이던 전제
  였는데, 그 전제 자체가 **HIGH였다**(아래) — 보정된 절차에서는 수동 재삽입이
  필요 없다.

**HIGH였던 것 — 이 절이 처음 쓰였을 때(계약 갱신 (6)) §①②는 여전히 옛 기준
(`ede5d5b`)이었다.** 그 상태로 §①②를 실행하면 6F-6의 병합된 줄이 **조용히 사라진다**
(verifier r6 실측, D-6F5-29: `PROFILE_*` 3→0·게이트 등재 `adapters.profile` 4→0·
TRUNCATE의 `operator_profile` 2→0, exit 0·stderr 없음 — `ede5d5b`가 6F-6 이전이기
때문이다). 이 절은 「모양이 바뀌었다」·「재실측 안 한다」만 적고 **「지금 §①②를
실행하지 마라」를 적지 않았다** — 파괴적 명령이 문서에서 이 절보다 **위**에 있어
사고 때 위에서부터 그대로 실행된다. r2 MEDIUM-1과 같은 조용한 실패 양식인데, 그때는
결과 트리가 옳았고 이번엔 틀렸다. **§①②는 위에서 이미 기준 `edeaa9a3`로 교체됐고
round 5 표에서 6F-6 줄 생존·이 slice 줄 소멸·트리 동일성 6/6·전건 `check`를 재측정해
닫았다** — 이 정정은 D-6F5-29·D-6F5-30(계약 갱신 (7))에서 나왔다.

**「재실측은 하지 않는다 / 이것은 더 이상 이 slice의 rollback이 아니다」는 이 절이
처음 낸 결론이었고, 그 결론도 정정됐다(D-6F5-30).** 되돌림 대상이 6F-5-a·6F-6 두
레인에 걸치는 것은 사실이지만, **그렇다고 이 slice만의 in_scope 경로를 골라 되돌리는
절차가 성립하지 않는 것은 아니다** — 복원 기준을 고정 SHA(`ede5d5b`)가 아니라 「이
브랜치가 분기해 나온 현재 `main`」(`edeaa9a3`, `git merge-base HEAD origin/main`으로
산출)으로 바꾸면, 그 기준 자체가 이미 6F-6을 담고 있어 이 slice만의 기여를 골라
되돌릴 수 있다(round 5 표가 그 실측이다). **`main`의 병합 커밋(`edeaa9a3`) 자체를
되돌리는 것**(V12/PR #38 전체를 `main`에서 되돌리는 것)은 여전히 이 slice의 rollback
범위 밖이다 — 그러나 그것과 「이 slice가 얹은 것만 걷어내는 것」은 다른 일이고, 후자는
위 절차로 여전히 가능하다.
