# M7/7C②-b — 명령과 실측

base `78d97c59` · 브랜치 `m7-7c2b/2026-10-29` · 마지막 산출물 커밋 `6da69dba`.
출력 전문을 싣지 않는다(핵심 결과 한 줄). **마지막 HEAD 의 전건 결과 정본은 verifier 와 PR 조치
코멘트다** — 이 표는 그 직전까지 담는다.

## 1. RED 선행 (착수 커밋 기준)

- cmd: `./gradlew --no-daemon :app:test --rerun --tests 'bidvector.app.http.BidPriceRequestResponseTest' --tests 'bidvector.app.http.BidPriceRequestEndpointTest'`
- exit: **1(RED)** · 핵심 결과: 44 중 **3 실패** · 컴파일 오류 0(`^e:` 0건)
- RED 인 셋: 개연 불가(`1.00000`) 순수 조립 · 같은 값의 **응답 JSON** · 밴드 바로 밖(`0.29999`).
  셋 다 `expected:<IMPLAUSIBLE> but was:<USABLE>` — 표시가 `origin` 만 보던 사실의 실측이다.
- **RED 가 아닌 둘을 함께 적는다**(초록인 것도 측정이다): 밴드 양 끝(`0.30`·`0.995`)은 앞 판에서도
  `USABLE` 이라 경계 포함을 잠그는 **보존 단언**이고, 정책 해소 전제는 「표시 어휘에 다섯째 상태가
  없다」의 근거(`EffectiveFrom.Initial`)를 잠그는 **전제 단언**이다. 둘은 변이 ⓒ 에서 붉어진다.

## 2. 어휘 등식이 살아 있다 (구현 뒤 · OpenAPI 갱신 전)

- cmd: `./gradlew --no-daemon :app:test --rerun --tests 'bidvector.app.http.BidPrice*' --tests 'bidvector.app.http.OpenApiBidPrice*'`
- exit: **1** · 핵심 결과: 80 중 **1 실패** — `OpenApiBidPriceVocabularyTest` 의 문서↔타입 등식
  하나(`floorRateGroundsState` 에 새 값이 타입에만 있다). 표시 test 다섯은 이 시점에 이미 초록.
- 이 한 줄이 「어휘의 정본은 OpenAPI 이고 생성 타입이 따라간다」가 **기계로 집행된다**는 실측이다.

## 3. 생성 타입 (손 편집 아님)

- cmd: `npm ci` → `npm run api:types` → `npm run api:types:check` (web/)
- exit: 0 / 0 / 0 · 핵심 결과: 바이트 등식 **147654 bytes** — 생성기 출력이 커밋본과 같다.

## 4. DEC-10 census 키 단위 diff

- cmd: `./gradlew --no-daemon :app:test --rerun --tests 'bidvector.app.architecture.FloorTrustGateTest'`
  → `python3 tools/floor-trust-allowlist.py <로그>`
- exit: 1(게이트) / **0**(도구) · 핵심 결과: ⓐ **+1** · ⓑ **+1 / −2** · ⓑ′ **−3**
- 자리별 사유는 `config/quality/architecture-policy.properties` 의 floor-trust 머리말이 정본이다
  (여기 옮겨 적지 않는다 — 같은 사실을 두 자리에 두지 않는다).

## 5. 변이 — 각각 RED, 통제군은 `--rerun`

표적은 네 묶음(`FloorTrustGate*` · 투찰가 · 어휘 · 밴드 값 일치)이고 **매 회차 `--rerun`** 이다
(FROM-CACHE 는 실행이 아니다 — 로그의 `FROM-CACHE` 0건).

| 변이 | 바꿔치운 것 | exit | 붉어진 자리 |
| --- | --- | --- | --- |
| 통제군 | 없음(산출물 그대로) | **0** | 100 초록 · 실패 0 · 건너뜀 0 |
| ⓐ | 표시가 다시 `origin` 만 보고 상태·값을 짓게 | **1** | 거동 3 + **census ⓑ′**(원값 멤버 접근 둘이 되돌아온다) |
| ⓑ | `IMPLAUSIBLE` 에서 근거 칸에 값을 싣게 | **1** | 거동 3 + **census ⓑ·ⓑ′**(제외 가지에서 원값을 꺼내는 자리가 새로 보인다) |
| ⓒ | 표시가 평가와 다른 밴드(리터럴)를 보게 | **1** | 거동 3 + **census ⓒ**(「밴드의 출처가 하나다」) |

- 컴파일 오류 0(세 회차 모두 `^e:` 0건) — 의도한 test 에서 RED 다.
- ⓐⓑⓒ 가 **거동과 census 두 층에서** 동시에 붉은 것이 이 slice 의 핵심 측정이다: 거동 단언만
  지우면 census 가 남고, census 를 재생성으로 덮으면 거동이 남는다.
- 변이 적용 전 HEAD 가 매번 커밋돼 있었고 되돌림은 그 파일 하나의 `git checkout --` 이다
  (미커밋 산출물 없음 — 회차마다 뒤에 `git status --porcelain` 빈 출력 확인).

## 6. acceptance — CI job 명령 그대로

Kotlin `check` job(`.github/workflows/ci.yml`) — 두 회차를 **실패 이력까지** 적는다:

| 회차 | HEAD | cmd | exit | 핵심 결과 |
| --- | --- | --- | --- | --- |
| 1 | `187da92f` | `./gradlew --no-daemon check` | **1** | `app:test` **3 실패** — 새 표면 둘이 게이트 미등재(주입 표면 등식 둘 · 상수시간 비교 그물 하나) |
| 1 | `187da92f` | `./gradlew --no-daemon qualityBaseline` | 0 | 측정이라 실패시키지 않는다 |
| 2 | `b36857be` | `./gradlew --no-daemon check` | **1** | `app:detekt` **1 실패** — 1회차 수정에서 더한 근거 문장이 줄 길이 상한 초과 |
| 2 | `b36857be` | `./gradlew --no-daemon qualityBaseline` | 0 | — |
| 3 | `6da69dba` | `./gradlew --no-daemon check` | **0** | 전건 초록(이 문서의 본문이 트리에 있는 상태에서 쟀다 — 누출 게이트의 scanRoot 가 이 디렉터리다) |
| 3 | `6da69dba` | `./gradlew --no-daemon qualityBaseline` | 0 | — |

**두 회차의 빨강이 이 slice 의 교훈**이다 — 표적 test 선택(투찰가·어휘·census)으로는 세 자리가
보이지 않았고 **전건 job 이 잡았다**. 둘째 빨강은 그 수정 자체가 만든 것이고, 표적
`:app:ktlintCheck` 가 **그 편집 전에** 돌아 가려졌다.

수정 뒤 표적 재확인(각각 해당 HEAD):

| cmd | exit | 핵심 결과 |
| --- | --- | --- |
| `./gradlew --no-daemon :app:test --rerun --tests 'bidvector.app.architecture.AppHttpDependencyGateTest' --tests 'bidvector.app.http.ConstantTimeComparisonStructureTest'` | 0 | 등재 둘이 등식을 만족 |
| `./gradlew --no-daemon :app:detekt :app:ktlintCheck` | 0 | `^e:` 0건 |

`web` job 전건(`web/`, 단계 ID `W-1`~`W-11`, HEAD `6da69dba`) — **전부 exit 0**:
`npm ci`(W-1) · `npm run lint`(W-2) · `npm run typecheck`(W-3) · `npm run gate:src-tree`(W-4) ·
`npm run api:types:check`(W-5) · `npm run test`(W-6) · `npm run gate:tests`(W-7) ·
`npm run build`(W-8) · `npm run gate:boundary-canary`(W-9) ·
`npx playwright install chromium`(W-10) · `npm run test:e2e`(W-11, 스모크 2 통과).

CI 전용 단계(`npx playwright install-deps chromium`, apt)는 로컬에서 돌리지 않는다 —
`tools/one-command-check.sh` 머리 주석이 그 제외를 이미 규정한다.

`ml-engine`·`container` job 은 입력이 바뀌지 않았다(이 slice 의 변경 경로에 `ml-engine/**` ·
`docker/**` 가 없다 — 복원 목록이 그 전수다). `container` 는 PR CI 가 정본이다.

## 7. 비밀값 스캔

- cmd: `command grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m7/7c2b/`
- exit: **0 — 매치 있음**. 전부 **이 slice 가 더하지 않은 기존 줄**이다(정책 파일의 비밀값 축 키 ·
  capability-map 의 legacy 분류 근거 · OpenAPI 의 보안 스키마 종류). 그 줄들은 `leakPatternGate`
  의 baseline 에 이미 있어 `check` 에서 초록이다.
- 그래서 **이 slice 가 더한 줄만** 따로 쟀다: `git diff 78d97c59..HEAD -- ':!reports/evidence'` 의
  `+` 줄과 이 디렉터리의 문서를 모아 같은 패턴 파일로 스캔 → **exit 1(매치 없음 = 통과)**, 434줄.
- 육안 확인: 알림 채널 식별자·사업자 정보 없음. 패턴 어휘를 이 문서에 축어로 적지 않는다.

## 8. clean-tree

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`(코드·정책·문서 열하나)
- 결과: **빈 출력** · 양성 대조 1회 — in_scope 파일 하나에 줄을 덧붙이면 ` M …` 로 잡히고,
  **비파괴 절삭**(`head -n <원래 줄수>`)으로 복구한 뒤 다시 빈 출력(`checkout --` 을 쓰지 않는다 —
  미커밋 편집을 함께 지운다).
- evidence 경로는 이 문서를 커밋하기 전이라 더럽다 — 그 커밋 뒤의 빈 출력은 verifier 가 잰다.

## 9. 낡는 좌표 — 역방향 파급

이 slice 는 `file:line` 을 새로 적지 않는다. 편집한 파일을 가리키는 **다른 문서의 좌표**는
`grep -rnoE '<stem>:[0-9]+'` 로 전수를 냈다(전체 파일명과 축약형 둘 다).

- `docs/discovery/capability-map.md` — 삽입 지점 1752, **순증 +3**. 그 아래를 가리키는 좌표
  **27건**이 밀렸다(다른 slice·마일스톤의 evidence 와 codex 리뷰 JSON).
- `openapi/bidvector-operator-api.yaml` — 첫 삽입 145, 그 아래 좌표 **1건**(다른 slice 의
  checklist)이 밀렸다.
- `config/quality/architecture-policy.properties`·생성 타입·Kotlin 넷 — 가리키는 좌표 **0건**.

밀린 28건은 **전부 in_scope 밖**(다른 slice·마일스톤의 evidence)이라 고치지 않고 알려진 제한으로
둔다. 그 문서들을 이 slice 가 편집하면 그쪽 slice 의 base/head 가 흔들린다.

## 10. 알려진 제한

① **표시 경로가 census ⓐ 에 처음 들어왔다.** D-DEC10-4(「분석·표시는 게이트를 걸지 않는다」)가
   **표시 축에서 바뀐 것**이고 가격 경로의 진입 타입은 불변이다. 정책 파일 머리말이 그 사실을 적는다.
② **`OPEN-DEC10-CRITICAL-RATE-CALLERS` 는 out_scope 이고 이 slice 가 그 사각을 줄이지 않는다.**
   이미 허용된 코드 단위 안에서 shared-kernel 원값 진입 함수를 부르는 형태는 census 가 보지 못하며,
   이 slice 의 `floorRateFieldsOf` 도 그 허용 단위 가운데 하나다(서명이 바뀌어 키만 재적혔다).
③ **밴드 밖 원값을 응답에 아예 내지 않는다**(D-7C2B-2 의 선택). 운영자는 「공고에 어떤 값이 적혀
   있었나」를 이 응답으로 볼 수 없고 출처·차수만 본다. 새 칸을 만들지 않기로 한 결과다.
④ **표시 율 문자열은 `Rate` 의 정규화 결과다** — 게시 표기 `0.30000` 은 `0.3` 으로 나간다. 이
   slice 가 만든 거동이 아니고(DEC-03 축의 기존 정규화), 밴드 경계 test 가 그 사실을 고정한다.
⑤ **좌표 28건이 밀렸다**(§9) — 전부 in_scope 밖 문서라 고치지 않았다.
⑥ **7C④-2b 와 OpenAPI·생성 타입이 겹친다**(D-7C2B-5). 이 브랜치는 base 이후 main 을 병합하지
   않았다 — 그 slice 가 먼저 머지되면 **재생성 + 바이트 등식**과 OpenAPI 키 계수·중복 키 0 확인이
   필요하고, rollback 복원 기준도 그 병합된 main 커밋으로 재산출해야 한다.
⑦ **web 화면은 이 필드를 아직 소비하지 않는다**(7E 미착수). 어휘와 타입은 섰지만 운영자가 화면에서
   그 상태를 읽는 것은 그 slice 뒤다 — 소비 코드를 이 slice 가 만들지 않았다.
⑧ **게이트 등재 둘이 계약이 지명한 키 밖이다**(주입 표면 · 상수시간 비교 그물). 같은 in_scope
   파일 안이고 승인된 설계의 기계적 귀결이지만, 계약 문면에는 census 키만 적혀 있다 — 계약 갱신
   대상으로 보고한다.
