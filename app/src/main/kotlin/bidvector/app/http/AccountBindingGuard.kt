package bidvector.app.http

import bidvector.workflow.access.AccountId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.format.Formatter
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.WebDataBinder
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.InitBinder
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
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
 *    이 길로는 지어지지 않는다. Spring 은 formatter 의 예외를 형 불일치로 감싸 공유 오류표의 500 으로 보낸다 — 그래서 이 advice 가
 *    **먼저**(`HIGHEST_PRECEDENCE`) 그 형 불일치를 받아, 원인이 이 가드의 거부면 400 으로 낸다. 그 밖의 형 불일치는 공유 오류표의
 *    마지막 처리기를 **그대로 부른다**(같은 함수 — 어긋날 수 없다) — 공유 오류표는 바꾸지 않는다(D-7B-7 팀장 추가). `java.beans`
 *    editor 는 쓰지 않는다 — 그 패키지는 전송 표면 뿌리다.
 *
 * **전제와 그 잠금**: 2 의 「대상이 있는 바인더는 전부 거부」는 오늘 app main 의 `@ModelAttribute` 사용이 0 이라는 관측 위에 서 있다
 * (`AccountBindingStructureTest` 가 그 ∅ 를 등식으로 잠근다). 뒤의 slice 가 `@ModelAttribute` · 폼 DTO 를 쓰게 되면 이 거부를 그 대상에
 * 맞게 풀어야 한다 — `OPEN-7D-ACCOUNTID-CONSTRUCTION` 의 근본 처리(생성 경로 봉쇄)가 오면 이 가드의 2 는 필요 없어진다.
 *
 * 바인딩 층(2 · 3)은 **`@RestController` 핸들러에만** 건다 — 관리 포트의 actuator 핸들러는 선택 본문을 `Map` 으로 바인딩하므로,
 * 전역으로 걸면 health 가 400 이 된다(측정: `ManagementHealthSurfaceTest`). 우리 핸들러는 전부 `@RestController` 다.
 *
 * 두 층을 **한 빈**에 둔 까닭: 출하 조립과 HTTP test 조립이 같은 컴포넌트 스캔으로 이 빈 하나를 얻는다 — 등록이 갈라져
 * 한쪽에만 빠지는 자리가 없다. 근본(`AccountId` 생성 경로 봉쇄)은 7D 파일이라 이 slice 가 하지 않는다 —
 * OPEN 후보 `OPEN-7D-ACCOUNTID-CONSTRUCTION`.
 */
@RestControllerAdvice(annotations = [RestController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
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

    /**
     * 위 formatter 의 거부를 400 으로 낸다(형 불일치 안에 [AccountBindingRefusedException] 이 있을 때). 그 밖의 형 불일치는 공유
     * 오류표의 마지막 처리기 [GlobalErrorHandler.fallback] 을 **그대로 부른다** — 응답 생성 코드를 두 벌 두지 않아, fallback 이 바뀌어도
     * 둘이 어긋날 수 없다(D-7B-7 팀장 추가 (가)). 이 advice 가 먼저 받는다고 다른 거동이 바뀌지 않는다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun refusedConversion(
        exception: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorBody> {
        val refused = generateSequence<Throwable>(exception) { it.cause }.any { it is AccountBindingRefusedException }
        if (!refused) return GlobalErrorHandler().fallback(exception, request)
        val body = ErrorMapping.forThrowable(InvalidEditRequestException(REFUSED), request.correlationIdOrUnknown())
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    private companion object {
        const val MODULE_NAME = "bidvector-account-binding-guard"
        const val REFUSED = "계정 식별자는 요청 값으로 지을 수 없다"
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
    ): AccountId = throw AccountBindingRefusedException()

    override fun print(
        `object`: AccountId,
        locale: Locale,
    ): String = "AccountId(***)"
}

/** 가드가 단순 변환을 거부할 때 던진다 — 형 불일치로 감싸여도 [AccountBindingGuard.refusedConversion] 이 원인으로 알아본다. */
class AccountBindingRefusedException : IllegalArgumentException("계정 식별자는 요청 값으로 지을 수 없다")
