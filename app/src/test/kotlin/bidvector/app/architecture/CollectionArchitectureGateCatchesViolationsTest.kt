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
 * M6/6F-8 수집 배선 게이트의 **음성** 쪽 — production 을 지키는 **같은 규칙 값**에 fixture 루트를 넣어 심은
 * 위반을 잡는지 잰다(`ArchitectureGateCatchesViolationsTest` 와 같은 형태). 위반 상세에서 심은 클래스 이름과
 * **어느 대상 때문에** 잡혔는지를 함께 확인한다 — 다른 이유로 잡혀도 통과하는 masking 을 막는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionArchitectureGateCatchesViolationsTest {
    private val policy = ArchitecturePolicy.load()
    private val rules = CollectionArchitectureRules()
    private val fixtureRoot = "${policy.packageRoot}.archfixture.violating"
    private val violating: JavaClasses = ClassFileImporter().importPackages(fixtureRoot)

    private fun useCaseRules() =
        rules.collectionMustNotReadRawFields(
            collectionPackage = "$fixtureRoot.workflow.collection",
            procurementPackage = "${policy.packageRoot}.procurement",
            allowedTypes = policy.collectionAllowedProcurementTypes.toSet(),
            passThroughTypes = policy.collectionPassThroughTypes.toSet(),
            forbiddenFieldTypes = policy.collectionForbiddenFieldTypes.toSet(),
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
        )

    @Test
    fun `use case 패키지 밖 이웃 workflow 패키지의 헬퍼가 원문 필드를 읽어도 잡는다 — 한 걸음 옮긴 변이`() {
        moduleRules().mustReport("RogueNeighborTitlePeek", "FieldConcept")
        moduleRules().mustReport("RogueNeighborTitlePeek", "RawNoticeObservation.valueOf")
    }

    @Test
    fun `app 의 이웃 클래스가 원문 관측의 항목 원문 텍스트를 꺼내도 잡는다`() {
        moduleRules().mustReport("RogueRawFieldPeek", "RawNoticeObservation.getSourceText")
    }

    private fun reflectionRules() =
        rules.moduleMustNotUseReflection(
            roots = listOf("$fixtureRoot.workflow", "$fixtureRoot.app"),
            reflectionPackages = policy.reflectionPackages.toSet(),
            allowedReferencers = policy.reflectionAllowedReferencers.toSet(),
            classType = policy.reflectionClassType,
            allowedClassMembers = policy.reflectionClassAllowedMembers.toSet(),
        )

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

    @Test
    fun `Class 의 이름 조회는 잡지 않는다 — 규칙이 반사가 아닌 사용까지 막는 과잉이 아니다`() {
        reflectionRules().mustNotReport("CleanNameLookup")
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
        rules
            .divisionParseCallsMustBeAllowedPairs(
                policy.divisionParseType,
                policy.divisionParseMembers.toSet(),
                policy.divisionParseAllowedPairs.toSet(),
            ).mustReport("RogueDivisionFromString", "fromLabel")
    }

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
    ) = rules.appTypesMustBeReferencedOnlyBy("$fixtureRoot.app", types, allowed, "음성 대조 — $label")

    private fun List<ArchRule>.mustNotReport(mentioned: String) {
        val details =
            flatMap { rule ->
                rule
                    .allowEmptyShould(true)
                    .evaluate(violating)
                    .failureReport.details
            }
        details.filter { it.contains(mentioned) }.shouldBeEmpty()
    }

    private fun List<ArchRule>.mustReport(
        mentioned: String,
        target: String,
    ) {
        val details =
            flatMap { rule ->
                rule
                    .allowEmptyShould(true)
                    .evaluate(violating)
                    .failureReport.details
            }
        details.filter { it.contains(mentioned) && it.contains(target) }.shouldNotBeEmpty()
    }
}
