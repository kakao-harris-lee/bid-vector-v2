package bidvector.adapters.contract

import java.io.File
import java.util.Properties

/**
 * M2/2D — `contract-policy.properties`를 test 에서 읽는 유일한 지점(매직값 금지, 정책
 * 표면 하나). build-logic 의 `ContractPolicy`는 `internal`이라 다른 모듈의
 * `build.gradle.kts`·test 에서 보이지 않는다 — `adapters/build.gradle.kts`가 정책 **파일
 * 경로**를 시스템 속성으로 넘기고(`ContractTestdataSupport.contractTestdataRoot()`와 같은
 * 관례), 여기서 그 파일을 직접 읽는다. 기본값이 없다 — 키 부재는 정책 오류다.
 */
private val contractPolicyProperties: Properties by lazy {
    val path =
        System.getProperty("bidvector.contracts.policy")
            ?: error("시스템 속성 'bidvector.contracts.policy' 가 없다 — 빌드가 넘긴다")
    File(path).reader(Charsets.UTF_8).use { reader -> Properties().apply { load(reader) } }
}

internal fun contractPolicyValue(key: String): String =
    contractPolicyProperties.getProperty(key) ?: error("정책 키 '$key' 가 없다")

internal fun contractPolicyInt(key: String): Int =
    contractPolicyValue(key).trim().toIntOrNull() ?: error("정책 키 '$key' 의 값이 정수가 아니다")
