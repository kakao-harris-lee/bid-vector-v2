package bidvector.buildlogic

import info.solidsoft.gradle.pitest.PitestTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
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
 * 파일 이름은 도구가 정한 것이다. 자리의 **둘째 축**인 `timestampedReports` 는 파생할 수 없어
 * [refuseTimestampedReports] 가 거부한다.
 */
fun Project.pitestMutationXmlReport(): Provider<RegularFile> {
    val pitest = tasks.named<PitestTask>(PITEST_TASK_NAME)
    refuseTimestampedReports(pitest)
    return pitest.flatMap { it.reportDir.file(MUTATIONS_XML_FILE_NAME) }
}

/**
 * `timestampedReports=true` 를 **구성 단계에서 거부한다.** 그쪽을 켜면 pitest 가 리포트를 시각
 * 이름의 하위 디렉터리에 쓰는데, Gradle 은 출력 디렉터리의 낡은 파일을 지우지 않는다 — 그래서
 * 전 실행의 좋은 리포트가 `reportDir` 에 남고 게이트는 그것을 읽어 **로컬에서 초록**이 된다
 * (새 checkout 인 CI 는 「존재하지 않는다」로 붉다). 자리를 파생하는 것으로는 막을 수 없다:
 * 시각 이름을 우리가 알 수 없고, 알더라도 낡은 파일이 그대로 남는다.
 *
 * 읽는 값이 **task 의 것**인 이유는 리포트를 쓰는 것이 task 이기 때문이다 — extension 을 거쳐
 * 오든 task 에 직접 설정되든 그 자리에 모인다.
 *
 * **무엇을 보는지 정확히**: 게이트가 구성되는 시점에 그 task 에 설정돼 있는 값이다. 배선이 lazy
 * 하므로 그 시점은 모듈 스크립트가 평가된 뒤이고, 그래서 extension 축과 task 축이 둘 다 잡힌다
 * (실측: 회귀 test 둘). **그 뒤에 등록되는 설정은 보지 못한다** — 「최종값」이라고 적지 않는
 * 이유다. 그 경로는 알려진 제한으로 등재돼 있고, CI 는 새 checkout 에서 convention 하나만이
 * 이 값을 정하는 자리라 거기서는 생기지 않는다.
 */
private fun refuseTimestampedReports(pitest: TaskProvider<PitestTask>) {
    if (pitest.get().timestampedReports.getOrElse(false)) {
        throw GradleException(
            "mutation 리포트 자리가 실행마다 바뀌면 래칫이 그 자리를 가리킬 수 없다 — " +
                "pitest `timestampedReports` 는 false 여야 한다(ADR 0007 OPEN-ADR-07). " +
                "켜면 낡은 리포트가 `reportDir` 에 남아 게이트가 그것으로 통과한다.",
        )
    }
}

/** pitest plugin 이 만드는 task 이름. */
private const val PITEST_TASK_NAME = "pitest"

/** PIT 의 XML 리포트 파일 이름. */
private const val MUTATIONS_XML_FILE_NAME = "mutations.xml"
