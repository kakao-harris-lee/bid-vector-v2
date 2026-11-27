#!/usr/bin/env bash
# M15/15T(D-15T-4 · 수정 라운드 1) — **이미지의 설치 기록과 설치 집합이 `uv.lock` 과 맞는가.**
#
# 왜 있는가: `docker/ml-serving.Dockerfile` 의 runtime stage 는 `uv.lock` 에서 뽑은 해시 목록으로
# 의존을 설치한다(`--require-hashes`). 그 플래그는 **설치 전** 차단이고, 지워지면 빌드는 그대로
# 초록이다. 이 게이트는 **결과**를 잰다 — 플래그와 독립된 측정이라, 보고서에 적힌 바이트가 lock 과
# 다르면 플래그가 있든 없든 붉는다. 술어를 Dockerfile 텍스트가 아니라 **만든 이미지**에 거는
# 이유도 같다(하네스 「게이트 술어는 문자열이 아니라 구조로 닫는다」).
#
# **주장을 술어만큼으로 적는다**(cr F-3 = vr F-1). 이것은 「최종 파일시스템의 모든 바이트가
# lock 의 바이트다」가 **아니다**. 재는 것은 둘 — 설치 RUN 한 걸음의 **기록(보고서)** 에 적힌
# 바이트가 lock 의 해시 집합에 드는가, 그리고 설치 집합의 **(이름, 판)** 이 그 기록과 맞는가.
#
# 막는 것: PyPI(또는 CDN·미러)가 같은 이름·판에 **다른 바이트**를 내주는 것, 전이 의존의 판이
# 빌드 시점에 조용히 달라지는 것, 보고서에 없는 배포물이 뒤에 **더해지는** 것, 설치 목록이 wheel 의
# extra 선언보다 **좁아지는** 것.
# 막지 않는 것: lock 에 적힌 그 바이트가 이미 나쁜 경우(해시는 「바뀌지 않음」이지 「검토됨」이
# 아니다) · **같은 (이름, 판)의 강제 재설치**((가)가 (이름, 판) 단위라 바이트가 바뀐 것을 보지
# 못한다 — 알려진 제한) · 그리고 이 저장소에 쓸 수 있는 저자 — Dockerfile·게이트·lock 을 함께
# 고치는 PR 은 통과한다. 이것은 실수의 래칫이다.
#
# 술어(판독은 `tools/image-pip-lock-check.py`, 그 머리 주석이 세 술어의 정본):
#   · (가) 설치 집합 == 보고서 집합 ∪ {프로젝트 자신} ∪ {base 가 싣고 온 것} — 양방향, (이름, 판).
#   · (나) 보고서 항목마다 보고서가 적은 archive 의 sha256 ∈ lock 의 그 (이름, 판) 해시 집합.
#   · (다) 보고서 이름 집합 ⊇ lock 의 프로젝트 항목이 `<extra>` 에 선언한 직접 의존 이름.
#   · 비공허 — 네 모집단과 (다)의 요구 집합 모두 ≥ 1 이고 계수를 출력한다. 「오탐 0 인 판이 양성도
#     0」을 막는다.
#   · 자기 변이 대조 — 임시 사본에 변이 **다섯**을 넣고 각각 **exit 1(위반)로** 붉어지는지, 그리고
#     **기대한 사유((가)/(나)/(다))로** 붉어지는지를 매 실행 잰다. 종료 코드 계약이 세 갈래이므로
#     「비-0」으로 세면 판정 불가(2)가 성공으로 읽힌다(cr F-1). 변이마다 사본이 실제로 달라졌는지
#     먼저 확인한다(더하기만 한 변이는 초록이 된다).
#
# **손 목록이 없다.** 「base 가 싣고 온 것」은 정책 파일의 `base.image.repo`·`base.image.digest`
# 가 가리키는 **같은 이미지에 같은 질의**를 걸어 받는다(위생 게이트가 그 두 키의 정본이다 —
# 값을 여기 다시 적지 않는다). 「프로젝트 자신」은 lock 에서 registry 가 아닌 단 하나의 항목으로
# 도출한다. 둘 다 바뀌는 날 게이트가 따라간다.
#
# 이 호스트의 `grep` 은 ugrep 래퍼라 쓰지 않는다 — 정책 판독은 awk, JSON·TOML 판독은 python3 다.
#
# 종료 코드: **0 통과 · 1 이미지가 기준을 어겼다 · 2 판정 불가**(정책·환경·스키마 — 조용한 통과를
# 만들지 않는다).
#
# 사용법: tools/image-pip-lock-check.sh <image-ref> <policy-file> <lock-file> <보고서 경로(이미지 안)> <extra 이름>
#
# **extra 이름을 인자로 받는다**(기본값 없음). 스크립트에 상수로 박으면 Dockerfile 의
# `uv export --extra <이름>` 과 **두 자리**가 되고 어느 쪽이 정본인지 명령에 보이지 않는다. 정책
# 파일을 인자로 받는 관례(`image-hygiene-check.sh` D-6A2a-7)와 같은 사유다 — 어느 extra 로
# 판정했는지가 호출 자리에 보인다. 이름이 lock 의 `provides-extras` 에 없으면 **정지**다.
set -euo pipefail
export LC_ALL=C

if [ "$#" -ne 5 ]; then
  echo "사용법: $0 <image-ref> <policy-file> <lock-file> <보고서 경로(이미지 안)> <extra 이름>" >&2
  exit 2
fi

IMAGE_REF="$1"
POLICY_FILE="$2"
LOCK_FILE="$3"
REPORT_PATH="$4"
SERVING_EXTRA="$5"
SELFCHECK="${IMAGE_PIP_LOCK_SELFCHECK:-1}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
READER="$SCRIPT_DIR/image-pip-lock-check.py"

[ -f "$POLICY_FILE" ] || { echo "정책 파일이 없다: $POLICY_FILE" >&2; exit 2; }
[ -f "$LOCK_FILE" ] || { echo "lock 파일이 없다: $LOCK_FILE" >&2; exit 2; }
[ -f "$READER" ] || { echo "판독기가 없다: $READER" >&2; exit 2; }
case "$REPORT_PATH" in
  /*) ;;
  *) echo "보고서 경로가 절대 경로가 아니다 — 상대 경로는 컨테이너 작업 디렉터리에 따라 다른 곳을 가리킨다: '$REPORT_PATH'" >&2; exit 2 ;;
esac
# extra 이름은 PEP 685 정규화 이름의 문자 집합으로 좁힌다 — 실재 여부는 lock 의
# `provides-extras` 가 판정한다(판독기).
case "$SERVING_EXTRA" in
  '' | *[!A-Za-z0-9._-]*)
    echo "extra 이름이 허용 문자([A-Za-z0-9._-])만으로 돼 있지 않다: '$SERVING_EXTRA'" >&2
    exit 2
    ;;
esac

# 자기 변이 대조 스위치는 **열거로 닫는다**(`workflow-action-pins-check.sh` 선례) — 읽지 못하는
# 값에서 조용히 꺼지면 게이트의 자기 건전성 측정만 사라지고 CI 는 초록이다. 끌 때도 한 줄 찍는다.
case "$SELFCHECK" in
  0 | 1) ;;
  *) echo "IMAGE_PIP_LOCK_SELFCHECK 가 0·1 이 아니다: $SELFCHECK" >&2; exit 2 ;;
esac

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 가 필요하다 — 이 게이트가 보고서(JSON)와 lock(TOML)을 판독하는 데 쓴다" >&2
  exit 2
fi

# 정책 값 판독 — 위생 게이트의 `_policy_value` 와 같은 축이다. 키 대조는 **리터럴 접두**
# (`index($0, k) == 1`)라 정규식 특수문자를 손으로 이스케이프하지 않고, **중복 키**는 정책 오류로
# 끊는다(중복은 앞 값을 조용히 무력화한다).
_policy_value() {
  local key="$1" matches raw
  matches="$(awk -v k="${key}=" 'index($0, k) == 1 { n++ } END { print n+0 }' "$POLICY_FILE")"
  if [ "$matches" -eq 0 ]; then
    echo "정책 키 ${key} 를 ${POLICY_FILE} 에서 읽지 못했다" >&2
    exit 2
  fi
  if [ "$matches" -gt 1 ]; then
    echo "정책 키 ${key} 가 ${POLICY_FILE} 에 ${matches}번 선언됐다(정책 오류 — 중복 키는 앞 값을 조용히 무력화한다)" >&2
    exit 2
  fi
  raw="$(awk -v k="${key}=" 'index($0, k) == 1 { print substr($0, length(k) + 1) }' "$POLICY_FILE" | tr -d '\r')"
  if [ -z "$raw" ]; then
    echo "정책 키 ${key} 의 값이 비어 있다(정책 오류 — 값 없는 키는 정책 오류다)" >&2
    exit 2
  fi
  printf '%s' "$raw"
}

BASE_IMAGE_REPO="$(_policy_value base.image.repo)"
BASE_IMAGE_DIGEST="$(_policy_value base.image.digest)"
case "$BASE_IMAGE_DIGEST" in
  sha256:*) ;;
  *) echo "정책 키 base.image.digest 가 sha256: 로 시작하지 않는다: '$BASE_IMAGE_DIGEST'" >&2; exit 2 ;;
esac

WORK="$(mktemp -d)"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

echo "== 이미지 pip↔lock 등식 게이트: ${IMAGE_REF} =="

# 두 이미지에 **글자 그대로 같은 질의**를 건다 — 질의가 두 벌이면 한쪽의 교정이 다른 쪽을 놓친다.
# `dist.metadata["Name"]` 이 없는 배포물은 조용히 빠지는 대신 질의 자체가 비-0 으로 끊긴다.
DIST_QUERY='
import re
from importlib.metadata import distributions

pairs = set()
for dist in distributions():
    name = dist.metadata["Name"]
    if name is None:
        raise SystemExit("이름 없는 배포물이 있다 — 집합을 신뢰할 수 없다")
    pairs.add((re.sub(r"[-_.]+", "-", name).lower(), dist.version))
for name, version in sorted(pairs):
    print(name, version)
'

# **`--network none`**(cr F-10) — 세 질의는 전부 로컬 판독(`importlib.metadata`·`cat`)이라
# 네트워크가 필요 없다. `--security-opt no-new-privileges` 를 이미 붙인 자리에 같은 급의 한
# 토큰이다. 이미지 받아 오기는 `docker run` **밖**에서 이미 끝난다(아래 base 절).
rc=0
docker run --rm --network none --security-opt no-new-privileges --entrypoint python "$IMAGE_REF" \
  -c "$DIST_QUERY" > "$WORK/installed.txt" 2> "$WORK/installed.err" || rc=$?
if [ "$rc" -ne 0 ]; then
  echo "이미지의 배포물 집합을 질의하지 못했다(exit ${rc}) — 판정 불가: $(tr '\n' ' ' < "$WORK/installed.err")" >&2
  exit 2
fi

rc=0
docker run --rm --network none --security-opt no-new-privileges --entrypoint cat "$IMAGE_REF" \
  "$REPORT_PATH" > "$WORK/report.json" 2> "$WORK/report.err" || rc=$?
if [ "$rc" -ne 0 ]; then
  echo "이미지 안 설치 보고서(${REPORT_PATH})를 읽지 못했다(exit ${rc}) — 보고서 생성이 사라지면 이 게이트는 통과가 아니라 정지다: $(tr '\n' ' ' < "$WORK/report.err")" >&2
  exit 2
fi

# base 가 싣고 온 것 — **같은 다이제스트, 같은 질의**. 없으면 받아 온다(위생 게이트 (2) 와 같은
# 레지스트리 의존이고, 접근이 없으면 이 축은 판정 불가로 끊긴다).
BASE_REF="${BASE_IMAGE_REPO}@${BASE_IMAGE_DIGEST}"
if ! docker image inspect "$BASE_REF" >/dev/null 2>&1; then
  # **재시도 셋**(cr F-8). 미러가 한 번 흔들리면 뒤 `docker run` 이 실패하고 **모든 PR** 이
  # exit 2 로 붉는다 — 이 저장소가 같은 축에 이미 규율을 갖고 있다(apt `Acquire::Retries=3` ·
  # trivy·buf·shfmt 의 `--retry-all-errors`). 셋 다 실패하면 아래 `docker run` 이 판정 불가로 받는다.
  # `[ … ] && sleep` 형태를 쓰지 않는다 — 루프 본문의 마지막 명령이 비-0 이면 `set -e` 가
  # 스크립트를 죽인다(이 저장소의 관례: 독립 `if`).
  pull_try=1
  while [ "$pull_try" -le 3 ]; do
    if docker pull "$BASE_REF" >/dev/null 2>&1; then
      break
    fi
    pull_try=$((pull_try + 1))
    if [ "$pull_try" -le 3 ]; then
      sleep 2
    fi
  done
fi
rc=0
docker run --rm --network none --security-opt no-new-privileges --entrypoint python "$BASE_REF" \
  -c "$DIST_QUERY" > "$WORK/base.txt" 2> "$WORK/base.err" || rc=$?
if [ "$rc" -ne 0 ]; then
  echo "정책 기준 base(${BASE_REF})의 배포물 집합을 질의하지 못했다(exit ${rc}) — 네트워크/registry 접근이 필요하다(판정 불가): $(tr '\n' ' ' < "$WORK/base.err")" >&2
  exit 2
fi

_read() { # <보고서> <lock> <설치 집합> → 판독기 종료 코드(출력은 그대로 흘린다)
  local report="$1" lock="$2" installed="$3" out=0
  python3 "$READER" --lock "$lock" --report "$report" \
    --installed "$installed" --base "$WORK/base.txt" --extra "$SERVING_EXTRA" || out=$?
  return "$out"
}

_read_capture() { # <출력 파일> <보고서> <lock> <설치 집합> → 판독기 종료 코드
  local sink="$1" report="$2" lock="$3" installed="$4" out=0
  python3 "$READER" --lock "$lock" --report "$report" \
    --installed "$installed" --base "$WORK/base.txt" --extra "$SERVING_EXTRA" \
    > "$sink" 2>&1 || out=$?
  return "$out"
}

# ---- 본 판정 ----
VERDICT=0
_read "$WORK/report.json" "$LOCK_FILE" "$WORK/installed.txt" || VERDICT=$?
if [ "$VERDICT" -eq 2 ]; then
  echo "== lock 등식 게이트: 판정 불가 ==" >&2
  exit 2
fi

# ---- 자기 변이 대조 ----
# **바꿔치우기·제거**로 넣는다 — 더하기만 한 변이는 초록이 되고, 그 초록은 방어의 증거가 아니다.
# 변이마다 「사본이 원본과 실제로 달라졌는가」를 **변이기 자신이** 비-0 으로 단언한다.
#
# 변이기는 **판독기 모듈을 그대로 불러** 요구 집합·프로젝트 항목을 얻는다(`importlib`) — 같은
# 논리를 두 벌로 두면 한쪽의 교정이 다른 쪽을 놓친다. 다섯을 **한 번에** 만들고, 하나라도
# 반영에 실패하면 전체가 판정 불가다.
#
# **본 판정이 이미 위반이면 자기 변이를 돌리지 않는다.** 변이의 기대는 「초록 입력에서 그 변이가
# 위반을 만든다」인데, 입력이 이미 위반이면 기대 사유 대조가 **원래 있던 위반**을 집어 거짓
# 어긋남을 낸다(실측: `--extra serving` 을 뺀 이미지에서 변이 ②가 「(다)가 섞였다」로 어긋나
# exit 1 이어야 할 판정이 exit 2 가 됐다). 자기 변이가 막는 것은 **거짓 초록**이고, 판정이 이미
# 붉으면 막을 거짓 초록이 없다 — 그래서 건너뛰고 그 사실을 요약에 적는다.
SELFCHECK_SUMMARY="자기변이=꺼짐"
if [ "$VERDICT" -ne 0 ]; then
  SELFCHECK_SUMMARY="자기변이=건너뜀(본 판정이 이미 위반이라 초록 기준선이 없다)"
elif [ "$SELFCHECK" = "1" ]; then
  mrc=0
  IPLC_WORK="$WORK" IPLC_LOCK="$LOCK_FILE" IPLC_READER="$READER" IPLC_EXTRA="$SERVING_EXTRA" \
    python3 -c '
import importlib.util, json, os, tomllib
from pathlib import Path

work = os.environ["IPLC_WORK"]
lock_path = os.environ["IPLC_LOCK"]
extra = os.environ["IPLC_EXTRA"]

spec = importlib.util.spec_from_file_location("iplc_reader", os.environ["IPLC_READER"])
reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader)
norm = reader.normalize

report = json.load(open(work + "/report.json", encoding="utf-8"))
_hashes, project, project_entry = reader.load_lock(Path(lock_path))
wanted = reader.required_names(project_entry, extra)

base = set()
for line in open(work + "/base.txt", encoding="utf-8"):
    fields = line.split()
    if len(fields) == 2:
        base.add((norm(fields[0]), fields[1]))

def pairs():
    return [(norm(i["metadata"]["name"]), i["metadata"]["version"]) for i in report["install"]]

def copy():
    return json.loads(json.dumps(report))

def say(tag, msg):
    print("  변이 " + tag + ": " + msg)

# ① 보고서의 해시 하나를 다른 값으로 바꿔치운다 → (나)
mutated = copy()
digests = mutated["install"][0]["download_info"]["archive_info"]["hashes"]
before = digests["sha256"]
after = ("0" * 64) if before != "0" * 64 else ("1" * 64)
digests["sha256"] = after
target1 = work + "/report-mut-hash.json"
json.dump(mutated, open(target1, "w", encoding="utf-8"))
check = json.load(open(target1, encoding="utf-8"))["install"][0]["download_info"]["archive_info"]["hashes"]["sha256"]
if check != after or check == before:
    raise SystemExit("① 변이가 사본에 반영되지 않았다")
say("①", "보고서 해시 바꿔치움")

# ② base ∪ {프로젝트} ∪ 요구이름 **밖**의 항목을 제거 → (가) 단독.
#    첫 항목을 고정으로 집으면 그것이 base 나 프로젝트에 들 때 expected 가 줄지 않아
#    (가)가 붉지 않고 거짓 정지가 된다(cr F-5). 선택을 변이기가 단언한다.
candidates = [
    i for i, (n, v) in enumerate(pairs())
    if (n, v) not in base and (n, v) != project and n not in wanted
]
if not candidates:
    raise SystemExit("② base·프로젝트·요구이름 밖의 보고서 항목이 0 건이다 — 이 변이는 (가) 단독을 재지 못한다")
mutated = copy()
dropped2 = mutated["install"].pop(candidates[0])
target2 = work + "/report-mut-drop.json"
json.dump(mutated, open(target2, "w", encoding="utf-8"))
if len(json.load(open(target2, encoding="utf-8"))["install"]) != len(report["install"]) - 1:
    raise SystemExit("② 변이가 사본에 반영되지 않았다")
say("②", "보고서에서 " + norm(dropped2["metadata"]["name"]) + " 제거(base·프로젝트·요구 밖)")

# ⑤ 보고서에서 **요구 이름** 하나를 제거 → (다). 설치 목록이 선언보다 좁아지는 축이다.
wanted_idx = [i for i, (n, v) in enumerate(pairs()) if n in wanted]
if not wanted_idx:
    raise SystemExit("⑤ 보고서에 요구 이름이 0 건이다 — (다)가 공허하다")
mutated = copy()
dropped5 = mutated["install"].pop(wanted_idx[0])
target5 = work + "/report-mut-required.json"
json.dump(mutated, open(target5, "w", encoding="utf-8"))
if len(json.load(open(target5, encoding="utf-8"))["install"]) != len(report["install"]) - 1:
    raise SystemExit("⑤ 변이가 사본에 반영되지 않았다")
say("⑤", "보고서에서 요구 이름 " + norm(dropped5["metadata"]["name"]) + " 제거")

# ③ lock 사본에서 한 패키지의 해시를 제거 → (나)(대조할 집합이 빈다).
#    줄 꼴에 기대는 자리라 **사유를 구분해** 적는다(cr F-6): 지울 줄이 없거나 사본이 TOML 로
#    읽히지 않으면 그것은 「변이 실패」가 아니라 「lock 의 줄 꼴이 바뀌었다」다.
needle = "hash = " + chr(34) + "sha256:"
target_name = pairs()[0][0]
lines = open(lock_path, encoding="utf-8").read().splitlines(keepends=True)
kept, inside, removed = [], False, 0
for line in lines:
    if line.startswith("name = "):
        inside = norm(line.split("=", 1)[1].strip().strip(chr(34))) == target_name
    if inside and needle in line:
        removed += 1
        continue
    kept.append(line)
if removed == 0:
    raise SystemExit("③ lock 에서 지울 해시 줄을 찾지 못했다 — lock 의 줄 꼴이 바뀌었다: " + target_name)
target3 = work + "/lock-mut.toml"
open(target3, "w", encoding="utf-8").writelines(kept)
try:
    roundtrip = tomllib.load(open(target3, "rb"))
except tomllib.TOMLDecodeError as exc:
    raise SystemExit("③ 사본이 TOML 로 읽히지 않는다 — lock 의 줄 꼴이 바뀌었다(해시가 다른 키와 한 줄에 산다): " + str(exc))
for entry in roundtrip["package"]:
    if norm(entry["name"]) == target_name:
        leftover = (entry.get("sdist") or {}).get("hash")
        leftover = leftover or [w for w in entry.get("wheels") or [] if w.get("hash")]
        if leftover:
            raise SystemExit("③ 변이가 사본에 반영되지 않았다")
say("③", target_name + " 의 lock 해시 " + str(removed) + " 줄 제거")

# ④ 설치 집합 사본에서 한 줄을 제거 → (가)(보고서에 있는데 설치돼 있지 않다). ②의 반대 방향이다.
installed_lines = [
    line for line in open(work + "/installed.txt", encoding="utf-8").read().splitlines() if line.strip()
]
if len(installed_lines) < 2:
    raise SystemExit("④ 설치 집합이 둘 미만이라 제거 변이를 넣을 수 없다")
target4 = work + "/installed-mut.txt"
open(target4, "w", encoding="utf-8").write("\n".join(installed_lines[1:]) + "\n")
if len(open(target4, encoding="utf-8").read().splitlines()) != len(installed_lines) - 1:
    raise SystemExit("④ 변이가 사본에 반영되지 않았다")
say("④", "설치 집합에서 " + installed_lines[0] + " 제거")
' || mrc=$?
  if [ "$mrc" -ne 0 ]; then
    echo "자기 변이 사본을 만들지 못했다(exit ${mrc}) — 변이가 적용되지 않은 초록은 방어의 증거가 아니다" >&2
    exit 2
  fi

  mutation_failures=0
  # **기대는 「비-0」이 아니라 exit 1(위반) + 기대 사유다**(cr F-1). 종료 코드 계약이
  # `0 통과 · 1 위반 · 2 판정 불가` 세 갈래이므로 「비-0」으로 세면 **판독기가 그 입력에서
  # 서지 못하는 것**이 성공으로 읽힌다 — 그러면 등식이 아무 변이도 잡지 않는 날에도 CI 가
  # 초록이다(실측 RED: 변이 ①을 「해시 키 제거」로 바꾸면 판독기는 exit 2 인데 앞 판은
  # 「4건 전부 붉음」을 찍고 통과했다). 어긋나면 판독기 출력을 **찍는다** — 어긋난 회차에
  # 무엇이 나왔는지가 로그에 없으면 다음 사람이 다시 재야 한다. 선례는
  # `tools/workflow-action-pins-check.sh` 의 기대 사유 대조다.
  _expect_red() { # <이름> <기대 사유> <금지 사유|-> <보고서> <lock> <설치 집합>
    local label="$1" want="$2" deny="$3" sink="$WORK/mut-out.txt" erc=0
    _read_capture "$sink" "$4" "$5" "$6" || erc=$?
    if [ "$erc" -ne 1 ]; then
      echo "자기 변이 '${label}' 가 위반(exit 1)으로 붉지 않았다(exit ${erc}) — 이 게이트는 그 변이를 등식 위반으로 잡지 못한다:" >&2
      cat "$sink" >&2
      mutation_failures=$((mutation_failures + 1))
      return 0
    fi
    if ! awk -v w="$want" 'index($0, w) > 0 { found = 1 } END { exit found ? 0 : 1 }' "$sink"; then
      echo "자기 변이 '${label}' 가 붉었지만 사유에 '${want}' 가 없다 — 기대한 술어가 아닌 다른 축이 잡았다:" >&2
      cat "$sink" >&2
      mutation_failures=$((mutation_failures + 1))
      return 0
    fi
    if [ "$deny" != "-" ]; then
      if awk -v d="$deny" 'index($0, d) > 0 { found = 1 } END { exit found ? 0 : 1 }' "$sink"; then
        echo "자기 변이 '${label}' 의 사유에 '${deny}' 가 섞였다 — 두 축이 분리되지 않았다:" >&2
        cat "$sink" >&2
        mutation_failures=$((mutation_failures + 1))
      fi
    fi
  }
  _expect_red "보고서 해시 바꿔치우기" "(나)" "-" "$WORK/report-mut-hash.json" "$LOCK_FILE" "$WORK/installed.txt"
  _expect_red "보고서 항목 제거" "(가)" "(다)" "$WORK/report-mut-drop.json" "$LOCK_FILE" "$WORK/installed.txt"
  _expect_red "lock 해시 제거" "(나)" "-" "$WORK/report.json" "$WORK/lock-mut.toml" "$WORK/installed.txt"
  _expect_red "설치 집합 항목 제거" "(가)" "(다)" "$WORK/report.json" "$LOCK_FILE" "$WORK/installed-mut.txt"
  _expect_red "요구 이름 제거" "(다)" "-" "$WORK/report-mut-required.json" "$LOCK_FILE" "$WORK/installed.txt"
  if [ "$mutation_failures" -gt 0 ]; then
    echo "== 자기 변이 대조 실패: ${mutation_failures}건(판정 불가) ==" >&2
    exit 2
  fi
  SELFCHECK_SUMMARY="자기변이=5건 전부 exit 1 + 기대 사유((나)·(가)·(나)·(가)·(다))"
else
  echo "주의: IMAGE_PIP_LOCK_SELFCHECK=0 — 자기 변이 대조를 끄고 돌았다" >&2
fi

echo "-- 실측 요약 --"
echo "정책=${POLICY_FILE} lock=${LOCK_FILE} 보고서=${REPORT_PATH} base=${BASE_REF} extra=${SERVING_EXTRA}"
echo "${SELFCHECK_SUMMARY}"

if [ "$VERDICT" -ne 0 ]; then
  echo "== lock 등식 게이트 실패 ==" >&2
  exit 1
fi

echo "== lock 등식 게이트 통과 =="
