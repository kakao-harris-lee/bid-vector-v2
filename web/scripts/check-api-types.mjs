#!/usr/bin/env node
// ADR 0011 D-2 — 생성 타입과 OpenAPI 문서의 **바이트 등식**. 문서를 고치고 생성을 잊은
// 경로와 생성물을 손으로 고친 경로가 같은 자리에서 막힌다.
//
// 비교는 바이트로 한다(문자열 정규화·정렬 없음). 정규화를 넣으면 「무엇이 같은 것인가」가
// 이 스크립트의 판단이 되고, 그 판단이 생성기와 어긋나는 순간 게이트가 조용히 통과한다.
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const schemaPath = resolve(webRoot, '..', 'openapi', 'bidvector-operator-api.yaml');
const committedPath = resolve(webRoot, 'src', 'api', 'generated', 'operator-api.ts');

const workDir = mkdtempSync(join(tmpdir(), 'bidvector-api-types-'));
const regeneratedPath = join(workDir, 'operator-api.ts');

// 실패 경로에서도 임시 디렉터리를 치운다(code-review r1 F-5) — `process.exit()` 는 `finally` 를
// 건너뛰므로 즉시 나가지 않고 **종료 코드만 세운 뒤** 정리까지 끝내고 자연 종료한다. 같은 클래스를
// 이 저장소가 한 번 고쳤다(`tools/one-command-check.sh` 의 wheel 디렉터리).
try {
  execFileSync(
    process.execPath,
    [resolve(webRoot, 'node_modules', 'openapi-typescript', 'bin', 'cli.js'), schemaPath, '-o', regeneratedPath],
    { cwd: webRoot, stdio: ['ignore', 'inherit', 'inherit'] },
  );

  const regenerated = readFileSync(regeneratedPath);
  let committed;
  try {
    committed = readFileSync(committedPath);
  } catch {
    console.error(
      `생성 타입이 커밋되어 있지 않다: ${committedPath}\n  고치는 법: web/ 에서 \`npm run api:types\``,
    );
    process.exitCode = 1;
  }

  if (committed !== undefined) {
    if (committed.equals(regenerated)) {
      console.log(`생성 타입이 OpenAPI 문서와 바이트까지 같다 (${committed.byteLength} bytes).`);
    } else {
      console.error(
        '생성 타입이 OpenAPI 문서와 어긋난다 — 커밋본과 재생성본의 바이트가 다르다.\n' +
          `  커밋본: ${committedPath} (${committed.byteLength} bytes)\n` +
          `  재생성: ${schemaPath} 기준 (${regenerated.byteLength} bytes)\n` +
          '  고치는 법: web/ 에서 `npm run api:types` 뒤 생성물을 커밋한다.',
      );
      process.exitCode = 1;
    }
  }
} finally {
  rmSync(workDir, { recursive: true, force: true });
}
