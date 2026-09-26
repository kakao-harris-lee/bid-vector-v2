package bidvector.app.architecture

import bidvector.procurement.FieldConcept
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.sharedkernel.Resolution
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate

/**
 * M6/6F-8 수집 배선 게이트의 **양성** 쪽 — production 바이트코드가 규칙을 지킨다. 규칙과 허용 집합은
 * [CollectionArchitectureRules]·`architecture-policy.properties` 가 갖는다(음성 쪽은
 * `CollectionArchitectureGateCatchesViolationsTest`).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionArchitectureGateTest {
    private val policy = ArchitecturePolicy.load()
    private val rules = CollectionArchitectureRules()
    private val divisionRules = DivisionValueRules(policy.divisionValueType)
    private val appRoot = "${policy.packageRoot}.app"
    private val production: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TEST_FIXTURES)
            .importPackages(policy.packageRoot)

    @Test
    fun `수집 use case 패키지가 실제로 production 에 있다 — 규칙이 공허하지 않다`() {
        production.filter { it.packageName.startsWith(policy.collectionPackage) }.shouldNotBeEmpty()
    }

    @Test
    fun `수집 use case 는 원문 필드를 직접 읽지 않는다 — 참조 집합·통과 전용 멤버·결과 필드`() {
        rules
            .collectionMustNotReadRawFields(
                collectionPackage = policy.collectionPackage,
                procurementPackage = "${policy.packageRoot}.procurement",
                allowedTypes = policy.collectionAllowedProcurementTypes.toSet(),
                passThroughTypes = policy.collectionPassThroughTypes.toSet(),
                forbiddenFieldTypes = policy.collectionForbiddenFieldTypes.toSet(),
            ).checkAll()
    }

    @Test
    fun `수집 use case 의 procurement 참조 집합은 허용 집합과 같다 — 낡은 허용 항목이 게이트를 느슨하게 두지 않는다`() {
        rules.observedProcurementTypes(
            production,
            policy.collectionPackage,
            "${policy.packageRoot}.procurement",
        ) shouldBe policy.collectionAllowedProcurementTypes.toSet()
    }

    @Test
    fun `원문 값 획득 봉쇄의 모듈 root 가 실제로 production 에 있다 — 규칙이 공허하지 않다`() {
        policy.rawAccessRoots.forEach { root ->
            production.filter { it.packageName == root || it.packageName.startsWith("$root.") }.shouldNotBeEmpty()
        }
    }

    @Test
    fun `원문 키 접근 타입 참조와 통과 전용 멤버 접근은 workflow·app production 전체에서 허용 집합 밖에 없다`() {
        rules
            .moduleMustNotReadRawFields(
                roots = policy.rawAccessRoots,
                rawAccessTypes = policy.rawAccessTypes.toSet(),
                allowedReferencers = policy.rawAccessAllowedReferencers.toSet(),
                passThroughTypes = policy.collectionPassThroughTypes.toSet(),
                allowedMemberAccessors = policy.rawAccessAllowedMemberAccessors.toSet(),
            ).checkAll()
    }

    @Test
    fun `원문 값 획득 봉쇄의 허용 참조자·접근자 집합은 관측 집합과 같다 — 낡은 허용 항목이 게이트를 느슨하게 두지 않는다`() {
        rules.observedReferencers(
            production,
            policy.rawAccessRoots,
            policy.rawAccessTypes.toSet(),
        ) shouldBe policy.rawAccessAllowedReferencers.toSet()
        rules.observedMemberAccessors(
            production,
            policy.rawAccessRoots,
            policy.collectionPassThroughTypes.toSet(),
        ) shouldBe policy.rawAccessAllowedMemberAccessors.toSet()
    }

    @Test
    fun `workflow·app production 은 리플렉션 API 를 참조하지 않고 Class 는 이름 조회만 한다`() {
        rules
            .moduleMustNotUseReflection(
                roots = policy.rawAccessRoots,
                reflectionPackages = policy.reflectionPackages.toSet(),
                allowedReferencers = policy.reflectionAllowedReferencers.toSet(),
                classType = policy.reflectionClassType,
                allowedClassMembers = policy.reflectionClassAllowedMembers.toSet(),
            ).checkAll()
    }

    @Test
    fun `리플렉션 봉쇄의 허용 참조자·허용 Class 멤버는 관측 집합과 같다 — 규칙이 공허하지 않고 낡은 항목이 없다`() {
        rules.observedReflectionReferencers(
            production,
            policy.rawAccessRoots,
            policy.reflectionPackages.toSet(),
        ) shouldBe policy.reflectionAllowedReferencers.toSet()
        rules.observedClassMembers(
            production,
            policy.rawAccessRoots,
            policy.reflectionClassType,
        ) shouldBe policy.reflectionClassAllowedMembers.toSet()
    }

    @Test
    fun `수집 use case 를 참조하는 production 클래스는 허용 집합뿐이다 — HTTP 나 이벤트 리스너로 열리는 길이 없다`() {
        rules
            .appTypesMustBeReferencedOnlyBy(
                policy.packageRoot,
                setOf(policy.collectionUseCaseType),
                policy.collectionUseCaseReferencers.toSet(),
                "D-6F8-6 우회 5 — 수집 use case 참조 집합은 러너와 배선이다",
            ).checkAll()
        rules.observedReferencers(
            production,
            listOf(policy.packageRoot),
            setOf(policy.collectionUseCaseType),
        ) shouldBe policy.collectionUseCaseReferencers.toSet()
    }

    @Test
    fun `공고명 원시 키 리터럴은 계약 데이터를 실은 정책 클래스 밖 production 상수 풀에 없다`() {
        rules
            .titleKeyLiteralMustStayInAllowedClasses(noticeTitleRawKey(), policy.titleKeyAllowedClasses.toSet())
            .checkAll()
    }

    @Test
    fun `공고명 키 게이트가 실제로 정책 클래스를 본다 — 허용 집합을 비우면 정확히 그 클래스가 걸린다`() {
        val report =
            rules
                .titleKeyLiteralMustStayInAllowedClasses(noticeTitleRawKey(), emptySet())
                .flatMap { it.evaluate(production).failureReport.details }

        report.any { it.contains("bidvector.procurement.CollectionPolicyKt") } shouldBe true
    }

    @Test
    fun `업무구분 세부 분류 원시 키 리터럴은 계약 행을 실은 파일 클래스 밖 production 상수 풀에 없다`() {
        rules
            .classificationKeyLiteralsMustStayInAllowedClasses(
                classificationRawKeys(policy.classificationKeyConcepts),
                policy.classificationKeyAllowedClasses.toSet(),
            ).checkAll()
    }

    @Test
    fun `세부 분류 키 게이트의 허용 클래스는 관측과 같다 — 개념마다 키가 실제로 그 클래스에 있고 낡은 항목이 없다`() {
        val keys = classificationRawKeys(policy.classificationKeyConcepts)

        keys.forEach { key ->
            withClue("키 $key") {
                rules.classesContainingAnyLiteral(production, setOf(key)) shouldBe
                    policy.classificationKeyAllowedClasses.toSet()
            }
        }
    }

    @Test
    fun `허용 클래스가 상수 풀에 가진 운영 계약 키의 개념은 전부 개념 집합에 든다 — 새 계약 행이 게이트 밖에 남지 않는다`() {
        val allowed = policy.classificationKeyAllowedClasses.toSet()
        val conceptsInAllowedClasses =
            operationalFieldContracts()
                .filter { contract ->
                    rules.classesContainingAnyLiteral(production, setOf(contract.rawName.name)).any { it in allowed }
                }.map { it.concept.name }
                .toSet()

        conceptsInAllowedClasses shouldBe policy.classificationKeyConcepts.toSet()
    }

    @Test
    fun `대분류 값을 얻는 자리는 허용 쌍뿐이다 — 타입 멤버 접근·값 획득·클래스 객체 세 축`() {
        divisionRules.typeAccessRules(policy.divisionTypeAccessPairs.toSet()).checkAll()
        divisionRules.acquisitionRules(policy.divisionAcquisitionPairs.toSet()).checkAll()
        divisionRules.classObjectRules(policy.divisionClassObjectReferencers.toSet()).checkAll()
    }

    @Test
    fun `대분류 타입 멤버 접근 허용 쌍은 관측과 같다 — 멤버 목록이 없으므로 새 멤버도 쌍을 바꾼다`() {
        divisionRules.observedTypeAccesses(production) shouldBe policy.divisionTypeAccessPairs.toSet()
    }

    @Test
    fun `대분류 값 획득 허용 쌍은 관측과 같다 — 반환 타입이 대분류인 호출과 대분류 필드 읽기 전수`() {
        divisionRules.observedAcquisitions(production) shouldBe policy.divisionAcquisitionPairs.toSet()
    }

    @Test
    fun `대분류 클래스 객체를 참조하는 production 클래스는 허용 집합과 같다 — Enum valueOf 입구가 비어 있다`() {
        divisionRules.observedClassObjectReferences(production) shouldBe policy.divisionClassObjectReferencers.toSet()
    }

    @Test
    fun `프로세스를 자동 시작하는 러너 타입은 수집 러너만 참조한다`() {
        rules
            .appTypesMustBeReferencedOnlyBy(
                appRoot,
                policy.runnerTypes.toSet(),
                policy.runnerAllowedReferencers.toSet(),
                "D-6F8-3 우회 5 — 러너 타입 참조 집합은 수집 러너 하나다",
            ).checkAll()
    }

    @Test
    fun `서비스 키 원문 설정은 배선 한 곳만 참조한다`() {
        rules
            .appTypesMustBeReferencedOnlyBy(
                appRoot,
                setOf(policy.serviceKeyType),
                policy.serviceKeyReaders.toSet(),
                "D-6F8-4 우회 4 — 서비스 키 원문 설정 참조 집합은 배선 한 곳이다",
            ).checkAll()
    }

    @Test
    fun `로거는 수집 러너의 한 줄 출구를 만드는 배선만 쓴다`() {
        rules
            .appTypesMustBeReferencedOnlyBy(
                appRoot,
                policy.loggingTypes.toSet(),
                policy.loggingAllowedUsers.toSet(),
                "D-6F8-4 우회 4 — 로거 사용 집합은 수집 로그 출구 하나다",
            ).checkAll()
    }

    private fun List<ArchRule>.checkAll() = forEach { rule -> rule.check(production) }

    companion object {
        /** 개념 이름(정책 파일)에서 운영 정책 계약의 원시 키 집합을 읽는다 — 키 문자열은 코드에 박지 않는다. */
        fun classificationRawKeys(conceptNames: List<String>): Set<String> {
            val concepts = conceptNames.map(FieldConcept::valueOf)
            return operationalFieldContracts().filter { it.concept in concepts }.map { it.rawName.name }.toSet()
        }

        fun operationalFieldContracts() =
            (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 24)) as Resolution.Resolved)
                .value
                .fieldContracts.contracts

        /** 공고명 원시 키 — 코드에 박지 않고 운영 정책의 `NOTICE_TITLE` 계약에서 읽는다. */
        fun noticeTitleRawKey(): String {
            val policy = (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 9, 24)) as Resolution.Resolved).value
            return policy.fieldContracts
                .contractsFor(FieldConcept.NOTICE_TITLE)
                .single()
                .rawName.name
        }
    }
}
