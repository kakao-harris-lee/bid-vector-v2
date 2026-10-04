package bidvector.buildlogic

/**
 * 한 컴파일된 test 클래스에서 등식이 쓰는 **사실만** 뽑은 것. 바이트코드 판독(`TestClassCensus`)과
 * 등식 계산(`GateRegistration`)을 가르는 자리다 — 등식은 class 파일 없이 값으로 잰다.
 *
 * [binaryName] 은 `$` 를 품은 중첩 이름일 수 있다. 접기는 등식 쪽이 한다.
 */
internal data class TestClassFacts(
    val binaryName: String,
    val methodAnnotations: Set<String>,
    val classAnnotations: Set<String>,
)

/**
 * 「JUnit 이 이 클래스를 발견하는가」를 정하는 어휘 — 값은 계약 파일(`gate-tests.properties`)이 든다.
 *
 * [annotations] 는 JUnit 자신의 발견 규칙 셋(`@Test`·`@TestFactory`·`@TestTemplate`)이고, 그 메타
 * 애노테이션을 통한 파생(`@ParameterizedTest`·`@RepeatedTest`)은 **열거하지 않는다** — 클래스패스에서
 * 메타를 따라가 푼다. 저자가 만든 애노테이션도 같은 메타를 달면 함께 잡힌다.
 *
 * [conditionPackages] 는 **실행 조건** 애노테이션의 패키지 뿌리다(이름 열거가 아니다). 그 패키지의
 * 애노테이션이 클래스에 붙으면 그 클래스는 보통의 `check` 에서 돌지 않으므로 제외 쪽으로 간다.
 * `@Disabled`(`org.junit.jupiter.api`)는 **일부러 이 뿌리 밖**이다 — 제외로 치면 게이트 test 에 그 한 줄을
 * 붙이는 것이 등재에서 빼는 길이 되고, `gateExecutionGate` 의 「건너뛰었다」 판정도 함께 사라진다.
 */
internal data class TestDiscoveryVocabulary(
    val annotations: Set<String>,
    val conditionPackages: Set<String>,
)

/** 한 모듈의 관측 — [population] 은 접은 최상위 이름, [excluded] 는 build 사실이 낸 제외다. */
internal data class GateRegistrationCensus(
    val population: Set<String>,
    val excluded: Set<String>,
)

/**
 * **등재 장부와 소스의 양방향 등식** — `모집단 ∖ 제외 == 등재`(`OPEN-6G-GATE-REGISTRY-KONEPS`,
 * `OPEN-6G2G-REGISTRATION-PACKAGE-COVER`).
 *
 * 모집단은 **컴파일된 클래스**다. 소스 파일 이름으로 찾으면 한 파일에 test 클래스가 둘일 때 둘째가
 * 보이지 않고(저장소 실례 아홉), 패키지마다 손으로 건 술어는 덮지 않은 패키지를 남긴다(adapters 열둘 중 여섯).
 *
 * 제외는 **build 사실에서만** 나온다(운영자 결정 B-3 (나)) — Gradle `Test.filter` 의 제외 패턴과 클래스에
 * 붙은 실행 조건 애노테이션. 손으로 적은 이름은 아무것도 제외하지 못한다. 그 선언([declaredExcluded])은
 * 제외의 **출처가 아니라 래칫**이다: build 사실이 제외를 늘리면 선언과 어긋나 붉어지므로, 게이트 test 에
 * 조건 애노테이션을 붙여 조용히 등재 밖으로 빼는 길이 닫힌다.
 */
internal object GateRegistration {
    fun census(
        classes: List<TestClassFacts>,
        vocabulary: TestDiscoveryVocabulary,
        excludePatterns: Set<String>,
        metaAnnotations: (String) -> Set<String>,
    ): GateRegistrationCensus {
        val isDiscovery = discoveryPredicate(vocabulary.annotations, metaAnnotations)
        val population =
            classes
                .filter { facts -> facts.methodAnnotations.any(isDiscovery) }
                .map { it.binaryName.outermost() }
                .toSet()
        val conditional =
            classes
                .filter { facts -> facts.classAnnotations.any { it.isInAnyPackage(vocabulary.conditionPackages) } }
                .map { it.binaryName.outermost() }
                .toSet()
        val filtered = population.filter { name -> excludePatterns.any { name.matchesTestFilter(it) } }

        return GateRegistrationCensus(population, (conditional + filtered) intersect population)
    }

    fun violations(
        census: GateRegistrationCensus,
        registered: Set<String>,
        declaredExcluded: Set<String>,
    ): List<String> {
        val required = census.population - census.excluded
        return listOf(
            (required - registered) to "게이트 등재 장부에 없는 test 클래스다 — 등재하지 않으면 지워져도 `check` 가 초록이다",
            (registered - required) to "등재돼 있지만 소스에 없다 — 장부가 유령 이름을 산 것처럼 센다",
            (census.excluded - declaredExcluded) to "선언되지 않은 제외다 — build 사실이 이 클래스를 `check` 밖으로 뺐다",
            (declaredExcluded - census.excluded) to "선언만 있는 제외다 — 이 이름을 `check` 밖으로 빼는 build 사실이 없다",
        ).flatMap { (names, reason) -> names.sorted().map { "$reason: $it" } }
    }

    /** 메타 애노테이션을 따라가 발견 어휘에 닿는지 — 순환은 방문 집합이 끊는다. */
    private fun discoveryPredicate(
        annotations: Set<String>,
        metaAnnotations: (String) -> Set<String>,
    ): (String) -> Boolean {
        val resolved = mutableMapOf<String, Boolean>()

        fun reaches(
            name: String,
            seen: MutableSet<String>,
        ): Boolean =
            resolved.getOrPut(name) {
                name in annotations || (seen.add(name) && metaAnnotations(name).any { reaches(it, seen) })
            }

        return { name -> reaches(name, mutableSetOf()) }
    }

    /** 중첩 클래스를 바깥으로 접는다 — 등재는 최상위 이름이고, 중첩 test 는 바깥 suite 로 돈다. */
    private fun String.outermost(): String = substringBefore('$')

    private fun String.isInAnyPackage(roots: Set<String>): Boolean = roots.any { startsWith("$it.") }

    /**
     * Gradle `Test.filter` 의 제외 패턴 판정 — `*` 하나만 쓰는 와일드카드이고, 정규식 메타는 막는다.
     * 패턴은 FQCN 과 단순 이름 둘 다에 맞춰 본다(`*CrossLangSmokeTest` 관례).
     */
    private fun String.matchesTestFilter(pattern: String): Boolean {
        val regex = Regex(pattern.split('*').joinToString(".*") { Regex.escape(it) })
        return regex.matches(this) || regex.matches(substringAfterLast('.'))
    }
}
