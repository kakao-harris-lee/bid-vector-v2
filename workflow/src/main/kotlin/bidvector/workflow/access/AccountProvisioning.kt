package bidvector.workflow.access

import bidvector.workflow.strategy.Clock

/** 운영자 명령의 결과 — 거부는 사유를 **운영자에게** 알린다(여기는 HTTP 가 아니다). */
sealed interface ProvisionOutcome {
    data object Created : ProvisionOutcome

    data object Rotated : ProvisionOutcome

    /** 「이미 있으면 거부」(산출물 2) — 덮어쓰지 않는다. */
    data object RefusedAccountExists : ProvisionOutcome

    /** 교체 대상이 없다 — 교체 명령이 계정을 **만들지는 않는다**. */
    data object RefusedAccountAbsent : ProvisionOutcome
}

/**
 * 계정 부트스트랩·비밀 문구 교체(M7/7D 산출물 2) — **운영자가 명시적으로 실행하는 명령**
 * 에서만 조립된다(요청 경로의 조립에는 [AccountProvisioningPort] 구현이 들어가지 않는다).
 *
 * **[rotate] 가 세션 폐기를 함께 진다**(산출물 3 「비밀번호가 바뀌면 그 계정 세션 전부
 * 폐기」). 해시를 쓰는 함수가 이 하나이고 그 함수가 폐기까지 하므로, 「해시만 바꾸고 세션은
 * 남기는」 상태가 이 타입의 표면에 없다 — 같은 트랜잭션이라 둘은 함께 커밋되거나 함께
 * 롤백된다(경계는 어댑터의 [bidvector.adapters.access.AccountProvisioningTransaction]).
 *
 * 기본 비밀 문구를 **지어내지 않는다** — 값은 호출부(명령)가 환경에서 받아 넘긴다.
 */
class AccountProvisioning(
    private val accounts: AccountProvisioningPort,
    private val sessions: BrowserSessionStorePort,
    private val hasher: PassphraseHasher,
    private val clock: Clock,
) {
    fun create(
        login: LoginName,
        clear: ClearPassphrase,
    ): ProvisionOutcome {
        if (accounts.anyExists()) return ProvisionOutcome.RefusedAccountExists
        accounts.insert(login, hasher.hash(clear), clock.now())
        return ProvisionOutcome.Created
    }

    fun rotate(
        login: LoginName,
        clear: ClearPassphrase,
    ): ProvisionOutcome {
        val now = clock.now()
        val account =
            accounts.replaceHash(login, hasher.hash(clear), now)
                ?: return ProvisionOutcome.RefusedAccountAbsent
        sessions.revokeAllOf(account, now)
        return ProvisionOutcome.Rotated
    }
}
