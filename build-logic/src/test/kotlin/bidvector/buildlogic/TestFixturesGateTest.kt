package bidvector.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

/**
 * M1/1A-b ④(a)(D-2) — `java-test-fixtures` 적용이 명시 사유로 실패하는지의 유일한 실행
 * 증거. **이 저장소에서 TestKit(`GradleRunner`) 을 쓰는 첫 사례**다 — 다른 게이트 test 는
 * 전부 순수 함수([KotlinFunctionLengthsTest] 류)이거나 ArchUnit 으로 이미 컴파일된 바이트코드를
 * 재평가([TypeShapeFixtureTest])하지만, 이 판정은 **플러그인 적용 자체**(project 평가 단계)에서
 * 일어나 실제 Gradle project 를 하나 띄워야만 재현할 수 있다.
 *
 * `withPluginClasspath()`가 `pluginUnderTestMetadata`(kotlin-dsl 이 자동 생성)를 통해 이
 * 모듈의 runtime classpath 를 격리된 fixture project 에 주입한다 — 네트워크 자원 해석 없이
 * 오프라인으로 돈다. fixture 의 `module/build.gradle.kts`는 `java-test-fixtures`를
 * **먼저** 적용한다 — 그래야 `bidvector.kotlin-conventions`가 자신의 `plugins.withId(...)`를
 * 등록하는 시점에 이미 적용된 상태라 콜백이 **그 자리에서 즉시** 불려, 스크립트 뒤쪽의
 * version catalog·`gradle.properties` 요구(`bidvector.jvmToolchain` 등)에 닿기 전에
 * 실패한다 — fixture 를 최소로 유지할 수 있는 이유다.
 */
class TestFixturesGateTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `java-test-fixtures 적용은 명시 사유로 실패한다`() {
        File(projectDir, "settings.gradle.kts").writeText(
            "rootProject.name = \"fixture\"\ninclude(\"module\")\n",
        )
        val moduleDir = File(projectDir, "module").apply { mkdirs() }
        File(moduleDir, "build.gradle.kts").writeText(
            "plugins {\n" +
                "    id(\"java-test-fixtures\")\n" +
                "    id(\"bidvector.kotlin-conventions\")\n" +
                "}\n",
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments(":module:help", "--stacktrace")
                .buildAndFail()

        assertTrue(
            result.output.contains("OPEN-1BC-TESTFIXTURES-GATE"),
            "출력에 명시 사유가 없다:\n${result.output}",
        )
        assertTrue(
            result.output.contains("전 모듈에서 허용하지 않는다"),
            "출력에 D-2 사유 문구가 없다:\n${result.output}",
        )
    }
}
