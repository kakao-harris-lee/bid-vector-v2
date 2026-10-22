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
# 백업: 이 프로젝트의 postgres 가 돌고 있으면 `tools/db-backup.sh` 로 `<state>/backups/` 에 뜬다(첫 반입은
# 대상이 없어 건너뛴다). 되돌림이 마이그레이션을 넘을 때 그 백업이 복원 재료다(`tools/prod-rollback.sh`).
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

# 백업 대상
pg_cid="$("${COMPOSE[@]}" ps -q postgres 2>/dev/null || true)"
current="$(cat "$STATE/current" 2>/dev/null || true)"
echo "  현재 release: ${current:-없음(첫 반입)} · DB 백업 대상: $([ -n "$pg_cid" ] && echo '있음' || echo '없음')"

if [ "$APPLY" -eq 0 ]; then
  echo "== dry-run — 바꾸는 것 0"
  [ -n "$pg_cid" ] && _plan "tools/db-backup.sh <postgres> ${STATE}/backups/<시각>-pre-${REL}"
  _plan "previous ← ${current:-없음}"
  _plan "${COMPOSE[*]} up -d --wait --wait-timeout ${WAIT_TIMEOUT_S}"
  _plan "tools/prod-health.sh --env-file <운영 env>$([ -n "$PROJECT" ] && echo " --project ${PROJECT}")"
  _plan "current ← ${REL}"
  echo "prod-deploy: dry-run OK — --apply 로 실행한다"
  exit 0
fi

echo "== 반입"
mkdir -p "$STATE/backups"
if [ -n "$pg_cid" ]; then
  stamp="$(TZ=Asia/Seoul date '+%Y%m%dT%H%M%S')"
  bdir="$STATE/backups/${stamp}-pre-${REL}"
  ./tools/db-backup.sh "$pg_cid" "$bdir" >/dev/null || _die "반입 직전 DB 백업 실패 — 반입하지 않았다" 1
  echo "  백업: ${bdir}"
  echo "${REL} ${bdir}" >> "$STATE/backups/index"
fi
if [ -n "$current" ] && [ "$current" != "$REL" ]; then
  printf '%s\n' "$current" > "$STATE/previous"
fi
"${COMPOSE[@]}" up -d --wait --wait-timeout "$WAIT_TIMEOUT_S" || _die "up 이 healthy 로 수렴하지 않았다 — tools/prod-rollback.sh 를 검토한다" 1
health_args=(--env-file "$ENVF")
[ -z "$PROJECT" ] || health_args+=(--project "$PROJECT")
./tools/prod-health.sh "${health_args[@]}" || _die "헬스 실패 — tools/prod-rollback.sh 를 검토한다" 1
printf '%s\n' "$REL" > "$STATE/current"
echo "$(TZ=Asia/Seoul date '+%Y-%m-%dT%H:%M:%S%z') deploy ${REL} (previous ${current:-none})" >> "$STATE/history"
echo "prod-deploy: OK — current=${REL}"
