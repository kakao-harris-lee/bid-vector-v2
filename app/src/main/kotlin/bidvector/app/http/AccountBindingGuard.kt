package bidvector.app.http

import bidvector.workflow.access.AccountId
import org.springframework.format.Formatter
import org.springframework.web.bind.WebDataBinder
import org.springframework.web.bind.annotation.InitBinder
import org.springframework.web.bind.annotation.RestControllerAdvice
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.module.SimpleModule
import java.util.Locale

/**
 * **요청 바인딩은 `AccountId` 를 짓지 않는다 — fail-closed**(M7/7B 계약 D-7B-7 ① · 검토 r5 V5-N1).
 *
 * 계정은 세션 인증이 낸 값이 요청 속성으로 운반될 뿐이다(D-7B-2). 그런데 `AccountId` 는 공개 생성자를 가진 값 타입이라
 * 바인딩 층이 요청 값으로 그것을 지을 수 있다 — 검토 r5 가 실측했다: 두 겹 중첩 본문 DTO(H1) · setter 운반(H2) ·
 * `@ModelAttribute` 메서드(H5)에서 Spring · Jackson 이 `AccountId(9)` 를 지었다. 모양(깊이 · setter · 상위 타입 · 제네릭)을
 * 정적 규칙으로 쫓는 것은 열거라 형제가 남는다. 그래서 **짓는 층에서 거부한다** — 모양과 무관하게 닫힌다:
 *
 * 1. **JSON 본문**: 이 빈은 Jackson 모듈이기도 하다. Boot 가 컨텍스트의 `JacksonModule` 빈을 출하 매퍼에 등록하므로,
 *    본문 어디에 `AccountId` 자리가 있든(중첩 · setter · 제네릭 · 상위 타입) 역직렬화가 실패한다(400).
 * 2. **데이터 바인딩**(`@ModelAttribute` 와 주석 없는 객체 매개변수 · 쿼리 · 폼): 전역 `@InitBinder` 가 **`JsonNode` 가
 *    아닌 대상을 가진 바인더**를 거부한다(400). 오늘 app 의 핸들러는 객체 바인딩을 한 곳도 쓰지 않는다 — 본문은 전부
 *    `@RequestBody JsonNode` 이고(그 바인더의 대상은 `JsonNode` 다) 나머지는 경로 · 헤더 문자열이다. 반사 금지 때문에 대상
 *    타입이 `AccountId` 에 닿는지 걷지 않고, 객체 바인딩 자체를 닫는다(허용은 `JsonNode` 하나).
 * 3. **단순 변환**(`@RequestParam` · `@PathVariable` · `@RequestHeader` 의 `String → AccountId`): 같은 바인더에 `AccountId`
 *    거부 formatter 를 단다. 바인더의 editor 는 변환 서비스보다 먼저 묻히므로, 누가 `String → AccountId` 변환기를 등록해도
 *    이 길로는 지어지지 않는다. 거부는 형 변환 실패로 나타나고 응답은 공유 오류표의 500 이다(Spring 이 formatter 의 예외를
 *    형 불일치로 감싼다 — 이 slice 는 그 표를 바꾸지 않는다). `java.beans` editor 는 쓰지 않는다 — 그 패키지는 전송 표면 뿌리다.
 *
 * 두 층을 **한 빈**에 둔 까닭: 출하 조립과 HTTP test 조립이 같은 컴포넌트 스캔으로 이 빈 하나를 얻는다 — 등록이 갈라져
 * 한쪽에만 빠지는 자리가 없다. 근본(`AccountId` 생성 경로 봉쇄)은 7D 파일이라 이 slice 가 하지 않는다 —
 * OPEN 후보 `OPEN-7D-ACCOUNTID-CONSTRUCTION`.
 */
@RestControllerAdvice
class AccountBindingGuard : SimpleModule(MODULE_NAME) {
    init {
        addDeserializer(AccountId::class.java, RejectingAccountIdDeserializer)
    }

    /** 객체 바인딩을 닫고(허용 대상은 본문 `JsonNode` 하나), 단순 변환에는 `AccountId` 거부 formatter 를 단다. */
    @InitBinder
    fun refuseAccountBinding(binder: WebDataBinder) {
        val target = binder.target
        val objectBinding = if (target != null) target !is JsonNode else binder.targetType != null
        if (objectBinding) throw InvalidEditRequestException("요청 객체 바인딩은 열려 있지 않다")
        binder.addCustomFormatter(RejectingAccountIdFormatter, AccountId::class.java)
    }

    private companion object {
        const val MODULE_NAME = "bidvector-account-binding-guard"
    }
}

/** 본문의 어느 자리든 `AccountId` 로 읽으려 하면 실패한다 — Jackson 의 입력 불일치(400)로. */
private object RejectingAccountIdDeserializer : ValueDeserializer<AccountId>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): AccountId = ctxt.reportInputMismatch(this, "계정 식별자는 요청 본문으로 지을 수 없다")
}

/** 요청 문자열을 `AccountId` 로 바꾸려 하면 실패한다(형 변환 실패). 찍기는 값을 싣지 않는다(계정 번호가 새지 않게). */
private object RejectingAccountIdFormatter : Formatter<AccountId> {
    override fun parse(
        text: String,
        locale: Locale,
    ): AccountId = throw IllegalArgumentException("계정 식별자는 요청 값으로 지을 수 없다")

    override fun print(
        `object`: AccountId,
        locale: Locale,
    ): String = "AccountId(***)"
}
