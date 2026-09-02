package bidvector.archfixture.violating.qualification

/**
 * Kotlin 표준 I/O 위반. **추가 의존이 하나도 없다** — stdlib 만으로 콘솔을 연다.
 * 금지 열거로는 잡히지 않았다: 승인 문서가 `kotlin.io` 를 이름으로 든 적이 없어 출처 대조
 * 테스트조차 정의상 놓쳤다. allow-list 가 그 비대칭을 뒤집는다.
 */
class ConsoleIoLeak {
    fun read(): String = readln()
}
