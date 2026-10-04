package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * D-6G2g-11 (운영자 결정 B-4 (나)) — **수집 깊이 선택이 코드가 아니라 계약 파일에 있고, 구현이
 * 그 표를 실제로 따른다.**
 *
 * 형제 게이트들이 저마다 「관측 == 등재」를 재지만, 그 관측이 **얼마나 깊게 수집한 것인지**는
 * 어디에도 적혀 있지 않았다. 그래서 전송·반사만 깊고 나머지는 소유 타입만 보는 비대칭이 보이지
 * 않은 채 남았다(`OPEN-6G2B-COLLECTION-DEPTH`).
 *
 * 이 test 가 재는 것은 둘이고, 형제 게이트가 이미 재는 「관측 == 등재」는 **되풀이하지 않는다**.
 *
 *  1. **축 모집단** — [DepthAxis] 와 `collection.depth.*` 키 집합이 양방향으로 같다. 축을 하나
 *     빼먹은 구현이나 쓰이지 않는 키가 조용하지 않다.
 *  2. **민감도 표** — 오늘 production 에서 두 깊이의 관측이 **실제로 갈리는** 축 집합이 아래
 *     상수와 같다. 이것이 「구현만 바꾸고 키는 그대로」를 잡는 자리다: 어떤 축의 구현이 넘겨받은
 *     깊이를 무시하면 그 축의 두 관측이 같아져 민감 집합에서 빠지고, 등식이 깨진다. 반대로
 *     production 이 바뀌어 비민감 축이 민감해지면 그것도 드러난다 — 그때 등재를 다시 재야 한다.
 *
 * 키를 바꾸는 쪽(「`FULL` 로 바꾸고 등재는 그대로」)은 형제 게이트가 잡는다 — 깊이가 바뀌면
 * 관측이 바뀌고 등재와 갈린다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionDepthGateTest {
    private val policy = ArchitecturePolicy.load()
    private val production: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TEST_FIXTURES)
            .importPackages(policy.packageRoot)

    @Test
    fun `깊이 축 모집단은 정책 키 집합과 같다 — 양방향`() {
        val declared = policy.declaredDepthAxes
        val known = DepthAxis.entries.map(DepthAxis::key).toSet()

        (known - declared) shouldBe emptySet()
        (declared - known) shouldBe emptySet()
    }

    @Test
    fun `모든 축의 깊이 값이 닫힌 어휘 안이다`() {
        DepthAxis.entries.forEach { axis ->
            (policy.depth(axis) in ReferenceCollection.entries) shouldBe true
        }
    }

    /**
     * 민감 축에서 두 깊이의 관측이 갈린다 — 구현이 깊이 인자를 무시하면 이 집합에서 빠진다.
     * 비민감 축에서는 같다(그 사실도 등식의 한 변이다 — 갈리기 시작하면 등재를 다시 재야 한다).
     */
    @Test
    fun `두 깊이의 관측이 갈리는 축 집합은 실측한 표와 같다`() {
        val sensitive =
            DepthAxis.entries
                .filter { observe(it, ReferenceCollection.FULL) != observe(it, ReferenceCollection.OWNER_ONLY) }
                .toSet()

        sensitive shouldBe DEPTH_SENSITIVE_AXES
    }

    /**
     * domain 순수성이 `OWNER_ONLY` 인 이유를 값으로 고정한다 — 깊은 수집이 더하는 좌표는 둘뿐이고
     * 둘 다 저자가 쓴 좌표가 아니다: `java.lang.Class` 는 `enum class` 마다 생기는
     * `Enum.valueOf(Class, String)` 과 함수 참조가 남기는 **컴파일러 산출**이고,
     * `java.time.chrono.ChronoLocalDate` 는 `LocalDate` 비교 메서드의 **상위 타입 시그니처**다
     * (허용 패키지는 `java.time`·`java.time.temporal` 이라 `java.time.chrono` 는 밖이다).
     */
    @Test
    fun `domain 순수성에서 깊은 수집이 더하는 좌표는 둘뿐이고 둘 다 저자가 쓴 것이 아니다`() {
        val added =
            observe(DepthAxis.DOMAIN_PURITY, ReferenceCollection.FULL) -
                observe(DepthAxis.DOMAIN_PURITY, ReferenceCollection.OWNER_ONLY)

        added.map { it.substringAfter("->") }.toSet() shouldBe
            setOf("java.lang.Class", "java.time.chrono.ChronoLocalDate")
    }

    /** 축마다의 관측 — 두 깊이에서 각각 부르려고 깊이를 인자로 받는다. */
    private fun observe(
        axis: DepthAxis,
        depth: ReferenceCollection,
    ): Set<String> {
        val collectionRules = CollectionArchitectureRules(depth)
        val procurement = "${policy.packageRoot}.procurement"
        return when (axis) {
            DepthAxis.TRANSPORT -> {
                transportPairs(depth)
            }

            DepthAxis.REFLECTION -> {
                collectionRules
                    .observedReflectionTypePairs(production, policy.reflectionRoots, policy.reflectionPackages.toSet())
                    .map { "${it.first}->${it.second}" }
                    .toSet()
            }

            DepthAxis.COLLECTION_PROCUREMENT -> {
                collectionRules.observedProcurementTypes(production, policy.collectionPackage, procurement, depth)
            }

            DepthAxis.USECASE -> {
                referencers(listOf(policy.packageRoot), setOf(policy.collectionUseCaseType), depth)
            }

            DepthAxis.KEY_HASH -> {
                referencers(policy.keyHashRoots, setOf(policy.keyHashType), depth)
            }

            DepthAxis.RAW_ACCESS -> {
                referencers(policy.rawAccessRoots, policy.rawAccessTypes.toSet(), depth)
            }

            DepthAxis.DOMAIN_PURITY -> {
                domainExternalReferences(depth)
            }

            DepthAxis.RUNNER -> {
                referencers(listOf(appRoot()), policy.runnerTypes.toSet(), depth)
            }

            DepthAxis.SERVICE_KEY -> {
                referencers(listOf(appRoot()), setOf(policy.serviceKeyType), depth)
            }

            DepthAxis.LOGGING -> {
                referencers(listOf(appRoot()), policy.loggingTypes.toSet(), depth)
            }

            DepthAxis.OPERATOR_CREDENTIAL -> {
                referencers(listOf(appRoot()), policy.operatorCredentialTypes.toSet(), depth)
            }
        }
    }

    private fun appRoot(): String = "${policy.packageRoot}.app"

    private fun transportPairs(depth: ReferenceCollection): Set<String> =
        TransportSurfaceRules(
            packageRoot = policy.packageRoot,
            surfacePackages = policy.transportSurfacePackages.toSet(),
            surfaceTypes = policy.transportSurfaceTypes.toSet(),
            collection = depth,
        ).observedPairs(production, policy.transportRoots)
            .map { "${it.first}->${it.second}" }
            .toSet()

    private fun referencers(
        roots: List<String>,
        types: Set<String>,
        depth: ReferenceCollection,
    ): Set<String> =
        CollectionArchitectureRules(depth)
            .observedReferencers(production, roots, types, depth)

    /**
     * domain 모듈이 허용 목록(T-A 하위 · T-B 정확 패키지 · T-C 클래스) 밖 좌표를 참조하는 자리.
     * 원시 타입은 패키지가 없어 점이 없는 이름으로 들어온다 — 클래스가 아니므로 뺀다.
     */
    private fun domainExternalReferences(depth: ReferenceCollection): Set<String> {
        val allowedClasses = (policy.allowedApiClasses + policy.allowedRuntimeClasses).toSet()
        val exactPackages = policy.allowedExactPackages.toSet()
        val byClassPackages = policy.byClassPackages.toSet()
        val domainRoots = policy.domainModules.map { "${policy.packageRoot}.$it" }

        return production
            .filter { item -> domainRoots.any { item.packageName == it || item.packageName.startsWith("$it.") } }
            .flatMap { origin -> origin.referencedTypeNames(depth).map { origin.outermostClassName() to it } }
            .filter { (_, type) -> type.contains('.') }
            .filterNot { (_, type) -> policy.allowedSubtrees.any { type == it || type.startsWith("$it.") } }
            .filterNot { (_, type) -> type.substringBeforeLast('.') in exactPackages }
            .filterNot { (_, type) -> type.substringBeforeLast('.') in byClassPackages && type in allowedClasses }
            .map { (holder, type) -> "$holder->$type" }
            .toSet()
    }

    private companion object {
        /**
         * 오늘 production 에서 두 깊이의 관측이 갈리는 축 — 착수 실측(`00_kickoff_measurement.md` D-1)의
         * 수치가 근거다: 전송 쌍 +14 · `collection-procurement` +2 · `raw-access` +2 ·
         * domain 순수성 +28. 나머지 일곱은 +0 이다.
         */
        val DEPTH_SENSITIVE_AXES =
            setOf(
                DepthAxis.TRANSPORT,
                DepthAxis.COLLECTION_PROCUREMENT,
                DepthAxis.RAW_ACCESS,
                DepthAxis.DOMAIN_PURITY,
            )
    }
}
