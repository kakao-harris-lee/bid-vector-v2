# M6/6G-2g — 게이트 술어 확장 slice: 6G-2b 가 남긴 게이트 OPEN 일곱 (계약 초안, 2026-10-04, 팀장)

- base: `31721008`(PR #59 머지 커밋 = `main`) · 브랜치 `m6-6g2g/2026-10-04` · worktree `bid-vector-v2-m6-6g2g`
- 정본: 이 파일. 결정 ID `D-6G2g-N`. 운영자 결정은 「운영자 승인」 절.
- 레인: K 하나(`kotlin-implementer`, sonnet). Python 없음. 판정은 `verifier`(opus) + `code-reviewer`(sonnet) 병렬.
- 성격: **게이트·계약형 slice** — Phase 2.5 설계 검토 필수((0) 경계 → (1) 열거/구성 → (2) 우회 ≥5 → (2b) 값 획득 축 → (3) 과잉·미달). **게이트 술어를 바꾸는 모든 커밋은 severity 무관 표적 재검증**(6G-2b 와 같다).
- 실수집과의 관계: test·정책 파일만 바꾼다(production diff 0 기대). 실행 상태 바이트·스냅숏 스키마에 닿지 않으므로 수집 중 머지 가능.

## 왜 이 slice 인가

6G-2b 가 전송 표면 게이트를 두 층으로 세우면서 **같은 모양의 간극이 형제 게이트에 남아 있음**을 실측했고(D-6G2b-20·23·35·36·39·44), 6G-2c 가 등재 덮개의 간극(D-6G2c-38 ④·42 ⑤)을 더했다. 전부 **술어 확장**이라 6G-2c 의 D-13(게이트를 넓히지 않는다)에 걸려 넘어왔다(운영자 결정 A-3 ②).

## 수령 OPEN 일곱과 항목

| OPEN | 출처 | 간극 | 이 slice 의 항목 |
|---|---|---|---|
| `OPEN-6G2B-COLLECTION-DEPTH` | D-6G2b-44 | 형제 정확 집합 게이트(`raw-access`·`key-hash`·`usecase`·domain 허용 목록)가 **소유 타입만** 수집 — 전송·반사만 깊다(인자·반환·호출 대상까지) | D-1 수집 깊이를 공통 수집기로 통일, 쌍 등식 재관측(늘어나는 쌍은 전부 등재·실측 — 6G-2b 의 14 선례) |
| `OPEN-6G2B-FOLDING-UNIFICATION` | D-6G2b-35 | `outermostClass()` 접기(전송·바깥 참조·반사) vs `enclosingClass` 접기(`key-hash`·`injection`) 가 다름 — 등재 `NoticeKeyHash$Companion`·`Resolution$Resolved` 가 반증 | D-2 접기 규칙 하나로 통일 + 영향받는 등재 재관측(재등재는 양방향 등식이 강제) |
| `OPEN-6G2B-REFLECTION-ROOT-DOMAIN` | D-6G2b-20·43 | 반사 쌍 등식의 뿌리가 `workflow`·`app`·`adapters` 까지 — domain 계열(`procurement`·`decision`·`qualification`·`settlement`·`shared-kernel`)은 순수성 게이트의 기본 거부에만 기댄다(6F-9 `procurement` 잔여 포함) | D-3 **착수 실측** 뒤 운영자 결정 B-1: domain 모듈의 기존 반사 참조 수를 재고 0 이면 뿌리 확장(등재 0), 아니면 쌍 등재 |
| `OPEN-6G2B-ALLOWED-PACKAGE-EGRESS` | D-6G2b-36·39 | 허용 패키지 **안**의 출구 — `java.io` 의 `/dev/tcp`·FIFO, `java.nio.file` 원격 FS, 라이브러리 자체 로더(`com.networknt.schema` 원격 스키마), StAX 외부 엔티티, Spring bean factory | D-4 **경계 유지 + 구조 한 수**: 낱개 열거를 늘리지 않고, 「파일 시스템 출구」는 경로 리터럴 게이트(`/dev/`·`file:` 스킴)가 아니라 **`Path`/`File` 생성 자리를 등재 보유자로 한정**하는 쌍 등식으로 — 설계 검토 (1) 이 열거인지 구성인지 판정한다. 라이브러리 로더·StAX·bean factory 는 위협 모델 「방어하지 않는 것」 유지(운영자 결정 B-2) |
| `OPEN-6G2B-HOLDER-INTERNAL-SURFACE` | D-6G2b-23, 6G-2c vr I-1 | 등재 보유자 안의 **String 시그니처 send** 류·internal 멤버를 반사로 부르는 길(`release$bid_vector_adapters`) — (클래스, 타입) 해상도 밖 | D-5 등재 보유자 public/internal 멤버 중 전송 타입을 받지 않는 send 류를 (클래스, 멤버) 층으로 세는 3층 — 보유자 **29**(쌍 65, 용도 다섯; 6G-2b 문면 「28」은 오기) 의 비-private 멤버 280 중 후보 **5**(착수 실측 D-6G2g-5) |
| `OPEN-6G-GATE-REGISTRY-KONEPS` | D-6G2b-15 | 게이트 등재 장부 전반(koneps 포함) — 쌍 등식이 갈음하지 못함 | D-6 **원 문장이 없다**(착수 실측: 이름의 첫 등장은 `576d92f7` 의 OPEN 표 한 행, 문면은 처분뿐) → **이 slice 가 정의를 세운다**: 「등재 장부 ↔ 소스 등식이 모듈·패키지마다 들쭉날쭉하다(adapters.koneps 등재 12 는 어떤 등식에도 걸리지 않고 그 패키지에 미등재 test 하나)」. D-7 과 한 묶음으로 닫는다 |
| `OPEN-6G2G-REGISTRATION-PACKAGE-COVER` | D-6G2c-38 ④·40 ③·42 ⑤ | adapters 등재 등식이 패키지 여섯만 덮고 여섯(contract·extraction·koneps·persistence·snapshot·strategy)은 안 덮음, 미등재 test 일곱 · `adapters/build.gradle.kts` 의 `tasks.test` 가 `gate-tests.properties` 를 입력으로 선언하지 않음(app 은 선언) | D-7 등재 등식을 **패키지 열거가 아니라 모듈 전수**(소스 트리에서 `@Test` 클래스 기계 수집 == 등재 집합)로 바꾸고, 세 모듈 test task 에 `gate-tests.properties` 입력 선언(6G-2c D-33 선례) + 「선언 없으면 UP-TO-DATE 초록」 변이 실측 |

## 위협 모델 경계 (Phase 2.5 (0)) — 초안

**방어하는 것**: 등재 밖 전송·반사·raw 접근·키 해시·use case 호출이 production 코드에 생기면 게이트가 붉는다(수집 깊이·접기·패키지 덮개의 간극으로 조용히 통과하는 길을 닫는다). **방어하지 않는 것**: 빌드 스크립트를 임의로 쓰는 저자(6G 경계) · 허용 패키지 안의 라이브러리 자체 로더·StAX·bean factory 출구(D-4) · 동적 import 류(6G-2c 경계).

## in_scope (r2 확정)

- `app/src/test/kotlin/bidvector/app/architecture/**`(게이트 술어) · `app/src/test/kotlin/bidvector/archfixture/**`(음성 fixture) · `adapters/src/test/kotlin/bidvector/adapters/**/…GateRegistrationTest.kt`(등재 등식) · `workflow/src/test/kotlin/bidvector/workflow/WorkflowGateRegistrationTest.kt`(D-2·6·7 가 고친다 — 초안 누락, 착수 실측 8-1) · 낡은 KDoc 참조 셋(**주석 한 줄씩** — D-6G2g-16) · `build-logic/src/main/kotlin/**`·`build-logic/src/test/kotlin/**`(등재 등식 task — B-5 (다)) · `app|adapters|workflow|shared-kernel|decision|procurement|qualification|strategy/build.gradle.kts`(**task 배선·입력 선언 블록만**) · `config/quality/member-effects*.properties` 는 **불필요**(B-4 (나)) · `config/quality/architecture-policy.properties` · `config/quality/gate-tests.properties` · `app|adapters|workflow/build.gradle.kts`(**test task 입력 선언 블록만**) · `reports/evidence/m6/6g2g/**` · `milestone-6.md`(착수·종결 문단만).
- **out_scope**: production 코드 전부(변경이 필요해지면 멈추고 계약 갱신) · `ml-engine/**` · 실행 상태·스냅숏 형식.

## acceptance

CI `check` job 명령 그대로(`./gradlew --no-daemon check` · `qualityBaseline`). production 무변경이면 container job 생략(6G-2e 선례, 사유 등재). 항목마다 변이 ≥1 RED(numstat 적용 확인), 게이트 술어 커밋은 verifier 표적 재검증.

## rollback

in_scope 경로 한정 `git restore --source=<base>`; 공유 파일(`architecture-policy.properties`·`gate-tests.properties`·`build.gradle.kts` 셋·`milestone-6.md`)은 커밋 해시 hunk — 목록은 실측 HEAD 에서 `git log` 로 낸다(6G-2c 교훈). ①~⑥ 버릴 clone.

## 운영자 승인 (2026-10-04 결정 완료 — 「추천대로 진행해」: B-1 (가) · B-2 (가) · B-3 (나) · B-4 (나) · B-5 (다), 정본은 D-6G2g-9)

- **B-1** 반사 뿌리를 domain 계열까지 넓힐지 — (가) 넓힌다 · (나) 순수성 게이트에 맡기고 OPEN 유지. 착수 실측: domain 여섯 모듈 반사 참조 **0**(소스·바이트코드 셋 다), 뿌리는 `layer.*` 도출이라 바꿀 자리는 상수가 아니라 도출 식 → (가)면 **등재 0**. **팀장 추천 (가)**.
- **B-2** 허용 패키지 안 출구 — (가) 파일 시스템 출구만 구조 한 수(D-4) · (나) 전부 경계 유지(코드 0, 문면만). 착수 실측: `java.nio.file` 은 전송 뿌리 열일곱에 **없다**(있는 것은 `java.nio.channels`) — 파일 시스템 출구는 오늘 쌍 층 밖이고 1층이 통째로 허용. `Path`/`File` 을 만드는 보유자 **10**(타입을 이름 붙이는 데까지 13), 그중 여섯은 이미 전송 등재 → 새 이름 네댓. **팀장 추천 (가)** — 뿌리에 `java.nio.file`·`java.io`(File) 를 더하고 쌍 등재; 라이브러리 로더·StAX·bean factory 는 경계 유지.
- **B-3** 등재 등식을 모듈 전수로 바꿀 때 **의도적 미등재**는 어떻게 표시할지 — (가) `@Test` 0 인 클래스 자동 제외 · (나) 제외도 등식의 한 변으로, 사유를 **build 스크립트의 사실**(`filter`·`@EnabledIfSystemProperty`)에서 기계로 읽어 맞댄다. 착수 실측: `*Test.kt` 인데 `@Test` 0 은 전 모듈 **0** — (가)는 오늘 아무것도 거르지 못하고, 실제 제외 대상(`CrossLangSmokeTest`·`RealServerIntegrationTest`, shared-kernel 의 의도적 선별 7)은 (나)만 잡는다. **팀장 추천 (나)**.
- **B-4(신설)** 수집 깊이 통일의 범위 — 전송·반사의 FULL 수집기를 (가) **모든 축**에(domain 순수성까지: enum 27 의 컴파일러 생성 `Enum.valueOf(Class,String)` + 함수 참조 둘 = `java.lang.Class` 쌍 28 이 새로 보여 정책 주석 「`Class` 는 목록에 없어서 닫힌다」를 뒤집어야 한다) · (나) **축별 선택** — raw-access(+2, 이미 허용 보유자) · usecase(0) · key-hash(0) · workflow→procurement(+2) 는 FULL, domain 순수성은 OWNER_ONLY 유지 + 그 선택을 고정하는 대조 test. **팀장 추천 (나)**.
- **B-5(신설)** 등재 등식의 자리 — (가) 모듈마다 등식 test(여섯 디렉터리 추가, 중복 여섯) · (나) app 한 자리에서 남의 소스 트리 훑기(모집단이 소스로 내려가 파일명 술어 구멍 아홉이 들어옴) · (다) build-logic 의 `gateExecutionGate` 형제 task — **컴파일된 test 클래스 전수 == 등재**(구조 층, 입력 선언 문제도 함께 사라짐; `build-logic/**` 가 in_scope 에 들어옴). **팀장 추천 (다)**.

## 계약 갱신 r1 (2026-10-04, 팀장 — 착수 실측 `_workspace/m6-6g2g/00_kickoff_measurement.md`, 읽기 전용 · 바이트코드 상수 풀 근사, `java.lang.invoke` 축은 ArchUnit 이 세지 않아 제외)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2g-1** | D-1 FULL 통일 시 증가: raw-access **참조자 축**(`collection.raw-access.allowed-referencers`) **∅ → 2**(`CollectionWiring`·`OpeningCollectionWiring` — 멤버 접근 축에는 이미 허용 2 라 새 표면은 아니나, 「아무도 참조하지 않는다」는 구조적 성질이 「둘만 참조한다」로 약해진다 — 설계 검토 (3) 자리) · usecase 0 · key-hash 0 · workflow→procurement +2(`NoticeCollected`·`RowDiscriminator`) · **domain 순수성 +28**(`java.lang.Class`, 컴파일러 생성) → 결정 **B-4** 신설 | 착수 실측 D-1 |
| **D-6G2g-2** | 접기 관례 셋이 구현 다섯 자리(`outermostClass()` 하나 + `topLevel()` private 복사 셋). 재등재 필요 둘(`NoticeKeyHash$Companion`·`Resolution$Resolved`), `Map$Entry`·Spring 핸들러는 보유자 축 아님 | 착수 실측 D-2 |
| **D-6G2g-3** | domain 여섯 모듈 반사 참조 0 — B-1 (가)면 등재 0, 바꿀 자리는 `layer.*` 도출 식 | 착수 실측 D-3 |
| **D-6G2g-4** | `java.nio.file` 은 전송 뿌리 밖; `Path`/`File` 생성 보유자 10(13) — B-2 입력 | 착수 실측 D-4 |
| **D-6G2g-5** | 전송 보유자 **29**(6G-2b 「28」 오기 정정), 비-private 멤버 280(internal 38), 전송 타입 없이 내보내는 멤버 **5**(`ReleaseCheckKt.fetchPromoted` · `DurableAppend.append` · `FileChannelAppend.append` · `replaceDurably` · `requireSafeKonepsBaseUri`) → 3층 등재 후보 5 | 착수 실측 D-5 |
| **D-6G2g-6** | `OPEN-6G-GATE-REGISTRY-KONEPS` 원 문장 없음 → 이 slice 가 정의(위 표 D-6) | 착수 실측 D-6 |
| **D-6G2g-7** | 등재 등식 현황: app 52==52 · workflow 48==48 · **adapters 131 vs 124**(미등재 7 = 의도적 `check` 밖 2 + **순수 누락 5**: `KonepsOpeningCompleteSourceTest`·`JdbcEditSessionRepositoryTest`·`JdbcEditSessionSaveGuardTest`·`JdbcStrategyRepositoryCodexRegressionTest`·`JdbcStrategyRepositoryTest`) · **shared-kernel 8 vs 1**(장부 머리말의 의도적 선별 7) · 나머지 모듈 일치, 잉여 0. adapters 덮개 열두 패키지 중 여섯, 등재 124 중 78(63%)이 등식 밖. 술어 세 모양(app 컴파일 클래스 양방향 · workflow 소스 재귀 양방향 · adapters 소스 비재귀 단방향), 파일명 술어 구멍 아홉(한 파일에 test 둘 이상 — `FileSampleListLedgerTest` 는 어떤 등식도 못 봄). 입력 선언 app·workflow 예, adapters 아니오 → 결정 **B-5** 신설 | 착수 실측 D-7 |
| **D-6G2g-8** | in_scope 보강: `WorkflowGateRegistrationTest.kt` 추가(초안 누락) · B-5 (다)면 `build-logic/**` · B-4 (가)면 `member-effects*.properties`. 순수 누락 5 등재는 정책 파일 편집만 | 착수 실측 8 |

## 계약 갱신 r2 (2026-10-04, 팀장 — 운영자 결정 · 설계 검토 `_workspace/m6-6g2g/01_design-review.md`)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2g-9** | **운영자 결정 B-1~B-5** = 추천대로: B-1 (가) 반사 뿌리를 domain 여섯까지(`layer.*` 도출 식, 등재 0) · B-2 (가) 전송 뿌리 + `java.nio.file`, 낱개 `java.io.File`·`FileInputStream`·`FileOutputStream`·`FileReader`·`FileWriter`·`RandomAccessFile`(라이브러리 로더·StAX·bean factory 는 경계 유지) · B-3 (나) 제외는 build 사실(Test task `filter.excludePatterns` · `@EnabledIfSystemProperty`)에서 기계로 · B-4 (나) 깊이는 축별 정책 키 `collection.depth.<axis>=FULL\|OWNER_ONLY` + 구현 모드 == 정책 표 등식 test · B-5 (다) 등재 등식은 build-logic 의 `gateExecutionGate` 형제 task(컴파일된 `@Test` 클래스 전수 == 등재 ∖ 제외, 양방향, 입력 선언 포함) | 운영자 2026-10-04 |
| **D-6G2g-10** | shared-kernel 의 「의도적 선별 7」은 build 사실로 표현되지 않으므로 **등재한다** — 장부의 뜻이 「CI 가 돌려야 하는 test 전수」로 바뀌고 `gate-tests.properties` 머리말을 그렇게 고친다. adapters 순수 누락 5 도 등재. `check` 밖 둘(`CrossLangSmokeTest` filter · `RealServerIntegrationTest` `@EnabledIf`)은 제외 집합 | 설계 검토 (3) 미달 |
| **D-6G2g-11** | raw-access **참조자 축은 OWNER_ONLY 유지**(∅ 보존 — 「아무도 참조하지 않는다」 성질을 지킨다); FULL 로 올리는 축은 workflow→procurement(+2 등재)·usecase·key-hash(0). domain 순수성 OWNER_ONLY. 전부 `collection.depth.*` 표에 적고 test 가 등식으로 잠근다 | 설계 검토 (3) 과잉 |
| **D-6G2g-12** | 기존 등재 등식 test(app 1 · workflow 1 · adapters 6)는 task 가 초록이 된 **뒤** 같은 slice 에서 삭제(두 벌 금지). 삭제는 별도 커밋, 그 커밋도 게이트 술어 변경이라 표적 재검증 | 설계 검토 (1) |
| **D-6G2g-13** | 항목 순서(구현 레인): ① D-7·D-6 task(+입력 선언, 등재 보강 D-10, 제외 build 사실) → ② 기존 등식 test 삭제(D-12) → ③ D-1 깊이 정책 키 + 등식 test(D-11) → ④ D-2 접기 통일 + 재등재 둘 → ⑤ D-3 반사 뿌리 도출 식 → ⑥ D-4 뿌리·낱개 + 쌍 등재(≈13 보유자) → ⑦ D-5 3층 `collection.transport.member-surface`(후보 5). 항목마다 변이 ≥1 RED(numstat 확인, 「대신」 변이, 커밋 뒤 변이), 새 public 표면 보고 항목, production diff 0 유지 | 설계 검토 |
| **D-6G2g-14** | **(계약 갱신 r3) ④ 접기 통일의 방향 = 이름 절단(`outermostName()`)** — D-13 문면 「`outermostClass()` 하나만」은 팀장 오기. `outermostClass()` 는 `enclosingClass` 접기이고 6G-2b cr M-1 이 실측한 결함(해소 여부에 따라 JDK 중첩 타입의 접힘이 갈려 전송 표면을 늘리지 않는 편집이 등식을 깼다)을 피해 쌍 등식 셋이 이름 절단으로 옮겨 간 것이다 → 이름 절단 하나로 통일하고 `outermostClass()`·`topLevel()` 복사 셋을 함께 삭제. 재등재 둘(`NoticeKeyHash$Companion`·`Resolution$Resolved`)은 어느 쪽이든 같다. `enclosingClass` 접기를 쓰던 게이트(원문 값 획득·대분류·공고명 키·러너·로거)는 등재가 전부 `bidvector..` 안이라 두 접기가 같다 — 재관측 결과를 evidence 에 적고 바뀌면 멈춘다. 레인 ①·② 커밋 `ec999d45`·`5ee8ca92` 수령 | 레인 질문 2026-10-04 |
| **D-6G2g-15** | **(계약 갱신 r4) ③ 실측이 D-11 표를 정정** — ① `collection-procurement` 축은 **OWNER_ONLY 유지**(계약 표의 FULL 은 착수 실측의 상수 풀 근사가 과소 추정한 것; ArchUnit 실측에서 FULL 은 `RawNoticeObservation`·`KonepsCollectionPolicyData`·`ObservationKey` 등 열 몇 종을 더 들이고 그 셋은 ① 층이 「수집 use case 는 원문 키 접근 타입을 이름 붙이지 못한다」로 막으려는 바로 그 타입 — 허용 집합에 담으면 ① 이 뜻을 잃는다; raw-access 참조자 축과 같은 과잉 논거) · ② domain 순수성 깊은 수집 좌표는 둘(`java.lang.Class` + `java.time.chrono.ChronoLocalDate` — `LocalDate` 비교의 상위 시그니처, 허용 패키지 밖), 둘 다 저자 좌표가 아니라 OWNER_ONLY 결정 그대로, test 기대값만 둘 · ③ 깊이를 쓰는 축은 일곱이 아니라 **열하나**(`runner`·`service-key`·`logging`·`operator-credential` 추가, 전부 깊이 비민감 FULL, 등재 0). 레인 커밋 `c1e36b85`(③) · 변이 셋 RED(키만 FULL · 구현 하드코딩 · 유령 축 키). ①·② 변이 전부 RED(입력 선언 제거 시 거짓 초록 실측 포함). 새 public 표면 둘 — build-logic `GateRegistrationGateTask`(빌드 스크립트 저자 = 경계 밖) · app test 전용 `enum class DepthAxis` — 수용; production diff 0 | 레인 보고 2026-10-04 |
| **D-6G2g-16** | **in_scope 추가(주석 한 줄씩)**: ② 가 지운 등식 test 클래스 이름을 드는 KDoc 셋 — `workflow/src/test/kotlin/bidvector/workflow/collection/SampleSelectionTest.kt`(`WorkflowGateRegistrationTest`) · `workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityPolicyDataTest.kt`(`MlGateRegistrationTest`) · `adapters/src/test/kotlin/bidvector/adapters/contract/RealServerIntegrationTest.kt`(`MlGateRegistrationTest`) → 「등재 등식은 build-logic `GateRegistrationGateTask`」로 한 커밋. 어떤 게이트도 이름 존재를 확인하지 않아 초록이었다 — 문면만 | 레인 보고 |
| **D-6G2g-17** | **(계약 갱신 r5) 레인 완료 수령 — 동결, 판정 SHA `d6c79a99`(마지막 산출물), evidence `6f850773`.** 항목 커밋: ① `ec999d45`(build-logic `GateRegistrationGateTask`, 등재 보강 adapters 5·shared-kernel 7·코어 1, `gate-tests.properties` 머리말·version 2→3) ② `5ee8ca92`(등식 test 여덟 삭제 + app·workflow 입력 선언 둘 제거) ③ `c1e36b85`+`d6c79a99`(깊이 키 열하나, 분배 함수 sizeGate 분할 — 첫 `check` 가 붉었고 그 사실을 적음) ④ `29777f5f`(이름 절단 하나, 접기 자리 일곱→하나, 재등재 둘) ⑤ `73925fdb`+`26764422`(반사 뿌리 전 층 도출, 등재 0; test 이름 둘 정정 — **이탈 추가 수용**) ⑥ `d70cf300`(뿌리 `java.nio.file` + 낱개 여섯, 새 용도 `file-system` 쌍 41·보유자 15, **1층 허용 목록에서 이름 둘 제거** — verifier 표적, 음성 fixture 둘) ⑦ `e5a37958`(3층 `member-surface` 쌍 18, vr I-1 의 `release$bid_vector_adapters` 포함) + `3ee5f905`(D-16 KDoc 셋). 팀장 대조: production `src/main` diff **0**(build-logic 제외), in_scope 밖 변경 0, 판정 SHA 뒤 evidence 만. 변이 열셋 RED(레인), 새 표면 둘(D-15 수용). acceptance `check`·`qualityBaseline` exit 0(test 2,664), container job 생략(production diff 0 — 6G-2e 선례). **게이트 술어 slice — 모든 커밋이 표적**: verifier(opus) + code-reviewer(sonnet) 병렬 | 레인 보고 2026-10-04 |
| **D-6G2g-18** | **(계약 갱신 r6) 사실 선언 — evidence HEAD 이동 `6f850773` → `5f76b0db`**: 동결 지시와 레인의 D-14 재독(「재관측 결과를 evidence 에 적는다」가 커밋 메시지에만 있어 `commands.md` 절 하나 추가)이 엇갈렸다. 커밋은 `commands.md` 한 파일, 산출물 변경 0, 판정 SHA `d6c79a99` 불변, rollback 대조(`d6c79a99..5f76b0db` 복원 경로) 빈 출력. 되쓰지 않는다. verifier·cr 는 산출물 `d6c79a99` 를 보고 있고 evidence 는 `5f76b0db` 로 읽도록 통지. 재관측 결과: `enclosingClass` 접기를 쓰던 게이트 여섯의 등재는 재등재 둘 외 변화 0(D-14 「바뀌면 멈춘다」 비해당) | 레인 보고 2026-10-04 |
| **D-6G2g-19** | **(계약 갱신 r7) verifier r1 `not-ready` @`d6c79a99`**(`_workspace/m6-6g2g/02_verifier_r1.md`) · **cr r1 새 high 없음**(`03_code_review_r1.md`, medium 넷·low 다섯). **재작업 1/5.** 수정 라운드 1 — finding 별 커밋, **모든 커밋이 술어 변경이라 verifier 표적 재검증**. 처분: **vr H-1(3층 술어가 몸 모양에 의존)** → 보유자 안의 private·synthetic 멤버와 람다를 **구조적으로 도달 추적**(호출 그래프 닫힘: 비-private 멤버 → 그 멤버가 부르는 같은 클래스의 private/synthetic 멤버·람다 본체 → 전송 멤버 호출)하여 「시그니처에 전송 타입이 없는데 전송에 도달하는 비-private 멤버」로 술어를 세운다; 등재는 비-private 멤버만(synthetic·컴파일러 생성은 등재 대상이 아님 — cr low 「`hashCode` 등재」도 함께 해소); `OpeningCollectionWiring#openingCollectionSources`·`FileChannelAppend.append(String)` 등 verifier 가 센 17 을 재관측해 등재. 변이: private 헬퍼 경유·람다 경유·직접 호출 셋 다 RED · **vr H-2(메타 애노테이션 조회가 testRuntimeClasspath 만)** → 모듈 자기 test classes 디렉터리를 조회에 더하고 실제 class 파일로 unit test(모듈 test 소스의 합성 애노테이션만 쓰는 test → 모집단 포함 RED) · **vr M-1(build-logic 등재 게이트의 메타 classpath 비어 있음)** → 같은 수정으로 닫힘(build-logic 도 자기 test 출력 + 런타임 classpath); 알려진 제한 2 문면 정정 · **vr M-2 = cr G-1(`collection.depth.domain-purity` 죽은 키, 등식이 test 안 두 번째 구현을 잼)** → 키를 **지운다**(domain 순수성은 깊이 개념이 없는 `directDependenciesFromSelf` 구조 — 축 열하나 → 열, 그 사실을 정책 주석·checklist 에); 깊이 등식은 **게이트 호출 자리가 읽는 키**와 묶는다(cr low 「생성자 기본값 `FULL`」 제거 — 기본값 없음, 키 필수) · **vr M-3(String 경로 생성자 — `PrintWriter(String)`·`Formatter(String)`)** → 팀장 결정 D-20 | verifier r1 · cr r1 2026-10-04 |
| **D-6G2g-20** | **파일 시스템 출구 낱개 확장(B-2 의 정신 안, 팀장)**: String 경로로 파일을 **여는** JDK 생성자를 가진 타입 셋을 낱개에 더한다 — `java.io.PrintWriter`·`java.io.PrintStream`·`java.util.Formatter`(각각 `(String fileName)` 생성자가 파일을 연다). `Scanner(String)` 은 문자열 파싱이라 제외(`Scanner(File)` 은 `File` 타입으로 이미 잡힘). 낱개 여섯 → **아홉**; 경계 문장에 「`java.io`·`java.util` 의 파일 여는 String 생성자 타입은 이 아홉」. 관측에서 쌍 재산출·등재. 운영자에게 보고(B-2 (가) 범위 안의 열거 확장) | vr M-3 |
| **D-6G2g-21** | **cr 처분**: **G-2(상속받은 `@Test` 가 모집단 밖)** → 모집단 판정을 상위 클래스 사슬까지(class 파일의 super 사슬을 같은 조회 경로에서 따라가 `@Test` 류 메서드 상속 포함); `@Test` 를 선언한 추상 클래스는 **모집단 제외**(인스턴스화 불가 — JUnit 도 돌리지 않음), unit test 둘 · **G-3(3층 쌍이 오버로드를 접음)** → 쌍을 (클래스, 멤버 이름 + **서술자**)로; 등재 18 재산출 · **G-4(접기 통일로 주입 허용이 `Resolution.NotApplicable` 까지)** → 주입 축(`app.injection.allowed-types`)은 **타입 동일성**이라 접지 않는다 — 정확 JVM 이름 대조로 되돌리고 `Resolution$Resolved` 재등재 복원; 접기는 **보유자(누가 참조하는가) 축**에만 — D-14 문면에 그 구분을 더한다 · low: 메타 애노테이션 순환의 `false` 영구 캐시 → 방문 중 표시로 · `CollectionArchitectureRules` 깊이 출처 둘 → 키 하나(D-19) · evidence ① 표 `app 51` → 52 + HEAD 열(vr L-1 = cr G-7) · rollback 복원 목록을 디렉터리 넷으로 접은 것 → ① 명령의 기계 산출 그대로(vr L-2) · `RealServerIntegrationTest` KDoc 결론 문장 정리(in_scope D-16 범위) | cr r1 |

## 하네스 레인 변경

(착수 뒤 리뷰 요청 시점마다 등재)

## 입력 OPEN (이 slice 가 닫지 않는 것)

- `OPEN-6G-REVIEW-FOLLOWUPS` 잔여 열하나 · `OPEN-6G2D-*`·`OPEN-6G2F-MAX-PAGES-PROVENANCE` · 명칭 셋 · `torn` 위치 · 5,000자 경로 OSError 문면 → **6G-2c-형식**(스냅숏 추출 뒤, ≈2026-10-27).
- `OPEN-6G2C-BUSY-SEED-ORDER` → 6G-2c-형식(seed 순서는 실행 상태 판독 경로).
- `OPEN-6G2F-NOTICE-LIST-ROWS` → M7 뒤 값 slice. `OPEN-6B3-RAW-OBSERVATION-RETENTION` → 6B-3.
