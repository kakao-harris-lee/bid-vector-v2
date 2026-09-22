package bidvector.app.http

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 우회 (4) 폐쇄 — 타이밍 test는 CI에서 본질적으로 flaky해 만들지 않는다(팀장 preflight
 * 논의 그대로). 대신 **구조**를 잰다: 패키지의 모든 컴파일된 class 파일이 `MessageDigest
 * .isEqual`은 어딘가에서 쓰되(자격증명 비교가 실재함을 보장), `String.equals`는 아무도
 * 안 쓰고, `Intrinsics.areEqual`(Kotlin `==`이 컴파일되는 자리)은 **명시 허용 목록** 밖
 * 어디에도 없어야 한다.
 *
 * **D-6A1-37 시정 — 판정 범위를 이름 축에서 허용 목록으로 뒤집는다.** D-6A1-29 는
 * 발견(`walkTopDown()`)만 패키지 전체로 넓히고 판정 대상은 다시 `OperatorCredentialFilter`
 * 이름 접두사로 좁혔다. verifier 실측(MUT-N1): 자격증명 비교를 같은 패키지의 다른 이름
 * 클래스(`CredentialComparator`)로 추출하고 `==`를 쓰면 그 class 파일은 이름 필터 밖이라
 * **아무도 안 본다** — 전건 `check`가 초록인 채 단락 비교가 되살아난다.
 *
 * 이름 목록과 허용 목록의 차이는 **기본값**이다 — 이름 목록에서 새 class는 기본이
 * 미판정이라 우회가 생길 때마다 이름을 늘려야 하고(끝이 없다), 허용 목록에서 새 class는
 * 기본이 판정이라 늘릴 필요가 없다. 그래서 여기서는 **패키지 전체 class 파일**을 판정하고,
 * `Intrinsics.areEqual`을 정당하게 쓰는 class만 [ALLOWED_INTRINSICS_AREEQUAL]에 **개별
 * 근거와 함께** 올린다 — 전부 data class 자동 생성 `equals()`이거나 sealed 싱글턴 객체
 * 참조 동일성이고, 자격증명 값을 참조하지 않는다(javap로 실측 확인, 아래 근거 참고).
 */
class ConstantTimeComparisonStructureTest {
    @Test
    fun `패키지의 어떤 class 도 허용 목록 밖에서 Intrinsics areEqual 을 쓰지 않는다`() {
        val outputs = packageClassOutputs()
        outputs.keys.shouldNotBeEmpty()

        outputs.values.any { it.contains(MESSAGE_DIGEST_IS_EQUAL) } shouldBe true
        outputs.filterValues { it.contains(STRING_EQUALS) }.keys shouldBe emptySet<String>()
        disallowedAreEqual(outputs) shouldBe emptySet<String>()
    }

    /**
     * 양성 대조 — 허용 목록 밖 class(예: 자격증명 비교를 추출한 형제 클래스)가
     * `Intrinsics.areEqual`을 쓰면 술어가 실제로 잡는지, 실제 바이트코드 없이 술어
     * 로직만으로 고정한다(MUT-N1 시나리오를 진 채로 판정 대상이 이름이 아니라 허용 목록
     * 전체임을 확인).
     */
    @Test
    fun `허용 목록 밖 class 의 Intrinsics areEqual 은 이 술어에 걸린다 — 양성 대조`() {
        val simulated =
            mapOf(
                "CredentialComparator" to
                    """
                    invokestatic kotlin/jvm/internal/Intrinsics.areEqual:(Ljava/lang/Object;Ljava/lang/Object;)Z
                    """.trimIndent(),
                "ErrorBody" to
                    """
                    invokestatic kotlin/jvm/internal/Intrinsics.areEqual:(Ljava/lang/Object;Ljava/lang/Object;)Z
                    """.trimIndent(),
            )

        disallowedAreEqual(simulated) shouldBe setOf("CredentialComparator")
    }

    /** 양성 대조 — 허용 목록의 각 항목은 근거 문장을 갖고 임의로 늘지 않는다. */
    @Test
    fun `허용 목록은 정해진 넷뿐이고 각각 근거 문장을 갖는다`() {
        ALLOWED_INTRINSICS_AREEQUAL.keys shouldBe
            setOf("ApiAuditRecord", "ErrorBody", "StrategyReadResponse", "StrategyReadControllerKt")
        ALLOWED_INTRINSICS_AREEQUAL.values.all { it.isNotBlank() } shouldBe true
    }
}

/**
 * `Intrinsics.areEqual`을 정당하게 남기는 class 파일 이름(`nameWithoutExtension`) → 근거.
 * **자격증명 비교를 담지 않는 class만** 올린다 — 새 항목을 더할 때마다 그 class가
 * [OperatorCredentialFilter]의 자격증명 값(`expectedCredential`·`presented`)을 참조하지
 * 않는지 javap으로 확인한다. 이름 접두사가 아니라 **종류**(data class 자동 equals 또는
 * sealed 싱글턴 참조 동일성)로 가른 목록이다.
 */
private val ALLOWED_INTRINSICS_AREEQUAL: Map<String, String> =
    mapOf(
        "ApiAuditRecord" to
            "감사 행 값 객체(data class) 자동 생성 equals — 필드는 시각·주체·경로·상태·correlation id, 자격증명 값 없음",
        "ErrorBody" to
            "오류 응답 값 객체(data class) 자동 생성 equals — 필드는 code·message·correlationId, 자격증명 값 없음",
        "StrategyReadResponse" to
            "전략 조회 응답 값 객체(data class) 자동 생성 equals — 전략 필드뿐, 자격증명 값 없음",
        "StrategyReadControllerKt" to
            "provenanceLabel()의 Provenance sealed 싱글턴 객체 참조 동일성(when 소진, javap 확인 완료) — 자격증명과 무관",
    )

private const val MESSAGE_DIGEST_IS_EQUAL = "java/security/MessageDigest.isEqual"
private const val STRING_EQUALS = "java/lang/String.equals"
private const val INTRINSICS_AREEQUAL = "kotlin/jvm/internal/Intrinsics.areEqual"

private fun disallowedAreEqual(outputs: Map<String, String>): Set<String> =
    outputs
        .filterKeys { it !in ALLOWED_INTRINSICS_AREEQUAL }
        .filterValues { it.contains(INTRINSICS_AREEQUAL) }
        .keys

private fun packageClassOutputs(): Map<String, String> {
    val packageDir = File("build/classes/kotlin/main/bidvector/app/http")
    check(packageDir.isDirectory) {
        "빌드 산출물을 찾지 못했다: ${packageDir.absolutePath} — :app:compileKotlin 선행 필요"
    }
    return packageDir
        .walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .associate { it.nameWithoutExtension to javap(it) }
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
