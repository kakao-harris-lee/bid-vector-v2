#!/usr/bin/env bash
# M7/7F 본편 — **반입**(one-command, D-7F2-2). 기본은 dry-run 이다 — `--apply` 가 없으면 컨테이너·볼륨·
# 상태 파일 어느 것도 바꾸지 않고 preflight 와 계획만 보인다.
#
#     ./tools/prod-deploy.sh --env-file <운영 env> --state-dir <디렉터리> --release <태그>
#                            [--project <이름>] [--apply]
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

SERVICES=(app web proxy ml-serving)
COMPOSE_MIN="2.24.0"
DISK_MIN_MB=2048
WAIT_TIMEOUT_S=300

_die() { echo "prod-deploy: $1" >&2; exit "${2:-1}"; }
_plan() { echo "  [계획] $*"; }

ENVF="" STATE="" REL="" PROJECT="" APPLY=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file) ENVF="${2:-}"; shift 2 ;;
    --state-dir) STATE="${2:-}"; shift 2 ;;
    --release) REL="${2:-}"; shift 2 ;;
    --project) PROJECT="${2:-}"; shift 2 ;;
    --apply) APPLY=1; shift ;;
    *) _die "알 수 없는 인자: $1" 2 ;;
  esac
done
[ -n "$ENVF" ] && [ -n "$STATE" ] && [ -n "$REL" ] || _die "사용법: $0 --env-file <운영 env> --state-dir <dir> --release <태그> [--project <이름>] [--apply]" 2
for t in docker jq curl stat; do command -v "$t" >/dev/null 2>&1 || _die "$t 가 필요하다" 2; done

COMPOSE=("${BIDVECTOR_PROD_COMPOSE[@]}" --env-file "$ENVF")
[ -z "$PROJECT" ] || COMPOSE+=(-p "$PROJECT")
export BIDVECTOR_RELEASE_TAG="$REL"

echo "== preflight — release ${REL}"
# env 파일
[ -r "$ENVF" ] || _die "env 파일을 읽지 못했다" 2
envabs="$(cd "$(dirname "$ENVF")" && pwd)/$(basename "$ENVF")"
case "$envabs/" in "$REPO_ROOT"/*) _die "env 파일이 저장소 안에 있다 — 저장소 밖에 둔다" 1 ;; esac
mode="$(stat -c '%a' "$ENVF")"
[ "$mode" = "600" ] || _die "env 파일 권한이 0600 이 아니다: $mode" 1
./tools/prod-env-keys-check.sh --env-file "$ENVF" >/dev/null || _die "env 파일 키 집합이 템플릿과 다르다" 1
echo "  env 파일: 저장소 밖 · 0600 · 키 집합 등식"

# manifest 와 이미지
MAN="$STATE/releases/${REL}.manifest"
[ -r "$MAN" ] || _die "release manifest 가 없다: ${REL} — tools/prod-release.sh 먼저" 1
for svc in "${SERVICES[@]}"; do
  want="$(grep -E "^image\.${svc}=" "$MAN" | cut -d= -f2-)"
  [ -n "$want" ] || _die "manifest 에 ${svc} 이미지 ID 가 없다" 1
  have="$(docker image inspect "bidvector/${svc}:${REL}" --format '{{.Id}}' 2>/dev/null || true)"
  [ "$have" = "$want" ] || _die "bidvector/${svc}:${REL} 의 ID 가 manifest 와 다르다(없거나 태그가 옮겨졌다)" 1
done
grep -qx 'vuln=not-run' "$MAN" && echo "  경고: 이 release 는 취약점 게이트를 돌리지 않았다(vuln=not-run)"
echo "  manifest: 이미지 ${#SERVICES[@]}개 ID 등식 · 커밋 $(grep '^git_sha=' "$MAN" | cut -d= -f2 | cut -c1-12)"

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
echo "  프로젝트 ${proj} · 루프백 포트 ${port} $([ -n "$holder" ] && echo '(이 프로젝트가 사용 중)' || echo '(비어 있음)')"

# 디스크
free_mb="$(df -Pm "$STATE" 2>/dev/null | awk 'NR==2 {print $4}')" || true
[ -n "$free_mb" ] || { mkdir -p "$STATE" 2>/dev/null || true; free_mb="$(df -Pm "$STATE" | awk 'NR==2 {print $4}')"; }
[ "$free_mb" -ge "$DISK_MIN_MB" ] || _die "상태 디렉터리 디스크 여유 ${free_mb}MB < ${DISK_MIN_MB}MB" 1

# 백업 대상 — 판정 술어는 **데이터 볼륨의 존재**다(실행 중 컨테이너가 아니다).
#
# `compose ps -q` 는 기본이 실행 중 컨테이너만이라, 스택이 `stop`·`down`(-v 없음)·crash 로 멈춰 있으면
# 볼륨에 데이터가 그대로 있는데도 「대상 없음」이 되어 **첫 반입과 똑같은 그림**이 됐다. 그 상태로
# 마이그레이션이 돌면 되돌릴 재료가 없다(v H-1 · CR M-1, 2026-10-31 실측).
pg_vol_key="$(printf '%s' "$cfg" | jq -r '.services.postgres.volumes[]? | select(.type=="volume") | .source' | head -n1)"
[ -n "$pg_vol_key" ] && [ "$pg_vol_key" != "null" ] || _die "postgres 의 데이터 볼륨을 해석하지 못했다(판정 불가)" 2
# 이름을 짓지 않고 compose 가 붙인 라벨로 찾는다 — 프로젝트 접두 규칙을 추측하지 않는다.
pg_vol="$(docker volume ls -q \
  --filter "label=com.docker.compose.project=${proj}" \
  --filter "label=com.docker.compose.volume=${pg_vol_key}" 2>/dev/null | head -n1 || true)"
pg_cid="$("${COMPOSE[@]}" ps -q postgres 2>/dev/null || true)"
current="$(cat "$STATE/current" 2>/dev/null || true)"
pending="$(cat "$STATE/pending" 2>/dev/null || true)"

# **지금 실제로 떠 있는 release** 는 `current` 가 아니라 `pending` 일 수 있다 — 반입이 `up`·헬스에서
# 실패하면 컨테이너는 새 release 인데 `current` 는 마지막으로 성공한 것에 머문다(v H-2 실측).
RUNNING_REL="${pending:-$current}"

if [ -n "$pg_vol" ] && [ -n "$pg_cid" ]; then
  DB_STATE=running
elif [ -n "$pg_vol" ]; then
  DB_STATE=stopped
elif [ -n "$current" ]; then
  # 반입 기록은 있는데 볼륨이 없다 — 누가 `down -v` 를 돌렸다. 백업할 데이터가 없으므로 막지는
  # 않지만, 「첫 반입」과 같은 줄로 보이면 안 된다.
  DB_STATE=volume-gone
else
  DB_STATE=first
fi
case "$DB_STATE" in
  running)     db_line="데이터 볼륨 있음 · postgres 실행 중 → 백업한다" ;;
  stopped)     db_line="데이터 볼륨 있음 · **postgres 멈춰 있음** → postgres 만 올려 백업한다" ;;
  volume-gone) db_line="**데이터 볼륨이 없다**(반입 기록은 ${current}) — 백업할 데이터가 없다" ;;
  first)       db_line="데이터 볼륨 없음 · 반입 기록 없음 → 첫 반입" ;;
esac
echo "  현재 release: ${current:-없음(첫 반입)}$([ -n "$pending" ] && echo " · **끝나지 않은 반입: ${pending}**(떠 있는 것은 이쪽)") · DB: ${db_line}"

# 마이그레이션 가드 — **되돌림만이 아니라 반입 경로에도 건다.** 되돌림은 결국 이 스크립트를 부르고
# (`prod-rollback.sh`), 운영자가 `--release <옛것>` 으로 직접 부르는 길도 있다. 그 길에 가드가 없으면
# 옛 이미지가 새 스키마 위에서 가드 없이 뜬다(v H-2).
if [ -n "$RUNNING_REL" ] && [ "$RUNNING_REL" != "$REL" ]; then
  lost="$(_prod_migrations_lost "$STATE" "$RUNNING_REL" "$REL")" \
    || _die "마이그레이션 비교에 필요한 manifest 를 읽지 못했다(떠 있는 것 ${RUNNING_REL} · 가려는 것 ${REL})" 2
  if [ -n "$lost" ]; then
    echo "  떠 있는 release ${RUNNING_REL} 가 ${REL} 에 없는 마이그레이션을 실었다: $(printf '%s' "$lost" | tr '\n' ' ')" >&2
    backup="$(grep -E "^${RUNNING_REL} " "$STATE/backups/index" 2>/dev/null | tail -n1 | cut -d' ' -f2- || true)"
    echo "  반입 직전 백업: ${backup:-기록 없음}" >&2
    _die "멈춤 — 이미지를 되돌려도 스키마는 돌아오지 않는다(runbook §7.2)" 3
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
    ;;
esac
case "$DB_STATE" in
  running|stopped)
    stamp="$(TZ=Asia/Seoul date '+%Y%m%dT%H%M%S')"
    bdir="$STATE/backups/${stamp}-pre-${REL}"
    ./tools/db-backup.sh "$pg_cid" "$bdir" >/dev/null || _die "반입 직전 DB 백업 실패 — 반입하지 않았다" 1
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
  if [ -n "$current" ] && [ "$current" != "$REL" ]; then
    echo "  되돌림: ./tools/prod-rollback.sh --env-file <운영 env> --state-dir ${STATE}$([ -n "$PROJECT" ] && echo " --project ${PROJECT}") [--apply]" >&2
  else
    echo "  되돌릴 이전 release 가 없다(첫 반입이거나 같은 release 재시도) — runbook §7 을 본다." >&2
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
echo "$(TZ=Asia/Seoul date '+%Y-%m-%dT%H:%M:%S%z') deploy ${REL} (previous ${current:-none})" >> "$STATE/history"
echo "prod-deploy: OK — current=${REL}"
