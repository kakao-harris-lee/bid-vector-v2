package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * DEC-10 게이트의 **구조 등식**(D-DEC10-6 ⓐⓑ · D-DEC10-17 입도 · D-DEC10-18 밴드 출처) — `capability-map.md` DEC-10 의 「가장
 * 교훈적인 실패 패턴」이 요구하는 것을 집행한다: *「이 값이 안전한 이유는 소비자가 X 만 하기
 * 때문」이라는 형태의 근거는 소비자가 늘어나면 자동으로 깨진다. V2 는 이런 근거를 주석이 아니라
 * architecture/contract test 로 표현한다(소비자가 늘면 테스트가 깨지도록)*.
 *
 * **호출자는 전부 코드 단위(메서드·서명)로 센다**(D-DEC10-17, `FloorTrustRules`). 파일 파사드
 * 입도는 r1·r2 가 두 번, **자기 제외**(그 타입 자신에 쓴 메서드)는 r3 가 한 번 뚫었다 — 지금은
 * 자기 멤버까지 허용 목록에 서명 키로 적는다. 등식은 부분집합이
 * 아니라 **관측 == 허용**이라, 허용 밖 자리와 낡은 허용 항목을 함께 센다.
 *
 * 통과 값의 **클래스 입도** 멤버 접근 쌍(ⓐ)은 `DivisionValueRules` 를 그대로 쓴다 — 거친 그물을
 * 하나 더 두는 것이고, 그 축이 잡는 것(새 멤버의 등장)은 코드 단위와 다른 사실이다.
 *
 * 텍스트 grep 술어가 없다 — 전부 컴파일된 바이트코드의 접근 집합이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FloorTrustGateTest {
    private val policy = ArchitecturePolicy.load()
    private val gatedClassRules = DivisionValueRules(policy.floorTrustGatedType)
    private val gatedRules = FloorTrustRules(policy.floorTrustGatedType)
    private val rawRules = FloorTrustRules(policy.floorTrustRawType)
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
    fun `ⓐ 게이트 통과 값의 멤버 접근 쌍 집합은 허용 집합과 같다 — 클래스 입도`() {
        gatedClassRules.observedTypeAccesses(production) shouldBe policy.floorTrustGatedTypeAccessPairs.toSet()
    }

    /**
     * 「생성 경로는 게이트 함수 **하나**」의 기계적 증명 — **코드 단위**(메서드·서명) 입도다.
     * 같은 파일의 둘째 생성 함수도, 같은 이름 오버로드도 이 집합을 바꾼다.
     */
    @Test
    fun `ⓐ′ 생성 계열을 만지는 코드 단위 집합은 허용 집합과 같다`() {
        gatedRules.observedCreationCodeUnits(production) shouldBe policy.floorTrustCreationCodeUnits.toSet()
    }

    @Test
    fun `ⓐ″ 생성 계열로 관측된 멤버가 실제로 있다 — 축이 비어 있지 않다`() {
        gatedRules.observedCreationMembers(production).shouldNotBeEmpty()
    }

    /**
     * **ⓑ·ⓑ′ 는 보장이 아니라 재고(inventory)다**(D-DEC10-22). census 는 **타입을 따라가므로**
     * 값의 표현이 바뀌면(문자열·`BigDecimal`·영속 행·JSON) 그 자리가 어느 축에도 남지 않는다 —
     * verifier r4 가 그 두 꼴을 실측했다. 그래서 「원값이 가격 경로에 닿을 수 없다」를 **이 축이
     * 보장하지 않는다**. 보장하는 것은 **진입 타입**(ⓓ + 컴파일 실패 fixture)이고, 이 두 축이
     * 재는 것은 **누가 원값을 만지는가가 바뀌었다**는 사실이다 — 새 소비자·새 운반 자리가
     * 생기면 목록 한 줄을 명시적으로 더하게 되고, 그 자리가 가격 쪽인지 분석·표시 쪽인지는
     * 사람이 분류한다.
     */
    @Test
    fun `ⓑ 원값을 얻는 코드 단위 쌍 집합은 허용 집합과 같다 — production 전 층`() {
        rawRules.observedAcquisitionUnits(production) shouldBe policy.floorTrustRawAcquisitionPairs.toSet()
    }

    /**
     * 원값의 **멤버 접근** 쌍 등식. 획득 축(ⓑ)은 디스크립터의 반환·필드 타입만 보므로 **파생
     * 접근자**(원값에서 숫자만 꺼내 내보내는 함수)와 **운반 클래스**가 그 축 밖이다. 허용된
     * 가격 경로 파일 안에 그 함수를 더하는 형태를 r2 가 실측했고, 코드 단위 입도가 그것을 닫는다.
     */
    @Test
    fun `ⓑ′ 원값의 멤버 접근 코드 단위 쌍 집합은 허용 집합과 같다`() {
        rawRules.observedMemberAccessUnits(production) shouldBe policy.floorTrustRawTypeAccessPairs.toSet()
    }

    /**
     * ⓑ 의 양성 쪽 — 관측 집합이 **비어 있지 않다**. 비어 있으면 「원값을 얻는 자리가 없다」와
     * 「측정이 망가졌다」가 같은 값을 낸다(수집·영속이 원값을 다루므로 반드시 비지 않는다).
     */
    @Test
    fun `ⓑ″ 원값 획득 관측 집합은 비어 있지 않다 — 측정이 살아 있다`() {
        rawRules.observedAcquisitionUnits(production).shouldNotBeEmpty()
    }

    /**
     * **밴드의 출처**(r2 CR M-1) — 게이트는 밴드를 인자로 받으므로, 위조한 정책 값을 진짜 게이트
     * 함수에 넘기면 위의 어느 축도 바뀌지 않는다(생성 자리도 멤버도 그대로다). 그래서 정책 값
     * 타입의 **생성 코드 단위**를 따로 센다 — production 에서 그 값을 만드는 자리는 출하 정책
     * 상수 하나여야 한다.
     */
    @Test
    fun `ⓒ 정책 값을 만드는 코드 단위 집합은 허용 집합과 같다 — 밴드의 출처가 하나다`() {
        val observed =
            policy.floorTrustPolicyValueTypes
                .flatMap { type -> FloorTrustRules(type).observedCreationCodeUnits(production) }
                .toSet()

        observed shouldBe policy.floorTrustPolicyValueCreationCodeUnits.toSet()
    }

    /**
     * **가격 경로 ③ 의 입력 타입**(D-DEC10-22) — 생성자가 `internal` 이라 모듈 밖은 컴파일로
     * 막히고(컴파일 실패 fixture), 모듈 **안**에서 날 `Rate?` 로 입력을 짓는 자리는 이 등식이
     * 센다. production 의 생성 자리는 게이트 산출물을 받는 공개 factory 하나여야 한다.
     */
    @Test
    fun `ⓓ 가격 경로 입력 타입을 만드는 코드 단위 집합은 허용 집합과 같다`() {
        val observed =
            policy.floorTrustPriceEntryTypes
                .flatMap { type -> FloorTrustRules(type).observedCreationCodeUnits(production) }
                .toSet()

        observed shouldBe policy.floorTrustPriceEntryCreationCodeUnits.toSet()
    }

    @Test
    fun `ⓓ′ 가격 경로 입력 타입이 실제로 production 에 있다 — 등식이 공허하지 않다`() {
        policy.floorTrustPriceEntryTypes.shouldNotBeEmpty()
        policy.floorTrustPriceEntryTypes.forEach { type ->
            production.filter { it.fullName == type }.shouldNotBeEmpty()
        }
    }

    @Test
    fun `ⓒ′ 정책 값 타입이 실제로 production 에 있다 — 등식이 공허하지 않다`() {
        policy.floorTrustPolicyValueTypes.shouldNotBeEmpty()
        policy.floorTrustPolicyValueTypes.forEach { type ->
            production.filter { it.fullName == type }.shouldNotBeEmpty()
        }
    }
}
