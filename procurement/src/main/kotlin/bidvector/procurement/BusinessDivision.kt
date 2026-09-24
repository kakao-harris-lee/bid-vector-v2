package bidvector.procurement

/**
 * 업무 대분류(D-6F9-1, M6/6F-9) — P-7 문서 열거 어휘 넷. **순서가 문서 표기 순서**다(왕복 비교·정책의
 * [DocumentedVocabulary]가 이 순서를 쓴다). 원천은 응답 필드가 아니라 **수집 오퍼레이션**이다 —
 * 공사 목록·용역 목록을 따로 부르므로 어느 오퍼레이션으로 받았는가가 곧 대분류다. 응답의 `bsnsDivNm`·
 * 코드+라벨 축([BusinessCategory])·낙찰정보 코드 축과 접지 않는다(P-7·`OPEN-COL-03`) — 각자 자기 칸이다.
 */
enum class BusinessDivision(
    val label: String,
) {
    GOODS("물품"),
    SERVICE("용역"),
    CONSTRUCTION("공사"),
    FOREIGN("외자"),
    ;

    companion object {
        /** 어휘 밖 라벨은 `null` — 지어내지도 던지지도 않는다(복원 경로가 손상 여부를 판단한다). */
        fun fromLabel(label: String): BusinessDivision? = entries.firstOrNull { it.label == label }
    }
}

/**
 * 용역구분(D-6F9-2 — `srvceDivNm`: 일반용역·기술용역 …) — 원문 **trim만** 하고 정규화하지 않는다([NoticeTitle]
 * 과 같은 관례). 대분류·공공조달분류·주공종과 다른 축이라 [BusinessCategory]에 섞지 않는다.
 * trim 뒤 빈 문자열이면 `null`이다(D-6F8-11 — 「없음」은 센티넬이 아니라 타입으로 닫는다).
 */
@ConsistentCopyVisibility
data class ServiceDivision private constructor(
    val value: String,
) {
    companion object {
        fun of(raw: String): ServiceDivision? = raw.trim().takeIf(String::isNotEmpty)?.let(::ServiceDivision)
    }
}

/**
 * 주공종(D-6F9-2 — `mainCnsttyNm`: 전기공사업·건축공사업 …) — 공사 응답이 **이름만** 싣는다. 코드를 지어내지
 * 않는다 — 이 값은 [BusinessCategory.code]로 흐르지 않는다(우회 3). 원문 trim만, 빈 값은 `null`.
 */
@ConsistentCopyVisibility
data class MainConstructionType private constructor(
    val value: String,
) {
    companion object {
        fun of(raw: String): MainConstructionType? = raw.trim().takeIf(String::isNotEmpty)?.let(::MainConstructionType)
    }
}
