/**
 * 개찰 축 하네스의 **순수 층**(M9/opening-harness, `OPEN-7E3A-OPENING-AXIS-HARNESS`).
 *
 * 서버도 파일도 열지 않는다 — fixture 본문은 호출자가 읽어 넘긴다. 그래서 fake 가 이 모듈을
 * 불러도 포트를 요구하지 않고, 게이트 test 가 같은 표를 부수효과 없이 잴 수 있다.
 *
 * **이 파일이 개찰 축 라우팅 술어의 한 자리다.** 오퍼레이션 이름·진행구분 어휘·행 모양이 여기
 * 하나에 있고, 출하 정본과의 등식은 `opening-axis.test.mjs` 가 잰다.
 */

/**
 * 개찰 축 오퍼레이션 경로 — **출하 기본값의 사본**이다
 * (`app/src/main/kotlin/bidvector/app/collection/OpeningCollectionProperties.kt`).
 *
 * 언어가 달라 사본을 피할 수 없으므로 [shippedOpeningOperationPaths] 와의 **집합 등식**으로 묶는다.
 * 그 등식이 없던 동안 조달청 `02` 개편이 이름을 바꾸자 fake 가 전부 404 를 냈고 수집은 **조용히
 * 0건**이었다(PR #130 이 적은 자리).
 */
export const OPENING_AXIS_OPERATIONS = {
  construction: {
    openingResultListPath: 'getOpengResultListInfoCnstwk',
    reservePriceDetailPath: 'getOpengResultListInfoCnstwkPreparPcDetail',
    baseAmountPath: 'getBidPblancListInfoCnstwkBsisAmount',
  },
  service: {
    openingResultListPath: 'getOpengResultListInfoServc',
    reservePriceDetailPath: 'getOpengResultListInfoServcPreparPcDetail',
    baseAmountPath: 'getBidPblancListInfoServcBsisAmount',
  },
  /** 업무 접미가 없는 단일 오퍼레이션 둘(P-1 §1.5) — 두 업무가 같은 경로를 쓴다. */
  shared: {
    openingCompletePath: 'getOpengResultListInfoOpengCompt',
    bidPriceFormulaAPath: 'getBidPblancListBidPrceCalclAInfo',
  },
};

/** 공고 목록 축의 오퍼레이션 — 개찰 축 설계가 「같은 업무로 수집됐는가」를 볼 때 쓴다. */
export const NOTICE_LIST_OPERATIONS = {
  construction: 'getBidPblancListInfoCnstwk',
  service: 'getBidPblancListInfoServc',
};

/**
 * 진행구분 라벨 → 개찰 결과 상태 — **출하 정책의 사본**
 * (`workflow/.../OpeningProgressPolicyData.kt` 의 `OPENING_PROGRESS_POLICY`).
 * 등재 밖 라벨은 `PROGRESS_UNRECOGNIZED`, 칸 부재는 `PROGRESS_ABSENT` 다(라벨이 만드는 값이 아니다).
 */
export const OPENING_PROGRESS_LABELS = {
  개찰완료: 'OPENED',
  유찰: 'FAILED_BID',
  재입찰: 'REBID',
};

const DIVISION_TOKENS = { construction: 'CONSTRUCTION', service: 'SERVICE' };
const PROGRESS_DIVISION_KEY = 'progrsDivCdNm';
const NOTICE_ROUND = '000';

function literalAfter(text, pattern, what) {
  const match = pattern.exec(text);
  if (match === null) {
    throw new Error(`출하 기본값에서 ${what} 를 찾지 못했다 — 정본의 모양이 바뀌었다`);
  }
  return match[1];
}

/**
 * 출하 속성 파일 본문 → 개찰 축 경로 표. [OPENING_AXIS_OPERATIONS] 와 **같은 모양**을 내므로
 * 등식이 업무 배정의 뒤바뀜까지 잡는다(공사 경로가 용역 자리에 앉은 꼴).
 *
 * 업무 키를 믿지 않고 블록 안의 `division =` 토큰까지 본다 — 키만 보면 두 블록의 내용이 통째로
 * 뒤바뀐 경우를 지나보낸다.
 */
export function shippedOpeningOperationPaths(text) {
  const divisions = {};
  for (const [category, token] of Object.entries(DIVISION_TOKENS)) {
    const start = text.indexOf(`"${category}" to`);
    const end = text.indexOf('division =', start);
    if (start < 0 || end < 0) {
      throw new Error(`출하 기본값에서 업무 ${category} 의 개찰 축 블록을 찾지 못했다`);
    }
    const block = text.slice(start, end);
    const declared = literalAfter(
      text.slice(end),
      /division = BusinessDivision\.(\w+)/u,
      `업무 ${category} 의 대분류`,
    );
    if (declared !== token) {
      throw new Error(`업무 ${category} 의 대분류가 ${declared} 다 — 표의 업무 배정이 뒤바뀌었다`);
    }
    divisions[category] = {
      openingResultListPath: literalAfter(
        block,
        /openingResultListPath = "([^"]+)"/u,
        `${category} 개찰결과 목록 경로`,
      ),
      reservePriceDetailPath: literalAfter(
        block,
        /reservePriceDetailPath = "([^"]+)"/u,
        `${category} 예비가격 상세 경로`,
      ),
      baseAmountPath: literalAfter(block, /baseAmountPath = "([^"]+)"/u, `${category} 기초금액 경로`),
    };
  }
  return {
    ...divisions,
    shared: {
      openingCompletePath: literalAfter(
        text,
        /val openingCompletePath: String = "([^"]+)"/u,
        '개찰완료 경로',
      ),
      bidPriceFormulaAPath: literalAfter(
        text,
        /val bidPriceFormulaAPath: String = "([^"]+)"/u,
        'A값 경로',
      ),
    },
  };
}

/** 출하 속성 파일 본문 → 공고 목록 오퍼레이션 표(위와 같은 사유의 등식). */
export function shippedNoticeListOperations(text) {
  const found = {};
  for (const match of text.matchAll(
    /"(\w+)" to KonepsOperationProperties\(\s*"([^"]+)",\s*BusinessDivision\.(\w+),?\s*\)/gu,
  )) {
    const [, category, path, token] = match;
    if (DIVISION_TOKENS[category] !== token) {
      throw new Error(`공고 목록 표의 업무 ${category} 대분류가 ${token} 다 — 배정이 뒤바뀌었다`);
    }
    found[category] = path;
  }
  if (Object.keys(found).length === 0) {
    throw new Error('출하 기본값에서 공고 목록 오퍼레이션 표를 찾지 못했다 — 정본의 모양이 바뀌었다');
  }
  return found;
}

/** 출하 정책 본문 → 라벨 표(등재 밖·부재는 라벨에 배정되지 않으므로 여기 들지 않는다). */
export function shippedProgressLabels(text) {
  const labels = {};
  for (const match of text.matchAll(/"([^"]+)" to OpeningProgress\.(\w+)/gu)) {
    labels[match[1]] = match[2];
  }
  if (Object.keys(labels).length === 0) {
    throw new Error('출하 정책에서 진행구분 라벨 표를 찾지 못했다 — 정본의 모양이 바뀌었다');
  }
  return labels;
}

/**
 * 개찰 축 원문이 있는 공고의 **기대 상태를 데이터에서 유도한다**. 설계가 선언한 값과 이 값이
 * 같은지는 test 가 본다 — 선언만 두면 진행구분을 바꾸고 기대를 그대로 둔 fixture 가 지나간다.
 */
function derivedStateOf(notice, promotedDivisions) {
  if (!promotedDivisions.includes(notice.division)) {
    return 'OBSERVED_NOT_PROMOTED';
  }
  if (notice.progressDivision === null) {
    return 'PROGRESS_ABSENT';
  }
  return OPENING_PROGRESS_LABELS[notice.progressDivision] ?? 'PROGRESS_UNRECOGNIZED';
}

/**
 * fixture → 설계. 세 묶음이 **fixture 와의 관계로** 갈린다: 개찰 축을 받는 공고 · 공고 목록에만
 * 있는 공고 · 어디에도 없는 공고. 그 관계가 곧 갈래이므로 교차 등식 test 가 그것을 잰다.
 */
export function openingAxisDesign(fixture) {
  const promotedDivisions = fixture.promotedDivisions;
  if (!Array.isArray(promotedDivisions) || promotedDivisions.length === 0) {
    throw new Error('승격을 받는 업무가 선언되지 않았다 — 승격 범위가 비면 갈래 다섯이 사라진다');
  }
  const withAxis = fixture.notices.map((notice) => {
    if (OPENING_AXIS_OPERATIONS[notice.division] === undefined) {
      throw new Error(`개찰 축 표에 없는 업무다: ${notice.division}`);
    }
    return {
      ...notice,
      promoted: promotedDivisions.includes(notice.division),
      derivedOpeningState: derivedStateOf(notice, promotedDivisions),
      noticeListOperation: NOTICE_LIST_OPERATIONS[notice.division],
    };
  });
  const all = [...withAxis, ...fixture.noticeAxisOnly, ...fixture.uncollected];
  const byState = new Map();
  for (const notice of all) {
    byState.set(notice.expectedOpeningState, [
      ...(byState.get(notice.expectedOpeningState) ?? []),
      notice,
    ]);
  }
  return {
    all,
    withAxis,
    noticeAxisOnly: fixture.noticeAxisOnly,
    uncollected: fixture.uncollected,
    promotedDivisions,
    axisOffsetDays: fixture.axisOffsetDays,
    bidders: fixture.bidders,
    amounts: fixture.amounts,
    byState,
    designedStates: new Set(all.map((one) => one.expectedOpeningState)),
  };
}

/** 복수예가 행 수 — 하나만 주면 예비가격 축이 통째로 빠진다(6G mock 과 같은 수). */
const RESERVE_PRICE_ROWS = 15;
/** 추첨으로 뽑힌 순번 — 투찰 행의 선택과 **일부러 어긋나게** 둔다(6G mock 의 같은 판단). */
const DRAWN_SEQUENCES = new Set([3, 7, 11, 14]);

function noticesOf(design, division) {
  return design.withAxis.filter((notice) => notice.division === division);
}

function requestedNotice(design, params) {
  const number = params.get('bidNtceNo');
  return design.withAxis.find((notice) => notice.noticeNumber === number) ?? null;
}

/**
 * 오퍼레이션 → 행 생성 함수. **fake 의 라우팅이 이 표만 본다** — 표에 없는 경로는 fake 가 404 를
 * 내고, 표는 출하 기본값과 등식으로 묶여 있다.
 *
 * 일시는 **실행 시각 상대**다(D-7E-2 — 시각을 조작하지 않는다). 그 변환은 호출자가 넘긴
 * [dateTime] 하나가 하므로 이 모듈에 시계가 없다.
 */
export function openingAxisRouteTable(design, { dateTime }) {
  const offsets = design.axisOffsetDays;
  const amounts = design.amounts;

  const listRow = (notice) => ({
    bidNtceNo: notice.noticeNumber,
    bidNtceOrd: NOTICE_ROUND,
    // 참가업체수는 **투찰 행 수와 같다** — 실물이 어긋나지 않는 자리다.
    prtcptCnum: String(design.bidders.length),
    // 부재는 **칸이 없는 것**이다(빈 문자열이 아니다) — 그 둘은 다른 갈래를 낳는다.
    ...(notice.progressDivision === null ? {} : { [PROGRESS_DIVISION_KEY]: notice.progressDivision }),
  });

  const reservePriceRows = (notice) =>
    Array.from({ length: RESERVE_PRICE_ROWS }, (unused, index) => {
      const sequence = index + 1;
      return {
        bidNtceNo: notice.noticeNumber,
        bidNtceOrd: NOTICE_ROUND,
        compnoRsrvtnPrceSno: String(sequence).padStart(2, '0'),
        bsisPlnprc: String(amounts.basePlannedPrice + sequence * amounts.plannedPriceStep),
        drwtYn: DRAWN_SEQUENCES.has(sequence) ? 'Y' : 'N',
        bssamt: String(amounts.baseAmount),
        plnprc: String(amounts.plannedPrice),
        // 실개찰 일시 — 추출·승격이 이 축에서 읽는다(canonical 이 아니라 원문에서).
        rlOpengDt: dateTime(offsets.actualOpening),
      };
    });

  const bidderRows = (notice) =>
    design.bidders.map((bidder) => ({
      bidNtceNo: notice.noticeNumber,
      bidNtceOrd: NOTICE_ROUND,
      opengRank: String(bidder.rank),
      // 상호는 합성이다. **사업자등록번호·대표자명을 싣지 않는다**(D-7E-6) — 6G mock 은 경계가
      // 그 둘을 떨어뜨리는지 재려고 싣지만, 이 하네스는 그것을 재지 않으므로 개인 식별 키를
      // 저장소의 fixture 에 두지 않는다.
      prcbdrNm: bidder.name,
      bidprcAmt: String(bidder.amountWon),
      bidprcrt: bidder.bidRate,
      drwtNo1: String(bidder.firstDraw),
      drwtNo2: String(bidder.secondDraw),
    }));

  const baseAmountRows = (notice) => [
    {
      bidNtceNo: notice.noticeNumber,
      bidNtceOrd: NOTICE_ROUND,
      bssamt: String(amounts.baseAmount),
      bssamtOpenDt: dateTime(offsets.baseAmountOpen),
      rsrvtnPrceRngBgnRate: '-3',
      rsrvtnPrceRngEndRate: '+3',
      bidPrceCalclAYn: 'Y',
      // 순공사원가 — 공사 전용이고 제외 ⑨ 의 입력이다.
      bssAmtPurcnstcst: String(amounts.pureConstructionCost),
    },
  ];

  const formulaARows = (notice) => [
    {
      bidNtceNo: notice.noticeNumber,
      bidNtceOrd: NOTICE_ROUND,
      // **A 구성 항목 전부**를 싣는다 — 하나라도 없으면 A 묶음이 통째로 빠진다.
      ...amounts.formulaA,
      qltyMngcstAObjYn: 'Y',
      smkpAmtYn: 'N',
      ntceNticeDt: dateTime(offsets.noticeAnnounced),
      bidPrceCalclAOpenDt: dateTime(offsets.formulaAOpen),
    },
  ];

  /** 상세 축 — **요청한 공고**의 행만 낸다(그 공고가 설계에 없으면 0 행이다). */
  const perNotice = (rowsOf) => (params) => {
    const notice = requestedNotice(design, params);
    return notice === null ? [] : rowsOf(notice);
  };

  const routes = new Map();
  for (const division of Object.keys(DIVISION_TOKENS)) {
    const paths = OPENING_AXIS_OPERATIONS[division];
    routes.set(paths.openingResultListPath, () => noticesOf(design, division).map(listRow));
    routes.set(paths.reservePriceDetailPath, perNotice(reservePriceRows));
    routes.set(paths.baseAmountPath, perNotice(baseAmountRows));
  }
  routes.set(OPENING_AXIS_OPERATIONS.shared.openingCompletePath, perNotice(bidderRows));
  routes.set(OPENING_AXIS_OPERATIONS.shared.bidPriceFormulaAPath, perNotice(formulaARows));
  return routes;
}

/**
 * 러너 로그 줄과 맞댈 계수 — **닫힌 등식**이다. 승격된 수와 범위 밖 수의 합이 읽은 묶음 수와
 * 같고, 그 둘은 설계의 업무 배정에서 나온다.
 *
 * 「≥ 1」로 느슨하게 두지 않는 이유: 갈래 다섯 중 넷이 사라져도 그 술어는 초록이다.
 */
export function promotionExpectation(design) {
  const divisions = {};
  for (const division of Object.keys(DIVISION_TOKENS)) {
    const frame = noticesOf(design, division).length;
    if (frame > 0) {
      divisions[division] = { frame, sampled: frame };
    }
  }
  const promoted = design.withAxis.filter((notice) => notice.promoted).length;
  return {
    divisions,
    promotion: {
      groupsRead: design.withAxis.length,
      promoted,
      outsideSample: design.withAxis.length - promoted,
    },
  };
}

/**
 * 공고 축 수집이 요청해야 하는 업종 — **fixture 가 정한다.**
 *
 * 하네스에 리터럴로 적으면 fixture 에 다른 업무의 공고를 더해도 수집이 그 업종을 부르지 않아 그
 * 공고가 `NOTICE_ABSENT` 가 된다. 그것은 교차 등식 test 가 **보지 못하는 사각**이다: fixture 에
 * 공고가 있는 것과 수집이 그 오퍼레이션을 **부르는 것**은 다르다(이 구멍을 한 번 밟았다).
 */
export function noticeCollectionCategories(noticeListFixture) {
  const divisionOf = new Map(
    Object.entries(NOTICE_LIST_OPERATIONS).map(([division, operation]) => [operation, division]),
  );
  const categories = new Set();
  for (const operation of noticeListFixture.items.map((item) => item.operation)) {
    const division = divisionOf.get(operation);
    if (division === undefined) {
      throw new Error(`공고 축 fixture 에 등재 밖 오퍼레이션이 있다: ${operation}`);
    }
    categories.add(division);
  }
  return [...categories].sort();
}

/**
 * 러너 출력에서 그 줄 **하나**를 집는다. 출력 전체를 그냥 넘기면 `key=value` 가 여러 줄에 있어
 * 계수가 뒤 줄의 값으로 덮인다 — 줄을 먼저 좁히지 않으면 등식이 **다른 줄을 재면서** 초록일 수 있다.
 */
export function runnerLine(output, marker, label) {
  const line = output.split('\n').find((one) => one.includes(marker));
  if (line === undefined) {
    throw new Error(`${label} 출력에 「${marker}」 줄이 없다 — 러너가 그 자리까지 가지 못했다`);
  }
  return line;
}

/**
 * 러너가 남긴 줄의 계수 ↔ 기대. 어긋나면 **줄 전문**을 담아 던진다 — 앞 판의 붉음은 「30000ms
 * 안에 기다리지 못했다」였고 그것은 원인을 말하지 않아 오진 비용을 만들었다(PR #130 의 같은 교훈).
 */
export function assertRunnerCounts(line, expected, label) {
  const counts = new Map([...line.matchAll(/(\w+)=(\S+)/gu)].map((match) => [match[1], match[2]]));
  const wrong = [];
  for (const [key, value] of Object.entries(expected)) {
    if (!counts.has(key)) {
      wrong.push(`${key} 칸이 줄에 없다`);
      continue;
    }
    if (counts.get(key) !== String(value)) {
      wrong.push(`${key}: 기대 ${value} · 실측 ${counts.get(key)}`);
    }
  }
  if (wrong.length > 0) {
    throw new Error(`${label} 러너의 계수가 설계와 어긋난다 — ${wrong.join(' · ')}\n  줄: ${line}`);
  }
}
