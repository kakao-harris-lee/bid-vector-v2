/**
 * 개찰 축 하네스의 **순수 층**(M9/opening-harness, `OPEN-7E3A-OPENING-AXIS-HARNESS`).
 *
 * 서버도 파일도 열지 않는다 — fixture 본문은 호출자가 읽어 넘긴다. 그래서 fake 가 이 모듈을
 * 불러도 포트를 요구하지 않고, 게이트 test 가 같은 표를 부수효과 없이 잴 수 있다.
 *
 * **이 파일이 개찰 축 라우팅 술어의 한 자리다.** 오퍼레이션 이름·진행구분 어휘·행 모양이 여기
 * 하나에 있고, 출하 정본과의 등식은 `opening-axis.test.mjs` 가 잰다.
 */
import { assertStrippedKotlin } from '../../scripts/kotlin-source.mjs';
// 추출 술어는 **내부 커널**에 있다(M11 — 팀장 판정 (ㄱ)). 이 파일의 `shipped…` 넷은 동일성
// 단언을 거친 뒤 그 커널로 넘기는 **얇은 입구**다 — 술어 자신의 양성·음성 대조는 커널을 직접
// 재고(합성 조각이 공개 입구에서 거부되므로), 커널을 참조할 수 있는 파일은 import 그래프 술어가
// 이 파일과 그 단위 test 둘로 고정한다.
import {
  DIVISION_TOKENS,
  detailAxisCountsOf,
  noticeListOperationsOf,
  openingOperationPathsOf,
  progressLabelsOf,
} from './opening-axis-extract.mjs';

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
    baseAmountPath: 'getBidPblancListInfoCnstwkBsisAmount02',
  },
  service: {
    openingResultListPath: 'getOpengResultListInfoServc',
    reservePriceDetailPath: 'getOpengResultListInfoServcPreparPcDetail',
    baseAmountPath: 'getBidPblancListInfoServcBsisAmount02',
  },
  /** 업무 접미가 없는 단일 오퍼레이션 둘(P-1 §1.5) — 두 업무가 같은 경로를 쓴다. */
  shared: {
    openingCompletePath: 'getOpengResultListInfoOpengCompt',
    bidPriceFormulaAPath: 'getBidPblancListBidPrceCalclAInfo02',
  },
};

/**
 * 공고 목록 축의 오퍼레이션 — 개찰 축 설계가 「같은 업무로 수집됐는가」를 볼 때 쓴다.
 *
 * **`02` 는 입찰공고정보서비스 축에만 붙는다**(M9/portal-02 D-1): 공고 목록·기초금액·A값이 그
 * 서비스이고 개찰결과 목록·예비가격 상세·개찰완료는 낙찰정보서비스라 **불변**이다. 위 표에서 접미가
 * 붙은 것이 셋뿐인 이유가 그것이고, 두 서비스의 경계는 출하 쪽 `KonepsEndpointDefaultsTest` 가
 * 집합으로 잠근다.
 */
export const NOTICE_LIST_OPERATIONS = {
  construction: 'getBidPblancListInfoCnstwk02',
  service: 'getBidPblancListInfoServc02',
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

const PROGRESS_DIVISION_KEY = 'progrsDivCdNm';
/** canonical 이 없을 때 조회가 내는 잠정 축 상태 — 그 자리를 한 이름으로 둔다. */
const NOT_OBSERVED = 'NOT_OBSERVED';
const NOTICE_ROUND = '000';

/**
 * 출하 속성 파일 본문 → 개찰 축 경로 표. [OPENING_AXIS_OPERATIONS] 와 **같은 모양**을 내므로
 * 등식이 업무 배정의 뒤바뀜까지 잡는다(공사 경로가 용역 자리에 앉은 꼴).
 *
 * **왜 입구에서 단언하는가**(M9 v M-1): 추출이 정규식 일치를 쓰므로 **주석 한 줄이 실제 값을
 * 가릴 수 있었다** — 출하 경로를 바꾸고 옛 값을 바로 위 주석에 두면 추출이 그 주석을 집어 등식이
 * **초록으로 지나갔다**(그 축은 조용한 404 로만 나타난다). 걷기는 **공유 한 자리**가 하고
 * (M10/gates-web), 이 입구는 받은 텍스트가 **그 읽기가 내준 바로 그것인지**를 단언한다(M11).
 */
export function shippedOpeningOperationPaths(source) {
  return openingOperationPathsOf(assertStrippedKotlin(source));
}

/** 출하 본문 → **공고 하나가 받는 상세 축의 수**(cr MEDIUM-1 처방의 입력). */
export function shippedDetailAxisCounts(source) {
  return detailAxisCountsOf(assertStrippedKotlin(source));
}

/** 출하 속성 파일 본문 → 공고 목록 오퍼레이션 표(위와 같은 사유의 등식). */
export function shippedNoticeListOperations(source) {
  return noticeListOperationsOf(assertStrippedKotlin(source));
}

/** 출하 정책 본문 → 라벨 표(등재 밖·부재는 라벨에 배정되지 않으므로 여기 들지 않는다). */
export function shippedProgressLabels(source) {
  return progressLabelsOf(assertStrippedKotlin(source));
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
 * 잠정 1위 축의 기대 상태를 **투찰 행 설계에서 유도한다**(cr MEDIUM-1).
 *
 * canonical 이 서지 않는 공고(승격 범위 밖·개찰 축 없음·공고 없음)는 그 축이 `NOT_OBSERVED` 다 —
 * 조회가 `Observed` 가 아닌 view 에서 **잠정 축을 읽지 않기** 때문이다(출하 조립 실측).
 *
 * 승격된 공고는 투찰 행의 순위 1 계수가 답을 정한다. 이 유도가 fixture 의 `bidders` 를 보므로
 * 동가 1위를 만드는 변이는 **선언과 유도가 어긋나** 붉어진다 — 「셋을 싣고 유일한 순위 1 을 둔다」가
 * 왜 설계인지가 그 자리에 선다.
 *
 * **앞 판은 이 축을 전혀 못박지 않았다**: spec 이 화면이 그린 같은 응답에서 읽어 대조해서,
 * 개찰완료 상세 축이 아예 수집되지 않는 회귀가 초록으로 지났다(cr MEDIUM-1).
 */
function provisionalStateOf(bidders) {
  const rankOnes = bidders.filter((bidder) => bidder.rank === 1).length;
  if (rankOnes === 0) {
    return 'RANK_MISSING';
  }
  return rankOnes > 1 ? 'RANK_DUPLICATED' : 'DETERMINED';
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
    const promoted = promotedDivisions.includes(notice.division);
    return {
      ...notice,
      promoted,
      derivedOpeningState: derivedStateOf(notice, promotedDivisions),
      // 승격되지 않으면 canonical 이 없고, 조회는 그때 잠정 축을 읽지 않는다.
      derivedProvisionalState: promoted ? provisionalStateOf(fixture.bidders) : NOT_OBSERVED,
      noticeListOperation: NOTICE_LIST_OPERATIONS[notice.division],
    };
  });
  // 개찰 축이 없는 묶음 둘은 canonical 이 설 길이 없다 — 그 축의 기대가 **구조적으로** 하나다.
  const withoutAxis = [...fixture.noticeAxisOnly, ...fixture.uncollected].map((notice) => ({
    ...notice,
    derivedProvisionalState: NOT_OBSERVED,
  }));
  const all = [...withAxis, ...withoutAxis];
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
    noticeAxisOnly: withoutAxis.slice(0, fixture.noticeAxisOnly.length),
    uncollected: withoutAxis.slice(fixture.noticeAxisOnly.length),
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

/**
 * 요청의 공고 — **그 업무의 설계 안에서만** 찾는다(cr LOW). 전체에서 찾으면 용역 기초금액 경로가
 * 공사 공고번호에 200 + 행을 낸다. 오늘은 각 수집 실행이 자기 업무만 물어 도달하지 않는 경로지만,
 * 출하가 업무를 잘못 골라 부르는 결함이 **0행이 아니라 행으로** 답해진다.
 *
 * 업무 공통 축 둘(개찰완료·A값)은 `division` 이 `null` 이라 전체에서 찾는다 — 그것이 그 축의
 * 실제 성질이다(업무 접미가 없는 단일 오퍼레이션, P-1 §1.5).
 */
function requestedNotice(design, params, division) {
  const number = params.get('bidNtceNo');
  return (
    design.withAxis.find(
      (notice) =>
        notice.noticeNumber === number && (division === null || notice.division === division),
    ) ?? null
  );
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
      // 순공사원가는 **공사 전용**이다(제외 ⑨ 의 입력) — 용역 응답에 실으면 실물과 어긋난다.
      ...(notice.division === 'construction'
        ? { bssAmtPurcnstcst: String(amounts.pureConstructionCost) }
        : {}),
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

  /** 상세 축 — **요청한 공고**의 행만 낸다(그 공고가 그 업무의 설계에 없으면 0 행이다). */
  const perNotice = (rowsOf, division) => (params) => {
    const notice = requestedNotice(design, params, division);
    return notice === null ? [] : rowsOf(notice);
  };

  const routes = new Map();
  for (const division of Object.keys(DIVISION_TOKENS)) {
    const paths = OPENING_AXIS_OPERATIONS[division];
    routes.set(paths.openingResultListPath, () => noticesOf(design, division).map(listRow));
    routes.set(paths.reservePriceDetailPath, perNotice(reservePriceRows, division));
    routes.set(paths.baseAmountPath, perNotice(baseAmountRows, division));
  }
  // 업무 공통 축 둘은 업무를 가르지 않는다 — 단일 오퍼레이션이라 그것이 실제 성질이다.
  routes.set(OPENING_AXIS_OPERATIONS.shared.openingCompletePath, perNotice(bidderRows, null));
  routes.set(OPENING_AXIS_OPERATIONS.shared.bidPriceFormulaAPath, perNotice(formulaARows, null));
  return routes;
}

/**
 * 러너 로그 줄과 맞댈 계수 — **닫힌 등식**이다. 승격된 수와 범위 밖 수의 합이 읽은 묶음 수와
 * 같고, 그 둘은 설계의 업무 배정에서 나온다.
 *
 * 「≥ 1」로 느슨하게 두지 않는 이유: 갈래 다섯 중 넷이 사라져도 그 술어는 초록이다.
 */
export function promotionExpectation(design, { detailAxesPerNotice }) {
  const divisions = {};
  for (const division of Object.keys(DIVISION_TOKENS)) {
    const frame = noticesOf(design, division).length;
    if (frame > 0) {
      const axes = detailAxesPerNotice[division];
      if (typeof axes !== 'number') {
        throw new Error(`업무 ${division} 의 상세 축 수를 출하 본문에서 받지 못했다`);
      }
      divisions[division] = {
        frame,
        sampled: frame,
        /**
         * **상세 축을 실제로 불렀는가**(cr MEDIUM-1) — 앞 판의 기대는 `{frame, sampled}` 둘뿐이라
         * 「표본은 뽑혔는데 상세는 하나도 안 불렀다」가 등식 **밖**이었다. 러너가 공시하는 수이고,
         * 축 수는 출하 열거에서 세므로 공사 넷·용역 셋의 비대칭이 리터럴로 굳지 않는다.
         */
        detailCalls: frame * axes,
        /**
         * 연령 게이트는 **오늘 구조적으로 무력**이다(cr 실측 — 표본틀이 `OpeningAnchor` 를
         * `NotCarriedByFrame` 하나로만 짓고 그래서 기준 시각이 늘 null 이다). 0 을 못박으면 그
         * 전제가 바뀌는 날(별 slice 가 개찰 일시를 실으면) 이 자리가 **먼저** 붉어져 알린다.
         */
        skippedAgeGate: 0,
      };
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
