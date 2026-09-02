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

    /**
     * **방향이 뒤집혔다.** 예전에는 「가족이 금지 목록에 있는가」를 봤는데, 그 물음은 목록에 없는
     * 좌표(`kotlin.io`)를 정의상 잡지 못했다. 이제 domain 게이트가 allow-list 라 물음도 바뀐다 —
     * **승인 문서가 금지한 가족이 허용 목록으로 도로 들어오지 않는가.**
     */
    @Test
    fun `승인 문서가 금지한 가족이 허용 목록에 들어오지 않는다`() {
        assertAll(
            FAMILIES.map { family ->
                {
                    val admitted = family.packages.filter(::isAllowed)
                    assertTrue(admitted.isEmpty(), "${family.name}(${family.source}) 가 허용된다: $admitted")
                }
            },
        )
    }

    /** 정확 패키지 허용 + `bidvector` 만 하위까지. 게이트의 판정과 같은 모양이다. */
    private fun isAllowed(candidate: String): Boolean =
        candidate in policy.allowedExactPackages ||
            policy.allowedSubtrees.any { candidate == it || candidate.startsWith("$it.") }

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
     * 클래스 단위 금지는 **허용된 정확 패키지 안**에 있어야 뜻이 선다. 허용된 적 없는 패키지의
     * 클래스를 적는 것은 아무것도 막지 않으면서 막는 것처럼 보인다. 이 잔여 deny 가 「무한 열거」가
     * 아닌 근거이기도 하다 — 대상이 유한한 JDK 패키지 안으로 한정된다.
     */
    @Test
    fun `클래스 금지는 허용된 정확 패키지 안에 있다`() {
        val orphan =
            policy.forbiddenClasses.filterNot { fqcn ->
                fqcn.substringBeforeLast('.') in policy.allowedExactPackages
            }
        assertTrue(orphan.isEmpty(), "허용된 적 없는 패키지의 클래스를 금지했다: $orphan")
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
                    "지침서 §3.1 · $IO_RULE",
                    listOf("java.sql", "org.postgresql"),
                    listOf("org.postgresql"),
                ),
                Family("I/O (JDK)", IO_RULE, listOf("java.io", "java.nio")),
                // Codex 3차 #2 의 실물. 승인 문서는 **I/O 를 금지**하지 그 언어의 좌표를 열거하지
                // 않는다 — 같은 문면이 Kotlin 표준 I/O 도 덮는다. deny-list 시절에는 이 줄을 쓸
                // 근거가 없어 보였고 그래서 `readln()` 이 새어 나갔다.
                Family("I/O (Kotlin stdlib)", IO_RULE, listOf("kotlin.io")),
                Family("동시성", IO_RULE, listOf("java.util.concurrent", "kotlin.concurrent")),
            )
    }
}
