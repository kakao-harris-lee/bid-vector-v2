# M9 / budget-clear — 실행한 명령과 핵심 결과

**판정 대상 SHA `a8120fb5`** — `origin/main`(`6c38fb32`)을 이 브랜치로 병합한 커밋이고 PR #124 의 현재
head 다. 산출물 자체의 마지막 커밋은 `be1026dc`(수정 라운드) · 그 앞은 `c36a01e1` · 첫 리뷰 대상 head 는
`b988f985` 였다. base `79f3e5c4`, 병합 뒤 비교 원천은 `6c38fb32`(`rollback.md` 「원천이 바뀐 이유」).

**acceptance 의 정본은 PR CI 다**(팀장 공지 2026-11-05 — 호스트 빌드 큐 포화로 전건 재실측을 CI 로 옮겼다).
로컬은 표적·변이만 돌렸다.

## acceptance — PR #124 CI (정본)

**run `42009499386`** · head `a8120fb5feac5415a6add7b102eb291adc4ae86f` · **completed/success**
(2026-11-05T10:17:38Z → 11:04:20Z). `gh pr checks 124 --watch` 가 낸 네 job 전부 **pass**:

| job | 결과 | 소요 |
| --- | --- | --- |
| `check` | **pass** | 46m38s |
| `web` | **pass** | 3m15s |
| `container` | **pass** | 5m59s |
| `ml-engine` | **pass** | 8m53s |

`container` 가 초록으로 돌아온 것은 이 slice 와 무관하다 — PR #126(CVE-2026-109518 apt 하한)이 main 에
들어오고 그것을 `a8120fb5` 가 받았다. 앞 head `b988f985` 의 run `41994036870` 은 그 CVE 로 `failure` 였다.

**병합 커밋은 내 본문을 하나도 바꾸지 않았다**: `git diff 6c38fb32..a8120fb5` 의 patch-id 가 병합 **전** slice
diff(`79f3e5c4..34ddda2b`)와 같고(`d3d2ac3c…`) 경로 수 18 도 같다. main 이 바꾼 경로와 내 18 경로의 교집합은
**0** 이다 — 그래서 아래 로컬 전건(`be1026dc`)과 위 CI 가 **같은 내 코드**를 쟀다.

## acceptance — 로컬 전건(앞 라운드, 참고)

| 명령 | 결과 |
| --- | --- |
| `./gradlew --no-daemon check` @`be1026dc` | **exit 0** (31m50s). `contractGate`·`sizeGate`·`typeShapeGate`·`gateExecutionGate`·`gateRegistrationGate`·`compatibilitySmoke` 전부 그 실행 안에서 돌았다. 그 앞 라운드(`c36a01e1`)도 exit 0 — 31m22s · 최근 실행 XML 합 test 4,162 · app test 클래스 121 |

새 test 클래스 `StrategyBudgetClearEndpointTest` 는 **만드는 커밋에서** `config/quality/gate-tests.properties` 의
`gate.tests.app` 블록에 사전순으로 등재했다 — 등재가 없으면 `gateRegistrationGate` 가 모집단 등식으로 떨어진다.

## `web` job 의 step 별 로컬 실행 (앞 라운드, 참고 — 정본은 위 CI `web` pass)

| step | 명령 | 결과 |
| --- | --- | --- |
| W-1 | `npm ci` | exit 0 |
| W-2 | `npm run lint` | exit 0 |
| W-3 | `npm run typecheck` | exit 0 |
| W-4 | `npm run gate:src-tree` | exit 0 |
| W-5 | `npm run api:types:check` | exit 0 — 생성 타입이 OpenAPI 문서와 **바이트까지 같다**(189,329 bytes — `description` 이 들어와 앞 라운드보다 435 늘었다). 재생은 `npm run api:types` |
| W-6 | `npm run test` | exit 0 — 34 파일 · **678 test**(이 slice 가 더한 넷 — 편집 세션 표시 셋 + 설정 화면 하나) |
| W-7 | `npm run gate:tests` | exit 0 — 등록부 기준 skipped/todo 0 · failed 0 |
| W-8 | `npm run build` | exit 0 |
| W-9 | `npm run gate:boundary-canary` | exit 0 — 메인·워커 두 그래프에서 경계 플러그인이 fixture 실경로를 거부 |
| W-10 | `npx playwright install chromium` | exit 0 |
| W-11 | `npm run test:e2e` | exit 0 — 2 passed |
| W-12 | `./gradlew --no-daemon :app:bootJar` | exit 0 — 이 jar 로 아래 여정이 돈다(판정 SHA 의 main 소스에서 빌드된 것) |
| W-13 | `npm run test:e2e:journey` | exit 0 — **11 passed · flaky 0 · 건너뜀 0 · spec 4/4**. 여정 ① 이 금액대를 적용하고 되돌리며, 여정 ②③④⑤·설정 여정 셋의 **출발 단언이 그대로다** |

`container`·`ml-engine` 은 **로컬에서** 돌리지 않았다(이 slice 의 변경에 Python·docker·migration 이 0 이다) —
병합 뒤 둘 다 위 CI 에서 pass 다.

## 변이 — 바꿔치우기와 그 RED

변이는 **커밋 뒤**에 걸고 원복은 변이 파일만 정확 문자열로 되돌렸다(`git checkout --` 쓰지 않음). 여섯 변이
모두 대상 단언에서만 떨어진다 — 「더하기만 한 변이」가 아니라 **바꿔치우기**다. M-1~M-4 는 수정 라운드에서
**다시 걸어 RED 를 재확인**했다.

| 변이 | 무엇을 바꿨나 | 결과 |
| --- | --- | --- |
| M-1 | 금액 칸 추출의 비움 갈래를 **0 원**으로 (`Cleared -> null` → `Cleared -> 0L`) | **RED 4** — 전략 조회 null · 제안 게이트 · 체크리스트 복귀 · 전수 게이트(값을 세운 초안으로 되돌아오지 않는다) |
| M-2 | **칸 생략도 비움으로** 받게 (명시적 null 술어 → null 이거나 없음) | **RED 1** — 「값 칸 생략은 여전히 400」 |
| M-3 | 비움을 **모든 필드가** 받게 (`acceptsClearing` → 항상 참) | **RED 2** — 「비움을 받지 않는 필드의 null 은 400」 · 전수 게이트(받는다고 한 필드가 터진다) |
| M-4 | 화면의 비움 본문을 **0 원**으로 (`amountWon: null` → `amountWon: 0`) | **RED 4** (vitest) — 빈 칸의 본문 · 표 전수 대조 · 보낸 값 문면 · 설정 화면의 본문 |
| **M-5** | 비움 판정을 **칸 배정·알려진 키 검사 앞으로** 옮김 (검사 순서 변이 — v M-1 재현) | **RED 1** — 「비움 요청에 다른 칸이나 미지 키가 함께 오면 400」. 그 변이가 앞 라운드에서는 **전부 초록**이었다(검증 레인 실측) |
| **M-6** | 화면이 **빈 칸에서 본문을 만들지 않게** (비우기 분기 자체를 끔) | **RED 4** (vitest) — 표시 셋 + 설정 화면 하나 |

원복 뒤 `git status --porcelain -- app/src/main/kotlin web/src/display/editSessionDisplay.ts` 빈 출력.

## D-4 정정의 근거 — 수정 라운드에서 실측한 것

| 무엇 | 실측 |
| --- | --- |
| `OPEN-6F4-TITLE-INGEST` 는 닫혔나 | **닫혔다** — `reports/evidence/m6/6f8/scope.md` 의 OPEN 표가 「수령·닫음 · D-6F8-2」, 같은 문서 D-6F8-2 가 공고명을 필드 계약 데이터로 옮긴 결정, milestone-6 의 「6F-8 종결 2026-09-24 — PR #45」 |
| `OPEN-6F10-*` 는 몇인가 | **여덟**(합집합). 두 자리가 **양방향으로** 어긋난다 — 6F-10 「이 slice 가 신설한 OPEN」 표에 일곱 · `milestone-6.md` 종결 문단에 일곱인데 **같은 일곱이 아니다**: 표에만 `CAUSE-CODE-DEDUP`, 문단에만 `STRATEGY-EVENT-CONSUMER`(D-6F10-13 신설) · 교집합 여섯 |
| 그 여덟에 처분이 있나 | **없다** — 닫음어휘 근접 술어의 일치는 **일곱**이고 전부 **종결 문단 한 줄**에서 나온다(그 줄에 식별자 일곱과 종결 어휘가 함께 있다). 여덟째 `CAUSE-CODE-DEDUP` 은 일치 **0 건**이다. §1 이 적은 오탐 꼴이라 판정으로 쓰지 않았다(v r2 L-a — 앞 판이 이 계수를 「여덟 모두」로 적었다) |
| `OPEN-6F4W-UNAVAILABLE-PRODUCER` | 신설 기록만 있고 처분 기록 0 — 열린 채로 둔다 |

## D-3 확인(코드 변경 0)

「요건 원문이 감시 텍스트에 실리는 production 경로 0」을 드는 게이트는 `EvaluationAdapterDependencyTest` 의
상수 풀 허용 목록 단언(양성 대조 동반)이고, 위 `check` 실행 안에서 돌아 **살아 있다**. 이 slice 는 그 사실만
확인하고 감시 커널·어댑터를 건드리지 않았다.

## 호스트 규율

무거운 명령마다 앞서 `pgrep -af 'GradleWrapperMain|GradleWorkerMain'` · `free -m` · `ps -eo pid,rss,args --sort=-rss`
를 **별도 호출로** 재고 시작했다. Gradle 은 전부 `bv-build.sh`(자리 둘) 경유 · `--no-daemon`. 레인 종료 시 자기
Kotlin 컴파일 daemon 만 PID 로 멈춘다(`./gradlew --stop`·`pkill -f` 쓰지 않음).
