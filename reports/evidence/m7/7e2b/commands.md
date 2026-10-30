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

## acceptance — `ci.yml` `web` job 의 명령 그대로 + Kotlin

| step | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| W-2 | `npm run lint` | 0 | |
| W-3 | `npm run typecheck` | 0 | |
| W-4 | `npm run gate:src-tree` | 0 | 컴파일 경계 전제 성립 |
| W-5 | `npm run api:types:check` | 0 | 생성 타입이 문서와 바이트까지 같다 |
| W-6 | `npm run test` | 0 | 23 파일 **444 passed** |
| W-7 | `npm run gate:tests` | 0 | 등록부의 게이트 test 가 수집에 있고 전부 통과 |
| W-8 | `npm run build` | 0 | 자산 둘(js·css) |
| W-9 | `npm run gate:boundary-canary` | 0 | 메인·워커 두 그래프에서 경계가 활성 |
| W-11 | `npm run test:e2e` | 0 | 셸 스모크 2 passed |
| W-13 | `npm run test:e2e:journey` | 0 | **9 passed** — 여정 ① 이 들어왔고 그 **뒤에 도는 설정 여정 셋이 그대로 초록**이다(순서 독립 실측) |
| check | `./gradlew --no-daemon check qualityBaseline` | 0 | Kotlin 전건 — `BUILD SUCCESSFUL in 28m 17s`(호스트가 여정·clone 과 겹쳐 CI 실측보다 길다) |

W-1(`npm ci`)·W-10(브라우저 설치)·W-12(`:app:bootJar`)는 환경 준비 step 이고 이 레인에서 먼저
돌려 두었다(`npm ci` 0 · `:app:bootJar` 0).

**Kotlin 절이 선 자리**: 그 명령은 `39c5c01e` 의 트리 + 요구표 편집(지금의 `3354143d`)에서 돌았고,
그 뒤 산출물 커밋(`2acd2439`)이 바꾼 것은 **e2e spec 한 파일**뿐이다 — Kotlin 소스 · OpenAPI ·
`config/quality/**` 와 교집합이 0 이라(`git diff --name-only 39c5c01e..2acd2439`) 그 사이에 이
job 의 입력이 움직이지 않았다. **evidence 커밋 뒤의 전건 재실측은 verifier 가 든다**(아래 비밀값
절의 사유 — evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다).

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
