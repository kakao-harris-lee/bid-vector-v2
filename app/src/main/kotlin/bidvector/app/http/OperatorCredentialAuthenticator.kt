package bidvector.app.http

import jakarta.servlet.http.HttpServletRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 단일 운영자 자격증명 **인증 경로**(운영자 결정 ②, D-6A1-6) — 값은 환경변수 주입,
 * 기본값 없음(생성자가 빈 값을 거부한다 — 조립 시점에 이미 실패한다, `bidvector.persistence`
 * 관례와 같은 fail-fast). 실패는 401이고 사유를 나누지 않는다(없음/틀림을 구분하지 않는다
 * — 구분하면 자격증명 존재 자체를 흘린다).
 *
 * **M7/7D — 이 클래스는 더 이상 `Filter` 가 아니다.** 7D 가 두 번째 인증 경로(세션 쿠키)를
 * 더하면서, 「모든 경로 기본 거부」는 [RequestAuthenticationFilter] 한 등록이 지고 이 클래스는
 * 그 등록이 구성으로 받는 경로 하나가 됐다(계약 설계 검토 (1) 「열거가 아니라 구성」). 바뀐
 * 것은 **누가 체인에 꽂히는가**이고, 아래 자격증명 비교의 규율과 [OperatorCredential] 타입은
 * 그대로다 — 이름이 `…Filter` 로 남으면 필터가 아닌 것을 필터라 부르게 된다.
 *
 * **우회 ⑦ — 이 경로는 endpoint 이름을 모른다.** 기본 거부의 자리가 등록 하나라서, 새
 * endpoint 가 어느 인증 경로에도 걸리지 않고 열리는 상태가 구성상 존재하지 않는다.
 *
 * **우회 (4)(D-6A1-43) — 비교는 [OperatorCredential.matches] 하나뿐이고, 이 클래스는 raw
 * `String`을 아예 다루지 않는다.** 「이 경로는 그 타입만 다룬다. 환경변수에서 읽는 자리에서
 * 곧바로 감싼다」 — 생성자가 [OperatorCredential]만 받는다. 생성자가 `String` 인자를 받아
 * **내부에서** 감싸면, raw 문자열이
 * 생성자 매개변수로 이 class 안에 순간적으로 존재해 「그 값을 다시 꺼내 이웃 패키지의
 * 새 비교 함수로 넘긴다」는 형태의 재도입이 가능하다(MUT-A3 재현 — 6A-2b 착수
 * 재검증에서 실측: `private val rawExpected: String = expectedCredential` 필드를
 * 새로 만들고 이웃 패키지 `==` 함수에 넘기니 전건 `check`가 초록이었다). **환경변수를 읽는
 * 조립 지점(`BidVectorApplication`·`HttpTestSupport`)에서 [OperatorCredential.of]로 즉시
 * 감싸면 이 class 안에는 애초에 raw 문자열이 존재하지 않는다** — 재도입하려면 이
 * 생성자 시그니처와 조립 지점 둘 다 고쳐야 하고, 그것은 더 이상 「한 줄 이동」이 아니다.
 * `MessageDigest.isEqual`을 직접 호출하는 규율을 `ConstantTimeComparisonStructureTest`
 * (게이트)로만 지키면, 그 게이트는 파일 하나 → 이름 하나 →
 * 패키지+허용 목록 순으로 넓어질 수 있고 위치·이름을 바꾸는 변이가 그때마다 뚫는다(MUT-A2~A4).
 * **타입은 코드가 어디로 옮겨지든 따라온다** — 게이트는 회귀 그물로 남긴다.
 *
 * 이름에 스캔 어휘를 쓰지 않는다(D-6A1-9, D-6A1-19가 모든 설정 키로 일반화) — `Credential`.
 */
class OperatorCredentialAuthenticator(
    private val expected: OperatorCredential,
) : RequestAuthenticator {
    override fun authenticate(request: HttpServletRequest): AuthenticationResult {
        val presented = request.getHeader(CREDENTIAL_HEADER)
        // 없음과 틀림을 **한 식**으로 접는다 — 두 경우가 같은 결과이고(D-6A1-6), 반환 자리를
        // 하나로 두면 「한쪽만 고치는」 편집이 생길 수 없다.
        val matches = presented != null && expected.matches(OperatorCredential.wrap(presented))
        return if (matches) AuthenticationResult.Authenticated(OPERATOR_SUBJECT) else AuthenticationResult.Denied
    }

    companion object {
        const val CREDENTIAL_HEADER = "X-Operator-Credential"

        /**
         * audit 주체 라벨 — 6A-1 과 **같은 값**이다(무회귀). 세션 경로의 라벨은
         * `account:<id>` 이고, 그 둘과 「신원 없음」 하나가 주체 어휘 전부다(V20 주석).
         */
        const val OPERATOR_SUBJECT = "operator"
    }
}

/**
 * 자격증명 값을 감싸는 전용 타입(D-6A1-43, 운영자 결정 2026-09-23 「타입으로 끝낸다」) —
 * **비교가 상수 시간인 유일한 경로**로 [matches] 하나만 노출한다.
 *
 * **왜 이 타입이 종점인가.** `ConstantTimeComparisonStructureTest`(이전 방어)는 *위치*·
 * *이름*에 건 술어였다 — 비교 코드가 어느 class·어느 패키지에 있는지를 본다. verifier
 * r3 가 그 술어를 세 축에서 동시에 열었다: 같은 파일 안에서 `==`→`Objects.equals`로 한
 * 글자만 바꿔도(MUT-A4), 이웃 패키지로 비교를 옮겨도(MUT-A3), 허용 목록의 simple name을
 * 하위 패키지에서 재사용해도(MUT-A2) 전건 `check`가 초록이었다 — **위치·이름 술어는
 * 코드를 옮기면 진다.** 타입은 코드가 어디에 있든 따라온다: 이 타입은 **빠른 비교를
 * 제공하지 않는다** — `equals`/`hashCode`/`toString`을 의도적으로 재정의하지 않는다
 * (data class로 만들면 컴파일러가 내용 기반 `equals`를 합성해 `Intrinsics.areEqual`을
 * 심고, 그것이 이 타입이 막으려는 바로 그 형태가 된다). 기본 `Any.equals`는 참조
 * 동일성이라 서로 다른 [wrap] 호출이 만든 두 인스턴스는 내용이 같아도 항상 `false`다
 * — `==`로 비교를 대체하려는 시도는 **작동하지 않는 방향으로만** 실패한다(항상 인증
 * 거부 — 시간에 따라 새는 방향이 아니다).
 *
 * **(2b) 값 획득 축 — 이 타입이 밖에 무엇을 허락하는가(실측).** 공개 표면은 `of(String)`·
 * `wrap(String)`·`matches(OperatorCredential): Boolean` 셋뿐이다. 원시 바이트(`bytes`)를
 * 꺼내는 accessor가 없어 감싼 뒤에는 원문을 되돌릴 방법이 없다. `toString()`은 재정의하지
 * 않아 `Any.toString()`(`클래스명@해시코드`)을 그대로 쓰므로 **값을 포함하지 않는다** —
 * 로그에 실수로 찍혀도(`logger.info("credential={}", credential)`) 원문이 새지 않는다
 * (`OperatorCredentialTest`가 실측한다). `equals`/`hashCode`도 값을 비교·해시하지
 * 않으므로 컬렉션(`Set`·`Map` 키)에 넣어도 내용 기반 조회가 성립하지 않는다.
 *
 * **두 factory 로 가르는 이유.** [of]는 **신뢰하는 설정값**(환경변수 — 조립 지점에서
 * 딱 한 번) 전용이고 빈 값을 조립 시점에 거부한다(fail-fast, D-6A1-6 계승). [wrap]은
 * **신뢰하지 않는 제시값**(HTTP 헤더) 전용이고 검증하지 않는다 — 빈 문자열도 감싸
 * [matches]가 자연히 거짓을 내게 한다(검증에서 예외를 던지면 「없음/틀림을 구분하지
 * 않는다」는 D-6A1-6 불변식이 401 대신 500으로 깨진다).
 */
class OperatorCredential private constructor(
    private val bytes: ByteArray,
) {
    /** 우회 (4) — 유일한 비교 경로. JDK가 상수 시간을 보장하는 [MessageDigest.isEqual]만 쓴다. */
    fun matches(other: OperatorCredential): Boolean = MessageDigest.isEqual(bytes, other.bytes)

    companion object {
        /** 신뢰하지 않는 제시값(HTTP 헤더) 감싸기 — 검증하지 않는다(빈 값도 감싼다). */
        fun wrap(value: String): OperatorCredential = OperatorCredential(value.toByteArray(StandardCharsets.UTF_8))

        /** 신뢰하는 설정값(환경변수) 감싸기 — 빈 값을 조립 시점에 거부한다(fail-fast). */
        fun of(value: String): OperatorCredential {
            require(value.isNotBlank()) { "운영자 자격증명은 빈 값일 수 없다" }
            return wrap(value)
        }
    }
}
