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

# 도구 preflight — **env 조각을 읽기 전에** 본다. 그 조각은 compose 의 `${VAR:?}` 를 만족시키려고
# `openssl rand` 를 돌리므로(빌드는 그 값을 쓰지 않지만 compose 가 값을 요구한다), `openssl` 이
# 없는 호스트에서는 대입 안의 명령 치환이 실패해 **문면 없이 즉사**한다. 스모크에는 같은
# preflight 가 있는데 이 진입점에는 없었다(검토 r2 cr R2-8).
for t in docker openssl; do
  command -v "$t" >/dev/null 2>&1 || { echo "$t 가 필요하다 — 이 진입점은 compose 로 두 이미지를 굽고, 값 조각이 openssl 로 자리값을 만든다" >&2; exit 2; }
done

# shellcheck source=tools/local-tls-env.sh
. "$REPO_ROOT/tools/local-tls-env.sh"

"${BIDVECTOR_LOCAL_TLS_COMPOSE[@]}" build web proxy

# 만든 바이트를 **기록으로 남긴다**(검토 r2 cr R2-6). 스모크의 등식이 원래 말하려던 명제는
# 「게이트가 판정한 바이트 == 스모크가 실행하는 바이트」인데, 앞 판이 실제로 잰 것은 「스모크가
# 다시 빌드해도 태그가 안 바뀐다」였다. 둘은 *빌드 step 과 스모크 사이에서 아무도 그 태그를
# 바꾸지 않는다* 는 전제에서만 같고, 그 전제는 코드에 없었다. 이 기록이 그 전제를 **측정**으로
# 바꾼다 — 스모크가 자기 재빌드 **전에** 이 파일을 읽어 현재 태그와 대조한다.
BIDVECTOR_LOCAL_TLS_ID_FILE="${REPO_ROOT}/build/local-tls/built-image-ids.txt"
mkdir -p "$(dirname "$BIDVECTOR_LOCAL_TLS_ID_FILE")"
: > "$BIDVECTOR_LOCAL_TLS_ID_FILE"
for img in bidvector/web:local bidvector/proxy:local; do
  id="$(docker image inspect "$img" --format '{{.Id}}')"
  printf '%s %s\n' "$img" "$id"
  printf '%s=%s\n' "$img" "$id" >> "$BIDVECTOR_LOCAL_TLS_ID_FILE"
done
