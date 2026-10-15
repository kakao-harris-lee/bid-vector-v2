# M7/7A commands — P-5·P-6

출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이고 감사자는 명령을 다시 돌린다. 라운드 이력 절을
두지 않는다. **마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트**다 — 이 표는 그
직전까지를 담는다(evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다).

우회 실측의 명령·종료 코드·판정은 `checklist.md` §3 의 표에 둔다 — 같은 사실을 두 자리에 적지 않는다.

## acceptance — `web` job 전 step 그대로 · 산출물 HEAD `f20341b6`

작업 디렉터리는 `web/`(job 의 `defaults.run.working-directory`와 같다). 뺀 CI 전용 step 은 둘 —
`actions/checkout`·`actions/setup-node`(툴체인 설치)와 `npx playwright install-deps chromium`
(브라우저의 시스템 패키지, apt).

| step | cmd | exit | 핵심 결과 |
|---|---|---|---|
| W-1 | `npm ci` | 0 | 229 packages |
| W-2 | `npm run lint` | 0 | 소스·게이트 스크립트·설정·e2e 전부를 본다 |
| W-3 | `npm run typecheck` | 0 | 진단 0 |
| W-4 | `npm run gate:src-tree` | 0 | src 는 web/ 안 실디렉터리 · 네 옵션 단언 통과 · web/ 링크 0 · 프로그램 파일 184(src 안 9) 전부 src·node_modules 안 · `openapi-fetch` import 집합 등식 성립 · 비리터럴 동적 import 0 · 번들 경계 플러그인 등재됨 |
| W-5 | `npm run api:types:check` | 0 | 커밋본과 재생성본 바이트 동일(30879) |
| W-6 | `npm run test` | 0 | 2 files · 7 tests passed(라우트 표 + 번들 경계 판정 술어) |
| W-7 | `npm run build` | 0 | 152 modules · 번들 경계 플러그인 통과 |
| W-8 | `npx playwright install chromium` | 0 | 설치 완료 |
| W-9 | `npm run test:e2e` | 0 | 2 passed (`vite preview` 로 띄운 **빌드 산출물**: 셸 · 알 수 없는 주소) |

## acceptance — 완료 조건 1 (S-20) · 같은 HEAD

| cmd | exit | 핵심 결과 |
|---|---|---|
| `./tools/one-command-check.sh` | 0 | Kotlin `check`·`qualityBaseline` BUILD SUCCESSFUL · Python 1605 passed · web W-1~W-9 전부 통과(마지막 줄 「Kotlin 전건 + Python 전건 + web 전건 통과」) |

스크립트의 web 절이 `web` job 과 **같은 명령·같은 순서**인지는 두 파일에서 명령 줄만 뽑아 대조했다
(차이는 뺀 CI 전용 step 둘뿐). CI 에서 이 스크립트를 돌리는 `check` job 에는 그 둘이 그 job 안에
있다 — `setup-node`(`web/.nvmrc`·npm 캐시)와 Playwright 시스템 의존 설치.

## 호스트 규율 — 무거운 빌드 전 별도 호출 셋

| cmd | 결과 |
|---|---|
| `pgrep -af 'GradleWrapperMain\|GradleWorkerMain' \| grep -v pgrep` | exit 1 — 도는 빌드 없음 |
| `free -m` | available 18139MB(≥6GB) · Swap free 6429MB(≥2GB) |
| `ps -eo pid,rss,args --sort=-rss \| head` | 최상위 RSS 약 0.57GB — 큰 프로세스 옆이 아니다 |

## 돌리지 않은 job 의 생략 사유 — 기계 확인

| 확인 | 결과 |
|---|---|
| `git diff --name-only d930d54c..f20341b6 -- ml-engine` | 0행 → `ml-engine` job 입력 무변경 |
| `git diff --name-only d930d54c..f20341b6 -- docker contracts tools/{image-hygiene,db-*,vuln-scan,contract-crosslang-smoke}.sh config/quality app adapters shared-kernel` | 0행 → `container` job 입력 무변경 |
| `git diff --name-only d930d54c..f20341b6 -- '*.kt' '*.kts' '*.py' '*.proto'` | 0행 → 컴파일 대상 소스 무변경 |
| `git diff --numstat d930d54c..f20341b6 -- .github/workflows/ci.yml` | 추가만(삭제 0) — `web` job 신설 + `check` job 에 툴체인 step 둘 → 다른 job 의 줄은 지워진 것도 바뀐 것도 없다 |

## rollback 실측 — 저장소 밖 임시 clone, HEAD `f20341b6`

| cmd | exit | 핵심 결과 |
|---|---|---|
| `git restore --source=d930d54c --staged --worktree -- <in_scope 경로 개별 인자>` | 0 | A 26 → D 26 · M 4 · base 대비 diff 0행 · `web/` 사라짐 |
| 되돌린 트리에서 `./gradlew --no-daemon check` | 0 | BUILD SUCCESSFUL in 9m 28s · `leakPatternGate` 가 UP-TO-DATE 가 아니라 실제로 실행됨 |

자세한 전제·목록·술어는 `rollback.md`.

## 비밀값 스캔 — 패턴 파일 참조형

`grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m7/7a/`
(`node_modules`·`dist`·Playwright 산출 디렉터리 제외)

- evidence 디렉터리만 대상으로 한 같은 명령: **exit 1 — 매치 없음**. 루트 `leakPatternGate` 의
  scanRoot 가 그 디렉터리이므로 이 slice 가 게이트에 새 매치를 더하지 않았다.
- in_scope 전체로 넓히면 잡히는 것은 전부 설명된다: **이 slice 의 diff 밖**의 기존 줄(`.gitignore` 의
  비밀값 파일 규약 · `container` job 의 DB 값 생성)과, 잠금 파일에 적힌 **npm 패키지 이름**이 스캔
  어휘와 겹쳐 생기는 행(비밀값이 아니다 — 그 어휘를 여기 축어로 적지 않는다). 육안 확인: 번들·소스에
  자격 값·연락처·사업자 정보 없음. 인증은 7D 소관이고 이 slice 는 `VITE_` 접두 공개 설정 자리
  하나만 둔다.
