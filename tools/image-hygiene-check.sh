#!/usr/bin/env bash
# M6/6C — 이미지 위생 게이트(scope.md ⑥, 설계 검토 (1)). 술어는 Dockerfile 텍스트가
# 아니라 **만든 이미지의 실측**에 건다(2026-09-16 하네스 「게이트 술어는 구조로」, 우회
# (1)(2)): docker inspect Config.User·이미지 안 id -u·금지 패키지 다섯 각각 실제 import·
# 베이스 다이제스트 라벨·이미지 자신의 태그·크기 상한.
#
# 사용법: tools/image-hygiene-check.sh <image-ref>
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "사용법: $0 <image-ref>" >&2
  exit 2
fi

IMAGE_REF="$1"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POLICY_FILE="$REPO_ROOT/config/quality/image-hygiene-policy.properties"

if [ ! -f "$POLICY_FILE" ]; then
  echo "정책 파일이 없다: $POLICY_FILE" >&2
  exit 2
fi

_policy_value() {
  local key="$1"
  local value
  value="$(sed -n "s/^${key}=//p" "$POLICY_FILE" | tail -1)"
  if [ -z "$value" ]; then
    echo "정책 키 ${key} 를 ${POLICY_FILE} 에서 읽지 못했다" >&2
    exit 2
  fi
  echo "$value"
}

FORBIDDEN_PACKAGES="$(_policy_value forbidden.packages)"
SIZE_CAP_BYTES="$(_policy_value size.cap.bytes)"
NONROOT_UID_MIN="$(_policy_value nonroot.uid.min)"

failures=0
fail() {
  echo "위생 위반: $1" >&2
  failures=$((failures + 1))
}

echo "== 이미지 위생 게이트: ${IMAGE_REF} =="

# (0) 이 이미지 자신의 태그가 떠 있지 않은가 — `:latest` 또는 태그 생략(암묵 latest) 거부.
case "$IMAGE_REF" in
  *:latest)
    fail "이미지 참조가 :latest 태그다 — 고정 태그가 필요하다: $IMAGE_REF"
    ;;
  *:*)
    ;;
  *)
    fail "이미지 참조에 태그가 없다(암묵 :latest) — 고정 태그가 필요하다: $IMAGE_REF"
    ;;
esac

# (1) non-root — Config.User 실측 + 컨테이너 안 id -u 실측(둘 다, 하나가 다른 하나를
# 우회할 수 없게).
CONFIG_USER="$(docker image inspect "$IMAGE_REF" --format '{{.Config.User}}')"
if [ -z "$CONFIG_USER" ] || [ "$CONFIG_USER" = "root" ] || [ "$CONFIG_USER" = "0" ] || [ "$CONFIG_USER" = "0:0" ]; then
  fail "Config.User 가 root/미지정이다: '${CONFIG_USER}'"
fi

RUNTIME_UID="$(docker run --rm --entrypoint id "$IMAGE_REF" -u 2>/dev/null || true)"
if [ -z "$RUNTIME_UID" ] || ! [[ "$RUNTIME_UID" =~ ^[0-9]+$ ]]; then
  fail "컨테이너 안 id -u 를 읽지 못했다: '${RUNTIME_UID}'"
elif [ "$RUNTIME_UID" -eq 0 ]; then
  fail "컨테이너가 uid 0(root)으로 돈다"
elif [ "$RUNTIME_UID" -lt "$NONROOT_UID_MIN" ]; then
  fail "컨테이너 uid(${RUNTIME_UID})가 정책 하한(${NONROOT_UID_MIN}) 미만이다"
fi

# (2) 베이스 다이제스트 고정 — Dockerfile 을 다시 읽는 대신 **이미지 라벨**(빌드 시점에
# 이미지 config 에 구워진 값)을 실측한다.
BASE_LABEL="$(docker image inspect "$IMAGE_REF" --format '{{index .Config.Labels "org.bidvector.baseimage"}}' 2>/dev/null || true)"
if [ -z "$BASE_LABEL" ] || [ "$BASE_LABEL" = "<no value>" ]; then
  fail "org.bidvector.baseimage 라벨이 없다 — 베이스 고정을 이미지에서 확인할 수 없다"
elif [[ "$BASE_LABEL" == *:latest* ]]; then
  fail "베이스 이미지 라벨이 :latest 를 참조한다: ${BASE_LABEL}"
elif [[ "$BASE_LABEL" != *"@sha256:"* ]]; then
  fail "베이스 이미지 라벨에 다이제스트(@sha256:)가 없다: ${BASE_LABEL}"
fi

# (3) 금지 패키지 다섯 — 이름 대조가 아니라 **실제 import 시도**(우회 (2), 5A S-1b 형태).
IFS=',' read -r -a forbidden_array <<< "$FORBIDDEN_PACKAGES"
for pkg in "${forbidden_array[@]}"; do
  if docker run --rm --entrypoint python "$IMAGE_REF" -c "import ${pkg}" >/dev/null 2>&1; then
    fail "금지 패키지 '${pkg}' 가 이 이미지에서 import 된다"
  fi
done

# (4) 크기 상한 — `docker image inspect .Size`(단일 플랫폼 이미지 실 크기, `docker save`
# 바이트 수와 일치함을 실측 확인함, commands.md 「크기 지표 선택 근거」)를 잰다. `docker
# images` 의 사람이 읽는 SIZE 열은 이 로컬 환경에서 attestation 매니페스트를 합산해
# 실제 런타임 크기보다 크게 보고한다(2026-09-16 실측 — 같은 이미지에 476MB vs 113.7MB) —
# 지표로 쓰지 않는다.
IMAGE_SIZE_BYTES="$(docker image inspect "$IMAGE_REF" --format '{{.Size}}')"
if ! [[ "$IMAGE_SIZE_BYTES" =~ ^[0-9]+$ ]]; then
  fail "이미지 크기를 읽지 못했다: '${IMAGE_SIZE_BYTES}'"
elif [ "$IMAGE_SIZE_BYTES" -gt "$SIZE_CAP_BYTES" ]; then
  fail "이미지 크기(${IMAGE_SIZE_BYTES} bytes)가 정책 상한(${SIZE_CAP_BYTES} bytes)을 넘는다"
fi

echo "-- 실측 요약 --"
echo "Config.User=${CONFIG_USER} id-u=${RUNTIME_UID} base-label=${BASE_LABEL}"
echo "size_bytes=${IMAGE_SIZE_BYTES} cap_bytes=${SIZE_CAP_BYTES}"

if [ "$failures" -gt 0 ]; then
  echo "== 위생 게이트 실패: ${failures}건 ==" >&2
  exit 1
fi

echo "== 위생 게이트 통과 =="
