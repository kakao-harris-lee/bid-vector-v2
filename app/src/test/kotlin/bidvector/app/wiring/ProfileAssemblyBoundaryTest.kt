package bidvector.app.wiring

import bidvector.adapters.profile.JdbcBusinessProfileStore
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.profile.BusinessProfileStorePort
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.sql.DataSource

/** V22(계정 사업 정보)의 타입이 사는 패키지. */
private const val PROFILE_PACKAGE = "bidvector.workflow.profile"

/** 조립이 사는 패키지. */
private const val WIRING_PACKAGE = "bidvector.app.wiring"

/**
 * **2b 표적 4 — 진짜 방어는 조립 자리의 포트 경계다.**
 *
 * 2a 의 격리 게이트들은 「판정 패키지가 V22 를 **이름으로** 보지 않는다」를 재고, 그 술어가 덮지 못하는
 * 길(이행 값 흐름 · 제3 패키지의 인터페이스 · 실행 시 조립한 이름)을 알려진 제한으로 적어 두었다.
 * 그 길들이 실제로 값을 나르려면 **누군가 판정 쪽에 V22 를 건네야** 하고, 건네는 자리는 조립뿐이다.
 * 그래서 술어를 하나 더 만들지 않고 **집합 규칙** 하나를 세운다:
 *
 * > `app.wiring` 의 class 가운데 **V22 타입에 의존하는** 것은 [ProfileWiring] 과 그 중첩 class 뿐이다.
 *
 * **술어와 모집단을 모두 구조로 닫는다**(r2 v N1). 앞 판 둘은 각각 반만 닫았다: r1 은 **이름 접두**로
 * 판정해 어댑터 구현 타입으로 감싼 길이 지나갔고(MB5), r2 는 타입 폐포로 고쳤지만 **모집단이 `@Bean`
 * 메서드 서명**이라 네 꼴이 그대로 지나갔다(MF2 실측) — 설정 class **생성자 주입** · **중첩
 * `@Configuration`** · 소거된 **제네릭 인자** · 컨텍스트 **조회**(`getBeansOfType`). 지금은 ArchUnit 의
 * **의존 관계**로 센다: 생성자 · 필드 · 메서드 서명 · 제네릭 타입 인자 · class 리터럴 · 호출이 모두 한
 * 술어에 들어오고, 중첩 class 가 모집단에서 빠지지 않는다.
 *
 * 판정 조립(`SuggestionWiring` · `EvaluationWiring`)을 **이름으로 열거하지 않는다** — 기본이 거부라서
 * 판정 쪽에 V22 를 넣으려면 이 게이트를 먼저 고쳐야 한다.
 *
 * **재지 않는 것**(실측 범위):
 * ⓐ **실행 시 이름으로 조립한 길** — `Class.forName("bidvector.workflow.profile.…")` 처럼 class 리터럴을
 *   남기지 않는 꼴은 의존 그래프에 없다. 2a 의 격리 게이트가 상수 풀의 **점 표기**를 보는 것이 그 자리다.
 * ⓑ `app.wiring` **밖**의 조립 — `app` 의 다른 패키지가 V22 를 쥐는 꼴은 `AppHttpDependencyGateTest`
 *   (허용 목록 밖 참조 · 주입 표면 등식)와 `ArchitectureGateTest` 가 잡는다(r2 v L-c 의 실측 귀속 —
 *   앞 판 KDoc 은 표면 census 를 가리켰고, 그것은 HTTP handler · filter · servlet 만 거둔다).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfileAssemblyBoundaryTest {
    private val wiring: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TEST_FIXTURES)
            .importPackages(WIRING_PACKAGE)

    @Test
    fun `V22 타입에 의존하는 조립은 ProfileWiring 하나다`() {
        wiring.size shouldBeGreaterThan 0 // 모집단이 비면 이 게이트는 아무것도 막지 않는다.

        profileAssemblyRule().check(wiring)
    }

    /**
     * 음성 대조 — 술어가 **아닌 것을 아니라고** 하는지 실제 타입으로 고정한다(판정 port · DataSource ·
     * String). 양성 쪽은 아래 두 test 가 **규칙을 돌려** 잰다.
     */
    @Test
    fun `술어는 V22 밖 타입을 세지 않는다 — 음성 대조`() {
        val probes =
            ClassFileImporter().importClasses(
                OperatorProfilePort::class.java,
                DataSource::class.java,
                String::class.java,
            )

        carriesProfileType.test(probes.get(OperatorProfilePort::class.java)) shouldBe false
        carriesProfileType.test(probes.get(DataSource::class.java)) shouldBe false
        carriesProfileType.test(probes.get(String::class.java)) shouldBe false
    }

    /**
     * 양성 대조 ① — **구현 타입으로 감싼 길**에 같은 규칙을 돌려 위반이 나는지 본다. r1 의 이름 접두
     * 술어가 놓친 자리다(어댑터 구현 타입은 이름이 `workflow.profile.` 로 시작하지 않는다).
     */
    @Test
    fun `규칙은 어댑터 구현 타입으로 감싼 의존을 잡는다 — 양성 대조`() {
        val fixture = ClassFileImporter().importClasses(ImplProbeFixture::class.java)

        val violations = profileAssemblyRule().evaluate(fixture).failureReport.details

        violations.shouldNotBeEmpty()
        violations.all { it.contains(JdbcBusinessProfileStore::class.java.name) } shouldBe true
    }

    /**
     * 양성 대조 ② — **배열 성분**으로 감싼 의존에 같은 규칙을 돌린다. 배열 class 는 패키지가 없어
     * 이름 비교가 지나가고, 술어가 성분 타입으로 내려가지 않으면 이 대조가 붉다(r2 CR L-2).
     *
     * 배열 타입을 `importClasses` 로는 집을 수 없어서(배열에는 class 파일이 없다) 배열을 **서명에 가진**
     * fixture 를 들여온다. fixture 는 test 소스라 본 규칙의 모집단에 들지 않는다(`DoNotIncludeTests`).
     */
    @Test
    fun `규칙은 배열 성분으로 감싼 의존을 잡는다 — 양성 대조`() {
        val fixture = ClassFileImporter().importClasses(ArrayProbeFixture::class.java)

        val violations = profileAssemblyRule().evaluate(fixture).failureReport.details

        // **줄 수를 단언하지 않는다**: ArchUnit 은 배열 필드 하나에 위반 줄을 둘 낸다(배열 타입과 성분
        // 타입을 따로 센다 — 실측). 그 셈은 도구의 보고 단위이고, 여기서 잠그는 것은 「이 fixture 가
        // 위반이고 그 줄이 V22 port 를 가리킨다」다.
        violations.shouldNotBeEmpty()
        violations.all { it.contains(BusinessProfileStorePort::class.java.name) } shouldBe true
    }
}

/**
 * 이 게이트의 규칙 그 자체 — 세 test(출하 모집단 · 양성 대조 둘)가 **같은 규칙**을 돌린다. 규칙을 두 벌
 * 적으면 대조가 다른 술어를 재게 된다.
 *
 * `val` 이 아니라 함수인 이유: 최상위 프로퍼티는 **파일 순서로** 초기화되므로 아래 [carriesProfileType]
 * 보다 위에 두면 초기화 시점에 비어 있다(실측: 컴파일 오류). 순서에 기대지 않는 쪽을 고른다.
 */
private fun profileAssemblyRule(): ArchRule =
    noClasses()
        .that()
        .resideInAPackage("$WIRING_PACKAGE..")
        .and(DescribedPredicate.not(JavaClass.Predicates.belongToAnyOf(ProfileWiring::class.java)))
        .should()
        .dependOnClassesThat(carriesProfileType)
        .because("V22 를 조립해도 되는 자리는 ProfileWiring 뿐이다 — 판정 쪽으로 건너가는 길을 여기서 막는다")

/** 양성 대조 fixture ① — V22 port 의 **구현 타입**을 서명에 가진다(이름은 `adapters` 쪽이다). */
private class ImplProbeFixture {
    @JvmField var store: JdbcBusinessProfileStore? = null
}

/** 양성 대조 fixture ② — V22 port 의 **배열**을 서명에 가진다(배열 class 에는 패키지가 없다). */
private class ArrayProbeFixture {
    @JvmField var ports: Array<BusinessProfileStorePort>? = null

    @JvmField var names: Array<String>? = null
}

/**
 * 대상 타입 자신이나 그 **상위 타입**(클래스 · 인터페이스 전이)이 V22 패키지의 선언인가. 구현 타입으로
 * 감싸 넘기는 길을 같은 술어로 잡으려고 상위 타입까지 본다.
 *
 * **배열은 성분 타입으로 내려가서 본다**(r2 CR L-2) — `Array<BusinessProfileReader>` 는 Spring 이 실제로
 * 주입하는 길인데 배열 class 자체는 패키지가 없어 이름 비교가 지나간다.
 */
private val carriesProfileType =
    object : DescribedPredicate<JavaClass>("$PROFILE_PACKAGE 의 선언이거나 그 하위 타입(배열은 성분 타입)") {
        override fun test(target: JavaClass): Boolean {
            val resolved = target.baseComponentType
            return (listOf(resolved) + resolved.allRawSuperclasses + resolved.allRawInterfaces)
                .any { it.packageName == PROFILE_PACKAGE }
        }
    }
