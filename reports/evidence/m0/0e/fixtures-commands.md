# M0 / 0E — `fixtures/` 검증 명령과 종료 코드

전부 저장소 루트에서 실행한다. 실행일 2026-08-30, **F-1~F-4·F-7 재실행 2026-08-31**
(재판정 · 그 정정 · 어휘 판정 — 기대값·입력 파일은 한 번도 바뀌지 않았고 manifest 의 분류·출처만
바뀌었다).
네트워크·DB·외부 API 호출 없음.
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
- 핵심 결과: 모든 case 의 (input + expected) 가 OK, **FAILED 0**. 검사 분모는 **분류와 무관하게 `cases`
  전건**이다 — `insufficient-evidence` 로 내린 case 도 파일이 그대로 남아 해시 대조를 받는다.
  **셈은 이 명령의 출력이 낸다**(`shasum -c` 의 OK 줄 수 = case 수 × 2)

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

**앞서 이 자리는 `git -C bid-vector log --oneline -1 ed4b06c`를 exit 0으로 적었고 그것은 주
작업 디렉터리에서만 참이다.** `bid-vector`는 `.gitignore` 된 symlink 라 커밋되지 않으므로
**clean review worktree 에는 그 경로가 없다** — 감사자가 같은 명령을 돌리면 exit 128 이고
pinned SHA 와 `legacy_reference`를 독립 재검증할 수 없었다. 아래 **F-5.0** 이 그 실측이고,
**F-5.1~F-5.3** 이 체크아웃 없이 도는 재검증 경로다.

### F-5.0 · clean worktree 실측 (재현: 종전 명령이 왜 성립하지 않는가)

- cmd:
  ```bash
  git worktree add -q --detach /tmp/wt-f5 HEAD
  cd /tmp/wt-f5 && test -e bid-vector; echo "test -e: $?"
  git -C bid-vector log --oneline -1 ed4b06c; echo "git: $?"
  cd - >/dev/null && git worktree remove /tmp/wt-f5
  ```
- exit: `test -e bid-vector` **1** · `git -C bid-vector log ...` **128**
- 핵심 결과: `fatal: cannot change to 'bid-vector': No such file or directory`.
  **clean worktree 에 legacy 체크아웃이 없다는 것이 실측으로 확인됐다**

### F-5.1 · 저장소 안 불변 산출물 — 파싱과 선언 해시 대조

legacy 좌표를 저장소 안에 고정한 것이 `fixtures/legacy-reference-index.json` 이다. legacy
파일 **내용은 옮기지 않고** pinned commit 과 각 경로의 **git object id** 만 싣는다.

- cmd:
  ```bash
  python3 -c "import json; json.load(open('fixtures/legacy-reference-index.json')); print('parsed')"
  printf '%s  fixtures/legacy-reference-index.json\n' \
    "$(awk '/^  index_file_sha256: /{print $2}' fixtures/manifest.yaml)" | shasum -a 256 -c -
  ```
- exit: 0
- 핵심 결과: 파싱 성공, manifest `legacy_repo.index_file_sha256` 과 파일 해시 일치(OK)

### F-5.2 · index ↔ manifest `legacy_reference` 집합 대조 (legacy 체크아웃 불필요)

- cmd:
  ```bash
  /tmp/fxvenv/bin/python -c "
  import json, yaml
  ix = json.load(open('fixtures/legacy-reference-index.json'))
  m  = yaml.safe_load(open('fixtures/manifest.yaml'))
  sha = ix['legacy_repo']['pinned_sha_short']
  idx = {e['path'] for e in ix['manifest_legacy_reference_paths']['entries']}
  ref, bad = set(), []
  for c in m['cases']:
      lr = c.get('legacy_reference')
      if not lr: continue
      if lr['repo_sha'] != sha: bad.append(c['id'])
      ref |= set(lr.get('files', []))
  assert sha == m['legacy_repo']['pinned_sha'] and \
         ix['legacy_repo']['pinned_sha_full'] == m['legacy_repo']['pinned_sha_full']
  assert not bad, bad
  assert idx == ref, (idx ^ ref)
  assert all('object_id' in e for e in ix['manifest_legacy_reference_paths']['entries'] +
                                       ix['data_extract_section_2_paths']['entries'])
  print('paths', len(idx), '| de-section-2', len(ix['data_extract_section_2_paths']['entries']))
  "
  ```
- exit: 0
- 핵심 결과: 경로 집합 일치(대칭차 0), 모든 case 의 `repo_sha` 가 pinned SHA 와 같음,
  두 목록의 모든 항목이 `object_id` 를 가짐. **이 검사는 legacy 체크아웃 없이 돈다**

### F-5.3 · legacy 체크아웃이 있을 때의 좌표 재검증 (준비 절차 포함)

**준비 절차** — 이 저장소는 legacy 체크아웃을 담지 않는다. 감사자는 운영자에게서 legacy
체크아웃을 받아 임의 경로 `$LEGACY` 에 두고 **읽기만** 한다(원격 URL 은 운영자·회사 식별자를
담아 저장소에 적지 않는다). 고정 SHA 는 `ed4b06cbb8862c7cf121bb27d7cb962afe42270e` 이고,
아래 명령이 그 SHA 와 각 경로의 object id 를 index 와 대조한다.

- cmd:
  ```bash
  LEGACY=bid-vector   # 감사자는 자기 체크아웃 경로로 바꾼다
  python3 -c "
  import json, subprocess, os
  L = os.environ['LEGACY']
  ix = json.load(open('fixtures/legacy-reference-index.json'))
  full = ix['legacy_repo']['pinned_sha_full']; sha = ix['legacy_repo']['pinned_sha_short']
  got = subprocess.run(['git','-C',L,'rev-parse',sha+'^{commit}'],capture_output=True,text=True)
  assert got.returncode == 0 and got.stdout.strip() == full, got
  n = 0
  for k in ('data_extract_section_2_paths','manifest_legacy_reference_paths'):
      for e in ix[k]['entries']:
          r = subprocess.run(['git','-C',L,'rev-parse',f\"{sha}:{e['path']}\"],
                             capture_output=True,text=True)
          assert r.returncode == 0 and r.stdout.strip() == e['object_id'], (e['path'], r)
          n += 1
  print('verified', n)
  "
  ```
- exit: 0 (**주 작업 디렉터리의 read-only symlink 로 실행**. clean worktree 에서는 `$LEGACY`
  가 없어 이 검사가 성립하지 않고, 그때 서는 것이 **F-5.1·F-5.2** 다)
- 핵심 결과: pinned commit 일치, 39개 좌표(§2 목록 15 + `legacy_reference` 24) 전부 object id
  일치. **읽기만 했고 legacy Python 을 실행하지 않았다**

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
  print('classification', dict(collections.Counter(c['classification'] for c in m['cases'])))
  print('source kinds', dict(collections.Counter(c['source']['kind'] for c in m['cases'])))
  print('kind x class', dict(collections.Counter(
        (c['source']['kind'], c['classification']) for c in m['cases'])))
  print('approved_by_user', set(c['review']['approved_by_user'] for c in m['cases']))
  print('provisional', [c['id'] for c in m['cases'] if 'provisional' in c])
  print('with not_covered', sum(1 for c in m['cases'] if 'not_covered' in c))
  print('with change_history', sum(1 for c in m['cases'] if c['change_history']))
  print('with legacy_reference', sum(1 for c in m['cases'] if 'legacy_reference' in c))
  "
  ```
- exit: 0
- 핵심 결과: YAML 파싱 성공. `data-extract.md` §3 필수 키 + 선언 확장 키의 누락 0건.
  **셈(case 수 · 분류별 분포 · `source.kind` 분포와 그 교차 · `uncovered_axes` 수)은 이 명령이
  낸다 — 여기 옮겨 적지 않는다.** `kind x class` 가 **`m0-derived-rule` ↔ `insufficient-evidence`
  대응이 전건인지**를 한 줄로 보인다(운영자 결정 2026-08-31,
  `manifest.yaml`의 `classification_policy.insufficient_evidence`)

### F-7b · `verified_paths` 계약의 구조 검사

운영자 결정 2026-08-31(assertion path 계약화)이 세운 필드를 기계로 본다. **세 가지** —
`authoritative` 전건에 있고 `insufficient-evidence` 전건에 없는가 · 목록이 비었거나 중복인가 ·
**목록의 모든 경로가 그 case 의 기대값 JSON 에 실재하는가**. 셋째가 핵심이다: 경로가 오타이거나
기대값에 없으면 그 계약은 아무것도 비교하지 못한다.

- cmd:
  ```bash
  /tmp/fxvenv/bin/python -c "
  import yaml, json, re
  m = yaml.safe_load(open('fixtures/manifest.yaml'))
  def resolve(o, p):
      cur = o
      for key, idx in re.findall(r'\.([A-Za-z_][A-Za-z0-9_]*)|\[(\d+)\]', p):
          if key:
              if not isinstance(cur, dict) or key not in cur: return False
              cur = cur[key]
          else:
              i = int(idx)
              if not isinstance(cur, list) or i >= len(cur): return False
              cur = cur[i]
      return True
  bad = []
  for c in m['cases']:
      vp = c.get('verified_paths')
      if c['classification'] == 'authoritative':
          if not vp: bad.append((c['id'], 'authoritative 인데 목록이 없거나 비었다')); continue
          if len(vp) != len(set(vp)): bad.append((c['id'], '중복 경로'))
          exp = json.load(open(c['expected_file']))
          for p in vp:
              if not p.startswith('\$.'): bad.append((c['id'], 'prefix: ' + p))
              elif not resolve(exp, p): bad.append((c['id'], '기대값에 부재: ' + p))
      elif vp is not None:
          bad.append((c['id'], 'insufficient-evidence 인데 목록이 있다'))
  print('with verified_paths', sum(1 for c in m['cases'] if 'verified_paths' in c))
  print('violations', bad)
  "
  ```
- exit: 0
- 핵심 결과: `violations` **빈 목록**. 필드 보유 수는 이 명령이 낸다 — **`authoritative` 전건과
  같아야 하고 그 대조는 F-7 의 `classification` 줄과 함께 읽는다.**
  **이 검사가 보지 못하는 것을 적어 둔다** — 경로가 **실재하는가**는 재지만 그 목록이 그 case 의
  `verifies` 를 **옳게 도출했는가**는 기계가 재지 못한다. 그것은 사람이 읽는 자리이고
  도출 규칙은 `manifest.yaml` 의 `schema.extensions.verified_paths` 가 적는다

## F-8 · mutation 스윕 — 분류 기준의 재실행

**분류를 결정하는 스윕이 저장소 안에 있다**(운영자 승인 2026-09-02, Codex 재리뷰 B10
medium #3). 앞선 라운드는 `_workspace/`(`.gitignore`)에서 돌려 **감사자가 판정을 다시
낼 수 없었다.** **방법·적대 집합 갈래·판정 기준의 정본은 `commands.md` **C-15** 이고**,
이 항목은 그 판정을 **어떻게 다시 내는가**만 적는다.

- cmd:
  ```bash
  python3 fixtures/tools/mutation_sweep_adversarial.py   # 분류를 결정하는 스윕
  python3 fixtures/tools/mutation_sweep_targeted.py      # 표적 변이 + 표기 변형
  ```
- exit: 0 · 0
- 핵심 결과: 적대 스윕의 **강등 대상 0**, 곧 `authoritative` 전건이 판정
  「확장 적대 집합에서 위반 변이체 통과 0」을 만족한다. **잔존 수는 이 명령이 내고
  분류별 분포는 F-7 이 낸다 — 여기 옮겨 적지 않는다.** 표적 스윕에서 통과하는 유일한
  줄은 `대조군 — 무변이`(변이가 없어 통과가 정상)와 **승인 대기 절**이다

**표준 라이브러리만 쓴다.** 이 저장소의 `python3` 에 `PyYAML` 이 없어 F-7·F-7b 는 격리
venv 를 쓰지만, 스윕은 **clean HEAD 에서 추가 설치 없이** 돌아야 하므로 manifest 의 계약
부분만 읽는 좁은 reader(`fixtures/tools/manifest_contract.py`)를 함께 둔다. 그 reader 가
`PyYAML` 과 같은 값을 읽는지는 감사자가 직접 잰다:

- cmd: `/tmp/fxvenv/bin/python fixtures/tools/mutation_sweep_adversarial.py --crosscheck-pyyaml`
  (venv 준비는 **F-7** 이 적는다)
- exit: 0
- 핵심 결과: `pyyaml crosscheck OK` — reader 의 투영이 `PyYAML` 과 case 전건에서 일치.
  어긋나면 `ManifestFormatError` 로 멈춘다(조용한 오독을 만들지 않는다)

**강등 라운드 자체의 재현은 `--manifest` 가 받는다.** 기대값·입력 파일은 강등에서 바뀌지
않았으므로 강등 **이전** manifest 를 물리면 그날의 판정이 그대로 다시 나온다:

- cmd:
  ```bash
  git show 58e3331:fixtures/manifest.yaml > /tmp/pre-demotion-manifest.yaml
  python3 fixtures/tools/mutation_sweep_adversarial.py --manifest /tmp/pre-demotion-manifest.yaml
  ```
- exit: 0
- 핵심 결과: 강등 대상이 나온다. **그 수가 그날 내려간 수보다 크다 — 그것이 정상이다**:
  현재 스크립트가 담은 (a) 기준은 **B10 이 조인 뒤**의 것이고, 조인 결과가 강등이 아니라
  **계약 추가**였던 case 들은 그 옛 manifest 에서 아직 계약을 갖지 않는다. 두 집합의 차는
  `git diff 58e3331 HEAD -- fixtures/manifest.yaml` 의 `verified_paths` 줄이 낸다

---

## 실행하지 않은 것 (승인 대상)

| 항목 | 상태 |
| --- | --- |
| legacy Python 실행 (`legacy-observation` 수집) | **미실행** — `manifest.yaml` `access_approval_required` **A-3** |
| 운영 DB read | **미실행** — **A-1** · **A-2** |
| KONEPS OpenAPI 호출 | **미실행** — **A-4** |
| LLM · Telegram · email 호출 | **미실행** |
| push · merge · 배포 | **미실행** |
