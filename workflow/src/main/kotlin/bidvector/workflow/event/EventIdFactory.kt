package bidvector.workflow.event

/**
 * 이벤트 식별자 생성 port(scope.md ⑥, D-M4-4 (a)) — 4A `Clock`과 같은 이유로 port다:
 * `workflow`는 외부 좌표(시각·난수·프레임워크)를 직접 잡지 않는다. 실 기제(UUIDv7 등)는
 * 4C-2 어댑터가 고른다.
 */
fun interface EventIdFactory {
    fun newId(): EventId
}
