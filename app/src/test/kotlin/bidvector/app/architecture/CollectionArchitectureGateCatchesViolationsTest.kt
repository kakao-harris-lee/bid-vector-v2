package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * 수집 배선 게이트의 **음성** 쪽 — production 을 지키는 **같은 규칙 값**에 fixture 루트를 넣어 심은
 * 위반을 잡는지 잰다(`ArchitectureGateCatchesViolationsTest` 와 같은 형태). 위반 상세에서 심은 클래스 이름과
 * **어느 대상 때문에** 잡혔는지를 함께 확인한다 — 다른 이유로 잡혀도 통과하는 masking 을 막는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionArchitectureGateCatchesViolationsTest {
    private val policy = ArchitecturePolicy.load()
    private val rules = CollectionArchitectureRules(policy.depth(DepthAxis.REFLECTION))
    private val divisionRules = DivisionValueRules(policy.divisionValueType)
    private val fixtureRoot = "${policy.packageRoot}.archfixture.violating"
    private val violating: JavaClasses = ClassFileImporter().importPackages(fixtureRoot)

    private fun useCaseRules() =
        rules.collectionMustNotReadRawFields(
            collectionPackage = "$fixtureRoot.workflow.collection",
            procurementPackage = "${policy.packageRoot}.procurement",
            allowedTypes = policy.collectionAllowedProcurementTypes.toSet(),
            passThroughTypes = policy.collectionPassThroughTypes.toSet(),
            forbiddenFieldTypes = policy.collectionForbiddenFieldTypes.toSet(),
            depth = policy.depth(DepthAxis.COLLECTION_PROCUREMENT),
        )

    @Test
    fun `use case 자리에서 필드 계약 타입을 이름 붙이면 잡는다`() {
        useCaseRules().mustReport("RogueRawKeyReader", "KonepsFieldContractRegistry")
        useCaseRules().mustReport("RogueRawKeyReader", "FieldConcept")
    }

    @Test
    fun `use case 자리에서 통과 전용 원문의 멤버를 부르면 잡는다`() {
        useCaseRules().mustReport("RogueRawKeyReader", "RawNoticeObservation.valueOf")
    }

    @Test
    fun `결과 타입이 원문이나 관측 키를 필드로 나르면 잡는다`() {
        useCaseRules().mustReport("RogueCarrierResult", "RawNoticeObservation")
        useCaseRules().mustReport("RogueCarrierResult", "ObservationKey")
    }

    private fun moduleRules() =
        rules.moduleMustNotReadRawFields(
            roots = listOf("$fixtureRoot.workflow", "$fixtureRoot.app"),
            rawAccessTypes = policy.rawAccessTypes.toSet(),
            allowedReferencers = policy.rawAccessAllowedReferencers.toSet(),
            passThroughTypes = policy.collectionPassThroughTypes.toSet(),
            allowedMemberAccessors = policy.rawAccessAllowedMemberAccessors.toSet(),
            depth = policy.depth(DepthAxis.RAW_ACCESS),
        )

    @Test
    fun `use case 패키지 밖 이웃 workflow 패키지의 헬퍼가 원문 필드를 읽어도 잡는다 — 한 걸음 옮긴 변이`() {
        moduleRules().mustReport("RogueNeighborTitlePeekKt", "FieldConcept")
        moduleRules().mustReport("RogueNeighborTitlePeekKt", "RawNoticeObservation.valueOf")
    }

    @Test
    fun `app 의 이웃 클래스가 원문 관측의 항목 원문 텍스트를 꺼내도 잡는다`() {
        moduleRules().mustReport("RogueRawFieldPeek", "RawNoticeObservation.getSourceText")
    }

    /**
     * A-2 — production 을 지키는 **같은 규칙 값**에 fixture 뿌리를 넣는다. 뿌리에 `adapters` 가 들었고(운영자
     * 결정 2026-10-03) 허용은 (클래스, 타입)·(클래스, 멤버) 쌍이다. 멤버 쌍에 [CLEAN_NAME_LOOKUP_PAIR] 하나를
     * 더하는 것은 과잉 대조를 세우기 위해서다 — 쌍 등식에서는 「`getName` 은 어디서든 괜찮다」가 성립하지
     * 않으므로, 「**등재된** 이름 조회는 신고되지 않는다」로 과잉을 재야 한다.
     */
    private fun reflectionRules(memberPairs: Set<Pair<String, String>> = defaultMemberPairs()) =
        rules.moduleMustNotUseReflection(
            roots = listOf("$fixtureRoot.workflow", "$fixtureRoot.app", "$fixtureRoot.adapters"),
            reflectionPackages = policy.reflectionPackages.toSet(),
            allowedTypePairs = policy.reflectionTypePairs.toSet(),
            classType = policy.reflectionClassType,
            allowedMemberPairs = memberPairs,
        )

    private fun defaultMemberPairs() =
        policy.reflectionClassMemberPairs.toSet() + ("$fixtureRoot.app.$CLEAN_NAME_LOOKUP_PAIR" to "getName")

    @Test
    fun `원문 타입을 이름 붙이지 않고 리플렉션으로 값을 꺼내도 잡는다 — getMethod 와 invoke`() {
        reflectionRules().mustReport("RogueReflectionPeek", "java.lang.reflect.Method")
        reflectionRules().mustReport("RogueReflectionPeek", "java.lang.Class.getMethod")
    }

    @Test
    fun `이름으로 클래스를 불러오거나 메서드 핸들·코틀린 반사를 쓰는 길도 잡는다`() {
        reflectionRules().mustReport("RogueReflectionPeek", "java.lang.Class.forName")
        reflectionRules().mustReport("RogueReflectionPeek", "java.lang.invoke.MethodHandles")
        reflectionRules().mustReport("RogueReflectionPeek", "kotlin.reflect.KClass")
    }

    /** A-2 — 뿌리가 `workflow`·`app` 뿐이던 판에서 게이트 밖이던 자리. 한 걸음 옮긴 반사를 이제 잡는다. */
    @Test
    fun `adapters 층의 반사도 잡는다 — 뿌리를 한 걸음 옮긴 변이`() {
        reflectionRules().mustReport("RogueAdapterReflectionPeek", "java.lang.Class.getMethod")
        reflectionRules().mustReport("RogueAdapterReflectionPeek", "java.lang.Class.forName")
        reflectionRules().mustReport("RogueAdapterReflectionPeek", "java.lang.reflect.Method")
    }

    /** A-2 쌍 축 — 뿌리를 앞 판으로 좁히면 같은 fixture 가 신고되지 않는다(뿌리 확장의 음성 대조). */
    @Test
    fun `뿌리를 좁히면 adapters 층의 반사가 신고되지 않는다 — 뿌리 확장의 음성 대조`() {
        val narrowed =
            rules.moduleMustNotUseReflection(
                roots = listOf("$fixtureRoot.workflow", "$fixtureRoot.app"),
                reflectionPackages = policy.reflectionPackages.toSet(),
                allowedTypePairs = policy.reflectionTypePairs.toSet(),
                classType = policy.reflectionClassType,
                allowedMemberPairs = defaultMemberPairs(),
            )

        narrowed.mustNotReport("RogueAdapterReflectionPeek")
        narrowed.mustReport("RogueReflectionPeek", "java.lang.Class.getMethod")
    }

    /**
     * A-2 쌍 축 — 등재된 클래스가 **새 반사 멤버**를 더 부르는 길. 멤버 이름만 전역으로 허용하거나 클래스만
     * 등재하면 초록인 자리다. `getName` 쌍으로만 등재하고 더 부른 멤버만 신고됨을 잰다.
     */
    @Test
    fun `등재된 클래스가 새 반사 멤버를 더 부르면 그 멤버만 잡는다 — 멤버 쌍 등식 축`() {
        val holder = "$fixtureRoot.adapters.RogueAdapterNameLookupGainingReflection"
        val registered = setOf(holder to "getName")

        val details = reflectionRules(registered).details()

        details.filter { it.contains("$holder -> java.lang.Class.getDeclaredMethod") }.shouldNotBeEmpty()
        details.filter { it.contains("$holder -> java.lang.Class.getName") }.shouldBeEmpty()
    }

    @Test
    fun `등재된 이름 조회는 잡지 않는다 — 규칙이 반사가 아닌 사용까지 막는 과잉이 아니다`() {
        reflectionRules().mustNotReport(CLEAN_NAME_LOOKUP_PAIR)
    }

    @Test
    fun `등재되지 않은 이름 조회는 잡는다 — 멤버 이름을 전역으로 허용하지 않는다`() {
        reflectionRules(policy.reflectionClassMemberPairs.toSet())
            .mustReport(CLEAN_NAME_LOOKUP_PAIR, "java.lang.Class.getName")
    }

    @Test
    fun `러너와 배선 밖에서 수집 use case 를 참조하면 잡는다 — 컨트롤러·리스너로 수집을 여는 길`() {
        typeRule(setOf(policy.collectionUseCaseType), policy.collectionUseCaseReferencers.toSet(), "use case")
            .mustReport("RogueUseCaseCaller", "CollectNoticesUseCase")
    }

    @Test
    fun `공고명 원시 키를 상수 풀에 가진 허용 밖 클래스를 잡는다`() {
        rules
            .titleKeyLiteralMustStayInAllowedClasses(
                CollectionArchitectureGateTest.noticeTitleRawKey(),
                policy.titleKeyAllowedClasses.toSet(),
            ).mustReport("RogueTitleKeyLiteral", CollectionArchitectureGateTest.noticeTitleRawKey())
    }

    @Test
    fun `업무구분 세부 분류 원시 키를 상수 풀에 가진 허용 밖 클래스를 잡는다 — 키마다`() {
        val keys = CollectionArchitectureGateTest.classificationRawKeys(policy.classificationKeyConcepts)

        keys.size shouldBe policy.classificationKeyConcepts.size
        keys.forEach { key ->
            rules
                .classificationKeyLiteralsMustStayInAllowedClasses(
                    setOf(key),
                    policy.classificationKeyAllowedClasses.toSet(),
                ).mustReport("RogueClassificationKeyLiteral", key)
        }
    }

    @Test
    fun `URL 경로에서 대분류를 짓는 허용 밖 호출을 잡는다`() {
        divisionAcquisition().mustReport("RogueDivisionFromString", "BusinessDivision#fromLabel")
        divisionTypeAccess().mustReport("RogueDivisionFromString", "fromLabel")
    }

    /**
     * MV1 — 변환 **함수를 부르지 않고** enum 상수를 읽어 경로에서 값을 짓는다. 멤버 이름 목록만
     * 보면 전체 `check` 가 초록이 되는 표기다.
     */
    @Test
    fun `enum 상수를 읽어 경로에서 대분류를 짓는 허용 밖 클래스를 잡는다 — 멤버 호출이 하나도 없어도`() {
        divisionTypeAccess().mustReport("RogueDivisionFromEnumConstant", "CONSTRUCTION")
        divisionAcquisition().mustReport("RogueDivisionFromEnumConstant", "BusinessDivision#SERVICE")
    }

    /**
     * MV2 — 타입 자신에 생긴 **새 파생 멤버**를 부른다. production 타입에 멤버를 심을 수 없어
     * 대상 타입만 같은 모양의 fixture enum 으로 바꾸고 규칙 값은 그대로다(허용 집합 비움). 멤버
     * 목록이 없어 어떤 이름이든 쌍으로 관측된다.
     */
    @Test
    fun `타입에 새로 생긴 파생 멤버를 부르는 자리를 잡는다 — 멤버 이름 목록이 없다`() {
        val fixtureEnum = DivisionValueRules("$fixtureRoot.collection.RogueDivisionLikeEnum")

        fixtureEnum
            .typeAccessRules(emptySet())
            .mustReport("RogueDivisionFromCompanionDerivation", "ofOperationPath")
        fixtureEnum
            .acquisitionRules(emptySet())
            .mustReport("RogueDivisionFromCompanionDerivation", "RogueDivisionLikeEnum#ofOperationPath")
    }

    /** MV4 — `java.lang.Enum.valueOf(Class, String)`. 타입 이름이 남는 자리는 클래스 객체뿐이다. */
    @Test
    fun `Enum valueOf 로 대분류를 만드는 허용 밖 클래스를 잡는다 — 호출 소유자에 타입 이름이 없어도`() {
        divisionClassObject().mustReport("RogueDivisionFromEnumBridge", "BusinessDivision")
    }

    /**
     * 규칙이 과잉이 아니다 — 대조 둘. `CleanNameLookup` 은 대분류를 **아예 언급하지 않는** 클래스다(약한 대조:
     * 규칙이 대분류를 언급하는 모든 클래스를 신고하도록 잘못 써도 초록이다). `CleanDivisionCarrier` 는 대분류를
     * **가지고 있지만 만들지 않는** 클래스라 과잉 경계 위에 있다 — 축 ②의 자기 소유 읽기 제외 분기를 지우면 이
     * 단언이 RED 가 된다(측정). 운반 슬롯 getter 를 **부르는 쪽**이 축 ②에 드는 것은 의도이고
     * (정책 주석), 나르기만 하는 쪽이 드는 것은 과잉이다.
     */
    @Test
    fun `대분류를 만들지 않는 fixture 는 대분류 축 셋에 걸리지 않는다 — 언급조차 없는 것과 나르기만 하는 것`() {
        listOf("CleanNameLookup", "CleanDivisionCarrier").forEach { clean ->
            divisionTypeAccess().mustNotReport(clean)
            divisionAcquisition().mustNotReport(clean)
            divisionClassObject().mustNotReport(clean)
        }
    }

    private fun divisionTypeAccess() = divisionRules.typeAccessRules(policy.divisionTypeAccessPairs.toSet())

    private fun divisionAcquisition() = divisionRules.acquisitionRules(policy.divisionAcquisitionPairs.toSet())

    private fun divisionClassObject() = divisionRules.classObjectRules(policy.divisionClassObjectReferencers.toSet())

    @Test
    fun `app 안의 두 번째 러너를 잡는다`() {
        typeRule(policy.runnerTypes.toSet(), policy.runnerAllowedReferencers.toSet(), "러너")
            .mustReport("RogueCollectionRunner", "ApplicationRunner")
    }

    @Test
    fun `서비스 키 원문 설정을 배선 밖에서 읽으면 잡는다`() {
        typeRule(setOf(policy.serviceKeyType), policy.serviceKeyReaders.toSet(), "키")
            .mustReport("RogueServiceKeyReader", "KonepsCredentialProperties")
    }

    @Test
    fun `수집 로그 출구 밖의 로거 사용을 잡는다`() {
        typeRule(policy.loggingTypes.toSet(), policy.loggingAllowedUsers.toSet(), "로거")
            .mustReport("RogueLoggerUser", "LoggerFactory")
    }

    @Test
    fun `app 헬퍼가 원문 저장 포트나 정규화 함수를 직접 부르면 잡는다`() {
        val portCalls =
            ArchitectureRules(policy).appPortCallsMustBeAllowedPairs(
                appRoot = "$fixtureRoot.app",
                ports = policy.portCallPorts.toSet(),
                allowedPairs = policy.portCallAllowedPairs.toSet(),
            )

        portCalls.mustReport("RogueCollectionPortCaller", "RawObservationStore.append")
        portCalls.mustReport("RogueCollectionPortCaller", "CanonicalizeKt.canonicalize")
    }

    private fun typeRule(
        types: Set<String>,
        allowed: Set<String>,
        label: String,
    ) = rules.appTypesMustBeReferencedOnlyBy(
        "$fixtureRoot.app",
        types,
        allowed,
        "음성 대조 — $label",
        policy.depth(DepthAxis.USECASE),
    )

    private fun List<ArchRule>.details(): List<String> =
        flatMap { rule ->
            rule
                .allowEmptyShould(true)
                .evaluate(violating)
                .failureReport.details
        }

    private fun List<ArchRule>.mustNotReport(mentioned: String) {
        details().filter { it.contains(mentioned) }.shouldBeEmpty()
    }

    /**
     * vr L-5 · cr ④ — 대상은 **이름 경계까지** 맞춰 본다. 맨 `contains` 로 재면 `java.net.URL` 단언이
     * `java.net.URLConnection` 으로 잡혀도 통과해, 「다른 이유로 잡혔다」를 거르려는 취지가 헐거웠다.
     * 이 파일의 상세 줄은 규칙마다 꼬리가 다르므로(서술자·따옴표) 전체 일치가 아니라 경계 일치다.
     * **클래스 이름 쪽도 같은 경계로 본다**(PR #58 K — 앞 판은 그쪽만 맨 `contains` 였다).
     */
    private fun List<ArchRule>.mustReport(
        mentioned: String,
        target: String,
    ) {
        details()
            .filter { it.mentionsAtNameBoundary(mentioned) && it.mentionsAtNameBoundary(target) }
            .shouldNotBeEmpty()
    }

    /**
     * [target] 이 **더 긴 이름의 토막**으로 걸리지 않는지 — 앞뒤 글자가 식별자 문자면 다른 이름이다
     * (cr r2 L-6 — 앞 판은 뒤쪽만 봤다).
     *
     * `$` 와 `.` 은 **경계로 센다**: 같은 멤버의 합성 다리(`append$default`)와, 상세 줄이 전체 이름으로
     * 적는 멤버를 단순 이름 + 멤버로 단언하는 자리(`RawObservationStore.append` ⊂
     * `bidvector.procurement.RawObservationStore.append`)가 그 형태다.
     *
     * **남는 한 칸**: 그래서 `net.URL` 처럼 **점 뒤에서 시작하는 꼬리 조각**은 여전히 `java.net.URL` 로
     * 통과한다. 두 경우가 글자 종류로는 구별되지 않는다(둘 다 「점 뒤의 완전한 조각」이다). 닫으려면 규칙마다
     * 상세 형식을 알고 전체 일치로 비교해야 하고, 이 파일의 상세 꼬리는 규칙마다 다르다 — checklist 알려진
     * 제한. 전송 쪽 음성 단언은 이미 전체 일치다.
     */
    private fun String.mentionsAtNameBoundary(target: String): Boolean {
        var from = indexOf(target)
        while (from >= 0) {
            if (!getOrNull(from - 1).continuesName() && !getOrNull(from + target.length).continuesName()) return true
            from = indexOf(target, from + 1)
        }
        return false
    }

    /** 이름이 이어지는 글자인가 — `null`(줄 머리·줄 끝)과 `.`·`$` 는 경계다. */
    private fun Char?.continuesName(): Boolean = this != null && (isLetterOrDigit() || this == '_')

    private companion object {
        /** 과잉 대조 fixture — `Class` 의 이름 조회만 한다. 쌍 등식에서는 **등재해야** 조용하다. */
        const val CLEAN_NAME_LOOKUP_PAIR = "CleanNameLookup"
    }
}
