# M9 / budget-clear — 실행한 명령과 핵심 결과

판정 대상 SHA **`c36a01e1`**(산출물 마지막 커밋). base `79f3e5c4`. 명령은 CI 워크플로의 job 명령 그대로이고
Gradle 은 빌드 자리 래퍼(`bv-build.sh`)를 지난다.

## acceptance — CI `check` job

| 명령 | 결과 |
| --- | --- |
| `./gradlew --no-daemon check` | **exit 0** (31m22s — 다른 레인의 빌드와 자리를 나눠 썼다). 최근 실행 XML 합 test 4,162 · app test 클래스 121. `contractGate`·`sizeGate`·`typeShapeGate`·`gateExecutionGate`·`gateRegistrationGate`·`compatibilitySmoke` 전부 이 실행 안에서 돌았다 |

새 test 클래스 `StrategyBudgetClearEndpointTest` 는 **만드는 커밋에서** `config/quality/gate-tests.properties` 의
`gate.tests.app` 블록에 사전순으로 등재했다 — 등재가 없으면 `gateRegistrationGate` 가 모집단 등식으로 떨어진다.

## acceptance — CI `web` job

| step | 명령 | 결과 |
| --- | --- | --- |
| W-1 | `npm ci` | exit 0 |
| W-2 | `npm run lint` | exit 0 |
| W-3 | `npm run typecheck` | exit 0 |
| W-4 | `npm run gate:src-tree` | exit 0 |
| W-5 | `npm run api:types:check` | exit 0 — 생성 타입이 OpenAPI 문서와 **바이트까지 같다**(188,894 bytes). 재생은 `npm run api:types` |
| W-6 | `npm run test` | exit 0 — 34 파일 · 677 test(이 slice 가 더한 셋 포함 — 편집 세션 표시 파일 45) |
| W-7 | `npm run gate:tests` | exit 0 — 등록부 기준 skipped/todo 0 · failed 0 |
| W-8 | `npm run build` | exit 0 |
| W-9 | `npm run gate:boundary-canary` | exit 0 — 메인·워커 두 그래프에서 경계 플러그인이 fixture 실경로를 거부 |
| W-10 | `npx playwright install chromium` | exit 0 |
| W-11 | `npm run test:e2e` | exit 0 — 2 passed |
| W-12 | `./gradlew --no-daemon :app:bootJar` | exit 0 — 이 jar 로 아래 여정이 돈다(판정 SHA 의 main 소스에서 빌드된 것) |
| W-13 | `npm run test:e2e:journey` | exit 0 — **11 passed · flaky 0 · 건너뜀 0 · spec 4/4**. 여정 ① 이 금액대를 적용하고 되돌리며, 여정 ②③④⑤·설정 여정 셋의 **출발 단언이 그대로다** |

`container`·`ml-engine` job 은 돌리지 않았다 — 이 slice 의 변경에 Python·docker·migration 이 0 이다.

## 변이 — 바꿔치우기와 그 RED

변이는 **커밋 뒤**에 걸고 원복은 변이 파일만 정확 문자열로 되돌렸다(`git checkout --` 쓰지 않음). 네 변이 모두
대상 단언에서만 떨어진다 — 「더하기만 한 변이」가 아니라 **바꿔치우기**다.

| 변이 | 무엇을 바꿨나 | 결과 |
| --- | --- | --- |
| M-1 | 금액 칸 추출의 비움 갈래를 **0 원**으로 (`Cleared -> null` → `Cleared -> 0L`) | **RED 4** — 전략 조회 null · 제안 게이트 · 체크리스트 복귀 · 전수 게이트(값을 세운 초안으로 되돌아오지 않는다) |
| M-2 | **칸 생략도 비움으로** 받게 (명시적 null 술어 → null 이거나 없음) | **RED 1** — 「값 칸 생략은 여전히 400」 |
| M-3 | 비움을 **모든 필드가** 받게 (`acceptsClearing` → 항상 참) | **RED 2** — 「비움을 받지 않는 필드의 null 은 400」 · 전수 게이트(받는다고 한 필드가 터진다) |
| M-4 | 화면의 비움 본문을 **0 원**으로 (`amountWon: null` → `amountWon: 0`) | **RED 3** (vitest) — 빈 칸의 본문 · 표 전수 대조 · 보낸 값 문면 |

원복 뒤 `git status --porcelain -- app/src/main/kotlin web/src/display/editSessionDisplay.ts` 빈 출력.

## D-3 확인(코드 변경 0)

「요건 원문이 감시 텍스트에 실리는 production 경로 0」을 드는 게이트는 `EvaluationAdapterDependencyTest` 의
상수 풀 허용 목록 단언(양성 대조 동반)이고, 위 `check` 실행 안에서 돌아 **살아 있다**. 이 slice 는 그 사실만
확인하고 감시 커널·어댑터를 건드리지 않았다.

## 호스트 규율

무거운 명령마다 앞서 `pgrep -af 'GradleWrapperMain|GradleWorkerMain'` · `free -m` · `ps -eo pid,rss,args --sort=-rss`
를 **별도 호출로** 재고 시작했다. Gradle 은 전부 `bv-build.sh`(자리 둘) 경유 · `--no-daemon`. 레인 종료 시 자기
Kotlin 컴파일 daemon 만 PID 로 멈춘다(`./gradlew --stop`·`pkill -f` 쓰지 않음).
