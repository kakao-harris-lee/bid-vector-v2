package bidvector.app.architecture

/**
 * D-6G2g-11 (운영자 결정 B-4 (나)) — **참조 수집 깊이를 재는 축**. 깊이 자체는
 * [ReferenceCollection] 이고, 어느 축이 어느 깊이인지는 `architecture-policy.properties` 의
 * `collection.depth.<축>` 이 정한다.
 *
 * 축을 enum 으로 두는 이유는 **정책 표와 구현의 등식을 잴 모집단**이 필요해서다. 키 문자열만
 * 있으면 축을 하나 빼먹은 구현이 조용하다 — `CollectionDepthGateTest` 가 이 enum 과 정책 키
 * 집합을 양방향으로 맞대고, 축마다 「구현이 실제로 낸 관측 == 그 축의 선언된 깊이로 낸 관측」을
 * 잰다.
 */
enum class DepthAxis(
    val key: String,
) {
    /** (클래스, 전송 표면 타입) 쌍과 바깥 참조 패키지 — 6G-2b 가 FULL 로 올린 자리. */
    TRANSPORT("transport"),

    /** (클래스, 반사 타입) 쌍. */
    REFLECTION("reflection"),

    /** 수집 use case 패키지가 `procurement` 에서 참조하는 타입 집합. */
    COLLECTION_PROCUREMENT("collection-procurement"),

    /** 수집 use case 타입을 참조하는 production 클래스 집합. */
    USECASE("usecase"),

    /** 공고 키 해시를 짓는 함수를 참조하는 클래스 집합. */
    KEY_HASH("key-hash"),

    /** 원문 키 접근 타입을 참조하는 클래스 집합 — **참조자 축**(멤버 접근 축과 다르다). */
    RAW_ACCESS("raw-access"),

    /** 프로세스를 자동 시작하는 Spring 러너 타입을 참조하는 클래스 집합. */
    RUNNER("runner"),

    /** 서비스 키 원문 설정 타입을 참조하는 클래스 집합. */
    SERVICE_KEY("service-key"),

    /** 로거 타입을 참조하는 클래스 집합. */
    LOGGING("logging"),

    /** 운영자 자격증명 타입을 참조하는 클래스 집합. */
    OPERATOR_CREDENTIAL("operator-credential"),
}
