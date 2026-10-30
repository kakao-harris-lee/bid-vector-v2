# M7/7C④-3 실행 명령 — 시작하기 체크리스트 operation

base `fa5ca096` · 병합된 main 고정 SHA **`ea91d753`**(PR #105 7E-2 머지) · 브랜치 `m7-7c4c/2026-10-30`.
출력 전문을 싣지 않는다(핵심 결과 한 줄) — 감사자는 명령을 다시 돌린다.

**마지막 HEAD 의 전건 결과 정본은 verifier 와 PR 조치 코멘트다.** 이 표는 그 직전까지를 담는다.

## A. 술어 확장 — RED 선행과 대조 (D-7C4C-5·6)

| # | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| A-1 | `./gradlew --no-daemon :app:test --tests '…OpenApiContractTest'` (대조 선행 커밋) | 1 | 13 tests, 1 failed — `awaiting` 짝 배열 음성 대조가 RED |
| A-2 | 같은 명령 (술어 확장 단독 커밋 직후) | 1 | 13 tests, 1 failed — **D-6A1-44 위치 폐쇄 test** 가 RED(확장이 그 fixture 를 정당한 모양으로 만들었다) |
| A-3 | 같은 명령 (fixture 를 깊이 2 로 내린 뒤) | 0 | 13 tests, 0 failed |
| A-4 | `./gradlew --no-daemon :app:sizeGate` | 0 | 확장 직후 `OpenApiContractTest` 가 **정확히 500/500 줄**(한도 500) — 대조를 자기 class 로 가른 사유 |
| A-5 | `./gradlew --no-daemon :app:test --tests '…OpenApiContractTest' --tests '…OpenApiFlatnessControlTest'` | 0 | 11 + 2 = 13 — 가르기 전 수와 같다(단언 유실 0) |
| A-6 | 같은 명령 (r2 — 배열 래퍼 대조 선행 커밋) | 1 | 3 tests, 1 failed — 래퍼의 `nullable`·미지 키가 통과하던 자리가 RED |
| A-7 | 같은 명령 (r2 — 래퍼 키 허용 목록 단독 커밋 뒤) | 0 | 술어 대조 3 · 계약 11 · 체크리스트 계약 6, 전부 0 failed |

**A-2·A-3 의 수를 정정했다**(r1 CR LOW): 앞 판은 **14** 로 적었는데 그 수의 출처가 test 실행기가 아니라
결과 XML 의 줄 계수였다(`grep -c` 로 센 값이고 닫는 태그를 함께 셌다). 실행기 로그와 그 커밋들의 `@Test`
수는 **13** 이다(`fa5ca096` 11 → 대조 둘을 더해 13 → 가르기 뒤 11+2). 커밋 `61837348` 의 메시지에도 같은
14 가 있고 **이력은 되쓰지 않는다** — 정본은 이 줄이다. 가르기에서 단언이 유실되지 않았다는 주장은 수가
아니라 A-5 의 등식(11+2 = 13)과 본문 이동 diff 가 진다.

**확장으로 새로 통과하는 모양의 전수**(D-7C4C-2 조건): 스키마 39 · 속성 정의 241 · 옛 술어 거부 0 ·
**새로 통과 0 · 새로 거부 0**. base **`fa5ca096`** 와 병합된 main **`ea91d753`** 두 spec 에서 같은
값이고 그 사이 `87cf6a06` 도 같다(그 머지가 더한 어휘 항목은 스칼라 분기라 object 분기와 만나지 않는다).
**head 에서는** 스키마 41 · 속성 246 · 새로 통과 **1**(이 slice 의 `awaiting` 하나) · 새로 거부 0 —
r2 의 배열 래퍼 허용 목록을 넣은 뒤에도 같다. 재현은 `OpenApiContractTest` 의 전수 단언 하나가 진다 —
「계약의 모든 속성 정의가 평탄한 형태만 쓴다」가 그 0 을 CI 에서 다시 센다.

## B. 산출물 표적 실행

| # | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| B-1 | `./gradlew --no-daemon :workflow:test --tests '…ReadOnboardingChecklistUseCaseTest'` | 0 | 6 tests, 0 failed |
| B-2 | `./gradlew --no-daemon :app:test` 표적 7 class(체크리스트 둘 · 계약 · 술어 대조 · 보안 · 주체 운반 · 상수시간) | 0 | 45 tests, 0 failed |
| B-3 | `javap -p -c` 로 새 응답 둘의 `Intrinsics.areEqual` 계수 | 0 | 응답 3 · 짝 2 · 컨트롤러 0 · companion 0 — 허용 목록 등재 둘이 **죽은 항목이 아니다** |

## C. 변이 — 전부 바꿔치우기, 측정 뒤 되돌림 확인

각 행은 「변이 적용 → 표적 실행 → `git checkout --` 되돌림 → 트리 깨끗」을 한 번에 돈다. 산출물이
모두 커밋된 상태에서만 돌렸다(미커밋 작업을 되돌림이 지우지 않게).

**컴파일되지 않은 변이는 세지 않는다.** r2 의 M9 첫 판은 자격 없는 이름을 써서 `Unresolved reference`
로 떨어졌고 — 그것은 「거동 0」이 아니라 「빌드 0」이라 아무것도 재지 못한다 — 전체 한정 이름으로 고쳐
다시 걸었다. 표의 M9 는 **컴파일된** 판의 결과다.

| 변이 | 무엇을 바꿔치웠나 | 결과 | 잡은 test |
| --- | --- | --- | --- |
| M1 | 깊이 1 층 술어가 자기 대신 **전체 술어**를 부른다(깊이 N) | RED 1/2 | 술어 대조 — 양성 |
| M2 | object **키 허용 목록** 검사 제거 | RED 1/2 | 술어 대조 — 양성 |
| M3 | 빈 `properties` 허용 | RED 1/2 | 술어 대조 — 양성 |
| M4 | 달력 구역을 KST → UTC | RED 1/6 | use case — 「오늘은 KST 달력일이다」 |
| M5 | 세 상태를 **두 상태로 접는다**(`awaiting` → `pending`) | RED 1/5 | endpoint — 「세 목록이 wire 에서 갈라진다」 |
| M6 | `ROOTS` 에서 체크리스트 뿌리 제거(등재 망각) | RED 1/5 | 보안 계약 — 「자격 헤더 대안을 남긴다」 |
| M7 | 미완 목록을 **정렬**(선언 순서 파괴) | RED 2/5 | endpoint — 세 목록 · 전이 둘 |
| M8 (r2) | 배열 래퍼 검사를 **허용 목록 → 옛 차단 목록**으로 되돌린다 | RED 1/3 | 술어 대조 — 「배열 래퍼의 nullable 과 미지 키도 거부한다」 |
| M9 (r2) | use case 가 전략 읽기 실패를 **삼키고** 빈 규칙을 쓴다 | RED 1/6 | endpoint — 「저장된 전략이 무효면 500 이고 사유 코드가 실린다」 |

음성 대조(변이 없는 트리)는 B-2 가 같은 class 들에서 0 failed 다.

## D. acceptance — CI job 명령 그대로

| # | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| D-1 | `./gradlew --no-daemon check` (1 회차) | 1 | `:app:detekt` 2 issues — 새 술어 두 함수의 `ReturnCount`(한도 2) |
| D-2 | `./gradlew --no-daemon check` (2 회차, detekt 정정 뒤) | 1 | 957 tests, 2 failed + ktlint 1 — 표면 계약 미등재 둘과 식 본문 줄바꿈 |
| D-3 | `./gradlew --no-daemon :app:ktlintTestSourceSetCheck` | 0 | 정정 확인 |
| D-4 | `./gradlew --no-daemon check qualityBaseline` (**main `ea91d753` 병합 뒤**) | 0 | **BUILD SUCCESSFUL 27m 39s** · FAILED 0 |
| D-5 | `./gradlew --no-daemon qualityBaseline --rerun` | 0 | D-4 에서 UP-TO-DATE 였으므로 **실제 실행**으로 다시 측정 |
| D-6 | `./gradlew --no-daemon check qualityBaseline` (r2 1 회차) | 1 | `:workflow:ktlintMainSourceSetCheck` 하나 — **test 실패 0**. 구역 상수를 `private` 로 좁히자 파일이 class 하나만 담게 되어 filename 규칙이 걸렸다 |
| D-7 | 같은 명령 (파일명을 class 이름으로 고친 뒤) | 0 | **BUILD SUCCESSFUL 27m 31s** · FAILED 0 |
| D-8 | `./gradlew --no-daemon qualityBaseline leakPatternGate --rerun` | 0 | D-7 에서 UP-TO-DATE 였던 둘을 **실제 실행**으로 재측정 |

**D-1·D-2 가 이 slice 의 핵심 교훈이다**: 표적 실행 7 class 가 초록인 상태에서 전건이 셋을 잡았다
(detekt · 출하 조립 census · ktlint). 「부분 게이트는 안 돌린 것과 같다」가 두 라운드로 실증됐다.

## E. web job 전체 (OpenAPI·생성 타입이 바뀐다 — 계약의 acceptance)

병합된 트리에서 W-1~W-13 전부. 모두 exit 0.

| 단계 | 명령 | 핵심 결과 |
| --- | --- | --- |
| W-1 | `npm ci` | 잠금 파일 고정판 |
| W-2 | `npm run lint` | 0 |
| W-3 | `npm run typecheck` | 0 |
| W-4 | `npm run gate:src-tree` | 0 |
| W-5 | `npm run api:types:check` | **생성 타입이 문서와 바이트까지 같다 — 174122 bytes** |
| W-6 | `npm run test` | 20 files, 406 tests passed |
| W-7 | `npm run gate:tests` | 0 |
| W-8 | `npm run build` | 0 |
| W-9 | `npm run gate:boundary-canary` | 0 |
| W-10 | `npx playwright install chromium` | 0 |
| W-11 | `npm run test:e2e` | 2 passed |
| W-12 | `./gradlew --no-daemon :app:bootJar` | 0 |
| W-13 | `npm run test:e2e:journey` | **8 passed** |

생성 타입은 `npm run api:types` 재생성으로만 바뀌었다(손편집 0) — 병합 뒤 재생성이 **아무 바이트도
바꾸지 않았다**(7E-2 가 계약·생성 타입을 만지지 않았다).

**r2 에서 W-1~W-11 을 다시 돌렸다**(계약 문면이 바뀌어 생성 타입이 따라 바뀌었다): 전부 exit 0 ·
단위 406 · 스모크 2 · 바이트 등식 **174196**. W-12·W-13 은 r1 에서 측정한 값이 그대로 유효하다 —
그 뒤 바뀐 것은 계약 문면 한 줄과 test·게이트 목록이고 여정이 지나는 경로(수집 → 저장 → API → 화면)의
코드가 아니다.

## F. 비밀값 스캔

| # | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| F-1 | `command grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m7/7c4c/` | 1 | **매치 없음 = 통과** |
| F-2 | 같은 명령을 in_scope 산출물 경로에 | 0 | 매치 셋 — **전부 계약 문서의 보안 스킴 선언 줄이고 내 것이 아니다**: 같은 어휘의 계수가 PINNED(`ea91d753`)와 현재 트리에서 **둘 다 3** 이다(내가 더한 수 0). 그 파일을 마지막으로 그 줄로 만진 커밋은 7C②-b 의 병합이다 |
| F-3 | 육안 — 연락처·사업자 정보·운영자 실값 | — | 0. 이 slice 의 값은 전부 합성이고(항목 어휘는 닫힌 enum), 응답에 자유 문자열 칸이 없다 |

루트 `leakPatternGate` 의 스캔 뿌리는 `reports/evidence` 라 F-1 이 그 게이트와 같은 집합을 본다 —
F-2 의 셋은 게이트 대상 밖이고 D-4 의 전건이 exit 0 인 것과 모순되지 않는다.

## G. clean-tree 게이트

| # | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| G-1 | `git status --porcelain --` + in_scope 경로를 **개별 인자**로 | 0 | 빈 출력 |
| G-2 | 양성 대조 — in_scope 파일 하나에 한 줄을 심고 G-1 재실행 | 0 | 그 파일이 `M` 으로 잡힌다. 되돌림은 **비파괴 절삭**(`head -n <원래 줄수>`)이고 `checkout --` 을 쓰지 않았다 |

## I. 설계 검토 (2b) — 새 public 표면 전수 (수정 라운드마다 갱신)

| 표면 | 값 획득 | 경계 |
| --- | --- | --- |
| `GET /api/onboarding-checklist` | 세션 계정의 체크리스트 | 계정 주체 없는 인증은 401 · 요청에 계정 인자 자리 0(질의·본문 없음) · 7B 바인딩 가드가 요청 값으로 `AccountId` 를 짓는 길을 전역 차단 |
| `OnboardingChecklistResponse` · `AwaitingItemResponse` | 없음(커널 값에서만 채운다) | 자유 문자열 칸 0 — 닫힌 enum 이름만 싣는다 |
| `ReadOnboardingChecklistUseCase` | `AccountId` 인자 하나 | 호출자가 세션 계정만 넘긴다(컨트롤러의 접근자 하나) |
| `AccountScopedRoutes.ONBOARDING_CHECKLIST` | 없음(선언) | 등재를 잊으면 보안 계약 test 가 RED |
| `ProfileWiring.readOnboardingChecklistUseCase` 빈 | 주입 넷 | 조립 경계는 `ProfileAssemblyBoundaryTest` 가 집합으로 잰다 |

**r2 에서 하나 줄었다**: `ONBOARDING_CALENDAR_ZONE` 을 `private` 로 좁혔다(읽는 자리가 선언 파일 한 줄
뿐이었다 — r1 CR LOW). 술어 함수들은 test source set 의 `private`·`internal` 이라 운영 표면이 아니다.

## H. 알려진 제한

1. **`nullable` 과 미지 키는 스칼라 층에서 그대로 열려 있다.** 키 허용 목록은 깊이 1 object 와
   **그 object 를 나르는 배열 래퍼** 둘을 닫는다(r2 가 래퍼를 더했다 — r1 CR M-1 · v L-1). 스칼라 쪽은
   계약이 범위 밖에 두었으므로(D-7C4C-6) `nullable: true` 스칼라 속성과 `nullable` **스칼라 배열**은
   전과 같이 통과한다 — 의도된 비대칭이고 대조 test 가 그 두 줄을 직접 적는다. 두 자리를 함께 닫으면
   기존 계약의 nullable 스칼라가 전부 붉어진다.
   **같은 관용이 만드는 보장의 한계**(r1 v L-2): 깊이 1 object 의 속성이 `{type: string, properties: …}`
   처럼 스칼라라고 선언하면서 object 를 품으면 그 안쪽은 술어의 판정 밖이다. 「깊이 2 는 표현할 함수가
   없다」는 **타입이 정직한 스키마에서** 성립하고, 그 문면을 술어 KDoc 에서 그렇게 좁혔다. 이 관용은
   D-6A1-38 부터 있던 것이고 이 slice 가 연 자리가 아니다.
2. **깊이 1 object 는 속성 자리와 배열 아이템 자리에서 **같이** 열렸다**(D-7C4C-2 문면). 자리로 가르지
   않은 이유는 그것이 위치 술어이고 이 저장소가 D-6A1-44 에서 종점 없음을 세 번 겪었기 때문이다.
   오늘 계약에서 쓰는 것은 배열 아이템 한 자리뿐이다.
3. **진행률을 서버가 내지 않는다.** 세 목록만 낸다 — 분자·분모 판단이 생기면 그때 서버가 정한다
   (`awaiting` 을 세지 않는다는 D-7C4-2 가 그 판단의 입력이다).
4. **V12 자격 사실과 전략은 아직 싱글턴이다.** 체크리스트의 입력 넷 중 둘이 계정 소유가 아니므로,
   계정이 둘 이상인 환경에서 그 두 축은 모든 계정에 같은 값을 낸다. R-2 남은 절반이 여는 자리이고,
   use case 는 그 비대칭을 숨기지 않는다(port 서명이 그대로 드러낸다).
5. **`OpenApiContractTest` 는 `a2a9a961` 에서 448 줄로 한도(500)에 52 줄 여유다**(라운드마다 바뀌는 수 — r1 기재 429·71 은 CR r2 LOW 로 정정). 확장 직후 500/500 이었고 대조를
   가르며 확보했다 — 다음 라운드가 그 파일에 단언을 더할 여지는 그만큼이다.
6. **「해당 없음」은 응답의 여집합으로만 안다.** 네 번째 목록을 내지 않기로 한 결과이고, 클라이언트가
   어휘 전수를 알아야 성립한다(그 어휘는 계약의 enum 네 자리가 고정한다).
7. **전략 하나가 무효면 시작하기 화면이 통째로 500 이다**(r1 CR Open Question). 체크리스트 항목 여섯은
   전략과 무관한데도 막힌다 — 전략을 읽는 형제 operation 들과 같은 fail-closed 이고, 부분 응답을 내는
   쪽은 「어느 입력이 빠졌는가」를 응답 모양에 새로 들여야 해서 이 slice 의 축이 아니다. 그 사상은
   endpoint test 가 잠근다(무효 전략 → 500 + `INVALID_STORED_STRATEGY`).
8. **`properties` 키가 없는 스키마는 술어가 판정하지 않는다**(r1 v 참고 R-1). 루트 자유 맵·루트 배열
   아래는 순회 표지(`properties`)가 없어 수집되지 않고, 그 아래 한 겹이 이 확장으로 더 들어갈 뿐이다
   (맵→맵→object 는 옛 술어에서도 통과했다). D-6A1-44 가 고른 표지의 귀결이고 이 slice 의 축이 아니다.
9. **체크리스트 endpoint 를 출하 조립으로 실제 호출하는 test 는 없다**(r1 v 참고 R-2). HTTP 거동은
   test 조립(같은 생성자 빈)에서 재고, 출하 조립은 census 가 컨트롤러와 빈의 기동까지만 잰다 —
   프로필 operation 들과 같은 수준이다.
