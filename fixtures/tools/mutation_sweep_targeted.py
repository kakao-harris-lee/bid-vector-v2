#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""표적 변이 + 표기 변형 스윕 — **분류를 결정하지 않는다.**

분류의 판정은 `mutation_sweep_adversarial.py` 가 낸다. 이 스크립트가 재는 것은 둘.

  1. **앞선 리뷰 라운드가 실증한 변이체가 지금 계약에 잡히는가** — B7 이 낸 세탁
     변이체(`money-basis-006` 전체 · 과세 단독 · provenance 단독 · corpus 적격 단독) ·
     `floor-shortfall-001` 의 0% 렌더 · `verdict-004` 의 override 통과 등.
  2. **projection 피연산자의 대소문자 변형이 술어를 우회하는가** — B8 `findings[0]` 이
     빠져나간 구조적 원인이 이 갈래의 부재였다(verifier r16 F-8). `CASE_VARIANTS` 가
     projection 전건에서 기계로 생성한다.

**표적 목록은 앞선 라운드의 기록이다.** 강등으로 계약 필드를 잃은 case 는 이제
`authoritative` 집합 밖이라 자동으로 건너뛴다 — 목록에서 지우지 않는 것은 그 자리가
**어느 라운드가 무엇을 실증했는지**의 감사 추적이기 때문이다.

**실제 fixture 는 건드리지 않는다** — 변이는 기대값 JSON 의 격리된 메모리 사본에서만
일어나고 이 스크립트는 아무 파일도 쓰지 않는다.

실행:

    python3 fixtures/tools/mutation_sweep_targeted.py
    python3 fixtures/tools/mutation_sweep_targeted.py --crosscheck-pyyaml
    python3 fixtures/tools/mutation_sweep_targeted.py --manifest <경로>
"""
import argparse
import copy
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import manifest_contract as mc  # noqa: E402

DELETE = mc.DELETE

# (case, [(경로, 변이값), ...], 이 변이가 그 case 의 `verifies` 를 거짓으로 만드는 이유)
MUTANTS = [
    ("money-basis-006", [("$.vatTreatment", "INCLUSIVE"), ("$.provenance", "Clean"),
                         ("$.eligibleForAuthoritativeCorpus", True), ("$.reasonCode", DELETE)],
     "B7 재현 — legacy 값이 Inclusive 로 승격되고 provenance 가 Clean 이 된다"),
    ("money-basis-006", [("$.vatTreatment", "INCLUSIVE")], "과세만 세탁"),
    ("money-basis-006", [("$.provenance", "Clean")], "provenance 만 승격"),
    ("money-basis-006", [("$.eligibleForAuthoritativeCorpus", True)], "corpus 적격만 뒤집기"),
    ("floor-shortfall-001", [("$.renderedAsZeroPercent", True)], "측정 불가가 0% 로 렌더된다"),
    ("verdict-004", [("$.overrideOutcome", "Accepted")], "거부돼야 할 override 가 통과된다"),
    ("money-basis-002", [("$.comparedBases", ["BASE_AMOUNT", "ESTIMATED_PRICE"])],
     "basis 가 다른데도 비교가 수행됐다고 주장한다"),
    ("rate-unit-003", [("$.outcome", "Accepted")], "미선언 입력이 수용된다"),
    ("license-006", [("$.uncertainReason", "OperatorLicensesNotDeclared")], "대조군 — 무변이"),
]

# **승인 대기로 남긴 자리** — 고치지 않기로 한 자리라 수정 뒤에도 통과해야 정상이다.
PENDING = [
    ("rate-unit-003", [("$.outcome", "Accepted")], "outcome 이 가족 B 자작 토큰"),
    ("rate-unit-004", [("$.outcome", "Accepted")], "동상"),
    ("money-basis-002", [("$.comparedBases", ["BASE_AMOUNT", "ESTIMATED_PRICE"])],
     "경로 간 동등 술어가 없다"),
    ("verdict-004", [("$.overrideOutcome", "Accepted")], "overrideOutcome 이 가족 B 자작 토큰"),
    ("floor-shortfall-006", [("$.outcome", "Rejected")], "outcome 이 가족 B 자작 토큰"),
    ("money-basis-006", [("$.vatTreatment", "INCLUSIVE")], "B8: 대문자 표기 단독"),
    ("money-basis-006", [("$.vatTreatment", "Inclusive")], "B8: 승인 문면 표기 단독 — 이것이 빠져나갔다"),
    ("money-basis-006", [("$.vatTreatment", "InClUsIvE")], "B8: 임의 대소문자 혼합"),
]


def notation_variants(cases):
    """projection 이 건 값의 표기를 흔든다 — 대소문자 변형 적대 집합(verifier r16 F-8)."""
    variants = []
    for cid, case in cases.items():
        for entry in case.get("verified_projections") or []:
            operand = entry["operand"]
            for variant in (operand.upper(), operand.lower(),
                            operand[:1].lower() + operand[1:].upper()):
                if variant != operand:
                    variants.append((cid, [(entry["path"], variant)],
                                     "표기 변형 %r (%s)" % (variant, entry["path"])))
    return variants


def run(cases, mutants):
    passed = []
    for cid, muts, why in mutants:
        if cid not in cases:
            continue  # 강등된 case 는 대상이 아니다
        case = cases[cid]
        mutated = copy.deepcopy(mc.load_expected(case))
        for path, value in muts:
            mc.set_path(mutated, path, value)
        ok = mc.holds(case, mutated)
        print("%-26s %-9s %s" % (cid, "PASSES" if ok else "caught", why))
        if ok:
            passed.append((cid, [p for p, _ in muts]))
    return passed


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--manifest", default=mc.MANIFEST)
    ap.add_argument("--crosscheck-pyyaml", action="store_true",
                    help="PyYAML 이 있으면 manifest reader 결과를 그것과 대조한다")
    args = ap.parse_args(argv)

    if args.crosscheck_pyyaml:
        print("pyyaml crosscheck OK — cases", mc.crosscheck_pyyaml(args.manifest))

    cases = mc.authoritative_cases(args.manifest)

    print("%-26s %-9s %s" % ("case", "mutant", "why"))
    passed = run(cases, MUTANTS + notation_variants(cases))
    print()
    print("위반 변이체가 통과한 자리:", passed if passed else "없음")
    print("  ※ `대조군 — 무변이` 줄은 변이가 없어 통과가 정상이고, 위 목록에 그 구성으로 든다.")

    print("\n--- 승인 대기 (고치지 않은 자리) ---")
    run(cases, PENDING)
    return 0


if __name__ == "__main__":
    sys.exit(main())
