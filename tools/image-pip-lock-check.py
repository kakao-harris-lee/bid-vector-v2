#!/usr/bin/env python3
"""이미지에 들어간 Python 배포물 ↔ `uv.lock` 등식 판독기(M15/15T, D-15T-4).

이 파일은 **판독만** 한다 — docker 질의와 자기 변이 대조는 `tools/image-pip-lock-check.sh`
가 지고, 여기에는 넷째 입력 파일을 받아 두 등식을 재는 순수 함수만 있다. 가른 이유는
변이 대조 때문이다: 같은 판독기를 **바꿔치운 사본**에 다시 걸어야 하고, 그러려면 판독기가
파일 경로만 받는 것이어야 한다(이미지를 다시 뜨지 않는다).

세 술어(계약 D-15T-4 의 둘 + 수정 라운드 1 의 (다), cr F-2):

  (가) 설치 집합 == 보고서 집합 ∪ {프로젝트 자신} ∪ {base 가 싣고 온 것}
      — 세 모집단 모두 **측정값**이다. 「base 가 싣고 온 것」은 손 목록이 아니라 같은 base
        다이제스트 이미지에 같은 질의를 걸어 받는다(셸이 잰다). 「프로젝트 자신」도 손 이름이
        아니라 lock 에서 **registry 가 아닌 단 하나의 항목**으로 도출한다 — 그 항목이 둘이거나
        없으면 판정 불가다. **(이름, 판) 단위다 — 바이트를 보지 않는다.**
  (나) 보고서 항목마다 보고서가 적은 archive 의 sha256 ∈ lock 의 그 (이름, 판) 해시 집합
      — `--require-hashes` 플래그와 **독립된 측정**이다. 플래그가 사라져도 보고서에 적힌 바이트가
        lock 과 다르면 여기서 붉는다. 보고서는 설치 RUN **한 걸음의 기록**이고 최종 파일시스템의
        측정이 아니다(같은 (이름, 판)의 강제 재설치는 이 술어 밖 — 알려진 제한).
  (다) 보고서 집합의 **이름**이 lock 의 프로젝트 항목이 선언한 직접 의존을 **포함**한다 —
        무조건 항목 + `extra == '<--extra>'` 항목. 앞 판 Dockerfile 은 `pip install
        "<wheel>[serving]"` 로 extras 닫힘을 wheel 메타데이터에서 받았고, 목록 설치로 바꾸면서
        그 보증이 사라졌다(목록이 좁아지면 이미지도 함께 좁아져 (가)·(나) 둘 다 초록이다).
        닫힘을 다시 구현하지 않는다 — **직접 의존 이름의 포함**만 본다. 그 집합이 비면 정지다.

종료 코드(이 저장소의 게이트 관례): **0 통과 · 1 이미지가 기준을 어겼다 · 2 판정 불가**.
「판정 불가」로 끊는 자리는 조용한 통과가 생길 수 있는 모든 자리다 — 보고서 스키마 판이 다름,
해시를 싣지 않은 항목, 빈 모집단, lock 의 프로젝트 항목이 하나가 아님.

사용법:

    tools/image-pip-lock-check.py --lock <uv.lock> --report <report.json> \
        --installed <설치 집합 파일> --base <base 집합 파일> --extra <extra 이름>

`--installed`·`--base` 는 한 줄에 `<정규화 이름> <판>` 꼴이다(셸이 이미지 안
`importlib.metadata` 로 만든다).
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

# 보고서 스키마 판. pip 25.0.1 실측값은 문자열 "1" 이다 — 판이 달라지면 `download_info`
# 의 모양도 달라질 수 있으므로 **통과가 아니라 정지**로 받는다(열거가 아니라 등식).
SUPPORTED_REPORT_VERSION = "1"

EXIT_OK = 0
EXIT_VIOLATION = 1
EXIT_UNDECIDABLE = 2


class Undecidable(Exception):
    """판정 불가 — 정책·환경·스키마가 틀려 등식을 잴 수 없다(exit 2)."""


def normalize(name: str) -> str:
    """PEP 503 정규화 — `Foo_Bar.baz` 와 `foo-bar-baz` 가 같은 이름이 되게 한다."""
    return re.sub(r"[-_.]+", "-", name).lower()


def read_pairs(path: Path, label: str) -> set[tuple[str, str]]:
    """`<이름> <판>` 줄 목록을 집합으로 읽는다. 빈 모집단은 판정 불가다."""
    pairs: set[tuple[str, str]] = set()
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line:
            continue
        fields = line.split()
        if len(fields) != 2:
            raise Undecidable(f"{label}({path}) {lineno}번째 줄이 '<이름> <판>' 두 칸이 아니다: {line!r}")
        pairs.add((normalize(fields[0]), fields[1]))
    if not pairs:
        raise Undecidable(f"{label}({path})가 비어 있다 — 질의가 끊겼다면 「차이 0」은 같다는 뜻이 아니다")
    return pairs


def required_names(project_entry: dict[str, object], extra: str) -> set[str]:
    """lock 의 프로젝트 항목이 **선언한** 직접 의존 이름 — 무조건 + `extra == '<extra>'`.

    닫힘 해석이 아니다(전이 의존은 보지 않는다). 표식의 **꼴이 바뀌면 정지**한다 — 겹 표식
    (`extra == 'x' and sys_platform == 'linux'`)을 조용히 빼면 (다)가 그만큼 비어 버린다.
    """
    metadata = project_entry.get("metadata")
    if not isinstance(metadata, dict):
        raise Undecidable("lock 의 프로젝트 항목에 [package.metadata] 가 없다 — (다)를 잴 수 없다")
    provides = metadata.get("provides-extras")
    if not isinstance(provides, list) or extra not in provides:
        raise Undecidable(
            f"lock 의 프로젝트 항목이 extra {extra!r} 를 선언하지 않는다(provides-extras={provides!r})"
            " — extra 이름이 바뀌었거나 인자가 틀렸다"
        )
    entries = metadata.get("requires-dist")
    if not isinstance(entries, list) or not entries:
        raise Undecidable("lock 의 프로젝트 항목에 requires-dist 가 없다 — (다)를 잴 수 없다")

    wanted: set[str] = set()
    for entry in entries:
        if not isinstance(entry, dict) or not isinstance(entry.get("name"), str):
            raise Undecidable(f"requires-dist 항목을 읽지 못했다: {entry!r}")
        name = normalize(entry["name"])
        marker = entry.get("marker")
        if marker is None:
            wanted.add(name)
            continue
        if not isinstance(marker, str):
            raise Undecidable(f"requires-dist 의 marker 가 문자열이 아니다: {entry!r}")
        collapsed = " ".join(marker.split())
        if collapsed in (f"extra == '{extra}'", f'extra == "{extra}"'):
            wanted.add(name)
        elif f"extra == '{extra}'" in collapsed or f'extra == "{extra}"' in collapsed:
            raise Undecidable(
                f"requires-dist 의 표식 꼴이 단순 extra 비교가 아니다: {marker!r}"
                " — 겹 표식을 조용히 빼면 (다)가 그만큼 비어 버린다"
            )
    if not wanted:
        raise Undecidable(
            f"extra {extra!r} 가 요구하는 직접 의존 이름이 0 건이다 — (다)가 공허하게 참이 된다"
        )
    return wanted


def load_lock(
    path: Path,
) -> tuple[dict[tuple[str, str], set[str]], tuple[str, str], dict[str, object]]:
    """`uv.lock` 을 **조회용으로만** 읽는다 — 닫힘 해석을 다시 구현하지 않는다.

    stdlib `tomllib` 만 쓴다(`container` job 에 uv 가 없다). 반환은 세 개다 —
    (이름, 판) → sha256 집합, 프로젝트 자신의 (이름, 판), 그리고 프로젝트 항목 자체((다)의 입력).
    """
    try:
        import tomllib
    except ModuleNotFoundError as exc:  # pragma: no cover - 3.11+ 에는 항상 있다
        raise Undecidable(f"tomllib 을 쓸 수 없다(Python 3.11+ 필요): {exc}") from exc

    with path.open("rb") as handle:
        data = tomllib.load(handle)

    packages = data.get("package")
    if not isinstance(packages, list) or not packages:
        raise Undecidable(f"lock({path})에서 package 목록을 읽지 못했다")

    hashes: dict[tuple[str, str], set[str]] = {}
    project_entries: list[tuple[tuple[str, str], dict[str, object]]] = []
    for entry in packages:
        name = entry.get("name")
        version = entry.get("version")
        if not isinstance(name, str) or not isinstance(version, str):
            raise Undecidable(f"lock 의 package 항목에 name·version 이 없다: {entry!r}")
        key = (normalize(name), version)
        source = entry.get("source")
        if not isinstance(source, dict) or "registry" not in source:
            # registry 가 아닌 항목 == 이 저장소가 스스로 만드는 것(editable/virtual).
            project_entries.append((key, entry))
            continue
        collected: set[str] = set()
        sdist = entry.get("sdist")
        if isinstance(sdist, dict) and isinstance(sdist.get("hash"), str):
            collected.add(sdist["hash"])
        for wheel in entry.get("wheels") or []:
            if isinstance(wheel, dict) and isinstance(wheel.get("hash"), str):
                collected.add(wheel["hash"])
        # **덮어쓰지 않고 합친다**(cr F-4). uv 의 fork resolution·여러 registry 에서 같은
        # (이름, 판) 항목이 둘 생기면 대입은 앞 항목의 해시를 지운다 — 방향은 거짓 빨강이지만
        # 사유가 「받은 바이트가 lock 에 없다」로 나와 원인을 가리키지 않는다. 오늘 중복 0 건.
        hashes.setdefault(key, set()).update(
            value.split("sha256:", 1)[1] for value in collected if value.startswith("sha256:")
        )

    if len(project_entries) != 1:
        raise Undecidable(
            "lock 에서 registry 가 아닌 항목이 정확히 하나가 아니다"
            f"({len(project_entries)}개: {sorted(k for k, _ in project_entries)})"
            " — 프로젝트 자신을 도출할 수 없다"
        )
    if not hashes:
        raise Undecidable(f"lock({path})에 registry 항목이 없다 — 대조할 해시가 0 이다")
    return hashes, project_entries[0][0], project_entries[0][1]


def load_report(path: Path) -> list[tuple[str, str, str, str]]:
    """pip 설치 보고서에서 (이름, 판, sha256, url) 을 뽑는다.

    해시를 싣지 않은 항목은 **통과가 아니라 정지**다(계약 D-15T-4). 그 자리를 통과로 두면
    보고서가 해시를 잃는 날 (나)가 조용히 비어 버린다.
    """
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise Undecidable(f"보고서({path})가 JSON 이 아니다: {exc}") from exc

    version = data.get("version")
    if str(version) != SUPPORTED_REPORT_VERSION:
        raise Undecidable(
            f"보고서 스키마 판이 {version!r} 이다 — 이 판독기는 {SUPPORTED_REPORT_VERSION!r} 만 읽는다"
        )

    install = data.get("install")
    if not isinstance(install, list) or not install:
        raise Undecidable(f"보고서({path})의 install 목록이 비었다 — 설치가 0 건이면 대조도 0 건이다")

    items: list[tuple[str, str, str, str]] = []
    for index, entry in enumerate(install):
        metadata = entry.get("metadata") if isinstance(entry, dict) else None
        download = entry.get("download_info") if isinstance(entry, dict) else None
        if not isinstance(metadata, dict) or not isinstance(download, dict):
            raise Undecidable(f"보고서 {index}번 항목에 metadata·download_info 가 없다")
        name = metadata.get("name")
        version_value = metadata.get("version")
        if not isinstance(name, str) or not isinstance(version_value, str):
            raise Undecidable(f"보고서 {index}번 항목의 이름·판을 읽지 못했다: {metadata!r}")
        archive = download.get("archive_info")
        digest = None
        if isinstance(archive, dict):
            candidates = archive.get("hashes")
            if isinstance(candidates, dict):
                digest = candidates.get("sha256")
        if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise Undecidable(
                f"보고서 항목 {name} {version_value} 에 sha256 이 없거나 모양이 아니다 — "
                "해시 없는 항목은 통과가 아니라 정지다"
            )
        url = download.get("url")
        items.append(
            (normalize(name), version_value, digest, url if isinstance(url, str) else "")
        )
    return items


def check(
    lock_path: Path, report_path: Path, installed_path: Path, base_path: Path, extra: str
) -> tuple[int, list[str], str]:
    """세 술어를 재고 (종료 코드, 위반 문면 목록, 요약 한 줄) 을 낸다."""
    lock_hashes, project, project_entry = load_lock(lock_path)
    wanted = required_names(project_entry, extra)
    report_items = load_report(report_path)
    installed = read_pairs(installed_path, "설치 집합")
    base = read_pairs(base_path, "base 집합")

    violations: list[str] = []

    # (나) 바이트 — 보고서 항목마다 lock 의 그 (이름, 판) 해시 집합에 드는가.
    compared = 0
    for name, version, digest, url in report_items:
        key = (name, version)
        known = lock_hashes.get(key)
        if known is None:
            violations.append(
                f"(나) 보고서 항목 {name}=={version} 이 lock 에 없다 — 검사한 집합 밖의 배포물이 들어왔다"
            )
            continue
        compared += 1
        if digest not in known:
            violations.append(
                f"(나) {name}=={version} 이 실제로 내려받은 바이트의 sha256 이 lock 의 해시 집합"
                f"({len(known)}개)에 없다 — 받은 것: {digest[:16]}… / 출처: {url[-60:]}"
            )

    if compared == 0:
        raise Undecidable("lock 과 대조한 보고서 항목이 0 건이다 — 「불일치 0」이 공허하게 참이다")

    # (가) 설치 집합 — 세 모집단의 합집합과 양방향 등식.
    report_set = {(name, version) for name, version, _digest, _url in report_items}
    expected = report_set | {project} | base
    unexpected = sorted(installed - expected)
    missing = sorted(expected - installed)
    for name, version in unexpected:
        violations.append(
            f"(가) 이미지에 {name}=={version} 이 설치돼 있는데 보고서·프로젝트·base 어디에도 없다"
            " — 해시 없는 설치 걸음이 뒤에 더해졌을 수 있다"
        )
    for name, version in missing:
        violations.append(
            f"(가) {name}=={version} 이 보고서·프로젝트·base 에는 있는데 이미지에 설치돼 있지 않다"
        )

    # (다) 선언 — 목록이 좁아지는 경로는 (가)·(나)가 둘 다 초록이므로 **lock 의 선언**과 대조한다.
    report_names = {name for name, _version, _digest, _url in report_items}
    for name in sorted(wanted - report_names):
        violations.append(
            f"(다) lock 이 extra {extra!r} 의 직접 의존으로 선언한 {name} 이 설치 보고서에 없다"
            " — 설치 목록이 wheel 의 선언보다 좁다"
        )

    summary = (
        f"lock-패키지={len(lock_hashes)}건 보고서-항목={len(report_items)}건 해시-대조={compared}건 "
        f"설치-집합={len(installed)}건 base-집합={len(base)}건 "
        f"프로젝트={project[0]}=={project[1]} extra={extra}·요구-이름={len(wanted)}건"
    )
    return (EXIT_VIOLATION if violations else EXIT_OK), violations, summary


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(add_help=True, description=__doc__)
    parser.add_argument("--lock", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--installed", required=True, type=Path)
    parser.add_argument("--base", required=True, type=Path)
    parser.add_argument("--extra", required=True)
    args = parser.parse_args(argv)

    for label, path in (
        ("lock", args.lock),
        ("보고서", args.report),
        ("설치 집합", args.installed),
        ("base 집합", args.base),
    ):
        if not path.is_file():
            print(f"{label} 파일이 없다: {path}", file=sys.stderr)
            return EXIT_UNDECIDABLE

    try:
        code, violations, summary = check(
            args.lock, args.report, args.installed, args.base, args.extra
        )
    except Undecidable as exc:
        print(f"판정 불가: {exc}", file=sys.stderr)
        return EXIT_UNDECIDABLE

    for line in violations:
        print(f"등식 위반: {line}", file=sys.stderr)
    print(f"-- lock 등식 요약 --\n{summary}")
    if code != EXIT_OK:
        print(f"== lock 등식 실패: {len(violations)}건 ==", file=sys.stderr)
    return code


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
