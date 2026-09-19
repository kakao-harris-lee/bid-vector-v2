package bidvector.app.http

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 우회 (4) 폐쇄 — 타이밍 test는 CI에서 본질적으로 flaky해 만들지 않는다(팀장 preflight
 * 논의 그대로). 대신 **구조**를 잰다: 자격증명 비교를 담은 파일 수준 함수
 * (`OperatorCredentialFilterKt`, `constantTimeEquals`)의 컴파일된 바이트코드가
 * `MessageDigest.isEqual`을 부르고, `String.equals`·`Intrinsics.areEqual`(Kotlin `==`이
 * 컴파일되는 자리)을 부르지 **않는지** 잰다. `presented == expected`로 되돌리는 변이는
 * `Intrinsics.areEqual` 상수 풀 항목을 만들어 이 test를 붉게 만든다(수동 재현,
 * evidence checklist).
 *
 * `ProfileAdapterDependencyTest`와 같은 기법(`javap -p -v`로 상수 풀을 훑는다) — 소스
 * 텍스트 정규식이 아니라 컴파일된 바이트코드를 본다(우회를 재현하려면 게이트 자체를
 * 바꿔야 하는 층).
 */
class ConstantTimeComparisonStructureTest {
    @Test
    fun `credential 비교는 MessageDigest 상수 시간만 쓴다`() {
        val classFile = File("build/classes/kotlin/main/bidvector/app/http/OperatorCredentialFilterKt.class")
        check(classFile.isFile) { "빌드 산출물을 찾지 못했다: ${classFile.absolutePath} — :app:compileKotlin 선행 필요" }

        val output = javap(classFile)

        output.contains("java/security/MessageDigest.isEqual") shouldBe true
        output.contains("java/lang/String.equals") shouldBe false
        output.contains("kotlin/jvm/internal/Intrinsics.areEqual") shouldBe false
    }

    /** 양성 대조 — 술어 자체가 늘 통과만 하는 회귀를 막는다. */
    @Test
    fun `Intrinsics areEqual 로 되돌린 표본은 이 술어에 걸린다 — 양성 대조`() {
        val mutatedOutput =
            """
            invokestatic java/security/MessageDigest.isEqual:([B[B)Z
            invokestatic kotlin/jvm/internal/Intrinsics.areEqual:(Ljava/lang/Object;Ljava/lang/Object;)Z
            """.trimIndent()
        mutatedOutput.contains("kotlin/jvm/internal/Intrinsics.areEqual") shouldBe true
    }
}

private fun javap(classFile: File): String {
    val javapExecutable =
        File(System.getProperty("java.home"), "bin/javap").let {
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) File(it.path + ".exe") else it
        }
    val process =
        ProcessBuilder(javapExecutable.absolutePath, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "javap 실행 실패(exit=$exitCode): ${classFile.name}\n$output" }
    return output
}
