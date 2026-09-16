# M5/5F-1 — 출하 inference 정책 값 둘: `scenario.clamp_max ≤ 1.0` · `assessment.agency_sample_threshold` 잠정값 (2026-09-16)

```yaml
milestone: M5
slice: 5f1-inference-policy-values
base_sha: 845e29b   # PR #21(5E-3) 머지 커밋 = main
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - ml-engine/policy/inference-v1.yaml                        # 값 둘 — clamp_max 1.4 → 1.0, agency_sample_threshold: 10 신설(주석에 잠정·재승인 조건). 다른 키·순서 무변경
  - reports/evidence/m5/5d/policy-values.md                   # clamp_max 행 값 + change_history 1행(운영자 결정 2026-09-16 ①) — 5D 문서지만 승인 값의 정본 자리라 여기서 갱신
  - reports/evidence/m5/5d2/policy-values.md                  # agency_sample_threshold 행 「미정」→ 잠정값 10 + 지위 문면 + change_history 1행(운영자 결정 ③)
  - ml-engine/tests/inference/test_policy.py                  # 「출하 파일은 threshold 부재로 거부」 test 를 「출하 파일이 그대로 로드된다 + 값 대조」로 뒤집음. 다른 case 무편집
  - ml-engine/tests/inference/_policy_support.py              # placeholder 주입이 no-op 이 됨 — helper 는 출하 파일을 그대로 읽게 단순화(호출부 시그니처 무변경) 또는 docstring 만. 둘 중 하나, 판단 등재
  - ml-engine/tests/app/test_server_prediction.py             # (B) 「출하 정책 → NOT_READY」 를 「출하 정책 그대로 → READY·promoted·Success」 로 뒤집음(실 socket). (A) 임시 사본 경로는 threshold 가 이제 출하에 있으므로 제거하거나 (B) 와 합침 — 판단 등재. `test_single_broken_policy_*` 무편집
  - ml-engine/tests/serving/test_kotlin_rules_parity.py       # placeholder 주입 제거(출하 파일 그대로) — 미러 규칙 단언 무편집
  - ml-engine/tests/serving/test_wire.py                      # (있으면) clamp 상한 1.0 인 정책으로 「후보율 ≤ 1」 이 정책 층에서 보장됨을 실 엔진 경로 1 case 로 고정 — 없으면 tests/app/test_server_prediction.py 에
  - reports/evidence/m5/5f1/**
out_of_scope:
  - ml-engine/src/**                       # 산식·로더·엔진 코드 무편집 — 값만 바뀐다. 코드가 바뀌어야 값이 서면 slice 를 멈추고 보고
  - fixtures/**, golden                    # golden case 는 자체 정책 cfg(`clampMax`·`agencySampleThreshold`)를 실으므로 영향 0 — S-5 로 실측
  - reports/evidence/m5/5e2/**             # 5E-2 알려진 제한 2·7 은 편집하지 않는다(종결 판정 문서가 처분)
  - milestone-5.md                         # 착수·종결 등재는 종결 판정 브랜치(m5-closure)가 담당 — 세 브랜치가 같은 파일에 문단을 더하는 충돌 회피
  - Kotlin (5F-2), contracts/**
acceptance_commands:   # CI `ml-engine` job 그대로(S-0·S-1b·S-2~S-7 양성·S-9·S-11) + S-10·S-12
  - "(cd ml-engine && uv sync --frozen --all-extras)"
  - "(cd ml-engine && set -euo pipefail && uv sync --frozen --extra serving --no-dev && for m in sqlalchemy psycopg requests httpx celery; do if uv run --no-sync python -c \"import $m\" >/dev/null 2>&1; then echo \"금지 패키지 $m 이 serving extras 에 설치됐다\" >&2; exit 1; fi; done && uv sync --frozen --all-extras)"
  - "(cd ml-engine && uv run ruff check . && uv run ruff format --check .)"
  - "(cd ml-engine && uv run mypy --strict src/ml_engine)"
  - "(cd ml-engine && uv run lint-imports)"
  - "(cd ml-engine && uv run python -m pytest tests -q)"
  - "(cd ml-engine && uv run python tools/design_ratchet.py --check)"
  - "(cd ml-engine && set -euo pipefail && uv run python tools/reuse_provenance_check.py && if uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md; then echo \"양성 대조(어긋난 evidence)가 실패해야 하는데 통과했다\" >&2; exit 1; fi)"
  - "(cd ml-engine && uv run python -c \"import tomllib,pathlib; p=tomllib.load(open('pyproject.toml','rb')); v=pathlib.Path('.python-version').read_text().strip(); assert v.startswith('3.12') and '3.12' in p['project']['requires-python'], (v, p['project']['requires-python'])\")"
  - "./gradlew --no-daemon check"     # S-10 — evidence 커밋마다 그 HEAD 에서
  - "(cd ml-engine && uv build --wheel -o /tmp/ml-engine-wheel && uv run python -m pytest tests/gates/test_wheel_reexport.py -q)"   # S-11
  - "./tools/contract-crosslang-smoke.sh"   # S-12
rollback: |
  값 둘을 base 로 되돌린다 — `git restore --source=845e29b --staged --worktree -- <in_scope 경로 개별 인자>`(신규 evidence 디렉터리는 삭제). 되돌리면 출하 정책은 다시 NOT_READY(5E-2 상태)이고 clamp 상한은 1.4 로 복귀한다(계약 위반 후보가 `MappingRejected` 로 거부되는 5E-2 fail-closed 가 그 상태를 지킨다). 임시 clone ①~⑥ 실측, 정본 `reports/evidence/m5/5f1/rollback.md`.
```

## 이 slice 가 하는 일(운영자 결정 2026-09-16 ①·③, `reports/evidence/m5/closure/checklist.md` §3)

① **`scenario.clamp_max: 1.4 → 1.0`** (`OPEN-5E2-CANDIDATE-RATE-UPPER` (a)). 계약의 응답 후보율 축은 ≤ 1(D-2B-8·D-2F-4)이고 Kotlin `ParsedSuccessFields` 가 그것을 대조한다. 엔진 clamp 가 1 을 넘게 두면 5E-2 wire 가 적법한 입력에서도 `MappingRejected`(gRPC 오류)를 낸다. 값을 정책 층에서 1.0 으로 내려 **후보가 계약 안에서 만들어지게** 한다 — 산식 무변경, 5E-2 fail-closed 는 그대로 둔다(정책이 다시 넘으면 여전히 거부).
③ **`assessment.agency_sample_threshold: 10` 신설**(`OPEN-5D2-POLICY-VALUES` (a) 잠정값). 5D-2 후보 (b) golden synthetic 10(`ml-kernel-011`)을 잠정값으로 채택 — legacy 대응 상수가 없어 지어낸 값이 아니라 **승인된 golden case 가 쓰는 값**을 옮긴다. 정책 파일 주석과 5D-2 policy-values 에 「잠정 — 실 코퍼스 재학습 지표 뒤 재승인」을 명기. 대안 (a) 40 은 GBM 미학습 가드(#31) 축의 값이라 기관 표본 임계와 다른 축.
④ 그 결과 **출하 정책 그대로 gate READY** — 5E-2 가 「출하 정책으로는 서빙이 안 된다」를 test 로 고정해 뒀으므로 그 test 를 **정반대 사실**로 뒤집는다(실 socket: READY·`promoted`·`Success`). 값 변경이 아니라 코드 변경으로만 READY 가 되면 이 slice 의 전제가 틀린 것 — 멈추고 보고.

## 계약 고정 결정
| ID | 결정 | 근거 |
| --- | --- | --- |
| D-5F1-1 | 값만 바꾼다 — `src/**` 무편집. 정책 → 문서 → test 순(5D 관례) | 산식·로더는 5D·5D-2·5E-2 종결 산출물 |
| D-5F1-2 | 잠정값 10 의 근거는 golden 011 case(승인 corpus)이고 재승인 조건은 실 코퍼스 재학습 지표 | 지어낸 수치 0 원칙(5C-2 관례) |
| D-5F1-3 | 5E-2 의 「출하 = NOT_READY」 test 는 삭제가 아니라 **반전**(출하 = READY) — 값이 빠지면 다시 붉어지는 방향 | 게이트는 산출물에 건다 |
| D-5F1-4 | 5D·5D-2 policy-values 표 갱신은 이 slice 가 한다(승인 값의 정본 자리) — 5E-2 알려진 제한 문면은 무편집(종결 판정이 처분) | 두 문서가 다른 말을 하지 않게 |

## 위협 모델 — 5F-1 고유 경계
**방어한다**: (a) 출하 정책이 계약 밖 후보를 만들지 않는다(clamp ≤ 1) (b) 출하 정책 그대로 READY 가 된다는 사실이 test 로 고정된다(값이 빠지면 붉음). **방어하지 않는다**: 임계 10 의 통계적 타당성(재학습 slice 몫) · 정책 값이 바뀔 때 5D golden 이 재생성돼야 하는가(golden 은 자체 cfg — 영향 0 을 S-5 로 실측만).
**우회 후보**: (1) 값을 바꾸고 test 를 안 뒤집으면 — S-5 가 붉어야 한다(5E-2 test 가 「NOT_READY」를 단언하므로 자동) (2) clamp_max 1.0 에서 `clamp_min < clamp_max` 불변식(0.7 < 1.0) 유지 — 로더 test 가 확인 (3) `assessment.plausible_max 1.2` 는 입력 표본 밴드라 무관 — 혼동하지 않는다.

## (2b) 값 획득 축
새 public 표면 0(값만).

## 하네스 레인 변경
`git log --oneline 845e29b..HEAD -- CLAUDE.md .claude/` — 없음(착수 시점).

## OPEN — 수령·처분
| 식별자 | 처분 |
| --- | --- |
| `OPEN-5E2-CANDIDATE-RATE-UPPER` | 이 slice 병합으로 **종결**((a) 채택) |
| `OPEN-5D2-POLICY-VALUES` | 이 slice 병합으로 **종결(잠정값)** — 재승인 조건은 5D-2 policy-values 에 등재 |

## 계약 갱신 이력
| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-16 착수 | 초판 — D-5F1-1~4 | 운영자 결정 ①③(선택지 답변, 종결 판정 §3) |
