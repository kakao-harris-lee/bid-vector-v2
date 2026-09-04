#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""확장 적대 집합 스윕 — **분류를 결정하는 스윕이다.**

판정: **`authoritative` = 확장 적대 집합에서 위반 변이체 통과 0**
(운영자 결정 2026-09-02 「동결+강등」, Codex 재리뷰 B9 가 쓴 수).
통과가 하나라도 있으면 그 case 는 기계적으로 `insufficient-evidence` 로 내려간다 —
**리뷰 압력 아래서 술어를 발명하지 않는다.** 어휘를 늘리는 것은 운영자 결정의 일이고,
**2026-09-05 decision 18** 이 `is-present`·`differs-from-path`·`differs-from-case` 셋을
더했다(2026-09-02 동결의 개정). 정의의 정본은 `manifest.yaml` `schema.extensions` 다.

방법·갈래 정의의 정본은 `reports/evidence/m0/0e/commands.md` **C-15** 다.
여기서 도는 갈래는 그 항목의 확장이다.

  (a) `verifies` 가 **주장하는 필드의 삭제** — 아래 `ASSERTED` 가 case 별로 든다.
  (b) projection 경로의 **null 치환** — projection 전건에서 기계로 생성한다.
  (c) projection 피연산자와 다른 **확정 토큰 치환** — 같음.
  (d) 기대값이 **`null` 인 필드의 non-null 치환** — `NULL_ASSERTED` 가 case 별로 든다.
  (b′) `differs-from-path` 의 **거울 경로 값 대입**, (c′) `differs-from-case` 의 **거울 case
  값 대입** — 술어가 막는 「둘이 같아짐」을 직접 만든다. 갈래를 술어별로 가르는 것은
  [projection_mutants] 이고, 그 함수가 **어휘 밖 술어를 예외로 낸다**.

`main()` 이 스윕 전에 `manifest_contract.self_check()` 를 돌린다 — 판정이 술어 구현 위에
서므로 구현이 정의와 맞는지 먼저 잰다. 그 검사는 corpus 를 읽지 않는다.

**(a) 만 사람의 판단이다.** 어느 필드가 `verifies` 의 주장에 드는지는 기계가 읽지
못한다. 그래서 `ASSERTED` 의 각 줄이 **근거를 오른쪽 주석에 싣는다** — 이 파일에서
사람 판단이 개입한 자리는 그 열이 전부다.

**(a) 의 기준은 Codex 재리뷰 B10(high)이 조였다** — `verifies` **문면만이 아니라
그것이 근거로 인용한 결정이 정하는 것**(사유 토큰 · 계약 형태 · 관계 주장의 축)**도
주장에 든다.** 이 레인의 앞선 해석선(「사유를 괄호에 적은 case 만」)은 기각됐다.

**실제 fixture 는 건드리지 않는다** — 변이는 기대값 JSON 의 격리된 메모리 사본에서만
일어나고 이 스크립트는 아무 파일도 쓰지 않는다.

실행:

    python3 fixtures/tools/mutation_sweep_adversarial.py
    python3 fixtures/tools/mutation_sweep_adversarial.py --crosscheck-pyyaml
    python3 fixtures/tools/mutation_sweep_adversarial.py --manifest <경로>

`--manifest` 는 강등 **이전** manifest 를 물려 그날의 판정을 재현할 때 쓴다
(기대값·입력 파일은 강등에서 바뀌지 않았으므로 옛 manifest + 현재 fixture 로 성립한다).
"""
import argparse
import copy
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import manifest_contract as mc  # noqa: E402


# (a) `verifies` 가 존재·값을 주장하는데 계약 밖인 경로 — 각 case 의 `verifies` 문면과
#     그것이 인용한 근거에서 도출한 **사람의 판단**이다. 근거는 오른쪽 주석.
ASSERTED = {
    "license-006":                 ["$.uncertainReason"],                  # U-5 sealed enum 의 사유 토큰
    "capacity-gate-003":           ["$.suitabilityAxisAffected"],          # OPEN-QUAL-08 분할의 양(陽)의 절반
    "floor-threshold-001":         ["$.criticalAssessmentRate.fraction"],  # 관계 주장의 축(임계값)
    "floor-threshold-003":         ["$.criticalAssessmentRate.fraction"],
    "rate-unit-003":               ["$.outcome"],                          # "거부된다"
    "rate-unit-004":               ["$.outcome"],                          # "거부된다"
    "money-basis-001":             ["$.outcome", "$.reasonCode"],          # "사유와 함께 거부된다"
    "money-basis-002":             ["$.comparedBases"],                    # "basis 가 같으면"
    "money-basis-004":             ["$.outcome"],                          # "들어갈 수 없다"
    "money-basis-006":             ["$.reasonCode"],                       # "승격되지 않는다" 의 사유
    "floor-shortfall-006":         ["$.outcome"],                          # "정상 처리된다"
    "base-amount-provenance-004":  ["$.outcome"],                          # "거부된다"
    "base-amount-provenance-005":  ["$.outcome"],                          # "거부된다"
    "verdict-004":                 ["$.overrideOutcome", "$.reasonCode"],  # "사유와 함께 거부되며"
    "rate-unit-001":               ["$.outcome"],                          # "명시 변환된다" = 통과 상태
    "rate-unit-002":               ["$.outcome"],                          # "그대로 통과한다"
    "rate-unit-005":               ["$.outcome"],                          # "선언이 개연성을 이긴다" = 수용
}

# (c) 갈래가 쓰는 대체 토큰. 목록에 없는 피연산자는 `Other` 로 친다.
OTHER_TOKEN = {"Inclusive": "Exclusive", "Clean": "DerivedVat", "Unmeasurable": "Computed"}

# (a′) 갈래가 쓰는 **값 변이**. `ASSERTED` 의 경로에 삭제와 **별도로** 건다 —
# 삭제만으로는 「경로는 있는데 값이 뒤집힌」 산출을 잡지 못한다(Codex B12 high 진단:
# *"스윕은 존재하는 경로만 검증하고, 경로가 덮지 않는 주장은 탐지 못 한다"*).
# 불리언은 반전하고, 문자열은 아래 표의 적대 토큰(없으면 `Other`)으로 바꾼다.
ADVERSARIAL_VALUE = {"Accepted": "Rejected", "Rejected": "Accepted",
                     "Comparable": "Rejected", "Uncertain": "Eligible",
                     # 1B 계약 어휘(운영자 결정 2026-09-05 decision 19). 상태 토큰의 적대값은
                     # **반대 상태**다 — `Other` 같은 무의미 토큰보다 강한 변이다.
                     "Known": "Absent", "Absent": "Known"}

# (d) 기대값이 **`null`** 인데 `verifies` 가 그 **부재**를 주장하는 경로 — 사람의 판단이다.
#     Codex B14 high 의 진단: 적대 집합이 null 기대값을 한 번도 변이하지 않아 「강등 대상 0」이
#     건전성의 증거가 아니었다. 아래 다섯이 심판이 쓴 최소 집합이다.
NULL_ASSERTED = {
    "floor-shortfall-005": ["$.frequency"],   # "판정 불가로 전이" — 값이 나오면 전이가 아니다
    "floor-shortfall-001": ["$.frequency"],   # "값이 아니라 사유 있는 측정 불가"
    "license-009":         ["$.requiredLicenses"],  # "수집 실패" — 요건이 있으면 수집된 것이다
    "license-007":         ["$.requiredLicenses"],  # "요건 원문이 없으면" — 없음이 주장이다
}
NULL_REPLACEMENTS = [0.0, {"numerator": 0, "denominator": 149}, 0,
                     {"numerator": 0, "denominator": 0}, "0%"]


def projection_mutants(cid, case, entry, registry):
    """술어 하나가 잡아야 하는 변이체. **술어마다 갈래가 다르다.**

    정의의 정본은 `manifest.yaml` 의 `schema.extensions.verified_projections` 이고
    갈래는 그 「잡는 것」 열의 기계 표현이다(운영자 결정 2026-09-05 decision 18).
    `not-equals` 의 갈래는 동결분 그대로다 — 늘리지도 줄이지도 않았다.
    """
    path, projection = entry["path"], entry["projection"]
    if projection == "not-equals":
        return [(cid, path, None, "(b) projection 경로 null"),
                (cid, path, OTHER_TOKEN.get(entry["operand"], "Other"), "(c) 다른 확정 토큰")]
    if projection == "is-present":
        return [(cid, path, mc.DELETE, "(a) projection 경로 삭제"),
                (cid, path, None, "(b) projection 경로 null")]

    rows = [(cid, path, mc.DELETE, "(a) projection 경로 삭제"),
            (cid, path, None, "(b) projection 경로 null")]
    if projection == "differs-from-path":
        # (b′) 거울 **경로**의 값을 이 경로에 대입한다 — 술어가 막는 「둘이 같아짐」이다.
        status, twin = mc.get(mc.load_expected(case), entry["operand"])
        why = "(b′) 거울 경로 값 대입"
    elif projection == "differs-from-case":
        # (c′) 거울 **case** 의 같은 경로 값을 대입한다. 거울은 강등 여부와 무관하게 찾는다 —
        # 대조 대상은 분류가 아니라 그 case 의 기대값이다.
        mirror = mc._mirror_case(case, entry["operand"], registry, mc.MANIFEST)
        status, twin = mc.get(mc.load_expected(mirror), path)
        why = "(c′) 거울 case 값 대입"
    else:
        raise mc.ManifestFormatError("어휘 밖의 술어: %r" % projection)
    if status == "OK":
        rows.append((cid, path, twin, why))
    return rows


def build_mutants(cases, registry=None):
    mutants = []
    for cid, paths in ASSERTED.items():
        if cid not in cases:
            continue  # 강등된 case 는 대상이 아니다
        for path in paths:
            mutants.append((cid, path, mc.DELETE, "(a) verifies 주장 필드 삭제"))
            ok, cur = mc.get(json.load(open(cases[cid]["expected_file"])), path)
            if not ok:
                continue
            if isinstance(cur, bool):
                flipped = not cur
            elif isinstance(cur, str):
                flipped = ADVERSARIAL_VALUE.get(cur, "Other")
            else:
                continue      # 수·객체·null 은 이 갈래의 대상이 아니다
            mutants.append((cid, path, flipped, "(a') verifies 주장 필드 값 변이"))
    for cid, paths in NULL_ASSERTED.items():
        if cid not in cases:
            continue
        for path in paths:
            ok, cur = mc.get(json.load(open(cases[cid]["expected_file"])), path)
            if not ok or cur is not None:
                continue      # 기대값이 null 인 자리만 이 갈래의 대상이다
            for rep in NULL_REPLACEMENTS:
                mutants.append((cid, path, rep, "(d) null 기대값의 non-null 치환"))
    for cid, case in cases.items():
        for entry in case.get("verified_projections") or []:
            mutants.extend(projection_mutants(cid, case, entry, registry))
    return mutants


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--manifest", default=mc.MANIFEST)
    ap.add_argument("--crosscheck-pyyaml", action="store_true",
                    help="PyYAML 이 있으면 manifest reader 결과를 그것과 대조한다")
    args = ap.parse_args(argv)

    if args.crosscheck_pyyaml:
        print("pyyaml crosscheck OK — cases", mc.crosscheck_pyyaml(args.manifest))

    # 술어 어휘의 실행이 정의와 맞는지 먼저 잰다 — 스윕의 판정이 그 위에 서기 때문이다.
    # corpus 를 읽지 않는 격리 검사라 이 스윕의 수치에 영향을 주지 않는다.
    print("술어 self-check OK — 검사", mc.self_check())

    registry = mc.all_cases(args.manifest)
    cases = mc.authoritative_cases(args.manifest)
    rows, demoted = [], set()
    for cid, path, value, why in build_mutants(cases, registry):
        mutated = copy.deepcopy(mc.load_expected(cases[cid]))
        mc.set_path(mutated, path, value)
        passes = mc.holds(cases[cid], mutated, cases=registry, manifest=args.manifest)
        rows.append((cid, path, why, passes))
        if passes:
            demoted.add(cid)

    for row in sorted(rows):
        print("%-28s %-24s %-26s %s" % (row[0], row[1], row[2], "PASSES" if row[3] else "caught"))
    print()
    print("강등 대상 (위반 변이체 통과):", len(demoted))
    for cid in sorted(demoted):
        print("  -", cid)
    print("잔존 authoritative:", len(cases) - len(demoted))
    return 0


if __name__ == "__main__":
    sys.exit(main())
