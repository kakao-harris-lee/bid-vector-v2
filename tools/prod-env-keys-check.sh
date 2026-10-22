#!/usr/bin/env bash
# M7/7F 본편 — 운영 env 키 집합의 **등식**(값을 보지 않는다).
#
#   모드 1(인자 없음): `docker/prod.env.template` 의 키 집합 == 운영 compose 세 파일이 요구하는
#                      `${VAR…}` 참조 집합. 템플릿이 compose 보다 넓거나(죽은 키) 좁으면(반입 때 compose
#                      가 그 자리에서 실패) 붉다.
#   모드 2(`--env-file <경로>`): 그 env 파일의 키 집합 == 템플릿 키 집합 + 파일 형식 점검.
#                      값은 읽지 않는다 — 키 이름(`=` 앞)과 「비어 있는가」만 본다.
#
# compose 의 참조는 **해석기가 아니라 텍스트에서** 걷는다. 그래서 술어는 「참조 문법 `${이름`」
# 하나로 좁다 — `$이름`(중괄호 없음) 형식이 들어오면 그 키는 집합 밖이다. 그 형식을 이 저장소의
# compose 가 쓰지 않음을 같은 검사가 **0 으로 단언**한다(못 읽는 표기는 통과가 아니라 exit 2 —
# 7F-local 참조 검사와 같은 fail-closed). `$$`(컨테이너 셸 몫의 이스케이프)는 참조가 아니다.
#
# 종료 코드: 0 등식 · 1 불일치 · 2 판정 불가(파일 없음·못 읽는 표기·사용법).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

TEMPLATE="docker/prod.env.template"

_die() { echo "prod-env-keys-check: $1" >&2; exit "${2:-1}"; }

_template_keys() {
  # 주석·빈 줄을 뺀 `KEY=` 줄. 템플릿에 값이 있으면 그 자체가 위반이다(값 0 의 파일).
  local f="$1" line bad=0
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|'#'*) continue ;; esac
    [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]] || _die "템플릿에 KEY= 형식이 아닌 줄이 있다" 2
    [ -z "${BASH_REMATCH[2]}" ] || bad=1
    printf '%s\n' "${BASH_REMATCH[1]}"
  done < "$f"
  [ "$bad" -eq 0 ] || _die "템플릿에 값이 들어 있다 — 이 파일은 키 이름만 갖는다" 1
}

_compose_keys() {
  local f
  for f in "${BIDVECTOR_PROD_COMPOSE_FILES[@]}"; do
    [ -r "$f" ] || _die "compose 파일을 읽지 못했다: $f" 2
  done
  # 못 읽는 표기: `$` 뒤가 `{`·`$`가 아닌 참조(`$NAME`). 주석 줄은 뺀다.
  if grep -hvE '^[[:space:]]*#' "${BIDVECTOR_PROD_COMPOSE_FILES[@]}" | sed 's/\$\$//g' | grep -qE '\$[A-Za-z_]'; then
    _die "중괄호 없는 변수 참조(\$NAME)가 있다 — 이 검사가 읽지 않는 표기라 판정 불가" 2
  fi
  grep -hvE '^[[:space:]]*#' "${BIDVECTOR_PROD_COMPOSE_FILES[@]}" | sed 's/\$\$//g' \
    | grep -oE '\$\{[A-Za-z_][A-Za-z0-9_]*' | sed 's/^\${//' | sort -u
}

_diff_sets() {
  # $1 이름 왼쪽, $2 이름 오른쪽, $3·$4 정렬된 집합
  local only_l only_r
  only_l="$(comm -23 <(printf '%s\n' "$3") <(printf '%s\n' "$4"))"
  only_r="$(comm -13 <(printf '%s\n' "$3") <(printf '%s\n' "$4"))"
  if [ -n "$only_l" ] || [ -n "$only_r" ]; then
    [ -z "$only_l" ] || echo "  $1 에만: $(printf '%s' "$only_l" | tr '\n' ' ')" >&2
    [ -z "$only_r" ] || echo "  $2 에만: $(printf '%s' "$only_r" | tr '\n' ' ')" >&2
    return 1
  fi
}

[ -r "$TEMPLATE" ] || _die "템플릿이 없다: $TEMPLATE" 2
template_keys="$(_template_keys "$TEMPLATE" | sort -u)"
[ -n "$template_keys" ] || _die "템플릿 키가 0 개다 — 양성 대조 실패" 2

case "${1:-}" in
  "")
    [ "$#" -eq 0 ] || _die "사용법: $0 [--env-file <경로>]" 2
    compose_keys="$(_compose_keys)"
    [ -n "$compose_keys" ] || _die "compose 참조가 0 개다 — 양성 대조 실패" 2
    if ! _diff_sets "템플릿" "compose" "$template_keys" "$compose_keys"; then
      _die "템플릿 키 집합과 compose 참조 집합이 다르다" 1
    fi
    echo "prod-env-keys-check: 등식 — 키 $(printf '%s\n' "$template_keys" | wc -l | tr -d ' ')개 (템플릿 == compose 세 파일)"
    ;;
  --env-file)
    [ "$#" -eq 2 ] || _die "사용법: $0 --env-file <경로>" 2
    envf="$2"
    [ -r "$envf" ] || _die "env 파일을 읽지 못했다" 2
    env_keys="" empty=""
    while IFS= read -r line || [ -n "$line" ]; do
      case "$line" in ''|'#'*) continue ;; esac
      [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]] || _die "env 파일에 KEY=값 형식이 아닌 줄이 있다(값은 출력하지 않는다)" 2
      env_keys+="${BASH_REMATCH[1]}"$'\n'
      [ -n "${BASH_REMATCH[2]}" ] || empty+="${BASH_REMATCH[1]} "
    done < "$envf"
    env_keys="$(printf '%s' "$env_keys" | sed '/^$/d' | sort)"
    dups="$(printf '%s\n' "$env_keys" | uniq -d)"
    [ -z "$dups" ] || _die "env 파일에 같은 키가 두 번 있다: $(printf '%s' "$dups" | tr '\n' ' ')" 1
    if ! _diff_sets "env 파일" "템플릿" "$env_keys" "$template_keys"; then
      _die "env 파일 키 집합이 템플릿과 다르다" 1
    fi
    # RELEASE_TAG 는 반입 스크립트가 채운다 — 비어 있어도 된다. 나머지는 비면 안 된다.
    empty="$(printf '%s' "$empty" | tr ' ' '\n' | grep -vx 'BIDVECTOR_RELEASE_TAG' | sed '/^$/d' || true)"
    [ -z "$empty" ] || _die "값이 빈 키가 있다: $(printf '%s' "$empty" | tr '\n' ' ')" 1
    echo "prod-env-keys-check: env 파일 키 집합 == 템플릿 ($(printf '%s\n' "$env_keys" | wc -l | tr -d ' ')개)"
    ;;
  *) _die "사용법: $0 [--env-file <경로>]" 2 ;;
esac
