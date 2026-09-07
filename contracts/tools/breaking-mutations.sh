#!/usr/bin/env bash
# M2/2D — breaking mutation 증명(S-3, scope.md 「이 slice 가 하는 일」 ①②).
# 하네스 `test-discovery-guard` B-1~B-4 — 2D 알려진 제한 11~13(F-21·F-22·F-23) 을 닫는다.
#
# 승인된 `.proto`(정책 데이터의 `approved.tag`)를 **임시 디렉터리**에서 mutation 집합
# (정책 데이터 `breaking.mutations`) 하나씩 적용하고, `buf breaking`이 **전건** 실패함을
# 증명한다. 양성 대조(호환 변경 넷 — 필드·enum 값·RPC·메시지 추가)는 같은 스크립트가
# 통과함을 증명한다. mutation 이 담긴 `.proto` 는 저장소에 커밋하지 않는다(D-2D-5) —
# 전부 `mktemp -d`로 만들고 종료 시 지운다.
#
# 종료 코드는 `data-extract.md` §6 스윕 규약과 같다 — 0 정상(mutation 전건이 잡히고 규칙
# 표류 없이 양성 대조도 기대대로 통과) · 1 위반(잡히지 않은 mutation·규칙 표류·양성
# 대조가 예기치 않게 breaking 으로 잡힘·`--update` 표류 미수용) · 2 도구 오류(buf 부재·
# 정책 키 부재/값 이상·buf 버전 불일치·git 태그 없음·컴파일 오류·판정 근거 부재·
# `expected.tsv` 불완전/표류한 표 등 환경/스크립트 문제).
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
# **verifier r4 F-21(하네스 B-1 로 닫힘)** — 표에 행이 없는 mutation 이 대조를 건너뛰고
# "규칙 이름이 비어 있지 않으면 caught" 로 돌아가던 관용을 없앴다. 루프 전에
# `assert_expected_tsv_complete`가 `breaking.mutations`(+양성 대조) 목록과 표의 mutation
# 열을 **양방향**으로 대조한다 — 표에 없는 mutation, 표에만 있고 목록에 없는 행(표류한 표)
# 둘 다 exit 2(도구 오류). 대조를 통과한 뒤에는 `expected_rule_type_for`의 결과가 항상
# 비어 있지 않으므로 루프의 규칙 표류 판정에서 "빈 값이면 건너뛴다" 가드를 없앴다.
#
# **F-22(하네스 B-2 로 닫힘)** — `--update`는 이제 갱신 전/후 표를 **diff**하고, 차이가
# 있으면(표류) 파일은 갱신하되 `exit 1`로 끝난다. `--update --accept-drift`만 그 표류를
# 수용해 exit 0. 표가 없던 최초 실행은 비교 대상이 없어 exit 0.
#
# **F-23(하네스 B-3 로 닫힘)** — `has_compile_error`/`first_rule_type`이 더는
# `finding_types "$1" | grep -qx ...`/`... | head -1` 로 파이프에 태우지 않는다.
# `finding_types`의 결과를 변수에 담고 판정은 bash 패턴 매칭(`[[ $'\n'"$types"$'\n' ==
# *$'\n'"$TYPE"$'\n'* ]]`)으로 한다 — 대용량 출력에서 `grep -q`의 조기 종료가
# `pipefail`과 만나 상류를 SIGPIPE 로 죽이고 "컴파일 오류 없음"으로 오판하던 표면이
# 구조적으로 없다.
#
# **B-4 — 함수를 source 가능하게 한다.** 실행 로직 전체를 `main()`에 담고 파일 하단의
# `if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then main "$@"; fi` 가드로 감싼다.
# `contracts/tools/breaking-mutations-selftest.sh`가 이 파일을 **source**해서
# `assert_expected_tsv_complete`·`write_expected_tsv`·`has_compile_error`를 buf 호출 없이
# 직접 실측한다. `EXPECTED_TSV`는 환경변수로 미리 설정돼 있으면 그 값을 우선한다(selftest
# 가 임시 표를 가리키는 방법 — 경로만 바뀔 뿐 표 내용 대조는 그대로 선다).
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
# B-4 — selftest 가 임시 표를 가리키도록 환경변수로 덮어쓸 수 있다. 값은 경로일 뿐이고
# 표 내용 대조(F-21·F-22)는 그대로 선다 — 우회 표면이 아니다.
EXPECTED_TSV="${EXPECTED_TSV:-$CONTRACTS_DIR/testdata/breaking/expected.tsv}"
POSITIVE_CONTROL_LABEL="compatible-additions(positive-control)"

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

# `--error-format=json`의 각 finding 줄에서 `"type"` 값만 뽑아 개행 구분 문자열로 낸다.
# 이 안의 파이프는 F-23 이 막으려는 파이프가 아니다 — 결과는 항상 `$(...)`로 전량
# 캡처되고, 뒤에서 `grep -q`/`head -1` 처럼 조기 종료하며 읽는 소비자가 없다.
finding_types() {
    printf '%s\n' "$1" | grep -o '"type":"[A-Za-z_]*"' | sed -E 's/"type":"([A-Za-z_]*)"/\1/'
}

# 컴파일 오류(정책 `breaking.compile-error.type`) 가 하나라도 섞여 있으면 위반이 아니라
# mutation 적용이 구문을 깬 것이다 — genuine breaking 규칙과 구분한다.
#
# **F-23(하네스 B-3)** — `finding_types` 결과를 변수 `types`에 담고 판정은 개행으로
# 감싼 부분 문자열 매칭이다. 파이프도 조기 종료(`grep -q`)도 없어 대용량 출력에서
# `pipefail`이 상류를 SIGPIPE 로 죽이는 표면이 없다.
has_compile_error() {
    local types
    types=$(finding_types "$1")
    [[ $'\n'"$types"$'\n' == *$'\n'"$COMPILE_ERROR_TYPE"$'\n'* ]]
}

# evidence 열용 — 첫 finding 의 규칙 이름(컴파일 오류가 아닌 경우의 대표값). `"type"`을
# 하나도 못 뽑으면 빈 문자열을 낸다 — 그 경우를 "규칙을 못 얻었다"는 신호로 호출부가
# 양성 검사한다(verifier r3 F-19).
#
# **F-23(하네스 B-3)** — `finding_types`의 같은 변수를 줄 단위 here-string(`<<<`)으로
# 읽는다. here-string 은 파이프가 아니다(생산자 프로세스가 따로 없다) — SIGPIPE 표면이
# 구조적으로 없다.
first_rule_type() {
    local types t
    types=$(finding_types "$1")
    while IFS= read -r t; do
        if [[ -n "$t" && "$t" != "$COMPILE_ERROR_TYPE" ]]; then
            printf '%s\n' "$t"
            return 0
        fi
    done <<<"$types"
    return 0
}

# verifier r3 F-20 — 커밋된 `expected.tsv`에서 이 mutation 의 기대 규칙 이름을 읽는다.
# bash 3.2(macOS 기본)에는 연관 배열이 없어 매번 `awk`로 조회한다(mutation 수가 적어
# 성능 문제 없음). **B-1 로 「행이 없으면 빈 문자열」의 소비처가 사라졌다** —
# `assert_expected_tsv_complete`가 루프 전에 완전성을 이미 확인하므로, 이 함수가 빈
# 문자열을 내는 것은 더 이상 "정상적인 비교 생략"이 아니라 그 자체가 버그 신호다.
expected_rule_type_for() {
    local mutation="$1"
    [[ -f "$EXPECTED_TSV" ]] || return 0
    awk -F'\t' -v m="$mutation" '$1==m {print $4}' "$EXPECTED_TSV" | head -1
}

# ---- verifier r4 F-21(하네스 B-1) — expected.tsv 완전성 양방향 단언 ----
# `breaking.mutations`(+양성 대조 라벨) 각각이 표에 행을 가져야 하고(없으면 표가
# 불완전), 표의 각 행도 그 목록 안에 있어야 한다(표류한 표 — 목록에서 mutation 을
# 뺐는데 표 행만 남는 경우). 둘 중 하나라도 어긋나면 return 2(도구 오류) — 호출부가
# `|| exit 2`로 받는다.
assert_expected_tsv_complete() {
    [[ -f "$EXPECTED_TSV" ]] || return 0
    local expected_mutations known mutation
    expected_mutations=$(awk -F'\t' 'NR>1 && NF>0 {print $1}' "$EXPECTED_TSV")
    known=$'\n'"$(printf '%s\n' "${MUTATIONS[@]}" "$POSITIVE_CONTROL_LABEL")"$'\n'
    while IFS= read -r mutation; do
        [[ -z "$mutation" ]] && continue
        if [[ "$known" != *$'\n'"$mutation"$'\n'* ]]; then
            echo "'$EXPECTED_TSV' 에 목록에 없는 mutation 행이 있다(표류한 표) — '$mutation'" >&2
            return 2
        fi
    done <<<"$expected_mutations"
    for mutation in "${MUTATIONS[@]}" "$POSITIVE_CONTROL_LABEL"; do
        if [[ $'\n'"$expected_mutations"$'\n' != *$'\n'"$mutation"$'\n'* ]]; then
            echo "'$EXPECTED_TSV' 에 mutation '$mutation' 행이 없다 — 표가 불완전하다" >&2
            return 2
        fi
    done
    return 0
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

# ---- F-22(하네스 B-2) — 기대값 표 갱신. 갱신 전/후를 diff 해 표류를 드러낸다 ----
# 반환: 0(갱신할 이전 표가 없거나, 표류가 없거나, `--accept-drift`로 수용됨)
#       1(표류가 있고 `--accept-drift` 없음 — 파일은 그래도 갱신됐다)
# `RESULT_ROWS`·`ACCEPT_DRIFT`는 호출자 스코프의 전역 변수를 그대로 쓴다(B-4 — selftest
# 가 이 함수만 source 해서 두 변수를 직접 채우고 호출할 수 있게 하기 위함).
write_expected_tsv() {
    local old_content="" new_content row
    [[ -f "$EXPECTED_TSV" ]] && old_content=$(cat "$EXPECTED_TSV")
    mkdir -p "$(dirname "$EXPECTED_TSV")"
    {
        echo -e "mutation\toutcome\texit_code\trule_type"
        for row in "${RESULT_ROWS[@]}"; do
            echo -e "$row"
        done
    } >"$EXPECTED_TSV"
    echo "== $EXPECTED_TSV 갱신됨(--update) =="
    if [[ -z "$old_content" ]]; then
        return 0
    fi
    new_content=$(cat "$EXPECTED_TSV")
    if [[ "$old_content" == "$new_content" ]]; then
        return 0
    fi
    echo "== [규칙 표류] 갱신 전/후 표가 다르다 ==" >&2
    diff <(printf '%s\n' "$old_content") <(printf '%s\n' "$new_content") >&2
    if [[ "${ACCEPT_DRIFT:-0}" -eq 1 ]]; then
        echo "== --accept-drift 로 표류를 수용했다 =="
        return 0
    fi
    echo "표류를 검토한 뒤 재실행 시 '--update --accept-drift' 로 명시한다" >&2
    return 1
}

main() {
    UPDATE_MODE=0
    ACCEPT_DRIFT=0
    local arg
    for arg in "$@"; do
        case "$arg" in
        --update) UPDATE_MODE=1 ;;
        --accept-drift) ACCEPT_DRIFT=1 ;;
        esac
    done

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

    # verifier r3 F-19 — S-3 자신도 buf 버전을 정책과 대조한다.
    BUF_VERSION_ACTUAL=$(buf --version | tr -d '[:space:]')
    if [[ "$BUF_VERSION_ACTUAL" != "$BUF_VERSION_POLICY" ]]; then
        echo "buf 버전이 정책과 다르다 — 실측 '$BUF_VERSION_ACTUAL', 정책 '$BUF_VERSION_POLICY'" >&2
        exit 2
    fi

    if ! git -C "$REPO_ROOT" rev-parse "$APPROVED_TAG" >/dev/null 2>&1; then
        echo "승인 태그 '$APPROVED_TAG' 가 저장소에 없다 — 도구 오류" >&2
        exit 2
    fi

    # verifier r3 F-20 — 기대값 표가 없으면(최초 실행) 비교할 대상이 없다.
    if [[ "$UPDATE_MODE" -eq 0 && ! -f "$EXPECTED_TSV" ]]; then
        echo "$EXPECTED_TSV 가 없다 — 최초 기준표는 '$0 --update' 로 만든다" >&2
        exit 2
    fi

    # F-21(하네스 B-1) — 루프 전 완전성 단언(비-update 모드만 — update 는 이 실행 결과로
    # 표를 새로 쓰는 쪽이라 사전 대조 대상이 아니다).
    if [[ "$UPDATE_MODE" -eq 0 ]]; then
        assert_expected_tsv_complete || exit 2
    fi

    AGAINST="$REPO_ROOT/.git#tag=${APPROVED_TAG},subdir=contracts"
    RESULT_ROWS=()
    OVERALL_STATUS=0

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
            # 규칙 이름을 하나 이상 얻었을 때만 caught 다.
            if has_compile_error "$output" || [[ -z "$rule_type" ]]; then
                echo "buf 도구 오류(exit=100 이지만 유효한 breaking 규칙을 확인하지 못했다) — mutation '$mutation'" >&2
                echo "$output" >&2
                exit 2
            fi
            if [[ "$UPDATE_MODE" -eq 0 ]]; then
                expected_rule=$(expected_rule_type_for "$mutation")
                # B-1 — 「빈 값이면 대조를 건너뛴다」관용을 없앴다. 완전성 단언이 앞서
                # 통과했으므로 이 자리에서 빈 값은 이미 나올 수 없다.
                if [[ "$expected_rule" != "$rule_type" ]]; then
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
        record "$POSITIVE_CONTROL_LABEL" "passed" "$positive_code"
    elif [[ $positive_code -eq 100 ]] && has_compile_error "$positive_output"; then
        echo "buf 도구 오류(exit=100 이지만 컴파일 오류) — 양성 대조 mutation 자체가 구문을 깼다" >&2
        echo "$positive_output" >&2
        exit 2
    elif [[ $positive_code -eq 100 ]]; then
        echo "[예기치 않은 실패] 호환 변경이 breaking 으로 잡혔다:"
        echo "$positive_output"
        record "$POSITIVE_CONTROL_LABEL" "unexpected-failure" "$positive_code" "$(first_rule_type "$positive_output")"
        OVERALL_STATUS=1
    else
        echo "buf 도구 오류(exit=$positive_code) — 양성 대조" >&2
        echo "$positive_output" >&2
        exit 2
    fi

    # ---- F-22(하네스 B-2) — 기대값 표 갱신은 `--update`로 명시했을 때만 ----
    if [[ "$UPDATE_MODE" -eq 1 ]]; then
        write_expected_tsv || OVERALL_STATUS=1
    fi

    CAUGHT_COUNT=$(printf '%s\n' "${RESULT_ROWS[@]}" | awk -F'\t' '$2=="caught"' | wc -l | tr -d ' ')
    echo "== 요약: ${CAUGHT_COUNT}/${#MUTATIONS[@]} mutation 잡힘, 최소 요구 $MUTATIONS_MIN =="

    if [[ "$CAUGHT_COUNT" -lt "$MUTATIONS_MIN" ]]; then
        echo "잡힌 mutation 수가 정책 최소치(breaking.mutations.min=$MUTATIONS_MIN) 미만이다" >&2
        OVERALL_STATUS=1
    fi

    exit $OVERALL_STATUS
}

# B-4 — 직접 실행될 때만 돈다. source 될 때는 함수·상수 정의만 남긴다(selftest 가
# buf 호출 없이 `assert_expected_tsv_complete`·`write_expected_tsv`·`has_compile_error`
# 를 직접 부른다).
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
