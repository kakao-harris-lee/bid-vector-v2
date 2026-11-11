package bidvector.app.http

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * D-6A3-20 — 이 패키지의 계약·표면 test 들이 공유하는 YAML 로딩·스키마 조회 헬퍼(sizeGate 분리,
 * `PredictionContractTest`→`PredictionAdditiveContractTest` 선례와 같은 이유 — 파일당 500줄 한도,
 * v2-지침서 §5). 같은 코드를 복제하지 않도록 여기 한 곳에 모은다(v2-지침서 §5 「중복 금지」).
 *
 * 영향 범위를 적는 이유(code-review r4 L-5): 이 KDoc 은 「이 로더를 바꾸면 무엇이 영향을 받나」를
 * 보러 오는 자리다. 작게 적혀 있으면 과소평가하게 된다. **그래서 이름을 열거하지 않는다** — 앞 판은
 * 넷을 이름으로 들었고 그 목록은 소비자가 늘면서 조용히 낡았다(M10/10B 착수 실측: 열여덟). 지금
 * 쓰는 자리는 `loadOpenApiSpec` 호출부를 찾아 센다.
 */
@Suppress("UNCHECKED_CAST")
internal fun loadOpenApiSpec(): Map<String, Any?> {
    val specProperty =
        requireNotNull(System.getProperty("bidvector.openapi.spec")) {
            "bidvector.openapi.spec 시스템 프로퍼티가 없다"
        }
    return parseOpenApiSpec(File(specProperty).readText())
}

/**
 * **중복 키를 거부한다**(code-review r3 Q3 · 계약 D-7D-6). SnakeYAML 의 기본값은
 * `allowDuplicateKeys = true` 라, 같은 키가 둘 있으면 **뒤 값이 조용히 이긴다**.
 *
 * 그 침묵은 이 로더를 쓰는 **세 계약 test 의 집합 등식**을 비대칭으로 만든다: 중복이 목록을
 * 좁히면 등식이 RED 로 떨어지지만(fail-closed), 넓히거나 원래 선언을 살리면 **초록이다**.
 * 수정 r2 의 변이 M27 이 정확히 그 경우였다 — 이미 `security:` 를 가진 operation 에 둘째
 * `security:` 를 더했는데 원래 선언이 이겨 문서가 바뀌지 않았고, 변이가 초록이 됐다. 그래서
 * 그 라운드는 「문서에 선언을 **더하는** 변이는 판정 근거로 쓰지 않는다」는 **우회책**을 적었다.
 * 이 한 줄이 그 우회를 불필요하게 만든다.
 *
 * **같은 함정을 닫은 선례가 이 저장소에 있다** — `ArchitecturePolicy.duplicateKeys` 는
 * `java.util.Properties` 가 중복 키에서 마지막만 남기는 것을 원문 줄 계수로 잡는다. `.properties`
 * 쪽만 닫혀 있고 YAML 쪽은 열려 있던 상태를 여기서 맞춘다.
 *
 * 소비자 간 불일치도 함께 줄어든다: 같은 계약 파일을 읽는 `openapi-typescript`(js-yaml)는 중복
 * 키에서 **던진다**. 이제 두 소비자가 유효성을 같은 기준으로 본다.
 */
@Suppress("UNCHECKED_CAST")
internal fun parseOpenApiSpec(text: String): Map<String, Any?> =
    Yaml(LoaderOptions().apply { isAllowDuplicateKeys = false }).load<Map<String, Any?>>(text)

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.schema(name: String): Map<String, Any?> =
    ((this["components"] as Map<String, Any?>)["schemas"] as Map<String, Any?>)[name] as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.propertyKeys(schemaName: String): Set<String> =
    (schema(schemaName)["properties"] as Map<String, Any?>).keys

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.requiredKeys(schemaName: String): Set<String> =
    ((schema(schemaName)["required"] as? List<String>) ?: emptyList()).toSet()

/**
 * 스키마 속성의 **닫힌 어휘**. 선언이 없거나 비면 **던진다**.
 *
 * 읽기가 `null`·빈 목록을 답으로 내면 그 칸을 쓰는 집합 등식이 **공허하게 초록**이 된다 —
 * 어휘를 다시 `type: string` 으로 내리는 변이가 정확히 그 길이고, 그 변이는 생성 타입을 거쳐
 * web 의 전수 검사까지 함께 공허하게 만든다(M10/10B). 그래서 선언 부재를 답이 아니라 실패로 둔다.
 *
 * 세 계약 test class 가 같은 모양의 `private` 사본을 갖고 있다 — **넷째 사본을 만들지 않도록**
 * 여기 둔다(v2-지침서 §5 「중복 금지」). 그 셋을 이 자리로 모으는 것은 별 정리다.
 */
@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.propertyEnum(
    schemaName: String,
    property: String,
): List<String> {
    val properties = schema(schemaName)["properties"] as Map<String, Any?>
    val definition =
        requireNotNull(properties[property] as? Map<String, Any?>) {
            "$schemaName 의 $property 칸이 선언되지 않았다"
        }
    val values =
        requireNotNull(definition["enum"] as? List<String>) {
            "$schemaName 의 $property 칸이 enum 을 선언하지 않았다 — 닫힌 어휘가 열린 string 으로 돌아갔다"
        }
    require(values.isNotEmpty()) { "$schemaName 의 $property 칸 enum 이 비었다" }
    return values
}

/**
 * D-6A3-20 — `paths.<path>.<method>.responses` 의 상태
 * 코드 **키 집합**. 409·401 선언을 지우면 이 값을 쓰는 test 가 RED가
 * 된다 — HTTP 호출 없이 문서 자신만 본다.
 */
@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.responseStatusCodes(
    path: String,
    method: String,
): Set<String> {
    val pathItem = (this["paths"] as Map<String, Any?>)[path] as Map<String, Any?>
    val operation = pathItem[method] as Map<String, Any?>
    return (operation["responses"] as Map<String, Any?>).keys
}

/** D-6A1-30 — 값 자체가 object이거나(Map), 배열 어느 깊이에서든 object를 담으면 평탄하지 않다. */
internal fun containsNestedObject(value: Any?): Boolean =
    when (value) {
        is Map<*, *> -> true
        is List<*> -> value.any(::containsNestedObject)
        else -> false
    }
