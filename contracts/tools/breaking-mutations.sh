#!/usr/bin/env bash
# M2/2D — breaking mutation 증명(S-3, scope.md 「이 slice 가 하는 일」 ①②).
#
# 승인된 `.proto`(정책 데이터의 `approved.tag`)를 **임시 디렉터리**에서 mutation 집합
# (정책 데이터 `breaking.mutations`) 하나씩 적용하고, `buf breaking`이 **전건** 실패함을
# 증명한다. 양성 대조(호환 변경 넷 — 필드·enum 값·RPC·메시지 추가)는 같은 스크립트가
# 통과함을 증명한다. mutation 이 담긴 `.proto` 는 저장소에 커밋하지 않는다(D-2D-5) —
# 전부 `mktemp -d`로 만들고 종료 시 지운다.
#
# 종료 코드는 `data-extract.md` §6 스윕 규약과 같다 — 0 정상(mutation 전건이 잡히고 규칙
# 표류 없이 양성 대조도 기대대로 통과) · 1 위반(잡히지 않은 mutation·규칙 표류·양성
# 대조가 예기치 않게 breaking 으로 잡힘) · 2 도구 오류(buf 부재·정책 키 부재/값 이상·
# buf 버전 불일치·git 태그 없음·컴파일 오류·판정 근거 부재 등 환경/스크립트 문제).
#
# **verifier r3 F-18·F-19 — "구멍을 하나씩 막지 않고 양성 단언 구조로"**. 판정에 필요한
# 모든 값(정책 키 존재+비어있지 않음, `breaking.mutations.min`이 1 이상의 정수, buf 버전
# 일치, `caught`이면 진짜 규칙 이름 하나 이상 확보)이 **전부 확인될 때만** 다음 단계로
# 간다 — 하나라도 확인되지 않으면 exit 2. `policy_value`는 실패를 `exit`이 아니라
# `return 1`로 낸다(호출부가 `VAR=$(policy_value ..) || exit 2`로 받아야 부모 셸이
# 실제로 멈춘다 — 명령 치환 안의 `exit`은 서브셸만 끝낸다는 것이 F-18 의 근본 원인이었다).
# `breaking.mutations` 는 **키가 있는 한** 빈 값이 허용된 유일한 항목이다(우회 후보 (1) —
# 빈 집합은 뒤의 최소 크기 단언이 잡아야 하는 정상 실패 경로).
#
# **verifier r2 F-14 로 정정된 사실** — `buf breaking`의 exit code 는 **위반과 컴파일
# 오류를 가르지 않는다**(실측: `.proto` 구문이 깨진 사본에도 exit 100 이 난다). exit 100
# 은 "caught 후보"일 뿐이고 `--error-format=json`의 `"type"` 필드를 봐야 한다 — 컴파일
# 오류는 전부 `breaking.compile-error.type`(정책 데이터) 값이고, 진짜 breaking 규칙은
# `FIELD_NO_DELETE` 같은 규칙 이름이다.
#
# 결과는 `contracts/testdata/breaking/expected.tsv`(커밋된 **기대값** 표, mutation →
# 기대 규칙 이름)와 대조한다(verifier r3 F-20) — 매 실행 덮어쓰지 않는다. 규칙 이름이
# 다른 규칙으로 표류하면 exit 1. 표를 의도적으로 새로 세우거나 갱신할 때만
# `./tools/breaking-mutations.sh --update`로 명시적으로 다시 쓴다.
#
# **`set -u`(nounset)를 쓰지 않는다** — 실측(2026-09-07): macOS 기본 `/bin/bash`가 3.2.57
# (GPL 라이선스 사유로 오래 고정)이고, 이 버전은 빈 배열의 `"${ARR[@]}"` 확장을 "unbound
# variable"로 잘못 취급하는 알려진 버그가 있다(bash 4.4+ 에서 수정, 그리고 이 bash 는
# `declare -A` 연관 배열도 없어 기대값 조회를 `awk` 로 한다). 빈 값 검사는 명시
# 조건문으로 하므로 `-u` 없이도 안전하다.
set -o pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
CONTRACTS_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
REPO_ROOT=$(cd "$CONTRACTS_DIR/.." && pwd)
POLICY_FILE="$REPO_ROOT/config/quality/contract-policy.properties"
EXPECTED_TSV="$CONTRACTS_DIR/testdata/breaking/expected.tsv"

UPDATE_MODE=0
if [[ "${1:-}" == "--update" ]]; then
    UPDATE_MODE=1
fi

# 값을 stdout 으로, 실패는 return 1 로 낸다 — `exit`을 여기서 쓰면 명령 치환의 서브셸만
# 끝나고 부모 스크립트는 빈 값을 들고 계속 간다(verifier r3 F-18 의 근본 원인).
policy_value() {
    local key="$1"
    local line
    line=$(grep -E "^${key}=" "$POLICY_FILE" | head -1)
    if [[ -z "$line" ]]; then
        echo "정책 키 '$key' 가 '$POLICY_FILE' 에 없다" >&2
        return 1
    fi
    echo "${line#*=}"
}

# 호출부가 `VAR=$(require_policy_value key)` 형태로 쓰면, 이 함수 안의 `exit 2`는 여전히
# 그 명령 치환의 서브셸만 끝낸다 — 그래서 반드시 `|| exit 2`로 부모에서 받는다(아래
# 호출부 참고). 이 함수 자체는 "키 존재 + 값 비어있지 않음"까지 확인해 그 뒤 어디서든
# 부모가 멈추게 return 1 한다.
require_policy_value() {
    local key="$1"
    local value
    value=$(policy_value "$key") || return 1
    if [[ -z "$value" ]]; then
        echo "정책 키 '$key' 의 값이 비어 있다 — 도구 오류" >&2
        return 1
    fi
    echo "$value"
}

require_positive_int() {
    local key="$1" value="$2"
    if ! [[ "$value" =~ ^[0-9]+$ ]] || [[ "$value" -lt 1 ]]; then
        echo "정책 키 '$key' 의 값 '$value' 이 1 이상의 정수가 아니다 — 도구 오류" >&2
        exit 2
    fi
}

# verifier r1 F-9 — `sed -i ''`는 BSD sed(macOS) 전용 문법이다. GNU sed(Linux, 미래 CI)는
# 같은 자리에서 다음 인자를 in-place 접미사로 먹어 버린다. 임시 파일 경유로 두 구현
# 모두에서 동작하게 한다.
sed_inplace() {
    local pattern="$1" file="$2"
    local tmp
    tmp=$(mktemp)
    sed "$pattern" "$file" >"$tmp" && mv "$tmp" "$file"
}

if ! command -v buf >/dev/null 2>&1; then
    echo "buf 가 PATH 에 없다 — 도구 오류" >&2
    exit 2
fi

APPROVED_TAG=$(require_policy_value "approved.tag") || exit 2
MUTATIONS_MIN=$(require_policy_value "breaking.mutations.min") || exit 2
require_positive_int "breaking.mutations.min" "$MUTATIONS_MIN"
COMPILE_ERROR_TYPE=$(require_policy_value "breaking.compile-error.type") || exit 2
BUF_VERSION_POLICY=$(require_policy_value "tool.buf.version") || exit 2

# `breaking.mutations`는 키가 있는 한 빈 값이 허용된 유일한 항목이다(우회 후보 (1) —
# 빈 집합은 뒤의 최소 크기 단언이 "0 < min"으로 정상적으로 실패해야 한다).
MUTATIONS_RAW=$(policy_value "breaking.mutations") || exit 2
MUTATIONS=()
if [[ -n "$MUTATIONS_RAW" ]]; then
    IFS=',' read -r -a MUTATIONS <<<"$MUTATIONS_RAW"
fi

# verifier r3 F-19 — S-3 자신도 buf 버전을 정책과 대조한다. `contractGate`(Kotlin)가 이미
# 같은 정책 값(`tool.buf.version`)을 실측 대조하지만, 그건 `check` 경로고 S-3 는 독립
# 실행이라 다른 buf 버전으로 S-3 만 돌리는 경로가 따로 열려 있었다 — 같은 정책 값을 여기
# 서도 읽을 뿐 리터럴을 중복 정의하지 않는다.
BUF_VERSION_ACTUAL=$(buf --version | tr -d '[:space:]')
if [[ "$BUF_VERSION_ACTUAL" != "$BUF_VERSION_POLICY" ]]; then
    echo "buf 버전이 정책과 다르다 — 실측 '$BUF_VERSION_ACTUAL', 정책 '$BUF_VERSION_POLICY'" >&2
    exit 2
fi

if ! git -C "$REPO_ROOT" rev-parse "$APPROVED_TAG" >/dev/null 2>&1; then
    echo "승인 태그 '$APPROVED_TAG' 가 저장소에 없다 — 도구 오류" >&2
    exit 2
fi

# verifier r3 F-20 — 기대값 표가 없으면(최초 실행) 비교할 대상이 없다 — `--update`로
# 명시적으로 먼저 세운다. 매 실행이 조용히 새 "기대값"을 만들어내지 않는다.
if [[ "$UPDATE_MODE" -eq 0 && ! -f "$EXPECTED_TSV" ]]; then
    echo "$EXPECTED_TSV 가 없다 — 최초 기준표는 '$0 --update' 로 만든다" >&2
    exit 2
fi

AGAINST="$REPO_ROOT/.git#tag=${APPROVED_TAG},subdir=contracts"
RESULT_ROWS=()
OVERALL_STATUS=0

# ---- 시나리오 준비 — 승인된 contracts 를 임시 디렉터리에 복사한다 ----
fresh_scratch() {
    local scratch
    scratch=$(mktemp -d "${TMPDIR:-/tmp}/bidvector-breaking-XXXXXX")
    cp -R "$CONTRACTS_DIR" "$scratch/contracts"
    echo "$scratch"
}

run_buf_breaking() {
    local scratch_contracts="$1"
    buf breaking "$scratch_contracts" --against "$AGAINST" --error-format=json \
        >/tmp/bidvector-breaking-out.$$ 2>&1
    local code=$?
    cat /tmp/bidvector-breaking-out.$$
    rm -f /tmp/bidvector-breaking-out.$$
    return $code
}

# `--error-format=json`의 각 finding 줄에서 `"type"` 값만 뽑는다.
finding_types() {
    printf '%s\n' "$1" | grep -o '"type":"[A-Za-z_]*"' | sed -E 's/"type":"([A-Za-z_]*)"/\1/'
}

# 컴파일 오류(정책 `breaking.compile-error.type`) 가 하나라도 섞여 있으면 위반이 아니라
# mutation 적용이 구문을 깬 것이다 — genuine breaking 규칙과 구분한다.
has_compile_error() {
    finding_types "$1" | grep -qx "$COMPILE_ERROR_TYPE"
}

# evidence 열용 — 첫 finding 의 규칙 이름(컴파일 오류가 아닌 경우의 대표값). `--error-
# format`이 무효화되는 등으로 `"type"`을 하나도 못 뽑으면 빈 문자열을 낸다 — 그 경우를
# "규칙을 못 얻었다"는 신호로 호출부가 양성 검사한다(verifier r3 F-19).
first_rule_type() {
    finding_types "$1" | grep -vx "$COMPILE_ERROR_TYPE" | head -1
}

# verifier r3 F-20 — 커밋된 `expected.tsv`에서 이 mutation 의 기대 규칙 이름을 읽는다.
# bash 3.2(macOS 기본)에는 연관 배열이 없어 매번 `awk`로 조회한다(mutation 수가 적어
# 성능 문제 없음). 표에 그 mutation 행이 아직 없으면(새 mutation 추가 직후) 빈 문자열 —
# 비교 대상이 없다는 뜻이지 실패가 아니다.
expected_rule_type_for() {
    local mutation="$1"
    [[ -f "$EXPECTED_TSV" ]] || return 0
    awk -F'\t' -v m="$mutation" '$1==m {print $4}' "$EXPECTED_TSV" | head -1
}

# ---- mutation 적용 함수 — 각각 승인된 스키마의 알려진 좌표를 바꾼다 ----
COMMON="proto/bidvector/ml/v1/common.proto"
PREDICTION="proto/bidvector/ml/v1/prediction.proto"
TRAINING="proto/bidvector/ml/v1/training.proto"

apply_mutation() {
    local scratch="$1" mutation="$2"
    local c="$scratch/contracts/$COMMON"
    local p="$scratch/contracts/$PREDICTION"
    local t="$scratch/contracts/$TRAINING"
    case "$mutation" in
    field-delete)
        sed_inplace '/Currency currency = 2;/d' "$c"
        ;;
    field-number-change)
        sed_inplace 's/Currency currency = 2;/Currency currency = 9;/' "$c"
        ;;
    type-change)
        sed_inplace 's/int64 amount_won = 1;/string amount_won = 1;/' "$c"
        ;;
    enum-value-delete)
        sed_inplace '/CURRENCY_KRW = 1;/d' "$c"
        ;;
    enum-value-number-change)
        sed_inplace 's/CURRENCY_KRW = 1;/CURRENCY_KRW = 7;/' "$c"
        ;;
    oneof-field-remove)
        sed_inplace '/ExactRelease exact_release = 2;/d' "$c"
        ;;
    rpc-delete)
        sed_inplace '/rpc GetModelMetadata/d' "$p"
        ;;
    rpc-streaming-change)
        sed_inplace \
            's/rpc GetModelMetadata(GetModelMetadataRequest) returns (GetModelMetadataResponse);/rpc GetModelMetadata(GetModelMetadataRequest) returns (stream GetModelMetadataResponse);/' \
            "$p"
        ;;
    package-rename)
        for f in "$c" "$scratch/contracts/proto/bidvector/ml/v1/error.proto" \
            "$scratch/contracts/proto/bidvector/ml/v1/features.proto" "$p" "$t"; do
            sed_inplace 's/package bidvector.ml.v1;/package bidvector.ml.v2;/' "$f"
        done
        ;;
    optional-removal)
        sed_inplace 's/optional string requested_release_id = 5;/string requested_release_id = 5;/' "$t"
        ;;
    *)
        echo "알 수 없는 mutation '$mutation'" >&2
        return 2
        ;;
    esac
}

# ---- reserved-number-reuse 는 승인 스키마에 reserved 선언이 없어 별도 합성 시나리오로 증명한다 ----
run_reserved_number_reuse() {
    local old new
    old=$(mktemp -d "${TMPDIR:-/tmp}/bidvector-reserved-old-XXXXXX")
    new=$(mktemp -d "${TMPDIR:-/tmp}/bidvector-reserved-new-XXXXXX")
    mkdir -p "$old/proto/bidvector/ml/v1/scratch" "$new/proto/bidvector/ml/v1/scratch"
    cat >"$old/buf.yaml" <<'YAML'
version: v2
modules:
  - path: proto
YAML
    cp "$old/buf.yaml" "$new/buf.yaml"
    cat >"$old/proto/bidvector/ml/v1/scratch/reserved.proto" <<'PROTO'
syntax = "proto3";
package bidvector.ml.v1.scratch;
message Sample {
  int64 amount_won = 1;
  reserved 2;
}
PROTO
    cat >"$new/proto/bidvector/ml/v1/scratch/reserved.proto" <<'PROTO'
syntax = "proto3";
package bidvector.ml.v1.scratch;
message Sample {
  int64 amount_won = 1;
  string reused_field = 2;
}
PROTO
    buf breaking "$new" --against "$old" --error-format=json >/tmp/bidvector-breaking-out.$$ 2>&1
    local code=$?
    cat /tmp/bidvector-breaking-out.$$
    rm -f /tmp/bidvector-breaking-out.$$
    rm -rf "$old" "$new"
    return $code
}

record() {
    local mutation="$1" outcome="$2" exit_code="$3" rule_type="${4:-}"
    RESULT_ROWS+=("$mutation	$outcome	$exit_code	$rule_type")
}

echo "== breaking mutation 증명 — 승인 태그 $APPROVED_TAG 대비 =="
for mutation in "${MUTATIONS[@]}"; do
    if [[ "$mutation" == "reserved-number-reuse" ]]; then
        output=$(run_reserved_number_reuse)
        code=$?
    else
        scratch=$(fresh_scratch)
        apply_mutation "$scratch" "$mutation"
        rc=$?
        if [[ $rc -eq 2 ]]; then
            rm -rf "$scratch"
            exit 2
        fi
        output=$(run_buf_breaking "$scratch/contracts")
        code=$?
        rm -rf "$scratch"
    fi

    if [[ $code -eq 100 ]]; then
        rule_type=$(first_rule_type "$output")
        # verifier r3 F-19 — 양성 단언: exit 100 이어도 컴파일 오류가 하나도 없고 진짜
        # 규칙 이름을 하나 이상 얻었을 때만 caught 다. `"type"`을 하나도 못 뽑는 경우
        # (형식 변경·플래그 무효화 등)도 COMPILE 만 있는 경우(F-14)도 똑같이 "판정 근거
        # 부재"로 취급해 도구 오류로 멈춘다 — 오탐이 미탐보다 낫다.
        if has_compile_error "$output" || [[ -z "$rule_type" ]]; then
            echo "buf 도구 오류(exit=100 이지만 유효한 breaking 규칙을 확인하지 못했다) — mutation '$mutation'" >&2
            echo "$output" >&2
            exit 2
        fi
        if [[ "$UPDATE_MODE" -eq 0 ]]; then
            expected_rule=$(expected_rule_type_for "$mutation")
            if [[ -n "$expected_rule" && "$expected_rule" != "$rule_type" ]]; then
                echo "[규칙 표류] $mutation — 기대 '$expected_rule', 실측 '$rule_type'" >&2
                record "$mutation" "rule-drift" "$code" "$rule_type"
                OVERALL_STATUS=1
                continue
            fi
        fi
        echo "[잡힘] $mutation (exit=$code, rule=$rule_type)"
        record "$mutation" "caught" "$code" "$rule_type"
    elif [[ $code -eq 0 ]]; then
        echo "[미검출] $mutation — buf breaking 이 이 mutation 을 통과시켰다"
        echo "$output"
        record "$mutation" "not-caught" "$code"
        OVERALL_STATUS=1
    else
        echo "buf 도구 오류(exit=$code, 위반도 무위반도 아니다) — mutation '$mutation'" >&2
        echo "$output" >&2
        exit 2
    fi
done

# ---- 양성 대조 — 호환 변경 넷은 buf breaking 을 통과해야 한다 ----
echo "== 양성 대조 — 호환 변경 넷(필드·enum 값·RPC·메시지 추가) =="
positive_scratch=$(fresh_scratch)
pc="$positive_scratch/contracts/$COMMON"
pp="$positive_scratch/contracts/$PREDICTION"
sed_inplace 's/AmountProvenanceKind provenance = 5;/AmountProvenanceKind provenance = 5;\n  string new_compatible_field = 6;/' "$pc"
sed_inplace 's/CURRENCY_KRW = 1;/CURRENCY_KRW = 1;\n  CURRENCY_USD = 2;/' "$pc"
cat >>"$pc" <<'PROTO'

message NewCompatibleMessage {
  string value = 1;
}
PROTO
cat >>"$pp" <<'PROTO'

message PingRequest {
  RequestEnvelope envelope = 1;
}

message PingResponse {
  RequestEnvelope envelope = 1;
}
PROTO
sed_inplace \
    's/rpc GetModelMetadata(GetModelMetadataRequest) returns (GetModelMetadataResponse);/rpc GetModelMetadata(GetModelMetadataRequest) returns (GetModelMetadataResponse);\n  rpc Ping(PingRequest) returns (PingResponse);/' \
    "$pp"
positive_output=$(run_buf_breaking "$positive_scratch/contracts")
positive_code=$?
rm -rf "$positive_scratch"
if [[ $positive_code -eq 0 ]]; then
    echo "[통과] 호환 변경 넷은 breaking 으로 잡히지 않는다(기대대로)"
    record "compatible-additions(positive-control)" "passed" "$positive_code"
elif [[ $positive_code -eq 100 ]] && has_compile_error "$positive_output"; then
    echo "buf 도구 오류(exit=100 이지만 컴파일 오류) — 양성 대조 mutation 자체가 구문을 깼다" >&2
    echo "$positive_output" >&2
    exit 2
elif [[ $positive_code -eq 100 ]]; then
    echo "[예기치 않은 실패] 호환 변경이 breaking 으로 잡혔다:"
    echo "$positive_output"
    record "compatible-additions(positive-control)" "unexpected-failure" "$positive_code" "$(first_rule_type "$positive_output")"
    OVERALL_STATUS=1
else
    echo "buf 도구 오류(exit=$positive_code) — 양성 대조" >&2
    echo "$positive_output" >&2
    exit 2
fi

# ---- 기대값 표 갱신 — `--update`로 명시했을 때만 쓴다(verifier r3 F-20) ----
if [[ "$UPDATE_MODE" -eq 1 ]]; then
    mkdir -p "$(dirname "$EXPECTED_TSV")"
    {
        echo -e "mutation\toutcome\texit_code\trule_type"
        for row in "${RESULT_ROWS[@]}"; do
            echo -e "$row"
        done
    } >"$EXPECTED_TSV"
    echo "== $EXPECTED_TSV 갱신됨(--update) =="
fi

CAUGHT_COUNT=$(printf '%s\n' "${RESULT_ROWS[@]}" | awk -F'\t' '$2=="caught"' | wc -l | tr -d ' ')
echo "== 요약: ${CAUGHT_COUNT}/${#MUTATIONS[@]} mutation 잡힘, 최소 요구 $MUTATIONS_MIN =="

if [[ "$CAUGHT_COUNT" -lt "$MUTATIONS_MIN" ]]; then
    echo "잡힌 mutation 수가 정책 최소치(breaking.mutations.min=$MUTATIONS_MIN) 미만이다" >&2
    OVERALL_STATUS=1
fi

exit $OVERALL_STATUS
