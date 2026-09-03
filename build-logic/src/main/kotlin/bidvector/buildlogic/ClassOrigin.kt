package bidvector.buildlogic

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

/**
 * class 파일의 **`SourceFile` 속성** — 그 클래스를 낸 소스 파일의 이름.
 *
 * **원산지 판정의 앵커다.** 애노테이션(`kotlin.Metadata`)은 앵커가 되지 못한다 — 소스에 한 줄
 * 붙이면 Java 클래스도 갖기 때문이다. `SourceFile` 은 컴파일러가 쓰는 속성이라 위조하려면
 * 바이트코드 편집기가 필요하고,
 * 무엇보다 **값이 게이트를 통과한 소스 이름 집합에 들어야** 하므로 위조만으로는 부족하다.
 *
 * ASM 은 `kotlin-compiler-embeddable` 이 relocate 해 품은 것을 쓴다 — build-logic 의 기존
 * 의존이라 좌표가 늘지 않고 Kotlin 버전과 함께 움직인다. relocate 경로가 바뀌면 **컴파일이
 * 깨져** 시끄럽게 드러난다.
 *
 * `SKIP_DEBUG` 를 주면 안 된다 — 그 플래그가 `visitSource` 를 통째로 건너뛴다.
 */
internal fun ByteArray.sourceFileName(): String? {
    var name: String? = null
    ClassReader(this).accept(
        object : ClassVisitor(Opcodes.ASM9) {
            override fun visitSource(
                source: String?,
                debug: String?,
            ) {
                name = source
            }
        },
        ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES,
    )
    return name
}

/**
 * 게이트를 통과한 소스의 **이름 집합**. 컴파일러가 실제로 먹은 파일(`compileKotlin.source`)에서
 * 뽑으므로 「소스는 안 재고 class 는 신뢰」라는 틈이 정의상 사라진다.
 *
 * **경로가 아니라 이름이다** — `SourceFile` 속성이 이름만 담기 때문이며, 그 대가는 알려진
 * 제한에 등재돼 있다.
 */
internal fun verifiedSourceNames(sources: Iterable<java.io.File>): Set<String> =
    sources.filter { it.isFile }.map { it.name }.toSet()
