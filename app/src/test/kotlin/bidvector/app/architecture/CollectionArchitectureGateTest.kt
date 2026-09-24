package bidvector.app.architecture

import bidvector.procurement.FieldConcept
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.sharedkernel.Resolution
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
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
