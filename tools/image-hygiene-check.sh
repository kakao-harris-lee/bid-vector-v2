#!/usr/bin/env bash
# M6/6C — 이미지 위생 게이트(scope.md ⑥, 설계 검토 (1), D-6C-9). 술어는 Dockerfile 텍스트나
# 손으로 적는 라벨이 아니라 **만든 이미지의 실행·빌드 산출물**에 건다(2026-09-16 하네스
# 「게이트 술어는 구조로」, verifier r1 F-1·F-2): 실 ENTRYPOINT 로 띄운 컨테이너의 pid 1
# 사용자·이미지의 실제 layer 체인·금지 패키지 다섯 각각 실제 import·이미지 자신의 태그·
# 크기 상한.
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

# F-3(D-6C-9, verifier r1 MEDIUM, 표적 재검증) — 이전 판은 `tail -1`이라 파일 끝에 같은
# 키를 덧붙이면 앞의 문서화된 값이 조용히 무력화됐다. 키마다 매치가 정확히 1이어야
# 정책으로 인정한다 — 0건이면 기존과 같은 사유로, **2건 이상이면 새로 정책 오류로** exit 2.
_policy_value() {
  local key="$1"
  local matches
  matches="$(grep -c "^${key}=" "$POLICY_FILE" || true)"
  if [ "$matches" -eq 0 ]; then
    echo "정책 키 ${key} 를 ${POLICY_FILE} 에서 읽지 못했다" >&2
    exit 2
  fi
  if [ "$matches" -gt 1 ]; then
    echo "정책 키 ${key} 가 ${POLICY_FILE} 에 ${matches}번 선언됐다(정책 오류 — 중복 키는 앞 값을 조용히 무력화한다)" >&2
    exit 2
  fi
  sed -n "s/^${key}=//p" "$POLICY_FILE"
}

FORBIDDEN_PACKAGES="$(_policy_value forbidden.packages)"
SIZE_CAP_BYTES="$(_policy_value size.cap.bytes)"
NONROOT_UID_MIN="$(_policy_value nonroot.uid.min)"
BASE_IMAGE_LAYERS="$(_policy_value base.image.layers)"

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

# (1) non-root — F-1(D-6C-9, verifier r1 HIGH, 표적 재검증). 이전 판은 `docker run
# --entrypoint id`로 이미지의 ENTRYPOINT 를 **덮어써서** "선언된 사용자"만 쟀다 — USER 를
# 유지한 채 setuid 로 root 를 얻는 entrypoint 가 그 형태를 그대로 통과했다(verifier r1
# 실측). 이제는 **실 ENTRYPOINT 로 컨테이너를 띄우고 실제로 도는 pid 1 프로세스의
# 사용자**(`docker top`, 호스트가 커널에서 직접 읽는 값이라 컨테이너 안에서 조작할 수
# 없다)를 잰다. 컨테이너가 서지 않으면(설정 오류·즉시 종료 포함) 그 자체로 위반이다.
CONFIG_USER="$(docker image inspect "$IMAGE_REF" --format '{{.Config.User}}')"
if [ -z "$CONFIG_USER" ] || [ "$CONFIG_USER" = "root" ] || [ "$CONFIG_USER" = "0" ] || [ "$CONFIG_USER" = "0:0" ]; then
  fail "Config.User 가 root/미지정이다: '${CONFIG_USER}'"
fi

HYGIENE_CONTAINER="bidvector-hygiene-check-$$"
cleanup_hygiene_container() {
  docker rm -f "$HYGIENE_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup_hygiene_container EXIT

# D-6C-7 — 서버는 환경 7개가 전부 있어야 뜬다(기본값 없음). 이미지에 이미 구운 정책
# 파일 경로를 그대로 준다 — 위생 게이트 전용 부팅값이지 업무 정책 값이 아니다.
docker run -d --name "$HYGIENE_CONTAINER" \
  -e ML_ENGINE_BIND=0.0.0.0:50051 \
  -e ML_ENGINE_INFERENCE_POLICY=/app/policy/inference-v1.yaml \
  -e ML_ENGINE_TRAINING_POLICY=/app/policy/training-v1.yaml \
  -e ML_ENGINE_EVALUATION_POLICY=/app/policy/evaluation-v1.yaml \
  -e ML_ENGINE_SERVING_POLICY=/app/policy/serving-v1.yaml \
  -e ML_ENGINE_ARTIFACT_OUT_DIR=/app/artifacts \
  -e ML_ENGINE_CODE_VERSION=hygiene-check \
  "$IMAGE_REF" >/dev/null

sleep 1

RUNTIME_UID=""
CONTAINER_RUNNING="$(docker inspect "$HYGIENE_CONTAINER" --format '{{.State.Running}}' 2>/dev/null || echo false)"
if [ "$CONTAINER_RUNNING" != "true" ]; then
  fail "실 ENTRYPOINT 컨테이너가 뜨지 않았다(State.Running=${CONTAINER_RUNNING}) — pid 1 사용자를 잴 수 없다"
else
  TOP_DATA_ROWS="$(docker top "$HYGIENE_CONTAINER" -eo pid,uid,comm 2>/dev/null | tail -n +2 | sed '/^[[:space:]]*$/d' || true)"
  ROW_COUNT="$(printf '%s\n' "$TOP_DATA_ROWS" | grep -c . || true)"
  if [ -z "$TOP_DATA_ROWS" ] || [ "$ROW_COUNT" -ne 1 ]; then
    fail "컨테이너 안 프로세스를 하나로 특정하지 못했다(행 수=${ROW_COUNT}) — exec-form 단일 프로세스를 기대했다(컨테이너가 그 사이 종료됐을 수 있다)"
  else
    RUNTIME_UID="$(printf '%s\n' "$TOP_DATA_ROWS" | awk '{print $2}')"
  fi
fi

if [ -n "$RUNTIME_UID" ]; then
  if ! [[ "$RUNTIME_UID" =~ ^[0-9]+$ ]]; then
    fail "실 pid 1 사용자를 숫자 uid 로 읽지 못했다: '${RUNTIME_UID}'"
  elif [ "$RUNTIME_UID" -eq 0 ]; then
    fail "컨테이너의 실 pid 1 이 uid 0(root)으로 돈다"
  elif [ "$RUNTIME_UID" -lt "$NONROOT_UID_MIN" ]; then
    fail "컨테이너의 실 pid 1 uid(${RUNTIME_UID})가 정책 하한(${NONROOT_UID_MIN}) 미만이다"
  fi
fi

# (2) 베이스 다이제스트 고정 — F-2(D-6C-9, verifier r1 HIGH, 표적 재검증). 라벨 문자열은
# Dockerfile 이 손으로 적는 자유 텍스트라 실제 `FROM`과 묶이지 않는다(`FROM`을 떠 있는
# 태그로 바꾸고 라벨만 유지해도 통과했다, verifier r1 실측) — **보조 정보로만** 참고
# 출력한다. 구속력 있는 판정은 이 이미지의 `RootFS.Layers` 앞부분이 정책의
# `base.image.layers`(고정 다이제스트가 실제로 낸 layer 체인, 위조 불가)와 순서대로
# 일치하는지다.
BASE_LABEL="$(docker image inspect "$IMAGE_REF" --format '{{index .Config.Labels "org.bidvector.baseimage"}}' 2>/dev/null || true)"

IFS=',' read -r -a expected_base_layers <<< "$BASE_IMAGE_LAYERS"
# `mapfile`(bash 4+)을 쓰지 않는다 — macOS 기본 `/bin/bash`가 3.2 라 로컬 실행이 깨진다.
actual_layers=()
while IFS= read -r layer_line; do
  [ -n "$layer_line" ] && actual_layers+=("$layer_line")
done < <(docker image inspect "$IMAGE_REF" --format '{{json .RootFS.Layers}}' 2>/dev/null | jq -r '.[]' 2>/dev/null || true)

expected_count=${#expected_base_layers[@]}
if [ "${#actual_layers[@]}" -lt "$expected_count" ]; then
  fail "이미지 layer 수(${#actual_layers[@]})가 기대 베이스 layer 수(${expected_count})보다 적다 — 베이스가 정책과 다르다"
else
  base_mismatch=0
  for i in "${!expected_base_layers[@]}"; do
    if [ "${actual_layers[$i]:-}" != "${expected_base_layers[$i]}" ]; then
      base_mismatch=1
      break
    fi
  done
  if [ "$base_mismatch" -ne 0 ]; then
    fail "이미지의 앞 ${expected_count} layer 가 정책의 고정 베이스 layer 체인과 다르다 — FROM 이 다른 이미지를 가리킨다(라벨과 무관하게 위반)"
  fi
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
echo "Config.User=${CONFIG_USER} 실pid1-uid=${RUNTIME_UID} base-label(보조)=${BASE_LABEL}"
echo "base-layer-접두-일치=$([ "$expected_count" -gt 0 ] && [ "${base_mismatch:-1}" -eq 0 ] && echo true || echo false)"
echo "size_bytes=${IMAGE_SIZE_BYTES} cap_bytes=${SIZE_CAP_BYTES}"

if [ "$failures" -gt 0 ]; then
  echo "== 위생 게이트 실패: ${failures}건 ==" >&2
  exit 1
fi

echo "== 위생 게이트 통과 =="
