package bidvector.adapters.architecture

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties
import bidvector.adapters.audit.ALLOWED_ROOTS as AUDIT_ROOTS
import bidvector.adapters.evaluation.ALLOWED_ROOTS as EVALUATION_ROOTS
import bidvector.adapters.notice.ALLOWED_ROOTS as NOTICE_ROOTS
import bidvector.adapters.opening.ALLOWED_ROOTS as OPENING_ROOTS
import bidvector.adapters.profile.ALLOWED_ROOTS as PROFILE_ROOTS
import bidvector.adapters.qualification.ALLOWED_ROOTS as QUALIFICATION_ROOTS
import bidvector.adapters.relay.ALLOWED_ROOTS as RELAY_ROOTS
import bidvector.adapters.strategy.ALLOWED_ROOTS as STRATEGY_ROOTS

/**
 * **같은 허용 뿌리 목록이 두 층에 살아 어긋나는 자리를 축 전부에서 잰다**(`OPEN-SCOPED-ROOTS-PARITY-REST`).
 *
 * 범위 축의 허용 뿌리는 두 벌 있다 — 바이트코드 층(각 축 dependency test 의 `ALLOWED_ROOTS`, 상수 풀을
 * `javap` 로 훑는다)과 이름 층(`architecture-policy.properties` 의 `source.scoped-allowed-roots.<축>`, PSI 를
 * 보는 `domainSourceReferenceGate`). 두 층은 **같은 규칙의 다른 표현**이고 분담 이유는 `const val` 인라인
 * 사각이다(인라인되면 상수 풀에서 사라지지만 이름은 소스에 남는다). 목록이 갈리면 그 분담이 깨진다.
 *
 * **그 어긋남이 2026-11 여덟째 병합에서 실제로 일어났다** — 상류가 바이트코드 층에만 뿌리를 더해 이름 층
 * 게이트가 붉었다. 그쪽 방향은 게이트가 잡아 주지만 **반대 방향은 아무것도 붉지 않는다**: 이름 층 정책만
 * 넓어지면 두 층 가운데 넓은 쪽이 조용히 넓어진 채로 남는다(이 slice 가 음성 대조로 재현했다 — 그 뿌리를
 * 더해도 정책 키를 읽는 자리 전수가 초록이었다). 그래서 단언은 포함이 아니라 **집합 등식**이다.
 *
 * **앞 판보다 주장이 넓다.** 앞 판은 축 하나(`evaluation`)를 손으로 적은 **열거**라 아홉째 축이 생기면
 * 조용했다. 이 판은 축 → 바이트코드 층 목록 [BYTECODE_LAYER_ROOTS] 하나를 두고 **그 key 집합과
 * `source.scoped-packages` 를 집합 등식으로** 잰다 — 축을 더하거나 빼면 그 등식이 깨진다. registry 의 값은
 * 문자열이 아니라 **컴파일되는 심볼 참조**이므로 어느 축의 `ALLOWED_ROOTS` 를 지우거나 이름을 바꾸면
 * 컴파일이 깨진다(CLAUDE.md 「게이트 술어는 문자열이 아니라 구조로 닫는다」).
 *
 * 등식이 **공허해지는 길들도 함께 막는다** — ① 축이 `source.scoped-packages` 모집단에서 빠지면 이름 층은
 * 그 패키지를 아예 판정하지 않으므로 등식이 「판정하지 않는 목록」에 대한 참이 된다 ② 어느 한쪽이 비면
 * 「둘 다 비었다」가 등식을 만족시킨다 ③ 모집단 자체가 비면 아무 범위도 잠기지 않는다.
 *
 * 술어 자체의 동치(`fqn == 뿌리 || fqn.startsWith("뿌리.")`)는 이 test 가 아니라 두 층의 술어 test 가 든다 —
 * 바이트코드 층은 각 축 dependency test 의 양성 대조, 이름 층은 build-logic 의 `SourceReferencePolicyTest`다.
 * **두 목록이 「옳은가」도 이 test 의 대상이 아니다** — 둘이 함께 넓어지면 조용하고, 그 자리는 ADR·리뷰가 든다.
 */
class ScopedAllowedRootsParityTest {
    @Test
    fun `이름 층이 판정하는 축 집합은 바이트코드 층 목록을 가진 축 집합과 같다`() {
        val scopes = readPolicy().list(SCOPED_PACKAGES)

        // 모집단이 비면 아래 축별 등식이 아무 범위도 잠그지 않는다.
        scopes.shouldNotBeEmpty()

        scopes.toSet() shouldBe BYTECODE_LAYER_ROOTS.keys
    }

    @Test
    fun `축마다 두 층의 허용 뿌리가 같은 집합이다`() {
        val policy = readPolicy()
        val nameLayer = BYTECODE_LAYER_ROOTS.keys.associateWith { policy.list(SCOPED_ROOTS_PREFIX + it).toSet() }

        // 빈 집합끼리의 등식은 아무 범위도 잠그지 않는다 — 어느 축의 어느 층도 비어 있지 않다.
        val emptySides =
            BYTECODE_LAYER_ROOTS.keys.filter {
                nameLayer.getValue(it).isEmpty() || BYTECODE_LAYER_ROOTS.getValue(it).isEmpty()
            }
        emptySides shouldBe emptyList()

        // 축마다 `shouldBe` 를 루프로 돌리지 않는다 — kotest hard assertion 은 첫 불일치에서 멈춰
        // 나머지 축의 어긋남을 가린다(`CleanMigrationCheckTest` 의 표 단위 map 등식과 같은 꼴).
        nameLayer shouldBe BYTECODE_LAYER_ROOTS
    }

    /**
     * **최장 접두 사각.** `SourceReferencePolicy.scopeOf` 는 패키지를 품는 축 가운데 **가장 긴 것**을 고른다.
     * 어느 축이 다른 축의 하위 패키지면 그 하위 축의 뿌리 목록이 상위 축의 것을 **가리고**, 상위 축의 등식은
     * 자기 key 만 보므로 조용히 초록으로 남는다 — 넓은 목록을 가진 하위 축 하나로 상위 축의 범위를 벗어난다.
     * 축끼리 서로 접두가 아니면 패키지마다 후보가 하나뿐이고, 그래서 위 축별 등식이 이름 층 판정 모집단
     * **전체**를 덮는다.
     */
    @Test
    fun `어느 축도 다른 축의 패키지 접두가 아니다 — 최장 접두 선택의 후보는 축마다 하나다`() {
        val scopes = BYTECODE_LAYER_ROOTS.keys

        val nested =
            scopes
                .flatMap { outer -> scopes.filter { it.startsWith("$outer.") }.map { inner -> "$outer < $inner" } }
                .sorted()

        nested shouldBe emptyList()
    }

    private companion object {
        const val SCOPED_PACKAGES = "source.scoped-packages"
        const val SCOPED_ROOTS_PREFIX = "source.scoped-allowed-roots."
        const val LOCATION_PROPERTY = "bidvector.architecture.policy"

        /**
         * 축 → **바이트코드 층**의 허용 뿌리. 값은 각 축 dependency test 가 실제로 판정에 쓰는 그 집합
         * 자체이고(별칭 import 한 심볼), 그래서 목록을 세 번째로 적지 않는다. key 집합은 위 첫 test 가
         * `source.scoped-packages` 와 등식으로 묶으므로 이 표는 **열거가 아니라 구성**이다.
         */
        val BYTECODE_LAYER_ROOTS: Map<String, Set<String>> =
            mapOf(
                "bidvector.adapters.audit" to AUDIT_ROOTS,
                "bidvector.adapters.evaluation" to EVALUATION_ROOTS,
                "bidvector.adapters.notice" to NOTICE_ROOTS,
                "bidvector.adapters.opening" to OPENING_ROOTS,
                "bidvector.adapters.profile" to PROFILE_ROOTS,
                "bidvector.adapters.qualification" to QUALIFICATION_ROOTS,
                "bidvector.adapters.relay" to RELAY_ROOTS,
                "bidvector.adapters.strategy" to STRATEGY_ROOTS,
            )

        /**
         * 경로는 빌드가 넘긴다(`adapters/build.gradle.kts` 의 선언 입력 + system property, 이 모듈의
         * `bidvector.contracts.policy` 관례와 같은 형태). 작업 디렉터리 상대 경로에 기대면 실행 위치가
         * 바뀔 때 조용히 깨지고, 선언 입력이 아니면 정책만 고친 뒤 `:adapters:test` 가 UP-TO-DATE 로
         * 건너뛰어 이 등식들이 **돌지 않은 채 초록**이 된다(이 slice 가 음성 대조로 실측했다).
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
