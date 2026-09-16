# M5/5F-1 — reuse.md

**이식 없음(N/A).** 이 slice 는 `D-5F1-1`(값만 바꾼다 — `src/**` 무편집)에 따라 새 모듈을
작성하지 않는다. `tools/reuse_provenance_check.py`가 대상으로 삼는 `Reuse: <원본 경로>@
<commit>` docstring 을 가진 신규 파일이 없다.

## 값 둘의 근거(재활용 판정과 같은 성질 — 지어낸 수치가 아님)

- `scenario.clamp_max: 1.4 → 1.0` — legacy 값(`1.4`, `ed4b06c`)을 대체한다. 새 값
  `1.0`은 legacy 코드가 아니라 **이 저장소 자신의 계약**(`Candidate.bid_rate` ≤ 1,
  D-2B-8·D-2F-4)에서 역산했다 — 계약이 허용하는 상한 그 자체를 clamp 상한으로 썼다.
- `assessment.agency_sample_threshold: 10` — legacy 에 대응 상수가 없다
  (`reports/evidence/m5/5d2/policy-values.md`가 이미 확인). 지어내지 않고 **승인된
  golden case `ml-kernel-011`이 실제로 쓰는 synthetic 값 10**(후보 (b))을 옮겼다 —
  golden fixture 자체는 편집하지 않았다(무접촉, `git diff --name-status` 로 확인,
  `golden-manifest.json` 참고).

## 재사용한 것(이 저장소 자신의 기존 산출물)

- `_completed_case_inference_policy()`(`tests/serving/test_kotlin_rules_parity.py`)를
  임시 사본 생성 대신 `load_inference_policy(_SHIPPED_POLICY_PATH)` 직접 호출로
  단순화 — 출하 파일이 이제 완성 case 이므로 5D-2 가 남긴 임시 사본 패턴이 불필요해졌다.
  같은 패턴(변이 확인용 임시 사본)은 `_inference_policy_with_clamp_max`로 새로
  남겨 `scenario.clamp_max` 변이 test 에만 쓴다.
