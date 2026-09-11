# M5/5A — rollback.md (verifier r1 수정 라운드 뒤 재산출)

**복원 대상**: `d281329bed7f095a3d98eaaa5eecb06afd0d5969`(scope.md `base_sha`). 목록은
`git diff --name-status d281329..e45202f4ce679c8be0634cbf7f23000bbd06ef54`(코드 레인
마지막 커밋, 이 파일 자신을 담는 다음 커밋은 제외 — 「목록이 자기 커밋을 가리키지 않는다」)
기계 산출에서 **하네스 레인 변경**(`milestone-5.md`·`reports/evidence/m5/5a/scope.md`·
`reports/evidence/m5/5a/policy-values.md`)을 뺀 것이다 — 그 셋은 운영자/세션 모델이 쓴
계약 문서이고 이 slice 의 `out_of_scope`이므로 되돌리지 않는다(2026-09-04 하네스 규율).

## 되돌릴 커밋 (코드 레인, verifier r1 착수 이후)

이전 종결(`e93314b`) 뒤 하네스 레인이 계약을 두 번 갱신했다(`dbd133d`·`18ddc1d`, D-5A-0(b)
위치·S-7 문면 — 위 목록 제외 대상). 코드 레인 수정 커밋 넷:
- `9006f3736adbdf3d7a537f00e3bd20e7a708b227` — fix: F-1(승인 통로 개방)·F-2(우회 구조적 폐쇄)
- `f08dc83628e74fab6663e5d0990d0895d17d6507` — fix: F-3(양방향 대조)·F-4(ruff 제외)·F-5(CI 액션)
- `fdb1d52e4b6b510ee34e75f4e5e95c59abbb3814` — fix: F-8(design-ratchet duplicate key)
- `e45202f4ce679c8be0634cbf7f23000bbd06ef54` — docs: evidence 갱신(commands·checklist)

넷 다 공유 파일(`ci.yml`·`.gitignore`)을 겹쳐 만지지만 **같은 slice 의 연속 커밋**이라
hunk 충돌이 없다(`git log d281329..HEAD -- .github/workflows/ci.yml .gitignore`에 다른
레인의 커밋이 섞이지 않음 — 실측). 파일 전체 restore 로 충분하다.

## 목록 (기계 산출, 하네스 레인 제외)

```
M	.github/workflows/ci.yml
M	.gitignore
A	ml-engine/.python-version
A	ml-engine/README.md
M	ml-engine/pyproject.toml
A	ml-engine/src/ml_engine/__init__.py
A	ml-engine/src/ml_engine/adapters/__init__.py
A	ml-engine/src/ml_engine/contracts/__init__.py
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
A	ml-engine/tests/gates/fixtures/good_serving/ml_engine/__init__.py
A	ml-engine/tests/gates/fixtures/good_serving/ml_engine/contracts/__init__.py
A	ml-engine/tests/gates/fixtures/good_serving/ml_engine/serving/__init__.py
A	ml-engine/tests/gates/fixtures/good_serving/pyproject.toml
A	ml-engine/tests/gates/fixtures/ratchet_violations/sample.py
A	ml-engine/tests/gates/fixtures/reuse-claims-fake-pointer.md
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
A	reports/evidence/m5/5a/rollback.md
```

## 복원 명령

```
git restore --source=d281329 --staged --worktree -- <위 목록의 경로 각각(개별 인자)>
```

## 실측 — 임시 clone(2026-09-11, verifier r1 라운드 뒤)

1. `git clone --no-hardlinks /Users/harris/Development/private/bid-vector-v2-m4e <scratch>`
   → `<scratch>`에서 `git checkout m5-5a/2026-09-11`(HEAD `e45202f`).
2. 위 복원 명령을 **경로 개별 인자**로 실행 → exit 0, `git status --short`에 위 목록과
   정확히 같은 집합이 `M`/`D`로 표시(신규 경로 전부 `D`).
3. `git diff d281329 -- .github/workflows/ci.yml .gitignore ml-engine reports/evidence/m5/5a/checklist.md reports/evidence/m5/5a/commands.md reports/evidence/m5/5a/reuse.md reports/evidence/m5/5a/rollback.md`
   → **0줄**(in_scope diff 0 — `scope.md`·`policy-values.md`는 대조 대상에서 뺐다, 되돌리지
   않으므로).
4. 되돌린 트리에서 `ml-engine/`: `uv.lock`이 없으므로(base 상태) `--frozen` 대신
   `uv sync --extra dev` → exit 0 → `uv run python -m pytest tests -q` → **125 passed**
   (2A~2E 원래 계약 test, 5A 게이트 31개는 파일째 사라졌으므로 당연히 없음).
5. 저장소 루트에서 `./gradlew --no-build-cache --no-daemon clean check` → **BUILD
   SUCCESSFUL**(354 actionable tasks, 전부 executed) — Kotlin 쪽 무영향, `ci.yml`이
   `jobs: [check]` 하나로 복원됐음도 확인.
6. 임시 clone 삭제(`rm -rf <scratch>`).

## 되돌리지 않는 것

`milestone-5.md`·`reports/evidence/m5/5a/scope.md`·`reports/evidence/m5/5a/policy-values.md`
— 하네스 레인(운영자/세션 모델)이 쓴 계약 문서. 이 slice 의 `out_of_scope`이자 이미 승인된
계약이다.
