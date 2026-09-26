package bidvector.app.architecture

import bidvector.app.http.EDITABLE_FIELDS
import bidvector.app.http.EDITABLE_FIELD_TOKENS
import bidvector.app.wiring.EditValue
import bidvector.app.wiring.EditValueSlot
import bidvector.app.wiring.valueSlot
import bidvector.app.wiring.withFieldValue
import bidvector.strategy.StrategyDraft
import bidvector.strategy.ThresholdField
import bidvector.strategy.WatchRuleId
import bidvector.workflow.strategy.EditableField
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal

/**
 * 편집 필드 어휘의 **정의역**을 바이트코드에서 도출해 HTTP 표가 그 전부를 덮는지 잰다
 * (M6/6A-2b D-6A2b-1). 컴파일러의 전수 `when` 은 「필드 → 토큰」 방향만 강제한다 —
 * 역방향 표(`EDITABLE_FIELDS`)에서 하나가 빠지면 그 필드는 HTTP 로 **부를 수 없게 되는데**
 * 컴파일러는 그 부재를 보지 못한다. 그 사각을 이 게이트가 덮는다.
 *
 * 양쪽이 모두 기계 산출이라 손 목록이 없다 — 새 `EditableField` 하위 타입이나 새
 * `WatchRuleId`·`ThresholdField` 가 생기면 표를 갱신할 때까지 RED 다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EditableFieldVocabularyGateTest {
    private val production: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TEST_FIXTURES)
            .importPackages("bidvector")

    private fun subclassNames(type: Class<*>): Set<String> =
        production
            .get(type)
            .subclasses
            .map(JavaClass::getName)
            .toSet()

    @Test
    fun `HTTP 표의 필드 갈래가 EditableField 하위 타입 전부와 같다 — 집합 등식`() {
        EDITABLE_FIELDS.map { it::class.java.name }.toSet() shouldBe subclassNames(EditableField::class.java)
    }

    @Test
    fun `HTTP 표의 감시·임계 필드가 그 어휘 전부와 같다 — 집합 등식`() {
        EDITABLE_FIELDS
            .filterIsInstance<EditableField.Watch>()
            .map { it.id::class.java.name }
            .toSet() shouldBe subclassNames(WatchRuleId::class.java)

        EDITABLE_FIELDS
            .filterIsInstance<EditableField.Threshold>()
            .map { it.field::class.java.name }
            .toSet() shouldBe subclassNames(ThresholdField::class.java)
    }

    @Test
    fun `토큰은 필드마다 서로 다르다 — 표를 뒤집어도 충돌이 없다`() {
        EDITABLE_FIELD_TOKENS.size shouldBe EDITABLE_FIELDS.size
    }

    /**
     * 「필드가 이름하는 칸」과 「그 칸의 값으로 초안을 고치는 자리」는 서로 다른 전수 `when`
     * 이다 — 컴파일러는 각각을 총(total)으로 만들 뿐 **둘이 같은 칸을 말하는지**는 보지
     * 않는다. 필드 전수를 실제로 태워 그 짝을 고정한다(어긋나면 `check` 가 크게 실패한다).
     */
    @Test
    fun `모든 필드가 자기 칸의 값으로 초안을 바꾼다 — 두 표의 짝 맞춤`() {
        val base = StrategyDraft()

        val changed =
            EDITABLE_FIELDS.associateWith { field ->
                base.withFieldValue(field, sampleValue(field.valueSlot()))
            }

        changed.forEach { (field, draft) -> withClue(field) { draft shouldNotBe base } }
        changed.size shouldBe EDITABLE_FIELDS.size
    }

    private fun <T> withClue(
        clue: Any,
        block: () -> T,
    ): T =
        runCatching(block).getOrElse { failure ->
            throw AssertionError("필드 $clue 에서 실패했다", failure)
        }

    private fun sampleValue(slot: EditValueSlot): EditValue =
        when (slot) {
            EditValueSlot.TERMS -> EditValue.Terms(listOf("어휘-표본"))
            EditValueSlot.AMOUNT_WON -> EditValue.AmountWon(1_000L)
            EditValueSlot.NUMBER -> EditValue.Number(BigDecimal("0.5"))
            EditValueSlot.COUNT -> EditValue.Count(3)
        }
}
