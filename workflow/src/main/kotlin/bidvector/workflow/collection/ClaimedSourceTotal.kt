package bidvector.workflow.collection

/**
 * **원천이 한 번 말한 총수는 뒤 배치가 지우지 못한다**(M9/award-ingest v r3 L-1) — 쪽 상한으로 이어
 * 돌 때 공유 누산기([SourceAccountingTally])는 마지막 배치의 총수를 쓰는데
 * (`source.sourceTotal ?: sourceTotal`), 어댑터는 배치마다 새 누산기라 재개 첫 쪽이 총수를 말하지
 * 않으면 **0** 을 낸다. 그러면 회계 행이 「받은 수 ≥ 총수」가 되어 덜 받은 슬롯이 다 받은 것처럼
 * 적힌다.
 *
 * **같은 누산기로 걷는 수집 갈래는 셋이고, 이제 셋이 다 이 자리를 부른다** — 공고 목록
 * ([CollectNoticesUseCase])·낙찰 목록([CollectAwardListUseCase])·개찰결과 목록([OpeningSampleFramer]).
 * 셋째는 M11/11A 가 받았다(`OPEN-M10-OPENING-LIST-RESUME-TOTAL` 닫힘 · capability map §12.3 (k)).
 * 복제하지 않는 것은 중복 금지 규율이기도 하지만, 공유 누산기를 최댓값으로 올리는 날 **걷을 자리가
 * 하나**여야 하기 때문이다.
 *
 * 파일 이름은 이 함수가 지키는 **값**(원천이 말한 총수)을 가리키고 선언 이름은 그 값을 고르는 **방법**을
 * 가리킨다 — 둘을 맞추려 다시 옮기면 그 자체가 또 한 번의 이동이 되므로 이름을 바꾸지 않는다.
 *
 * **닫히는 범위는 배치 경계뿐이다**(M9/award-ingest v r4 M-1). 이 함수가 보는 것은 목록 포트 호출
 * 하나가 내는 배치의 총수라, 쪽 상한 재개로 배치가 둘 이상인 슬롯에서만 최댓값이 뜻을 갖는다.
 * **한 배치 안**에서 뒤 쪽이 총수를 줄이면 어댑터 누산기가 마지막 값을 쥐고 이 함수는 그 결과만
 * 받는다 — 쪽 상한을 넘지 않는 정상 슬롯은 배치가 하나이므로 그 판은 **열려 있다**(낙찰 runbook
 * §3.2 의 신호 표 · 그 slice 의 **알려진 제한 13**).
 */
internal fun widerTotalOf(
    seen: Int?,
    batch: Int?,
): Int? =
    when {
        seen == null -> batch
        batch == null -> seen
        else -> maxOf(seen, batch)
    }
