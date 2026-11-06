# M9/ml-lifecycle — rollback

**전제**: 이 slice 는 아직 main 에 **없다**(머지 전). 머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 revert 다.

**복원 원천은 `56bd33ea`** — 이 브랜치가 **마지막으로 병합한 main 커밋**(PR #137, 승인 전 일괄 라운드에서 병합)이다.
고정 기재이고 움직이는 `origin/main` 을 쓰지 않는다. 착수 base `df4976cc` 는 더 이상 원천이 아니다 — 그
사이 main 이 89 커밋 움직였고, `df4976cc` 에서 복원하면 **남의 줄을 떨어뜨린다**(`capability-map.md` 는 이
range 에서 다른 레인 넷이 114 줄을 더했다).

전제 둘을 단언했다 — ① `56bd33ea` 는 HEAD 의 **조상**이다(`git merge-base --is-ancestor` exit 0)
② 이 slice 의 **첫 산출물 커밋이 그 SHA 에 없다**(`ffa93b5b` 에 대해 `--is-ancestor` 비-0). ② 가 성립하므로
그 SHA 에서의 전체 복원이 남의 줄을 떨어뜨릴 수 없고 커밋 해시 hunk 격리가 필요 없다.

**공유 파일에 hunk 격리가 필요 없는 근거**: 다른 slice 도 만지는 파일은 `docs/discovery/capability-map.md` 와
**다른 slice 의 정책 값 장부 둘**(`reports/evidence/m5/5c2/policy-values.md` ·
`reports/evidence/m5/5d/policy-values.md` — cr M-4 · v r2 L-2 로 in_scope 확장 승인, scope.md 「계약 갱신
2026-11-06」)이다. `HEAD ^56bd33ea` 에서 그 셋을 만진 **비-머지** 커밋은 전부 이 slice 의 것이고
(capability-map 셋 · 장부 각 둘), 넷째 행은 **이 브랜치의 main 병합 커밋**이다. 편집은 **덧붙이기**이며,
복원 원천이 main 측 내용을 그대로 담으므로 전체 복원이 남의 줄을 떨어뜨릴 수 없다.

**하네스 경로**(`CLAUDE.md` · `.claude/**`)는 되돌리지 않는다 — 같은 range 의 비-머지 로그가 **빈 출력**
(이 slice 에 하네스 레인 커밋 0).

**fixture·6G 판정은 건드리지 않았다** — `git diff --name-only 56bd33ea..HEAD` 에 `fixtures/` ·
`reports/evidence/m6/` · `evaluation/backtest/**` · `strategy-backtest-v2.yaml` 이 **0 건**이다(golden *test*
파일 하나는 호출 형태만 따라갔고 기대값 JSON 은 무편집).

## 복원 명령 (in_scope 경로 한정 · 경로는 개별 인자)

```bash
git restore --source=56bd33ea --staged --worktree -- \
  docs/discovery/capability-map.md \
  ml-engine/policy/evaluation-v1.yaml \
  ml-engine/policy/inference-v1.yaml \
  ml-engine/src/ml_engine/app/pipeline.py \
  ml-engine/src/ml_engine/app/server.py \
  ml-engine/src/ml_engine/evaluation/__init__.py \
  ml-engine/src/ml_engine/evaluation/policy.py \
  ml-engine/src/ml_engine/evaluation/report.py \
  ml-engine/src/ml_engine/inference/__init__.py \
  ml-engine/src/ml_engine/inference/maturity.py \
  ml-engine/src/ml_engine/inference/policy.py \
  ml-engine/src/ml_engine/registry/policy.py \
  ml-engine/src/ml_engine/training/_holdout_window.py \
  ml-engine/src/ml_engine/training/artifact_writer.py \
  ml-engine/src/ml_engine/training/holdout.py \
  ml-engine/src/ml_engine/training/jobs/pipeline.py \
  ml-engine/src/ml_engine/training/release.py \
  ml-engine/tests/app/test_pipeline.py \
  ml-engine/tests/app/test_server.py \
  ml-engine/tests/app/test_server_prediction.py \
  ml-engine/tests/evaluation/test_baselines.py \
  ml-engine/tests/evaluation/test_evaluation_policy.py \
  ml-engine/tests/evaluation/test_public_signatures.py \
  ml-engine/tests/evaluation/test_report.py \
  ml-engine/tests/evaluation/test_segments.py \
  ml-engine/tests/evaluation/test_verdict.py \
  ml-engine/tests/evaluation/test_windows.py \
  ml-engine/tests/gates/test_policy_loader.py \
  ml-engine/tests/inference/golden/test_kernel_golden.py \
  ml-engine/tests/inference/test_maturity.py \
  ml-engine/tests/serving/test_prediction.py \
  ml-engine/tests/serving/test_runtime.py \
  ml-engine/tests/training/test_holdout.py \
  ml-engine/tests/training/test_residual_release.py \
  ml-engine/tests/training/test_train_artifact.py \
  reports/evidence/m5/5c2/policy-values.md \
  reports/evidence/m5/5d/policy-values.md
```

**신규 파일은 복원이 아니라 삭제다**(`56bd33ea` 에 없으므로 `restore` 가 받지 않는다) — **11** 이다.

```bash
git rm -f \
  ml-engine/src/ml_engine/evaluation/epoch.py \
  ml-engine/src/ml_engine/evaluation/policy_source.py \
  ml-engine/src/ml_engine/inference/time_axis.py \
  ml-engine/src/ml_engine/registry/policy_approvals.py \
  ml-engine/tests/ast_sites.py \
  ml-engine/tests/evaluation/test_epoch.py \
  ml-engine/tests/evaluation/test_gate_relaxation_paths.py \
  ml-engine/tests/inference/test_time_axis.py \
  ml-engine/tests/policy_fixture.py \
  ml-engine/tests/registry/test_policy_approvals.py \
  ml-engine/tests/training/test_release_identity.py
```

evidence 디렉터리는 **목록 밖**이다(장부층 — 그래서 이 목록이 자기를 담은 커밋을 가리키지 않는다).
함께 지우려면 `git rm -r reports/evidence/m9/ml-lifecycle`.

목록은 **기계 산출**이다 — `git log --no-merges --format= --name-only HEAD ^56bd33ea | sort -u` 에서 이
slice 의 evidence 디렉터리를 뺀 뒤, 각 경로가 원천에 있는지(`git cat-file -e`)로 `M`/`A` 를 갈랐다
(`M` **37** · `A` **11**). 라운드가 늘면 다시 산출한다.

## 되돌린 트리에서 무엇이 꺼지는가

- **승격 인용이 사라진다**: 평가 창의 판정이 정책 effective date 를 묻지 않게 되고, `Promotion` 이 세 값으로
  돌아간다(`PromotionSuperseded` 가 사라진다). report 의 `policy_effective_date` 칸도 사라져 report checksum 이
  달라진다 — 되돌린 뒤 새로 낸 report 는 되돌림 **전** report 와 checksum 이 다르다(값이 사라진 만큼).
- **시간축 provenance 가 사라진다**: 관측이 `datetime` 을 다시 들고, 개찰 시각을 모르는 행을 표현할 자리가
  없어진다(그런 행은 애초에 만들 수 없게 되므로 **조용히 사라지는 행이 생기는 것은 아니다** — 결측을 들고
  올 경로 자체가 없어진다). 모집단 항등식과 대체 제외 표, `maturity.time_axis_substitute` 칸도 함께
  사라지고, 그 칸이 사라지므로 출하 정책 파일은 되돌린 로더의 `known_keys` 전수와 다시 일치한다.
- **model_version 축이 다섯으로 줄어든다**: `sample_scope` 가 해시 입력에서 빠져, 범위만 다른 두 아티팩트가
  같은 `release_id` 를 가질 수 있는 상태로 돌아간다. **이미 쓰인 아티팩트의 식별자가 바뀐다** — 되돌림 전에
  학습한 아티팩트를 되돌린 코드로 재파생하면 다른 id 가 나온다(운영 반입이 보류 상태라 영향 범위는 저장소
  안이다).
- **정책 출처 재검증이 사라진다**(수정 라운드): `EvaluationPolicy` 가 생성 토큰 없이 조립 가능해지고, 제품
  경로가 정책 파일을 다시 읽지 않는다 — `dataclasses.replace` 류로 임계를 비튼 정책이 다시 게이트를 돌 수
  있는 상태로 돌아간다. 정책 파일의 `effective_date` 칸과 `maturity.time_axis_substitute` 칸도 함께 사라져
  되돌린 로더의 `known_keys` 전수와 다시 일치한다.
- **운영 영향 0**: ML 앱 배선과 수집 연장이 사용자 결정으로 보류 상태이고, 이 레인은 운영 host·DB·외부 API 에
  닿지 않았다. 되돌림은 저장소 파일만 움직인다.

예상 복구 시간: 명령 둘(초 단위).

## 실측 (버릴 clone, **실측 HEAD `9631517e`** — 이 slice 의 마지막 산출물 커밋)

**앞 라운드의 실측을 옮기지 않았다 — 이 절은 여덟째 측정이다.** 승인 전 일괄 라운드가 **원천을
다시 옮겼다**(`97ed49c4` → 이 라운드에 병합한 main `56bd33ea`). 목록은 `M` 37 · `A` 11 로
**그대로**다(r5 와 `diff` 빈 출력) — `97ed49c4..56bd33ea` 가 만진 54 파일에 `ml-engine/**` 이
**0** 이고, 겹치는 in_scope 는 `docs/discovery/capability-map.md` **하나**다(계수로 확인).
그 하나는 **합집합이 보존됐음을 계수로 확인했다**: 양쪽이 더한 줄(lane 51 · main 5) 전부 병합본에
있고, 양쪽이 지운 줄 전부 없고, 중복 비빈줄 종류가 base 와 **같다**(24 — 병합이 만든 중복 0),
충돌 표지 0. 비빈줄 등식도 닫힌다(3492 + 51 − 0 + 5 − 4 = **3544** == 실측).

| # | 확인 | 결과 |
|---|---|---|
| — | 목록 등식 | 기계 산출 `M` **37** · `A` **11**(이 slice 의 evidence 제외) · 문서 목록 **37 + 11**, `comm -3` 빈 출력 |
| ① | 복원 명령 셋 | `restore` exit **0** · `rm` exit **0** · `rm -r`(evidence) exit **0** |
| ② | `git status --porcelain` 계수 | **`M` 37 · `D` 16**(= 신규 11 + 함께 지운 evidence 다섯) |
| ③ | `git diff 56bd33ea -- .` | **빈 출력** |
| ④⑤ | 컴파일·test 축 | **트리 동일성으로 갈음한다**: 복원 뒤 `write-tree` == 원천 트리 해시(`00016987…` 양쪽) — 되돌린 트리는 `56bd33ea` 와 **같은 트리**이고 그것은 CI 가 통과한 main 이다 |
| ⑥ | 이 slice 가 닿는 게이트 | 아래 「⑥ 게이트」 |

### 「내 표지 0 · 남의 표지 보존」 계수

양성 대조와 함께 센다. 라운드 3 의 새 표지 둘을 더했다.

| 표지 | 복원 뒤 | 레인 worktree(양성 대조) |
|---|---|---|
| `policy_approvals` · `load_approved_evaluation_policy` | 0 · 0 | 양수 |
| `load_approved_inference_policy` (라운드 3) | **0** | 8 파일 |
| `_check_exclusion_denominators` · `_flatten_annotation` · `_POLICY_ENTRY_EXCEPTIONS` (라운드 4) | **0** · **0** · **0** | 2 · 2 · 2 파일 |
| `approved_policy`(게이트 배선, 라운드 4) | **0** | 4 파일 |
| 재구성 승인 문면 · `_UNRESOLVABLE_ANNOTATIONS` · `_src_functions` · `_is_policy_type` (라운드 5) | **0** 넷 | 2 파일 각각 |
| `isroutine`(승인 전 일괄) | **0** | 2 파일 |
| `_check_substitute_exclusion` · `_opened_at_counts` (라운드 3) | **0** · **0** | 2 · 4 파일 |
| `policy_source_digest` | **0** | 8 파일 |
| `UnmeasurableTimeAxis` · `TimeAxisSubstitute` | **0** · 0 | 8 파일 · 양수 |
| `_window_release_id` · `PromotionSuperseded` | **0** · 0 | 4 파일 · 양수 |

**표지로 쓸 수 없는 상류 어휘 셋**(측정으로 걸러냈다): `sample_scope` · `effective_date`
(6G 백테스트가 이미 쓴다) · **`_resolve`**(승인 전 일괄에서 추가 — 복원 뒤 **14 파일**, 상류의
일반 어휘다). 같은 교훈이 세 번째다 — 표지는 이 slice 가 **새로 만든 이름**이어야 한다.

| 남의 표지(보존되어야 한다) | 복원 뒤 |
|---|---|
| `web/e2e/harness/opening-axis.mjs`(opening-harness 레인) | **있음** |
| `reports/evidence/m9/{opening-harness,ledger}/scope.md` | **있음** |
| `reports/evidence/m9/gates-tests/scope.md` · `config/quality/gate-tests.properties` | **있음** |
| `reports/evidence/m9/paper-bid/scope.md`(이번 병합분) | **있음** |
| `docs/discovery/capability-map.md` 줄수 | **원천과 같다** |

## ⑥ 게이트

복원으로 트리가 원천과 **바이트 동일**해지므로 ml-engine job 의 열한 단계 입력이 전부 원천과
같다 — 그 트리는 CI 가 통과한 main 이다. 이 slice 의 evidence 디렉터리를 **남겨 둘 경우**
유일한 차이가 그 디렉터리이고, 그것을 입력으로 삼는 게이트는 Kotlin `check` job 의 누출 패턴
게이트 하나다. 이 레인은 Gradle 을 돌리지 않으므로(M9 공통 규율) 그 축은 **PR CI 가 정본**
이고, 같은 어휘의 참조형 스캔만 로컬에서 쟀다.

| 명령 | 결과 |
|---|---|
| 참조형 스캔 `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m9/ml-lifecycle/` | **매치 0**(exit 1) |

## 유효성 확인 (감사자용)

```bash
git diff --name-only 9631517e..<판정 SHA> -- <위 복원 목록 37 + 신규 11>
```

빈 출력이면 실측 뒤 되돌림 대상이 움직이지 않았다는 뜻이고 이 실측이 유효하다. 한 줄이라도
나오면 미검증이다. 이 slice 는 실측 뒤 **evidence 만** 커밋했으므로 그 출력이 비어 있어야 한다 — **판정 SHA
`9631517e`(= 실측 HEAD)** 와 evidence HEAD 사이의 그 diff 가 **빈 출력**임을 확인했다.
