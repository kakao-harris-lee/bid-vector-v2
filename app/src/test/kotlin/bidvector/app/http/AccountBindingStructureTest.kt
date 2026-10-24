package bidvector.app.http

import bidvector.app.architecture.ArchitecturePolicy
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * **app main 이 요청 값으로 `AccountId` 를 지을 길의 구조 축**(M7/7B 계약 D-7B-6 · D-7B-7) — `AccountCarrierStructureTest` 에서 나눴다
 * (파일 크기 상한). ① Jackson — app main 이 가리키는 Jackson (소유자, 멤버) 쌍 전수와 Jackson 상속 class 집합을 오늘 관측값으로
 * 고정한다. ② 핸들러 매개변수 — 요청 매핑 메서드의 한 겹 구조 규칙과 그 모집단(라우트 센서스 · `@RestController`). 깊은 길은 짓는
 * 층에서 `AccountBindingGuard` 가 실패로 닫고 `AccountBindingGuardTest` 가 행동으로 잰다.
 */
class AccountBindingStructureTest {
    private val outputs: Map<String, String> by lazy { appClassOutputs() }

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
     * **관리 포트 핸들러는 이 모집단에 의도적으로 들지 않는다** — app main 의 class 가 아니라 Boot 의 actuator class 이고(모집단은 app main
     * class 파일 전수다), 가드도 그들에게 걸지 않는다(그 핸들러는 선택 본문을 `Map` 으로 바인딩한다 — 걸면 health 가 400 이 된다).
     */
    @Test
    fun `요청 매핑 메서드를 선언한 app class 는 전부 RestController 다 — 바인딩 가드의 적용 범위`() {
        val declaring = requestMappingMethods(appClasses()).map { it.declaringClass }.toSet()
        declaring.shouldNotBeEmpty()

        declaring
            .filterNot { it.isAnnotationPresent(org.springframework.web.bind.annotation.RestController::class.java) }
            .map { it.name } shouldBe emptyList()
    }

    /**
     * 팀장 조건 (a) — 가드가 연 바깥 패키지(`tools.jackson.core` · `tools.jackson.databind.module` ·
     * `org.springframework.web.bind` · `org.springframework.core` · `org.springframework.format` ·
     * `org.springframework.web.method.annotation`)를 **가리키는 app class 는 가드 파일의 class 들뿐이다**. 패키지 허용 등식은
     * class 단위가 아니므로(패키지가 열리면 어느 class 든 `JsonParser` · `ResolvableType` 을 쓸 수 있다) 그 사실을 이 등식이
     * 잠근다. 하위 패키지(`…core.annotation` 등)는 대상이 아니다 — 앞서부터 열려 있던 자리다.
     */
    @Test
    fun `가드가 연 바깥 패키지를 가리키는 app class 는 가드 파일의 class 들뿐이다`() {
        guardPackageHolders(outputs) shouldBe GUARD_FILE_CLASSES
    }

    /** 대조 — 다른 app class 가 `JsonParser` · `ResolvableType` 을 가리키면 걸리고, 하위 패키지(`core.annotation`)는 걸리지 않는다. */
    @Test
    fun `다른 class 가 가드의 패키지를 쓰면 술어에 걸린다 — 대조`() {
        val simulated =
            mapOf(
                "http/Parser" to "#7 = Class #8 // tools/jackson/core/JsonParser",
                "http/Resolver" to "  public org.springframework.core.ResolvableType type();",
                "http/Ordered" to "#9 = Utf8 Lorg/springframework/core/annotation/Order;",
            )

        guardPackageHolders(simulated) shouldBe setOf("http/Parser", "http/Resolver")
    }

    /**
     * 팀장 조건 (c) — 가드의 「대상이 있는 바인더는 전부 거부」는 **오늘 app main 의 `@ModelAttribute` 사용이 0** 이라는 관측 위에 서 있다.
     * 그 전제를 등식으로 잠근다(메서드 · 매개변수 둘 다). 뒤의 slice 가 쓰게 되면 이 등식이 먼저 붉고, 가드의 거부를 그 대상에 맞게 풀어야
     * 한다(가드 KDoc · `OPEN-7D-ACCOUNTID-CONSTRUCTION`).
     */
    @Test
    fun `app main 의 어디도 ModelAttribute 를 쓰지 않는다 — 바인딩 거부의 전제`() {
        modelAttributeUsers(appClasses()) shouldBe emptySet()
    }

    /** 양성 대조 — 모델 속성 메서드와 모델 속성 매개변수가 술어에 걸린다. */
    @Test
    fun `ModelAttribute 메서드와 매개변수는 술어에 걸린다 — 양성 대조`() {
        modelAttributeUsers(listOf(ModelAttributeProbe::class.java)) shouldBe setOf("vrAccount", "handler")
    }

    /** 양성 대조 — G11 의 모양(매개변수 `AccountId`)과 그것을 실은 묶음 타입이 규칙에 걸리고, 평범한 매개변수는 걸리지 않는다. */
    @Test
    fun `AccountId 를 싣는 핸들러 매개변수는 규칙에 걸린다 — 양성 대조`() {
        val probe =
            requestMappingMethods(listOf(G11Probe::class.java))
                .associate { it.name to it.genericParameterTypes.any(::carriesAccountId) }

        probe shouldBe mapOf("direct" to true, "wrapped" to true, "listed" to true, "plain" to false)
    }

    private companion object {
        const val JSON_NODE = "tools/jackson/databind/JsonNode"
        const val GUARD = "http/AccountBindingGuard"

        /** 가드 파일의 class 중 가드가 연 패키지를 가리키는 것 — 오늘 관측(javap). */
        val GUARD_FILE_CLASSES =
            setOf(GUARD, "http/RejectingAccountIdDeserializer", "http/RejectingAccountIdFormatter")
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
    }
}

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

/** 가드가 연 패키지의 **바로 아래 타입**(첫 글자가 대문자) — 하위 패키지는 고르지 않는다. 슬래시 · 점 두 표기를 다 본다. */
private val GUARD_PACKAGE_TYPE =
    Regex(
        """\b(?:tools[/.]jackson[/.]core|tools[/.]jackson[/.]databind[/.]module|""" +
            """org[/.]springframework[/.]web[/.]bind|org[/.]springframework[/.]core|""" +
            """org[/.]springframework[/.]format|""" +
            """org[/.]springframework[/.]web[/.]method[/.]annotation)[/.][A-Z]""",
    )

private fun guardPackageHolders(outputs: Map<String, String>): Set<String> =
    outputs.filterValues { text -> GUARD_PACKAGE_TYPE.containsMatchIn(text) }.keys

/** `@ModelAttribute` 를 단 메서드와, 매개변수에 단 메서드의 이름. */
private fun modelAttributeUsers(classes: List<Class<*>>): Set<String> =
    classes
        .flatMap { it.declaredMethods.toList() }
        .filter { method ->
            method.isAnnotationPresent(MODEL_ATTRIBUTE) ||
                method.parameters.any { it.isAnnotationPresent(MODEL_ATTRIBUTE) }
        }.map { it.name }
        .toSet()

private val MODEL_ATTRIBUTE = org.springframework.web.bind.annotation.ModelAttribute::class.java

/** (c) 양성 대조용 — 모델 속성 메서드 하나 · 모델 속성 매개변수를 받는 핸들러 하나. */
private class ModelAttributeProbe {
    @org.springframework.web.bind.annotation.ModelAttribute("vrAccount")
    fun vrAccount(account: bidvector.workflow.access.AccountId): String = account.toString()

    fun handler(
        @org.springframework.web.bind.annotation.ModelAttribute("vrAccount") held: Any,
    ): String = held.toString()
}
