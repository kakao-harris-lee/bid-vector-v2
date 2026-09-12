# 레인 계약 — M5 / 5D-golden · `ml-kernel` 커널 golden corpus (curator 병행 레인)

> **지위**: 운영자 결정 **D-M5-7 (a)**(2026-09-12) — 커널 golden 은 curator 가
> `authored-from-approved-spec` 으로 **신설**하고 5D 구현 레인과 **병행**한다(별도 worktree).
> 이 레인은 코드를 만들지 않는다 — fixture · manifest · evidence 뿐이다.
> 정본 규약은 `data-extract.md`(§1 분류 · §3 manifest schema · §4 ML 7항목 · §5 추출 절차 · §7 금지)이고
> 형식은 기존 `ml-boundary` 4건을 그대로 따른다.

```yaml
milestone: m5
slice: 5d-golden
role: fixture-curator
worktree: /Users/harris/Development/private/bid-vector-v2-m5cur
branch: m5-5d-golden/2026-09-12
base_sha: f5020aa982e6c5ade01f1660c0568dcd75c2fa7e   # origin/main (PR #10 머지 = 5A·5B 실물 포함)
in_scope:
  - fixtures/input/ml-kernel-0*.json                 # 신설 14
  - fixtures/expected/ml-kernel-0*.json              # 신설 14
  - fixtures/manifest.yaml                           # cases 14 추가 + uncovered_axes 「ML」 축 갱신
  - fixtures/tools/mutation_sweep_adversarial.py     # ASSERTED · NULL_ASSERTED · ADVERSARIAL_VALUE 등재
  - reports/evidence/m5/5d-golden/**
out_of_scope:
  - ml-engine/** 전부 — 코드 0. `tests/inference/golden/` 소비 자리는 5D 구현 레인 소유
  - 다른 도메인의 기존 case · 기존 기대값 · 기존 분류 (무접촉)
  - 정책 값의 승인 (OPEN-5D-POLICY-VALUES) · slice 5D 착수 계약의 확정
  - push · merge · 배포
acceptance_commands:
  - "python3 fixtures/tools/mutation_sweep_adversarial.py"          # G-1
  - "python3 fixtures/tools/mutation_sweep_adversarial.py --crosscheck-pyyaml"  # G-2
  - "python3 fixtures/tools/mutation_sweep_targeted.py"             # G-3
  - "python3 fixtures/tools/check_legacy_numbers.py"                # G-4 (ml-kernel 적중 0 — 아래 단서)
  - "python3 fixtures/tools/manifest_prose_consistency.py"          # G-5
  - "grep -nEi -f config/quality/leak-patterns.txt fixtures/input/ml-kernel-*.json fixtures/expected/ml-kernel-*.json"  # G-6
rollback: |
    in_scope 경로 한정. 신설 28 파일 삭제 + `fixtures/manifest.yaml` · `fixtures/tools/mutation_sweep_adversarial.py`
    의 이 레인 hunk 만 되돌린다(둘 다 공유 파일 — 다른 레인의 줄을 건드리지 않는다). 절차는 `rollback.md`.
```

## 하네스 레인 변경 (상시 절)

`git log --oneline f5020aa..HEAD -- CLAUDE.md .claude/` — 착수 시 없음. 리뷰 요청 시점 재실행.

## 이 레인이 세운 규율 — 왜 정책 값이 synthetic 인가

`fixtures/manifest.yaml` 머리말 3 이 요구하는 형태를 그대로 적용했다. **`data-dictionary.md` §12.1 이
`legacy-behavior` 로 등재한 수를 input·expected 어느 쪽에도 쓰지 않는다.** 5D 가 이식하는 커널의 상수는
거의 전부 그 표에 있거나(`0.8`·`1.2` 사정률 밴드 · `0.5`·`1.5` 투찰비 밴드 · `0.7`·`1.4` 클램프 · `-1.0`)
아직 승인되지 않은 `OPEN-5D-POLICY-VALUES` 후보값이다(`1.2816` · `0.24/0.52/0.24` · κ `12`/`40` · `40` 행).

그래서 **모든 정책 값을 입력이 선언하는 synthetic 값으로 받고, 기대값이 그 값이 아니라 규칙에만 의존하게
설계했다.** 임계는 `24`(legacy `40` 아님), κ 는 `15`/`30`(legacy `12`/`40` 아님), z 는 `1.25`(legacy `1.2816`
아님), 클램프는 `0.6`/`1.3`(legacy `0.7`/`1.4` 아님)이다. 시나리오 부호도 `-1` 대신
`"NEGATIVE"`/`"NEUTRAL"`/`"POSITIVE"` 토큰이다 — `-1.0` 이 §12.1 등재 리터럴이라 기계 검사가 축을 가르지 못한다.

**결과로 이 corpus 는 출하 정책 값을 하나도 주장하지 않는다.** 잠그는 것은 커널의 규칙과 닫힌식이고,
값의 승인은 `OPEN-5D-POLICY-VALUES` 가 계속 소유한다. 각 case 의 `not_covered` 가 그것을 적는다.

## 어느 토큰을 정확 비교로 잠갔는가

**승인 문면이 있는 이름만** `verified_paths` 에 뒀다 — M2 계약(`error.proto` `UnmeasurableReason` 셋,
`prediction.proto` `CandidateLabel` 셋 · `SegmentSupport`)과 `data-dictionary.md` §6.4 인용 블록
(`Observed` · `NoObservation`). 5D 착수 계약이 신설한 `UnmeasurableDetail`(`NEVER_TRAINED` 등)은
**승인 문면이 없어** 잠그지 않았다. ML-02 acceptance 가 요구하는 것은 그 이름이 아니라 **구별**이므로
`ml-kernel-001`↔`002` 에 `differs-from-case` projection 을 달아 「두 사유가 서로 다르다」만 계약했다.

그 밖의 판정은 전부 **불리언·셈·십진 문자열**로 우회해 잠갔다 — `koneps-collection` 이 세운 관례와 같다.

## 실행하지 않은 것

**legacy Python 을 한 줄도 실행하지 않았다.** `bid-vector/` 아래 K5·K6·K7 소스는 **산식 확인용으로 읽기만**
했고(`ed4b06c`), legacy 출력은 기대값의 근거가 아니다. 모든 수는 승인 문면에서 유도했고 유도 각 줄을 기대값
파일의 `derivation` 이 싣는다(`ml-kernel-010`·`011`·`013`). DB·API·LLM·알림 접근 0.
