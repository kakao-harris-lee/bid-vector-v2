# M7/7E-2b 실행 명령과 종료 코드

base `1e387499` · 브랜치 `m7-7e2b/2026-10-30`. 명령은 `web/` 에서 돌렸고(Kotlin 절만 저장소
뿌리), **종료 코드로** 판정한다. 출력은 핵심 한 줄만 적는다.

## Phase 1 착수 실측 — 하네스로 **실제 응답**을 봤다 (구현 전)

`node e2e/harness/journey-up.mjs`(출하 app.jar · 버릴 Postgres · fake KONEPS · vite preview)를
띄우고 preview 를 지나 호출했다. 응답 모양을 추측하지 않았다.

| 물음 | 자리 | 결과 |
| --- | --- | --- |
| 빈 계정의 세 목록 | `GET /api/onboarding-checklist` | 완료 **0** · 미완 **일곱** · 선행 대기 **한 짝**(시평액 ← 나라장터 준비). 진행률 분모는 일곱 |
| Kotlin test 의 빈 계정과 같은가 | `OnboardingChecklistEndpointTest` 와 대조 | **다르다** — 그쪽은 test 전략 fixture 가 관심 업종을 이미 가져 완료 하나다. 화면의 출발은 하네스 쪽이다 |
| 채우면 어떻게 움직이는가 | 쓰기 다섯을 차례로 | 사업 정보 → 완료 셋 · 선행 대기가 **미완으로** 이동 / 자격 사실 → 완료 둘 / 시평액 → 완료 하나 / 관심 업종 → 완료 / 예산 하한 → 완료. 끝에 **완료 여덟 · 미완 0 · 선행 대기 0** |
| 되돌릴 수 있는가 — 여섯 | 같은 operation 으로 | 상호·소재지·나라장터 준비(`UNANSWERED`) · 업종(빈 배열) · 시평액(`/remove`) · 관심 업종(빈 어휘) 전부 **200** 이고 항목이 미완으로 돌아온다 |
| 되돌릴 수 있는가 — **금액대** | 편집 값 세 꼴 | 칸 생략 · `null` · 빈 어휘 **모두 400**(`INVALID_REQUEST`). 계약에 비우는 길이 없다 → 후속 `OPEN-7E2B-BUDGET-CLEAR` |
| 되돌릴 수 있는가 — **면허** | 자격 사실을 전부 비움 | **200 인데 완료로 남는다** — V12 행을 지우는 operation 이 없다 |
| 그 둘이 남의 여정을 깨는가 | `GET /api/suggestions` · `POST /api/bid-price-requests` | **금액대는 깬다**: 어휘가 전부 빈 채 하한만 서면 `X-Result-State` 가 `WATCH_NOT_CONFIGURED` → **`NO_MATCH`**. **면허는 무해하다**: 신고·미신고 양쪽에서 `licenseVerdict=UNCERTAIN` · `licenseValidity=NOT_VERIFIED` · 미판정 축 셋 그대로 |
| 승인 목록 증분 | base OpenAPI ↔ 화면이 부르는 집합 | **하나**(`GET /api/onboarding-checklist`). 마법사의 쓰기는 7E-2 가 이미 승인한 operation 을 그대로 쓴다 |
| 항목 → 폼이 1:1 인가 | 커널의 완료 조건 ↔ 7E-2 폼 | **아니다** — 항목 여덟이 쓰기 단위 **다섯**에 묶인다(사업 정보 셋 · 자격 사실 둘 · 시평액 · 금액대 · 감시 조건) → D-7E2b-7 |

## 리팩터 커밋의 거동 보존 (D-7E2b-8)

분리 커밋 하나의 트리에서 쟀다 — 화면 A 를 붙이기 **전**이다.

| 명령 | exit | 핵심 결과 |
| --- | --- | --- |
| `npm run test` | 0 | 406 passed(분리 전과 같은 수) — `ProfileSection.test.tsx`·`SettingsScreen.test.tsx` 포함 |
| `npm run typecheck` · `npm run lint` | 0 · 0 | |
| `npm run gate:tests` · `npm run gate:src-tree` | 0 · 0 | |
| `npm run test:e2e:journey` | 0 | **8 passed** — 설정 여정 셋이 그대로 초록 |

**옮기며 드러난 결함 하나**: react-query 가 `mutationFn` 을 `(variables, context)` 로 부르므로
계약 함수를 그대로 넘기면 둘째 인자가 요청에 실린다(기존 코드는 클로저라 가려져 있었다).
`ProfileSection.test.tsx` 의 「지우기는 면허명으로 보낸다」가 **exit 1** 로 잡았고 본문 하나만
넘기도록 고쳤다 — 분리가 아니었으면 드러나지 않았을 자리다.

## RED — 구현 전에 붉은 것을 확인한 자리

| 자리 | 명령 | RED |
| --- | --- | --- |
| 체크리스트 표시 술어 | `npm run test` | **2 파일 수집 실패**(모듈 부재) — `onboardingDisplay.test.ts` · `OnboardingScreen.test.tsx`. 나머지 406 은 그대로 초록 |
| 화면 자리 표의 경계 | 같은 명령 | 구현 뒤 `routes.test.ts` 의 「실화면은 넷」이 **붉었고**(A 가 다섯째다) 그 단언을 갱신했다 — 의도한 RED 앵커 |
| 어휘 순서 게이트 | `npx vitest run scripts/onboarding-vocabulary.test.mjs` | 모듈 부재로 수집 0 → 구현 뒤 7 passed |

## 변이 — **바꿔치우기**(버릴 clone, `git clone --shared` + `npm` 설치본)

변이마다 원문이 **정확히 한 번** 있는지 먼저 세고 바꾼다(심지 않은 변이의 초록은 방어의 증거가
아니라 미측정이다). 통제군을 **앞뒤로** 돌려 clone 자체가 초록인 것을 확인했다.

| # | 변이 | 결과 |
| --- | --- | --- |
| 통제(전) | 없음 | 23 파일 **444 passed** |
| M1 | 해당 없음을 미완으로 접는다 | **2 파일 실패**(표 test · 화면 test) |
| M2 | 선행 대기를 진행률 분모에 넣는다 | **2 파일 실패** |
| M3 | 쓰기 뒤 체크리스트를 다시 읽지 않는다 | **화면 test 2건 실패** |
| M4 | 항목 선언 순서를 바꾼다 | **어휘 순서 게이트만 실패**(표 test 20 은 초록) — 그 축이 그 게이트에만 있다는 실측 |
| M5 | 선행 대기를 「다음 단계」로 연다 | **표 test 2건 실패** |
| M6 | 프로필 값으로 완료를 추론한다(우회 1) | **화면 test 1건 실패** |
| 통제(후) | 되돌린 clone | 23 파일 **444 passed** |

### r1 — 보강한 probe 가 붉는가 (같은 방식, 승인 전 일괄)

verifier·CR 이 「지금 코드에 결함은 없고 **test 가 비어 있다**」고 지목한 자리다. 그러므로 probe 는
원문에서 **초록**이어야 하고, 그 자리를 여는 변이에서 **붉어야** 한다 — 둘 다 쟀다.

| # | 변이 | 결과 |
| --- | --- | --- |
| 통제(전) | 없음 | 23 파일 **453 passed** |
| S1 | 전략의 관심 업종이 비면 `WATCH_CONDITIONS` 를 미완으로 그린다 | 배지 전수 test RED |
| S2 | 프로필이 나라장터 미응답이면 `NARA_READINESS` 를 미완으로 그린다 | 같은 test RED |
| B1 | 읽지 못했어도 배너를 띄운다 | 배너 probe **2건** RED |
| B2 | 미완이 0 이어도 띄운다 | 배너 probe **4건** RED — 이 레인의 변이(`pending < 0`)는 **첫 렌더부터** 띄우므로 양성 쪽(「미완 수 표시」)까지 함께 붉는다. verifier 가 건 변이는 **데이터가 온 뒤에만** 띄워 2건이 붉었다(같은 축, 다른 변이)
| B3 | 배너가 선행 대기까지 센다 | 「선행 대기만 남으면 뜨지 않는다」 RED |
| P1 | 저장해도 고른 단계를 놓지 않는다 | 단계 전진 test RED |
| P2 | 프로필 401 을 말 없이 비운다 | 401 test RED |
| P3 | 편집 세션이 열려도 단계를 옮긴다 | 단계 잠금 test RED |
| P4 | 진행률이 live region 이 아니다 | 보조기술 test RED |
| 통제(후) | 되돌린 clone | 23 파일 **453 passed** |

**첫 라운드에서 B3 가 초록으로 지나갔고, 그 사유는 거동이 아니라 단언의 시점이었다.**
`waitFor(() => expect(queryByTestId(…)).toBeNull())` 은 응답이 닿기 **전** 첫 렌더에서 이미 참이라
거기서 끝난다 — 세 음성 단언이 모두 그 꼴이었고, B1·B2 가 붉었던 것은 그 변이들이 **첫 렌더부터**
배너를 세웠기 때문이다(우연이다). 같은 질의를 쓰는 **양성 표지**를 먼저 기다린 뒤 음성 단언을 한 번만
세도록 고쳤고, 그 뒤 B1·B2 의 실패가 1006ms(타임아웃)에서 **8ms**(즉시)로 바뀌었다 — 기다림이 아니라
판정이 되었다는 계수다.

### r2 — 끝난 세션의 갇힘과 나머지 LOW

MEDIUM 은 **r2 의 단계 잠금이 들여온 회귀**다. 그래서 회귀 test 를 **두 HEAD 에서 같은 파일로**
돌렸다 — 고친 코드에만 있는 이름을 쓰지 않고 화면 밖에서 보이는 것만 센다.

| 자리 | r2 `afec83fb` | 고친 뒤 |
| --- | --- | --- |
| **probe 파일**(커밋 밖, 세 건: `EXPIRED` 잠금 해제 · 패널 탈출 버튼 ≥1 · `CANCELLED`) | **3 failed** | **3 passed** |

**이 「3」은 probe 파일의 수이고 커밋된 회귀 test 와 1:1 이 아니다**(CR r3 B-1). 그때 커밋한 셋
가운데 **셋째**(「재조회가 실패하면 …」)는 버튼이 **있는지만** 세고 누르지 않아 `afec83fb` 에서도
초록이었다 — 그 수정을 가르지 못하는 특성화 test 였다. r3 에서 그 자리를 **거동을 누르는 test**
둘로 바꿨고(아래 표), 변이 표의 MR5·MR6 이 말하는 「회귀 test 2건」이 당시 실제로 가른 수다.

그 뒤 같은 방식의 변이 일곱(지금 코드에서 각각 RED · 통제군 앞뒤 **464 passed**):

| # | 변이 | 결과 |
| --- | --- | --- |
| MR1 | 언마운트 잠금 해제를 지운다 | 전략 401 test RED |
| MR2 | 전략 401 을 말 없이 비운다 | 같은 test RED |
| MR3 | 잠긴 단계의 사유를 보조기술에서 뗀다 | 사유 문단 test RED |
| MR4 | 저장 뒤 늘 첫 미완으로 간다 | 걸음 test RED |
| MR5 | 끝난 세션의 「닫기」를 뗀다 | 회귀 test 2건 RED |
| MR6 | 잠금 술어를 「열린 세션」으로 되돌린다 | 회귀 test 2건 RED |
| MR7 | 다음 걸음이 선행 대기 단계도 센다 | 표 test 2건 RED |

### r3 — 고침마다 두 HEAD (이 묶음의 검증 정본)

r3 뒤에는 전체 재리뷰를 돌리지 않으므로 **각 고침이 실제로 무언가를 바꿨는가**를 고침마다 잰다.
test 파일은 **지금 판 그대로** 두고 **구현만** 두 HEAD 사이에서 바꿔 돌렸다(verifier r3 와 같은 방식).

| 고침 | 그 자리를 세는 test | 구현 `70b5cb98` | 지금 |
| --- | --- | --- | --- |
| L-1 재조회 404 → 끝난 세션 | 「409 재조회가 404 면 자리를 치우고 잠금을 푼다」 | **RED** | 초록 |
| L-1 명령 404 → 끝난 세션 | 「그만두기가 404 면 그것으로 끝난 세션이다」 | **RED** | 초록 |
| L-2 `dismiss` 가 쓰기 상태도 비움 | 「닫으면 실패 얼굴과 409 사유도 함께 걷힌다」 | **RED** | 초록 |
| L-4 치운 뒤 다시 쓸 수 있는가 | 「닫은 뒤 고치기를 누르면 새 세션이 선다」 | **RED** | 초록 |
| L-9 `APPLIED` 를 닫을 때 재조회 | 「APPLIED 세션을 닫으면 체크리스트를 다시 읽는다」 | **RED** | 초록 |
| L-6 `aria-disabled` + 누름 무시 | 「편집 세션이 열려 있는 동안에는 단계를 옮길 수 없다」 | **RED** | 초록 |
| L-6 live 문단을 늘 그린다 | 「단계가 잠기면 그 사유가 문단으로 서고 …」 | **RED** | 초록 |
| (r2 L-1·L-2 재확인) | 「전략 질의가 401 이면 … 잠금을 풀어 준다」 | **RED** | 초록 |

세 파일 합으로 `70b5cb98` 에서 **8 failed / 74 passed**, 지금 **82 passed**.

**L-3 은 기준 HEAD 가 다르다** — 그 줄(`StrategySection` 의 `disabled={session.inProgress}`)은 **r2**
가 바꿨고 세는 자리가 없었다. 그래서 설정 test 를 **r2 이전 구현**과 **verifier 가 지목한 변이**
양쪽에 맞댔고 둘 다 **2 failed / 24 passed**(지금 26 passed)다.

**L-5·L-7·L-8 은 거동을 바꾸지 않는다**(두 HEAD 모두 초록) — ⓐ 소진 `switch` 는 **다음 state 추가를
컴파일에서 멈추는** 구조 변경이고 오늘 거동은 같다 ⓑ 표 test 이름을 함수의 말에 맞추고 「자기 단계만
미완이면 그 자리」를 더한 **특성화** test ⓒ production 호출부 0 인 함수와 그 항진 test 삭제.
세 자리는 「두 HEAD 를 가른다」로 적지 않는다.

## acceptance — `ci.yml` `web` job 의 명령 그대로 + Kotlin

| step | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| W-2 | `npm run lint` | 0 | |
| W-3 | `npm run typecheck` | 0 | |
| W-4 | `npm run gate:src-tree` | 0 | 컴파일 경계 전제 성립 |
| W-5 | `npm run api:types:check` | 0 | 생성 타입이 문서와 바이트까지 같다 |
| W-6 | `npm run test` | 0 | 23 파일 **472 passed**(r1 +9 · r2 +11 · r3 +8) |
| W-7 | `npm run gate:tests` | 0 | 등록부의 게이트 test 가 수집에 있고 전부 통과 |
| W-8 | `npm run build` | 0 | 자산 둘(js·css) |
| W-9 | `npm run gate:boundary-canary` | 0 | 메인·워커 두 그래프에서 경계가 활성 |
| W-11 | `npm run test:e2e` | 0 | 셸 스모크 2 passed |
| W-13 | `npm run test:e2e:journey` | 0 | **9 passed**(이 레인, 라운드마다 마지막 산출물 커밋에서). **순서 독립은 이 레인 혼자 잴 수 없다** — 기본 순서에서 ② 가 ① 보다 앞에 돌기 때문이다(`journey` < `onboarding`). 양방향은 **verifier r2 가 정순·역순 둘 다 돌려** 각각 9 passed 를 냈다. 그리고 `e2e/**` 는 `tsconfig` 의 `include` 밖이라 **실행이 그 코드의 유일한 검증**이다 — 판정 SHA 의 정본은 그 SHA 의 **CI `web` job**(W-12·W-13 포함)이고, `afec83fb` 에서 success(3m5s)였다. 이 라운드 판정 SHA 의 CI 는 **PR 조치 코멘트가 든다**(evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다) |
| check | `./gradlew --no-daemon check qualityBaseline` | 0 | Kotlin 전건 — `BUILD SUCCESSFUL in 28m 17s`(호스트가 여정·clone 과 겹쳐 CI 실측보다 길다). r1·r2 는 Kotlin 입력을 건드리지 않아 표적 `leakPatternGate` 로 잰다 |

W-1(`npm ci`)·W-10(브라우저 설치)·W-12(`:app:bootJar`)는 환경 준비 step 이고 이 레인에서 먼저
돌려 두었다(`npm ci` 0 · `:app:bootJar` 0).

**Kotlin 절이 선 자리**: 그 명령은 `39c5c01e` 의 트리 + 요구표 편집(지금의 `3354143d`)에서 돌았고,
그 뒤 산출물 커밋(`2acd2439`)이 바꾼 것은 **e2e spec 한 파일**이다 — `git diff --name-only
3354143d..2acd2439`. (r0 은 그 범위를 `39c5c01e..` 로 적었고 그것은 **인용 오류**였다, CR r1 LOW:
그 범위의 실제 출력은 요구표와 spec **둘**이다. 결론은 어느 범위에서도 같다.) Kotlin 소스 ·
OpenAPI · `config/quality/**` 와 교집합이 **0** 이라 그 사이에 이 job 의 입력이 움직이지 않았다.
**evidence 커밋 뒤의 전건 재실측은 verifier 가 든다**(아래 비밀값 절의 사유 — evidence 는 자기
마지막 커밋의 post-state 를 담을 수 없다).

**여정의 기다림을 한 자리 고쳤다**(r3, 산출물 결함 아님): 시평액 삭제 뒤 받은 체크리스트가
**바로 앞 재진입(홈 → 시작하기)의 응답**이었다 — 홈을 거치면 배너와 화면이 같은 질의를 각각 걸어
응답이 둘이고 늦게 닿는 쪽이 다음 기다림에 잡힌다. 기다림을 걸기 전에 **화면이 최신 응답을
그렸는지** 단언하도록 고쳤다(그 배지가 「완료」면 그 응답은 이미 닿았다).

## 새 public 표면 전수

목록은 명령으로 낸다(값을 여기에 박지 않는다):

```
git diff --name-status 1e387499..HEAD -- web
grep -ohE '^export (async )?(function|const|type|interface) [A-Za-z_]+' \
  web/src/display/onboardingDisplay.ts web/src/screens/OnboardingScreen.tsx \
  web/src/screens/profileWrites.tsx web/src/screens/Profile*Card.tsx \
  web/src/screens/strategyEdit.tsx web/scripts/onboarding-vocabulary.mjs
```

각 표면이 값을 지어낼 수 있는가는 `checklist.md` §0.5 가 든다.

## 비밀값 스캔

- 이 slice 가 바꾼 경로 전부 + evidence 에 대해 참조형으로:
  `grep -rniE -f config/quality/leak-patterns.txt $(git diff --name-only 1e387499..HEAD -- web docs/discovery/m7-screen-data-requirements.md) reports/evidence/m7/7e2b/`
  → **exit 1**(매치 없음 = 통과).
- 여정 ① 이 **화면 A 에서** 비밀 문구·사업자번호 꼴이 0 인 것을 잰다(D-7E-6). 그 축은 7E-2 가
  설정 화면에만 걸어 두었고, 화면 A 가 프로필 값을 그리는 새 자리라 같은 단언을 더했다.
  trace 설정은 바꾸지 않았다(CI 는 스크린샷만).
- **마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니다**: evidence 는 자기 마지막 커밋의
  post-state 를 담을 수 없다. 그 값은 verifier 와 PR 조치 코멘트가 든다.

## rollback 실측

`rollback.md` 가 ⓪~⑦ 을 든다. 한 줄 요약: 되돌린 `web` 하위 트리의 해시가 **base 와 같고**,
내 표지 열둘이 0 이며 남의 표지는 남았고, 그 트리에서 게이트 여섯과 셸 스모크가 전부 exit 0 이다.

## differential.json · golden-manifest.json

**N/A** — 이 slice 는 Python 과의 차이를 판정하지 않고(화면 코드다), fixture 를 더하거나 고치지
않았다(`web/e2e/harness/fixtures/` 무변경 — `git diff --name-only 1e387499..HEAD -- web/e2e/harness`
가 빈 출력).
