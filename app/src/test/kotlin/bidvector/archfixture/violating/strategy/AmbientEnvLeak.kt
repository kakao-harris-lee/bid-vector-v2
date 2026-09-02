package bidvector.archfixture.violating.strategy

import java.util.Locale
import java.util.TimeZone

/** 주변 환경 읽기. 같은 판정이 도메인을 실행 환경에 묶는 것을 막는다. */
class AmbientEnvLeak {
    fun locale(): String = Locale.getDefault().language

    fun zone(): String = TimeZone.getDefault().id
}
