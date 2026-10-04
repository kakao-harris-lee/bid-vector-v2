package bidvector.buildlogic

import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipFile

/**
 * 컴파일된 test 클래스에서 등식이 쓰는 사실을 읽는다 — **소스 텍스트가 아니라 바이트코드**다.
 *
 * ASM 은 `kotlin-compiler-embeddable` 이 relocate 해 품은 것을 쓴다(`ClassOrigin` 관례) — build-logic 의
 * 기존 의존이라 좌표가 늘지 않는다.
 */
internal fun ByteArray.testClassFacts(): TestClassFacts? {
    var binaryName: String? = null
    var superTypes: List<String> = emptyList()
    var isAbstract = false
    val classAnnotations = linkedSetOf<String>()
    val methodAnnotations = linkedSetOf<String>()

    ClassReader(this).accept(
        object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(
                version: Int,
                access: Int,
                name: String,
                signature: String?,
                superName: String?,
                interfaces: Array<out String>?,
            ) {
                binaryName = name.replace('/', '.')
                isAbstract = access and Opcodes.ACC_ABSTRACT != 0
                superTypes =
                    (listOfNotNull(superName) + interfaces.orEmpty())
                        .map { it.replace('/', '.') }
                        .filterNot { it == "java.lang.Object" }
            }

            override fun visitAnnotation(
                descriptor: String,
                visible: Boolean,
            ): AnnotationVisitor? {
                classAnnotations += descriptor.annotationName()
                return null
            }

            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor =
                object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(
                        annotationDescriptor: String,
                        visible: Boolean,
                    ): AnnotationVisitor? {
                        methodAnnotations += annotationDescriptor.annotationName()
                        return null
                    }
                }
        },
        ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
    )

    return binaryName?.let { TestClassFacts(it, methodAnnotations, classAnnotations, superTypes, isAbstract) }
}

/** [roots] 아래 모든 `.class` 를 읽어 사실로 만든다. */
internal fun testClassFactsIn(roots: Iterable<File>): List<TestClassFacts> =
    roots
        .filter(File::isDirectory)
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "class" } }
        .mapNotNull { it.readBytes().testClassFacts() }

/**
 * 애노테이션 타입에 **붙은** 애노테이션을 클래스패스에서 읽는다 — `@ParameterizedTest` 가
 * `@TestTemplate` 이라는 사실을 열거하지 않고 푸는 자리다.
 *
 * 조회 자리에는 test 런타임 클래스패스뿐 아니라 **그 모듈의 test 출력**도 든다. 저자가 그 모듈의
 * test 소스에 선언한 합성 애노테이션은 런타임 클래스패스 어디에도 없기 때문이다(vr r1 H-2).
 *
 * 읽히지 않는 좌표는 **빈 집합**이다. 그러면 그 애노테이션은 발견 어휘에 닿지 못하고, 그 클래스는
 * 모집단에서 빠져 **등재 잉여로 붉는다** — 조용히 통과하는 방향이 아니다.
 */
internal class ClasspathMetaAnnotations(
    private val classpath: Iterable<File>,
) : (String) -> Set<String> {
    private val cache = mutableMapOf<String, List<TestClassFacts>>()

    override fun invoke(annotation: String): Set<String> = factsOf(annotation)?.classAnnotations.orEmpty()

    /** 상위 사슬을 푸는 같은 조회 자리 — 못 찾으면 `null` 이고 그 가지는 거기서 끝난다. */
    fun factsOf(binaryName: String): TestClassFacts? =
        cache
            .getOrPut(binaryName) {
                listOfNotNull(bytesOf("${binaryName.replace('.', '/')}.class")?.testClassFacts())
            }.firstOrNull()

    private fun bytesOf(entryPath: String): ByteArray? =
        classpath.firstNotNullOfOrNull { entry ->
            when {
                entry.isDirectory -> entry.resolve(entryPath).takeIf(File::isFile)?.readBytes()
                entry.isFile && entry.extension == "jar" -> entry.jarEntryBytes(entryPath)
                else -> null
            }
        }

    private fun File.jarEntryBytes(entryPath: String): ByteArray? =
        runCatching {
            ZipFile(this).use { jar ->
                jar.getEntry(entryPath)?.let { jar.getInputStream(it).use(java.io.InputStream::readBytes) }
            }
        }.getOrNull()
}

private fun String.annotationName(): String = removePrefix("L").removeSuffix(";").replace('/', '.')
