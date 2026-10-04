package bidvector.archfixture.violating.transport

import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.writeText

// D-6G2g-9 (운영자 결정 B-2 (가)) — **허용 패키지 안의 파일 시스템 출구**의 음성 fixture.
//
// 두 길 다 1층(바깥 참조 허용 패키지 목록)을 그냥 지나가던 자리다: `java.nio.file` 은 허용 패키지였고
// `java.io` 는 지금도 허용 패키지다(`IOException` 때문에 뺄 수 없다). 2층의 (클래스, 타입) 쌍 등식이
// 그 둘을 각각 **뿌리**와 **낱개 열거**로 잡는지 `TransportSurfaceGateCatchesViolationsTest` 가 잰다.
//
// 어떤 클래스도 실행되지 않는다 — 게이트는 바이트코드만 읽는다.

/** `java.nio.file` 뿌리 — Kotlin 확장으로 경로를 짓고 쓴다(타입 이름을 한 번도 적지 않는다). */
class RogueFileSystemWrite {
    fun leak(name: String) = Path(name).writeText("관문 밖으로 나간 바이트")
}

/** `java.io` 낱개 열거 — 같은 출구를 레거시 타입으로 연다. 패키지 뿌리로는 막을 수 없는 자리다. */
class RogueLegacyFileWrite {
    fun leak(name: String) = File(name).writeText("관문 밖으로 나간 바이트")
}
