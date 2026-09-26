# M6/6A-2a — Kotlin 앱 이미지(scope.md D-6A2a-2·3). **밖에서 만든 `bootJar` 를 넣는다** —
# 이미지 안에서 Gradle 을 돌리지 않는다(호스트 규칙상 무거운 빌드는 하나뿐이고, CI `container`
# job 은 이미 `setup-gradle` 을 갖췄다). 대가는 `docker build` 만으로는 이미지가 안 나오는 것이다:
#
#     ./gradlew --no-daemon :app:bootJar
#     docker build -f docker/app.Dockerfile -t bidvector/app:local .
#
# `app/build/libs/app.jar` 가 없으면 아래 `COPY` 가 **그 경로를 이름으로 대며** 실패한다.
# 잘못된 아카이브(평범한 `app-plain.jar` 등)를 넣으면 `extract` 가 실패하고 그 자리에서
# 무엇을 해야 하는지 말한다 — 두 실패 모두 이미지가 나오지 않는다(fail-closed).
#
# 베이스는 **JRE 21**(JDK 아님)이고 다이제스트로 고정한다(2026-09-26 실측
# `docker pull eclipse-temurin:21-jre-noble`). `:latest`·미고정 태그는 쓰지 않는다 —
# 위생 게이트가 이미지 참조의 태그와 실제 layer 체인을 둘 다 본다.
FROM eclipse-temurin:21-jre-noble@sha256:7edbe8532195c8222735caca437e56780e1fc7db08a4b4b4af77fe188cccc15f AS builder

WORKDIR /builder

# 빌드 인자·환경 슬롯에 **값이 없다** — 자격증명이 레이어에 박히는 경로(우회 4)를 만들지
# 않는다. 이 `ARG` 는 jar 의 위치(공개 경로)뿐이고 기본값이 정본이다.
ARG JAR_FILE=app/build/libs/app.jar
COPY ${JAR_FILE} application.jar

# Boot 4.1 표준 형태 — 의존 layer 와 앱 layer 를 갈라 Docker 캐시가 산다. 추가 도구가 없다
# (`jarmode=tools` 는 Boot 가 배포물 안에 이미 넣어 둔다).
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted \
    || { \
        echo "application.jar 을 layered bootJar 로 풀 수 없다 — './gradlew --no-daemon :app:bootJar' 로 app/build/libs/app.jar 를 먼저 만든다(D-6A2a-2). app-plain.jar 는 배포물이 아니다." >&2; \
        exit 1; \
    }

# ---- runtime ----
FROM eclipse-temurin:21-jre-noble@sha256:7edbe8532195c8222735caca437e56780e1fc7db08a4b4b4af77fe188cccc15f AS runtime

# D-6C-9 와 같은 규율 — 이 라벨은 **보조 정보**다. Dockerfile 이 손으로 적는 자유 텍스트라
# 실제 `FROM` 과 아무 것도 묶지 않는다. 구속력 있는 베이스 고정 판정은
# `tools/image-hygiene-check.sh` 가 이 이미지의 `RootFS.Layers` 앞부분을 정책 다이제스트에서
# 파생한 실제 layer 체인과 대조하는 것이다.
LABEL org.bidvector.baseimage="eclipse-temurin:21-jre-noble@sha256:7edbe8532195c8222735caca437e56780e1fc7db08a4b4b4af77fe188cccc15f"

# non-root — 위생 게이트가 `Config.User` 와 **실 ENTRYPOINT 로 띄운 컨테이너의 모든 프로세스
# uid** 를 정책 하한과 대조한다(ml-serving 과 같은 고정 UID/GID·시스템 계정 범위).
RUN groupadd --system --gid 10001 bidvector \
    && useradd --system --uid 10001 --gid bidvector --home-dir /application --shell /usr/sbin/nologin bidvector

WORKDIR /application

# layer 넷을 각자 COPY 한다 — 변한 layer 만 다시 밀린다. 순서는 「덜 바뀌는 것부터」다.
#
# **`--chown` 을 쓰지 않는다**(code-review r1 LOW). 런타임 사용자에게 소유권을 주면 침해된 앱
# 프로세스가 `application.jar` 과 `lib/` 의 jar 를 **재기동 뒤에도 남는 형태로** 고칠 수 있다.
# 이 앱은 `/application` 에 쓸 이유가 없다(읽기만 한다 — 임시 파일은 `java.io.tmpdir` 로 간다).
# root 소유·전체 읽기가 기본 mode 이고, `USER bidvector` 는 그것으로 충분히 읽는다. Boot 공식
# Dockerfile 형태도 `--chown` 을 쓰지 않는다.
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

USER bidvector

# 설정은 **전부 환경변수이고 기본값이 없다**(관리 포트 하나만 조립 근이 기본값을 갖는다,
# D-6A2a-4). 여기서 `ENV` 로 값을 주지 않는다 — `docker/compose.yaml` 이 `${VAR:?}` 로 채운다.
# 값이 없으면 Spring 이 `BindException` 으로 기동을 거부한다(fail-fast, 6A-1·6F-8 의 기존 구조).
#
# HEALTHCHECK 을 이 이미지에 넣지 않는다. 도구는 있다(실측: 이 베이스에 `curl` 이 이미
# 들어 있다) — 막는 것은 **포트를 가리킬 방법**이다. 관리 포트는 환경 값이고 그 기본값의
# 정본은 조립 근(Kotlin)이다. Dockerfile 에 두 번째 기본값을 구우면 드리프트 표면이 하나
# 늘고(매직 넘버·중복 금지), 기본값 없이 `${MANAGEMENT_SERVER_PORT}` 만 쓰면 bare
# `docker run` 이 멀쩡한 앱을 unhealthy 로 신고한다. 그래서 healthcheck 는 그 변수를 이미
# 들고 있는 `docker/compose.yaml` 한 자리에만 둔다(ml-serving 도 같은 자리다).
#
# **`EXPOSE` 도 두지 않는다**(code-review r1 LOW). 바로 위 단락이 HEALTHCHECK 을 뺀 이유로
# 「Dockerfile 에 두 번째 기본값을 구우면 드리프트 표면이 하나 늘고(매직 넘버·중복 금지)」를
# 들었는데, `EXPOSE 8080 8081` 이 바로 그 두 번째 기본값이었다 — CI 는 18080/18081 을 쓰므로
# 그 메타데이터는 이미 틀렸다. 문서 전용이라 깨지는 것은 없고, 잃는 것도 없다: 포트 publish 는
# `docker/compose.yaml` 이 명시한다. 덧붙여 `EXPOSE` 는 `docker run -P` 가 관리 포트까지
# publish 하게 만들었다(`OPEN-6A2A-MGMT-PORT-EXPOSURE` 의 한 갈래) — 그 갈래도 함께 닫힌다.

ENTRYPOINT ["java", "-jar", "application.jar"]
