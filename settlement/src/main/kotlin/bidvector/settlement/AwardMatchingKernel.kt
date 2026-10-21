package bidvector.settlement

/**
 * 「운영자 상호가 설정돼 있는가」를 판정해 **조회·판정 관문**을 낸다(SET-01 acceptance:
 * *"운영자 상호가 미설정이면 판정하지 않고 외부 조회도 하지 않는다"*).
 *
 * 술어를 `Boolean` 으로 두면 호출부가 무시할 수 있다. 여기서는 산출이 [AwardLookupDecision]
 * 이고 조회에 쓸 정규화 상호를 [AwardLookupDecision.Lookup] 만 나르므로, 미설정 상태에서는
 * 조회 호출을 **조립할 값이 없다**. 실제 조회 경로는 이 slice 에 없다(소비 slice 몫).
 */
fun decideAwardLookup(
    operator: OperatorTradeName,
    policy: AwardMatchingPolicyData,
): AwardLookupDecision =
    when (operator) {
        OperatorTradeName.NotDeclared -> {
            AwardLookupDecision.Skip(OperatorNameAbsentReason.NotDeclared)
        }

        is OperatorTradeName.Declared -> {
            when (val normalization = normalizeCompanyName(operator.rawName, policy)) {
                CompanyNameNormalization.Empty -> {
                    AwardLookupDecision.Skip(OperatorNameAbsentReason.NormalizesToEmpty)
                }

                is CompanyNameNormalization.Normalized -> {
                    AwardLookupDecision.Lookup(normalization.name)
                }
            }
        }
    }

/**
 * 낙찰/패찰 판정 — **시그니처가 금액을 받지 않는다.**
 *
 * legacy 는 *"caller symmetry and audit only"* 로 낙찰가·투찰가를 받고 본문에서 쓰지 않았다
 * (`award_verification.py:110-136`). 그 형태는 후속 개발자가 금액을 판정에 끌어들이기 쉬워
 * 채택하지 않는다 — 인자가 **아예 없으면** 코드로 강제된다. 금액을 더한 호출이 컴파일되지
 * 않는다는 사실은 `CompileFailureHarnessTest` fixture 1 이 잰다.
 *
 * 운영자 축 검사가 결과 축보다 **먼저**다(legacy 순서) — 둘 다 없으면 「판정 안 함」이지
 * 「미기록」이 아니다.
 */
fun judgeAwardOutcome(
    lookup: AwardLookupDecision,
    winner: AwardWinnerTradeName,
    policy: AwardMatchingPolicyData,
): AwardOutcome =
    when (lookup) {
        is AwardLookupDecision.Skip -> AwardOutcome.NotJudged(lookup.reason)
        is AwardLookupDecision.Lookup -> judgeAgainstWinner(lookup.operator, winner, policy)
    }

private fun judgeAgainstWinner(
    operator: NormalizedCompanyName,
    winner: AwardWinnerTradeName,
    policy: AwardMatchingPolicyData,
): AwardOutcome =
    when (winner) {
        AwardWinnerTradeName.NotPublished -> {
            AwardOutcome.NotRecorded(WinnerNameAbsentReason.NotPublished)
        }

        is AwardWinnerTradeName.Published -> {
            when (val normalization = normalizeCompanyName(winner.rawName, policy)) {
                CompanyNameNormalization.Empty -> {
                    AwardOutcome.NotRecorded(WinnerNameAbsentReason.NormalizesToEmpty)
                }

                is CompanyNameNormalization.Normalized -> {
                    if (normalization.name == operator) SettledAwardOutcome.Won else SettledAwardOutcome.Lost
                }
            }
        }
    }

/**
 * 「한 번만 기록되고 덮이지 않는다」 — 확정 판정이 이미 있으면 [judged] 가 무엇이든 기존 값을
 * 그대로 돌려준다(legacy `award_notifications.py:186-191` 의 멱등 조건 둘이 각각 첫·셋째
 * 갈래다).
 *
 * 기록 대상 타입이 [SettledAwardOutcome] 이라 미기록·판정 안 함이 기록으로 승격되는 경로가
 * 타입으로 없다. 영속화는 소비 slice 몫이고 이 함수는 **무엇을 쓸 것인가**만 판정한다.
 */
fun recordAwardOutcome(
    existing: RecordedAwardOutcome,
    judged: AwardOutcome,
): AwardOutcomeRecording =
    when (existing) {
        is RecordedAwardOutcome.Settled -> {
            AwardOutcomeRecording.AlreadySettled(existing.outcome)
        }

        RecordedAwardOutcome.Absent -> {
            when (judged) {
                is SettledAwardOutcome -> AwardOutcomeRecording.Recorded(judged)
                is AwardOutcome.NotRecorded -> AwardOutcomeRecording.NotSettled(judged)
                is AwardOutcome.NotJudged -> AwardOutcomeRecording.NotSettled(judged)
            }
        }
    }
