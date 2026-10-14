# M7/7A checklist — P-5(`web/` 골격)·P-6(one-command)

- 계약: 같은 디렉터리의 `scope.md`. 설계 검토: `_workspace/m7-7a/01_design-review.md`(우회 표 U-1~U-8).
- base `d930d54c` · 브랜치 `m7-7a/2026-10-14` · 이 문서가 담는 마지막 산출물 커밋 `dd33d4bb`.
- 명령과 종료 코드는 `commands.md`, 되돌림은 `rollback.md`.
- 문서 산출물(P-1~P-4)은 세션 모델 레인 소관이라 이 문서에 넣지 않는다.

## 1. 수용 조건 대조

| 조건(scope.md) | 충족 근거 |
|---|---|
| P-5 「`web` job 명령 그대로 로컬 exit 0」 | `web` job 의 W-1~W-9 를 순서대로 그대로 실행해 전부 exit 0(`commands.md` acceptance 절). CI 전용 step 둘(checkout·setup-node, `install-deps`)은 로컬에서 성립하지 않아 뺐고 그 둘이 게이트가 아니다 |
| P-5 「생성 타입 check 모드 음성 대조(OpenAPI 한 줄 바꾸면 RED)」 | 아래 U-6 두 행 — 문서를 바꾸고 재생성하지 않은 트리와 생성물을 손으로 고친 트리 둘 다 exit 1. 두 변이 모두 되돌린 뒤 같은 명령이 exit 0 으로 복귀 |
| P-5 「legacy 경로·타입 import 0(게이트)」 | 아래 U-1~U-5·U-8 전부 RED. **빈 자리의 초록이 아니라 변이의 RED 로** 잰다 — 깨끗한 트리의 통제군 행이 같은 세 명령에서 exit 0 인 것과 짝으로 본다 |
| P-5 「화면 구현 없음(빈 셸 + 라우트 자리 다섯)」 | 자리 여섯(화면 다섯 + 알림함)의 빈 셸뿐이고 질의·상태·디자인 토큰을 두지 않았다. 자리 표가 정본이고 라우터는 거기서 파생한다 — 단위 test 가 경로 여섯·순서·id 중복 0 을 잠근다(W-6) |
| P-6 「S-20 exit 0」 | `tools/one-command-check.sh` 가 Kotlin 전건 → Python 전건 → web 전건을 돌아 exit 0(`commands.md`). 스크립트의 web 절 명령은 `web` job 의 명령과 **순서까지 같다**(같은 절에 기계 대조 한 줄) |

## 2. 우회 실측 (설계 검토 U-1~U-8)

판정 기준: 세 게이트 명령(`npm run typecheck` · `npm run gate:src-tree`(W-4) · `npm run lint`) 중
**하나라도 비0** 이면 RED. 변이는 **바꿔치우기**다 — 각 행은 깨끗한 트리에서 같은 세 명령이 exit 0
(아래 통제군 행)인 것과 대조해 읽는다. 모든 변이는 산출물 커밋 뒤에 심고 바로 되돌렸으며, 되돌림은
`git checkout --` 이 아니라 사본 복원·신규 파일 삭제로 했다(미커밋 편집 파괴 금지).

| # | 변이 | typecheck | W-4 | lint | 판정 |
|---|---|---|---|---|---|
| — | 통제군 — 변이 없는 산출물 트리 | 0 | 0 | 0 | GREEN(대조 기준) |
| U-1 | legacy `.ts` 를 상대 경로로 import | 2 (TS6059) | 0 | 0 | **RED** |
| U-2 | tsconfig `paths` 별칭이 legacy 를 가리킴 + 그 별칭으로 import | 2 (TS6059) | 1 | 0 | **RED** |
| U-3 | `src` 안 심볼릭 링크(파일)가 legacy `.ts` 를 가리킴 | **0** | 1 | 0 | **RED** (W-4 가 혼자 막는다) |
| U-3b | 같은 것을 디렉터리 링크로 | 2 | 1 | 0 | **RED** |
| U-4a | `// @ts-ignore` + legacy import | **0** | 0 | 1 (ban-ts-comment) | **RED** |
| U-4b | `// @ts-expect-error <설명>` + legacy import | 2 (쓰이지 않은 지시) | 0 | 1 | **RED** |
| U-4c | `// @ts-nocheck` + legacy import | **0** | 0 | 1 | **RED** |
| U-4d | 첫 줄이 `@ts-nocheck` 로 시작하는 **산문 주석** + legacy import | **0** | 0 | 1 | **RED** |
| U-5a | 컴포넌트에서 전역 `fetch('/api/…')` | — | — | 1 (no-restricted-globals) | **RED** |
| U-5b | `window.fetch('/api/…')` | — | — | 1 (no-restricted-properties) | **RED** |
| U-5c | `new XMLHttpRequest()` | — | — | 1 (no-restricted-globals) | **RED** |
| U-5d | `axios` import | — | — | 1 (no-restricted-imports) | **RED** |
| U-5e | `openapi-fetch` 를 클라이언트 자리 밖에서 직접 import | — | — | 1 (no-restricted-imports) | **RED** |
| U-5 통제군 | **같은 `fetch` 호출을 클라이언트 자리 안에서** | — | — | 0 | GREEN(설계대로 — 허용 자리가 실제로 하나임을 잰다) |
| U-6a | 생성 타입 파일을 손으로 고침 | — | `api:types:check` exit 1 | — | **RED** |
| U-6b | OpenAPI 문서 한 줄을 바꾸고 재생성 안 함(음성 대조) | — | `api:types:check` exit 1 (바이트 30879 ≠ 30894) | — | **RED** |
| U-7 | 동적 `import(변수)` 로 legacy 경로 — 「경계로 처리」 행 | 빌드 산출물의 legacy 경로 문자열 **0** (다섯 어휘) · 양성 대조로 같은 방식의 grep 이 번들에 실재하는 문자열 둘을 잡음 | | | 경계 확인 |
| U-8 | `allowJs: true` + rootDir 밖 legacy `.js` import | 2 (TS6059) | 1 | 0 | **RED** |
| U-9 | **표 밖 추가 실측** — tsconfig `files` 로 rootDir 밖 legacy 파일을 프로그램에 직접 더함 | 2 (TS6059) | 1 | 0 | **RED** |

## 3. 설계 검토 표의 정정 둘

1. **U-3 의 기대가 틀렸다.** 표는 「`preserveSymlinks` 기본값이라 실제 경로가 rootDir 밖 → typecheck
   RED」로 적었으나, 실측에서 tsc 는 **링크 경로**로 파일을 프로그램에 넣어 TS6059 를 내지 않았다
   (typecheck·lint 둘 다 exit 0 — 자립한 legacy 타입 파일을 링크해 정확한 export 를 import 한
   트리에서). 즉 1차 층은 비심볼릭 `.ts` 만 덮었다. 그 표가 「실측 필수 — 거동이 설정에 달림」이라
   적어 둔 자리에서 실제로 갈렸다. 닫은 방법: `web/scripts/check-src-tree.mjs`(W-4)가 tsconfig 의
   **유효 옵션을 컴파일러 API 로 읽어** `rootDir`·`include`·`allowJs`·`paths` 를 단언하고, `src` 안
   심볼릭 링크 0 과 프로그램 파일 전부의 realpath 가 `src` 안임을 본다. 열거하는 것은 legacy 경로
   이름이 아니라 **경계 자신의 전제**다.
2. **U-8 의 1차 측정이 오염됐다(정정 커밋 `dd33d4bb`).** 첫 probe 의 머리 주석이
   `// @ts-nocheck 없음 — …` 으로 시작해 그 토큰이 **지시어로 읽혀** 파일 전체 진단이 사라졌고, 그
   exit 0 을 「allowJs 에서는 TS6059 가 안 난다」로 읽었다. 깨끗한 probe 에서는 TS6059 가 난다(표의
   U-8 행). 오염 자체는 U-4d 행으로 남겼다 — 산문 주석도 지시어로 읽히며 lint 가 그것을 잡는다.
   앞 커밋(`391dab05`)의 메시지에 틀린 사유 문장이 남아 있다(이력을 되쓰지 않는다) — 교체 문장은
   정정 커밋 메시지와 이 항목이다. W-4 는 유지한다: 혼자 막는 것은 U-3 이고, 네 옵션 단언의 사유는
   「typecheck 는 자기 전제가 사라진 것을 보지 못한다」다.

## 4. 수정 라운드가 만든 새 파일 ↔ in_scope 대조

수정 라운드(`391dab05`·`dd33d4bb`)가 더한 새 파일은 `web/scripts/check-src-tree.mjs` 하나이고
in_scope 의 `web/**` 안이다. 계약 갱신 필요 없음(scope.md 의 in/out 집합이 그대로 덮는다).

## 5. 알려진 제한

- **비활성화 방법**: `web/` 는 런타임 코드가 아니고 어디서도 참조되지 않는다(Gradle `settings.gradle.kts`
  에 없고 Python 패키지도 아니다). CI 에서 끄는 길은 `web` job 삭제 하나다. 운영 영향 0.
- **W-4 가 덮지 않는 자리**: tsconfig 의 `types`/`typeRoots` 로 legacy `.d.ts` 를 **주변 선언**으로
  끌어오는 경로는 단언 밖이다(그 자리는 패키지 모양 디렉터리와 진입 선언 파일을 요구하고, 들어오는
  것은 값이 아니라 선언뿐이다). 7E 가 `types` 를 늘릴 때 같은 자리에 단언을 더한다.
- **생성 타입의 입력은 수작성 OpenAPI 문서 하나**라 legacy 가 겪은 환경 간 재생성 흔들림 축이 구조적으로
  없다(조사 §2). 입력이 생성물로 바뀌면(7C 가 문서를 기계 산출하면) 그 축이 생긴다.
- **의존 패키지 취약점 스캔 없음** — `OPEN-7A-WEB-DEPENDENCY-SCAN`(ADR 0011 §5, 7F 에서 닫는다).
  이 slice 는 `npm ci` 가 잠금 파일 밖을 해석하지 않는 것까지만 본다.
- **Playwright 스모크는 한 개**(셸이 뜬다). 여정 스모크는 7E·7D 뒤.
- `ml-engine`·`container` job 은 입력 무변경이라 이 slice 에서 돌리지 않았다 — 근거는 `commands.md`
  의 기계 확인 한 줄(in_scope ∩ 두 job 입력 = 공집합).

## 6. N/A 항목 (evidence-pack 규격)

- `differential.json`: N/A — Python 대조 대상 거동이 없다(골격에 도메인 계산이 없다).
- `golden-manifest.json`: N/A — fixture 를 쓰지 않는다. 생성 타입의 입력은 저장소의 OpenAPI 문서이고
  그 등식은 W-5 가 바이트로 잰다.
