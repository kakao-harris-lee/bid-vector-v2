#!/usr/bin/env bash
# M15/15U — 운영 스택에서 **공고 목록 수집 일회 러너**를 한 번 돌린다(runbook §14).
#
#     ./tools/prod-collection.sh --env-file <운영 env> --koneps-env-file <서비스 키 env> \
#         --state-dir <디렉터리> --run-state-root <host 절대 경로> \
#         { --days-back <N> | --from <YYYY-MM-DD> --to <YYYY-MM-DD> } \
#         --categories <소문자,쉼표> --calls-per-day <N> --calls-total <N> [--project <이름>]
#
# 러너 자체(무엇을 수집하는가 · 종료 코드 다섯 · 슬롯 줄의 회계 · 멈춤 줄)의 정본은
# `docs/runbook/m6-6e-operations.md` §1.3·§3.3 이고 실수집 절차의 선례는 `m6-6g-real-collection.md`
# §2-1 이다. 이 스크립트가 더하는 것은 **운영 스택에서 그것을 거는 법**뿐이고, 모양은
# `tools/prod-evaluation-commit.sh`(§13.3)와 같다 — 같은 compose 파일 셋 · 같은 프로젝트 이름 · 같은
# env 파일 · release 태그는 `<state>/current` 에서 · `--pull never` · 잠금 둘(같은 순서) · manifest
# 이미지 ID · 반입 트리 == 그 release 의 커밋 · 설정 통로 · 일회 컨테이너 잔존·이름 충돌
# (`<프로젝트>-app-run-collection`) · 신호 trap · 금지 JVM 옵션 · 값 없는 플래그 = 3.
# **반입이 막는 것을 이 러너만 통과시키는 길을 만들지 않는다.**
#
# **훑기·평가 커밋과 다른 것은 셋이다.**
#
# ① **env 파일이 둘이다**(relay 와 같은 축). `--env-file` 은 스택 설정(`prod.env` — compose 가 요구하는
#    키 집합)이고 `--koneps-env-file` 은 **조달청 서비스 키**(`koneps.env` — compose 가 모르는 키라
#    템플릿에 넣지 않는다)다. 컨테이너까지 가는 통로는 `run --env-from-file` 하나이고(최상위
#    `--env-file` 은 보간에만 쓰여 컨테이너에 닿지 않는다 — M15/15Q 실측), 그 파일은 서비스가 선언한
#    env 를 **이기므로** 키 집합을 등식으로 닫는다. 술어는 relay 와 **같은 것**이다
#    (`tools/prod-env.sh` 의 `_prod_second_env_ok`) — 사본 둘을 두지 않는다. 허용 이름은 손 목록이
#    아니라 **`bidvector.koneps.service-key` 가 받는 환경 변수 이름 둘**이다(Spring 의 두 사상).
#    **값은 argv·로그·오류 문면 어디에도 나오지 않는다.**
#
# ② **실행 상태 디렉터리를 host 에서 붙인다.** app 이미지의 사용자는 uid/gid 10001 이고 일회 컨테이너에
#    쓰기 가능한 볼륨이 없다 — 시도 원장과 상한 회계가 사는 자리를 host 에서 줘야 한다. 붙이는 원천은
#    `--run-state-root` 아래 **래퍼가 지은 한 경로**(`notice-list/<YYYY-MM>`)뿐이고 컨테이너 안 경로는
#    고정이다. 달로 가르는 사유: `--calls-total` 은 **그 디렉터리 생애의 누적**이라(`AttemptHistory.spend`
#    가 시도 원장 전체의 줄을 센다) 끝없이 도는 운영에서 한 디렉터리를 쓰면 언젠가 닿고 그 뒤 매 run 이
#    `INCOMPLETE(2)` 다. 달마다 가르면 그 값이 **월 상한**이 되고 원장 크기도 달 단위로 묶인다 — 러너를
#    고치지 않고 닫는 길이다. **`_prod_config_surface_ok` 는 `config` 출력을 보므로 `run -v` 를 보지
#    못한다** — 그래서 인자가 붙임 문법(`:`)을 실어 둘째 붙임이나 `:ro` 뒤집기를 만들지 못하게 **인자
#    쪽에서** 닫는다(아래 `--run-state-root` 검사).
#
# ③ **창과 상한은 인자다 — 기본값을 지어내지 않는다.** 러너의 `from`·`to`·`categories`·`calls-per-day`·
#    `calls-total`·`run-state-dir` 여섯에 기본값이 없고 미설정이 기동 실패다(m6-6e §1.5). 창은 둘 중
#    하나를 요구한다: `--from/--to`(첫 적재·메우기) 또는 `--days-back <N>`(주기 — `to` = 오늘 KST,
#    `from` = 오늘-N). **범위 상한(31일)·미래 금지는 러너가 기동 실패로 드러내는 것을 그대로 둔다** —
#    같은 술어를 셸에 사본으로 적지 않는다(그 사본은 러너 정책이 바뀌는 날 조용히 거짓이 된다).
#
# 종료 코드 — **러너의 다섯과 래퍼의 둘이 겹치지 않게 옮겼다**(relay 와 같은 축, 다른 수).
# `CollectionExitCode` 는 0·1·2·3(`ALREADY_RUNNING`)·4(`UNLOCKABLE`) 다섯인데(m6-6e §3.3) 래퍼 선례는
# 3 을 전제 실패에, 4 를 미룸에 쓴다. 그대로 두면 **뜻이 정확히 반대인 둘**이 같은 수로 만난다:
# 래퍼 3(고쳐야 한다) ↔ 러너 3(기다리면 풀린다) · 래퍼 4(기다리면 풀린다) ↔ 러너 4(고쳐야 한다).
# §9.5 의 경보는 종료 코드로만 갈라지므로 그 겹침은 **경보를 정확히 거꾸로** 울린다.
#
#   0 `COMPLETE` · 1 `FAILED` · 2 `INCOMPLETE`     — 러너의 것 그대로
#   3 이 스크립트의 전제 실패                        — 훑기와 같은 목록 + 창·상한 인자 + 서비스 키 env 축
#   4 **미뤘다**(잠금 경합 — 설계된 정상, §9.1)
#   5 러너 `ALREADY_RUNNING`(옛 3)                   — 다른 프로세스가 실행 상태 디렉터리를 쥐었다
#   6 러너 `UNLOCKABLE`(옛 4)                        — 자물쇠를 걸 수 없다. 경로·권한을 고친다
#   7 **모르는 코드**                                 — 러너의 다섯 밖. 경보를 울린다
#
# **`5` 를 경보에서 빼지 않는다**(relay 의 `5` 와 다른 판단이다). 래퍼의 잠금 둘 아래에서 러너의
# `ALREADY_RUNNING` 이 서는 경로는 **래퍼를 거치지 않은 실행**(손으로 띄운 `java -jar`·컨테이너)이 같은
# 실행 상태 디렉터리를 쥔 경우뿐이다 — 래퍼를 거친 손 실행은 프로젝트 잠금에서 `4` 로 먼저 미룬다. 즉
# `5` 는 설계된 정상이 아니라 사람이 알아야 하는 어긋남이고, 그래서 `10-defer.conf` 는 `4` 만 뺀다.
# `INCOMPLETE(2)` 와 `UNLOCKABLE`(→`6`)도 빼지 않는다 — 전자는 일 상한·쿼터·절단으로 그날 공고가 덜
# 들어온 것이고 후자는 기다려도 풀리지 않는다.
#
# **「신호 종료」는 두 자리에서 뜻이 다르다.** ⓐ **래퍼 자신**이 TERM·INT·HUP 를 받으면 trap 이 받아 이
# run 의 일회 컨테이너를 치우고 같은 신호로 자살한다(128+n) — 그 경로는 **아래 `case` 에 닿지 않는다**.
# ⓑ **일회 컨테이너**가 KILL·TERM 으로 죽으면 `docker compose run` 이 128+n 을 **돌려주므로** 그 값은
# `case` 로 와서 `7` 이 된다. 아래 `*)` 문면이 말하는 것은 ⓑ 다.
# 호출 모양·unit·타이머·활성화 절차의 정본은 runbook §14 다.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=tools/prod-env.sh
. "$REPO_ROOT/tools/prod-env.sh"

_die() { echo "prod-collection: $1" >&2; exit "${2:-3}"; }

# **저장소 포함 판정은 실경로 대 실경로다**(M15/15U r1 — cr medium · vr M1). 위 `REPO_ROOT` 는 `pwd` 가
# 심링크를 유지하는 **논리** 경로이고(선례 네 스크립트와 `tools/prod-env.sh` 가 같은 꼴이다) 비교 대상은
# `readlink -f` 로 **해석된** 경로다. 둘을 그대로 맞대면 심링크된 저장소 경로로 래퍼를 부르는 배치에서
# 저장소 **안**을 가리키는 자리가 통과한다(실측: 논리 비교 MISSED · 실경로 비교 CAUGHT). runbook 은
# `WorkingDirectory=<저장소>` 가 심링크인 배치를 배제하지 않는다.
#
# `REPO_ROOT` 자신을 `pwd -P` 로 바꾸지 않는 이유: 그 값은 `cd` 와 compose 파일 셋 읽기에도 쓰이고 선례
# 넷이 같은 꼴이라, 이 slice 가 그 cwd 의 뜻을 혼자 바꾸면 래퍼들의 모양이 갈린다. 그래서 **비교만**
# 양쪽에 건다 — 논리든 실경로든 어느 쪽에 걸려도 거절이다(겹 둘).
REPO_ROOT_REAL="$(readlink -f "$REPO_ROOT" 2>/dev/null || printf '%s' "$REPO_ROOT")"

# <실경로> <무엇> <원문 경로> → 저장소 안이면 전제 실패(3). 실험 입력은 커밋되지 않는다.
# 러너도 같은 판단을 하지만(`requireOutsideRepository`) **이 배치에서는 아무것도 보지 못한다**(vr L5):
# 컨테이너가 받는 경로는 고정된 `/var/lib/bidvector-run-state` 이고 그 안에 저장소 표식이 없으므로,
# host 쪽 실경로가 저장소 안이어도 러너 쪽 가드는 통과한다. **그래서 이 래퍼가 유일한 겹이다.**
_collection_outside_repository() {
  local abs="$1" what="$2" raw="$3" root
  for root in "$REPO_ROOT" "$REPO_ROOT_REAL"; do
    case "$abs/" in
      "$root"/*)
        _die "${what} 가 저장소 안이다(${raw} → ${abs}) — 실험 입력은 커밋되지 않는다. 저장소 밖의 자리를 준다(runbook §14 ①)" 3 ;;
    esac
  done
}

# **값 없는 플래그는 전제 실패(3)다**(M15/15Q cr H-1) — `$# == 1` 인데 `shift 2` 를 하면 bash 가 비-0 을
# 내고 `set -e` 아래에서 스크립트가 **그 자리에서 1 로, 아무 말 없이** 죽는다. 그 `1` 은 러너의
# `FAILED`(예외·기동 거부)와 같은 값이라 §9.5 의 경보가 틀린 이야기를 하고 journal 에 단서가 한 줄도
# 없다. 도달 경로는 §14 의 `ExecStart=` 자신이다(자리표시자를 **바꾸지 않고 지운** 운영자는 플래그를
# argv 의 마지막 원소로 남긴다). 네 래퍼가 같은 가드를 쓴다.
_need() { [ "$#" -ge 2 ] || _die "$1 에 값이 없다 — 플래그 뒤에 값을 둔다" 3; }

# **컨테이너 안 실행 상태 경로는 고정이다** — 인자에서 오지 않는다(붙임의 대상 쪽을 운영자가 고를 수
# 있으면 둘째 붙임·덮어쓰기의 자리가 생긴다). 이미지 안에 이 경로를 쓰는 다른 자리가 없고, 저장소 표식
# (`settings.gradle.kts`·`.git`)이 없는 자리라 러너의 「실험 입력은 저장소 밖」 가드도 지난다.
COLLECTION_RUN_STATE_IN_CONTAINER=/var/lib/bidvector-run-state
# 실행 상태 root 아래 **이 갈래의 자리**. 개찰·낙찰 갈래와 섞이지 않게 갈래 이름을 한 칸 둔다.
COLLECTION_BRANCH_DIR=notice-list

ENVF="" KONEPSENVF="" STATE="" PROJECT="" RUN_STATE_ROOT=""
FROM="" TO="" DAYS_BACK="" CATEGORIES="" CALLS_PER_DAY="" CALLS_TOTAL=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file) _need "$@"; ENVF="$2"; shift 2 ;;
    --koneps-env-file) _need "$@"; KONEPSENVF="$2"; shift 2 ;;
    --state-dir) _need "$@"; STATE="$2"; shift 2 ;;
    --project) _need "$@"; PROJECT="$2"; shift 2 ;;
    --run-state-root) _need "$@"; RUN_STATE_ROOT="$2"; shift 2 ;;
    --from) _need "$@"; FROM="$2"; shift 2 ;;
    --to) _need "$@"; TO="$2"; shift 2 ;;
    --days-back) _need "$@"; DAYS_BACK="$2"; shift 2 ;;
    --categories) _need "$@"; CATEGORIES="$2"; shift 2 ;;
    --calls-per-day) _need "$@"; CALLS_PER_DAY="$2"; shift 2 ;;
    --calls-total) _need "$@"; CALLS_TOTAL="$2"; shift 2 ;;
    *) _die "알 수 없는 인자: $1" 3 ;;
  esac
done
_usage="사용법: $0 --env-file <운영 env> --koneps-env-file <서비스 키 env> --state-dir <dir> --run-state-root <host 절대 경로> { --days-back <N> | --from <YYYY-MM-DD> --to <YYYY-MM-DD> } --categories <소문자,쉼표> --calls-per-day <N> --calls-total <N> [--project <이름>]"
[ -n "$ENVF" ] && [ -n "$KONEPSENVF" ] && [ -n "$STATE" ] && [ -n "$RUN_STATE_ROOT" ] \
  && [ -n "$CATEGORIES" ] && [ -n "$CALLS_PER_DAY" ] && [ -n "$CALLS_TOTAL" ] || _die "$_usage" 3

# ── 창 — 둘 중 하나다. 조용히 하나를 고르지 않는다 ─────────────────────────────
# 둘을 함께 받으면 어느 쪽을 쓰는지가 **읽는 사람에게** 보이지 않고, 하나도 없으면 러너가 기동 실패(1)로
# 끝나 그 값이 「예외」와 섞인다. 둘 다 전제 실패(3)다.
if [ -n "$DAYS_BACK" ]; then
  [ -z "$FROM" ] && [ -z "$TO" ] \
    || _die "--days-back 과 --from/--to 를 함께 줄 수 없다 — 창을 정하는 자리는 하나다" 3
  # `0`·음수·`031`(앞 0)·공백·빈 값을 모두 거절한다. 0 은 「창이 없다」가 아니라 **하루 창**으로
  # 읽히기 쉬운 값이고(그 뜻은 `--from/--to` 로 적는다), 앞 0 은 `date` 가 받아도 운영자가 읽는 수와
  # 다르다. **31 을 넘는 것은 러너 몫이다**(같은 술어를 셸에 베껴 적지 않는다).
  [[ "$DAYS_BACK" =~ ^[1-9][0-9]{0,2}$ ]] \
    || _die "--days-back 은 1 이상의 정수다(앞 0·공백·음수를 받지 않는다): ${DAYS_BACK}" 3
  # **「오늘」은 KST 로 한 번만 짓는다**(host 의 TZ 에 기대지 않는다). 달 디렉터리도 같은 호출에서
  # 나오므로, 달이 바뀌는 자정에 걸친 run 도 **시작 때 정한 하나**를 끝까지 쓴다.
  TO="$(TZ=Asia/Seoul date +%F)" || _die "오늘(KST)을 읽지 못했다" 3
  FROM="$(TZ=Asia/Seoul date -d "$TO -${DAYS_BACK} days" +%F)" \
    || _die "창의 시작일을 계산하지 못했다(--days-back ${DAYS_BACK})" 3
else
  [ -n "$FROM" ] && [ -n "$TO" ] \
    || _die "창이 없다 — --days-back <N> 또는 --from <날짜> --to <날짜> 를 준다. ${_usage}" 3
fi
# **모양만 본다** — 실존하는 날짜인지·범위가 31일 이하인지·미래인지는 러너가 기동 실패로 말한다.
for _d in "$FROM" "$TO"; do
  [[ "$_d" =~ ^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$ ]] \
    || _die "날짜는 YYYY-MM-DD 모양이다: ${_d}" 3
done
unset _d
# **업종은 소문자와 쉼표뿐이다**(인자 주입 거절). 러너 인자는 아래에서 배열로 넘어가 낱말 쪼개짐이
# 일어나지 않지만, 그 사실에 기대지 않고 값의 모양으로도 끊는다(겹 둘) — 표에 없는 업종 이름은 러너가
# 기동 실패로 말한다(그 목록을 셸에 베껴 적지 않는다).
[[ "$CATEGORIES" =~ ^[a-z]+(,[a-z]+)*$ ]] \
  || _die "--categories 는 소문자 업종 이름을 쉼표로 이은 것이다(공백·대문자·빈 칸 없이): ${CATEGORIES}" 3
for _n in "$CALLS_PER_DAY" "$CALLS_TOTAL"; do
  [[ "$_n" =~ ^[1-9][0-9]{0,6}$ ]] \
    || _die "--calls-per-day·--calls-total 은 1 이상의 정수다(러너가 상한 0 을 기동 실패로 본다): ${_n}" 3
done
unset _n
[ -r "$ENVF" ] || _die "운영 env 파일을 읽지 못했다: $ENVF" 3

# ── 실행 상태 root — 붙임의 원천을 묶는다(인자 검사) ──────────────────────────
# `run -v <원천>:<대상>` 의 원천은 **이 root 아래 래퍼가 지은 한 경로**뿐이고, 대상은 위의 고정 경로다.
# 그래서 인자가 할 수 있는 것은 root 를 고르는 것까지다 — 아래 넷이 그 범위를 닫는다.
# **디렉터리를 만드는 것은 여기가 아니다**(M15/15U r1, cr medium) — 전제 검사 뒤로 옮겼다. 앞 판은
# 거절되는 run 과 **미룬 run** 도 운영자의 root 아래에 자리를 남겼고, 그것은 「그 거절이 … 앞에서
# 끝난다」는 이 slice 의 문면과 어긋났다.
case "$RUN_STATE_ROOT" in
  /*) ;;
  *) _die "--run-state-root 는 절대 경로다(상대 경로는 부른 자리에 따라 다른 자리를 가리킨다): ${RUN_STATE_ROOT}" 3 ;;
esac
# **`:` 는 붙임 문법이다** — root 에 그 글자가 있으면 `-v` 인자가 둘째 붙임이나 `:ro` 같은 옵션을 실어
# 붙임의 **뜻**을 바꿀 수 있다. `_prod_config_surface_ok` 는 `config` 출력을 보므로 이 자리를 보지
# 못한다(그 사실이 이 거절의 사유다 — 「경계로 처리」가 아니라 여기서 닫는다).
case "$RUN_STATE_ROOT" in
  *:*) _die "--run-state-root 에 ':' 가 있다 — 그 글자는 docker 의 붙임 문법이라 둘째 붙임·옵션을 만들 수 있다. ':' 없는 자리에 둔다: ${RUN_STATE_ROOT}" 3 ;;
esac
# **만들지 않는다** — 경로를 잘못 대면 상한 회계가 0 에서 다시 서고(러너도 같은 사유로 디렉터리를 만들지
# 않는다) 운영자는 그것을 알 길이 없다. 소유·권한을 세우는 것은 운영자의 한 번이다(§14.1).
[ -d "$RUN_STATE_ROOT" ] \
  || _die "실행 상태 root 가 없거나 디렉터리가 아니다: ${RUN_STATE_ROOT} — 만들지 않는다(경로를 잘못 대면 상한 회계가 0 에서 다시 선다). runbook §14 ① 이 그 자리를 만든다" 3
RUN_STATE_ABS="$(readlink -f "$RUN_STATE_ROOT" 2>/dev/null || true)"
[ -n "$RUN_STATE_ABS" ] || _die "실행 상태 root 의 절대 경로를 구하지 못했다: ${RUN_STATE_ROOT}" 3
_collection_outside_repository "$RUN_STATE_ABS" "실행 상태 root" "${RUN_STATE_ROOT}"
case "$RUN_STATE_ABS" in
  *:*) _die "실행 상태 root 의 실경로에 ':' 가 있다 — 붙임 문법이라 받지 않는다: ${RUN_STATE_ABS}" 3 ;;
esac
# **달 경로는 여기서 계산만 한다.** 달 문자열은 위의 `TO`(KST)에서 잘라 쓰므로 창 계산과 **같은
# 「오늘」**이고, 달이 바뀌는 자정에 걸친 run 도 한 값을 끝까지 쓴다. `--from/--to` 갈래에서는 그 `to` 가
# 달을 정한다(메우기를 그 달의 회계에 붙인다).
RUN_STATE_PARENT="${RUN_STATE_ABS}/${COLLECTION_BRANCH_DIR}"
RUN_STATE_DIR="${RUN_STATE_PARENT}/${TO%-*}"

# ── 서비스 키 env — 키 집합을 닫는다 ───────────────────────────────────────────
# 술어는 relay 와 **같은 것**이다(`tools/prod-env.sh` — 사본 둘은 한쪽만 고쳐지는 날이 온다). 여기 남는
# 것은 **이 파일의 속성 목록**뿐이고, 허용 환경 변수 이름은 그 이름에서 Spring 의 두 사상으로 나온다
# (`BIDVECTOR_KONEPS_SERVICEKEY` · `BIDVECTOR_KONEPS_SERVICE_KEY`).
#
# **`bidvector.koneps` 의 나머지 키는 이 파일의 것이 아니다.** `base-url`·`operations.*` 는 공개 설정이고
# 그 통로는 compose 다(설정 통로 등식이 보는 자리) — 여기 들어오면 **반입 가드를 거치지 않은 채** 조회
# 주소를 바꾸는 길이 되므로 키 집합 밖으로 거절된다.
BIDVECTOR_SECOND_ENV_REQUIRED_PROPS=(bidvector.koneps.service-key)
BIDVECTOR_SECOND_ENV_OPTIONAL_PROPS=()
# **신호 trap 을 해석 앞에 건다** — 해석은 `$( )` 안에서 돌아 서브셸이므로 임시 자리는 부모가 치운다.
# 아래 `_collection_on_signal` 이 이 trap 을 나중에 바꿔 단다.
_prod_stub_trap_install
_prod_second_env_ok "$KONEPSENVF" "서비스 키 env" "§14.1" || _die "$_PROD_SECOND_ENV_ERR" 3
# **재는 경로와 넘기는 경로가 같다** — 해석·권한·금지 속성 스캔·컨테이너 전달이 이 한 값을 쓴다.
KONEPS_ABS="$_PROD_SECOND_ENV_ABS"

# **금지되는 JVM 옵션** — 술어는 다른 래퍼들과 **같은 것**이다(`tools/prod-env.sh`). JDK HttpClient 의
# 내부 로그 속성 둘이 지정되면 요청 URL 이 stderr 로 나가고, 이 갈래의 URL 은 **질의 문자열에 조달청
# 서비스 키를 싣는다**(`ServiceKey` 가 URL 인코딩해 붙인다). 러너 자신도 기동을 거부하지만 그 거부는
# 종료 1 이라 러너의 「예외」와 섞인다 — 여기서 **전제 실패(3)** 로 먼저 끊는다. 보는 자리는 **해석된
# compose 가 못 보는 둘**이다: 이 프로세스의 JVM 옵션 통로와 `koneps.env`.
_prod_jvm_env_log_free || _die "${_PROD_HTTPCLIENT_LOG_ERR}(runbook §14)" 3
_prod_httpclient_log_free_file "$KONEPS_ABS" || _die "${_PROD_HTTPCLIENT_LOG_ERR}" 3
_prod_compose_version_ok || _die "$_PROD_COMPOSE_VER_ERR" 3

# **반입·되돌림·훑기와 같은 잠금 둘을 같은 순서로 잡는다**(`tools/prod-env.sh`). 수집은 같은 창의
# 재수집이 `notice` 에는 멱등이므로 **겹치면 돌지 않고 다음 주기로 미뤄도 된다** — 반입 중에 옛
# `current` 로 일회 컨테이너를 띄우는 길은 잠금만이 막는다.
#
# **경합은 `4`, 전제 실패는 `3`**(M15/15M, cr H-1). 가르는 값은 문면이 아니라 `_PROD_LOCK_BUSY` 다.
_deferred_or_precondition() { [ -n "${_PROD_LOCK_BUSY:-}" ] && echo 4 || echo 3; }
PROJ_FOR_LOCK="${PROJECT:-$BIDVECTOR_PROD_PROJECT}"
_prod_project_lock "$PROJ_FOR_LOCK" || _die "$_PROD_LOCK_ERR" "$(_deferred_or_precondition)"
_prod_lock "$STATE/.lock" "이 상태 디렉터리" || _die "$_PROD_LOCK_ERR" "$(_deferred_or_precondition)"

rel="$(cat "$STATE/current" 2>/dev/null || true)"
[ -n "$rel" ] || _die "반입 기록이 없다($STATE/current) — 반입 전에는 수집할 스택이 없다" 3
_prod_valid_tag "$rel" \
  || _die "반입 기록이 release 태그 모양이 아니다($STATE/current) — 손상됐다. 반입 기록을 먼저 본다" 3

pending="$(cat "$STATE/pending" 2>/dev/null || true)"
if [ -n "$pending" ] && [ "$pending" != "$rel" ]; then
  _die "끝나지 않은 반입이 있다(pending=${pending} · current=${rel}) — 먼저 되돌리거나 반입을 마친다(runbook 「되돌림 — 반입이 실패했을 때」)" 3
fi

MAN="$STATE/releases/${rel}.manifest"
[ -r "$MAN" ] || _die "release manifest 가 없다: ${rel}(${MAN}) — 반입 기록과 release 기록이 어긋났다" 3
_prod_images_match "$MAN" "$rel" || _die "${_PROD_IMAGE_ERR} — 수집하지 않는다" 3
_prod_git_sha_match "$MAN" || _die "${_PROD_GIT_SHA_ERR} — 수집하지 않는다" 3

COMPOSE=("${BIDVECTOR_PROD_COMPOSE[@]}" --env-file "$ENVF")
[ -z "$PROJECT" ] || COMPOSE+=(-p "$PROJECT")
export BIDVECTOR_RELEASE_TAG="$rel"

cfg="$("${COMPOSE[@]}" config --format json 2>/dev/null)" || _die "운영 compose 를 해석하지 못했다" 3
_prod_config_surface_ok "$cfg" || _die "${_PROD_SURFACE_ERR} — 수집하지 않는다" 3
PROJ="$(printf '%s' "$cfg" | jq -r .name)"
[ -n "$PROJ" ] && [ "$PROJ" != null ] || _die "해석된 compose 프로젝트 이름을 읽지 못했다" 3
[ "$PROJ" = "$PROJ_FOR_LOCK" ] \
  || _die "해석된 프로젝트 이름이 잠금을 잡은 이름과 다르다: ${PROJ} ≠ ${PROJ_FOR_LOCK} — 잠금과 잔존·일회 컨테이너 이름이 다른 자리를 본다" 3

# 술어는 훑기·평가 커밋·relay 와 **같은 것**이라 남은 그쪽 컨테이너도 여기서 걸린다(runbook §11 제한 13).
_prod_oneoff_residue "$PROJ" || _die "${_PROD_ONEOFF_ERR}" 3

# ── 실행 상태 디렉터리 — **전제를 다 지난 뒤에** 만든다 ────────────────────────
# 자리가 여기인 사유(M15/15U r1, cr medium): 거절되는 run(트리·프로젝트 이름·잔존·서비스 키 env)과
# **미룬 run**(잠금 경합 → 4)은 운영자의 root 아래에 **아무것도 남기지 않아야 한다.**
#
# **두 층을 각각 명시적으로 만든다.** `mkdir -p -m <mode>` 의 `-m` 은 **마지막 디렉터리에만** 적용되고
# 중간 디렉터리는 **호출자 umask** 로 만들어진다(실측: umask 0022 → 2755 · 0077 → 2700 · **0000 → 2777**).
# 마지막 꼴이 이 축의 사고다 — `notice-list` 에 other 쓰기가 열리면 **다른 host 계정이 달 디렉터리를
# 바꿔칠 수 있고**(지운 뒤 새로 만든다) 그 순간 호출 상한 회계가 0 에서 다시 선다(6G 가 「운영자 승인
# 없이는 지우지 않는다」로 적은 바로 그 사고). umask 에 기대지 않는다.
#
# **모드가 층마다 다른 사유는 실측이다.** 컨테이너는 host 쪽 **조상**을 지나지 않는다 — bind 붙임은
# 데몬(root)이 붙임 시점에 경로를 풀고, 컨테이너 안에서는 붙임 자리부터 권한이 재어진다(실측: 조상이
# 2700 이어도 uid 가 다르고 **gid 만 같은** 컨테이너가 잎에 쓴다 · 음성 대조로 잎이 2700 이면 같은 호출이
# 실패한다). 그래서 **group 쓰기가 필요한 자리는 잎 하나**이고, 조상에 group 쓰기를 열면 같은 group 의
# 다른 계정에게 위의 바꿔치기를 허락하는 것뿐이다. 조상은 `2700`, 잎은 `2770` 이다.
#
# **setgid 가 group 을 잇는다.** root 의 setgid → `notice-list` → 잎으로 group 이 물려 내려가므로
# (실측: 세 층 전부 group 10001) 조상의 setgid 는 **다음 달 디렉터리가 바른 group 으로 생기는가**를
# 정하는 자리다. 그 비트는 **여기서 잴 수 있다** — gid 의 *값* 등식만 못 재고(그 gid 로 `chgrp` 할 권한이
# 없다, `OPEN-M15-COLLECTION-RUN-STATE-GID`) setgid 의 **유무**는 잰다.
_collection_dir_mode() { # <경로> → mode 세 자리 + setuid/setgid 자리
  stat -c '%a' "$1" 2>/dev/null || true
}
# <경로> <무엇> → 그 자리가 심링크면 전제 실패(3).
# **두 층 가운데 어느 것도 심링크여서는 안 된다**(M15/15U r1 — vr M1 의 배치가 바로 그 꼴이다: root 는
# 저장소 밖인데 `notice-list` 가 저장소 안을 가리키는 심링크였다). 이 자리를 만드는 것은 래퍼이고
# 운영자가 여기에 링크를 둘 이유가 없으므로, 받지 않는 쪽이 설명이 하나다.
#
# **mode 술어로는 이것을 가를 수 없다**(이 라운드 실측): GNU `stat` 은 기본이 `lstat` 이라 심링크 자리에서
# **링크 자신의 mode(777)** 를 낸다. 그래서 앞 판은 심링크를 **우연히** setgid 축에서 막았고, 문면은
# 운영자에게 엉뚱한 자리(setgid)를 가리켰다. 우연을 의도로 바꾸는 것이 이 검사다.
_collection_not_symlink() {
  [ ! -L "$1" ] \
    || _die "${2} 가 심링크다(${1} → $(readlink "$1" 2>/dev/null || echo '?')) — 이 두 층은 래퍼가 만드는 자리이고 링크를 받지 않는다(링크 하나로 붙임의 원천이 저장소 안이나 root 밖으로 간다). 그 링크를 걷고 실제 디렉터리를 둔다(runbook §14 ①)" 3
}
_collection_prepare_run_state() {
  local m
  _collection_not_symlink "$RUN_STATE_PARENT" "실행 상태 갈래 디렉터리"
  mkdir -p -m 2700 "$RUN_STATE_PARENT" \
    || _die "실행 상태 갈래 디렉터리를 만들지 못했다: ${RUN_STATE_PARENT} — root 의 소유·권한을 본다(runbook §14 ①)" 3
  _collection_not_symlink "$RUN_STATE_PARENT" "실행 상태 갈래 디렉터리"
  m="$(_collection_dir_mode "$RUN_STATE_PARENT")"
  [ -n "$m" ] || _die "실행 상태 갈래 디렉터리의 권한을 읽지 못했다: ${RUN_STATE_PARENT}" 3
  # setgid 가 없으면 **다음 달 디렉터리의 group 이 이 래퍼의 gid** 가 되고, 그 자리는 컨테이너가 쓰지
  # 못한다 — 러너가 기동에서 죽고 그 종료는 1 이라 「예외」와 섞인다. 여기서 3 으로 먼저 가른다.
  [ "$(( 8#${m} & 8#2000 ))" -eq $(( 8#2000 )) ] \
    || _die "실행 상태 갈래 디렉터리에 setgid 가 없다(${RUN_STATE_PARENT} mode=${m}) — 그 비트가 group 을 달 디렉터리로 잇는다. 없으면 다음 달의 자리가 이 래퍼의 group 으로 생겨 컨테이너가 원장에 쓰지 못한다. 'chmod 2700' 뒤에 다시(runbook §14 ①)" 3
  [ "$(( 8#${m: -3} & 8#020 ))" -eq 0 ] \
    || _die "실행 상태 갈래 디렉터리에 group 쓰기가 열려 있다(${RUN_STATE_PARENT} mode=${m}) — 이 자리에 쓸 수 있으면 달 디렉터리를 **바꿔칠 수 있고** 그 순간 호출 상한 회계가 0 에서 다시 선다. group 쓰기가 필요한 자리는 **달 디렉터리 하나**다. 'chmod 2700' 뒤에 다시" 3
  [ "$(( 8#${m: -3} & 8#007 ))" -eq 0 ] \
    || _die "실행 상태 갈래 디렉터리가 other 에 열려 있다(${RUN_STATE_PARENT} mode=${m}) — 위와 같은 바꿔치기를 누구에게나 허락한다. 'chmod 2700' 뒤에 다시" 3
  _collection_not_symlink "$RUN_STATE_DIR" "달 디렉터리"
  mkdir -p -m 2770 "$RUN_STATE_DIR" \
    || _die "달 디렉터리를 만들지 못했다: ${RUN_STATE_DIR} — 갈래 디렉터리의 소유·권한을 본다(runbook §14 ①)" 3
  _collection_not_symlink "$RUN_STATE_DIR" "달 디렉터리"
  m="$(_collection_dir_mode "$RUN_STATE_DIR")"
  [ -n "$m" ] || _die "달 디렉터리의 권한을 읽지 못했다: ${RUN_STATE_DIR}" 3
  # **이미 있는 자리에는 위 `-m` 이 적용되지 않는다**(운영자가 손으로 만든 자리 · 옛 run 이 남긴 자리).
  # group 쓰기가 닫혀 있으면 컨테이너가 원장에 쓰지 못한다(음성 대조로 쟀다).
  [ "$(( 8#${m: -3} & 8#070 ))" -eq $(( 8#070 )) ] \
    || _die "달 디렉터리에 group 읽기·쓰기·탐색이 열려 있지 않다(${RUN_STATE_DIR} mode=${m} gid=$(stat -c '%g' "$RUN_STATE_DIR" 2>/dev/null || echo '?')) — 컨테이너는 uid/gid 10001 로 돌고 이 래퍼는 $(id -un) 으로 돈다. 둘이 같은 원장에 쓰는 길은 group 공유뿐이다. 'chmod 2770' 과 group 을 runbook §14 ① 의 값으로 맞춘 뒤 다시" 3
  [ "$(( 8#${m: -3} & 8#007 ))" -eq 0 ] \
    || _die "달 디렉터리가 other 에 열려 있다(${RUN_STATE_DIR} mode=${m}) — 이 자리는 호출 상한 회계를 담는다(지우면 상한이 0 에서 다시 선다). 'chmod 2770' 뒤에 다시" 3
  # **만든 뒤 실경로를 다시 본다**(vr M1) — **둘째 겹**이다. 위 인자 검사는 root 의 실경로만 봤으므로,
  # `notice-list` 나 달 디렉터리 자신이 저장소 안을 가리키는 심링크인 배치에서는 붙는 자리가 저장소 안이
  # 된다(실측: root 는 밖인데 달 디렉터리의 실경로가 저장소 안이었다). 러너 쪽 같은 가드는 컨테이너
  # 경로가 고정이라 그 배치에서 **아무것도 보지 못하므로** 겹은 래퍼 안에만 둘 수 있다.
  #
  # **위 심링크 거절이 서 있는 동안 이 셋은 발화하지 않는다** — 그렇다고 지우지 않는 이유는 둘이다:
  # ⓐ 이 값(`real`)이 `-v` 에 실리는 **그 경로**다(재는 자리와 붙는 자리를 한 값으로 묶는 규율) ·
  # ⓑ 한 층의 거절이 뚫려도(새 꼴·TOCTOU) 경계 자체는 남는다. 발화하지 않는 겹임을 문면으로 적어 둔다.
  local real
  real="$(readlink -f "$RUN_STATE_DIR" 2>/dev/null || true)"
  [ -n "$real" ] || _die "달 디렉터리의 실경로를 구하지 못했다: ${RUN_STATE_DIR}" 3
  _collection_outside_repository "$real" "달 디렉터리" "${RUN_STATE_DIR}"
  case "$real" in
    *:*) _die "달 디렉터리의 실경로에 ':' 가 있다 — 붙임 문법이라 받지 않는다: ${real}" 3 ;;
  esac
  case "$real/" in
    "$RUN_STATE_ABS"/*) ;;
    *) _die "달 디렉터리의 실경로가 실행 상태 root 밖이다(${RUN_STATE_DIR} → ${real} · root ${RUN_STATE_ABS}) — 심링크로 붙임의 원천을 root 밖으로 옮긴 자리다. 그 심링크를 걷은 뒤 다시" 3 ;;
  esac
  # **붙이는 것은 실경로다** — 재는 자리와 붙는 자리가 같아야 한다(둘째 env 파일의 경로 축과 같은 규율).
  RUN_STATE_DIR="$real"
}
_collection_prepare_run_state

echo "collection: release ${rel} · 프로젝트 ${PROJ} · 창 ${FROM}~${TO} · 업종 ${CATEGORIES} · 상한 ${CALLS_PER_DAY}/일 ${CALLS_TOTAL}/누적 · 실행 상태 ${RUN_STATE_DIR}"
ONEOFF_NAME="${PROJ}-app-run-collection"
clash="$(docker ps -aq --filter "name=^${ONEOFF_NAME}$" 2>/dev/null)" \
  || _die "일회 컨테이너 이름 충돌을 세지 못했다(docker ps 실패) — 판정 불가" 3
[ -z "$clash" ] \
  || _die "일회 컨테이너 이름 ${ONEOFF_NAME} 를 이미 다른 컨테이너가 쓰고 있다(이 프로젝트 밖일 수 있다) — 'docker ps -a --filter name=^${ONEOFF_NAME}\$' 로 보고 치운 뒤 다시" 3

_collection_on_signal() {
  local sig="$1"
  trap - TERM INT HUP
  docker rm -f "$ONEOFF_NAME" >/dev/null 2>&1 || true
  echo "collection: ${sig} 로 끝났다 — 이 run 의 일회 컨테이너 ${ONEOFF_NAME} 를 치웠다" >&2
  kill -s "$sig" $$
}
trap '_collection_on_signal TERM' TERM
trap '_collection_on_signal INT' INT
trap '_collection_on_signal HUP' HUP
# `-v` — 실행 상태 디렉터리를 host 에서 붙이는 **유일한 자리**다(머리말 ②). 원천은 래퍼가 지은 경로,
# 대상은 고정 경로다.
# `--env-from-file` — 서비스 키를 **컨테이너까지** 넘기는 유일한 통로다(머리말 ①).
# `SPRING_MAIN_WEB_APPLICATION_TYPE=none` — 러너를 켤 때 HTTP 표면을 끈다(m6-6e §1.3).
# `bidvector.koneps.base-url`·`operations.*` 는 **주지 않는다** — 출하 기본값이 `02` 개편판이고
# (`KonepsEndpointDefaultsTest` 가 문면을 잠근다) 되돌림은 그 속성 하나다(m6-6g §8). 여기에 두 자리가
# 생기면 되돌림이 어느 자리를 고쳐야 하는지가 갈린다.
rc=0
"${COMPOSE[@]}" run --rm --no-deps --pull never --name "$ONEOFF_NAME" \
  -v "${RUN_STATE_DIR}:${COLLECTION_RUN_STATE_IN_CONTAINER}" \
  --env-from-file "$KONEPS_ABS" \
  -e SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  app \
  --bidvector.collection.mode=once \
  --bidvector.collection.from="$FROM" \
  --bidvector.collection.to="$TO" \
  --bidvector.collection.categories="$CATEGORIES" \
  --bidvector.collection.calls-per-day="$CALLS_PER_DAY" \
  --bidvector.collection.calls-total="$CALLS_TOTAL" \
  --bidvector.collection.run-state-dir="$COLLECTION_RUN_STATE_IN_CONTAINER" || rc=$?
# **통과를 열거로 닫는다**(M15/15R, S-3 과 같은 사유). `*) exit "$rc"` 는 모르는 코드를 그대로 흘리는데,
# 래퍼가 쓰는 `5`·`6` 은 **러너의 다음 코드 자리**다 — 러너에 여섯째가 `5` 로 들어오는 날 그 값이 래퍼의
# `5` 자리로 들어와 뜻이 뒤바뀐다. 그래서 러너의 다섯만 **이름으로** 받고 그 밖은 `7` 로 낸다.
case "$rc" in
  0|1|2) exit "$rc" ;;
  3) echo "collection: 러너가 ALREADY_RUNNING(3) 으로 끝났다 — 다른 프로세스가 실행 상태 디렉터리(${RUN_STATE_DIR})를 쥐고 있다. 호출 0. **래퍼의 잠금 둘을 지난 뒤**라 그 프로세스는 이 래퍼를 거치지 않은 실행이다(손으로 띄운 러너·컨테이너) — 그것을 찾는다(m6-6e §3.3). 래퍼의 전제 실패(3)와 겹치지 않게 **5** 로 낸다" >&2; exit 5 ;;
  4) echo "collection: 러너가 UNLOCKABLE(4) 로 끝났다 — 자물쇠를 **걸 수 없다**(잠금을 지원하지 않는 파일 시스템이거나 자물쇠 파일을 열 수 없다). 호출 0. 기다려도 풀리지 않는다 — 실행 상태 디렉터리의 경로·권한을 고친다(m6-6e §3.3 · runbook §14 ①). 래퍼의 미룸(4)과 겹치지 않게 **6** 으로 낸다" >&2; exit 6 ;;
  *) echo "collection: 러너가 예상 밖의 종료 코드 ${rc} 를 냈다 — 래퍼가 5·6 을 쓰므로 그대로 통과시키지 않는다. docker run 자신의 실패(125·126·127) · **일회 컨테이너가** 신호로 죽어 돌아온 128+n(137·143) · 러너에 새로 생긴 코드가 여기 온다(래퍼 자신이 받은 신호는 위 trap 이 받아 이 자리에 닿지 않는다). **7** 로 낸다(SuccessExitStatus 밖이라 경보가 운다 — runbook §14 의 표)" >&2; exit 7 ;;
esac
