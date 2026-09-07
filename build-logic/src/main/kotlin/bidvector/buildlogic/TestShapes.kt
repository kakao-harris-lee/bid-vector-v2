package bidvector.buildlogic

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.io.File

/**
 * `test-shape-policy.properties` 를 읽어 만든 판정 정책 — 설계 검토
 * `_workspace/harness-test-shape/02_design-review.md` A-3. 어노테이션 목록은 짧은 이름으로
 * 매칭한다(import 해석 없음) — kotest 러너가 붙거나 새 test-메서드 어노테이션이 생기면 이
 * 목록만 바뀐다.
 */
internal data class TestShapePolicy(
    val methodAnnotations: Set<String>,
    val factoryAnnotations: Set<String>,
    val forbidSuspend: Boolean,
    val forbidPrivate: Boolean,
) {
    companion object {
        fun load(policy: Map<String, String>): TestShapePolicy =
            TestShapePolicy(
                methodAnnotations = policy.requireList("test.shape.method-annotations").toSet(),
                factoryAnnotations = policy.requireList("test.shape.factory-annotations").toSet(),
                forbidSuspend = policy.requireBoolean("test.shape.forbid-suspend"),
                forbidPrivate = policy.requireBoolean("test.shape.forbid-private"),
            )
    }
}

/** 위반 하나 — JUnit 이 조용히 discover 하지 않을 test-메서드 형태(A-2). */
internal data class TestShapeViolation(
    val fileName: String,
    val line: Int,
    val functionName: String,
    val reason: String,
)

/**
 * test-메서드(정책 목록) 함수가 JUnit Jupiter 가 조용히 discover 하지 않는 형태인지 소스 PSI 에서
 * 잡는다 — `OPEN-2B-TEST-DISCOVERY-GUARD`(2B 에서 25/27 가짜 초록 실측, 식 본문
 * `= runBlocking { … }` 함정). 순수 함수, task 는 배선·report 만(`PublicApiTypes.extract` 관례).
 *
 * 허용 형태는 하나뿐이다 — **블록 본문 ∧ (반환 타입 없음 ∨ `Unit`)**. 식 본문은 `: Unit =` 을
 * 붙여도 위반으로 센다(설계 검토 A-2 — 오탐이 미탐보다 낫다). `@TestFactory`(정책 목록)는
 * 반대다 — `DynamicNode` 컬렉션을 반환해야 하므로 명시 반환 타입이 `Unit` 이면 위반이다.
 * `suspend`·`private` 는 블록 본문이어도 정책 스위치로 별도 위반 처리한다(설계 검토 「미달
 * 후보」 (a)(b) — 둘 다 Jupiter 가 discover 하지 않는 같은 「조용한 미실행」 위협).
 */
internal object TestShapes {
    fun extract(
        fileName: String,
        text: String,
        policy: TestShapePolicy,
    ): List<TestShapeViolation> =
        withKotlinPsi { factory -> violationsOf(factory.createFile(fileName, text), fileName, policy) }

    /** task 용 — 환경을 파일마다 만들지 않는다(`KotlinPsi.parseKotlinFiles`). */
    fun extractAll(
        files: List<File>,
        policy: TestShapePolicy,
    ): List<TestShapeViolation> = parseKotlinFiles(files) { ktFile, file -> violationsOf(ktFile, file.path, policy) }

    private fun violationsOf(
        ktFile: KtFile,
        fileName: String,
        policy: TestShapePolicy,
    ): List<TestShapeViolation> {
        val text = ktFile.text
        val functions = mutableListOf<KtNamedFunction>()
        ktFile.accept(
            object : KtTreeVisitorVoid() {
                override fun visitNamedFunction(function: KtNamedFunction) {
                    functions += function
                    super.visitNamedFunction(function)
                }
            },
        )
        return functions.mapNotNull { it.violation(fileName, text, policy) }
    }

    private fun KtNamedFunction.violation(
        fileName: String,
        text: String,
        policy: TestShapePolicy,
    ): TestShapeViolation? {
        val shortNames = annotationEntries.mapNotNull { entry -> entry.shortName?.asString() }.toSet()
        val isFactory = shortNames.any { it in policy.factoryAnnotations }
        val isMethod = !isFactory && shortNames.any { it in policy.methodAnnotations }
        if (!isFactory && !isMethod) return null

        val reasons = suspendPrivateReasons(policy) + shapeReason(isFactory)
        if (reasons.isEmpty()) return null
        return TestShapeViolation(fileName, text.lineOf(textOffset), name ?: "<익명>", reasons.joinToString("; "))
    }

    private fun KtNamedFunction.suspendPrivateReasons(policy: TestShapePolicy): List<String> =
        listOfNotNull(
            "suspend fun 은 JUnit 이 discover 하지 않는다(test.shape.forbid-suspend)"
                .takeIf { policy.forbidSuspend && hasModifier(KtTokens.SUSPEND_KEYWORD) },
            "private fun 은 JUnit 이 discover 하지 않는다(test.shape.forbid-private)"
                .takeIf { policy.forbidPrivate && hasModifier(KtTokens.PRIVATE_KEYWORD) },
        )

    /** 반환 타입/본문 형태 축 — factory 는 반대 규칙(위 KDoc). */
    private fun KtNamedFunction.shapeReason(isFactory: Boolean): List<String> {
        val returnTypeName = typeReference?.text
        return if (isFactory) {
            listOfNotNull(
                "@TestFactory 는 명시 반환 타입이 Unit 이면 안 된다(DynamicNode 컬렉션을 반환해야 한다)"
                    .takeIf { returnTypeName == "Unit" },
            )
        } else {
            val allowed = hasBlockBody() && (returnTypeName == null || returnTypeName == "Unit")
            val reason =
                when {
                    allowed -> null
                    !hasBlockBody() -> "식 본문 test 메서드는 반환 타입이 Unit 이 아니면 JUnit 이 discover 하지 않는다"
                    else -> "블록 본문이지만 명시 반환 타입이 Unit 이 아니다"
                }
            listOfNotNull(reason)
        }
    }
}

/** `true`/`false` 리터럴만 허용한다 — 다른 값은 정책 오류(기본값 없음, D-6 관례). */
private fun Map<String, String>.requireBoolean(key: String): Boolean =
    when (val raw = requireValue(key).trim()) {
        "true" -> true
        "false" -> false
        else -> error("정책 키 '$key' 의 값 '$raw' 이 true/false 가 아니다")
    }
