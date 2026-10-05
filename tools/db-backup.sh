#!/usr/bin/env bash
# M6/6B-2 D-1 — 논리 백업 한 벌과 **복원 등식의 재료**(manifest)를 한 번에 뜬다.
#
# 이 스크립트는 **자격증명을 받지 않는다.** 인자는 대상 컨테이너와 출력 디렉터리(와 선택
# 적인 데이터베이스 이름)뿐이고, 모든 DB 접속은 `docker exec` 로 **컨테이너 안에서 유닉스
# 소켓**을 통해 일어난다. 공식 postgres 이미지의 `pg_hba.conf` 는 소켓 줄이 `trust` 이므로
# 암호가 필요 없다 — 값이 호스트 argv(`/proc/<pid>/cmdline`)·환경·로그 어디에도 실리지
# 않는다. 슈퍼유저·DB 이름은 컨테이너 자신의 `POSTGRES_USER`·`POSTGRES_DB` 에서 읽는다.
#
# **`--no-role-passwords` 를 쓴다.** 맨 `pg_dumpall --roles-only` 은 로그인 역할의
# SCRAM 검증자(`PASSWORD 'SCRAM-SHA-256$...'`)를 평문 파일에 적는다 — 2026-10-05 실측으로
# 그 한 줄이 `config/quality/leak-patterns.txt` 참조 스캔에 걸린다. 이 slice 의 위협 모델은
# 「자격 값이 백업 산출물에 없다」를 방어 대상으로 세우므로 검증자를 담지 않는다. 잃는 것은
# 없다: 복원이 필요로 하는 것은 역할의 **존재·속성·소속**이고(그래야 GRANT 가 해소된다),
# `bidvector_app` 은 NOLOGIN 이라 애초에 암호가 없다. 로그인 역할의 암호는 복원 시점에
# 환경이 준다(운영 절차는 runbook §금지·주의).
#
# 산출: `roles.sql`(클러스터 역할) · `db.dump`(pg_dump custom) · `manifest.json`.
# manifest 의 `measurements` 객체가 복원 등식의 양변이다 — `db-restore.sh --compare` 가
# 같은 스크립트로 복원본에서 다시 재어 키 단위로 맞춘다(두 번째 구현이 없다).
#
# 표 목록·역할 목록·시퀀스 목록은 전부 **카탈로그에서 발견**한다(손 목록 0) — 새 표가
# 생기면 등식이 저절로 그 표를 포함한다.
#
# 종료 코드: 2 도구 부재 · 3 사용법·대상 오류 · 1 측정·덤프 실패.
set -euo pipefail

_die() { echo "db-backup: $1" >&2; exit "${2:-1}"; }

_usage() {
  cat >&2 <<'USAGE'
usage: tools/db-backup.sh <container> <output-dir> [<database>]
  <container>   대상 postgres 컨테이너 이름 또는 ID(동작 중이어야 한다)
  <output-dir>  산출물을 쓸 디렉터리(없으면 만든다, 비어 있어야 한다)
  <database>    생략하면 컨테이너의 POSTGRES_DB
USAGE
  exit 3
}

[ "$#" -ge 2 ] && [ "$#" -le 3 ] || _usage
CONTAINER="$1"
OUT="$2"
DATABASE="${3:-}"

for tool in docker jq sha256sum; do
  command -v "$tool" >/dev/null 2>&1 || _die "$tool 이 필요하다" 2
done

[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null || echo false)" = "true" ] \
  || _die "컨테이너 '$CONTAINER' 가 동작 중이 아니다" 3

SUPERUSER="$(docker exec "$CONTAINER" printenv POSTGRES_USER)" \
  || _die "컨테이너에 POSTGRES_USER 가 없다 — postgres 컨테이너가 맞는가" 3
[ -n "$DATABASE" ] || DATABASE="$(docker exec "$CONTAINER" printenv POSTGRES_DB)" \
  || _die "컨테이너에 POSTGRES_DB 가 없다 — 데이터베이스 이름을 인자로 준다" 3

mkdir -p "$OUT"
[ -z "$(ls -A "$OUT")" ] || _die "출력 디렉터리 '$OUT' 가 비어 있지 않다" 3

# 컨테이너 안에서만 도는 질의 — 호스트 argv 에 실리는 것은 SQL 뿐이다.
_psql() {
  docker exec -i "$CONTAINER" psql -X -q -t -A -v ON_ERROR_STOP=1 \
    -U "$SUPERUSER" -d "$DATABASE" -c "$1"
}

# 발견한 식별자를 질의 문자열로 되돌리기 전에 모양을 확인한다 — 카탈로그에서 온 이름이지만
# 생성 경로가 바뀌어도 이 스크립트가 임의의 SQL 을 조립하지 않게 한다.
_assert_plain_identifier() {
  case "$1" in
    '' | *[!a-z0-9_]*) _die "식별자 모양이 아니다: '$1'" 1 ;;
  esac
}

# `flyway_schema_history` 는 이 목록에서 뺀다 — 그 표의 정본 측정은 아래 `flywayHistory`
# 이고(행 집합 전체 해시라 내용 해시보다 강하다), 두 자리에서 재면 같은 사실이 두 번
# 들어가 **되돌림 판정이 갈라진다**: forward-fix 뒤 이력은 늘어나는 것이 정상인데
# `tables` 쪽 사본이 그것을 「데이터가 돌아오지 않았다」로 신고했다(2026-10-05 실측).
# 권한 행렬에는 그대로 남는다 — 이력 표의 GRANT 는 6B-1 축9 가 못 박은 사실이다.
Q_TABLES=$(cat <<'SQL'
select table_name from information_schema.tables
 where table_schema = 'public' and table_type = 'BASE TABLE'
   and table_name <> 'flyway_schema_history'
 order by table_name
SQL
)

# Flyway 이력은 **행 집합 전체의 해시**로 잡는다. 복원본의 이 값이 같으면 같은 이력이고,
# 마이그레이션 파일이 같은 checkout 이라면 `validate()` 의 답도 같다(validate 는 이력 행과
# 파일 체크섬만 보는 순수 함수다). 이력 표가 아예 없는 복원(데이터만 복원한 사고)은
# "ABSENT" 로 드러난다. 최대 버전은 `installed_rank` 순서로 집는다 — `version` 은 TEXT 라
# `max()` 가 사전식이라서 '9' > '17' 이 된다.
Q_FLYWAY=$(cat <<'SQL'
select jsonb_build_object(
         'rows', count(*),
         'topVersion', (select version from public.flyway_schema_history
                         order by installed_rank desc limit 1),
         'rowSetSha256', encode(sha256(convert_to(
           coalesce(string_agg(h::text, chr(10) order by h::text), ''), 'UTF8')), 'hex'))
  from public.flyway_schema_history h
SQL
)

# 이력 표의 **존재 여부를 먼저 묻는다.** CASE 안에 넣어 가릴 수 없다 — PostgreSQL 은 쓰이지
# 않는 가지도 파싱하므로 표가 없으면 `to_regclass` 가드가 있어도 문장 전체가 거부된다.
# 데이터만 복원하고 이력 표를 빠뜨린 사고가 이 자리에서 "ABSENT" 로 드러나야 한다.
_flyway_json() {
  if [ "$(_psql "select to_regclass('public.flyway_schema_history') is not null")" != "t" ]; then
    printf '"ABSENT"'
    return 0
  fi
  _psql "$Q_FLYWAY"
}

# 행이 0 인 표에서는 내용 해시가 같아지므로 **열 구조를 따로** 잰다 — 빈 표의 열을 잃은
# 복원이 내용 해시만으로는 드러나지 않는다(forward-fix 의 DROP COLUMN 도 이 축이 본다).
Q_COLUMNS=$(cat <<'SQL'
select coalesce(jsonb_object_agg(table_name, cols), '{}'::jsonb) from (
  select table_name,
         jsonb_agg(jsonb_build_array(column_name, data_type, is_nullable,
                                     coalesce(column_default, ''), is_identity)
                   order by ordinal_position) as cols
    from information_schema.columns
   where table_schema = 'public' and table_name <> 'flyway_schema_history'
   group by table_name) c
SQL
)

# IDENTITY 현재값 — 복원 뒤 다음 INSERT 가 기존 키와 충돌하지 않는지의 축.
Q_SEQUENCES=$(cat <<'SQL'
select coalesce(jsonb_object_agg(sequencename, coalesce(last_value::text, 'unset')), '{}'::jsonb)
  from pg_sequences where schemaname = 'public'
SQL
)

# 역할은 **클러스터** 객체다 — `pg_dump <db>` 가 담지 않는다. 새 클러스터로 복원할 때
# `roles.sql` 을 먼저 넣지 않으면 GRANT 가 전부 실패한다(조사 4.1 ①).
Q_ROLES=$(cat <<'SQL'
select coalesce(jsonb_object_agg(rolname, jsonb_build_object(
         'super', rolsuper, 'inherit', rolinherit, 'createrole', rolcreaterole,
         'createdb', rolcreatedb, 'login', rolcanlogin,
         'replication', rolreplication, 'bypassrls', rolbypassrls)), '{}'::jsonb)
  from pg_roles where rolname not like 'pg\_%'
SQL
)

# 축9 유효 권한 행렬 — `CleanMigrationPrivilegeTest`(6B-1)와 같은 술어다:
# `has_*_privilege` 가 직접 부여·PUBLIC 부여·역할 상속을 모두 해소한다. 역할 목록도 객체
# 목록도 카탈로그에서 발견한다(그 test 의 손 기대 행렬은 clean DB 재현 축이고, 여기서는
# 원본 대 복원본의 **등식**이 판정이다).
#
# 표만이 아니라 **시퀀스와 함수**까지 잰다(2026-10-05 verifier L-2) — V2 가 시퀀스에 USAGE 를
# 주고 `SECURITY DEFINER` 감사 함수에 EXECUTE 를 주므로, 표 GRANT 만 보면 그 둘의 상실이
# 단독으로는 드러나지 않는다. 키에 종류 접두를 붙여 한 역할의 행렬 안에서 섞이지 않게 한다.
Q_PRIVILEGES=$(cat <<'SQL'
select coalesce(jsonb_object_agg(rolname, matrix), '{}'::jsonb) from (
  select r.rolname, jsonb_object_agg(o.key, o.priv) as matrix
    from pg_roles r
    cross join lateral (
      select 'table/' || c.relname as key,
             jsonb_build_object(
               'select',     has_table_privilege(r.oid, c.oid, 'SELECT'),
               'insert',     has_table_privilege(r.oid, c.oid, 'INSERT'),
               'update',     has_table_privilege(r.oid, c.oid, 'UPDATE'),
               'delete',     has_table_privilege(r.oid, c.oid, 'DELETE'),
               'truncate',   has_table_privilege(r.oid, c.oid, 'TRUNCATE'),
               'references', has_table_privilege(r.oid, c.oid, 'REFERENCES'),
               'trigger',    has_table_privilege(r.oid, c.oid, 'TRIGGER')) as priv
        from pg_class c join pg_namespace n on n.oid = c.relnamespace
       where c.relkind = 'r' and n.nspname = 'public'
      union all
      select 'sequence/' || c.relname,
             jsonb_build_object(
               'usage',  has_sequence_privilege(r.oid, c.oid, 'USAGE'),
               'select', has_sequence_privilege(r.oid, c.oid, 'SELECT'),
               'update', has_sequence_privilege(r.oid, c.oid, 'UPDATE'))
        from pg_class c join pg_namespace n on n.oid = c.relnamespace
       where c.relkind = 'S' and n.nspname = 'public'
      union all
      select 'function/' || p.oid::regprocedure::text,
             jsonb_build_object('execute', has_function_privilege(r.oid, p.oid, 'EXECUTE'))
        from pg_proc p join pg_namespace n on n.oid = p.pronamespace
       where n.nspname = 'public'
    ) o
   where r.rolname not like 'pg\_%' and not r.rolsuper
   group by r.rolname) m
SQL
)

# `SECURITY DEFINER` 함수의 **소유자**가 복원에서 바뀌면 감사 삽입이 누구 권한으로 도는지가
# 조용히 달라진다(조사 4.1 ②) — 소유자와 보안 속성을 같이 잰다. 키는 **전 서명**
# (`regprocedure`)이다 — 이름+인자 수로 잡으면 타입만 다른 오버로드가 같은 키를 들어
# `jsonb_object_agg` 가 조용히 뒤엣것만 남긴다(2026-10-05 code-review G-9).
Q_FUNCTIONS=$(cat <<'SQL'
select coalesce(jsonb_object_agg(p.oid::regprocedure::text, jsonb_build_object(
         'owner', pg_get_userbyid(p.proowner), 'securityDefiner', p.prosecdef)), '{}'::jsonb)
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
SQL
)

Q_TRIGGERS=$(cat <<'SQL'
select coalesce(jsonb_object_agg(c.relname || '.' || t.tgname, pg_get_triggerdef(t.oid)), '{}'::jsonb)
  from pg_trigger t
  join pg_class c on c.oid = t.tgrelid
  join pg_namespace n on n.oid = c.relnamespace
 where not t.tgisinternal and n.nspname = 'public'
SQL
)

Q_CONSTRAINTS=$(cat <<'SQL'
select coalesce(jsonb_object_agg(conrelid::regclass::text || '.' || conname,
                                 pg_get_constraintdef(oid)), '{}'::jsonb)
  from pg_constraint
 where connamespace = 'public'::regnamespace and conrelid <> 0
SQL
)

# 인코딩·collation 이 다른 클러스터로 복원하면 정렬과 비교가 조용히 달라진다 — 복원 쪽이
# 같은 값으로 데이터베이스를 만들도록 소유자까지 같이 담는다.
Q_DATABASE=$(cat <<'SQL'
select jsonb_build_object('owner', pg_get_userbyid(datdba),
                          'encoding', pg_encoding_to_char(encoding),
                          'collate', datcollate, 'ctype', datctype)
  from pg_database where datname = current_database()
SQL
)

# 표별 행 수와 **정렬된 행 전체의 sha256** — 표 목록은 위 질의가 발견한 것을 그대로 쓴다.
_tables_json() {
  local tables table body="" sep=""
  tables="$(_psql "$Q_TABLES")"
  while IFS= read -r table; do
    [ -n "$table" ] || continue
    _assert_plain_identifier "$table"
    body="${body}${sep}select '${table}' as name, count(*)::text as rows,"
    body="${body} coalesce(encode(sha256(convert_to(string_agg(r::text, chr(10) order by r::text),"
    body="${body} 'UTF8')), 'hex'), 'empty') as content from public.${table} r"
    sep=" union all "
  done <<EOF
$tables
EOF
  if [ -z "$body" ]; then printf '{}'; return 0; fi
  _psql "select coalesce(jsonb_object_agg(name, jsonb_build_object('rows', rows, 'contentSha256', content)), '{}'::jsonb) from ( $body ) m"
}

docker exec "$CONTAINER" pg_dumpall -U "$SUPERUSER" --roles-only --no-role-passwords \
  > "$OUT/roles.sql" || _die "역할 덤프 실패" 1
docker exec "$CONTAINER" pg_dump -U "$SUPERUSER" -d "$DATABASE" --format=custom \
  > "$OUT/db.dump" || _die "스키마·데이터 덤프 실패" 1

jq -n \
  --arg container "$CONTAINER" \
  --arg database "$DATABASE" \
  --arg takenAt "$(date -Is)" \
  --arg dumpVersion "$(docker exec "$CONTAINER" pg_dump --version)" \
  --arg rolesSha "$(sha256sum "$OUT/roles.sql" | cut -d' ' -f1)" \
  --arg dumpSha "$(sha256sum "$OUT/db.dump" | cut -d' ' -f1)" \
  --argjson database_props "$(_psql "$Q_DATABASE")" \
  --argjson flywayHistory "$(_flyway_json)" \
  --argjson tables "$(_tables_json)" \
  --argjson columns "$(_psql "$Q_COLUMNS")" \
  --argjson sequences "$(_psql "$Q_SEQUENCES")" \
  --argjson roles "$(_psql "$Q_ROLES")" \
  --argjson privilegeMatrix "$(_psql "$Q_PRIVILEGES")" \
  --argjson functions "$(_psql "$Q_FUNCTIONS")" \
  --argjson triggers "$(_psql "$Q_TRIGGERS")" \
  --argjson constraints "$(_psql "$Q_CONSTRAINTS")" \
  '{
     schema: "bidvector-db-backup/1",
     source: { container: $container, database: $database, takenAt: $takenAt, dumpVersion: $dumpVersion },
     artifacts: { "roles.sql": { sha256: $rolesSha }, "db.dump": { sha256: $dumpSha } },
     measurements: {
       database: $database_props,
       flywayHistory: $flywayHistory,
       tables: $tables,
       columns: $columns,
       sequences: $sequences,
       roles: $roles,
       privilegeMatrix: $privilegeMatrix,
       functions: $functions,
       triggers: $triggers,
       constraints: $constraints
     }
   }' > "$OUT/manifest.json" || _die "manifest 조립 실패" 1

# 표지 줄만 낸다 — 산출물 전문도, manifest 전문도 찍지 않는다.
jq -r '"db-backup: \(.source.database) — Flyway 이력 "
       + (if (.measurements.flywayHistory | type) == "string" then "ABSENT"
          else "\(.measurements.flywayHistory.rows)행 top \(.measurements.flywayHistory.topVersion)" end)
       + " · 표 \(.measurements.tables | length)"
       + " · 역할 \(.measurements.roles | length)"
       + " · 권한 행렬 \(.measurements.privilegeMatrix | length)역할"' "$OUT/manifest.json"
