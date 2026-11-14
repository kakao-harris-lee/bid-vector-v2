package bidvector.adapters.architecture

import java.io.File
import bidvector.adapters.audit.ALLOWED_ROOTS as AUDIT_ROOTS
import bidvector.adapters.evaluation.ALLOWED_ROOTS as EVALUATION_ROOTS
import bidvector.adapters.event.ALLOWED_ROOTS as EVENT_ROOTS
import bidvector.adapters.extraction.ALLOWED_ROOTS as EXTRACTION_ROOTS
import bidvector.adapters.koneps.ALLOWED_ROOTS as KONEPS_ROOTS
import bidvector.adapters.ml.ALLOWED_ROOTS as ML_ROOTS
import bidvector.adapters.notice.ALLOWED_ROOTS as NOTICE_ROOTS
import bidvector.adapters.notification.ALLOWED_ROOTS as NOTIFICATION_ROOTS
import bidvector.adapters.opening.ALLOWED_ROOTS as OPENING_ROOTS
import bidvector.adapters.persistence.ALLOWED_ROOTS as PERSISTENCE_ROOTS
import bidvector.adapters.profile.ALLOWED_ROOTS as PROFILE_ROOTS
import bidvector.adapters.qualification.ALLOWED_ROOTS as QUALIFICATION_ROOTS
import bidvector.adapters.relay.ALLOWED_ROOTS as RELAY_ROOTS
import bidvector.adapters.strategy.ALLOWED_ROOTS as STRATEGY_ROOTS

/**
 * **축 → 바이트코드 층 허용 뿌리, 그리고 그 축을 판정하는 데 필요한 자리들.**
 *
 * 앞 판에서 이 표는 `ScopedAllowedRootsParityTest` 의 `private companion` 에 살았고 **판정 입력이
 * 아니었다** — 판정은 축 test 열넷이 각자 자기 `ALLOWED_ROOTS` 를 호출 자리에 적어서 했다. 그 틈으로
 * 두 우회가 통과했다(M11 verifier r1 H-1): 판정을 같은 facade 의 **다른 이름**으로 옮기거나 **호출
 * 자리의 식**으로 옮기면 보유자는 여전히 하나로 보이고 표는 장식이 된다. 실측으로 둘 다 통과했다.
 *
 * **처방은 표를 판정 입력으로 만드는 것이다** — [ScopedBytecodeLayerGateTest] 의 **단일 test** 가
 * 이 표를 순회하며 판정하고, 축 test 에는 판정 호출이 **하나도 없다**. 그러면 다른 이름의 집합을
 * 선언해도 그것으로 판정하는 자리가 없다(`OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` ④).
 *
 * 표가 이 `object` 에 사는 것은 **소유자 앵커가 표를 담은 class 를 가리켜야** 하기 때문이다 — 두 층
 * 등식 test 의 네 번째·다섯 번째 단언은 「표의 값 심볼의 소유 class」를 **표를 담은 class 자신의
 * 상수 풀**에서 뽑는다. 표와 앵커는 그래서 함께 움직인다.
 */
internal object ScopedAllowedRoots {
    /**
     * 값은 각 축 dependency test 가 선언한 그 집합 **자체**(별칭 import 한 심볼)이고, 그래서 목록을
     * 세 번째로 적지 않는다. key 집합은 `source.scoped-packages` 와 집합 등식으로 묶이므로
     * (`ScopedAllowedRootsParityTest`) 이 표는 **열거가 아니라 구성**이다 — 축을 더하거나 빼면 그
     * 등식이 깨지고, 어느 축의 `ALLOWED_ROOTS` 를 지우거나 이름을 바꾸면 **컴파일**이 깨진다.
     */
    val BYTECODE_LAYER_ROOTS: Map<String, Set<String>> =
        mapOf(
            "bidvector.adapters.audit" to AUDIT_ROOTS,
            "bidvector.adapters.evaluation" to EVALUATION_ROOTS,
            "bidvector.adapters.event" to EVENT_ROOTS,
            "bidvector.adapters.extraction" to EXTRACTION_ROOTS,
            "bidvector.adapters.koneps" to KONEPS_ROOTS,
            "bidvector.adapters.ml" to ML_ROOTS,
            "bidvector.adapters.notice" to NOTICE_ROOTS,
            "bidvector.adapters.notification" to NOTIFICATION_ROOTS,
            "bidvector.adapters.opening" to OPENING_ROOTS,
            "bidvector.adapters.persistence" to PERSISTENCE_ROOTS,
            "bidvector.adapters.profile" to PROFILE_ROOTS,
            "bidvector.adapters.qualification" to QUALIFICATION_ROOTS,
            "bidvector.adapters.relay" to RELAY_ROOTS,
            "bidvector.adapters.strategy" to STRATEGY_ROOTS,
        )

    /**
     * 이 모듈의 패키지 뿌리 — 리터럴이 아니라 **이 object 가 사는 패키지의 앞 두 세그먼트**다.
     * 구성 표본의 앵커로 쓰이므로 리터럴로 적으면 그 앵커가 또 하나의 열거가 된다.
     */
    val OWN_MODULE_ROOT: String =
        ScopedAllowedRoots::class.java.packageName.split('.').take(2).joinToString(".")

    /**
     * 축 → 그 패키지의 컴파일 산출물 디렉터리. **경로 리터럴이 이 한 자리**다(앞 판은 축마다 하나로
     * 열넷이었다). 작업 디렉터리 상대 경로인 것은 앞 판과 같고, 없으면
     * [disallowedCoordinates] 가 명시 실패를 낸다.
     */
    fun classesDirOf(scope: String): File = File(MAIN_CLASSES_ROOT, scope.replace('.', '/'))

    /**
     * adapters main 의 **1급 패키지 전부** — 디렉터리 전수이므로 축이 **없는** 형제까지 든다. 구성
     * 표본이 「형제 adapter 패키지가 허용 밖이면 걸리는가」를 축마다 묻는 자리이고, 축 열넷이
     * adapters 의 패키지 전부가 아님(신설 OPEN)도 같은 값이 보여 준다.
     */
    fun adapterPackages(): Set<String> {
        val root = classesDirOf(OWN_MODULE_ROOT)
        check(root.isDirectory) {
            "빌드 산출물을 찾지 못했다: ${root.absolutePath} — :adapters:compileKotlin 선행 필요"
        }
        val packages =
            root
                .listFiles()
                .orEmpty()
                .filter(File::isDirectory)
                .map { "$OWN_MODULE_ROOT.${it.name}" }
                .toSet()
        check(packages.isNotEmpty()) { "adapters 의 1급 패키지를 찾지 못했다: ${root.absolutePath}" }
        return packages
    }

    private const val MAIN_CLASSES_ROOT = "build/classes/kotlin/main"
}
