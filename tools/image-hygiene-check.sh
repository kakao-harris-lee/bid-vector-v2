#!/usr/bin/env bash
# M6/6C — 이미지 위생 게이트(scope.md ⑥, 설계 검토 (1), D-6C-9·D-6C-10). 술어는 Dockerfile
# 텍스트나 손으로 적는 라벨이 아니라 **만든 이미지의 실행·빌드 산출물**에 건다(하네스
# 「게이트 술어는 구조로」, verifier r1 F-1·F-2·r2 R2-3·R2-4): 능력이 차단된 채 실 ENTRYPOINT
# 로 띄운 컨테이너의 모든 프로세스 사용자·정책 다이제스트에서 파생한 이미지의 실제 layer
# 체인·금지 패키지 다섯 각각 실제 import·이미지 자신의 태그·크기 상한.
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

# R2-8(verifier r2 LOW) — 이 게이트는 `jq`로 `RootFS.Layers` JSON 을 판독한다. `ubuntu-latest`
# 에 선탑재라 지금은 서지만 워크플로가 설치를 선언하지 않는다 — 없으면 (2) 절이 값 획득
# 실패를 "베이스가 정책과 다르다"는 엉뚱한 사유로 보고할 뻔했다. 여기서 먼저, 분명하게 끊는다.
if ! command -v jq >/dev/null 2>&1; then
  echo "jq 가 필요하다 — 이 게이트가 이미지의 RootFS.Layers 를 판독하는 데 쓴다" >&2
  exit 2
fi

# R2-1(D-6C-10 ①, verifier r2 HIGH, 표적 재검증) — 직전 판(F-3 시정)은 중복 키만 잡고
# **값이 비어 있거나 CRLF 가 섞인 경우를 통과시켰다**(수치 비교가 `elif` 조건 안에 있어
# `[ 가 "integer expression expected"로 비-0 을 내면 조건이 거짓으로 읽혀 위반이 조용히
# 삼켜졌다 — `base.image.layers=`를 비우면 요약에 `false`를 찍으면서도 exit 0 이었다).
# 이제는 값 모양도 검증한다: CRLF 절삭 → 빈 값 거부 → kind 별 모양(수치/목록/텍스트).
_policy_value() {
  local key="$1"
  local kind="${2:-text}" # text | numeric | list
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
  local raw
  raw="$(sed -n "s/^${key}=//p" "$POLICY_FILE" | tr -d '\r')"
  if [ -z "$raw" ]; then
    echo "정책 키 ${key} 의 값이 비어 있다(정책 오류 — 값 없는 키는 정책 오류다)" >&2
    exit 2
  fi
  case "$kind" in
    numeric)
      if ! [[ "$raw" =~ ^[0-9]+$ ]]; then
        echo "정책 키 ${key} 의 값이 숫자가 아니다: '${raw}'" >&2
        exit 2
      fi
      ;;
    list)
      local IFS=','
      local -a items
      read -r -a items <<< "$raw"
      if [ "${#items[@]}" -lt 1 ]; then
        echo "정책 키 ${key} 는 최소 1개 원소가 있어야 한다: '${raw}'" >&2
        exit 2
      fi
      local item
      for item in "${items[@]}"; do
        if [ -z "$item" ]; then
          echo "정책 키 ${key} 에 빈 원소가 있다: '${raw}'" >&2
          exit 2
        fi
      done
      ;;
    text) ;;
    *)
      echo "알 수 없는 정책 값 종류 '${kind}'(스크립트 결함)" >&2
      exit 2
      ;;
  esac
  printf '%s' "$raw"
}

# 수치 비교를 `elif` 체인에서 뽑아 각자 독립 `if`로 둔다(D-6C-10 ① — set -e 가 elif/if
# 조건을 면제하는 것과 무관하게 만든다). 두 값 다 여기서 다시 모양을 본다 — `_policy_value`
# 가 이미 정책 쪽을 검증하지만, 이미지에서 읽은 값(우변이 아니라 좌변)까지 방어한다.
_check_at_most() {
  local label="$1" actual="$2" limit="$3"
  if ! [[ "$actual" =~ ^[0-9]+$ ]]; then
    fail "${label} 을(를) 숫자로 읽지 못했다: '${actual}'"
    return
  fi
  if ! [[ "$limit" =~ ^[0-9]+$ ]]; then
    fail "${label} 의 정책 상한을 숫자로 읽지 못했다: '${limit}'"
    return
  fi
  if [ "$actual" -gt "$limit" ]; then
    fail "${label}(${actual})이(가) 정책 상한(${limit})을 넘는다"
  fi
}

FORBIDDEN_PACKAGES="$(_policy_value forbidden.packages list)"
SIZE_CAP_BYTES="$(_policy_value size.cap.bytes numeric)"
NONROOT_UID_MIN="$(_policy_value nonroot.uid.min numeric)"
BASE_IMAGE_REPO="$(_policy_value base.image.repo text)"
BASE_IMAGE_DIGEST="$(_policy_value base.image.digest text)"

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

# (1) non-root — F-1(D-6C-9)·R2-3(D-6C-10 ③, 표적 재검증). `--entrypoint id` 로 이미지의
# ENTRYPOINT 를 덮어써 "선언된 사용자"만 재던 형태는 setuid entrypoint 를 놓쳤다(r1). 그
# 처방(실 ENTRYPOINT + `docker top`)도 **한 순간의 표집**이라 표집 뒤에 상승하는
# entrypoint 를 놓쳤다(r2 실측: t=2s 비특권 → t=8s root). 표집을 늘리는 대신 **능력 자체를
# 차단**한다 — `--security-opt no-new-privileges`로 띄우면 setuid/setgid 상승이 구성상
# 불가능해진다(출하 이미지가 이 제약 아래 정상 기동함을 실측 확인, commands.md). `docker
# top` 실측은 방어 심도로 남긴다.
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
  --security-opt no-new-privileges \
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
  fail "실 ENTRYPOINT 컨테이너가 뜨지 않았다(State.Running=${CONTAINER_RUNNING}) — 프로세스 사용자를 잴 수 없다"
else
  TOP_DATA_ROWS="$(docker top "$HYGIENE_CONTAINER" -eo pid,uid,comm 2>/dev/null | tail -n +2 | sed '/^[[:space:]]*$/d' || true)"
  if [ -z "$TOP_DATA_ROWS" ]; then
    fail "컨테이너 안에서 도는 프로세스를 하나도 찾지 못했다(그 사이 종료됐을 수 있다)"
  else
    # R2-5(verifier r2 LOW) — 「행 수 ≠ 1」이 아니라 **행마다 uid** 를 본다. 정상 이미지가
    # 프로세스 여러 개가 되는 날에도 root 자식이 조용히 통과하지 않는다.
    while IFS= read -r row; do
      row_uid="$(printf '%s\n' "$row" | awk '{print $2}')"
      [ -z "$RUNTIME_UID" ] && RUNTIME_UID="$row_uid"
      if ! [[ "$row_uid" =~ ^[0-9]+$ ]]; then
        fail "컨테이너 안 프로세스 uid 를 숫자로 읽지 못했다: '${row_uid}'"
      elif [ "$row_uid" -eq 0 ]; then
        fail "컨테이너 안 프로세스 중 하나 이상이 uid 0(root)으로 돈다"
      elif [ "$row_uid" -lt "$NONROOT_UID_MIN" ]; then
        fail "컨테이너 안 프로세스 uid(${row_uid})가 정책 하한(${NONROOT_UID_MIN}) 미만이다"
      fi
    done <<< "$TOP_DATA_ROWS"
  fi
fi

# (2) 베이스 다이제스트 고정 — R2-4(D-6C-10 ②, 표적 재검증) 이후. 정책 값은 layer 목록이
# 아니라 고정 다이제스트 **하나**(Dockerfile 의 FROM 과 같은 값 — diff 에서 뜻이 보인다,
# r2 실측: 목록은 변이 이미지의 것으로 바꿔치면 통과했다). 게이트가 그 다이제스트를
# `docker buildx imagetools inspect`로 풀어(멀티플랫폼 index → linux/amd64 manifest) pull 한
# 뒤 실제 layer 체인을 **그때그때 파생**한다 — 바꿔치기 표면이 다이제스트 하나로 준다.
# 네트워크·registry 접근이 없으면 이 축은 판정 불가로 실패한다(알려진 제한, checklist.md).
BASE_LABEL="$(docker image inspect "$IMAGE_REF" --format '{{index .Config.Labels "org.bidvector.baseimage"}}' 2>/dev/null || true)"

base_layer_prefix_ok=false
# `|| true`가 이 subshell **안**에 있어야 한다 — `pipefail`(스크립트 상단) 아래서 조회
# 실패가 대입 자체의 실패로 번져 `set -e`가 스크립트를 죽이는 것을 막는다. 실패는 빈
# 문자열로만 남고, 아래 `[ -z ]` 검사가 그것을 `fail()`로 정직하게 옮긴다.
BASE_PLATFORM_DIGEST="$(docker buildx imagetools inspect --raw "${BASE_IMAGE_REPO}@${BASE_IMAGE_DIGEST}" 2>/dev/null | jq -r '.manifests[]? | select(.platform.os=="linux" and .platform.architecture=="amd64") | .digest' 2>/dev/null | head -1 || true)"

if [ -z "$BASE_PLATFORM_DIGEST" ]; then
  fail "정책 기준 베이스(${BASE_IMAGE_REPO}@${BASE_IMAGE_DIGEST})의 linux/amd64 manifest 를 조회하지 못했다 — 네트워크/registry 접근이 필요하다(알려진 제한)"
else
  if ! docker image inspect "${BASE_IMAGE_REPO}@${BASE_PLATFORM_DIGEST}" >/dev/null 2>&1; then
    docker pull "${BASE_IMAGE_REPO}@${BASE_PLATFORM_DIGEST}" >/dev/null 2>&1 || true
  fi

  expected_base_layers=()
  while IFS= read -r layer_line; do
    [ -n "$layer_line" ] && expected_base_layers+=("$layer_line")
  done < <(docker image inspect "${BASE_IMAGE_REPO}@${BASE_PLATFORM_DIGEST}" --format '{{json .RootFS.Layers}}' 2>/dev/null | jq -r '.[]' 2>/dev/null || true)

  if [ "${#expected_base_layers[@]}" -eq 0 ]; then
    fail "정책 기준 베이스에서 layer 목록을 파생하지 못했다(pull 실패 가능성) — 판정 불가"
  else
    # `mapfile`(bash 4+)을 쓰지 않는다 — macOS 기본 `/bin/bash`가 3.2 라 로컬 실행이 깨진다.
    actual_layers=()
    while IFS= read -r layer_line; do
      [ -n "$layer_line" ] && actual_layers+=("$layer_line")
    done < <(docker image inspect "$IMAGE_REF" --format '{{json .RootFS.Layers}}' 2>/dev/null | jq -r '.[]' 2>/dev/null || true)

    expected_count=${#expected_base_layers[@]}
    if [ "${#actual_layers[@]}" -lt "$expected_count" ]; then
      fail "이미지 layer 수(${#actual_layers[@]})가 파생한 베이스 layer 수(${expected_count})보다 적다 — 베이스가 정책과 다르다"
    else
      base_mismatch=0
      for i in "${!expected_base_layers[@]}"; do
        if [ "${actual_layers[$i]:-}" != "${expected_base_layers[$i]}" ]; then
          base_mismatch=1
          break
        fi
      done
      if [ "$base_mismatch" -ne 0 ]; then
        fail "이미지의 앞 ${expected_count} layer 가 정책이 파생한 베이스 layer 체인과 다르다 — FROM 이 다른 이미지를 가리킨다(라벨과 무관하게 위반)"
      else
        base_layer_prefix_ok=true
      fi
    fi
  fi
fi

# (3) 금지 패키지 다섯 — 이름 대조가 아니라 **실제 import 시도**(우회 (2), 5A S-1b 형태).
IFS=',' read -r -a forbidden_array <<< "$FORBIDDEN_PACKAGES"
for pkg in "${forbidden_array[@]}"; do
  if docker run --rm --security-opt no-new-privileges --entrypoint python "$IMAGE_REF" -c "import ${pkg}" >/dev/null 2>&1; then
    fail "금지 패키지 '${pkg}' 가 이 이미지에서 import 된다"
  fi
done

# (4) 크기 상한 — `docker image inspect .Size`(단일 플랫폼 이미지 실 크기, `docker save`
# 바이트 수와 일치함을 실측 확인함, commands.md 「크기 지표 선택 근거」)를 잰다. `docker
# images` 의 사람이 읽는 SIZE 열은 이 로컬 환경에서 attestation 매니페스트를 합산해
# 실제 런타임 크기보다 크게 보고한다(2026-09-16 실측 — 같은 이미지에 476MB vs 113.7MB) —
# 지표로 쓰지 않는다. 비교는 `_check_at_most`(독립 `if`, elif 아님)로 한다.
IMAGE_SIZE_BYTES="$(docker image inspect "$IMAGE_REF" --format '{{.Size}}')"
_check_at_most "이미지 크기" "$IMAGE_SIZE_BYTES" "$SIZE_CAP_BYTES"

echo "-- 실측 요약 --"
echo "Config.User=${CONFIG_USER} 실프로세스-uid=${RUNTIME_UID} base-label(보조)=${BASE_LABEL}"
echo "base-layer-접두-일치=${base_layer_prefix_ok}"
echo "size_bytes=${IMAGE_SIZE_BYTES} cap_bytes=${SIZE_CAP_BYTES}"

if [ "$failures" -gt 0 ]; then
  echo "== 위생 게이트 실패: ${failures}건 ==" >&2
  exit 1
fi

echo "== 위생 게이트 통과 =="
