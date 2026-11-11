package bidvector.buildlogic

import org.jetbrains.kotlin.psi.KtFile

/**
 * 한 파일에 대한 소스 층 세 축의 판정 — task 배선과 분리한 순수 자리다.
 *
 * 축마다 **잰 것의 수**를 함께 낸다. 수가 0 이면 그 축은 그 파일에서 아무것도 보지 못한 것이고,
 * 리포트의 합이 0 이면 게이트가 공허하게 초록인 것이다(「통과했다」와 「쟀다」는 다른 말이다).
 */
internal data class SourceAxisVerdict(
    val violations: List<String>,
    val references: Int,
    val scopedFiles: Int,
    val memberCalls: Int,
) {
    operator fun plus(other: SourceAxisVerdict) =
        SourceAxisVerdict(
            violations + other.violations,
            references + other.references,
            scopedFiles + other.scopedFiles,
            memberCalls + other.memberCalls,
        )

    companion object {
        val EMPTY = SourceAxisVerdict(emptyList(), 0, 0, 0)
    }
}

internal object SourceAxisViolations {
    fun judge(
        policy: SourceReferencePolicy,
        module: String,
        ktFile: KtFile,
        fileName: String,
    ): SourceAxisVerdict {
        val references = SourceReferences.extractFrom(ktFile, fileName)
        val calls = SourceMemberCalls.extractFrom(ktFile, fileName)
        val packageName = ktFile.packageFqName.asString()
        val scope = policy.scopeOf(packageName)
        return SourceAxisVerdict(
            violations =
                allowlistViolations(policy, module, references) +
                    scopeViolations(policy, references, scope) +
                    envSniffViolations(policy, calls, owningType(packageName, fileName)),
            references = references.size,
            scopedFiles = if (scope == null) 0 else 1,
            memberCalls = calls.size,
        )
    }

    /** ⓐ domain 허용 목록 — domain 모듈에서만 판정한다(모듈 선택은 정책이 한다). */
    private fun allowlistViolations(
        policy: SourceReferencePolicy,
        module: String,
        references: List<SourceReference>,
    ): List<String> =
        if (!policy.isDomain(module)) {
            emptyList()
        } else {
            references
                .filterNot { if (it.wildcard) policy.admitsWildcard(it.fqn) else policy.admits(it.fqn) }
                .map {
                    "${it.fileName}:${it.line} ${it.fqn} — 허용 목록에 없다 " +
                        "(v2-지침서.md §3.1 「domain은 Spring, JPA, JSON, HTTP, broker를 import하지 않는다」)"
                }
        }

    /** ⓑ 패키지 범위 축 — 축에 드는 파일의 자기 저장소 좌표가 그 축의 허용 뿌리 안인가. */
    private fun scopeViolations(
        policy: SourceReferencePolicy,
        references: List<SourceReference>,
        scope: List<String>?,
    ): List<String> =
        if (scope == null) {
            emptyList()
        } else {
            references
                .filter { policy.isSelfNamespace(it.fqn) && !policy.admitsInScope(it.fqn, scope) }
                .map {
                    "${it.fileName}:${it.line} ${it.fqn} — 이 패키지 축의 허용 뿌리 밖이다 " +
                        "(컴파일 시간 상수는 바이트코드에 흔적을 남기지 않는다, OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT)"
                }
        }

    /**
     * 이 파일이 담은 **최상위 타입의 좌표** — 패키지 + 파일 이름이다(이 저장소는 파일마다 같은 이름의
     * 최상위 타입 하나를 둔다). 바이트코드 축의 허용 키가 그 좌표를 쓰므로, 두 층이 **같은 키 하나**를
     * 읽으려면 소스 층에서도 같은 모양을 만들어야 한다.
     */
    private fun owningType(
        packageName: String,
        fileName: String,
    ): String =
        fileName
            .substringAfterLast('/')
            .removeSuffix(".kt")
            .let { if (packageName.isEmpty()) it else "$packageName.$it" }

    /** ⓒ 환경 스니핑 축 — 임계 0. 억제는 환경 질의가 아니라 명시적 정책 값이어야 한다(OPS-13). */
    private fun envSniffViolations(
        policy: SourceReferencePolicy,
        calls: List<SourceMemberCall>,
        owningType: String,
    ): List<String> =
        calls
            .filter { policy.isEnvSniff(it) && !policy.allowsEnvSniff(owningType, it.member) }
            .map {
                "${it.fileName}:${it.line} ${it.receiver}.${it.member} — production 코드가 실행 환경을 스니핑한다 " +
                    "(OPS-13 임계 0; 억제는 명시적 정책 값으로, OPS-07 (a))"
            }
}
