package bidvector.app.wiring

import java.io.File

/**
 * `EvaluationAdapterDependencyTest`(adapters 모듈)와 같은 기법 — `javap -p -v`로 컴파일된
 * class 파일의 상수 풀을 훑는다(소스 텍스트 grep이 아니다). D-6A3-2·D-6A3-3 두 게이트가
 * 공유한다(중복 금지).
 */
internal fun packageJavapOutput(packageDir: File): String {
    check(packageDir.isDirectory) {
        "빌드 산출물을 찾지 못했다: ${packageDir.absolutePath} — :app:compileKotlin 선행 필요"
    }
    return packageDir
        .walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .joinToString("\n") { javap(it) }
}

/**
 * `javap`를 OS `PATH`의 이름 조회가 아니라 실행 중인 JVM의 `java.home`에서 해석한다
 * (CI의 JDK 21과 다른 `javap`를 부를 여지를 없앤다, adapters 모듈 선례와 같은 이유).
 */
internal fun javap(classFile: File): String {
    val javapExecutable =
        File(System.getProperty("java.home"), "bin/javap").let {
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) File(it.path + ".exe") else it
        }
    val process =
        ProcessBuilder(javapExecutable.absolutePath, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "javap 실행 실패(exit=$exitCode): ${classFile.name}\n$output" }
    return output
}
