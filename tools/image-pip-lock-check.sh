#!/usr/bin/env bash
# M15/15T(D-15T-4) — **이미지에 들어간 Python 배포물의 바이트가 `uv.lock` 이 적은 바이트인가.**
#
# 왜 있는가: `docker/ml-serving.Dockerfile` 의 runtime stage 는 `uv.lock` 에서 뽑은 해시 목록으로
# 의존을 설치한다(`--require-hashes`). 그 플래그는 **설치 전** 차단이고, 지워지면 빌드는 그대로
# 초록이다. 이 게이트는 **결과**를 잰다 — 플래그와 독립된 측정이라, 받은 바이트가 lock 과 다르면
# 플래그가 있든 없든 붉는다. 술어를 Dockerfile 텍스트가 아니라 **만든 이미지**에 거는 이유도 같다
# (하네스 「게이트 술어는 문자열이 아니라 구조로 닫는다」).
#
# 막는 것: PyPI(또는 CDN·미러)가 같은 이름·판에 **다른 바이트**를 내주는 것, 전이 의존의 판이
# 빌드 시점에 조용히 달라지는 것, 해시 없는 설치 걸음이 뒤에 **더해지는** 것.
# 막지 않는 것: lock 에 적힌 그 바이트가 이미 나쁜 경우(해시는 「바뀌지 않음」이지 「검토됨」이
# 아니다), 그리고 이 저장소에 쓸 수 있는 저자 — Dockerfile·게이트·lock 을 함께 고치는 PR 은
# 통과한다. 이것은 실수의 래칫이다.
#
# 술어(판독은 `tools/image-pip-lock-check.py`, 그 머리 주석이 두 등식의 정본):
#   · (가) 설치 집합 == 보고서 집합 ∪ {프로젝트 자신} ∪ {base 가 싣고 온 것} — 양방향.
#   · (나) 보고서 항목마다 내려받은 archive 의 sha256 ∈ lock 의 그 (이름, 판) 해시 집합.
#   · 비공허 — 네 모집단 모두 ≥ 1 이고 계수를 출력한다. 「오탐 0 인 판이 양성도 0」을 막는다.
#   · 자기 변이 대조 — 임시 사본에 변이 넷을 넣고 각각 붉어지는지를 **매 실행** 잰다. 변이마다
#     사본이 실제로 달라졌는지 먼저 확인한다(더하기만 한 변이는 초록이 된다).
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
# 사용법: tools/image-pip-lock-check.sh <image-ref> <policy-file> <lock-file> <보고서 경로(이미지 안)>
set -euo pipefail
export LC_ALL=C

if [ "$#" -ne 4 ]; then
  echo "사용법: $0 <image-ref> <policy-file> <lock-file> <보고서 경로(이미지 안)>" >&2
  exit 2
fi

IMAGE_REF="$1"
POLICY_FILE="$2"
LOCK_FILE="$3"
REPORT_PATH="$4"
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

rc=0
docker run --rm --security-opt no-new-privileges --entrypoint python "$IMAGE_REF" \
  -c "$DIST_QUERY" > "$WORK/installed.txt" 2> "$WORK/installed.err" || rc=$?
if [ "$rc" -ne 0 ]; then
  echo "이미지의 배포물 집합을 질의하지 못했다(exit ${rc}) — 판정 불가: $(tr '\n' ' ' < "$WORK/installed.err")" >&2
  exit 2
fi

rc=0
docker run --rm --security-opt no-new-privileges --entrypoint cat "$IMAGE_REF" \
  "$REPORT_PATH" > "$WORK/report.json" 2> "$WORK/report.err" || rc=$?
if [ "$rc" -ne 0 ]; then
  echo "이미지 안 설치 보고서(${REPORT_PATH})를 읽지 못했다(exit ${rc}) — 보고서 생성이 사라지면 이 게이트는 통과가 아니라 정지다: $(tr '\n' ' ' < "$WORK/report.err")" >&2
  exit 2
fi

# base 가 싣고 온 것 — **같은 다이제스트, 같은 질의**. 없으면 받아 온다(위생 게이트 (2) 와 같은
# 레지스트리 의존이고, 접근이 없으면 이 축은 판정 불가로 끊긴다).
BASE_REF="${BASE_IMAGE_REPO}@${BASE_IMAGE_DIGEST}"
if ! docker image inspect "$BASE_REF" >/dev/null 2>&1; then
  docker pull "$BASE_REF" >/dev/null 2>&1 || true
fi
rc=0
docker run --rm --security-opt no-new-privileges --entrypoint python "$BASE_REF" \
  -c "$DIST_QUERY" > "$WORK/base.txt" 2> "$WORK/base.err" || rc=$?
if [ "$rc" -ne 0 ]; then
  echo "정책 기준 base(${BASE_REF})의 배포물 집합을 질의하지 못했다(exit ${rc}) — 네트워크/registry 접근이 필요하다(판정 불가): $(tr '\n' ' ' < "$WORK/base.err")" >&2
  exit 2
fi

_read() { # <보고서> <lock> → 판독기 종료 코드
  local report="$1" lock="$2" installed="$3" out=0
  python3 "$READER" --lock "$lock" --report "$report" \
    --installed "$installed" --base "$WORK/base.txt" || out=$?
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
# **바꿔치우기**로 넣는다 — 더하기만 한 변이는 초록이 되고, 그 초록은 방어의 증거가 아니다.
# 변이마다 「사본이 원본과 실제로 달라졌는가」를 **변이기 자신이** 비-0 으로 단언한다.
SELFCHECK_SUMMARY="자기변이=꺼짐"
if [ "$SELFCHECK" = "1" ]; then
  mutate() { # <이름> <산출 파일> <python 변이기> [넷째 argv]
    local label="$1" out="$2" code="$3" extra="${4:-}" mrc=0
    python3 -c "$code" "$WORK" "$out" "$extra" || mrc=$?
    if [ "$mrc" -ne 0 ]; then
      echo "자기 변이 '${label}' 를 적용하지 못했다(exit ${mrc}) — 변이가 적용되지 않은 초록은 방어의 증거가 아니다" >&2
      exit 2
    fi
  }

  # ① 보고서의 해시 하나를 **다른 값으로 바꿔치운다** → (나)가 붉어야 한다.
  mutate "보고서 해시 바꿔치우기" "$WORK/report-mut-hash.json" '
import json, sys
work, out = sys.argv[1], sys.argv[2]
data = json.load(open(work + "/report.json"))
hashes = data["install"][0]["download_info"]["archive_info"]["hashes"]
before = hashes["sha256"]
after = ("0" * 64) if before != "0" * 64 else ("1" * 64)
if before == after:
    raise SystemExit("바꿔치울 값이 원본과 같다")
hashes["sha256"] = after
json.dump(data, open(out, "w"))
reread = json.load(open(out))["install"][0]["download_info"]["archive_info"]["hashes"]["sha256"]
if reread != after or reread == before:
    raise SystemExit("변이가 사본에 반영되지 않았다")
'
  # ② 보고서에서 한 항목을 **없앤다** → (가)가 붉어야 한다(설치돼 있는데 보고서에 없다).
  mutate "보고서 항목 제거" "$WORK/report-mut-drop.json" '
import json, sys
work, out = sys.argv[1], sys.argv[2]
data = json.load(open(work + "/report.json"))
before = len(data["install"])
if before < 2:
    raise SystemExit("항목이 둘 미만이라 제거 변이를 넣을 수 없다")
data["install"] = data["install"][1:]
json.dump(data, open(out, "w"))
if len(json.load(open(out))["install"]) != before - 1:
    raise SystemExit("변이가 사본에 반영되지 않았다")
'
  # ③ lock 사본에서 **한 패키지의 해시를 없앤다** → (나)가 붉어야 한다(대조할 집합이 빈다).
  mutate "lock 해시 제거" "$WORK/lock-mut.toml" '
import json, re, sys, tomllib
work, out, lock_path = sys.argv[1], sys.argv[2], sys.argv[3]
report = json.load(open(work + "/report.json"))
target = re.sub(r"[-_.]+", "-", report["install"][0]["metadata"]["name"]).lower()
lines = open(lock_path, encoding="utf-8").read().splitlines(keepends=True)
kept, inside, dropped = [], False, 0
for line in lines:
    if line.startswith("name = "):
        inside = re.sub(r"[-_.]+", "-", line.split("=", 1)[1].strip().strip(chr(34))).lower() == target
    if inside and ("hash = \"sha256:" in line):
        dropped += 1
        continue
    kept.append(line)
if dropped == 0:
    raise SystemExit("lock 에서 지울 해시 줄을 찾지 못했다: " + target)
open(out, "w", encoding="utf-8").writelines(kept)
roundtrip = tomllib.load(open(out, "rb"))
for entry in roundtrip["package"]:
    if re.sub(r"[-_.]+", "-", entry["name"]).lower() == target:
        if entry.get("sdist", {}).get("hash") or [w for w in entry.get("wheels") or [] if w.get("hash")]:
            raise SystemExit("변이가 사본에 반영되지 않았다")
' "$LOCK_FILE"
  # ④ 설치 집합 사본에서 **한 줄을 없앤다** → (가)가 붉어야 한다(보고서에 있는데 설치돼 있지 않다).
  #    ②의 반대 방향이다 — 질의가 조용히 줄어드는 축을 이쪽에서 받는다.
  mutate "설치 집합 항목 제거" "$WORK/installed-mut.txt" '
import sys
work, out = sys.argv[1], sys.argv[2]
lines = [l for l in open(work + "/installed.txt", encoding="utf-8").read().splitlines() if l.strip()]
if len(lines) < 2:
    raise SystemExit("설치 집합이 둘 미만이라 제거 변이를 넣을 수 없다")
open(out, "w", encoding="utf-8").write("\n".join(lines[1:]) + "\n")
if len(open(out, encoding="utf-8").read().splitlines()) != len(lines) - 1:
    raise SystemExit("변이가 사본에 반영되지 않았다")
'

  mutation_failures=0
  _expect_red() { # <이름> <보고서> <lock> <설치 집합>
    local label="$1" mrc=0
    _read "$2" "$3" "$4" >/dev/null 2>&1 || mrc=$?
    if [ "$mrc" -eq 0 ]; then
      echo "자기 변이 '${label}' 가 초록이다 — 이 게이트는 그 변이를 잡지 못한다" >&2
      mutation_failures=$((mutation_failures + 1))
    fi
  }
  _expect_red "보고서 해시 바꿔치우기" "$WORK/report-mut-hash.json" "$LOCK_FILE" "$WORK/installed.txt"
  _expect_red "보고서 항목 제거" "$WORK/report-mut-drop.json" "$LOCK_FILE" "$WORK/installed.txt"
  _expect_red "lock 해시 제거" "$WORK/report.json" "$WORK/lock-mut.toml" "$WORK/installed.txt"
  _expect_red "설치 집합 항목 제거" "$WORK/report.json" "$LOCK_FILE" "$WORK/installed-mut.txt"
  if [ "$mutation_failures" -gt 0 ]; then
    echo "== 자기 변이 대조 실패: ${mutation_failures}건(판정 불가) ==" >&2
    exit 2
  fi
  SELFCHECK_SUMMARY="자기변이=4건 전부 붉음"
else
  echo "주의: IMAGE_PIP_LOCK_SELFCHECK=0 — 자기 변이 대조를 끄고 돌았다" >&2
fi

echo "-- 실측 요약 --"
echo "정책=${POLICY_FILE} lock=${LOCK_FILE} 보고서=${REPORT_PATH} base=${BASE_REF}"
echo "${SELFCHECK_SUMMARY}"

if [ "$VERDICT" -ne 0 ]; then
  echo "== lock 등식 게이트 실패 ==" >&2
  exit 1
fi

echo "== lock 등식 게이트 통과 =="
