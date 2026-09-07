package bidvector.buildlogic

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContractPolicyTest {
    private fun policyFile(contents: String): File {
        val path = createTempFile(prefix = "contract-policy", suffix = ".properties")
        path.writeText(contents, Charsets.UTF_8)
        return path.toFile()
    }

    private val validProperties =
        """
        approved.tag.pattern=^contracts/v[0-9]+-approved-[0-9]{4}-[0-9]{2}-[0-9]{2}$
        approved.tag=contracts/v1-approved-2026-09-07
        max.message.bytes=262144
        breaking.mutations=field-delete,rpc-delete
        breaking.mutations.min=2
        tool.buf.version=1.72.0
        tool.protoc=3.25.9
        tool.protoc.gen.grpc.kotlin=1.5.0
        tool.protoc.gen.grpc.java=1.84.0
        """.trimIndent()

    @Test
    fun `모든 키가 있으면 값을 읽는다`() {
        val policy = ContractPolicy.load(policyFile(validProperties))
        assertEquals("contracts/v1-approved-2026-09-07", policy.approvedTag)
        assertEquals(262144, policy.maxMessageBytes)
        assertEquals(listOf("field-delete", "rpc-delete"), policy.breakingMutations)
        assertEquals(2, policy.breakingMutationsMin)
        assertEquals("1.72.0", policy.bufVersion)
        assertEquals("3.25.9", policy.protocVersion)
        assertEquals("1.5.0", policy.protocGenGrpcKotlinVersion)
        assertEquals("1.84.0", policy.protocGenGrpcJavaVersion)
    }

    @Test
    fun `키가 없으면 정책 오류다`() {
        val policy = ContractPolicy.load(policyFile("approved.tag=x"))
        assertFailsWith<IllegalStateException> { policy.maxMessageBytes }
    }

    @Test
    fun `승인 태그가 정규식을 만족하면 위반이 없다`() {
        val policy = ContractPolicy.load(policyFile(validProperties))
        assertNull(approvedTagViolation(policy.approvedTag, policy.approvedTagPattern))
    }

    @Test
    fun `승인 태그가 이름 규칙을 어기면 위반이다`() {
        val policy = ContractPolicy.load(policyFile(validProperties))
        assertNotNull(approvedTagViolation("main", policy.approvedTagPattern))
        assertNotNull(approvedTagViolation("contracts/v1-approved-2026-9-7", policy.approvedTagPattern))
    }
}
