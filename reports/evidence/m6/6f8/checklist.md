# M6/6F-8 checklist — 수집 배선 · 공고명 수집

정본 계약은 `scope.md`. 이 문서는 그 계약을 **구현이 어떻게 닫았는가**의 대응표와 알려진 제한, 리뷰 요청 조건이다.
개수·SHA 는 적지 않는다 — 명령이 낸다(`commands.md`).

## 1. 위협 모델 우회 일곱 ↔ 닫는 장치

| # | 우회(scope.md) | 닫는 장치 | 잠금 test·게이트 |
|---|---|---|---|
| 1 | use case 가 `canonicalize` 를 건너뛰고 원문 키를 직접 읽는다 — **한 걸음 옮겨도**(이웃 패키지 헬퍼·app 클래스) | 정규화 지점 하나. **주 잠금 = 모듈 전체 규칙**(정책 키 `collection.raw-access.*`): `workflow..`·`app..` production **전체**가 원문 키 접근 타입(계약·레지스트리·원문 키·값·존재 판별·개념 토큰)과 원문 관측을 직접 읽는 공개 해석 함수의 파일 클래스(`resolveAmount`·`instantFrom`)를 참조하지 못하고(허용 참조자 ∅), 통과 전용 타입(원문·정책·정규화 결과)의 멤버에 접근하지 못한다(허용 접근자 = 배선 한 클래스 — 필드 계약 레지스트리를 원문 저장 어댑터에 넘기는 조립 자리). 두 허용 집합은 관측 집합과 **같아야** 한다(낡은 항목 방지). 그 위에 use case 패키지의 procurement 참조 **집합 == 허용 집합**·결과 타입 필드 금지, app 은 수집 포트 넷과 `canonicalize` 를 못 부른다(기존 「호출 쌍」 규칙, 허용 쌍 0) | `CollectionArchitectureGateTest` · 음성 `CollectionArchitectureGateCatchesViolationsTest`(fixture `RogueNeighborTitlePeek`(이웃 workflow 패키지)·`RogueRawFieldPeek`(app 이웃)·`RogueRawKeyReader`·`RogueCollectionPortCaller`) · `CollectNoticesUseCaseTest`(영속 명령의 원문이 저장된 관측 그 객체, 공고명이 계약 경유) |
| 1b | (우회 1 의 반사 갈래, F2-2) 원문 타입을 이름 붙이지 않고 리플렉션으로 값을 꺼낸다 — `javaClass.getMethod("getSourceText").invoke(…)` | 타입 이름이 아니라 **반사 표면**으로 닫는다(정책 키 `collection.reflection.*`): `workflow..`·`app..` production 전체가 `java.lang.reflect`·`java.lang.invoke`·`kotlin.reflect` 의 어떤 타입도 참조하지 못하고(허용 참조자 ∅), `Class` 의 멤버는 이름 조회(`getName`)만 접근한다 — 허용 **목록**이라 새 반사 멤버는 기본 거부다. 두 허용 집합은 관측 집합과 같아야 한다(관측 `Class` 멤버가 정확히 `getName` 이라 규칙이 공허하지 않다). 러너의 원인 코드는 `KClass` 대신 `Class.getName` 을 쓴다(출력 동일 — 그 한 자리가 이 규칙 이전의 유일한 반사 표면 참조였다) | `CollectionArchitectureGateTest`(반사 봉쇄 · 「허용 … 관측 집합과 같다」) · 음성 `CollectionArchitectureGateCatchesViolationsTest`(fixture `RogueReflectionPeek` 네 길 — `getMethod`/`invoke`·`forName`·`MethodHandles`·`KClass.members` · 잡히면 안 되는 `CleanNameLookup`) |
| 2 | 공고명 키를 어댑터·use case·러너에 문자열로 박는다 | 키는 `NOTICE_TITLE` 계약 행만 나른다. 상수 풀에 그 리터럴을 가진 production 클래스 집합 ⊆ {정책 파일 클래스} — 키 값은 게이트가 **정책에서 읽는다**. **보조 잠금이다** — 컴파일 상수에만 걸려 런타임 조립(`buildString`)·리소스 파일은 못 본다(측정: `commands.md`). 원시 키로 도메인 값을 만들려면 결국 계약 경유 읽기나 원문 멤버 접근을 거쳐야 하므로 **위협 ② 의 주 잠금은 1행의 모듈 전체 규칙**이다 | `CollectionArchitectureGateTest`(허용 집합을 비우면 정확히 정책 클래스가 걸림 — 규칙이 공허하지 않음) · 음성 fixture `RogueTitleKeyLiteral` · `NoticeTitleCanonicalizeTest`(계약 키를 바꾸면 그 키를 따름) |
| 3 | 탈락·중복을 세지 않고 버린다 | 소스 회계 + 정규화 탈락 + 저장 결과로 최종 회계를 다시 만들고 `CollectionAccounting` 생성자 등식이 재검사. 탈락은 사유별, 원문은 탈락 항목도 먼저 저장 | `CollectNoticesUseCaseTest`(회계 등식·사유별 합·탈락 항목 원문 저장·저장 결과 넷의 분류) · `CollectionRunnerE2ETest`(DB 합계) |
| 4 | 서비스 키가 예외 메시지·로그로 샌다 | 키는 배선 한 곳에서만 원문으로 읽히고(`KonepsCredentialProperties` 참조 집합 = {배선}) 로거 사용 집합 = {배선}. 러너 실패는 원 예외를 잇지 않는 정제된 예외(원인 코드만). **전송 실패 사유 채널(`TransportFailed`·`Failed.detail`·구조 실패 사유)은 예외 클래스 이름만 싣는다**(JDK 예외 메시지에 요청 URI 가 실릴 수 있다). 기본 URL 은 https 이거나 loopback 만(평문 http 외부 호스트는 기동 실패) | `KonepsServiceKeyLeakTest`(연결 거부·5xx·429·깨진 본문·지연, 서버가 URL 을 되돌려도) · `CollectionRunnerTest`(SQL 상세·요청 URI 실린 예외 → 원인 코드만) · `CollectionRunnerE2ETest`(전 로거 DEBUG 캡처의 메시지·**예외 cause 체인 전체**·**표준 출력·표준 오류**에 원문·인코딩 키 부재, 표본이 키를 실제로 실었음을 mock 이 받은 질의로, 표준 출력 캡처가 콘솔 줄을 실제로 봄을 양성 대조로 확인) · `CollectionWiringTest`(기본 URL 검사) · 구조 게이트 둘 |
| 5 | 러너가 기본 켜짐 / 상한 없이 돈다 / HTTP·이벤트로 열린다 | `CollectionWiring` 전체가 `mode=once` 조건부. 범위·업종(중복 포함)·키·기본 URL 오류는 기동 실패. 러너 타입 참조 집합 = {`CollectionRunner`}, **수집 use case 참조 집합 = {러너, 배선}**(컨트롤러·이벤트 리스너·`@PostConstruct` 로 부르려면 이 타입을 참조해야 한다). 범위는 생성 경로 하나(`CollectionRange.of`) + 정책 생성자 `internal`. 「오늘」은 KST 달력일 | `CollectionWiringTest`(속성 없음 → 러너·키 바인딩 없이 뜸, mode 다른 값 4종, 상한 32일·미래·역전·상한 정확히 31일 경계, **KST 자정 경계(UTC 날짜와 갈리는 시각)**, 미지·빈·형식 오류·**중복** 업종, 키 부재·빈 값, 환경변수 형태, 기본 URL) · `CollectionRangeTest` · 구조 게이트 |
| 6 | 쿼터 초과 뒤에도 계속 부른다 | `TruncationCause` 를 else 없는 `when` 으로 {이어 읽기, 다음 슬롯, 실행 멈춤} 에 매핑 — 새 원인은 컴파일이 막는다. 멈춘 자리와 남은 슬롯을 결과에 싣는다 | `CollectNoticesUseCaseTest`(쿼터 소진 뒤 **소스 호출 계수 0**, 쿼터 아닌 원인 아홉 종은 전부 다음 슬롯 호출) · 러너 종료 코드 2 |
| 7 | 신설 게이트 미등재 | `gate-tests.properties` 에 새 test class 전부 등재(workflow 는 양방향 등재 test 가 강제) | `gateExecutionGate`(Kotlin `check`) |

거동 쪽 확인: 재실행 멱등(새 notice 행 0 · 슬롯마다 재실행 `normalized = 0`, 재실행 `duplicate = 1차 normalized + 1차 duplicate`,
`unchanged = 1차 inserted + updated`) · 공고명이 `notice_title` 에 계약 경유로 저장 · `collection_run` 행 = 조회일 × 업종 · 형식이 어긋난 차수 항목은
실행을 죽이지 않고 IDENTIFIER 탈락으로 회계에 오른다 · **빈 문자열인 옵션 일시·금액 항목은 탈락하지 않고 마감 `null` 공고로 저장된다**(슬롯마다 하나, D-6F8-11 — 표는 5c) —
`CollectionRunnerE2ETest`(Testcontainers + mock KONEPS, 실호출 0).

## 2. (2b) 값 획득 축 — 새 public 표면 전수

| 표면 | 값을 얻는가 / 새로 낳는가 | 판정 |
|---|---|---|
| `FieldConcept.NOTICE_TITLE` + 계약 행 `bidNtceNm` | 원시 키의 유일한 자리(데이터). 소진 `when` 파급 0 | 닫는다 — 상수 풀 게이트 |
| `KonepsFieldContractRegistry.valueIn` | 계약 경유 읽기(기존 `contractsFor`+`valueOf` 세 줄) | **`internal`** — 다른 모듈에 새 표면 없음(컴파일 probe: `commands.md`) |
| `CollectNoticesUseCase.collect` / `CollectionProgress` | 입력: 포트·정책 제공 함수. 출력: 계수·열거·이름뿐 | 닫는다 — 결과·계수 타입이 원문·관측 키·정책·정규화 결과를 필드로 갖지 않음(게이트 규칙 ③) |
| `CollectionRange` / `CollectionRangePolicyData` / `COLLECTION_RANGE_POLICY` | 상한·미래 검사를 우회한 범위를 만드는 길 | 닫는다 — 범위 생성자 private, 정책 생성자 `internal`(컴파일 probe) |
| `CollectionSourceName` | 로그에 실리는 업종 이름 | 닫는다 — private 생성자 + `of`(형식 검사: 소문자 시작 토큰) |
| `CollectionSource`·`CollectionSlot`·`WriteTally`·`CollectionSlotReport`·`CollectionHalt`·`CollectionReport` | 계수·열거·이름을 옮기는 값 | 경계로 처리 — 공개 생성자(위조해도 로그·종료 코드에만 영향). 원문·키를 담을 필드 타입이 없다 |
| `SlotTally` | 슬롯 누적 계수 | `internal` |
| `CollectionRunner` / `CollectionLog` / `CollectionTermination` | 러너는 use case 만 부른다(포트·정규화 호출 0 — 게이트). 로그·종료는 주입 자리 | 경계로 처리 — 로그 줄 함수는 결과 타입만 입력으로 받음. 종료 주입은 production 에서 배선의 한 빈, test 의 대체 빈은 test 소스셋의 profile 잠금 `@TestConfiguration`(production classpath 에 없음) |
| `CollectionRunFailedException` | 원인 코드 문자열로 메시지 조립 | 경계로 처리 — 생성은 러너 한 곳, 원인 코드는 클래스 이름·SQLSTATE 뿐, 원 예외를 잇지 않음 |
| `CollectionProperties`·`KonepsEndpointProperties` | 비밀 아닌 설정(날짜·업종·URL·릴리스 표식) | 경계로 처리 — 합성 `toString` 이 나가도 비밀 없음 |
| `KonepsCredentialProperties` | 서비스 키 원문 | **타입 참조 경로만** 닫는다 — data class 아님(test 로 잠금), 참조 집합 = {배선}, 기본값 없음. `Environment.getProperty`·`@Value` 로 같은 값을 읽는 길은 타입을 이름 붙이지 않아 못 잡는다(app main 사용 0 실측, 알려진 제한 17) |
| `CollectionWiring` 빈 메서드 | 조립 | 경계로 처리 — `open fun`(Spring 프록시 요건)이라 test 가 부를 수 있으나 그러려면 `KonepsCredentialProperties` 를 이름 붙여야 해 참조 집합 게이트가 잡는다 |
| ArchUnit 규칙·정책 키 | 게이트 | — |

**새 표면(이 slice 의 수정 범위)**: 공개 API 추가 0 — `canonicalize`·`CanonicalizationOutcome` 시그니처 불변(방출되는 탈락 사유가 늘었다: `ParseFailureKind.IDENTIFIER`),
`KonepsTransportOutcome.TransportFailed` 는 `internal` 이고 필드 이름·내용만 바뀐다(예외 메시지 → 예외 클래스 이름 — 값을 얻는 길이 아니라 **채널을 닫는** 쪽), `endpointBaseUri`·`IdentityOutcome`
은 private. 정책 파일 키 `collection.raw-access.*`·`collection.usecase.*` 는 게이트 입력이다.

| 「경계로 처리」 행 실측 | 결과 |
|---|---|
| 정규화 함수의 **다른 호출자**(`canonicalize` 를 use case 패키지 밖 workflow 이웃 패키지가 부른다) | 모듈 전체 규칙이 못 잡는다 — 원문 키·멤버 접근이 아니라 같은 함수의 재호출이라 「변환 지점 하나」는 유지(측정: `commands.md` 변이 G4, 초록). **「app 쪽은 기존 호출 쌍 규칙이 막는다」는 직접 호출에만 참이다**(verifier r2 F2-4) — app 이 workflow 이웃 클래스를 거쳐 부르는 길은 열려 있고, 그 두 번째 수집 경로는 「원문을 먼저 커밋」(알려진 제한 1)을 구조적으로 지키지 않을 수 있다. 오늘 production 의 `canonicalize` 호출자는 use case 하나뿐이고(grep) 「관측 호출자 == {use case}」 등식은 두지 않았다 — 알려진 제한 27 |
| **서드파티 반사 도구**(Spring `BeanWrapper`·`ReflectionUtils`, Jackson `convertValue` — `java.lang.reflect` 를 이름 붙이지 않고 값을 읽는 길) | 반사 규칙(1b)은 JDK·Kotlin 반사 표면만 본다 — 라이브러리 타입 이름 목록은 (g) 와 같은 열거가 되어 넣지 않았다. workflow·app production 의 사용은 0(관측 집합 등식이 `java.lang.reflect` 등은 ∅ 로 잠그나 서드파티 타입은 못 본다) — 알려진 제한 29 |
| 원문을 읽는 공개 해석 함수의 **새 파일**(`resolveAmount`·`instantFrom` 같은 진입점이 다른 파일 클래스에 생김) | 열거(파일 클래스 둘)라 새 파일은 자동으로 안 걸린다 — procurement 공개 함수 실측으로 현재 진입점이 그 둘뿐임을 확인했고, 새 진입점은 리뷰가 보아야 한다(알려진 제한 24) |

**`object`/companion 주입 자리**: 새로 생긴 것 없음(`CollectionSourceName` 의 정규식은 private).

## 3. 새 파일 ↔ in_scope 대조 (A·M 둘 다)

`in_scope` 밖 경로 **0** — 판별: `git diff --name-status <base>..HEAD` 의 각 경로를 `scope.md` `in_scope` 항목에 대조(D-6F8-10 이 `AmountResolutionOutcome.kt` 를 더한 뒤 기준).
사용한 in_scope 항목: workflow collection main·test · procurement main 다섯 파일(`FieldContract`·`CollectionPolicy`·`Canonicalize`·`AmountResolutionOutcome` + 게이트 등재) +
procurement test · adapters koneps main(전송 실패·구조 실패 사유 채널)·test · app collection main · app wiring main · app test(러너·배선·게이트·배포물 내용) ·
`archfixture/violating`(이웃 패키지·app 이웃·use case 호출자 표본) · `app/build.gradle.kts`(kotlin-reflect 의존 한 줄 + bootJar 경로 주입, 5항 13번) ·
`gate-tests.properties`·`architecture-policy.properties`(추가만) · `docs/discovery/**`(data-dictionary) · `reports/evidence/m3/3a/policy-values.md`(§1.3 공고명 행·P-15) ·
evidence · `milestone-6.md`(팀장 문단).
**수정 라운드 2(D-6F8-11·13)가 더한 파일**(전부 in_scope 안): `procurement/src/test/**` 의 `CanonicalizeBlankValuesTest.kt` · `app/src/test/kotlin/bidvector/archfixture/violating/**` 의 `RogueReflectionPeek.kt`·`CleanNameLookup.kt`
(그 밖은 기존 파일 수정 — `AmountResolutionOutcome.kt`·`Canonicalize.kt`·`CollectionRunner.kt`·게이트 규칙·정책 파일 둘). **일시 해석의 정본 파일 `DateTimeInterpretation.kt` 는 in_scope 밖이라 손대지 않았다** — 알려진 제한 28.
**쓰지 않은 in_scope 항목**: `KonepsPresentInSets.kt`(공고명 행이 `NOTICE_LIST` 기본값) · adapters persistence(title 영속은 이미 됨) · `workflow/build.gradle.kts`(의존 추가 불요) ·
slf4j 는 Boot 스타터가 제공 · `member-effects*`(workflow 효과 도출 변동 없음) · `fixtures/**`·`expected/**`(공고명 필드가 corpus 기대값에 안 닿음 — 아래 4항).
마이그레이션 없음(`out_of_scope` 준수).

## 4. fixture·정책 version 근거

- corpus: `bidNtceNm` 을 가진 input fixture 는 `koneps-collection-019` 하나(공고번호 없는 행에 실린 문자열). 그 case 의 `verified_paths` 에 미지 필드 수가
  없어 기대값·`manifest.yaml` sha 무변경 — `SharedKernelCorpusConformanceTest` 가 Kotlin `check` 안에서 초록.
- 정책 version: `KONEPS_COLLECTION_POLICY` 는 `EffectiveFrom.Initial` 단일 항목 그대로 — 필드 계약 행 하나 추가는 운영자 지시(2026-09-24)가 승인.
  `COLLECTION_RANGE_POLICY`(신설, 상한 31일)의 근거는 `scope.md` D-6F8-3.

## 5. 알려진 제한

1. **항목마다 원문을 먼저 커밋하고 canonical 은 항목 트랜잭션 하나로 저장한다**(scope.md D-6F8-1 정정 문면). 근거: `JdbcNoticeRepository` 는 `TransactionBoundary` 참여자가 아니고
   (묶으면 `Rejected` 경로의 `rollback()` 이 원문 append 까지 되돌린다), `RawObservationStore` 계약(⑤)이 「이후 단계가 실패해도 이미 커밋된 원문은 남는다」다. 실패 창: 원문 저장 뒤
   저장소 예외 → 원문은 남고 canonical 은 없다(재실행이 새 원문 행을 추가하고 canonical 을 정상 저장 — 실패 횟수만큼 원문 행이 쌓인다, append-only 관측 로그의 의도된 거동).
2. `collection_run` 표에 **업종 열이 없다** — 같은 조회일의 공사·용역 행이 DB 에서 구별되지 않고(둘 다 `NOTICE_LIST`) 개수만 조회일 × 업종과 같다. 업종별 회계는 러너 로그와
   결과 타입에 있다. 열 추가는 마이그레이션이라 하지 않았다 — `OPEN-6F8-COLLECTION-RUN-CATEGORY`.
3. 저장 `Rejected`(권위 없는 유입이 권위 값을 덮으려 함)는 탈락 사유 어휘가 없어(procurement `Accounting.kt` 는 in_scope 밖) 회계에서 `duplicate` 로 접히고 거부 건수는
   결과 타입 `WriteTally.rejected` 와 `rejected_write` 감사 표에만 있다(`collection_run` 에는 없음). 그래서 「재실행 `duplicate` = 1차 `normalized` + 1차 `duplicate`」 등식은 저장 거부가 없다는 전제다.
4. 저장소 인프라 예외는 삼키지 않는다 — 그 슬롯의 회계는 남지 않고 실행이 실패로 끝난다(앞서 끝난 슬롯의 회계·이미 커밋된 원문·canonical 은 남는다).
5. 이어 읽기(`MaxPages`)에는 횟수 상한이 없다 — 소스의 `totalCount` 도달·빈 페이지·「같은 커서 재요청」 가드로 끝난다. 끝없이 새 페이지를 내는 소스는 가정하지 않는다.
6. 러너 실패는 **원인 코드만** 남긴다(원 예외 메시지·스택은 로그·전파 어디에도 없음) — 진단은 슬롯 진행 줄과 재실행. 절충이다(SQL 상세에 행 값이 실릴 수 있음).
7. JDK `HttpClient` 자체의 디버그 로깅(`-Djdk.httpclient.HttpClient.log=…`)을 켜면 JDK 가 요청 URI(키 포함)를 남긴다 — 코드 밖의 실행 규율이다(실행 명령에 넣지 않는다).
   E2E 에서 확인한 사실: DEBUG 전 로거 캡처에서 인코딩 키가 나온 유일한 곳은 **같은 JVM 의 mock 서버(`com.sun.net.httpserver`)의 서버 쪽 로그**였고 그것은 시험 대상이 아니라 캡처에서 뺐다.
8. 「오늘」(범위 상한의 `to ≤ 오늘`)은 KST 달력일이며 `workflow.evaluation.OPENING_DATE_ZONE`(이름은 개찰일용)을 재사용한다 — `Asia/Seoul` 리터럴 중복을 피한 선택.
9. 러너가 켜진 프로세스도 기본은 웹 서버가 함께 뜬다 — `spring.main.web-application-type=none` 을 쓴다(그 형태를 E2E 재실행이 실측). 부팅 요건(운영자 자격증명 값·후보 상한)은 러너와 무관하게 남는다.
10. `ntceNm`(legacy 둘째 키)은 어느 문서에도 없어(`policy-values.md` §1.7.6) 등재하지 않았다 — 실수집 원문에서 확인한 뒤 후속.
11. 정본 승인 문서 `reports/evidence/m3/3a/policy-values.md` §1.3 공고명 행·P-15 가 등재돼 있다(P-14 형식). 그 행이 옮긴 사실: 공식 참고자료는 `bidNtceNm` 을 응답 항목 **필수**(구분 `1`, 항목크기 1000)로 선언하는데
    **계약 행은 옵션**이다(수집이 공고명 부재로 항목을 탈락시키지 않는다 — 부재는 `null`). 이 완화는 운영자 지시 범위의 결정이며 실수집 원문에서 부재율을 재는 것이 D-6F8-5 의 확인이다.
12. 응답 shape 는 정책 문서 기반 합성 fixture 다 — **실 KONEPS 미검증**이다(D-6F8-5 실수집이 판정).
13. **기존 결함을 고쳤다(범위 밖 발견, in_scope `app/build.gradle.kts` 안)** — 배포물 `bootJar` 는 `kotlin-reflect` 없이 나가 `java -jar app.jar` 가 `@ConfigurationProperties` 의 Kotlin 생성자
    바인딩에서 즉시 죽었다(`PersistenceProperties` 부터). test 런타임 classpath 에는 그 라이브러리가 이미 있어(다른 라이브러리 경유) production 조립을 Testcontainers 로 부팅하는 test 가 전부
    초록이었다 — `:app:dependencyInsight` 실측: `runtimeClasspath` 없음 · `testRuntimeClasspath` 있음. 의존 한 줄을 더하고 `BootJarRuntimeClasspathTest` 가 산출물을 열어 잠근다(의존을 지우는 변이 RED,
    `commands.md`). 고친 jar 의 실행은 같은 문서의 스모크가 실측했다.
14. 이 slice 가 승인 문서 `data-dictionary.md` 에 줄을 넣어 그 파일을 가리키는 `file:line` 인용이 밀리는지 본다: 인용은 세 좌표(그 문서의 삽입 지점보다 위쪽)뿐이라 밀리지 않는다 — 명령: `commands.md`.

15. **`OPEN-6F8-RAW-PII-RETENTION` → 6B-3**: `raw_observation.payload` 는 항목 원문을 그대로 저장하므로(F-7 결정 — `sourceText` 바이트 동일) 실응답에 담당자 이름·전화·이메일 키(`ntceInsttOfcl*`·`dminsttOfcl*`)가
    있으면 **영속된다**. 필드 계약에는 없어 `payload_fields` 열에는 안 들어가지만 원문 열에는 들어간다. 이 실수집이 6B-3(보존 기간) 대상 표를 늘린다. 개발 DB 는 확인 뒤 `docker rm -f -v` 로 폐기한다(`rollback.md`).
    evidence 에는 담당자 키가 있는 행 **수만** 싣는다(값·행 내용 없음).
16. **`OPEN-6F8-QUOTA-XML-ENVELOPE`**: data.go.kr 게이트웨이의 XML 오류 봉투로 한도 초과가 오면 `parseKonepsEnvelope`(JSON 만 해석)가 `StructureFailure` 로 분류하고 use case 는 그것을 다음 슬롯으로 넘긴다 —
    멈추지 않고 남은 슬롯마다 한 번씩(최대 조회일 × 업종) 거부된 호출이 나간다. 기존 어댑터 분류(M3 P-4)라 이 slice 는 고치지 않았다. 실수집에서 슬롯 원인이 연달아 `StructureFailure` 로 찍히면 실행자가 중단한다.
17. **로거·키 읽기 게이트의 사각(현재 사용 0)**: 로거 집합 게이트는 app 패키지 + slf4j 두 타입만 본다(adapters·workflow·procurement 는 지금 로거 0 이지만 잠기지 않았고 JUL·`System.Logger`·`println` 은 대상 밖 — E2E 가
    표준 출력·표준 오류를 잡는 것이 그 그물). 키 읽기 게이트는 `KonepsCredentialProperties` **타입 참조**를 본다 — `Environment.getProperty("…")`·`@Value` 로 같은 값을 읽는 길은 타입을 이름 붙이지 않아 못 잡는다(app main 사용 0 실측).
    후속: 로거 대상 패키지 확장, `Environment`·`@Value` 참조 금지 규칙.
18. 실행 수준 실패(무효 서비스 키 resultCode 30 → `NotRetryable`, 망 단절)에 **조기 정지가 없다** — 계약이 멈추는 것은 쿼터뿐이라 위반은 아니다. 무효 키면 슬롯마다 한 번씩 실패(호출 조회일 × 업종, 종료 코드 2)하고 응답이 매달리면
    슬롯당 재시도 시간 × 슬롯 수만큼 걸린다. 「연속 N 슬롯이 키·입력 수준 원인으로 절단이면 멈춘다」는 후속 후보이고 실수집은 실행자가 첫 슬롯 원인을 본다(16번과 같은 감시).
19. 어댑터 단계 탈락(공고번호·차수 없음)과 소스 안 중복은 `items` 에 실리지 않아 **원문이 저장되지 않는다** — 계수는 회계에 남는다(E2E 가 명시적으로 단언). 「원문을 잃지 않는다」는 use case 이후(정규화 탈락·저장 거부 포함)로 한정된다.
20. `BootJarRuntimeClasspathTest` 는 산출물 안 `kotlin-reflect` 한 이름의 존재를 잰다 — 다음에 test·production classpath 차이로 빠지는 다른 라이브러리는 같은 방식으로 다시 숨을 수 있다(증상은 `java -jar` 부팅 실패). 증상 자체를 잠그는 것은 산출물 jar 를
    별도 프로세스로 띄워 컨텍스트가 올라오는지 재는 스모크(DB 필요)라 이 slice 는 하지 않았고 `commands.md` 스모크 실측으로 갈음했다. `tasks.test { dependsOn(bootJar) }` 라 `:app:test` 마다 jar 를 다시 만든다.
21. **`OPEN-6F8-NON-THROWING-FACTORIES`**: shared-kernel 값 객체(`NoticeRound`·`Rate`)는 불변식 위반을 `IllegalArgumentException` 으로만 알려 `canonicalize` 안에 좁은 try/catch 둘(차수·낙찰하한율)이 있다. 비예외 팩토리가 생기면 사라진다(공유 커널이라 이 slice 에서 넓히지 않음).
22. `canonicalize` **뒤**의 값 유래 저장 예외는 접히지 않는다(계약이 인프라 예외 전파를 명시). **`OPEN-6F8-NUL-CHARACTER`**(verifier r2 F2-1 — 실측): 원문 값에 NUL 문자(JSON 이스케이프 `\u0000`)가 실리면
    **원문 저장 단계**(`raw_observation` INSERT — 계약 필드의 투영 열 `payload_fields JSONB` 가 NUL 을 거부)가 `sqlState=22P05` 로 실패한다. 같은 INSERT 라 **원문 행도 남지 않고**(원문을 잃는다),
    실행은 슬롯 회계 없이 exit 1 + `collection failed cause=…sqlState=22P05` 로 끝나며 **재실행해도 같은 항목에서 멈춘다**(결정적). 옛 서술의 「`22021` · 저장 단계」는 SQLSTATE 와 자리가 모두 틀렸다.
    KONEPS 응답이 NUL 을 싣는다는 관측은 없다(v2 fixture·legacy backfill 로그 0건, D-6F8-5 실수집에서 `22P05|22021` 0회 — 팀장 실측). 처방(후속): NUL 을 어느 층(어댑터·투영)에서 어떤 **명시 결과**로 접을지는
    원문 무변환 원칙과 충돌하는 결정 사항이다. **실행 중 이 SQLSTATE 가 한 번이라도 찍히면 실행자가 멈추고 보고한다**(그때 HIGH). 낙찰하한율 극단 양의 지수(`1e999999999`)는 `Rate` 를 만들고 저장에서 실패할 수 있으나
    **미실측**이다 — insert 경로 `setBigDecimal` 이 NUMERIC 범위 초과로 실패(22003 추정)하고 update 거부 경로의 `toPlainString()` 이 10억 자릿수 문자열을 할당할 수 있다는 것은 코드를 읽은 추정이다. 실데이터 개연성 없음.
    **범위 밖 기존 부채(참고)**: `JdbcNoticeRepository.updateNotice` 는 모든 `PSQLException` 을 「권위 덮어쓰기 거부」로 분류해 데이터 유래 SQL 오류가 오분류될 수 있다(3번과 겹침) — 이 slice 가 만든 것이 아니며 후속 OPEN 후보다.
23. 공고명 키 리터럴 게이트는 **보조 잠금**이다(위 1행 우회 2) — 컴파일 상수만 본다.
24. 원문을 읽는 공개 해석 함수의 파일 클래스 열거(`collection.raw-access.types` 의 `…Kt` 둘)는 새 진입점이 새 파일에 생기면 자동으로 안 걸린다(위 (2b) 표).
25. **`OPEN-6F8-BUSINESS-CATEGORY-SOURCE`**(D-6F8-12 — 데이터 공백, 이 slice 에서 고치지 않는다): 오늘 **모든 공고의 업무구분이 `null`** 이다 — 두 겹이다. (a) 운영 필드 계약에는 업무구분 **코드** 행이 없다(라벨 `bsnsDivNm` 행만 있고
    `businessCategoryFrom` 은 코드 행이 있어야 `BusinessCategory` 를 만든다 — 코드 행은 test 정책에만 있다). (b) 실수집에서 목록 응답에 그 키의 존재율이 0 이었다(팀장 실측). 그래서 전략의 「관심 업종」 규칙은 지금 어떤 공고와도 맞지 않는다.
    어느 키를 업종 축으로 쓸지는 정책 데이터(운영자 승인) 결정이다. 선택지: ① 응답에 있는 공공조달분류 번호·이름 키를 업종 축으로 채택(용역에만 채워짐 — 공사는 비어 있음) ② 다른 오퍼레이션(공고 상세 등)에서 업무구분을 얻음(호출 수 증가) ③ 운영자 승인 대기(현행 유지 — 업종 규칙 비활성).
    팀장이 운영자에게 올린다. 이 slice 는 코드·정책을 바꾸지 않았다.
26. **마감이 `null` 인 공고는 평가 후보가 아니다**(D-6F8-11 이 빈 마감을 부재로 접은 결과의 거동 — 바꾸지 않았다): `JdbcCandidateSource` 의 후보 질의는 `WHERE status = ANY(?) AND deadline_at > ?` 이고 SQL 삼값 논리에서 `NULL > ?` 는 참이 아니라
    마감 없는 공고는 후보에서 **조용히** 빠진다 — 이미 잠겨 있다(`JdbcCandidateSourceTest` 「마감이 없는 공고는 후보가 아니다」). 도메인 술어 `isBiddable(status, now, deadline)` 도 마감을 요구한다. 그래서 빈 마감 공고는 수집·영속되지만 평가에는 오르지 않는다
    (수집이 공고를 버리는 것보다 낫다 — 원문·정규화 결과가 남고 마감이 나중에 채워지면 갱신된다). 마감 없는 공고를 평가에 올릴지는 별도 결정이다.
27. **F2-3·F2-4 — 원문 봉쇄 규칙의 정밀도**: 멤버 접근 허용(`collection.raw-access.allowed-member-accessors`)이 **클래스 단위**라 배선(`CollectionWiring`)은 통과 전용 타입의 어떤 멤버에도 닿을 수 있다 — 지금은 원문이 포트 메서드로만 흐르므로 호출 쌍 규칙이
    막아 준다. 허용을 (클래스, 멤버) 쌍으로 좁히면 정책 파일 주석(「값을 읽지 않는다」)과 게이트가 일치한다(후속). 「관측 `canonicalize` 호출자 == {use case}」 등식도 두지 않았다(위 (2b) 표 G4 행).
28. **빈 값 규칙의 자리(D-6F8-11)**: 「값이 없거나 공백뿐이면 부재」는 `Canonicalize.kt` 의 `instantResolutionOf` 가 일시 해석 앞에서 건다 — 정본 자리는 `DateTimeInterpretation.kt` 의 `instantFrom` 이지만 그 파일이 in_scope 밖이라 손대지 않았다.
    `instantFrom` 을 다른 호출자가 직접 부르면 빈 값이 `ParseFailed` 로 나온다(오늘 호출자는 `canonicalize` 하나 — grep). in_scope 에 그 파일이 들어오면 한 줄(`raw == null || raw.isBlank()`)로 옮기고 래퍼를 지우는 것이 낫다.
29. **서드파티 반사 도구는 반사 규칙(1b)이 못 본다** — Spring `BeanWrapper`·`ReflectionUtils`·`BeanUtils`, Jackson `ObjectMapper.convertValue` 는 `java.lang.reflect` 를 이름 붙이지 않고 getter 를 읽는다. 라이브러리 타입 이름은 열거가 되어 넣지 않았다.
    workflow·app production 의 사용은 0(app 의 HTTP 직렬화는 프레임워크 내부). 후속: 원문 관측(`RawNoticeObservation`)의 항목 원문 접근자를 반사에서도 못 읽게 하는 쪽(procurement 가 in_scope 밖인 파일)이 구조적이다.

## 5b. `canonicalize` 가 던질 수 있는 자리 전수 (D-6F8-7)

실데이터를 처음 붙인 변환 지점이라 **원문 값 유래** `require`/`check`/`error`/파싱 예외가 나는 자리를 전수했다. 도출: `canonicalize` 호출 그래프의 procurement·shared-kernel 생성 자리를 읽고, 「계약 전수 × 적대적 값」
스윕(`CanonicalizeNeverThrowsTest`)을 접기 전에 RED 로 돌려 실제로 던지는 자리를 확인했다(수정 전 운영·test 정책에서 던진 계열: 공고번호·차수·금액 키 넷·낙찰하한율·업무구분 코드). 처분은 **탈락**(항목 전체) · **부재**(그 필드만 `null`, 항목은 산다) · **도달 불가**(근거) 중 하나다.

| # | 자리 | 원문 유래 실패 | 처분 | 잠금 |
|---|---|---|---|---|
| 1 | 공고번호(`NoticeNumber`) | 공백뿐 → `require(isNotBlank)` | **탈락**(번호 없음 — 어댑터 `mapRawItem` 과 같은 판단) | 개별 test · 스윕 |
| 2 | 공고번호 | 비공백 → `of`(trim·ASCII 대문자화·내부 공백 합침)가 던질 수 없다 | **도달 불가** — 비공백이면 가장자리가 비공백 문자라 `trim` 불변식이 유지된다 | 스윕(전각 공백·NUL·긴 문자열·한글 포함) |
| 3 | 차수(`NoticeRound`) | 공백뿐 → 형식 위반으로 던짐 | **탈락**(번호 없음, 어댑터와 같은 판단) | 개별 test · 스윕 |
| 4 | 차수 | 비어 있지 않지만 세 자리 ASCII 숫자가 아님(`1`·`01`·`00A`·앞뒤 공백·전각 숫자) → `require` | **탈락**(`ParseFailure(IDENTIFIER)`) — 이 값 객체가 정본이라 형식 규칙을 다시 쓰지 않고 좁은 try/catch 로 접는다(알려진 제한 21) | 세 계층 |
| 5 | 금액 파싱(`toLongOrNull`) | 비수치·`Long` 범위 초과 → `null` | 기존 **탈락**(NUMERIC, 던지지 않음). **빈·공백뿐인 값은 숫자 파싱 실패가 아니라 없는 값 — 후보를 건너뛴다**(D-6F8-11, 5c) | 스윕 · 개별 test · 5c 표 |
| 6 | 금액 값 | **음수** → `BaseAmount`·`EstimatedAmount` 의 `requireNonNegative` | **탈락**(NUMERIC) — 운영 정책 `rangeBands` 가 빈 표라 밴드가 못 막았다 | 개별 test · 스윕 |
| 7 | 금액 값 | 0 → 다음 후보로 건너뜀 | 기존(던지지 않음) | 기존 test |
| 8 | 배정예산 | `toLongOrNull` + `takeIf { > 0 }` | 기존 **부재**(음수·0) | 스윕 |
| 9 | 통화(`currencyFor`) | 단위 ≠ 원 → `error` | **도달 불가** — 금액 축 `Resolved` 는 `scale = WON_INTEGER` 계약에서만 나오고 `FieldContract` 생성자가 단위를 scale 로 강제한다(값이 아니라 정책 구성) | `FieldContractTest`(단위 쌍) |
| 10 | 출처(`provenanceFor`) | 템플릿 `NOT_APPLICABLE` → `error` | **도달 불가** — 입력이 아니라 정책 구성 오류. 운영 정책의 해석 순서 키 전부 템플릿을 갖는다 | `CanonicalizeNeverThrowsTest`(순서 키 전수) |
| 11 | `FallbackFromBudget` | `require(provenance is FilledFromBudgetKey)` | **도달 불가** — 그 분기 안에서 만든 provenance 만 넘긴다(입력 무관) | 스윕 |
| 12 | 낙찰하한율 | 비수치 → `toBigDecimalOrNull` `null` | 기존 **부재** | 스윕 |
| 13 | 낙찰하한율 | **음수** → `Rate` 의 `require` | **부재**(비수치와 같은 통로) | 개별 test · 스윕 |
| 14 | 낙찰하한율 | 극단 음의 지수(`1E-2147483647`) → `BigDecimal.divide` 의 `ArithmeticException` | **부재**(좁은 try/catch, 알려진 제한 21) | 개별 test · 스윕 |
| 15 | 낙찰하한율 | 극단 양의 지수(`1e999999999`) | 생성 성공 — 저장 열 한계는 canonicalize 밖(알려진 제한 22) | 스윕 |
| 16 | 업무구분 코드(`CategoryCode`)·라벨 | 코드 공백뿐 → `require` · 라벨 공백뿐 → 빈 `CategoryLabel` | **부재**(항목은 산다; 라벨 공백은 라벨 `null` — D-6F8-11) — 운영 정책엔 그 계약이 없어 운영에서는 도달하지 않고 test 정책에서만 | 개별 test · 스윕 · 5c 표 |
| 17 | 마감·개찰 일시 | 형식·달력 범위 위반 → `LocalDateTime.of` 예외 | 기존 **탈락**(DATE_TIME, `runCatching` 이 접는다). **빈·공백뿐인 값은 부재**(마감·개찰 `null` — D-6F8-11, 5c) | 개별 test · 스윕 · 5c 표 |
| 18 | 발주기관 코드·이름·공고명 | `AgencyCode.of`·`AgencyName.of`·`NoticeTitle.of` 는 공백이면 `null` | 기존 **부재**(비예외 팩토리) | 스윕 |
| 19 | 미지 키 수 | 집합 차 | 던질 수 없다 | 스윕 |
| 20 | `!!`·`checkNotNull`·`getValue` 계열 | canonicalize 경로 procurement main 에 원문 값에 걸린 것 0(`grep`) — 어댑터의 `it!!` 는 null 가드 뒤이고 canonicalize 밖 | **도달 불가** | 명령: `commands.md` |
| — | 명시 `null` 값 | `valueOf` 가 부재와 같게 `null` | 던질 수 없다 | 스윕(명시 null 전수) |

**canonicalize 밖**(경계로 처리 — 인프라 예외는 삼키지 않는다): 저장·원문 append 의 데이터 의존 SQL 오류는 알려진 제한 22.

## 5c. 빈 문자열·공백뿐인 값의 처리 일관성 전수 (D-6F8-11)

실수집이 처음 드러낸 결함의 뿌리: KONEPS 는 옵션 값을 키 부재·`null` 이 아니라 **빈 문자열**로 낼 때가 많다. D-6F4-8 「값이 없으면 없다」에 따라 빈·공백 값은 없는 값이어야 하는데 필드마다 처리가 갈려 있었다.
도출: 운영·test 정책의 계약 전수를 `canonicalize` 가 소비하는 자리별로 읽었고, `CanonicalizeBlankValuesTest` 의 「계약 전수 × 공백 값」 표를 **접기 전에 RED 로** 돌려 실제로 탈락을 내는 자리를 확인했다(수정 전 offender 는 일시·금액 두 계열뿐이었다).

| 필드(개념 · nullability) | 소비 자리 | 빈·공백 값 — 수정 전 | 수정 후 |
|---|---|---|---|
| 공고번호·차수(`NOTICE_NUMBER`·`NOTICE_ROUND` · REQUIRED) | `resolvedNoticeId` | 탈락(`CollectionMissingNoticeNumber`) | 그대로 — **필수 식별자 부재**가 사유다(해석 실패가 아니다) |
| 기초금액 후보(`bssamt`·`asignBdgtAmt`·`bdgtAmt` · OPTIONAL) | `evaluateCandidate` | **탈락 NUMERIC**(빈 값이 숫자 파싱 실패로) | 후보 건너뜀 — 다음 후보가 이어받고 전부 비면 기초금액 부재 |
| 추정가격(`presmptPrce` · OPTIONAL) | 같음 | **탈락 NUMERIC** | 후보 건너뜀 → 추정가격 `null` |
| 배정예산(자기 개념) | `allocatedBudgetFrom` | 부재(`toLongOrNull` `null`) | 그대로 |
| 낙찰하한율(`sucsfbidLwltRate` · OPTIONAL) | `floorRateFrom` | 부재(`toBigDecimalOrNull` `null`) | 그대로 |
| 마감·개찰예정(`bidClseDt`·`opengDt` · OPTIONAL) | `instantResolutionOf` → `instantFrom` | **탈락 DATE_TIME** | **부재(`null`)** — 형태가 있는데 해석되지 않는 값만 DATE_TIME 탈락 |
| 업무구분 라벨(`bsnsDivNm` · 문서 필수) | `businessCategoryFrom` | (코드가 있을 때) 빈 `CategoryLabel` — 센티넬 | 라벨 `null` |
| 업무구분 코드(운영 정책엔 행 없음, test 정책만) | 같음 | 부재 | 그대로 |
| 공고명(`bidNtceNm` · OPTIONAL) | `NoticeTitle.of` | 부재(trim 뒤 빈 값 → `null`) | 그대로 |
| 발주기관 코드·이름 넷(OPTIONAL, `ntceInsttNm` 은 문서 필수) | `agencyFrom` | 부재(비예외 팩토리 `null`) | 그대로 |
| 시공능력평가 목록·개찰 축 필드 | `canonicalize` 미소비 | — | — |

**REQUIRED 빈 값의 사유는 「해석 실패」가 아니다.** `canonicalize` 는 `FieldNullability` 를 소비하지 않는다(선언·생성자 밖 사용 0 — grep) — 필수로 **강제**되는 것은 식별자 둘뿐이고 그 빈 값의 사유는 이미
`CollectionMissingNoticeNumber`(필수 식별자 부재)다. 다른 문서 필수 필드(`bsnsDivNm`·`ntceInsttNm`)는 필수로 강제하지 않는다 — 빈 값은 부재로 접고 항목은 산다(공고를 문서상 필수 필드가 비었다는 이유로 버리지 않는다.
공고명을 옵션으로 완화한 D-6F8-2 와 같은 결정). 새 사유 어휘는 만들지 않았다(어휘 파일 `Accounting.kt` 는 in_scope 밖 — 필요해지면 멈추고 보고한다).
**앞뒤 공백이 붙은 비공백 값**은 필드마다 다르다: 금액·공고명·번호는 trim 뒤 해석하고, 일시·낙찰하한율은 trim 하지 않아 형태 위반(일시는 DATE_TIME 탈락, 하한율은 부재)이다 — 비대칭이지만 실데이터 관측이 없어 고치지 않았다(5d 의 진단 질의가 재는 축 하나).

**왜 이 표가 기존 스윕에 안 잡혔나**: `CanonicalizeNeverThrowsTest` 는 빈 값(`""`·`" "`)을 적대적 값 표에 이미 넣었지만 **던지는지만** 단언한다 — 빈 값이 탈락을 내도 던지지 않아 초록이었다. 합성 fixture 도 빈 일시를 담지 않았다.
새 표는 **결과의 종류**(식별자를 뺀 어느 계약 필드도 빈 값으로 항목을 탈락시키지 못한다)를 잠근다.

## 5d. 실수집에서 일시 형태가 정상인데도 DATE_TIME 로 탈락한 5건의 진단 (D-6F8-11, 정적 — 원문 값은 보지 않았다)

- **계약이 읽는 일시 키는 둘뿐이다**(정책 전수): 마감 `bidClseDt`·개찰예정 `opengDt`. 응답의 다른 일시 키(실수집 관측: 빈 것 일곱 · 정상 형태 다섯)는 계약 밖이라 **미지 필드 계수로만** 남고 항목을 탈락시킬 수 없다 —
  「계약에 있는 다른 일시 키가 빈 문자열이라 같은 결함에 걸렸다」는 가설은 **기각**이다(`CanonicalizeBlankValuesTest` 「실수집이 낸 형태 …」가 그 키 조합으로 재현해 정규화·미지 필드 계수를 단언).
- 그러므로 5건의 `DATE_TIME` 은 `bidClseDt` 또는 `opengDt` **자기 값**에서 났다. 정규식이 형태(`YYYY-MM-DD HH:MM:SS`, ASCII 공백 하나)를 통과한 값이 해석에 실패하는 길은 **달력·시각 범위 위반**(월 0·13 이상, 그 달에 없는 일, 시 24 이상, 분·초 60 이상 —
  `0000-00-00` 류 자리표시자 포함)뿐이다. 앞뒤 공백·비ASCII 공백·초 없는 형태는 **형태 불일치**로 자리를 달리한다. 실수집 관측에서 두 일시 키 중 `bidClseDt` 는 「빈 것」·「정상 형태」 어느 목록에도 들지 않아
  5건 가운데 일부는 마감이 **빈 값**이었을 가능성이 있고, 그렇다면 이 수정으로 함께 풀린다.
- **처분**: 값을 보지 않고 고치지 않았다 — 형태가 있는데 해석이 안 되는 값은 계약(D-6F8-11)대로 DATE_TIME 탈락이다. **수정 뒤 실수집 3차에서 남는 DATE_TIME 탈락이 곧 이 5건의 정체**이며, 남으면 아래 진단 질의(**계수만**)로 분류한다:

```sql
SELECT k, kind, count(*) FROM (
  SELECT k, CASE
    WHEN nullif(btrim(v), '') IS NULL THEN 'blank'
    WHEN v !~ '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$' THEN 'shape-mismatch'
    WHEN substr(v,6,2)::int NOT BETWEEN 1 AND 12 OR substr(v,9,2)::int NOT BETWEEN 1 AND 31
      OR substr(v,12,2)::int > 23 OR substr(v,15,2)::int > 59 OR substr(v,18,2)::int > 59 THEN 'range-invalid'
    ELSE 'shape-ok'
  END AS kind
  FROM raw_observation, LATERAL (VALUES ('bidClseDt', payload_fields->>'bidClseDt'), ('opengDt', payload_fields->>'opengDt')) AS t(k, v)
) classified GROUP BY 1, 2 ORDER BY 1, 2;
```

  `shape-ok` 에 남는 것은 그 달에 없는 일(2월 30일 류)이다. 이 질의는 `raw_observation` 의 계약 필드 투영 열만 읽고 값을 내지 않는다(evidence 에는 이 건수만).

## 6. 실수집 실행 명령 형태 (팀장, D-6F8-5 2번)

비밀은 **명령 문자열·argv·셸 history·에이전트 transcript 어디에도 값이 나타나지 않는 형태**로 넘긴다 — 서브셸에서 파일로부터 읽은 값을 **그 프로세스 환경에만** 넘긴다(치환 전 식만 기록에 남는다).
키 변수 이름은 적지 않고 자리표시자로 둔다:

```
( BIDVECTOR_KONEPS_SERVICEKEY="$(sed -n 's/^<legacy 원문형 키 변수 이름>=//p' ../bid-vector/.env | tr -d '\r"')" \
  BIDVECTOR_COLLECTION_MODE=once BIDVECTOR_COLLECTION_FROM=<오늘-30일, ISO 날짜> BIDVECTOR_COLLECTION_TO=<오늘> \
  BIDVECTOR_COLLECTION_CATEGORIES=construction,service \
  BIDVECTOR_PERSISTENCE_JDBCURL=<개발 DB JDBC URL> BIDVECTOR_PERSISTENCE_USERNAME=<…> BIDVECTOR_PERSISTENCE_CREDENTIAL=<…> \
  OPERATOR_CREDENTIAL_VALUE=<임의의 비어 있지 않은 값 — 부팅 요건> BIDVECTOR_EVALUATION_CANDIDATECAP=1000 \
  SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  java -jar app/build/libs/app.jar )
```

규율: `export`·`set -x`·`echo`·`-D…`·`--…=`·`bootRun --args`·`docker run -e NAME=값` 금지(argv·환경에 값이 실려 `ps` 로 보인다; 컨테이너면 `-e NAME`(값 없이 통과)). 실행 중 `-Djdk.httpclient.HttpClient.log`·`jdk.*` 로거 DEBUG 금지.
legacy `.env` 에는 키 변수가 **둘**(원문형·인코딩형)이다 — `ServiceKey` 가 스스로 URL 인코딩하므로 **원문형**을 넘긴다(인코딩형은 이중 인코딩으로 `resultCode 30` + 쿼터만 소모). 형태 확인이 필요하면 `grep -c` 같은 **계수만** 본다.
기본 URL(`bidvector.koneps.base-url`)은 https 이거나 loopback 만 받는다(평문 http 외부 호스트는 기동 실패). 확인 뒤 개발 DB 는 `docker rm -f -v` 로 폐기한다.

확인할 것(evidence 에는 **건수·비율만**): 슬롯별 회계 · `notice` 행 수 · `notice_title` 비어 있지 않은 비율 · `collection_run` 행 수 · 재실행 등식(슬롯마다 `normalized = 0`, `duplicate = 1차 normalized + 1차 duplicate`,
`unchanged = 1차 inserted + updated` — 실행 사이에 공고가 바뀌지 않았다는 전제, 알려진 제한 3) · 담당자 키가 있는 원문 행 수(계수만):
`SELECT COUNT(*) FROM raw_observation WHERE payload LIKE '%ntceInsttOfcl%' OR payload LIKE '%dminsttOfcl%'`. 슬롯 원인이 연달아 `StructureFailure` 로 찍히면(알려진 제한 16) 실행자가 중단한다. `collection failed cause=…sqlState=22P05`(또는 `22021`)가 찍히면 원문에 NUL 이 있는 것이므로(알려진 제한 22 — 재실행해도 같은 항목에서 멈춘다) 멈추고 보고한다.

출력은 `collection start` · 슬롯마다 `collection slot …` · (쿼터 멈춤이면 `collection halted …`) · `collection finished … exit=<0|2>` 줄이고, 종료 코드 0 = 전 슬롯 완료,
2 = 절단·쿼터 멈춤(회계에 원인), 1 = 실패(`collection failed cause=<코드>`). 이 형태의 부팅은 `commands.md` 의 스모크로 실측했다(mock KONEPS · 버릴 Postgres 컨테이너 · 실호출 0).

## 7. 리뷰 요청 조건 점검

- [x] 구현 diff 커밋됨 — in_scope 경로 clean-tree(`commands.md`, 양성 대조 1회 포함)
- [x] acceptance 셋 exit 0 (`commands.md`) — 캐시 우회 `check` 는 버릴 worktree
- [x] test/lint/type/architecture/contract 전부 — 부분 게이트 없이 CI job 명령 그대로
- [x] fixture·정책 version 근거 기록(4항)
- [x] 알려진 제한·rollback 기록(5항 · `rollback.md`)
- [x] 비밀값 스캔 — 패턴 파일 참조형(`commands.md`), 육안: evidence 에 키·공고 원문·개인정보 없음
- [ ] 실수집 확인(D-6F8-5) — 팀장, verifier ready-for-review 뒤
