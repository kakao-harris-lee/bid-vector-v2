package bidvector.workflow.collection

import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptLedger
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.SourceEndpoint
import java.time.Instant

/**
 * **결말 없이 끝난 라운드를 닫는 자리**(D-6G2d-42) — 재개의 **첫 걸음**이라 수집 흐름과 분리한다.
 *
 * 적재와 결말 사이에서 죽은 걷기는 의도·호출 줄만 남긴다. 그 라운드를 `AXIS Failed` 로 닫으면 상한이
 * 그것을 **라운드 하나**로 세고, 쪽이 여럿인 축도 한 쪽 축과 같은 기동에서 멈춘다(앞 판은 꼬리의 호출
 * 줄을 세어 참가자 수와 결측을 상관시켰다).
 *
 * 걷기 식별자는 그 라운드의 **마지막 호출 시각**이다 — 배치를 끝내지 못했으므로 배치의 관측 시각이
 * 없고, 그 라운드가 남긴 원문 행은 이 결말이 `Failed` 라 **어차피 쓰이지 않는다**(축이 미완이므로 그
 * 공고는 `incomplete_axis` 다). 형식 version 을 올려 라운드 표지를 두는 길도 있었으나, 그러면 승인
 * 문서의 「형식 version 2 부터」가 낡고 얻는 것은 **쓰이지 않는 칸의 정확한 이름**뿐이다.
 *
 * 닫는 것은 **재개하는 쪽의 일**이다: 죽은 실행은 아무것도 적지 못하고, 다음 실행만이 「앞 라운드가
 * 끝나지 않았다」를 안다.
 *
 * **닫은 목록을 낸다**(M6/G1-C03) — 이 줄들은 **우리가 적은 장부이지 그 축의 관측이 아니다.** 시각이
 * 「닫은 때」(지금)라 재조회 게이트가 그대로 읽으면 **닫는 행위 자체가 48 시간 창을 열어** 그 축이 영영
 * 다시 불리지 않는다(매 실행이 닫고, 닫자마자 막힌다).
 */
internal class InterruptedRoundCloser(
    private val attempts: AttemptLedger,
    private val now: () -> Instant,
) {
    fun close(): Map<String, Set<SourceEndpoint>> {
        val closed = attempts.read().interruptedRounds()
        closed.forEach { (noticeKey, byAxis) ->
            byAxis.forEach { (axis, lastCall) ->
                attempts.append(
                    CollectionAttempt(
                        noticeKey = noticeKey,
                        axis = axis,
                        outcome = AttemptOutcome.Failed(INTERRUPTED_ROUND),
                        at = now(),
                        kind = AttemptKind.AXIS,
                        walk = lastCall,
                    ),
                )
            }
        }
        return closed.mapValues { (_, byAxis) -> byAxis.keys }
    }
}

/**
 * 중단된 라운드의 결말 코드(D-6G2d-42) — 「이 라운드는 결말을 적지 못했다」이고, 절단 사유가 아니다
 * (그 사유들은 `failureOf` 가 분류한다). 일시 실패이므로 상한이 세고 그 축은 상한 미만에서 다시 불린다.
 */
private const val INTERRUPTED_ROUND = "INTERRUPTED"
