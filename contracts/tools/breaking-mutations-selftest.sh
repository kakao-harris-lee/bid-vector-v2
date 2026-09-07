#!/usr/bin/env bash
# 하네스 `test-discovery-guard` B-4 — `breaking-mutations.sh` 를 buf 없이 회귀 실측한다.
#
# `breaking-mutations.sh` 를 **source** 해서(그 파일의 `BASH_SOURCE[0] == $0` 가드가
# `main`을 돌리지 않는다) F-21·F-22·F-23 이 고친 함수를 buf 호출 없이 직접 부른다.
# 각 시나리오의 **실제 반환값이 기대와 같은지**를 이 스크립트가 단언한다 — 개별 함수의
# 반환값 자체는 「테스트」가 아니라 「도구의 정상 동작」이므로, 이 selftest 가 그 동작을
# 검증하는 진짜 test 다.
#
# 종료 코드 — 0: 세 시나리오 전부 기대대로. 1: 하나라도 기대와 다르다(도구 회귀).
set -o pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
TARGET="$SCRIPT_DIR/breaking-mutations.sh"

FAILURES=()
assert_eq() {
    local label="$1" expected="$2" actual="$3"
    if [[ "$expected" == "$actual" ]]; then
        echo "[통과] $label — 기대 '$expected', 실측 '$actual'"
    else
        echo "[실패] $label — 기대 '$expected', 실측 '$actual'" >&2
        FAILURES+=("$label")
    fi
}

WORKDIR=$(mktemp -d "${TMPDIR:-/tmp}/bidvector-breaking-selftest-XXXXXX")
trap 'rm -rf "$WORKDIR"' EXIT

# ---- F-21 — expected.tsv 완전성 ----
echo "== F-21 — expected.tsv 완전성 =="
EXPECTED_TSV="$WORKDIR/expected.tsv"
{
    printf 'mutation\toutcome\texit_code\trule_type\n'
    printf 'field-delete\tcaught\t100\tFIELD_NO_DELETE\n'
    # `type-change` 행을 일부러 뺀다 — 표가 불완전하다.
    printf 'compatible-additions(positive-control)\tpassed\t0\t\n'
} >"$EXPECTED_TSV"

# `source`는 `main`을 돌리지 않는다(BASH_SOURCE[0] != $0, 가드).
# shellcheck disable=SC1090
source "$TARGET"

MUTATIONS=("field-delete" "type-change")
rc=0
assert_expected_tsv_complete || rc=$?
assert_eq "F-21 행 하나 빠진 표는 exit 2" "2" "$rc"

# 표류한 표(목록에 없는 행)도 같은 exit 2 — 위 표에 목록 밖 mutation 행을 더한다.
{
    cat "$EXPECTED_TSV"
    printf 'type-change\tcaught\t100\tFIELD_SAME_TYPE\n'
    printf 'rogue-mutation\tcaught\t100\tSOME_RULE\n'
} >"$WORKDIR/expected-drifted.tsv"
EXPECTED_TSV="$WORKDIR/expected-drifted.tsv"
rc=0
assert_expected_tsv_complete || rc=$?
assert_eq "F-21 표류한(목록 밖) 행이 있는 표는 exit 2" "2" "$rc"

# 완전한 표는 exit 0.
{
    printf 'mutation\toutcome\texit_code\trule_type\n'
    printf 'field-delete\tcaught\t100\tFIELD_NO_DELETE\n'
    printf 'type-change\tcaught\t100\tFIELD_SAME_TYPE\n'
    printf 'compatible-additions(positive-control)\tpassed\t0\t\n'
} >"$WORKDIR/expected-complete.tsv"
EXPECTED_TSV="$WORKDIR/expected-complete.tsv"
rc=0
assert_expected_tsv_complete || rc=$?
assert_eq "F-21 완전한 표는 exit 0" "0" "$rc"

# ---- F-22 — --update 의 표류 처리 ----
echo "== F-22 — --update 표류 =="
EXPECTED_TSV="$WORKDIR/expected-update.tsv"
{
    printf 'mutation\toutcome\texit_code\trule_type\n'
    printf 'field-delete\tcaught\t100\tFIELD_NO_DELETE\n'
} >"$EXPECTED_TSV"

# 규칙 이름이 바뀐 새 결과로 갱신 — 표류.
RESULT_ROWS=("field-delete	caught	100	FIELD_SAME_TYPE")
ACCEPT_DRIFT=0
rc=0
write_expected_tsv || rc=$?
assert_eq "F-22 --update 표류는 exit 1" "1" "$rc"
assert_eq "F-22 표류해도 파일은 갱신된다" "FIELD_SAME_TYPE" "$(awk -F'\t' '$1=="field-delete"{print $4}' "$EXPECTED_TSV")"

# 같은 표류를 --accept-drift 로 수용 — 다시 원래 값으로 갱신하며 이번엔 drift 가
# 있어도 exit 0.
printf 'mutation\toutcome\texit_code\trule_type\nfield-delete\tcaught\t100\tFIELD_NO_DELETE\n' >"$EXPECTED_TSV"
RESULT_ROWS=("field-delete	caught	100	FIELD_SAME_TYPE")
ACCEPT_DRIFT=1
rc=0
write_expected_tsv || rc=$?
assert_eq "F-22 --update --accept-drift 는 exit 0" "0" "$rc"

# 표가 없던 최초 실행 — 비교 대상이 없어 exit 0.
rm -f "$WORKDIR/expected-fresh.tsv"
EXPECTED_TSV="$WORKDIR/expected-fresh.tsv"
RESULT_ROWS=("field-delete	caught	100	FIELD_NO_DELETE")
ACCEPT_DRIFT=0
rc=0
write_expected_tsv || rc=$?
assert_eq "F-22 최초 --update(표 없음)는 exit 0" "0" "$rc"

# ---- F-23 — 대용량 출력에서 has_compile_error 가 pipefail 로 죽지 않는다 ----
echo "== F-23 — 128 KiB 합성 출력 =="
COMPILE_ERROR_TYPE=$(require_policy_value "breaking.compile-error.type") || exit 2
{
    i=0
    while [[ $i -lt 2800 ]]; do
        printf '{"type":"FIELD_NO_DELETE","path":"noise-%d.proto"}\n' "$i"
        i=$((i + 1))
    done
    printf '{"type":"%s","path":"broken.proto"}\n' "$COMPILE_ERROR_TYPE"
} >"$WORKDIR/synthetic.json"
SYNTHETIC_SIZE=$(wc -c <"$WORKDIR/synthetic.json" | tr -d ' ')
SYNTHETIC_PAYLOAD=$(cat "$WORKDIR/synthetic.json")

rc=0
has_compile_error "$SYNTHETIC_PAYLOAD" || rc=$?
assert_eq "F-23 ${SYNTHETIC_SIZE}B(>64KiB) 합성 출력에서 has_compile_error 가 참이다" "0" "$rc"

FIRST_RULE=$(first_rule_type "$SYNTHETIC_PAYLOAD")
assert_eq "F-23 first_rule_type 은 컴파일 오류를 제외한 첫 규칙을 낸다" "FIELD_NO_DELETE" "$FIRST_RULE"

# ---- 요약 ----
if [[ ${#FAILURES[@]} -eq 0 ]]; then
    echo "== selftest 통과 — F-21·F-22·F-23 전부 기대대로 =="
    exit 0
fi
echo "== selftest 실패 — ${#FAILURES[@]}건: ${FAILURES[*]} ==" >&2
exit 1
