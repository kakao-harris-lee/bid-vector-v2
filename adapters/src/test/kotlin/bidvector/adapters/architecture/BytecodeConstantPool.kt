package bidvector.adapters.architecture

import java.io.File

/**
 * **상수 풀을 읽는 한 자리.** 축 dependency test 들이 `javap -p -v` 로 컴파일 산출물의 좌표를 훑는
 * 자리를 공유한다 — 이 slice 가 네 축(`event`·`extraction`·`koneps`·`ml`)을 소스 텍스트 술어에서
 * 이 층으로 옮기면서 세웠고, 두 층 등식 test 의 보유자 단언도 같은 자리를 쓴다.
 *
 * **왜 파일마다가 아니라 일괄인가 — 측정이 꼴을 정했다.** 형제 열 축은 class 파일 하나마다 프로세스
 * 하나를 띄운다. 이 slice 가 받은 네 축의 class 는 260 개이고 그 꼴을 복제하면 `:adapters:test` 에
 * **6분 9초**가 붙는다(실측). 축마다 일괄 호출 한 번이면 같은 260 개가 **12초**다. 형제 열 축의 같은
 * 비용은 이 slice 의 축이 아니라 `OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` 가 받는다(그 OPEN 이 열넷을
 * 공유 헬퍼로 모으는 자리를 받는다).
 *
 * `javap` 는 OS `PATH` 의 이름 조회가 아니라 **실행 중인 JVM 의 `java.home`** 에서 해석한다(형제
 * 관례와 같은 이유 — CI 의 JDK 21 과 다른 `javap` 를 부를 여지를 없앤다).
 */
internal object BytecodeConstantPool {
    /**
     * [classFiles] 전부를 `javap` **한 번**으로 읽고 **파일마다의 출력 토막**으로 갈라 준다.
     * 일괄 호출의 위험은 귀속이 어긋나는 것이라, `Classfile` 헤더 수가 입력 수와 같은지 먼저 센다 —
     * 헤더가 모자라면 어느 class 의 상수 풀을 못 본 것이고 그때 조용히 「위반 없음」이 되면 안 된다.
     */
    fun chunksOf(classFiles: List<File>): List<Chunk> {
        require(classFiles.isNotEmpty()) { "읽을 class 파일이 없다 — 호출자가 빈 목록을 걸러야 한다" }
        val output = javapOutput(classFiles)
        val headers = CLASSFILE_HEADER.findAll(output).toList()
        check(headers.size == classFiles.size) {
            "javap 가 입력 수와 다른 수의 Classfile 헤더를 냈다: 헤더 ${headers.size} != 입력 ${classFiles.size}"
        }
        return headers.mapIndexed { index, header ->
            val end = headers.getOrNull(index + 1)?.range?.first ?: output.length
            Chunk(File(header.groupValues[1].trim()), output.substring(header.range.first, end))
        }
    }

    /** 한 class 파일의 `javap -p -v` 출력 토막. [classFile] 은 헤더가 적은 경로다(입력 순서가 아니다). */
    data class Chunk(
        val classFile: File,
        val body: String,
    )

    /** [root] 아래 class 파일 전수. 빈 디렉터리를 「위반 없음」으로 오판하지 않도록 호출자가 비공허를 단언한다. */
    fun classFilesUnder(root: File): List<File> =
        root
            .walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .sortedBy(File::getPath)
            .toList()

    private val CLASSFILE_HEADER = Regex("""(?m)^Classfile (.+)$""")

    private val executable: String by lazy {
        val javaHome = System.getProperty("java.home")
        val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        File(javaHome, "bin/${if (isWindows) "javap.exe" else "javap"}").absolutePath
    }

    private fun javapOutput(classFiles: List<File>): String {
        val process =
            ProcessBuilder(listOf(executable, "-p", "-v") + classFiles.map(File::getAbsolutePath))
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "javap 실행 실패(exit=$exitCode, 입력 ${classFiles.size}개)\n$output" }
        return output
    }
}

/**
 * 바이트코드 내부 이름(`a/b/C`)과 이름 기반 class 로드가 남기는 점 표기 좌표(`a.b.C`) 양쪽에서
 * `bidvector…` 조각을 뽑는다 — **좌측 경계를 고정한다**(형제 열 축의 같은 정규식은 고정하지 않는다).
 *
 * 고정하지 않으면 **중첩 네임스페이스의 가운데 토막**을 자기 좌표로 읽는다: `ml` 축이 쓰는 생성 proto
 * 타입의 JVM 패키지가 `contract.bidvector.ml.v1` 이고, 고정 없는 정규식은 그 내부 이름
 * `contract/bidvector/ml/v1/…` 에서 `bidvector/ml/v1/…` 를 뽑아 **오탐 193**을 낸다(실측). 틀리는
 * 방향은 막는 쪽이라 미탐이 아니지만, 그 축을 이 층으로 옮기는 순간 게이트가 거짓으로 붉는다.
 *
 * 형제 열 축은 이 경계가 없고 오늘 그 꼴을 만나는 축이 **0** 이다(`contract.bidvector.*` 를 쓰는
 * adapters 패키지는 `ml` 하나, 전수) — 그 잠복은 `OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` 가 받는다.
 *
 * **경계는 `/` 와 `.` 둘만 배제한다 — 식별자 글자를 배제하면 양성까지 잃는다.** JVM 서술자는
 * `Lbidvector/procurement/Notice;` 꼴이라 좌표 앞 글자가 `L` 이다. 앞 글자를 「식별자 아님」으로
 * 요구하면 그 서술자 전부가 빠지고, 그러면 **오탐도 0 이고 양성도 0** 인 조용한 초록이 된다(이
 * slice 가 실측으로 잡았다: 그 과한 판은 persistence 에서 뽑는 좌표가 677 → 675 로 줄었다).
 * 가리려는 꼴은 「`bidvector` 가 수식 이름의 **첫 세그먼트가 아닌** 자리」이므로 `/`·`.` 만 본다.
 */
internal val SELF_COORDINATE = Regex("""(?<![/.])bidvector[/.][A-Za-z0-9_/.$]+""")

/**
 * 허용 뿌리 술어 — `fqn == 뿌리` 또는 `뿌리.` 접두다. 접두 일치가 **세그먼트 경계**에서만 성립해야
 * 한다: 맨 `startsWith(뿌리)` 는 `bidvector.procurementlegacy` 같은 이웃 좌표를 허용으로 새게 하고
 * 그 새는 이름은 지어낼 수 있다(형제 축들의 같은 단언).
 */
internal fun isOutsideRoots(
    coordinate: String,
    allowedRoots: Set<String>,
): Boolean =
    coordinate.startsWith("bidvector.") &&
        allowedRoots.none { coordinate == it || coordinate.startsWith("$it.") }

/**
 * [classesDir] 아래 컴파일된 class 가 [allowedRoots] **밖**의 자기 저장소 좌표를 참조하는 자리.
 * 반환 문자열은 `class 이름: 좌표` 라 위반이 어느 산출물에서 왔는지 바로 읽힌다.
 */
internal fun disallowedCoordinates(
    classesDir: File,
    allowedRoots: Set<String>,
): List<String> {
    check(classesDir.isDirectory) {
        "빌드 산출물을 찾지 못했다: ${classesDir.absolutePath} — :adapters:compileKotlin 선행 필요"
    }
    val classFiles = BytecodeConstantPool.classFilesUnder(classesDir)
    check(classFiles.isNotEmpty()) { "class 파일을 찾지 못했다: ${classesDir.absolutePath}" }

    var extracted = 0
    val violations =
        BytecodeConstantPool
            .chunksOf(classFiles)
            .flatMap { chunk ->
                val found = SELF_COORDINATE.findAll(chunk.body).map { it.value.replace('/', '.') }.toList()
                extracted += found.size
                found
                    .filter { isOutsideRoots(it, allowedRoots) }
                    .map { "${chunk.classFile.name}: $it" }
            }.distinct()
            .sorted()

    // **추출 층의 비공허 단언**(cr M-2). 위 세 겹(디렉터리·파일·헤더 계수)은 **읽었는가**만 재고
    // **뽑았는가**는 재지 않았다 — 정규식이 조용히 좁아지면 `emptyList()` 가 그대로 통과해 이 게이트가
    // 공허하게 초록이 된다(실측: 좌측 경계를 과하게 조인 변이가 여섯 축 **전부에서 위반 0 그대로**였다).
    // 어느 축이든 자기 패키지 좌표만으로 수백 개를 남기므로(최소 197, 실측) 0 은 술어가 망가진 것이다.
    check(extracted > 0) {
        "상수 풀에서 자기 좌표를 하나도 뽑지 못했다: ${classesDir.absolutePath} — " +
            "`SELF_COORDINATE` 가 좁아졌는지 본다(「위반 0」과 「추출 0」은 다른 말이다)"
    }
    return violations
}
