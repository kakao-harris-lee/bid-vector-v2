# M3/3H-2 rollback — 발주기관 요청·표본 축 조립 + 표본 축 결측 사유

base `e101a0c`(3H-1 PR #30 머지, 3H-2의 착수 커밋). 이 문서는 in_scope 전체(Kotlin·계약
주석·Python·공유 문서)를 덮는다 — verifier r1 F-3·F-4 시정.

## 대상 경로(기계 산출, `git diff --name-status e101a0c..HEAD`)

라운드마다 이 명령을 다시 돌려 아래 세 그룹을 갱신한다 — 손으로 추가하지 않는다.

### 그룹 A — Kotlin·계약 주석(전체 restore, hunk 격리 불요)

각 파일의 `git log --oneline e101a0c..HEAD -- <파일>`이 이 slice 자신의 커밋만 보인다 —
다른 slice/레인과 겹치는 줄이 없어 `base..HEAD` 전체 restore로 충분하다.

- `adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt`
- `adapters/src/test/kotlin/bidvector/adapters/ml/RequestMappingTest.kt`
- `contracts/proto/bidvector/ml/v1/features.proto`
- `workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt`
- `workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleConversion.kt`
- `workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionRequest.kt`
- `workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisFixtures.kt`
- `workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt`
- `workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt`
- `workflow/src/test/kotlin/bidvector/workflow/evaluation/SampleEligibilityTest.kt`

### 그룹 B — Python 엔진(`ml-implementer` 레인 산출물, 전체 restore, hunk 격리 불요)

같은 이유(각 파일이 이 slice 커밋만 가짐)로 `base..HEAD` 전체 restore.

- `ml-engine/src/ml_engine/inference/distribution.py`
- `ml-engine/src/ml_engine/inference/observations.py`
- `ml-engine/src/ml_engine/features/facts.py`
- `ml-engine/tests/inference/test_distribution.py`

### 그룹 C — 공유 문서(hunk 격리 — 프로그램 전역 문서라 다른 slice가 같은 파일을 계속 쓴다)

각 파일이 이 range 안에서는 아래 단일 커밋 하나에만 걸려 있다(착수 이후 이 slice만 만졌다) —
`git diff <sha>~1..<sha> -- <파일> | git apply -R` 한 번으로 그 hunk만 걷는다. `base..HEAD`
전체 restore를 쓰지 않는 이유는 이 파일들이 3H-2 종료 뒤에도 다른 slice가 계속 이어 쓰므로,
경계를 커밋 단위로 명시해 두지 않으면 다음 라운드의 판별 근거가 없어지기 때문이다.

| 파일 | 걸린 커밋 |
| --- | --- |
| `milestone-3.md` | `7696cb4` |
| `docs/discovery/capability-map.md` | `3d05f78` |
| `docs/discovery/data-dictionary.md` | `3d05f78` |
| `reports/evidence/m5/5d3/scope.md` | `4da12ea` |

### 되돌리지 않는 것

- **하네스 경로** — `.claude/skills/codex-review-gate/SKILL.md`(`ff061e2`) · `.gitignore` +
  `config/` 아래 예시 환경 파일 하나(`5b362b7`, 운영자 결정 — 하네스는 아니나 slice 산출물도
  아니다) · `docs/harness/change-history.md`(`7a230bb`). scope.md 「하네스 레인 변경」 절과
  일치(2026-09-17 갱신 — 이전 판(`2542e32` 시점)의 「없음」은 그 뒤 `f10baba`가 이 절을 채운
  다음부터 낡아 있었다, F-4).
- **`reports/evidence/m3/3h2/**`** — evidence 자신(이 문서 포함)은 신규 wiring이 아니라
  기록이다. 되돌리면 이 rollback 절차의 근거 자체가 사라진다.

## 복구 명령

```bash
# 그룹 A + B — 전체 restore
git restore --source=e101a0c --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/RequestMappingTest.kt \
  contracts/proto/bidvector/ml/v1/features.proto \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleConversion.kt \
  workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionRequest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisFixtures.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/SampleEligibilityTest.kt \
  ml-engine/src/ml_engine/inference/distribution.py \
  ml-engine/src/ml_engine/inference/observations.py \
  ml-engine/src/ml_engine/features/facts.py \
  ml-engine/tests/inference/test_distribution.py

# 그룹 C — hunk 격리(파일마다 그 커밋 하나만)
git diff 7696cb4~1..7696cb4 -- milestone-3.md | git apply -R
git diff 3d05f78~1..3d05f78 -- docs/discovery/capability-map.md docs/discovery/data-dictionary.md | git apply -R
git diff 4da12ea~1..4da12ea -- reports/evidence/m5/5d3/scope.md | git apply -R
```

수동 해소가 필요해지면(다음 라운드가 그룹 C 파일의 같은 삽입 지점 근처를 또 편집해
`git apply -R`이 conflict marker를 내면): 그 파일을 열어 위 커밋 diff의 `+`행 블록만 정확히
지우고, 그 앞뒤에 남아야 할 다른 slice의 내용은 그대로 둔다. 지울 블록의 경계는 각 diff의
첫 `+`행과 마지막 `+`행이다(위 명령 출력을 손으로 대조).

## 임시 clone 실측(`029ccdb6.../scratchpad/3h2-rollback-clone-r1`, `--no-hardlinks`)

① 그룹 A+B 복구 명령 — 실측 결과는 commands.md의 이 rollback 절에 한 줄.
② 그룹 C hunk 격리 명령 셋 — 실측 결과·conflict 유무를 commands.md에 한 줄.
③ `git diff --name-status e101a0c -- <그룹 A+B 14개 경로>` — 빈 결과(바이트 동일) 확인.
   (신규 파일 없음 — 그룹 A+B·C 전부 `M` — 별도 `git rm` 불필요.)
④ 그룹 C 파일마다 두 가지를 확인: 「내 줄이 사라졌다」(그 커밋의 `+`행이 파일에 없음, grep으로
   대조) **와** 「남의 줄이 남았다」(그 파일의 다른 절 — 예: `milestone-3.md`의 3H-1 종결
   문단, `capability-map.md`의 다른 `OPEN-*` 행 — 이 그대로 있음).
⑤ `./gradlew --offline --no-daemon --no-build-cache :workflow:compileTestKotlin
   :adapters:compileTestKotlin` — exit 0.
⑥ `./gradlew --offline --no-daemon --no-build-cache :workflow:test :adapters:test` — exit 0.
⑦ `cd ml-engine && uv run pytest -q` — exit 0(되돌린 Python이 3H-1 시점 test와 정합). 이
   저장소는 `uv run pytest`가 `dev`·`serving`·`training` extras 를 자동 설치하지 않는다 —
   임시 clone 처럼 `.venv` 가 없는 환경에서는 먼저 `uv sync --extra dev --extra serving
   --extra training` 을 돌린다(회귀 아님 — 원래 worktree 는 이미 설치돼 있어 겪지 않는다).
⑧ `./gradlew --no-build-cache --no-daemon clean check`(되돌린 트리 전건) — exit 0. **주의**:
   그룹 A 의 `contracts/proto/bidvector/ml/v1/features.proto` 를 되돌린 뒤 **커밋하지 않고**
   이 명령을 돌리면 `contractGate` 가 「작업 트리가 비어있지 않다(ml-contract, contracts)」로
   실패한다 — 이 게이트는 `contracts`/`ml-contract` 경로에 **커밋되지 않은 diff 가 있으면
   방향과 무관하게** 위반으로 본다(원복이라도 예외 없음). 실제 rollback 실행 시에는 ①~④를
   확인한 뒤 그 상태를 별도 커밋(`git commit`)으로 고정하고 나서 ⑤~⑧을 돈다 — 이 문서의
   임시 clone 실측도 그렇게 했다.

결과는 commands.md의 rollback 실측 절에 기록한다(게이트 결과를 이 문서 자신에 적으면 evidence
편집 자기참조가 생긴다 — evidence-pack 2026-09-16 규약).

## 알려진 제한

- 표본 축 결측 사유 확장(D-3H2-3, `distribution.py` 등 그룹 B)을 이제 이 문서가 되돌린다 —
  이전 판(F-3 시정 전)은 Kotlin만 되돌리고 Python을 남겨 「닫힌 집합이 넓어졌을 뿐 좁아지지
  않아 무해하다」는 논거로 대신했는데, 지금은 그룹 B도 명시 절차·실측이 있어 그 논거가 더는
  필요하지 않다.
- 그룹 C의 단일 커밋 hunk 격리는 그 파일에 3H-2 이후 다른 slice의 커밋이 더 붙으면(예:
  `capability-map.md`를 다른 M3/M5 slice가 다시 편집) 더는 안전하지 않다 — 그때는 hunk가
  conflict를 내고, 위 「수동 해소」 절차로 넘어간다.
