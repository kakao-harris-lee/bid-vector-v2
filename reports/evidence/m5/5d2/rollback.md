# M5/5D-2 — rollback.md

정본. 목록은 `git diff --name-status <base>..<코드 마지막 커밋> -- <in_scope 경로>`로
기계 산출(경로를 개별 인자로). **`reports/evidence/m5/5d2/{scope.md,policy-values.md}`는
팀장 문서 레인 소유**(착수 계약 `9466347`·계약 갱신 `623baa1`) — 이 목록에서 제외한다.
`commands.md`·`checklist.md`·`rollback.md`(이 파일 자신)는 자기 자신을 담은 커밋을
가리킬 수 없다는 규율(2026-09-10) — 목록에서 제외. `reuse.md`만 코드 레인 소유 evidence
라 대상에 포함.

base = `cefb19c9269ac3b29d0246175fb04d71c04aa3d4`(5D 종결), 코드 마지막 커밋 =
`fe836db20fe2a83aa6806966457384a267096d6c`(evidence — reuse.md. 구현 코드 자체는
`b9cfe77`).

```
$ git diff --name-status cefb19c9269ac3b29d0246175fb04d71c04aa3d4..fe836db20fe2a83aa6806966457384a267096d6c \
    -- ml-engine/src/ml_engine/inference ml-engine/tests/inference ml-engine/policy \
       reports/evidence/m5/5d2
M	ml-engine/src/ml_engine/inference/__init__.py
A	ml-engine/src/ml_engine/inference/availability.py
A	ml-engine/src/ml_engine/inference/distribution.py
A	ml-engine/src/ml_engine/inference/engine.py
A	ml-engine/src/ml_engine/inference/observations.py
M	ml-engine/src/ml_engine/inference/policy.py
M	ml-engine/src/ml_engine/inference/predict.py
M	ml-engine/src/ml_engine/inference/results.py
A	ml-engine/tests/inference/_policy_support.py
A	ml-engine/tests/inference/_sample_support.py
M	ml-engine/tests/inference/golden/_adapter.py
M	ml-engine/tests/inference/golden/test_kernel_golden.py
M	ml-engine/tests/inference/test_assessment.py
A	ml-engine/tests/inference/test_availability.py
A	ml-engine/tests/inference/test_distribution.py
A	ml-engine/tests/inference/test_engine.py
A	ml-engine/tests/inference/test_observations.py
M	ml-engine/tests/inference/test_policy.py
M	ml-engine/tests/inference/test_predict.py
M	ml-engine/tests/inference/test_results.py
M	ml-engine/tests/inference/test_scenario.py
A	reports/evidence/m5/5d2/policy-values.md   ← 팀장 문서 레인(제외)
A	reports/evidence/m5/5d2/reuse.md           ← 이 slice 소유(포함)
A	reports/evidence/m5/5d2/scope.md           ← 팀장 문서 레인(제외)
```

`ml-engine/policy/inference-v1.yaml`은 diff 0(편집 없음, D-5D2-3 — 값 승인 전 출하 YAML
에 신설 키를 넣지 않는다). `M` 표시된 5D 소유 파일 9개(`__init__.py`·`policy.py`·
`predict.py`·`results.py`·golden 어댑터/test·`test_assessment.py`·`test_policy.py`·
`test_predict.py`·`test_results.py`·`test_scenario.py`)는 이 range 안에서 **이 slice
외 다른 레인이 손대지 않았다**(위 diff 가 base 대비 전체 변경분이고, 팀장 커밋 둘(`9466347`·
`623baa1`)은 `milestone-5.md`·`reports/evidence/m5/{5a,5d2}/*.md`만 건드려 겹치지 않는다)
— hunk 격리 없이 `git restore --source=<base>`전체 복원이 안전하다.

## 되돌리기 명령(신설 경로 삭제 + 편집 파일 전체 복원, 경로 개별 인자)

```bash
rm -f ml-engine/src/ml_engine/inference/availability.py \
      ml-engine/src/ml_engine/inference/distribution.py \
      ml-engine/src/ml_engine/inference/engine.py \
      ml-engine/src/ml_engine/inference/observations.py \
      ml-engine/tests/inference/_policy_support.py \
      ml-engine/tests/inference/_sample_support.py \
      ml-engine/tests/inference/test_availability.py \
      ml-engine/tests/inference/test_distribution.py \
      ml-engine/tests/inference/test_engine.py \
      ml-engine/tests/inference/test_observations.py \
      reports/evidence/m5/5d2/reuse.md
git restore --source=cefb19c9269ac3b29d0246175fb04d71c04aa3d4 --staged --worktree -- \
      ml-engine/src/ml_engine/inference/__init__.py \
      ml-engine/src/ml_engine/inference/policy.py \
      ml-engine/src/ml_engine/inference/predict.py \
      ml-engine/src/ml_engine/inference/results.py \
      ml-engine/tests/inference/golden/_adapter.py \
      ml-engine/tests/inference/golden/test_kernel_golden.py \
      ml-engine/tests/inference/test_assessment.py \
      ml-engine/tests/inference/test_policy.py \
      ml-engine/tests/inference/test_predict.py \
      ml-engine/tests/inference/test_results.py \
      ml-engine/tests/inference/test_scenario.py
```

## 임시 clone 실측(2026-09-15, `git clone --no-hardlinks`)

1. `/…/scratchpad/5d2-rollback-check`에 현재 브랜치 HEAD(`fe836db`)를 `git clone
   --no-hardlinks`로 복제.
2. 위 되돌리기 명령 실행 → `git status --short`가 신설 10개 삭제(inference 4 +
   test 지원 2 + test 4) + `reuse.md` 삭제 + 편집 파일 9개(M) 만 보임 — `scope.md`·
   `policy-values.md`는 목록 밖이라 그대로 남음(확인).
3. `git diff cefb19c9269ac3b29d0246175fb04d71c04aa3d4 -- ml-engine/src/ml_engine/inference
   ml-engine/tests/inference ml-engine/policy reports/evidence/m5/5d2/reuse.md | wc -l`
   (작업트리 diff) → **0**(in_scope 코드·evidence 전체 원복 확인).
4. `cd ml-engine && uv sync --frozen --all-extras && uv run python -m pytest tests -q
   -m "not legacy_parity"` → **347 passed, 1 skipped, 4 deselected**(5D 종결 시점과
   정확히 같은 수 — `ml-kernel-011` skip 복귀, 5D-2 테스트 전부 소멸).
5. `uv run mypy --strict src/ml_engine` → `Success: no issues found in 27 source files`
   (5D-2 신설 4파일 소멸 — 27 = 5D 종결 시점 파일 수).
6. `uv run lint-imports` → `Contracts: 5 kept, 0 broken`(45 files, 125 dependencies —
   5D 종결 시점과 동일).

## 알려진 제한

되돌리기는 `reuse.md`만 삭제한다(단일 파일 경로 한정) — `scope.md`·`policy-values.md`는
팀장 문서 레인 소유라 대상이 아니다(그 문서를 되돌리려면 운영자·팀장의 별도 결정 필요).
`commands.md`·`checklist.md`·`rollback.md`(이 파일 자신)는 목록에 없다(2026-09-10 규율).
`ml-engine/policy/inference-v1.yaml`은 애초에 diff 가 없어 되돌릴 대상도 없다.
