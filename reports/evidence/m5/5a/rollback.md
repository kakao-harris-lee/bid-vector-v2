# M5/5A — rollback.md

**복원 대상(코드 레인의 실제 시작점)**: `b00e9299bb9dec48ea9604ac8b53ef1084c1c701`(착수 계약
고정 커밋 — scope.md `base_sha` `d281329`와 이 커밋 사이는 하네스 레인 변경(`milestone-5.md`·
`reports/evidence/m5/5a/scope.md`·`policy-values.md`)이며 되돌리지 않는다 — 그 문서들은
계약 자체이고 이 rollback 은 **구현 3개 커밋만** 대상이다).

**되돌릴 커밋**(코드 레인, 순서대로):
- `d79bb449f6f4b8267cf62f64df737d97ea3fe1e3` — feat: 패키지 경계·게이트 test
- `40ec19d668794eeed20e7046c46656af72192480` — ci: Python job 신설
- `5af39d157a998c9b3f4be9146cf40dc03a6e9198` — docs: evidence

세 커밋 다 공유 파일(`.github/workflows/ci.yml`·`.gitignore`)이 겹치지만 **같은 슬라이스의
연속 커밋**이라 hunk 충돌이 없다(`git log b00e929..HEAD -- .github/workflows/ci.yml .gitignore`
= 이 세 커밋뿐, 다른 레인의 동시 편집 없음 — 실측). 그래서 hunk 단위 격리 없이 파일
전체 restore 로 충분하다(2026-09-10 hunk 격리 규정은 **겹치는 레인이 있을 때**의 절차 —
여기는 없다).

## 목록 (기계 산출)

`git diff --name-status b00e929..5af39d1`(2026-09-11 산출, 아래는 그 출력 그대로):

```
M	.github/workflows/ci.yml
M	.gitignore
A	ml-engine/.python-version
A	ml-engine/README.md
M	ml-engine/pyproject.toml
A	ml-engine/src/ml_engine/__init__.py
A	ml-engine/src/ml_engine/adapters/__init__.py
A	ml-engine/src/ml_engine/contracts/__init__.py
A	ml-engine/src/ml_engine/contracts/_generated/.gitkeep
A	ml-engine/src/ml_engine/evaluation/__init__.py
A	ml-engine/src/ml_engine/features/__init__.py
A	ml-engine/src/ml_engine/inference/__init__.py
A	ml-engine/src/ml_engine/registry/__init__.py
A	ml-engine/src/ml_engine/registry/policy.py
A	ml-engine/src/ml_engine/serving/__init__.py
A	ml-engine/src/ml_engine/training/__init__.py
M	ml-engine/tests/conftest.py
A	ml-engine/tests/gates/fixtures/bad_contracts_bypass/ml_engine/__init__.py
A	ml-engine/tests/gates/fixtures/bad_contracts_bypass/ml_engine/features/__init__.py
A	ml-engine/tests/gates/fixtures/bad_contracts_bypass/pyproject.toml
A	ml-engine/tests/gates/fixtures/bad_serving/ml_engine/__init__.py
A	ml-engine/tests/gates/fixtures/bad_serving/ml_engine/serving/__init__.py
A	ml-engine/tests/gates/fixtures/bad_serving/pyproject.toml
A	ml-engine/tests/gates/fixtures/ratchet_violations/sample.py
A	ml-engine/tests/gates/fixtures/reuse-mismatch.md
A	ml-engine/tests/gates/test_contracts_reexport.py
A	ml-engine/tests/gates/test_design_ratchet.py
A	ml-engine/tests/gates/test_generated_not_tracked.py
A	ml-engine/tests/gates/test_import_contracts.py
A	ml-engine/tests/gates/test_mypy_allowlist.py
A	ml-engine/tests/gates/test_policy_loader.py
A	ml-engine/tests/gates/test_reuse_provenance.py
A	ml-engine/tests/gates/test_serving_purity.py
A	ml-engine/tools/__init__.py
A	ml-engine/tools/design_ratchet.py
A	ml-engine/tools/generate_contracts.py
A	ml-engine/tools/reuse_provenance_check.py
A	ml-engine/uv.lock
A	reports/evidence/m5/5a/checklist.md
A	reports/evidence/m5/5a/commands.md
A	reports/evidence/m5/5a/reuse.md
```

## 복원 명령

```
git restore --source=b00e929 --staged --worktree -- <위 목록의 경로 각각(개별 인자)>
```

(신규(`A`) 경로는 `restore --source`가 base 트리에 없는 파일로 인식해 워킹트리에서
삭제한다 — `checkout --`가 아니라 `restore`를 쓰는 이유, 2026-09-04 교훈.)

## 실측 — 임시 clone(2026-09-11)

1. `git clone --no-hardlinks /Users/harris/Development/private/bid-vector-v2-m4e <scratch>`
   → `<scratch>`에서 `git checkout m5-5a/2026-09-11`(HEAD `5af39d1`).
2. 위 복원 명령을 **경로 개별 인자**로 실행 → exit 0, `git status --short`에 위 목록과
   정확히 같은 집합이 `M`/`D`로 표시(신규 경로 전부 `D`).
3. `git diff b00e929 -- .github/workflows/ci.yml .gitignore ml-engine reports/evidence/m5/5a`
   → **0줄**(in_scope diff 0).
4. 되돌린 트리에서 `ml-engine/`: `uv.lock`이 없으므로(base 상태) `--frozen` 대신
   `uv sync --extra dev` → exit 0 → `uv run python -m pytest tests -q` → **125 passed**
   (2A~2E 원래 계약 test, 5A 게이트 27개는 파일째 사라졌으므로 당연히 없음 — 회귀 없음
   확인의 정의가 「되돌린 트리가 5A 이전과 같다」이지 「게이트 test 가 있다」가 아니다).
5. 저장소 루트에서 `./gradlew --no-build-cache --no-daemon clean check` → **BUILD SUCCESSFUL**
   (354 actionable tasks, 전부 executed) — Kotlin 쪽 무영향, `ci.yml`이 `jobs: [check]`
   하나로 복원됐음도 `python3 -c "import yaml; ..."`로 확인.
6. 임시 clone 삭제(`rm -rf <scratch>`).

## 되돌리지 않는 것

`d281329..b00e929`(하네스 레인 — `milestone-5.md`·`reports/evidence/m5/5a/scope.md`·
`policy-values.md`, 착수 계약 고정). 이 slice 의 `out_of_scope`이자 이미 승인된 계약이다.
