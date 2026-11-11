package bidvector.buildlogic

import java.io.File
import java.util.Properties

/**
 * `config/quality` 아래의 properties 는 정책 **데이터**다 — 임계와 금지 목록을 빌드 스크립트에
 * 리터럴로 박지 않기 위한 자리이며 `policy.version` 으로 판을 구분한다.
 *
 * **UTF-8 Reader 로 읽는다.** `Properties.load(InputStream)` 은 ISO-8859-1 이라 값에 한글을 쓰면
 * 깨진다 — 분류 사유가 값에 들어오면서 실제 문제가 됐다.
 */
internal fun readPolicy(file: File): Map<String, String> =
    file.reader(Charsets.UTF_8).use { reader ->
        Properties()
            .apply { load(reader) }
            .entries
            .associate { (key, value) -> key.toString() to value.toString() }
    }

internal fun Map<String, String>.requireInt(key: String): Int =
    requireValue(key).trim().toIntOrNull()
        ?: error("정책 키 '$key' 의 값이 정수가 아니다")

/** `true`·`false` 외의 값은 정책 오류다 — 알 수 없는 값을 거짓으로 읽으면 게이트가 조용히 느슨해진다. */
internal fun Map<String, String>.requireBoolean(key: String): Boolean =
    when (val raw = requireValue(key).trim()) {
        "true" -> true
        "false" -> false
        else -> error("정책 키 '$key' 의 값 '$raw' 이 true 도 false 도 아니다")
    }

internal fun Map<String, String>.requireList(key: String): List<String> =
    requireValue(key)
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)

/**
 * **빈 값은 「끈 것」이 아니라 정책 오류다.** [requireList] 는 빈 값에 빈 목록을 내므로, 모집단을 그
 * 목록에서 얻는 축은 키를 **지우면** 죽고 **비우면** 조용히 공허해진다(리뷰 M-2 — 그 공허가 다수
 * 모듈의 정상 상태와 같은 리포트를 낸다). 그 비대칭을 값을 쓰는 자리에서 끊는다.
 */
internal fun Map<String, String>.requireNonEmptyList(key: String): List<String> =
    requireList(key).ifEmpty { error("정책 키 '$key' 의 값이 비어 있다 — 이 축은 빈 목록으로 꺼지지 않는다") }

/**
 * **목록이되 항목이 꼭 하나**인 값. 배포 아카이브의 적재 자리(`Spring-Boot-Lib`·`Spring-Boot-Classes`)
 * 처럼 **하나만 선언될 수 있는 자리**를 그 목록에서 가져올 때 쓴다 — 같은 값을 다른 키에 복사하면
 * 둘이 갈라지는 날 게이트가 자기 전제를 잃고, 목록이 둘이 되면 「어느 쪽이 그 자리인가」가 판정 밖에서
 * 정해진다.
 */
internal fun Map<String, String>.requireSingle(key: String): String =
    requireNonEmptyList(key).singleOrNull()
        ?: error("정책 키 '$key' 는 항목이 하나여야 한다 — 이 값은 배포물이 **하나만** 선언할 수 있는 자리다")

internal fun Map<String, String>.requireValue(key: String): String = this[key] ?: error("정책 키 '$key' 가 없다")
