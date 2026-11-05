import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  assertRunnerCounts,
  OPENING_AXIS_OPERATIONS,
  OPENING_PROGRESS_LABELS,
  noticeCollectionCategories,
  openingAxisDesign,
  openingAxisRouteTable,
  promotionExpectation,
  runnerLine,
  shippedNoticeListOperations,
  shippedOpeningOperationPaths,
  shippedProgressLabels,
} from './opening-axis.mjs';

/**
 * 개찰 축 하네스의 **게이트 술어**(M9/opening-harness) — 등식 넷을 잠근다.
 *
 * ⓐ **설계 집합 == 계약 열거 아홉**. 하네스가 「개찰 축을 다룬다」고 적는 것과 **아홉 갈래를 낳는
 *    것**은 다르다. 기대 집합을 여기 리터럴로 적으면 계약이 열을 더한 날 하네스는 조용히 여덟만
 *    낸다 — 그래서 `tsc` 가 전수를 강제하는 표의 키에서 끌어온다.
 * ⓑ **오퍼레이션 이름 == 출하 기본값**. 둘은 언어가 달라 사본이고, PR #130 이 적은 대로 그 둘을
 *    묶는 기계 등식이 없었다. 갈리면 fake 가 전부 404 를 내고 수집이 **조용히 0건**이다.
 * ⓒ **진행구분 어휘 == 출하 정책**. 라벨은 상류의 어휘이고 하네스는 그 셋을 그대로 써야 한다.
 * ⓓ **fixture 둘의 교차 등식**. 개찰 축 설계와 공고 목록 fixture 가 갈리면 설계가 선언한 갈래
 *    대신 `NOTICE_ABSENT` 가 나온다 — 그것도 아홉 중 하나라 **집합이 여전히 아홉일 수 있다**.
 *
 * 살아 있는 응답이 그 설계와 같은지는 여정 spec 이 CI 에서 잰다(이 자리는 설계만 본다).
 */
const here = dirname(fileURLToPath(import.meta.url));
const webRoot = resolve(here, '..', '..');
const repoRoot = resolve(webRoot, '..');

const read = (relative) => readFileSync(resolve(repoRoot, relative), 'utf8');

/** 출하 기본값의 정본 — 개찰 축 엔드포인트 표가 여기 하나다. */
const SHIPPED_ENDPOINTS = 'app/src/main/kotlin/bidvector/app/collection/OpeningCollectionProperties.kt';
/** 공고 목록 오퍼레이션 표의 정본. */
const SHIPPED_NOTICE_ENDPOINTS = 'app/src/main/kotlin/bidvector/app/collection/CollectionProperties.kt';
/** 진행구분 라벨 → 상태 정책의 정본. */
const SHIPPED_POLICY = 'workflow/src/main/kotlin/bidvector/workflow/opening/OpeningProgressPolicyData.kt';
/**
 * 계약 열거의 **런타임 운반체**. 이 표는 `satisfies Record<OpeningResultState, StateDisplay>` 라
 * 전수를 `tsc` 가 강제한다 — 계약이 열을 더하면 typecheck 가 먼저 붉어지고 이 등식이 이어 붉어진다.
 * 그 보증이 사라지면(`satisfies` 가 빠지면) 아래 추출이 **거부한다**.
 */
const STATE_DISPLAY_MODULE = 'web/src/display/openingResultDisplay.ts';

const EXHAUSTIVENESS_MARKER = 'as const satisfies Record<OpeningResultState, StateDisplay>';

/**
 * 표의 **최상위 키**를 원문에서 뽑는다. `.mjs` 에서 TS 를 import 하지 않는 것은 이 저장소의
 * 관례다(`scripts/vat-label-source.test.mjs` 와 같은 자리·같은 이유).
 */
function contractStates() {
  const source = read(STATE_DISPLAY_MODULE);
  const open = source.indexOf('export const OPENING_RESULT_STATE_DISPLAY = {');
  const close = source.indexOf(EXHAUSTIVENESS_MARKER, open);
  if (open < 0 || close < 0) {
    throw new Error(
      `${STATE_DISPLAY_MODULE} 에서 전수 보증을 찾지 못했다 — ` +
        `\`${EXHAUSTIVENESS_MARKER}\` 가 없으면 이 등식의 계약 쪽이 무엇도 보증하지 않는다`,
    );
  }
  const body = source.slice(open, close);
  return new Set([...body.matchAll(/^ {2}([A-Z][A-Z_]+):/gmu)].map((match) => match[1]));
}

const design = openingAxisDesign(
  JSON.parse(readFileSync(resolve(here, 'fixtures', 'opening-axis.json'), 'utf8')),
);
const noticeList = JSON.parse(readFileSync(resolve(here, 'fixtures', 'notice-list.json'), 'utf8'));

describe('ⓐ 설계 집합 == 계약 열거 아홉', () => {
  it('하네스가 낳기로 설계한 상태 집합이 계약 열거와 같다', () => {
    const contract = contractStates();

    expect(contract.size, '계약 열거 추출이 비었다 — 표의 모양이 바뀌었다').toBeGreaterThan(0);
    expect([...design.designedStates].sort()).toStrictEqual([...contract].sort());
  });

  it('갈래마다 공고가 **정확히 하나 이상** 이고 기대 상태에 중복 선언이 없다', () => {
    for (const state of design.designedStates) {
      expect(design.byState.get(state)?.length, `${state} 를 낳는 공고가 없다`).toBeGreaterThanOrEqual(1);
    }
    const numbers = design.all.map((one) => one.noticeNumber);
    expect(new Set(numbers).size, '같은 공고가 두 갈래로 선언됐다').toBe(numbers.length);
  });
});

describe('ⓑ 오퍼레이션 이름 == 출하 기본값', () => {
  it('fake 가 섬기는 개찰 축 경로가 출하 기본 표와 같다(업무 배정까지)', () => {
    expect(shippedOpeningOperationPaths(read(SHIPPED_ENDPOINTS))).toStrictEqual(OPENING_AXIS_OPERATIONS);
  });

  it('출하 기본값을 찾지 못하면 통과가 아니라 거부다', () => {
    expect(() => shippedOpeningOperationPaths('val 아무것도 없다')).toThrow(/출하 기본값/u);
  });
});

describe('ⓒ 진행구분 어휘 == 출하 정책', () => {
  it('하네스가 쓰는 라벨 → 상태 표가 출하 정책과 같다', () => {
    expect(shippedProgressLabels(read(SHIPPED_POLICY))).toStrictEqual(OPENING_PROGRESS_LABELS);
  });

  it('설계가 선언한 기대 상태는 그 라벨 표에서 유도된 값과 같다', () => {
    for (const notice of design.withAxis) {
      expect(notice.expectedOpeningState, `${notice.noticeNumber} 의 선언과 유도가 어긋난다`).toBe(
        notice.derivedOpeningState,
      );
    }
  });
});

describe('ⓓ fixture 둘의 교차 등식', () => {
  /** 공고 목록 fixture 가 내는 (공고번호 → 업무 오퍼레이션). */
  const collected = new Map(
    noticeList.items.map((item) => [item.fields.bidNtceNo, item.operation]),
  );

  it('개찰 축을 받는 공고는 공고 목록에도 있고 **같은 업무**의 오퍼레이션으로 들어온다', () => {
    for (const notice of design.withAxis) {
      const operation = collected.get(notice.noticeNumber);
      expect(operation, `${notice.noticeNumber} 가 공고 목록 fixture 에 없다 — NOTICE_ABSENT 가 된다`).toBeDefined();
      expect(operation, `${notice.noticeNumber} 의 업무가 개찰 축 설계와 갈린다`).toBe(
        notice.noticeListOperation,
      );
    }
  });

  it('개찰 축 **없이** 서는 갈래의 공고는 공고 목록에만 있다', () => {
    for (const notice of design.noticeAxisOnly) {
      expect(collected.has(notice.noticeNumber), `${notice.noticeNumber} 가 공고 목록 fixture 에 없다`).toBe(
        true,
      );
      expect(
        design.withAxis.some((one) => one.noticeNumber === notice.noticeNumber),
        `${notice.noticeNumber} 에 개찰 축이 붙으면 그 갈래가 사라진다`,
      ).toBe(false);
    }
  });

  it('공고 목록 fixture 의 오퍼레이션이 **출하 기본 표 안**이다', () => {
    const shipped = shippedNoticeListOperations(read(SHIPPED_NOTICE_ENDPOINTS));

    // 개찰 축과 같은 사유의 등식이다 — `02` 개편이 이 이름을 바꿨을 때 fixture 는 옛 이름을
    // 들고 있었고 fake 가 404 를 내 수집이 **조용히 0건**이었다(PR #130).
    expect(new Set(Object.values(shipped)).size, '출하 기본 표가 비었다').toBeGreaterThan(0);
    for (const operation of new Set(noticeList.items.map((item) => item.operation))) {
      expect(Object.values(shipped), `${operation} 가 출하 기본 표에 없다 — fake 가 404 를 낸다`).toContain(
        operation,
      );
    }
    // 개찰 축 설계가 쓰는 업무의 공고 목록 경로도 같은 표에서 나온다.
    for (const notice of design.withAxis) {
      expect(shipped[notice.division]).toBe(notice.noticeListOperation);
    }
  });

  it('공고 축 수집이 부를 업종이 fixture 에서 나온다 — 설계가 쓰는 업무가 모두 든다', () => {
    const categories = noticeCollectionCategories(noticeList);

    // fixture 에 공고가 **있는 것**과 수집이 그 오퍼레이션을 **부르는 것**은 다르다. 리터럴로
    // 적힌 업종 목록은 fixture 가 늘어도 따라오지 않아 새 공고가 `NOTICE_ABSENT` 가 된다.
    for (const notice of design.withAxis) {
      expect(
        categories,
        `업무 ${notice.division} 를 수집이 부르지 않으면 ${notice.noticeNumber} 는 NOTICE_ABSENT 다`,
      ).toContain(notice.division);
    }
    expect(() =>
      noticeCollectionCategories({ items: [{ operation: 'getBidPblancListInfoCnstwk02' }] }),
    ).toThrow(/등재 밖 오퍼레이션/u);
  });

  it('수집되지 않는 갈래의 공고는 **어느 fixture 에도** 없다', () => {
    for (const notice of design.uncollected) {
      expect(collected.has(notice.noticeNumber), `${notice.noticeNumber} 가 수집되면 그 갈래가 사라진다`).toBe(
        false,
      );
      expect(design.withAxis.some((one) => one.noticeNumber === notice.noticeNumber)).toBe(false);
    }
  });
});

describe('라우팅 표와 행 생성', () => {
  const dateTime = (offsetDays) => `2026-11-0${Math.max(1, 9 + offsetDays)} 09:00:00`;
  const routes = openingAxisRouteTable(design, { dateTime });

  it('출하 기본 경로 전부에 라우트가 있고 그 밖의 경로는 없다', () => {
    const shipped = new Set(
      Object.values(OPENING_AXIS_OPERATIONS).flatMap((group) => Object.values(group)),
    );

    expect(new Set(routes.keys())).toStrictEqual(shipped);
  });

  it('개찰결과 목록은 그 업무의 공고만 내고 진행구분 부재를 **칸 부재**로 낸다', () => {
    const rows = routes.get(OPENING_AXIS_OPERATIONS.construction.openingResultListPath)(
      new URLSearchParams(),
    );
    const numbers = rows.map((row) => row.bidNtceNo);

    expect(new Set(numbers)).toStrictEqual(
      new Set(design.withAxis.filter((one) => one.division === 'construction').map((one) => one.noticeNumber)),
    );
    const absent = design.withAxis.find((one) => one.progressDivision === null);
    const row = rows.find((one) => one.bidNtceNo === absent.noticeNumber);
    expect(row, '진행구분 부재 갈래의 행이 없다').toBeDefined();
    expect('progrsDivCdNm' in row, '부재는 빈 문자열이 아니라 **칸이 없는 것**이다').toBe(false);
  });

  it('상세 축은 **요청한 공고**의 행만 낸다', () => {
    const detail = routes.get(OPENING_AXIS_OPERATIONS.shared.openingCompletePath);
    const mine = design.withAxis[0].noticeNumber;
    const rows = detail(new URLSearchParams({ bidNtceNo: mine }));

    expect(rows.length).toBeGreaterThan(0);
    expect(new Set(rows.map((row) => row.bidNtceNo))).toStrictEqual(new Set([mine]));
    expect(detail(new URLSearchParams({ bidNtceNo: 'SYN-NOT-A-NOTICE' }))).toStrictEqual([]);
  });

  it('투찰 행에 사업자등록번호·대표자명이 **없다**(D-7E-6)', () => {
    const rows = routes.get(OPENING_AXIS_OPERATIONS.shared.openingCompletePath)(
      new URLSearchParams({ bidNtceNo: design.withAxis[0].noticeNumber }),
    );

    for (const row of rows) {
      expect(Object.keys(row)).not.toContain('prcbdrBizno');
      expect(Object.keys(row)).not.toContain('prcbdrCeoNm');
    }
  });
});

describe('러너 계수 등식', () => {
  const expectation = promotionExpectation(design);

  it('업무마다 표본틀·표본 크기가 설계의 공고 수와 같다', () => {
    expect(expectation.divisions.construction.frame).toBe(
      design.withAxis.filter((one) => one.division === 'construction').length,
    );
    expect(expectation.divisions.construction.sampled).toBe(expectation.divisions.construction.frame);
    expect(expectation.promotion.groupsRead).toBe(design.withAxis.length);
    expect(expectation.promotion.promoted + expectation.promotion.outsideSample).toBe(
      design.withAxis.length,
    );
    expect(expectation.promotion.outsideSample, '승격 범위 밖 공고가 없으면 그 갈래가 사라진다').toBe(
      design.withAxis.filter((one) => !one.promoted).length,
    );
  });

  it('로그 줄의 계수가 기대와 같으면 지나가고 어긋나면 **줄 전문**과 함께 거부한다', () => {
    const line = 'opening-promote finished groupsRead=7 promoted=6 outsideSample=1 noUsableAxis=0';

    expect(() => assertRunnerCounts(line, { groupsRead: 7, promoted: 6, outsideSample: 1 }, '승격')).not.toThrow();
    expect(() => assertRunnerCounts(line, { promoted: 5 }, '승격')).toThrow(/promoted/u);
    expect(() => assertRunnerCounts(line, { promoted: 5 }, '승격')).toThrow(/groupsRead=7/u);
  });

  it('출력에서 그 줄 하나를 집는다 — 좁히지 않으면 뒤 줄의 값이 계수를 덮는다', () => {
    const output = [
      'opening-collection finished frame=6 sampled=6 detailCalls=24 exit=0',
      'opening-promote finished groupsRead=7 promoted=6 outsideSample=1',
    ].join('\n');

    expect(runnerLine(output, 'opening-collection finished', '수집')).toContain('frame=6');
    expect(() =>
      assertRunnerCounts(runnerLine(output, 'opening-collection finished', '수집'), { frame: 6 }, '수집'),
    ).not.toThrow();
    expect(() => runnerLine(output, 'snapshot finished', '추출')).toThrow(/그 자리까지 가지 못했다/u);
  });

  it('줄에 그 칸이 **없으면** 통과가 아니다', () => {
    expect(() => assertRunnerCounts('opening-promote finished promoted=6', { groupsRead: 7 }, '승격')).toThrow(
      /groupsRead/u,
    );
  });
});
