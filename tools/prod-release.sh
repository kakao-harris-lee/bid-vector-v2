#!/usr/bin/env bash
# M7/7F 본편 — **release 만들기**: 운영 이미지 넷에 release 태그를 붙이고 게이트를 돌리고 manifest 를
# 남긴다(D-7F2-2). 기본은 dry-run 이다 — `--apply` 가 없으면 태그도 manifest 도 만들지 않는다.
#
#     ./tools/prod-release.sh --tag <release> --state-dir <디렉터리> --env-file <운영 env>
#                             [--source-tag <기존 태그>] [--accept-no-vuln-scan] [--apply]
#
# 운영 host 가 개발 host 와 같은 기계라(D-7F2-1 Q-1) 레지스트리·이미지 운반이 없다. 그래서 「반입
# 묶음」은 tar 가 아니라 **태그 + manifest** 다: manifest 가 이미지 ID 를 적고, 반입·되돌림이
# 그 ID 와 현재 태그를 대조한다(누가 태그를 옮겼으면 멈춘다).
#
# 이미지의 출처는 둘 중 하나다:
#   ⓐ 기본(빌드): 이 checkout 의 HEAD 에서 `:app:bootJar` → 운영 compose 의 `build` 정의로 넷을 굽는다
#      (빌드 정의는 compose 한 자리 — 7F-local cr M-5). **무거운 빌드**다 — 호스트 직렬 규칙을 따른다.
#   ⓑ `--source-tag <태그>`: 이미 있는 이미지에 release 태그를 **더한다**(원래 태그를 옮기지 않는다).
#      리허설용이다 — 그 이미지가 어느 커밋에서 왔는지를 이 스크립트는 증명하지 못하므로 manifest 에
#      `provenance=source-tag:<태그>` 로 적는다.
#
# 게이트: 이미지 위생(`tools/image-hygiene-check.sh`)은 언제나 돈다. 취약점(`tools/vuln-scan-check.sh`)은
# trivy 가 있어야 한다 — 없으면 판정 불가로 멈추고, `--accept-no-vuln-scan` 일 때만 계속하며 manifest 에
# `vuln=not-run` 을 적는다(반입 preflight 가 그 줄을 경고로 보인다). allowlist 만료가 14일 안이면 멈춘다
# (7F-local vr F-5 — 만료가 오면 네 스캔이 모두 붉다).
#
# 종료 코드: 0 · 1 게이트·검증 실패 · 2 사용법·도구·판정 불가.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

SERVICES=(app web proxy ml-serving)
# 서비스 → (위생 정책 · 취약점 kind). CI container job 의 S-22a~b3 · S-22d~e3 와 같은 대응이다.
declare -A HYGIENE_POLICY=(
  [app]=config/quality/image-hygiene-policy-app.properties
  [web]=config/quality/image-hygiene-policy-web.properties
  [proxy]=config/quality/image-hygiene-policy-proxy.properties
  [ml-serving]=config/quality/image-hygiene-policy.properties
)
declare -A VULN_KIND=([app]=app [web]=web [proxy]=proxy [ml-serving]=ml-serving)
VULN_POLICY=config/quality/vuln-policy.properties
ALLOWLIST_MIN_DAYS=14
MIGRATION_DIR=adapters/src/main/resources/db/migration
# 이 스크립트는 반입·되돌림의 상태 잠금을 **잡지 않는다** — 쓰는 것이 `releases/<태그>.manifest`
# 하나뿐이고 그 이름은 실행마다 다르며 `mv` 로 원자적으로 놓인다(같은 태그의 중복은 preflight 가
# 막는다). 잠금을 넓히면 반입 중에 다음 release 를 만들 수 없게 되는데 그럴 이유가 없다(CR r3 L-7).

_die() { echo "prod-release: $1" >&2; exit "${2:-1}"; }
_plan() { echo "  [계획] $*"; }

TAG="" STATE="" ENVF="" SOURCE_TAG="" ACCEPT_NO_VULN=0 APPLY=0
while [ "$#" -gt 0 ]; do
  case "$1" in
    --tag) TAG="${2:-}"; shift 2 ;;
    --state-dir) STATE="${2:-}"; shift 2 ;;
    --env-file) ENVF="${2:-}"; shift 2 ;;
    --source-tag) SOURCE_TAG="${2:-}"; shift 2 ;;
    --accept-no-vuln-scan) ACCEPT_NO_VULN=1; shift ;;
    --apply) APPLY=1; shift ;;
    *) _die "알 수 없는 인자: $1" 2 ;;
  esac
done
[ -n "$TAG" ] && [ -n "$STATE" ] && [ -n "$ENVF" ] || _die "사용법: $0 --tag <release> --state-dir <dir> --env-file <운영 env> [--source-tag <tag>] [--accept-no-vuln-scan] [--apply]" 2
[[ "$TAG" =~ ^[a-z0-9][a-z0-9._-]{0,62}$ ]] || _die "release 태그 형식이 아니다(소문자·숫자·._-)" 2
[ "$TAG" != "local" ] || _die "release 태그로 local 을 쓰지 않는다 — 개발 레인의 재생 태그다" 2
for t in docker git jq; do command -v "$t" >/dev/null 2>&1 || _die "$t 가 필요하다" 2; done

echo "== preflight"
# 같은 release 태그를 다시 쓰지 않는다 — manifest 와 이미지가 갈라진다.
[ ! -e "$STATE/releases/$TAG.manifest" ] || _die "release $TAG 의 manifest 가 이미 있다 — 새 태그를 쓴다" 1
for svc in "${SERVICES[@]}"; do
  if docker image inspect "bidvector/${svc}:${TAG}" >/dev/null 2>&1; then
    _die "bidvector/${svc}:${TAG} 가 이미 있다 — release 태그는 덮지 않는다" 1
  fi
done
./tools/prod-env-keys-check.sh --env-file "$ENVF" >/dev/null || _die "운영 env 파일 키 집합이 템플릿과 다르다" 1
# `--untracked-files=no` — **추적되지 않는 파일은 세지 않는다.** 그래서 manifest 의 `git_sha` 는
# 「이 커밋에서 만들었다」일 뿐 이미지 내용을 다 설명하지는 않는다: 추적되지 않는 파일도 빌드
# 컨텍스트에 들어간다(각 이미지의 `.dockerignore` 가 좁히지만 그 자체가 설명은 아니다, CR L-10).
# 재현 가능한 provenance 가 필요하면 깨끗한 clone 에서 release 를 만든다(runbook §2).
dirty="$(git status --porcelain --untracked-files=no)"
[ -z "$dirty" ] || _die "추적 파일에 커밋되지 않은 변경이 있다 — release 는 커밋 하나에서 만든다" 1
GIT_SHA="$(git rev-parse HEAD)"

# allowlist 만료 근접(vr F-5).
#
# **정본 게이트와 같은 파일을 같은 방식으로 읽는다**(v M-2). 앞 판은 경로를 하드코딩하고 `^allow\|`
# 정규식으로 읽었는데, `tools/vuln-scan-check.sh` 는 경로를 정책 키에서 읽고 **앞 공백·CR 을 지운 뒤**
# 판정한다. 그래서 앞에 공백 두 칸을 둔 등재는 게이트에는 유효하고 이 검사에는 보이지 않았다
# (2026-10-31 실측: 게이트 1 · 이 검사 0 · 만료 7일짜리가 그대로 통과).
_policy_one() { # <키 정규식> — 정확히 한 줄이어야 한다
  local v n
  v="$(sed -n "s/^$1=//p" "$VULN_POLICY")"
  n="$(printf '%s' "$v" | grep -c '' || true)"
  [ "$n" = 1 ] || _die "취약점 정책의 $1 키가 ${n} 줄이다(판정 불가)" 2
  printf '%s' "$v"
}
[ -r "$VULN_POLICY" ] || _die "취약점 정책을 읽지 못했다: ${VULN_POLICY}" 2
ALLOWLIST="$(_policy_one 'allowlist\.file')"
# 부재를 「등재 0」으로 읽지 않는다 — 정본 게이트와 같은 술어다(CR L-1). 이 자리는 프로세스 치환이라
# `pipefail` 이 닿지 않아, 파일이 사라지거나 이름이 바뀌면 조용히 「등재 0」이 됐다.
[ -r "$ALLOWLIST" ] || _die "allowlist 파일을 읽지 못했다: ${ALLOWLIST}(정책이 가리키는 파일은 실재해야 한다 — 부재를 '등재 0'으로 읽지 않는다)" 2

today_epoch="$(date +%s)"
soonest="" allow_n=0 parsed_n=0
while IFS= read -r line; do
  [ -n "$line" ] || continue
  allow_n=$((allow_n + 1))
  case "$line" in allow\|*) ;; *) _die "allowlist 행이 'allow|<kind>|<ID>|<패키지>=<만료일> <사유>' 형태가 아니다: '${line}'" 2 ;; esac
  exp="$(printf '%s' "$line" | sed -n 's/^allow|[^=]*=\([0-9][0-9-]*\).*/\1/p')"
  [ -n "$exp" ] || _die "allowlist 행에서 만료일을 읽지 못했다: '${line}'" 2
  e="$(date -d "$exp" +%s)" || _die "allowlist 만료일을 해석하지 못했다: '${exp}'" 2
  parsed_n=$((parsed_n + 1))
  if [ -z "$soonest" ] || [ "$e" -lt "$soonest" ]; then soonest="$e"; fi
done < <(sed -e 's/\r$//' -e 's/^[[:space:]]*//' "$ALLOWLIST" | grep -v '^#' | grep -v '^$' || true)
# 읽은 수와 판독한 수가 같아야 한다 — 한 줄이라도 새면 「가장 이른 만료」가 거짓이 된다.
[ "$allow_n" = "$parsed_n" ] || _die "allowlist 등재 ${allow_n} 중 ${parsed_n} 만 판독했다(판정 불가)" 2
if [ -n "$soonest" ]; then
  left=$(( (soonest - today_epoch) / 86400 ))
  [ "$left" -ge "$ALLOWLIST_MIN_DAYS" ] || _die "취약점 allowlist 의 가장 이른 만료가 ${left}일 뒤다(< ${ALLOWLIST_MIN_DAYS}) — 만료 처리 먼저(runbook §8.5)" 1
  echo "  allowlist 등재 ${allow_n} · 가장 이른 만료까지 ${left}일"
else
  # 등재가 0 이면 만료가 임박할 수 없다 — 그래도 **한 줄을 남긴다.** 말이 없으면 「검사가 통과했다」와
  # 「검사가 돌 입력이 없었다」가 같은 그림이 되고, 뒤의 것은 allowlist 를 전부 치운 날 조용히 시작된다
  # (실제로 그랬다: 등재 8 → 0 이 되며 이 자리가 말을 멈췄다).
  echo "  allowlist 등재 0 — 만료 검사를 건너뛴다(검사할 입력이 없다)"
fi

VULN_RUN=1
if ! command -v trivy >/dev/null 2>&1; then
  [ "$ACCEPT_NO_VULN" -eq 1 ] || _die "trivy 가 없다 — 취약점 게이트를 돌릴 수 없다(설치하거나, 리허설이면 --accept-no-vuln-scan)" 2
  VULN_RUN=0
  echo "  경고: 취약점 게이트를 돌리지 않는다(--accept-no-vuln-scan) — manifest 에 vuln=not-run"
fi
mapfile -t MIGRATIONS < <(git ls-tree --name-only "HEAD:${MIGRATION_DIR}" | grep -E '^V[0-9]+__.*\.sql$' | sort -V)
[ "${#MIGRATIONS[@]}" -gt 0 ] || _die "마이그레이션 목록이 비었다 — 경로가 틀렸다(판정 불가)" 2
echo "  커밋 ${GIT_SHA:0:12} · 마이그레이션 ${#MIGRATIONS[@]}개 · 출처 $([ -n "$SOURCE_TAG" ] && echo "source-tag:${SOURCE_TAG}" || echo build)"

if [ -n "$SOURCE_TAG" ]; then
  for svc in "${SERVICES[@]}"; do
    docker image inspect "bidvector/${svc}:${SOURCE_TAG}" >/dev/null 2>&1 || _die "원본 이미지가 없다: bidvector/${svc}:${SOURCE_TAG}" 2
  done
fi

if [ "$APPLY" -eq 0 ]; then
  echo "== dry-run — 바꾸는 것 0"
  if [ -n "$SOURCE_TAG" ]; then
    for svc in "${SERVICES[@]}"; do _plan "docker tag bidvector/${svc}:${SOURCE_TAG} bidvector/${svc}:${TAG}"; done
  else
    _plan "./gradlew --no-daemon :app:bootJar"
    _plan "BIDVECTOR_RELEASE_TAG=${TAG} ${BIDVECTOR_PROD_COMPOSE[*]} --env-file <운영 env> --profile ml build ${SERVICES[*]}"
  fi
  for svc in "${SERVICES[@]}"; do _plan "위생 게이트 bidvector/${svc}:${TAG}"; done
  # `A && for …` 가 아니라 `if`다 — 이 꼴이 블록의 마지막 명령이 되면 `set -e` 아래에서 dry-run 이
  # 조용히 exit 1 이 된다(CR L-5).
  if [ "$VULN_RUN" -eq 1 ]; then
    for svc in "${SERVICES[@]}"; do _plan "취약점 게이트 bidvector/${svc}:${TAG}"; done
  fi
  _plan "manifest → ${STATE}/releases/${TAG}.manifest"
  echo "prod-release: dry-run OK — --apply 로 실행한다"
  exit 0
fi

# 태그를 붙인 뒤 게이트가 실패하면 **태그만 남고 manifest 가 없는 막다른 상태**가 됐다. preflight 의
# 「이미 있다」가 같은 태그의 재시도를 막는데, runbook 의 태그 규칙은 커밋당 결정적이라 같은 커밋에서
# 원인을 고친 뒤 쓸 이름이 없었다(CR M-3). 이 실행이 붙인 태그는 이 실행이 거둔다.
RELEASE_TAGGED=0
_cleanup_release_tags() {
  local rc=$?
  [ "$RELEASE_TAGGED" -eq 1 ] || return 0
  local svc
  for svc in "${SERVICES[@]}"; do docker image rm "bidvector/${svc}:${TAG}" >/dev/null 2>&1 || true; done
  echo "prod-release: 실패(exit ${rc}) — 이 실행이 붙인 release 태그 ${TAG} 를 거뒀다. 원인을 고친 뒤 **같은 태그로 다시** 돌릴 수 있다" >&2
}
trap _cleanup_release_tags EXIT

echo "== 이미지"
if [ -n "$SOURCE_TAG" ]; then
  for svc in "${SERVICES[@]}"; do docker tag "bidvector/${svc}:${SOURCE_TAG}" "bidvector/${svc}:${TAG}"; done
  RELEASE_TAGGED=1
else
  # 빌드가 끝나야 태그가 생긴다 — 그 전에 표시를 올리면 아무 태그도 없는데 「거뒀다」가 찍힌다(CR r2 L-6).
  ./gradlew --no-daemon :app:bootJar
  RELEASE_TAGGED=1
  BIDVECTOR_RELEASE_TAG="$TAG" "${BIDVECTOR_PROD_COMPOSE[@]}" --env-file "$ENVF" --profile ml build "${SERVICES[@]}"
fi

echo "== 게이트"
for svc in "${SERVICES[@]}"; do
  ./tools/image-hygiene-check.sh "bidvector/${svc}:${TAG}" "${HYGIENE_POLICY[$svc]}" >/dev/null \
    || _die "위생 게이트 실패: bidvector/${svc}:${TAG}" 1
  echo "  위생 ${svc}: OK"
  if [ "$VULN_RUN" -eq 1 ]; then
    ./tools/vuln-scan-check.sh "bidvector/${svc}:${TAG}" "${VULN_KIND[$svc]}" config/quality/vuln-policy.properties >/dev/null \
      || _die "취약점 게이트 실패: bidvector/${svc}:${TAG}" 1
    echo "  취약점 ${svc}: OK"
  fi
done

mkdir -p "$STATE/releases"
tmp="$STATE/releases/.${TAG}.manifest.tmp"
{
  echo "release=${TAG}"
  echo "git_sha=${GIT_SHA}"
  echo "created_kst=$(TZ=Asia/Seoul date '+%Y-%m-%dT%H:%M:%S%z')"
  echo "provenance=$([ -n "$SOURCE_TAG" ] && echo "source-tag:${SOURCE_TAG}" || echo build)"
  echo "vuln=$([ "$VULN_RUN" -eq 1 ] && echo pass || echo not-run)"
  for svc in "${SERVICES[@]}"; do
    echo "image.${svc}=$(docker image inspect "bidvector/${svc}:${TAG}" --format '{{.Id}}')"
  done
  for m in "${MIGRATIONS[@]}"; do echo "migration=${m}"; done
} > "$tmp"
# **먼저 내리고 옮긴다.** 반대 순서면 `mv` 와 `RELEASE_TAGGED=0` 사이에서 죽었을 때 manifest 는
# 있는데 태그는 trap 이 거둔 상태가 되고, 그 뒤 반입은 「ID 가 manifest 와 다르다」로, release 재시도는
# 「manifest 가 이미 있다」로 둘 다 막힌다(CR r2 L-6).
RELEASE_TAGGED=0
mv "$tmp" "$STATE/releases/${TAG}.manifest"
echo "prod-release: OK — ${STATE}/releases/${TAG}.manifest"
