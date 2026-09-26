#!/usr/bin/env bash
# M6/6C — 이미지 위생 게이트(scope.md ⑥, 설계 검토 (1), D-6C-9·D-6C-10). 술어는 Dockerfile
# 텍스트나 손으로 적는 라벨이 아니라 **만든 이미지의 실행·빌드 산출물**에 건다(하네스
# 「게이트 술어는 구조로」, verifier r1 F-1·F-2·r2 R2-3·R2-4): 능력이 차단된 채 실 ENTRYPOINT
# 로 띄운 컨테이너의 모든 프로세스 사용자·정책 다이제스트에서 파생한 이미지의 실제 layer
# 체인·이미지 자신의 태그·크기 상한, 그리고 kind 별 「금지」 판정(ml-serving: 금지 패키지 각각
# 실제 import / 앱: 컴파일 도구 각각 실제 실행 + 풀린 의존 layer 의 test 전용 좌표).
#
# **M6/6A-2a D-6A2a-7 — 정책 파일을 인자로 받는다.** 앱 이미지가 생기면서 「금지」 판정이 하나가
# 아니게 됐다(ml-serving 은 Python `import`, 앱은 JVM 구조). 기본값을 두지 않는다 — 어느 정책으로
# 판정했는지가 명령에 보여야 한다(기본값은 "어느 정책이 돌았는가"를 감춘다).
#
# 사용법: tools/image-hygiene-check.sh <image-ref> <policy-file>
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "사용법: $0 <image-ref> <policy-file>" >&2
  exit 2
fi

IMAGE_REF="$1"
POLICY_FILE="$2"

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
#
# code-review r1 LOW — 키 대조를 **리터럴**로 한다. `grep -c "^${key}="` 는 키를 BRE 로 읽어
# `size.cap.bytes` 가 `sizeXcapYbytes=` 에도 맞았다(현 정책 파일에서 오답은 안 났지만, 이
# 함수가 존재하는 이유인 **중복 키 탐지**가 느슨해진다). `awk` 의 `index($0, k) == 1` 은 정규식이
# 아니라 문자열 접두 비교다 — 이스케이프 목록을 손으로 관리하지 않는다.
_policy_value() {
  local key="$1"
  local kind="${2:-text}" # text | numeric | list
  local matches
  matches="$(awk -v k="${key}=" 'index($0, k) == 1 { n++ } END { print n+0 }' "$POLICY_FILE")"
  if [ "$matches" -eq 0 ]; then
    echo "정책 키 ${key} 를 ${POLICY_FILE} 에서 읽지 못했다" >&2
    exit 2
  fi
  if [ "$matches" -gt 1 ]; then
    echo "정책 키 ${key} 가 ${POLICY_FILE} 에 ${matches}번 선언됐다(정책 오류 — 중복 키는 앞 값을 조용히 무력화한다)" >&2
    exit 2
  fi
  local raw
  raw="$(awk -v k="${key}=" 'index($0, k) == 1 { print substr($0, length(k) + 1) }' "$POLICY_FILE" | tr -d '\r')"
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
      # **R5-1(code-review r2 LOW-3, 같은 계열의 다섯 번째 판) — 공백 「종류」 열거를 멈추고
      # 허용 문자 집합으로 뒤집는다.** 이 축이 막는 결함은 늘 같다: 원소에 뭔가 섞이면 두 판정
      # 축이 모두 「부재」로 읽어(`--entrypoint $' jshell'` 은 컨테이너 생성 실패로 `if` 가
      # 거짓이 되고 `command -v` 도 없다고 답한다) **첫 원소만 판정하고 나머지를 조용히 끈다.**
      # 앞선 네 판은 지우거나 거부할 문자를 열거했고(R3-1 공백·탭 → R4-1 앞뒤 공백), 그 목록은
      # `tr -d '[:space:]'` 가 모르는 유니코드 공백(NBSP U+00A0 등)에 열려 있었다. 이제는
      # **무엇이 허용인지**만 적는다 — 열거가 아니라 구성이고, 새 문자가 생겨도 닫혀 있다.
      #
      # 허용 집합의 근거: 현 정책 값 전부(`javac`·`jshell`·`curl`·`opentest4j`·`byte-buddy`·
      # `sqlalchemy` …)가 영숫자와 `. _ + -` 뿐이다. 이 집합으로 좁히면 공백·유니코드 공백·
      # 따옴표·와일드카드·쉼표가 한 술어로 함께 닫힌다. 값이 이 집합을 벗어나야 할 날에는
      # 정책이 아니라 **이 줄**을 고친다(그 편집이 diff 에 보인다).
      local item
      for item in "${items[@]}"; do
        case "$item" in
          '' | *[!A-Za-z0-9._+-]*)
            echo "정책 키 ${key} 의 원소가 허용 문자([A-Za-z0-9._+-])만으로 돼 있지 않다 — 그 원소의 판정이 조용히 꺼진다: '${raw}'" >&2
            exit 2
            ;;
        esac
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

SIZE_CAP_BYTES="$(_policy_value size.cap.bytes numeric)"
NONROOT_UID_MIN="$(_policy_value nonroot.uid.min numeric)"
BASE_IMAGE_REPO="$(_policy_value base.image.repo text)"
BASE_IMAGE_DIGEST="$(_policy_value base.image.digest text)"
RUNTIME_KIND="$(_policy_value runtime.kind text)"

# kind 별 값은 그 kind 에서만 읽는다 — 다른 kind 의 정책 파일에 없는 키를 요구하면
# `_policy_value` 가 정책 오류로 끊는다(분기 소진: 모르는 kind 는 아래 `*)` 가 잡는다).
FORBIDDEN_PACKAGES=""
FORBIDDEN_EXECUTABLES=""
REQUIRED_EXECUTABLES=""
FORBIDDEN_DEPENDENCY_COORDINATES=""
DEPENDENCY_LAYER_PATH=""
DEPENDENCY_LAYER_MIN_ENTRIES=""
PROBE_ENV=()
case "$RUNTIME_KIND" in
  python-serving)
    FORBIDDEN_PACKAGES="$(_policy_value forbidden.packages list)"
    # D-6C-7 — 서버는 환경 7개가 전부 있어야 뜬다(기본값 없음). 이미지에 이미 구운 정책
    # 파일 경로를 그대로 준다 — 위생 게이트 전용 부팅값이지 업무 정책 값이 아니다.
    PROBE_ENV=(
      -e ML_ENGINE_BIND=0.0.0.0:50051
      -e ML_ENGINE_INFERENCE_POLICY=/app/policy/inference-v1.yaml
      -e ML_ENGINE_TRAINING_POLICY=/app/policy/training-v1.yaml
      -e ML_ENGINE_EVALUATION_POLICY=/app/policy/evaluation-v1.yaml
      -e ML_ENGINE_SERVING_POLICY=/app/policy/serving-v1.yaml
      -e ML_ENGINE_ARTIFACT_OUT_DIR=/app/artifacts
      -e ML_ENGINE_CODE_VERSION=hygiene-check
    )
    ;;
  jvm-app)
    FORBIDDEN_EXECUTABLES="$(_policy_value forbidden.executables list)"
    # code-review r1 Open Question 2 — compose healthcheck 이 이미지 안 `curl` 에 기댄다. 그
    # 전제는 주석과 실측 기록에만 있었고, 베이스 다이제스트를 올렸을 때 사라지면 증상은
    # 「app 이 healthy 로 수렴하지 않음」(타임아웃)이라 원인이 보이지 않는다. **이름 있는 축**으로
    # 만든다 — 부재면 게이트가 그 자리에서, 그 이름으로 실패한다.
    REQUIRED_EXECUTABLES="$(_policy_value required.executables list)"
    FORBIDDEN_DEPENDENCY_COORDINATES="$(_policy_value forbidden.dependency.coordinates list)"
    DEPENDENCY_LAYER_PATH="$(_policy_value dependency.layer.path text)"
    DEPENDENCY_LAYER_MIN_ENTRIES="$(_policy_value dependency.layer.min-entries numeric)"
    # 앱도 설정이 전부 있어야 뜬다(기본값 없음 — 관리 포트 하나만 조립 근이 기본값을 갖는다).
    # 위생 게이트 전용 부팅값이지 업무 정책 값이 아니다(위 ml-serving 과 같은 근거).
    #
    # JDBC 주소는 **RFC 5737 TEST-NET-1**(192.0.2.0/24, 문서·예시 전용으로 예약돼 어디로도
    # 라우팅되지 않는다)이다. 실 DB 를 켜지 않으면서 프로세스를 살려 둬야 하기 때문이다 —
    # 이름 해석 실패나 연결 거부는 즉시 기동 실패로 끝나 **(1) 의 프로세스 표집 창이 사라진다.**
    # 여기로 보낸 SYN 은 응답이 없어 드라이버의 연결 타임아웃(기본 10초)까지 막히고, 그
    # 사이 pid 1 은 살아 있다(2026-09-26 실측: t=10s 에도 `State.Running=true`, uid 10001).
    PROBE_ENV=(
      -e SERVER_PORT=8080
      -e MANAGEMENT_SERVER_PORT=8081
      -e BIDVECTOR_PERSISTENCE_JDBCURL=jdbc:postgresql://192.0.2.1:5432/hygiene-check
      -e BIDVECTOR_PERSISTENCE_USERNAME=hygiene-check
      -e BIDVECTOR_PERSISTENCE_CREDENTIAL=hygiene-check
      -e OPERATOR_CREDENTIAL_VALUE=hygiene-check-placeholder-not-an-operator-value
      -e BIDVECTOR_EVALUATION_CANDIDATECAP=1
    )
    ;;
  *)
    echo "알 수 없는 runtime.kind '${RUNTIME_KIND}'(정책 오류 — 스크립트의 갈래와 정책이 어긋났다)" >&2
    exit 2
    ;;
esac

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

# 부팅값은 위 `case` 가 kind 별로 채운 `PROBE_ENV` 다(정책 파일이 아니라 게이트 내부 값 —
# 업무 정책 값이 아니다). 능력 차단(`no-new-privileges`)은 kind 와 무관하게 늘 건다.
docker run -d --name "$HYGIENE_CONTAINER" \
  --security-opt no-new-privileges \
  "${PROBE_ENV[@]}" \
  "$IMAGE_REF" >/dev/null

sleep 1

# R3-6(verifier r3 LOW) — 요약 줄이 검사 범위를 그대로 드러내도록 **관측한 uid 전부**를
# 모은다(이전 판은 첫 행만 담아, 판정은 전 행을 보면서도 요약은 그것을 못 보여줬다).
RUNTIME_UIDS_SUMMARY=""
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
      RUNTIME_UIDS_SUMMARY="${RUNTIME_UIDS_SUMMARY:+${RUNTIME_UIDS_SUMMARY},}${row_uid}"
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

    # R3-3(verifier r3 LOW) — 파생이 linux/amd64 로 고정돼 있다(OPEN-6C-MULTIARCH, 6E 전까지
    # 대상 밖). 아래 실패 사유는 그래서 두 가지를 함께 언급한다 — FROM 이 실제로 다른
    # 이미지를 가리키거나, **이 이미지가 linux/amd64 가 아닌 플랫폼으로 빌드됐다.** 둘을
    # 구분하는 추가 판정은 넣지 않는다(하드코딩된 축 자체가 OPEN 이다) — 문면만 정확히 한다.
    expected_count=${#expected_base_layers[@]}
    if [ "${#actual_layers[@]}" -lt "$expected_count" ]; then
      fail "이미지 layer 수(${#actual_layers[@]})가 파생한 베이스 layer 수(${expected_count})보다 적다 — 베이스가 정책과 다르거나 이 이미지가 linux/amd64 가 아닐 수 있다(OPEN-6C-MULTIARCH)"
    else
      base_mismatch=0
      for i in "${!expected_base_layers[@]}"; do
        if [ "${actual_layers[$i]:-}" != "${expected_base_layers[$i]}" ]; then
          base_mismatch=1
          break
        fi
      done
      if [ "$base_mismatch" -ne 0 ]; then
        fail "이미지의 앞 ${expected_count} layer 가 정책이 파생한 베이스 layer 체인과 다르다 — FROM 이 다른 이미지를 가리키거나 이 이미지가 linux/amd64 가 아닐 수 있다(라벨과 무관하게 위반, OPEN-6C-MULTIARCH)"
      else
        base_layer_prefix_ok=true
      fi
    fi
  fi
fi

# (3) 「금지」 판정 — 이름 대조가 아니라 **실행·산출물**에 건다. kind 로 갈라지는 유일한 절이다.
# 요약 줄에 실을 한 줄은 `FORBIDDEN_SUMMARY` 가 든다(검사 범위가 요약에 그대로 드러나게 한다).
FORBIDDEN_SUMMARY=""
case "$RUNTIME_KIND" in
  python-serving)
    # 금지 패키지 다섯 — **실제 import 시도**(우회 (2), 5A S-1b 형태).
    IFS=',' read -r -a forbidden_array <<< "$FORBIDDEN_PACKAGES"
    for pkg in "${forbidden_array[@]}"; do
      if docker run --rm --security-opt no-new-privileges --entrypoint python "$IMAGE_REF" -c "import ${pkg}" >/dev/null 2>&1; then
        fail "금지 패키지 '${pkg}' 가 이 이미지에서 import 된다"
      fi
    done
    FORBIDDEN_SUMMARY="금지-import-검사=${#forbidden_array[@]}건"
    ;;
  jvm-app)
    # ① 컴파일·개발 도구 부재(D-6A2a-7 ①). `--entrypoint <tool>` 로 덮어쓰는 것은 여기서
    # 정당하다: 재는 대상이 "그 도구가 이 이미지에 있는가"이고 프로세스 사용자가 아니다
    # (사용자 축은 위 (1) 이 실 ENTRYPOINT 로 이미 쟀다).
    #
    # **축이 둘이다.** 2026-09-26 변이 ⑦ 실측: `--version` 실행 시도 하나로는 **`serialver`
    # 를 놓쳤다** — JDK 베이스에 그 파일이 있는데도 `--version` 을 거부해(exit 1) 게이트가
    # "부재"로 읽었다. 그래서 PATH 조회 축을 더한다. 그 축은 셸이 필요하므로 **셸 존재를 먼저
    # 확인하고, 없으면 판정 불가로 실패**한다(조용한 통과를 만들지 않는다 — 6C 의 교훈).
    IFS=',' read -r -a executable_array <<< "$FORBIDDEN_EXECUTABLES"
    shell_probe_available=true
    if ! docker run --rm --security-opt no-new-privileges --entrypoint sh "$IMAGE_REF" -c 'exit 0' >/dev/null 2>&1; then
      shell_probe_available=false
      fail "이미지에 셸이 없어 금지 실행 파일의 PATH 조회 축을 판정할 수 없다 — 실행 시도 축만으로는 --version 을 거부하는 도구를 놓친다(변이 ⑦ 실측)"
    fi
    for tool in "${executable_array[@]}"; do
      if docker run --rm --security-opt no-new-privileges --entrypoint "$tool" "$IMAGE_REF" --version >/dev/null 2>&1; then
        fail "금지 실행 파일 '${tool}' 이 이 이미지에서 실행된다 — JRE 가 아니라 JDK 베이스일 수 있다"
      elif [ "$shell_probe_available" = true ] \
        && docker run --rm --security-opt no-new-privileges --entrypoint sh "$IMAGE_REF" -c 'command -v "$1"' sh "$tool" >/dev/null 2>&1; then
        fail "금지 실행 파일 '${tool}' 이 이 이미지의 PATH 에 있다(실행은 --version 을 거부했다) — JRE 가 아니라 JDK 베이스일 수 있다"
      fi
    done

    # ①' **필수** 실행 파일 존재(code-review r1 Open Question 2). 금지 축과 같은 PATH 조회를
    # 쓰므로 셸이 없으면 판정 불가다 — 위에서 이미 `fail` 로 끊었고, 여기서는 그 경우 축을
    # 건너뛰지 않고 「판정 불가」 사유를 한 번 더 남긴다(조용한 통과를 만들지 않는다).
    IFS=',' read -r -a required_array <<< "$REQUIRED_EXECUTABLES"
    for tool in "${required_array[@]}"; do
      if [ "$shell_probe_available" != true ]; then
        fail "필수 실행 파일 '${tool}' 의 존재를 판정할 수 없다(이미지에 셸이 없다)"
      elif ! docker run --rm --security-opt no-new-privileges --entrypoint sh "$IMAGE_REF" -c 'command -v "$1"' sh "$tool" >/dev/null 2>&1; then
        fail "필수 실행 파일 '${tool}' 이 이 이미지의 PATH 에 없다 — compose healthcheck 가 이것으로 준비 상태를 묻는다"
      fi
    done

    # ② 풀린 의존 layer 에 test 전용 좌표 0(D-6A2a-7 ②). **주 잠금은 Gradle 구조**(`bootJar`
    # 는 `runtimeClasspath` 만 담는다)이고 이 검사는 그것이 이미지까지 이어졌는지 재는 보조다.
    DEP_LAYER_LISTING="$(docker run --rm --security-opt no-new-privileges --entrypoint ls "$IMAGE_REF" -1 "$DEPENDENCY_LAYER_PATH" 2>/dev/null || true)"
    DEP_LAYER_ENTRIES="$(printf '%s\n' "$DEP_LAYER_LISTING" | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]')"
    IFS=',' read -r -a coordinate_array <<< "$FORBIDDEN_DEPENDENCY_COORDINATES"
    # **양성 대조가 먼저다** — 목록이 비거나 경로가 틀리면 아래 루프의 "위반 0" 은 공허하게
    # 참이다(6C 의 세 라운드가 같은 계열을 세 번 냈다: 게이트가 틀린 답을 내는 게 아니라
    # **아무 일도 하지 않게** 됐다). 그래서 목록이 실재함을 수치로 먼저 요구한다.
    if ! [[ "$DEP_LAYER_ENTRIES" =~ ^[0-9]+$ ]] || [ "$DEP_LAYER_ENTRIES" -lt "$DEPENDENCY_LAYER_MIN_ENTRIES" ]; then
      fail "의존 layer(${DEPENDENCY_LAYER_PATH})의 항목 수(${DEP_LAYER_ENTRIES})가 정책 하한(${DEPENDENCY_LAYER_MIN_ENTRIES}) 미만이다 — 경로가 틀렸거나 layer 가 풀리지 않았다(판정 불가)"
    else
      for coord in "${coordinate_array[@]}"; do
        if printf '%s\n' "$DEP_LAYER_LISTING" | grep -qiF -- "$coord"; then
          fail "test 전용 좌표 '${coord}' 가 의존 layer(${DEPENDENCY_LAYER_PATH})에 있다"
        fi
      done
    fi
    FORBIDDEN_SUMMARY="금지-실행파일-검사=${#executable_array[@]}건 필수-실행파일-검사=${#required_array[@]}건 의존layer=${DEPENDENCY_LAYER_PATH} 항목수=${DEP_LAYER_ENTRIES}(하한 ${DEPENDENCY_LAYER_MIN_ENTRIES}) 금지좌표-검사=${#coordinate_array[@]}건"
    ;;
  *)
    echo "알 수 없는 runtime.kind '${RUNTIME_KIND}'(스크립트 결함 — 위 분기와 여기가 어긋났다)" >&2
    exit 2
    ;;
esac

# (4) 크기 상한 — `docker image inspect .Size`(단일 플랫폼 이미지 실 크기, `docker save`
# 바이트 수와 일치함을 실측 확인함, commands.md 「크기 지표 선택 근거」)를 잰다. `docker
# images` 의 사람이 읽는 SIZE 열은 이 로컬 환경에서 attestation 매니페스트를 합산해
# 실제 런타임 크기보다 크게 보고한다(2026-09-16 실측 — 같은 이미지에 476MB vs 113.7MB) —
# 지표로 쓰지 않는다. 비교는 `_check_at_most`(독립 `if`, elif 아님)로 한다.
IMAGE_SIZE_BYTES="$(docker image inspect "$IMAGE_REF" --format '{{.Size}}')"
_check_at_most "이미지 크기" "$IMAGE_SIZE_BYTES" "$SIZE_CAP_BYTES"

echo "-- 실측 요약 --"
echo "정책=${POLICY_FILE} runtime.kind=${RUNTIME_KIND}"
echo "${FORBIDDEN_SUMMARY}"
echo "Config.User=${CONFIG_USER} 실프로세스-uid전체=[${RUNTIME_UIDS_SUMMARY}] base-label(보조)=${BASE_LABEL}"
echo "base-layer-접두-일치=${base_layer_prefix_ok}"
echo "size_bytes=${IMAGE_SIZE_BYTES} cap_bytes=${SIZE_CAP_BYTES}"

if [ "$failures" -gt 0 ]; then
  echo "== 위생 게이트 실패: ${failures}건 ==" >&2
  exit 1
fi

echo "== 위생 게이트 통과 =="
