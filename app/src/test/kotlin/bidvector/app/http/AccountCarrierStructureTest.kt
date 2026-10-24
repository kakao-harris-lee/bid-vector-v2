package bidvector.app.http

import bidvector.app.architecture.ArchitecturePolicy
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * **production 에 주체 라벨을 계정으로 되읽는 길이 0 이다**(M7/7B 계약 D-7B-2) — 구조로 잰다.
 *
 * 행동 test(`AuthenticatedAccountTest`)는 「지금의 접근자가 라벨을 안 읽는다」를 보지만, 새
 * 핸들러가 라벨을 직접 파싱하는 것은 못 본다. 그래서 `bidvector.app` 의 **컴파일된 class 전수**를
 * javap 로 읽어 세 자리를 **집합 등식**으로 잠근다(포함이 아니라 등식 — 늘어도 줄어도 붉다):
 *
 * 1. 상수 풀에 `account:` 로 시작하는 문자열을 가진 class — 라벨을 **만드는** 자리 하나뿐이다.
 *    파싱하려면 그 접두를 어딘가 적어야 하므로, 새 파싱 자리는 여기서 붉다.
 * 2. `authenticatedSubject()` 를 부르는 class — 운영자 주체를 쓰는 기존 자리 하나뿐이다.
 *    접두 없이 라벨을 읽는 변형(`substringAfter(':')` 등)은 여기서 붉다.
 * 3. 주체 속성 키 문자열을 가진 class — 그 속성을 **적거나 감사에 옮기는** 자리뿐이다. 접근자를
 *    우회해 `getAttribute` 로 라벨을 직접 읽는 변형은 여기서 붉다.
 *
 * **계약 D-7B-3 이 더한 셋**(verifier r1 M-1 · 팀장 표적) — 위 문자열 상수 술어는 키를 실행 시에
 * 조립하면(`listOf(…).joinToString(".")`) 피해 갈 수 있었다(verifier 변이 M2 초록). 그래서 문자열이 아니라
 * **호출 지점과 서명**으로 닫는다:
 *
 * 4. 요청 속성을 읽는(`getAttribute`) class — 속성을 적고 읽는 정해진 넷뿐이다. 계정 범위 핸들러가 속성을
 *    직접 읽어 라벨을 파싱하는 변형은 키를 어떻게 만들든 여기서 붉다.
 * 5. app main 에서 `AccountId` 를 **만드는** 자리 — 0 이다. 계정은 세션 인증이 낸 값이 운반될 뿐이다.
 * 6. `AccountId` 를 생성자 인자로 받는 class — 운반 자리(`AuthenticationResult.Authenticated`) 하나다.
 *    `app.injection.allowed-types` 의 `AccountId` 한 줄은 타입 단위라 어느 class 에나 주입 길을 연다
 *    (verifier 변이 M4 초록) — 이 등식이 그 줄을 한 자리로 좁힌다.
 *
 * **검토 r2 · r3 · r4 가 더한 넷**: `getAttribute` 를 소유자 무관 서명으로(4 를 그렇게 바꿨다), `AccountId` Class 상수 보유 = ∅,
 * app main 의 Jackson 멤버 소유자 전수 = {`JsonNode`}(손에 든 값으로 짓는 바인딩 전부 — V3-N1 · V4-N1), 요청 매핑 메서드의
 * 매개변수에 `AccountId` 를 싣는 타입 0(Spring 이 대신 짓는 길 — V4-N1 ②). 시스템 주체(`BidActor.System`)를 짓는 자리 0(R4-3).
 *
 * 등식의 오른쪽은 **관측값**이다(javap 실측). 정당한 새 자리가 생기면 이 집합을
 * 고치는 커밋이 그 자리를 이름으로 드러낸다 — 그것이 이 test 의 목적이다.
 */
class AccountCarrierStructureTest {
    private val outputs: Map<String, String> by lazy { appClassOutputs() }

    @Test
    fun `account 접두 문자열은 라벨을 만드는 자리 하나에만 있다`() {
        outputs.keys.shouldNotBeEmpty()

        classesWithUtf8 { it.startsWith(ACCOUNT_LABEL_PREFIX) } shouldBe LABEL_WRITERS
    }

    @Test
    fun `주체 라벨 접근자를 부르는 자리는 운영자 주체 하나뿐이다 — 계정 범위 핸들러는 없다`() {
        callersOf(SUBJECT_ACCESSOR) shouldBe SUBJECT_READERS
    }

    @Test
    fun `주체 속성 키를 아는 자리는 적는 쪽과 감사 쪽뿐이다`() {
        classesWithUtf8 { it == AUDIT_SUBJECT_ATTRIBUTE } shouldBe SUBJECT_KEY_HOLDERS
    }

    @Test
    fun `계정 범위 핸들러는 typed 접근자를 쓴다`() {
        callersOf(ACCOUNT_ACCESSOR) shouldBe ACCOUNT_READERS
    }

    @Test
    fun `요청 속성을 읽는 자리는 정해진 넷뿐이다 — 키를 어떻게 만들든 · 어느 타입으로 부르든`() {
        signatureCallers(outputs, GET_ATTRIBUTE_SIGNATURE) shouldBe ATTRIBUTE_READERS
    }

    /**
     * 검토 r2 V2-N1 — `AccountId` 를 **이름으로 가리켜** 만드는 길(Jackson `convertValue(n, AccountId::class.java)` · 반사 등)은
     * 그 타입의 Class 상수(`ldc … // class AccountId`)를 요구한다. app main 에서 그 상수를 가진 class 는 **거부 가드 하나**다
     * (D-7B-7 ① — `AccountBindingGuard` 가 「`AccountId` 는 짓지 않는다」를 등록하려면 그 타입을 이름 불러야 한다). `<init>` 호출자
     * 등식은 생성자를 **직접** 부르는 길만 닫는다. 이름을 쓰지 않는 길(손에 든 값의 `getClass()`)은 이 술어 밖이고 아래 Jackson
     * 멤버 등식이 닫는다(검토 r3 V3-N1 — 「다른 길은 모두 Class 상수를 요구한다」는 사실이 아니었다).
     */
    @Test
    fun `app main 에서 AccountId 의 Class 상수를 드는 class 는 거부 가드 하나다`() {
        classConstantHolders(outputs, ACCOUNT_ID_INTERNAL) shouldBe setOf(GUARD)
    }

    /**
     * 검토 r3 V3-N1 · r4 V4-N1 · r5 V5-L1 — 손에 든 `AccountId` 로 **다른 id 를 짓는** 길(`convertValue(n, seed.javaClass)` ·
     * `readerForListOf(seed.javaClass)` · `readValueAs` · jackson2 `TypeReference` …)은 Class 상수도 `<init>` 호출도 반사도 없다.
     * r3 판은 이름 · 서술자 **열거**였고(G3 가 지나감), r4 판은 소유자만 고정해 `JsonNode` 자신의 멤버가 열린 문이었다(V5-L1 ·
     * R5-2). 그래서 **(소유자, 멤버 서명) 쌍 전수 == 오늘 관측 집합**으로 건다: app main 이 가리키는 Jackson 멤버(jackson3 ·
     * jackson2, 호출 · `Methodref` · `MethodHandle` 포함)가 `JsonNode` 접근자 19 와 거부 가드가 쓰는 셋뿐이다. 새 멤버 하나 —
     * `JsonNode.traverse` 든 매퍼의 무엇이든 — 가 붉다. 정당한 자리가 생기면 이 집합을 고치는 커밋이 그 쌍을 이름으로 드러낸다.
     * 근본(`AccountId` 생성 경로 봉쇄)은 7D 파일이라 이 slice 가 하지 않는다 — OPEN 후보 `OPEN-7D-ACCOUNTID-CONSTRUCTION`.
     */
    @Test
    fun `app main 이 가리키는 Jackson 멤버 쌍은 오늘 관측 집합과 같다`() {
        val members = jacksonMembers(outputs)
        members.filter { it.startsWith("$JSON_NODE.") }.shouldNotBeEmpty()

        members shouldBe OBSERVED_JACKSON_MEMBERS
    }

    /**
     * 팀장 조건 (a) — 관측 집합의 `JsonNode` 멤버는 **값을 지을 수 없는 접근자**다: 대상 타입(`Class` · `JavaType` ·
     * `TypeReference`)이나 읽기 문맥(`ObjectReadContext`)을 받지 않고 `JsonParser` 를 돌려주지 않는다. 쌍 등식이 바뀔 때
     * 그 바뀐 쌍이 이 성질을 깨면 여기서도 붉다(등식만 고치고 지나가지 못한다).
     */
    @Test
    fun `관측 집합의 JsonNode 멤버는 대상 타입 · 읽기 문맥을 받지 않고 파서를 돌려주지 않는다`() {
        OBSERVED_JACKSON_MEMBERS
            .filter { it.startsWith("$JSON_NODE.") }
            .filter { member -> BINDING_CAPABLE.any { it in member } || member.endsWith(JSON_PARSER_RETURN) } shouldBe
            emptyList()
    }

    /**
     * Kotlin 은 상속한 멤버를 **자기 class 를 소유자로** 부른다(`this.addDeserializer` 의 소유자는 `AccountBindingGuard` 다).
     * 그래서 Jackson 타입을 상속한 app class 는 쌍 등식 밖에서 그 상위 타입의 멤버를 부를 수 있다(`ObjectMapper` 하위 class 의
     * `convertValue`). Jackson 상위 타입을 가진 app class 를 **고정 집합 등식**으로 잠근다 — 거부 가드와 그 역직렬화기 둘.
     */
    @Test
    fun `Jackson 타입을 상속한 app class 는 거부 가드와 그 역직렬화기 둘이다`() {
        jacksonSubclasses(outputs) shouldBe setOf(GUARD, "http/RejectingAccountIdDeserializer")
    }

    /** 양성 · 음성 대조 — 우회 F1 · G3 · `readValueAs` · jackson2 `TypeReference` · 메서드 핸들 · `traverse` 가 쌍으로 잡힌다. */
    @Test
    fun `매퍼 판독기 파서 jackson2 메서드 핸들 traverse 의 멤버는 쌍 술어에 걸린다 — 대조`() {
        val lines =
            mapOf(
                "F1" to "12: invokevirtual #20 // Method tools/jackson/databind/ObjectMapper.convertValue:" +
                    "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;",
                "G3" to "7: invokevirtual #9 // Method tools/jackson/databind/ObjectMapper.readerForListOf:" +
                    "(Ljava/lang/Class;)Ltools/jackson/databind/ObjectReader;",
                "AS" to "4: invokevirtual #5 // Method tools/jackson/core/JsonParser.readValueAs:" +
                    "(Ljava/lang/Class;)Ljava/lang/Object;",
                "J2" to "#30 = Methodref #31.#32 // com/fasterxml/jackson/databind/ObjectMapper.readValue:" +
                    "(Ljava/lang/String;Lcom/fasterxml/jackson/core/type/TypeReference;)Ljava/lang/Object;",
                "MH" to
                    "#41 = MethodHandle 5:#40 // REF_invokeVirtual tools/jackson/databind/ObjectMapper.convertValue:" +
                    "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;",
                "TRAVERSE" to "6: invokevirtual #13 // Method tools/jackson/databind/JsonNode.traverse:" +
                    "(Ltools/jackson/core/ObjectReadContext;)Ltools/jackson/core/JsonParser;",
                "NODE" to "5: invokevirtual #11 // Method tools/jackson/databind/JsonNode.get:" +
                    "(Ljava/lang/String;)Ltools/jackson/databind/JsonNode;",
            )

        val outside = lines.mapValues { (_, line) -> jacksonMembers(mapOf("x" to line)) - OBSERVED_JACKSON_MEMBERS }
        outside.filterValues { it.isEmpty() }.keys shouldBe setOf("NODE")
        val subclass = "public final class bidvector.app.http.Forge extends tools.jackson.databind.ObjectMapper {"
        jacksonSubclasses(mapOf("http/Forge" to subclass)) shouldBe setOf("http/Forge")
    }

    /**
     * 검토 r4 V4-N1 ② — **Spring 이 대신 짓는 길**(G11)의 **한 겹** 구조 규칙이다: 요청 매핑 메서드(`@RequestMapping` 과 그 합성)의
     * 매개변수 타입(제네릭 인자 · 배열 원소 포함)이 `AccountId` 이거나, `AccountId` 를 **직접** 필드 · 생성자 매개변수로 가진
     * 타입이면 붉다. **범위는 한 겹 · 요청 매핑 메서드뿐이다**(검토 r5 V5-N1 — 두 겹 중첩 · setter 운반 · `@ModelAttribute`
     * 메서드는 이 규칙 밖이었다). 그 깊은 길은 **짓는 층에서** `AccountBindingGuard` 가 실패로 닫고, 모양별 행동은
     * `AccountBindingGuardTest` 가 잰다. `@InitBinder` · `@ExceptionHandler` 메서드는 데이터 바인딩을 하지 않는다(바인더 · 예외 ·
     * 요청 류 해석기만 받는다 — R5-5).
     */
    @Test
    fun `요청 매핑 메서드의 매개변수는 AccountId 를 직접 싣지 않는다`() {
        val handlers = requestMappingMethods(appClasses())
        handlers.shouldNotBeEmpty()

        val carrying = handlers.filter { method -> method.genericParameterTypes.any(::carriesAccountId) }
        carrying.map { it.toString() } shouldBe emptyList()
    }

    /**
     * 팀장 조건 (c) · R5-6 — 위 규칙의 **모집단이 공허하지 않다**: 요청 매핑 메서드를 선언한 app class 집합 == 출하 조립의 HTTP
     * 표면 계약(`app.surface.api.handlers`, `HttpSurfaceCensusTest` 가 실 조립에서 잰다)의 `bidvector.app` 핸들러 집합.
     */
    @Test
    fun `요청 매핑 메서드를 선언한 app class 는 HTTP 표면 계약의 핸들러와 같다`() {
        val declaring = requestMappingMethods(appClasses()).map { it.declaringClass.name }.toSet()

        val census =
            ArchitecturePolicy
                .load()
                .apiSurfaceHandlers
                .filter { it.startsWith("bidvector.app.") }
                .toSet()
        declaring shouldBe census
    }

    /**
     * D-7B-7 ① — 바인딩 거부 가드(`AccountBindingGuard`)는 **`@RestController` 핸들러에만** 걸린다(관리 포트의 actuator 핸들러를
     * 비켜 가려고). 그래서 요청 매핑 메서드를 가진 app class 가 전부 `@RestController` 여야 가드 밖의 핸들러가 생기지 않는다.
     */
    @Test
    fun `요청 매핑 메서드를 선언한 app class 는 전부 RestController 다 — 바인딩 가드의 적용 범위`() {
        val declaring = requestMappingMethods(appClasses()).map { it.declaringClass }.toSet()
        declaring.shouldNotBeEmpty()

        declaring
            .filterNot { it.isAnnotationPresent(org.springframework.web.bind.annotation.RestController::class.java) }
            .map { it.name } shouldBe emptyList()
    }

    /** 양성 대조 — G11 의 모양(매개변수 `AccountId`)과 그것을 실은 묶음 타입이 규칙에 걸리고, 평범한 매개변수는 걸리지 않는다. */
    @Test
    fun `AccountId 를 싣는 핸들러 매개변수는 규칙에 걸린다 — 양성 대조`() {
        val probe =
            requestMappingMethods(listOf(G11Probe::class.java))
                .associate { it.name to it.genericParameterTypes.any(::carriesAccountId) }

        probe shouldBe mapOf("direct" to true, "wrapped" to true, "listed" to true, "plain" to false)
    }

    /** 검토 r4 R4-3 — 「마감 지남」 진입이 시스템 전용이 된 뒤(C3-L6) app main 이 시스템 주체를 지을 길도 0 이다. */
    @Test
    fun `app main 의 어디도 시스템 주체를 짓지 않는다`() {
        callersOf(BID_ACTOR_SYSTEM_INIT) shouldBe emptySet()
    }

    /** 검토 r5 R5-7 — 위 등식의 in-file 양성 대조: 시스템 주체 생성자를 부르는 줄은 술어에 걸린다. */
    @Test
    fun `시스템 주체를 짓는 class 는 술어에 걸린다 — 양성 대조`() {
        val line =
            "9: invokespecial #12 // Method bidvector/workflow/bidding/BidActor\$System.\"<init>\":" +
                "(Ljava/lang/String;)V"

        callers(mapOf("http/Maker" to line), BID_ACTOR_SYSTEM_INIT) shouldBe setOf("http/Maker")
    }

    @Test
    fun `app main 의 어디도 AccountId 를 만들지 않는다`() {
        callersOf(ACCOUNT_ID_INIT) shouldBe emptySet()
    }

    @Test
    fun `AccountId 를 생성자로 받는 class 는 운반 자리 하나다`() {
        constructorsTaking(outputs, ACCOUNT_ID_TYPE) shouldBe setOf("http/AuthenticationResult\$Authenticated")
    }

    /** 양성 대조 — 래퍼 타입으로 부르는 `getAttribute` 와 Class 상수도 술어에 걸린다(verifier r2 우회 E 의 모양). */
    @Test
    fun `래퍼로 속성을 읽고 Jackson 으로 AccountId 를 만드는 class 는 술어에 걸린다 — 양성 대조`() {
        val bypass =
            listOf(
                "4: invokevirtual #9 // Method jakarta/servlet/ServletRequestWrapper.getAttribute:" +
                    "(Ljava/lang/String;)Ljava/lang/Object;",
                "12: ldc #15 // class bidvector/workflow/access/AccountId",
            ).joinToString("\n")
        val simulated = mapOf("http/Bypass" to bypass)

        signatureCallers(simulated, GET_ATTRIBUTE_SIGNATURE) shouldBe setOf("http/Bypass")
        classConstantHolders(simulated, ACCOUNT_ID_INTERNAL) shouldBe setOf("http/Bypass")
    }

    /** 양성 대조 — 새 셋 술어가 javap 문면에서 실제로 잡는지(verifier 변이 M2 · M4 모양). */
    @Test
    fun `속성을 직접 읽거나 AccountId 를 만들거나 주입받는 class 는 새 술어에 걸린다 — 양성 대조`() {
        val handler =
            listOf(
                "  public bidvector.app.http.NewHandler(bidvector.workflow.access.AccountId);",
                "4: invokeinterface #9 // InterfaceMethod " +
                    "jakarta/servlet/http/HttpServletRequest.getAttribute:(Ljava/lang/String;)Ljava/lang/Object;",
                "9: invokespecial #12  // Method bidvector/workflow/access/AccountId.\"<init>\":(J)V",
            ).joinToString("\n")
        val simulated = mapOf("http/NewHandler" to handler)

        signatureCallers(simulated, GET_ATTRIBUTE_SIGNATURE) shouldBe setOf("http/NewHandler")
        callers(simulated, ACCOUNT_ID_INIT) shouldBe setOf("http/NewHandler")
        constructorsTaking(simulated, ACCOUNT_ID_TYPE) shouldBe setOf("http/NewHandler")
    }

    /** 양성 대조 — 술어가 javap 문면에서 실제로 잡는지, 바이트코드 없이 고정한다. */
    @Test
    fun `라벨을 파싱하는 새 class 는 세 술어에 걸린다 — 양성 대조`() {
        val parser =
            listOf(
                "#12 = Utf8               account:",
                "#30 = Utf8               bidvector.http.subject",
                "4: invokestatic  #20  // Method bidvector/app/http/ErrorBodyKt.authenticatedSubject:(L…;)L…;",
            ).joinToString("\n")
        val simulated = mapOf("http/NewHandler" to parser)

        utf8Holders(simulated) { it.startsWith(ACCOUNT_LABEL_PREFIX) } shouldBe setOf("http/NewHandler")
        utf8Holders(simulated) { it == AUDIT_SUBJECT_ATTRIBUTE } shouldBe setOf("http/NewHandler")
        callers(simulated, SUBJECT_ACCESSOR) shouldBe setOf("http/NewHandler")
    }

    private fun classesWithUtf8(predicate: (String) -> Boolean): Set<String> = utf8Holders(outputs, predicate)

    private fun callersOf(method: String): Set<String> = callers(outputs, method)

    private companion object {
        const val ACCOUNT_LABEL_PREFIX = "account:"
        const val SUBJECT_ACCESSOR = "bidvector/app/http/ErrorBodyKt.authenticatedSubject:"
        const val ACCOUNT_ACCESSOR = "bidvector/app/http/AuthenticatedAccountKt.authenticatedAccount:"

        /** 소유자(타입)와 무관한 **메서드 서명** — 요청 · 래퍼 · 하위 타입 어느 것으로 불러도 같은 서명이다(V2-N1). */
        const val GET_ATTRIBUTE_SIGNATURE = ".getAttribute:(Ljava/lang/String;)Ljava/lang/Object;"
        const val ACCOUNT_ID_INTERNAL = "bidvector/workflow/access/AccountId"
        const val ACCOUNT_ID_INIT = "bidvector/workflow/access/AccountId.\"<init>\":"
        const val ACCOUNT_ID_TYPE = "bidvector.workflow.access.AccountId"
        const val JSON_NODE = "tools/jackson/databind/JsonNode"
        const val BID_ACTOR_SYSTEM_INIT = "bidvector/workflow/bidding/BidActor\$System.\"<init>\":"
        const val GUARD = "http/AccountBindingGuard"
        const val JSON_PARSER_RETURN = ")Ltools/jackson/core/JsonParser;"
        val BINDING_CAPABLE =
            listOf("Ljava/lang/Class;", "/JavaType;", "/TypeReference;", "/ObjectReadContext;")

        /** 오늘 관측(javap) — `JsonNode` 접근자 19 · 거부 가드의 셋. */
        val OBSERVED_JACKSON_MEMBERS =
            setOf(
                "tools/jackson/databind/JsonNode.asInt:()I",
                "tools/jackson/databind/JsonNode.booleanValue:()Z",
                "tools/jackson/databind/JsonNode.canConvertToInt:()Z",
                "tools/jackson/databind/JsonNode.canConvertToLong:()Z",
                "tools/jackson/databind/JsonNode.decimalValue:()Ljava/math/BigDecimal;",
                "tools/jackson/databind/JsonNode.get:(Ljava/lang/String;)Ltools/jackson/databind/JsonNode;",
                "tools/jackson/databind/JsonNode.has:(Ljava/lang/String;)Z",
                "tools/jackson/databind/JsonNode.intValue:()I",
                "tools/jackson/databind/JsonNode.isArray:()Z",
                "tools/jackson/databind/JsonNode.isBoolean:()Z",
                "tools/jackson/databind/JsonNode.isIntegralNumber:()Z",
                "tools/jackson/databind/JsonNode.isNull:()Z",
                "tools/jackson/databind/JsonNode.isNumber:()Z",
                "tools/jackson/databind/JsonNode.isObject:()Z",
                "tools/jackson/databind/JsonNode.isString:()Z",
                "tools/jackson/databind/JsonNode.longValue:()J",
                "tools/jackson/databind/JsonNode.propertyNames:()Ljava/util/Collection;",
                "tools/jackson/databind/JsonNode.stringValue:()Ljava/lang/String;",
                "tools/jackson/databind/JsonNode.values:()Ljava/util/Collection;",
                "tools/jackson/databind/DeserializationContext.reportInputMismatch:" +
                    "(Ltools/jackson/databind/ValueDeserializer;Ljava/lang/String;[Ljava/lang/Object;)" +
                    "Ljava/lang/Object;",
                "tools/jackson/databind/ValueDeserializer.\"<init>\":()V",
                "tools/jackson/databind/module/SimpleModule.\"<init>\":(Ljava/lang/String;)V",
            )

        /** 속성을 읽는 자리 — 계정 접근자 · 주체·correlation 접근자 · 감사 필터 · 인증 필터. */
        val ATTRIBUTE_READERS =
            setOf(
                "http/AuthenticatedAccountKt",
                "http/ErrorBodyKt",
                "http/RequestAuditFilter",
                "http/RequestAuthenticationFilter",
            )

        /** 라벨을 만드는 자리 — `BrowserSessionAuthenticator.subjectOf`. */
        val LABEL_WRITERS = setOf("http/BrowserSessionAuthenticator\$Companion")

        /** 운영자 주체(`OperatorId`)를 라벨에서 얻는 기존 자리 — 전략 편집의 감사 주체. */
        val SUBJECT_READERS = setOf("http/StrategyEditControllerKt")

        /** 속성을 적는 필터 · 라벨을 감사 행에 옮기는 필터 · 접근자의 정의. */
        val SUBJECT_KEY_HOLDERS =
            setOf("http/ErrorBodyKt", "http/RequestAuthenticationFilter", "http/RequestAuditFilter")

        /** 계정 범위 경로의 주체 진입점 셋. */
        val ACCOUNT_READERS =
            setOf("http/BidCaseCommandController", "http/BidCaseQueryController", "http/BidCaseHttp")
    }
}

private val UTF8_LINE = Regex("""=\s+Utf8\s+(.*)$""")

private fun utf8Holders(
    outputs: Map<String, String>,
    predicate: (String) -> Boolean,
): Set<String> =
    outputs
        .filterValues { text -> text.lineSequence().any { line -> utf8Value(line)?.let(predicate) == true } }
        .keys

private fun utf8Value(line: String): String? =
    UTF8_LINE
        .find(line)
        ?.groupValues
        ?.get(1)

/** 그 **서명**의 메서드를 부르는 class — 소유자 타입을 보지 않는다(`// Method` · `// InterfaceMethod` 줄). */
private fun signatureCallers(
    outputs: Map<String, String>,
    signature: String,
): Set<String> =
    outputs
        .filterValues { text -> text.lineSequence().any { "Method " in it && it.endsWith(signature) } }
        .keys

/**
 * Jackson 멤버를 가리키는 줄의 **(소유자, 멤버 서명)** — 호출(`Method` · `InterfaceMethod`)과 상수 풀(`Methodref` ·
 * `InterfaceMethodref` · `MethodHandle`). jackson3(`tools/jackson`) · jackson2(`com/fasterxml/jackson`) 둘 다 본다.
 */
private val JACKSON_MEMBER = Regex("""((?:tools/jackson|com/fasterxml/jackson)/[\w/$]+\.[\w<>"$]+:\([^)]*\)\S+)""")

private fun jacksonMembers(outputs: Map<String, String>): Set<String> =
    outputs.values
        .flatMap { text ->
            text
                .lineSequence()
                .filter { "Method" in it }
                .flatMap { line -> JACKSON_MEMBER.findAll(line).map { it.groupValues[1] } }
                .toList()
        }.toSet()

/** class 선언 줄(줄 머리의 `… class X extends … implements …`)에 Jackson 타입이 있는 app class. */
private val JACKSON_SUPERTYPE =
    Regex(
        """^(?:[a-z]+ )*(?:class|interface) \S+ (?:extends|implements) """ +
            """.*\b(?:tools\.jackson|com\.fasterxml\.jackson)\.""",
    )

private fun jacksonSubclasses(outputs: Map<String, String>): Set<String> =
    outputs
        .filterValues { text ->
            text.lineSequence().any { JACKSON_SUPERTYPE.containsMatchIn(it) }
        }.keys

private val ACCOUNT_ID_CLASS: Class<*> = bidvector.workflow.access.AccountId::class.java

/** 요청 매핑 메서드 — `@RequestMapping` 과 그 합성(`@GetMapping` 등)을 Spring 의 메타 애너테이션 판정으로 고른다. */
private fun requestMappingMethods(classes: List<Class<*>>): List<java.lang.reflect.Method> =
    classes.flatMap { type ->
        type.declaredMethods.filter {
            org.springframework.core.annotation.AnnotatedElementUtils
                .hasAnnotation(it, org.springframework.web.bind.annotation.RequestMapping::class.java)
        }
    }

/** 그 타입(제네릭 인자 · 배열 원소 포함)이 `AccountId` 이거나, `AccountId` 를 필드 · 생성자 매개변수로 가진다. */
private fun carriesAccountId(type: java.lang.reflect.Type): Boolean =
    when (type) {
        is Class<*> -> {
            type == ACCOUNT_ID_CLASS ||
                (type.isArray && carriesAccountId(type.componentType)) ||
                type.declaredFields.any { it.type == ACCOUNT_ID_CLASS } ||
                type.declaredConstructors.any { ACCOUNT_ID_CLASS in it.parameterTypes }
        }

        is java.lang.reflect.ParameterizedType -> {
            carriesAccountId(type.rawType) || type.actualTypeArguments.any(::carriesAccountId)
        }

        is java.lang.reflect.WildcardType -> {
            type.upperBounds.any(::carriesAccountId)
        }

        is java.lang.reflect.GenericArrayType -> {
            carriesAccountId(type.genericComponentType)
        }

        else -> {
            false
        }
    }

/** app main class 전수 — javap 틀과 같은 파일 목록을 이름으로 적재한다(초기화하지 않는다). */
private fun appClasses(): List<Class<*>> {
    val root = File("build/classes/kotlin/main/bidvector/app")
    check(root.isDirectory) { "빌드 산출물을 찾지 못했다: ${root.absolutePath} — :app:compileKotlin 선행 필요" }
    val loader = AccountCarrierStructureTest::class.java.classLoader
    return root
        .walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .map {
            "bidvector.app." +
                it
                    .relativeTo(root)
                    .path
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')
        }.map { Class.forName(it, false, loader) }
        .toList()
}

/** G11 양성 대조용 — 매핑 애너테이션을 가진 메서드 넷(직접 · 묶음 · 목록 · 평범). */
private class G11Probe {
    class Wrapped(
        val account: bidvector.workflow.access.AccountId,
    )

    @org.springframework.web.bind.annotation.GetMapping("/direct")
    fun direct(account: bidvector.workflow.access.AccountId): String = account.toString()

    @org.springframework.web.bind.annotation.GetMapping("/wrapped")
    fun wrapped(form: Wrapped): String = form.account.toString()

    @org.springframework.web.bind.annotation.PostMapping("/listed")
    fun listed(accounts: List<bidvector.workflow.access.AccountId>): String = accounts.toString()

    @org.springframework.web.bind.annotation.GetMapping("/plain")
    fun plain(caseId: String): String = caseId
}

/** 그 타입의 **Class 상수**(`ldc` · `ldc_w`)를 드는 class — 반사 · 데이터 바인딩으로 그 타입을 만드는 길의 표지다. */
private fun classConstantHolders(
    outputs: Map<String, String>,
    internalName: String,
): Set<String> {
    val ldc = Regex("""\bldc(_w)?\s+#\d+\s+// class ${Regex.escape(internalName)}$""")
    return outputs.filterValues { text -> text.lineSequence().any { ldc.containsMatchIn(it.trim()) } }.keys
}

/** 그 메서드를 **부르는** class — 정의한 class 자신은 빼고 센다(정의는 호출이 아니다). */
private fun callers(
    outputs: Map<String, String>,
    method: String,
): Set<String> {
    // 키는 `bidvector/app/` 아래 상대 경로다 — 메서드 소유자도 같은 모양으로 맞춘다.
    val owner = method.substringBefore('.').removePrefix("bidvector/app/")
    return outputs
        .filterKeys { it != owner }
        .filterValues { text -> text.lineSequence().any { it.contains("Method $method") } }
        .keys
}

/**
 * 생성자 매개변수에 [type] 이 있는 class — javap `-p` 의 생성자 선언 줄(`<FQN>(…);`)을 읽는다. 키는
 * `bidvector/app/` 아래 상대 경로이고 FQN 은 그 경로에서 만든다.
 */
private fun constructorsTaking(
    outputs: Map<String, String>,
    type: String,
): Set<String> =
    outputs
        .filter { (key, text) ->
            val fqn = Regex.escape("bidvector.app." + key.replace('/', '.'))
            val declaration = Regex("""^\s*(?:public |protected |private )?(?:synthetic )?$fqn\(([^)]*)\);\s*$""")
            text.lineSequence().any { line ->
                declaration
                    .find(line)
                    ?.groupValues
                    ?.get(1)
                    ?.contains(type) == true
            }
        }.keys

private fun appClassOutputs(): Map<String, String> {
    val root = File("build/classes/kotlin/main/bidvector/app")
    check(root.isDirectory) { "빌드 산출물을 찾지 못했다: ${root.absolutePath} — :app:compileKotlin 선행 필요" }
    return root
        .walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .associate { it.relativeTo(root).path.removeSuffix(".class") to javap(it) }
}

private fun javap(classFile: File): String {
    val process =
        ProcessBuilder(javapPath(), "-p", "-v", "-c", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "javap 실행 실패(exit=$exitCode): ${classFile.name}\n$output" }
    return output
}

private fun javapPath(): String = File(System.getProperty("java.home"), "bin/javap").absolutePath
