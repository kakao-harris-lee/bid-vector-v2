# M6/6B-4 실행 명령과 종료 코드

정본: `scope.md`. 판정 SHA(산출물 마지막) `54bbef81`. 명령 앞뒤로 HEAD 를 단언하고, 측정
중에는 커밋하지 않는다. 출력 전문은 싣지 않는다(핵심 결과 한 줄) — 감사자는 명령을 다시 돈다.

**마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니다** — evidence 는 자기 마지막 커밋의
post-state 를 담을 수 없다. 그 자리는 verifier 리포트와 PR 조치 코멘트다.

## acceptance — `ml-engine` job 전건 (`.github/workflows/ci.yml`)

양끝 HEAD `54bbef81` 동일. `working-directory: ml-engine`.

## 2026-10-14T (판정 SHA 54bbef81)
- cmd: `uv sync --frozen --all-extras` (S-1)
- exit: 0
- 핵심 결과: lock 그대로 해소

## 2026-10-14T
- cmd: S-1b serving extras 분리 — `uv sync --frozen --extra serving --no-dev` + 금지 패키지 다섯 import 시도 + dev 복구
- exit: 0
- 핵심 결과: 금지 패키지 전부 import 불가

## 2026-10-14T
- cmd: `uv run ruff check .` + `uv run ruff format --check .` (S-2)
- exit: 0
- 핵심 결과: 227 files already formatted

## 2026-10-14T
- cmd: `uv run mypy --strict src/ml_engine` (S-3)
- exit: 0
- 핵심 결과: 98 source files, no issues

## 2026-10-14T
- cmd: `uv run lint-imports` (S-4)
- exit: 0
- 핵심 결과: Contracts 8 kept, 0 broken

## 2026-10-14T
- cmd: `uv run python -m pytest tests -q` (S-5)
- exit: 0
- 핵심 결과: 1516 passed (421s)

## 2026-10-14T
- cmd: `uv run python tools/design_ratchet.py --check` (S-6)
- exit: 0
- 핵심 결과: allowlist 밖 위반 0

## 2026-10-14T
- cmd: S-7 재활용 출처 두 자리 대조 + 양성 대조(어긋난 evidence 가 실패해야 한다)
- exit: 0
- 핵심 결과: 본 검사 통과 · 양성 대조가 기대대로 실패

## 2026-10-14T
- cmd: S-9 Python 버전 두 자리 대조
- exit: 0
- 핵심 결과: `.python-version` 과 `requires-python` 둘 다 3.12

## 2026-10-14T
- cmd: `uv build --wheel -o /tmp/ml-engine-wheel` + `pytest tests/gates/test_wheel_reexport.py -q` (S-11)
- exit: 0
- 핵심 결과: 1 passed — 설치본에서 재수출 성립

### 수정 전 실패 이력(검증이 실제로 작동한 증거)

## 2026-10-14T
- cmd: `uv run python -m pytest tests/gates/test_design_ratchet.py -q` (설계 래칫, 분할 전)
- exit: 1
- 핵심 결과: `runner.py` 의 `_on_done` 이 51줄 — 함수 분할 뒤 통과

## 2026-10-14T
- cmd: `uv run ruff check src/ml_engine/training/jobs/journal.py` (예외 이름 규약 전)
- exit: 1
- 핵심 결과: N818 — 내부 예외 이름을 고쳐 통과

## acceptance — `check` job

## 2026-10-14T
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 10m 22s (359 actionable tasks)

## 2026-10-14T
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 17s (측정, 게이트 아님)

## RED 선행 실측 (TDD)

## 2026-10-14T
- cmd: `uv run python -m pytest tests/training/test_jobs_state.py -q` (전이표 간선 추가 전)
- exit: 2
- 핵심 결과: `TransitionRejectionReason` import 불가 — 구현 뒤 31 passed

## 비밀값 어휘 스캔

패턴은 파일을 참조해 돌린다(어휘를 이 문서에 축어로 적지 않는다). 대상은 slice 산출물 경로와
이 evidence 디렉터리.

## 2026-10-14T
- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m6/6b4/`
- exit: 1
- 핵심 결과: 매치 없음 = 통과

## 2026-10-14T
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <산출물 경로들>`
- exit: 0
- 핵심 결과: 매치는 전부 기존 어휘 계열(취소 신호 타입 이름·compose 의 `${VAR:?}` 참조)이고 새
  비밀값은 0. **구속력 있는 판정은 이 grep 이 아니라 `check` 안의 `leakPatternGate`** 이고(그
  scanRoot 는 `reports/evidence`), 위 `check` exit 0 이 그 결과다. 육안 확인 병기 — 식별자·연락처
  형태 값 0, compose 는 값 리터럴 0(참조만).

## clean-tree 게이트

## 2026-10-14T
- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0
- 핵심 결과: 빈 출력

## 2026-10-14T
- cmd: 양성 대조 — in_scope 파일 하나에 줄을 덧붙인 뒤 같은 명령, 그 뒤 **비파괴 절삭**으로 복원
  (`checkout --` 쓰지 않는다)
- exit: 0
- 핵심 결과: 해당 파일이 ` M` 으로 잡힘 → 복원 뒤 다시 빈 출력

## 변이 실측 — 바꿔치우기(더하기 아님), 적용은 `git diff --numstat` 으로 확인

열은 「무엇을 바꿔치웠나 → 어느 test 가 RED 가 되는가」. 전부 `git restore --source=HEAD` 로
되돌리고 `git status --porcelain` 빈 출력을 확인했다.

| # | 변이 | numstat | RED |
| --- | --- | --- | --- |
| M-1 | 정책 값 `pending_queue_max` 4 → **3** | 1/1 | 2 — 승인 값 test · 값이 폐기 시점에 닿는지 test |
| M-2 | 같은 값 4 → **5** | 1/1 | 같은 2 |
| M-3 | 상한 판정 off-by-one(초과분 계산) | 1/1 | 8 — 폐기 시점·동률·래치·저널 상태·재기동 상한·수렴 |
| M-4 | Python 전이표에서 `ACCEPTED→FAILED` 제거 | 0/1 | 5 — 교차 등식 3 + 전이표 2 |
| M-5 | Kotlin 허용표에서 같은 간선 제거 | 0/1 | 4 — **Gradle 없이 Python 게이트가 잡는다** + 음성 대조 자신 |
| M-6 | proto 주석 표 행 제거 | 0/1 | 3 — 교차 등식 |
| M-7 | 저널 쓰기 **전에** 메모리 커밋(순서) | 1/1 | 1 — 쓰기 실패 시 메모리 불변 test |
| M-8 | 원자 교체를 직접 쓰기로 | 1/3 | 4 — 크래시 주입 fsync·replace × (저널·재기동) |
| M-9 | 깨진 저널을 「없음」으로 읽기 | 2/2 | 6 — schema·시각·어휘·중복 둘·필드 누락 |
| M-10 | `START` 를 다시 `submit()` 안으로 | 1/0 | 10 — 대기 관측·wire handle·취소·종료 경로 |

M-9 보충: JSON **문법** 오류는 이 변이가 건드리지 않는 갈래(`JSONDecodeError`)라
`test_malformed_payload_is_failed_not_absent` 는 초록으로 남았다. 구조·어휘·불변식 갈래 여섯이
잡았다. M-5 는 음성 대조 test 자신(`parse_kotlin_edges` 가 지운 간선을 본다)도 함께 붉어진다 —
기준 문면이 이미 바뀌었음을 그 test 가 알아차린 것이다.

## rollback 실측

`rollback.md` 참조(실측 HEAD · ①~⑥ 결과를 그 파일이 갖는다).
