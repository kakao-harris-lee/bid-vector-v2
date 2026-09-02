package bidvector.buildlogic

import java.io.File
import java.util.Properties

/**
 * `config/quality` 아래의 properties 는 정책 **데이터**다 — 임계와 금지 목록을 빌드 스크립트에
 * 리터럴로 박지 않기 위한 자리이며 `policy.version` 으로 판을 구분한다.
 */
internal fun readPolicy(file: File): Map<String, String> =
    file.inputStream().use { stream ->
        Properties()
            .apply { load(stream) }
            .entries
            .associate { (key, value) -> key.toString() to value.toString() }
    }

internal fun Map<String, String>.requireInt(key: String): Int =
    requireValue(key).trim().toIntOrNull()
        ?: error("정책 키 '$key' 의 값이 정수가 아니다")

internal fun Map<String, String>.requireList(key: String): List<String> =
    requireValue(key)
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)

internal fun Map<String, String>.requireValue(key: String): String = this[key] ?: error("정책 키 '$key' 가 없다")
