package bidvector.adapters.evaluation

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/**
 * **같은 허용 뿌리 목록이 두 층에 살아 어긋났던 자리를 잰다**(M9/gates-build-followups, 리뷰 cr L-3).
 *
 * evaluation 축의 허용 뿌리는 두 벌 있다 — 바이트코드 층([ALLOWED_ROOTS], 상수 풀을 훑는
 * [EvaluationAdapterDependencyTest])과 이름 층(`architecture-policy.properties` 의
 * `source.scoped-allowed-roots.bidvector.adapters.evaluation`, PSI 를 보는
 * `domainSourceReferenceGate`). 두 층은 **같은 규칙의 다른 표현**이고 분담 이유는 `const val` 인라인
 * 사각이다(인라인되면 상수 풀에서 사라지지만 이름은 소스에 남는다). 목록이 갈리면 그 분담이 깨진다.
 *
 * **그 어긋남이 2026-11 여덟째 병합에서 실제로 일어났다** — 상류가 바이트코드 층에만 뿌리를 더해
 * 이름 층 게이트가 붉었다. 그쪽 방향은 게이트가 잡아 주지만 **반대 방향은 아무것도 붉지 않는다**:
 * 이름 층 정책만 넓어지면 두 층 가운데 넓은 쪽이 조용히 넓어진 채로 남는다. 그래서 이 단언은 포함이
 * 아니라 **집합 등식**이다.
 *
 * 등식이 **공허해지는 길 둘도 함께 막는다** — ① 축이 `source.scoped-packages` 모집단에서 빠지면
 * 이름 층은 이 패키지를 아예 판정하지 않으므로 등식이 「판정하지 않는 목록」에 대한 참이 된다.
 * ② 어느 한쪽이 비면 「둘 다 비었다」가 등식을 만족시킨다.
 *
 * 술어 자체의 동치(`fqn == 뿌리 || fqn.startsWith("뿌리.")`)는 이 test 가 아니라 두 층의 술어 test 가
 * 든다 — 바이트코드 층은 [EvaluationAdapterDependencyTest] 의 양성 대조, 이름 층은 build-logic 의
 * `SourceReferencePolicyTest`(알림함 어댑터 뿌리 거부 단언)다.
 *
 * 남은 일곱 축(`audit`·`notice`·`opening`·`profile`·`qualification`·`relay`·`strategy`)에는 아직 같은
 * 등식이 없다 — 각 축의 `ALLOWED_ROOTS` 가 그 파일 안에만 있어 여는 편집이 이 slice 의 in_scope 밖이다
 * (`OPEN-SCOPED-ROOTS-PARITY-REST`). 이 test 를 그 축으로 넓히는 비용은 축마다 한 단어다.
 */
class EvaluationAllowedRootsParityTest {
    @Test
    fun `이름 층 정책의 evaluation 축 허용 뿌리는 바이트코드 층 목록과 같은 집합이다`() {
        val policy = readPolicy()

        // ① 축이 이름 층의 판정 모집단에 들어 있다 — 그래야 이 등식이 실제 판정 대상을 말한다.
        policy.list(SCOPED_PACKAGES) shouldContain SCOPE

        val nameLayerRoots = policy.list(SCOPED_ROOTS_PREFIX + SCOPE).toSet()
        // ② 어느 쪽도 비어 있지 않다 — 빈 집합끼리의 등식은 아무 범위도 잠그지 않는다.
        nameLayerRoots.shouldNotBeEmpty()
        ALLOWED_ROOTS.shouldNotBeEmpty()

        nameLayerRoots shouldBe ALLOWED_ROOTS
    }

    private companion object {
        const val SCOPE = "bidvector.adapters.evaluation"
        const val SCOPED_PACKAGES = "source.scoped-packages"
        const val SCOPED_ROOTS_PREFIX = "source.scoped-allowed-roots."
        const val LOCATION_PROPERTY = "bidvector.architecture.policy"

        /**
         * 경로는 빌드가 넘긴다(`adapters/build.gradle.kts` 의 선언 입력 + system property, 이 모듈의
         * `bidvector.contracts.policy` 관례와 같은 형태). 작업 디렉터리 상대 경로에 기대면 실행 위치가
         * 바뀔 때 조용히 깨지고, 선언 입력이 아니면 정책만 고친 뒤 `:adapters:test` 가 UP-TO-DATE 로
         * 건너뛰어 이 등식이 **돌지 않은 채 초록**이 된다.
         */
        fun readPolicy(): Map<String, String> {
            val path =
                System.getProperty(LOCATION_PROPERTY)
                    ?: error("시스템 속성 '$LOCATION_PROPERTY' 가 없다 — adapters/build.gradle.kts 가 넘긴다")
            val file = File(path)
            check(file.isFile) { "아키텍처 정책 파일을 찾지 못했다: ${file.absolutePath}" }
            return file.reader(Charsets.UTF_8).use { reader ->
                Properties()
                    .apply { load(reader) }
                    .entries
                    .associate { (key, value) -> key.toString() to value.toString() }
            }
        }

        /** build-logic `requireList` 와 같은 분해다 — 줄 이음은 `Properties` 가 이미 푼다. */
        fun Map<String, String>.list(key: String): List<String> =
            (this[key] ?: error("정책 키 '$key' 가 없다"))
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
    }
}
