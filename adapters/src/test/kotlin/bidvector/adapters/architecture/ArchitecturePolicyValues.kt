package bidvector.adapters.architecture

import java.io.File
import java.util.Properties

/**
 * **아키텍처 정책을 읽는 한 자리.** 앞 판은 `ScopedAllowedRootsParityTest` 의 companion 이 같은
 * 세 함수(`readPolicy`·`list`·키 상수)를 들고 있었고, 이 slice 가 그것을 쓰는 자리를 **셋**으로
 * 늘리면서(두 층 등식 · 축 판정 · 추출 층 표본) 복제가 될 참이었다.
 *
 * 경로는 빌드가 system property 로 넘기고 그 파일은 `:adapters:test` 의 **선언 입력**이다
 * (`adapters/build.gradle.kts`). 작업 디렉터리 상대 경로에 기대면 실행 위치가 바뀔 때 조용히 깨지고,
 * 선언 입력이 아니면 정책만 고친 뒤 task 가 `UP-TO-DATE` 로 건너뛰어 이 값을 읽는 단언들이
 * **돌지 않은 채 초록**이 된다(M11/11C 가 음성 대조로 실측한 자리).
 */
internal object ArchitecturePolicyValues {
    /** build-logic `requireList` 와 같은 분해다 — 줄 이음은 [Properties] 가 이미 푼다. */
    fun list(key: String): List<String> =
        value(key)
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)

    /**
     * 승인된 모듈의 **패키지 뿌리 전부** — 손 열거가 아니라 `layer.order` → `layer.<층>` →
     * `package.root` 도출이다. 모듈이 늘면 이 집합이 함께 늘고, 어느 축의 양성 표본도 「그때
     * 손으로 적은 뿌리 몇」에 머물지 않는다(`OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` ⑤).
     *
     * **모듈 이름 → 패키지 뿌리 규칙(`-` 제거)의 정본은 build-logic `PackageOwnershipPolicy` 의
     * `ownedPackage` 이고 이것이 세 번째 사본이다**(둘째는 `app` test 의 `ArchitecturePolicy`).
     * 사본이 틀리면 **조용하지 않다** — `-` 가 남은 이름은 좌표 정규식의 문자 집합에서 끊겨 표본
     * 자체가 깨진다. 규칙과 실물을 묶는 **모듈 전수 대조**는 `app` 의 `ArchitectureGateTest`
     * (「뿌리 아래 1급 패키지 == 승인된 모듈 집합」)가 같은 `check` 안에서 든다.
     */
    val moduleRoots: Set<String> by lazy {
        val root = value(PACKAGE_ROOT)
        list(LAYER_ORDER)
            .flatMap { layer -> list("$LAYER_PREFIX$layer") }
            .map { module -> "$root.${module.replace("-", "")}" }
            .toSet()
    }

    fun value(key: String): String = values[key] ?: error("아키텍처 정책에 '$key' 가 없다")

    const val SCOPED_PACKAGES = "source.scoped-packages"
    const val SCOPED_ROOTS_PREFIX = "source.scoped-allowed-roots."

    private const val PACKAGE_ROOT = "package.root"
    private const val LAYER_ORDER = "layer.order"
    private const val LAYER_PREFIX = "layer."
    private const val LOCATION_PROPERTY = "bidvector.architecture.policy"

    private val values: Map<String, String> by lazy {
        val path =
            System.getProperty(LOCATION_PROPERTY)
                ?: error("시스템 속성 '$LOCATION_PROPERTY' 가 없다 — adapters/build.gradle.kts 가 넘긴다")
        val file = File(path)
        check(file.isFile) { "아키텍처 정책 파일을 찾지 못했다: ${file.absolutePath}" }
        file.reader(Charsets.UTF_8).use { reader ->
            Properties()
                .apply { load(reader) }
                .entries
                .associate { (key, value) -> key.toString() to value.toString() }
        }
    }
}
