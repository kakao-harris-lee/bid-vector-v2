package bidvector.app.http

import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * D-6A3-20 — `OpenApiContractTest`·`OpenApiDryRunContractTest`가 공유하는 YAML 로딩·스키마
 * 조회 헬퍼(sizeGate 분리, `PredictionContractTest`→`PredictionAdditiveContractTest` 선례와
 * 같은 이유 — 파일당 500줄 한도, v2-지침서 §5). 두 test class 가 같은 코드를 복제하지
 * 않도록 여기 한 곳에 모은다(v2-지침서 §5 「중복 금지」).
 */
@Suppress("UNCHECKED_CAST")
internal fun loadOpenApiSpec(): Map<String, Any?> {
    val specProperty =
        requireNotNull(System.getProperty("bidvector.openapi.spec")) {
            "bidvector.openapi.spec 시스템 프로퍼티가 없다"
        }
    return Yaml().load<Map<String, Any?>>(File(specProperty).readText())
}

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
 * D-6A3-20(검토 라운드 1 contract-keeper R1) — `paths.<path>.<method>.responses` 의 상태
 * 코드 **키 집합**. verifier M7a/M7b/M7d(409·401 선언 삭제)가 이 값을 쓰는 test 를 RED로
 * 만든다 — HTTP 호출 없이 문서 자신만 본다.
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
