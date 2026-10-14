package bidvector.workflow.retention

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** P-3 — 파기 순서는 FK 간선에서 도출한다(손 순서 금지, 설계 검토 우회 6). */
class DeletionOrderTest {
    @Test
    fun `자식이 부모보다 먼저 온다 — 입력 순서가 부모 먼저여도 뒤집는다`() {
        val outcome =
            deletionOrderOf(
                tables = listOf("parent", "child"),
                dependencies = setOf(TableDependency(child = "child", parent = "parent", cascade = false)),
            )

        outcome shouldBe DeletionOrder.Ordered(listOf("child", "parent"))
    }

    @Test
    fun `세 겹 사슬도 잎에서부터 온다`() {
        val outcome =
            deletionOrderOf(
                tables = listOf("a", "b", "c"),
                dependencies =
                    setOf(
                        TableDependency("b", "a", cascade = false),
                        TableDependency("c", "b", cascade = false),
                    ),
            )

        outcome shouldBe DeletionOrder.Ordered(listOf("c", "b", "a"))
    }

    @Test
    fun `파기 대상 밖의 부모를 가리키는 간선은 순서에 영향이 없다`() {
        // raw_observation 은 마스킹 대상이라 파기 목록에 없다 — 자식들의 순서를 바꾸지 않는다.
        val outcome =
            deletionOrderOf(
                tables = listOf("notice", "qualification_text"),
                dependencies =
                    setOf(
                        TableDependency("notice", "raw_observation", cascade = false),
                        TableDependency("qualification_text", "raw_observation", cascade = false),
                    ),
            )

        outcome shouldBe DeletionOrder.Ordered(listOf("notice", "qualification_text"))
    }

    @Test
    fun `같은 깊이의 표는 입력 순서를 보존한다 — 실행마다 순서가 흔들리지 않는다`() {
        val outcome = deletionOrderOf(listOf("x", "y", "z"), emptySet())

        outcome shouldBe DeletionOrder.Ordered(listOf("x", "y", "z"))
    }

    /**
     * cr L-6 — 자기참조 FK(`parent_id` 꼴)는 **순환이 아니다**. 순환으로 세면 그 표 하나 때문에
     * run 전체가 `REFUSED` 가 되어 **아무것도 지우지 않고** 조용히 멈춘다. 같은 표 안의 순서는
     * 자식 가드(`NOT EXISTS`)가 진다.
     */
    @Test
    fun `자기참조 간선은 순환이 아니다 — 그 표 하나 때문에 run 전체가 멈추지 않는다`() {
        val outcome =
            deletionOrderOf(
                tables = listOf("tree", "leaf"),
                dependencies =
                    setOf(
                        TableDependency("tree", "tree", cascade = false),
                        TableDependency("leaf", "tree", cascade = false),
                    ),
            )

        outcome shouldBe DeletionOrder.Ordered(listOf("leaf", "tree"))
    }

    @Test
    fun `순환은 순서가 없다 — 걸린 표 집합을 값으로 돌려준다`() {
        val outcome =
            deletionOrderOf(
                tables = listOf("a", "b"),
                dependencies =
                    setOf(
                        TableDependency("a", "b", cascade = false),
                        TableDependency("b", "a", cascade = false),
                    ),
            )

        outcome shouldBe DeletionOrder.Cyclic(setOf("a", "b"))
    }
}
