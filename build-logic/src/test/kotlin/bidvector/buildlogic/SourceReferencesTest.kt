package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PSI 추출의 음성 — 설계 검토 §4 S-1~S-4·S-9·S-10·S-16. 정책과 무관하게 **추출된 FQN 후보
 * 자체**를 잰다(순수 함수, 인라인 소스 문자열).
 */
class SourceReferencesTest {
    @Test
    fun `S-1 import 없는 완전수식 참조를 잡는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val accepted: Int = java.net.HttpURLConnection.HTTP_OK\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    @Test
    fun `S-2 별칭 import 는 원 FQN 을 낸다`() {
        val refs = SourceReferences.extract("Probe.kt", "package p\n\nimport java.net.HttpURLConnection as Conn\n")
        assertEquals(listOf("java.net.HttpURLConnection"), refs.map { it.fqn })
        assertTrue(refs.none { it.wildcard })
    }

    @Test
    fun `S-3 정확 패키지 밖의 wildcard import 를 패키지 FQN 으로 낸다`() {
        val wildcard = SourceReferences.extract("Probe.kt", "package p\n\nimport java.net.*\n").single()
        assertEquals("java.net", wildcard.fqn)
        assertTrue(wildcard.wildcard)
    }

    @Test
    fun `S-4 클래스 단위 패키지의 wildcard 도 같은 형태로 낸다`() {
        val wildcard = SourceReferences.extract("Probe.kt", "package p\n\nimport java.util.*\n").single()
        assertEquals("java.util", wildcard.fqn)
        assertTrue(wildcard.wildcard)
    }

    @Test
    fun `S-9 애노테이션의 완전수식 타입 참조를 잡는다`() {
        val refs = SourceReferences.extract("Probe.kt", "package p\n\n@java.beans.Transient\nclass C\n")
        assertTrue(refs.any { it.fqn == "java.beans.Transient" }, "$refs")
    }

    @Test
    fun `S-10 import 없는 class literal 을 잡는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    fun target(): Any = java.net.HttpURLConnection::class.java\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    @Test
    fun `S-16 백틱 식별자를 원 이름으로 읽는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val accepted: Int = `java`.`net`.HttpURLConnection.HTTP_OK\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    @Test
    fun `단순 이름 참조는 완전수식 단언에서 건너뛴다 — import 나 같은 파일 선언이 푼다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nimport java.net.HttpURLConnection\n\nclass C {\n" +
                    "    val accepted: Int = HttpURLConnection.HTTP_OK\n}\n",
            )
        assertEquals(listOf("java.net.HttpURLConnection"), refs.map { it.fqn })
    }

    @Test
    fun `값 체인은 건너뛴다 — 대문자 세그먼트가 없다`() {
        val refs = SourceReferences.extract("Probe.kt", "package p\n\nclass C {\n    val x = a.b.c\n}\n")
        assertTrue(refs.isEmpty(), "$refs")
    }

    @Test
    fun `package 선언과 import 지시자는 완전수식 단언의 입력에서 뺀다`() {
        val refs = SourceReferences.extract("Probe.kt", "package bidvector.procurement\n\nclass C\n")
        assertTrue(refs.isEmpty(), "$refs")
    }

    @Test
    fun `중첩 클래스는 두 형태를 모두 낸다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val e: java.util.Map.Entry<String, String>? = null\n}\n",
            )
        assertEquals(setOf("java.util.Map", "java.util.Map\$Entry"), refs.map { it.fqn }.toSet())
    }

    /**
     * **verifier r18 F-1.** 완전수식 참조가 다른 수식 표현의 **수신자**(여기서는 `.toString()`
     * 호출의 receiver)이면, 바깥 노드(`selector` 가 `KtCallExpression` 이라 `collectSegments`
     * 가 null)가 후보를 못 만드는데도 안쪽 노드가 「부모가 dot-qualified」라는 이유만으로
     * 방문에서 빠졌다. 설계 검토 §4 S-2 단언 6항 — 자식을 건너뛰는 것은 부모가 **실제로 후보를
     * 만들었을 때만**이다.
     */
    @Test
    fun `F-1 완전수식 참조 뒤에 메서드 호출이 이어져도 잡는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val status: String = java.net.HttpURLConnection.HTTP_OK.toString()\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    @Test
    fun `F-1 완전수식 호출의 수신자도 잡는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val encoded: String = java.net.URLEncoder.encode(\"x\", \"UTF-8\")\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.URLEncoder" }, "$refs")
    }

    @Test
    fun `F-1 생성자 호출의 완전수식 callee 도 잡는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val name: String = java.io.File(\"x\").name\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.io.File" }, "$refs")
    }

    /** 회귀 — 후보를 만든 노드의 자식은 여전히 중복 보고되지 않는다. */
    @Test
    fun `F-1 정상 체인은 여전히 한 번만 잡는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val v: Int = java.net.HttpURLConnection.HTTP_OK\n}\n",
            )
        assertEquals(1, refs.count { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    /**
     * **verifier r18 F-3 (verifier r19 M-1·M-3 로 판별 기준이 바뀌었다).** 글자 모양
     * (SCREAMING_CASE) 대신 **존재**로 판별한다 — `java.lang.Integer$MAX_VALUE` 는 실재하는
     * 클래스가 아니므로(`Class.forName` 이 못 찾는다) 체인이 뿌리에서 멈추고 `java.lang.Integer`
     * 만 후보로 남는다.
     */
    @Test
    fun `MAX_VALUE 처럼 실재하지 않는 중첩 클래스는 뿌리에서 체인이 멈춘다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val max: Int = java.lang.Integer.MAX_VALUE\n}\n",
            )
        assertEquals(setOf("java.lang.Integer"), refs.map { it.fqn }.toSet(), "$refs")
    }

    @Test
    fun `Map-Entry 처럼 실재하는 중첩 클래스는 두 형태를 모두 낸다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val e: java.util.Map.Entry<String, String>? = null\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.util.Map\$Entry" }, "$refs")
    }

    /**
     * **verifier r19 M-1.** 한 글자(`E`)·혼합 대소문자(`NaN`) 상수는 SCREAMING_CASE 글자
     * 모양 규칙으로는 멤버로 안 잡혔다 — `java.lang.Double$NaN`·`java.lang.Math$E` 가 실재하지
     * 않는 클래스이므로 존재 판별로는 둘 다 뿌리에서 멈춘다.
     */
    @Test
    fun `M-1 한 글자·혼합 대소문자 상수도 실재하지 않으면 뿌리에서 멈춘다`() {
        val nan =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val nan: Double = java.lang.Double.NaN\n}\n",
            )
        assertEquals(setOf("java.lang.Double"), nan.map { it.fqn }.toSet(), "$nan")

        val e =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val e: Double = java.lang.Math.E\n}\n",
            )
        assertEquals(setOf("java.lang.Math"), e.map { it.fqn }.toSet(), "$e")
    }

    /**
     * **verifier r19 M-3 이 닫히는 근거.** `java.util.Map.ENTRY` 는 실재하는 클래스가 아니므로
     * (진짜 이름은 `Entry`) 체인이 `Map` 에서 멈춘다 — 만들어 낸 이름을 컴파일할 도리가 없어
     * 「도달 불가」였던 것이 존재 판별로도 같은 결론(뿌리만 검사)에 이른다.
     */
    @Test
    fun `M-3 존재하지 않는 전대문자 이름은 뿌리에서 멈춘다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val x: Any? = java.util.Map.ENTRY\n}\n",
            )
        assertEquals(setOf("java.util.Map"), refs.map { it.fqn }.toSet(), "$refs")
    }

    /**
     * **비 JDK 루트는 존재를 확인할 수 없다 — 닫히는 방향을 유지한다.** `foo.Bar.BAZ` 는
     * `foo`·`javax`·`jdk`·`kotlin` 어느 것도 아니므로 존재 판별 없이 예전 규칙(대문자 세그먼트는
     * 전부 체인에 잇는다)을 그대로 쓴다 — `foo.Bar$BAZ` 까지 요구해야 한다.
     */
    @Test
    fun `비 JDK 루트는 존재 판별 없이 대문자 세그먼트를 전부 체인에 잇는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val x: Int = foo.Bar.BAZ\n}\n",
            )
        assertEquals(setOf("foo.Bar", "foo.Bar\$BAZ"), refs.map { it.fqn }.toSet(), "$refs")
    }

    /**
     * **verifier r19 M-2.** F-1 이 `KtCallExpression` 의 callee 도 세그먼트로 받으면서
     * `tree.Node()`(`tree: Tree` 파라미터의 `inner class` 인스턴스화)가 `tree.Node` 라는 가짜
     * 패키지 후보를 냈다. 소문자 뿌리가 **같은 파일에 선언된 이름**(여기서는 함수 파라미터)이면
     * 값 체인이라 건너뛴다.
     */
    @Test
    fun `M-2 소문자 뿌리가 같은 파일의 선언 이름이면 값 체인으로 건너뛴다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass Tree {\n    inner class Node\n}\n\nfun f(tree: Tree) = tree.Node()\n",
            )
        assertTrue(refs.none { it.fqn.startsWith("tree") }, "$refs")
    }

    /** 회귀 — 지역 선언과 무관한 완전수식 참조(F-1)는 그대로 잡힌다. */
    @Test
    fun `M-2 이후에도 완전수식 생성자 호출은 그대로 잡힌다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val name: String = java.io.File(\"x\").name\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.io.File" }, "$refs")
    }

    /**
     * **Codex 14차 #1 — ① 원 재현.** M-2 수정이 판별을 파일 전체 이름 집합으로 했다가, **다른
     * 함수**의 동명 지역 변수(`val java = 1`)가 이 함수의 완전수식 참조까지 지우는 미탐을 냈다.
     * `java` 와 그 참조가 선언되는 함수는 서로의 조상이 아니므로 [KtElement.visibleLocalNames]
     * 로는 보이지 않아야 한다 — 게이트 술어는 미탐보다 오탐을 택한다.
     */
    @Test
    fun `Codex-1 다른 함수의 동명 지역 변수는 이 함수의 완전수식 참조를 지우지 못한다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nfun other() {\n    val java = 1\n}\n\n" +
                    "fun target(): String = java.net.HttpURLConnection.HTTP_OK.toString()\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    /**
     * **Codex 14차 #1 — ② 같은 함수의 진짜 값 체인(회귀).** `val java = 1` 이 **같은 함수**의
     * 앞선 문장이면 Kotlin 은 실제로 그 지역 변수를 가리키므로(뒤 문장에서 참조하지 않으면
     * 컴파일도 안 된다) 값 체인으로 건너뛰는 것이 옳다.
     */
    @Test
    fun `Codex-1 같은 함수 안의 앞선 지역 변수는 여전히 값 체인이다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nfun target(): Int {\n    val java = 1\n    return java.net.hashCode()\n}\n",
            )
        assertTrue(refs.none { it.fqn.startsWith("java.net") }, "$refs")
    }

    /** **Codex 14차 #1 — ③ 람다 파라미터.** */
    @Test
    fun `Codex-1 람다 파라미터도 값 체인으로 건너뛴다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass Tree {\n    inner class Node\n}\n\n" +
                    "fun f(trees: List<Tree>) = trees.map { tree -> tree.Node() }\n",
            )
        assertTrue(refs.none { it.fqn.startsWith("tree") }, "$refs")
    }

    /** **Codex 14차 #1 — ④ 감싸는 클래스의 프로퍼티(중첩 클래스 포함).** */
    @Test
    fun `Codex-1 감싸는 클래스의 프로퍼티도 값 체인으로 건너뛴다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass Tree {\n    inner class Node\n}\n\n" +
                    "class Holder(private val tree: Tree) {\n    fun build() = tree.Node()\n}\n",
            )
        assertTrue(refs.none { it.fqn.startsWith("tree") }, "$refs")
    }

    /**
     * **verifier r20 H-1 — ① 원 재현(P10).** Kotlin 의 지역 변수 스코프는 **선언 지점부터**
     * 시작한다 — 참조보다 **뒤쪽**에 둔 동명 지역 변수는 그 참조를 가리지 못한다(그 자리에서
     * `java` 는 아직 선언되지 않았으므로 패키지로 해석된다. 컴파일도 실제로 된다 — `val x = …;
     * val java = 1; return x + java`). 위치를 가리지 않던 이전 판별은 이 경우도 값 체인으로
     * 오판해 미탐을 냈다.
     */
    @Test
    fun `r20 H-1 참조보다 뒤쪽에 선언된 동명 지역 변수는 그 참조를 가리지 못한다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nfun target(): Int {\n" +
                    "    val x = java.net.HttpURLConnection.HTTP_OK\n" +
                    "    val java = 1\n" +
                    "    return x + java\n" +
                    "}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    /** **verifier r20 H-1 — ② 앞쪽 선언 회귀.** 참조보다 앞선 동명 지역 변수는 여전히 가린다. */
    @Test
    fun `r20 H-1 참조보다 앞쪽에 선언된 동명 지역 변수는 그 참조를 여전히 가린다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nfun target(): Int {\n" +
                    "    val java = Any().hashCode()\n" +
                    "    return java.net.hashCode()\n" +
                    "}\n",
            )
        assertTrue(refs.none { it.fqn.startsWith("java.net") }, "$refs")
    }

    /**
     * **verifier r20 H-1 — ③a 중첩 블록, 바깥의 앞쪽 선언.** 바깥 블록의 **앞쪽** 선언은 그
     * 안쪽 블록에서도 값 체인이다(조상 사슬 + 위치 조건 둘 다 만족).
     */
    @Test
    fun `r20 H-1 바깥 블록의 앞쪽 선언은 안쪽 블록에서도 값 체인이다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nfun target(flag: Boolean): Int {\n" +
                    "    val java = 1\n" +
                    "    if (flag) {\n" +
                    "        return java.net.hashCode()\n" +
                    "    }\n" +
                    "    return java\n" +
                    "}\n",
            )
        assertTrue(refs.none { it.fqn.startsWith("java.net") }, "$refs")
    }

    /**
     * **verifier r20 H-1 — ③b 중첩 블록, 바깥의 뒤쪽 선언.** 안쪽 블록의 참조보다 바깥 블록의
     * 동명 선언이 텍스트상 **나중에** 오면(안쪽 블록이 끝난 뒤에 선언), 그 선언은 안쪽 블록의
     * 참조를 가리지 못한다 — 위치 조건이 조상 사슬을 거슬러 올라가는 매 단계에서 각각 적용돼야
     * 한다(바깥 블록 자체의 offset 비교도 안쪽 참조의 offset 기준이어야 한다).
     */
    @Test
    fun `r20 H-1 바깥 블록의 뒤쪽 선언은 그보다 앞선 안쪽 블록의 참조를 가리지 못한다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nfun target(flag: Boolean): Int {\n" +
                    "    if (flag) {\n" +
                    "        return java.net.HttpURLConnection.HTTP_OK\n" +
                    "    }\n" +
                    "    val java = 1\n" +
                    "    return java\n" +
                    "}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    /**
     * **verifier r21 H-1' — ① 자기 초기화식(원 재현).** `startOffset` 만 비교하면 선언의 `val`
     * 키워드가 참조보다 앞서므로 가려지는 것으로 오판한다 — Kotlin 은 초기화식 안에서 그 지역
     * 변수를 아직 보지 않으므로(자기 자신을 참조할 수 없다) `java` 는 이 자리에서 패키지로
     * 해석되고 컴파일도 된다. `endOffset`(선언 서브트리 전체의 끝) 비교로 자동 제외된다.
     */
    @Test
    fun `r21 H-1' 가리는 선언 자신의 초기화식 안 참조는 가려지지 않는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nfun target(): Int {\n" +
                    "    val java = java.net.HttpURLConnection.HTTP_OK\n" +
                    "    return java\n" +
                    "}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    /** **verifier r21 H-1' — ② `var` 판.** */
    @Test
    fun `r21 H-1' var 의 자기 초기화식 안 참조도 가려지지 않는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nfun target(): Int {\n" +
                    "    var java = java.net.HttpURLConnection.HTTP_OK\n" +
                    "    java += 1\n" +
                    "    return java\n" +
                    "}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    /**
     * **verifier r21 H-1' — ③ `when (val java = …)` subject.** subject 변수는 블록의 형제
     * 문장이 아니라 `KtWhenExpression` 의 자식이라 애초에 [visibleLocalNames] 의 블록 순회에
     * 잡히지 않는다 — 별도 조치 없이 닫힌다는 것을 여기서 고정한다.
     */
    @Test
    fun `r21 H-1' when subject 의 자기 초기화식 안 참조도 가려지지 않는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nfun target(): Int =\n" +
                    "    when (val java = java.net.HttpURLConnection.HTTP_OK) {\n" +
                    "        else -> java\n" +
                    "    }\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    /** **verifier r21 H-1' — ④ 앞쪽 선언 회귀.** 진짜 값 체인은 여전히 건너뛴다. */
    @Test
    fun `r21 H-1' 앞쪽에 끝난 선언은 뒤쪽 참조를 여전히 가린다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nfun target(): Int {\n" +
                    "    val java = Any().hashCode()\n" +
                    "    return java.net.hashCode()\n" +
                    "}\n",
            )
        assertTrue(refs.none { it.fqn.startsWith("java.net") }, "$refs")
    }

    /**
     * **verifier r21 H-1' — ⑤ 초기화식 안 람다.** 초기화식이 람다를 한 겹 더 두르고 있어도
     * ([KtFunctionLiteral] 의 본문 블록에는 `java` 선언이 없으므로) 조상 사슬을 거슬러 선언
     * 자신의 블록에 이르렀을 때 같은 `endOffset` 비교로 걸러진다.
     */
    @Test
    fun `r21 H-1' 초기화식 안 람다에 감싸인 참조도 가려지지 않는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nfun target(): Int {\n" +
                    "    val java = run { java.net.HttpURLConnection.HTTP_OK }\n" +
                    "    return java\n" +
                    "}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }
}
