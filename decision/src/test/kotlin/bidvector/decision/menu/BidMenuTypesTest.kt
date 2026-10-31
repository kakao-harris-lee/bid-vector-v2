package bidvector.decision.menu

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * 메뉴 값 타입의 생성 불변식 — **표현 불가능한 상태를 타입으로 닫는다.**
 *
 * 모듈 밖 생성 폐쇄는 여기서 못 잰다(같은 모듈 test 는 `internal` 생성자를 그냥 부른다) —
 * 그것은 [MenuCompileFailureHarnessTest] 의 몫이다. 이 class 는 **생성자가 열려 있는 쪽에서**
 * 불변식이 실제로 던지는지를 잰다.
 */
class BidMenuTypesTest {
    /** 방향은 **공격이 가장 낮고 안전이 가장 높다**(라운드 2 HIGH-1) — 거꾸로는 만들어지지 않는다. */
    @Test
    fun `StanceRates 는 안전이 공격보다 낮은 조합을 받지 않는다`() {
        shouldThrow<IllegalArgumentException> {
            StanceRates(safe = fraction("0.90"), recommended = fraction("0.93"), aggressive = fraction("0.96"))
        }
        shouldThrow<IllegalArgumentException> {
            StanceRates(safe = fraction("0.96"), recommended = fraction("0.97"), aggressive = fraction("0.90"))
        }
    }

    @Test
    fun `StanceRates 는 태도 전수를 대응시킨다`() {
        val rates = testRates(aggressive = "0.90", recommended = "0.93", safe = "0.96")

        rates.rateOf(BidStance.Aggressive) shouldBe fraction("0.90")
        rates.rateOf(BidStance.Recommended) shouldBe fraction("0.93")
        rates.rateOf(BidStance.Safe) shouldBe fraction("0.96")
    }

    /** 자리와 태도가 어긋난 메뉴는 「정상 3안」으로 나갈 수 없다 — 뒤섞인 메뉴의 위조를 막는다. */
    @Test
    fun `Offered 는 태도가 자리와 어긋나면 만들어지지 않는다`() {
        val offered = anyOfferedMenu()

        shouldThrow<IllegalArgumentException> {
            BidMenuOutcome.Offered(
                safe = offered.recommended,
                recommended = offered.recommended,
                aggressive = offered.aggressive,
                disclosures = BidMenuDisclosure.entries.toSet(),
                policyVersion = MENU_TEST_VERSION,
            )
        }
    }

    /** 고지 하나를 뺀 메뉴는 표현 불가능하다 — 고지 의무가 값과 함께 표면까지 이동한다. */
    @Test
    fun `Offered 는 공통 고지가 빠지면 만들어지지 않는다`() {
        val offered = anyOfferedMenu()

        shouldThrow<IllegalArgumentException> {
            BidMenuOutcome.Offered(
                safe = offered.safe,
                recommended = offered.recommended,
                aggressive = offered.aggressive,
                disclosures = setOf(BidMenuDisclosure.NotAutoSubmitted),
                policyVersion = MENU_TEST_VERSION,
            )
        }
    }

    /**
     * (r2 M-4) — 결과가 **커널의 살아 있는 컬렉션**을 그대로 실으면 불변식이 사후에 무너진다.
     * 생성 시 복사해 가두므로 캐스팅해 비우려는 시도는 던진다.
     */
    @Test
    fun `Offered 의 고지 집합은 사후 변조되지 않는다`() {
        val offered = anyOfferedMenu()

        shouldThrow<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (offered.disclosures as MutableSet<BidMenuDisclosure>).clear()
        }
        offered.disclosures shouldBe BidMenuDisclosure.entries.toSet()
    }

    @Test
    fun `PartiallyOffered 의 제시·보류 맵은 사후 변조되지 않는다`() {
        val partial = anyPartialMenu()

        shouldThrow<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (partial.offered as MutableMap<BidStance, BidMenuOption>).clear()
        }
        shouldThrow<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (partial.withheld as MutableMap<BidStance, MenuUnmeasurableReason>).clear()
        }
        partial.offered.isNotEmpty() shouldBe true
        partial.withheld.isNotEmpty() shouldBe true
    }

    /**
     * (cr r2 L-3) — `PartiallyOffered` 의 `init` 불변식 여섯이 하나도 재지지 않았다. 커널이
     * 위반을 만들 수 없더라도 생성자는 같은 모듈에 열려 있으므로 `Offered` 와 같은 방식으로 잰다.
     */
    @Test
    fun `PartiallyOffered 는 여섯 불변식을 각각 거부한다`() {
        val partial = anyPartialMenu()
        val stance = partial.offered.keys.first()
        val option = partial.offered.getValue(stance)
        val reason = partial.withheld.values.first()
        val all = BidMenuDisclosure.entries.toSet()

        // ① 제시가 비면 Unmeasurable 의 자리다
        shouldThrow<IllegalArgumentException> {
            BidMenuOutcome.PartiallyOffered(emptyMap(), partial.withheld, all, MENU_TEST_VERSION)
        }
        // ② 보류가 비면 Offered 의 자리다
        shouldThrow<IllegalArgumentException> {
            BidMenuOutcome.PartiallyOffered(partial.offered, emptyMap(), all, MENU_TEST_VERSION)
        }
        // ③ 한 태도가 제시·보류 양쪽에 있을 수 없다
        shouldThrow<IllegalArgumentException> {
            BidMenuOutcome.PartiallyOffered(partial.offered, mapOf(stance to reason), all, MENU_TEST_VERSION)
        }
        // ④ 태도 전수를 덮어야 한다
        shouldThrow<IllegalArgumentException> {
            BidMenuOutcome.PartiallyOffered(mapOf(stance to option), emptyMap(), all, MENU_TEST_VERSION)
        }
        // ⑤ 제시 옵션의 태도가 키와 맞아야 한다
        val wrongKey = BidStance.entries.first { it != stance }
        shouldThrow<IllegalArgumentException> {
            BidMenuOutcome.PartiallyOffered(
                mapOf(wrongKey to option),
                BidStance.entries.filter { it != wrongKey }.associateWith { reason },
                all,
                MENU_TEST_VERSION,
            )
        }
        // ⑥ 공통 고지는 전수여야 한다
        shouldThrow<IllegalArgumentException> {
            BidMenuOutcome.PartiallyOffered(
                partial.offered,
                partial.withheld,
                setOf(BidMenuDisclosure.NotAutoSubmitted),
                MENU_TEST_VERSION,
            )
        }
    }

    /**
     * (팀장 지적, M-4 보강) — **가둔 뷰만으로는 부족하다.** data class 의 `equals`·`hashCode` ·
     * `toString` · `componentN` 은 primary 생성자의 프로퍼티를 읽는데, 그 자리에 **호출자가 넘긴
     * 원래 가변 컬렉션 참조**가 그대로 남아 있으면 생성 뒤 원본을 바꿀 때 공개 뷰는 그대로인데
     * `equals`·`hashCode` 가 따라 움직인다 — 해시 컬렉션에 들어간 결과가 길을 잃는 자리다.
     */
    @Test
    fun `결과는 생성 뒤 원본이 바뀌어도 equals 와 hashCode 가 움직이지 않는다`() {
        val offered = anyOfferedMenu()
        val mutableSource = BidMenuDisclosure.entries.toMutableSet()
        // 인자를 자리로 넘긴다 — 생성자 파라미터 이름이 바뀌어도 이 test 의 뜻은 같다.
        val fromMutable =
            BidMenuOutcome.Offered(
                offered.safe,
                offered.recommended,
                offered.aggressive,
                mutableSource,
                MENU_TEST_VERSION,
            )
        val fromFixed =
            BidMenuOutcome.Offered(
                offered.safe,
                offered.recommended,
                offered.aggressive,
                BidMenuDisclosure.entries.toSet(),
                MENU_TEST_VERSION,
            )
        (fromMutable == fromFixed) shouldBe true
        val hashBefore = fromMutable.hashCode()

        mutableSource.clear()

        fromMutable.disclosures shouldBe BidMenuDisclosure.entries.toSet()
        fromMutable.hashCode() shouldBe hashBefore
        (fromMutable == fromFixed) shouldBe true
    }

    /**
     * (r3 N-2) — 결과 그래프의 컬렉션 **여섯 중 하나**(`CollapsedWithOtherStances.otherStances`)가
     * 가둠 밖에 있었다. `withheld` 맵이 `unmodifiableMap` 이어도 그 **값** 안의 집합은 캐스팅
     * 한 줄로 비울 수 있어, 그 타입의 `init` 불변식 둘이 사후에 무너졌다.
     */
    @Test
    fun `보류 사유의 겹친 태도 집합은 사후 변조되지 않는다`() {
        val partial = anyPartialMenu()
        val reason =
            partial.withheld.values
                .filterIsInstance<MenuUnmeasurableReason.CollapsedWithOtherStances>()
                .first()
        val hashBefore = reason.hashCode()

        shouldThrow<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (reason.otherStances as MutableSet<BidStance>).clear()
        }
        reason.otherStances.isNotEmpty() shouldBe true
        reason.hashCode() shouldBe hashBefore
    }

    /** `BandCollapsed`·`Unmeasurable` 도 `Offered` 와 같은 꼴로 원본 표류를 막는다(r3 N-3). */
    @Test
    fun `실패 결과도 생성 뒤 원본이 바뀌면 흔들리지 않는다`() {
        val mutableStances = BidStance.entries.toMutableSet()
        val collapsed = BidMenuOutcome.BandCollapsed(CollapseCause.DuplicateEffectiveAmounts(1L), mutableStances)
        val collapsedHash = collapsed.hashCode()

        val mutableWithheld =
            BidStance.entries
                .associateWith<BidStance, MenuUnmeasurableReason> {
                    MenuUnmeasurableReason.FloorBoundNotRepresentable(it)
                }.toMutableMap()
        val unmeasurable = BidMenuOutcome.Unmeasurable(mutableWithheld)
        val unmeasurableHash = unmeasurable.hashCode()

        mutableStances.clear()
        mutableWithheld.clear()

        collapsed.collapsedStances shouldBe BidStance.entries.toSet()
        collapsed.hashCode() shouldBe collapsedHash
        unmeasurable.withheld.keys shouldBe BidStance.entries.toSet()
        unmeasurable.hashCode() shouldBe unmeasurableHash
    }

    private fun anyPartialMenu(): BidMenuOutcome.PartiallyOffered =
        composeBidMenu(
            testInput(rates = testRates(aggressive = "0.93", recommended = "0.93", safe = "0.96")),
            testMenuPolicy(reading = FloorRateReading.Undecided),
        ).shouldBeInstanceOf<BidMenuOutcome.PartiallyOffered>()

    private fun anyOfferedMenu(): BidMenuOutcome.Offered =
        composeBidMenu(testInput(), testMenuPolicy(reading = atOrAbove()))
            .shouldBeInstanceOf<BidMenuOutcome.Offered>()
}
