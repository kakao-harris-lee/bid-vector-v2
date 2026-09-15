# M5/5D-3 — rollback.md

## 되돌림 방식 — 착수 경계 기준 단일 역적용 (2026-09-16 규칙)

`distribution.py`·`observations.py`·`facts.py`(+ 재수출 `__init__.py` 둘)는 5D-2/5B 소유
공유 파일이고, `milestone-5.md`는 팀장 소유 문서다. 이 branch(`m5-5d3/2026-09-16`)에서
base(`d09666348c6489c1a8fd32144b91b82937620ef4`, PR #16 병합 커밋 — scope.md 문면의
`base_sha`는 자릿수가 하나 많은 오기, 이 SHA가 실측 정본) 이후 이 파일들을 만진 커밋은
전부 이 slice 자신(`5306958`·`104697a`·`b72af4e`)뿐이다(다른 레인의 끼어듦 없음 —
`git log --oneline d09666..HEAD`로 확인 가능, 커밋 셋이 전부 이 slice). 그래서 커밋 해시별
hunk 격리가 아니라 **착수 경계 기준 단일 역적용**으로 충분하다.

목록은 손으로 쓰지 않는다 — `git diff --name-status d09666348c6489c1a8fd32144b91b82937620ef4..HEAD`
에서 기계적으로 낸다. **라운드마다 파일이 늘면 이 명령을 다시 돌린다.**

## 복원 명령

```bash
git diff d09666348c6489c1a8fd32144b91b82937620ef4 -- \
  milestone-5.md \
  ml-engine/src/ml_engine/features/__init__.py \
  ml-engine/src/ml_engine/features/facts.py \
  ml-engine/src/ml_engine/inference/__init__.py \
  ml-engine/src/ml_engine/inference/distribution.py \
  ml-engine/src/ml_engine/inference/observations.py \
  ml-engine/tests/features/test_facts.py \
  ml-engine/tests/inference/_sample_support.py \
  ml-engine/tests/inference/golden/test_kernel_golden.py \
  ml-engine/tests/inference/test_distribution.py \
  ml-engine/tests/inference/test_engine.py \
  ml-engine/tests/inference/test_observations.py \
  | git apply -R
```

`reports/evidence/m5/5d3/**`(신규 파일, `git diff --name-status`의 `A` 항목)는 **되돌리지
않는다** — evidence는 남긴다. 되돌림 뒤 스테이지된 복원은 커밋한다.

## 임시 clone 실측 (`git clone --no-hardlinks`, b72af4e 시점 — 평가 완료 뒤 삭제)

① 파일을 만진 커밋 확인 — `git log --oneline d09666..HEAD`가 `5306958`·`104697a`·
`b72af4e` 셋뿐임을 확인(다른 slice 개입 없음, hunk 격리 불필요의 근거).
② 위 복원 명령 실행 — `git apply -R` **exit 0**.
③ 되돌린 뒤 `git diff d09666 -- <위 12개 경로>` **0줄**(내 줄 사라짐) — `reports/evidence/
m5/5d3/`는 `git status --short`에서 미출현(남의/자기 evidence 보존 — 여기선 자기 evidence,
「evidence는 남긴다」 규칙 실측) — `grep -c "SegmentMissing" ml-engine/src/ml_engine/
inference/distribution.py`가 다시 매치를 내(5D-2 원형의 클래스 정의 복원 확인).
④ `uv sync --frozen --all-extras` **exit 0** · `uv run mypy --strict src/ml_engine`
**exit 0**(`Success: no issues found in 54 source files`).
⑤ `uv run python -m pytest tests -q -m 'not legacy_parity'` **exit 0**,
**661 passed, 7 deselected** — 이 slice 이전 baseline(commands.md의 baseline 대조와 동일
수치)과 일치, 되돌림이 5D-2 종결 상태로 정확히 복귀했음을 수치로 확인.
⑥ 게이트: `uv run ruff check .`·`uv run lint-imports`·`uv run python tools/design_ratchet.py
--check`·`uv run python tools/reuse_provenance_check.py` **전부 exit 0**. 루트에서
`./gradlew --no-build-cache --no-daemon :leakPatternGate` **exit 0**(`BUILD SUCCESSFUL`) —
되돌리지 않은 `reports/evidence/m5/5d3/`가 게이트를 붉히지 않음을 확인.

## 되돌리면 무엇이 복귀하는가

5D-2 종결 상태(global-only) — `SegmentMissing` 마커 복원, `SampleSegment.agency`/`category`가
다시 `str`, `DistributionRequest.from_proto`가 표본마다 `SegmentMissing()`만 낸다,
`SampleRejectionReason`이 8종(`SEGMENT_REASON_NOT_ALLOWED` 소멸), `resolve_text_fact` 공개
승격 소멸(`_resolve_text_fact` 사설 함수로 복귀). **5D-2 알려진 제한 1·8이 다시 유효**해진다
(`OPEN-5D2-SAMPLE-SEGMENT` 재개방) — golden `ml-kernel-011`의 `segment_support == DIRECT`
갈래는 다시 K5 직접 호출로만 검증된다(서빙 경로 도달 불가로 복귀).

## 하네스 레인 변경

없음 — `git log --oneline d09666..HEAD -- CLAUDE.md .claude/`가 빈 목록(이 slice 범위에서
하네스 경로 편집 없음).
