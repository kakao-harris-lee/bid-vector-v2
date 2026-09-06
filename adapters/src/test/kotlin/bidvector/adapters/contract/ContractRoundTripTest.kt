package bidvector.adapters.contract

import contract.bidvector.ml.v1.AmountProvenanceKind
import contract.bidvector.ml.v1.ApplicationFailure
import contract.bidvector.ml.v1.Basis
import contract.bidvector.ml.v1.Currency
import contract.bidvector.ml.v1.FailureCode
import contract.bidvector.ml.v1.LatestPromoted
import contract.bidvector.ml.v1.ModelReleaseSelector
import contract.bidvector.ml.v1.Money
import contract.bidvector.ml.v1.PredictionEnvelope
import contract.bidvector.ml.v1.Rate
import contract.bidvector.ml.v1.RequestEnvelope
import contract.bidvector.ml.v1.Unmeasurable
import contract.bidvector.ml.v1.UnmeasurableReason
import contract.bidvector.ml.v1.VatTreatment
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * M2/2A round-trip — `contracts/testdata/` 의 `.binpb` 표본(canonical, `buf convert`로 생성. 재현 절차는
 * `reports/evidence/m2/2a/commands.md`) 를 생성 Kotlin/Java 타입으로 파싱하고 deterministic
 * serialization 후 바이트가 원본과 같은지 잰다(`OPEN-2A-CANONICAL-FORM` — protobuf deterministic
 * serialization 을 canonical form 으로 확정하는 근거: proto3 필드가 전부 스칼라/oneof/enum 뿐이고
 * unknown field 가 없는 이 testdata 에서는 `CodedOutputStream.useDeterministicSerialization()`이
 * map 없는 단일 메시지의 필드를 선언 순서(오름차순 필드 번호)로 직렬화해 결정적이다).
 *
 * **거부 규칙은 이 test 안의 순수 함수다** — Kotlin 쪽 validation 구현은 M4 몫이라 main 에 두지
 * 않는다(scope.md 「구현 순서」 4). 여기서는 계약이 요구하는 거부 규칙을 test 가 문서화한다.
 */
class ContractRoundTripTest {
    private val testdataRoot: Path = contractTestdataRoot()

    private fun bytes(name: String): ByteArray = readTestdataBytes(testdataRoot, name)

    // ---- ⑦ round-trip: 원본 testdata == parse 후 canonical 재직렬화 ----

    @Test
    fun `Money 는 canonicalization 후 원본과 바이트가 같다`() {
        val original = bytes("money.binpb")
        canonicalBytes(Money.parseFrom(original)).toList() shouldBe original.toList()
    }

    @Test
    fun `Rate 는 canonicalization 후 원본과 바이트가 같다`() {
        val original = bytes("rate.binpb")
        canonicalBytes(Rate.parseFrom(original)).toList() shouldBe original.toList()
    }

    @Test
    fun `RequestEnvelope 는 canonicalization 후 원본과 바이트가 같다`() {
        val original = bytes("request_envelope.binpb")
        canonicalBytes(RequestEnvelope.parseFrom(original)).toList() shouldBe original.toList()
    }

    @Test
    fun `PredictionEnvelope 는 canonicalization 후 원본과 바이트가 같다`() {
        val original = bytes("prediction_envelope.binpb")
        canonicalBytes(PredictionEnvelope.parseFrom(original)).toList() shouldBe original.toList()
    }

    @Test
    fun `Unmeasurable 은 canonicalization 후 원본과 바이트가 같다`() {
        val original = bytes("unmeasurable.binpb")
        canonicalBytes(Unmeasurable.parseFrom(original)).toList() shouldBe original.toList()
    }

    @Test
    fun `ApplicationFailure 는 canonicalization 후 원본과 바이트가 같다`() {
        val original = bytes("application_failure.binpb")
        canonicalBytes(ApplicationFailure.parseFrom(original)).toList() shouldBe original.toList()
    }

    // ---- ② Rate.fraction 정규형 — scale 보존, 지수 표기 거부 ----

    @Test
    fun `Rate fraction 정규형은 scale 을 보존한다`() {
        val rate = Rate.parseFrom(bytes("rate.binpb"))
        rate.fraction shouldBe "0.8700"
        isNormalizedFraction(rate.fraction) shouldBe true
    }

    @Test
    fun `지수 표기 fraction 은 정규형이 아니다`() {
        isNormalizedFraction("1e-3") shouldBe false
        isNormalizedFraction("1E-3") shouldBe false
    }

    @Test
    fun `빈 문자열과 숫자가 아닌 fraction 은 정규형이 아니다`() {
        isNormalizedFraction("") shouldBe false
        isNormalizedFraction("not-a-number") shouldBe false
    }

    // ---- ⑥ fail-closed — enum UNSPECIFIED·정의 밖 정수 거부 ----

    @Test
    fun `유효한 Money 는 허용된다`() {
        val money = Money.parseFrom(bytes("money.binpb"))
        isAcceptableMoney(money) shouldBe true
    }

    @Test
    fun `Money 는 basis 가 UNSPECIFIED 면 거부된다`() {
        val money = validMoneyBuilder().setBasis(Basis.BASIS_UNSPECIFIED).build()
        isAcceptableMoney(money) shouldBe false
    }

    @Test
    fun `Money 는 provenance 가 UNSPECIFIED 면 거부된다`() {
        val money =
            validMoneyBuilder().setProvenance(AmountProvenanceKind.AMOUNT_PROVENANCE_KIND_UNSPECIFIED).build()
        isAcceptableMoney(money) shouldBe false
    }

    @Test
    fun `Money 는 정의 밖 enum 정수(currency)를 거부한다`() {
        // proto3 open enum — 정의되지 않은 정수도 파싱을 통과하지만 getCurrency()는 UNRECOGNIZED.
        val money = validMoneyBuilder().setCurrencyValue(99).build()
        money.currency shouldBe Currency.UNRECOGNIZED
        isAcceptableMoney(money) shouldBe false
    }

    @Test
    fun `Money 는 정의 밖 enum 정수(basis)를 거부한다`() {
        val money = validMoneyBuilder().setBasisValue(-7).build()
        money.basis shouldBe Basis.UNRECOGNIZED
        isAcceptableMoney(money) shouldBe false
    }

    // ---- ④ RequestEnvelope — 빈 문자열 = 미지정 거부 ----

    @Test
    fun `RequestEnvelope 는 request_id 가 빈 문자열이면 거부된다`() {
        val envelope =
            RequestEnvelope
                .newBuilder()
                .setRequestId("")
                .setCorrelationId("corr-1")
                .build()
        isAcceptableRequestEnvelope(envelope) shouldBe false
    }

    @Test
    fun `RequestEnvelope 는 correlation_id 가 빈 문자열이면 거부된다`() {
        val envelope =
            RequestEnvelope
                .newBuilder()
                .setRequestId("req-1")
                .setCorrelationId("")
                .build()
        isAcceptableRequestEnvelope(envelope) shouldBe false
    }

    @Test
    fun `RequestEnvelope 는 둘 다 채워지면 허용된다`() {
        val envelope = RequestEnvelope.parseFrom(bytes("request_envelope.binpb"))
        isAcceptableRequestEnvelope(envelope) shouldBe true
    }

    // ---- ④ PredictionEnvelope — model_release_selector 미지정 거부 ----

    @Test
    fun `PredictionEnvelope 는 selector 가 미지정이면 거부된다`() {
        val envelope =
            PredictionEnvelope
                .newBuilder()
                .setBase(RequestEnvelope.newBuilder().setRequestId("r").setCorrelationId("c"))
                .setFeatureSchemaVersion("award-rate-v1")
                .build()
        envelope.modelReleaseSelector.selectorCase shouldBe ModelReleaseSelector.SelectorCase.SELECTOR_NOT_SET
        isAcceptablePredictionEnvelope(envelope) shouldBe false
    }

    @Test
    fun `PredictionEnvelope 는 exact_release selector 가 있으면 허용된다`() {
        val envelope = PredictionEnvelope.parseFrom(bytes("prediction_envelope.binpb"))
        envelope.modelReleaseSelector.selectorCase shouldBe ModelReleaseSelector.SelectorCase.EXACT_RELEASE
        isAcceptablePredictionEnvelope(envelope) shouldBe true
    }

    @Test
    fun `PredictionEnvelope 는 latest_promoted selector 가 있으면 허용된다`() {
        val envelope =
            PredictionEnvelope
                .newBuilder()
                .setBase(RequestEnvelope.newBuilder().setRequestId("r").setCorrelationId("c"))
                .setFeatureSchemaVersion("award-rate-v1")
                .setModelReleaseSelector(
                    ModelReleaseSelector.newBuilder().setLatestPromoted(LatestPromoted.getDefaultInstance()),
                ).build()
        isAcceptablePredictionEnvelope(envelope) shouldBe true
    }

    // ---- ⑤ 결과 봉투 어휘 — Unmeasurable·ApplicationFailure ----

    @Test
    fun `Unmeasurable reason 이 UNSPECIFIED 면 거부된다`() {
        val unmeasurable =
            Unmeasurable.newBuilder().setReason(UnmeasurableReason.UNMEASURABLE_REASON_UNSPECIFIED).build()
        isAcceptableUnmeasurable(unmeasurable) shouldBe false
    }

    @Test
    fun `testdata 의 Unmeasurable 은 허용된다`() {
        val unmeasurable = Unmeasurable.parseFrom(bytes("unmeasurable.binpb"))
        unmeasurable.reason shouldBe UnmeasurableReason.UNMEASURABLE_REASON_UNTRAINED_SEGMENT
        isAcceptableUnmeasurable(unmeasurable) shouldBe true
    }

    @Test
    fun `testdata 의 ApplicationFailure 는 MODEL_NOT_READY·retryable=true 다`() {
        val failure = ApplicationFailure.parseFrom(bytes("application_failure.binpb"))
        failure.code shouldBe FailureCode.FAILURE_CODE_MODEL_NOT_READY
        failure.retryable shouldBe true
    }

    // ---- fixtures ----

    private fun validMoneyBuilder(): Money.Builder =
        Money
            .newBuilder()
            .setAmountWon(123_456_789L)
            .setCurrency(Currency.CURRENCY_KRW)
            .setBasis(Basis.BASIS_BASE_AMOUNT)
            .setVatTreatment(VatTreatment.VAT_TREATMENT_EXCLUSIVE)
            .setProvenance(AmountProvenanceKind.AMOUNT_PROVENANCE_KIND_PUBLISHED)

    // ---- 계약이 요구하는 거부 규칙 — 순수 함수. Kotlin 쪽 실제 validation 구현은 M4 몫이고,
    // 여기서는 round-trip test 가 그 규칙을 문서화·고정한다(scope.md 「구현 순서」 4).
    // `isNormalizedFraction`은 `ContractFractionRules.kt`(같은 패키지) 공유 함수다 — 2B의
    // `PredictionContractTest`와 중복 정의하지 않는다(verifier r1 F-3). ----

    private fun isAcceptableMoney(money: Money): Boolean =
        isKnown(money.currency, Currency.CURRENCY_UNSPECIFIED, Currency.UNRECOGNIZED) &&
            isKnown(money.basis, Basis.BASIS_UNSPECIFIED, Basis.UNRECOGNIZED) &&
            isKnown(money.vatTreatment, VatTreatment.VAT_TREATMENT_UNSPECIFIED, VatTreatment.UNRECOGNIZED) &&
            isKnown(
                money.provenance,
                AmountProvenanceKind.AMOUNT_PROVENANCE_KIND_UNSPECIFIED,
                AmountProvenanceKind.UNRECOGNIZED,
            )

    private fun isAcceptableRequestEnvelope(envelope: RequestEnvelope): Boolean =
        envelope.requestId.isNotEmpty() && envelope.correlationId.isNotEmpty()

    private fun isAcceptablePredictionEnvelope(envelope: PredictionEnvelope): Boolean =
        envelope.modelReleaseSelector.selectorCase != ModelReleaseSelector.SelectorCase.SELECTOR_NOT_SET

    private fun isAcceptableUnmeasurable(unmeasurable: Unmeasurable): Boolean =
        isKnown(
            unmeasurable.reason,
            UnmeasurableReason.UNMEASURABLE_REASON_UNSPECIFIED,
            UnmeasurableReason.UNRECOGNIZED,
        )

    private fun <T> isKnown(
        value: T,
        unspecified: T,
        unrecognized: T,
    ): Boolean = value != unspecified && value != unrecognized
}
