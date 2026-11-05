# M9/ml-lifecycle — rollback

**전제**: 이 slice 는 아직 main 에 **없다**(머지 전). 머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 revert 다.

**복원 원천은 `df4976cc`**(고정 기재 — 움직이는 `origin/main` 을 쓰지 않는다). 이 브랜치는 **main 을 한 번도
병합하지 않았다**(`git log --merges df4976cc..HEAD` **0행**) — 그래서 착수 base 가 곧 「마지막으로 병합한 main」
이고, 둘을 가려야 하는 상황이 아직 오지 않았다. **main 이 움직여 병합하게 되면 원천·목록·실측을 다시 낸다.**

전제 둘을 단언했다 — ① `df4976cc` 는 HEAD 의 **조상**이다(`git merge-base --is-ancestor` exit 0)
② 이 slice 의 **첫 커밋이 그 SHA 에 없다**(`--is-ancestor` 비-0). ② 가 성립하므로 그 SHA 에서의 전체 복원이
남의 줄을 떨어뜨릴 수 없고 커밋 해시 hunk 격리가 필요 없다.

**공유 파일에 hunk 격리가 필요 없는 근거**: 다른 slice 도 만지는 파일은 `docs/discovery/capability-map.md`
하나이고, 이 range 에서 그 파일을 만진 커밋은 **하나이며 이 slice 의 것**이다
(`git log --oneline df4976cc..HEAD -- docs/discovery/capability-map.md` 1행).

**하네스 경로**(`CLAUDE.md` · `.claude/**`)는 되돌리지 않는다 — 같은 range 의 로그가 **빈 출력**(이 slice 에
하네스 레인 커밋 0).

**fixture·6G 판정은 건드리지 않았다** — `git diff --name-only df4976cc..HEAD` 에 `fixtures/` · golden JSON ·
`reports/evidence/m6/` 가 **0 건**이다(golden *test* 파일 하나는 호출 형태만 따라갔고 기대값 JSON 은 무편집).

## 복원 명령 (in_scope 경로 한정 · 경로는 개별 인자)

```bash
git restore --source=df4976cc --staged --worktree -- \
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
  ml-engine/src/ml_engine/training/_holdout_window.py \
  ml-engine/src/ml_engine/training/artifact_writer.py \
  ml-engine/src/ml_engine/training/holdout.py \
  ml-engine/src/ml_engine/training/release.py \
  ml-engine/tests/app/test_pipeline.py \
  ml-engine/tests/evaluation/test_baselines.py \
  ml-engine/tests/evaluation/test_evaluation_policy.py \
  ml-engine/tests/evaluation/test_report.py \
  ml-engine/tests/evaluation/test_segments.py \
  ml-engine/tests/evaluation/test_verdict.py \
  ml-engine/tests/evaluation/test_windows.py \
  ml-engine/tests/inference/golden/test_kernel_golden.py \
  ml-engine/tests/inference/test_maturity.py \
  ml-engine/tests/serving/test_prediction.py \
  ml-engine/tests/serving/test_runtime.py \
  ml-engine/tests/training/test_holdout.py \
  ml-engine/tests/training/test_residual_release.py \
  ml-engine/tests/training/test_train_artifact.py
```

**신규 파일은 복원이 아니라 삭제다**(`df4976cc` 에 없으므로 `restore` 가 받지 않는다) — 여섯이다.

```bash
git rm -f \
  ml-engine/src/ml_engine/evaluation/epoch.py \
  ml-engine/src/ml_engine/inference/time_axis.py \
  ml-engine/tests/evaluation/test_epoch.py \
  ml-engine/tests/evaluation/test_gate_relaxation_paths.py \
  ml-engine/tests/inference/test_time_axis.py \
  ml-engine/tests/training/test_release_identity.py
```

evidence 디렉터리는 **목록 밖**이다(장부층 — 그래서 이 목록이 자기를 담은 커밋을 가리키지 않는다).
함께 지우려면 `git rm -r reports/evidence/m9/ml-lifecycle`.

목록은 **기계 산출**이다(`git diff --name-status df4976cc..HEAD` 에서 evidence 제외 — `M` **29** · `A` **6**).
라운드가 늘면 다시 산출한다.

## 되돌린 트리에서 무엇이 꺼지는가

- **승격 인용이 사라진다**: 평가 창의 판정이 정책 effective date 를 묻지 않게 되고, `Promotion` 이 세 값으로
  돌아간다(`PromotionSuperseded` 가 사라진다). report 의 `policy_effective_date` 칸도 사라져 report checksum 이
  달라진다 — 되돌린 뒤 새로 낸 report 는 되돌림 **전** report 와 checksum 이 다르다(값이 사라진 만큼).
- **시간축 provenance 가 사라진다**: 관측이 `datetime` 을 다시 들고, 개찰 시각을 모르는 행을 표현할 자리가
  없어진다(그런 행은 애초에 만들 수 없게 되므로 **조용히 사라지는 행이 생기는 것은 아니다** — 결측을 들고
  올 경로 자체가 없어진다). 모집단 항등식과 대체 제외 표, `maturity.time_axis_substitute_approved` 칸도 함께
  사라지고, 그 칸이 사라지므로 출하 정책 파일은 되돌린 로더의 `known_keys` 전수와 다시 일치한다.
- **model_version 축이 다섯으로 줄어든다**: `sample_scope` 가 해시 입력에서 빠져, 범위만 다른 두 아티팩트가
  같은 `release_id` 를 가질 수 있는 상태로 돌아간다. **이미 쓰인 아티팩트의 식별자가 바뀐다** — 되돌림 전에
  학습한 아티팩트를 되돌린 코드로 재파생하면 다른 id 가 나온다(운영 반입이 보류 상태라 영향 범위는 저장소
  안이다).
- **운영 영향 0**: ML 앱 배선과 수집 연장이 사용자 결정으로 보류 상태이고, 이 레인은 운영 host·DB·외부 API 에
  닿지 않았다. 되돌림은 저장소 파일만 움직인다.

예상 복구 시간: 명령 둘(초 단위).

## 실측 (버릴 clone, **실측 HEAD `8284e602`** — 마지막 산출물 커밋)

**앞 라운드의 실측을 옮기지 않았다.** 첫 측정은 `22394872` 에서 났는데 그 뒤 전건 acceptance 가 설계 래칫
위반 하나를 잡아 `_holdout_window.py`(복원 목록 **안**)를 다시 커밋했다 — 되돌림 대상이 움직였으므로 그
측정은 무효이고 이 절은 재측정 결과다(목록·트리 해시는 같다).

| # | 확인 | 결과 |
|---|---|---|
| — | 목록 등식 | 기계 산출 `M` **29** · `A` **6**(evidence 제외) · 문서 목록 **29 + 6** |
| ① | 복원 명령 둘 | `restore` exit **0** · `rm` exit **0** |
| ② | `git status --porcelain` | **`M` 29 · `D` 8**(`D` 는 신규 여섯 + 함께 지운 evidence 둘) |
| ③ | `git diff df4976cc -- .` | **빈 출력** |
| ④⑤ | 컴파일·test 축 | **트리 동일성으로 갈음한다**(「HEAD 초록」이 아니다): 복원 뒤 작업 트리의 `git write-tree` 가 base 트리 해시와 **같다**(`29e4039d…` 양쪽) — 되돌린 트리는 `df4976cc` 와 **같은 트리**이고 그것은 CI 가 통과한 main 이다 |
| ⑥ | 이 slice 가 닿는 게이트 | 아래 「⑥ 게이트」 |

「내 표지 0 · 남의 표지 보존」 계수: 트리 해시가 같으므로 내 표지는 **0 파일**이고, 이 브랜치가 main 을 병합한
적이 없으므로 **보존해야 할 남의 줄이 애초에 0** 이다(그 사실이 ③ 의 빈 출력과 같은 측정이다).

## ⑥ 게이트

복원으로 트리가 base 와 **바이트 동일**해지므로 ml-engine job 의 열 단계(S-1~S-11) 입력이 전부 base 와 같다 —
그 트리는 CI 가 통과한 main 이다. evidence 디렉터리를 **남겨 둘 경우** 유일한 차이가 그 디렉터리이고, 그것을
입력으로 삼는 게이트는 Kotlin `check` job 의 `leakPatternGate` 하나다. 이 레인은 Gradle 을 돌리지 않으므로
(M9 공통 규율) 그 축은 **PR CI 가 정본**이고, 같은 어휘의 참조형 스캔만 로컬에서 쟀다.

| 명령 | 결과 |
|---|---|
| 참조형 스캔 `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m9/ml-lifecycle/` | **매치 0**(exit 1) |

## 유효성 확인 (감사자용)

```bash
git diff --name-only 8284e602..<판정 SHA> -- <위 복원 목록 29 + 신규 6>
```

빈 출력이면 실측 뒤 되돌림 대상이 움직이지 않았다는 뜻이고 이 실측이 유효하다. 한 줄이라도 나오면 미검증이다.
이 slice 는 실측 뒤 **evidence 만** 커밋했으므로 그 출력이 비어 있어야 한다.
