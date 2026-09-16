# M5/5E-2 — rollback.md

착수 커밋 `5939b33`(팀장 — scope.md·milestone-5.md), base `4b9fa21`(PR #18 병합).
목록은 `git diff --name-status 4b9fa21..HEAD`로 기계 산출(아래, 2026-09-16 종결
시점 재산출).

```
A	ml-engine/src/ml_engine/serving/runtime.py
A	ml-engine/src/ml_engine/serving/wire.py
A	ml-engine/tests/app/test_server_prediction.py
A	ml-engine/tests/serving/test_kotlin_rules_parity.py
A	ml-engine/tests/serving/test_runtime.py
A	ml-engine/tests/serving/test_wire.py
A	reports/evidence/m5/5e2/**  (scope.md·commands.md·checklist.md·reuse.md·golden-manifest.json·rollback.md)
M	milestone-5.md
M	ml-engine/src/ml_engine/app/server.py
M	ml-engine/src/ml_engine/serving/__init__.py
M	ml-engine/src/ml_engine/serving/prediction.py
M	ml-engine/src/ml_engine/serving/status.py
M	ml-engine/tests/serving/test_grpc.py
M	ml-engine/tests/serving/test_prediction.py
```

## 절차

1. **신규 파일 삭제**(개별 경로, `-A`/`.` 금지):
   ```
   rm ml-engine/src/ml_engine/serving/runtime.py \
      ml-engine/src/ml_engine/serving/wire.py \
      ml-engine/tests/app/test_server_prediction.py \
      ml-engine/tests/serving/test_kotlin_rules_parity.py \
      ml-engine/tests/serving/test_runtime.py \
      ml-engine/tests/serving/test_wire.py
   rm -r reports/evidence/m5/5e2
   ```
2. **수정 파일을 base 로 복원**(개별 인자, `git restore --source=<base> --staged --worktree --`):
   ```
   git restore --source=4b9fa21 --staged --worktree -- \
     ml-engine/src/ml_engine/app/server.py \
     ml-engine/src/ml_engine/serving/__init__.py \
     ml-engine/src/ml_engine/serving/prediction.py \
     ml-engine/src/ml_engine/serving/status.py \
     ml-engine/tests/serving/test_grpc.py \
     ml-engine/tests/serving/test_prediction.py
   ```
3. **`milestone-5.md`는 hunk 격리** — 착수 커밋 `5939b33`이 이 파일에 낸 hunk(+4줄,
   5E-2 착수 문단) **만** 역적용한다(5D-3 이 이 파일의 다른 절을 만졌을 수 있어 base
   기준 통짜 restore 는 5D-3 의 몫을 지울 위험이 있다 — 착수 시점엔 5D-3 이 이미
   base 에 병합돼 있어 겹칠 hunk 가 없었지만, 규율은 유지한다):
   ```
   git diff 5939b33~1..5939b33 -- milestone-5.md | git apply -R
   ```
   실패(다른 라운드가 인접 줄을 만졌다면)하면 수동 해소 절차(2026-09-10 규율)를 따른다
   — 이 slice 는 milestone-5.md 를 이후 라운드에서 다시 만지지 않았으므로(팀장 단독
   저작) 통상 충돌이 없다.
4. **`reports/evidence/m5/5e2/scope.md`는 착수 커밋 기준 단일 역적용** — 이 파일은
   `5939b33`에서 신규 생성됐다. verifier r1 L-5 — 「이후 수정 이력이 없다」는 팀장의
   계약 갱신 커밋(`cfbb859`·`dc90f18`, S-9·S-3/S-6/S-7 명령 정정 + in_scope 추가)
   시점에서 이미 거짓이었다(scope.md 는 팀장 소유라 구현 레인이 관여하지 않는
   수정이다). **절차 자체는 성립한다** — scope.md 는 신규 파일이므로 「단일 역적용」은
   그 최신 내용을 포함해 통째로 삭제하는 것과 같고(위 1 의 디렉터리째 삭제에 포함,
   별도 명령 불필요), 파일에 수정 이력이 몇 번 쌓였든 삭제 결과는 달라지지 않는다.
   틀린 것은 이 문서의 **서술**(「이후 수정 이력이 없다」)이지 **명령**이 아니다.
5. **하네스 레인 변경 절 재등재** — scope.md §「하네스 레인 변경」은 착수 시점
   "없음"이었다(등재 대상 커밋 0). 이 slice 기간 중 `CLAUDE.md`·`.claude/` 변경이
   있었는지 재확인:
   ```
   git log --oneline 4b9fa21..HEAD -- CLAUDE.md .claude/
   ```
   결과 0건이면 재등재할 내용이 없다(scope.md 원문 그대로 유지, 별도 조치 없음).

## 임시 clone 실측(①~⑥, 실행 의무)

```
git clone . /tmp/5e2-rollback-check && cd /tmp/5e2-rollback-check
git checkout m5-5e2/2026-09-16
# 위 1~3 절차 실행
```

- ① 신규 파일 삭제 — `git status --short` 로 6 파일 + evidence 디렉터리가 `D`(또는
  worktree 에서 사라짐)로 표시되는지 확인.
- ② 수정 파일 복원 — `git diff --stat`(복원 대상 6 파일)이 빈 diff(0 changed) 인지
  확인.
- ③ `milestone-5.md` hunk 역적용 — `git apply -R` exit 0, `git diff -- milestone-5.md`
  가 빈 diff.
- ④ 되돌린 트리에서 `(cd ml-engine && uv sync --extra serving --extra dev && uv run python -m pytest tests -q)` → base 시점 수치(837 passed)로 돌아오는지 확인.
- ⑤ `(cd ml-engine && uv run mypy --strict src/ml_engine)` → `Success: no issues found in 70 source files`(72 → 70, 신규 두 파일 제거 확인).
- ⑥ **게이트 단계** — `./gradlew --no-daemon check` exit 0(BUILD SUCCESSFUL). 이
  slice 는 Kotlin 소스를 만지지 않았으므로 되돌린 트리도 base 와 동일하게 초록이어야
  한다 — 붉으면 milestone-5.md hunk 역적용이 다른 절을 건드렸다는 신호(2026-09-12
  규율).

되돌린 트리 = 5E-1 종결 상태(`CalculateOptimalBid` 는 오버라이드 없이 생성 base 의
기본 `UNIMPLEMENTED`, `GetModelMetadata.readiness` 는 항상 `NOT_READY`, test 808 —
5D-3·leak-baseline-coord 등 base `4b9fa21` 이후 병합분은 이 slice 의 rollback 대상이
아니므로 그대로 남는다, base 자체의 test 수는 837 — 5E-1 종결 시점 808 과 다른 이유는
그 사이 병합된 다른 slice 들의 순증).

## 실측 결과(2026-09-16, 이 문서 작성 시점 실행)

임시 clone(`/tmp/5e2-rollback-check`)에서 ①~⑥ 전부 실행, exit 코드와 핵심 결과는
`commands.md`에 옮기지 않는다(evidence-pack 규율 — rollback 실측은 이 문서 자체가
정본). ①②③ 성립(diff 0), ④ 837 passed, ⑤ `Success: no issues found in 70 source
files`, ⑥ `BUILD SUCCESSFUL`. 임시 clone 은 확인 뒤 삭제했다.
