// 걷어낸 **부재 어휘**가 다시 들어오지 않는가 — `web/` 트리 전수 훑기(M14/14A ④).
//
// 이 slice 의 acceptance 는 **열거가 아니라 측정**이다(계약). 열거는 낡지만 훑기는 낡지 않는다 —
// 새 화면이 그 글자를 손으로 적으면 목록을 고치지 않아도 여기서 붉는다.
//
// **훑기는 보조 층이다.** 첫째 층은 타입이다: 공유 상수가 없고 부재의 얼굴이 사유를 **필수
// 인자**로 받으므로, 사유를 고르지 않고는 부재를 그릴 수 없다. 이 층이 받는 것은 그 길을 비켜
// **글자를 손으로 적는** 꼴이다.
//
// 술어를 데이터로 두고 판정을 순수 함수로 가른 이유는 대조 때문이다 — 합성 트리를 먹여
// 「심으면 찾는가 · 차감 아래면 못 찾는가」를 실제 파일시스템 없이 반복해 잴 수 있다.
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';

/**
 * 훑는 뿌리 — **한 자리**다. 뿌리를 좁히는 것이 이 게이트를 비우는 가장 싼 길이라(설계 검토
 * 우회 A-8) 짝 test 가 이 집합을 단언한다.
 */
export const SWEEP_ROOTS = ['src', 'e2e', 'scripts'];

/**
 * 차감 — **생성 타입 하나뿐**이다. 그 글은 OpenAPI 설명에서 오고 부재 어휘가 아니라 **부분
 * 일치**로 걸린다(「지어낸 기본값이 없다」는 뜻의 문장이다). 그 파일을 고치는 것은 14E 몫이자
 * 승인 경계라 분모에서 뺀다.
 *
 * **차감은 넓어지는 쪽으로만 자란다**(설계 검토 우회 A-6). 그래서 짝 test 가 이 목록의 크기를
 * 단언한다 — 한 줄이 늘면 그 자리에서 멈춘다.
 */
export const SWEEP_DEDUCTIONS = ['src/api/generated'];

/** 훑는 확장자 — 소스와 데이터 전부. 축어 목록이 아니라 「텍스트로 읽을 수 있는 것」이다. */
const SWEPT_EXTENSIONS = ['.ts', '.tsx', '.mjs', '.js', '.json', '.css', '.html', '.txt'];

/**
 * 걷어낸 문면 — **조각에서 조립한다.** 축어로 적으면 이 파일이 자기 훑기에 걸리고, 그것을
 * 피하려고 차감을 두면 차감이 한 줄 자란다. 조립하면 둘 다 생기지 않는다.
 */
export const RETIRED_WORDING = ['값', '없음'].join(' ');

const isSwept = (name) => SWEPT_EXTENSIONS.some((one) => name.endsWith(one));

/** 차감 아래인가 — **경로 성분**으로 비교한다(접두 비교는 이름이 겹치는 이웃을 함께 뺀다). */
function isDeducted(relativePath, deductions) {
  const parts = relativePath.split('/');
  return deductions.some((one) => {
    const prefix = one.split('/');
    return prefix.every((segment, index) => parts[index] === segment);
  });
}

/**
 * 훑을 파일의 `web/` 기준 상대 경로 전수. 뿌리가 하나라도 없으면 **던진다** — 빈 목록은 모든
 * 단언을 공허하게 만든다.
 */
export function sweepFiles(webRoot, roots = SWEEP_ROOTS, deductions = SWEEP_DEDUCTIONS) {
  const found = [];
  for (const root of roots) {
    const absolute = resolve(webRoot, root);
    if (!statSync(absolute, { throwIfNoEntry: false })?.isDirectory()) {
      throw new Error(`훑기 뿌리가 없다: ${root}`);
    }
    const walk = (directory) => {
      for (const entry of readdirSync(directory, { withFileTypes: true })) {
        const child = join(directory, entry.name);
        if (entry.isDirectory()) {
          walk(child);
          continue;
        }
        if (!entry.isFile() || !isSwept(entry.name)) {
          continue;
        }
        const relativePath = relative(webRoot, child).split(sep).join('/');
        if (!isDeducted(relativePath, deductions)) {
          found.push(relativePath);
        }
      }
    };
    walk(absolute);
  }
  return found.sort();
}

/**
 * 파일 목록에서 걷어낸 문면이 선 자리. `read` 를 받는 것이 대조의 입구다 — 합성 트리를 먹여
 * 같은 술어를 돌린다(사본을 만들지 않는다).
 */
export function retiredOccurrencesIn(files, read, wording = RETIRED_WORDING) {
  const found = [];
  for (const file of files) {
    const text = read(file);
    text.split('\n').forEach((line, index) => {
      if (line.includes(wording)) {
        found.push({ file, line: index + 1 });
      }
    });
  }
  return found;
}

/** 실제 트리의 판정 — 위 둘을 잇는다. */
export function retiredOccurrences(webRoot, roots = SWEEP_ROOTS, deductions = SWEEP_DEDUCTIONS) {
  const files = sweepFiles(webRoot, roots, deductions);
  return retiredOccurrencesIn(files, (file) => readFileSync(resolve(webRoot, file), 'utf8'));
}
