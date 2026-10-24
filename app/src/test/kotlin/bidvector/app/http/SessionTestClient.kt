package bidvector.app.http

import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

/**
 * 세션 경로로 부르는 test 클라이언트 — 실 로그인(`/api/session`) 뒤 쿠키 · CSRF 를 들고 다닌다.
 * 계정 범위 경로(입찰함)는 세션으로만 닿으므로, 그 경로의 HTTP test 들이 같은 한 벌을 쓴다.
 */
internal class SessionTestClient(
    private val restTemplate: TestRestTemplate,
    private val url: (String) -> String,
) {
    private var cookie: String = ""
    private var stamp: String = ""

    /** 로그인 — 실패하면 그 자리에서 터진다(뒤의 단언이 「로그인 실패」를 다른 실패로 읽지 않게). */
    fun signIn(): SessionTestClient {
        val response =
            exchange(
                "/api/session",
                HttpMethod.POST,
                json(HttpHeaders()),
                """{"loginName":"$TEST_LOGIN","passphrase":"$TEST_PASSPHRASE"}""",
            )
        check(response.statusCode.value() == CREATED_STATUS) { "로그인 실패: ${response.statusCode}" }
        val setCookie = requireNotNull(response.headers.getFirst(HttpHeaders.SET_COOKIE)) { "Set-Cookie 가 없다" }
        cookie = setCookie.substringAfter("${SessionCookieWriter.HOST_PREFIXED_NAME}=").substringBefore(";")
        stamp = requireNotNull(CSRF.find(response.body.orEmpty())?.groupValues?.get(1)) { "로그인 응답에 csrf 가 없다" }
        return this
    }

    fun get(path: String): ResponseEntity<String> = exchange(path, HttpMethod.GET, cookieOnly())

    /** 상태를 바꾸는 요청 — 쿠키 + CSRF. [body] 가 `null` 이면 본문도 Content-Type 도 싣지 않는다. */
    fun post(
        path: String,
        body: String?,
    ): ResponseEntity<String> = send(HttpMethod.POST, path, body)

    /** 아무 메서드 — 상태를 바꾸는 메서드면 CSRF 를 싣는다. [body] 가 `null` 이면 본문도 Content-Type 도 없다. */
    fun send(
        method: HttpMethod,
        path: String,
        body: String?,
    ): ResponseEntity<String> {
        val headers =
            cookieOnly().apply {
                if (method.name() !in BrowserSessionAuthenticator.CSRF_EXEMPT_METHODS) {
                    set(BrowserSessionAuthenticator.CSRF_HEADER, stamp)
                }
            }
        return exchange(path, method, if (body == null) headers else json(headers), body)
    }

    private fun cookieOnly(): HttpHeaders =
        HttpHeaders().apply { set(HttpHeaders.COOKIE, "${SessionCookieWriter.HOST_PREFIXED_NAME}=$cookie") }

    private fun json(headers: HttpHeaders): HttpHeaders =
        HttpHeaders().apply {
            putAll(headers)
            contentType = MediaType.APPLICATION_JSON
        }

    private fun exchange(
        path: String,
        method: HttpMethod,
        headers: HttpHeaders,
        body: String? = null,
    ): ResponseEntity<String> = restTemplate.exchange(url(path), method, HttpEntity(body, headers), String::class.java)

    private companion object {
        const val CREATED_STATUS = 201
        val CSRF = Regex("\"csrf\"\\s*:\\s*\"([^\"]+)\"")
    }
}
