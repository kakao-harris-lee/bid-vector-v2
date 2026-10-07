package bidvector.app.relay

import java.sql.SQLException

/**
 * 예외에서 **로그에 실어도 되는 원인 코드만** 뽑는다 — 메시지를 싣지 않는다(접속 문자열·호스트·
 * 공고 내용이 거기로 샌다). SQL 예외는 클래스 이름 + SQLSTATE 5자리까지다.
 *
 * **사본 넷 가운데 둘을 여기로 모았다**(PR #63 finding 6): relay 러너와 평가 커밋 러너.
 * 수집 레인의 둘(`app.collection.CollectionRunner`·`OpeningCollectionLines`)은 이 slice 의
 * in_scope 밖이라 그대로 두고 `OPEN-6F10-CAUSE-CODE-DEDUP` 으로 넘긴다.
 *
 * **왜 `app.relay` 에 사는가**(이름이 relay 를 말하지 않는데): in_scope 안에서 러너 둘이 함께
 * 쓸 수 있는 자리가 러너 패키지뿐이다. `app.wiring` 은 조립 층(tier1)이라 러너가 그쪽을
 * 참조하면 방향이 뒤집히고, 중립 패키지를 새로 만드는 것은 in_scope 밖이다. 선례도 같은
 * 모양이다 — `CollectionLog`·`CollectionTermination` 이 수집 패키지에 살면서 relay·평가 러너가
 * 함께 쓴다. 넷을 중립 자리로 옮기는 것이 `OPEN-6F10-CAUSE-CODE-DEDUP` 의 몫이다.
 */
internal fun causeCodeOf(failure: Exception): String =
    when (failure) {
        is SQLException -> "${failure.javaClass.name}:sqlState=${failure.sqlState}"
        else -> failure.javaClass.name
    }
