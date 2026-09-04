package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.io.File
import kotlin.test.assertTrue

/**
 * 금지 목록의 **출처 대조**. 기대 집합의 출처는 정책 파일이 아니라 **승인 문서**다 — 정책을
 * 복사해 오면 두 목록이 서로를 확인해 주는 척만 하고 아무것도 잡지 못한다.
 *
 * 이 테스트가 있는 이유: 같은 결함이 두 번 재발했다. 좌표를 하나씩 더하는 방식은
 * `java.net.http` 옆의 `java.net.HttpURLConnection` 을, `com.fasterxml.jackson` 옆의
 * `tools.jackson` 을, `java.nio.file` 옆의 `java.nio.channels` 를 차례로 놓쳤다.
 * **가족을 문서에서 뽑고 좌표를 뿌리 패키지로 적으면** 형제 잎이 남지 않는다.
 */
class ForbiddenFamilyCoverageTest {
    private val policy = ModuleDependencyPolicy.load(File(REAL_POLICY))

    /** 소스 층의 **완전 술어**(설계 검토 §1) — 패키지 축과 클래스 축을 이 하나가 판정한다. */
    private val sourcePolicy = SourceReferencePolicy.load(File(REAL_POLICY))

    /**
     * **방향이 뒤집혔다.** 예전에는 「가족이 금지 목록에 있는가」를 봤는데, 그 물음은 목록에 없는
     * 좌표(`kotlin.io`)를 정의상 잡지 못했다. 이제 domain 게이트가 allow-list 라 물음도 바뀐다 —
     * **승인 문서가 금지한 가족이 허용 목록으로 도로 들어오지 않는가.**
     *
     * 패키지 축(`family.packages`)은 [SourceReferencePolicy.admitsWildcard] 로, 클래스 축
     * (`family.classes`)은 [SourceReferencePolicy.admits] 로 잰다 — 이 테스트가 예전에 갖고
     * 있던 부분 술어(패키지 축만 보는 로컬 구현)를 걷어내고 production 이 쓰는 완전 술어를
     * 그대로 쓴다(v2-지침서.md §5 「중복 금지」). 두 축을 하나의 술어로 합치지 않는 이유는
     * `SourceReferencePolicyTest` 가 갖는다 — 패키지 문자열을 클래스 FQN 으로 재면 `kotlin`
     * 처럼 bare 로 열린 exact 패키지가 `kotlin.io`·`kotlin.concurrent` 를 오판정한다.
     */
    @Test
    fun `승인 문서가 금지한 가족이 허용 목록에 들어오지 않는다`() {
        assertAll(
            FAMILIES.flatMap { family ->
                family.packages.map { pkg ->
                    {
                        assertTrue(!sourcePolicy.admitsWildcard(pkg), "${family.name}(${family.source}) 의 패키지가 허용된다: $pkg")
                    }
                } +
                    family.classes.map { fqn ->
                        {
                            assertTrue(!sourcePolicy.admits(fqn), "${family.name}(${family.source}) 의 클래스가 허용된다: $fqn")
                        }
                    }
            },
        )
    }

    @Test
    fun `Maven 좌표가 있는 가족은 group 좌표계에도 전부 있다`() {
        assertAll(
            FAMILIES.filter { it.groups.isNotEmpty() }.map { family ->
                {
                    val missing = family.groups.filterNot { it in policy.forbiddenGroups }
                    assertTrue(missing.isEmpty(), "${family.name}(${family.source}) 의 누락: $missing")
                }
            },
        )
    }

    /**
     * **방향이 다시 뒤집혔다.** `class.forbidden` 이 사라지고 T-C(클래스 단위 허용)가 그 자리를
     * 대신하므로, 이제 물음은 「금지가 허용 안에 있는가」가 아니라 **「허용이 제자리에 있는가」**다.
     */
    @Test
    fun `클래스 허용은 클래스 단위 패키지 안에 있다`() {
        val orphan =
            policy.allowedClasses.filterNot { fqcn ->
                fqcn.substringBeforeLast('.').substringBefore('$') in policy.byClassPackages
            }
        assertTrue(orphan.isEmpty(), "클래스 단위 패키지 밖의 클래스를 허용했다: $orphan")
    }

    /**
     * **두 정책이 서로를 부정하지 않는다.** T-C 가 연 클래스를 효과 표면에 올리면 같은 좌표를
     * 한쪽은 허용하고 한쪽은 금지 표면으로 삼는다 — 어느 게이트도 그 모순을 보지 못하므로
     * 여기서 잰다. (T-D 의 owner 검사는 이 자리를 떠났다: 이제 판정이 **선언 클래스**라
     * `Throwable#printStackTrace` 처럼 owner 가 허용 목록 밖일 수 있다.)
     */
    @Test
    fun `효과 표면과 클래스 허용이 겹치지 않는다`() {
        val both = policy.effectSurfaceClasses.filter { it in policy.allowedClasses }
        assertTrue(both.isEmpty(), "같은 클래스를 허용하면서 효과 표면으로도 뒀다: $both")
    }

    /** 같은 모순의 패키지 판. `java.io` 가 정확 패키지로 열리면 표면 줄이 죽는다. */
    @Test
    fun `효과 표면 패키지가 정확 패키지로 열려 있지 않다`() {
        val both =
            policy.effectSurfacePackages.filter { surface ->
                policy.allowedExactPackages.any { it == surface || it.startsWith("$surface.") }
            }
        assertTrue(both.isEmpty(), "효과 표면 아래가 정확 패키지로 열려 있다: $both")
    }

    /**
     * **클래스 단위 패키지는 그 자체로 허용이 아니다.** `java.util` 이 `package.allowed.exact` 에
     * 함께 들어가면 T-C 가 통째로 무력해진다 — 이 slice 가 실제로 그 상태였다.
     */
    @Test
    fun `클래스 단위 패키지가 정확 패키지로도 열려 있지 않다`() {
        val both = policy.byClassPackages.filter { it in policy.allowedExactPackages }
        assertTrue(both.isEmpty(), "클래스 단위 패키지가 통째로도 열려 있다: $both")
    }

    private class Family(
        val name: String,
        val source: String,
        val packages: List<String> = emptyList(),
        val groups: List<String> = emptyList(),
        /** T-C(클래스 단위) 축 좌표 — 패키지는 열려도 이 클래스만은 닫혀 있어야 한다. */
        val classes: List<String> = emptyList(),
    )

    private companion object {
        val REAL_POLICY: String =
            System.getProperty("bidvector.architecture.policy")
                ?: error("시스템 속성 'bidvector.architecture.policy' 가 없다 — 빌드가 넘긴다")

        /**
         * 승인 문서를 **줄 번호가 아니라 인용문·절 제목으로** 가리킨다. 이 slice 가
         * `milestone-1.md` 를 편집했고 그 아래 줄 번호가 전부 밀렸다 — 인용문은 밀리지 않는다.
         */
        const val IO_RULE = "milestone-1.md 「구현 규칙」 — \"domain은 I/O가 없는 입력→출력 함수/객체다\""

        /** 각 행의 `source` 가 그 가족을 요구하는 승인 문서의 문면이다. */
        val FAMILIES =
            listOf(
                Family(
                    "Spring",
                    "지침서 §3.1 · ADR 0006 D-3",
                    listOf("org.springframework"),
                    listOf("org.springframework"),
                ),
                Family(
                    "JPA",
                    "지침서 §3.1 · ADR 0006 D-3",
                    listOf("jakarta.persistence", "javax.persistence", "org.hibernate"),
                    listOf("jakarta.persistence", "org.hibernate"),
                ),
                Family(
                    "JSON",
                    "지침서 §3.1 · ADR 0006 D-3",
                    listOf("com.fasterxml.jackson", "tools.jackson", "kotlinx.serialization"),
                    listOf("com.fasterxml.jackson", "tools.jackson"),
                ),
                Family(
                    "HTTP",
                    "지침서 §3.1 · ADR 0006 D-3",
                    packages = listOf("java.net", "okhttp3", "io.ktor"),
                    groups = listOf("com.squareup.okhttp3", "io.ktor"),
                    classes = listOf("java.net.HttpURLConnection"),
                ),
                Family(
                    "broker",
                    "지침서 §3.1",
                    listOf("jakarta.jms", "org.apache.kafka", "com.rabbitmq"),
                    listOf("jakarta.jms", "org.apache.kafka", "com.rabbitmq"),
                ),
                Family("gRPC", "ADR 0006 D-3 · D-6", listOf("io.grpc"), listOf("io.grpc")),
                Family("Protobuf", "ADR 0006 D-6", listOf("com.google.protobuf"), listOf("com.google.protobuf")),
                Family(
                    "SQL",
                    "지침서 §3.1 · $IO_RULE",
                    listOf("java.sql", "org.postgresql"),
                    listOf("org.postgresql"),
                ),
                Family("I/O (JDK)", IO_RULE, listOf("java.io", "java.nio")),
                // 승인 문서는 **I/O 를 금지**하지 그 언어의 좌표를 열거하지
                // 않는다 — 같은 문면이 Kotlin 표준 I/O 도 덮는다. deny-list 시절에는 이 줄을 쓸
                // 근거가 없어 보였고 그래서 `readln()` 이 새어 나갔다.
                Family("I/O (Kotlin stdlib)", IO_RULE, listOf("kotlin.io")),
                Family("동시성", IO_RULE, listOf("java.util.concurrent", "kotlin.concurrent")),
                // 패키지 자체(java.lang·java.util)는 T-C 로 열려 있다 — 문제는 그 안의 개별
                // 클래스다. `architecture-policy.properties` 의 「일부러 넣지 않은 것」 주석이
                // 이 좌표들을 든다.
                Family(
                    "환경·리플렉션 (JDK)",
                    "architecture-policy.properties 「일부러 넣지 않은 것」 · v2-지침서.md §3.1",
                    classes = listOf("java.lang.Class", "java.util.Locale"),
                ),
            )
    }
}
