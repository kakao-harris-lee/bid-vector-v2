package bidvector.app.access

import bidvector.adapters.access.AccountProvisioningTransaction
import bidvector.workflow.access.ClearPassphrase
import bidvector.workflow.access.LoginName
import bidvector.workflow.access.ProvisionOutcome
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

/** 로그 한 줄의 출구 — 러너는 이 한 자리로만 쓴다(`CollectionLog` 관례, test 가 줄을 잡는다). */
fun interface AccountCommandLog {
    fun write(line: String)
}

/** 프로세스 종료 자리 — 일회성 명령이 끝나면 프로세스가 끝난다(`CollectionTermination` 관례). */
fun interface AccountCommandTermination {
    fun terminate(exitCode: Int)
}

/** 명령의 두 갈래 — 설정 값 하나가 고른다. 어느 쪽도 기본 비밀 문구를 지어내지 않는다. */
enum class AccountCommandMode {
    /** 계정이 하나도 없을 때만 만든다. 이미 있으면 거부한다(산출물 2). */
    CREATE,

    /**
     * 비밀 문구를 바꾸고 **그 계정의 세션을 전부 폐기한다**(산출물 3 마지막 절). 이 갈래가
     * 없으면 그 요구를 촉발할 경로가 저장소에 존재하지 않는다 — 가입·재설정 메일은 범위
     * 밖이므로(R-2) 운영자 명령이 그 자리다.
     */
    ROTATE,
}

/**
 * 계정 부트스트랩 명령(M7/7D 산출물 2) — **운영자가 명시적으로 실행하는 한 번의 명령**이다.
 * `bidvector.account.mode` 가 없으면 이 러너의 배선 전체가 올라오지 않으므로(조건부 설정),
 * 평소 기동에는 존재하지 않는다. 가입 화면·HTTP endpoint 는 **없다**.
 *
 * 초기 비밀 문구는 환경에서 받는다(`bidvector.account.initial-value`) — 하드코딩 값·기본값이
 * 0 이고, 비어 있으면 설정 바인딩이 기동을 실패시킨다(fail-fast, `OperatorCredentialProperties`
 * 와 같은 규율). 받은 값은 즉시 [ClearPassphrase] 로 감싸므로 이 클래스에 raw 문자열 필드가
 * 없고, 로그에는 결과 어휘만 나간다(값·해시 0 — `AccountCommandRunnerTest` 가 실측한다).
 *
 * 쓰기는 **소유자 연결**로 돈다([AccountProvisioningTransaction] 의 조립이 그 연결을 쥔다) —
 * 앱 역할은 `account` 에 INSERT 권한이 없고 해시 열에 UPDATE 권한이 없다(V20).
 */
class AccountCommandRunner(
    private val transaction: AccountProvisioningTransaction,
    private val mode: AccountCommandMode,
    private val login: LoginName,
    private val initial: ClearPassphrase,
    private val log: AccountCommandLog,
    private val termination: AccountCommandTermination,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        // 갈래 선택은 `when` 소진이다 — 갈래가 늘면 컴파일이 누락을 잡는다.
        val outcome =
            transaction.inTransaction { provisioning ->
                when (mode) {
                    AccountCommandMode.CREATE -> provisioning.create(login, initial)
                    AccountCommandMode.ROTATE -> provisioning.rotate(login, initial)
                }
            }
        log.write(lineOf(outcome))
        termination.terminate(exitCodeOf(outcome))
    }

    private fun lineOf(outcome: ProvisionOutcome): String =
        when (outcome) {
            is ProvisionOutcome.Created -> "account command=create result=created login=${login.value}"
            is ProvisionOutcome.Rotated -> "account command=rotate result=rotated login=${login.value}"
            is ProvisionOutcome.RefusedAccountExists -> "account command=create result=refused cause=account-exists"
            is ProvisionOutcome.RefusedAccountAbsent -> "account command=rotate result=refused cause=account-absent"
        }

    /** 거부는 0 이 아니다 — cron·스크립트가 「했다」와 「안 했다」를 종료 코드로 가른다. */
    private fun exitCodeOf(outcome: ProvisionOutcome): Int =
        when (outcome) {
            is ProvisionOutcome.Created, is ProvisionOutcome.Rotated -> 0
            is ProvisionOutcome.RefusedAccountExists, is ProvisionOutcome.RefusedAccountAbsent -> 1
        }
}
