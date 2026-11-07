package bidvector.buildlogic

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * **실행 환경 스니핑 0**(OPS-13) 을 **산출물의 멤버 참조**에서 잰다 — 전 모듈 production 클래스.
 *
 * 왜 소스 층과 별도의 task 인가: 소스 층 축(`domainSourceReferenceGate` ⓒ)은 `수신자.멤버` **글자
 * 쌍**이라 수신자를 별칭으로 바꾸거나 정적 import 로 지우면 쌍이 만들어지지 않는다(실측 — verifier
 * H-1 · 리뷰 HIGH). 컴파일된 참조는 그 넷을 **하나로** 모으므로, 우회하려면 소스 표기가 아니라 **이
 * 게이트 자신을 고쳐야** 한다. 소스 층은 좌표를 줄 단위로 지목해 주는 1차 그물로 남긴다
 * (`java.setSrcDirs` 봉쇄 + `sourceLanguageGate` 와 같은 2층 구성).
 *
 * 모집단이 **전 모듈**인 것이 이 축의 요점이다 — ⓐ 허용 목록 축은 정책이 고른 domain 모듈에서만 돌고
 * ⓑ 범위 축은 자기 저장소 좌표만 보므로, 환경 스니핑이 실제로 쓰일 자리(`app`·`adapters`·`workflow`
 * 의 배선)를 받는 축이 **없었다**.
 *
 * 모집단은 **읽은 클래스 전수**다 — 익명 클래스를 빼면 callable reference·suspend 람다·익명 object 가
 * 통째로 판정 밖이 된다(수정 라운드 3 실측). 귀속만 감싼 최상위 클래스로 올려 「고칠 자리」를 남긴다.
 *
 * 판정은 [EnvironmentAccess] 가 한다(순수). 이 task 는 읽기와 배선만 맡는다.
 */
abstract class EnvironmentAccessGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val denied = readPolicy(policyFile.get().asFile).requireList(DENIED_MEMBERS).toSet()
        val roots = classes.files.filter { it.isDirectory }.map { it.toPath() }
        // **익명 클래스를 빼지 않는다**(수정 라운드 3 — verifier H-1 · 리뷰 H-1). 앞 판은
        // `filterNot { it.isAnonymousClass }` 로 모집단을 좁혔는데, 그 필터는 타입 **모양 래칫**
        // (`TypeShapeGateTask`)의 관례이고 거기서는 「이름 없는 타입은 선언된 모양이 아니다」가 근거다.
        // 접근 **전수 조사**에는 그 근거가 없다 — Kotlin 은 callable reference·suspend 람다·익명
        // object 를 이름 없는 내부 클래스로 내보내고 그 접근은 그 클래스에 귀속되므로, 필터가 곧
        // 사각이었다(실측 59 클래스 · 718 접근이 조용히 빠졌다).
        val imported = if (roots.isEmpty()) emptyList() else ClassFileImporter().importPaths(roots).toList()
        val accesses =
            imported.flatMap { type ->
                val enclosing = type.topLevelName()
                type.accessesFromSelf.map {
                    ClassMemberAccess(type.name, it.targetOwner.name, it.target.name, enclosing)
                }
            }
        val violations = EnvironmentAccess.violations(accesses, denied)

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "types=${imported.size}\n" +
                // 앞 판이 **빼고 있던** 모집단을 수로 남긴다 — 그 수가 0 이 아니라는 것이 필터 제거의
                // 효과이고, 다시 필터가 생기면 이 줄이 떨어진다.
                "anonymousTypes=${imported.count { it.isAnonymousClass }}\n" +
                "accesses=${accesses.size}\n" +
                "denied=${denied.size}\n" +
                "violations=${violations.size}\n",
        )

        if (violations.isEmpty()) return
        throw GradleException(
            violations.joinToString(
                prefix = "실행 환경 스니핑 ${violations.size}건 — OPS-13 의 임계는 0 이다:\n  ",
                separator = "\n  ",
            ),
        )
    }

    /**
     * 이 클래스를 품은 **최상위 클래스** 이름. 이름 없는 내부 클래스(`Outer$f$1`)의 접근을 「고칠 자리」
     * 로 올린다 — `getEnclosingClass` 를 더 올라갈 수 없을 때까지 따라간다.
     */
    private fun JavaClass.topLevelName(): String {
        var current = this
        while (true) {
            val outer = current.enclosingClass.orElse(null) ?: return current.name
            current = outer
        }
    }

    private companion object {
        const val DENIED_MEMBERS = "bytecode.env-access.denied-members"
    }
}
