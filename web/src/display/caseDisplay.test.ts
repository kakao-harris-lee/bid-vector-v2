import { describe, expect, it } from 'vitest';

import {
  BID_CASE_STATE_DISPLAY,
  BID_KIND_DISPLAY,
  NEXT_CHECKPOINT_DISPLAY,
  TIMELINE_ACTOR_DISPLAY,
  nextActionOf,
} from './caseDisplay';
import { displayOf } from './stateDisplay';

describe('입찰 건 상태 표시 표 — 계약 enum 전수', () => {
  it('상태 열넷이 전부 표에 있다', () => {
    expect(Object.keys(BID_CASE_STATE_DISPLAY).sort()).toStrictEqual(
      [
        'WATCHING',
        'DEFERRED',
        'PRICE_RECEIVED',
        'BID_PLANNED',
        'BID_RECORDED',
        'AWAITING_OPENING',
        'OPENED_PROVISIONAL_FIRST',
        'OPENED_OUT_OF_RANK',
        'UNDER_QUALIFICATION_REVIEW',
        'WON',
        'LOST',
        'FAILED_OR_REBID',
        'SKIPPED',
        'DEADLINE_PASSED_UNRECORDED',
      ].sort(),
    );
  });

  it('「보류」는 어휘에 없다 — 「나중에」와 「건너뜀」으로 갈린다(§6.1)', () => {
    const labels = Object.values(BID_CASE_STATE_DISPLAY).map((entry) => entry.label);

    expect(labels).not.toContain('보류');
    expect(displayOf(BID_CASE_STATE_DISPLAY, 'DEFERRED').label).toBe('나중에');
    expect(displayOf(BID_CASE_STATE_DISPLAY, 'SKIPPED').label).toBe('건너뜀');
  });

  it('다음 확인 시점 여섯이 전부 표에 있고 「없음」이 빈 값이 아니다', () => {
    expect(Object.keys(NEXT_CHECKPOINT_DISPLAY).sort()).toStrictEqual(
      ['DEADLINE', 'OPENING', 'DOCUMENT_DUE', 'NOTICE_EXPECTED', 'REMINDER', 'NONE'].sort(),
    );
    expect(displayOf(NEXT_CHECKPOINT_DISPLAY, 'NONE').label).toBe('없음');
  });

  it('페이퍼와 실투찰이 같은 어휘를 쓰되 표시가 다르다', () => {
    expect(Object.keys(BID_KIND_DISPLAY).sort()).toStrictEqual(['REAL', 'PAPER'].sort());
    expect(displayOf(BID_KIND_DISPLAY, 'PAPER').label).not.toBe(displayOf(BID_KIND_DISPLAY, 'REAL').label);
  });

  it('타임라인 주체 둘이 표에 있다', () => {
    expect(Object.keys(TIMELINE_ACTOR_DISPLAY).sort()).toStrictEqual(['USER', 'SYSTEM'].sort());
  });
});

/**
 * §6.5 「다음 행동 하나가 화면을 지배한다」 — primary 버튼은 **그 상태의 다음 전이**다. 표를
 * 상태별로 두므로 「어느 상태에서 어느 버튼」이 코드 분기가 아니라 데이터다.
 */
describe('다음 행동 하나', () => {
  it('담긴 건의 다음 행동은 투찰가 요청이다', () => {
    expect(nextActionOf('WATCHING')?.label).toBe('투찰가 요청');
  });

  it('투찰 예정의 다음 행동은 「제출함」 기록이다', () => {
    expect(nextActionOf('BID_PLANNED')?.label).toBe('제출함');
  });

  /**
   * Phase 1 실측 — 투찰가 요청은 건의 상태를 바꾸지 않는다(요청 뒤에도 `WATCHING`). 그래서
   * 「요청했는가」가 둘째 축이고, 같은 상태에서 다음 행동이 갈린다.
   */
  it('투찰가를 손에 쥐면 같은 상태에서 다음 행동이 초안으로 바뀐다', () => {
    expect(nextActionOf('WATCHING', false)?.kind).toBe('REQUEST_PRICE');
    expect(nextActionOf('WATCHING', true)?.kind).toBe('DRAFT_BID');
  });

  it('투찰가를 쥐어도 종료 상태에는 다음 행동이 생기지 않는다', () => {
    expect(nextActionOf('WON', true)).toBeNull();
    expect(nextActionOf('BID_RECORDED', true)).toBeNull();
  });

  it('이 slice 가 전이를 만들지 않는 상태에는 다음 행동이 없다 — 버튼을 지어내지 않는다', () => {
    expect(nextActionOf('WON')).toBeNull();
    expect(nextActionOf('SKIPPED')).toBeNull();
  });

  it('모든 상태에 답이 있다 — 「모르는 상태」가 없다', () => {
    for (const state of Object.keys(BID_CASE_STATE_DISPLAY)) {
      expect(() => nextActionOf(state as keyof typeof BID_CASE_STATE_DISPLAY)).not.toThrow();
    }
  });
});
