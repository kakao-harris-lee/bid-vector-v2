package bidvector.sharedkernel

/** 이 도메인이 다루는 통화. KRW 하나뿐이라 `java.util.Currency`(로케일 자원 I/O)를 열지 않는다. */
enum class Currency {
    KRW,
}

/**
 * 금액이 어느 개념 축에서 왔는지. `Money.basis`가 나르는 값이고, 각 [Money] 구현이 상수로 낸다.
 */
enum class Basis {
    BASE_AMOUNT,
    ESTIMATED,
    YEGA,
    BID,
    ALLOCATED_BUDGET,
    AWARD,
}

/**
 * 부가세 처리. `Unknown` 금액은 다른 과세 처리의 금액과 산술·비교에 들어갈 수 없다
 * (`data-dictionary.md` §1.2.1) — 강제는 [sameKnownVat]가 든다.
 */
enum class VatTreatment {
    INCLUSIVE,
    EXCLUSIVE,
    UNKNOWN,
}
