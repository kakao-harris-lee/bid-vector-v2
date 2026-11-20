// **컨트롤의 경계는 `control-line` 토큰을 쓴다**(M14/14A · 팀장 판정 2026-11-20).
//
// 대비 게이트가 그 토큰을 3:1 로 재는데, 컨트롤이 **다른 토큰**을 쓰면 그 측정은 아무것도
// 지키지 못한다 — 재는 값과 쓰는 값이 갈리는 자리다. 이 게이트가 그 둘을 묶는다.
//
// 술어는 **AST** 다. 문자열 훑기로는 어느 요소의 class 인지 알 수 없고(한 파일 안에 카드와
// 입력칸이 함께 산다), 카드 테두리는 `line` 을 **써야 하므로** 토큰 이름만 세는 판으로는
// 둘을 가를 수 없다.
//
// 모집단은 **내장 컨트롤 태그 전수**다. 목록이 아니라 태그 집합이라, 새 입력칸이 생기면
// 자동으로 든다.
import { readFileSync, readdirSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';

import ts from 'typescript';

/**
 * 손가락과 키보드가 **직접 다루는** 내장 요소. `label` 은 넣지 않는다 — 그 자신은 경계를 그리는
 * 자리가 아니고, 감싼 컨트롤이 자기 테두리를 든다.
 */
export const CONTROL_TAGS = ['input', 'select', 'textarea', 'button'];

/** 카드·가름선의 토큰 — 컨트롤에 오면 안 되는 쪽. */
export const CARD_LINE_CLASS = 'border-line';

/** 컨트롤의 토큰 — 대비 게이트가 재는 쪽. */
export const CONTROL_LINE_CLASS = 'border-control-line';

const isTsx = (name) => name.endsWith('.tsx') && !name.endsWith('.test.tsx');

export function sourceFilesIn(root) {
  const found = [];
  const walk = (directory) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const child = join(directory, entry.name);
      if (entry.isDirectory()) {
        walk(child);
      } else if (entry.isFile() && isTsx(entry.name)) {
        found.push(child);
      }
    }
  };
  walk(root);
  return found.sort();
}

/** class 글자에서 토큰 하나가 **홀로** 섰는가 — 부분 일치로 재면 긴 이름이 짧은 이름에 걸린다. */
const hasClassToken = (text, token) => text.split(/\s+/u).includes(token);

/**
 * 한 원문에서 **컨트롤 태그가 카드 토큰을 쓰는 자리**. 반환은 `태그 "클래스"` 꼴의 글자다 —
 * 좌표(`file:line`)를 쓰지 않는다(이 slice 가 편집하는 파일이다).
 */
export function cardLineOnControlsIn(fileName, text) {
  const source = ts.createSourceFile(fileName, text, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX);
  const found = [];
  const visit = (node) => {
    if (ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) {
      const tag = node.tagName.getText(source);
      if (CONTROL_TAGS.includes(tag)) {
        for (const attribute of node.attributes.properties) {
          if (
            !ts.isJsxAttribute(attribute) ||
            attribute.name.getText(source) !== 'className' ||
            attribute.initializer === undefined
          ) {
            continue;
          }
          // 글자든 식이든 **원문 전체**를 본다 — 템플릿·삼항 안에 숨겨도 같은 글자가 거기 있다.
          const raw = attribute.initializer.getText(source);
          if (hasClassToken(raw.replaceAll(/[`'"${}]/gu, ' '), CARD_LINE_CLASS)) {
            found.push(`${tag} ${raw.slice(0, 60)}`);
          }
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return found;
}

/** 출하 트리 전수의 위반. 빈 배열이면 통과다. */
export function cardLineOnControls(srcRoot) {
  return sourceFilesIn(srcRoot).flatMap((file) =>
    cardLineOnControlsIn(relative(resolve(srcRoot, '..'), file).split(sep).join('/'), readFileSync(file, 'utf8')),
  );
}

/** 컨트롤 토큰을 **실제로 쓰는** 자리 수 — 0 이면 위 단언이 공허하다. */
export function controlLineUses(srcRoot) {
  return sourceFilesIn(srcRoot).reduce((total, file) => {
    const text = readFileSync(file, 'utf8');
    return total + (text.match(new RegExp(CONTROL_LINE_CLASS, 'gu'))?.length ?? 0);
  }, 0);
}
