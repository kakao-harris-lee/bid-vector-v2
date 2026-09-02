plugins {
    id("bidvector.kotlin-conventions")
}

// ADR 0006 D-4 — 업무 모듈 교차는 workflow 가 조합한다. 의존 **선언**이 방향의 1차 강제이므로
// 코드가 들어오기 전에도 방향을 선언해 둔다(Gradle 이 역방향과 순환을 설정 단계에서 거부한다).
dependencies {
    implementation(project(":shared-kernel"))
    implementation(project(":procurement"))
    implementation(project(":qualification"))
    implementation(project(":strategy"))
    implementation(project(":decision"))
    implementation(project(":settlement"))
}
