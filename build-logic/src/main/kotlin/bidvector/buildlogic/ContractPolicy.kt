package bidvector.buildlogic

import java.io.File

/**
 * `contract-policy.properties`의 값 해석 — M2/2D `contractGate`·breaking mutation 증명·
 * pytest 도구 버전 대조가 공유하는 유일한 읽기 지점(`DuplicatePolicy`와 같은 관례). 기본값이
 * 없다 — 키 부재는 정책 오류다.
 */
internal class ContractPolicy(
    private val values: Map<String, String>,
) {
    val approvedTagPattern: Regex get() = Regex(values.requireValue("approved.tag.pattern"))
    val approvedTag: String get() = values.requireValue("approved.tag")
    val maxMessageBytes: Int get() = values.requireInt("max.message.bytes")
    val breakingMutations: List<String> get() = values.requireList("breaking.mutations")
    val breakingMutationsMin: Int get() = values.requireInt("breaking.mutations.min")
    val bufVersion: String get() = values.requireValue("tool.buf.version")
    val protocVersion: String get() = values.requireValue("tool.protoc")
    val protocGenGrpcKotlinVersion: String get() = values.requireValue("tool.protoc.gen.grpc.kotlin")

    // verifier r1 F-13 — 생성물의 Java/gRPC 절반을 만드는 플러그인. `grpc-java` 카탈로그
    // 버전과 1:1 로 묶여 있다(`ml-contract/build.gradle.kts`).
    val protocGenGrpcJavaVersion: String get() = values.requireValue("tool.protoc.gen.grpc.java")

    companion object {
        fun load(file: File): ContractPolicy = ContractPolicy(readPolicy(file))
    }
}

/** 승인 태그 이름이 정책의 정규식을 만족하는지 — 임의 브랜치·커밋이 "승인 태그" 행세를 못 하게. */
internal fun approvedTagViolation(
    tag: String,
    pattern: Regex,
): String? =
    if (pattern.matches(tag)) {
        null
    } else {
        "승인 태그 '$tag' 가 정책 이름 규칙 '${pattern.pattern}' 을 만족하지 않는다"
    }
