package bidvector.adapters.evaluation

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(D-6F2-1, `StrategyAdapterDependencyTest`
 * 와 같은 형태·같은 이유). `evaluation` 패키지는 [bidvector.workflow.evaluation]
 * (`CandidateSourcePort`·`CorrelationIdFactory`)·[bidvector.workflow.event](`CorrelationId`)·
 * [bidvector.workflow.strategy](`Clock`)·[bidvector.procurement](`Notice`·`NoticeId`·
 * `NoticeStatus`·`isBiddable`)·`shared-kernel`(`NoticeRound` 등 값 타입)을 봐야 한다.
 * `bidvector.adapters.persistence`는 domain이 아니라 **같은 모듈의 다른 adapter 패키지**다
 * — SQL 문(`Sql.SELECT_OPEN_CANDIDATES`)과 `Notice` 복원 경로([reconstructNotice])가 거기
 * 산다(`PersistenceAdapterDependencyTest`·`StrategyAdapterDependencyTest`의 allow-list를
 * 넓히지 않는다 — 이 test는 그 test들과 별개다). `bidvector.adapters.evaluation` 자신은
 * 이 패키지의 클래스끼리 서로 참조할 수 있어야 하므로 포함한다.
 *
 * **`bidvector.strategy`(M6/6F-4-w, D-6F4W-3 신설)** — [NoticeWatchSubjectPort]가
 * `assembleKeywordScopeText`·`assembleFullScopeText`(순수 커널)를 불러 감시 텍스트를
 * 조립한다(우회 3 — 어댑터가 이어붙이기를 복제하지 않고 커널을 부른다). `WatchSubject`·
 * `CategoryCode`(strategy 쪽) 타입도 이 축에서 함께 참조된다.
 */
private val ALLOWED_ROOTS =
    setOf(
        "bidvector.workflow.evaluation",
        "bidvector.workflow.event",
        "bidvector.workflow.strategy",
        "bidvector.procurement",
        "bidvector.sharedkernel",
        "bidvector.strategy",
        "bidvector.adapters.persistence",
        "bidvector.adapters.evaluation",
    )

private fun isDisallowed(importedPackage: String): Boolean =
    importedPackage.startsWith("bidvector.") &&
        ALLOWED_ROOTS.none { importedPackage == it || importedPackage.startsWith("$it.") }

/**
 * 바이트코드 내부 이름(`a/b/C`)과 이름 기반 클래스 로드가 남기는 점 표기 좌표(`a.b.C`) 양쪽에서
 * `bidvector...` 부분을 뽑는다 — 상수 풀 전체를 훑는다(`StrategyAdapterDependencyTest`와 같은
 * 정규식·같은 근거 — verifier r3 MEDIUM-5).
 */
private val BIDVECTOR_INTERNAL_NAME = Regex("""bidvector[/.][A-Za-z0-9_/.$]+""")

/**
 * D-6F2-1 — **소스 텍스트가 아니라 컴파일된 클래스의 상수 풀을 `javap -p -v`로 훑는다.**
 * import 문 없이 전체 한정 좌표로 직접 참조하는 우회(scope.md 우회 6)를 소스 텍스트 정규식
 * 형태(`EventAdapterDependencyTest`)는 못 본다(6F-1 verifier MUT-E3 실측) — 신설 게이트는
 * 구조로 닫는 쪽만 쓴다(CLAUDE.md 「게이트 술어는 문자열이 아니라 구조로」).
 */
class EvaluationAdapterDependencyTest {
    @Test
    fun `evaluation 패키지의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val classesDir = File("build/classes/kotlin/main/bidvector/adapters/evaluation")
        check(classesDir.isDirectory) {
            "빌드 산출물을 찾지 못했다: ${classesDir.absolutePath} — :adapters:compileKotlin 선행 필요"
        }
        val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        classFiles.shouldNotBeEmpty() // 빈 디렉터리를 "위반 없음"으로 오판하지 않는다.

        val violations = classFiles.flatMap(::disallowedBytecodeReferences).distinct()
        violations shouldBe emptyList()
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다(문자열 판정 자체는 이 값으로 잰다). */
    @Test
    fun `허용 밖 domain 모듈을 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.qualification") shouldBe true
        isDisallowed("bidvector.workflow.ml") shouldBe true
        isDisallowed("bidvector.adapters.ml") shouldBe true
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.workflow.evaluation") shouldBe false
        isDisallowed("bidvector.workflow.event") shouldBe false
        isDisallowed("bidvector.workflow.strategy") shouldBe false
        isDisallowed("bidvector.procurement") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.strategy") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
        isDisallowed("bidvector.adapters.evaluation") shouldBe false
    }

    /**
     * D-6F2-9 ② 참조 단언(verifier r1 HIGH-1 수정) — ①(거동 등식, `JdbcCandidateSourceTest`)
     * 은 SQL이 **낸 결과값**만 잰다. `biddableStatuses()`를 안 쓰고 우연히 같은 리터럴
     * (`"Open"`·`"Renoticed"`)을 SQL에 하드코딩해도 그 값은 통과하므로, 여기서는 컴파일된
     * `JdbcCandidateSource`의 상수 풀이 **실제로 `biddableStatuses`를 참조**하는지를 잰다 —
     * SQL 문자열을 grep 하지 않는다(스타일 하나로 열리는 문자열 술어를 쓰지 않는다,
     * CLAUDE.md 「게이트 술어는 구조로」). 이 상수 풀은 컴파일러가 실제로 만든 참조라
     * `biddableStatuses()` 호출을 지우면(리터럴 배열로 되돌리면) 이 이름 자체가 상수 풀에서
     * 사라진다 — 소스 문법과 무관하게 참조 유무만 재는 구조 게이트다.
     */
    @Test
    fun `JdbcCandidateSource 의 컴파일된 클래스는 biddableStatuses 를 참조한다`() {
        val classFile = File("build/classes/kotlin/main/bidvector/adapters/evaluation/JdbcCandidateSource.class")
        check(classFile.isFile) {
            "빌드 산출물을 찾지 못했다: ${classFile.absolutePath} — :adapters:compileKotlin 선행 필요"
        }

        javapOutput(classFile) shouldContain "biddableStatuses"
    }

    /**
     * D-6F4W-3 참조 단언 — [JdbcCandidateSource]의 `biddableStatuses` 참조 단언과 같은
     * 형태. `NoticeWatchSubjectPort`가 텍스트를 직접 조립하지 않고 `strategy` 커널
     * (`assembleKeywordScopeText`·`assembleFullScopeText`)을 실제로 부르는지, 소스 문법이
     * 아니라 컴파일된 상수 풀로 잰다.
     */
    @Test
    fun `NoticeWatchSubjectPort 의 컴파일된 클래스는 조립 커널을 참조한다`() {
        val classFile = File("build/classes/kotlin/main/bidvector/adapters/evaluation/NoticeWatchSubjectPortKt.class")
        check(classFile.isFile) {
            "빌드 산출물을 찾지 못했다: ${classFile.absolutePath} — :adapters:compileKotlin 선행 필요"
        }

        val output = javapOutput(classFile)
        output shouldContain "assembleKeywordScopeText"
        output shouldContain "assembleFullScopeText"
    }

    /**
     * D-6F4W-7 우회 3 — **상수 풀 허용 목록**(CLAUDE.md 「존재 단언은 못 막는다」, D-6F4W-14
     * 수정). 바로 위 참조 단언은 「커널을 부르는가」만 잰다 — 어댑터가 커널을 부르면서
     * **동시에** 자기 이어붙이기를 옆에 또 두는 우회는 잡지 못한다. 이 test 는 두 class 가
     * 참조하는 `Methodref`/`InterfaceMethodref` 전체가 [ALLOWED_METHOD_REFERENCES]의
     * 부분집합인지를 잰다 — **금지 목록이 아니라 허용 목록**이라 목록 밖 참조는 이름이
     * 무엇이든(`String.join`·`String.format`·`concat`·`StringBuilder` 등) RED 가 된다.
     * 금지 목록(이름 3개 열거)이었을 때는 `java.lang.String.join` 복제가 그 열거를 전부
     * 비켜가 부재 단언·참조 단언·거동 test 8건이 모두 통과했다(verifier r1 MEDIUM-2, m4b
     * 재현). 술어가 실제로 걸리는지는 [허용_밖_참조를_갖는_표본은_이_술어에_걸린다] 양성
     * 대조가 증명한다.
     */
    @Test
    fun `NoticeWatchSubjectPort 의 컴파일된 클래스가 참조하는 메서드는 허용 목록의 부분집합이다`() {
        val classFiles =
            listOf(
                File("build/classes/kotlin/main/bidvector/adapters/evaluation/NoticeWatchSubjectPort.class"),
                File("build/classes/kotlin/main/bidvector/adapters/evaluation/NoticeWatchSubjectPortKt.class"),
            )
        classFiles.forEach { classFile ->
            check(classFile.isFile) {
                "빌드 산출물을 찾지 못했다: ${classFile.absolutePath} — :adapters:compileKotlin 선행 필요"
            }
        }

        val disallowed = classFiles.flatMap(::methodReferences).toSet() - ALLOWED_METHOD_REFERENCES

        disallowed shouldBe emptySet()
    }

    /**
     * 양성 대조 — [ConcatenationMachineryFixture]는 문자열 템플릿(`invokedynamic
     * makeConcatWithConstants`)과 `java.lang.String.join`(verifier m4b 재현) 둘 다로
     * 이어붙인다. 둘 다 [ALLOWED_METHOD_REFERENCES] 밖이라 허용 목록 술어가 실제로 걸린다
     * — 술어가 늘 통과만 하는 회귀를 막는다.
     */
    @Test
    fun `허용 밖 참조를 갖는 표본은 이 술어에 걸린다 — 양성 대조`() {
        val classFile =
            File("build/classes/kotlin/test/bidvector/adapters/evaluation/ConcatenationMachineryFixture.class")
        check(classFile.isFile) {
            "빌드 산출물을 찾지 못했다: ${classFile.absolutePath} — :adapters:compileTestKotlin 선행 필요"
        }

        val disallowed = methodReferences(classFile) - ALLOWED_METHOD_REFERENCES

        disallowed.shouldNotBeEmpty()
    }

    /**
     * M6/6A-3+6F-3 D-6A3-17(a) — HIGH-1(verifier r1) 우회 2 시정. [EvaluationAdapterDependencyTest]
     * 의 `ALLOWED_ROOTS`(패키지 단위 허용)는 `bidvector.adapters.persistence`를 이미 허용하므로
     * (다른 클래스 — `JdbcCandidateSource` 등 — 가 정당하게 그 패키지를 쓴다) `RecordingNotification
     * RequestPort` 를 DataSource 를 받아 영속하도록 바꿔도 그 패키지 단위 게이트는 초록으로
     * 남는다. 이 test 는 `NoticeWatchSubjectPort` 와 같은 **상수 풀 허용 목록**(금지 목록이
     * 아니라 허용 목록 — 이름이 무엇이든 목록 밖은 RED)으로 그 클래스 하나를 별도로 좁혀 잠근다
     * — `persistence`·`java.sql`·`javax.sql` 이 이 목록 어디에도 없어 그 경로로의 회귀는 이름과
     * 무관하게 걸린다.
     */
    @Test
    fun `RecordingNotificationRequestPort 의 컴파일된 클래스가 참조하는 메서드는 허용 목록의 부분집합이다`() {
        val classFile =
            File("build/classes/kotlin/main/bidvector/adapters/evaluation/RecordingNotificationRequestPort.class")
        check(classFile.isFile) {
            "빌드 산출물을 찾지 못했다: ${classFile.absolutePath} — :adapters:compileKotlin 선행 필요"
        }

        val disallowed = methodReferences(classFile) - ALLOWED_RECORDING_METHOD_REFERENCES

        disallowed shouldBe emptySet()
    }

    /**
     * 양성 대조 — [PersistingNotificationPortFixture]는 `RecordingNotificationRequestPort`가
     * 하면 안 되는 것(`DataSource`를 받아 SQL을 실행)을 흉내 낸다. `java.sql`·`javax.sql`
     * 참조가 [ALLOWED_RECORDING_METHOD_REFERENCES] 밖이라 허용 목록 술어가 실제로 걸린다.
     */
    @Test
    fun `RecordingNotificationRequestPort 가 영속하도록 바뀐 표본은 이 술어에 걸린다 — 양성 대조`() {
        val classFile =
            File("build/classes/kotlin/test/bidvector/adapters/evaluation/PersistingNotificationPortFixture.class")
        check(classFile.isFile) {
            "빌드 산출물을 찾지 못했다: ${classFile.absolutePath} — :adapters:compileTestKotlin 선행 필요"
        }

        val disallowed = methodReferences(classFile) - ALLOWED_RECORDING_METHOD_REFERENCES

        disallowed.shouldNotBeEmpty()
    }
}

/**
 * D-6A3-17(a) — [EvaluationAdapterDependencyTest]의 `RecordingNotificationRequestPort` 전용
 * 허용 목록. `RecordingNotificationRequestPort.class`를 `javap -p -v`로 실측해 손으로 옮겼다
 * (2026-09-23, commands.md에 원 출력 대조 기록) — `mutableListOf<NotificationRequest>()`의
 * `ArrayList` 생성자·`Collection.add`(`recorded += notification`)·`CollectionsKt.toList`
 * (`requested()`)·`Intrinsics.checkNotNullParameter`(null 체크)·`Object.<init>` 뿐이다.
 * persistence·`java.sql`·`javax.sql` 이름은 어디에도 없다.
 */
private val ALLOWED_RECORDING_METHOD_REFERENCES =
    setOf(
        "java/lang/Object.\"<init>\":()V",
        "java/util/ArrayList.\"<init>\":()V",
        "kotlin/jvm/internal/Intrinsics.checkNotNullParameter:(Ljava/lang/Object;Ljava/lang/String;)V",
        "java/util/Collection.add:(Ljava/lang/Object;)Z",
        "kotlin/collections/CollectionsKt.toList:(Ljava/lang/Iterable;)Ljava/util/List;",
    )

/**
 * [EvaluationAdapterDependencyTest]의 `RecordingNotificationRequestPort` 허용 목록 전용 양성
 * 대조 표본 — `DataSource`를 받아 SQL을 실행하는 형태를 흉내 낸다. production 코드가 아니다.
 */
internal class PersistingNotificationPortFixture(
    private val dataSource: javax.sql.DataSource,
) {
    fun leak() {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT 1").use { it.execute() }
        }
    }
}

/**
 * D-6F4W-14 — [EvaluationAdapterDependencyTest]의 허용 목록 술어가 쓰는 참조 집합.
 * `NoticeWatchSubjectPort`·`NoticeWatchSubjectPortKt` 두 class 를 `javap -p -v`로 실측해
 * 손으로 옮겼다(2026-09-23, commands.md 에 원 출력 대조 기록). `assemble*` 커널 둘·
 * `assembleWatchCategories` 커널(M6/6F-9 — 관심 업종 집합도 어댑터가 짓지 않고 커널을 부른다, 이전에 있던 `SetsKt`
 * `setOf`·`emptySet` 과 strategy `CategoryCode` 생성자는 이 허용 집합에서 **빠졌다** — 조여졌다)·`Notice`/값 객체
 * getter·`WatchSubject`/`Fact` 생성자·`Intrinsics`(null 체크)·`Object.<init>`·
 * `noticeToWatchSubject`가 `private`이라 Kotlin이 내는 `access$` 합성 접근자(D-6F4W-16)
 * 뿐이다 — 이어붙이기 기계(`StringBuilder`·`makeConcatWithConstants`·`String.join`·
 * `joinToString`류)는 어떤 이름도 여기 없다. **새 이름이 어댑터에 나타나면 이 집합의
 * 부분집합 검사가 그 이름과 무관하게 RED 가 된다** — 금지 목록(이름 열거)과 달리 열거를
 * 비켜가는 우회가 없다.
 */
private val ALLOWED_METHOD_REFERENCES =
    setOf(
        "bidvector/adapters/evaluation/NoticeWatchSubjectPortKt.noticeToWatchSubject:" +
            "(Lbidvector/procurement/Notice;)Lbidvector/strategy/WatchSubject;",
        // noticeToWatchSubject 를 private 으로 좁히면(D-6F4W-16, code-reviewer LOW) Kotlin 이
        // 같은 파일의 다른 class(NoticeWatchSubjectPort)가 부를 수 있도록 합성 접근자를 낸다.
        "bidvector/adapters/evaluation/NoticeWatchSubjectPortKt.access\$noticeToWatchSubject:" +
            "(Lbidvector/procurement/Notice;)Lbidvector/strategy/WatchSubject;",
        "bidvector/procurement/Agency.getName:()Lbidvector/procurement/AgencyName;",
        "bidvector/procurement/AgencyName.getValue:()Ljava/lang/String;",
        "bidvector/procurement/BusinessCategory.getCode:()Lbidvector/procurement/CategoryCode;",
        "bidvector/procurement/BusinessCategory.getLabel:()Lbidvector/procurement/CategoryLabel;",
        "bidvector/procurement/BusinessDivision.getLabel:()Ljava/lang/String;",
        "bidvector/procurement/CategoryCode.getValue:()Ljava/lang/String;",
        "bidvector/procurement/CategoryLabel.getValue:()Ljava/lang/String;",
        "bidvector/procurement/MainConstructionType.getValue:()Ljava/lang/String;",
        "bidvector/procurement/Notice.getBaseAmount:()Lbidvector/procurement/ResolvedBaseAmount;",
        "bidvector/procurement/Notice.getBusinessCategory:()Lbidvector/procurement/BusinessCategory;",
        "bidvector/procurement/Notice.getBusinessDivision:()Lbidvector/procurement/BusinessDivision;",
        "bidvector/procurement/Notice.getDemandAgency:()Lbidvector/procurement/Agency;",
        "bidvector/procurement/Notice.getMainConstructionType:()Lbidvector/procurement/MainConstructionType;",
        "bidvector/procurement/Notice.getNoticeAgency:()Lbidvector/procurement/Agency;",
        "bidvector/procurement/Notice.getServiceDivision:()Lbidvector/procurement/ServiceDivision;",
        "bidvector/procurement/Notice.getTitle:()Lbidvector/procurement/NoticeTitle;",
        "bidvector/procurement/NoticeTitle.getValue:()Ljava/lang/String;",
        "bidvector/procurement/ResolvedBaseAmount.getAmount:()Lbidvector/sharedkernel/BaseAmount;",
        "bidvector/procurement/ServiceDivision.getValue:()Ljava/lang/String;",
        "bidvector/sharedkernel/Fact\$Absent.\"<init>\":(Lbidvector/sharedkernel/ReasonCode;)V",
        "bidvector/sharedkernel/Fact\$Known.\"<init>\":(Ljava/lang/Object;)V",
        "bidvector/strategy/TextKt.assembleFullScopeText:" +
            "(Ljava/lang/String;Ljava/lang/String;" +
            "Ljava/lang/String;Ljava/lang/String;)Lbidvector/strategy/FullScopeText;",
        "bidvector/strategy/TextKt.assembleKeywordScopeText:" +
            "(Ljava/lang/String;Ljava/lang/String;)Lbidvector/strategy/KeywordScopeText;",
        "bidvector/strategy/WatchCategoriesKt.assembleWatchCategories:" +
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/util/Set;",
        "bidvector/strategy/WatchSubject.\"<init>\":" +
            "(Ljava/util/Set;Lbidvector/strategy/KeywordScopeText;" +
            "Lbidvector/strategy/FullScopeText;Lbidvector/sharedkernel/Fact;)V",
        "bidvector/workflow/evaluation/WatchSubjectOutcome\$Found.\"<init>\":(Lbidvector/strategy/WatchSubject;)V",
        "java/lang/Object.\"<init>\":()V",
        "kotlin/jvm/internal/Intrinsics.checkNotNullParameter:(Ljava/lang/Object;Ljava/lang/String;)V",
    )

/** `javap -p -v` 출력의 상수 풀에서 `Methodref`/`InterfaceMethodref` 줄의 원문 좌표 주석만 뽑는다. */
private val METHOD_REFERENCE_COMMENT = Regex("""#\d+ = (?:Interface)?Methodref\s+#\d+\.#\d+\s*// (.+)""")

private fun methodReferences(classFile: File): Set<String> =
    METHOD_REFERENCE_COMMENT
        .findAll(javapOutput(classFile))
        .map { it.groupValues[1].trim() }
        .toSet()

/**
 * [EvaluationAdapterDependencyTest]의 양성 대조 전용 표본 — production 코드가 아니다.
 * `NoticeWatchSubjectPort`처럼 커널을 부르지 않고 직접 이어붙이는 두 형태(문자열 템플릿·
 * `java.lang.String.join`, D-6F4W-14 verifier m4a·m4b 재현)가 [ALLOWED_METHOD_REFERENCES]
 * 술어에 실제로 걸리는지를 증명한다.
 */
internal class ConcatenationMachineryFixture {
    fun concatenate(
        a: String?,
        b: String?,
    ): String = "$a $b"

    fun concatenateViaJoin(
        a: String?,
        b: String?,
    ): String = java.lang.String.join(" ", listOfNotNull(a, b))
}

/**
 * `javap`를 OS `PATH`의 이름 조회가 아니라 **실행 중인 JVM의 `java.home`**에서 해석한다
 * (`StrategyAdapterDependencyTest`와 같은 이유 — CI의 JDK 21과 다른 `javap`를 부를 여지를
 * 없앤다).
 */
private val javapExecutable: String by lazy {
    val javaHome = System.getProperty("java.home")
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    File(javaHome, "bin/${if (isWindows) "javap.exe" else "javap"}").absolutePath
}

/** `javap -p -v`의 원문 출력 — [disallowedBytecodeReferences]와 참조 단언 test가 공유한다. */
private fun javapOutput(classFile: File): String {
    val process =
        ProcessBuilder(javapExecutable, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "javap 실행 실패(exit=$exitCode): ${classFile.name}\n$output" }
    return output
}

private fun disallowedBytecodeReferences(classFile: File): List<String> =
    BIDVECTOR_INTERNAL_NAME
        .findAll(javapOutput(classFile))
        .map { it.value.replace('/', '.') }
        .filter(::isDisallowed)
        .toList()
