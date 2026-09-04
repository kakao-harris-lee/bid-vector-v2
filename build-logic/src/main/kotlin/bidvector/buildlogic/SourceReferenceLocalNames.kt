package bidvector.buildlogic

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * 같은 파일에 선언된 이름(프로퍼티·파라미터·지역 변수·import 별칭) — verifier r19 M-2.
 * `SourceReferences` 와 같은 detekt `TooManyFunctions` 예산(파일당 11개, 이미 꽉 찼다)을
 * 나눠 쓴다.
 *
 * 소문자 뿌리 세그먼트가 이 집합에 있으면 패키지 접두가 아니라 값 체인이다
 * (`tree.Node()` 의 `tree`). 지역 변수도 Kotlin PSI 에서는 `KtProperty` 로 표현되므로
 * 별도 분기가 필요 없다. 파일 **전체**를 훑어 함수·블록 스코프를 가리지 않는다 — 이름이
 * 겹치지 않는 한 안전 방향(값 체인으로 더 자주 건너뛰는 쪽)으로만 넓어진다.
 *
 * **같은 패키지 다른 파일의 top-level 선언은 이 집합에 없다** — 알려진 제한(제한 34 ①의
 * 「import 로 바꾸면 벗어난다」는 이 자리에는 듣지 않는다. 인스턴스 경유 참조라 import 로
 * 대체할 대상이 없다).
 */
internal fun KtFile.locallyDeclaredNames(): Set<String> {
    val names = mutableSetOf<String>()
    names += importDirectives.mapNotNull { it.aliasName }
    accept(
        object : KtTreeVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                property.name?.let { names += it }
                super.visitProperty(property)
            }

            override fun visitParameter(parameter: KtParameter) {
                parameter.name?.let { names += it }
                super.visitParameter(parameter)
            }
        },
    )
    return names
}
