package bidvector.archfixture.violating.procurement

import java.net.HttpURLConnection

/**
 * 컴파일 시 인라인되는 상수. 바이트코드에 `java/net/HttpURLConnection` 타입 참조가 남지 않아
 * (`javap` 로 확인) ArchUnit 이 못 보고, JDK 타입이라 Maven group 도 없어 1차 게이트도 못 본다 —
 * 두 기존 게이트의 사각이 겹치는 자리(알려진 제한 7). 소스 게이트는 import 자체를 본다.
 */
class InlinedConstantLeak {
    val accepted: Int = HttpURLConnection.HTTP_OK
}
