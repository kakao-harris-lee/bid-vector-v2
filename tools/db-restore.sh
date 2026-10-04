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
_compare() {
  local expected="$1" actual="$2" ignore_csv="${3:-}" ignore_json findings
  [ -r "$expected" ] || _die "원본 manifest 를 읽을 수 없다: $expected" 3
  [ -r "$actual" ] || _die "복원본 manifest 를 읽을 수 없다: $actual" 3
  ignore_json="$(jq -cn --arg s "$ignore_csv" '$s | split(",") | map(select(length > 0))')"

  findings="$(jq -rn \
    --slurpfile e "$expected" --slurpfile a "$actual" --argjson ignore "$ignore_json" '
    def brief: tojson | if length > 90 then .[0:87] + "..." else . end;
    ($e[0].measurements) as $E | ($a[0].measurements) as $A |
    (($E | keys) + ($A | keys) | unique) as $keys |
    $keys[]
    | select(($ignore | index(.)) == null)
    | . as $k
    | select($E[$k] != $A[$k])
    | (if ($E[$k] | type) == "object" and ($A[$k] | type) == "object"
       then ((($E[$k] | keys) + ($A[$k] | keys) | unique)
             | map(select($E[$k][.] != $A[$k][.])) | (.[0] // "?"))
       else "(측정 전체)" end) as $n
    | (if ($E[$k] | type) == "object" and ($A[$k] | type) == "object"
       then [$E[$k][$n], $A[$k][$n]] else [$E[$k], $A[$k]] end) as $pair
    | "등식 어긋남: \($k) / \($n) — 원본 \($pair[0] | brief) · 복원본 \($pair[1] | brief)"')"

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

# 우회 2(백업 바이트 변조) — 복원 전에 manifest 가 적은 해시와 실제 파일을 맞춘다.
for artifact in roles.sql db.dump; do
  recorded="$(jq -r --arg a "$artifact" '.artifacts[$a].sha256' "$BACKUP/manifest.json")"
  measured="$(sha256sum "$BACKUP/$artifact" | cut -d' ' -f1)"
  [ "$recorded" = "$measured" ] \
    || _die "$artifact 의 sha256 이 manifest 와 다르다 — 백업이 손상됐거나 바뀌었다" 1
done

SUPERUSER="$(jq -r '.measurements.database.owner' "$BACKUP/manifest.json")"
ENCODING="$(jq -r '.measurements.database.encoding' "$BACKUP/manifest.json")"
COLLATE="$(jq -r '.measurements.database.collate' "$BACKUP/manifest.json")"
CTYPE="$(jq -r '.measurements.database.ctype' "$BACKUP/manifest.json")"
[ -n "$SUPERUSER" ] && [ "$SUPERUSER" != "null" ] || _die "manifest 에 데이터베이스 소유자가 없다" 3

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

if docker inspect "$CONTAINER" >/dev/null 2>&1; then
  found_label="$(docker inspect -f "{{index .Config.Labels \"$LABEL_KEY\"}}" "$CONTAINER" 2>/dev/null || true)"
  [ "$found_label" = "$RUN_LABEL" ] \
    || _die "컨테이너 '$CONTAINER' 는 이 실행이 만든 것이 아니다 — 손대지 않는다" 3
  [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER")" = "true" ] \
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
  present="$(_psql_db "$SUPERUSER" "select exists(select 1 from pg_roles where rolname = '$role')")"
  [ "$present" = "t" ] || missing="${missing:+$missing, }$role"
done < <(jq -r '.measurements.roles | keys[]' "$BACKUP/manifest.json")
if [ -n "$missing" ]; then
  sed -n '1,20p' "$roles_log" >&2
  _die "역할 복원 뒤에도 없는 역할: $missing" 1
fi

# ---- ② 새 빈 데이터베이스 -------------------------------------------------------
exists="$(_psql_db "$SUPERUSER" "select exists(select 1 from pg_database where datname = '$DATABASE')")"
[ "$exists" = "f" ] || _die "데이터베이스 '$DATABASE' 가 이미 있다 — 복원은 빈 DB 로만 한다" 3
_psql_db "$SUPERUSER" "create database $DATABASE owner $SUPERUSER template template0
  encoding '$ENCODING' lc_collate '$COLLATE' lc_ctype '$CTYPE'" >/dev/null \
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
