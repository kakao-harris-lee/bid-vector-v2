#!/usr/bin/env bash
# M7/7F-local — web·proxy 이미지 빌드의 **단일 진입점**(cr M-5).
#
# 앞 판은 같은 태그에 빌드 정의가 두 벌이었다: ci.yml 이 `docker build -f … -t bidvector/web:local .`
# 를 직접 돌리고(태그·컨텍스트·Dockerfile 을 ci.yml 에 다시 적음), 스모크는 같은 태그를
# `compose build` 로 다시 만들었다. 귀결 둘 — ① 재빌드가 캐시를 놓치면 **게이트가 판정한
# 바이트와 스모크가 실행한 바이트가 다르다** ② compose 쪽 `build:` 에 `args`·`target` 이 붙으면
# ci.yml 의 둘째 정의는 모르는 채로 남는다. 정의를 compose 하나로 모으고, 두 호출자(ci.yml·
# 스모크)가 모두 이 스크립트를 지난다.
#
# 실행:
#
#     ./tools/local-tls-build.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# 인자를 받지 않는다 — 한 호출이 두 이미지를 **함께** 굽는다. 조용히 무시하면
# `./tools/local-tls-build.sh web` 을 「web 만 다시 굽는다」로 믿은 사람에게 아무 말도 하지
# 않는다(검토 r2 cr R2-7).
[ "$#" -eq 0 ] || {
  echo "사용법: $0 (인자를 받지 않는다 — web·proxy 두 이미지를 함께 굽는다)" >&2
  exit 2
}

# shellcheck source=tools/local-tls-env.sh
. "$REPO_ROOT/tools/local-tls-env.sh"

"${BIDVECTOR_LOCAL_TLS_COMPOSE[@]}" build web proxy

for img in bidvector/web:local bidvector/proxy:local; do
  printf '%s %s\n' "$img" "$(docker image inspect "$img" --format '{{.Id}}')"
done
