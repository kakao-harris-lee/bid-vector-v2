package bidvector.app.wiring

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * D-6A3-2 — `app.wiring`이 `adapters.ml`에서 참조하는 클래스 집합은 `UnavailableMlAnalysis`
 * 뿐이다(운영자 결정 1, 자리지킴 유지). `OpportunityAnalysis`·`GrpcBidPredictionGateway`·
 * `GrpcEmbeddingGateway`는 실 ML 배선(`OPEN-ML-ANALYSIS-WIRING`)이 열기 전까지 이 slice
 * 어디에도 나타나지 않는다. **구조 게이트**(바이트코드 상수 풀 실측) — 소스 텍스트 grep이
 * 아니다(`EvaluationAdapterDependencyTest`와 같은 기법, [packageJavapOutput] 공유).
 */
class MlPortWiringGateTest {
    @Test
    fun `app wiring 이 참조하는 adapters ml 클래스 집합은 UnavailableMlAnalysis 뿐이다`() {
        val output = packageJavapOutput(File("build/classes/kotlin/main/bidvector/app/wiring"))
        referencedAdaptersMlClasses(output) shouldBe setOf("UnavailableMlAnalysis")
    }

    /** 양성 대조 — 다른 ml 클래스 이름을 심은 표본은 이 술어에 걸린다(구조가 실제로 잰다). */
    @Test
    fun `허용 밖 adapters ml 클래스를 참조하는 표본은 이 술어에 걸린다 — 양성 대조`() {
        val synthetic =
            """
            // Lbidvector/adapters/ml/UnavailableMlAnalysis;
            // Lbidvector/adapters/ml/GrpcBidPredictionGateway;
            """.trimIndent()

        referencedAdaptersMlClasses(synthetic) shouldBe setOf("UnavailableMlAnalysis", "GrpcBidPredictionGateway")
    }
}

private val ADAPTERS_ML_REFERENCE = Regex("""bidvector[/.]adapters[/.]ml[/.]([A-Za-z0-9_$]+)""")

/** `bidvector.adapters.ml.<Name>` 형태의 참조를 텍스트에서 뽑는다(패키지 하위 클래스 이름만). */
private fun referencedAdaptersMlClasses(javapOutput: String): Set<String> =
    ADAPTERS_ML_REFERENCE
        .findAll(javapOutput)
        .map { it.groupValues[1].substringBefore('$') }
        .toSet()
