package bidvector.buildlogic

import info.solidsoft.gradle.pitest.PitestTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.named

/**
 * 래칫 게이트가 읽을 pitest XML 리포트 — **pitest task 자신의 `reportDir` 에서 파생한다.**
 *
 * 「도구가 쓰는 자리」와 「게이트가 읽는 자리」를 각각 적으면 둘이 갈라질 수 있고 **그 갈라짐은
 * 조용하다**: `reportDir` 만 옮기고 옛 자리의 리포트를 남기면, pitest 가 새 자리에 나빠진 수를
 * 쓰는데도 게이트는 좋은 낡은 수를 읽어 로컬 증분 빌드가 UP-TO-DATE·exit 0 이 된다(리뷰
 * 라운드에서 재현). CI 는 새 checkout 이라 옛 자리가 비어 「존재하지 않는다」로 붉지만, 로컬에서
 * 초록을 보고 올리는 경로가 그만큼 열려 있었다.
 *
 * 그래서 값이 하나다. 꼴은 Gradle 이 권하는 producer→consumer 배선 그대로(`task.flatMap {
 * it.출력속성 }`)이고 따라오는 것이 둘 있다 — 게이트가 pitest 산출물의 **소비자로 선언**되어
 * 순서가 배선에서 나오고, 자리를 옮기면 양쪽이 **함께** 움직인다.
 *
 * 파일 이름은 도구가 정한 것이다. 자리까지 고정되는 것은 `timestampedReports=false` 와 짝이다.
 */
fun Project.pitestMutationXmlReport(): Provider<RegularFile> =
    tasks.named<PitestTask>(PITEST_TASK_NAME).flatMap { it.reportDir.file(MUTATIONS_XML_FILE_NAME) }

/** pitest plugin 이 만드는 task 이름. */
private const val PITEST_TASK_NAME = "pitest"

/** PIT 의 XML 리포트 파일 이름. */
private const val MUTATIONS_XML_FILE_NAME = "mutations.xml"
