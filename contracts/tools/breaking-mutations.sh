#!/usr/bin/env bash
# M2/2D — breaking mutation 증명(S-3, scope.md 「이 slice 가 하는 일」 ①②).
#
# 승인된 `.proto`(정책 데이터의 `approved.tag`)를 **임시 디렉터리**에서 mutation 집합
# (정책 데이터 `breaking.mutations`) 하나씩 적용하고, `buf breaking`이 **전건** 실패함을
# 증명한다. 양성 대조(호환 변경 넷 — 필드·enum 값·RPC·메시지 추가)는 같은 스크립트가
# 통과함을 증명한다. mutation 이 담긴 `.proto` 는 저장소에 커밋하지 않는다(D-2D-5) —
# 전부 `mktemp -d`로 만들고 종료 시 지운다.
#
# 종료 코드는 `data-extract.md` §6 스윕 규약과 같다 — 0 정상(mutation 전건이 잡히고 양성
# 대조도 기대대로 통과) · 1 위반(잡히지 않은 mutation, 또는 양성 대조가 예기치 않게
# breaking 으로 잡힘) · 2 도구 오류(buf 부재·정책 키 부재·git 태그 없음 등 환경 문제).
#
# 결과표는 `contracts/testdata/breaking/expected.tsv`에 쓴다(mutation → 잡힘/못잡힘/exit).
set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
CONTRACTS_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
REPO_ROOT=$(cd "$CONTRACTS_DIR/.." && pwd)
POLICY_FILE="$REPO_ROOT/config/quality/contract-policy.properties"
EXPECTED_TSV="$CONTRACTS_DIR/testdata/breaking/expected.tsv"

policy_value() {
    local key="$1"
    local line
    line=$(grep -E "^${key}=" "$POLICY_FILE" | head -1)
    if [[ -z "$line" ]]; then
        echo "정책 키 '$key' 가 '$POLICY_FILE' 에 없다" >&2
        exit 2
    fi
    echo "${line#*=}"
}

if ! command -v buf >/dev/null 2>&1; then
    echo "buf 가 PATH 에 없다 — 도구 오류" >&2
    exit 2
fi

APPROVED_TAG=$(policy_value "approved.tag")
MUTATIONS_RAW=$(policy_value "breaking.mutations")
MUTATIONS_MIN=$(policy_value "breaking.mutations.min")
IFS=',' read -r -a MUTATIONS <<<"$MUTATIONS_RAW"

if ! git -C "$REPO_ROOT" rev-parse "$APPROVED_TAG" >/dev/null 2>&1; then
    echo "승인 태그 '$APPROVED_TAG' 가 저장소에 없다 — 도구 오류" >&2
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
    buf breaking "$scratch_contracts" --against "$AGAINST" >/tmp/bidvector-breaking-out.$$ 2>&1
    local code=$?
    cat /tmp/bidvector-breaking-out.$$
    rm -f /tmp/bidvector-breaking-out.$$
    return $code
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
        sed -i '' '/Currency currency = 2;/d' "$c"
        ;;
    field-number-change)
        sed -i '' 's/Currency currency = 2;/Currency currency = 9;/' "$c"
        ;;
    type-change)
        sed -i '' 's/int64 amount_won = 1;/string amount_won = 1;/' "$c"
        ;;
    enum-value-delete)
        sed -i '' '/CURRENCY_KRW = 1;/d' "$c"
        ;;
    enum-value-number-change)
        sed -i '' 's/CURRENCY_KRW = 1;/CURRENCY_KRW = 7;/' "$c"
        ;;
    oneof-field-remove)
        sed -i '' '/ExactRelease exact_release = 2;/d' "$c"
        ;;
    rpc-delete)
        sed -i '' '/rpc GetModelMetadata/d' "$p"
        ;;
    rpc-streaming-change)
        sed -i '' \
            's/rpc GetModelMetadata(GetModelMetadataRequest) returns (GetModelMetadataResponse);/rpc GetModelMetadata(GetModelMetadataRequest) returns (stream GetModelMetadataResponse);/' \
            "$p"
        ;;
    package-rename)
        for f in "$c" "$scratch/contracts/proto/bidvector/ml/v1/error.proto" \
            "$scratch/contracts/proto/bidvector/ml/v1/features.proto" "$p" "$t"; do
            sed -i '' 's/package bidvector.ml.v1;/package bidvector.ml.v2;/' "$f"
        done
        ;;
    optional-removal)
        sed -i '' 's/optional string requested_release_id = 5;/string requested_release_id = 5;/' "$t"
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
    buf breaking "$new" --against "$old" >/tmp/bidvector-breaking-out.$$ 2>&1
    local code=$?
    cat /tmp/bidvector-breaking-out.$$
    rm -f /tmp/bidvector-breaking-out.$$
    rm -rf "$old" "$new"
    return $code
}

record() {
    local mutation="$1" outcome="$2" exit_code="$3"
    RESULT_ROWS+=("$mutation	$outcome	$exit_code")
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

    if [[ $code -ne 0 ]]; then
        echo "[잡힘] $mutation (exit=$code)"
        record "$mutation" "caught" "$code"
    else
        echo "[미검출] $mutation — buf breaking 이 이 mutation 을 통과시켰다"
        echo "$output"
        record "$mutation" "not-caught" "$code"
        OVERALL_STATUS=1
    fi
done

# ---- 양성 대조 — 호환 변경 넷은 buf breaking 을 통과해야 한다 ----
echo "== 양성 대조 — 호환 변경 넷(필드·enum 값·RPC·메시지 추가) =="
positive_scratch=$(fresh_scratch)
pc="$positive_scratch/contracts/$COMMON"
pp="$positive_scratch/contracts/$PREDICTION"
sed -i '' 's/AmountProvenanceKind provenance = 5;/AmountProvenanceKind provenance = 5;\n  string new_compatible_field = 6;/' "$pc"
sed -i '' 's/CURRENCY_KRW = 1;/CURRENCY_KRW = 1;\n  CURRENCY_USD = 2;/' "$pc"
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
sed -i '' \
    's/rpc GetModelMetadata(GetModelMetadataRequest) returns (GetModelMetadataResponse);/rpc GetModelMetadata(GetModelMetadataRequest) returns (GetModelMetadataResponse);\n  rpc Ping(PingRequest) returns (PingResponse);/' \
    "$pp"
positive_output=$(run_buf_breaking "$positive_scratch/contracts")
positive_code=$?
rm -rf "$positive_scratch"
if [[ $positive_code -eq 0 ]]; then
    echo "[통과] 호환 변경 넷은 breaking 으로 잡히지 않는다(기대대로)"
    record "compatible-additions(positive-control)" "passed" "$positive_code"
else
    echo "[예기치 않은 실패] 호환 변경이 breaking 으로 잡혔다:"
    echo "$positive_output"
    record "compatible-additions(positive-control)" "unexpected-failure" "$positive_code"
    OVERALL_STATUS=1
fi

# ---- 결과표 기록 ----
mkdir -p "$(dirname "$EXPECTED_TSV")"
{
    echo -e "mutation\toutcome\texit_code"
    for row in "${RESULT_ROWS[@]}"; do
        echo -e "$row"
    done
} >"$EXPECTED_TSV"

CAUGHT_COUNT=$(printf '%s\n' "${RESULT_ROWS[@]}" | awk -F'\t' '$2=="caught"' | wc -l | tr -d ' ')
echo "== 요약: ${CAUGHT_COUNT}/${#MUTATIONS[@]} mutation 잡힘, 최소 요구 $MUTATIONS_MIN =="

if [[ "$CAUGHT_COUNT" -lt "$MUTATIONS_MIN" ]]; then
    echo "잡힌 mutation 수가 정책 최소치(breaking.mutations.min=$MUTATIONS_MIN) 미만이다" >&2
    OVERALL_STATUS=1
fi

exit $OVERALL_STATUS
