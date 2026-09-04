package bidvector.archfixture.violating.settlement

/**
 * import 없는 완전수식 참조 — `ForbiddenImport` 류(import 만 보는 수단)가 정의상 못 잡는 자리이자
 * 소스 게이트의 완전수식 참조 단언의 유일한 fixture 근거다. 인라인 상수라 바이트코드에 타입
 * 참조가 남지 않고(알려진 제한 7과 같은 성질) JDK 타입이라 1차 게이트도 못 본다.
 */
class FullyQualifiedReferenceLeak {
    val accepted: Int = java.net.HttpURLConnection.HTTP_OK
}
