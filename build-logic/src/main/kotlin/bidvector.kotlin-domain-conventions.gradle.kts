import bidvector.build.DomainDependencyGateTask

plugins {
    id("bidvector.kotlin-conventions")
}

/**
 * ADR 0006 D-3 — 의존 선언이 **1차 강제 수단**이다. architecture test 는 실제로 참조된 타입만
 * 보므로 "들어왔지만 아직 안 쓴" 프레임워크를 잡지 못한다. 이 게이트가 그 틈을 닫는다.
 */
val domainDependencyGate =
    tasks.register<DomainDependencyGateTask>("domainDependencyGate") {
        description = "domain 모듈의 classpath 에 금지 group 이 오르면 실패한다"
        policyFile = layout.settingsDirectory.file("config/quality/architecture-policy.properties")
        moduleName = project.name
        graphs.add(configurations.named("compileClasspath").flatMap { it.incoming.resolutionResult.rootComponent })
        graphs.add(configurations.named("testCompileClasspath").flatMap { it.incoming.resolutionResult.rootComponent })
        report = layout.buildDirectory.file("reports/domain-dependency-gate/resolved-components.txt")
    }

tasks.named("check") {
    dependsOn(domainDependencyGate)
}
