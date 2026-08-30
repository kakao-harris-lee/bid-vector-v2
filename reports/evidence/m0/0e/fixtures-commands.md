# M0 / 0E — `fixtures/` 검증 명령과 종료 코드

전부 저장소 루트에서 실행한다. 실행일 2026-08-30. 네트워크·DB·외부 API 호출 없음.
**출력 전문을 싣지 않는다**(`evidence-pack` 규격) — 감사자는 명령을 다시 돌린다.

---

## F-1 · SHA-256 대조 (manifest ↔ 파일)

- cmd:
  ```bash
  awk '/^    input_file: /{f=$2} /^    expected_file: /{g=$2} \
       /^    sha256: [0-9a-f]{64}$/{print $2"  "f} \
       /^    expected_sha256: [0-9a-f]{64}$/{print $2"  "g}' fixtures/manifest.yaml \
    | shasum -a 256 -c -
  ```
- exit: 0
- 핵심 결과: 63 case × (input + expected) 전부 OK, FAILED 0

## F-2 · 모든 fixture JSON 파싱

- cmd:
  ```bash
  python3 -c "
  import json,glob
  n=0
  for f in sorted(glob.glob('fixtures/input/*.json')+glob.glob('fixtures/expected/*.json')):
      json.load(open(f)); n+=1
  print('parsed OK:', n)
  "
  ```
- exit: 0
- 핵심 결과: 파싱 실패 0

## F-3 · numeric discipline — legacy 숫자 미채택 검사

`data-dictionary.md` §12.1이 `legacy-behavior`로 등재한 수(비정수 율·배수·오차 · 금액 구간
경계 셋 · `-1.0`)가 fixture 의 input·expected 어디에도 없는지 본다. 면제 항목과 **검사가 못
보는 자리**는 스크립트 docstring 과 `manifest.yaml`의 `numeric_discipline.check.exempt`가 적는다.

- cmd: `python3 fixtures/tools/check_legacy_numbers.py`
- exit: 0
- 핵심 결과: legacy-number hits 0

## F-4 · secret · 개인정보 스캔

- cmd:
  ```bash
  grep -rniE '(api[_-]?key|secret|token|passwd|password|bearer|authorization|telegram|chat_id|@[a-z0-9.-]+\.(com|net|kr|org)|010-[0-9]{4}|[0-9]{6}-[0-9]{7})' fixtures/
  ```
- exit: 0
- 핵심 결과: **fixture 데이터 hit 0.** 유일한 hit 는 `access_approval_required` **A-1**의
  masking 계획이 적은 **제외 목록** 문장이다

- cmd: `grep -rhoE '"[가-힣]{2,}"' fixtures/input fixtures/expected | sort -u`
- exit: 0
- 핵심 결과: 면허 종류명과 도메인 어휘뿐 — **회사명·기관명 0건**. 기관은 전부 `SYN-AGENCY-*`,
  공고는 `SYN-NOTICE-*`

## F-5 · legacy 기준 SHA 확인 (read-only)

- cmd: `git -C bid-vector log --oneline -1 ed4b06c`
- exit: 0
- 핵심 결과: `ed4b06c` 실재. `data-extract.md` §2가 든 12개 테스트 파일이 전부 그 트리에
  있음을 `git cat-file -e`로 확인했다. **읽기만 했고 실행하지 않았다**

## F-6 · 기대값 인용의 대조 — 커밋된 HEAD 기준

병행 레인이 `docs/discovery/capability-map.md`를 working tree 에서 수정 중이므로, 인용이
**커밋된 HEAD**에도 있는지 확인해 인용 좌표를 고정한다.

- cmd:
  ```bash
  for p in '임계 사정률이 정확히 경계값인 표본은 미달로 세지 않는다' \
           '최소 표본 수 미만이면 값이 아니라 사유가 있는 측정 불가를 반환한다' \
           'lmtGrpNo` 결측 행 2개는 단일 그룹 AND로 폴딩된다' \
           '선언에 없는 raw 키가 응답에 나오면' \
           '매핑에 없는 코드가 오면 미지 코드로 표시되고 임의 라벨이 붙지 않는다' \
           '도급한도가 낮다는 이유로 공고가' \
           '기준일이 미상이면 시행일 표를 적용하지 않는다'; do
    printf '%s\t%s\n' "$(git show HEAD:docs/discovery/capability-map.md | grep -c "$p")" "$p"
  done
  ```
- exit: 0
- 핵심 결과: 일곱 인용 전부 커밋된 HEAD 에 존재. `data-dictionary.md`는 working tree 와
  HEAD 가 같다

## F-7 · manifest YAML 파싱과 구조 점검

시스템 python 에 `PyYAML`이 없어 격리 venv 로 실행했다(저장소에 남기지 않았다).

- cmd:
  ```bash
  python3 -m venv /tmp/fxvenv && /tmp/fxvenv/bin/pip install -q pyyaml
  /tmp/fxvenv/bin/python -c "
  import yaml, collections
  m = yaml.safe_load(open('fixtures/manifest.yaml'))
  print('cases', len(m['cases']))
  print('uncovered', len(m['uncovered_axes']),
        '| insufficient', len(m['insufficient_evidence']),
        '| access', len(m['access_approval_required']))
  print('classification', set(c['classification'] for c in m['cases']))
  print('source kinds', dict(collections.Counter(c['source']['kind'] for c in m['cases'])))
  print('approved_by_user', set(c['review']['approved_by_user'] for c in m['cases']))
  print('provisional', [c['id'] for c in m['cases'] if 'provisional' in c])
  print('with not_covered', sum(1 for c in m['cases'] if 'not_covered' in c))
  print('with legacy_reference', sum(1 for c in m['cases'] if 'legacy_reference' in c))
  "
  ```
- exit: 0
- 핵심 결과: YAML 파싱 성공. `data-extract.md` §3 필수 키 + 선언 확장 키의 누락 0건.
  **셈(case 수 · 층 · `source.kind` 분포 · `uncovered_axes` 수)은 이 명령이 낸다 — 여기 옮겨
  적지 않는다**

---

## 실행하지 않은 것 (승인 대상)

| 항목 | 상태 |
| --- | --- |
| legacy Python 실행 (`legacy-observation` 수집) | **미실행** — `manifest.yaml` `access_approval_required` **A-3** |
| 운영 DB read | **미실행** — **A-1** · **A-2** |
| KONEPS OpenAPI 호출 | **미실행** — **A-4** |
| LLM · Telegram · email 호출 | **미실행** |
| push · merge · 배포 | **미실행** |
