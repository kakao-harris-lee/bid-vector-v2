package bidvector.workflow.profile

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/** 이 패키지의 바이트코드 내부 이름 — 상수 풀의 클래스 참조가 이 접두로 나타난다. */
private const val PROFILE_INTERNAL_PREFIX = "bidvector/workflow/profile/"

private val workflowClasses = File("build/classes/kotlin/main/bidvector/workflow")

/**
 * **「아직 판정하지 않음」 표시 잠금**(M7 완료 조건 G2 — QUAL-08 · QUAL-11, scope 산출물 4).
 *
 * 두 겹이다. (1) 판정 대기 표에 두 축이 있다. (2) `workflow` 의 **이 패키지 밖** 어느 컴파일된
 * 클래스도 이 패키지를 참조하지 않는다 — 판정 경로(`workflow.evaluation` 등)가 소재지나 시평액을
 * 읽기 시작하면 (2) 가 붉어지고, 그 slice 는 (1) 의 표에서 그 축을 빼야 한다. domain 모듈
 * (`qualification` 등)은 층 게이트가 이미 `workflow` 참조를 막으므로 여기서 다시 재지 않는다.
 *
 * 소스 텍스트가 아니라 **상수 풀**을 본다 — 전체 한정 이름으로 import 없이 참조하는 우회를
 * 텍스트 정규식은 못 본다(`ProfileAdapterDependencyTest` 와 같은 근거). 클래스 파일의 상수 풀
 * UTF-8 항목에 내부 이름이 그대로 실리므로 바이트 검색으로 충분하다(ASCII 이름).
 */
class ProfileJudgementIsolationTest {
    @Test
    fun `판정 대기 축은 QUAL-08 과 QUAL-11 둘이다`() {
        UnjudgedQualificationAxis.entries.map { it.capability } shouldBe listOf("QUAL-08", "QUAL-11")
    }

    @Test
    fun `workflow 의 profile 밖 클래스는 profile 패키지를 참조하지 않는다`() {
        check(workflowClasses.isDirectory) {
            "빌드 산출물을 찾지 못했다: ${workflowClasses.absolutePath} — :workflow:compileKotlin 선행 필요"
        }
        val outside =
            workflowClasses
                .walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .filterNot { it.parentFile.name == "profile" && it.parentFile.parentFile == workflowClasses }
                .toList()
        outside.shouldNotBeEmpty() // 빈 모집단을 「위반 없음」으로 오판하지 않는다.

        outside.filter(::referencesProfile).map { it.relativeTo(workflowClasses).path } shouldBe emptyList()
    }

    /** 양성 대조 — 술어가 늘 거짓만 내는 회귀를 막는다(이 패키지 자신의 클래스는 참조를 가진다). */
    @Test
    fun `이 패키지 자신의 클래스에서는 술어가 참이다 — 양성 대조`() {
        val own = File(workflowClasses, "profile").listFiles { file -> file.extension == "class" }.orEmpty().toList()
        own.shouldNotBeEmpty()
        own.any(::referencesProfile) shouldBe true
    }
}

private fun referencesProfile(classFile: File): Boolean =
    String(classFile.readBytes(), Charsets.ISO_8859_1).contains(PROFILE_INTERNAL_PREFIX)
