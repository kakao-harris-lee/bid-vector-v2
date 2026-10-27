package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * DEC-10 게이트의 **구조 등식**(D-DEC10-6 ⓐⓑ) — `capability-map.md` DEC-10 의 「가장 교훈적인
 * 실패 패턴」이 요구하는 것을 집행한다: *「이 값이 안전한 이유는 소비자가 X 만 하기 때문」이라는
 * 형태의 근거는 소비자가 늘어나면 자동으로 깨진다. V2 는 이런 근거를 주석이 아니라
 * architecture/contract test 로 표현한다(소비자가 늘면 테스트가 깨지도록)*.
 *
 * 술어는 `DivisionValueRules` 의 축 ①②를 **재사용**한다 — 그 클래스의 세 축(타입 멤버 접근 ·
 * 값 획득 · 클래스 객체 참조)은 타입을 값으로 받는 제네릭 기계이고, 이름만 6F-9 것이다.
 * `ArchRule` 쪽(부분집합 방향)은 쓰지 않는다: 그 `because` 문면이 대분류 축을 말하므로 DEC-10
 * 실패에 틀린 사유를 찍는다. **등식은 부분집합보다 강하다** — 관측 == 허용이면 허용 밖 쌍이
 * 없다는 것과 낡은 허용 항목이 없다는 것을 함께 센다.
 *
 * 텍스트 grep 술어가 없다 — 전부 컴파일된 바이트코드의 접근 집합이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FloorTrustGateTest {
    private val policy = ArchitecturePolicy.load()
    private val gatedRules = DivisionValueRules(policy.floorTrustGatedType)
    private val rawRules = DivisionValueRules(policy.floorTrustRawType)
    private val creationRules = FloorTrustRules(policy.floorTrustGatedType)
    private val production: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TEST_FIXTURES)
            .importPackages(policy.packageRoot)

    @Test
    fun `게이트 통과 값 타입이 실제로 production 에 있다 — 등식이 공허하지 않다`() {
        production.filter { it.fullName == policy.floorTrustGatedType }.shouldNotBeEmpty()
        production.filter { it.fullName == policy.floorTrustRawType }.shouldNotBeEmpty()
    }

    @Test
    fun `ⓐ 게이트 통과 값의 멤버 접근 쌍 집합은 허용 집합과 같다`() {
        gatedRules.observedTypeAccesses(production) shouldBe policy.floorTrustGatedTypeAccessPairs.toSet()
    }

    /**
     * 「생성 경로는 게이트 함수 **하나**」의 기계적 증명 — **코드 단위**(함수) 입도다(r1 v H-1 (b)).
     *
     * 앞 판은 이 단언을 **정책 파일에서** 냈고(허용 쌍을 걸러 호출자를 셌다) 호출자가 파일
     * 파사드였다. 그래서 ⓐ 등식과 합성해서만 의미가 있었고, 같은 파일에 밴드를 무시하는 둘째
     * 생성 함수를 넣으면 **둘 다 초록**이었다. 지금은 production 바이트코드에서 관측한 함수 집합이
     * 허용 집합과 **같아야** 한다 — 동어반복이 아니고, 파일 안에서 함수가 늘면 깨진다.
     */
    @Test
    fun `ⓐ′ 생성 계열을 만지는 코드 단위 집합은 허용 집합과 같다 — 파일이 아니라 함수`() {
        creationRules.observedCreationCodeUnits(production) shouldBe policy.floorTrustCreationCodeUnits.toSet()
    }

    /** ⓐ′ 가 **무엇을** 생성 계열로 셌는지 — 빈 집합을 세고 초록인 상태를 가른다. */
    @Test
    fun `ⓐ″ 생성 계열로 관측된 멤버가 실제로 있다 — 축이 비어 있지 않다`() {
        creationRules.observedMemberNames(production).shouldNotBeEmpty()
    }

    /**
     * r1 v H-1 (a) — 원값의 **멤버 접근** 쌍 등식. 획득 축(ⓑ)은 디스크립터의 반환·필드 타입만
     * 보므로 **파생 접근자**(원값에서 `BigDecimal` 을 꺼내 내보내는 getter)와 **운반 클래스**가
     * 그 축 밖이었다 — 그 둘을 심어도 전건 게이트가 초록인 것이 실측됐다. 이 축은 원값 타입
     * 자신의 멤버를 만지는 자리 전부를 세므로 그 형태가 집합을 바꾼다.
     */
    @Test
    fun `ⓑ′ 원값의 멤버 접근 쌍 집합은 허용 집합과 같다`() {
        rawRules.observedTypeAccesses(production) shouldBe policy.floorTrustRawTypeAccessPairs.toSet()
    }

    @Test
    fun `ⓑ 원값을 얻는 쌍 집합은 허용 집합과 같다 — production 전 층`() {
        rawRules.observedAcquisitions(production) shouldBe policy.floorTrustRawAcquisitionPairs.toSet()
    }

    /**
     * ⓑ 의 양성 쪽 — 관측 집합이 **비어 있지 않다**. 비어 있으면 「원값을 얻는 자리가 없다」와
     * 「측정이 망가졌다」가 같은 값을 낸다(수집·영속이 원값을 다루므로 반드시 비지 않는다).
     */
    @Test
    fun `ⓑ′ 원값 획득 관측 집합은 비어 있지 않다 — 측정이 살아 있다`() {
        rawRules.observedAcquisitions(production).shouldNotBeEmpty()
    }
}
