package bidvector.workflow.profile

/**
 * 운영자 사업 정보 입력의 경계값(M7/7C④) — **선언 한 자리**다. 값 타입의 `init` 과 V22 의 CHECK 가
 * 같은 **수**를 쓰고, 둘이 갈리면 `BusinessProfileSchemaGuardTest` 의 경계 test 가 붉어진다(한쪽만
 * 고치면 「앱은 받는데 DB 가 거부」 또는 그 반대가 생긴다).
 *
 * **수는 같지만 술어가 늘 같지는 않다**(r1 CR M-3 · F4). 빈 값 쪽에서 앱은 `isNotBlank` 이고, 표는
 * **상호와 면허명 두 열 모두** 「POSIX `[:space:]` 밖 문자가 하나는 있다」다(면허명은 r2 CR R2-1 에서
 * 같은 술어로 맞췄다 — 그 열은 PK 의 일부라 공백뿐인 행 하나가 시평액 목록 전체의 읽기를 막는다).
 * 공백뿐인 흔한 입력은 양쪽이 거부하며, 두 집합의 차이는 유니코드 공백의 변두리이고 **DB locale 에도
 * 달려 있다**(scope 알려진 제한 6) — 그 틈으로 직접 SQL 이 넣은 값은 읽기에서 크게 실패한다.
 *
 * 값은 **도출이 아니라 선언**이다 — 상호 100자 · 금액 상한 1,000조 원은 실데이터에서 잰 한계가 아니라
 * 「정상 입력은 넉넉히 들고 비정상 입력(붙여넣기 사고 · 자릿수 실수)은 거른다」는 운영 상한이다.
 * 연도 하한 2000 도 같은 성격이다(그보다 오래된 평가액은 입력 실수로 본다).
 */
object ProfileInputPolicy {
    /** 상호 길이 상한 — **코드포인트 수**(DB `char_length` 와 같은 단위). */
    const val TRADE_NAME_MAX_CODE_POINTS: Int = 100

    /** 시평액 행의 면허명 길이 상한 — 코드포인트 수. */
    const val LICENSE_NAME_MAX_CODE_POINTS: Int = 100

    /** 시평액 상한(원). 하한은 0 초과다 — 0 은 「모름」의 기본값으로 흘러들기 쉬운 값이라 받지 않는다. */
    const val CAPACITY_AMOUNT_MAX_WON: Long = 1_000_000_000_000_000L

    /** 시평액 기준 연도의 범위(양 끝 포함). */
    const val ASSESSMENT_YEAR_MIN: Int = 2000
    const val ASSESSMENT_YEAR_MAX: Int = 2100

    /** 한 계정이 둘 수 있는 시평액 행 수 상한 — use case 가 쓰기 전에 잰다(표에는 행 수 CHECK 가 없다). */
    const val CAPACITY_ROWS_MAX: Int = 50
}

/** 코드포인트 수 — 서로게이트 쌍 하나를 한 글자로 센다(DB `char_length` 와 같은 단위). */
internal fun String.codePointLength(): Int = codePointCount(0, length)
