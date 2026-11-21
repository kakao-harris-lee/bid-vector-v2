# M15 / 15c — 사업자번호 선택 입력 — slice 계약

```yaml
milestone: M15
slice: 15c
base_sha: 99f2fb25989078d7ab1640de0dc8e399f5d1688d   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  # ── 마이그레이션 (V30 — 번호는 계약이 배정했다) ─────────────────────────
  - adapters/src/main/resources/db/migration/V30__business_registration_number.sql   # 신설
  # ── 도메인 값 (workflow) ────────────────────────────────────────────────
  - workflow/src/main/kotlin/bidvector/workflow/profile/BusinessProfileValues.kt   # 값 타입 + BusinessDetails 칸
  - workflow/src/main/kotlin/bidvector/workflow/profile/ProfileInputPolicy.kt      # 입력 길이 상한 한 자리
  - workflow/src/main/kotlin/bidvector/workflow/profile/OnboardingChecklist.kt     # KDoc — 「슬롯 금지」가 거짓이 된다
  - workflow/src/test/kotlin/bidvector/workflow/profile/BusinessProfileValuesTest.kt
  - workflow/src/test/kotlin/bidvector/workflow/profile/OnboardingChecklistTest.kt  # 비워 둬도 완료를 막지 않는다
  # ── 어댑터 (adapters) ───────────────────────────────────────────────────
  - adapters/src/main/kotlin/bidvector/adapters/profile/BusinessProfileSql.kt
  - adapters/src/main/kotlin/bidvector/adapters/profile/JdbcBusinessProfileStore.kt
  - adapters/src/test/kotlin/bidvector/adapters/persistence/BusinessProfileColumnSpecs.kt
  - adapters/src/test/kotlin/bidvector/adapters/profile/BusinessProfileSchemaGuardTest.kt
  - adapters/src/test/kotlin/bidvector/adapters/profile/JdbcBusinessProfileStoreTest.kt
  # ── HTTP (app) ──────────────────────────────────────────────────────────
  - app/src/main/kotlin/bidvector/app/http/ProfileRequests.kt
  - app/src/main/kotlin/bidvector/app/http/ProfileResponses.kt
  - app/src/test/kotlin/bidvector/app/http/ProfileRequestsTest.kt
  - app/src/test/kotlin/bidvector/app/http/ProfileResponseTest.kt
  - app/src/test/kotlin/bidvector/app/http/ProfileEndpointTest.kt
  - app/src/test/kotlin/bidvector/app/http/OpenApiProfileContractTest.kt   # 있으면 칸 집합 등식 갱신
  # ── 계약과 생성 타입 ────────────────────────────────────────────────────
  - openapi/bidvector-operator-api.yaml    # BusinessDetailsRequest · Profile · 두 머리말
  - web/src/api/generated/operator-api.ts  # 재생성(바이트 등식 W-5)
  # ── 화면 (web) ──────────────────────────────────────────────────────────
  - web/src/display/profileDisplay.ts
  - web/src/display/profileDisplay.test.ts
  - web/src/screens/ProfileDetailsCard.tsx     # 선택 입력 칸 하나(testId 하나)
  - web/src/screens/SettingsScreen.tsx         # 「준비 중」 목록에서 사업자번호 행을 걷는다
  - web/src/screens/SettingsScreen.test.tsx
  - web/src/screens/OnboardingScreen.tsx       # 「사업자번호」 절 — 입력함 / 비워 둠(선택)
  - web/src/screens/OnboardingScreen.test.tsx
  - web/src/screens/ProfileSection.test.tsx    # 공유 고정물이 새 칸을 지난다
  - web/src/api/approvedOperations.ts          # 주석만 — operation 집합은 **불변**이다
  # ── 누출 게이트 (e2e) ───────────────────────────────────────────────────
  - web/e2e/journey/privateValues.ts           # 신설 — 술어 한 자리 + 예외 testId 한 자리
  - web/e2e/journey/journey.spec.ts
  - web/e2e/journey/onboarding.spec.ts
  - web/e2e/journey/settings.spec.ts
  - reports/evidence/m15/15c/
out_of_scope:
  - adapters/src/main/resources/db/migration/V28__* · V29__*   # 15A · 15B 가 쥔 번호
  - operator_strategy · operator_profile · edit_session 의 계정 단위 전환   # 15A
  - POST /api/accounts · OPEN_ROUTES · 가입 화면                            # 15B
  - 체크섬(검증번호) 검증 · 국세청 진위 확인 API                            # 계약 「범위 밖」
  - 체크리스트 항목 신설                                        # 「비워 두어도 완료를 막지 않는다」
  - 알림함 본문 · Telegram 문면 · 로그 — 사업자번호를 **싣지 않는다**(새 자리 0)
  - web/e2e/capture/usecase-captures.mjs       # 예외를 주지 않는다(아래 「누출 게이트」)
  - .github/workflows/ci.yml                   # acceptance 는 오늘의 job 명령 그대로
acceptance_commands:
  # ci.yml `check` job — Kotlin 전건
  - ./gradlew --no-daemon check
  - ./gradlew --no-daemon qualityBaseline
  # ci.yml `web` job 전건 W-1~W-13 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
rollback: rollback.md
```

## 이 slice 가 더하는 것

`account_business_profile` 에 **선택** 열 `business_registration_number`(NULL 허용 · 숫자
10자리 CHECK)를 두고, 프로필 쓰기 요청의 선택 칸과 읽기 응답의 선택 칸으로 잇는다. 설정의
사업 정보 카드에 입력칸 하나가 서고, 시작하기의 「사업자번호」 절은 **입력함 / 비워
둠(선택)** 을 말한다.

운영자 지시 「사업자번호 필수 사항도 옵션으로」의 읽기는 계약이 정했다 — 지금 이 값은
**어디에도 저장되지 않으므로**(V22 에 열이 없다) 「필수를 선택으로 낮추는 일」이 아니라
**선택 입력 칸을 새로 두는 일**이다.

## 받는 꼴 — 둘로 **열거**하고 10자리로 정규화한다

| 입력 | 저장·응답 |
| --- | --- |
| `1234567890` | `1234567890` |
| `123-45-67890` | `1234567890` |
| 그 밖(공백 포함 · 하이픈 자리가 다름 · 자릿수 부족·초과 · 숫자 아님) | **400** |

**체크섬(검증번호)을 걸지 않는다** — 계약의 완화 지시다. 국세청 진위 확인도 범위 밖이다.

꼴을 **둘로 못박는** 이유: 「하이픈을 지우고 남은 숫자가 10 이면 받는다」로 열면
`1-2-3-4-5-6-7-8-9-0` 과 `--1234567890--` 이 같은 값이 된다. 지우는 규칙은 끝이 없고,
그 관용이 곧 「운영자가 적은 것과 저장된 것이 다르다」가 된다. 받는 꼴이 둘이면
정규화가 한 번이고 **되돌릴 수 있다**.

정규화 자리는 **값 타입 하나**다(`BusinessRegistrationNumber`). HTTP 판독·어댑터 읽기·화면이
각자 지우면 이중 정규화가 되고, 그 가운데 하나만 바뀌어도 아무것도 붉지 않는다. 화면은
**표시 서식**(3-2-5)만 가지며 그것은 정규화가 아니다 — 금액의 `toLocaleString` 과 같은 축이다.

## 누출 게이트 — 예외는 testId 하나, 그 밖의 0 은 유지한다

기존 e2e 셋(`journey` · `onboarding` · `settings`)이 「사업자번호 꼴(3-2-5)이 화면에 0」을
잰다. 그 게이트가 막는 것은 **남의 업체 번호**이고, 자기 계정 설정 칸의 자기 값은 예외다.

**예외를 주기 전에 게이트를 먼저 넓힌다.** 착수 실측: 셋의 술어는 `body.innerText()` 뿐인데
그것은 **입력칸의 `value` 를 담지 않는다**. 그 상태로 입력칸을 더하면 게이트는 「예외를 주지
않아도 조용」하다 — 방어 부재와 미측정이 같은 값이 된다. 그래서 술어를 **보이는 글 + 폼
값·속성**으로 넓히고(캡처 하네스가 이미 쓰는 꼴), 넓힌 집합에서 **testId 하나**만 뺀다.

- 술어와 예외 testId 는 **각각 한 자리에만 선언**한다(`web/e2e/journey/privateValues.ts`).
  부르는 자리가 예외를 고르지 않으므로 「어느 spec 에서만 열려 있다」가 생기지 않는다.
- **양성 대조**: 같은 값을 **다른 칸**(상호 입력)에 두면 같은 술어가 문제를 낸다. 예외가
  화면 전체를 열지 않는다는 것을 그 한 쌍이 잰다(더하기만 한 변이가 아니라 **자리를
  바꾼** 변이다).
- 캡처 하네스(`usecase-captures.mjs`)에는 **예외를 주지 않는다** — 그 산출물은 PNG 로 남아
  저장소 밖으로 나간다. 캡처 경로에서는 0 이 그대로 0 이다.
- 로그 · 알림함 본문 · Telegram 문면에 **싣는 자리를 만들지 않는다**. 값 타입의
  `toString()` 은 가려지고(상호와 같은 규율), 저장 거부는 SQLSTATE 다섯 글자만 나른다.

## 착수 실측 (base `99f2fb25`)

수치와 그것을 낸 명령은 `commands.md` 가 정본이다.

| 축 | 잰 것 | 결과 |
| --- | --- | --- |
| 오늘 저장되는가 | V22 의 열 · `BusinessProfileColumnSpecs` | **아니다** — 열이 없고 그 부재를 열 전수 test 가 잠근다 |
| 마이그레이션 번호 | `db/migration` 의 최대 | main 최대 V27 · 계약이 15C 에 **V30** 배정 |
| 앱 역할 권한 | V22 의 GRANT | 표 단위 `INSERT`, **열 단위 `UPDATE`** — 새 열은 UPDATE 를 따로 줘야 한다 |
| 누출 게이트의 술어 | e2e 셋 | `body.innerText()` 뿐 — **입력칸 `value` 를 담지 않는다**(위 절) |
| 그 꼴의 정규식 | e2e 셋 + 캡처 하네스 | 3-2-5 하이픈 꼴 — 숫자 10자리 연속은 **안 잡는다**(금액이 그 꼴이다) |
| 입력 판독 수단 | `RequestValues.kt` | `declaredText(key, max)` + `requestValue(key){…}` 가 이미 있다 — 새 판독 함수 0 |
| 화면의 공유 | `ProfileDetailsCard` | 설정과 **시작하기 2 절**이 같은 카드를 쓴다 — 입력칸은 한 번 더하고 두 화면에 선다 |
| operation 집합 | `approvedOperations.ts` | **불변** — 기존 `POST /api/profile/business-details` 가 칸 하나를 더 받는다 |

## 알려진 제한 (착수 시점)

1. **체크섬을 걸지 않는다** — 자릿수만 맞으면 받는다. 상용 전 재결정은
   `OPEN-M15-SIGNUP-HARDENING` 이 든다.
2. **진위 확인이 없다** — 저장된 값이 실제 사업자의 것인지 아무도 모른다. 시작하기의
   「입력함」은 **적었다**는 뜻일 뿐이다.
3. **표시 서식은 화면이 만든다** — 저장·wire 는 10자리이고 3-2-5 는 화면의 서식이다. 두
   자리가 갈리면 화면 test 가 붉어지는 것이 유일한 경보다(상호의 어휘 비대칭과 같은 꼴).
4. **입력칸이 두 화면에 선다**(위 실측) — 계약의 「설정의 사업자 정보 카드」는 카드 하나를
   가리키고, 그 카드가 시작하기 2 절에도 서므로 입력칸도 거기 선다. 1 절은 상태만 말한다.
5. **누출 게이트의 정규식은 하이픈 꼴만 본다** — 숫자 10자리 연속을 금지하면 금액·공고번호가
   전부 걸린다. 화면이 그 값을 보일 때 하이픈 꼴로 보이는 것이 이 게이트가 서는 전제다.

## 승인 경계

- **마이그레이션은 V30 하나**다. 15A·15B 의 표·열에 닿지 않는다.
- 새 operation · 새 외부 호출 · 새 의존성 **0**.
- 기존 게이트의 술어를 **넓히되 좁히지 않는다**(누출 게이트 — 예외 testId 하나).
- 운영 DB write · push · merge 는 하지 않는다. 재작업 상한 5 에 닿으면 보고한다.

## base 확인

`git rev-parse origin/main` → `99f2fb25989078d7ab1640de0dc8e399f5d1688d` ·
`git merge-base --is-ancestor 99f2fb25 HEAD` 를 착수 커밋에서 기계로 확인했다(위 yaml 의
40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에
줄이 서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에
있다. 귀속은 라운드마다 기계로 재산출해 확인한다.

## 갱신 이력

- 2026-11-21 초판(착수).
