# M6/6G-2b — 리뷰 요청 조건 점검

**D-6G-65 분류: 게이트 하드닝.** 제품 거동·수집 코드·실행 상태 형식·원장 형식 무변경, production diff 0.
실수집을 막지 않는다. 축은 셋이다 — 전송 표면 쌍 등식(D-6G2b-1~5) · 반사 게이트 뿌리 확장(A-2,
D-6G2b-11) · **바깥 참조 기본 거부**(vr H-1 → D-6G2b-22).

| 항목 | 상태 | 근거 |
|---|---|---|
| diff 가 커밋되어 base/head 고정 | ✓ | base `1745a3e2` · 마지막 산출물 커밋 `85d00ecf` · clean-tree 양성 대조 |
| acceptance 전부 exit 0 | ✓ | `commands.md` 「acceptance」 셋 |
| test/lint/type/architecture/contract 전건 | ✓ | 축약 없이 `check` + `qualityBaseline` + `one-command-check.sh`(Python job 까지) |
| fixture·정책 version 근거 | ✓ | `architecture-policy.properties` `policy.version` 7 → 8(키 계열 셋 제거 · 허용의 모양이 집합 → 쌍 → 기본 거부 허용 목록). 도메인 fixture·golden 무변경 |
| 알려진 제한과 rollback | ✓ | 아래 「알려진 제한」 · `rollback.md` |
| 누출 어휘 스캔 | ✓ | `commands.md` — 이 slice 가 더한 줄 매치 0 |
| `differential.json` | **N/A** | Python 대조 축 없음(Kotlin `check` job 하나, `ml-engine` 무변경) |
| `golden-manifest.json` | **N/A** | 도메인 fixture 를 쓰지도 바꾸지도 않는다 — 음성 fixture 는 바이트코드용 Kotlin 클래스다 |
| 설계 검토 우회 대응표 | ✓ | 아래 표 셋(전송 축 열 · 바깥 참조 축 여섯 · 반사 축 다섯) |

## 설계 검토 (2) 의 우회 열 → 막는 자리

| 우회 | 막는 구조 | 측정 |
|---|---|---|
| 1. KA1 — 호출 사슬의 반환 타입으로만 지난다 | 수집이 호출 대상의 `rawReturnType` 을 모은다 | fixture `RogueUrlChainFetch` 신고 · 깊은 수집 양성 대조 · 변이 ① |
| 2. KA12 — `java.beans.Expression` | 뿌리 `java.beans` | fixture `RogueBeanExpressionCall`(형제 `Statement` 도) · 변이 ② |
| 3. KA13 — 프로세스 실행 | 낱개 `java.lang.ProcessBuilder`·`Runtime`·`Process`·`ProcessHandle` | fixture `RogueProcessCurl`·`RogueRuntimeExec` |
| 4. KA14 — 비동기 채널 | 뿌리 `java.nio.channels` | fixture `RogueAsyncChannelFetch` |
| 5. KA15 — Spring 클라이언트 | 뿌리 `org.springframework.web.client`·`.reactive`·`org.springframework.http.client` | fixture `RogueSpringRestClient`·`RogueSpringRequestFactory` · 변이 ② |
| 6. Kotlin 확장 함수(소유 타입이 `kotlin.io`) | 수집이 호출 대상의 `rawParameterTypes` 를 모은다 | fixture `RogueResourceUrlRead` — 소유 타입만 보면 **전혀** 신고되지 않는다(양성 대조) |
| 7. 등재된 보유자가 새 전송 타입을 더 쥔다 | 허용이 (클래스, 타입) **쌍**의 정확 집합 | fixture `RogueRegisteredHolderGainingTransport` — 합성 등재 집합으로 「등재된 쌍은 조용 · 더 쥔 타입만 신고」 |
| 8. typealias 로 이름을 가린다(KA4) | 바이트코드에 원 타입이 남는다 | fixture `RogueTypealiasedClient` |
| 9. 전송 표면을 쥔 test 지원 코드를 production 소스셋에 둔다 | 뿌리가 production 전체(`roots=bidvector`)이고 등재 쌍 등식이 전수다 | 등재에서 한 쌍을 빼면 그 쌍이 위반으로 나오는 양성 대조 · 변이 ③(쌍 등식 RED). **fixture 로는 재지 않았다**(알려진 제한 6) |
| 10. `com.sun.net.httpserver`·`sun.net` | 뿌리 `com.sun.net`·`sun.net`(착수 실측으로 더했다) | fixture `RogueHttpServerExposure` |

(2b) 값 획득 축 — **새 public 표면은 정책 파일 키뿐이다**(더한 것 15 · 지운 것 8 · 순증 7, 아래 절의 기계
산출값과 같다)이고 production 의 새 public 선언은 0 이다.
키를 비우거나 용도를 조용히 바꾸는 길은 변이 ③이 잰다(용도 키 등식 · 쌍 등식 · 등재 밖 참조 0 셋이 RED).

(3) 과잉 — 뿌리가 넓어 무해한 참조가 걸리는 쪽은 과잉 대조 둘(들어오는 서블릿 표면 · 전송 무관 계산)이
잰다. 무해 타입 목록은 두지 않았다(그 쪽이 더 조인다 — `commands.md` 「계약 대조」 2).

### vr H-1 — 바깥 참조 축 (D-6G2b-22·25)

| 우회 | 막는 구조 | 측정 |
|---|---|---|
| 전송 뿌리 **밖**의 JDK API 로 바이트를 낸다 | 바깥 참조를 **허용 패키지 + 기본 거부**로. 전송 표면은 이 층을 지나지 않고 쌍 등식만 본다(두 층) | fixture 일곱(V1~V5 + `java.awt` · `javax.script`)을 모듈마다 상세 줄 전체 일치로 · 변이 둘(허용 하나 제거 · 미관측 추가) |
| 허용 패키지 **하위**의 전송 성격 패키지 | 허용을 **정확 패키지**로 두어 하위가 따라오지 않는다 — 거부 하위 목록이 필요 없다 | 접두 뿌리로 묶으면 V1·V3 가 지나는 것을 실측(「계약 대조」 9) |
| 허용 패키지 **안**의 확장 지점(`ServiceLoader`) | 낱개 전송 타입으로 2층이 잡는다 | fixture 하나가 1층 신고 0 · 2층 신고 1 을 함께 잰다 |
| 쓰이지 않는 허용 패키지를 남겨 둔다 | 두 방향 등식(허용 ⊂ 관측) | 미관측 패키지를 더하면 등식이 그것을 낸다(양성 대조) |
| 모듈 하나가 classpath 에서 빠져 등식이 공허해진다 | 모집단 기대값을 `layer.*` 에서 읽는다(cr M-4·D-30) | 모듈 집합 단언 |
| 판정 대상 모듈 목록에서 한 모듈을 뺀다 | 목록을 별도 키로 두지 않고 `layer.*` 에서 **도출** — 모집단 단언과 같은 자리 | 변이 ⑪: `layer.application` 을 비우면 **넷이 붉다**(모집단 · 모듈 키 등식 · 1급 패키지 집합 · 의존 방향) |
| 전송 타입이 **시그니처 간접 자리**에만 있다 | 수집이 호출 대상의 인자·반환 타입을 모은다 | fixture 넷(제네릭 둘 · SAM · 어노테이션, cr M-2) |

### A-2 — 반사 축 (D-6G2b-11)

| 우회 | 막는 구조 | 측정 |
|---|---|---|
| 반사를 `adapters` 로 한 걸음 옮긴다 | 뿌리에 `bidvector.adapters` 를 더하고 **원문 값 획득 뿌리와 별도 키**로 둔다 | fixture `RogueAdapterReflectionPeek` 신고 · 뿌리 좁힘 양성·음성 대조 둘 · 변이 ⑤⑥ |
| 등재된 클래스가 **새 반사 멤버**를 더 부른다 | 허용이 (클래스, 멤버) 쌍 | fixture `RogueAdapterNameLookupGainingReflection` — 합성 등재로 「등재한 `getName` 은 조용 · `getDeclaredMethod` 만 신고」 |
| 멤버 이름을 전역으로 허용받는다(앞 판의 `getName`) | 전역 멤버 허용 목록을 없앴다 | 등재 밖 이름 조회가 신고됨을 잰다(과잉 대조의 쌍둥이 단언) |
| 등재 쌍을 조용히 지운다 | 두 쌍 집합 == 관측 집합 | 변이 ④(쌍 하나 제거 → 2 failed) |
| 뿌리 확장을 조용히 되돌린다 | 좁힌 뿌리에서는 `adapters` 쌍이 관측에서 사라져 등식이 깨진다 | 변이 ⑥(규칙은 초록, 등식만 RED) |

## 크기 게이트

| | evidence(`scope.md` 포함) | 레인 세 파일만 | 산출물(코드·`config/quality` 추가분) |
|---|---|---|---|
| 줄 | 1019 | 752 | 1,841 |
| 바이트 | 109,651 | 67,630 | 99,039 |

**줄은 두 축 다 통과, 바이트는 `scope.md` 를 넣으면 10,612 B(11%) 초과한다** — 앞 라운드의 21% 에서 줄었다
(수정 라운드가 산출물을 1,841줄 / 99,039 B 로 늘렸다). 남은 초과는 전부 `scope.md` 쪽이다: 그 한 파일이
evidence 바이트의 38% 이고 레인이 만지지 않는 팀장 파일이다(갱신 r1~r4 로 커졌다). 레인 세 파일만
보면 산출물의 68% 로 통과한다. 6G-2f·D-6G2f-17 과 같은 **사실 등재**다.

산출물 줄 구성: 게이트·fixture Kotlin 열 · 정책 둘 · ArchUnit 핀 하나 · 기존 test 넷. `milestone-6.md` 착수
문단과 `scope.md` 는 팀장 커밋이라 산출물에 세지 않는다. 바이트는 양쪽 다 diff 의 `+` 접두를 포함한 같은
방법으로 쟀다. 한국어 산문은 한 자 3 바이트이고 Kotlin 은 1 이라 바이트 축은 같은 일의 양을 같은 수로 세지
않으므로 **줄 축이 이 slice 에서 더 바른 척도**다(6G-2f 와 같은 판단).

## clean-tree 양성 대조

HEAD `85d00ecf` 에서 in_scope 열아홉 경로를 **개별 인자**로 쟀다 — **빈 출력(0줄) → `M` 한 줄 → 빈 출력**.
공유 파일 하나의 마지막 줄을 **비파괴 절삭**해 `M` 을 확인하고 사본으로 복원했다. `git checkout --` 는 쓰지
않는다(다른 레인의 미커밋 편집을 지운다). 경로를 변수 하나로 묶지 않는다 — pathspec 이 하나가 되면 「빈
출력」이 더러운 트리와 구별되지 않는다. evidence 를 포함한 마지막 상태의 판정은 verifier 와 PR 조치 코멘트
몫이다.

## 새 public 표면 — 정책 파일 키 **더한 것 15 · 지운 것 8**(순증 7)

기계로 센 값이다(base 와 HEAD 의 선언 키 집합 차집합).

- 더한 것 15 — 전송 아홉(`collection.transport.roots`·`surface-packages`·`surface-types`·`purposes` +
  `holders.<용도>` 다섯) · 반사 셋(`collection.reflection.roots`·`type-pairs`·`class-member-pairs`) ·
  바깥 참조 셋(`allowed-packages.<모듈>`). 판정 대상 모듈 목록은 키가 아니라 `layer.*` 도출이다.
- 지운 것 8 — `collection.http-client.*` 셋 · `collection.transport-bypass.*` 셋 ·
  `collection.reflection.{allowed-referencers,class-allowed-members}` 둘.

production 의 새 public 선언은 0 이다(production diff 0). test 쪽 표면 `TransportSurfaceRules` ·
`ReferenceCollection` · 공유 함수 `referencedTypeNames`·`outermostName`·`outermostClass` 는 출하 바이트에
없다.

`ReferenceCollection.OWNER_ONLY` 는 **쓰이는 게이트가 아니라 양성 대조**다 — 깊은 수집이 조용히 되돌려지는
변이를 음성·양성 양쪽에서 잡는다(변이 ①: 4 failed).

## 레인 경계 — 동결 중 커밋 사실 (선언)

**판정 r2 SHA `d1b2b7f0`(2026-10-03 19:57) 뒤에 레인 커밋 다섯이 붙었다.** 이력을 되쓰지 않고 사실로
선언한다.

| SHA | 시각 | 내용 |
|---|---|---|
| `28c789c6` | 20:00 | 정책(공유) — 판정 대상 모듈을 `layer.*` 도출로, `collection.external.modules` 키 삭제 |
| `9431ead0` | 20:00 | 술어·단언 — `externalJudgedModules` 도출, 모집단 기대값을 `policy.allModules` 로 |
| `dca888aa` | 20:11 | ktlint(위 커밋이 남긴 `}` 앞 빈 줄) — **마지막 산출물 커밋** |
| `137d5615` | 20:46 | evidence — 모듈 도출·변이 ⑪·rollback 재실측 |
| `a3dc58e1` | 20:47 | evidence — 계약 갱신 r5 반영 |

레인이 지시로 읽은 것은 팀장 메시지 「승인 — 계속 진행 … 등식 test 가 세 모듈을 모듈 목록 상수가 아니라
M-4 의 모집단 단언과 같은 자리에서 읽게 해 모듈을 빠뜨리면 같이 붉게」다. 그 메시지가 레인 수신함에 먼저
닿았고 r2 동결 통보는 그 작업이 끝난 뒤 닿았다 — 동결과 지시가 비행 중 엇갈렸다.

**판정에 미치는 영향**: 위 셋(`28c789c6`·`9431ead0`·`dca888aa`)은 **게이트 정책·술어·단언**이라 severity 와
무관하게 표적 재검증 대상이고, `d1b2b7f0` 에는 담겨 있지 않다. rollback 의 실측 HEAD 와 복원 목록도 그
커밋들을 반영한 `dca888aa` 기준이다(`rollback.md`).

## 알려진 제한

1. **목적지를 모른다**(계약의 경계 밖 선언). 등재된 `attachment`·`llm`·`ml-grpc` 보유자가 KONEPS 주소로
   호출하는지는 정적 분석이 재지 못한다 — 통제는 「서비스 키 원문 설정은 배선 한 곳만 참조한다」다.
   이 게이트는 그 보유자들의 존재를 **숨기지 않고 등재로 드러낸다**.
2. **반사 타입이 전혀 남지 않는 형태는 밖이다.** 뿌리가 `java.lang.reflect`·`java.lang.invoke` 를 덮어 6G
   보다 넓어졌지만, 서드파티 반사 도구(Spring `BeanWrapper`·Jackson `convertValue` 류)를 경유하면 전송·반사
   타입이 남지 않는다. 타입 이름 목록으로 막으면 열거로 돌아가므로 두지 않았다(6G 의 같은 제한 승계).
3. **A-2 는 집행했다** — 뿌리가 `workflow`·`app`·`adapters` 이고 등재 쌍 12 다. 남는 제한은 **뿌리가
   production 전체가 아니라는 것**이다: `procurement`·`decision`·`qualification`·`settlement`·
   `shared-kernel` 은 여전히 반사 게이트 밖이다(그 모듈들은 domain 계열이고 다른 게이트가 프레임워크·반사
   참조를 금지하지만, 이 게이트의 쌍 등식으로는 재지 않는다). 전송 표면 게이트와 달리 뿌리를 production
   전체로 올리지 않은 것은 운영자 결정 문면(`adapters` 까지)을 넘지 않기 위해서다.
4. **`OPEN-6G-GATE-REGISTRY-KONEPS` 는 닫지 않았다.** D-6G2b-9 의 등재는 했으나 그 OPEN 의 내용은 게이트
   장부 전반이라 쌍 등식이 갈음하지 못한다.
5. **`io.netty` 뿌리는 오늘 아무것도 재지 않는다** — 클래스패스에 없다(gRPC 전송 모듈이 `runtimeClasspath`
   에 없다). 의존이 들어오면 그때부터 닫힌다.
6. **우회 9(production 소스셋에 심은 test 지원 코드)는 fixture 로 재지 않았다.** 뿌리가 production 전체라
   구조로는 닫혀 있고 등재 쌍 등식의 양성 대조가 「등재 밖 쌍은 신고된다」를 재지만, **그 배치 자체를 심은
   fixture 는 없다**(음성 fixture 는 test 소스셋에 있어야 하므로 같은 방식으로 재지 못한다).
7. **뿌리를 좁히는 변이는 production 등식이 잡지 못한다**(변이 ② 실측 — 뿌리 열일곱 → 셋에서 양성 쪽
   전건 초록). 잡는 것은 음성 fixture 표뿐이므로 그 표가 이 게이트의 민감도를 혼자 든다.
8. **클래스패스 전수는 `:app:runtimeClasspath` 기준이다**(좌표 101). 다른 구성(test 전용 의존)에만 있는
   전송 표면은 production 뿌리 밖이라 이 게이트의 대상이 아니다.
9. **in_scope 문면 밖 자리는 남지 않는다** — 전송·A-2·adapters 경로는 계약 갱신 r1·r2 가, workflow external
   fixture 와 ArchUnit 핀은 r5(D-6G2b-31)가 넣었다. 남는 제한은 **그 둘을 다른 자리에 둘 수 없다**는 사실
   이다: fixture 는 모듈별 허용 집합을 모듈마다 재려면 그 모듈 뿌리 아래여야 하고, 핀은 test 리소스다.
10. **등재 보유자 안의 새 사용처는 쌍의 해상도 밖이다**(vr M-1 → D-6G2b-23,
   `OPEN-6G2B-HOLDER-INTERNAL-SURFACE`). 등재 보유자에 String 시그니처의 public 함수를 더해 그 안에서 이미
   등재된 전송 타입으로 호출을 내면, 어느 클래스든 그 함수를 문자열로 부를 수 있고 관문을 지나지 않는다.
   쌍은 「새 타입」만 재고 「같은 타입의 새 사용처」는 보지 못한다. 해상도를 올리는 길은 (호출 메서드, 송신
   멤버) 쌍이고 보유자 스물여덟의 멤버 전수가 또 한 라운드다.
11. **반사 음성 fixture 는 뿌리를 정책이 아니라 test 리터럴로 받는다**(vr 참고 관찰). 그래서 정책 뿌리 축소는
   쌍 등식 하나만 잡고, `adapters` 쌍이 0 이 되는 날에는 축소가 보이지 않는다.
12. **허용 패키지 *안*의 출구는 어느 층도 잡지 못한다**(cr r2 M-3 · vr M-r2-1 → D-6G2b-36·39,
    **`OPEN-6G2B-ALLOWED-PACKAGE-EGRESS`**). 둘이다 — ⓐ **파일 시스템 경유**(`java.io`·`java.nio.file` 로
    `/dev/tcp`·FIFO·원격 파일 시스템) ⓑ **라이브러리 자체 로더**(예: `com.networknt.schema` 의 원격 스키마
    적재). 둘 다 허용 패키지라 1층을 지나고 전송 표면 타입이 남지 않아 2층도 못 본다. 정확 패키지 입도
    (D-28)의 귀결이고, 닫는 길은 `ServiceLoader` 선례처럼 낱개 열거뿐이라 **지금 늘리지 않는다** —
    위협 모델 「방어하지 않는 것」에 둔다.
13. **접기 규칙이 저장소에서 하나가 아니다**(cr r2 M-2 → D-6G2b-35, **`OPEN-6G2B-FOLDING-UNIFICATION`**).
    쌍 등식 게이트 셋은 이름 기준 절단을 쓰지만 6F·6G 의 앞선 게이트들은 `enclosingClass` 접기를 그대로
    쓰고 그 등재가 중첩 이름을 담는다(`collection.key-hash.holders` 의 `NoticeKeyHash$Companion` ·
    `app.injection.allowed-types` 의 `Resolution$Resolved`). 옮기려면 그 게이트들의 관측을 다시 재야 한다.
14. **수집 쪽 음성 단언의 이름 경계에 한 칸 남는다**(cr r2 L-6). 앞뒤를 다 보지만 `.`·`$` 를 경계로 세므로
    **점 뒤에서 시작하는 꼬리 조각**(`net.URL` ⊂ `java.net.URL`)은 여전히 통과한다. 그 형태와 「전체 이름의
    멤버를 단순 이름으로 단언하는」 정당한 형태가 글자 종류로 구별되지 않는다. 닫으려면 규칙마다 상세 형식을
    알고 전체 일치로 비교해야 한다 — 전송 쪽 음성 단언은 이미 전체 일치다.
15. **`Class` 멤버를 이름으로만 맞춘다**(cr r2 L-10). 서술자를 보지 않으므로 동명 과적재가 생기면 구별하지
    못한다 — 오늘 `java.lang.Class` 에는 없다.
16. **반사 패키지 둘이 두 목록에 다 있다**(cr r2 L-9). `java.lang.reflect`·`java.lang.invoke` 가 전송 표면
    뿌리와 `collection.reflection.packages` 양쪽에 있다. 전송 쪽은 production **전체**를, 반사 쪽은 세 모듈을
    뿌리로 삼아 **범위가 다르므로** 겹침이 중복이 아니다. 닫힌 용도 어휘 다섯에는 반사 등재 자리가 없다 —
    반사는 쌍 등식이 따로 들기 때문이다. **정책 파일 주석은 이 라운드에서 고치지 않았다**(「겹쳐 적지
    않는다」로 읽히는 문면이 남아 있다) — 그 파일은 공유 파일이고 이번 장부 일괄은 공유 파일을 만지지
    않는다. 다음에 그 파일을 여는 slice 가 함께 고칠 자리다.
17. **`archunit.properties` 핀의 범위는 `:app` test classpath 다**(cr r2 L-8). `build-logic` 도 ArchUnit 을
    의존하지만 `ArchRule`·`should` 를 쓰지 않아 오늘 효과는 같다. 다른 모듈에 rule 이 생기면 그 모듈에도
    같은 핀이 필요하다.
18. **ArchUnit 핀의 두 단언은 서로를 대신하지 못한다**(PR #58 A). 설정 **값** 단언은 키 이름이 틀린 **죽은
    핀**을 드러내고, 빈 `should` **probe** 는 ArchUnit 이 기본값을 바꾸는 날을 드러낸다. 앞 판의 죽은 핀은
    거동으로는 보이지 않았다 — 기본값이 이미 `true` 였기 때문이다(변이 ⑫ 실측).
19. **덮개 모집단에 다른 slice 의 fixture 다섯이 든다**(PR #58 D). 구조적 모집단(게이트가 신고하는 전수)이
    드러낸 것이고, 이 게이트들이 신고하는 것이 **맞으므로** 빼지 않고 등재했다. 그 집합이 늘거나 줄면 다른
    slice 가 fixture 를 더하거나 지운 것이고 양방향 등식이 드러낸다 — 이 레인이 고칠 일은 아니다.
20. **반사 축은 수집 깊이에 민감하지 않다**(PR #58 G). 깊은 수집이 더하는 반사 쌍이 0 이라 양성·음성 어느
    대조도 들지 않는다. 전송은 양쪽이 들고 바깥 참조는 양성 쪽이 든다 — 축마다 다르다는 사실을
    `referencedTypeNames` KDoc 에 적었다.
21. **바깥 참조 허용 집합은 `app`·`workflow`·`adapters` 셋뿐이다.** `procurement`·`decision`·`qualification`·
   `settlement`·`strategy`·`sharedkernel` 은 이 층 밖이다(domain 계열이고 `ArchitectureGateTest` 의 T-A~T-D
   가 그 층의 바깥 참조를 따로 잠근다). 운영자 결정 문면(세 모듈)을 넘지 않았다.
