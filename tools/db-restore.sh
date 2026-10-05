#!/usr/bin/env bash
# M6/6B-2 D-2 — 백업 한 벌을 **새 빈 데이터베이스**에 되살리고, 되살아난 것이 원본과 같은지를
# manifest 등식으로 판정한다.
#
# **쓰기 대상은 구조적으로 자기 자원뿐이다.** 대상 컨테이너가 이미 있으면 `bidvector.rehearsal`
# 라벨이 호출자가 준 실행 표식과 같을 때만 손댄다 — 아니면 거부한다. 없으면 이 스크립트가
# 그 라벨을 붙여 직접 만든다. 호스트 포트를 publish 하지 않고(`-p` 를 쓰지 않는다)
# `--network none` 으로 띄우므로, 만든 컨테이너는 netns 를 공유하는 컨테이너 말고는
# 아무도 닿지 못한다(D-6B2-3).
#
# 복원 순서가 중요하다 — **역할 먼저, 그 다음 스키마·데이터**. `pg_dump <db>` 는 역할을
# 담지 않고 `GRANT ... TO bidvector_app` 만 담으므로(역할은 클러스터 객체다), 새 클러스터에서
# 역할이 없으면 GRANT 가 전부 실패한다(조사 4.1 ①).
#
# 등식의 양변은 **같은 스크립트가** 잰다 — 복원본의 측정은 `tools/db-backup.sh` 를 다시 불러
# 얻는다. 두 번째 측정 구현이 없으므로 둘이 따로 드리프트할 자리가 없다.
#
# Flyway `validate()` 는 이 스크립트가 부르지 않는다. `flyway_schema_history` 의 **행 집합
# 해시 등식**이 그 자리에 선다 — validate 는 이력 행과 마이그레이션 파일 체크섬만 보는 순수
# 함수이므로, 같은 checkout 에서 이력 행이 같으면 답도 같다. 실제 Flyway 호출은
# `tools/db-rehearsal.sh` 가 한다(그 쪽은 임시 마이그레이션 적용 때문에 어차피 필요하다).
#
# 종료 코드: 2 도구 부재 · 3 사용법·대상 오류 · 1 복원 실패 또는 등식 어긋남.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

_die() { echo "db-restore: $1" >&2; exit "${2:-1}"; }

_usage() {
  cat >&2 <<'USAGE'
usage: tools/db-restore.sh <backup-dir> <container> <image> <database> <run-label>
       tools/db-restore.sh --compare <expected-manifest> <actual-manifest> [<ignore-csv>]
  <backup-dir>  tools/db-backup.sh 가 만든 디렉터리(roles.sql·db.dump·manifest.json)
  <container>   복원 대상 컨테이너 이름. 있으면 <run-label> 라벨이 맞아야 하고, 없으면 만든다
  <image>       대상 컨테이너를 만들 때 쓸 postgres 이미지(원본과 같은 참조를 준다)
  <database>    복원해 넣을 **새** 데이터베이스 이름(이미 있으면 거부한다)
  <run-label>   이 실행의 표식 — 만든 컨테이너에 bidvector.rehearsal 라벨로 붙는다
USAGE
  exit 3
}

# ---- 등식 비교(단독 모드로도 쓴다) -------------------------------------------------
# 어긋남은 **두 단계까지** 판다 — 측정 안의 이름, 그 값이 또 object 면 그 안의 이름까지.
# 권한 행렬은 한 역할의 행렬에 표·시퀀스·함수가 함께 들어 커졌으므로, 역할 이름만 대면 어느
# 객체가 권한을 잃었는지가 문면에서 사라진다(2026-10-05 code-review R-4 · verifier R2-L-2).
#
# 무시 목록은 **키를 먼저 결속한 뒤** 조회한다(`. as $k` 가 `$ignore | index($k)` 보다 앞선다).
# 파이프 안에서 `.` 는 `$ignore` 로 재결속되므로 `($ignore | index(.))` 는 배열에서 배열을
# 찾아 늘 0 을 돌려주고, 그러면 **모든 측정이 무시되어 판정이 아무것도 재지 않는다**
# (2026-10-05 실측 — 리허설의 음성 대조가 이것을 잡았다). 무시 목록을 쓰는 호출자는 같은
# 목록으로 음성 대조를 함께 돌려 이 자리가 비어 있지 않음을 보여야 한다.
# 아래 비교는 `.measurements` 가 object 임을 전제로 `keys` 를 부른다. 그 전제가 깨진 입력에서
# jq 가 죽으면 **빈 findings 가 「일치」로 읽힌다** — `_compare` 는 `||` 목록과 `if !` 조건에서
# 불리므로 함수 본문까지 errexit 가 억제되고, 실패한 대입이 함수를 끊지 못한다. 그래서 형식을
# 먼저 단언하고(아래), jq 의 종료 코드는 **명시 분기**로 받는다(2026-10-05 code-review G-1 ·
# verifier M-1). manifest 는 자기 해시로 자기를 증명할 수 없으므로 이 형식 단언이 그 자리다.
_assert_manifest() {
  local file="$1" role="$2" verdict
  verdict="$(jq -r '
      if (type == "object")
         and (.schema == "bidvector-db-backup/1")
         and ((.measurements | type) == "object")
      then "ok" else "shape" end' "$file" 2>/dev/null)" || verdict="parse"
  # 0 바이트 입력에서 jq 는 출력 없이 성공하므로 세 사유 어느 것도 아닌 빈 값이 나온다.
  [ -n "$verdict" ] || verdict="empty"
  # 깨진 manifest 는 등식의 어긋남이 아니라 **대상 오류**다 — 이 스크립트의 코드 규약에서 3 이다.
  [ "$verdict" = "ok" ] \
    || _die "$role manifest 가 이 형식이 아니다(${verdict}): $file" 3
}

_compare() {
  local expected="$1" actual="$2" ignore_csv="${3:-}" ignore_json findings
  [ -r "$expected" ] || _die "원본 manifest 를 읽을 수 없다: $expected" 3
  [ -r "$actual" ] || _die "복원본 manifest 를 읽을 수 없다: $actual" 3
  _assert_manifest "$expected" "원본"
  _assert_manifest "$actual" "복원본"
  ignore_json="$(jq -cn --arg s "$ignore_csv" '$s | split(",") | map(select(length > 0))')"

  if ! findings="$(jq -rn \
    --slurpfile e "$expected" --slurpfile a "$actual" --argjson ignore "$ignore_json" '
    def brief: tojson | if length > 90 then .[0:87] + "..." else . end;
    def bothobj(x; y): (x | type) == "object" and (y | type) == "object";
    def firstdiff(x; y): ((x | keys) + (y | keys) | unique)
                         | map(select(x[.] != y[.])) | (.[0] // "?");
    ($e[0].measurements) as $E | ($a[0].measurements) as $A |
    (($E | keys) + ($A | keys) | unique) as $keys |
    $keys[]
    | . as $k
    | select(($ignore | index($k)) == null)
    | select($E[$k] != $A[$k])
    | (if bothobj($E[$k]; $A[$k]) then firstdiff($E[$k]; $A[$k]) else null end) as $n
    | (if $n == null then $E[$k] else $E[$k][$n] end) as $e2
    | (if $n == null then $A[$k] else $A[$k][$n] end) as $a2
    | (if bothobj($e2; $a2) then firstdiff($e2; $a2) else null end) as $n2
    | (if $n2 == null then $e2 else $e2[$n2] end) as $e3
    | (if $n2 == null then $a2 else $a2[$n2] end) as $a3
    | "등식 어긋남: \($k)"
      + (if $n == null then " / (측정 전체)" else " / \($n)" end)
      + (if $n2 == null then "" else " / \($n2)" end)
      + " — 원본 \($e3 | brief) · 복원본 \($a3 | brief)"')"; then
    echo "db-restore: manifest 비교가 실패했다 — 입력을 읽지 못했다" >&2
    return 1
  fi

  if [ -n "$findings" ]; then
    printf '%s\n' "$findings" >&2
    return 1
  fi
  return 0
}

if [ "${1:-}" = "--compare" ]; then
  shift
  [ "$#" -ge 2 ] && [ "$#" -le 3 ] || _usage
  command -v jq >/dev/null 2>&1 || _die "jq 가 필요하다" 2
  _compare "$1" "$2" "${3:-}" || exit 1
  echo "db-restore: 등식 일치 — 어긋난 측정 0"
  exit 0
fi

# ---- 복원 ---------------------------------------------------------------------------
[ "$#" -eq 5 ] || _usage
BACKUP="$1"
CONTAINER="$2"
IMAGE="$3"
DATABASE="$4"
RUN_LABEL="$5"
LABEL_KEY="bidvector.rehearsal"

for tool in docker jq sha256sum openssl; do
  command -v "$tool" >/dev/null 2>&1 || _die "$tool 이 필요하다" 2
done
for artifact in roles.sql db.dump manifest.json; do
  [ -r "$BACKUP/$artifact" ] || _die "백업에 $artifact 가 없다: $BACKUP" 3
done

case "$DATABASE" in
  '' | *[!a-z0-9_]*) _die "데이터베이스 이름이 식별자 모양이 아니다: '$DATABASE'" 3 ;;
esac

# **빈 실행 표식을 거부한다.** 없는 라벨을 묻는 Go 템플릿 `index` 는 빈 문자열을 돌려주므로,
# 표식이 비어 있으면 「라벨이 없는 남의 컨테이너」가 등식을 통과한다 — unset 변수 하나가 이
# 인자를 만든다. 아래 등식은 「라벨이 **존재**하고 값이 같다」로 따로 닫지만, 비어 있는 표식은
# 그 자체로 쓸 수 없는 값이라 여기서 먼저 끊는다(2026-10-05 verifier H-1).
case "$RUN_LABEL" in
  *[![:space:]]*) ;;
  *) _die "실행 표식이 비어 있다 — 그 값으로는 자기 자원을 가릴 수 없다" 3 ;;
esac

# 형식 단언이 **해시 대조보다 먼저** 선다. 뒤에 두면 아래 루프의 `jq` 가 깨진 manifest 에서 먼저
# 죽어 종료 코드가 이 스크립트의 규약(1·2·3) 밖인 jq 의 것이 된다(2026-10-05 verifier R2-L-3).
_assert_manifest "$BACKUP/manifest.json" "백업"

# 우회 2(백업 바이트 변조) — 복원 전에 manifest 가 적은 해시와 실제 파일을 맞춘다.
for artifact in roles.sql db.dump; do
  recorded="$(jq -r --arg a "$artifact" '.artifacts[$a].sha256' "$BACKUP/manifest.json")"
  measured="$(sha256sum "$BACKUP/$artifact" | cut -d' ' -f1)"
  [ "$recorded" = "$measured" ] \
    || _die "$artifact 의 sha256 이 manifest 와 다르다 — 백업이 손상됐거나 바뀌었다" 1
done

# 복원 경로는 manifest 를 **믿지 않는 입력**으로 다룬다(형제 두 파일의 바이트를 해시로 맞추고
# 형식을 단언한다). 그 전제 아래 같은 파일에서 읽은 식별자를 무가드로 SQL 에 조립하면 일관되지
# 않는다 — `db-backup.sh` 는 카탈로그에서 온 표 이름에도 같은 검사를 건다(2026-10-05
# code-review G-2). 설계 검토 (2b) 의 「주입 자리 없음」은 manifest 를 입력으로 세지 않은 문장이다.
_assert_plain_identifier() {
  case "$2" in
    '' | *[!a-z0-9_]*) _die "$1 식별자 모양이 아니다: '$2'" 1 ;;
  esac
}

SUPERUSER="$(jq -r '.measurements.database.owner' "$BACKUP/manifest.json")"
ENCODING="$(jq -r '.measurements.database.encoding' "$BACKUP/manifest.json")"
COLLATE="$(jq -r '.measurements.database.collate' "$BACKUP/manifest.json")"
CTYPE="$(jq -r '.measurements.database.ctype' "$BACKUP/manifest.json")"
[ -n "$SUPERUSER" ] && [ "$SUPERUSER" != "null" ] || _die "manifest 에 데이터베이스 소유자가 없다" 3
_assert_plain_identifier "manifest 의 데이터베이스 소유자가" "$SUPERUSER"

_wait_ready() {
  local i
  # 공식 이미지는 initdb 용 **임시 서버**를 먼저 띄웠다 내린다 — 초기화 완료 표지를 먼저
  # 기다리지 않으면 그 임시 서버를 붙잡고 곧바로 연결이 끊긴다(2026-10-05 실측).
  for i in $(seq 1 60); do
    docker logs "$CONTAINER" 2>&1 | grep -q 'init process complete' && break
    sleep 1
  done
  for i in $(seq 1 60); do
    docker exec -i "$CONTAINER" psql -X -q -tAc 'select 1' -U "$SUPERUSER" \
      -d "$SUPERUSER" >/dev/null 2>&1 && return 0
    sleep 1
  done
  _die "대상 컨테이너가 제한 시간 안에 접속을 받지 않았다" 1
}

# `--type container` — 같은 이름의 이미지·볼륨·네트워크가 컨테이너 조회에 끼어들지 않게 한다.
if docker inspect --type container "$CONTAINER" >/dev/null 2>&1; then
  # 라벨 맵을 통째로 받아 **존재와 값을 함께** 묻는다. Go 템플릿의 `index` 는 없는 키에 빈
  # 문자열을 돌려주므로 템플릿만으로는 「없음」과 「빈 값」이 구분되지 않는다. 라벨이 하나도
  # 없으면 `.Config.Labels` 는 `null` 이라 `has` 가 터지므로 타입부터 본다.
  matched="$(docker inspect --type container -f '{{json .Config.Labels}}' "$CONTAINER" \
    | jq -r --arg k "$LABEL_KEY" --arg v "$RUN_LABEL" \
        'if (type == "object") and has($k) and (.[$k] == $v) then "yes" else "no" end')" \
    || _die "컨테이너 '$CONTAINER' 의 라벨을 읽지 못했다" 3
  [ "$matched" = "yes" ] \
    || _die "컨테이너 '$CONTAINER' 는 이 실행이 만든 것이 아니다 — 손대지 않는다" 3
  [ "$(docker inspect --type container -f '{{.State.Running}}' "$CONTAINER")" = "true" ] \
    || _die "대상 컨테이너 '$CONTAINER' 가 동작 중이 아니다" 3
else
  # 자격 값은 여기서 만들고 **이름만** 넘긴다(`-e VAR`) — docker 의 argv 에 값이 실리지
  # 않는다. 어차피 `--network none` 이라 netns 를 공유하지 않는 한 아무도 닿지 못한다.
  POSTGRES_PASSWORD="$(openssl rand -hex 24)"
  export POSTGRES_PASSWORD
  docker run -d --name "$CONTAINER" \
    --label "$LABEL_KEY=$RUN_LABEL" \
    --network none \
    --security-opt no-new-privileges:true \
    -e POSTGRES_USER="$SUPERUSER" -e POSTGRES_DB="$SUPERUSER" -e POSTGRES_PASSWORD \
    "$IMAGE" >/dev/null || _die "대상 컨테이너를 만들지 못했다" 1
  unset POSTGRES_PASSWORD
  _wait_ready
fi

_psql_db() {
  docker exec -i "$CONTAINER" psql -X -q -t -A -v ON_ERROR_STOP=1 \
    -U "$SUPERUSER" -d "$1" -c "$2"
}

# 리터럴을 셸에서 따옴표로 감싸 붙이는 대신 `psql -v` 로 넘기고 `:'var'` 로 인용한다 —
# 인용 규칙이 psql 쪽에 있으므로 값에 무엇이 들었든 문자열로만 읽힌다.
#
# SQL 을 **stdin 으로** 준다. `-c` 는 psql 의 렉서를 거치지 않아 변수 보간이 일어나지 않는다
# (2026-10-05 실측: `psql -v v=hello -c "select :'v'"` → `syntax error at or near ":"`,
# 같은 문장을 stdin 으로 주면 `hello`). 오류는 `ON_ERROR_STOP` 이 비-0 으로 낸다.
_psql_db_v() {
  local database="$1" sql="$2"
  shift 2
  printf '%s\n' "$sql" \
    | docker exec -i "$CONTAINER" psql -X -q -t -A -v ON_ERROR_STOP=1 \
        -U "$SUPERUSER" -d "$database" "$@"
}

# ---- ① 역할 ---------------------------------------------------------------------
# 이미 있는 역할의 `CREATE ROLE` 은 실패한다(초기화가 슈퍼유저를 먼저 만들고, 같은
# 클러스터에 두 번째 데이터베이스를 복원할 때도 그렇다). 그래서 **명령의 종료 코드가 아니라
# 결과를 단언한다** — manifest 가 적은 역할이 전부 존재해야 한다. 속성까지 같은지는 아래
# 등식의 `roles` 측정이 본다.
roles_log="$(mktemp)"
trap 'rm -f "$roles_log"' EXIT
docker exec -i "$CONTAINER" psql -X -q -U "$SUPERUSER" -d "$SUPERUSER" \
  < "$BACKUP/roles.sql" >"$roles_log" 2>&1 || true
missing=""
while IFS= read -r role; do
  [ -n "$role" ] || continue
  # 역할 **이름**에는 모양 검사를 걸지 않는다 — 아래 `:'r'` 이 값 자리를 완전히 인용하므로 더할
  # 안전이 없고, 검사는 `Admin`·`app-user`·`app.reader` 같은 적법한 역할을 막았다(code-review R-1).
  # `$SUPERUSER` 쪽 가드는 유지한다 — 그쪽은 `owner $SUPERUSER` 라는 진짜 식별자 자리다.
  present="$(_psql_db_v "$SUPERUSER" \
    "select exists(select 1 from pg_roles where rolname = :'r')" -v r="$role")"
  [ "$present" = "t" ] || missing="${missing:+$missing, }$role"
done < <(jq -r '.measurements.roles | keys[]' "$BACKUP/manifest.json")
if [ -n "$missing" ]; then
  sed -n '1,20p' "$roles_log" >&2
  _die "역할 복원 뒤에도 없는 역할: $missing" 1
fi

# ---- ② 새 빈 데이터베이스 -------------------------------------------------------
exists="$(_psql_db "$SUPERUSER" "select exists(select 1 from pg_database where datname = '$DATABASE')")"
[ "$exists" = "f" ] || _die "데이터베이스 '$DATABASE' 가 이미 있다 — 복원은 빈 DB 로만 한다" 3
_psql_db_v "$SUPERUSER" \
  "create database $DATABASE owner $SUPERUSER template template0
   encoding :'enc' lc_collate :'coll' lc_ctype :'ctyp'" \
  -v enc="$ENCODING" -v coll="$COLLATE" -v ctyp="$CTYPE" >/dev/null \
  || _die "대상 데이터베이스를 만들지 못했다" 1

# ---- ③ 스키마·데이터 -------------------------------------------------------------
docker cp "$BACKUP/db.dump" "$CONTAINER:/tmp/db.dump" >/dev/null \
  || _die "덤프를 대상 컨테이너로 옮기지 못했다" 1
restore_log="$(mktemp)"
trap 'rm -f "$roles_log" "$restore_log"' EXIT
if ! docker exec -i "$CONTAINER" pg_restore --exit-on-error \
      -U "$SUPERUSER" -d "$DATABASE" /tmp/db.dump >"$restore_log" 2>&1; then
  sed -n '1,20p' "$restore_log" >&2
  docker exec "$CONTAINER" rm -f /tmp/db.dump >/dev/null 2>&1 || true
  _die "pg_restore 가 실패했다(위 스무 줄)" 1
fi
docker exec "$CONTAINER" rm -f /tmp/db.dump >/dev/null 2>&1 || true

# ---- ④ 등식 ---------------------------------------------------------------------
AFTER_DIR="$(mktemp -d)"
trap 'rm -f "$roles_log" "$restore_log"; rm -rf "$AFTER_DIR"' EXIT
"$SCRIPT_DIR/db-backup.sh" "$CONTAINER" "$AFTER_DIR" "$DATABASE" >/dev/null \
  || _die "복원본을 측정하지 못했다" 1

if ! _compare "$BACKUP/manifest.json" "$AFTER_DIR/manifest.json" ""; then
  _die "복원본이 원본과 다르다(위 목록)" 1
fi

echo "db-restore: $DATABASE 복원 완료 — 측정 $(jq -r '.measurements | keys | length' "$BACKUP/manifest.json")가지 전부 일치"
