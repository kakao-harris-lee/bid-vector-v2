// M2/2A included build — `.proto` 단일 출처(`../contracts/proto`)의 Kotlin/Java 생성 전용
// 빌드(D-2A-0 (c)). subproject 가 아니라 included build 인 이유는
// `reports/evidence/m2/2a/scope.md` D-2A-0(1A 게이트 가족이 subproject 안의 생성물을 어떤
// 형태로든 거부)이다. **손으로 쓴 소스가 0**이다 — 이 파일 셋(`settings.gradle.kts`·
// `build.gradle.kts`·`gradle.properties`)이 유일한 내용물이고 S-5 가 그것을 실측한다.
// 래칫·게이트가 적용되지 않는다(D-2A-6) — 정당한 이유는 잴 손 소스가 없기 때문이다.
import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
}

// `bidvector:ml-contract` composite 치환이 이 group:name 좌표로 성립한다
// (`settings.gradle.kts`의 `includeBuild("ml-contract")` + `adapters`의
// `testImplementation("bidvector:ml-contract")`).
group = "bidvector"
version = "unspecified"

kotlin {
    // included build 는 루트 gradle.properties 를 상속하지 않는다 — 이 빌드 자신의
    // `gradle.properties`가 같은 값을 든다.
    jvmToolchain(providers.gradleProperty("bidvector.jvmToolchain").get().toInt())
}

sourceSets {
    main {
        proto {
            // 단일 출처는 저장소 루트의 `contracts/proto`다 — symlink 가 아니라 srcDir
            // 참조다(scope.md in_scope 주석, `SourceLanguageGate`가 symlink 를 따라가는
            // 문제 회피 — 이 빌드는 그 게이트 대상이 아니지만 참조 형태는 정합해 둔다).
            srcDir(layout.projectDirectory.dir("../contracts/proto"))
        }
    }
}

dependencies {
    implementation(platform(libs.grpc.bom))
    implementation(libs.protobuf.java.runtime)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.kotlinx.coroutines.core)
}

// §4b 스모크(scope.md D-2A-0 대가 ③) — grpc-kotlin 1.5.0 + grpc-java 1.84.0 + protobuf-java
// 3.25.9 조합의 protoc·플러그인 해석·실행. 2A 의 `.proto` 는 아직 `service`가 없어(2B 몫)
// 이 두 플러그인이 실제로 파일을 내지는 않지만, 좌표 해석과 protoc 호출 자체가 이
// 조합에서 실패하지 않는지를 여기서 확인한다 — 런타임(생성 코루틴 스텁 클래스 로드) 쪽
// 증거는 `adapters/src/test/kotlin/.../GrpcKotlinStackSmokeTest.kt`가 따로 든다.
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.runtime.get()}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.java.get()}"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.grpc.kotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
                create("grpckt")
            }
        }
    }
}
