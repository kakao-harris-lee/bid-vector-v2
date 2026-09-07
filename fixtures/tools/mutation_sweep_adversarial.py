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

종료 코드(2026-09-06, 1B-c 이월 — verifier r1 *"exit code 로 강제되지 않는다"*):

    0  정상 — 위반 변이체 통과 0(강등 대상 0)
    1  위반 변이체 통과 ≥1 — 통과한 (case, 경로, 갈래) 를 stderr 에 한 줄씩 낸다
    2  도구·manifest 형식 오류 — `ManifestFormatError` · 파일 열기 실패 · 기대값 JSON 파싱 실패

판정 로직은 그대로다 — 「강등 대상」 수치가 종료 코드로 나갈 뿐이라, acceptance 가
`check` 처럼 종료 코드 하나로 판정을 읽는다.
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
    # verifier r2 N-5 — bap 001·002·003 은 적대 스윕에서 변이체가 하나도 생성되지 않아
    # (ASSERTED·NULL_ASSERTED·verified_projections 어디에도 없었다) 「강등 대상 0」이
    # 공허했다(1D 이전부터의 이월 부채, r1 N-1). first-match 결과(`classification`)와
    # 그 근거(`firstMatchedRule`/`evidence.firstMatchedRule`)·`policyVersion`은
    # `verifies`가 직접 주장하는 값이다.
    "base-amount-provenance-001": ["$.classification", "$.evidence.firstMatchedRule", "$.policyVersion"],
    "base-amount-provenance-002": ["$.classification", "$.firstMatchedRule", "$.policyVersion"],
    "base-amount-provenance-003": ["$.classification", "$.firstMatchedRule", "$.policyVersion"],
    "floor-threshold-001":         ["$.criticalAssessmentRate.fraction"],  # 관계 주장의 축(임계값)
    "floor-threshold-003":         ["$.criticalAssessmentRate.fraction"],
    # curator 발견(2026-09-06) — 승격 case(decision 28)의 `verifies`가 **경계 등가 자체**를
    # 주장하므로 `$.sampleIsShortfall`(그 경계 판정의 결과)도 여기 든다. 001·003과 달리 이
    # case는 방향이 아니라 등가를 겨눈다.
    "floor-threshold-002":         ["$.sampleIsShortfall", "$.criticalAssessmentRate.fraction"],
    "rate-unit-003":               ["$.representable"],                    # "표현 불가"(1B-c 정정)
    "rate-unit-004":               ["$.representable"],                    # "표현 불가"(1B-c 정정)
    "money-basis-001":             ["$.representable"],                    # "표현 불가"(1B-c 재정의)
    "money-basis-002":             ["$.fact", "$.comparedBases"],          # "basis 가 같으면" + 비교 성립
    "money-basis-004":             ["$.representable"],                    # "표현 불가"(1B-c 재정의)
    "money-basis-005":             ["$.fact", "$.reasonCode"],             # "비교에 들어갈 수 없다" + 그 사유
    # `money-basis-006` 의 `$.reasonCode` 는 **여기 들지 않는다** — 2026-09-05(1B-c) 판단.
    #   그 case 가 주장하는 것은 **사유가 함께 나온다**(존재)이고 **어느 이름인가**가 아니다:
    #   `LegacyOriginNotPromotable` 은 corpus 운영 규칙의 이름이라 `ReasonCode` enum 에 없고
    #   (`contract_binding.does_not_carry`), decision 19 가 세운 승인 어휘 밖이라 **정확 비교로
    #   잠글 수 없다.** (a′) 갈래는 「그 값을 잠글 자격이 있는 경로」를 전제하므로 이런 자리에는
    #   설 수 없다. 존재 주장은 `verified_projections` 의 `is-present` 가 지고, 그 술어가 (a)
    #   삭제와 (b) null 을 **여기서 잃는 것보다 넓게** 생성한다 — 적대 집합이 줄지 않는다.
    #   **값을 잠글 자격이 있는 경로를 이 목록에서 빼는 것은 다른 일이다** — 그것은 우회이고
    #   위협 모델 (3)이 막는다.
    "floor-shortfall-006":         ["$.outcome"],                          # "정상 처리된다"
    "base-amount-provenance-004":  ["$.outcome"],                          # "거부된다"
    "base-amount-provenance-005":  ["$.outcome"],                          # "거부된다"
    "verdict-004":                 ["$.overrideOutcome", "$.reasonCode"],  # "사유와 함께 거부되며"
    # `rate-unit-001`·`002`·`005` 의 `$.fact`·`$.conversionDivisor` 는 **여기 들지 않는다** —
    #   2026-09-05 정정. `ofPercent`/`ofFraction` 은 `Rate` 를 직접 내고 제수는 `private const` 라
    #   **계약이 방출하지 않는 축**이다(`money-basis-006` 의 `$.reasonCode` 와 같은 갈래).
    #   변환 성공은 `$.rate.fraction` 이 진다 — 실패하면 `Rate` 자체가 없다.
    "rate-unit-001":               ["$.rate.fraction"],                    # "명시 변환된다" 의 결과
    "rate-unit-002":               ["$.rate.fraction"],                    # "배율 없이 그대로"
    "rate-unit-005":               ["$.rate.fraction"],                    # "선언이 개연성을 이긴다"
    # M1/1E — curator 판단(2026-09-06). money-basis-003 은 decision 29 로 authoritative 로
    #   되돌아갔다 — "같은 쌍이 감시·검색 경로에서 같은 답을 낸다"가 verifies 의 주장이다.
    #   `$.perPath.*.outcome`(`Comparable`)은 여기 들지 않는다 — 미승인 토큰이라 값을
    #   잠글 자격이 없다(change_history, money-basis-006 의 `$.reasonCode` 와 같은 갈래).
    "money-basis-003": [
        "$.pathsAgree",
        "$.perPath.MONITORING_FILTER.result",
        "$.perPath.MONITORING_FILTER.basisUsed",
        "$.perPath.SEARCH_QUERY.result",
        "$.perPath.SEARCH_QUERY.basisUsed",
    ],
    # STR-01 acceptance 넷·STR-02 acceptance 넷(decision 30) — `$.verdict` 는 모든 watch
    # case 의 무게중심이고, `$.matched`/`$.failed` 는 case 마다 그 문면이 직접 드는 규칙이다.
    "strategy-watch-001": ["$.verdict", "$.failed"],    # "완전일치가 아니면 탈락하고 그 규칙이 실린다"
    "strategy-watch-002": ["$.verdict", "$.matched"],   # "AND 결합의 양(陽) — 둘 다 통과"
    "strategy-watch-003": ["$.verdict", "$.failed"],    # "AND 결합의 음(陰) — 한 축 결여로 탈락"
    "strategy-watch-004": ["$.verdict", "$.matched"],   # "게이트 없음 ≠ 모든 공고 통과"(결과 타입에서 구분)
    "strategy-watch-005": ["$.verdict", "$.failed"],    # "필수 키워드가 description 에만 있으면 탈락"
    "strategy-watch-006": ["$.verdict", "$.matched"],   # "같은 키워드가 title/requirements 에 있으면 통과"
    "strategy-watch-007": ["$.verdict", "$.matched"],   # "제외 키워드가 description 에만 있으면 안 떨어짐"
    "strategy-watch-008": ["$.verdict", "$.matched"],   # "중점 지역은 description 포함 전체 텍스트에 매칭"
    # STR-03 acceptance 셋(decision 30) — `$.validation`(sealed variant)과 그 근거.
    "strategy-validation-001": ["$.validation", "$.violations"],  # "review>bidNow 는 모든 경로에서 거부"
    "strategy-validation-002": ["$.validation", "$.violations"],  # "min>max(둘 다 유효값) 거부"
    "strategy-validation-003": [
        "$.validation",
        "$.isConfigured",
        "$.watchRulesEmpty",
    ],  # "설정됐는가"≠"좁히는가"가 서로 다른 값
    # M3/3A — curator 판단(2026-09-07). 신설 `koneps-collection-010`~`018` 은 근거가
    #   조달청 공식 문서의 **항목 명세 행**이라 `source.kind: official-doc` 으로 서고,
    #   `verifies` 가 주장하는 것은 **문서가 선언한 것**(단위·과세·필수성·형식·열거)과
    #   **선언하지 않은 것**(타임존·예산 키 과세·목록 단위)이다. 아래 경로가 그 주장이다.
    #   **부재를 주장하는 자리는 `false` 불리언이라 (a′) 가 뒤집기를 만든다** — 그 자리가
    #   `true` 로 뒤집혀도 통과하면 「문서가 선언하지 않는다」가 계약이 아니었던 것이다.
    "koneps-collection-010": [
        "$.declaredUnit",               # 단위는 선언에서 온다
        "$.conversionDivisor",          # 승인된 제수 100
        "$.rate.fraction",              # 변환 결과
        "$.unitInferredFromMagnitude",  # 값 크기로 되짚지 않는다(ADR 0002 D-4)
    ],
    "koneps-collection-011": [
        "$.vatTreatment",                    # "부가가치세 … 제외"
        "$.currencyUnit",                    # "(원화,원)"
        "$.nullability",                     # 항목구분 옵션
        "$.vatTreatmentInferredFromValue",   # 값 크기로 과세를 되짚지 않는다
        "$.legacyAnnotationUsedAsBasis",     # legacy 주석("부가세 포함")은 근거가 아니다
    ],
    "koneps-collection-012": [
        "$.noticeOrder.value",      # 원문 토큰 "000"
        "$.noticeOrder.type",       # 식별자
        "$.roundTripPreserved",     # 왕복 보존
        "$.widthPreserved",         # 항목크기 3
        "$.equalToUnpaddedToken",   # "000" ≠ "0"
    ],
    "koneps-collection-013": [
        "$.declaredRequired",                      # 항목구분 필수
        "$.envelopeResults[1].codePresent",        # 부재 관측
        "$.envelopeResults[1].treatedAsNormal",    # 부재를 "00" 으로 승격하지 않는다(R-COL-01)
        "$.envelopeResults[1].contractViolation",  # 부재 = 계약 위반
    ],
    "koneps-collection-014": [
        "$.documentDeclaresDatetimeFormat",                    # 형식은 선언돼 있다
        "$.documentDeclaresSourceTimezone",                    # 타임존은 선언돼 있지 않다
        "$.rawTextPreserved",                                  # 원문 보존
        "$.convertibleToInstantWithoutInterpretationRule",     # 규칙 없이는 변환 불가
    ],
    "koneps-collection-015": [
        "$.fields[0].currencyUnit",
        "$.fields[0].documentDeclaresVatTreatment",
        "$.fields[0].vatTreatment",
        "$.fields[1].currencyUnit",
        "$.fields[1].documentDeclaresVatTreatment",
        "$.fields[1].vatTreatment",
        "$.vatTreatmentDefaultedFromNeighbourField",  # 이웃 필드 선언의 전이 금지(§1.2.1)
    ],
    "koneps-collection-016": [
        "$.documentEnumeratedValues",                   # 문서가 넷으로 열거한다
        "$.observed[0].coveredByDocumentEnumeration",   # 열거 안
        "$.observed[1].coveredByDocumentEnumeration",   # 열거 밖(다른 축의 라벨)
    ],
    "koneps-collection-017": [
        "$.documentedBaseAmountKey",                    # 공식 기초금액 키는 bssamt 하나
        "$.candidateKeys[0].declaredInDocument",        # bssAmt — 문서에 없다
        "$.candidateKeys[0].sameConceptAsBaseAmount",
        "$.candidateKeys[1].declaredInDocument",        # bssamt — 문서에 있다
        "$.candidateKeys[1].documentedConcept",
        "$.candidateKeys[1].sameConceptAsBaseAmount",
        "$.candidateKeys[2].declaredInDocument",        # bssAmtPurcnstcst — 문서에 **있다**
        "$.candidateKeys[2].documentedConcept",         # 그러나 개념이 다르다(기초금액순공사비)
        "$.candidateKeys[2].sameConceptAsBaseAmount",   # 부분을 전체 자리에 넣지 않는다
        "$.baseAmountKeyDeclaration.currencyUnit",
        "$.baseAmountKeyDeclaration.documentDeclaresVatTreatment",  # 과세는 선언돼 있지 않다
        "$.remarkSampleUsedAsVatBasis",                 # 비고 필드의 샘플을 근거로 쓰지 않는다
    ],
    "koneps-collection-018": [
        "$.documentDeclaresListFormat",      # 형식은 선언돼 있다
        "$.componentSeparator",
        "$.componentCount",
        "$.documentDeclaresUnit",            # 단위는 선언돼 있지 않다
        "$.documentDeclaresVatTreatment",    # 과세도 선언돼 있지 않다
        "$.normalizedToMoney",               # 그래서 Money 가 아니다(§5.5)
    ],
    # `-027` 은 다른 문서(활용가이드 `pps-openapi-guide`)를 인용한다. 주장은 **표가 무엇을
    #   담는가**와 **무엇을 담지 않는가** 둘이고, 뒤쪽 셋이 `false` 불리언이라 (a′) 뒤집기가
    #   「문서가 정하지 않는다」를 계약으로 세운다.
    # 운영자 승인 2026-09-07(Q-2 B-1~B-10)으로 승격된 열일곱. 각 줄의 경로는 그 case 의
    #   `verified_paths` 부분집합이다 — 계약 밖 경로를 여기 넣으면 변이체가 통과해
    #   기계적으로 강등된다. 미승인 사유·상태 토큰은 두 목록 어디에도 없다.
    "koneps-collection-001": [
        "$.consumedAsDomainValue.synNewKey",
        "$.silentlySwallowed",
        "$.requiresHumanReviewBeforeConsumption",
        "$.unknownFields",
    ],
    "koneps-collection-002": [
        "$.unitInferred",
        "$.accountedInRunReport",
        "$.consumedAsDomainValue.synFloorRt",
        "$.rejectedItems[0].rawName",
    ],
    "koneps-collection-003": [
        "$.estimatedPrice.state",
        "$.foldedToZero",
        "$.presentInPayload",
    ],
    "koneps-collection-004": [
        "$.estimatedPrice.state",
        "$.foldedToZero",
        "$.presentInPayload",
    ],
    "koneps-collection-005": [
        "$.noticeOrder.value",
        "$.noticeOrder.type",
        "$.intConversionPerformed",
        "$.arithmeticDefined",
    ],
    "koneps-collection-006": [
        "$.code",
        "$.label",
        "$.combinedStringLeakedPastParser",
    ],
    "koneps-collection-007": [
        "$.code",
        "$.codeKnown",
        "$.label.state",
        "$.labelInvented",
    ],
    "koneps-collection-008": [
        "$.storedValueOverwritten",
        "$.resultBasis",
        "$.resultVatTreatment",
        "$.resultProvenance",
        "$.incomingReBasedToTargetSlotBasis",
        "$.incomingRecordedBasis",
        "$.incomingRecordedVatTreatment",
    ],
    "koneps-collection-009": [
        "$.resolvedBaseAmount.targetSlot",
        "$.resolvedBaseAmount.basis",
        "$.resolvedBaseAmount.vatTreatment",
        "$.resolvedBaseAmount.provenance",
        "$.storedInCanonicalDirectSlot",
        "$.typeDistinctFromDirect",
        "$.reBasedToBaseAmount",
        "$.arithmeticComparisonWithBaseAmountBasisAllowed",
    ],
    "koneps-collection-019": [
        "$.received",
        "$.normalized",
        "$.dropped",
        "$.normalizedNoticeNumbers",
    ],
    "koneps-collection-020": [
        "$.identityHolds",
        "$.constructed",
        "$.capSkippedDerivedBySubtraction",
        "$.duplicateCountedInsideDropped",
    ],
    "koneps-collection-021": [
        "$.identityHolds",
        "$.constructed",
        "$.negativeResidualFoldedToZero",
    ],
    "koneps-collection-022": [
        "$.terminated",
        "$.truncated",
        "$.progressObserved",
    ],
    "koneps-collection-023": [
        "$.sameIdentity",
        "$.normalizationOwnedByValueObject",
        "$.normalizationRepeatedAtCallSites",
        "$.sameSourceUrlImpliesSameNoticeId",
        "$.normalized",
    ],
    "koneps-collection-024": [
        "$.detailFetchCallCount",
        "$.backoffStateStoredOnDataTable",
    ],
    "koneps-collection-025": [
        "$.sweepResults[0].detailFetchCallCount",
        "$.sweepResults[1].detailFetchCallCount",
        "$.totalDetailFetchCallCount",
    ],
    "koneps-collection-026": [
        "$.results[0].interpretedZone",
        "$.results[0].instantDerived",
        "$.results[1].instantDerived",
        "$.results[1].assumedUtc",
    ],
    "koneps-collection-027": [
        "$.documentedErrorCodes",                    # 16 코드 전수
        "$.documentedErrorCodeCount",
        "$.successCodeInErrorTable",                 # 00 은 에러 표에 없다
        "$.successCodeFromResponseSample",           # 00 은 응답 명세가 준다
        "$.totalDistinctCodes",                      # 16 + 00 = 17 (OPEN-COL-02 문면과 대조)
        "$.documentDeclaresRetryCategory",           # 범주는 문서가 주지 않는다
        "$.appliesToBidPublicInfoServiceDeclared",   # 적용 범위를 문서가 선언하지 않는다
        "$.codes[0].listedInErrorTable",             # 03 — legacy 는 성공으로 두는데 표는 에러로 싣는다
        "$.codes[0].prescribedActionPresent",        # 03 의 조치방안 칸은 비어 있다
        "$.codes[0].declaredSuccessByDocument",
        "$.codes[1].prescribedActionPresent",        # 08 — legacy 가 대상 서비스에서 관측한 코드
        "$.codes[2].prescribedActionPresent",        # 22 — quota 축이 resultCode 에도 있다
    ],
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
                     "Known": "Absent", "Absent": "Known",
                     # M1/1E — `WatchVerdict`(decision 31) 상태 토큰. `NoGate`→`Passed`가
                     # STR-01 acceptance 셋째가 막는 바로 그 접기다("게이트 없음"이 "모든
                     # 공고 통과"로 접히면 안 된다) — 역방향(`Passed`→`NoGate`)은 같은
                     # 위험을 겨누지 않아 넣지 않는다. `"Rejected"` 키는 이미 위에서
                     # `"Accepted"`(다른 도메인의 상태 토큰)로 매핑돼 있다 — 여기서
                     # `"Passed"`로 덮어쓰면 그 매핑이 조용히 깨진다(파이썬 dict 리터럴의
                     # 중복 키는 뒤 값이 이긴다). 그래서 `strategy-watch-001`·`003`·`005`의
                     # `"Rejected"` verdict는 그 기존 매핑(`"Accepted"`)을 그대로 쓴다 —
                     # 도메인 어휘는 아니지만 기대값과 **다른 확정 토큰**이라는 스윕의
                     # 요구는 그대로 만족한다.
                     "Passed": "Rejected", "NoGate": "Passed",
                     # `StrategyValidation`(decision 31) 상태 토큰.
                     "Valid": "Invalid", "Invalid": "Valid"}

# (d) 기대값이 **`null`** 인데 `verifies` 가 그 **부재**를 주장하는 경로 — 사람의 판단이다.
#     Codex B14 high 의 진단: 적대 집합이 null 기대값을 한 번도 변이하지 않아 「강등 대상 0」이
#     건전성의 증거가 아니었다. 아래 다섯이 심판이 쓴 최소 집합이다.
NULL_ASSERTED = {
    "floor-shortfall-005": ["$.frequency"],   # "판정 불가로 전이" — 값이 나오면 전이가 아니다
    "floor-shortfall-001": ["$.frequency"],   # "값이 아니라 사유 있는 측정 불가"
    "license-009":         ["$.requiredLicenses"],  # "수집 실패" — 요건이 있으면 수집된 것이다
    "license-007":         ["$.requiredLicenses"],  # "요건 원문이 없으면" — 없음이 주장이다
    # `rate-unit-003`·`004` 의 `$.rate`(null)는 2026-09-05 정정으로 기대값에서 사라졌다 —
    #   기대값이 `{"representable": false}` 하나이고 그 자리는 `ASSERTED` 가 진다.
    # M1/1E — STR-01 acceptance 셋째 "게이트 없음"이 결과 타입에서 구분된다는 것은
    #   `$.matched` 가 **부재**(`null`)라는 사실 자체가 주장이다(`Passed(emptySet())`와
    #   달리 `NoGate`는 matched 자리가 없다) — non-null 로 채워지면 그 구분이 사라진다.
    "strategy-watch-004": ["$.matched"],
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
    try:
        return sweep(args)
    except (mc.ManifestFormatError, OSError, json.JSONDecodeError) as exc:
        print("도구 오류 (%s): %s" % (type(exc).__name__, exc), file=sys.stderr)
        return 2


def sweep(args):
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

    for cid, path, why, passes in sorted(rows):
        if passes:
            print("위반 변이체 통과: %s %s %s" % (cid, path, why), file=sys.stderr)
    return 1 if demoted else 0


if __name__ == "__main__":
    sys.exit(main())
