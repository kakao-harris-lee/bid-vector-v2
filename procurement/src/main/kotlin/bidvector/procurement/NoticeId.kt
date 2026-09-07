package bidvector.procurement

import bidvector.sharedkernel.NoticeRound

/**
 * 공고번호(`bidNtceNo`) — KONEPS 의 권위 유니크 키(COL-05). 정규화 규칙은 이 값 객체가
 * 소유한다(legacy `normalize_notice_number` 를 호출부마다 부르는 형태를 채택하지 않는다).
 *
 * **정규화(v2-defect 023 수정, 3A 잔여 일괄 verifier r3 전) — trim + ASCII 대문자화 +
 * 내부 ASCII 공백 연속을 `-`로 합친다.** legacy `normalize_notice_number`(`parsing.py`,
 * 심볼 `normalize_notice_number` — legacy 좌표는 파일+심볼로만 가리킨다, 줄 번호를 쓰지
 * 않는다: `policy-values.md` §0, `re.sub(r"\s+", "", str(v).strip().upper())`)는 내부
 * 공백을 **제거**하는데, koneps-collection-023 이 요구하는 정규형은 공백을 구분자(`-`)로
 * **치환**한다 — 팀리드 결정(2026-09-07, 「구분자 처리는 case 기대값대로」)으로 legacy
 * 리터럴 대신 이 case 의 기대값(`"SYN NTC 2301"` → `"SYN-NTC-2301"`)을 따른다. **의도적으로
 * 넓힌 정규화**라는 것을 KDoc 에 남긴다 — 대소문자·공백 표기 차이만 흡수하고, 자릿수·글자
 * 자체를 바꾸지 않으므로 `source_url` 동일성 불변식(같은 source_url ⇒ 같은 notice_number)은
 * 여전히 성립한다(그 불변식은 「같은 원문이 같은 결과로 결정론적으로 사상된다」는 성질만
 * 요구한다).
 *
 * **ASCII 한정이다(verifier r3 N3-7)** — 대문자화([asciiUppercase])와 공백 합침
 * (`WHITESPACE_RUN = Regex("\\s+")`, Java `\s` 는 기본이 ASCII 전용) 둘 다 ASCII 밖은
 * 건드리지 않는다. 전각(ideographic) 공백 `U+3000`·기타 비 ASCII 공백은 `-`로 합쳐지지
 * 않아 `"SYN　NTC"`(전각)와 `"SYN NTC"`(반각)는 **다른** `NoticeNumber`로 남는다. 한글
 * 문자 자체는 대소문자가 없어 대문자화 축의 대상이 아니다. 공고번호 형식이 문서상
 * 라틴·숫자·구분자뿐이라(policy-values.md §1.1) 운영 위험은 낮다고 판단했다 — 확장이
 * 필요해지면 별도 결정으로 넓힌다.
 */
data class NoticeNumber(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "NoticeNumber는 빈 문자열일 수 없다" }
        require(value == value.trim()) { "NoticeNumber는 정규화된(trim) 형태여야 한다: '$value'" }
    }

    companion object {
        private val WHITESPACE_RUN = Regex("\\s+")
        private const val ASCII_LOWER_TO_UPPER_OFFSET = 'A' - 'a'

        /**
         * ASCII `a`~`z`만 대문자화한다 — `String.uppercase()`(인자 없음)는 컴파일된 바이트코드가
         * `java.util.Locale.ROOT`를 참조해 domain 허용 목록 밖이다(실측:
         * `ArchitectureGateTest` 실패, `Locale`은 `java.util` 허용 예외 62종에 없다).
         * `LicensePolicy.kt`의 `stripAndLowercase`와 같은 이유·같은 기법(CharArray 직접
         * 조립)이다 — 공고번호는 숫자·라틴 알파벳·구분자만 쓴다(한글은 대소문자가 없다).
         */
        private fun asciiUppercase(value: String): String {
            val chars = CharArray(value.length)
            for (i in value.indices) {
                val c = value[i]
                chars[i] = if (c in 'a'..'z') c + ASCII_LOWER_TO_UPPER_OFFSET else c
            }
            return String(chars)
        }

        /** 원문 공고번호 문자열에서 정규화한다 — 정규화 규칙은 여기 하나뿐이다(COL-05). */
        fun of(raw: String): NoticeNumber = NoticeNumber(asciiUppercase(raw.trim()).replace(WHITESPACE_RUN, "-"))
    }
}

/**
 * 공고 식별자 — 공고번호와 차수의 쌍(③, COL-05). 같은 공고번호라도 차수(재공고)가 다르면
 * 다른 공고다(`data-dictionary.md` §2.2.1 `Renoticed`).
 */
data class NoticeId(
    val number: NoticeNumber,
    val round: NoticeRound,
)
