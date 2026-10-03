package bidvector.archfixture.violating.transport

import java.net.http.HttpClient
import java.util.function.Supplier
import kotlin.reflect.KClass

// D-6G2b-24(cr M-2) 음성 fixture — 전송 타입이 **시그니처의 간접 자리에만** 나타나는 형태들. 계약이 verifier
// 표적으로 들었고 임시 변이로 RED 가 실측된 축이라, 상시 fixture 로 고정한다. 실행되지 않는다.

/** 제네릭 인자 — 원시 타입은 `List` 이고 전송 타입은 시그니처에만 있다. */
class RogueGenericArgHolder {
    fun hold(clients: List<HttpClient>): Int = clients.size
}

/** 제네릭 인자(함수형) — `Supplier.get()` 의 반환은 지워져 `Object` 다. */
class RogueSupplierArgHolder {
    fun build(supplier: Supplier<HttpClient>): Any? = supplier.get()
}

/** SAM 람다 — 전송 타입이 SAM 인터페이스의 타입 인자로만 나타난다. */
class RogueSamLambdaHolder {
    fun supplier(): Supplier<HttpClient> = Supplier { HttpClient.newHttpClient() }
}

/** 어노테이션 인자 — 클래스 리터럴로만 전송 타입을 이름 붙인다. */
@UsesTransport(HttpClient::class)
class RogueAnnotatedHolder {
    fun describe(): String = "no transport type in the body"
}

@Target(AnnotationTarget.CLASS)
annotation class UsesTransport(
    val type: KClass<*>,
)
