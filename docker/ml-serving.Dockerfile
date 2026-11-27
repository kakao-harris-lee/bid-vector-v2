# M6/6C — ml-serving 이미지(scope.md ①, D-6C-7). multi-stage: builder 가 wheel 을 만들고
# runtime 은 그 wheel + `serving` extra 만 설치한다(5A D-5A-0 (b) — 학습·DB 의존 부재는
# `tools/image-hygiene-check.sh` 가 만든 이미지에서 실측한다, 텍스트가 아니라 구조로).
#
# 베이스는 다이제스트로 고정한다(2026-10-27 실측 `docker buildx imagetools inspect
# python:3.12.16-slim-bookworm` — 멀티플랫폼 index 의 다이제스트). chore(2026-10-27) — 3.12.15
# 에서 올렸다: `CVE-2026-84782`(openssl·libssl3 HIGH)의 수정판 `3.0.22-1~deb12u2` 가 이 상향으로
# 들어온다(같은 태그의 재배포판 `sha256:6a0e06f5…` 은 아직 `…deb12u1` 이라 닫히지 않았다 — 실측).
# python 마이너는 3.12 그대로다(`requires-python >=3.12,<3.13` · `.python-version` 3.12 · cp312
# 동일 — uv.lock 재생성 없음). debian point release 는 12.15 로 그대로다(`scan.os.name.ml-serving`
# 무편집, runbook §8.3.1 의 4). `:latest`·미고정 태그는 쓰지 않는다(위생 게이트 (2)).
FROM python:3.12.16-slim-bookworm@sha256:6dd8b6dab6b3adc7812bab69eca5dbdc1a5cc690ad28306d234ffb4ef8ac9753 AS builder

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
COPY ml-engine/pyproject.toml ml-engine/setup.py ml-engine/.python-version ml-engine/uv.lock ./ml-engine/
COPY ml-engine/tools ./ml-engine/tools
COPY ml-engine/src ./ml-engine/src

WORKDIR /src/ml-engine
# M15/15T(D-15T-1) — **이 이미지가 받는 Python 배포물의 바이트를 `uv.lock` 에 묶는다.** 앞 판은
# lock 을 이 stage 에 들이지 않았고, 그래서 두 자리가 해시 없이 풀렸다 — ① 격리 빌드 환경의
# `setuptools>=68`·`grpcio-tools`(와 그 전이. 그 도구들이 출하 wheel 안의 **생성 stub** 을 만든다)
# ② runtime stage 의 의존 전체. 같은 저장소의 `uv.lock` 은 그 닫힘을 sha256 으로 갖고 있고 CI
# `ml-engine` job 은 그것으로 설치한다. 즉 **검사한 집합과 출하하는 집합이 다른 경로로 풀렸다.**
#
# 해시 목록은 **커밋하지 않는다** — 저장소에 두면 「lock ↔ 생성물」 등식 게이트가 하나 더 필요해진다.
# 원천을 하나(`uv.lock`)로 두고 이 stage 가 그때그때 만든다. 목록은 runtime 에 남기지 않는다
# (아래 설치 RUN 이 지운다) — 남는 것은 설치 **보고서**이고 그것이 결과 등식 게이트의 입력이다.
#
# **`--locked` 다(`--frozen` 이 아니다).** uv 0.9.22 실측(2026-11-27):
#   · 정상 lock 에서 `--network none` 으로 exit 0 — 재해석이 없어 빌드에 네트워크 의존을 더하지 않는다.
#   · `pyproject.toml` 의 판만 올린 사본에서 exit 2 로 끊긴다. 같은 사본에서 `--frozen` 은 exit 0 이고
#     `uv sync --frozen --all-extras --dry-run` 도 exit 0 이다 — 즉 **`ml-engine` job 의 어느 step 도
#     pyproject↔lock 어긋남을 받지 않는다.** 이 줄이 그것을 처음 끊는 자리다.
#   · `--no-dev` 는 이 프로젝트에서 **거동 0** 이다(`dev` 는 dependency group 이 아니라 extra 라
#     `--extra serving` 이 이미 그것을 뺀다 — 산출이 바이트까지 같음을 실측). CI `ml-engine` job 의
#     관용구를 그대로 둔다(새 축약을 만들지 않는다).
#
# 빌드 제약은 **lock 닫힘 전체**(`--all-extras`)다. 격리 빌드 환경이 요구하는 것
# (`[build-system].requires` 와 그 전이)은 `dev` extra 를 타고 lock 에 들어 있고, 제약은 요청된
# 패키지에만 걸리므로 넓은 집합이 좁히는 일은 없다 — 어느 extra 가 빌드 도구를 싣는지에 기대지 않는다.
#
# 비공허를 여기서 잰다: 목록에 해시가 0 건이면 `--require-hashes` 는 「요구할 것이 없다」로 조용히
# 통과한다. 그 초록은 방어의 증거가 아니다.
RUN set -eu; \
    mkdir -p /pip-input; \
    uv export --format requirements.txt --locked --no-emit-project --no-annotate --no-header \
        --all-extras > /pip-input/build-constraints.txt; \
    uv export --format requirements.txt --locked --no-emit-project --no-annotate --no-header \
        --extra serving --no-dev > /pip-input/serving-requirements.txt; \
    for list in /pip-input/build-constraints.txt /pip-input/serving-requirements.txt; do \
        hashes="$(awk 'index($0, "--hash=sha256:") > 0 { n++ } END { print n+0 }' "$list")"; \
        pins="$(awk 'index($0, "==") > 0 { n++ } END { print n+0 }' "$list")"; \
        [ "$hashes" -ge 1 ] && [ "$pins" -ge 1 ] \
            || { echo "$list 의 해시 ${hashes}건·고정 ${pins}건 — uv export 가 해시를 싣지 않았다" >&2; exit 1; }; \
        echo "$list: 고정 ${pins}건 해시 ${hashes}건"; \
    done

# M15/15T(D-15T-3) — 격리 빌드 환경에도 해시를 강제한다. `--require-hashes` 는 빌드 의존마다
# 일치하는 해시를 요구하고, 그 해시는 위 제약 파일(= lock)에서만 온다. `setuptools>=68` 이 lock 의
# 판으로 좁혀지는 것이 의도된 부수 효과다. 음성 대조(일회 실측, commands.md): 제약 파일에서
# `setuptools` 의 **해시 전부**를 다른 값으로 바꿔치우면 이 줄이 exit 2(`Hash mismatch for
# setuptools==…`) 로 끊긴다. **하나만** 바꿔치우면 통과한다 — 그 패키지의 다른 해시(sdist/wheel)가
# 아직 맞기 때문이다. 변이를 넣는 사람은 그 패키지의 집합 전체를 갈아야 한다.
RUN uv build --wheel -o /wheels -b /pip-input/build-constraints.txt --require-hashes

# ---- runtime ----
FROM python:3.12.16-slim-bookworm@sha256:6dd8b6dab6b3adc7812bab69eca5dbdc1a5cc690ad28306d234ffb4ef8ac9753 AS runtime

# D-6C-9(verifier r1 F-2) 이후 — 이 라벨은 **보조 정보**일 뿐이다. Dockerfile 이 손으로
# 적는 자유 텍스트라 실제 `FROM`과 아무 것도 묶지 않는다(라벨만 남기고 FROM 을 떠 있는
# 태그로 바꿔도 라벨은 그대로다, verifier r1 실측). 위생 게이트의 **구속력 있는** 베이스
# 고정 판정은 `tools/image-hygiene-check.sh`가 이 이미지의 `RootFS.Layers` 앞부분을
# `config/quality/image-hygiene-policy.properties`의 **`base.image.digest`**(게이트가 그것을
# `docker buildx imagetools inspect` 로 풀어 실제 layer 체인을 파생한다, D-6C-10 ②)와
# 대조하는 것이다. 위조 불가다 — 같은 바이트가 아니면 같은 다이제스트가 나올 수 없다.
# (앞 판 주석은 `base.image.layers` 를 가리켰는데 그 키는 D-6C-10 에서 없어졌다.)
LABEL org.bidvector.baseimage="python:3.12.16-slim-bookworm@sha256:6dd8b6dab6b3adc7812bab69eca5dbdc1a5cc690ad28306d234ffb4ef8ac9753"

# chore(2026-11-05) — `CVE-2026-109518`(HIGH, `apt`·`libapt-pkg6.0` 2.6.1)이 이 베이스에 새로 잡혀
# 모든 PR 의 `container` job S-22d 가 붉다. **베이스 상향으로는 닫히지 않는다**: 같은 태그의 최신
# index `sha256:e697c818…`(2026-10-28)도 `apt` 2.6.1 이고 debian point release 도 12.15 그대로다
# (실측). 수정판 **2.6.2** 는 `bookworm-security` 에 있으므로 런타임 stage 에서 그 둘만 올린다.
# allowlist 에 등재하지 않는다 — 네 kind 모두 등재 0 을 유지하고 상향으로 해소하는 것이 이
# 저장소의 관례다(`config/quality/vuln-allowlist.properties` 머리 주석 · 선례 chore 2026-10-29).
#
# **하한은 설치 인자가 아니라 `dpkg --compare-versions` 로 닫는다.** `apt-get install` 에는 apk 의
# `'curl>=8.23.0-r0'` 같은 하한 문법이 없고 `pkg=<판>` 정확 고정뿐인데, 정확 고정은 security pocket
# 이 다음 판으로 갈아치우는 날 **모든 빌드를 붉힌다**(web·proxy 가 apk 하한을 고른 것과 같은 사유).
# 그래서 올리기는 무제약으로 하고 하한을 **별도 술어로** 단언한다 — 후보가 하한 아래면 그 자리에서
# 빌드가 비-0 이다(음성 대조 실측: 하한 99 → exit 1). 이 형태는 apk 하한이 「무제약 토큰을 함께
# 적으면 조용히 무시된다」던 함정(web.Dockerfile 주석)도 갖지 않는다 — 술어가 설치와 별개 줄이다.
#
# M9/cve-followups — PR #126 code-review 의 세 지적(`OPEN-CVE-APT-RETRY`)을 받았다.
#
# ① **`-o Acquire::Retries=3`** — 이 RUN 이 CI `container` job 의 **다섯째 네트워크 의존**(배포판
#    아카이브 `deb.debian.org`·`archive.ubuntu.com`)을 만든다. 그 job 은 「네트워크가 한 번 튀는
#    것으로 CI 가 붉어지지 않게 한다」를 앞선 리뷰로 이미 받아 trivy 설치 step 에 `curl --retry 3
#    --retry-all-errors` 를 쓴다. 같은 규율을 apt 쪽에도 둔다 — 없으면 미러가 한 번 흔들릴 때
#    **모든 PR** 의 이미지 빌드가 붉어지고, 그것은 이 RUN 이 애초에 고치려던 상태와 같은 모양이다.
#    두 호출에 각자 붙인다(이미지에 apt 설정 파일을 심지 않는다 — 설정은 이 RUN 안에서만 산다).
#    두 베이스 모두 이 키를 **선언하지 않는다**(`apt-config dump` 에 없다) — man 의 거동이
#    「비-0 일 때만 재시도」라 이 플래그 전까지 재시도가 0 이었다(실측). 주의: `-o` 는 **키 이름을
#    검사하지 않는다** — 오타 키(`Acquire::Retrie`)는 exit 0 으로 조용히 무시되고(실측) 재시도만
#    사라진다. 그래서 이 줄을 고칠 때 확인은 `apt-config -o … dump` 의 반영 여부로 한다.
# ② **`--no-remove`** — `--only-upgrade` 는 「명령줄에 없는 것을 새로 설치하지 않는다」일 뿐
#    **제거를 막지 않는다**. 장래 아카이브 상태에서 해결기가 비필수 패키지를 지우며 상향을
#    만족시키면 `-y` 가 그것을 조용히 승인한다(app 쪽은 `required.executables` 가 한 축을 잡지만
#    이 이미지에는 대응 축이 없다 — 금지 import 와 크기뿐이다). man: 「If any packages are to be
#    removed apt-get immediately aborts」(Configuration Item `APT::Get::Remove`). 오늘 거동은
#    바뀌지 않고(제거 대상 0) 경로 하나가 닫힌다 — **플래그만 바꿔치운 실측**: 제거가 필요한
#    요청에 이 플래그를 붙이면 exit 100 으로 끊기고, 빼면 같은 요청이 exit 0 이다.
# ③ **`~` 접미 수정판은 거짓 빨강 쪽으로 틀린다.** `dpkg --compare-versions 2.6.2~deb12u1 ge 2.6.2`
#    은 **거짓**이다(`~` 는 빈 것보다 앞선다 — `2.6.2+deb12u1` 은 참이다, 둘 다 실측). 상류가
#    수정판을 `~` 형태로 내면 CVE 가 닫혔는데도 이 RUN 이 「닫히지 않는다」는 문면으로 멈춘다.
#    거짓 초록이 아니라 fail-closed 방향이라 지금 고칠 것은 없다 — 물리면 하한을 그 `~` 판으로
#    내려 적거나 상류 버전 성분만 비교한다.
RUN set -eu; \
    apt-get -o Acquire::Retries=3 update -qq; \
    apt-get -o Acquire::Retries=3 install --only-upgrade -y --no-remove --no-install-recommends apt libapt-pkg6.0; \
    for floor in 'apt 2.6.2' 'libapt-pkg6.0 2.6.2'; do \
        set -- $floor; \
        cur="$(dpkg-query -f '${Version}' -W "$1")"; \
        dpkg --compare-versions "$cur" ge "$2" \
            || { echo "$1 $cur 가 하한 $2 미만이다 — CVE-2026-109518 이 닫히지 않는다" >&2; exit 1; }; \
    done; \
    rm -rf /var/lib/apt/lists/*

# non-root — 위생 게이트 (1)이 `docker inspect` 의 `Config.User` 와 이미지 안 `id -u` 로
# 실측한다(고정 UID/GID, 시스템 계정 범위).
RUN groupadd --system --gid 10001 mlserving \
    && useradd --system --uid 10001 --gid mlserving --home-dir /app --shell /usr/sbin/nologin mlserving

WORKDIR /app

COPY --from=builder /wheels/*.whl /tmp/wheels/
COPY --from=builder /pip-input/serving-requirements.txt /tmp/pip-input/serving-requirements.txt

# M15/15T(D-15T-2) — **설치가 두 걸음이다.** 앞 판은 `pip install "${wheel_file}[serving]"` 한 줄로
# extras 닫힘을 pip 이 그 자리에서 해석하게 했다. `pyproject.toml` 이 직접 의존의 **판**은 `==` 로
# 고정하지만 ① 전이 의존(scipy·narwhals 등)은 판도 떠 있었고 ② 어느 것도 **바이트**가 고정돼
# 있지 않았다. 실측(2026-11-27): 출하 이미지의 `narwhals` 는 2.29.0 인데 `uv.lock` 은 2.26.0 이다 —
# 검사한 집합과 출하한 집합이 실제로 갈려 있었다. 이 두 걸음이 그것을 닫는다(그 한 판의 하향이
# 이 slice 의 **의도된 거동 변화**다 — lock 이 CI 가 test 하는 집합이다).
#
# ① 의존: `--require-hashes` 는 목록의 **모든** 항목에 해시를 요구하므로 해시 불일치는 설치
#    **전에** 실패한다. `--only-binary :all:` 로 sdist 로 떨어지는 경로를 닫아 설치 중 임의 빌드
#    코드가 돌지 않게 하고, `--no-deps` 로 목록 밖 유입을 닫는다(닫힘은 lock 이 이미 갖고 있다).
#    `--report` 가 **실제로 내려받은 archive 의 sha256·URL·판**을 남긴다 — 그것이
#    `tools/image-pip-lock-check.sh` 의 입력이고, 플래그와 **독립된** 결과 측정이다. 실측: pip 25.0.1
#    의 보고서는 스키마 판 "1" 이고 항목마다 `download_info.archive_info.hashes.sha256` 을 싣는다
#    (캐시 적중 회차에도 그대로 싣는다 — 해시 없는 항목은 게이트가 통과가 아니라 정지로 받는다).
# ② 프로젝트 wheel: 같은 빌드가 방금 만든 파일이라 해시를 적지 않는다. `--no-index` 로 이 걸음이
#    무엇도 새로 받지 못하게 한다(양성 대조 실측: `--no-index` 아래서 색인에만 있는 판을 요구하면
#    exit 1 `from versions: none`).
# ③ `pip check` — wheel 의 메타데이터가 요구하는 것이 ①의 집합으로 충족되는지. 닫힘이 모자라면
#    여기서 비-0 이다.
#
# 보고서는 `/opt/bidvector/` 에 **root 소유로** 남긴다 — `/app` 은 실행 사용자 소유이고 그 아래
# `artifacts` 는 named volume 의 자리다. 내용은 공개 색인 정보(URL·해시·판)뿐이고 비밀값이 없다.
RUN set -eu; \
    mkdir -p /opt/bidvector; \
    pip install --no-cache-dir --no-compile --require-hashes --only-binary :all: --no-deps \
        --report /opt/bidvector/pip-install-report.json \
        -r /tmp/pip-input/serving-requirements.txt; \
    wheel_file="$(ls /tmp/wheels/*.whl)"; \
    pip install --no-cache-dir --no-compile --no-deps --no-index "$wheel_file"; \
    pip check; \
    chmod 0644 /opt/bidvector/pip-install-report.json; \
    rm -rf /tmp/wheels /tmp/pip-input /root/.cache/pip

# 정책 파일은 저장소 사본을 이미지에 복사한다(D-6C-7) — 정본은 저장소이고 런타임 경로는
# env 로만 지정한다(기본값 없음, `ml_engine.app.server.ServerConfig.from_env`가 빈 env 를
# 거부한다). 이미지 빌드 시점 checksum 은 image-hygiene-check.sh/commands.md 가 기록한다.
COPY ml-engine/policy /app/policy

# liveness/readiness 프로브(scope.md ③, D-6C-3) — 설치된 `ml_engine`·`bidvector`
# stub 위에서 도는 순수 gRPC client 스크립트라 별도 의존이 없다(위 pip install 로 이미
# 충분). `docker/compose.yaml` 의 healthcheck 가 이 둘을 부른다.
COPY docker/probe/liveness.py docker/probe/readiness.py /app/probe/

# `/app/artifacts` 는 job 산출물 루트이자 **job 저널**(`_journal/jobs.json`)의 자리다
# (M6/6B-4 D-6B4-2). 디렉터리와 소유자가 **이미지에** 있어야 하는 이유 둘 — ① 서버가
# 기동 시점에 이 자리에 저널을 쓰고, 쓸 수 없으면 부팅을 거부한다(조용한 비영속 금지)
# ② `docker/compose.yaml` 이 이 경로에 named volume 을 걸 때 Docker 가 **이미지 경로의
# 소유자**로 볼륨을 초기화한다. `chown -R /app` 이 이미 이 디렉터리를 덮는다.
RUN mkdir -p /app/artifacts && chown -R mlserving:mlserving /app

USER mlserving

# 서버는 환경 7개가 전부 있어야 뜬다(ConfigError 로 부팅 거부) — 기본값을 여기서 주지
# 않는다. `docker/compose.yaml` 이 env 를 채운다.
EXPOSE 50051

ENTRYPOINT ["python", "-m", "ml_engine.app.server"]
