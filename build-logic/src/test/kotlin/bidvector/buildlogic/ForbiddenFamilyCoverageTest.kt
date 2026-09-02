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

    @Test
    fun `승인 문서가 든 가족이 package 좌표계에 전부 있다`() {
        assertAll(
            FAMILIES.map { family ->
                {
                    val missing = family.packages.filterNot { it in policy.forbiddenPackages }
                    assertTrue(missing.isEmpty(), "${family.name}(${family.source}) 의 누락: $missing")
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
     * 뿌리 패키지로 적었는지 본다. `a.b.c` 를 적었는데 `a.b` 도 목록에 있으면 잎을 적은 것이고,
     * 그 형제는 여전히 새어 나간다 — 이 slice 가 두 번 겪은 그 형태다.
     */
    @Test
    fun `잎 좌표로 적힌 것이 없다`() {
        val redundant =
            policy.forbiddenPackages.filter { candidate ->
                policy.forbiddenPackages.any { other -> other != candidate && candidate.startsWith("$other.") }
            }
        assertTrue(redundant.isEmpty(), "뿌리가 이미 있는데 잎을 또 적었다: $redundant")
    }

    private class Family(
        val name: String,
        val source: String,
        val packages: List<String>,
        val groups: List<String> = emptyList(),
    )

    private companion object {
        val REAL_POLICY: String =
            System.getProperty("bidvector.architecture.policy")
                ?: error("시스템 속성 'bidvector.architecture.policy' 가 없다 — 빌드가 넘긴다")

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
                    listOf("java.net", "okhttp3", "io.ktor"),
                    listOf("com.squareup.okhttp3", "io.ktor"),
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
                    "지침서 §3.1 · milestone-1.md:62",
                    listOf("java.sql", "org.postgresql"),
                    listOf("org.postgresql"),
                ),
                Family("I/O", "milestone-1.md:62", listOf("java.io", "java.nio")),
            )
    }
}
