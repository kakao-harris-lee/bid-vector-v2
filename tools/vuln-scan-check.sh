#!/usr/bin/env bash
# 이미지 취약점 게이트(M6/6E-2b scope.md V-2, 결정 B-1~B-5). `tools/image-hygiene-check.sh`
# 가 「무엇이 이미지에 들어 있나」를 재는 자리라면, 이쪽은 **그 안의 패키지에 알려진 결함이
# 있는가**를 잰다 — 완료 조건 8 의 나머지 절반이고, 받는 OPEN 은 `OPEN-6C-IMAGE-VULN-SCAN`
# (D-6C-5 「흉내만 낸 스캔은 상시 초록 게이트가 된다」)이다.
#
# **판정은 트리비의 사람이 읽는 표가 아니라 JSON 결과 집합에 건다**(B-1). 텍스트 표는 폭에
# 따라 열이 잘리고 severity 낱말이 다른 칸에도 나오므로, grep 술어는 스타일 하나로 열린다
# (하네스 「게이트 술어는 문자열이 아니라 구조로」).
#
# **SBOM 과 스캔은 같은 입력에서 나온다**(B-1 의 사유). 이미지에서 CycloneDX SBOM 을 먼저
# 만들고, 스캔은 **그 SBOM 을 입력으로** 돈다 — 보관본이 곧 판정 대상이라 둘이 갈릴 자리가
# 구조적으로 없다. 잠금은 둘이다: 태그가 아니라 **이미지 ID** 로 SBOM 을 뜨고(아래), 스캔은
# 그 SBOM 만 읽는다.
#
# 사용법: tools/vuln-scan-check.sh <image-ref> <image-kind> <policy-file>
#   exit 0 = 통과 · 1 = 차단(미등재 finding / 만료·stale allowlist)
#        · 2 = 정책·도구·사용법 오류 **또는 판정 불가**(암묵 입력 발견 · 양성 대조 하한 미달 · DB 메타데이터 부재)
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "사용법: $0 <image-ref> <image-kind> <policy-file>" >&2
  exit 2
fi

IMAGE_REF="$1"
IMAGE_KIND="$2"
POLICY_FILE="$3"

if [ ! -f "$POLICY_FILE" ]; then
  echo "정책 파일이 없다: $POLICY_FILE" >&2
  exit 2
fi

for tool in jq trivy docker; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "${tool} 가 필요하다 — 이 게이트가 SBOM 생성·결과 판독·이미지 조회에 쓴다" >&2
    exit 2
  fi
done

# **암묵 입력 차단 (가) — D-6E2B-5, verifier r1 H-1 · code-review r1 M-1.**
# trivy 는 플래그 말고도 세 자리에서 설정을 받는다: cwd 의 `trivy.yaml`, cwd 의 `.trivyignore`,
# 그리고 거의 모든 플래그에 대응하는 `TRIVY_*` 환경변수. 그 셋은 **만료일도 사유도 stale 검사도
# 없는 둘째 면제 축**이고, 게이트가 받는 결과 집합에서 finding 을 지워 버리므로 미등재 검사도
# stale 검사도 그것을 보지 못한다(r1 실측: 루트에 `.trivyignore` 한 줄이면 CRITICAL 셋이 사라지고
# exit 0, `trivy.yaml` 의 `severity: [UNKNOWN]` 이면 findings_total 이 5 로 줄고 등재가 비면 초록).
#
# 환경변수는 **지우지 않고 거부한다.** 지우면 「누가 무엇을 주려 했는가」가 로그에서 사라지고,
# 게이트가 조용히 다른 입력으로 돌아간다 — 이 slice 의 다른 거부들과 같은 선택이다(값을 지어내지
# 않고 판정 불가를 선언한다). 이 스크립트는 `TRIVY_*` 를 **하나도 세우지 않으므로** 발견되는 것은
# 전부 바깥에서 온 것이다.
# `${!TRIVY_@}` 는 **이 스크립트 자신의 셸 변수도 본다** — `TRIVY_SANDBOX`·`TRIVY_VERSION`·
# `TRIVY_PINNED_VERSION`·`TRIVY_SEVERITIES`·`TRIVY_SCANNERS`·`TRIVY_PKG_TYPES` 가 그 접두를 갖는다.
# 그것들은 **이 검사보다 뒤에서** 처음 대입되므로 지금 시점에는 존재하지 않고, 그래서 여기서 걸리지
# 않는다. 순서에 기대는 잠금이라는 뜻이다 — **이 검사를 아래로 옮기면 자기 변수에 걸려 항상 거부한다.**
_trivy_env_names=( ${!TRIVY_@} )
if [ "${#_trivy_env_names[@]}" -gt 0 ]; then
  echo "TRIVY_* 환경변수가 설정돼 있다(${_trivy_env_names[*]}) — 이 게이트는 그것을 입력으로 받지 않는다. 면제 축은 allowlist 하나뿐이어야 한다(만료·사유·stale 이 걸린다)" >&2
  exit 2
fi

# 정책 값 판독. `tools/image-hygiene-check.sh` 의 `_policy_value` 와 같은 설계다(중복 키·빈 값·값 모양을
# 전부 정책 오류로 끊는다 — 6C 의 세 라운드가 만든 규율). **CRLF 는 「거부」가 아니라 「절삭」이다**
# (PR #68 /code-review): `tr -d '\r'` 로 캐리지 리턴을 **지우고**, 그 결과가 비면 그때 빈 값으로 끊는다.
# 그래서 CRLF 가 섞인 정책 파일은 값이 살아 있으면 통과한다 — 「CRLF 를 정책 오류로 끊는다」가 아니다. **셸 게이트가 둘이
# 되면서 이 파서도 둘이 됐다** — 모집단 증가로 `OPEN-6C-POLICY-GATE-STRUCTURAL`(셸 정책 파싱을
# build-logic 타입 태스크로)에 등재한다. 지금 공유 라이브러리로 뽑지 않는 것은 그 추출이
# 검증된 게이트(6C)를 이 slice 의 범위 밖에서 건드리기 때문이다.
_policy_value() {
  local key="$1"
  local kind="${2:-text}" # text | numeric | list | enum-list | bool | sha256 | version
  local allowed="${3:-}"  # enum-list 일 때 허용 값 집합(쉼표 구분)
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
    bool)
      if [ "$raw" != "true" ] && [ "$raw" != "false" ]; then
        echo "정책 키 ${key} 의 값이 true/false 가 아니다: '${raw}'(참/거짓을 지어내지 않는다)" >&2
        exit 2
      fi
      ;;
    sha256)
      if ! [[ "$raw" =~ ^[0-9a-f]{64}$ ]]; then
        echo "정책 키 ${key} 의 값이 소문자 64자리 SHA-256 이 아니다: '${raw}'" >&2
        exit 2
      fi
      ;;
    version)
      if ! [[ "$raw" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "정책 키 ${key} 의 값이 x.y.z 형태가 아니다: '${raw}'" >&2
        exit 2
      fi
      ;;
    list)
      # 허용 문자 집합으로 뒤집어 적는다(6C R5-1 과 같은 근거) — 공백·유니코드 공백·따옴표가
      # 한 술어로 함께 닫힌다. 원소에 뭔가 섞이면 그 원소의 판정이 조용히 꺼지는 것이 이 축이
      # 막는 결함이다.
      local IFS=','
      local -a items
      read -r -a items <<< "$raw"
      if [ "${#items[@]}" -lt 1 ]; then
        echo "정책 키 ${key} 는 최소 1개 원소가 있어야 한다: '${raw}'" >&2
        exit 2
      fi
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
    enum-list)
      # **값 영역까지 본다**(verifier r1 M-1 (a) · code-review r1). `list` 는 허용 **문자**만 보므로
      # `High,Critical` 같은 값이 통과하는데, 그것은 어떤 finding 과도 맞지 않아 **차단 후보가 0** 이
      # 된다 — 게이트가 아무것도 판정하지 않으면서 초록이다. 오늘은 allowlist 67건의 stale 검사가
      # 우연히 그물 노릇을 하지만, 이 slice 의 목표 상태(6E-2c 뒤 등재 0)에서는 그 그물이 사라진다.
      # 그래서 도구의 열거값과 **같은 집합**을 정책 쪽에서도 요구한다.
      if [ -z "$allowed" ]; then
        echo "enum-list 검증에 허용 값 집합이 주어지지 않았다(스크립트 결함): ${key}" >&2
        exit 2
      fi
      local IFS=','
      local -a enum_items
      read -r -a enum_items <<< "$raw"
      if [ "${#enum_items[@]}" -lt 1 ]; then
        echo "정책 키 ${key} 는 최소 1개 원소가 있어야 한다: '${raw}'" >&2
        exit 2
      fi
      local enum_item
      for enum_item in "${enum_items[@]}"; do
        if ! _contains "$allowed" "$enum_item"; then
          echo "정책 키 ${key} 의 원소 '${enum_item}' 가 도구의 허용 값(${allowed})에 없다 — 모양은 맞지만 어떤 finding 과도 맞지 않아 판정이 조용히 비게 된다" >&2
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

# trivy 0.75.0 의 열거값. **도구가 정하는 집합이므로 여기 적는다** — 정책 파일에 두면 「정책이
# 자기 검증 기준을 정하는」 꼴이 되어 검증이 공허해진다. 도구 판을 올릴 때 함께 본다
# (`trivy image --help` 의 Allowed values).
TRIVY_SEVERITIES="UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL"
TRIVY_SCANNERS="vuln,misconfig,secret,license"
TRIVY_PKG_TYPES="os,library"

# 줄 수를 셀 때 `printf '%s'`(개행 없음)를 쓰지 않는다 — `wc -l` 은 개행을 세므로 마지막 줄이
# 빠져 **차단 대상 한 건이 0 으로 읽힌다**(게이트가 조용히 통과하는 방향의 off-by-one,
# 2026-10-08 실측으로 잡음). `printf '%s\n'` 는 빈 문자열에 대해서도 0 을 낸다.
_count_lines() {
  printf '%s\n' "$1" | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]'
}

# **파이프 없는 멤버십 검사**(PR #68 /code-review A). `printf … | grep -qxF` 는 `set -o pipefail`
# 아래에서 **일치를 불일치로 읽는다**: `grep -q` 가 첫 일치에서 즉시 나가며 파이프를 닫고, 그러면
# `printf` 가 SIGPIPE(141)로 죽어 파이프라인 상태가 141 이 된다. 건초더미가 파이프 버퍼(64KB)를 넘고
# 일치가 앞쪽에 있을 때 재현된다 — 실측: 668KB·첫 줄 일치에서 옛 형태는 「불일치」, here-string 은
# 「일치」. **중복 키 검사에서는 이것이 게이트를 여는 쪽으로 틀린다**(중복을 못 보고 지난다).
#
# 같은 결함을 이 저장소의 `ci.yml` 이 이미 한 번 고쳤다(앱 로그 검사) — 그때의 처방과 같은 모양으로,
# 파이프를 없앤다. here-string 은 임시 파일로 전달되므로 SIGPIPE 자체가 생기지 않는다.
_in_lines() { # <찾을 줄> <건초더미(개행 구분 문자열)>
  grep -qxF -- "$1" <<< "$2"
}

_contains() { # <쉼표 목록> <원소>
  local IFS=','
  local -a items
  read -r -a items <<< "$1"
  local item
  for item in "${items[@]}"; do
    [ "$item" = "$2" ] && return 0
  done
  return 1
}

SCAN_KINDS="$(_policy_value scan.kinds list)"
if ! _contains "$SCAN_KINDS" "$IMAGE_KIND"; then
  echo "이미지 kind '${IMAGE_KIND}' 가 정책의 scan.kinds(${SCAN_KINDS})에 없다 — 어느 정책으로 판정했는지가 명령에 보여야 한다" >&2
  exit 2
fi

BLOCK_SEVERITIES="$(_policy_value block.severities enum-list "$TRIVY_SEVERITIES")"
BLOCK_ONLY_FIXED="$(_policy_value block.only-fixed bool)"
SCAN_SCANNERS="$(_policy_value scan.scanners enum-list "$TRIVY_SCANNERS")"
# 취약점 게이트인데 `vuln` 이 빠지면 **판정할 것이 없다**(verifier 표적 L-1). 열거값 안에 있으므로
# 모양 검사는 통과한다 — 「허용 값이다」와 「이 게이트에 쓸모가 있다」는 다른 질문이다.
if ! _contains "$SCAN_SCANNERS" "vuln"; then
  echo "정책 키 scan.scanners 에 'vuln' 이 없다(${SCAN_SCANNERS}) — 취약점 게이트가 취약점을 보지 않으면 판정이 공허하다" >&2
  exit 2
fi
SCAN_PKG_TYPES="$(_policy_value scan.pkg-types enum-list "$TRIVY_PKG_TYPES")"
SBOM_FORMAT="$(_policy_value sbom.format text)"
REPORT_DIR="$(_policy_value report.dir text)"
ALLOWLIST_FILE="$(_policy_value allowlist.file text)"
ALLOWLIST_MAX_DAYS="$(_policy_value allowlist.max-days numeric)"
# 설치 step 만 도구 핀을 보면, **이 게이트가 어느 스캐너로 판정했는가**는 아무도 잠그지 않는다
# (호스트에 다른 판이 깔려 있으면 그대로 돈다). 판정 자신이 핀을 확인한다 — 요약에 판을 찍는 것과
# 판으로 끊는 것은 다른 일이고, 재현 가능한 판정에 필요한 것은 후자다.
TRIVY_PINNED_VERSION="$(_policy_value tool.trivy.version version)"

# **양성 대조가 먼저다**(6C 의 세 라운드가 만든 규율). SBOM 이 비거나 패키지를 못 찾으면
# 「차단 대상 0」은 공허하게 참이다 — 상시 초록 게이트가 되는 바로 그 경로(D-6C-5). 그래서
# kind 별 하한을 정책이 정하고, 미달이면 **판정 불가로 실패**한다. 선언된 kind 전부가 이 키를
# 가져야 한다 — 하나라도 없으면 그 kind 가 돌 때까지 구멍이 보이지 않으므로 여기서 전수 확인한다.
_each_kind_has() { # <키 접두> <값 종류>
  local IFS=','
  local -a kinds
  read -r -a kinds <<< "$SCAN_KINDS"
  local k
  for k in "${kinds[@]}"; do
    _policy_value "${1}.${k}" "$2" >/dev/null
  done
}
_each_kind_has scan.min-packages numeric
_each_kind_has scan.min-analyzed-packages numeric
MIN_PACKAGES="$(_policy_value "scan.min-packages.${IMAGE_KIND}" numeric)"
# **스캔 쪽 하한**(D-6E2B-5 나②, code-review r1 H-1). 위 `min-packages` 는 SBOM **입력**의 구성요소
# 수이고, 이것은 **스캔 결과가 나열한 패키지 수**다. 둘은 다른 축이다 — SBOM 이 멀쩡해도 스캔이 서지
# 않으면 `.Results` 가 비는데 구성요소 수는 그대로다.
#
# **과대 주장하지 않는다(verifier 표적 M-1)**: `--list-all-pkgs` 는 DB 매칭과 무관하게 목록을 싣는다.
# 그래서 이 하한은 「결과가 통째로 빔」을 잡고 **「나열은 했는데 맞춰 보지 않음」은 못 잡는다**
# (`OPEN-6E2B-OS-MATCH-PREDICATE`).
# **finding 수 하한이 아니다**: 「취약점이 몇 건 이상이어야 한다」는 이미지가 정말 깨끗해지는 날
# 거짓이 된다. 「분석한 패키지가 몇 개 이상이어야 한다」는 그날에도 참이다.
MIN_ANALYZED_PACKAGES="$(_policy_value "scan.min-analyzed-packages.${IMAGE_KIND}" numeric)"
DB_MAX_AGE_DAYS="$(_policy_value scan.db.max-age-days numeric)"
# Java DB 는 jar 층을 다루는 kind 에서만 받아진다. 「전부 요구」로 두면 jar 가 없는 이미지가 못
# 받은 DB 때문에 붉어지고, 「전부 생략」으로 두면 jar 를 보는 이미지가 DB 없이 조용히 초록이다.
# 어느 kind 가 그것을 요구하는지는 **정책이 선언**한다.
_each_kind_has scan.java-db-required bool
JAVA_DB_REQUIRED="$(_policy_value "scan.java-db-required.${IMAGE_KIND}" bool)"

if [ ! -f "$ALLOWLIST_FILE" ]; then
  echo "allowlist 파일이 없다: ${ALLOWLIST_FILE}(정책이 가리키는 파일은 실재해야 한다 — 부재를 '등재 0'으로 읽지 않는다)" >&2
  exit 2
fi

if ! docker image inspect "$IMAGE_REF" >/dev/null 2>&1; then
  echo "이미지를 로컬에서 찾지 못했다: ${IMAGE_REF}" >&2
  exit 2
fi
# 태그가 아니라 **이미지 ID(내용 주소)** 로 스캔한다 — 요약에 그 ID 를 싣는 것과 짝이다.
# 태그는 실행 사이에 다른 바이트를 가리킬 수 있고, 그때 「무엇을 쟀는가」가 기록에서 사라진다.
IMAGE_ID="$(docker image inspect "$IMAGE_REF" --format '{{.Id}}')"

mkdir -p "$REPORT_DIR"
# 산출물 경로를 **절대 경로로 굳힌다.** 아래에서 trivy 를 빈 임시 디렉터리에서 돌리므로 상대
# 경로는 거기에 매달린다(code-review r1 L-4 가 지적한 「CWD 에 따라 산출물 자리가 달라진다」는
# 축이 여기서 필연이 된다).
REPORT_DIR_ABS="$(cd "$REPORT_DIR" && pwd)"
SBOM_FILE="${REPORT_DIR_ABS}/${IMAGE_KIND}-sbom.cdx.json"
SCAN_FILE="${REPORT_DIR_ABS}/${IMAGE_KIND}-scan.json"

# **암묵 입력 차단 (가) 의 나머지 절반** — 환경변수는 위에서 거부했고, 파일 둘은 여기서 닫는다.
# 잠금을 **둘 다** 건다(한쪽이 미래의 trivy 판에서 바뀌어도 다른 쪽이 남는다, 2026-10-09 실측으로
# 각각 독립으로 성립함을 확인) —
#   ① trivy 를 **빈 임시 디렉터리**에서 돌린다 → cwd 의 `trivy.yaml`·`.trivyignore` 가 없다
#   ② 그래도 `--config`·`--ignorefile` 로 **빈 파일을 명시**한다 → 기본 탐색 자체가 일어나지 않는다
#      — `--ignorefile` 은 **두 호출 모두**에 건다(PR #68 /code-review F: `sbom` 에만 있었다.
#      `image` 도 `.trivyignore` 를 읽으므로 한쪽만 걸면 SBOM 생성 단계가 열려 있다)
# 적대 cwd(둘 다 심은 디렉터리)에서 ② 만으로도 finding 집합이 보존됨을 실측했다.
# **판정 불가는 차단이 아니다 — exit 2 다**(code-review r1 M-5). 운영자는 runbook §8.2 를 읽고 exit 1 을
# 보면 「올리거나 등재하라」로 가는데, 재지 못한 경우에 할 일은 **스캐너가 왜 못 읽었는지 보는 것**이다.
# 종료 코드가 처방을 가르는 유일한 축이므로 그 둘이 어긋나면 안 된다.
_undecidable() {
  echo "판정 불가: $1" >&2
  echo "== 취약점 게이트 판정 불가(exit 2) — 이것은 「취약점이 있다」가 아니라 「재지 못했다」다 ==" >&2
  exit 2
}

TRIVY_SANDBOX="$(mktemp -d)"
cleanup_sandbox() { rm -rf "$TRIVY_SANDBOX"; }
trap cleanup_sandbox EXIT
EMPTY_CONFIG="${TRIVY_SANDBOX}/empty-trivy.yaml"
EMPTY_IGNOREFILE="${TRIVY_SANDBOX}/empty-trivyignore"
: > "$EMPTY_CONFIG"
: > "$EMPTY_IGNOREFILE"
mkdir -p "${TRIVY_SANDBOX}/work" "${TRIVY_SANDBOX}/cache" "${TRIVY_SANDBOX}/modules"

# **캐시도 샌드박스 안의 빈 디렉터리로 명시한다**(verifier 표적 M-2). env 잠금은 `TRIVY_` 접두 **열거**라
# 같은 일을 하는 `XDG_CACHE_HOME`·`HOME` 이 남아 있었다 — 메타데이터는 신선한데 내용만 비운 DB 를 그
# 경로로 심으면 trivy 가 다운로드를 건너뛰고, DB 술어도 분석 하한도 전부 통과한 채 finding 만 0 이 된다
# (실측). 열거를 늘리는 대신 **캐시 자리를 우리가 정한다**: 그러면 B-5 (a) 의 「실행마다 받는다」가
# 술어가 아니라 **구성으로** 참이 되고, 바깥 캐시가 가리키는 자리가 아예 쓰이지 않는다.
#
# 대가: 게이트 실행마다 DB 를 새로 받는다(한 실행 안의 호출 셋은 같은 캐시를 공유하므로 한 번이다).
# 그것이 이 축에서 맞는 비용이다 — 「받았다고 적힌 DB」와 「실제로 쓴 DB」가 갈릴 자리를 없앤다.
_trivy() {
  ( cd "${TRIVY_SANDBOX}/work" \
    && trivy --config "$EMPTY_CONFIG" --cache-dir "${TRIVY_SANDBOX}/cache" "$@" )
}

echo "== 이미지 취약점 게이트: ${IMAGE_REF} (kind=${IMAGE_KIND}) =="

# 취약점 DB 는 실행마다 받는다(B-5 (a)) — 새 CVE 로 붉어지는 것이 이 게이트의 목적이고,
# 처방(상향 또는 만료 있는 등재)은 정책 파일에 있다. 받은 DB 의 버전·갱신 시각은 아래에서
# **술어로** 쓴다(요약에만 싣고 끝내면 DB 가 없어도 초록이다 — code-review r1 M-2).
#
# `--quiet` 를 떼었다(code-review r1 L-2) — DB 가 오래됐다거나 층을 건너뛴다는 trivy 자신의
# 경고가 CI 로그에 남아야 한다. 보고서는 `--output` 으로 파일에 가므로 판정 출력이 더러워지지 않는다.
# trivy 자신의 치명 오류(DB 를 못 받음·이미지를 못 읽음)는 **차단이 아니라 판정 불가**다
# (verifier 표적 M-3). `set -e` 아래 그대로 두면 trivy 의 코드 1 이 그대로 나가 runbook §8.2 의
# 「1 = 차단 → 올리거나 등재하라」로 읽힌다 — cr M-5 가 하한 미달에서 고친 것과 같은 계열이다.
# **WASM 모듈 디렉터리도 고정한다**(verifier 표적2 M-A). trivy 는 기본으로 `$HOME/.trivy/modules` 의
# 모듈을 읽고, 모듈은 **post-scan 으로 결과를 고칠 수 있다** — 만료도 사유도 stale 도 없는 면제 축이고,
# 캐시(M-2)와 같은 계열의 남은 경로다. 여기서도 열거가 아니라 **자리를 우리가 정한다**.
#
# 플래그를 `image` 에만 다는 이유는 **측정된 사실**이다(2026-10-09): `--module-dir` 는 `image` 만 받고
# `sbom`·`version` 은 플래그 자체가 없다. 그리고 같은 쓰레기 모듈을 `$HOME` 에 심었을 때 **`image` 는
# 읽고(`Reading a module...` 뒤 FATAL) `sbom` 은 읽지 않는다**(exit 0, 그 줄 없음). 즉 오늘 모듈 축은
# `image` 에만 있고, 이 한 줄이 그 축 전부를 덮는다.
#
# **전환 조건**: 뒤 trivy 판이 `sbom` 에도 모듈을 붙이면 이 잠금은 그 자리를 덮지 못한다. 그때는 이 줄을
# 옮기는 것이 아니라 **잠금을 `HOME` 쪽으로 올려야** 한다(모든 하위 명령을 한 번에 덮는 자리). 도구 판을
# 올릴 때 `trivy sbom --help` 에 모듈 플래그가 생겼는지 함께 본다.
if ! _trivy image \
  --module-dir "${TRIVY_SANDBOX}/modules" \
  --ignorefile "$EMPTY_IGNOREFILE" \
  --scanners "$SCAN_SCANNERS" \
  --pkg-types "$SCAN_PKG_TYPES" \
  --format "$SBOM_FORMAT" \
  --output "$SBOM_FILE" \
  "$IMAGE_ID"; then
  _undecidable "trivy 가 이미지에서 SBOM 을 만들지 못했다(취약점 DB 를 못 받았거나 이미지를 못 읽었다) — 올리거나 등재할 일이 아니다"
fi

# 스캔 입력은 **방금 만든 SBOM** 이다 — 이미지를 두 번 읽지 않으므로 보관한 SBOM 과 판정
# 대상이 어긋날 자리가 없다. severity 로 미리 거르지 않는다: 보고서에는 전부 남기고 **차단
# 판정만** 아래 jq 가 정책대로 좁힌다(필터를 CLI 에 걸면 정책과 실제 판정이 두 자리가 된다).
#
# `--list-all-pkgs` 는 스캔 결과가 **나열한** 패키지 목록을 싣는다(아래 스캔 쪽 하한의 입력). 그것
# 없이는 `.Results` 에 취약점만 들어와 「결과가 통째로 비었다」와 「정말 깨끗함」을 가를 수가 없다.
if ! _trivy sbom \
  --scanners "$SCAN_SCANNERS" \
  --pkg-types "$SCAN_PKG_TYPES" \
  --list-all-pkgs \
  --ignorefile "$EMPTY_IGNOREFILE" \
  --format json \
  --output "$SCAN_FILE" \
  "$SBOM_FILE"; then
  _undecidable "trivy 가 SBOM 을 스캔하지 못했다(취약점 DB·Java DB 를 못 받았을 수 있다) — 올리거나 등재할 일이 아니다"
fi

if ! TRIVY_VERSION_JSON="$(_trivy version --format json)"; then
  _undecidable "trivy version 을 읽지 못했다 — 어느 도구·어느 DB 로 판정했는지를 기록할 수 없다"
fi
TRIVY_VERSION="$(printf '%s' "$TRIVY_VERSION_JSON" | jq -r '.Version // "unknown"')"
VULN_DB_VERSION="$(printf '%s' "$TRIVY_VERSION_JSON" | jq -r '.VulnerabilityDB.Version // "unknown"')"
VULN_DB_UPDATED_AT="$(printf '%s' "$TRIVY_VERSION_JSON" | jq -r '.VulnerabilityDB.UpdatedAt // "unknown"')"
VULN_DB_NEXT_UPDATE="$(printf '%s' "$TRIVY_VERSION_JSON" | jq -r '.VulnerabilityDB.NextUpdate // "unknown"')"
JAVA_DB_VERSION="$(printf '%s' "$TRIVY_VERSION_JSON" | jq -r '.JavaDB.Version // "unknown"')"
JAVA_DB_UPDATED_AT="$(printf '%s' "$TRIVY_VERSION_JSON" | jq -r '.JavaDB.UpdatedAt // "unknown"')"

if [ "$TRIVY_VERSION" != "$TRIVY_PINNED_VERSION" ]; then
  echo "도는 trivy(${TRIVY_VERSION})가 정책이 핀한 판(${TRIVY_PINNED_VERSION})과 다르다 — 판정이 재현되지 않는다" >&2
  exit 2
fi

SBOM_COMPONENTS="$(jq '[.components[]? | select(.type == "library" or .type == "operating-system")] | length' "$SBOM_FILE")"
# `--list-all-pkgs` 가 실은 **나열된** 패키지 목록(DB 매칭 여부와 무관). 결과 쪽 양성 대조의 입력이다.
ANALYZED_PACKAGES="$(jq '[.Results[]? | (.Packages // [])[]] | length' "$SCAN_FILE")"

failures=0
fail() { echo "취약점 게이트 위반: $1" >&2; failures=$((failures + 1)); }

# **취약점 DB 메타데이터 술어 (D-6E2B-5 나③, code-review r1 M-2).** 앞 판은 세 값을 전부
# `// "unknown"` 으로 떨어뜨려 요약에만 찍었다 — **DB 가 아예 없거나 갱신이 멎었어도 초록**이었다.
# 바로 위에서 도구의 *판*은 exit 2 로 끊으면서 판정의 다른 절반인 *DB* 는 끊지 않는, 반쪽만 적용된
# 논리였다. 「실행마다 받는다」(B-5 (a))가 참이려면 받은 것이 실재하고 최신인지를 게이트가 봐야 한다.
_check_db_metadata() { # <라벨> <버전> <갱신 시각>
  local label="$1" version="$2" updated="$3"
  case "$version" in
    '' | unknown | null)
      _undecidable "${label} 의 버전을 읽지 못했다('${version}') — DB 가 실리지 않았을 수 있다. 이 상태의 '차단 0' 은 '깨끗하다'가 아니라 '맞춰 보지 않았다'다"
      ;;
  esac
  case "$updated" in
    '' | unknown | null)
      _undecidable "${label} 의 갱신 시각을 읽지 못했다('${updated}')"
      ;;
  esac
  local epoch age
  if ! epoch="$(date -u -d "$updated" +%s 2>/dev/null)"; then
    _undecidable "${label} 의 갱신 시각을 날짜로 읽지 못했다: '${updated}'"
  fi
  age=$(( ( $(date -u +%s) - epoch ) / 86400 ))
  # 미래 시각이면 나이가 음수가 되어 상한 비교를 그냥 통과한다(verifier 표적 L-2). 메타데이터 위조나
  # 시계 왜곡에서만 생기지만, **신선함을 재는 술어가 그 반대쪽으로 열려 있으면 술어가 아니다.**
  if [ "$age" -lt 0 ]; then
    _undecidable "${label} 의 갱신 시각이 미래다(${updated}) — 메타데이터가 위조됐거나 시계가 어긋났다"
  fi
  if [ "$age" -gt "$DB_MAX_AGE_DAYS" ]; then
    _undecidable "${label} 이 ${age}일 전 것이다(정책 상한 ${DB_MAX_AGE_DAYS}일) — 새 CVE 를 못 보고 있다. 캐시를 비우고 다시 받거나 상류 공시 상태를 본다"
  fi
  printf '%s' "$age"
}

VULN_DB_AGE_DAYS="$(_check_db_metadata "취약점 DB" "$VULN_DB_VERSION" "$VULN_DB_UPDATED_AT")"
JAVA_DB_AGE_SUMMARY="요구하지 않음"
if [ "$JAVA_DB_REQUIRED" = "true" ]; then
  JAVA_DB_AGE_SUMMARY="$(_check_db_metadata "Java DB" "$JAVA_DB_VERSION" "$JAVA_DB_UPDATED_AT")일"
fi

# 양성 대조 둘. **입력 쪽**(SBOM 구성요소)과 **결과 쪽**(스캔이 분석한 패키지)을 따로 센다 —
# 앞 판은 입력 쪽만 있었고, 그래서 「이미지를 읽었는가」만 닫히고 「그 SBOM 에 DB 를 맞춰 봤는가」는
# 열려 있었다. 그 구멍을 덮던 것은 allowlist stale 검사 하나뿐인데, 등재가 0 이 되는 날 사라진다.
if ! [[ "$SBOM_COMPONENTS" =~ ^[0-9]+$ ]] || [ "$SBOM_COMPONENTS" -lt "$MIN_PACKAGES" ]; then
  _undecidable "SBOM 의 패키지 구성 요소 수(${SBOM_COMPONENTS})가 정책 하한(${MIN_PACKAGES}, kind=${IMAGE_KIND}) 미만이다 — 스캐너가 이 이미지를 읽지 못했을 수 있다(이 상태에서 '차단 0' 은 공허하게 참이다)"
fi

if ! [[ "$ANALYZED_PACKAGES" =~ ^[0-9]+$ ]] || [ "$ANALYZED_PACKAGES" -lt "$MIN_ANALYZED_PACKAGES" ]; then
  _undecidable "스캔이 분석한 패키지 수(${ANALYZED_PACKAGES})가 정책 하한(${MIN_ANALYZED_PACKAGES}, kind=${IMAGE_KIND}) 미만이다 — 결과가 통째로 비었다(스캔이 서지 않았거나 SBOM 을 읽지 못했다). 이 상태에서 '차단 0' 은 공허하게 참이다"
fi

# 차단 후보 집합 — 정책이 정하는 severity 이고, `block.only-fixed` 가 참이면 **수정본이 있는
# 것만**이다(B-3 (a) — 고칠 길이 없는 것으로 붉히면 상시 붉은 게이트가 된다). 판정 단위는
# (취약점 ID, 패키지 이름)이고 allowlist 키와 같은 단위다.
CANDIDATES="$(jq -r \
  --argjson severities "$(printf '%s' "$BLOCK_SEVERITIES" | jq -R 'split(",")')" \
  --argjson onlyFixed "$BLOCK_ONLY_FIXED" '
    [ .Results[]? | (.Vulnerabilities // [])[]
      | select(.Severity as $s | $severities | index($s))
      | select(($onlyFixed | not) or ((.FixedVersion // "") != ""))
      | "\(.VulnerabilityID)|\(.PkgName)|\(.Severity)|\(.InstalledVersion // "")|\(.FixedVersion // "")"
    ] | unique | .[]' "$SCAN_FILE")"

TOTAL_FINDINGS="$(jq '[.Results[]? | (.Vulnerabilities // [])[]] | length' "$SCAN_FILE")"
CANDIDATE_COUNT="$(_count_lines "$CANDIDATES")"

# allowlist 판독. 키는 `allow|<kind>|<취약점 ID>|<패키지 이름>`, 값은 `<만료일> <사유>` 다
# (좌표 금지 — 키는 내용에서 뽑는다, leak-baseline 의 교훈). 구분자 `|` 는 세 성분 어디에도
# 나타나지 않는다. 모양 위반·중복 키는 정책 오류이고, 만료·창 상한 위반과 stale 은 차단이다.
TODAY="$(date -u +%Y-%m-%d)"
# 날짜 산술은 GNU `date -d` 에 기댄다. BSD `date`(macOS 기본)에는 그 플래그가 없어 **만료 검사가
# 통째로 서지 않는다** — 조용히 꺼지는 대신 여기서, 그 이름으로 끊는다.
if ! TODAY_EPOCH="$(date -u -d "$TODAY" +%s 2>/dev/null)"; then
  echo "이 게이트는 GNU date(-d 날짜 산술)를 요구한다 — 만료 검사가 그것 없이는 서지 않는다" >&2
  exit 2
fi
MAX_EPOCH="$(date -u -d "$TODAY + ${ALLOWLIST_MAX_DAYS} days" +%s)"

ALLOW_LINES="$(sed -e 's/\r$//' -e 's/^[[:space:]]*//' "$ALLOWLIST_FILE" | grep -v '^#' | grep -v '^$' || true)"

allow_entry_count=0
# 배열 대신 **개행 구분 문자열**로 쌓는다 — 멤버십이 here-string 한 번으로 끝나고, 배열을 매번
# `printf` 로 펴면서 파이프를 만들 일이 없다.
allow_seen_keys_text=""
allow_this_kind_text=""
allow_this_kind=()
while IFS= read -r line; do
  [ -n "$line" ] || continue
  case "$line" in
    allow\|*) ;;
    *)
      echo "allowlist 행이 'allow|<kind>|<취약점 ID>|<패키지 이름>=<만료일> <사유>' 형태가 아니다: '${line}'" >&2
      exit 2
      ;;
  esac
  entry_key="${line%%=*}"
  entry_value="${line#*=}"
  if [ "$entry_key" = "$line" ]; then
    echo "allowlist 행에 '=' 가 없다: '${line}'" >&2
    exit 2
  fi
  IFS='|' read -r f_tag f_kind f_id f_pkg f_extra <<< "$entry_key"
  if [ -n "${f_extra:-}" ] || [ "$f_tag" != "allow" ] || [ -z "$f_kind" ] || [ -z "$f_id" ] || [ -z "$f_pkg" ]; then
    echo "allowlist 키의 성분이 'allow|<kind>|<취약점 ID>|<패키지 이름>' 넷이 아니다: '${entry_key}'" >&2
    exit 2
  fi
  if ! _contains "$SCAN_KINDS" "$f_kind"; then
    echo "allowlist 키의 kind '${f_kind}' 가 정책의 scan.kinds(${SCAN_KINDS})에 없다 — 아무 이미지에도 걸리지 않는 등재다: '${entry_key}'" >&2
    exit 2
  fi
  if ! [[ "$f_id" =~ ^[A-Za-z][A-Za-z0-9]*-[A-Za-z0-9.-]+$ ]]; then
    echo "allowlist 키의 취약점 ID 모양이 아니다: '${f_id}'" >&2
    exit 2
  fi
  case "$f_pkg" in
    *[!A-Za-z0-9._+:@/-]*)
      echo "allowlist 키의 패키지 이름에 허용 문자([A-Za-z0-9._+:@/-]) 밖 문자가 있다: '${f_pkg}'" >&2
      exit 2
      ;;
  esac
  entry_expiry="${entry_value%% *}"
  entry_reason="${entry_value#* }"
  if ! [[ "$entry_expiry" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    echo "allowlist 값의 첫 토큰이 만료일(YYYY-MM-DD)이 아니다: '${entry_value}'" >&2
    exit 2
  fi
  if ! entry_epoch="$(date -u -d "$entry_expiry" +%s 2>/dev/null)"; then
    echo "allowlist 만료일이 실재하는 날짜가 아니다: '${entry_expiry}'" >&2
    exit 2
  fi
  # r1(code-review L-7) — 스페이스만 지우면 **탭 하나뿐인 사유**가 통과한다. 이 파일의 다른 모양
  # 검사들이 전부 허용 문자 집합으로 뒤집어 쓴 것과 어긋나던 한 자리다.
  if [ "$entry_reason" = "$entry_value" ] || [ -z "${entry_reason//[[:space:]]/}" ]; then
    echo "allowlist 등재에 사유가 없다(만료일만 있는 등재는 등재가 아니다): '${entry_key}'" >&2
    exit 2
  fi
  if _in_lines "$entry_key" "$allow_seen_keys_text"; then
    echo "allowlist 키가 중복 선언됐다(뒤 값이 앞 값을 조용히 덮는다): '${entry_key}'" >&2
    exit 2
  fi
  allow_seen_keys_text+="${entry_key}"$'\n'
  allow_entry_count=$((allow_entry_count + 1))

  # 만료·창 상한은 kind 와 무관한 날짜 성질이라 **등재 전부**에 건다 — 한 번의 실행이 모든
  # 만료를 잡는다(다른 kind 의 등재가 그 이미지를 돌릴 때까지 숨지 않는다).
  if [ "$entry_epoch" -lt "$TODAY_EPOCH" ]; then
    fail "allowlist 등재가 만료됐다(${entry_expiry} < ${TODAY}): ${entry_key}"
  elif [ "$entry_epoch" -gt "$MAX_EPOCH" ]; then
    fail "allowlist 등재의 만료일이 상한(${ALLOWLIST_MAX_DAYS}일)보다 멀다(${entry_expiry}): ${entry_key}"
  fi

  if [ "$f_kind" = "$IMAGE_KIND" ]; then
    allow_this_kind+=("${f_id}|${f_pkg}")
    allow_this_kind_text+="${f_id}|${f_pkg}"$'\n'
  fi
done <<< "$ALLOW_LINES"

# stale — 이 kind 의 등재가 **차단 후보 어디에도 맞지 않으면** 차단이다(B-4, leak-baseline 의
# 「조용한 stale 금지」 동형). 베이스를 올려 finding 이 사라지면 그 등재가 여기서 붉어져
# 치우기를 강제한다. 차단 후보 집합을 기준으로 재므로 `block.severities` 를 좁히면 그 바깥
# severity 의 등재가 곧바로 stale 로 잡힌다.
candidate_keys="$(printf '%s\n' "$CANDIDATES" | sed '/^[[:space:]]*$/d' | awk -F'|' '{print $1 "|" $2}' | sort -u)"
# 요약에 **판정 단위(ID+패키지)의 수**를 따로 싣는다. 위 `CANDIDATE_COUNT` 는 수정판 문자열까지
# 포함한 행 수라 같은 (ID, 패키지)가 여러 Result 에서 다른 수정판을 달면 둘이 갈린다 — 그때
# 「후보 N 대 적용 N」을 기대하면 멀쩡한 통과가 어긋나 보인다. 적용 수와 비교할 짝은 이쪽이다.
CANDIDATE_KEY_COUNT="$(_count_lines "$candidate_keys")"
allow_applied=0
for entry in "${allow_this_kind[@]:-}"; do
  [ -n "$entry" ] || continue
  if _in_lines "$entry" "$candidate_keys"; then
    allow_applied=$((allow_applied + 1))
  else
    fail "allowlist 등재가 이 이미지의 차단 후보 어디에도 맞지 않는다(stale — 치워야 한다): allow|${IMAGE_KIND}|${entry}"
  fi
done

BLOCKING="$(printf '%s\n' "$CANDIDATES" | sed '/^[[:space:]]*$/d' | while IFS= read -r row; do
  key="$(printf '%s' "$row" | awk -F'|' '{print $1 "|" $2}')"
  if ! _in_lines "$key" "$allow_this_kind_text"; then
    printf '%s\n' "$row"
  fi
done)"
BLOCKING_COUNT="$(_count_lines "$BLOCKING")"

if [ "$BLOCKING_COUNT" -gt 0 ]; then
  echo "-- 미등재 차단 대상(취약점 ID|패키지|severity|설치판|수정판) --" >&2
  printf '%s\n' "$BLOCKING" >&2
  fail "차단 severity(${BLOCK_SEVERITIES})의 미등재 finding 이 ${BLOCKING_COUNT}건 있다 — 올리거나(베이스·의존) 사유·만료와 함께 ${ALLOWLIST_FILE} 에 등재한다"
fi

echo "-- 실측 요약 --"
echo "정책=${POLICY_FILE} kind=${IMAGE_KIND} image_id=${IMAGE_ID}"
echo "trivy=${TRIVY_VERSION} vuln-db-version=${VULN_DB_VERSION} vuln-db-updated-at=${VULN_DB_UPDATED_AT}(${VULN_DB_AGE_DAYS}일 전, 상한 ${DB_MAX_AGE_DAYS}) vuln-db-next-update=${VULN_DB_NEXT_UPDATE}"
echo "java-db-required=${JAVA_DB_REQUIRED} java-db-version=${JAVA_DB_VERSION} java-db-updated-at=${JAVA_DB_UPDATED_AT}(${JAVA_DB_AGE_SUMMARY})"
echo "sbom=${SBOM_FILE}(구성요소 ${SBOM_COMPONENTS}, 하한 ${MIN_PACKAGES}) scan=${SCAN_FILE}(분석 패키지 ${ANALYZED_PACKAGES}, 하한 ${MIN_ANALYZED_PACKAGES})"
echo "findings_total=${TOTAL_FINDINGS} 차단후보(${BLOCK_SEVERITIES}, only-fixed=${BLOCK_ONLY_FIXED})=${CANDIDATE_COUNT} 판정단위[ID+패키지]=${CANDIDATE_KEY_COUNT} allowlist_전체=${allow_entry_count} 이_kind_적용=${allow_applied} 미등재=${BLOCKING_COUNT}"

if [ "$failures" -gt 0 ]; then
  echo "== 취약점 게이트 실패: ${failures}건 ==" >&2
  exit 1
fi

echo "== 취약점 게이트 통과 =="
