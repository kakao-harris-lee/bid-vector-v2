# M7/7C③-b 실행 기록

base `9463f6e1` · 브랜치 `m7-7c3b/2026-10-31` · 계약 `scope.md`(D-7C3B-1~11) · 설계 검토 `checklist.md`.

재현은 worktree 뿌리에서. 무거운 Gradle 명령은 `~/.local/bin/bv-build.sh <명령>` 으로 감싼다
(호스트 빌드 자리 둘 · 획득 순간 메모리 재확인).

## §1 착수 실측 (Phase 1, 구현 전)

종류 어휘 여덟 × 원천을 코드 경로로 확인했다. **자동 원천이 서는 것은 넷**(마감 D-1/D-0 ·
서류 기한 D-2 · 공고 개정)이고, 둘은 **운영자 기록의 되비춤**뿐(개찰 완료 · 낙찰/패찰), 하나는
계정이 조건부(투찰가 요청), 하나는 원천 0(새 후보)이다. 표와 근거는 팀장 보고와 `checklist.md` §2.

그 실측이 계약 결정 넷을 반박했고 D-7C3B-7~11 로 갱신됐다 — 유찰 어휘 부재 · in_scope 의 web
화면 표 둘 · 7C③ 의 「COL-01 관련성 원천 없음」이 7B 머지로 뒤집힌 것 · 여정 ⑤ 단언의 변화.

```
# 상주 스케줄러 부재(일회 러너 + 외부 cron 이 유일한 장치)
command grep -rn "@Scheduled|EnableScheduling|TaskScheduler" --include=*.kt .   # 0건
```

## §2 RED 와 구현

**순서를 사실대로 적는다.** 되비춤·훑기의 **순수 축**은 구현과 test 를 같은 라운드에 썼고,
RED 는 **변이로 세웠다**(§4) — test-first 순서가 아니었다. 그 뒤의 축들은 test 가 먼저였다:
알림함 어휘 셋은 기존 단언 넷을 붉히며 들어왔고(`NOT_RECORDED_YET` 를 재던 자리), 러너의 출구와
배선의 켜짐 조건은 **미측정이던 자리를 test 가 먼저 열었다**.

측정한 RED 셋(값만):

| 자리 | 무엇이 붉었나 |
| --- | --- |
| 어휘 교체 | `BidPriceRequestEndpointTest`·`BidPriceRequestE2ETest`·`BidPriceRequestResponseTest`·`worksheetDisplay.test.ts` 가 옛 어휘를 재고 있었다 |
| 생성 타입 | `npm run typecheck` 이 `satisfies Record<…>` 에서 멈췄다(화면 표 둘 + 화면 test fixture 하나) |
| 게이트 래칫 | `check` 가 여덟 자리에서 붉었다 — §3 |

## §3 게이트 래칫 여덟

새 조립·새 호출이 **기존 등식을 깨는 것이 정상**이고, 각 등재는 근거를 단다.

| 게이트 | 무엇이 늘었나 | 근거 |
| --- | --- | --- |
| `app.port-call.allowed-pairs` | 쌍 둘 | 쓰기 포트를 app 에서 부르는 자리 **전수**가 그 둘이다 |
| `app.assembly.tier1-bootstrap-classes` | 배선 하나 | 조건부 러너 배선(보존·승격과 같은 분류) |
| `app.assembly.tier3-collection-classes` | 러너 갈래 넷 | 아홉째 일회 러너 — 요청 경로가 아니다 |
| `app.injection.allowed-types` | 포트 하나 | ② 층 팩토리가 생성자로 받는다(제한 층은 `denied-types` 가 다시 판다) |
| `app.assembly.tier2.allowed-workflow-types` | 타입 넷 | 그 팩토리가 참조하는 사실·결과·포트·기록 |
| `app.logging.allowed-users` | 배선 둘 | 잃은 알림의 표지와 러너 로그 출구 |
| `app.runner.allowed-referencers` | 러너 하나 | 같은 러너 |
| `AppHttpDependencyGateTest` 면제 계수 | 75 → 80 | 위 러너 넷 + 배선 하나 |
| `AccountCarrierStructureTest.ACCOUNT_READERS` | controller 하나 | **넷째 분류** — 계정 전용 뿌리가 아닌데 주체를 읽는 자리 |
| `ConstantTimeComparisonStructureTest` | 파일 클래스 하나 | 아래 bytecode 실측 |

`areEqual` 허용의 근거를 **실측**했다(이름이 아니라 bytecode):

```
javap -p -c -v app/build/classes/kotlin/main/bidvector/app/http/BidPriceRequestResponseKt.class
```

핵심 결과: `Intrinsics.areEqual` 호출 **7**, 그 피연산자는 `NotificationInboxOutcome$Created` ·
`$Updated` · `$Failed` 셋뿐. 자격증명 어휘는 **축어로 적지 않고 참조로 센다** —
`command grep -ciE -f config/quality/leak-patterns.txt <javap 출력>` 과 같은 축이고, 그 바이트코드
덤프에서 일치한 **9 건은 전부 JVM 의 속성명**(제네릭 서명 attribute)이라 자격증명 좌표는 **0** 이다.

## §4 변이 — 열둘, 통제군 넷

버릴 clone 에서 돌렸다(`git clone --no-hardlinks`). 각 변이는 **바꿔치우기**이고(더하기만 한
변이는 초록이라 재지 못한다), 실패 수는 test 결과 XML 에서 센다. 통제군은 **같은 명령**을
`--rerun` 으로 돌린다(cache 적중이 「실행」으로 읽히지 않게).

```
python3 <scratchpad>/mutate.py <clone>      # 표의 값이 그 출력이다
```

| # | 변이 | 실패 / 전체 |
| --- | --- | --- |
| C1 | 통제군 workflow | **0** / 56 |
| C2 | 통제군 adapters(실 DB) | **0** / 7 |
| C3 | 통제군 app 투찰가·트리거 | **0** / 62 |
| C4 | 통제군 app 러너 | **0** / 4 |
| M1 | 되비춤 사상에서 낙찰을 지운다 | 2 / 56 |
| M2 | 유찰을 패찰로 접는다 | 2 / 56 |
| M3 | 페이퍼 가드를 지운다 | 1 / 56 |
| M4 | 전이가 저장되지 않아도 되비춘다 | **0 → 1** / 57 (아래) |
| M5 | 마감 임박 창을 모레까지 넓힌다 | 2 / 56 |
| M6 | 달력일 대신 경과 시간으로 센다 | 5 / 56 |
| M7 | 마감 알림의 상태 필터를 지운다 | 3 / 56 |
| M8 | 개정이 담기 전인지 묻지 않는다 | 1 / 56 |
| M9 | 질의가 페이퍼 건까지 본다(실 DB) | 1 / 7 |
| M10 | 계정 주체가 없어도 적는다 | 2 / 62 |
| M11 | 갱신을 새 기록으로 접는다 | 2 / 62 |
| M12 | 기록 실패를 0 으로 끝낸다 | 1 / 4 |

**M4 는 처음에 초록이었다 — 실제 구멍이었다.** 저장이 `CaseNotFound` 로 떨어진 전이에 알림을
적는 변형이 **아무 test 도 지나지 않고** 통과했다. 그 갈래는 「읽기와 저장 사이에 건이 사라진」
자리라 소유 확인(읽기)으로는 닿지 않고, 이중체에 그 상황을 만드는 자리가 없었다. 이중체에
그 자리를 두고 음성 단언을 세운 뒤 **같은 변이가 1 실패**다(모집단이 56 → 57 로 는 것이 그
test 하나다). 통제군 C1 은 그 뒤에도 0 이다.

## §5 acceptance — CI 명령 그대로

```
./gradlew --no-daemon check
./gradlew --no-daemon qualityBaseline
cd web && npm ci && npm run lint && npm run typecheck && npm run gate:src-tree \
  && npm run api:types:check && npm run test && npm run gate:tests \
  && npm run build && npm run gate:boundary-canary
npx playwright install chromium && npm run test:e2e        # W-11 스모크
./gradlew --no-daemon :app:bootJar && npm run test:e2e:journey   # W-12·W-13 여정
```

`container` job 은 입력이 바뀌지 않았다 — **마이그레이션 0**(V23 이 어휘 여덟과 열 단위 GRANT 를
이미 들고 있고 `RetentionTableRegistry` 에 파기 행도 있다). path-limited diff 로 확인:

```
git diff --name-only 9463f6e1..HEAD -- adapters/src/main/resources/db/migration docker deploy
```

핵심 결과: 빈 출력.

### 결과

| 명령 | 결과 |
| --- | --- |
| Kotlin `check` + `qualityBaseline` | **BUILD SUCCESSFUL** (29m 16s) — test **3790 · 실패 0**(app 973 · adapters 1258 · workflow 682 · 나머지 877) |
| web job(Playwright 밖 여덟 step) | 여덟 전부 통과 — `api:types:check` 가 생성 타입과 OpenAPI 의 **바이트 등식**을 확인 |
| 여정 e2e(W-13) | **11 통과 · flaky 0 · 건너뜀 0 · 중단 0 · spec 4/4**(등록부와 같다) |
| `container` job 입력 | 무변경(위 path-limited diff 빈 출력) |

### 전건에서만 붉던 자리 둘 — 둘 다 측정으로 잡았고 둘 다 사실이다

**ⓐ 공유 이중체 오염.** 새 표면 test 가 같은 Spring 문맥의 이중체(전략 · 자격 판정 · 공고)를
투찰가용으로 바꿔 두고 되돌리지 않아, 바로 뒤에 도는 체크리스트 test 셋이 감시 조건 없는 전략을
보고 다른 답을 냈다. **표적 실행에서는 보이지 않고 전건에서만 나타난다** — 바꾼 쪽이 되돌리게
고쳤다(`BidCaseEndpointTest` 의 마감 이중체와 같은 규율).

**ⓑ stale 한 Gradle 결과 바이너리.** `app/build/test-results/test/binary/` 가 앞 실행과 어긋나면
`:app:test` 가 **16초 만에 `java.io.EOFException`** 으로 떨어진다(테스트가 하나도 돌지 않고, XML 도
남지 않는다). `earlyoom` 기록은 없었다 — OOM 이 아니다. 그 디렉터리를 지우면 정상 시간(약 28분)을
돈다. 이 slice 의 산출물과 무관한 **도구 상태**이고, 그래서 「붉다」를 산출물 탓으로 읽지 않도록 적는다.

## §6 rollback

`rollback.md` — 경로 한정 복원 목록(기계 산출) · 버릴 clone ①~⑦ 실측 · `실측 HEAD 6af4751d`.
핵심 결과: 복원 31 · 삭제 18 · 되돌린 경로의 diff **0 줄** · 복원 트리에서 `check` **exit 0** ·
base 와 남는 차이는 팀장 계약 파일 하나(의도된 제외). 내 표지 여덟 전부 0, 남의 표지 넷 보존.

**측정 환경의 사실 하나**: 이 레인의 clone·로그는 scratchpad **공유** 디렉터리에 두었다가 다른
레인에 지워졌고, 레인 전용 하위 디렉터리로 옮겨 다시 쟀다. 위 수치는 전부 **옮긴 뒤**의 실측이다.

## §7 r1 승인 전 일괄 — 고침별 RED → GREEN 과 재현 명령

**재현의 공통 자리**: 표적 Gradle 은 `~/.local/bin/bv-build.sh ./gradlew --no-daemon -q <task> --rerun`,
변이는 버릴 clone 에서 `python3 <레인 scratchpad>/mutate.py <clone>`.

| 항목 | 고치기 전(RED 로 세운 방법) | 고친 뒤 | 재현 |
| --- | --- | --- | --- |
| v M-1 읽기 전수 | `list()`·`timeline()` 에 되비춤을 끼우는 변형이 **아무 test 도 지나지 않았다**(읽기 test 가 `read()` 하나) | 읽기 셋을 한 자리에서 돌며 **양성 표지**(마감 판정이 상태를 옮겼다) 뒤 음성 단언 | `:workflow:test --tests bidvector.workflow.bidding.BidCaseNotificationTest` |
| v L-3 정책 기준일 | 기준일이 배선의 **지역 변수**라 UTC/KST 하루 어긋남을 잴 자리가 없었다(출하 표 항목이 하나라 값으로도 안 보인다) | 계산을 함수로 꺼내고 KST 경계(20:00Z)를 단언 + UTC 음성 대조 | `:app:test --tests bidvector.app.wiring.NotificationSweepWiringTest` |
| v L-4 = CR L-2 사실의 시각 | 같은 날 두 번 돌면 `occurredAt` 이 움직여 항목이 목록 맨 위로 다시 올라왔다 | 그 KST 달력일의 **시작**을 싣는다 — 같은 날 아침·저녁이 같은 값, 다음 날은 다른 값 | `:workflow:test --tests bidvector.workflow.bidding.NotificationSweepTest` |
| v L-1 여정 선행 | 머리글이 「순서 독립」을 말했으나 알림함 test 는 같은 파일 여정 ⑤ 에 기댄다 | 주장을 사실대로 정정(파일 안 결정적 · 다른 spec 과는 독립) | `cd web && npm run test:e2e:journey` |
| v L-2 배지 등식 | 「전수 헤더 == 첫 페이지 안읽음」이라 항목이 한 페이지를 넘는 날 거짓으로 붉어진다 | 등식은 배지 ↔ 헤더, 본문과는 부등식 | 〃 |
| CR L-6 행 locator | 화면 전체 locator 가 셸·다른 화면의 같은 접두를 집는다 | 알림함 화면 안으로 좁힘 | 〃 |
| CR L-1 러너 차례 | 「아홉째」가 주장이었다 | 술어로 재실측해 KDoc 에 명령째 적음 | `command grep -rl ": ApplicationRunner" app/src/main/kotlin \| wc -l` → **9** |
| CR M-1 · CR L-3·L-4 · CR OQ | ─ | **알려진 제한 등재**(checklist §2 10~13) — 술어 변경 없음 | ─ |
| CR L-5 요구표 | 비고가 「cron 등록은 7F」로만 적혀 발화 여부가 안 보였다 | 「**cron 미등록이라 배포에서 아직 발화하지 않는다**」를 세 행에 | ─ |

## §8 병합 라운드 둘

| 병합 | main | 충돌 | 해소 |
| --- | --- | --- | --- |
| 첫째 | `ac806a51`(7C⑤-b) | **4** | 아래 표 |
| 둘째 | `957587c4`(7E-3b) | **0** | `results.spec.ts` 두 test 블록 생존 확인(2) · 내 소유 둘 무변경 · 생성 TS 재생성 바이트 등식 |

첫째 병합의 충돌 넷(파일별 무엇을 어떻게):

| 파일 | 충돌 | 해소 |
| --- | --- | --- |
| `AppHttpDependencyGateTest.kt` | 양쪽이 **다른 수**(main 76 · 내 80) | 숫자를 고르지 않고 **세 tier 목록의 항목 이름 합집합을 기계로** 셌다 → **81**(중복 0). 재계산 문단을 KDoc 에 넷째로 남김 |
| `AccountCarrierStructureTest.kt` | 양쪽이 서로 다른 controller 를 한 자리에 | **합집합** — `MyBidResultController` + `BidPriceRequestController` 둘 다 |
| `ConstantTimeComparisonStructureTest.kt` | 양쪽이 같은 50 줄 한도에 걸려 **각자 상수를 뽑았다**(이름이 다름) | 내 중복 상수 덩어리를 KDoc 째 지우고 main 의 이름으로 통일, 내 고유 키 하나를 그 전수에 더함. main 의 +4 항목 생존을 센 뒤 키 등식 test 초록 확인 |
| `capability-map.md` | SET-01 절을 양쪽이 다시 씀 | main 의 절을 본문으로 두고 **내 문단을 그 뒤에** — 7C⑤-b 가 세운 사실 위에서 「알림의 낙찰은 되비춤이지 대사가 아니다」를 말하도록 고쳐 씀 |

**자동 병합(충돌 0)이 접지 않았는지 따로 셌다**: properties 두 파일의 **중복 키 0**(줄 이음을 펴서
기계 산출) · OpenAPI 의 얕은 중복 키 없음 · `HttpTestApplication` 에 양쪽 빈 생존 · 생성 TS 는
재생성해 **바이트 등식**.

## §9 동결

판정 대상 SHA 와 마지막 실측은 동결 보고에 적는다(evidence 편집 커밋은 언제나 그 뒤에 온다 —
마지막 HEAD 의 결과 정본은 verifier 와 PR 조치 코멘트다).
