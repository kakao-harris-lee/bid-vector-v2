# M4/4B-8 rollback

착수 경계(base_sha) = `845e29b`(scope.md). `git diff --name-only 845e29b..dfaff1a`가
문서 4건(`docs/discovery/capability-map.md`·`docs/discovery/data-dictionary.md`·
`milestone-4.md`·`reports/evidence/m4/4b8/scope.md`)만 내 — 팀장 착수 커밋 둘
(`c4ee7e0`·`dfaff1a`)은 in_scope 코드 경로를 건드리지 않는다. `git log --oneline
845e29b..HEAD -- procurement adapters workflow app config/quality/gate-tests.properties`가
이 slice의 구현 커밋 둘(`b3131ee`·`f528bdb`)만 내 — 같은 브랜치(`m4-4b8/2026-09-16`)가
전용이고 base 이후 다른 slice가 이 파일들을 만진 이력이 없다. 그래서 커밋 해시별 hunk
격리 없이 base 대조 단일 역적용으로 충분하다(2026-09-16 규칙).

되돌리면 `CategoryCode`가 다시 정규화 없는 `data class`(공개 생성자, `"A01"`≠`"a01"`)로
복귀하고, `predictionRequestFor`의 `baseAmountProvenanceLabel`은 다시
`BaseAmountProvenance.Unknown` 고정으로 돌아간다(`OPEN-4B7-TARGET-LABEL`·
`OPEN-4B7-CATEGORY-NORMALIZATION` 재개방) — 4B-7 종결 시점 상태로 복귀.

## 목록(기계 산출)

```
git diff --name-status 845e29b..HEAD -- procurement adapters workflow app config/quality/gate-tests.properties \
  | awk '{print $2}' > /tmp/4b8-rollback-files.txt
```

| 상태 | 경로 |
| --- | --- |
| M | `adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt` |
| M | `adapters/src/test/kotlin/bidvector/adapters/ml/JdbcCompetitionSampleSourceTest.kt` |
| M | `adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeFindRoundTripTest.kt` |
| M | `app/src/test/kotlin/bidvector/app/conformance/KonepsCollectionExecutors.kt` |
| M | `config/quality/gate-tests.properties` |
| M | `procurement/src/main/kotlin/bidvector/procurement/BusinessCategory.kt` |
| M | `procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt` |
| A(삭제 대상) | `procurement/src/test/kotlin/bidvector/procurement/BusinessCategoryTest.kt` |
| M | `procurement/src/test/kotlin/bidvector/procurement/CanonicalizeTest.kt` |
| M | `workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysis.kt` |
| M | `workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt` |
| M | `workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleConversion.kt` |
| M | `workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisFixtures.kt` |
| M | `workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt` |
| M | `workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt` |
| M | `workflow/src/test/kotlin/bidvector/workflow/evaluation/SampleEligibilityTest.kt` |

**라운드마다 파일이 늘면 위 `git diff --name-status` 명령을 다시 돌려 목록을 갱신한다** —
목록을 손으로 유지하지 않는다. `JdbcCompetitionSampleSource.kt`·`Sql.kt`는 이 slice의
in_scope 밖이라(D-4B8-4·우회 (4) — SQL·조회 어댑터 무변경) 목록에 없다 — `git diff --stat
845e29b..HEAD -- adapters/src/main/kotlin/bidvector/adapters/ml/JdbcCompetitionSampleSource.kt
adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt`가 빈 출력을 낸다(기계
확인, commands.md).

## 역적용 절차(임시 clone 실측, ①~⑥)

① 목록 추출: 위 명령.
② 경로를 개별 인자로 넘긴다(변수 하나로 묶지 않는다 — pathspec 함정):
```
cat /tmp/4b8-rollback-files.txt | xargs git restore --source=845e29b --staged --worktree --
```
③ 대조 — 「내 줄이 사라졌다」:
```
git diff --name-status 845e29b -- $(cat /tmp/4b8-rollback-files.txt)
```
출력이 비어야 한다(A 파일은 삭제, M 파일은 base와 바이트 동일).
④ 되돌린 트리에서 모듈별 compile:
```
./gradlew --no-daemon --no-build-cache :procurement:compileKotlin :procurement:compileTestKotlin \
  :adapters:compileKotlin :adapters:compileTestKotlin :workflow:compileKotlin :workflow:compileTestKotlin \
  :app:compileTestKotlin
```
⑤ 되돌린 트리의 test는 ④와 같은 명령 뒤 자연히 포함되지 않으므로 ⑥ 전건으로 대체한다
(이 slice가 닿은 게이트가 사실상 전건이라 부분 test 대신 ⑥을 정본으로 삼는다).
⑥ 되돌린 트리에서 **루트 `check` 전건**(2026-09-12 규칙 — rollback 확인은 게이트 단계를
반드시 포함한다):
```
./gradlew --no-build-cache --no-daemon clean check
```

## 실측 결과(2026-09-16, 임시 clone `--no-hardlinks`, HEAD `f528bdb`)

| 단계 | 명령 | exit |
| --- | --- | --- |
| ① 목록 추출 | 위 명령 | 0(16줄) |
| ② 역적용 | `xargs git restore --source=845e29b --staged --worktree --` | 0 |
| ③ 대조 | `git diff --name-status 845e29b -- <목록>` | 0(출력 없음) |
| ④ compile | 3모듈+app test compile(7 task) | 0(BUILD SUCCESSFUL) |
| ⑥ 게이트 전건 | `clean check` | 0(BUILD SUCCESSFUL) — commands.md에 세부 |

## 공유 파일

`config/quality/gate-tests.properties`는 여러 slice가 상시 공유하지만, 이 slice의 두
커밋(`git log --oneline 845e29b..HEAD -- config/quality/gate-tests.properties`)만 그
파일을 만졌다(추가한 것은 `BusinessCategoryTest` 한 줄 + 설명 주석 한 단락) — base 시점
이후 다른 브랜치 병합이 이 파일에 아직 반영되지 않은 상태(브랜치 전용, 병합 전)라 hunk
격리가 필요 없다. **병합 전 상태에서만 유효한 가정이다** — main 병합 뒤 다른 slice의
등재가 사이에 낀 채로 이 문서의 역적용을 그대로 실행하면 그 slice의 줄도 함께 사라질 수
있다. 그 시점에는 이 문서의 목록을 2026-09-09/2026-09-16 hunk 격리 규약(커밋 해시별
`git diff <sha>~1..<sha> | git apply -R`)으로 다시 좁혀야 한다.

**되돌리지 않는 공유 승인 문서** — 팀장이 `scope.md`(계약 고정 커밋 `c4ee7e0`)·
`milestone-4.md`·`docs/discovery/capability-map.md`·`docs/discovery/data-dictionary.md`
(§6.3.1, `dfaff1a`)를 이 slice 착수 전에 이미 갱신했다. 이 rollback 절차는 그 문서들을
**되돌리지 않는다**(목록에 없음, in_scope 코드 경로만 대상) — 되돌린 트리에서는 그
문서들이 여전히 "정규화 정본이 `CategoryCode.of`" 를 서술하는데 코드는 base(정규화 없음)로
돌아가 있다는 뜻이다. 게이트는 이 어긋남을 잡지 못한다 — rollback을 실행하는 쪽이 그
문서들도 함께 되돌릴지 별도로 판단해야 한다(4B-7 rollback.md의 같은 판정과 동일한 성격,
등재로 족한다).
