# M6/6A-2b — 결정별 판정과 남는 것

정본은 `scope.md`(D-6A2b-1~54, 위협 모델, 우회 아홉, (2b) 표). 여기는 **그 각 줄이 무엇으로 닫혔는지**와
**닫히지 않은 것**을 적는다. 명령·종료 코드·변이 결과는 `commands.md`.

## 결정별 판정

| 결정 | 무엇으로 닫혔나 | 남는 것 |
|---|---|---|
| D-6A2b-1 endpoint 여섯 | 컨트롤러 하나가 실행기만 호출. 세션 id 는 서버가 만든다(UUID) | 도달 불가한 `SessionAlreadyActive` 는 문서에서 뺐다(D-6A2b-24) |
| D-6A2b-2 어댑터가 draft 를 조립 | 현재 전략을 초안으로 내보내 **그 필드 하나만** 바꾼다. `MaxActiveBids` 필드 추가, 스냅숏 codec 왕복 | 마이그레이션 0(목표 그대로) |
| D-6A2b-3 한 트랜잭션 | 어댑터 층이 요청마다 경계를 열고 use case 를 조립. 장애 주입(DB 트리거로 outbox INSERT 거부) 실측: 전략 revision·세션 버전 불변, outbox 0행 | — |
| D-6A2b-4 외부 effect 0 | 발송기·소비자 배선 없음. 이벤트는 outbox 행으로만 남는다(E2E 가 DB 로 확인) | — |
| D-6A2b-5 필터 무편집 | 두 파일 diff 0. 새 경로가 기존 필터 체인에 덮이는 것을 기계 전수가 잰다 | 상수 두 자리는 문면이 아니라 **실측**으로 묶었다(audit 주체 == outbox 봉투 actor) |
| D-6A2b-6 결과 → 상태 코드 | 전수 `when`(`else` 없음) 셋. 거부 사유 일곱 전부 409 | 값 위반의 **사유**는 응답에 없다(알려진 제한 ③) |
| D-6A2b-7·20·21 형식 표면 | 출하 조립에서 거둔 매핑 전수 × 미선언 메서드 → 500 이 0. 415·400·**406** 축. 「두드림 ∪ 이유 붙은 제외 == 전체 매핑」 | 406 의 **본문은 비어 있다**(알려진 제한 ④) |
| D-6A2b-8·19·26 의존 게이트 | 대상은 **`bidvector.app` 전체**이고 면제는 계약 파일의 정확한 이름뿐이다. 술어는 **허용 목록 ⊆** — 능력 포트는 use case 생성자에서 도출하고 `java.sql` 은 다시 판다 | 허용 접두 안에 포트 인터페이스가 생기면 도출이 편집 use case 하나라 걸리지 않는다(알려진 제한 ⑨) |
| D-6A2b-32 면제 세 층 | ① 부팅·배선 11 ② 요청 스코프 4 ③ 수집 레인 4. 세 층이 겹치지 않고 합이 면제 전체임을 단언한다. 층마다 **다른 규칙**이 선다(아래 D-6A2b-34~37) | ② 층이 dry-run 경로의 요청 스코프 어댑터 셋을 **이름으로** 받는다(알려진 제한 ⑬) |
| D-6A2b-27 HTTP 표면 실측 | 출하 조립의 두 서블릿 컨텍스트에서 handler·Filter·Servlet 집합을 거둬 계약 집합과 등식. 모르는 `HandlerMapping` 종류는 이름이 붙은 표기로 남아 등식을 깬다 | 여러 `RouterFunction` 을 구분하지 못한다(오늘은 0 이라 등장 자체가 신호다) |
| D-6A2b-28 기준 revision 단일 읽기 | 어댑터가 draft 를 만든 **그 읽기**의 revision 을 command 에 싣고, value 시점에 현재와 다르면 거부한다 | — |
| D-6A2b-29 406 | **본문 없음이 계약이다.** 도달 불가한 코드는 어휘에서 뺐다 | 추적은 audit 행뿐이다(알려진 제한 ④) |
| D-6A2b-30 게이트 등재 | 이 slice 의 게이트·거동 test 아홉을 `gateExecutionGate` 에 등재. `@Disabled` 대조로 RED 실측 | — |
| D-6A2b-9 스캔 필터 복원 | Boot 기본 `excludeFilters` 둘 + 애너테이션 집합 등식. 빈 집합은 출하 조립 부팅 test 가 잰다 | 「영향 0」은 **거짓이었다**(아래 수확 ①) |
| D-6A2b-10 자격증명 재측정 | 참조자 집합 == {필터, 조립 지점}(집합 등식) | 완전 폐쇄는 6E |
| D-6A2b-11·24 만료 | 접근 시점 fold 만. 주기 sweep 없음 | **버려진 세션은 접히지 않는다** — `OPEN-6A2B-ABANDONED-SESSIONS`(→ 6B-3) |
| D-6A2b-12 OpenAPI | 경로 여섯·스키마 다섯·오류 코드 열셋. `field`·`state` enum 양방향 등식, **문서 (경로,메서드) == 등록 매핑** 등식 | 405 는 어느 operation 에도 적지 않는다(operation 의 응답이 아니다 — 문서에 사유) |
| D-6A2b-13 컨테이너 스모크 | 쓰기 왕복 + 무인증 401 + 미선언 메서드 405. 로컬 실측 통과, 정본은 CI | — |
| D-6A2b-18 기준 revision | `WaitingForConfirmation` 이 draft 를 뜬 revision 을 담고 confirm 이 **두 축**을 본다. 기준 없는 행은 fail-closed | 알려진 제한 ①(동시 세션 자체는 허용된다 — 덮어쓰기만 막는다) |
| D-6A2b-22 `toDraft` 전수성 | 왕복 등식 + 정의역 등식(리플렉션) | — |
| D-6A2b-23 정책 빈 하나 | 조립이 한 번 해소해 저장소·편집 트랜잭션·실행기가 공유 | — |
| D-6A2b-25 일괄 | 알 수 없는 본문 키 400 · 십진 척도·유효숫자 상한 · 세션 필드 강제 · 방어 메시지에서 값 제거 · 실 DB 무효 값 영속 0 · 문서 넷(C-1~4) | TRACE·migration 순서는 알려진 제한 ⑤⑥ |
| D-6A2b-34 ① 층 HTTP 확장 API 금지 | 면제의 **종류를 세지 않는다** — 금지 접두 일곱에 대한 의존 자체를 막는다(직접 의존 + 클래스·메서드 애너테이션). 예외는 계약 파일의 **타입** 하나(`FilterRegistrationBean`). 타입 목록이라 면제된 클래스 안에서도 다른 확장점은 열리지 않는다 | 변이 A1·A2·A6 이 RED. 허용된 그 타입으로 새 필터를 올리는 길은 남고, 그것은 표면 실측이 잡는다(탐침 N2) |
| D-6A2b-35 ② 층 정확 목록 | `bidvector.app.wiring` 패키지 허용을 버리고 **정확한 app 클래스 다섯**으로 | 변이 A3 이 RED |
| D-6A2b-36 어댑터 호출 삼중쌍 | (호출자, 인터페이스, 메서드) 목록으로만. 6A-3 의 호출 쌍 규칙 형태를 재사용했다 | 변이 A5 가 RED |
| D-6A2b-37 어댑터 예외 | 정확 목록 셋 + `Throwable` 이 준 멤버만 | 변이 A4 가 RED |
| D-6A2b-38 지적 일괄 | MEDIUM 여섯·LOW 여섯. 커널 둘(기준 값 선택화·재전달 지문), 표면 실측 둘(매핑 빈 집합·composite 위임 근거), 음성 대조 하나, 진단 하나, 등재 완전성 meta-gate 하나, 정렬·번호 | 없음 |
| D-6A2b-41 능력 전달 | ① 읽기 자리는 `save` 없는 읽기 port 를 받는다 ② ② 층의 workflow 허용을 정확 타입 목록으로(패키지 둘 제거) + 쓰기 능력 포트를 **서명·구현으로 쥐는 것** 금지 ③ 능력 포트를 받는 use case 의 조립은 등재된 (호출자, 타입) 쌍에서만, 편집 use case 는 **쌍이 없다** | ② 층이 use case 를 **쥐는 것**은 허용된다(알려진 제한 ⑯) · 다운캐스트는 게이트에 보이지 않는다(⑰) |
| D-6A2b-42 예외 정확 목록 | `isAllowed`·`tier2Allows` 의 어댑터 갈래가 `fullName in 목록` 으로 바뀌었다 | — |
| D-6A2b-43 fail-closed | `disallowedAdapterCall` 의 「그 밖」 갈래 제거. 오늘 도는 ② 층 호출 **둘**을 등재 | — |
| D-6A2b-44 서술자 | 쌍 좌표가 넷(`호출자\|선언 타입\|메서드\|서술자`) | — |
| D-6A2b-45 규칙별 음성 fixture | 규칙에 이름표(`AppRuleId`)를 붙여 **규칙별로** 판정. 규칙 하나를 공집합으로 바꾸면 그 규칙의 줄이 RED — r4 에 일곱, r5 가 여덟째(주입 표면)를 더했고 **여덟 전부 실측** | — |
| D-6A2b-46 게이트 신뢰도 | 형제 접두 하나 추가 · meta-gate 를 전수·양방향으로 · `gate-tests.properties` 를 선언된 입력으로 · OQ-1 KDoc · OQ-2 실측(404) | meta-gate 를 **JUnit 태그가 아니라 전수**로 세웠다(아래 대조표) |
| D-6A2b-47 문면·LOW | KDoc 넷 · ①-b 순서의 귀결을 전이표 행으로 · 「값 제출만 거부」 단언 · N-r4-9~14 · L-r4-1~3 | — |
| D-6A2b-49 주입 표면 | 제한 층·② 층이 **받을 수 있는** 타입의 정확 목록. 생성자 매개변수와 주입 애너테이션이 붙은 필드·세터를 모아 제네릭 인자·함수 타입 인자까지 전개한다. **범용 운반 타입은 목록에 못 오른다**(구조 판정) · 목록 == 오늘 주입 표면(등식) · 참조 허용 접두는 적용하지 않는다 | 예외 **하나** — 무편집으로 묶인 audit 필터의 함수 타입(아래 이탈 절) · 목록은 **타입**이고 구현은 ① 층이 고른다(알려진 제한 ⑱) |
| D-6A2b-50 일괄 | ② 층 능력 포트 거부 갈래 + 목록에서 `StrategyRepository` 와 죽은 항목 셋 제거 · 목록 등식 · 능력 보유의 제네릭 전개 · 포트 호출 게이트의 전략 읽기 복원 · 제한 층의 `StrategyReader` 직접 참조 금지 · meta-gate 발견을 컴파일된 test 클래스 전수로 · KDoc·주석·키 이름 · LOW 일괄 | — |

## 계약 문면 ↔ 구현 심볼 대조 (D-6A2b-34~47)

r4 의 차단 셋은 전부 「계약은 그렇게 말하는데 코드는 다르다」였다. 문장마다 그것을 지는 심볼을 적는다.

| 계약 문장 | 구현 심볼 | 이 문장을 잰 것 |
|---|---|---|
| 34 ① 층은 HTTP 확장 API 접두에 의존 못한다 | `dependOnHttpExtensionApi` + `tier1.forbidden-http-packages`(여덟) | 변이 A1·A2 |
| 34 예외는 **정확한 타입 목록** | `tier1.http-api-types`(하나) | — |
| 34 클래스 애너테이션도 의존 | `item.annotations` (+ 필드·생성자·메서드) | 변이 A6 |
| 35 ② 층 정확 app 클래스 | `tier2.allowed-app-classes`(다섯) | 변이 A3 |
| 36 어댑터 인터페이스 호출은 쌍으로만 | `callAdapterMemberOutsideContract` | 변이 A5 |
| 37 예외는 **정확 목록 소속** | `isAllowed`·`tier2Allows` 의 `fullName in appAdapterExceptionTypes` | 변이 A4(타입 축) |
| 37 `Throwable` 이 준 멤버만 | `throwableMemberViolation` | fixture `RogueExceptionOwnMember` |
| 41 ① 읽기 자리는 `save` 없는 port | `StrategyReader` · `StrategyRepository : StrategyReader` | 변이 F-r4-1 |
| 41 ② 정확 타입 목록 | `tier2.allowed-workflow-types`(**스물**) | 탐침 P2 · r5 의 목록 등식이 능력 포트 하나와 죽은 항목 셋을 걷어냈다 |
| 41 ② 능력 포트는 구조 도출(손 목록 금지) | `capabilityPorts = derivedUseCasePorts − ambientPorts` | 집합 등식 test |
| 41 ② 쓰기 능력 포트를 쥐지 못한다 | `holdCapabilityPort`(필드·생성자·메서드 서명·구현) | 변이 F-r4-1 |
| 41 ③ 편집 use case 조립은 어댑터 경계 한 곳 | `callUseCaseConstructorOutsideContract`, 편집 use case 쌍 **0** | 변이 F-r4-1 · 탐침 P1 |
| 41 ③ dry-run 은 한 호출자 고정 | `workflow.use-case-construction-pairs`(하나) | 변이 F-r4-1(b) |
| 42 | 위 37 첫 행과 같은 자리 | 변이 A4 |
| 43 fail-closed, 등재 쌍 둘 | `disallowedAdapterCall` 에 「그 밖」 갈래 없음 · `adapter.member-call-pairs`(둘) | 오늘 도는 호출 둘이 등재됨 |
| 44 서술자 | `MemberSignature` · `callPairKey` | 오버로드 변이 |
| 45 규칙마다 fixture · 공집합 대조 | `AppRuleId`(**여덟**) · `RuleNegativeSamples` · 규칙별 비공허성 test | 공집합 **여덟 전부** RED |
| 46 형제 접두 | `boot.webmvc` 추가 · `isUnder` 는 `.` 경계 | — |
| 46 meta-gate 를 구조로 | 전수·양방향(D-6A2b-48 수용) + **발견을 컴파일된 test 클래스로**(D-6A2b-50) | 세 형태 RED — r4 의 이탈 ①은 닫혔다 |
| 46 선언된 입력 | `app/build.gradle.kts` `inputs.file(gateTests)` | 재실행 실측 |
| 46 OQ-1 | `dependOnHttpExtensionApi` KDoc | verifier r4 실측 인용 |
| 46 OQ-2 | 실측(새 게이트 없음) | 404·404·404 |
| 47 | KDoc 넷 · 전이표 행 · 「값 제출만 거부」 단언 · N-r4-9~14 · L-r4-1~3 | — |
| 49 주입 표면 = 생성자·주입 애너테이션 필드·세터 | `AppDependencyConditions.injectionTypes` | F-r5-1 과 변형 넷 |
| 49 제네릭·함수 타입 인자 전개 | 같은 함수의 `expand` | 변형 ①③(제네릭 인자) |
| 49 범용 운반 타입은 목록에 못 온다 | `isCapabilityCarrier` + 「목록 검증」 test | 변형 ④(자작 SAM) |
| 49 목록 == 오늘 주입 표면 | 「집합 등식」 test | 변이마다 함께 RED |
| 49 참조 허용 접두는 주입에 적용 안 함 | 주입 규칙이 `allowedPackages` 를 보지 않는다 | F-r5-1(접두 안인데 RED) |
| 50 ② 층 능력 포트 거부 갈래 | `tier2Allows` 첫 갈래 | 목록에서 `StrategyRepository` 제거해도 초록 |
| 50 목록 == 관측 등식 | 「② 층 workflow 목록」 test | 죽은 항목 셋이 드러나 제거됨 |
| 50 능력 보유의 제네릭 전개 | `holdCapabilityPort` 의 `expand` | — |
| 50 포트 호출 게이트 복원 | `app.port-call.ports` + 쌍 둘의 오른쪽 | 게이트 초록(쌍이 다시 산다) |
| 50 제한 층 `StrategyReader` 직접 참조 금지 | `app.http.denied-types` + `isAllowed` 갈래 | — |
| 50 meta-gate 발견 = 컴파일된 test 클래스 전수 | `AppGateRegistrationTest.discoverAppTestClasses` | 세 형태 RED |

**이탈 둘(판정 필요) — r5·r6.**

3. **주입 운반 타입 금지에 예외가 하나 있다.** 6A-1 이 남긴 audit 필터가 `(ApiAuditRecord) -> Unit`
   을 생성자로 받는다. 그 파일은 이 slice 의 계약이 **무편집(diff 0)** 으로 묶어 두어 서명을 바꿀 수
   없으므로 계약 파일에 **(클래스, 타입) 쌍** 하나로 등재하고, 그 쌍이 오늘 실재하는 주입점임을
   단언한다(죽은 항목 금지). **남는 위험**: ① 층이 그 한 자리에 다른 몸통의 함수 값을 넘기면 같은
   통로가 열린다 — 알려진 제한 ⑱ 과 같은 뿌리다.

4. **② 층 목록에 use case 타입 둘이 남아 있다**(L-r6-2). D-6A2b-41 ② 문면은 「목록에 쓰기 능력
   포트와 **use case 생성자**를 넣지 않는다」인데 `EditStrategyWorkflow`·`EvaluateCandidatesUseCase`
   가 목록에 있다. 뺄 수 없다 — 실행기는 어댑터 경계가 넘겨주는 use case 인스턴스를 **받아야** 하고
   (알려진 제한 ⑯), dry-run 팩토리는 등재된 한 쌍으로 그것을 **조립해야** 한다. 실효는 다른 두 규칙이
   진다(`TIER2_CAPABILITY`·`USE_CASE_CONSTRUCTION`). r5 까지 이 사실은 ⑯ 과 41 ③ 문면에 흩어져
   있었고 이탈 절에는 없었다 — 여기 등재한다.

**앞 라운드 이탈 둘 — 처분됨(D-6A2b-48).** ① meta-gate 를 JUnit 태그가 아니라 전수로 세운 것
(저장소에 `@Tag` 선례가 0 건이었다), ② `scope.md` 의 접두 열거가 계약 파일과 달랐던 것. 전자는
이번 라운드에 발견 술어까지 구조로 바뀌었고, 후자는 「계약 파일이 정본」으로 정리됐다.

**r4 가 「이행」으로 적었으나 실제로 달랐던 것(F-r5-3·N-r5-1) — 이번 라운드에 닫혔다.** 41 ② 의
「목록에 쓰기 능력 포트를 넣지 않는다」를 대조표가 이행으로 적었는데 목록에 `StrategyRepository`
가 있었다. 지금은 술어(`tier2Allows` 첫 갈래)와 목록(등식 단언) 양쪽에서 막는다. **대조표에
「이행」으로 적고 실제로 다른 것은 그 자체가 결함**이라는 것이 이 항목의 교훈이다.

## 우회 아홉 — 무엇이 막는가

| 우회 | 막는 것 | 실측 |
|---|---|---|
| ① 컨트롤러가 전략 저장소를 직접 호출 | `AppliedStrategy` 생성 불가(컴파일) + **app 전체**에 걸린 허용 목록 ⊆ + **출하 조립의 표면 실측** | 변이 일곱(MU1·MU2·MU2b·포트 재수령·RouterFunction·빈 이름 URL 매핑·이른 필터)이 전부 RED |
| ② 같은 `commandId` 다른 본문 재전달 | `IdempotencyConflict` 409 | HTTP 층 test |
| ③ stale revision 으로 confirm | `StaleRevision` 409 — **두 축**(클라이언트가 본 값, draft 를 뜬 값). 기준은 draft 를 만든 **그 읽기**에서 온다 | 교차 세션 회귀(use case + 실 DB)와 읽기 사이 끼어듦 회귀 |
| ④ 세션 id 를 골라 남의 세션을 겨냥 | 요청 본문이 id 를 나르지 못한다 — 실으면 **400** 이다 | 본문에 id 를 실은 요청이 400, 두 begin 의 id 가 다르다 |
| ⑤ outbox 등록 실패인데 전략만 바뀐다 | 한 트랜잭션 | DB 트리거 장애 주입 + 경계 변이 RED |
| ⑥ 깨진 JSON·잘못된 메서드·미디어 타입·Accept 로 500 | 출하 조립 모집단의 기계 전수 | MU4·핸들러 삭제 변이가 RED |
| ⑦ 값으로 불변식을 깬다 | `validate()` 하나가 판정, **영속 전에** 400 | 실 DB 에서 전략·개정·outbox 0행, 세션 버전 0 |
| ⑧ HTTP 로 `System` 행위자 | 요청에서 행위자를 받지 않는다 — 실으면 400 | 변이 RED 6 |
| ⑨ 두 요청이 같은 세션을 동시에 전진 | 낙관적 동시성 → 409, 트랜잭션 롤백 | 매핑은 있다. **동시 실행 실측은 없다**(알려진 제한 ②) |

## 이 slice 의 수확 — 계약이 예측하지 못한 자리

① **D-6A2b-9 의 「영향 0」은 참이 아니었다.** 되살린 `TypeExcludeFilter` 가 `@TestConfiguration` 을 스캔에서
걷어내자, 그 스캔에 기대 기록형 종료·fake ML 빈을 얻던 E2E 둘이 production 종료 경로를 타 test JVM 이
**조용히** 죽었다. 통과 수가 아니라 **실행 자체**를 보는 게이트가 그것을 잡았다. 필터는 되돌리지 않았다 —
test 전용 빈이 출하 조립의 스캔에 섞이는 것이 바로 그 필터가 막는 것이고, 그 누출이 실재했다.

② **관리 포트의 비 GET 도 함께 닫혔다.** API 포트를 닫으려고 더한 메서드 불일치 핸들러는
`@RestControllerAdvice` 라 관리 child context 에도 선다 — 새 endpoint·새 빈 없이 405 가 됐다.

③ **새 의존 게이트가 기존 코드를 잡았다.** dry-run 응답 조립이 어댑터 포트 구현을 컨트롤러에 내주고
있었다 — 값(공고 ID 목록)만 내도록 좁혔다.

④ **대상을 「종류」로 세면 반드시 빠진다.** r1 은 금지를 열거했고 그 수정은 대상(핸들러 종류)을
열거했다 — `RouterFunction` 빈 · 빈 이름 URL 매핑 · 인증보다 앞선 필터가 목록 밖에서 SQL 을 실행했고
(그중 필터는 **자격 없이도** 돌았다) 전건 초록이었다. 지금은 대상이 app 전체이고 면제가 이름 목록이며,
**면제된 조립 클래스의 `@Bean` 이 만드는 진입점은 의존 게이트가 못 본다** — 그것을 표면 실측이 잡는다는
것을 변이로 확인했다. 두 축이 함께 있어야 하는 이유가 그 한 줄이다.

⑤ **406 은 본문을 낼 수 없다.** 클라이언트가 JSON 을 거부했으니 우리 오류 본문도 쓸 수 없다. 누출
축에서는 그것이 가장 안전한 결과라, 「우리 형태의 본문」이 아니라 **「어떤 본문도 없다」**를 계약으로
받았다(D-6A2b-29) — 「모든 오류는 `ErrorBody`」의 유일한 예외다.

## 알려진 제한

① **동시 세션 자체는 허용된다.** 서버가 id 를 만들므로 세션이 여럿 열린다. 낡은 draft 의 확인·제출은
`StaleRevision` 으로 막히지만(D-6A2b-18·28), 그때 운영자는 되돌아가 값을 다시 내야 한다 — 병합은 없다.
`SessionAlreadyActive` 갈래는 HTTP 로 도달할 수 없고 문서에서도 뺐다(전수 `when` 의 총성 때문에 코드에는 남는다).

② **동시 전진(우회 ⑨)의 거동은 실측하지 않았다.** 예외 → 409 매핑과 롤백 경로는 있지만 두 요청을 실제로
겹쳐 돌린 test 가 없다(`OPEN-6A2B-CONCURRENT-SESSION-ADVANCE`).

③ **값 불변식 위반의 사유가 응답에 없다**(`OPEN-6A2B-VIOLATION-DETAIL`). `ErrorBody` 세 필드 계약을 넓히지 않았다.

④ **406 응답은 본문이 없다**(계약이다 — D-6A2b-29). 다른 오류와 달리 correlation id 가 응답에 실리지
않는다 — 그 요청의 추적은 audit 행으로만 된다(406 도 행이 1 느는 것을 실측했다).

⑤ **use case 의 「값 무효 → 같은 필드 accepted」 갈래는 production 에서 죽은 분기다.** 실행기가 같은
`validate()` 로 **먼저** 400 을 내기 때문이다(그래야 사유를 HTTP 로 옮길 자리가 생긴다). 심층 방어로
남기고, use case 자신의 test 가 그 갈래를 계속 잰다.

⑥ **`ProvideValue` 가 실어 온 기준을 커널이 받는다.** 그 값은 요청이 아니라 어댑터(서버)가 싣지만
`EditCommand` 는 public 타입이라 같은 모듈 밖에서도 만들 수 있다. 커널이 value 시점에 현재 revision 과
대조하므로 **지어낸 값은 통과하지 못하고**, 남는 것은 「현재와 같은 값을 싣는 것」뿐이라 정상 사용과
구분되지 않는다.

⑦ **여러 `RouterFunction` 을 구분하지 못한다.** 표면 실측은 그 종류를 표기 하나로 센다. 오늘 이 앱에
`RouterFunction` 빈은 0 이라 **등장 자체가 신호**지만, 하나가 계약에 오르면 둘째는 조용할 수 있다.

⑧ **허용 목록 안의 읽기 포트 하나가 컨트롤러에 닿는다.** 능력 포트 도출은 편집 use case 하나만
보므로 그 생성자에 없는 포트는 능력으로 분류되지 않는다. verifier r4 L-r4-3 이 실측한 자리 —
컨트롤러가 기본 인자로 `OperatorProfilePort`(읽기 전용, 호출 쌍 목록에도 없다)를 주입받아 `current()`
를 부르면 전건 초록이다. 「새 포트가 생기면」이 아니라 **오늘 이미 닿는다**. 읽기 전용이라는 서술은
참이고, 쓰기 능력 축은 D-6A2b-41 이 따로 막는다.

⑨ **Kotlin `internal` 은 JVM 경계가 아니다.** 크기 분할이 만든 최상위 함수 열(판정 술어 넷·JSON 읽기
여섯)은 바이트코드에서 public 이다. 새 권한은 아니다(`commands.md` 의 `javap` 절) — 커널의 `apply`·
`beginSession` 도 이 slice 이전부터 같은 형태다.

⑩ **`TRACE` 는 이 표면 밖이다.** 405 를 내지만 본문이 프레임워크 기본 모양이고 audit 행도 남지 않는다.
base 에서도 같다 — 이 slice 가 만든 것이 아니고 형식 게이트의 탐침 집합에도 넣지 않았다.

⑪ **migration 이 빈 생성의 부수효과다.** 선언된 의존이 아니라 같은 컨텍스트의 싱글턴 생성 순서에
기대고 있다(KDoc 에 정직하게 적었다).

⑫ **컨테이너 축의 정본은 CI 다.** 로컬에서 전 단계를 실측했지만 러너 환경이 다르면 결과도 다를 수 있다.

⑬ **② 층이 dry-run 경로의 어댑터 구체 클래스 셋을 이름으로 받는다.** 요청 스코프 어댑터
(용량 포트·기록형 알림 포트·고정 전략 저장소)를 `EvaluationDryRunFactory` 가 **직접 만든다** —
편집 경로가 같은 문제를 어댑터 층의 트랜잭션 경계로 옮겨 해소한 것과 대조된다. 같은 형태로
옮기는 것이 정답이지만 그 이동은 `adapters/…/evaluation/**` 을 건드려 이 slice 의 in_scope
밖이다(`OPEN-6A2B-DRYRUN-ASSEMBLY-IN-APP`). 지금은 세 이름이 계약 파일에 적혀 있고, 그 셋
말고 다른 구체 클래스가 들어오면 게이트가 RED 다.

⑭ **관리 포트의 composite mapping 은 풀지 않고 수용했다.** 계약 집합에 `알 수 없는 종류` 표기로
올라 있다. 위임 대상이 census 가 도는 `HandlerMapping` 빈을 넘지 않는다는 것은 단언이 잠그지만,
composite 가 **어떤 조건으로 어느 위임에 보내는지**는 풀지 않았다. 관리 포트 노출 자체는 D-6A2a 의
잠금이 따로 지킨다.

⑮ **일반 타입을 통한 능력 세탁은 의존 게이트가 보지 못한다.** 직접 고안한 탐침(`commands.md`
「자체 우회 탐침」 N4)에서, 제한 층이 `java.util.ServiceLoader` 로 `Runnable` 구현을 꺼내면
어댑터 타입을 한 번도 적지 않아 전건 초록이다. `ServiceLoader` 형태는 오늘 실행 가능하지 않다 — `META-INF/services`
자원 파일과 그 구현이 함께 있어야 한다(둘 다 없다). **같은 뿌리의 DI 형태는 실행된다**(L-r5-2,
verifier r5 F-r5-1) — 그 자리는 D-6A2b-49 의 주입 표면이 닫았다. 닫으려면 locator 금지(v2-지침서 §5)를 기존 리플렉션 게이트에 얹는 편이
맞고, 그 게이트는 다른 slice 의 자리라 여기서 넓히지 않았다(`OPEN-6A2B-LOCATOR-BAN`).

⑯ **② 층은 편집 use case 를 **쥘 수** 있다(조립만 막는다).** `EditStrategyWorkflow` 는 ② 층 허용
타입 목록에 있다 — 실행기가 어댑터 경계의 `inTransaction` 이 넘겨주는 인스턴스를 받아야 하기
때문이다. 조립 자리가 한 곳뿐이라 그 인스턴스는 트랜잭션 안에서만 존재하지만, 「쥐는 것」 자체를
막지는 않는다.

⑰ **다운캐스트는 게이트에 보이지 않는다**(자체 탐침 P2, `commands.md`). ② 층이 받은 `StrategyReader`
를 `StrategyRepository` 로 내려꽂는 `checkcast` 는 ArchUnit 의 직접 의존이 아니다(OQ-1 과 같은
성질). 오늘 그 능력은 **무익하다** — `save` 의 인자 `AppliedStrategy` 를 app 이 만들 수 없고, 그
타입을 서명에 쓰는 순간 ② 층 타입 목록이 RED 다(실측). 닫는 것은 능력 축이 아니라 **정확 타입
목록 + `internal constructor`** 이고, 그 셋 중 하나가 느슨해지면 이 통로가 열린다.

⑱ **주입 목록은 타입을 적고 구현은 ① 층이 고른다.** 자체 탐침 R1·R8(`commands.md`) — ① 층이
허용 목록 **안**의 도메인 SAM(`Clock`)이나 어댑터 경계 구현의 몸통에 SQL 을 넣으면 전건 초록이다.
주입 축이 닫은 것은 「어떤 타입을 받는가」이고 「그 타입의 몸통이 무엇을 하는가」가 아니다. 오늘
그 몸통을 SQL 로 채울 수 있는 것은 ① 층에 JDBC 가 열려 있기 때문이다(D-6A2b-32 ①). 닫는 자리는
① 층의 JDBC **멤버 호출** 금지이거나 도메인 SAM 구현의 어댑터 층 고정이고, 둘 다 이 slice 의
계약 밖이라 넓히지 않았다.

⑲ **주입 표면 전개가 배열·`vararg` 를 푸는 대신 버린다**(code-review r6 N-r6-1). `Array<() -> Int>`
나 `vararg` 한 겹이면 F-r5-1 이 되살아난다 — 같은 파일의 형제 술어(능력 보유 판정)는 `baseComponentType`
으로 옳게 푼다. **오늘 재현 가능하고 D-6A2b-33 경계 안쪽이다.** 사전 등록 r6 규칙에 따라 이 라운드에
고치지 않고(게이트 술어를 넓히는 변경) **`OPEN-6A2B-INJECTION-HARDENING` 의 첫 항목**으로 넘긴다 —
고침은 한 줄이다. 이 slice 가 머지하는 것은 편집 endpoint·원자성·동시성이고, 의존·주입 게이트는 지금
상태 그대로(엄격한 방향, 초록) 남는다(D-6A2b-53).

## OPEN 처분

| OPEN | 처분 |
|---|---|
| `OPEN-6F9-STRATEGY-WRITE-ENDPOINT` · `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT` | **닫는다** |
| `OPEN-6A3-APP-HTTP-DEPENDENCY-ALLOWLIST` | **닫는다** — 허용 목록 ⊆ × **app 전체 대상**(면제는 이름 목록) + 출하 조립의 표면 실측. 변이 일곱이 RED |
| `OPEN-API-WRONG-METHOD-500` | **닫는다** — API 포트는 출하 조립 모집단의 기계 전수로, 관리 포트는 같은 조언이 함께 서서 |
| `OPEN-6A1-SCAN-FILTER-SIDE-EFFECT` | **닫는다** — 실제 누출이 있었음을 실측(수확 ①) |
| `OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION` | 수령·재측정·**유지**(→ 6E) |
| `OPEN-6A2B-VIOLATION-DETAIL` · `OPEN-6A2B-CONCURRENT-SESSION-ADVANCE` | 신설(알려진 제한 ②③) |
| `OPEN-6A2B-ABANDONED-SESSIONS` | 신설(→ 6B-3) — 버려진 비종단 세션 행의 파기 |
| `OPEN-6A2B-DRYRUN-ASSEMBLY-IN-APP` | 신설 — dry-run 의 요청 스코프 조립을 어댑터 층으로(편집 경로와 같은 형태). in_scope 밖이라 이 slice 에서 하지 않는다 |
| `OPEN-6A2B-LOCATOR-BAN` | 신설(알려진 제한 ⑮) — locator 금지를 기존 리플렉션 게이트에 얹는다. 그 게이트는 다른 slice 의 자리다 |
| `OPEN-6A2B-INJECTION-HARDENING` | **신설(D-6A2b-53, 게이트 분리 종결)** — 남은 주입 경로 하드닝. 첫 항목 **N-r6-1**(주입 전개가 배열·`vararg` 를 푸는 대신 버린다 — 같은 파일의 형제 술어는 옳게 푼다, 고침은 한 줄, 오늘 재현 가능·경계 안) · N-r6-3(meta-gate 발견 술어가 `@Test` 하나) · N-r6-4(상속 SAM·추상 클래스 운반 타입, 예외 목록 크기 래칫) · N-r6-6(프레임워크 콜백 세터가 표면 밖) · L-r6-1(수신 클래스 소유 SAM) · N-r6-13(`denied-types` 갈래의 음성 fixture) · N-r6-8(test 쪽 「최상위」가 문자열) |
| `OPEN-6A2B-COMPOSITION-ROOT-HARDENING` | **M-r6-1 추가**(D-6A2b-53) — ① 층이 다른 클래스의 정적 가변 필드에 값을 쓴다. 가변 정적 필드 금지 또는 ① 층의 타 클래스 정적 필드 쓰기 금지 |

## 하네스 레인 변경(이 slice 가 만진 게이트·CI)

`scope.md` 「하네스 레인 변경」 절이 정본이다. 이 라운드가 더한 것: 의존 게이트의 대상·면제 키(세 층 + ② 층 허용 목록 셋)와
HTTP 표면 실측 키 여섯(`architecture-policy.properties`), 게이트 test 등재 아홉(`gate-tests.properties`),
`ManagementHealthSurfaceTest` 의 미디어 타입 축, 삭제 하나(`HttpSurfaceFormatGateTest` — 모집단이 좁은
중복 게이트), 그리고 등재 완전성 meta-gate 신설 하나(`AppGateRegistrationTest` — `workflow`·`adapters`
에는 있고 `app` 에만 없던 자리다. 대상은 `app` test **전수·양방향**이라 등재 목록이 25 에서 37 로 늘었다).
라운드 4 가 더한 것: 의존 게이트의 새 축 둘(능력 보유·use case 조립)과 그 계약 키 셋, 규칙별 음성
fixture 파일 둘, 관리 포트 진입점 축 키 하나, `:app:test` 의 선언된 입력 하나(`gate-tests.properties`).
라운드 5 가 더한 것: **주입 표면 축**(규칙 하나 + 계약 키 넷 + 음성 fixture 하나), 제한 층 거부 타입
키 하나, 포트 호출 게이트의 포트 하나와 쌍 둘의 오른쪽 정정, 등재 meta-gate 의 발견 술어 교체와
`:app:test` 의 system property 하나, `②` 층 목록에서 넷 제거(능력 포트 하나 + 죽은 항목 셋).
종결 일괄이 되돌린 것: `app.port-call.ports` 에서 **조용히 빠졌던 포트 둘**(`CapacityPort`·
`NotificationRequestPort`)을 되돌렸다 — 다른 slice 의 감시 목록이고 「오늘 호출 0」은 죽은 항목이
아니라 래칫이다(N-r6-2). 같은 일괄이 `memberEffects`·`openApiSpec` 의 입력 path sensitivity 를
기본값에서 RELATIVE 로 바꿨다(helper 가 늘 붙인다 — 절대 경로가 이미 입력이라 순효과 0, N-r6-11).

## 비활성화 경로

쓰기 경로만 끄려면 컨트롤러와 편집 실행기 빈을 **함께** 뺀다(컨트롤러만 남기면 의존을 찾지 못해 기동이
실패한다). 그 뒤 읽기·dry-run 은 그대로 뜬다. 되돌림 전체는 `rollback.md`.
