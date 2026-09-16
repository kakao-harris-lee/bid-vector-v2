# M4/4B-7 rollback

착수 경계(base_sha) = `8799e0594496593521691a24a29551ee51b6f147`. `git log --oneline
8799e05..HEAD -- CLAUDE.md .claude/`는 빈 목록 — 이 slice의 in_scope 경로에 하네스 레인
변경이 섞이지 않았다. 다른 slice가 이 in_scope 파일들을 base 이후 만진 이력도 없다(같은
브랜치 `m4-4b7/2026-09-16`가 전용, `git log --oneline <base>..HEAD`가 이 slice의 두
커밋만 냄) — 그래서 커밋 해시별 hunk 격리 없이 base 대조 단일 역적용으로 충분하다.

되돌리면 요청 표본 0건(4D-3 이전 상태)으로 복귀한다 — `CompetitionSample.reserveDraw`가
없어지고, `predictionRequestFor`의 `competitionSamples`는 다시 `emptyList()`로 고정되며,
`OpportunityAnalysis`는 표본 조회 축(`CompetitionSamplePort`)을 잃는다.

## 목록(기계 산출)

```
git diff --name-status 8799e0594496593521691a24a29551ee51b6f147..HEAD -- workflow adapters config/quality/gate-tests.properties
```

| 상태 | 경로 |
| --- | --- |
| A(삭제 대상) | `adapters/src/main/kotlin/bidvector/adapters/ml/JdbcCompetitionSampleSource.kt` |
| M(base로 restore) | `adapters/src/main/kotlin/bidvector/adapters/ml/MoneyMapping.kt` |
| M | `adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt` |
| A | `adapters/src/test/kotlin/bidvector/adapters/ml/JdbcCompetitionSampleSourceTest.kt` |
| A | `adapters/src/test/kotlin/bidvector/adapters/ml/RequestMappingTest.kt` |
| M | `config/quality/gate-tests.properties` |
| M | `workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysis.kt` |
| M | `workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityPolicyData.kt` |
| M | `workflow/src/main/kotlin/bidvector/workflow/evaluation/Ports.kt` |
| M | `workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt` |
| A | `workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleConversion.kt` |
| A | `workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleEligibility.kt` |
| M | `workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionRequest.kt` |
| M | `workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisFixtures.kt` |
| M | `workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt` |
| M | `workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityPolicyDataTest.kt` |
| A | `workflow/src/test/kotlin/bidvector/workflow/evaluation/SampleEligibilityTest.kt` |
| M | `workflow/src/test/kotlin/bidvector/workflow/evaluation/TextSynthesisTest.kt` |
| M | `workflow/src/test/kotlin/bidvector/workflow/prediction/PredictionValueTest.kt` |

**라운드마다 파일이 늘면 위 `git diff --name-status` 명령을 다시 돌려 목록을 갱신한다** —
목록을 손으로 유지하지 않는다.

## 역적용 절차(임시 clone 실측, ①~⑥)

① 목록 추출:
```
git diff --name-status <base>..HEAD -- workflow adapters config/quality/gate-tests.properties \
  | awk '{print $2}' > /tmp/4b7-rollback-files.txt
```
② 경로를 개별 인자로 넘긴다(변수 하나로 묶지 않는다 — pathspec 함정):
```
cat /tmp/4b7-rollback-files.txt | xargs git restore --source=<base> --staged --worktree --
```
③ 대조 — 「내 줄이 사라졌다」:
```
git diff --name-status <base> -- $(cat /tmp/4b7-rollback-files.txt)
```
출력이 비어야 한다(A 파일은 삭제, M 파일은 base와 바이트 동일).
④ 되돌린 트리에서 모듈별 compile:
```
./gradlew --no-daemon --no-build-cache :workflow:compileKotlin :workflow:compileTestKotlin \
  :adapters:compileKotlin :adapters:compileTestKotlin
```
⑤ 되돌린 트리의 test(④와 같은 명령 뒤 자연히 포함되지 않으므로 ⑥ 전건으로 대체 —
이 slice가 닿은 게이트가 사실상 전건이라 부분 test 대신 ⑥을 정본으로 삼는다).
⑥ 되돌린 트리에서 **루트 `check` 전건**:
```
./gradlew --no-build-cache --no-daemon clean check
```

## 실측 결과(2026-09-16, 임시 clone `--no-hardlinks`, 브랜치 `m4-4b7/2026-09-16` HEAD `c943fc7`)

| 단계 | 명령 | exit |
| --- | --- | --- |
| ① 목록 추출 | 위 명령 | 0(19줄) |
| ② 역적용 | `xargs git restore --source=<base> --staged --worktree --` | 0 |
| ③ 대조 | `git diff --name-status <base> -- <목록>` | 0(출력 없음) |
| ④ compile | 4모듈 compileKotlin·compileTestKotlin | 0(BUILD SUCCESSFUL) |
| ⑥ 게이트 전건 | `clean check` | 0(BUILD SUCCESSFUL, 355 tasks) |

이 slice는 `Sql.kt`(`adapters/persistence`)에 변경을 남기지 않는다 — SELECT 상수를
1라운드에 추가했다가 2라운드에서(`MlAdapterDependencyTest` 형제 패키지 import 경계
실측 뒤) `JdbcCompetitionSampleSource.kt`(`adapters.ml`)로 옮겨 base 대비 순변경이
0이다(`git diff --stat 8799e05 -- adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt`
가 빈 출력을 낸다) — 그래서 위 목록에 `Sql.kt`가 없다.

## 공유 파일

`config/quality/gate-tests.properties`는 다른 여러 slice가 상시 공유하는 파일이지만,
이 slice의 두 커밋(`git log --oneline 8799e05..HEAD -- config/quality/gate-tests.properties`)
만 그 파일을 만졌다 — base 시점 이후 다른 브랜치의 병합이 아직 이 파일에 반영되지 않은
상태(브랜치가 전용, 병합 전)라 hunk 격리가 필요 없다. **병합 전 상태에서만 유효한
가정이다** — main에 병합된 뒤 다른 slice의 등재가 사이에 낀 채로 이 문서의 역적용을
그대로 실행하면 그 slice의 줄도 함께 사라질 수 있다. 그 시점에는 이 문서의 목록을
2026-09-09/2026-09-16 hunk 격리 규약(커밋 해시별 `git diff <sha>~1..<sha> | git apply -R`)
으로 다시 좁혀야 한다.
