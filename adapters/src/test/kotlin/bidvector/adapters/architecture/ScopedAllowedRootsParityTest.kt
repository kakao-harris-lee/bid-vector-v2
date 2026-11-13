package bidvector.adapters.architecture

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.Properties
import bidvector.adapters.audit.ALLOWED_ROOTS as AUDIT_ROOTS
import bidvector.adapters.evaluation.ALLOWED_ROOTS as EVALUATION_ROOTS
import bidvector.adapters.event.ALLOWED_ROOTS as EVENT_ROOTS
import bidvector.adapters.extraction.ALLOWED_ROOTS as EXTRACTION_ROOTS
import bidvector.adapters.koneps.ALLOWED_ROOTS as KONEPS_ROOTS
import bidvector.adapters.ml.ALLOWED_ROOTS as ML_ROOTS
import bidvector.adapters.notice.ALLOWED_ROOTS as NOTICE_ROOTS
import bidvector.adapters.notification.ALLOWED_ROOTS as NOTIFICATION_ROOTS
import bidvector.adapters.opening.ALLOWED_ROOTS as OPENING_ROOTS
import bidvector.adapters.persistence.ALLOWED_ROOTS as PERSISTENCE_ROOTS
import bidvector.adapters.profile.ALLOWED_ROOTS as PROFILE_ROOTS
import bidvector.adapters.qualification.ALLOWED_ROOTS as QUALIFICATION_ROOTS
import bidvector.adapters.relay.ALLOWED_ROOTS as RELAY_ROOTS
import bidvector.adapters.strategy.ALLOWED_ROOTS as STRATEGY_ROOTS

/**
 * **같은 허용 뿌리 목록이 두 층에 살아 어긋나는 자리를 축 전부에서 잰다**(`OPEN-SCOPED-ROOTS-PARITY-REST`).
 *
 * 범위 축의 허용 뿌리는 두 벌 있다 — 바이트코드 층(각 축 dependency test 의 `ALLOWED_ROOTS`, 상수 풀을
 * `javap` 로 훑는다)과 이름 층(`architecture-policy.properties` 의 `source.scoped-allowed-roots.<축>`, PSI 를
 * 보는 `domainSourceReferenceGate`). 두 층은 **같은 규칙의 다른 표현**이고 분담 이유는 `const val` 인라인
 * 사각이다(인라인되면 상수 풀에서 사라지지만 이름은 소스에 남는다). 목록이 갈리면 그 분담이 깨진다.
 *
 * **그 어긋남이 2026-11 여덟째 병합에서 실제로 일어났다** — 상류가 바이트코드 층에만 뿌리를 더해 이름 층
 * 게이트가 붉었다. 그쪽 방향은 게이트가 잡아 주지만 **반대 방향은 아무것도 붉지 않는다**: 이름 층 정책만
 * 넓어지면 두 층 가운데 넓은 쪽이 조용히 넓어진 채로 남는다(이 slice 가 음성 대조로 재현했다 — 그 뿌리를
 * 더해도 정책 키를 읽는 자리 전수가 초록이었다). 그래서 단언은 포함이 아니라 **집합 등식**이다.
 *
 * **앞 판보다 주장이 넓다.** 앞 판은 축 하나(`evaluation`)를 손으로 적은 **열거**라 아홉째 축이 생기면
 * 조용했다. 이 판은 축 → 바이트코드 층 목록 [BYTECODE_LAYER_ROOTS] 하나를 두고 **그 key 집합과
 * `source.scoped-packages` 를 집합 등식으로** 잰다 — 축을 더하거나 빼면 그 등식이 깨진다. registry 의 값은
 * 문자열이 아니라 **컴파일되는 심볼 참조**이므로 어느 축의 `ALLOWED_ROOTS` 를 지우거나 이름을 바꾸면
 * 컴파일이 깨진다(CLAUDE.md 「게이트 술어는 문자열이 아니라 구조로 닫는다」).
 *
 * 등식이 **공허해지는 길들도 함께 막는다** — ① 축이 `source.scoped-packages` 모집단에서 빠지면 이름 층은
 * 그 패키지를 아예 판정하지 않으므로 등식이 「판정하지 않는 목록」에 대한 참이 된다 ② 어느 한쪽이 비면
 * 「둘 다 비었다」가 등식을 만족시킨다 ③ 모집단 자체가 비면 아무 범위도 잠기지 않는다 ④ key 가 실재하지
 * 않는 패키지를 가리키거나 값이 **다른 축의** 집합이면 등식이 **딴 축에 대한 참**이 된다 — 그 길은 아래
 * 네 번째 단언이 닫는다(verifier F-2 가 ①~③ 만으로 ④ 가 열린다는 반례 둘을 냈다).
 *
 * 술어 자체의 동치(`fqn == 뿌리 || fqn.startsWith("뿌리.")`)는 이 test 가 아니라 두 층의 술어 test 가 든다 —
 * 바이트코드 층은 각 축 dependency test 의 양성 대조, 이름 층은 build-logic 의 `SourceReferencePolicyTest`다.
 * **두 목록이 「옳은가」도 이 test 의 대상이 아니다** — 둘이 함께 넓어지면 조용하고, 그 자리는 ADR·리뷰가 든다.
 */
class ScopedAllowedRootsParityTest {
    @Test
    fun `이름 층이 판정하는 축 집합은 바이트코드 층 목록을 가진 축 집합과 같다`() {
        val scopes = readPolicy().list(SCOPED_PACKAGES)

        // 모집단이 비면 아래 축별 등식이 아무 범위도 잠그지 않는다.
        scopes.shouldNotBeEmpty()

        scopes.toSet() shouldBe BYTECODE_LAYER_ROOTS.keys
    }

    @Test
    fun `축마다 두 층의 허용 뿌리가 같은 집합이다`() {
        val policy = readPolicy()
        val nameLayer = BYTECODE_LAYER_ROOTS.keys.associateWith { policy.list(SCOPED_ROOTS_PREFIX + it).toSet() }

        // 빈 집합끼리의 등식은 아무 범위도 잠그지 않는다 — 어느 축의 어느 층도 비어 있지 않다.
        val emptySides =
            BYTECODE_LAYER_ROOTS.keys.filter {
                nameLayer.getValue(it).isEmpty() || BYTECODE_LAYER_ROOTS.getValue(it).isEmpty()
            }
        emptySides shouldBe emptyList()

        // 축마다 `shouldBe` 를 루프로 돌리지 않는다 — kotest hard assertion 은 첫 불일치에서 멈춰
        // 나머지 축의 어긋남을 가린다(`CleanMigrationCheckTest` 의 표 단위 map 등식과 같은 꼴).
        nameLayer shouldBe BYTECODE_LAYER_ROOTS
    }

    /**
     * **최장 접두 사각.** `SourceReferencePolicy.scopeOf` 는 패키지를 품는 축 가운데 **가장 긴 것**을 고른다.
     * 어느 축이 다른 축의 하위 패키지면 그 하위 축의 뿌리 목록이 상위 축의 것을 **가리고**, 상위 축의 등식은
     * 자기 key 만 보므로 조용히 초록으로 남는다 — 넓은 목록을 가진 하위 축 하나로 상위 축의 범위를 벗어난다.
     * 축끼리 서로 접두가 아니면 패키지마다 후보가 하나뿐이고, 그래서 위 축별 등식이 이름 층 판정 모집단
     * **전체**를 덮는다.
     */
    @Test
    fun `어느 축도 다른 축의 패키지 접두가 아니다 — 최장 접두 선택의 후보는 축마다 하나다`() {
        val scopes = BYTECODE_LAYER_ROOTS.keys

        val nested =
            scopes
                .flatMap { outer -> scopes.filter { it.startsWith("$outer.") }.map { inner -> "$outer < $inner" } }
                .sorted()

        nested shouldBe emptyList()
    }

    /**
     * **표의 key 가 그 값 심볼의 선언 패키지와 묶여 있지 않으면 위 등식들이 딴 축에 대한 참이 된다**
     * (verifier F-2). 컴파일은 심볼의 **존재**만 잠그고 **어느 패키지에서 왔는지**는 잠그지 않아 두 우회가
     * 열려 있었다 — ① **별칭 바꿔치기**: 한 축의 자리에 다른 축의 집합을 별칭으로 끼우고 그 축의 정책을
     * 그 집합으로 넓힌다 ② **유령 축 개명**: 정책의 축 이름과 key 를 함께 바꾸면 이름 층이 실재하는
     * 패키지를 **더는 판정하지 않는데** 모집단·축별 등식은 그대로 성립한다. 둘 다 처방 전 초록이었다.
     *
     * 그래서 key 를 산문이 아니라 **컴파일 산출물**에서 얻는다 — 이 test 자신의 class 파일 상수 풀에 남는
     * `ALLOWED_ROOTS` getter 호출의 **소유 class**(각 축 file facade)가 그 값이 선언된 패키지다. ① 은 그
     * 축의 facade 참조가 사라져 뽑힌 집합이 작아지고, ② 는 key 가 가리키는 패키지가 그 집합에 없다. 둘 다
     * 우회에는 게이트 자신을 바꾸는 편집이 필요하다(CLAUDE.md 「게이트 술어는 문자열이 아니라 구조로
     * 닫는다」). 읽는 class 출력은 `:adapters:test` 의 **선언 입력**이라 표를 고치면 task 가 다시 돈다.
     *
     * **남는 꼴은 등재했다** — 표에 쓰지 않는 참조를 같은 파일에 더해 패키지 집합을 메꾸는 것, 그리고
     * 실재하지 않는 production 패키지 이름으로 test 파일까지 만드는 것(`scope.md` 설계 검토 (2)).
     */
    @Test
    fun `표의 key 는 그 값 심볼이 선언된 패키지다 — 별칭 바꿔치기·유령 축`() {
        val declaringPackages = rootsOwnersByPackage().keys

        // 추출이 조용히 아무것도 못 뽑으면 이 등식이 공허해진다 — 추출 층의 양성 대조.
        declaringPackages.shouldNotBeEmpty()

        declaringPackages shouldBe BYTECODE_LAYER_ROOTS.keys
    }

    /**
     * **표의 값이 소유 getter 호출 결과 「그 자체」인가**(10C 등재 ⓐ, verifier N-1). 위 단언은
     * 「이 class 가 그 패키지의 getter 를 **부른다**」만 보므로, 표의 값을 **식**으로 적으면
     * (`"…audit" to AUDIT_ROOTS + NOTICE_ROOTS`) 두 getter 가 다 불려 패키지 집합이 그대로이고
     * 정책 한 줄만 넓히면 끝이었다 — 별칭 바꿔치기도 필요 없는 한 토큰짜리 우회다(이 slice 가
     * 음성 대조로 재현했다: 처방 전 네 단언 전부 초록).
     *
     * 그래서 소유 class 의 getter 를 **리플렉션으로 실제로 불러** 표의 값과 **동일성**(`===`)을
     * 잰다. 집합 연산(`+`)은 새 인스턴스를 만들므로 내용이 같아도 동일성이 깨진다. 로드 대상은
     * **상수 풀에서 뽑은 소유자**뿐이고 정책 값·파일 내용에서 오지 않는다 — 임의 이름을 로드하는
     * 자리를 만들지 않는다. 반환값은 **동일성 비교에만** 쓰고 내용으로 판정하지 않는다.
     *
     * **패키지마다 소유자가 하나**인 것도 여기서 잠근다 — 둘이면 같은 메꾸기의 다른 꼴이다.
     */
    @Test
    fun `표의 값은 그 소유 getter 가 돌려주는 바로 그 집합이다 — 값 식 메꾸기`() {
        val owners = rootsOwnersByPackage()

        owners.keys shouldBe BYTECODE_LAYER_ROOTS.keys

        val notIdentical =
            BYTECODE_LAYER_ROOTS.keys
                .filterNot { scope ->
                    invokeRootsGetter(owners.getValue(scope)) === BYTECODE_LAYER_ROOTS.getValue(scope)
                }.sorted()

        notIdentical shouldBe emptyList()
    }

    /**
     * **표의 값이 그 축 게이트가 실제로 판정에 쓰는 집합인가**(10C 등재 ⓑ, verifier N-3). 위 두
     * 단언은 소유자의 **패키지**에만 묶이고 **심볼**에는 묶이지 않아, 같은 패키지에 보유자를 하나 더
     * 두면(`internal object AuditWide { val ALLOWED_ROOTS = … }`) 표를 그 심볼로 바꾸는 것으로
     * 통과했다 — 리플렉션 동일성도 그 `object` 를 상대로 성립하므로 위 처방으로 닫히지 않는다(이
     * slice 가 음성 대조로 재현했다: 처방 전 네 단언 전부 초록).
     *
     * 그래서 **축 패키지의 보유자가 하나이고 그것이 표의 소유자**임을 잰다. 장식으로 남은 옛
     * `ALLOWED_ROOTS` 가 두 번째 보유자로 걸리므로 표만 바꾸는 편집이 통과하지 못하고, 통과하려면
     * **그 축의 게이트가 실제로 그 집합으로 판정하도록** 고쳐야 한다 — 그러면 바이트코드 층도 함께
     * 넓어져 거짓이 사라진다(= 이 slice 설계 검토의 「양쪽을 함께 넓힌다」, 경계로 선언한 자리).
     * 같은 등식이 「판정 집합을 파일 안에서 다른 심볼로 옮기고 `ALLOWED_ROOTS` 는 장식으로 남긴다」
     * (10C 등재)도 함께 닫는다.
     *
     * **읽는 비용을 바이트 선별이 낸다** — class 파일 바이트에 `ALLOWED_ROOTS` 글자가 없으면 그
     * class 는 그 필드·getter 를 참조할 수 없다(상수 풀 UTF8 항목에 그 글자가 반드시 남는다). 그래서
     * 선별은 **상위집합이고 미탐이 없다**. 열넷 축의 test class 를 전부 `javap` 하면 58초이고, 선별
     * 뒤 일괄 호출은 1.6초다(실측).
     */
    @Test
    fun `축 패키지의 허용 뿌리 보유자는 하나이고 표의 소유자다 — 같은 패키지 다른 보유자`() {
        val owners = rootsOwnersByPackage()
        val holders = rootsHoldersByScope()

        // 선별이 조용히 아무것도 남기지 않으면 아래 등식이 공허해진다 — 선별 층의 양성 대조.
        holders.keys shouldBe BYTECODE_LAYER_ROOTS.keys

        holders shouldBe owners.mapValues { (_, owner) -> setOf(owner) }
    }

    private companion object {
        const val SCOPED_PACKAGES = "source.scoped-packages"
        const val SCOPED_ROOTS_PREFIX = "source.scoped-allowed-roots."
        const val LOCATION_PROPERTY = "bidvector.architecture.policy"

        /**
         * 축 → **바이트코드 층**의 허용 뿌리. 값은 각 축 dependency test 가 실제로 판정에 쓰는 그 집합
         * 자체이고(별칭 import 한 심볼), 그래서 목록을 세 번째로 적지 않는다. key 집합은 위 첫 test 가
         * `source.scoped-packages` 와 등식으로 묶으므로 이 표는 **열거가 아니라 구성**이다.
         */
        val BYTECODE_LAYER_ROOTS: Map<String, Set<String>> =
            mapOf(
                "bidvector.adapters.audit" to AUDIT_ROOTS,
                "bidvector.adapters.evaluation" to EVALUATION_ROOTS,
                "bidvector.adapters.event" to EVENT_ROOTS,
                "bidvector.adapters.extraction" to EXTRACTION_ROOTS,
                "bidvector.adapters.koneps" to KONEPS_ROOTS,
                "bidvector.adapters.ml" to ML_ROOTS,
                "bidvector.adapters.notice" to NOTICE_ROOTS,
                "bidvector.adapters.notification" to NOTIFICATION_ROOTS,
                "bidvector.adapters.opening" to OPENING_ROOTS,
                "bidvector.adapters.persistence" to PERSISTENCE_ROOTS,
                "bidvector.adapters.profile" to PROFILE_ROOTS,
                "bidvector.adapters.qualification" to QUALIFICATION_ROOTS,
                "bidvector.adapters.relay" to RELAY_ROOTS,
                "bidvector.adapters.strategy" to STRATEGY_ROOTS,
            )

        /**
         * 경로는 빌드가 넘긴다(`adapters/build.gradle.kts` 의 선언 입력 + system property, 이 모듈의
         * `bidvector.contracts.policy` 관례와 같은 형태). 작업 디렉터리 상대 경로에 기대면 실행 위치가
         * 바뀔 때 조용히 깨지고, 선언 입력이 아니면 정책만 고친 뒤 `:adapters:test` 가 UP-TO-DATE 로
         * 건너뛰어 이 등식들이 **돌지 않은 채 초록**이 된다(이 slice 가 음성 대조로 실측했다).
         */
        fun readPolicy(): Map<String, String> {
            val path =
                System.getProperty(LOCATION_PROPERTY)
                    ?: error("시스템 속성 '$LOCATION_PROPERTY' 가 없다 — adapters/build.gradle.kts 가 넘긴다")
            val file = File(path)
            check(file.isFile) { "아키텍처 정책 파일을 찾지 못했다: ${file.absolutePath}" }
            return file.reader(Charsets.UTF_8).use { reader ->
                Properties()
                    .apply { load(reader) }
                    .entries
                    .associate { (key, value) -> key.toString() to value.toString() }
            }
        }

        /** build-logic `requireList` 와 같은 분해다 — 줄 이음은 `Properties` 가 이미 푼다. */
        fun Map<String, String>.list(key: String): List<String> =
            (this[key] ?: error("정책 키 '$key' 가 없다"))
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)

        /**
         * 패키지 → [BYTECODE_LAYER_ROOTS] 값 심볼의 **소유 class 내부 이름**. `internal` top-level
         * `val` 은 다른 파일에서 **file facade 의 static getter 호출**로 읽히므로 그 호출의 소유
         * class 가 선언 패키지를 그대로 짊어진다 — 별칭으로 가려도 상수 풀은 그 자리를 바꾸지 않는다.
         *
         * 한 패키지에 소유자가 둘 이상이면 **여기서 명시 실패**한다. 그 꼴은 표의 값을 식으로 적어
         * 두 getter 를 함께 부르는 메꾸기이고, 「둘 가운데 하나」를 골라 돌려주면 위 단언들이 어느
         * 쪽을 본 것인지 알 수 없게 된다.
         */
        fun rootsOwnersByPackage(): Map<String, String> {
            val owners =
                BytecodeConstantPool
                    .chunksOf(ownClassFiles())
                    .flatMap { chunk -> ROOTS_GETTER_OWNER.findAll(chunk.body).map { it.groupValues[1] } }
                    .distinct()
            val byPackage = owners.groupBy { it.substringBeforeLast('/').replace('/', '.') }
            val ambiguous = byPackage.filterValues { it.size > 1 }.keys.sorted()
            check(ambiguous.isEmpty()) {
                "한 패키지에 허용 뿌리 소유자가 둘 이상이다(표의 값이 식인가) — $ambiguous"
            }
            return byPackage.mapValues { (_, found) -> found.single() }
        }

        /**
         * [owner] 의 `ALLOWED_ROOTS` getter 를 리플렉션으로 불러 **그 인스턴스**를 돌려준다. 실측:
         * 소유자는 file facade 이고 getter 는 `public static final java.util.Set getALLOWED_ROOTS()`
         * 라 가시성을 열 필요가 없다. 로드·조회·호출의 어느 실패도 **명시 실패**다 — 조용히 `null`
         * 을 돌려주면 동일성 단언이 「둘 다 없다」로 성립해 공허해진다.
         */
        fun invokeRootsGetter(owner: String): Any {
            val binaryName = owner.replace('/', '.')
            val loaded =
                runCatching { Class.forName(binaryName, false, ScopedAllowedRootsParityTest::class.java.classLoader) }
                    .getOrElse { error("허용 뿌리 소유 class 를 로드하지 못했다: $binaryName — $it") }
            val getter =
                runCatching { loaded.getDeclaredMethod("getALLOWED_ROOTS") }
                    .getOrElse { error("소유 class 에 getALLOWED_ROOTS 가 없다: $binaryName — $it") }
            // top-level `internal val` 은 file facade 의 **static** getter 다. static 이 아니면 소유자가
            // facade 가 아니라 인스턴스 보유자(`object` 등)이고, 그 꼴은 아래 보유자 단일성 단언이 받는다 —
            // 여기서는 `invoke(null)` 의 NPE 가 아니라 그 사실을 말하는 실패를 낸다.
            check(Modifier.isStatic(getter.modifiers)) {
                "허용 뿌리 getter 가 static 이 아니다: $binaryName — 표의 값이 top-level `val` 이 아니라 " +
                    "인스턴스 보유자에 산다(보유자 단일성 단언을 함께 읽어라)"
            }
            return runCatching { getter.invoke(null) }
                .getOrElse { error("허용 뿌리 getter 호출이 실패했다: $binaryName — $it") }
                ?: error("getALLOWED_ROOTS 가 null 을 돌려줬다: $binaryName")
        }

        /**
         * 축 → 그 **축 패키지의 test class 파일**이 참조하는 `ALLOWED_ROOTS` 보유자 집합. 읽는 것은
         * 이 test 자신과 같은 `:adapters:test` 출력이라 선언 입력이 그대로다(정책만 고친 뒤
         * `UP-TO-DATE` 로 건너뛰는 자리가 생기지 않는다).
         *
         * 바이트 선별이 상위집합인 근거는 이 class 의 KDoc 이 든다. 선별 뒤 **일괄 `javap` 한 번**으로
         * 읽고 `Classfile` 헤더로 축에 귀속한다.
         */
        fun rootsHoldersByScope(): Map<String, Set<String>> {
            val root = testClassesRoot()
            val byScope =
                BYTECODE_LAYER_ROOTS.keys.associateWith { scope ->
                    val directory = File(root, scope.replace('.', '/'))
                    check(directory.isDirectory) { "축 패키지의 test 출력을 찾지 못했다: ${directory.absolutePath}" }
                    BytecodeConstantPool.classFilesUnder(directory).filter(::mentionsAllowedRoots)
                }
            val empty = byScope.filterValues(List<File>::isEmpty).keys.sorted()
            check(empty.isEmpty()) { "축 패키지에서 허용 뿌리를 쥔 class 를 못 찾았다 — $empty" }

            val chunks = BytecodeConstantPool.chunksOf(byScope.values.flatten())
            return byScope.mapValues { (scope, files) ->
                val paths = files.map(File::getCanonicalPath).toSet()
                chunks
                    .filter { it.classFile.canonicalPath in paths }
                    .flatMap { chunk -> ROOTS_HOLDER.findAll(chunk.body).map { it.groupValues[1] } }
                    .toSet()
                    .ifEmpty { error("축 '$scope' 의 보유자를 상수 풀에서 못 뽑았다") }
            }
        }

        /**
         * class 파일 바이트에 `ALLOWED_ROOTS` 글자가 있는가 — 그 필드·getter 를 참조하는 class 는
         * 상수 풀 UTF8 항목에 그 글자를 **반드시** 남기므로 이 선별은 상위집합이다. `ISO_8859_1` 은
         * 바이트를 글자에 1:1 로 대응시켜 ASCII 바늘의 부분 문자열 검색이 정확하다.
         */
        fun mentionsAllowedRoots(classFile: File): Boolean =
            String(classFile.readBytes(), Charsets.ISO_8859_1).contains(ROOTS_SYMBOL)

        /**
         * test class 출력의 뿌리 — 자기 class 파일 자리에서 **자기 패키지 깊이만큼** 올라간다. 경로를
         * 적지 않는 것은 [ownClassFiles] 와 같은 이유다(실행 위치가 바뀌어도 깨지지 않는다).
         */
        fun testClassesRoot(): File {
            var directory = ownClassDirectory()
            repeat(ScopedAllowedRootsParityTest::class.java.packageName.split('.').size) {
                directory = directory.parentFile ?: error("test class 출력의 뿌리까지 올라가지 못했다")
            }
            return directory
        }

        /**
         * 이 test 자신의 class 파일 — **정확히 자기 class 와 그 `$` 중첩**만 센다. 앞 판은 class 이름
         * **접두**로 모아서 `…ParityTestFillKt` 같은 **별도 파일**의 facade 까지 읽었고, 그 파일에
         * 표에 쓰지 않는 참조를 두면 선언 패키지 집합을 메꿀 수 있었다(10C 등재 ⓒ, verifier N-2 —
         * 이 slice 가 음성 대조로 재현했다: 별칭 바꿔치기만 하면 RED, 거기에 `…Fill` 파일을 더하면
         * 다시 초록). 표의 초기화가 바깥 class 의 `<clinit>` 에서 일어나든 `Companion` 에서
         * 일어나든 둘 다 이 필터에 든다(`X.class` 와 `X$Companion.class`).
         *
         * 자기 **file facade**(`…ParityTestKt.class`)는 접두가 같지만 `$` 중첩이 아니라 빠진다 —
         * 오늘 그 facade 에 `getALLOWED_ROOTS` 참조는 **0** 이고(실측) 표는 `Companion` 에 산다.
         */
        fun ownClassFiles(): List<File> {
            val simpleName = ScopedAllowedRootsParityTest::class.java.simpleName
            val directory = ownClassDirectory()
            val files =
                directory
                    .listFiles()
                    .orEmpty()
                    .filter { it.isFile && (it.name == "$simpleName.class" || it.name.startsWith("$simpleName\$")) }
                    .filter { it.extension == "class" }
                    .sortedBy(File::getPath)
            check(files.isNotEmpty()) { "class 파일을 찾지 못했다: ${directory.absolutePath}" }
            return files
        }

        private fun ownClassDirectory(): File {
            val anchor = ScopedAllowedRootsParityTest::class.java
            val own =
                anchor.getResource("${anchor.simpleName}.class")
                    ?: error("자기 class 파일을 class loader 가 주지 않았다 — 이 술어는 컴파일 산출물을 읽는다")
            return File(own.toURI()).parentFile
        }
    }
}

/** 두 술어가 바이트 선별과 상수 풀에서 같은 이름을 보도록 한 자리에서 적는다. */
private const val ROOTS_SYMBOL = "ALLOWED_ROOTS"

/**
 * `javap -p -v` 출력에서 `ALLOWED_ROOTS` **필드 또는 getter** 의 소유 class 내부 이름. 상수 풀 항목
 * (`Fieldref … // owner.ALLOWED_ROOTS:…`)과 본문 주석(`// Field owner.ALLOWED_ROOTS:…`) 양쪽을
 * 받는다. 같은 파일 안의 접근은 getter 를 거치지 않고 필드를 직접 읽으므로 **둘을 다 봐야** 축의
 * 보유자가 빠지지 않는다. 중첩 class 보유자(`Outer$Inner`)도 받는다 — 축 보유자 단언이 그 꼴을
 * 두 번째 보유자로 세워야 한다.
 */
private val ROOTS_HOLDER = Regex("""//\s*(?:Field|Method)?\s*(bidvector/[A-Za-z0-9_/$]+)\.(?:get)?$ROOTS_SYMBOL""")

/**
 * `javap -p -v` 출력에서 `ALLOWED_ROOTS` getter 호출의 **소유 class 내부 이름**. 상수 풀 항목
 * (`Methodref … // owner.getALLOWED_ROOTS:()…`)과 본문 주석(`// Method owner.getALLOWED_ROOTS:()…`)
 * 양쪽을 받는다. 소유자 없는 `NameAndType` 항목은 `bidvector/` 로 시작하지 않아 걸리지 않는다.
 */
private val ROOTS_GETTER_OWNER = Regex("""// (?:Method )?(bidvector/[A-Za-z0-9_/]+)\.getALLOWED_ROOTS""")
