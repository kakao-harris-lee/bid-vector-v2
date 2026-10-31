#!/usr/bin/env bash
# M7/7F 본편 — **반입**(one-command, D-7F2-2). 기본은 dry-run 이다 — `--apply` 가 없으면 컨테이너·볼륨·
# 상태 파일(`current`·`previous`·`pending`·백업) 어느 것도 바꾸지 않고 preflight 와 계획만 보인다.
# (잠금은 preflight 앞에 있으므로 상태 디렉터리와 그 안의 `.lock` 은 dry-run 에서도 만들어진다.)
#
#     ./tools/prod-deploy.sh --env-file <운영 env> --state-dir <디렉터리> --release <태그>
#                            [--project <이름>] [--accept-missing-data] [--apply]
#
# `--accept-missing-data` 는 **되돌리기 어려운 탈출구**다 — 「반입 기록은 있는데 데이터 볼륨이 없다」를
# 넘어간다. 옛 볼륨의 데이터는 남은 채 새 쓰기가 다른 자리로 가므로, 왜 볼륨이 없는지 먼저 본다.
# 쓰면 `<state>/history` 줄에 표지가 남는다.
#
# 순서: preflight → (apply) 반입 직전 DB 백업 → 직전 release 기록 → `up -d --wait` → 헬스
#       (`tools/prod-health.sh`) → 현재 release 기록.
#
# preflight(하나라도 어긋나면 멈춘다):
#   · env 파일 — 저장소 밖 · 권한 0600 · 키 집합 == 템플릿(`tools/prod-env-keys-check.sh --env-file`).
#     env 파일은 compose **보간**에만 쓰인다 — 컨테이너가 받는 env 는 compose 파일이 이름으로 고른 것뿐이라,
#     env 파일에 키를 더해 app 설정(예: 쿠키 Secure)을 바꾸는 길이 없다(7D 제한 6). 키 집합 등식이 그 사실을
#     눈에 보이게 한다.
#   · release manifest 존재 · 이미지 넷의 현재 태그 ID == manifest(누가 태그를 옮겼으면 멈춘다).
#   · compose ≥ 2.24(`!override`) · 루프백 포트가 비었거나 이 프로젝트의 proxy 가 쥐고 있다 · 디스크 여유.
#
# 백업: 판정은 **데이터 볼륨의 존재**로 한다 — 실행 중 컨테이너가 아니다. 볼륨이 있으면 postgres 가
# 멈춰 있어도 DB 만 먼저 올려 `tools/db-backup.sh` 로 뜨고, 뜨지 못하면 **반입하지 않는다.** 건너뛰는
# 경우는 둘뿐이고 둘 다 출력에서 구별된다: 첫 반입(볼륨 없음·기록 없음)과 볼륨이 사라진 상태
# (`down -v` 뒤 — 백업할 데이터가 없다). 되돌림이 마이그레이션을 넘을 때 그 백업이 복원 재료다.
#
# 종료 코드: 0 · 1 preflight·반입·헬스 실패 · 2 사용법·도구·판정 불가.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

COMPOSE_MIN="2.24.0"
DISK_MIN_MB=2048
WAIT_TIMEOUT_S=300

_die() { echo "prod-deploy: $1" >&2; exit "${2:-1}"; }
_plan() { echo "  [계획] $*"; }

ENVF="" STATE="" REL="" PROJECT="" APPLY=0 ACCEPT_MISSING_DATA=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file) ENVF="${2:-}"; shift 2 ;;
    --state-dir) STATE="${2:-}"; shift 2 ;;
    --release) REL="${2:-}"; shift 2 ;;
    --project) PROJECT="${2:-}"; shift 2 ;;
    --accept-missing-data) ACCEPT_MISSING_DATA=1; shift ;;
    --apply) APPLY=1; shift ;;
    *) _die "알 수 없는 인자: $1" 2 ;;
  esac
done
[ -n "$ENVF" ] && [ -n "$STATE" ] && [ -n "$REL" ] || _die "사용법: $0 --env-file <운영 env> --state-dir <dir> --release <태그> [--project <이름>] [--accept-missing-data] [--apply]" 2
for t in docker jq curl stat; do command -v "$t" >/dev/null 2>&1 || _die "$t 가 필요하다" 2; done

COMPOSE=("${BIDVECTOR_PROD_COMPOSE[@]}" --env-file "$ENVF")
# `prod-env.sh` 가 이미 `-p <운영 이름>` 을 고정했다. 호출자가 `--project` 를 주면 **뒤에 오는 것이
# 이긴다**(실측) — 리허설·프로브가 자기 자리를 쓰는 길이다.
EXPECT_PROJECT="$BIDVECTOR_PROD_PROJECT"
if [ -n "$PROJECT" ]; then COMPOSE+=(-p "$PROJECT"); EXPECT_PROJECT="$PROJECT"; fi
export BIDVECTOR_RELEASE_TAG="$REL"

# **잠금 둘을 정해진 순서로 잡는다** — 프로젝트 먼저, 그 다음 상태 디렉터리(`tools/prod-env.sh`).
# 상태 디렉터리 하나만 잡던 앞 판은 **같은 프로젝트를 다른 state-dir 로** 동시에 반입할 수 있었다
# (v V3-4). 순서를 고정했으므로 교착은 없다. 잡지 못하면 **바로 거절**한다 — 기다리지 않는다.
mkdir -p "$STATE"
_prod_project_lock "$EXPECT_PROJECT" || _die "$_PROD_LOCK_ERR" 2
_prod_lock "$STATE/.lock" "이 상태 디렉터리" || _die "$_PROD_LOCK_ERR" 2

echo "== preflight — release ${REL}"
# env 파일
[ -r "$ENVF" ] || _die "env 파일을 읽지 못했다" 2
# **심볼릭 링크까지 풀어서** 본다 — 앞 판은 dirname 의 논리 경로만 보아, 저장소 밖에 둔 링크가
# 저장소 안 파일을 가리키면 통과했다(v L-2). 저장소 쪽도 같은 방식으로 푼다.
envabs="$(readlink -f "$ENVF")" || _die "env 파일 경로를 해석하지 못했다: $ENVF" 2
repo_phys="$(readlink -f "$REPO_ROOT")" || _die "저장소 경로를 해석하지 못했다" 2
case "$envabs/" in "$repo_phys"/*) _die "env 파일이 저장소 안에 있다(링크를 풀면 ${envabs}) — 저장소 밖에 둔다" 1 ;; esac
mode="$(stat -c '%a' "$ENVF")"
[ "$mode" = "600" ] || _die "env 파일 권한이 0600 이 아니다: $mode" 1
./tools/prod-env-keys-check.sh --env-file "$ENVF" >/dev/null || _die "env 파일 키 집합이 템플릿과 다르다" 1
echo "  env 파일: 저장소 밖 · 0600 · 키 집합 등식"

# manifest 와 이미지
MAN="$STATE/releases/${REL}.manifest"
[ -r "$MAN" ] || _die "release manifest 가 없다: ${REL} — tools/prod-release.sh 먼저" 1
# 같은 술어를 주기 러너도 쓴다(`tools/prod-env.sh`) — 반입만 보던 앞 판은 러너가 옮겨진 태그를
# 그대로 띄웠다(v V4-2).
_prod_images_match "$MAN" "$REL" || _die "$_PROD_IMAGE_ERR" 1
if grep -qx 'vuln=not-run' "$MAN"; then echo "  경고: 이 release 는 취약점 게이트를 돌리지 않았다(vuln=not-run)"; fi
echo "  manifest: 이미지 ${#BIDVECTOR_PROD_SERVICES[@]}개 ID 등식 · 커밋 $(grep '^git_sha=' "$MAN" | cut -d= -f2 | cut -c1-12)"

# compose 판
cv="$(docker compose version --short 2>/dev/null | sed 's/^v//')"
[ -n "$cv" ] || _die "docker compose 판을 읽지 못했다" 2
[ "$(printf '%s\n%s\n' "$COMPOSE_MIN" "$cv" | sort -V | head -n1)" = "$COMPOSE_MIN" ] || _die "compose ${cv} < ${COMPOSE_MIN} — !override 를 모른다" 1

# 해석 · 프로젝트 · 포트
cfg="$("${COMPOSE[@]}" config --format json 2>/dev/null)" || _die "운영 compose 를 해석하지 못했다" 1
proj="$(printf '%s' "$cfg" | jq -r .name)"
port="$(printf '%s' "$cfg" | jq -r '.services.proxy.ports[0].published')"
[[ "$port" =~ ^[0-9]+$ ]] || _die "proxy 포트를 읽지 못했다" 2
holder="$(docker ps --filter "publish=${port}" --format '{{.Label "com.docker.compose.project"}} {{.Names}}' | head -n1)"
if [ -n "$holder" ] && [ "${holder%% *}" != "$proj" ]; then
  _die "루프백 포트 ${port} 를 다른 컨테이너가 쥐고 있다: ${holder#* }" 1
fi
if [ -z "$holder" ] && (exec 3<>"/dev/tcp/127.0.0.1/${port}") 2>/dev/null; then
  _die "루프백 포트 ${port} 를 컨테이너가 아닌 프로세스가 쥐고 있다" 1
fi
# **프로젝트 이름을 단언한다.** 앞 판은 `proj` 를 읽기만 하고 그것이 운영 이름인지 보지 않았다 —
# 셸의 `COMPOSE_PROJECT_NAME` 이 이기면 preflight 가 전부 통과한 채 다른 스택으로 갔다(v V-2).
[ "$proj" = "$EXPECT_PROJECT" ] \
  || _die "해석된 프로젝트 이름이 기대와 다르다: ${proj} ≠ ${EXPECT_PROJECT} — compose 환경변수가 끼어들었다" 1
echo "  프로젝트 ${proj} · 루프백 포트 ${port} $([ -n "$holder" ] && echo '(이 프로젝트가 사용 중)' || echo '(비어 있음)')"

# 디스크
free_mb="$(df -Pm "$STATE" 2>/dev/null | awk 'NR==2 {print $4}')" || true
[ -n "$free_mb" ] || { mkdir -p "$STATE" 2>/dev/null || true; free_mb="$(df -Pm "$STATE" | awk 'NR==2 {print $4}')"; }
[ "$free_mb" -ge "$DISK_MIN_MB" ] || _die "상태 디렉터리 디스크 여유 ${free_mb}MB < ${DISK_MIN_MB}MB" 1

# 백업 대상 — 판정 술어는 **compose 가 실제로 마운트할 볼륨**과 **postgres 컨테이너의 존재**다.
#
# 앞 판은 compose 라벨로 볼륨을 찾았다. compose 는 **같은 이름의 라벨 없는 볼륨**을 경고 한 줄과 함께
# 그대로 마운트한다(손으로 만들어 복원·이전한 볼륨이 바로 그 모양이다) — 그때 라벨 필터는 「없음」이고,
# `current` 가 있으면 `volume-gone` 갈래로 가 **postgres 가 떠 있는지 보지도 않고** 백업 없이 진행했다
# (v V-1 실측: 백업 0 인 채 DB 가 적용 최대 버전까지 올라갔다).
#
# 그래서 이름을 추측하지도, 라벨에 기대지도 않는다 — **compose 가 해석한 이름**(`config` 의
# `.volumes[<키>].name`)을 그대로 `docker volume inspect` 한다. 추측이 0 이고 compose 가 쓸 값과 같다.
pg_vol_key="$(printf '%s' "$cfg" | jq -r '.services.postgres.volumes[]? | select(.type=="volume") | .source' | head -n1)"
[ -n "$pg_vol_key" ] && [ "$pg_vol_key" != "null" ] || _die "postgres 의 데이터 볼륨을 해석하지 못했다(판정 불가)" 2
pg_vol_name="$(printf '%s' "$cfg" | jq -r --arg k "$pg_vol_key" '.volumes[$k].name // empty')"
[ -n "$pg_vol_name" ] || _die "compose 가 해석한 데이터 볼륨 이름을 읽지 못했다: ${pg_vol_key}(판정 불가)" 2
pg_vol=""
docker volume inspect "$pg_vol_name" >/dev/null 2>&1 && pg_vol="$pg_vol_name"
pg_cid="$("${COMPOSE[@]}" ps -q postgres 2>/dev/null || true)"
# **멈춘 컨테이너까지** 센다 — 컨테이너가 있다는 것은 이 프로젝트가 이미 DB 를 가졌다는 뜻이고,
# 그 사실은 볼륨 조회가 어떻게 되든 백업 경로로 가는 근거다.
pg_cid_any="$("${COMPOSE[@]}" ps -aq postgres 2>/dev/null || true)"
current="$(cat "$STATE/current" 2>/dev/null || true)"
pending="$(cat "$STATE/pending" 2>/dev/null || true)"

# **지금 실제로 떠 있는 release** 는 `current` 가 아니라 `pending` 일 수 있다 — 반입이 `up`·헬스에서
# 실패하면 컨테이너는 새 release 인데 `current` 는 마지막으로 성공한 것에 머문다(v H-2 실측).
RUNNING_REL="${pending:-$current}"

# 컨테이너는 있는데 볼륨 조회가 비었다 — 둘 중 하나가 거짓이다. 추측하지 않고 멈춘다.
if [ -n "$pg_cid_any" ] && [ -z "$pg_vol" ]; then
  _die "postgres 컨테이너는 있는데 데이터 볼륨 ${pg_vol_name} 을 찾지 못했다 — 둘 중 하나가 거짓이다(판정 불가)" 2
fi
if [ -n "$pg_vol" ] && [ -n "$pg_cid" ]; then
  DB_STATE=running
elif [ -n "$pg_vol" ]; then
  # 볼륨이 있으면 컨테이너가 없어도 데이터가 있다 — 올려서 백업한다.
  DB_STATE=stopped
elif [ -n "$current" ]; then
  # 반입 기록은 있는데 볼륨이 없다 — 누가 `down -v` 를 돌렸거나 프로젝트 이름이 어긋났다.
  # **명시 플래그 없이는 멈춘다**(v V-1 처방): 그대로 진행하면 빈 DB 위에 운영이 뜨고,
  # 옛 볼륨의 데이터는 남은 채 새 쓰기가 다른 자리로 간다.
  DB_STATE=volume-gone
else
  DB_STATE=first
fi
case "$DB_STATE" in
  running)     db_line="데이터 볼륨 ${pg_vol_name} 있음 · postgres 실행 중 → 백업한다" ;;
  stopped)     db_line="데이터 볼륨 ${pg_vol_name} 있음 · **postgres 멈춰 있음** → postgres 만 올려 백업한다" ;;
  volume-gone) db_line="**데이터 볼륨 ${pg_vol_name} 이 없다**(반입 기록은 ${current})" ;;
  first)       db_line="데이터 볼륨 ${pg_vol_name} 없음 · 반입 기록 없음 → 첫 반입" ;;
esac
echo "  현재 release: ${current:-없음(첫 반입)}$([ -n "$pending" ] && echo " · **끝나지 않은 반입: ${pending}**(떠 있는 것은 이쪽)") · DB: ${db_line}"
ACCEPT_MISSING_DATA_USED=""
if [ "$DB_STATE" = volume-gone ] && [ "$ACCEPT_MISSING_DATA" -eq 1 ]; then
  ACCEPT_MISSING_DATA_USED=1
  echo "  **--accept-missing-data 로 빈 DB 위에 반입한다** — 옛 볼륨의 데이터는 남고 새 쓰기는 다른 자리로 간다"
fi
if [ "$DB_STATE" = volume-gone ] && [ "$ACCEPT_MISSING_DATA" -eq 0 ]; then
  _die "반입 기록(${current})은 있는데 데이터 볼륨이 없다 — 빈 DB 위에 반입하지 않는다. 프로젝트 이름(${proj})이 맞는지, 누가 \`down -v\` 를 돌렸는지 먼저 본다. 정말 빈 DB 로 가려면 --accept-missing-data" 1
fi

# **지금 DB 가 어디까지 올라가 있는가** — 상태 파일이 아니라 `flyway_schema_history` 를 읽는다.
# 상태 파일만 보던 앞 판은 새(또는 오타) `--state-dir` 로 부르면 「첫 반입」이 되어 가드가 통째로
# 꺼졌다(v V-3 실측). DB 는 어느 상태 디렉터리를 쓰든 하나뿐이라, 이 술어는 상태 파일이 어긋나도 산다.
#
# 자격·DB 이름은 **compose 가 해석한 값**에서 읽는다 — env 파일을 두 번째 파서로 읽던 앞 판은
# 따옴표·CRLF 값에서 컨테이너가 쓰는 값과 갈라졌다(CR r3 M-3 실측: compose 는 `probe_db`, 이 쪽은
# `"probe_db"`). `tools/db-backup.sh` 가 컨테이너의 `POSTGRES_USER` 를 쓰는 것과 같은 출처가 된다.
PG_USER="$(printf '%s' "$cfg" | jq -r '.services.postgres.environment.POSTGRES_USER // empty')"
PG_DB="$(printf '%s' "$cfg" | jq -r '.services.postgres.environment.POSTGRES_DB // empty')"
[ -n "$PG_USER" ] && [ -n "$PG_DB" ] || _die "compose 가 해석한 postgres 자격·DB 이름을 읽지 못했다(판정 불가)" 2
# **app 이 붙는 DB 와 가드가 읽는 DB 가 같아야 한다** — 둘이 갈리면 가드는 빈 DB 를 보고 통과시킨다.
#
# 술어는 URL 의 마지막 조각이 아니라 **URL 전체**다. DB 이름만 비교하던 앞 판은 두 축으로 열렸다
# (v V4-3 실측): 호스트가 `elsewhere.example` 이어도 통과했고(가드는 compose 의 postgres 를 읽고 app 은
# 다른 서버로 간다), `?currentSchema=shadow` 면 가드가 `public` 을 보고 「이력 표가 아직 없다」로 통과했다.
# 기대 형태는 운영자가 고를 수 있는 것이 아니다 — 가드가 `docker exec` 로 **이 compose 의 postgres**
# 를 읽으므로, app 도 같은 서비스·같은 DB·매개변수 없이 붙어야 둘이 같은 자리를 본다.
PG_SERVICE=postgres   # compose 서비스 이름 = 이 망 안에서의 호스트 이름
PG_PORT=5432          # 컨테이너 안 포트(운영 compose 는 publish 하지 않는다)
app_jdbc="$(printf '%s' "$cfg" | jq -r '.services.app.environment.BIDVECTOR_PERSISTENCE_JDBCURL // empty')"
[ -n "$app_jdbc" ] || _die "app 의 JDBC URL 을 읽지 못했다(판정 불가)" 2
want_jdbc="jdbc:postgresql://${PG_SERVICE}:${PG_PORT}/${PG_DB}"
if [ "$app_jdbc" != "$want_jdbc" ]; then
  # 쿼리 매개변수에는 자격이 실릴 수 있다(`?user=…&password=…`) — 문면에 축어로 옮기지 않는다.
  shown="${app_jdbc%%\?*}"; [ "$shown" = "$app_jdbc" ] || shown="${shown}?<매개변수 생략>"
  _die "app 의 JDBC URL 이 기대 형태와 다르다(${shown} ≠ ${want_jdbc}) — 가드는 이 compose 의 ${PG_SERVICE} 를 읽는다. 호스트·포트·DB·쿼리 매개변수가 모두 같아야 둘이 같은 자리를 본다(판정 불가)" 2
fi

# 읽기 결과를 **세 갈래**로 가른다: ok(버전 목록) · notable(이력 표 없음) · unreadable(접속·질의 실패).
# 앞 판은 셋이 한 갈래였고, `grep` 의 「매치 0」이 `pipefail` 로 올라와 **빈 기록도 읽기 실패**가 됐다
# — 그래서 「첫 반입이 app 에서 실패한 직후」가 자격 문제로 보고됐다(CR r3 M-1).
DB_READ="" DB_VERSIONS=""
_db_read_versions() {
  DB_VERSIONS=""; DB_READ=unreadable
  local probe out
  probe="$(docker exec "$pg_cid" psql -qAt -U "$PG_USER" -d "$PG_DB" \
    -c "select to_regclass('public.flyway_schema_history') is not null" 2>/dev/null)" || return 0
  case "$probe" in
    t) ;;
    f) DB_READ=notable; return 0 ;;
    *) return 0 ;;
  esac
  out="$(docker exec "$pg_cid" psql -qAt -U "$PG_USER" -d "$PG_DB" \
    -c "select coalesce(string_agg(version, ' ' order by version), '') from flyway_schema_history where success and version is not null" 2>/dev/null)" || return 0
  # `grep` 의 매치 0 을 실패로 올리지 않는다 — 빈 기록은 실패가 아니라 사실이다.
  DB_VERSIONS="$(printf '%s' "$out" | tr ' ' '\n' | { grep -E '^[0-9.]+$' || true; } | sort -u)"
  DB_READ=ok
}
_manifest_versions() { # <release> → manifest 가 적은 마이그레이션 버전(정렬)
  sed -n 's/^migration=V\([0-9][0-9.]*\)__.*/\1/p' "$STATE/releases/$1.manifest" 2>/dev/null | sort -u
}
# **한 함수로 둔다** — preflight 와 apply 재확인이 같은 글자를 쓴다. 앞 판은 둘이 달라서, 같은
# 읽기 실패가 한쪽에서는 exit 2 이고 다른 쪽에서는 「모르는 마이그레이션 0」이었다(v V3-1 · CR r3 M-2).
_db_guard() { # [실패 문면에 덧붙일 말]
  local tail_note="${1:-}" want ahead backup
  _db_read_versions
  case "$DB_READ" in
    unreadable)
      _die "DB 스키마를 읽지 못했다(psql 접속·질의 실패 — compose 가 해석한 ${PG_USER}@${PG_DB})${tail_note} — 판정 불가" 2 ;;
    notable)
      DB_GUARD=notable; echo "  DB 스키마: 마이그레이션 이력 표가 아직 없다(첫 기동 전)"; return 0 ;;
  esac
  if [ -z "$DB_VERSIONS" ]; then
    DB_GUARD=empty; echo "  DB 스키마: 이력 표는 있으나 적용 기록이 0 이다"; return 0
  fi
  want="$(_manifest_versions "$REL")"
  [ -n "$want" ] || _die "release ${REL} 의 manifest 에서 마이그레이션 버전을 읽지 못했다(판정 불가)" 2
  ahead="$(comm -23 <(printf '%s\n' "$DB_VERSIONS") <(printf '%s\n' "$want"))"
  DB_GUARD=checked
  echo "  DB 스키마: 적용 $(printf '%s\n' "$DB_VERSIONS" | wc -l)개 · 가려는 release 가 모르는 것 $(printf '%s' "$ahead" | grep -c . || true)개"
  if [ -n "$ahead" ]; then
    echo "  **지금 DB 가 ${REL} 에 없는 마이그레이션을 담고 있다**: $(printf '%s' "$ahead" | tr '\n' ' ')" >&2
    backup="$(grep -E "^${RUNNING_REL:-none} " "$STATE/backups/index" 2>/dev/null | tail -n1 | cut -d' ' -f2- || true)"
    echo "  반입 직전 백업: ${backup:-기록 없음}" >&2
    _die "멈춤 — 옛 이미지는 새 스키마 위에서 뜬다(Flyway 가 모르는 상위 버전을 무시하므로 조용히 뜬다)${tail_note}" 3
  fi
}
DB_GUARD=skipped
if [ -n "$pg_cid" ]; then
  _db_guard
elif [ "$DB_STATE" = stopped ]; then
  echo "  DB 스키마: postgres 가 멈춰 있어 지금은 읽지 못한다 — apply 에서 올린 뒤 다시 본다"
else
  echo "  DB 스키마: 읽을 DB 가 없다(${DB_STATE})"
fi

# 마이그레이션 가드(상태 파일 축) — **되돌림만이 아니라 반입 경로에도 건다.** 위 DB 축이 주이고
# 이것은 보조다: DB 를 읽지 못하는 때(멈춘 DB 의 dry-run)에도 상태 파일이 아는 만큼은 막는다.
if [ -n "$RUNNING_REL" ] && [ "$RUNNING_REL" != "$REL" ]; then
  lost="$(_prod_migrations_lost "$STATE" "$RUNNING_REL" "$REL")" \
    || _die "마이그레이션 비교에 필요한 manifest 를 읽지 못했다(떠 있는 것 ${RUNNING_REL} · 가려는 것 ${REL})" 2
  if [ -n "$lost" ]; then
    echo "  떠 있는 release ${RUNNING_REL} 가 ${REL} 에 없는 마이그레이션을 실었다: $(printf '%s' "$lost" | tr '\n' ' ')" >&2
    backup="$(grep -E "^${RUNNING_REL} " "$STATE/backups/index" 2>/dev/null | tail -n1 | cut -d' ' -f2- || true)"
    echo "  반입 직전 백업: ${backup:-기록 없음}" >&2
    _die "멈춤 — 이미지를 되돌려도 스키마는 돌아오지 않는다(runbook 「되돌림 — DB 를 넘는 경우」)" 3
  fi
fi

if [ "$APPLY" -eq 0 ]; then
  echo "== dry-run — 바꾸는 것 0"
  case "$DB_STATE" in
    stopped) _plan "${COMPOSE[*]} up -d --wait postgres   # 백업을 뜨려고 DB 만 먼저 올린다" ;;
  esac
  case "$DB_STATE" in
    running|stopped) _plan "tools/db-backup.sh <postgres> ${STATE}/backups/<시각>-pre-${REL}" ;;
    *)               _plan "백업 없음 — ${db_line}" ;;
  esac
  _plan "${COMPOSE[*]} up -d --wait --wait-timeout ${WAIT_TIMEOUT_S}"
  _plan "tools/prod-health.sh --env-file <운영 env>$([ -n "$PROJECT" ] && echo " --project ${PROJECT}")"
  _plan "previous ← ${current:-없음} · current ← ${REL}"
  echo "prod-deploy: dry-run OK — --apply 로 실행한다"
  exit 0
fi

echo "== 반입"
mkdir -p "$STATE/backups"
case "$DB_STATE" in
  stopped)
    # 볼륨에 데이터가 있는데 DB 가 멈춰 있다 — 백업을 뜨려면 올려야 한다. postgres 의 이미지는
    # release 태그가 아니라 compose 가 고정한 다이제스트라, 여기서 올리는 것이 새 release 의
    # 마이그레이션을 돌리지 않는다(app 을 올리지 않는다).
    "${COMPOSE[@]}" up -d --wait --wait-timeout "$WAIT_TIMEOUT_S" postgres \
      || _die "백업을 뜨려고 postgres 를 올리려 했으나 healthy 로 수렴하지 않았다 — 반입하지 않았다" 1
    pg_cid="$("${COMPOSE[@]}" ps -q postgres 2>/dev/null || true)"
    [ -n "$pg_cid" ] || _die "postgres 를 올렸으나 컨테이너를 찾지 못했다 — 반입하지 않았다" 1
    echo "  멈춰 있던 postgres 를 올렸다(백업용)"
    # **이제 DB 를 읽을 수 있다** — preflight 에서 건너뛴 DB 축 가드를 여기서 본다(app 은 아직
    # 올리지 않았으므로 마이그레이션은 돌지 않았다). **같은 함수**를 쓰므로 읽기 실패는 여기서도
    # exit 2 다 — 앞 판은 이 자리만 읽기 실패를 조용히 지나 「모르는 마이그레이션 0」을 찍었다.
    if [ "$DB_GUARD" = skipped ]; then
      _db_guard " (백업용으로 올린 postgres 는 떠 있다 — 원인을 고치고 다시 반입하거나 \`compose stop postgres\`)"
    fi
    ;;
esac
case "$DB_STATE" in
  running|stopped)
    stamp="$(TZ=Asia/Seoul date '+%Y%m%dT%H%M%S')"
    bdir="$STATE/backups/${stamp}-pre-${REL}"
    ./tools/db-backup.sh "$pg_cid" "$bdir" >/dev/null \
      || _die "반입 직전 DB 백업 실패 — 반입하지 않았다$([ "$DB_STATE" = stopped ] && echo '. 백업용으로 올린 postgres 는 떠 있다 — 원인을 고치고 다시 반입하거나 `compose stop postgres`')" 1
    echo "  백업: ${bdir}"
    echo "${REL} ${bdir}" >> "$STATE/backups/index"
    ;;
  *)
    echo "  백업 없음 — ${db_line}"
    ;;
esac
# **시도 중인 release 를 먼저 적는다.** 앞 판은 `up` 앞에서 `previous ← current` 를 적고 `current` 는
# 성공했을 때만 적었다. 그래서 반입이 실패하면 previous == current 가 되고, 떠 있는 것은 새 release 인데
# 되돌림은 「현재와 직전이 같다」로 거부했다 — 가장 급한 순간의 막다른 문면이었다(v H-2 실측).
# 이제 실패하면 `pending` 이 남아 「무엇이 떠 있는가」를 되돌림이 안다.
printf '%s\n' "$REL" > "$STATE/pending"
_deploy_failed() { # <문면>
  echo "prod-deploy: $1" >&2
  echo "  떠 있는 것은 ${REL} 이고 마지막으로 성공한 것은 ${current:-없음} 이다." >&2
  # 되돌림이 설 수 있는 경우는 둘이다 — 떠 있는 것과 마지막 성공분이 다르거나(pending 갈래),
  # 같은 release 재시도라서 `previous` 로 가는 갈래다. 앞 판은 뒤쪽을 「길이 없다」고 적었는데
  # 실제로는 선다(CR r2 L-2).
  _prev="$(cat "$STATE/previous" 2>/dev/null || true)"
  if { [ -n "$current" ] && [ "$current" != "$REL" ]; } || [ -n "$_prev" ]; then
    echo "  되돌림: ./tools/prod-rollback.sh --env-file <운영 env> --state-dir ${STATE}$([ -n "$PROJECT" ] && echo " --project ${PROJECT}") [--apply]" >&2
  else
    echo "  되돌릴 이전 release 기록이 없다(첫 반입이다) — 스택을 내리고 원인을 고친 뒤 다시 반입한다(runbook 「되돌림 — 반입이 실패했을 때」)." >&2
  fi
  exit 1
}
"${COMPOSE[@]}" up -d --wait --wait-timeout "$WAIT_TIMEOUT_S" || _deploy_failed "up 이 healthy 로 수렴하지 않았다"
health_args=(--env-file "$ENVF")
[ -z "$PROJECT" ] || health_args+=(--project "$PROJECT")
./tools/prod-health.sh "${health_args[@]}" || _deploy_failed "헬스 실패"
# 성공했을 때만 상태를 옮긴다 — previous 는 **마지막으로 성공한 직전** 이다.
if [ -n "$current" ] && [ "$current" != "$REL" ]; then
  printf '%s\n' "$current" > "$STATE/previous"
fi
printf '%s\n' "$REL" > "$STATE/current"
rm -f "$STATE/pending"
echo "$(TZ=Asia/Seoul date '+%Y-%m-%dT%H:%M:%S%z') deploy ${REL} (previous ${current:-none}${ACCEPT_MISSING_DATA_USED:+, accept-missing-data})" >> "$STATE/history"
echo "prod-deploy: OK — current=${REL}"
