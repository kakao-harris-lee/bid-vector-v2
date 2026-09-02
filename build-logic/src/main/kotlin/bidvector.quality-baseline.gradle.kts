import bidvector.build.QualityBaselineTask

// 모듈별 입력은 각 모듈의 convention plugin 이 붙인다 — 루트가 subproject 의 configuration 을
// 먼저 읽으려 하면 평가 순서에 걸린다.
tasks.register<QualityBaselineTask>("qualityBaseline") {
    group = "verification"
    description = "OPEN-ADR-06 입력 — v2-지침서.md §5 의 크기·결합도 축과 타입 축을 잰다"
    report = layout.buildDirectory.file("reports/quality-baseline/quality-baseline.md")
}
