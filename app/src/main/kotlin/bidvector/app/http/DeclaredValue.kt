package bidvector.app.http

import jakarta.servlet.http.HttpServletRequest

/**
 * 계약이 든 매개변수 하나와 그 **원문 값 목록**(합쳐지기 전의 사실).
 *
 * 왜 `Map` 이 아닌가: 제한 층 클래스의 생성자 인자 타입은 주입 표면 게이트가 **등식 목록**으로
 * 잠그고 그 목록에 `java.util.Map` 이 없다. 질의 값은 능력이 아니라 데이터지만, 목록을 넓히면
 * 이 층의 모든 클래스에 대해 넓어진다 — 그래서 목록 안의 타입(`java.util.List`·`java.lang.String`)
 * 으로 같은 사실을 나른다.
 */
internal data class DeclaredValue(
    val name: String,
    val values: List<String>,
)

/**
 * 계약이 든 이름들의 원문 값 목록을 읽는다 — 한 요청의 질의를 읽는 **유일한 자리**
 * (M7/7C⑤-a, D-7C5-5 — 7C① 이 세운 규율의 공용 자리).
 *
 * 왜 한 자리인가(7C① verifier r3 R3-M-1 의 처방): `@RequestParam` 으로 받으면 질의 읽기가
 * **핸들러 바깥**(프레임워크의 인자 해석)에서 일어나, 잘못된 퍼센트 인코딩처럼 **읽기 자체가
 * 깨지는** 입력이 전역 폴백(500)으로 샜다. 읽기를 핸들러 안으로 들이면 그 실패를 요청 거부
 * (400)로 옮길 수 있다. **자리가 술어다**: 이 호출이 실패했다는 것이 곧 질의 해석이 실패했다는
 * 사실이다.
 *
 * 서블릿 컨테이너는 질의 전체를 한 번에 해석하므로, 깨진 이스케이프가 **계약 밖 이름에 있어도**
 * 이 읽기가 깨진다. 그 실패를 **잡지 않고 그대로 던진다** — 어느 거부 예외로 옮길지는 endpoint
 * 마다 다르고(오류 어휘가 둘로 갈리면 안 된다), 호출부가 원래 실패를 `initCause` 로 달 수 있어야
 * 한다. 결과 타입으로 감싸면 그 원인을 담을 생성자 인자가 `Throwable` 이 되고, 그것은 주입 표면
 * 게이트가 막는 자리다(7C① 의 거부 예외 KDoc 이 같은 사실을 적는다).
 */
internal fun HttpServletRequest.readDeclaredValues(declared: List<String>): List<DeclaredValue> =
    declared.map { name -> DeclaredValue(name, getParameterValues(name)?.toList().orEmpty()) }

/**
 * 두 번 이상 온 **계약 이름**들 — 비어 있지 않으면 호출부가 거부한다. 값 둘을 하나로 합치면
 * 리터럴 쉼표가 든 값과 구별되지 않으므로 조용히 합치지 않는다. 알 수 없는 매개변수의 반복은
 * 판정 대상이 아니다(그 거동은 바꾸지 않는다).
 */
internal fun List<DeclaredValue>.repeatedNames(): List<String> = filter { it.values.size > 1 }.map { it.name }

/** 값이 없으면 `null` — `@RequestParam(required = false)` 이 주던 것과 같은 모양이다. */
internal fun List<DeclaredValue>.firstValue(name: String): String? =
    firstOrNull { it.name == name }?.values?.firstOrNull()
