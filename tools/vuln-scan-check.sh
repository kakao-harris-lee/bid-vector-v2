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
# 만들고, 스캔은 **그 SBOM 을 입력으로** 돈다 — 두 번 이미지를 읽으면 그 사이에 태그가
# 다른 이미지를 가리킬 수 있고, 그러면 보관한 SBOM 이 판정한 대상과 다른 물건이 된다.
#
# 사용법: tools/vuln-scan-check.sh <image-ref> <image-kind> <policy-file>
#   exit 0 = 통과 · 1 = 차단(미등재 finding / 만료·stale allowlist) · 2 = 정책·도구·사용법 오류
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

# 정책 값 판독. `tools/image-hygiene-check.sh` 의 `_policy_value` 와 같은 설계다(중복 키·빈 값·
# CRLF·값 모양을 전부 정책 오류로 끊는다 — 6C 의 세 라운드가 만든 규율). **셸 게이트가 둘이
# 되면서 이 파서도 둘이 됐다** — 모집단 증가로 `OPEN-6C-POLICY-GATE-STRUCTURAL`(셸 정책 파싱을
# build-logic 타입 태스크로)에 등재한다. 지금 공유 라이브러리로 뽑지 않는 것은 그 추출이
# 검증된 게이트(6C)를 이 slice 의 범위 밖에서 건드리기 때문이다.
_policy_value() {
  local key="$1"
  local kind="${2:-text}" # text | numeric | list | bool | sha256 | version
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
    text) ;;
    *)
      echo "알 수 없는 정책 값 종류 '${kind}'(스크립트 결함)" >&2
      exit 2
      ;;
  esac
  printf '%s' "$raw"
}

# 줄 수를 셀 때 `printf '%s'`(개행 없음)를 쓰지 않는다 — `wc -l` 은 개행을 세므로 마지막 줄이
# 빠져 **차단 대상 한 건이 0 으로 읽힌다**(게이트가 조용히 통과하는 방향의 off-by-one,
# 2026-10-08 실측으로 잡음). `printf '%s\n'` 는 빈 문자열에 대해서도 0 을 낸다.
_count_lines() {
  printf '%s\n' "$1" | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]'
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

BLOCK_SEVERITIES="$(_policy_value block.severities list)"
BLOCK_ONLY_FIXED="$(_policy_value block.only-fixed bool)"
SCAN_SCANNERS="$(_policy_value scan.scanners list)"
SCAN_PKG_TYPES="$(_policy_value scan.pkg-types list)"
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
_each_kind_has_min_packages() {
  local IFS=','
  local -a kinds
  read -r -a kinds <<< "$SCAN_KINDS"
  local k
  for k in "${kinds[@]}"; do
    _policy_value "scan.min-packages.${k}" numeric >/dev/null
  done
}
_each_kind_has_min_packages
MIN_PACKAGES="$(_policy_value "scan.min-packages.${IMAGE_KIND}" numeric)"

if [ ! -f "$ALLOWLIST_FILE" ]; then
  echo "allowlist 파일이 없다: ${ALLOWLIST_FILE}(정책이 가리키는 파일은 실재해야 한다 — 부재를 '등재 0'으로 읽지 않는다)" >&2
  exit 2
fi

if ! docker image inspect "$IMAGE_REF" >/dev/null 2>&1; then
  echo "이미지를 로컬에서 찾지 못했다: ${IMAGE_REF}" >&2
  exit 2
fi
IMAGE_ID="$(docker image inspect "$IMAGE_REF" --format '{{.Id}}')"

mkdir -p "$REPORT_DIR"
SBOM_FILE="${REPORT_DIR}/${IMAGE_KIND}-sbom.cdx.json"
SCAN_FILE="${REPORT_DIR}/${IMAGE_KIND}-scan.json"

echo "== 이미지 취약점 게이트: ${IMAGE_REF} (kind=${IMAGE_KIND}) =="

# 취약점 DB 는 실행마다 받는다(B-5 (a)) — 새 CVE 로 붉어지는 것이 이 게이트의 목적이고,
# 처방(상향 또는 만료 있는 등재)은 정책 파일에 있다. 받은 DB 의 버전·갱신 시각은 아래
# 요약에 남긴다(어느 DB 로 판정했는가가 보이지 않으면 판정을 재현할 수 없다).
trivy image \
  --quiet \
  --scanners "$SCAN_SCANNERS" \
  --pkg-types "$SCAN_PKG_TYPES" \
  --format "$SBOM_FORMAT" \
  --output "$SBOM_FILE" \
  "$IMAGE_ID"

# 스캔 입력은 **방금 만든 SBOM** 이다 — 이미지를 두 번 읽지 않으므로 보관한 SBOM 과 판정
# 대상이 어긋날 자리가 없다. severity 로 미리 거르지 않는다: 보고서에는 전부 남기고 **차단
# 판정만** 아래 jq 가 정책대로 좁힌다(필터를 CLI 에 걸면 정책과 실제 판정이 두 자리가 된다).
trivy sbom \
  --quiet \
  --scanners "$SCAN_SCANNERS" \
  --pkg-types "$SCAN_PKG_TYPES" \
  --format json \
  --output "$SCAN_FILE" \
  "$SBOM_FILE"

TRIVY_VERSION_JSON="$(trivy version --format json)"
TRIVY_VERSION="$(printf '%s' "$TRIVY_VERSION_JSON" | jq -r '.Version // "unknown"')"
VULN_DB_VERSION="$(printf '%s' "$TRIVY_VERSION_JSON" | jq -r '.VulnerabilityDB.Version // "unknown"')"
VULN_DB_UPDATED_AT="$(printf '%s' "$TRIVY_VERSION_JSON" | jq -r '.VulnerabilityDB.UpdatedAt // "unknown"')"
VULN_DB_NEXT_UPDATE="$(printf '%s' "$TRIVY_VERSION_JSON" | jq -r '.VulnerabilityDB.NextUpdate // "unknown"')"

if [ "$TRIVY_VERSION" != "$TRIVY_PINNED_VERSION" ]; then
  echo "도는 trivy(${TRIVY_VERSION})가 정책이 핀한 판(${TRIVY_PINNED_VERSION})과 다르다 — 판정이 재현되지 않는다" >&2
  exit 2
fi

SBOM_COMPONENTS="$(jq '[.components[]? | select(.type == "library" or .type == "operating-system")] | length' "$SBOM_FILE")"

failures=0
fail() { echo "취약점 게이트 위반: $1" >&2; failures=$((failures + 1)); }

if ! [[ "$SBOM_COMPONENTS" =~ ^[0-9]+$ ]] || [ "$SBOM_COMPONENTS" -lt "$MIN_PACKAGES" ]; then
  fail "SBOM 의 패키지 구성 요소 수(${SBOM_COMPONENTS})가 정책 하한(${MIN_PACKAGES}, kind=${IMAGE_KIND}) 미만이다 — 스캐너가 이 이미지를 읽지 못했을 수 있다(판정 불가: 이 상태에서 '차단 0' 은 공허하게 참이다)"
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
allow_seen_keys=()
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
  if [ "$entry_reason" = "$entry_value" ] || [ -z "${entry_reason// /}" ]; then
    echo "allowlist 등재에 사유가 없다(만료일만 있는 등재는 등재가 아니다): '${entry_key}'" >&2
    exit 2
  fi
  if printf '%s\n' "${allow_seen_keys[@]:-}" | grep -qxF -- "$entry_key"; then
    echo "allowlist 키가 중복 선언됐다(뒤 값이 앞 값을 조용히 덮는다): '${entry_key}'" >&2
    exit 2
  fi
  allow_seen_keys+=("$entry_key")
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
  if printf '%s\n' "$candidate_keys" | grep -qxF -- "$entry"; then
    allow_applied=$((allow_applied + 1))
  else
    fail "allowlist 등재가 이 이미지의 차단 후보 어디에도 맞지 않는다(stale — 치워야 한다): allow|${IMAGE_KIND}|${entry}"
  fi
done

BLOCKING="$(printf '%s\n' "$CANDIDATES" | sed '/^[[:space:]]*$/d' | while IFS= read -r row; do
  key="$(printf '%s' "$row" | awk -F'|' '{print $1 "|" $2}')"
  if ! printf '%s\n' "${allow_this_kind[@]:-}" | grep -qxF -- "$key"; then
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
echo "trivy=${TRIVY_VERSION} vuln-db-version=${VULN_DB_VERSION} vuln-db-updated-at=${VULN_DB_UPDATED_AT} vuln-db-next-update=${VULN_DB_NEXT_UPDATE}"
echo "sbom=${SBOM_FILE}(구성요소 ${SBOM_COMPONENTS}, 하한 ${MIN_PACKAGES}) scan=${SCAN_FILE}"
echo "findings_total=${TOTAL_FINDINGS} 차단후보(${BLOCK_SEVERITIES}, only-fixed=${BLOCK_ONLY_FIXED})=${CANDIDATE_COUNT} 판정단위[ID+패키지]=${CANDIDATE_KEY_COUNT} allowlist_전체=${allow_entry_count} 이_kind_적용=${allow_applied} 미등재=${BLOCKING_COUNT}"

if [ "$failures" -gt 0 ]; then
  echo "== 취약점 게이트 실패: ${failures}건 ==" >&2
  exit 1
fi

echo "== 취약점 게이트 통과 =="
