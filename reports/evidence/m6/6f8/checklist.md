# M6/6F-8 checklist — 수집 배선 · 공고명 수집

정본 계약은 `scope.md`. 이 문서는 그 계약을 **구현이 어떻게 닫았는가**의 대응표와 알려진 제한, 리뷰 요청 조건이다.
개수·SHA 는 적지 않는다 — 명령이 낸다(`commands.md`).

## 1. 위협 모델 우회 일곱 ↔ 닫는 장치

| # | 우회(scope.md) | 닫는 장치 | 잠금 test·게이트 |
|---|---|---|---|
| 1 | use case 가 `canonicalize` 를 건너뛰고 원문 키를 직접 읽는다 | 정규화 지점 하나. use case 패키지의 procurement 참조 **집합 == 허용 집합**(원문 키 접근 타입 부재), 통과 전용 타입(원문·정책·정규화 결과)의 멤버 접근 0, app 은 수집 포트 넷과 `canonicalize` 를 못 부른다(기존 「호출 쌍」 규칙에 포트를 더하고 허용 쌍 0) | `CollectionArchitectureGateTest` · 음성 `CollectionArchitectureGateCatchesViolationsTest`(fixture `RogueRawKeyReader`·`RogueCollectionPortCaller`) · `CollectNoticesUseCaseTest`(영속 명령의 원문이 저장된 관측 그 객체, 공고명이 계약 경유) |
| 2 | 공고명 키를 어댑터·use case·러너에 문자열로 박는다 | 키는 `NOTICE_TITLE` 계약 행만 나른다. 상수 풀에 그 리터럴을 가진 production 클래스 집합 ⊆ {정책 파일 클래스} — 키 값은 게이트가 **정책에서 읽는다** | `CollectionArchitectureGateTest`(허용 집합을 비우면 정확히 정책 클래스가 걸림 — 규칙이 공허하지 않음) · 음성 fixture `RogueTitleKeyLiteral` · `NoticeTitleCanonicalizeTest`(계약 키를 바꾸면 그 키를 따름) |
| 3 | 탈락·중복을 세지 않고 버린다 | 소스 회계 + 정규화 탈락 + 저장 결과로 최종 회계를 다시 만들고 `CollectionAccounting` 생성자 등식이 재검사. 탈락은 사유별, 원문은 탈락 항목도 먼저 저장 | `CollectNoticesUseCaseTest`(회계 등식·사유별 합·탈락 항목 원문 저장·저장 결과 넷의 분류) · `CollectionRunnerE2ETest`(DB 합계) |
| 4 | 서비스 키가 예외 메시지·로그로 샌다 | 키는 배선 한 곳에서만 원문으로 읽히고(`KonepsCredentialProperties` 참조 집합 = {배선}) 로거 사용 집합 = {배선}. 러너 실패는 원 예외를 잇지 않는 정제된 예외(원인 코드만). 전송 실패 detail 은 배치 결과로 나가지 않는다 | `KonepsServiceKeyLeakTest`(연결 거부·5xx·429·깨진 본문·지연, 서버가 URL 을 되돌려도) · `CollectionRunnerTest`(SQL 상세·요청 URI 실린 예외 → 원인 코드만) · `CollectionRunnerE2ETest`(전 로거 DEBUG 캡처에 원문·인코딩 키 부재, 표본이 키를 실제로 실었음을 mock 이 받은 질의로 확인) · 구조 게이트 둘 |
| 5 | 러너가 기본 켜짐 / 상한 없이 돈다 | `CollectionWiring` 전체가 `mode=once` 조건부. 범위·업종·키 오류는 기동 실패. 러너 타입 참조 집합 = {`CollectionRunner`}. 범위는 생성 경로 하나(`CollectionRange.of`) + 정책 생성자 `internal` | `CollectionWiringTest`(속성 없음 → 러너·키 바인딩 없이 뜸, mode 다른 값 4종, 상한 32일·미래·역전·상한 정확히 31일 경계, 미지·빈·형식 오류 업종, 키 부재·빈 값, 환경변수 형태) · `CollectionRangeTest` · 구조 게이트 |
| 6 | 쿼터 초과 뒤에도 계속 부른다 | `TruncationCause` 를 else 없는 `when` 으로 {이어 읽기, 다음 슬롯, 실행 멈춤} 에 매핑 — 새 원인은 컴파일이 막는다. 멈춘 자리와 남은 슬롯을 결과에 싣는다 | `CollectNoticesUseCaseTest`(쿼터 소진 뒤 **소스 호출 계수 0**, 쿼터 아닌 원인 아홉 종은 전부 다음 슬롯 호출) · 러너 종료 코드 2 |
| 7 | 신설 게이트 미등재 | `gate-tests.properties` 에 새 test class 전부 등재(workflow 는 양방향 등재 test 가 강제) | `gateExecutionGate`(Kotlin `check`) |

거동 쪽 확인: 재실행 멱등(새 notice 행 0, 저장 Unchanged 가 duplicate 로) · 공고명이 `notice_title` 에 계약 경유로 저장 ·
`collection_run` 행 = 조회일 × 업종 — `CollectionRunnerE2ETest`(Testcontainers + mock KONEPS, 실호출 0).

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
| `KonepsCredentialProperties` | 서비스 키 원문 | 닫는다 — data class 아님(test 로 잠금), 참조 집합 = {배선}, 기본값 없음 |
| `CollectionWiring` 빈 메서드 | 조립 | 경계로 처리 — `open fun`(Spring 프록시 요건)이라 test 가 부를 수 있으나 그러려면 `KonepsCredentialProperties` 를 이름 붙여야 해 참조 집합 게이트가 잡는다 |
| ArchUnit 규칙·정책 키 | 게이트 | — |

**`object`/companion 주입 자리**: 새로 생긴 것 없음(`CollectionSourceName` 의 정규식은 private).

## 3. 새 파일 ↔ in_scope 대조 (A·M 둘 다)

`in_scope` 밖 경로 **0** — 판별: `git diff --name-status <base>..HEAD` 의 각 경로를 `scope.md` `in_scope` 항목에 대조.
사용한 in_scope 항목: workflow collection main·test · procurement 세 파일(`FieldContract`·`CollectionPolicy`·`Canonicalize`) +
procurement test · adapters koneps test · app collection main · app wiring main · app test(러너·배선·게이트·배포물 내용) · `archfixture/violating` ·
`app/build.gradle.kts`(kotlin-reflect 의존 한 줄 + bootJar 경로 주입, 아래 5항 13번) ·
`gate-tests.properties`·`architecture-policy.properties`(추가만) · `docs/discovery/**`(data-dictionary) · evidence · `milestone-6.md`(팀장 문단).
**쓰지 않은 in_scope 항목**: `KonepsPresentInSets.kt`(공고명 행이 `NOTICE_LIST` 기본값) · adapters main koneps(키 누출 자리 없음 — test 만) ·
adapters persistence(title 영속은 이미 됨) · `workflow/build.gradle.kts`(의존 추가 불요) · slf4j 는 Boot 스타터가 제공 ·
`member-effects*`(workflow 효과 도출 변동 없음) · `fixtures/**`·`expected/**`(공고명 필드가 corpus 기대값에 안 닿음 — 아래 4항).
마이그레이션 없음(`out_of_scope` 준수).

## 4. fixture·정책 version 근거

- corpus: `bidNtceNm` 을 가진 input fixture 는 `koneps-collection-019` 하나(공고번호 없는 행에 실린 문자열). 그 case 의 `verified_paths` 에 미지 필드 수가
  없어 기대값·`manifest.yaml` sha 무변경 — `SharedKernelCorpusConformanceTest` 가 Kotlin `check` 안에서 초록.
- 정책 version: `KONEPS_COLLECTION_POLICY` 는 `EffectiveFrom.Initial` 단일 항목 그대로 — 필드 계약 행 하나 추가는 운영자 지시(2026-09-24)가 승인.
  `COLLECTION_RANGE_POLICY`(신설, 상한 31일)의 근거는 `scope.md` D-6F8-3.

## 5. 알려진 제한

1. **D-6F8-1 「원문 저장 + 영속 한 트랜잭션」 문면과 다르다** — 항목마다 원문을 먼저 커밋하고 canonical 은 항목 트랜잭션 하나로 저장한다. 근거: `JdbcNoticeRepository`
   는 `TransactionBoundary` 참여자가 아니고(개조하면 `Rejected` 경로의 `rollback()` 이 원문 append 까지 되돌린다), `RawObservationStore` 계약(⑤)이 「이후 단계가
   실패해도 이미 커밋된 원문은 남는다」다. 실패 창: 원문 저장 뒤 저장소 예외 → 원문은 남고 canonical 은 없다(재실행이 새 원문 행을 추가하고 canonical 을 정상 저장).
   **계약 문면 정정은 팀장 레인.**
2. `collection_run` 표에 **업종 열이 없다** — 같은 조회일의 공사·용역 행이 DB 에서 구별되지 않고(둘 다 `NOTICE_LIST`) 개수만 조회일 × 업종과 같다. 업종별 회계는 러너 로그와
   결과 타입에 있다. 열 추가는 마이그레이션이라 하지 않았다 — `OPEN-6F8-COLLECTION-RUN-CATEGORY` 신설 제안.
3. 저장 `Rejected`(권위 없는 유입이 권위 값을 덮으려 함)는 탈락 사유 어휘가 없어(procurement `Accounting.kt` 는 in_scope 밖) 회계에서 `duplicate` 로 접히고 거부 건수는
   결과 타입 `WriteTally.rejected` 와 `rejected_write` 감사 표에만 있다(`collection_run` 에는 없음).
4. 저장소 인프라 예외는 삼키지 않는다 — 그 슬롯의 회계는 남지 않고 실행이 실패로 끝난다(앞서 끝난 슬롯의 회계·이미 커밋된 원문·canonical 은 남는다).
5. 이어 읽기(`MaxPages`)에는 횟수 상한이 없다 — 소스의 `totalCount` 도달·빈 페이지·「같은 커서 재요청」 가드로 끝난다. 끝없이 새 페이지를 내는 소스는 가정하지 않는다.
6. 러너 실패는 **원인 코드만** 남긴다(원 예외 메시지·스택은 로그·전파 어디에도 없음) — 진단은 슬롯 진행 줄과 재실행. 절충이다(SQL 상세에 행 값이 실릴 수 있음).
7. JDK `HttpClient` 자체의 디버그 로깅(`-Djdk.httpclient.HttpClient.log=…`)을 켜면 JDK 가 요청 URI(키 포함)를 남긴다 — 코드 밖의 실행 규율이다(실행 명령에 넣지 않는다).
   E2E 에서 확인한 사실: DEBUG 전 로거 캡처에서 인코딩 키가 나온 유일한 곳은 **같은 JVM 의 mock 서버(`com.sun.net.httpserver`)의 서버 쪽 로그**였고 그것은 시험 대상이 아니라 캡처에서 뺐다.
8. 「오늘」(범위 상한의 `to ≤ 오늘`)은 KST 달력일이며 `workflow.evaluation.OPENING_DATE_ZONE`(이름은 개찰일용)을 재사용한다 — `Asia/Seoul` 리터럴 중복을 피한 선택.
9. 러너가 켜진 프로세스도 기본은 웹 서버가 함께 뜬다 — `spring.main.web-application-type=none` 을 쓴다(그 형태를 E2E 재실행이 실측). 부팅 요건(운영자 자격증명 값·후보 상한)은 러너와 무관하게 남는다.
10. `ntceNm`(legacy 둘째 키)은 어느 문서에도 없어(`policy-values.md` §1.7.6) 등재하지 않았다 — 실수집 원문에서 확인한 뒤 후속.
11. 정본 승인 문서 `reports/evidence/m3/3a/policy-values.md` §1.3 의 공고명 행·P 표 등재는 **in_scope 밖·팀장 레인**(P-14 선례). 이 slice 는 `data-dictionary.md` §6.3.3 만 썼다.
12. 응답 shape 는 정책 문서 기반 합성 fixture 다 — **실 KONEPS 미검증**이다(D-6F8-5 실수집이 판정).
13. **기존 결함을 고쳤다(범위 밖 발견, in_scope `app/build.gradle.kts` 안)** — 배포물 `bootJar` 는 `kotlin-reflect` 없이 나가 `java -jar app.jar` 가 `@ConfigurationProperties` 의 Kotlin 생성자
    바인딩에서 즉시 죽었다(`PersistenceProperties` 부터). test 런타임 classpath 에는 그 라이브러리가 이미 있어(다른 라이브러리 경유) production 조립을 Testcontainers 로 부팅하는 test 가 전부
    초록이었다 — `:app:dependencyInsight` 실측: `runtimeClasspath` 없음 · `testRuntimeClasspath` 있음. 의존 한 줄을 더하고 `BootJarRuntimeClasspathTest` 가 산출물을 열어 잠근다(의존을 지우는 변이 RED,
    `commands.md`). 고친 jar 의 실행은 같은 문서의 스모크가 실측했다.
14. 이 slice 가 승인 문서 `data-dictionary.md` 에 줄을 넣어 그 파일을 가리키는 `file:line` 인용이 밀리는지 본다: 인용은 세 좌표(그 문서의 삽입 지점보다 위쪽)뿐이라 밀리지 않는다 — 명령: `commands.md`.

## 6. 실수집 실행 명령 형태 (팀장, D-6F8-5 2번)

비밀은 **환경변수 이름만** 적는다 — 값은 그 프로세스 환경에만 넘긴다(셸 history·파일에 남기지 않는다).

```
BIDVECTOR_COLLECTION_MODE=once
BIDVECTOR_COLLECTION_FROM=<오늘-30일, ISO 날짜>   BIDVECTOR_COLLECTION_TO=<오늘>
BIDVECTOR_COLLECTION_CATEGORIES=construction,service
BIDVECTOR_KONEPS_SERVICEKEY=<legacy .env 의 키>
BIDVECTOR_PERSISTENCE_JDBCURL=<개발 DB JDBC URL>  BIDVECTOR_PERSISTENCE_USERNAME  BIDVECTOR_PERSISTENCE_CREDENTIAL
OPERATOR_CREDENTIAL_VALUE=<임의의 비어 있지 않은 값 — 부팅 요건>   BIDVECTOR_EVALUATION_CANDIDATECAP=1000
SPRING_MAIN_WEB_APPLICATION_TYPE=none
java -jar app/build/libs/app.jar
```

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
