# M5/5F-1 — rollback.md

정본. 목록은 `git diff --name-status <base>..HEAD -- <in_scope 경로>`(commands.md
「경계 확인」)로 기계 산출(경로를 개별 인자로). `reports/evidence/m5/5f1/scope.md`는
**팀장 문서 레인 소유**(착수 계약 `666e0eb`, 계약 갱신 (2) `e4ba6a4`) — 이 목록에서
제외한다. `checklist.md`·`commands.md`·`rollback.md`(이 파일 자신)는 자기 자신을 담은
커밋을 가리킬 수 없다는 규율(2026-09-10) — 목록에서 제외.

base = `845e29b`(PR #21 5E-3 병합 = main), 코드+evidence 마지막 커밋 =
`eceb488`(commands.md S-10 최종 재실측 한 줄).

```
$ git diff --name-status 845e29b..eceb488 -- \
    ml-engine/policy/inference-v1.yaml \
    reports/evidence/m5/5d/policy-values.md \
    reports/evidence/m5/5d2/policy-values.md \
    ml-engine/tests/inference/test_policy.py \
    ml-engine/tests/inference/_policy_support.py \
    ml-engine/tests/inference/test_scenario.py \
    ml-engine/tests/app/test_server_prediction.py \
    ml-engine/tests/serving/test_kotlin_rules_parity.py \
    ml-engine/tests/serving/test_wire.py \
    reports/evidence/m5/5f1
M	ml-engine/policy/inference-v1.yaml
M	ml-engine/tests/app/test_server_prediction.py
M	ml-engine/tests/inference/_policy_support.py
M	ml-engine/tests/inference/test_policy.py
M	ml-engine/tests/inference/test_scenario.py
M	ml-engine/tests/serving/test_kotlin_rules_parity.py
M	reports/evidence/m5/5d/policy-values.md
M	reports/evidence/m5/5d2/policy-values.md
A	reports/evidence/m5/5f1/checklist.md      # 자기 자신 — 제외
A	reports/evidence/m5/5f1/commands.md       # 자기 자신 — 제외
A	reports/evidence/m5/5f1/golden-manifest.json
A	reports/evidence/m5/5f1/reuse.md
A	reports/evidence/m5/5f1/rollback.md       # 자기 자신 — 제외
A	reports/evidence/m5/5f1/scope.md          # 팀장 소유 — 제외
```

`ml-engine/tests/serving/test_wire.py`는 diff 대상에 없다(scope.md 조건부 항목 —
`test_server_prediction.py`쪽에 후보율 확인을 넣기로 판단해 이 파일은 무편집으로
끝났다, checklist.md 참고). `ml-engine/tests/inference/test_scenario.py`는 계약 갱신
(2)(팀장 확인 `e4ba6a4`)로 in_scope 에 편입돼 `M` 상태다 — 다른 slice 와 겹치는 줄이
없다(`git log --oneline 845e29b..eceb488 -- ml-engine/tests/inference/test_scenario.py`
가 이 slice 커밋 하나(`1aac701`)만 반환). 마찬가지로 `reports/evidence/m5/5d/
policy-values.md`·`reports/evidence/m5/5d2/policy-values.md`도 이 slice 커밋
(`b687bbc`) 하나뿐 — hunk 격리 없이 `base..HEAD` 통째 되돌림으로 충분하다.

## 되돌리는 값(요약)

- `scenario.clamp_max`: `1.0` → `1.4`(legacy 값, `OPEN-5E2-CANDIDATE-RATE-UPPER` 재오픈)
- `assessment.agency_sample_threshold`: `10` → 키 삭제(`OPEN-5D2-POLICY-VALUES` 재오픈)
- 되돌리면 출하 정책은 다시 `PolicyRejected(MissingKey)`(5E-2 상태로 복귀)이고,
  clamp 상한이 1 을 넘는 후보는 다시 5E-2 fail-closed(`MappingRejected`)가 거부한다 —
  값을 지어내지 않는 계약은 되돌린 뒤에도 그대로 유지된다. `test_scenario.py`의 두
  fixture(`center`)와 D-5F1-5 접힘 test 도 base 상태(경계 충돌 없음)로 복귀한다.

## 절차

① 값·test 되돌리기 + 신규 evidence 파일 삭제:
```
git restore --source=845e29b --staged --worktree -- \
  ml-engine/policy/inference-v1.yaml \
  reports/evidence/m5/5d/policy-values.md \
  reports/evidence/m5/5d2/policy-values.md \
  ml-engine/tests/inference/test_policy.py \
  ml-engine/tests/inference/_policy_support.py \
  ml-engine/tests/inference/test_scenario.py \
  ml-engine/tests/app/test_server_prediction.py \
  ml-engine/tests/serving/test_kotlin_rules_parity.py
git rm reports/evidence/m5/5f1/reuse.md reports/evidence/m5/5f1/golden-manifest.json
```
② 확인 — `git diff 845e29b -- <위 M 경로 여덟>`이 빈 출력(base 와 완전 일치).
③ `(cd ml-engine && uv sync --frozen --all-extras)` exit 0.
④ `(cd ml-engine && uv run python -m pytest tests -q)` exit 0, 952 passed(원래
   baseline 과 동일).
⑤ ④의 pytest 자체가 compile+test 를 겸한다(Python — 별도 컴파일 단계 없음).
⑥ `./gradlew --no-daemon check` exit 0, BUILD SUCCESSFUL — evidence 문서 삭제 뒤에도
   `leakPatternGate`·전건 `check` 가 초록임을 확인(이 slice 는 Kotlin/`adapters` src
   를 건드리지 않으므로 게이트가 닿는 지점은 `reports/evidence` 스캔뿐).

## 임시 clone 실측(2026-09-16, D-5F1-5 test 강화 뒤 최종 재실측)

`git clone -q <worktree> <scratch>`(HEAD `eceb488`) 뒤 위 ①~⑥ 을 그대로 실행:
- ① `git restore`·`git rm` exit 0
- ② `git diff 845e29b -- <M 경로 여덟>` 0줄
- ③ `uv sync --frozen --all-extras` exit 0
- ④ `uv run python -m pytest tests -q` exit 0, **952 passed**(`test_scenario.py`
  포함 — 되돌린 두 test 의 `center=1.0`이 base clamp_max 1.4 에서 다시 안전함을
  재확인, 신설 D-5F1-5 test 는 base 에 없으므로 카운트에서 사라짐. 이 slice 의
  최종 954 passed 와의 차이 2 는 D-5F1-5 test 가 parametrize 로 2 건이기 때문)
- ⑤ ④ 겸함
- ⑥ `./gradlew --no-daemon check` exit 0, BUILD SUCCESSFUL(346 tasks, 211 executed,
  135 from cache — Gradle 캐시 상태에 따라 executed/from-cache 분배는 실행마다
  달라질 수 있다, 합계와 BUILD SUCCESSFUL이 판정 기준)

이 문서의 명령은 문서로만 존재하는 것이 아니라 실행 가능성이 확인됐다(2026-09-16
두 차례 — 계약 갱신 (2) 직후 1회, D-5F1-5 test 강화 뒤 1회 — 목록·exit·수치 전부
재현됨).

## 하네스 레인 변경

`git log --oneline 845e29b..HEAD -- CLAUDE.md .claude/` — 없음(commands.md 「경계
확인」과 동일 실측, 되돌린 뒤에도 재등재할 하네스 레인 커밋이 없다).

## 되돌리지 않는 것

`reports/evidence/m5/5f1/{scope.md,checklist.md,commands.md,rollback.md}` — 팀장
문서 레인 소유·자기 참조 규율(위 목록 설명). 이 slice 의 작업 기록 자체는 남긴다.
