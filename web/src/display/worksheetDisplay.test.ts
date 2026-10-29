import { describe, expect, it } from 'vitest';

import { displayOrUnknown } from './stateDisplay';
import {
  BID_PRICE_MENU_DISPLAY,
  BID_PRICE_MENU_REASON_DISPLAY,
  CONFIDENCE_REASON_DISPLAY,
  CONFIDENCE_STATE_DISPLAY,
  DISCLOSURE_SENTENCE,
  FLOOR_RATE_GROUNDS_DISPLAY,
  FLOOR_SHORTFALL_REASON_DISPLAY,
  FLOOR_SHORTFALL_STATE_DISPLAY,
  INBOX_RECORD_STATE_DISPLAY,
  LICENSE_MISSING_DETAIL_DISPLAY,
  LICENSE_UNCERTAIN_REASON_DISPLAY,
  LICENSE_VALIDITY_DISPLAY,
  LICENSE_VERDICT_DISPLAY,
  ML_ANALYSIS_STATE_DISPLAY,
  OUTCOME_DISPLAY,
  UNJUDGED_AXIS_DISPLAY,
} from './worksheetDisplay';

/**
 * 화면 C 가 소비하는 **계약 enum 전수**가 표에 있는지 잰다(D-7E-4). 계약에 값이 늘면
 * `satisfies Record<Enum, …>` 가 `tsc` 에서 멈추고, 행이 사라지면 아래 키 집합 단언이 RED 다
 * (두 층이 서로의 사각을 덮는다 — `tsc` 는 삭제를, 이 test 는 추가 없는 삭제를 본다).
 *
 * 키 목록을 test 가 다시 적는 것은 의도다 — 표와 test 가 같은 손에서 함께 바뀌어야 한다.
 */
describe('화면 C 상태 표시 표 — 계약 enum 전수', () => {
  it.each([
    ['outcome', OUTCOME_DISPLAY, ['REACHED', 'NOT_REACHED']],
    ['bidPriceMenuState', BID_PRICE_MENU_DISPLAY, ['NOT_COMPUTED']],
    ['bidPriceMenuNotComputedReason', BID_PRICE_MENU_REASON_DISPLAY, ['MENU_PATH_NOT_WIRED']],
    ['floorRateGroundsState', FLOOR_RATE_GROUNDS_DISPLAY, ['USABLE', 'ABSENT', 'ORIGIN_NOT_PUBLISHED']],
    ['floorShortfallState', FLOOR_SHORTFALL_STATE_DISPLAY, ['NOT_COMPUTED']],
    ['floorShortfallNotComputedReason', FLOOR_SHORTFALL_REASON_DISPLAY, ['SHORTFALL_POLICY_NOT_PUBLISHED']],
    [
      'mlAnalysisState',
      ML_ANALYSIS_STATE_DISPLAY,
      ['ANALYZED', 'UNAVAILABLE', 'SIMILARITY_PROJECTION_NOT_READY', 'NOT_REACHED'],
    ],
    ['confidenceState', CONFIDENCE_STATE_DISPLAY, ['NOT_AVAILABLE']],
    [
      'confidenceNotAvailableReason',
      CONFIDENCE_REASON_DISPLAY,
      ['ML_SCORE_NOT_PROVIDED', 'ML_UNAVAILABLE', 'SIMILARITY_PROJECTION_NOT_READY', 'EVALUATION_NOT_REACHED_ML_STAGE'],
    ],
    ['licenseVerdict', LICENSE_VERDICT_DISPLAY, ['ELIGIBLE', 'INELIGIBLE', 'UNCERTAIN']],
    [
      'licenseUncertainReason',
      LICENSE_UNCERTAIN_REASON_DISPLAY,
      [
        'REQUIREMENT_DATA_ABSENT',
        'REQUIREMENT_UNPARSABLE',
        'OPERATOR_LICENSES_NOT_DECLARED',
        'COLLECTION_FAILED',
        'PERMITTED_INDUSTRY_COMBINATION_RULE_UNDECIDED',
      ],
    ],
    [
      'licenseMissingDetailState',
      LICENSE_MISSING_DETAIL_DISPLAY,
      ['NOT_APPLICABLE', 'SINGLE_GROUP', 'MULTIPLE_GROUPS_NOT_FLATTENED'],
    ],
    ['licenseValidity', LICENSE_VALIDITY_DISPLAY, ['NOT_VERIFIED']],
    [
      'unjudgedAxes',
      UNJUDGED_AXIS_DISPLAY,
      ['QUAL_08_REGION', 'QUAL_10_PERFORMANCE', 'QUAL_11_CONSTRUCTION_CAPACITY'],
    ],
    ['inboxRecordState', INBOX_RECORD_STATE_DISPLAY, ['NOT_RECORDED_YET']],
    [
      'disclosures',
      DISCLOSURE_SENTENCE,
      [
        'YEGA_DRAWN_PRIVATELY',
        'NOT_AUTO_SUBMITTED',
        'STATUTORY_FLOOR_RATE_NOT_INTERPRETED',
        'PRICE_FITNESS_IS_NOT_PROBABILITY',
      ],
    ],
  ])('%s 의 표 키 집합은 계약 enum 과 같다', (_axis, table, expected) => {
    expect(Object.keys(table).sort()).toStrictEqual([...expected].sort());
  });
});

/** G2 13 행 — 각 행이 요구하는 **문면 자체**를 잠근다(추적표가 「표시된다」로 닫는 것이 이것이다). */
describe('화면 C 의 G2 13 행 문면', () => {
  it('DEC-05 · DEC-02 — 3안 자리는 「아직 산출 안 함」이고 숫자가 없다', () => {
    const slot = displayOrUnknown(BID_PRICE_MENU_DISPLAY, 'NOT_COMPUTED');

    expect(slot.label).toBe('아직 산출 안 함');
    expect(slot.label).not.toMatch(/\d/);
    expect(displayOrUnknown(BID_PRICE_MENU_REASON_DISPLAY, 'MENU_PATH_NOT_WIRED').label).not.toMatch(/\d/);
  });

  it('DEC-03 — 법정 하한율 미해석 고지 문장이 있다', () => {
    expect(DISCLOSURE_SENTENCE.STATUTORY_FLOOR_RATE_NOT_INTERPRETED.label).toContain('법정 하한율');
    expect(DISCLOSURE_SENTENCE.STATUTORY_FLOOR_RATE_NOT_INTERPRETED.label).toContain('해석하지 않');
  });

  it('DEC-10 — 게시 하한율의 「없다」와 「게시값이 아니다」가 다른 문면이다', () => {
    const absent = displayOrUnknown(FLOOR_RATE_GROUNDS_DISPLAY, 'ABSENT');
    const notPublished = displayOrUnknown(FLOOR_RATE_GROUNDS_DISPLAY, 'ORIGIN_NOT_PUBLISHED');

    expect(absent.label).not.toBe(notPublished.label);
    expect(displayOrUnknown(FLOOR_RATE_GROUNDS_DISPLAY, 'USABLE').tone).toBe('neutral');
  });

  it('QUAL-02 — 면허 유효기간은 「미검증」으로 표시된다(통과로 읽히지 않는다)', () => {
    const validity = displayOrUnknown(LICENSE_VALIDITY_DISPLAY, 'NOT_VERIFIED');

    expect(validity.label).toContain('미검증');
    expect(validity.tone).toBe('unjudged');
  });

  it('QUAL-08 · QUAL-11 — 지역·시공능력은 「아직 판정하지 않음」이다', () => {
    expect(displayOrUnknown(UNJUDGED_AXIS_DISPLAY, 'QUAL_08_REGION').label).toContain('지역');
    expect(displayOrUnknown(UNJUDGED_AXIS_DISPLAY, 'QUAL_11_CONSTRUCTION_CAPACITY').label).toContain('시공능력');
    for (const axis of Object.values(UNJUDGED_AXIS_DISPLAY)) {
      expect(axis.tone).toBe('unjudged');
      expect(axis.why).toContain('아직 판정하지 않');
    }
  });

  it('COL-04 — 「요건 없음」과 「수집 실패」가 다른 문면이다', () => {
    const absent = displayOrUnknown(LICENSE_UNCERTAIN_REASON_DISPLAY, 'REQUIREMENT_DATA_ABSENT');
    const failed = displayOrUnknown(LICENSE_UNCERTAIN_REASON_DISPLAY, 'COLLECTION_FAILED');

    expect(absent.label).not.toBe(failed.label);
    expect(failed.label).toContain('수집');
  });

  it('QUAL-01 — 판정 불가는 미달이 아니다', () => {
    expect(displayOrUnknown(LICENSE_VERDICT_DISPLAY, 'UNCERTAIN').tone).toBe('unjudged');
    expect(displayOrUnknown(LICENSE_VERDICT_DISPLAY, 'INELIGIBLE').tone).toBe('bad');
  });

  it('DEC-04 — 하한 미달 빈도는 「재지 못함」이고 표본 수를 적지 않는다', () => {
    expect(displayOrUnknown(FLOOR_SHORTFALL_STATE_DISPLAY, 'NOT_COMPUTED').label).not.toMatch(/\d/);
    expect(displayOrUnknown(FLOOR_SHORTFALL_REASON_DISPLAY, 'SHORTFALL_POLICY_NOT_PUBLISHED').label).not.toMatch(/\d/);
  });

  it('ML-03 — 신뢰도는 「없음」이고 확률 어휘를 쓰지 않는다', () => {
    expect(displayOrUnknown(CONFIDENCE_STATE_DISPLAY, 'NOT_AVAILABLE').label).not.toContain('확률');
    expect(DISCLOSURE_SENTENCE.PRICE_FITNESS_IS_NOT_PROBABILITY.label).toContain('확률');
  });

  it('R-4 — 알림함은 「아직 기록하지 않음」이다', () => {
    expect(displayOrUnknown(INBOX_RECORD_STATE_DISPLAY, 'NOT_RECORDED_YET').label).toContain('아직');
  });
});
