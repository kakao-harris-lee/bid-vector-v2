# M5/5F-1 — rollback.md

정본. 목록은 `git diff --name-status <base>..HEAD -- <in_scope 경로>`(commands.md
「경계 확인」)로 기계 산출(경로를 개별 인자로). `reports/evidence/m5/5f1/scope.md`는
**팀장 문서 레인 소유**(착수 계약 `666e0eb`) — 이 목록에서 제외한다. `checklist.md`·
`commands.md`·`rollback.md`(이 파일 자신)는 자기 자신을 담은 커밋을 가리킬 수 없다는
규율(2026-09-10) — 목록에서 제외.

base = `845e29b`(PR #21 5E-3 병합 = main), 코드+evidence 마지막 커밋 =
`b2504c7`(commands.md S-10 최종 HEAD 재실측 한 줄 등재).

```
$ git diff --name-status 845e29b..b2504c7 -- \
    ml-engine/policy/inference-v1.yaml \
    reports/evidence/m5/5d/policy-values.md \
    reports/evidence/m5/5d2/policy-values.md \
    ml-engine/tests/inference/test_policy.py \
    ml-engine/tests/inference/_policy_support.py \
    ml-engine/tests/app/test_server_prediction.py \
    ml-engine/tests/serving/test_kotlin_rules_parity.py \
    ml-engine/tests/serving/test_wire.py \
    reports/evidence/m5/5f1
M	ml-engine/policy/inference-v1.yaml
M	ml-engine/tests/app/test_server_prediction.py
M	ml-engine/tests/inference/_policy_support.py
M	ml-engine/tests/inference/test_policy.py
M	ml-engine/tests/serving/test_kotlin_rules_parity.py
M	reports/evidence/m5/5d/policy-values.md
M	reports/evidence/m5/5d2/policy-values.md
A	reports/evidence/m5/5f1/checklist.md      # 자기 자신 — 제외
A	reports/evidence/m5/5f1/commands.md       # 자기 자신 — 제외
A	reports/evidence/m5/5f1/golden-manifest.json
A	reports/evidence/m5/5f1/reuse.md
A	reports/evidence/m5/5f1/scope.md          # 팀장 소유 — 제외
```

`ml-engine/tests/serving/test_wire.py`는 diff 대상에 없다(scope.md 조건부 항목 —
`test_server_prediction.py`쪽에 후보율 확인을 넣기로 판단해 이 파일은 무편집으로
끝났다, checklist.md 참고). 다른 slice 와 겹치는 줄이 없다(`git log --oneline
845e29b..b2504c7 -- reports/evidence/m5/5d/policy-values.md reports/evidence/m5/5d2/
policy-values.md`가 이 slice 커밋 하나만 반환 — commands.md 「경계 확인」 계열) —
hunk 격리 없이 `base..HEAD` 통째 되돌림으로 충분하다.

## 되돌리는 값(요약)

- `scenario.clamp_max`: `1.0` → `1.4`(legacy 값, `OPEN-5E2-CANDIDATE-RATE-UPPER` 재오픈)
- `assessment.agency_sample_threshold`: `10` → 키 삭제(`OPEN-5D2-POLICY-VALUES` 재오픈)
- 되돌리면 출하 정책은 다시 `PolicyRejected(MissingKey)`(5E-2 상태로 복귀)이고,
  clamp 상한이 1 을 넘는 후보는 다시 5E-2 fail-closed(`MappingRejected`)가 거부한다 —
  값을 지어내지 않는 계약은 되돌린 뒤에도 그대로 유지된다.

## 절차

① 값·test 되돌리기 + 신규 evidence 파일 삭제:
```
git restore --source=845e29b --staged --worktree -- \
  ml-engine/policy/inference-v1.yaml \
  reports/evidence/m5/5d/policy-values.md \
  reports/evidence/m5/5d2/policy-values.md \
  ml-engine/tests/inference/test_policy.py \
  ml-engine/tests/inference/_policy_support.py \
  ml-engine/tests/app/test_server_prediction.py \
  ml-engine/tests/serving/test_kotlin_rules_parity.py
git rm reports/evidence/m5/5f1/reuse.md reports/evidence/m5/5f1/golden-manifest.json
```
② 확인 — `git diff 845e29b -- <위 M 경로 일곱>`이 빈 출력(base 와 완전 일치).
③ `(cd ml-engine && uv sync --frozen --all-extras)` exit 0.
④ `(cd ml-engine && uv run python -m pytest tests -q)` exit 0, 952 passed(원래
   baseline 과 동일 — `test_scenario.py` 두 test 는 out_of_scope 라 이 slice 전체
   기간 동안 미편집이었고 base 값(clamp_max 1.4)에서 원래부터 초록이었다).
⑤ ④의 pytest 자체가 compile+test 를 겸한다(Python — 별도 컴파일 단계 없음).
⑥ `./gradlew --no-daemon check` exit 0, BUILD SUCCESSFUL(346 tasks, 214 executed,
   132 from cache) — evidence 문서 삭제 뒤에도 `leakPatternGate`·전건 `check` 가
   초록임을 확인(이 slice 는 Kotlin/`adapters` src 를 건드리지 않으므로 게이트가
   닿는 지점은 `reports/evidence` 스캔뿐).

## 임시 clone 실측(2026-09-16)

`git clone -q <worktree> <scratch>` 뒤 위 ①~⑥ 을 그대로 실행 — 전부 실측한 그대로의
exit 코드(①②③ exit 0·④ 952 passed·⑤ 겸함·⑥ BUILD SUCCESSFUL)를 얻었다. 이 문서의
명령은 문서로만 존재하는 것이 아니라 실행 가능성이 확인됐다.

## 하네스 레인 변경

`git log --oneline 845e29b..HEAD -- CLAUDE.md .claude/` — 없음(commands.md 「경계
확인」과 동일 실측, 되돌린 뒤에도 재등재할 하네스 레인 커밋이 없다).

## 되돌리지 않는 것

`reports/evidence/m5/5f1/{scope.md,checklist.md,commands.md,rollback.md}` — 팀장
문서 레인 소유·자기 참조 규율(위 목록 설명). 이 slice 의 작업 기록 자체는 남긴다.
