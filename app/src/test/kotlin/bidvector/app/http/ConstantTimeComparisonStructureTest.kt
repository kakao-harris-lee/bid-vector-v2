package bidvector.app.http

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 우회 (4) 폐쇄 — 타이밍 test는 CI에서 본질적으로 flaky해 만들지 않는다(팀장 preflight
 * 논의 그대로). 대신 **구조**를 잰다: 자격증명 비교를 담은 `OperatorCredentialFilter`의
 * 컴파일된 바이트코드가 `MessageDigest.isEqual`을 부르고, `String.equals`·
 * `Intrinsics.areEqual`(Kotlin `==`이 컴파일되는 자리)을 부르지 **않는지** 잰다.
 *
 * **D-6A1-29 시정 — 클래스 하나(`OperatorCredentialFilterKt`, 파일 수준 함수의 파사드)만
 * 보던 것을 넓힌다.** verifier 실측: `doFilter` 조건에 단락 비교(`presented != expected
 * || …`)를 직접 넣는 변이(비교를 `constantTimeEquals` 위임 밖으로 인라인)는
 * `OperatorCredentialFilter.class`(필터 클래스 자신)에 위반을 남기는데, 이전 판은 그
 * class 파일을 보지 않아 놓쳤다 — 형제 게이트(`AuditAdapterDependencyTest`)가 이미
 * `walkTopDown()`으로 패키지 디렉터리를 훑는 것과 같은 형태로 고친다.
 *
 * **패키지 전체가 아니라 `OperatorCredentialFilter` 소유 class 파일로 범위를 좁힌다** —
 * 이 패키지의 다른 파일(`ErrorBody`·`ApiAuditRecord`·`StrategyReadResponse` 등)은 평범한
 * Kotlin data class이고, 그 자동 생성 `equals()`가 **자기 필드 비교에** `Intrinsics
 * .areEqual`을 정당하게 쓴다(실측: `ErrorBody.class`만 해도 4건). 패키지 전체에 대해
 * 무조건 0건을 요구하면 이 slice의 다른 정상 코드에서 즉시 거짓 RED가 난다 — 그래서
 * `walkTopDown()`으로 **발견**은 패키지 전체를 훑되(하드코딩된 단일 파일 경로에 갇히지
 * 않는다), **판정 대상은 `OperatorCredentialFilter`가 이름에 들어간 class 파일**(그
 * 클래스 자신·컴패니언·파일 파사드)로 좁힌다 — 신용카드 비교가 실제로 사는 자리는 이
 * 이름 축 안에서만 움직이기 때문이다.
 */
class ConstantTimeComparisonStructureTest {
    @Test
    fun `OperatorCredentialFilter 소유 클래스는 MessageDigest 상수 시간만 쓴다`() {
        val packageDir = File("build/classes/kotlin/main/bidvector/app/http")
        check(packageDir.isDirectory) {
            "빌드 산출물을 찾지 못했다: ${packageDir.absolutePath} — :app:compileKotlin 선행 필요"
        }

        val relatedClassFiles =
            packageDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .filter { it.nameWithoutExtension.startsWith("OperatorCredentialFilter") }
                .toList()
        relatedClassFiles.shouldNotBeEmpty() // OperatorCredentialFilter.class·…Kt.class 최소 둘

        val outputs = relatedClassFiles.associateWith(::javap)

        outputs.values.any { it.contains("java/security/MessageDigest.isEqual") } shouldBe true
        outputs.filterValues { it.contains("java/lang/String.equals") }.keys shouldBe emptySet<File>()
        outputs.filterValues { it.contains("kotlin/jvm/internal/Intrinsics.areEqual") }.keys shouldBe emptySet<File>()
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

    /**
     * 양성 대조 — 패키지 전체가 아니라 `OperatorCredentialFilter` 소유 파일로 범위를 좁힌
     * 것이 다른 파일의 정당한 `Intrinsics.areEqual`(data class 자동 `equals()`)을 오탐하지
     * 않음을 실측으로 고정한다.
     */
    @Test
    fun `허용 밖 파일 이름은 판정 대상에서 제외된다 — 양성 대조`() {
        val nameFilter = { name: String -> name.startsWith("OperatorCredentialFilter") }
        nameFilter("ErrorBody") shouldBe false
        nameFilter("OperatorCredentialFilterKt") shouldBe true
        nameFilter("OperatorCredentialFilter") shouldBe true
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
