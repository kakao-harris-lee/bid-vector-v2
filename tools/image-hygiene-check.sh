#!/usr/bin/env bash
# 이미지 위생 게이트(scope.md ⑥, 설계 검토 (1), D-6C-9·D-6C-10). 술어는 Dockerfile
# 텍스트나 손으로 적는 라벨이 아니라 **만든 이미지의 실행·빌드 산출물**에 건다(하네스
# 「게이트 술어는 구조로」, F-1·F-2·R2-3·R2-4): 능력이 차단된 채 실 ENTRYPOINT
# 로 띄운 컨테이너의 모든 프로세스 사용자·정책 다이제스트에서 파생한 이미지의 실제 layer
# 체인·이미지 자신의 태그·크기 상한, 그리고 kind 별 「금지」 판정(ml-serving: 금지 패키지 각각
# 실제 import / 앱: 컴파일 도구 각각 실제 실행 + 풀린 의존 layer 의 test 전용 좌표).
#
# **D-6A2a-7 — 정책 파일을 인자로 받는다.** 앱 이미지가 생기면서 「금지」 판정이 하나가
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

# R2-8(LOW) — 이 게이트는 `jq`로 `RootFS.Layers` JSON 을 판독한다. `ubuntu-latest`
# 에 선탑재라 지금은 서지만 워크플로가 설치를 선언하지 않는다 — 없으면 (2) 절이 값 획득
# 실패를 "베이스가 정책과 다르다"는 엉뚱한 사유로 보고할 뻔했다. 여기서 먼저, 분명하게 끊는다.
if ! command -v jq >/dev/null 2>&1; then
  echo "jq 가 필요하다 — 이 게이트가 이미지의 RootFS.Layers 를 판독하는 데 쓴다" >&2
  exit 2
fi

# R2-1(D-6C-10 ①, HIGH, 표적 재검증) — 값이 비어 있거나 CRLF 가 섞인 경우도 통과시켜서는
# 안 된다(수치 비교가 `elif` 조건 안에 있어
# `[ 가 "integer expression expected"로 비-0 을 내면 조건이 거짓으로 읽혀 위반이 조용히
# 삼켜진다 — `base.image.layers=`를 비우면 요약에 `false`를 찍으면서도 exit 0 이 될 수 있다).
# 값 모양도 검증한다: CRLF 절삭 → 빈 값 거부 → kind 별 모양(수치/목록/텍스트).
#
# LOW — 키 대조를 **리터럴**로 한다. `grep -c "^${key}="` 는 키를 BRE 로 읽어
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
      # **R5-1(LOW-3) — 공백 「종류」 열거를 멈추고
      # 허용 문자 집합으로 뒤집는다.** 이 축이 막는 결함은 늘 같다: 원소에 뭔가 섞이면 두 판정
      # 축이 모두 「부재」로 읽어(`--entrypoint $' jshell'` 은 컨테이너 생성 실패로 `if` 가
      # 거짓이 되고 `command -v` 도 없다고 답한다) **첫 원소만 판정하고 나머지를 조용히 끈다.**
      # 거부할 문자를 낱낱이 열거하는 접근(R3-1 공백·탭 → R4-1 앞뒤 공백)은
      # `tr -d '[:space:]'` 가 모르는 유니코드 공백(NBSP U+00A0 등)에 열려 있다. 대신
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
    pathlist)
      # `list` 와 같은 축이되 **경로를 담는 키 전용**으로 `/` 하나만 더 받는다(M7/7F-local F-2 의
      # 탐색 범위·제외 접두). 전역 집합을 넓히지 않는 이유는 위 문단 그대로다 — `/` 를 모든 키에
      # 허용하면 `forbidden.executables` 같은 이름 키가 경로를 받아도 조용히 통과한다. 종류를
      # 나누면 **어느 키가 경로인지**가 호출부에 보이고, 집합을 넓히는 편집은 이 줄에 남는다.
      local IFS=','
      local -a path_items
      read -r -a path_items <<< "$raw"
      if [ "${#path_items[@]}" -lt 1 ]; then
        echo "정책 키 ${key} 는 최소 1개 원소가 있어야 한다: '${raw}'" >&2
        exit 2
      fi
      local path_item
      for path_item in "${path_items[@]}"; do
        case "$path_item" in
          '' | *[!A-Za-z0-9._+/-]*)
            echo "정책 키 ${key} 의 원소가 허용 문자([A-Za-z0-9._+/-])만으로 돼 있지 않다 — 그 원소의 판정이 조용히 꺼진다: '${raw}'" >&2
            exit 2
            ;;
        esac
        case "$path_item" in
          /*) ;;
          *)
            echo "정책 키 ${key} 의 원소가 절대 경로가 아니다 — 상대 경로는 컨테이너 작업 디렉터리에 따라 다른 곳을 가리킨다: '${path_item}'" >&2
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
CONTENT_PATH=""
CONTENT_MIN_ENTRIES=""
REQUIRED_CONTENT_PATTERNS=""
FORBIDDEN_CONTENT_PATTERNS=""
KEYMAT_SCAN_PATHS=""
KEYMAT_EXCLUDE_PREFIXES=""
KEYMAT_PATTERNS=""
KEYMAT_MIN_ENTRIES=""
KEYMAT_CONTENT_MAX_BYTES=""
KEYMAT_CONTENT_MIN_SCANNED=""
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
    # Open Question 2 — compose healthcheck 이 이미지 안 `curl` 에 기댄다. 그
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
  caddy-site)
    # M7/7F-local — web 정적 이미지와 리버스 프록시 이미지가 **같은 갈래**를 쓴다. 판정 형태가
    # 같고 값만 다르기 때문이다(정책 파일이 둘: `-web`·`-proxy`). 갈래를 둘로 쪼개면 같은 코드가
    # 두 벌이 되고, 한 자리의 교정이 다른 자리를 놓친다.
    FORBIDDEN_EXECUTABLES="$(_policy_value forbidden.executables list)"
    REQUIRED_EXECUTABLES="$(_policy_value required.executables list)"
    CONTENT_PATH="$(_policy_value content.path text)"
    CONTENT_MIN_ENTRIES="$(_policy_value content.min-entries numeric)"
    REQUIRED_CONTENT_PATTERNS="$(_policy_value required.content.patterns list)"
    FORBIDDEN_CONTENT_PATTERNS="$(_policy_value forbidden.content.patterns list)"
    # 검토 r1 F-2 — 키·인증서 재료는 **싣고 있는 내용 축과 다른 범위**를 갖는다. 그 축은
    # 「서브되는 트리에 무엇이 있나」를 묻느라 좁고, 키는 그 트리 밖(`/data`·`/config`)에 구워도
    # 똑같이 사고다. 그래서 범위·제외·패턴·하한을 **따로 선언**한다.
    KEYMAT_SCAN_PATHS="$(_policy_value keymaterial.scan-paths pathlist)"
    KEYMAT_EXCLUDE_PREFIXES="$(_policy_value keymaterial.exclude-prefixes pathlist)"
    KEYMAT_PATTERNS="$(_policy_value keymaterial.patterns list)"
    KEYMAT_MIN_ENTRIES="$(_policy_value keymaterial.min-entries numeric)"
    KEYMAT_CONTENT_MAX_BYTES="$(_policy_value keymaterial.content.max-bytes numeric)"
    KEYMAT_CONTENT_MIN_SCANNED="$(_policy_value keymaterial.content.min-scanned numeric)"
    # 두 이미지가 요구하는 환경을 **합집합**으로 준다 — 각 이미지는 자기 것만 읽고 나머지는
    # 무시한다(쓰이지 않는 env 를 Caddy 가 문제 삼지 않는다). 포트는 위생 게이트 전용 부팅값이고
    # 업무 정책 값이 아니다(위 두 kind 와 같은 근거).
    #
    # **상류 주소는 RFC 5737 TEST-NET-1**(192.0.2.0/24 — 문서·예시 전용으로 예약돼 어디로도
    # 라우팅되지 않는다). 프록시 설정은 상류 값이 **비면 적응 오류로 즉사**해 (1) 의 프로세스
    # 표집 창이 사라지므로 값이 있어야 하고, 그 값이 실재하는 주소면 게이트가 남의 포트를
    # 두드리게 된다. 라우팅되지 않는 주소는 둘 다 피한다 — 어느 상류로도 요청하지 않는다.
    PROBE_ENV=(
      -e BIDVECTOR_WEB_PORT=18090
      -e BIDVECTOR_PROXY_HTTPS_PORT=18443
      -e BIDVECTOR_PROXY_DOMAIN=localhost
      -e BIDVECTOR_PROXY_APP_UPSTREAM=192.0.2.1:18080
      -e BIDVECTOR_PROXY_WEB_UPSTREAM=192.0.2.1:18090
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

# 「금지 실행 파일」·「필수 실행 파일」 두 축은 **kind 둘이 공유**한다(jvm-app · caddy-site).
# 판정 형태를 복사하지 않는다 — 복사본은 한 자리의 교정을 다른 자리에서 놓친다(6C 가 같은
# 계열의 결함을 세 라운드에 걸쳐 낸 자리다). 갈라지는 것은 **사유 문면**뿐이라 인자로 받는다.
#
# 셸 조회 축은 한 번만 재고 결과를 전역에 둔다(같은 이미지에 대해 두 번 물을 이유가 없다).
SHELL_PROBE_AVAILABLE=""
FORBIDDEN_EXECUTABLE_COUNT=0
REQUIRED_EXECUTABLE_COUNT=0
_ensure_shell_probe() {
  # `[ … ] && return 0` 형태를 쓰지 않는다 — AND-OR 목록의 비-0 과 `set -e` 의 상호작용을
  # 독자가 따져야 하는 자리를 만들지 않는다(같은 사유로 이 저장소의 수치 비교도 독립 `if` 다).
  if [ -n "$SHELL_PROBE_AVAILABLE" ]; then
    return 0
  fi
  if docker run --rm --security-opt no-new-privileges --entrypoint sh "$IMAGE_REF" -c 'exit 0' >/dev/null 2>&1; then
    SHELL_PROBE_AVAILABLE=true
  else
    SHELL_PROBE_AVAILABLE=false
    fail "이미지에 셸이 없어 금지 실행 파일의 PATH 조회 축을 판정할 수 없다 — 실행 시도 축만으로는 --version 을 거부하는 도구를 놓친다(변이 ⑦ 실측)"
  fi
}

# **축이 둘이다**(2026-09-26 변이 ⑦ 실측): `--version` 실행 시도 하나로는 `serialver` 를 놓쳤다 —
# JDK 베이스에 파일이 있는데도 `--version` 을 거부해(exit 1) 게이트가 "부재"로 읽었다. 그래서
# PATH 조회 축을 더하고, 그 축은 셸이 필요하므로 셸 부재는 **판정 불가로 실패**한다.
_check_forbidden_executables() { # <쉼표 목록> <사유 꼬리말>
  local list="$1" hint="$2" tool
  local -a arr
  IFS=',' read -r -a arr <<< "$list"
  _ensure_shell_probe
  for tool in "${arr[@]}"; do
    if docker run --rm --security-opt no-new-privileges --entrypoint "$tool" "$IMAGE_REF" --version >/dev/null 2>&1; then
      fail "금지 실행 파일 '${tool}' 이 이 이미지에서 실행된다 — ${hint}"
    elif [ "$SHELL_PROBE_AVAILABLE" = true ] \
      && docker run --rm --security-opt no-new-privileges --entrypoint sh "$IMAGE_REF" -c 'command -v "$1"' sh "$tool" >/dev/null 2>&1; then
      fail "금지 실행 파일 '${tool}' 이 이 이미지의 PATH 에 있다(실행은 --version 을 거부했다) — ${hint}"
    fi
  done
  FORBIDDEN_EXECUTABLE_COUNT="${#arr[@]}"
}

# 금지 축과 같은 PATH 조회를 쓰므로 셸이 없으면 판정 불가다 — 그 경우 축을 건너뛰지 않고
# 「판정 불가」 사유를 남긴다(조용한 통과를 만들지 않는다).
_check_required_executables() { # <쉼표 목록> <사유 꼬리말>
  local list="$1" hint="$2" tool
  local -a arr
  IFS=',' read -r -a arr <<< "$list"
  _ensure_shell_probe
  for tool in "${arr[@]}"; do
    if [ "$SHELL_PROBE_AVAILABLE" != true ]; then
      fail "필수 실행 파일 '${tool}' 의 존재를 판정할 수 없다(이미지에 셸이 없다)"
    elif ! docker run --rm --security-opt no-new-privileges --entrypoint sh "$IMAGE_REF" -c 'command -v "$1"' sh "$tool" >/dev/null 2>&1; then
      fail "필수 실행 파일 '${tool}' 이 이 이미지의 PATH 에 없다 — ${hint}"
    fi
  done
  REQUIRED_EXECUTABLE_COUNT="${#arr[@]}"
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

# R3-6(LOW) — 요약 줄이 검사 범위를 그대로 드러내도록 **관측한 uid 전부**를
# 모은다(요약이 첫 행만 담으면 판정은 전 행을 보면서도 그것을 보여주지 못한다).
RUNTIME_UIDS_SUMMARY=""
CONTAINER_RUNNING="$(docker inspect "$HYGIENE_CONTAINER" --format '{{.State.Running}}' 2>/dev/null || echo false)"
if [ "$CONTAINER_RUNNING" != "true" ]; then
  fail "실 ENTRYPOINT 컨테이너가 뜨지 않았다(State.Running=${CONTAINER_RUNNING}) — 프로세스 사용자를 잴 수 없다"
else
  TOP_DATA_ROWS="$(docker top "$HYGIENE_CONTAINER" -eo pid,uid,comm 2>/dev/null | tail -n +2 | sed '/^[[:space:]]*$/d' || true)"
  if [ -z "$TOP_DATA_ROWS" ]; then
    fail "컨테이너 안에서 도는 프로세스를 하나도 찾지 못했다(그 사이 종료됐을 수 있다)"
  else
    # R2-5(LOW) — 「행 수 ≠ 1」이 아니라 **행마다 uid** 를 본다. 정상 이미지가
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

    # R3-3(LOW) — 파생이 linux/amd64 로 고정돼 있다(OPEN-6C-MULTIARCH, 6E 전까지
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
    # 두 축의 판정은 **공유 helper** 가 든다(M7/7F-local 의 caddy-site 와 같은 코드) — 문면의
    # 사유만 이 kind 의 것이다. 축이 둘인 이유(변이 ⑦)는 helper 머리 주석에 있다.
    _check_forbidden_executables "$FORBIDDEN_EXECUTABLES" "JRE 가 아니라 JDK 베이스일 수 있다"

    # ①' **필수** 실행 파일 존재(Open Question 2).
    _check_required_executables "$REQUIRED_EXECUTABLES" "compose healthcheck 가 이것으로 준비 상태를 묻는다"

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
    FORBIDDEN_SUMMARY="금지-실행파일-검사=${FORBIDDEN_EXECUTABLE_COUNT}건 필수-실행파일-검사=${REQUIRED_EXECUTABLE_COUNT}건 의존layer=${DEPENDENCY_LAYER_PATH} 항목수=${DEP_LAYER_ENTRIES}(하한 ${DEPENDENCY_LAYER_MIN_ENTRIES}) 금지좌표-검사=${#coordinate_array[@]}건"
    ;;
  caddy-site)
    # ① 금지 실행 파일 — 이 kind 의 「금지」는 **빌더 누출과 베이스 교체**를 잡는 축이다. web
    # 이미지의 런타임 stage 에 node·npm 이 보이면 multi-stage 경계가 깨졌거나 베이스가 빌더
    # 이미지로 바뀐 것이고, 컴파일러가 보이면 런타임 이미지가 빌드 이미지가 된 것이다. 판정은
    # 이름 대조가 아니라 **실행·PATH 조회**다(jvm-app 과 같은 helper).
    _check_forbidden_executables "$FORBIDDEN_EXECUTABLES" "빌더 단계가 런타임으로 샜거나 베이스가 바뀌었을 수 있다"

    # ①' 필수 실행 파일 — compose 의 두 healthcheck 가 이미지 안 `curl` 로 묻는다(app 과 같은
    # 사유: 베이스 상향으로 사라지면 증상이 「healthy 로 수렴하지 않음」이라 원인이 보이지 않는다).
    _check_required_executables "$REQUIRED_EXECUTABLES" "compose healthcheck 가 이것으로 준비 상태를 묻는다"

    # ② 이미지가 **싣고 있는 내용**. jvm-app 의 의존 layer 축과 같은 형태다 — **양성 대조가
    # 먼저**다: 목록이 비거나 경로가 틀리면 아래 「위반 0」이 공허하게 참이 된다. 그래서 파일
    # 수 하한을 먼저 요구하고, 그 뒤에 필수·금지 패턴을 본다.
    CONTENT_LISTING="$(docker run --rm --security-opt no-new-privileges --entrypoint find "$IMAGE_REF" "$CONTENT_PATH" -type f 2>/dev/null || true)"
    CONTENT_ENTRIES="$(printf '%s\n' "$CONTENT_LISTING" | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]')"
    IFS=',' read -r -a required_content_array <<< "$REQUIRED_CONTENT_PATTERNS"
    IFS=',' read -r -a forbidden_content_array <<< "$FORBIDDEN_CONTENT_PATTERNS"
    if ! [[ "$CONTENT_ENTRIES" =~ ^[0-9]+$ ]] || [ "$CONTENT_ENTRIES" -lt "$CONTENT_MIN_ENTRIES" ]; then
      fail "내용 경로(${CONTENT_PATH})의 파일 수(${CONTENT_ENTRIES})가 정책 하한(${CONTENT_MIN_ENTRIES}) 미만이다 — 경로가 틀렸거나 COPY 가 비었거나 목록 도구가 없다(판정 불가)"
    else
      for pat in "${required_content_array[@]}"; do
        if ! printf '%s\n' "$CONTENT_LISTING" | grep -qF -- "$pat"; then
          fail "필수 내용 '${pat}' 이 ${CONTENT_PATH} 에 없다 — 이미지가 자기 역할의 입력을 싣지 않았다"
        fi
      done
      for pat in "${forbidden_content_array[@]}"; do
        if printf '%s\n' "$CONTENT_LISTING" | grep -qiF -- "$pat"; then
          fail "금지 내용 '${pat}' 이 ${CONTENT_PATH} 에 있다"
        fi
      done
    fi
    # ②' **키·인증서 재료는 넓게 찾는다**(검토 r1 F-2, r2 에서 네 군데 보강).
    # 앞 판은 위 `content.path` 한 자리만 봤고, `tls internal` 이 실제로 CA 를 두는 자리
    # (`/data/caddy/pki/...`)에 키를 구운 이미지가 초록이었다. 범위는 **이미지 파일시스템 전체**
    # 이고 `-xdev` 가 런타임 마운트(proc·sys·dev)를 밖에 둔다.
    #
    # r2 가 닫은 넷 — ⓐ 제외 접두 집합을 **아래 상수와의 등식**으로 잠근다(정책만 고쳐 표적
    # 제외를 끼워 넣지 못한다) ⓑ 접두 비교는 **리터럴**이다(정규식이면 `.`·`+` 가 조용히 넓힌다)
    # ⓒ 이름 일치는 **끝 고정**이다(부분 일치면 `xterm.keyboard` 류가 거짓 차단이 된다)
    # ⓓ 이름 열거 위에 **내용 한 겹**을 덮는다(확장자를 바꿔 숨기는 편집을 잡는다).
    IFS=',' read -r -a keymat_scan_paths <<< "$KEYMAT_SCAN_PATHS"
    IFS=',' read -r -a keymat_exclude_array <<< "$KEYMAT_EXCLUDE_PREFIXES"
    IFS=',' read -r -a keymat_pattern_array <<< "$KEYMAT_PATTERNS"

    # ⓐ 제외는 **닫힌 집합**이다. 정책이 선언한 값이 이 상수와 글자 그대로 같아야 한다 —
    # 제외를 넓히려면 정책이 아니라 **게이트 자신**을 고쳐야 하고 그 편집은 이 줄에 남는다.
    # 두 자리만 허용하는 사유: 베이스가 싣는 **공개** 신뢰 기점(공개 뿌리 인증서 모음)이고
    # 비밀값이 아니다. `/etc/ssl/` 통째가 아니라 `certs/` 로 좁힌 것은 그 형제인
    # `/etc/ssl/private/` 가 이 계열 배포판에서 **개인키의 표준 자리**이기 때문이다(r2 cr R2-1).
    keymat_scan_argv_pre="$(printf '%s ' "${keymat_scan_paths[@]}")"
    KEYMAT_ALLOWED_EXCLUDE_PREFIXES="/etc/ssl/certs/,/usr/share/ca-certificates/"
    # 종료 코드 분류(이 스크립트의 관례): **1 = 이미지가 기준을 어겼다**, **2 = 정책·환경이
    # 틀려 판정할 수 없다**. 제외 집합이 어긋난 것은 이미지의 잘못이 아니라 **정책의 잘못**이므로
    # 2 다(r3 cr R3-8). 문면에 **어느 정책 파일인가**를 넣는다 — 같은 값이 세 자리(게이트 상수 +
    # 정책 둘)에 살아서, 둘이 어긋나면 그 kind 가 돌 때까지 보이지 않는다(cr R3-9).
    if [ "$KEYMAT_EXCLUDE_PREFIXES" != "$KEYMAT_ALLOWED_EXCLUDE_PREFIXES" ]; then
      echo "키 재료 제외 접두가 게이트의 허용 집합과 다르다(판정 불가) — 정책 ${POLICY_FILE}[${KEYMAT_EXCLUDE_PREFIXES}] 허용[${KEYMAT_ALLOWED_EXCLUDE_PREFIXES}]: 제외를 넓히려면 정책이 아니라 게이트를 고친다" >&2
      exit 2
    fi

    # **이름 축 목록도 root 로 걷는다**(r4 vr R4-M1 = cr R4-1). r3 은 내용 축에만 `--user 0` 을
    # 달았고 이 목록은 이미지의 실행 사용자로 돌았다 — 그래서 `/root` 처럼 **0700 인 디렉터리
    # 안은 열거조차 되지 않았고**, 그 오류는 `2>/dev/null` 로 사라졌다. PEM 이면 내용 축이
    # 받지만 **PEM 이 아닌 키 컨테이너**(`.p12`·`.pfx`·`.jks`·`.der`)는 두 축을 다 빠져나갔다.
    # 두 축의 모집단을 같게 만든다.
    #
    # 권한 오류를 **삼키지 않는다** — `find` 의 stderr 를 따로 받아 한 줄이라도 있으면 판정
    # 불가다. root 로 도는 이상 오늘은 비지만, 비어 있음이 **측정된** 사실이어야 한다.
    # `--tmpfs /tmp` 를 붙이는 이유: stderr 를 받는 파일이 **스캔 대상 안에 있으면 find 가 그것까지
    # 센다**(실측 — 대상 수가 하나 늘었다. 계측기가 측정을 흔든 자리다). tmpfs 는 다른 마운트라
    # `-xdev` 가 알아서 건너뛴다.
    KEYMAT_LISTING_RAW="$(docker run --rm --user 0 --tmpfs /tmp --security-opt no-new-privileges --entrypoint sh "$IMAGE_REF" -c \
      "find ${keymat_scan_argv_pre} -xdev -type f 2>/tmp/_keymat_find_err | sed 's|^|F\t|'; [ -s /tmp/_keymat_find_err ] && sed 's|^|E\t|' /tmp/_keymat_find_err; exit 0" 2>&1 || true)"
    KEYMAT_LIST_ERRORS="$(printf '%s\n' "$KEYMAT_LISTING_RAW" | sed -n 's/^E\t//p')"
    if [ -n "$KEYMAT_LIST_ERRORS" ]; then
      echo "키 재료 이름 축의 목록에서 오류가 났다(판정 불가) — root 로 걷는데도 막혔다면 이 축의 「적중 0」은 깨끗하다는 뜻이 아니다: [$(printf '%s' "$KEYMAT_LIST_ERRORS" | tr '\n' ' ')]" >&2
      exit 2
    fi
    KEYMAT_LISTING="$(printf '%s\n' "$KEYMAT_LISTING_RAW" | sed -n 's/^F\t//p')"
    KEYMAT_CANDIDATES="$(printf '%s\n' "$KEYMAT_LISTING" | sed '/^[[:space:]]*$/d')"
    # ⓑ 접두 제거는 **리터럴 비교**(`index(...)==1`)다 — `grep -v "^$pre"` 는 BRE 라
    # `/opt/v1.2/` 같은 접두가 `/opt/v1X2/` 까지 지운다(제외가 조용히 넓어지는 fail-open).
    for pre in "${keymat_exclude_array[@]}"; do
      KEYMAT_CANDIDATES="$(printf '%s\n' "$KEYMAT_CANDIDATES" | awk -v p="$pre" 'index($0, p) != 1')"
    done
    KEYMAT_ENTRIES="$(printf '%s\n' "$KEYMAT_CANDIDATES" | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]')"
    # 양성 대조는 제외를 적용한 **뒤에** 센다 — 통째로 넓히는 편집(`/`)이 여기서 판정 불가가
    # 된다. 표적 제외(`/data/` 하나 추가)는 이 수를 거의 줄이지 않으므로 **ⓐ 등식**이 잡는다.
    if ! [[ "$KEYMAT_ENTRIES" =~ ^[0-9]+$ ]] || [ "$KEYMAT_ENTRIES" -lt "$KEYMAT_MIN_ENTRIES" ]; then
      fail "키·인증서 재료 탐색의 대상 파일 수(${KEYMAT_ENTRIES})가 정책 하한(${KEYMAT_MIN_ENTRIES}) 미만이다 — 범위(${KEYMAT_SCAN_PATHS})가 틀렸거나 제외 접두가 너무 넓거나 목록 도구가 없다(판정 불가)"
    else
      # ⓒ 이름 축 — **끝 고정**. 패턴은 확장자이므로 경로의 끝에서만 센다.
      KEYMAT_HITS=""
      for pat in "${keymat_pattern_array[@]}"; do
        suffix="$(printf '%s' "$pat" | sed 's/[].[^$*\\/]/\\&/g')"
        hit="$(printf '%s\n' "$KEYMAT_CANDIDATES" | grep -iE -- "${suffix}\$" || true)"
        [ -z "$hit" ] || KEYMAT_HITS="${KEYMAT_HITS}${hit}"$'\n'
      done
      # ⓓ 내용 축 — 이름이 무엇이든 PEM 개인키는 **머리 줄이 고정**이다. 이름 열거 밖으로
      # 나가는 편집(확장자를 떼거나 바꾸는 것)을 이 겹이 받는다. 공개 번들은 머리 줄이 달라
      # 걸리지 않으므로 이 축에는 제외가 필요 없다.
      #
      # **root 로 읽는다**(r3 vr R3-M1). 앞 판은 이미지의 실행 사용자로 돌아서 개인키의 관례
      # 권한인 root 소유 `0600` 파일에 `Permission denied` 가 났고, 그 오류는 `2>/dev/null` 로
      # 사라졌다 — 즉 **가장 그럴듯한 권한의 키를 못 읽고 초록**이었다. 읽기 전용 검사 컨테이너
      # 이고 `no-new-privileges` 는 그대로 둔다.
      #
      # **양성 대조는 「목록 수」가 아니라 「실제로 읽은 수」다.** 목록만 세면 읽기 실패가 그대로
      # 통과한다(그것이 R3-M1 이었다). 그래서 파일마다 한 바이트를 실제로 읽어 보고 읽힌 것과
      # 못 읽은 것을 갈라 센다 — 못 읽은 것이 하나라도 있으면 **판정 불가**다.
      #
      # 종료 코드로는 「돌았는가」를 가릴 수 없다 — `find` 는 읽을 수 없는 디렉터리 하나로도 1 을
      # 내고 `grep -l` 도 묶음이 비면 1 을 낸다(2026-10-19 실측: 크기 단위를 `-1M` 로 쓰면
      # BusyBox find 가 `invalid number` 로 끊어 탐침 전체가 조용히 0건이 됐다).
      keymat_scan_argv="$keymat_scan_argv_pre"
      KEYMAT_CONTENT_RAW="$(docker run --rm --user 0 --security-opt no-new-privileges --entrypoint sh "$IMAGE_REF" -c \
        "find ${keymat_scan_argv} -xdev -type f -size -${KEYMAT_CONTENT_MAX_BYTES}c 2>/dev/null | while IFS= read -r f; do
           if head -c 1 \"\$f\" >/dev/null 2>&1; then
             printf 'R\\n'
             grep -lE -- '-----BEGIN [A-Z ]*PRIVATE KEY-----' \"\$f\" 2>/dev/null | sed 's/^/H\\t/'
           else
             printf 'U\\t%s\\n' \"\$f\"
           fi
         done" 2>/dev/null || true)"
      KEYMAT_CONTENT_SCANNED="$(printf '%s\n' "$KEYMAT_CONTENT_RAW" | grep -c '^R$' || true)"
      KEYMAT_UNREADABLE="$(printf '%s\n' "$KEYMAT_CONTENT_RAW" | sed -n 's/^U\t//p')"
      KEYMAT_CONTENT_HITS="$(printf '%s\n' "$KEYMAT_CONTENT_RAW" | sed -n 's/^H\t//p')"
      if [ -n "$KEYMAT_UNREADABLE" ]; then
        echo "키 재료 내용 축이 읽지 못한 파일이 있다(판정 불가) — root 로 읽는데도 막혔다면 이 축의 「적중 0」은 깨끗하다는 뜻이 아니다: [$(printf '%s' "$KEYMAT_UNREADABLE" | tr '\n' ' ')]" >&2
        exit 2
      fi
      # 이 하한은 **자기 모집단**(크기 상한만 건 전체 수)의 것이다 — 이름 축의 하한을 빌려 쓰면
      # 제외가 늘어 그쪽이 내려갈 때 이 축의 바닥도 조용히 함께 내려간다(r3 cr R3-7).
      if ! [[ "${KEYMAT_CONTENT_SCANNED:-0}" =~ ^[0-9]+$ ]] || [ "${KEYMAT_CONTENT_SCANNED:-0}" -lt "$KEYMAT_CONTENT_MIN_SCANNED" ]; then
        fail "키 재료 내용 축이 **실제로 읽은** 파일 수(${KEYMAT_CONTENT_SCANNED:-0})가 하한(${KEYMAT_CONTENT_MIN_SCANNED}) 미만이다 — 이미지에 셸·find 가 없거나 탐침이 끊겼다(판정 불가)"
      fi
      [ -z "$KEYMAT_CONTENT_HITS" ] || KEYMAT_HITS="${KEYMAT_HITS}${KEYMAT_CONTENT_HITS}"$'\n'
      KEYMAT_HITS="$(printf '%s' "$KEYMAT_HITS" | sed '/^[[:space:]]*$/d' | sort -u)"
      if [ -n "$KEYMAT_HITS" ]; then
        fail "키·인증서 재료가 이미지에 구워져 있다: [$(printf '%s' "$KEYMAT_HITS" | tr '\n' ' ')] — 이 스택의 로컬 CA 는 실행 시 만들어지고 이미지에 구워지지 않는다"
      fi
    fi
    FORBIDDEN_SUMMARY="금지-실행파일-검사=${FORBIDDEN_EXECUTABLE_COUNT}건 필수-실행파일-검사=${REQUIRED_EXECUTABLE_COUNT}건 내용경로=${CONTENT_PATH} 파일수=${CONTENT_ENTRIES}(하한 ${CONTENT_MIN_ENTRIES}) 필수내용-검사=${#required_content_array[@]}건 금지내용-검사=${#forbidden_content_array[@]}건 키재료-대상=${KEYMAT_ENTRIES}(하한 ${KEYMAT_MIN_ENTRIES}) 키재료-이름패턴=${#keymat_pattern_array[@]}건 키재료-제외=${#keymat_exclude_array[@]}건(등식 고정) 키재료-내용축=읽음${KEYMAT_CONTENT_SCANNED}건(하한 ${KEYMAT_CONTENT_MIN_SCANNED})·못읽음0"
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
