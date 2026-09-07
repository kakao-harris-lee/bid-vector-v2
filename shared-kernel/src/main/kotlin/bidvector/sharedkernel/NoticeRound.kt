package bidvector.sharedkernel

/**
 * 공고 차수(KONEPS `bidNtceOrd`) — **제로패딩 3자리 숫자 문자열**(`"000"`·`"001"` …) 그대로
 * 나른다. `Int` 변환은 두지 않는다 — legacy가 이 값을 정수로 접어 1차 공고(`"000"`) 전부의
 * 표적조회 자격을 잃은 회귀가 있다(`R-QUAL-05`, `data-dictionary.md` §5.3 "식별자는
 * 숫자가 아니다"). 등가성은 원문 문자열의 구조적 동등이다 — `"0"`과 `"000"`은 이 타입
 * 밖에서 만들 수 없다(형식 위반은 생성 실패이지 정규화 대상이 아니다).
 *
 * 3자리 제로패딩 형식은 KONEPS `bidNtceOrd` 관측 사실이라 값 객체 KDoc에 근거를 싣는다 —
 * 정책값이 아니다(매직넘버 금지 원칙과 다른 축, M3/3A 설계 검토 「구현 지침」).
 */
data class NoticeRound(
    val value: String,
) {
    init {
        require(ZERO_PADDED_FORMAT.matches(value)) {
            "NoticeRound는 제로패딩 3자리 숫자여야 한다(KONEPS bidNtceOrd, R-QUAL-05): $value"
        }
    }

    companion object {
        private val ZERO_PADDED_FORMAT = Regex("\\d{3}")

        fun of(raw: String): NoticeRound = NoticeRound(raw)
    }
}
