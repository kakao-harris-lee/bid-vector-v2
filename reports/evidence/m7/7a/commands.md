# M7/7A commands — P-5·P-6

출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이고 감사자는 명령을 다시 돌린다. 라운드 이력 절을
두지 않는다. **마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트**다 — 이 표는 그
직전까지를 담는다(evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다).

우회 실측(U-1~U-8)의 명령·종료 코드·판정은 `checklist.md` 2절의 표 하나에 둔다 — 같은 사실을 두
자리에 적지 않는다.

## 2026-10-14 (UTC) · acceptance — `web` job 전 step 그대로 · HEAD `dd33d4bb`

작업 디렉터리는 `web/`(job 의 `defaults.run.working-directory`와 같다). 뺀 CI 전용 step 은 둘 —
`actions/checkout`·`actions/setup-node`(툴체인 설치)와 `npx playwright install-deps chromium`
(브라우저의 시스템 패키지, apt).

| step | cmd | exit | 핵심 결과 |
|---|---|---|---|
| W-1 | `npm ci` | 0 | 224 packages |
| W-2 | `npm run lint` | 0 | eslint 가 실제로 본 파일 13(소스·게이트 스크립트 둘·설정 둘·e2e) |
| W-3 | `npm run typecheck` | 0 | 진단 0 |
| W-4 | `npm run gate:src-tree` | 0 | rootDir·include·allowJs·paths 단언 통과 · src 심볼릭 링크 0 · 프로그램 파일 8 전부 src 안 |
| W-5 | `npm run api:types:check` | 0 | 커밋본과 재생성본 바이트 동일(30879) |
| W-6 | `npm run test` | 0 | 1 file, 2 tests passed |
| W-7 | `npm run build` | 0 | 152 modules, dist 생성 |
| W-8 | `npx playwright install chromium` | 0 | chromium 설치 완료 |
| W-9 | `npm run test:e2e` | 0 | 1 passed (vite preview 로 띄운 **빌드 산출물**에서) |

## 2026-10-14 (UTC) · acceptance — 완료 조건 1 (S-20) · 같은 HEAD

| cmd | exit | 핵심 결과 |
|---|---|---|
| `./tools/one-command-check.sh` | 0 | Kotlin `check` BUILD SUCCESSFUL in 9m 46s · `qualityBaseline` 21s · Python 1605 passed · web W-1~W-9 전부 통과(마지막 줄 「Kotlin 전건 + Python 전건 + web 전건 통과」) |

스크립트의 web 절이 `web` job 과 **같은 명령·같은 순서**인지는 두 파일에서 명령 줄만 뽑아 대조했다
(차이는 뺀 CI 전용 step 둘뿐).

## 2026-10-14 (UTC) · 호스트 규율 — 무거운 빌드 전 별도 호출 셋

| cmd | 결과 |
|---|---|
| `pgrep -af 'GradleWrapperMain\|GradleWorkerMain' \| grep -v pgrep` | exit 1 — 도는 빌드 없음 |
| `free -m` | available 18618MB(≥6GB) · Swap free 6356MB(≥2GB) |
| `ps -eo pid,rss,args --sort=-rss \| head` | 최상위 RSS 약 0.57GB — 큰 프로세스 옆이 아니다 |

## 2026-10-14 (UTC) · 돌리지 않은 job 의 생략 사유 — 기계 확인

| 확인 | 결과 |
|---|---|
| `git diff --name-only d930d54c..HEAD -- ml-engine` | 0행 → `ml-engine` job 입력 무변경 |
| `git diff --name-only d930d54c..HEAD -- docker contracts tools/{image-hygiene,db-*,vuln-scan,contract-crosslang-smoke}.sh config/quality app adapters shared-kernel` | 0행 → `container` job 입력 무변경 |
| `git diff --name-only d930d54c..HEAD -- '*.kt' '*.kts' '*.py' '*.proto'` | 0행 → 컴파일 대상 소스 무변경 |
| `git diff --numstat d930d54c..HEAD -- .github/workflows/ci.yml` | `68 0` · hunk 하나(`@@ -179,6 +179,74 @@`) → 다른 job 의 줄은 **지워진 것도 바뀐 것도 없다** |

## 2026-10-14 (UTC) · rollback 실측 — 저장소 밖 임시 clone, HEAD `dd33d4bb`

| cmd | exit | 핵심 결과 |
|---|---|---|
| `git restore --source=d930d54c --staged --worktree -- <in_scope 경로 개별 인자>` | 0 | D 22 · M 4 · base 대비 diff 0행 |
| 되돌린 트리에서 `./gradlew --no-daemon check` | 0 | BUILD SUCCESSFUL in 9m 19s(`leakPatternGate` 포함) |

자세한 전제·목록·술어는 `rollback.md`.

## 2026-10-14 (UTC) · 비밀값 스캔 — 패턴 파일 참조형

`grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m7/7a/`
(`node_modules`·`dist`·Playwright 산출 디렉터리 제외)

- evidence 디렉터리만 대상으로 한 같은 명령: **exit 1 — 매치 없음**. 루트 `leakPatternGate` 의
  scanRoot 가 그 디렉터리이므로 이 slice 가 게이트에 새 매치를 더하지 않았다.
- in_scope 전체로 넓히면 7행이 잡히고 전부 설명된다: 넷은 **이 slice 의 diff 밖**의 기존 줄
  (`.gitignore` 의 비밀값 파일 규약 세 줄 · `container` job 의 DB 값 생성 한 줄), 셋은 잠금 파일에
  적힌 **npm 패키지 이름** 하나가 스캔 어휘 하나와 겹쳐 생긴다(비밀값이 아니다 — 그 어휘를 여기
  축어로 적지 않는다). 육안 확인: 번들·소스에 자격 값·연락처·사업자 정보 없음. 인증은 7D 소관이고
  이 slice 는 `VITE_` 접두 공개 설정 자리 하나만 둔다.
