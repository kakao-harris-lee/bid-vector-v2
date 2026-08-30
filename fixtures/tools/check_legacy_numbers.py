#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""numeric discipline 검사.

`docs/discovery/data-dictionary.md` §12.1이 `legacy-behavior`로 등재한 수 가운데
**비정수 율/배수/오차**와 **금액 구간 경계 셋**이 fixture 의 input·expected 에
나타나지 않는지 검사한다.

정수 counts(`15` 복수예비가격 개수 · `12` confidence 계수 · `2`·`4`·`6` 반올림 자리수)와
sentinel `0`은 **검사에서 뺀다** — 그 수들은 자기 축 밖에서 흔한 셈이라 문자열 일치가
채택을 뜻하지 않는다. 그 축에서의 채택 여부는 사람이 읽는다.
"""
import json, os, sys, glob

FORBIDDEN = {
    0.90: "B1 사정률 분모 필터 밴드 하한", 1.10: "B1 상한",
    0.8: "B2 사정률 관측 편입 밴드 하한", 1.2: "B2 상한",
    0.001: "B3 사정률 제외 밴드 반폭(1e-3)",
    0.5: "B4/B5 하한", 1.5: "B4/B5 상한 · percent 판별 임계",
    0.30: "B6 게시 하한율 신뢰 밴드 하한", 0.995: "B6 상한",
    0.7: "B7 투찰율 클램프 하한", 1.4: "B7 상한",
    1.15: "기초금액÷추정가격 신뢰 상한", 0.05: "그 마진 성분",
    0.000001: "provenance 정수 허용 오차(1e-6)",
    0.01: "provenance VAT 허용 오차", 1.1: "VAT 파생 판정 배수",
    0.89745: "법정 하한율 신율", 0.88745: "법정 하한율 신율", 0.87495: "법정 하한율 신율",
    0.87745: "법정 하한율 구율", 0.86745: "법정 하한율 구율", 0.85495: "법정 하한율 구율",
    1000000000: "하한율 표 구간 경계", 5000000000: "하한율 표 구간 경계",
    10000000000: "하한율 표 구간 경계",
    0.15: "투찰가 밴드 위치 조정 계수", 0.02: "같음", 0.85: "같음",
    0.06: "confidence 계수", 0.08: "confidence 계수", 0.07: "confidence 계수",
    0.54: "confidence 계수", 0.2: "confidence 계수", 0.18: "confidence 계수",
    0.45: "confidence 계수/fallback", 0.95: "confidence 클램프",
    0.58: "price regime confidence", 0.86: "price regime confidence",
    0.84: "price regime confidence", 0.82: "price regime confidence",
    -1.0: "미지 범주 피처 코드",
}

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def walk(o, path, hits):
    if isinstance(o, dict):
        for k, v in o.items():
            walk(v, path + "." + k, hits)
    elif isinstance(o, list):
        for i, v in enumerate(o):
            walk(v, "%s[%d]" % (path, i), hits)
    elif isinstance(o, (int, float)) and not isinstance(o, bool):
        for bad, why in FORBIDDEN.items():
            if o == bad:
                hits.append((path, o, why))
    elif isinstance(o, str):
        try:
            n = float(o)
        except ValueError:
            return
        for bad, why in FORBIDDEN.items():
            if n == bad:
                hits.append((path + " (string)", o, why))


def main():
    total = 0
    for f in sorted(glob.glob(os.path.join(ROOT, "input", "*.json")) +
                    glob.glob(os.path.join(ROOT, "expected", "*.json"))):
        hits = []
        walk(json.load(open(f)), "", hits)
        if hits:
            total += len(hits)
            for p, v, why in hits:
                print("HIT %-46s %-38s %s  (%s)" % (os.path.relpath(f, ROOT), p, v, why))
    print("total legacy-number hits:", total)
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
