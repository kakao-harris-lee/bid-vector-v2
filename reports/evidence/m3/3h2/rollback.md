# M3/3H-2 rollback — Kotlin 요청·표본 축 조립 부분

범위: 이 문서는 `kotlin-implementer` 레인이 만진 8개 Kotlin 경로 한정이다(D-3H2-3의 Python
엔진 변경은 별도 레인 소관 — 이 문서가 다루지 않는다). base `e101a0c`(3H-1 PR #30 머지).

## 대상 경로(기계 산출)

`git diff --name-status e101a0c..HEAD -- <아래 8개 경로>`로 낸 목록 — 전부 `M`(수정, 신규
파일 없음), 다른 slice/레인과 겹치는 공유 파일이 아니다(각 파일의 `git log --oneline
e101a0c..HEAD -- <파일>`이 이 레인의 커밋만 보인다 — hunk 격리 불요, base..HEAD 전체 restore로
충분).

- `adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt`
- `adapters/src/test/kotlin/bidvector/adapters/ml/RequestMappingTest.kt`
- `workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt`
- `workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleConversion.kt`
- `workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisFixtures.kt`
- `workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt`
- `workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt`
- `workflow/src/test/kotlin/bidvector/workflow/evaluation/SampleEligibilityTest.kt`

**라운드마다 재실행 규율**: 위 8개 경로 목록이 늘면(추가 수정 라운드) 이 명령을 다시 돌려
목록을 갱신한다 — 손으로 추가하지 않는다.

## 복구 명령

```bash
git restore --source=e101a0c --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/RequestMappingTest.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleConversion.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisFixtures.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/SampleEligibilityTest.kt
```

하네스 경로(`CLAUDE.md`·`.claude/**`)는 이 range에 없다(scope.md 「하네스 레인 변경」 절 —
없음) — 되돌릴 대상 자체가 없다.

## 임시 clone 실측(`029ccdb6.../scratchpad/3h2-rollback-clone`, `--no-hardlinks`, HEAD `e866be3`)

① 위 복구 명령 — exit 0.
② `git diff --name-status e101a0c -- <같은 8개 경로>` — 빈 결과(exit 0, 출력 0줄) — 복구 후
   base와 바이트 동일.
③ (신규 파일이 없어 별도 `git rm` 불필요 — `M` 8건뿐, `A` 없음.)
④ `./gradlew --offline --no-daemon --no-build-cache :workflow:compileTestKotlin
   :adapters:compileTestKotlin` — exit 0.
⑤ `./gradlew --offline --no-daemon --no-build-cache :workflow:test :adapters:test` — exit 0
   (되돌린 트리가 3H-1 시점 test 로 통과 — 이 slice의 신설 test 도 함께 되돌아가 사라졌으므로
   RED 재현이 아니라 "3H-1 상태로 되돌아간 것 자체가 일관됨"을 확인).
⑥ `./gradlew --no-build-cache --no-daemon clean check`(되돌린 트리 전건) — 결과는
   commands.md의 이 rollback 절 실측 한 줄로 기록(게이트 결과는 evidence 문서 자기 낡음을
   피하려 commands.md에 남긴다).

## 알려진 제한

- 표본 축 결측 사유 확장(D-3H2-3, Python `distribution.py`)은 이 문서의 되돌림 범위 밖이다
  — Kotlin만 되돌리면 요청·표본 wire가 다시 `agencyId = null`/`NOT_COLLECTED_YET`을 내므로
  엔진이 여전히 `{NOT_COLLECTED_YET, UNKNOWN}`을 허용해도 실질적으로 `UNKNOWN`을 받을 일이
  없다 — 엔진 쪽만 되돌리지 않아도 회귀 위험이 없다(닫힌 집합이 넓어졌을 뿐 좁아지지 않았다).
