# M6/6C — ml-serving 이미지(scope.md ①, D-6C-7). multi-stage: builder 가 wheel 을 만들고
# runtime 은 그 wheel + `serving` extra 만 설치한다(5A D-5A-0 (b) — 학습·DB 의존 부재는
# `tools/image-hygiene-check.sh` 가 만든 이미지에서 실측한다, 텍스트가 아니라 구조로).
#
# 베이스는 다이제스트로 고정한다(2026-09-16 실측 `docker pull python:3.12.8-slim-bookworm`).
# `:latest`·미고정 태그는 쓰지 않는다(위생 게이트 (2)).
FROM python:3.12.8-slim-bookworm@sha256:2199a62885a12290dc9c5be3ca0681d367576ab7bf037da120e564723292a2f0 AS builder

# uv 바이너리만 뜯어 쓴다(설치 스크립트·curl 파이프 없음) — CI(`astral-sh/setup-uv@v7`)와 같은
# 버전(0.9.22)으로 맞춰 두 축(이미지·CI)이 어긋나지 않게 한다.
#
# **두 축의 고정 강도가 다르다**(PR #68 /code-review): 이 이미지 쪽은 아래처럼 **다이제스트**까지 박지만
# CI 쪽은 `astral-sh/setup-uv@v7` 의 `version: "0.9.22"` — **태그**다. 같은 문자열이라고 같은 바이트가
# 아니다. 둘을 함께 올릴 때 이미지 쪽은 다이제스트를 다시 떠야 하고, CI 쪽은 태그만 고치면 된다.
#
# M6/6E-2b B-7 — **다이제스트까지 고정한다.** 태그만 적으면 `0.9.22` 가 가리키는 바이트가 뒤에
# 바뀌어도 빌드가 조용히 따라간다(위 두 `FROM` 이 다이제스트를 적는 것과 같은 사유). 값은
# 멀티플랫폼 index 의 다이제스트라 빌드 플랫폼 선택은 그대로 둔다(`docker buildx imagetools
# inspect ghcr.io/astral-sh/uv:0.9.22`, 2026-10-08 실측). 이 stage 의 산출물은 wheel 뿐이고
# uv 자체는 runtime 이미지에 들어가지 않으므로 취약점 게이트의 판정 대상은 아니다 — 여기서
# 닫는 것은 **빌드 입력의 가변성**이다.
COPY --from=ghcr.io/astral-sh/uv:0.9.22@sha256:2320e6c239737dc73cccce393a8bb89eba2383d17018ee91a59773df802c20e6 /uv /usr/local/bin/uv

# `ml-engine/tools/generate_contracts.py` 의 `PROTO_ROOT`(parents[2]/contracts/proto)가
# `contracts/proto` 를 `ml-engine/` 의 형제 디렉터리로 기대한다(setup.py 의 wheel 빌드 훅이
# 이 경로를 통해 `.proto` 를 생성한다, D-5E-8) — 컨텍스트(repo root)의 상대 구조를 그대로
# 옮긴다.
WORKDIR /src
COPY contracts/proto ./contracts/proto
COPY ml-engine/pyproject.toml ml-engine/setup.py ml-engine/.python-version ./ml-engine/
COPY ml-engine/tools ./ml-engine/tools
COPY ml-engine/src ./ml-engine/src

WORKDIR /src/ml-engine
# uv.lock 은 dev 그룹(pytest·mypy 등)까지 고정한다 — wheel 빌드는 `[build-system].requires`
# (grpcio-tools, PEP 517 격리 빌드 환경)만 필요해 lock 파일 없이 `uv build` 를 그대로 쓴다
# (CI S-11 단계와 같은 명령, `uv sync` 를 이 stage 에서 부르지 않는다).
RUN uv build --wheel -o /wheels

# ---- runtime ----
FROM python:3.12.8-slim-bookworm@sha256:2199a62885a12290dc9c5be3ca0681d367576ab7bf037da120e564723292a2f0 AS runtime

# D-6C-9(verifier r1 F-2) 이후 — 이 라벨은 **보조 정보**일 뿐이다. Dockerfile 이 손으로
# 적는 자유 텍스트라 실제 `FROM`과 아무 것도 묶지 않는다(라벨만 남기고 FROM 을 떠 있는
# 태그로 바꿔도 라벨은 그대로다, verifier r1 실측). 위생 게이트의 **구속력 있는** 베이스
# 고정 판정은 `tools/image-hygiene-check.sh`가 이 이미지의 `RootFS.Layers` 앞부분을
# `config/quality/image-hygiene-policy.properties`의 `base.image.layers`(고정 다이제스트의
# 실제 layer 체인, 위조 불가 — 같은 바이트가 아니면 같은 다이제스트가 나올 수 없다)와
# 대조하는 것이다.
LABEL org.bidvector.baseimage="python:3.12.8-slim-bookworm@sha256:2199a62885a12290dc9c5be3ca0681d367576ab7bf037da120e564723292a2f0"

# non-root — 위생 게이트 (1)이 `docker inspect` 의 `Config.User` 와 이미지 안 `id -u` 로
# 실측한다(고정 UID/GID, 시스템 계정 범위).
RUN groupadd --system --gid 10001 mlserving \
    && useradd --system --uid 10001 --gid mlserving --home-dir /app --shell /usr/sbin/nologin mlserving

WORKDIR /app

COPY --from=builder /wheels/*.whl /tmp/wheels/
# extras 문법(`pkg[serving]`) 은 로컬 wheel 경로에도 붙는다 — `[project.dependencies]`
# (grpcio·protobuf, 항상)+`serving` extra(numpy·lightgbm·pyyaml)만 들어오고 dev/training
# 전용 도구(grpcio-tools·pytest 등)는 들어오지 않는다(5A D-5A-0 (b)).
RUN wheel_file="$(ls /tmp/wheels/*.whl)" \
    && pip install --no-cache-dir --no-compile "${wheel_file}[serving]" \
    && rm -rf /tmp/wheels /root/.cache/pip

# 정책 파일은 저장소 사본을 이미지에 복사한다(D-6C-7) — 정본은 저장소이고 런타임 경로는
# env 로만 지정한다(기본값 없음, `ml_engine.app.server.ServerConfig.from_env`가 빈 env 를
# 거부한다). 이미지 빌드 시점 checksum 은 image-hygiene-check.sh/commands.md 가 기록한다.
COPY ml-engine/policy /app/policy

# liveness/readiness 프로브(scope.md ③, D-6C-3) — 설치된 `ml_engine`·`bidvector`
# stub 위에서 도는 순수 gRPC client 스크립트라 별도 의존이 없다(위 pip install 로 이미
# 충분). `docker/compose.yaml` 의 healthcheck 가 이 둘을 부른다.
COPY docker/probe/liveness.py docker/probe/readiness.py /app/probe/

RUN mkdir -p /app/artifacts && chown -R mlserving:mlserving /app

USER mlserving

# 서버는 환경 7개가 전부 있어야 뜬다(ConfigError 로 부팅 거부) — 기본값을 여기서 주지
# 않는다. `docker/compose.yaml` 이 env 를 채운다.
EXPOSE 50051

ENTRYPOINT ["python", "-m", "ml_engine.app.server"]
