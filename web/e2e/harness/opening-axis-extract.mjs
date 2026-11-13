/**
 * 출하 Kotlin 본문에서 개찰 축 표를 **읽어 내는 순수 커널**(M11/gates-web-read — 팀장 판정 (ㄱ)).
 *
 * **내부 모듈이다.** 공개 입구는 `opening-axis.mjs` 의 `shipped…` 넷이고, 그 넷이 **동일성 단언**을
 * 거친 뒤 여기로 넘긴다. 그래서 이 모듈의 함수는 **단언을 거치지 않는 입구**이고, 소비자가 여기를
 * 직접 부르면 손댄 텍스트가 그대로 들어온다 — 그 자리가 ⑥ 을 다시 여는 곳이다.
 *
 * **그 자리를 호출 시점의 호출자 동일성으로 닫는다**(r1 — verifier H-1). 앞 판은 **import 그래프**
 * 술어 하나로 막았는데, 그것은 **지정자 문자열**을 보는 층이라 평범한 획득 꼴 넷이 지나갔다:
 * 확장자 없는 정적 import(이 저장소의 관용이다) · `createRequire` · 템플릿 `import()` ·
 * `vi.importActual`. 꼴을 더 열거하는 길은 끝나지 않으므로, **누가 불렀는가**를 프레임으로 묻는다 —
 * 획득 표기가 프레임에 남지 않으므로 그 축이 통째로 사라진다. 정적 import 그래프 술어는 **보조로
 * 남긴다**(lint·test 시점에 이름으로 알려 주는 값이 있고, 그 주장은 「지정자로 적힌 획득」까지다).
 *
 * **왜 갈랐나**: 단언이 「내준 텍스트와 동일한가」로 올라가면서, 추출 술어의 양성·음성 대조가
 * 쓰던 **합성 Kotlin 조각**이 공개 입구에서 술어가 돌기 전에 거부됐다(열세 자리 RED). 술어를 재는
 * 자리와 출처를 재는 자리를 **다른 함수**로 두는 것이 그 둘을 함께 세우는 유일한 길이다.
 *
 * 파일도 서버도 열지 않는다 — 인자로 받은 텍스트뿐이다.
 */
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { callerOutside } from '../../scripts/kotlin-read-guard.mjs';

const SELF = fileURLToPath(import.meta.url);
const HERE = dirname(SELF);

/** 커널을 **부를 수 있는** 자리 — 공개 입구와 그 단위 test 둘이다. */
const CALL_ALLOWED = new Set([resolve(HERE, 'opening-axis.mjs'), resolve(HERE, 'opening-axis.test.mjs')]);

/**
 * 호출자가 그 둘 가운데 하나인지 단언한다. **획득 꼴을 보지 않는다** — 어떤 구문으로 이 모듈을
 * 얻었든 부르는 순간 프레임이 선다.
 */
function assertOwnedCaller(what) {
  const caller = callerOutside(SELF);
  if (!CALL_ALLOWED.has(caller)) {
    throw new Error(
      `[opening-axis-extract] ${caller} 가 추출 커널(${what})을 직접 불렀다 — ` +
        '출하 본문은 opening-axis.mjs 의 shipped… 입구로만 읽는다(그 입구가 동일성을 단언한다)',
    );
  }
}

/** 업무 키 → 출하 대분류 토큰. 이 표가 두 자리의 배정 등식의 기준이다. */
export const DIVISION_TOKENS = { construction: 'CONSTRUCTION', service: 'SERVICE' };

/**
 * 일치가 **정확히 하나**인 리터럴(M9 v M-1). 첫 일치를 쓰면 가리는 줄이 조용히 이기고, 둘 이상은
 * 「어느 것이 정본인가」가 서지 않는다 — 둘 다 거부로 닫는다. 패턴은 전역이어야 한다.
 */
function soleLiteral(text, pattern, what) {
  const matches = [...text.matchAll(pattern)];
  if (matches.length !== 1) {
    throw new Error(
      `출하 기본값에서 ${what} 의 일치가 ${matches.length} 개다(정확히 하나여야 한다) — ` +
        '정본의 모양이 바뀌었거나 같은 선언이 두 자리에 있다',
    );
  }
  return matches[0][1];
}

/**
 * 출하 속성 파일 본문 → 개찰 축 경로 표.
 *
 * 업무 키를 믿지 않고 블록 안의 `division =` 토큰까지 본다 — 키만 보면 두 블록의 내용이 통째로
 * 뒤바뀐 경우를 지나보낸다.
 */
export function openingOperationPathsOf(text) {
  assertOwnedCaller('openingOperationPathsOf');
  const categories = Object.keys(DIVISION_TOKENS);
  const divisions = {};
  for (const [index, category] of categories.entries()) {
    const start = text.indexOf(`"${category}" to`);
    if (start < 0) {
      throw new Error(`출하 기본값에서 업무 ${category} 의 개찰 축 블록을 찾지 못했다`);
    }
    // 블록은 **다음 업무 표지까지**다(없으면 끝까지) — 고정 길이나 첫 `),` 로 끊으면 표의 서식이
    // 바뀔 때 조용히 옆 블록을 함께 읽는다.
    const next = categories[index + 1];
    const end = next === undefined ? text.length : text.indexOf(`"${next}" to`, start);
    const block = text.slice(start, end < 0 ? text.length : end);
    const declared = soleLiteral(
      block,
      /division = BusinessDivision\.(\w+)/gu,
      `업무 ${category} 의 대분류`,
    );
    if (declared !== DIVISION_TOKENS[category]) {
      throw new Error(`업무 ${category} 의 대분류가 ${declared} 다 — 표의 업무 배정이 뒤바뀌었다`);
    }
    divisions[category] = {
      openingResultListPath: soleLiteral(
        block,
        /openingResultListPath = "([^"]+)"/gu,
        `${category} 개찰결과 목록 경로`,
      ),
      reservePriceDetailPath: soleLiteral(
        block,
        /reservePriceDetailPath = "([^"]+)"/gu,
        `${category} 예비가격 상세 경로`,
      ),
      baseAmountPath: soleLiteral(block, /baseAmountPath = "([^"]+)"/gu, `${category} 기초금액 경로`),
    };
  }
  return {
    ...divisions,
    shared: {
      openingCompletePath: soleLiteral(
        text,
        /val openingCompletePath: String = "([^"]+)"/gu,
        '개찰완료 경로',
      ),
      bidPriceFormulaAPath: soleLiteral(
        text,
        /val bidPriceFormulaAPath: String = "([^"]+)"/gu,
        'A값 경로',
      ),
    },
  };
}

/**
 * 출하 본문 → **공고 하나가 받는 상세 축의 수**.
 *
 * 손으로 적지 않는 이유: 공사는 넷이고 **용역은 셋이다**(A값이 공사 전용) — 그 비대칭을 리터럴로
 * 두면 업종 목록에서 닫은 바로 그 드리프트를 다시 만든다.
 */
export function detailAxisCountsOf(text) {
  assertOwnedCaller('detailAxisCountsOf');
  const open = text.indexOf('fun detailAxesFor');
  if (open < 0) {
    throw new Error('출하 본문에서 상세 축 열거 함수를 찾지 못했다 — 정본의 모양이 바뀌었다');
  }
  // 함수 본문만 본다 — 뒤의 열거 선언(`enum class DetailAxis`)에도 같은 토큰이 있다.
  const body = text.slice(open, text.indexOf('internal enum class DetailAxis', open));
  const counts = {};
  for (const [category, token] of Object.entries(DIVISION_TOKENS)) {
    const start = body.indexOf(`BusinessDivision.${token}`);
    if (start < 0) {
      throw new Error(`상세 축 열거에서 업무 ${token} 의 갈래를 찾지 못했다`);
    }
    // 그 갈래의 본문은 `->` 뒤부터 다음 갈래(또는 끝)까지다.
    const from = body.indexOf('->', start);
    const rest = body.slice(from);
    const nextBranch = rest.slice(2).search(/BusinessDivision\./u);
    const branch = nextBranch < 0 ? rest : rest.slice(0, nextBranch + 2);
    const axes = [...branch.matchAll(/DetailAxis\.(\w+)/gu)].map((match) => match[1]);
    if (axes.length === 0) {
      throw new Error(`업무 ${token} 의 상세 축이 0 이다 — 갈래 판독이 빗나갔다`);
    }
    counts[category] = new Set(axes).size;
  }
  return counts;
}

/** 출하 속성 파일 본문 → 공고 목록 오퍼레이션 표(위와 같은 사유의 등식). */
export function noticeListOperationsOf(text) {
  assertOwnedCaller('noticeListOperationsOf');
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
export function progressLabelsOf(text) {
  assertOwnedCaller('progressLabelsOf');
  const labels = {};
  for (const match of text.matchAll(/"([^"]+)" to OpeningProgress\.(\w+)/gu)) {
    // 같은 라벨이 두 번 등재되면 **뒤가 조용히 이긴다** — 어느 것이 정본인지 서지 않으므로 거부한다.
    if (labels[match[1]] !== undefined && labels[match[1]] !== match[2]) {
      throw new Error(`출하 정책에 라벨 ${match[1]} 이 두 값으로 등재돼 있다`);
    }
    labels[match[1]] = match[2];
  }
  if (Object.keys(labels).length === 0) {
    throw new Error('출하 정책에서 진행구분 라벨 표를 찾지 못했다 — 정본의 모양이 바뀌었다');
  }
  return labels;
}
