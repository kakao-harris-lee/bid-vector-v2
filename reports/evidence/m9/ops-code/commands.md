# M9/ops-code commands

base `df4976cc` · **병합한 main `22c77cbb`**(복원 원천 — 앞서 `3ad4f3f6`·`97ed49c4`·`56bd33ea`·
`bd2f450e`·`07140729` 도 병합했고 다섯 다 이 SHA 의 조상이다) · 판정 SHA **`df25a491`**(마지막
산출물 커밋). 레인 규율대로 push 하지 않았다.

로컬에서 돌린 것은 **CI Kotlin job 의 명령 그대로** `./gradlew --no-daemon check` **전건**(§2)과
변이(§4)다. 앞 판들은 `:app:check` + 나머지 모듈 표적으로 줄였는데, 병합이 `workflow` 의
**production 한 줄**을 강제했으므로 줄이지 않았다 — 줄일 수 있는 단위는 job 이다. 그 뒤 cr r7 수정
라운드는 **test·KDoc 만** 바꿔 production 거동이 0 이라 전건을 CI 에 맡기고 `:workflow:check` 로
재실측했다(§2a). Gradle 은 전부 `~/.local/bin/bv-build.sh ./gradlew --no-daemon …`(빌드 자리 둘 ·
획득 순간 메모리 재확인)로 돌렸고 레인이 남긴 java 프로세스는 0 이다(종료 뒤 `pgrep` 로 확인).

## 1. 커밋

| SHA | 무엇 |
| --- | --- |
| `c724e1ee` | slice 계약 |
| `7532008c` | OPS-09 — 상태 코드 축의 재시도 처분 |
| `0d172b1b` | OPS-08 — 연속 호출 사이의 최소 간격 |
| `d5f8c0b6` | NOTI-01 — 알림함 생산자(커널·포트) |
| `8aa35d79` | evidence 1차 |
| `b54bf047` | 계약 갱신(팀장 승인 — in_scope 일곱 경로, 기각한 대안 둘) |
| `0569e407` | NOTI-01 production 배선(실 DB 측정 포함) |
| `fe17d7d6` | 측정 상태 장부 — acceptance-trace 세 행 |
| `dc71ce34` · `5758e303` | evidence 2차 · rollback 유효성 술어 |
| `637dcc20` | 계약 갱신 2차(팀장) |
| `60c020af` | v B-1 — 절단 사유 전수 목록에 `AccessBlocked` |
| `98b4ed7a` | cr HIGH · v M-1 — 개찰 걷기도 차단에 멈춘다(소진 `when` 한 자리) |
| `258f5486` | v H-1 · cr MEDIUM — 출하 정책 값이 wire 에 닿는 것을 잠그는 test |
| `1429e934` | cr MEDIUM · v M-2 — 청중 조회 실패가 평가 run 을 멈추지 않는다(+ v L-1 ① · 질의 계수) |
| `db7a3909` | cr low 넷 + 낡은 주석 둘 |
| `e7fd0f3d` | cr MEDIUM · v L-2 — capability-map 세 행 · 장부 문면 정정 |
| `766d98c9` | main `3ad4f3f6` 병합 |
| `ae248e3f` | 게이트·lint 실측 셋(전송 표면 게이트가 `java.util.concurrent` 를 거부) |
| `2d1db76d` | evidence 3차(판정 SHA `ae248e3f`) |
| `ce4279ed` | cr r2 MEDIUM 둘 — 걷기 처분이 값을 나르는 sealed · 모집단 클래스 집합 등식 |
| `187ea814` | cr r2 MEDIUM · v r2 L-1 — 청중 장애가 예외 클래스 이름을 나르고 그 표지를 test 가 든다 |
| `cf629235` | v r2 M-1 1차 — 배선 운반 단계 단언(벽시계) |
| `ec02a75a` | cr r2 low 셋 + v r2 L-2 — 과수면 · 은퇴한 엔트리 · 낡은 문면 넷 |
| `e54c0d7a` | 장부 OPS-08 — 운반 단계를 재는 자리 |
| `e7912022` | v r2 M-1 정정 — 벽시계 단언이 계수 축과 섞여 **MW1 을 통과했다**(실측), 두 축으로 가름 |
| `cfcf315f` | evidence 4차(판정 SHA `e7912022`) |
| `908b197a` | main `97ed49c4`(#134) 병합 — 겹침 둘, 충돌 해소 줄 0 |
| `9286765d` | evidence 5차(판정 SHA `908b197a`) |
| `3ad0d342` | v r3 M-1 — production 이 넘기는 기다림 함수의 두 고리(거동 · 상수 풀 형태) |
| `d3aad2aa` | evidence 6차(판정 SHA `3ad0d342`) |
| `0817df3b` | main `56bd33ea`(#137 paper-bid V25) 병합 — 투찰 종류 축을 `NONE` 으로 싣는다(컴파일이 강제) |
| `702b7346` | evidence 7차(판정 SHA `0817df3b`) |
| `f577e69d` | v r4 B-1 — 간격 축을 `CollectionWiringSleeperTest` 로 가르고(sizeGate) · L-1 — **쓰이는 전송**으로 잰다 |
| `2294beb7` | evidence 8차(판정 SHA `f577e69d`) |
| `704554b4` | v r5 H-1 — 자리 전수를 상수 풀에서 도출해 **자리마다** 간격을 잰다(개찰 자리가 측정 밖이었다) |
| `6b5b52bc` | main `bd2f450e`(#142 CVE) 병합 — 겹침 **0**(계수로 확인) |
| `f2c64019` | v r6 M-1 · cr r6 L-E — census 의 단위는 **클래스**다(문면을 측정 범위로 좁히고 전제를 음성 대조로 잠근다) |
| `eab9e196` | evidence 9차(판정 SHA `6b5b52bc`) — 장부·rollback 수·원천 정정 |
| `1d53dd6e` | main `07140729`(PR #128 낙찰 수집 · #139 ml-lifecycle) 병합 — **깨진 셋**(§4d) |
| `eaf846a2` | 병합이 깬 둘 — 낙찰 갈래 소진 `when`(컴파일 실패) · 그 전수 표의 빠진 행 |
| `63d74653` | 병합이 깬 셋째 — census 집합 대조가 가리킨 **낙찰 전송 자리**에 탐침(거동까지 잰다) |
| `9dbb1fec` · `d5c0ba01` · `8654e95a` | 계약 갱신 3차 · rollback 재실측 · evidence 10차(판정 SHA `63d74653`) |
| `0bff48ff` | cr r7 M-1·M-2·L-3 — 멈춤을 「멈추는가」가 아니라 **무엇이라 적는가**로 잠근다 · 「둘뿐」 문면 넷 |
| `82564c72` | `sizeGate` 가 거부한 502 줄을 문면 합쳐 497 로(분할하지 않는다 — 같은 관심사다) |
| `4578c4fd` | evidence 11차(판정 SHA `82564c72`) |
| `e2a8fafc` | main `22c77cbb`(#141 ops-migrations) 병합 — 충돌 여섯, 전부 양쪽 보존(§4e) |
| `a8ccb0b9` | 병합이 깬 첫째 — 충돌 집합 **밖**의 실 DB test 가 `release` 를 넘긴다(컴파일 강제) |
| `df25a491` | 병합이 깬 둘째 — 합집합이 넘긴 508 줄을 **내 문면만** 줄여 500 으로 |

## 2. acceptance 실행 — CI Kotlin job 의 명령 그대로

`./gradlew --no-daemon check` **전건**이다(`.github/workflows/ci.yml` 의 `check` job 이 그 명령을
그대로 돈다). **앞 판들이 줄였던 자리를 job 단위로 되돌렸다** — 줄일 수 있는 단위가 job 이고, 이
라운드는 `workflow` 의 **production 한 줄**을 바꿨으므로 `:app:check` + 표적으로 가를 자리가 아니다.

| 측정 | 값 |
| --- | --- |
| 결과 | **BUILD SUCCESSFUL · 34m 47s · exit 0** |
| task | 401 actionable(256 executed · 99 from cache · 46 up-to-date) · `FAILED` 줄 **0** |
| test(모듈별 XML 집계) | shared-kernel 8/92 · procurement 37/317 · workflow 85/762 · adapters 207/1402 · app 131/1074 — **합 468 클래스 3647 test · 실패 0 · 오류 0** |
| skipped | **4**, 전부 `adapters.contract.RealServerIntegrationTest`(실 외부 서버 전제, 이 slice 와 무관한 기존 조건부) |
| 함께 돈 게이트 | `contractGate`(buf 1.72.0 — 정책 파일의 핀과 같다) · `leakPatternGate` · `memberEffectGate` · `conventionCoverageGate` 각 1 · `sizeGate`·`gateRegistrationGate`·`gateExecutionGate` 각 10(모듈마다) · `domainApiTypeGate`·`domainSourceReferenceGate`·`moduleDependencyGate` · `compatibilitySmoke` 2 · detekt 11 · ktlint(main·test·script) 전 모듈 |

**실 DB 와 container 여정이 그 안에 있다** — `adapters` 의 알림함·평가 경로와 `app` 의 수집 E2E 가
같은 실행에서 돌았다. 앞 판이 「PR CI 가 정본」으로 미룬 `:workflow:check`·`:adapters:check`·
`:procurement:check` 도 이번에는 **여기서** 돌았다. acceptance 를 지는 class 들의 XML(전부 skipped 0 ·
failures 0 · errors 0):

`EvaluationCommitInboxProductionTest` 3 · `NotificationInboxBidKindTest` 4 ·
`KonepsShippedPolicyWireTest` 6 · `CollectionWiringSleeperTest` 3 · `CollectAwardListUseCaseTest` 9 ·
`OpeningAccessBlockedHaltTest` 5. `app` 의 E2E 여덟(`AwardCollectionE2ETest`·`CollectionRunnerE2ETest`·
`OpeningCollectionE2ETest` 등)도 같은 실행에 있다.

**`qualityBaseline` 은 돌리지 않았다** — CI 가 그것을 게이트가 아니라 측정으로 두고 실패시키지
않는다(워크플로 주석 「게이트가 아니라 측정이라 실패시키지 않는다」). 그 자리는 PR CI 가 정본이고
명령이 같다.

**실행 트리는 `9dbb1fec`** 다(cr r7 LOW-2) — 그 시점의 산출물 커밋 `63d74653` 에 계약 갱신 하나를
얹은 HEAD 이고, 둘의 차이는 `scope.md` 한 파일뿐이라 Kotlin 빌드 입력(소스·설정·정책)은 산출물
커밋의 것과 같다. evidence 는 **빌드 입력이 맞으므로**(누출 게이트가 `reports/evidence` 를 스캔한다)
이 트리로 돈 것이 좁은 쪽이 아니라 **넓은 쪽**이다.

## 2a. cr r7 수정 라운드 — `:workflow:check`

그 라운드는 test 단언·KDoc 만 바꿨고 **production 거동이 0** 이라(§4 의 MABK/MABS 는 측정용 변이,
산출물 아님) 전건 `check` 는 PR CI 에 맡기고 영향 모듈을 로컬로 쟀다.

| 명령 | 결과 |
| --- | --- |
| `:workflow:test --tests '*CollectAwardListUseCaseTest*' --rerun` | `tests=9 failures=0` |
| **`:workflow:check`**(1차) | **BUILD FAILED** — `sizeGate`, 파일 500 줄 한도 초과 1건(`CollectAwardListUseCaseTest.kt` **502 줄**) |
| **`:workflow:check`**(문면 합친 뒤) | **BUILD SUCCESSFUL · exit 0 · `FAILED` 줄 0** · workflow 전건 **85 클래스 762 test 실패 0** · `sizeGate`·`detekt`·ktlint(main·test·script)·`cpdCheck`·`typeShapeGate`·`testShapeGate`·`moduleDependencyGate`·`domainApiTypeGate` 포함 |

**그 거부가 측정이다.** 파일이 이미 480 줄이었고 KDoc 둘이 한도를 넘겼다 — 가르지 않고 겹치던 문단
둘을 합쳤다(같은 `haltOf` 처분의 같은 축이라 분할은 크기 회피가 된다). **남은 여유는 세 줄**이고
알려진 제한에 등재했다.

## 2b. 여섯째 병합 뒤 — 전건 `check` 재실측 (판정 SHA `df25a491`)

병합이 production 을 섞으므로 부분 게이트로 줄이지 않았다. **두 번 돌렸다** — 첫 실행이 `sizeGate`
에서 붉었고(§4e) 문면을 줄인 뒤 다시 돌렸다.

| 측정 | 값 |
| --- | --- |
| 1차 (`e2a8fafc`+`a8ccb0b9`) | **BUILD FAILED · 36m 30s** — `:app:sizeGate` 하나(`EvaluationCommitRunE2ETest.kt` 508 줄). 그 전에 `:adapters:test`·`:app:test` 는 이미 돌아 초록이었다 |
| 2차 (`df25a491`) | **BUILD SUCCESSFUL · 34m 28s · exit 0** · 392 actionable(127 executed) · `FAILED` 줄 **0** |
| test(모듈별 XML 집계) | shared-kernel 8/92 · procurement 37/317 · workflow 85/761 · adapters 209/1423 · app 133/1080 — **합 472 클래스 3673 test · 실패 0 · 오류 0** |
| skipped | **4**, 앞 라운드와 같은 `adapters.contract.RealServerIntegrationTest` |

**계수가 움직인 자리를 전부 귀속했다**(초록인데 조용한 것을 막는다). adapters +2 클래스·app +2
클래스는 #141 의 새 test 넷이고 전부 실행됐다(`MigrationLockBoundTest` 1 · `ReleaseWiringTest` 5 ·
`CheckConstraintRenderingFixpointTest` 1 · `ExecutionRowReleaseTest` 9, 실패 0). workflow 가
**762 → 761** 로 **하나 줄어든 것도 #141 의 것**이다 — 그 slice 가 `OutboxTransitionTableTest` 의
`@Test` 를 6 → 5 로 줄였고(원천에서 확인) 병합 결과도 5 다. 내 쪽에서 사라진 test 는 없다.

## 3. 실 Spring 컨텍스트 — OPS-08 의 비용(앞 라운드 실측, 재측정하지 않았다)

| test | 간격 1초 | 통제군(간격 0) |
| --- | --- | --- |
| `CollectionRunnerE2ETest` | 61.9s · 실패 0 | 41.0s · 실패 0 |
| `OpeningPageSizeE2ETest` | 92.0s · 실패 0 | 87.1s · 실패 0 |
| `OpeningCollectionE2ETest` | 97.4s · 실패 0 | — |
| `SnapshotExtractionE2ETest` | 63.6s · 실패 0 | — |
| `CollectionWiringTest` · `OpeningCollectionWiringTest` | 0.4s · 0.2s · 실패 0 | — |

**+40~60s 를 PR CI 에 더한다**(네 E2E 클래스 합산 추정, 측정된 두 클래스의 +26s 를 근거로).
팀장 승인을 받았고 알려진 제한에 등재했다. 측정된 +20.9s·+4.9s 는 **간격이 프로세스 전역**이라는
성질의 값이기도 하다 — 운영 쪽 영향은 checklist 알려진 제한 ⑩ 이 든다.

## 4. 변이 — 바꿔치우기, 커밋 뒤, 변이 파일만 원복

묶음마다 변이들이 **서로 다른 test 클래스**를 겨눈다 — 같은 클래스를 겨누는 둘은 서로의 RED 를
가릴 수 있어 나눴다(간격 `ZERO` 와 「간격을 상한 앞으로」가 그 짝이다).

| 변이 | RED(클래스 · 메서드 수) | 같은 run 에서 초록으로 남은 통제군 |
| --- | --- | --- |
| 개찰 처분 표에서 차단을 「계속」으로 | `OpeningAccessBlockedHaltTest` 4/5 | `CollectNoticesUseCaseTest` 17/17(공고 갈래는 자기 표를 읽는다) · `CollectionWiringTest` 21/21 |
| **출하** 표에서 403 행 제거 | `KonepsShippedPolicyWireTest` 2/5 | `KonepsStatusDispositionTest` 6/6 — 좁힌 test 정책은 상수 표를 그대로 쓰므로 **출하 값을 보는 자리만** 붉어진다 |
| **출하** `minimumCallInterval = ZERO` | `KonepsShippedPolicyWireTest` 2/5 | `KonepsCallPacerTest` — 자기 값으로 돌아 출하 값을 재지 않는다(v H-1 이 지목한 빈 자리) |
| **배선**이 pacer 에 `Duration.ZERO` 를 넘긴다 | `CollectionWiringTest` 1/21 | `KonepsShippedPolicyWireTest` 6/6 · `KonepsCallPacerTest` 8/8 — 표의 값과 관문의 기계는 그대로라 **운반 단계만** 붉어진다 |
| 배선의 기다림이 잠들지 않는다(식으로 바꿈) | `CollectionWiringTest` 1/21 | 같은 class 의 「넘기는 값」 단언은 **초록** — 두 축을 따로 재기 때문이다 |
| **기본값** 기다림 함수를 잠들지 않는 람다로(MW3) | `CollectionWiringSleeperTest` 1/3 — 자리 전수 거동 단언 | 같은 class 의 값·잠듦 단언 **초록** · `CollectionWiringTest` 19/19 |
| 공고 호출 자리가 **자기 sleeper 를 명시**(MW4) | 같은 단언 1/3 | 같은 class 의 나머지 둘 초록 · `CollectionWiringTest` 19/19 |
| 선언 파일 **밖**에 호출자를 하나 더(MW4c) | 자리 전수 대조 1/3 | 같은 run 에서 기존 게이트 둘(`AppHttpDependencyGateTest`·`CollectionArchitectureGateTest`)도 그 fixture 의 자격 타입 참조를 잡는다 |
| 5인자 호출을 **버리고** 선언 파일 안 헬퍼의 잠들지 않는 전송을 쓴다(MW4d) | 거동 단언 1/3 | **앞 판에서 이 변이가 초록이었다**(v r4 L-1) — 참조 형태를 세던 술어를 **쓰이는 인스턴스**로 바꾼 것이 이 RED 다 |
| **개찰** 호출 자리가 자기 sleeper 를 넘긴다(MWO) | 거동 단언 1/3 — 실패 문면이 그 자리를 짚는다(`wiring/OpeningCollectionWiring · 출하 간격 PT1S · 63ms`) | `CollectionWiringTest` 19/19 · `OpeningCollectionWiringTest` 11/11 · 같은 class 의 값·잠듦 단언 초록. **앞 판에서 이 변이가 초록이었다**(v r5 H-1) — 거동을 한 자리에서만 재던 틈이다 |
| pacer 가 다음 하한을 기다리기 **전** 시각에서 센다 | `KonepsCallPacerTest` 1/8 | `KonepsCallGateTest` 3/3 · 같은 class 의 나머지 7 — 늦음이 **한 번뿐**인 판에서만 갈린다 |
| 청중 장애를 빈 목록으로 접기 | `NotificationAudienceFailureTest` 2/2 | — |
| 청중 실패에서 다시 던지기 | `InboxRecordingNotificationsTest` 2/9 | `CollectNoticesUseCaseTest` 17/17 |
| 청중 표지 로그 제거(`debug("")`) | `NotificationAudienceMarkerTest` 1/1 | `CollectionWiringTest` 21/21 |
| 표지에서 **예외 클래스 이름** 제거 | `NotificationAudienceMarkerTest` 1/1 | `OpeningAccessBlockedHaltTest`·`InboxRecordingNotificationsTest` 의 같은 run 다른 변이와 분리 계수 |
| 알림함 기록을 채널 뒤로 | `InboxRecordingNotificationsTest` 1/9 | 결말 단언 둘(채널 실패에도 기록 1건 · 기록 실패에도 채널 1회)이 **초록으로 남는다** — v L-1 ① 이 지적한 그 사실 |
| `awaitTurn` 을 상한 검사 앞으로 | `KonepsShippedPolicyWireTest` 1/5 | `KonepsCallGateTest` 3/3 · `KonepsCallPacerTest` 7/7 |
| 사유 모집단에서 차단을 빼기(test 대역) | `OpeningAccessBlockedHaltTest` 1/5 | 나머지 4 — **모집단 등식이 비는 쪽도 붉어진다**는 대리 측정이다(새 `data class` 사유를 지어낼 수는 없다) |
| **낙찰** 호출 자리가 자기 sleeper 를 넘긴다(MWA) | `CollectionWiringSleeperTest` 1/3 — 거동 단언, 문면이 그 자리를 짚는다(`wiring/AwardCollectionWiring · 출하 간격 PT1S · 46.5ms`) | 같은 class 의 값·잠듦 단언 초록. **셋째 탐침이 키만 더한 손 불변식이 아님을 잰다**(cr r6 M-2 ⓑ) — 그 키를 기존 탐침에 묶었다면 이 변이가 초록이었다 |
| mock 봉투가 받은 항목보다 큰 `totalCount` 를 선언한다(M3NEG2 — 첫 소스가 관문을 **두 번** 지난다) | 같은 class 2/3 — **음성 대조가 먼저** 붉는다(`wiring/CollectionWiring · 음성 대조 — 첫 소스의 호출 2.07s 는 출하 간격 PT1S 미만`) | 같은 batch 의 CTRL(주석 한 줄) 3/3 초록 · 되돌린 뒤 porcelain 0 |
| 병합이 깨운 낙찰 처분을 **반대로**(차단이 계속 걷는 쪽으로) | `CollectAwardListUseCaseTest` 1/9 — 전수 표 | 나머지 8 초록 — 멈춤 집합만 갈린다 |
| 차단을 **엉뚱한 칸에** 적는다 — `AccessBlocked -> AwardCollectionHalt(BudgetLimit.DAILY, null)`(MABK) | 같은 class 1/9 — 전수 표 | CTRL3(주석 한 줄) 9/9 초록. **구판에서는 이 변이가 초록이었다**(아래 음성 대조) |
| 멈춘 뒤 **남은 슬롯 수**를 0 으로 적는다 — `notAttemptedSlots = 0`(MABS) | 같은 class **2/9** — 전수 표 + 예산 소진 test(`expected:<1> but was:<0>`) | 나머지 7 초록 — 손대지 않은 슬롯 축만 갈린다 |

세 축이 요점이다. ⓐ 출하 값을 겨눈 변이는 **좁힌 정책으로 도는 test 를 붉히지 않는다** — 그
비대칭이 「출하 값이 wire 에 닿는가」의 증거다. ⓑ 배선 변이 둘은 **서로 다른 단언**을 붉힌다(값과
기다림을 따로 재기 때문이다) — 한 단언으로 둘을 덮으려던 앞 판은 **MW1 을 통과했다**: 계수 축
(rate limiter 1 permit/1초)이 같은 크기의 지연을 만들어 벽시계에서 두 축이 섞였다(실측 1.088s,
초록). 그래서 그 단언을 지우고 둘로 갈랐다. ⓒ 순서·대기 변이는 앞 판 test 를 초록으로 두고 새
단언만 붉힌다.

**다섯째 병합 뒤 간격 축 변이를 전부 다시 돌렸다**(버릴 clone 에서 `63d74653` 체크아웃). 사유는
앞 배치의 **통제군이 붉었다**는 것이다 — 병합이 들여온 셋째 전송 자리 때문에 집합 대조가 먼저
멈춰 MWO·MWOd 가 거동 루프에 닿지도 못했다. 그 표는 버렸다. **FROM-CACHE 통제군은 실행이
아니므로** 전부 `--rerun` 이고, 결과 XML 의 mtime 과 test 수를 매 변이마다 적었다.

**음성 대조를 깨우려던 첫 변이는 거동이 0 이었다.** 101 행 fixture(`numOfRows` 100 초과)로 쪽
넘김을 일으키려 했으나 **mock 봉투가 `totalCount` 를 받은 항목 수로 적어** 걷기의 완료 술어
(`items + duplicate + dropped >= total`)가 첫 쪽에서 참이 된다 — 한 쪽만 받고 끝나 변이 전과 같은
거동이었고 test 는 초록이었다. **빌드가 0 인 것과 거동이 0 인 것은 다르다**: 그래서 봉투의
`totalCount` 자체를 받은 항목보다 크게 바꾸는 M3NEG2 로 바꿔치웠고, 그때 첫 호출이 2.07s 로
늘어나며 음성 대조가 먼저 붉어졌다(위 표). 거동이 **실제로** 바뀐 증거는 그 2.07s 와, 같은 run 에서
기다림 기록이 `[PT1S]` → `[PT1S, PT2S, PT3S]` 로 늘어난 것이다.

**cr r7 의 낙찰 변이 넷(CTRL3·MABK·MABS·MAB)은 `82564c72` 에서 쟀다.** 그 라운드는
`CollectAwardListUseCase.kt`·`CollectAwardListUseCaseTest.kt` 만 만졌고 app 배선·탐침 test 를
건드리지 않았으므로 간격 축 표(위 `63d74653` 실측)는 그대로 유효하다. 문면만 합친 `82564c72` 에서
다시 돌려 **앞 커밋에서 잰 결과와 같음**을 확인했다(주석 변경이 단언을 건드리지 않았다는 주장을
실행으로 바꿨다).

**여섯째 병합 뒤 변이를 다시 돌렸다**(`df25a491`, 버릴 clone). 병합이 두 수집 배선 파일을 만졌으므로
앞 실측(`82564c72`)이 더는 그 트리의 것이 아니다. 간격 축 넷(MWO·MWOd·MWA·M3NEG2)과 낙찰 축
셋(MABK·MABS·MAB)을 통제군 둘(`CTRL6`·`CTRL6w`, 주석 한 줄)과 함께 돌려 **판정이 앞 판과 같음**을
확인했다 — 통제군 3/3·9/9 초록, 일곱 전부 RED 이고 실패 문면도 같은 자리를 짚는다(개찰 49.5ms ·
낙찰 44.9ms · 집합 대조 `Some elements were missing` · 음성 대조 첫 호출 2.05s · 낙찰 전수 표).

**강해진 단언에는 음성 대조를 붙였다**(cr r7 M-1). 같은 변이 MABK 를 **구판**(`63d74653`, 기대값이
Boolean 이던 표)에 적용하면 `tests=9 failures=0` — **초록**이다. 같은 변이가 새 판에서 붉으므로,
이 라운드가 더한 것은 「없던 방어」이지 「있던 방어의 다른 표현」이 아니다. 리뷰어가 정적으로 지목한
자리를 실행으로 확인한 것이기도 하다.

**게이트 둘이 이 라운드의 코드를 거부했고 그 거부가 측정이다**: `javaClass.simpleName` 은 수집
리플렉션 게이트가(선례와 같은 자리에 쌍 하나 등재), 주입 인자가 생긴 `konepsTransportFor` 는 전송
표면 게이트(3층)의 **양방향 등식**이 낡은 서명으로 거부했다(서명 하나 갱신). 둘 다 열거를 넓히는
것이 아니라 관측과 맞추는 편집이고, 양방향이라 쓰이지 않는 등재도 붉어진다.

변이는 모두 커밋 **뒤에** 적용했고 원복은 변이 파일만 `git restore --source=HEAD --worktree --`
로 했다(미커밋 산출물을 지우는 `checkout --` 금지). 원복 뒤 작업 트리에 남은 변경이 evidence
뿐임을 `git status --porcelain` 으로 확인했다.

## 4a. 둘째 병합(main `97ed49c4`) — 겹침을 계수로 본다

충돌 해소 줄이 **0**(`git show --cc` 빈 출력)이라 자동 병합이고, 그래서 **합집합 함정을 계수로
쟀다**(충돌 0 이 「접히지 않았다」를 뜻하지 않는다).

| 겹친 경로 | 내 변경 / main 변경 | 병합 결과 |
| --- | --- | --- |
| `config/quality/gate-tests.properties` | +8 / +8 −2(전부 주석) | 등재 **530 = main 522 + 레인 8**, 즉 내 것 ∪ main(누락 0 · 초과 0 · 중복 0). main 이 지운 등재는 **없다**(집합 차 빈 것으로 확인) |
| `adapters/…/evaluation/EvaluationAdapterDependencyTest.kt` | +13 / +105 | `git diff <원천>..HEAD` 가 **내 13 줄만** 낸다(삭제 0) — main 의 105 줄이 그대로 남았고 내 줄이 그 위에 얹혔다 |

그 Kotlin 파일은 **전문을 읽었다**. main 이 더한 것은 `NoticeWatchSubjectPort` 축의 **필드 참조
허용 목록**과 그 양성·음성 대조이고, 내 줄은 **패키지 단위 허용 루트**(`workflow.notificationinbox`)와
그 대조 둘이다 — 두 축이 **다른 집합**이라 한쪽이 다른 쪽을 느슨하게 만들지 않는다. 병합 뒤 그
class 가 `tests=12 failures=0` 이다(앞 판보다 늘어난 수가 main 의 축이다).

`NoticeWatchSubjectPort.kt`(#134 가 바꾼 main 소스)와 persistence test 둘은 **내 경로가 아니다** —
겹침 목록이 그것을 계수로 말한다.

## 4b. 셋째 병합(main `56bd33ea`, #137 paper-bid V25) — 의미 축이 겹쳤다

**충돌 둘이 났고 양쪽을 보존해 풀었다**(앞 두 병합은 자동이었다).

| 겹친 경로 | 내 변경 / main 변경 | 해소 |
| --- | --- | --- |
| `config/quality/gate-tests.properties` | +8 / +3 | 두 쪽이 **같은 사전순 자리**에 넣어 충돌 — 합집합으로. **등재 집합 등식이 참이다**(누락 0 · 초과 0 · 중복 0). 수를 적을 때의 계수법은 `grep -oE '\bbidvector\.[A-Za-z0-9_.]+Test\b' … | sort -u | wc -l`(고유 FQN) 하나로 고정한다(v r4 L-2 — 앞 두 라운드가 다른 계수법으로 523·530 을 적었다): main 519 ∪ 레인 527 = **528**(이 라운드의 새 class 포함) |
| `app/…/wiring/NotificationInboxWiring.kt` | +38 / +8 −3 | main 의 표지 줄(`bidKind=…`)과 이 레인의 청중 표지 함수가 **같은 꼬리**에 붙어 충돌 — 둘 다 남겼다 |
| `workflow/…/notificationinbox/NotificationInboxPorts.kt` | +51 / +22 −8 | 자동 병합. 두 쪽 모두 보존(줄 수 189 = main 그대로 + 내 청중 타입) |
| `config/quality/architecture-policy.properties` | +2 −1 / +2 | 자동 병합. 키 139 개를 합집합과 대조해 **불일치 1** — 그 하나는 내 쪽이 **치환**한 전송 서명이고(낡은 서명이 사라지는 것이 맞다) 양방향 등식 게이트가 초록으로 그것을 확인한다 |
| `docs/discovery/capability-map.md` | +3 −3 / +5 −4 | 자동 병합. 줄 수 3972 = main 그대로(내 편집은 줄 수 불변 치환) |

**의미 축**: #137 이 알림함 키에 투찰 종류(`NotificationBidKind`, 기본값 없음)를 더했고, 계약의
「나중에 머지되는 쪽이 맞춘다」가 이 레인이다. **컴파일이 예고한 한 자리에서 멈췄다** — 생산자의
record 생성 한 줄(「actual type is NoticeId」). 새 후보는 건이 생기기 **전**의 소식이라 축이 없고,
#137 의 종류 전수 표가 그 자리를 `NONE` 으로 정한다(`NEW_CANDIDATE` → `PER_NOTICE`).

**두 층에서 단언한다**: 커널 test 가 기록된 묶음의 축을, **실 DB test 가 되읽은 `bid_kind` 열**을
본다. 변이 `NONE` → `REAL` 은 커널 `require` 가 먼저 막아 생산자를 지나는 test 전부를 붉힌다
(단위 6/9 · 실 DB 2/3) — 통제군으로 #137 자신의 `NotificationInboxBidKindTest` 4/4 가 초록이다
(그 test 는 이 생산자를 지나지 않는다). 저장 층 짝 CHECK 축은 #137 의
`NotificationInboxPairConstraintTest` 5/5 가 든다.

## 4c. 넷째 병합(main `bd2f450e`, #142 CVE) — 겹침 0

`git diff --name-only 56bd33ea bd2f450e` 는 **8 경로**(의존 고정·docker·그 slice 의 evidence)이고
내 산출물 경로와의 교집합이 **0** 이다. 자동 병합이고 충돌 0 이다. 병합 뒤
`git diff --name-only bd2f450e HEAD -- config/quality/vuln-allowlist.properties docker` 가 **빈
출력**이라 그 slice 의 줄이 그대로 남아 있다(내 쪽 diff 는 내 산출물 46 경로뿐이다 — **그때의
계수다**, 다섯째 병합 뒤 원천 `07140729` 기준으로는 48 이다).

## 4d. 다섯째 병합(main `07140729`, #128 낙찰 수집 · #139 ml-lifecycle) — 깨진 셋

`git diff --name-only bd2f450e 07140729` 는 **150 경로**이고 내 산출물과의 교집합이 **여섯**이다.
충돌이 난 자리는 둘(`git show --cc` 가 `architecture-policy.properties`·`gate-tests.properties`
두 파일만 낸다)이고 양쪽을 보존해 풀었다. **깨진 것은 셋이고 전부 `AccessBlocked` × 새 낙찰
갈래의 교차**다 — 셋 다 사람이 아니라 타입·게이트가 가리켰다.

| 깨진 자리 | 무엇이 가리켰나 | 고친 커밋 |
| --- | --- | --- |
| `CollectAwardListUseCase.haltOf` | **컴파일**(`'when' expression must be exhaustive`) — main 이 세운 소진 `when` 에 이 slice 의 새 사유가 들어왔다 | `eaf846a2` |
| `CollectAwardListUseCaseTest` 전수 표 | 모수를 sealed 계층에서 뽑으므로 새 사유가 병합과 **동시에** 표에 들어왔고 기대 행이 없었다 | 같은 커밋 |
| `CollectionWiringSleeperTest` 집합 대조 | `Some elements were unexpected: ["wiring/AwardCollectionWiring"]` — main 의 낙찰 배선이 `konepsTransportFor` 를 부르는데 탐침표에 없었다 | `63d74653` |

**셋째가 이 라운드의 수확이다.** 그 test 의 KDoc 이 「셋째 클래스가 전송을 만들면 거동을 재지 않은
채 지나갈 수 없다 — 집합 대조가 먼저 붉어진다」를 **주장**했고 병합이 그 주장을 **실측**으로 바꿨다.
탐침은 키만 더하지 않고 그 클래스의 production 조립 메서드로 거동까지 재며, 변이 MWA 가 그 결속을
잰다(§4).

**합집합을 계수로 확인했다**(충돌 0 이 「접히지 않았다」를 뜻하지 않고, 충돌 해소도 마찬가지다).

| 겹친 경로 | 내 변경(원천 대비) | 합집합 확인 |
| --- | --- | --- |
| `config/quality/gate-tests.properties` | +9 −0 | 고유 FQN **548 = main 539 ∪ 레인 528**(누락 0 · 초과 0). 중복 FQN 넷은 **원천·레인 양쪽에 이미 있던 같은 집합**이라 병합이 만든 것이 아니다 |
| `config/quality/architecture-policy.properties` | +2 −1 | 키 **140 = 합집합 141 − 하나**. 그 하나는 `collection.usecase.type` 이고 **main 이 `a6e51b70` 에서 지운 키**다(내 slice 는 그 키를 만지지 않았다 — 앞 원천 대비 diff 빈 출력). 지워진 채 남는 것이 맞다 |
| `docs/discovery/capability-map.md` | +3 −3 | 줄 수 **4032 → 4032**(내 편집은 줄 수 불변 치환) |
| `app/…/evaluation/EvaluationCommitRunE2ETest.kt` | +15 −0 | main 의 482 줄이 **삭제 0** 으로 남고 내 15 줄이 얹혔다 |
| `workflow/…/collection/CollectAwardListUseCase.kt` | +8 −4 | main 이 만든 파일 — 낡은 예고 KDoc 문단을 깨어난 사실로 바꾸고 분기 한 줄을 더했다 |
| `workflow/…/collection/CollectAwardListUseCaseTest.kt` | +9 −4 | 같은 자리의 전수 표 한 행 + 그 주장의 문면 |

## 4e. 여섯째 병합(main `22c77cbb`, #141 ops-migrations) — 충돌 여섯, 깨진 둘

`git diff --name-only 07140729 22c77cbb` 는 **107 경로**이고 내 산출물과의 교집합이 **아홉**,
충돌은 **여섯 파일 일곱 자리**다. main 은 `ReleaseIdentifier` 를 행 쓰는 조립에 꿰었고 이 slice 는
같은 생성자·배선에 알림함·청중 포트를 더했다 — **둘 다 더하는 변경이라 합집합이 곧 해소**이고
버린 거동이 없다.

| 손 해소 자리 | 무엇을 했나 |
| --- | --- |
| `EvaluationCommitRun.kt` ② | 생성자는 내 포트 둘 **뒤에** `release` 를 두고, 내 `InboxRecordingNotifications` 가 감싸는 **안쪽** `JdbcOutboxPort` 에 `release` 를 넘긴다. 한쪽 해소를 그대로 쓰면 알림함 감싸기가 사라지거나 릴리스가 행에 안 실린다 |
| `EvaluationCommitWiring.kt` ② | 빈 메서드 인자·생성자 호출 인자 각각 합집합 |
| `DryRunCommitParityTest`·`EvaluationCommitRunnerTest`·`EvaluationCommitRunE2ETest` 각 ① | 내 「불리면 터지는」 대역·실 어댑터와 main 의 `release` 를 나란히 |
| `gate-tests.properties` ① | 두 쪽이 **같은 사전순 자리**에 넣어 충돌 — 합집합으로 두고 사전순 유지 |

**합집합을 계수로 확인했다**(충돌 0 자동 병합 자리도 함께).

| 축 | 확인 |
| --- | --- |
| `gate-tests.properties` | 고유 FQN **552 = main 543 ∪ 레인 548**(누락 0 · 초과 0) · 중복 넷은 양쪽에 이미 있던 같은 집합 · 행 연속 `\` 온전 |
| `architecture-policy.properties` | 항목 **1327 = 합집합 1328 − 하나**. 그 하나는 `konepsTransportFor` 의 **옛 5인자 서명**이고 이 slice 가 치환한 것이다(양방향 등식 게이트라 남으면 오히려 붉다). main 의 `ReleaseWiring` tier 항목 생존(tier 41 = 레인 40 + 하나) |
| `docs/discovery/capability-map.md` | **4058** — main 의 +26 과 내 줄 수 불변 치환 +3/−3 이 둘 다 남았다 |
| `CollectionWiringTest.kt`(자동 병합) | **412 = main 411 + 내 import 1** · main 의 여덟 줄 전수 생존 |

**탐침·census 는 흔들리지 않았다** — main 의 `release` 는 세 수집 배선의 **저장소 빈 메서드**로
갔고 내 탐침이 부르는 `*Sources` 조립 메서드 서명은 그대로다. `konepsTransportFor` 호출자 클래스도
셋 그대로라 `CALL_SITE_PROBES` 집합 대조가 그대로 성립한다.

**깨진 것 둘 — 둘 다 충돌 밖에서 게이트·타입이 가리켰다.**

| 깨진 자리 | 무엇이 가리켰나 | 고친 커밋 |
| --- | --- | --- |
| `EvaluationCommitInboxProductionTest` | **컴파일**(`No value passed for parameter 'release'`). 이 파일은 이 브랜치에만 있는 **넷째 호출자**라 병합이 볼 수 없었다 — main 은 자기 쪽 호출자 셋만 고쳤다 | `a8ccb0b9` |
| `EvaluationCommitRunE2ETest.kt` | **`:app:sizeGate`** 508 줄(한도 500). 기준 482 + main 11 + 내 15 — 해소가 틀린 것이 아니라 **두 slice 의 덧셈**이 넘겼다 | `df25a491` |

둘째는 **남의 줄을 건드리지 않고** 내 문면만 줄였다(KDoc 8 → 2 · 인라인 주석 3 → 0, 코드 네 줄과
전제 한 문장은 유지). 결과가 **정확히 500 줄**이고 게이트 술어가 `> limit` 이라 통과하지만 여유가
0 이다 — **다음에 이 파일을 만지는 병합이 깬다**. 가르는 것이 구조적 답이나 그것은 새 test class 와
`gate-tests.properties` 등재를 낳아 이 라운드 범위 밖이고, 팀장 결정(2026-11-11)으로 지금처럼 두고
후속 slice 로 넘긴다(알려진 제한 ⑯). 열린 PR 둘 가운데 이 파일을 만지는 것은 없다 — #138 의 겹침
목록에 이 경로가 없고 #127 은 이미 머지됐다(실측).

## 5. clean-tree 게이트

`git status --porcelain -- <복원 목록 경로 개별 인자>`(**인자 44**, 기계 산출 목록 그대로) **빈
출력**. 양성 대조 1회 — `config/quality/gate-tests.properties` 에 개행 하나를 붙이면 그 파일 한 줄이
나오고 `truncate -s <원래 바이트>` 로 되돌리면 다시 빈 출력이다(비파괴 절삭, `checkout --` 를 쓰지
않았다 — 미커밋 산출물을 함께 지운다). 되돌린 뒤 남은 porcelain 은 이 디렉터리의 evidence 뿐이고,
그 파일들은 복원 목록 **밖**이라 §4 의 rollback 유효성 술어를 움직이지 않는다.

## 6. 비밀값 스캔

`grep -rniE -f config/quality/leak-patterns.txt` 축은 CI 의 누출 게이트가 정본이다(이 slice 는 그
어휘를 evidence 에 축어로 적지 않는다). `leakPatternGate` 는 **§2 의 전건 `check` 안에서 돌았고**
(task 1회) 그 빌드가 exit 0 이다 — 그 실행의 트리는 `9dbb1fec`(계약 갱신까지 담은 HEAD)이라 이
디렉터리의 evidence 도 스캔 입력에 들어 있었다. 뒤따르는 evidence 커밋은 같은 제외 디렉터리만
만지고, **마지막 HEAD 의 재실측 정본은 verifier 와 PR 조치 코멘트**다(여기 넣으려는 시도가 커밋을
또 낳는다). 이 라운드가 더한 문자열에 외부 값이 없다 — 전송 경로는 상태 코드 숫자 하나를 싣고,
청중 표지는 **계수도 주체도 싣지 않는** 고정 문장 하나다.
