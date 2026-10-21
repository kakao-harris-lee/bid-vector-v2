package bidvector.bidding

/**
 * 개찰 대사(SET-02) 후보 술어 — §6.1 *"「투찰 기록됨」 건만 개찰 대사(SET-02) 후보가 된다"*.
 *
 * **대사 자체는 SET 소유다.** 이 slice 가 드는 것은 후보 술어 하나까지이고, 그 술어가
 * [RealBidCase] 만 받는 것이 핵심이다 — 페이퍼(가상) 건을 실투찰 대사에 넣으려면 먼저
 * [asReal] 을 불러야 하고 그 호출은 페이퍼에 `null` 을 준다. 즉 **섞임이 타입에서 막힌다**
 * (`case.kind == REAL` 을 호출부마다 다시 쓰는 형태였다면 한 자리만 빠뜨려도 섞인다).
 *
 * 상태 조건이 `BidRecorded` **하나**인 이유: 개찰 대기 · 개찰됨 이후는 이미 대사가 지났거나
 * 진행 중이고, 그 이전은 낼 투찰이 없다.
 */
fun isReconciliationCandidate(real: RealBidCase): Boolean = real.case.state == BidState.BidRecorded
