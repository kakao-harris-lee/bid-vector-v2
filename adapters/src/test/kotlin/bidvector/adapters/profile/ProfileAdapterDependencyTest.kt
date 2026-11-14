package bidvector.adapters.profile

import bidvector.adapters.architecture.isOutsideRoots
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(D-6F6-6, `StrategyAdapterDependencyTest`·
 * `EvaluationAdapterDependencyTest`와 같은 형태·같은 이유). `profile` 패키지는
 * [bidvector.workflow.evaluation](`OperatorProfilePort`·`ProfileFacts`)·
 * [bidvector.qualification](`OperatorLicenses`·`LicenseName`)·[bidvector.strategy]
 * (`CategoryCode`)를 봐야 한다. `bidvector.adapters.persistence`는 domain이 아니라
 * **같은 모듈의 다른 adapter 패키지**다 — SQL 문(`Sql.SELECT_PROFILE` 등)과 바인딩 헬퍼
 * (`getTextList`·`setTextArray`)가 거기 산다(다른 형제 dependency test들의 allow-list를
 * 넓히지 않는다 — 이 test는 그것들과 별개다). `bidvector.adapters.profile` 자신은 이
 * 패키지의 클래스끼리(`JdbcOperatorProfileRepository` → `OperatorProfileRow`) 서로
 * 참조할 수 있어야 하므로 포함한다.
 *
 * M7/7C④ — V22 어댑터가 사업 정보 값 타입([bidvector.workflow.profile])과 주체 식별자
 * (`bidvector.workflow.access.AccountId`)를 본다. 이 test 의 단위는 **패키지**라 루트로 적는다.
 * **어느 class 가 그 좌표를 지녀도 되는지는 이 test 가 정하지 않는다** — `adapters` 모듈 전체에
 * 걸리는 `BusinessProfileIsolationGateTest` 가 `SourceFile` 속성으로 그 자리를 정한다(r2 F5 처분).
 */
internal val ALLOWED_ROOTS: Set<String> =
    java.util.Set.copyOf(
        setOf(
            "bidvector.workflow.evaluation",
            "bidvector.workflow.profile",
            "bidvector.workflow.access",
            "bidvector.qualification",
            "bidvector.strategy",
            "bidvector.adapters.persistence",
            "bidvector.adapters.profile",
        ),
    )

private fun isDisallowed(coordinate: String): Boolean = isOutsideRoots(coordinate, ALLOWED_ROOTS)

/**
 * **이 축의 판정은 이 class 에 없다** — [bidvector.adapters.architecture.ScopedBytecodeLayerGateTest] 가 표를 순회하며 한다(M12/12A ④).
 * 여기 남은 것은 이 축에만 있는 주장이고, 아래는 그 판정의 술어가 **상수 풀**인 이유다.
 *
 * D-6F6-6 — **소스 텍스트가 아니라 컴파일된 클래스의 상수 풀을 `javap -p -v`로 훑는다.**
 * import 문 없이 전체 한정 좌표로 직접 참조하는 우회(scope.md 우회 5)를 소스 텍스트 정규식
 * 형태는 못 본다(6F-1 verifier MUT-E3 실측) — 신설 게이트는 구조로 닫는 쪽만 쓴다(CLAUDE.md
 * 「게이트 술어는 문자열이 아니라 구조로」).
 *
 * **이 test 가 재는 것**: 이 패키지의 class 가 허용 루트 **밖**의 `bidvector` 좌표를 참조하지 않는다.
 * 재지 않는 것: 다른 패키지 · 모듈(이 test 의 모집단이 아니다 — V22 관심사는 모듈 전체에 걸리는
 * `BusinessProfileIsolationGateTest` 가 본다) · 값이 흐르는 경로 일반.
 */
class ProfileAdapterDependencyTest {
    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.workflow.evaluation") shouldBe false
        isDisallowed("bidvector.workflow.profile") shouldBe false
        isDisallowed("bidvector.qualification") shouldBe false
        isDisallowed("bidvector.strategy") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
        isDisallowed("bidvector.adapters.profile") shouldBe false
    }
}

