# M7/7E-2 실행 명령과 종료 코드

base `fa5ca096` · 브랜치 `m7-7e2/2026-10-30`. 명령은 `web/` 에서 돌렸고(Kotlin 절만 저장소
뿌리), **종료 코드로** 판정한다. 출력은 핵심 한 줄만 적는다.

## Phase 1 착수 실측 (구현 전 — 계약이 화면을 덮는가)

| 물음 | 명령·자리 | 결과 |
| --- | --- | --- |
| D 의 탭·배지·경고가 기댈 필드가 있는가 | base OpenAPI 의 `BidCase` 스키마 | **있다** — `state`(enum 14) · `nextCheckpointKind`(enum 6, 항상 값) · `nextCheckpointAt`(nullable) · `overdue`(boolean, 서버 판정) · `reconciliationCandidate`. 클라이언트 추론이 필요한 축 **0** |
| 카드 제목을 줄 필드가 있는가 | 같은 스키마 | **없다** — 공고명 칸이 0 이고 번호·차수뿐이다(알려진 제한 1) |
| 탭별 총수를 줄 헤더가 있는가 | `listBidCases` 200 의 `headers` | **없다**(알려진 제한 2) |
| 사업자번호를 둘 칸이 있는가 | `Profile`·`BusinessDetailsRequest` | **없다** — `/business-details` 문면이 「자리가 없다」고 적는다 → D-7E2-7(빈 자리) |
| 전이표와 화면의 다음 행동이 맞는가 | `bidding` 모듈의 전이표 | BID_RECORDED→AWAIT_OPENING · AWAITING_OPENING→개찰 둘 · PROVISIONAL_FIRST→심사 진입·결과 · OUT_OF_RANK·UNDER_REVIEW→결과 |
| 감시 조건을 세우면 제안이 통과하는가 | 제안 use case 의 상태 판정 + 면허 게이트 | **통과한다** — 빈 `watchRules` 가 `WATCH_NOT_CONFIGURED` 이고, 프로필 미신고는 `Uncertain`(미보유가 아니다)이라 드롭되지 않는다 |
| 승인 목록 계수 | 게이트의 AST 수집기로 base ↔ HEAD | base **11** → HEAD **28**(더한 것 **17**: D 넷 + 설정 열셋) |

**계약 번호 정정**: D-7E2-8 의 「설정 +14 · 총 29」는 착수 보고의 산술 오류다. 열거한 목록은
그대로이고 참값은 **설정 +13 · 총 28** 이다(위 AST 계수).

## RED — 구현 전에 붉은 것을 확인한 자리

| 자리 | 명령 | RED |
| --- | --- | --- |
| 승인 목록 ↔ 호출 집합 양방향 등식 | `npx vitest run scripts/screen-operations.test.mjs` | **exit 1** — 목록만 먼저 더하니 `approvedButUnused` 17(1 failed / 17 passed). 같은 실행에서 「목록의 쌍이 전부 계약에 있다」는 통과 → 더한 쌍에 오타 0 |
| D 의 두 표 | `npx vitest run src/display/bidBoxDisplay.test.ts` | **exit 1** — 모듈 부재로 수집 0 → 구현 뒤 14 passed |
| D 화면 | `npx vitest run src/screens/BidBoxScreen.test.tsx` | **exit 1** — 모듈 부재 → 구현 뒤 20 가운데 6 이 「그 건이 다른 탭에 있다」로 붉었다(탭이 실제로 가린다는 증거) → 탭 선택 단계를 test 에 넣어 20 passed |

**솔직히 적는 것**: 설정 화면(전략·프로필)의 **화면 test 는 구현 뒤에 썼다**(표 test 는 먼저
썼다). 그 자리에 「처음부터 붉었다」는 증거가 없으므로 test 가 구현을 조이는지는 아래 변이 열하나가
든다 — 그리고 그 가운데 셋(M6·M8·M11)은 설정 화면·표만 잰다.

**test 를 변이로 재 보지 않으면 초록의 뜻을 모른다**(이 라운드의 자리 둘): ⓐ 포커스 회귀 test 를
처음 `fireEvent.focus(window)` 로 썼더니 **재렌더가 일어나지 않아** 구판 꼴에서도 초록이었다 —
RTL 의 `rerender` 로 바꾼 뒤에야 변이가 붉어졌다. ⓑ 중복 접기(r1 L-4)는 **어느 test 로도 잠겨
있지 않아** 접기를 걷는 변이가 394 passed 로 통과했다(CR r2 L2R-5 가 잡았다).

**그 공백이 실제로 물렸다**: 쓰기 쪽 test 가 쉼표 없는 `'1200000'` 만 넣었고 **왕복이 단언 밖**
이어서, 앞자리만 읽는 파서가 「읽히기는 하지만 다른 수」를 보내는 것을 아무 층도 보지 못했다
(verifier 가 실 서버로 잡았다 — 아래 입력 모양 절). 지금은 거부 경계 **스물하나**와 왕복 넷이
그 자리에 있다.

## 입력 모양 — 거부 경계와 왕복 (v H-1 · CR H-2 · M-1 의 회귀)

| 재는 것 | 명령 | 결과 |
| --- | --- | --- |
| 전략 값의 거부 경계 **열둘**(2^53 위 둘 포함) | `vitest src/display/editSessionDisplay.test.ts` | 0 — 쉼표·단위·지수·꼬리 문자·금액 소수점·금액 음수·건수 소수점·건수 음수·소수 칸 쉼표·소수 칸 지수가 **각각 `null`** |
| 받는 모양이 지나치지 않다 | 같은 파일 | 0 — `1200000000`·`0`·`-0.5`·`100` 과 앞뒤 공백 정규화는 그대로 받는다 |
| 「보낸 값 == 본문 값」 | 같은 파일 + `SettingsScreen.test.tsx` | 0 — 본문 `1` 이면 화면도 **「1원」**이다(원문을 되풀이하지 않는다) |
| 시평액 거부 경계 **열둘**(2^53 위 포함) | `vitest src/display/profileDisplay.test.ts` | 0 |
| 안전 정수 **경계는 받는다** | `editSessionDisplay.test.ts` | 0 — `MAX_SAFE_INTEGER` 는 그대로 받는다(거부가 지나치지 않다) |
| 화면에서 쉼표 입력 | `SettingsScreen.test.tsx`·`ProfileSection.test.tsx` | 0 — 거부 얼굴이 뜨고 **요청이 가지 않는다** |
| 여정에서 쉼표 입력 | 설정 여정(새 test) | 0 — `value` 요청 **0 건**, 받는 입력에서 화면 문면 == `postDataJSON()` 의 값 |

## 변이 — 각각 바꿔치우고 쟀다 (전부 커밋 뒤에 돌렸다)

| # | 바꿔치운 것 | 붉어진 자리 |
| --- | --- | --- |
| M1 | 상태 배지를 리터럴 「관심」으로 | vitest **1** · 여정 ④ **1**(받은 값을 적어 보인다) |
| M2a | 탭 표에서 상태 한 줄 삭제 | typecheck **2** — `satisfies` 자신 · 조회 자리 · test 가 **함께** 선다 |
| M2b | 전수 `switch` → 조용한 기본 분기 | typecheck **2** · vitest **1**(결과 기록 갈래가 사라진다) |
| M3 | `overdue` 판정을 시각 비교로 | vitest **2**(「참일 때 뜸」과 「지나도 거짓이면 0」 둘 다) |
| M4 | 승인 목록 밖 `/skip` 호출 추가 | typecheck **2**(`PathsWithMethod` 가 거부) · 게이트 test **1** |
| M5 | 세션 만료를 `expiresAt < now` 로 | vitest **1** |
| M6 | 값 칸을 둘 싣는다 | vitest **3**(표 test 둘 + 화면 test 하나) |
| M7 | `seenRevision` 을 0 으로 고정 | vitest **1** |
| M8 | 확인 화면의 「보낸 값」에 세션 응답의 다른 칸을 꽂음 | vitest **1** |
| M9 | 탭 필터에 특례 하나(`BID_PLANNED → 'recorded'`) | vitest **1**(상태 14 전수 test 의 그 행) |
| M10 | 탭 필터를 문자열 접두 추론 셋으로 | vitest **1**(`BID_PLANNED` 행) |
| M11 | 409 사유 표의 키 한 자를 지움 | **typecheck 2**(계약 enum 으로 잠근 뒤) |
| M12 | 탭 포커스 의도를 **비우지 않고** 매 렌더 적용(승인 전 일괄이 고친 꼴) | vitest **1** — 「옮긴 뒤 다른 곳으로 간 포커스가 재렌더 뒤에도 그대로」 |
| M13 | 어휘 중복 접기를 걷음 | vitest **1**(붉은 test 2 — 표와 프로필 쓰기 본문) |

**동치 변이 하나**: `startsWith('OPENED') ? 'under-review'` 만 바꾸면 **exit 0** 이다. 내 표가
`OPENED_*` 둘을 이미 그 탭으로 보내므로 **어느 상태에서도 거동이 같다**(기계로 대조해 차집합이
빈 것을 확인했다). 거동이 같은 변이는 어느 test 도 가를 수 없다 — test 공백이 아니다.

**통제군**: 되돌린 뒤 `npx vitest run` **exit 0**(그 시점 305 · 지금 394 passed) — 변이의 RED 가 변이에서
온 것이고 흔들림이 아니라는 계수.

## 게이트 술어 변경과 양성 대조 (`OPEN-7E-GATE-RESIDUAL`)

| 잔여 | 양성 대조(재현 꼴) | 구판 | 신판 |
| --- | --- | --- | --- |
| ① | `./client.js` 네임스페이스 import + `: object` 주석 변수 export | **exit 0**(그것이 적힌 구멍이다) | **exit 1** — 지적 둘(네임스페이스 · 허용 종류 아님 `VariableDeclaration`)이 각각 |
| ② | `const publicDir = 'static'` + 단축 속성 `{ publicDir }` | 계수 그대로 통과 | **exit 1**(해석값이 그 절대 경로로 나온다) |
| ③ | `publicDir: 'static'` + **그 디렉터리 없음** | exit 0 | **exit 1** |
| ② 둘째 층 | 실제 `dist` 루트에 파일 하나 | 자산 단언이 보지 못함 | 하네스 **exit 1**(이름을 적는다) · 평소 모양 `assets, index.html` 은 통과(통제군) |
| ④ 쓰기 | `--rows: "[{…}]"` 를 `src/index.css` 에 | 어느 층도 보지 않음 | **exit 1**(걸린 문자를 적는다) |
| ④ 읽기 | 화면에서 `getComputedStyle(…).getPropertyValue('--rows')` | 어느 층도 보지 않음 | **exit 1** — 두 이름이 각각 |
| ④ 음성 대조 | 같은 이름의 **내 함수**(문자열로는 3 건) | — | **exit 0** — 이름이 아니라 **해소된 심볼**이 판정이다 |
| ⑤ | 전이 응답의 `caseVersion` 증가 | — | 여정 ④ 가 실측. 계약 전체 단언은 아니므로 잔여를 제한 7 로 재등재 |

술어 단위 test 는 등록부(`web/config/gate-tests.txt`)에 등재했다 — `dist` 루트 술어 넷을
`bundle-shape.test.mjs` 에 더해 그 파일이 10 passed 다.

## 여정 e2e

| 실행 | 명령 | 결과 |
| --- | --- | --- |
| 전건 | `npm run test:e2e:journey` | **exit 0 · 8 passed**(34.7s) |
| 여정 ④ 단독 | `--grep '여정 ④'` | **exit 0 · 1 passed**(26.9s) |
| 설정 여정 단독 | `--grep '설정 여정'` | **exit 0 · 1 passed**(34.4s) |
| 강제 실패(음성 대조) | 변이 M1 위에서 여정 ④ | **exit 1** — 여정이 화면 값을 실제로 재고 있다는 계수 |
| 하네스 위생 | 실행 뒤 `docker ps` | 하네스 컨테이너 잔존 **0** |

여정이 잡은 결함 **넷** — 셋은 spec 쪽, **하나는 산출물 쪽**이다:
  · (spec) 전이 버튼을 화면 전체에서 집어 **남의 건**까지 걸렸다(strict mode violation — 건 1·2).
    남의 건을 밀 수 있던 자리다 → 클릭·입력·전송을 자기 카드 안으로 좁혔다.
  · (spec) 설정 행을 DOM 조상으로 찾아(`xpath=ancestor::div[1]`) 120s timeout → 행에 자기 testId.
  · (spec) 제안 카드의 제목이 검색 카드와 **다른 컴포넌트**라 그 testId 가 없었다 → 링크 role 로 대조.
  · **(산출물)** 확인 화면의 「보낸 값」이 **비워 보낸 경우 빈 칸**이었다 — 「아직 안 보냄」과 같은
    얼굴이라 무엇을 적용하려는지 말하지 못했다. 「비움 — 이 조건을 지웁니다」로 고치고 단위 test 로
    잠갔다. 단위 test 가 먼저 못 잡은 이유: 그 test 는 빈 어휘가 **요청 본문**으로 가는 것까지만
    봤고 확인 화면의 **문면**은 단언 밖이었다.

## acceptance 전건 (ci.yml `web` job 명령 그대로 + Kotlin `check` + `qualityBaseline`)

| step | 명령 | exit |
| --- | --- | --- |
| W-1 | `npm ci` | 0 |
| W-2 | `npm run lint` | 0 |
| W-3 | `npm run typecheck` | 0 |
| W-4 | `npm run gate:src-tree` | 0 |
| W-5 | `npm run api:types:check` | 0 — 생성 타입이 OpenAPI 와 바이트 등식(이 slice 는 OpenAPI 를 읽기만 했다) |
| W-6 | `npm run test` | 0 — **404 passed(20 파일)** |
| W-7 | `npm run gate:tests` | 0 — 등록부 전부 수집·통과, skipped/todo 0 |
| W-8 | `npm run build` | 0 — 자산 `{js 1 · css 1}` · `dist` 루트 `{assets, index.html}` |
| W-9 | `npm run gate:boundary-canary` | 0 |
| W-10 | `npx playwright install chromium` | 0(설치본 재사용) |
| W-11 | `npm run test:e2e` | 0 |
| W-12 | `./gradlew --no-daemon :app:bootJar` | 0 |
| W-13 | `npm run test:e2e:journey` | 0 — **8 passed**(34.7s) |
| check | `./gradlew --no-daemon check qualityBaseline` | **0** — evidence 최종 HEAD 에서 재실측: `BUILD SUCCESSFUL in 20s` · 392 task(35 실행 · 357 up-to-date) · `:leakPatternGate` 가 그 목록에 **있다**. 첫 전건은 `BUILD SUCCESSFUL in 30m 31s`(401 task · 226 실행 · 136 from cache)였다 |

**20s·357 up-to-date 가 「안 돌았다」가 아님을 따로 쟀다**(「초록인데 조용하다」 규율). 이 slice 가
r1 에서 고친 것은 `web/**` 과 evidence 뿐이므로 Kotlin task 가 up-to-date 인 것은 맞지만, **입력이
실제로 바뀐 게이트**는 `:leakPatternGate`(evidence 를 스캔한다)다. 그 task 가 이 실행의 task 목록에
**UP-TO-DATE 표시 없이** 있고, 한 번 더 `--rerun-tasks` 로 강제해도 **exit 0** 이다. 즉 초록은
「바뀐 것을 재고 나온 초록」이다.

**첫 전건의 136 FROM-CACHE 도 기대값이다** — 이 slice 는 **Kotlin 입력을 하나도 바꾸지 않았다**.
근거는 변경 집합 자체다: `git diff --name-only fa5ca096..HEAD` 에서 `web/` 과
`reports/evidence/` 를 뺀 경로가 **0 건**이고, `.kt`·`.kts`·`gradle*`·`build-logic/`·
`config/quality/` 경로 변경도 **0 건**이다. 그래서 캐시 적중은 「측정이 비었다」가 아니라 「바꾸지
않은 것이 다시 돌지 않았다」다. 실행된 226 은 캐시가 없던 자리(이 worktree 의 첫 전건)다.

**실행 시간은 7E-1 실측(약 12m44s)의 두 배가 넘는다** — 같은 호스트에서 다른 레인(M7/7C④-c)이
Gradle 전건을 함께 돌렸다. 멈춘 것이 아니라 느린 것이고, 그 판정은 **로그 끝줄이 아니라**
worker 의 CPU 증가와 test executor 재생성으로 확인했다(로그는 task 출력을 버퍼링해 조용하다).

**`ci.yml`·`tools/one-command-check.sh` 를 고치지 않았다** — 여정 ④ 와 설정 여정이 **이미 있는
`npm run test:e2e:journey` 안에서** 돌기 때문이다(그 명령이 `playwright.journey.config.ts` 의
`testDir` 전부를 수집한다). 그래서 CI 와 one-command 의 축어 일치가 그대로 유지된다.

## 개인정보 스캔

e2e 산출물과 번들에 참조형 스캔을 돌렸다: `grep -rniE -f config/quality/leak-patterns.txt`
(어휘를 이 문서에 축어로 적지 않는다).

| 대상 | 일치 | 읽기 |
| --- | --- | --- |
| e2e 산출물(`web/test-results`) — **강제 실패 뒤**(`CI=1`) | **0** | 통과한 실행은 산출물이 0 건이라 그 상태의 「일치 0」은 공허하다 — 그래서 단언 하나를 깨 **실제 실패 산출물**을 내고 쟀다. 남은 것은 `.last-run.json` · `error-context.md` · `test-failed-1.png` 이고 **`trace.zip` 0 건**이다(D-7E-6: CI 는 trace 를 남기지 않는다) |
| 번들(`web/dist`) | **4** — 전부 **패턴 파일 넷째 줄의 어휘 하나**(축어로 적지 않는다) | **값이 아니라 속성 이름**이다: 로그인 입력의 `type` 속성. 비밀값이 아니고, 그것이 번들에 있는 것이 그 입력이 평문으로 그려지지 않는 이유다 |

강제 실패 산출물에서 **비밀 문구 칸의 이름**이 2 건 나오는데 둘 다 **spec 의 소스 줄**이다
(실패 지점 앞뒤를 Playwright 가 인용한다 — 그 줄 자체가 「비밀 문구가 화면에 없다」를 단언하는
코드다). **값이 아니라 식별자**이고, 그 실행의 비밀값을 담은 하네스 맥락 파일은 산출물 디렉터리
**밖**(tmpdir)에 있으며 하네스가 종료 때 지운다(실측: 실행 뒤 그 파일 없음).

여정 둘이 화면·콘솔에서 3-2-5 꼴과 로그인 비밀 문구 부재를 **각각** 단언하고, 사업자번호는
**칸 자체가 없다**(D-7E2-7).
