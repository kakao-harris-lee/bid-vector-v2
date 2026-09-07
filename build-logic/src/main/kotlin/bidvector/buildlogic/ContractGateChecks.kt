package bidvector.buildlogic

import java.io.File
import java.security.MessageDigest

/*
 * M2/2D `ContractGateTask`의 판정 로직 — 전부 순수 함수다(`CpdReportPresenceGateTask`의
 * `cpdReportPresenceViolation`과 같은 관례). Task 는 외부 프로세스(`buf`·gradle)를 실행하고
 * 그 결과를 여기 넘기기만 한다 — 프로세스 실행 자체는 build-logic test 로 재현하지 않는다.
 */

/**
 * (a) 외부 도구가 보고한 버전이 정책 리터럴과 다르면 위반. `buf --version`은 줄바꿈 없는 순수
 * 버전 문자열을 낸다 — trim 만 하고 파싱하지 않는다(버전 형식을 이 게이트가 해석하지 않는다).
 */
internal fun toolVersionViolation(
    tool: String,
    actual: String,
    expected: String,
): String? {
    val trimmed = actual.trim()
    return if (trimmed == expected) {
        null
    } else {
        "$tool 버전이 정책과 다르다 — 실측 '$trimmed', 정책 '$expected'"
    }
}

/** (b)(c) `buf lint`·`buf breaking` 프로세스 결과 — exit code 0 이 아니면 위반(도구 자신의
 * 진단 메시지를 그대로 실어 원인을 보존한다). */
internal fun bufProcessViolation(
    step: String,
    exitCode: Int,
    output: String,
): String? =
    if (exitCode == 0) {
        null
    } else {
        "$step 실패(exit=$exitCode):\n$output"
    }

/**
 * (d) `generateProto`를 두 번 실행한 산출물이 바이트 단위로 같은지 — 결정성 실측. 파일
 * 집합(상대 경로)이 다르거나 내용 해시가 다르면 위반.
 *
 * **verifier r1 F-1(high) — 빈 집합 둘도 위반이다.** 산출물 경로가 바뀌거나(플러그인 상향
 * 등) `hashDirectoryContents`가 디렉터리 부재로 `emptyMap()`을 두 번 내면, 이전 판은
 * "집합이 같고 내용도 같다"로 읽어 조용히 통과했다 — 아무것도 안 재고 통과하는 것과
 * "결정적으로 같다"를 구분하지 못했다. 빈 집합은 애초에 잴 것이 없다는 사실이지 결정성의
 * 증거가 아니다.
 */
internal fun generationDeterminismViolation(
    first: Map<String, String>,
    second: Map<String, String>,
): String? =
    when {
        first.isEmpty() && second.isEmpty() -> {
            "두 번의 generateProto 산출물이 모두 비어 있다 — 결정성을 잴 대상이 없다(산출물 경로가 바뀌었는지 확인하라)"
        }

        first.keys != second.keys -> {
            val onlyFirst = first.keys - second.keys
            val onlySecond = second.keys - first.keys
            "두 번의 generateProto 산출물 파일 집합이 다르다 — 1회차에만: $onlyFirst, 2회차에만: $onlySecond"
        }

        else -> {
            val mismatched = first.keys.filter { first[it] != second[it] }.sorted()
            mismatched
                .takeIf { it.isNotEmpty() }
                ?.let { "두 번의 generateProto 산출물이 바이트 단위로 다르다(결정성 위반) — $it" }
        }
    }

/** [generationDeterminismViolation]에 넘길 입력 — 디렉터리 아래 전 파일의 상대경로→sha256 hex. */
internal fun hashDirectoryContents(root: File): Map<String, String> {
    if (!root.isDirectory) return emptyMap()
    val digest = MessageDigest.getInstance("SHA-256")
    return root
        .walkTopDown()
        .filter { it.isFile }
        .associate { file ->
            file.relativeTo(root).path to
                digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        }
}

/** (e) `git status --porcelain -- <경로...>` 출력이 비어있지 않으면 위반 — 생성물·계약이
 * 게이트 실행 중 작업 트리를 더럽히면 안 된다(비커밋 실측). */
internal fun uncleanWorkingTreeViolation(
    paths: List<String>,
    porcelainOutput: String,
): String? =
    if (porcelainOutput.isBlank()) {
        null
    } else {
        "작업 트리가 비어있지 않다(${paths.joinToString()}) — 생성·게이트 실행이 흔적을 남겼다:\n$porcelainOutput"
    }

/**
 * (f) included build 에 손으로 쓴 소스가 없는지 — `ml-contract/src` 부재 + 최상위 **파일**이
 * `expectedTopLevelFiles`(빌드 파일 셋)뿐. 디렉터리(`.gradle`·`.kotlin`·`build`)는 파일 집합
 * 검사 대상이 아니다 — 그것들은 산출물·캐시이지 손으로 추가한 소스 자리가 아니다.
 */
internal fun includedBuildSourcePresenceViolation(
    srcDirectoryExists: Boolean,
    topLevelFileNames: Set<String>,
    expectedTopLevelFiles: Set<String>,
): String? {
    if (srcDirectoryExists) {
        return "ml-contract/src 가 존재한다 — included build 는 손으로 쓴 소스를 가질 수 없다(2A D-2A-0 (c))"
    }
    val unexpected = topLevelFileNames - expectedTopLevelFiles
    return if (unexpected.isEmpty()) {
        null
    } else {
        "ml-contract 최상위에 빌드 파일 셋 밖의 파일이 있다 — $unexpected(무소스 단언 위반, OPEN-2A-INCLUDED-BUILD)"
    }
}
