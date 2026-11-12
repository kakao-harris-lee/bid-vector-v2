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
  shippedDetailAxisCounts,
  shippedProgressLabels,
} from './opening-axis.mjs';
import { readKotlinSource } from '../../scripts/kotlin-source.mjs';

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

// Kotlin 정본의 **경로를 여기서 알지 않는다**(r2 — verifier r1 M-2). 아래 넷은 읽기 집합의
// **키**이고, 경로 표와 읽기는 공유 모듈이 소유한다 — 원문을 쥘 수 없으면 걷기 사본도 살 수 없다.
/** 출하 기본값의 정본 — 개찰 축 엔드포인트 표. */
const SHIPPED_ENDPOINTS = 'openingEndpoints';
/** 공고 목록 오퍼레이션 표의 정본. */
const SHIPPED_NOTICE_ENDPOINTS = 'noticeEndpoints';
/** 상세 축 열거의 정본 — 공고 하나가 받는 축 수(공사 넷 · 용역 셋)가 여기서 나온다. */
const SHIPPED_DETAIL_AXES = 'detailAxes';
/** 진행구분 라벨 → 상태 정책의 정본. */
const SHIPPED_POLICY = 'progressPolicy';
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

/** 이 하네스가 가진 fixture 전수 — 아래 모양 검사가 **둘 다** 본다. */
const FIXTURE_NAMES = ['notice-list.json', 'opening-axis.json'];

const canonicalJson = (text) => `${JSON.stringify(JSON.parse(text), null, 2)}\n`;

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
    expect(shippedOpeningOperationPaths(readKotlinSource(SHIPPED_ENDPOINTS))).toStrictEqual(OPENING_AXIS_OPERATIONS);
  });

  it('출하 기본값을 찾지 못하면 통과가 아니라 거부다', () => {
    expect(() => shippedOpeningOperationPaths('val 아무것도 없다')).toThrow(/출하 기본값/u);
  });

  /**
   * **v M-1 의 재현을 못박는다.** 앞 판의 추출은 정규식 **첫 일치**를 써서, 출하 경로를 바꾸고 옛
   * 값을 바로 위 주석에 두면 그 주석을 집어 **등식이 초록으로 지나갔다**(그 축은 조용한 404 로만
   * 나타난다). 이제 주석을 걷고 일치 수가 하나임을 요구한다.
   */
  /**
   * **r3 에서 계약이 바뀌었다**(verifier r2 H-3) — 추출은 **걷힌 텍스트만** 받는다. 가리는 주석이
   * 든 원문이 걷힌 뒤의 모습은 「주석은 사라지고 실제 값만 남은 것」이므로, 재현 원문도 그 모습으로
   * 만든다. 주석이 실제 값을 가리지 못한다는 성질 자체는 **걷기 쪽**에서 잠근다
   * (`scripts/kotlin-source.test.mjs` — 가리는 선언을 걷으면 실제 값만 남는다).
   */
  it('가리는 줄이 걷힌 뒤에도 실제 값을 집는다', () => {
    const shipped = readKotlinSource(SHIPPED_ENDPOINTS);
    const shadowed = shipped.replace(
      'val openingCompletePath: String = "getOpengResultListInfoOpengCompt"',
      '    val openingCompletePath: String = "getOpengResultListInfoOpengCompt02"',
    );

    expect(shadowed, '재현 원문이 만들어지지 않았다').not.toBe(shipped);
    // 주석이 아니라 **실제 값**을 집는다 → 하네스 사본과 갈려 등식이 붉어질 값이다.
    expect(shippedOpeningOperationPaths(shadowed).shared.openingCompletePath).toBe(
      'getOpengResultListInfoOpengCompt02',
    );
    expect(shippedOpeningOperationPaths(shadowed)).not.toStrictEqual(OPENING_AXIS_OPERATIONS);
  });

  /** 걷히지 않은 원문은 추출 자리에서 **거부된다** — 주석이 값을 가릴 기회가 오지 않는다. */
  it('걷히지 않은 원문은 거부된다', () => {
    const unstripped = `// 가리는 줄\n${readKotlinSource(SHIPPED_ENDPOINTS)}`;

    expect(() => shippedOpeningOperationPaths(unstripped)).toThrow(/걷힌 Kotlin 텍스트가 아니다/u);
  });

  it('같은 선언이 **두 자리에** 있으면 어느 것이 정본인지 서지 않으므로 거부한다', () => {
    const twice = readKotlinSource(SHIPPED_ENDPOINTS).replace(
      'val bidPriceFormulaAPath: String =',
      'val bidPriceFormulaAPath: String = "getBidPblancListBidPrceCalclAInfo"\n' +
        '    val bidPriceFormulaAPath: String =',
    );

    expect(() => shippedOpeningOperationPaths(twice)).toThrow(/정확히 하나/u);
  });

  it('문자열 안의 `//` 는 주석이 아니다 — base URL 에서 줄이 잘리면 추출이 엉뚱한 값을 본다', () => {
    const shipped = readKotlinSource(SHIPPED_ENDPOINTS);

    expect(shipped, '출하 파일에 base URL 리터럴이 없다 — 이 대조가 공허하다').toContain('https://');
    expect(shippedOpeningOperationPaths(shipped)).toStrictEqual(OPENING_AXIS_OPERATIONS);
  });
});

describe('ⓕ 상세 축 수 == 출하 열거', () => {
  it('공사가 용역보다 많다 — A값이 공사 전용인 비대칭을 출하 본문에서 센다', () => {
    const counts = shippedDetailAxisCounts(readKotlinSource(SHIPPED_DETAIL_AXES));

    // 손으로 적으면 그 비대칭이 리터럴로 굳는다(업종 목록에서 닫은 드리프트와 같은 꼴).
    expect(counts.construction).toBeGreaterThan(counts.service);
    expect(Object.keys(counts).sort()).toStrictEqual(['construction', 'service']);
  });

  it('열거 함수를 찾지 못하면 통과가 아니라 거부다', () => {
    expect(() => shippedDetailAxisCounts('아무것도 없다')).toThrow(/상세 축 열거/u);
  });

  it('수집 걸음의 기대가 그 수에서 나온다 — 상세 축을 **실제로 불렀는가**가 등식 안이다', () => {
    const counts = shippedDetailAxisCounts(readKotlinSource(SHIPPED_DETAIL_AXES));
    const expectation = promotionExpectation(design, { detailAxesPerNotice: counts });

    for (const [division, one] of Object.entries(expectation.divisions)) {
      expect(one.detailCalls, `${division} 상세 호출 기대`).toBe(one.frame * counts[division]);
      // 연령 게이트는 오늘 구조적으로 무력이다(앵커가 늘 `NotCarriedByFrame`) — 0 을 못박아
      // 그 전제가 바뀌는 날 이 자리가 먼저 붉어지게 한다.
      expect(one.skippedAgeGate).toBe(0);
    }
  });

  it('상세 축 수를 받지 못하면 기대를 지어내지 않는다', () => {
    expect(() => promotionExpectation(design, { detailAxesPerNotice: {} })).toThrow(/상세 축 수/u);
  });
});

describe('ⓒ 진행구분 어휘 == 출하 정책', () => {
  it('하네스가 쓰는 라벨 → 상태 표가 출하 정책과 같다', () => {
    expect(shippedProgressLabels(readKotlinSource(SHIPPED_POLICY))).toStrictEqual(OPENING_PROGRESS_LABELS);
  });

  it('설계가 선언한 기대 상태는 그 라벨 표에서 유도된 값과 같다', () => {
    for (const notice of design.withAxis) {
      expect(notice.expectedOpeningState, `${notice.noticeNumber} 의 선언과 유도가 어긋난다`).toBe(
        notice.derivedOpeningState,
      );
    }
  });

  /**
   * 잠정 1위 축도 같은 꼴로 본다(cr MEDIUM-1). 유도가 `bidders` 의 순위 1 계수를 보므로 **동가
   * 1위를 만드는 변이**가 선언과 어긋나 붉어진다 — 「투찰 행 셋에 유일한 순위 1」이 왜 설계인지가
   * 그 자리에 선다.
   */
  it('잠정 1위 축의 선언도 유도와 같다 — 세 묶음 전수', () => {
    for (const notice of design.all) {
      expect(
        notice.expectedProvisionalState,
        `${notice.noticeNumber} 의 잠정 축 선언과 유도가 어긋난다`,
      ).toBe(notice.derivedProvisionalState);
    }
    // 승격된 공고는 **결정적으로** 잠정 1위가 선다(연령 게이트가 오늘 무력이므로).
    expect(
      design.withAxis.filter((one) => one.promoted).map((one) => one.expectedProvisionalState),
    ).toStrictEqual(design.withAxis.filter((one) => one.promoted).map(() => 'DETERMINED'));
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
    const shipped = shippedNoticeListOperations(readKotlinSource(SHIPPED_NOTICE_ENDPOINTS));

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
    // **음성 대조 둘.** 앞 판은 이 자리에 `…Cnstwk02` 를 썼는데 `02` 개편이 그것을 **출하 이름으로
    // 만들어** 대조가 조용히 공허해졌다(이 라운드에서 실제로 붉어져 잡혔다). 그래서 ⓐ 옛 이름
    // — 개편 뒤 미등재가 된 현실의 드리프트 — 과 ⓑ 어느 개편에서도 이름이 될 수 없는 값을 함께 둔다.
    expect(() =>
      noticeCollectionCategories({ items: [{ operation: 'getBidPblancListInfoCnstwk' }] }),
    ).toThrow(/등재 밖 오퍼레이션/u);
    expect(() =>
      noticeCollectionCategories({ items: [{ operation: 'SYN-NOT-AN-OPERATION' }] }),
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

describe('ⓔ fixture 의 모양 — 병합 합집합이 접은 본문을 잡는다', () => {
  /**
   * **왕복 바이트 등식**이 중복 키를 잡는 기제: 중복 키는 `JSON.parse` 가 **조용히 접고**(뒤가
   * 이긴다) 재직렬화는 한 벌만 내므로 원본보다 짧아진다. 깊이와 무관하고 들여쓰기를 추측하지
   * 않는다 — `_provenance` 안쪽이든 어디든 잡는다.
   *
   * **배열 중복은 이 등식이 잡지 못한다**(실측으로 갈랐다 — 복제된 항목은 파싱에서 살아남아 왕복이
   * 통과한다). 그 축은 아래 계수 등식이 지고, 둘은 서로를 대신하지 못한다.
   *
   * 비용은 **정규 포맷이 래칫이 되는 것**이다 — 한 줄 객체로 손편집하면 그 자체로 붉다. 저장소
   * 선례(`api:types:check` 의 바이트 등식)와 같은 꼴이고, 처방을 실패 문면에 담는다.
   */
  it('두 fixture 가 정규 JSON 과 바이트 동일하다 — 중복 키가 여기서 멈춘다', () => {
    for (const name of FIXTURE_NAMES) {
      const raw = readFileSync(resolve(here, 'fixtures', name), 'utf8');

      expect(
        canonicalJson(raw),
        `${name} 이 정규 JSON 과 다르다 — 병합이 중복 키를 접었거나 손편집이 포맷을 벗어났다.\n` +
          '  다시 쓰기(web 에서): node -e \'const f=process.argv[1],fs=require("fs");' +
          'fs.writeFileSync(f,JSON.stringify(JSON.parse(fs.readFileSync(f,"utf8")),null,2)+"\\n")\' ' +
          `e2e/harness/fixtures/${name}`,
      ).toBe(raw);
    }
  });

  it('공고 축 fixture 의 항목 수 == 서로 다른 공고번호 수 — 복제된 항목이 멈춘다', () => {
    const numbers = noticeList.items.map((item) => item.fields.bidNtceNo);

    expect(numbers.length, '공고 축 fixture 가 비었다').toBeGreaterThan(0);
    expect(new Set(numbers).size, `같은 공고가 두 항목으로 있다: ${numbers.join(', ')}`).toBe(
      numbers.length,
    );
  });

  it('왕복 등식과 계수 등식이 **서로 다른 변이**를 잡는다(술어 자신의 측정)', () => {
    const raw = readFileSync(resolve(here, 'fixtures', 'notice-list.json'), 'utf8');

    // ① 키 중복 — 왕복이 잡고 계수는 못 잡는다.
    const dupKey = raw.replace('  "items": [', '  "_provenance": { "x": 1 },\n  "items": [');
    expect(canonicalJson(dupKey), '키 중복인데 왕복이 통과했다').not.toBe(dupKey);

    // ② 항목 복제 — 왕복은 통과하고 계수가 잡는다.
    const duplicated = JSON.parse(raw);
    duplicated.items.push(duplicated.items[0]);
    const dupItem = `${JSON.stringify(duplicated, null, 2)}\n`;
    expect(canonicalJson(dupItem), '배열 중복은 파싱에서 살아남으므로 왕복은 통과해야 한다').toBe(dupItem);
    const numbers = duplicated.items.map((item) => item.fields.bidNtceNo);
    expect(new Set(numbers).size, '항목 복제인데 계수가 같다').not.toBe(numbers.length);
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
  const expectation = promotionExpectation(design, {
    detailAxesPerNotice: shippedDetailAxisCounts(readKotlinSource(SHIPPED_DETAIL_AXES)),
  });

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
