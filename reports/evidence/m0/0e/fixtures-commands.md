# M0 / 0E — `fixtures/` 검증 명령과 결과

전부 저장소 루트에서 실행한다. 실행일 2026-08-30. 네트워크·DB·외부 API 호출 없음.

---

## F-1 · SHA-256 대조 (manifest ↔ 파일)

```bash
awk '/^    input_file: /{f=$2} /^    expected_file: /{g=$2} \
     /^    sha256: [0-9a-f]{64}$/{print $2"  "f} \
     /^    expected_sha256: [0-9a-f]{64}$/{print $2"  "g}' fixtures/manifest.yaml \
  | shasum -a 256 -c -
```

```text
lines: 126
OK:    126
FAILED: 0
```

63 case × (input + expected) = 126. 불일치 0건.

---

## F-2 · 모든 fixture JSON 파싱

```bash
python3 -c "
import json,glob
n=0
for f in sorted(glob.glob('fixtures/input/*.json')+glob.glob('fixtures/expected/*.json')):
    json.load(open(f)); n+=1
print('parsed OK:', n)
"
```

```text
parsed OK: 126
```

---

## F-3 · numeric discipline — legacy 숫자 미채택 검사

`data-dictionary.md` §12.1이 `legacy-behavior`로 등재한 수(비정수 율·배수·오차 · 금액 구간
경계 셋 · `-1.0`)가 fixture 의 input·expected 어디에도 없는지 본다. 면제 항목과 **검사가 못
보는 자리**는 스크립트 docstring 과 `manifest.yaml`의 `numeric_discipline.check.exempt`가 적는다.

```bash
python3 fixtures/tools/check_legacy_numbers.py
```

```text
total legacy-number hits: 0
exit=0
```

---

## F-4 · secret · 개인정보 스캔

```bash
grep -rniE '(api[_-]?key|secret|token|passwd|password|bearer|authorization|telegram|chat_id|@[a-z0-9.-]+\.(com|net|kr|org)|010-[0-9]{4}|[0-9]{6}-[0-9]{7})' fixtures/
```

```text
fixtures/manifest.yaml:  columns: 금액·율·차수·기관 유형·수집 시각. **회사/운영자 식별자,
                         Telegram id, 연락처, 원문 개인정보는 제외**
```

유일한 hit 는 `access_approval_required` **A-1**의 masking 계획이 적은 **제외 목록**이다.
fixture 데이터에는 0건.

fixture 안의 한글 문자열 전수:

```bash
grep -rhoE '"[가-힣]{2,}"' fixtures/input fixtures/expected | sort -u
```

```text
"건축공사업" "공고번호" "공고차수" "기술용역" "기초금액" "물품" "예정가" "예정가격"
"조경공사업" "추정가격" "토목공사업" "토목시설물축조업" "투찰가"
```

면허 종류명과 도메인 어휘뿐이다. **회사명·기관명은 하나도 없다** — 기관은 전부
`SYN-AGENCY-*`, 공고는 `SYN-NOTICE-*`.

---

## F-5 · legacy 기준 SHA 확인 (read-only)

```bash
git -C bid-vector log --oneline -1 ed4b06c
```

```text
ed4b06c feat(ml): Phase 3 PR2 — win-proxy 백테스트·캘리브레이션 리포트 (읽기 전용 측정) (#371)
```

`data-extract.md` §2가 든 12개 테스트 파일 전부 그 트리에 존재한다(`git cat-file -e`로 확인).
**읽기만 했고 실행하지 않았다.**

---

## F-6 · 기대값 인용의 대조 — 커밋된 HEAD 기준

병행 레인이 `docs/discovery/capability-map.md`를 working tree 에서 수정 중이므로, 인용이
**커밋된 HEAD**에도 있는지 확인해 인용 좌표를 고정한다.

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

```text
1	임계 사정률이 정확히 경계값인 표본은 미달로 세지 않는다
1	최소 표본 수 미만이면 값이 아니라 사유가 있는 측정 불가를 반환한다
1	lmtGrpNo` 결측 행 2개는 단일 그룹 AND로 폴딩된다
1	선언에 없는 raw 키가 응답에 나오면
1	매핑에 없는 코드가 오면 미지 코드로 표시되고 임의 라벨이 붙지 않는다
1	도급한도가 낮다는 이유로 공고가
1	기준일이 미상이면 시행일 표를 적용하지 않는다
```

`docs/discovery/data-dictionary.md`는 working tree 와 HEAD 가 같다(`git status` 미변경).

---

## F-7 · manifest YAML 파싱과 구조 점검

시스템 python 에 `PyYAML`이 없어 격리 venv 로 실행했다(저장소에 남기지 않았다).

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

```text
cases 63
uncovered 18 | insufficient 4 | access 4
classification {'authoritative'}
source kinds {'approved-spec': 18, 'operator-decision': 40, 'official-doc': 5}
approved_by_user {False}
provisional ['license-011']
with not_covered 20
with legacy_reference 40
```

`data-extract.md` §3 필수 키 + 선언 확장 키의 전수 존재도 같은 실행에서 확인했다(누락 0건).

---

## 실행하지 않은 것 (승인 대상)

| 항목 | 상태 |
| --- | --- |
| legacy Python 실행 (`legacy-observation` 수집) | **미실행** — `manifest.yaml` `access_approval_required` **A-3** |
| 운영 DB read | **미실행** — **A-1** · **A-2** |
| KONEPS OpenAPI 호출 | **미실행** — **A-4** |
| LLM · Telegram · email 호출 | **미실행** |
| push · merge · 배포 | **미실행** |
