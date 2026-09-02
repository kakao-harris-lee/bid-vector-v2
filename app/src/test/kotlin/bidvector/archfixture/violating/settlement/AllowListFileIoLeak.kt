package bidvector.archfixture.violating.settlement

import java.util.Formatter

/** 허용 **패키지** 안의 파일 I/O. `Formatter(String)` 은 그 경로를 열고 truncate 한다(Codex 4차 a). */
class AllowListFileIoLeak {
    fun open(path: String): Formatter = Formatter(path)
}
