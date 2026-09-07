package bidvector.buildlogic

import org.gradle.api.Task
import org.gradle.api.specs.Spec

/**
 * `Task.onlyIf(Spec)`용 — 값을 미리 확정한 순수 클래스다. 모듈 `build.gradle.kts` 안에서
 * `onlyIf { ... }` 람다를 직접 쓰면 config cache 가 "cannot (de)serialize Gradle script
 * object references"로 깨진다(실측, M2/2D `crossLangSmokeTest`) — 스크립트 closure 가
 * 캡처하는 것이 지역 값 하나뿐이어도 그 람다가 사는 스크립트 클래스 자체를 붙들기
 * 때문이다. 컴파일된 이 클래스는 원시 `Boolean` 필드 하나만 가져 그 문제가 없다.
 */
class PresentSpec(
    private val present: Boolean,
) : Spec<Task> {
    override fun isSatisfiedBy(task: Task): Boolean = present
}
