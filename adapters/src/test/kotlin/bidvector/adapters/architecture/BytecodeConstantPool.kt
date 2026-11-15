package bidvector.adapters.architecture

import java.io.File

/**
 * **상수 풀을 읽는 한 자리.** adapters 의 dependency test 축 **열넷 전부**가 `javap -p -v` 로 컴파일
 * 산출물을 훑는 자리를 여기서 공유한다 — 축 판정과 두 층 등식 test 의 보유자 단언이 같은 자리를 쓴다.
 *
 * **왜 파일마다가 아니라 일괄인가 — 측정이 꼴을 정했다.** 앞 판의 형제 열 축은 class 파일 하나마다
 * 프로세스 하나를 띄웠다. 그 열 축의 class **140** 개를 파일마다 읽으면 `javap` 벽시계가 **47.5초**
 * 이고 축마다 일괄 한 번이면 **5.8초**다(이 slice 착수 실측 — 같은 호스트·같은 조건에서 잰 쌍).
 * 그래서 이 꼴은 선택이 아니라 필요다.
 *
 * `javap` 는 OS `PATH` 의 이름 조회가 아니라 **실행 중인 JVM 의 `java.home`** 에서 해석한다(CI 의
 * JDK 21 과 다른 `javap` 를 부를 여지를 없앤다).
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
        val wholes =
            headers.mapIndexed { index, header ->
                output.substring(header.range.last + 1, headers.getOrNull(index + 1)?.range?.first ?: output.length)
            }

        // **토막이 출력을 빈틈없이 덮는가**(v L-1). 헤더 계수는 「몇 개를 읽었나」만 재고 **본문이
        // 잘렸는지는 못 잡는다** — verifier 가 마지막 토막의 본문을 비우고 그 class 에 위반을 심었을 때
        // 헤더 수는 입력 수와 같았고 판정이 그 위반을 보지 못했다. 덮개 등식은 per-file 비용 없이
        // 그 꼴을 상시로 잠근다.
        val covered = headers.first().range.first + headers.sumOf { it.value.length } + wholes.sumOf(String::length)
        check(covered == output.length) {
            "토막이 javap 출력을 빈틈없이 덮지 않는다(본문 절단): 덮은 길이 $covered != 출력 ${output.length}"
        }

        return headers.mapIndexed { index, header ->
            val whole = wholes[index]
            val poolAt = whole.indexOf(CONSTANT_POOL_MARKER)
            check(poolAt >= 0) {
                "javap 출력에 '$CONSTANT_POOL_MARKER' 이 없다: ${header.groupValues[1].trim()} — " +
                    "모든 class 파일은 상수 풀을 갖는다. 출력 형태가 바뀐 것이고 조용히 넘기면 안 된다"
            }
            Chunk(File(header.groupValues[1].trim()), whole.substring(poolAt))
        }
    }

    /** class 파일 **하나**의 상수 풀 본문. 축 고유의 점검들이 쓰는 자리다. */
    fun bodyOf(classFile: File): String = chunksOf(listOf(classFile)).single().body

    /**
     * 한 class 파일의 `javap -p -v` 출력 토막. [classFile] 은 헤더가 적은 경로다(입력 순서가 아니다).
     *
     * **[body] 는 `Constant pool:` 줄에서 시작한다 — 그 앞의 javap 머리말을 담지 않는다.**
     * 머리말에는 `Classfile <경로>` 뿐 아니라 **class 선언 줄**(`public final class
     * bidvector.adapters.<축>.X`)과 `this_class:`·`super_class:` 주석이 있고, 셋 다 자기 좌표를
     * 들고 있다. 담아 두면 **좌표 추출이 상수 풀을 한 글자도 못 읽어도 비지 않는다** — 즉
     * `disallowedCoordinates` 의 「하나도 못 뽑았다」 가드가 정규식이 망가져도 선다.
     *
     * **머리글 줄만 떼던 앞 판이 실제로 그랬다**(cr MEDIUM-1 실측): 상수 풀 꼴(`a/b/C`)을 버리고
     * 점 표기만 남기는 축소 변이가 적중을 56500 → 3953 으로 줄였는데도 **추출 0 인 class 가
     * 534 중 0** 이라 가드가 초록이었다. 경로가 하던 일을 class 선언 줄의 점 표기 FQN 이 그대로
     * 이어받았기 때문이다. `Constant pool:` 에서 끊으면 세는 수가 **실제 상수 풀 적중**이 된다.
     *
     * 축 고유의 `contains` 점검도 같은 자리에서 **자기 파일 이름·class 이름에 우연히 걸리는 길**을
     * 잃는다. 바늘은 전부 상수 풀과 `Code` 쪽이라 오늘 답은 바뀌지 않는다(전수 실측).
     */
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

    private const val CONSTANT_POOL_MARKER = "Constant pool:"

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
 * `bidvector…` 조각을 뽑는다 — **좌측 경계를 고정한다. 이제 축 열넷 전부가 이 하나를 쓴다.**
 *
 * 고정하지 않으면 **중첩 네임스페이스의 가운데 토막**을 자기 좌표로 읽는다: `ml` 축이 쓰는 생성 proto
 * 타입의 JVM 패키지가 `contract.bidvector.ml.v1` 이고, 고정 없는 정규식은 그 내부 이름
 * `contract/bidvector/ml/v1/…` 에서 `bidvector/ml/v1/…` 를 뽑아 **허용 밖 좌표(distinct) 308**을 낸다
 * (실측 — 세는 단위가 다르면 수가 다르다: test 가 내는 `class: 좌표` 쌍으로는 **474**다). 틀리는
 * 방향은 막는 쪽이라 미탐이 아니지만, 그 축을 이 층으로 옮기는 순간 게이트가 거짓으로 붉는다.
 *
 * **앞 판의 형제 열 축은 이 경계가 없었다. 상수 풀만 보면 그 둘은 같은 답을 낸다** — 실측:
 * 열세 축에서 경계 고정 집합과 비경계 집합이 **쌍 단위로 완전히 같고**(`A \ U` = `U \ A` = 0),
 * 다른 축은 `ml` 하나뿐이며 거기서만 비경계가 **474** 쌍을 더 뽑는다(중첩 이름공간 오탐).
 * 경계가 버리는 것은 **오탐뿐이고 실제 좌표의 손실은 0** 이다.
 *
 * **경계는 `/` 와 `.` 둘만 배제한다 — 식별자 글자를 배제하면 서술자에만 나타나는 좌표를 잃는다.**
 * JVM 서술자는 `Lbidvector/procurement/Notice;` 꼴이라 좌표 앞 글자가 `L` 이다. 앞 글자를 「식별자
 * 아님」으로 요구하면 그 꼴이 빠진다 — 손실은 **서술자에만 나타나는 좌표**이고 실측으로
 * `persistence` **2** · `ml` **3**, 나머지 네 축 **0** 이다(뽑는 좌표의 대부분은 다른 꼴로도 나타나
 * 살아남는다: persistence 677 → 675). 수가 작아도 그 판을 버리는 이유는 **오탐이 0 인 것만 보면 두
 * 판이 구별되지 않기 때문**이다 — 양성을 잃는 변화를 위반 수가 감추는 자리다(cr r1 L-1 정정).
 * 가리려는 꼴은 「`bidvector` 가 수식 이름의 **첫 세그먼트가 아닌** 자리」이므로 `/`·`.` 만 본다.
 */
internal val SELF_COORDINATE = Regex("""(?<![/.])bidvector[/.][A-Za-z0-9_/.$]+""")

/**
 * 허용 뿌리 술어 — `fqn == 뿌리` 또는 `뿌리.` 접두다. 접두 일치가 **세그먼트 경계**에서만 성립해야
 * 한다: 맨 `startsWith(뿌리)` 는 `bidvector.procurementlegacy` 같은 이웃 좌표를 허용으로 새게 하고
 * 그 새는 이름은 지어낼 수 있다. 그 모양을 잡는 대조는 축마다 손으로 적은 표본이 아니라
 * [ScopedBytecodeLayerGateTest] 의 **구성 표본**(허용 뿌리마다 `뿌리 + "x"`)이 든다.
 *
 * **열넷 축의 열넷 벌이 이 하나로 모였다** — 앞 판의 열 벌은 인자 이름 하나만 다른 같은 식이었다.
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
    // 세는 본문은 `Constant pool:` 에서 시작한다 — javap 머리말(경로 · class 선언 줄 · `this_class`)이
    // 자기 좌표를 들고 있어 **상수 풀을 한 글자도 못 읽어도** 이 단언이 서던 자리를 `Chunk` 가 구조로
    // 없앴다(그 KDoc · cr MEDIUM-1). 그래서 이 수는 **상수 풀 적중**의 수다.
    // **이 가드가 잡는 것은 추출이 통째로 멈추는 꼴이다** — 정규식이 **좁아지는** 것은 잡지 못한다.
    // 상수 풀에도 점 표기 문자열이 남아, 슬래시 꼴을 버리는 변이에서도 축별 합이 0 이 되지 않는다
    // (실측: 축마다 8~680). 정규식의 **모양**은 `BytecodeConstantPoolTest` 의 구성 표본이 진다.
    check(extracted > 0) {
        "상수 풀에서 자기 좌표를 하나도 뽑지 못했다: ${classesDir.absolutePath} — " +
            "`SELF_COORDINATE` 가 좁아졌는지 본다(「위반 0」과 「추출 0」은 다른 말이다)"
    }
    return violations
}
