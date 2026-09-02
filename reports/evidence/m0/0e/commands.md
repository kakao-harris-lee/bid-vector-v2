# 실행 명령 — M0 / 0E · spec-writer 레인

**명령 · exit · 핵심 결과 한 줄.** 출력 전문을 붙이지 않는다 — 감사자는 명령을 다시 돌린다
(`.claude/skills/evidence-pack/SKILL.md`). 저장소 루트에서 돌린다.

## 이 range 에는 세 레인이 있다

`14686db..HEAD` 는 **spec-writer(이 evidence) · fixture-curator(`fixtures/**` · `fixtures-*.md`) · 하네스
(`.claude/**` · `CLAUDE.md`)** 셋의 커밋을 함께 담는다. **`C-0` 이 이 레인의 커밋 집합을 pathspec 으로 뽑고 `C-7` 이 그 집합만으로 불변을 확인한다** — 전체로 재면 다른 레인의 변경이 섞인다.

```sh
LANE=(docs/adr docs/discovery/capability-map.md milestone-0.md milestone-1.md \
      'v2-지침서.md' $(ls reports/evidence/m0/0e/*.md | grep -v fixtures-))   # 파일이 늘면 따라간다
```

| # | 명령 | exit | 핵심 결과 |
| --- | --- | --- | --- |
| **C-0** | `git log --format=%h 14686db..HEAD -- "${LANE[@]}"` | 0 | 이 레인의 커밋 집합 (`1fe87eb` 이후). **수를 여기 적지 않는다** — 이 명령이 낸다. `C-7a`·`C-7b` 의 분모다 |
| **C-1a** | `sed -n '/^## 12\. OPEN/,/^### 12\.1/p' docs/discovery/capability-map.md \| grep -cE '^\| *OPEN-[A-Z]+-[0-9]+ *\|'` | 0 | **43** (45 → 43. `OPEN-OPS-07`·`OPEN-QUAL-05` 해소) |
| **C-1b** | `sed -n '/^## 9\. \`OPEN\`/,/^## 10\./p' docs/discovery/regression-ledger.md \| grep -oE 'OPEN-REG-[0-9]+' \| sort -u \| wc -l` | 0 | **5** (무변경) |
| **C-1c** | `sed -n '/^## 9\. \`OPEN\`/,/^## 10\./p' docs/discovery/data-dictionary.md \| grep -coE '^\| \*\*\`OPEN-DIC-[0-9]+\`\*\*'` | 0 | **10** (무변경) |
| **C-1d** | `grep -hcE '^### \`OPEN-ADR-[0-9]+\`' docs/adr/*.md \| paste -sd+ - \| bc` | 0 | **11** (12 → 11. `OPEN-ADR-01` 해소) |
| **C-2** | 그룹별 셈 — `scripts` 없이 python3 인라인, §12 G1~G6 의 행 수 (전문은 이 표 아래) | 0 | `{G1:3, G2:7, G3:14, G4:8, G5:7, G6:4} TOTAL 43` — §12 머리말의 선언과 일치 |
| **C-3** | §14 커버리지 양방향 대조 (전문은 이 표 아래) | 0 | 활성 **69** · (B) **14** · (C) **55** · **활성인데 미분류 `[]` · 분류인데 비활성 `[]` · (B)∩(C) `[]`** |
| **C-4** | `git diff 14686db HEAD -- 'v2-지침서.md' milestone-1.md milestone-0.md` | 0 | 개정 셋의 전후 문면. Q1 `3.x`→`4.x`+4.x 재확인 항목 · Q2 유효기간 축 제거+미검증 노출 유지 · Q3 산출물 행 삭제+`ADR 0009` (d) 사유. `milestone-0.md` 는 「M0 완료 기록」(A/B 리뷰 · 승인 표의 ADR 행)도 함께 낸다 |
| **C-5a** | `git diff -U0 14686db HEAD -- docs/adr/ \| grep -cE '^@@'` | 0 | **11** = 아홉 파일의 「상태」 줄 각 1 hunk + `ADR 0001` §5 의 두 hunk. **§1~§4·§6 무접촉** — 파일별 분해는 같은 명령에 `awk '/^\+\+\+/{f=$2} /^@@/{c[f]++}'` 를 이어 붙이면 난다 |
| **C-5b** | `for f in docs/adr/*.md; do sed -n '3p' "$f"; done \| grep -c '^- \*\*상태\*\*: \*\*결정\*\*'` | 0 | **9** — 아홉 전부 「결정」 |
| **C-6a** | `grep -c '^## 3\. 대안 — .OPEN-OPS-07. 후보의 판정' docs/adr/0005-domain-events-and-outbox.md` | 0 | **1** — 종료 조건(ADR 대안 절)이 그 제목으로 산출물에 실재한다. **줄 번호를 여기 적지 않는다** — 문서가 자라면 낡으므로(실제로 230 → 236 → 239 로 표류했다) 자리를 재려면 같은 패턴에 `-n` 을 준다 |
| **C-6b** | `grep -c '^### \`OPEN-ADR-01\`' docs/adr/0001-target-architecture.md` | 1 | **0** — 취소선 없는 활성 제목 없음 (`^### ~~\`OPEN-ADR-01\`~~` 은 1) |
| **C-7a** | `for c in $(git log --format=%h 14686db..HEAD -- "${LANE[@]}"); do git diff --name-only $c^ $c; done \| sort -u` | 0 | 이 레인의 커밋들이 건드린 파일 집합. **수를 여기 적지 않는다** — 커밋이 늘면 바뀌므로 **이 명령이 낸다**. `in_scope` 밖 경로가 섞였는지는 `C-7b` 가 본다 |
| **C-7b** | 같은 목록 \| `grep -E '^(fixtures/\|docs/discovery/(data-dictionary\|regression-ledger)\|reports/evidence/m0/0[a-d])'` | **0** | **매치 하나 — `fixtures/manifest.yaml`.** `in_scope` 파일과 그 파일을 한 커밋에 담은 **혼합 커밋**(부류 A)이 있어 pathspec 이 두 레인에 걸친다. **선언된 예외**이고 부류·검출 명령·판정 규칙은 표 아래 **「`C-7b` 매치의 처리」**가 갖는다 — **커밋을 여기 열거하지 않는다**(열거하면 접촉이 늘 때마다 낡는다). **⚠ 이 검사는 이 레인의 `fixtures/` 접촉을 전수로 재지 못한다** — `C-0` 이 `in_scope` 경로로 레인을 뽑으므로 **`fixtures/manifest.yaml` 만 건드린 커밋(부류 B)은 입력에 들지 않는다.** 같은 절이 그 부류의 검출을 갖는다. `data-dictionary.md` · `regression-ledger.md` · 앞 여섯 slice evidence 는 여전히 **무접촉** |
| **C-8** | `for f in reports/evidence/m0/*/codex-review-*.json; do python3 -c "import json,sys;d=json.load(open(sys.argv[1]));print(d['verdict'],d['reviewed_base'][:8],d['reviewed_head'][:8])" "$f"; done` | 0 | 각 리뷰 JSON 의 `verdict`·base·head 를 낸다. **건수도 head SHA 도 여기 옮겨 적지 않는다** — verdict 가 등재될 때마다 바뀌므로 **이 명령이 낸다**. 읽는 법: slice 별 마지막 줄이 그 slice 의 현재 상태이고, 0A 의 `approve` 는 `6c6b3a2a..6af70199` 의 **별도 리뷰 A** 다(`C-9b`·`C-9c` 가 그 range 를 다룬다) |
| **C-9a** | `git log --oneline 6c6b3a2..6af7019 -- docs/discovery/capability-map.md` | 0 | 커밋 **4개** (`0b0c8aa`·`23b9c2a`·`0581e97`·`d140f92`) — 0A 라운드 4~6 |
| **C-9b** | `git diff --stat 6c6b3a2 6af7019 -- docs/discovery/capability-map.md` | 0 | **31 삽입 / 14 삭제** — 실행 시점에 어느 리뷰 range 에도 들지 않았던 delta. **그 뒤 별도 리뷰 A(`6c6b3a2..6af7019`) 가 덮어 `approve`** |
| **C-9c** | `python3 -c "import json;print(json.load(open('reports/evidence/m0/0a2/codex-review-20260827T034348Z.json'))['reviewed_base'])"` | 0 | `6af7019…` — 0A2 리뷰의 base 가 그 넷 **뒤**다 |
| **C-10** | `git diff --numstat 14686db HEAD -- 'v2-지침서.md' milestone-0.md milestone-1.md docs/adr docs/discovery/capability-map.md \| awk '{a+=$1;d+=$2} END{print a-d}'` · `ls reports/evidence/m0/0e/*.md \| grep -v fixtures- \| xargs wc -l \| tail -1` | 0 | **게이트 통과** — 판정은 `evidence ≤ 산출물`. **분자는 이 레인 evidence 전수**다(`fixtures-*.md` 는 다른 레인) — 파일이 늘면 `glob` 이 따라가므로 **이름을 열거하지 않는다**(`rollback.md` 신설이 그 실례다). **두 수를 여기 옮겨 적지 않는다** — 커밋이 늘 때마다 바뀌므로 **이 명령이 낸다** |
| **C-11** | B5-high 수정의 계약 불변 (전문은 이 표 아래) | 0 | `violations` **빈 목록**. ① `verified_paths` 의 어느 값도 **가족 A 의 네 이름이 아니다** ② `verified_projections` 는 다섯 필수 키 · 술어 `not-equals` · 피연산자가 가족 A 밖 ③ 같은 경로가 두 필드에 겹치지 않고 술어가 기대값에서 **참** ④ `insufficient-evidence` 에는 두 필드가 없다 ⑤ `change_history` 네 필수 키. **함께 fixture-curator 레인 `F-7b` 를 문면 그대로 재실행해** `violations` 빈 목록을 확인했다(신설 필드가 그 레인의 검사를 깨지 않는다). **이 검사가 못 보는 것**: `[i]` 첨자 경로는 값을 풀지 않고 건너뛴다 — 이 라운드가 바꾼 세 자리는 전부 최상위다 |
| **C-12b** | 강등 뒤 **낡은 덮개 주장** — manifest 전수, **문단(블록) 단위**. 스크립트는 `_workspace/m0-0e/mutation/c12b2.py`(작업 기록) | 0 | **불일치 11 — 전부 무해**(수정 전 18에서 넷을 닫았다). 줄 단위였던 앞 판이 **id 와 `authoritative` 가 다른 줄이면 놓쳤고**(Codex B12 medium) 문단 단위로 넓혔다. **주장형만 센다** — 덮개 동사(덮인다·고정한다·`authoritative` 로 남는다)와 case id 가 한 문단의 ±200자 안에 함께 있는 자리다. **남는 11 의 성격**: ① 결정 **축어**·`change_history` 의 **강등 기록**(과거 서술) ② 축 자신이 그 case 를 **강등된 이유로** 드는 자리 ③ 이미 ⚠ 로 층을 밝힌 자리. **이 검사가 못 보는 것**: 무해/유해를 기계가 가르지 못하므로 **잔존 목록은 사람이 읽는다** — 0 을 목표로 두지 않는다 |
| **C-12** | 해소된 `OPEN` 의 **현재형 활성/대기 서술** (전문은 이 표 아래) | 0 | **B5 medium 이 든 둘(`OPS-21` 본문 · §13 하류 인계 표)은 사라졌다.** 남는 매치는 **하나**이고 `OPEN-OPS-07` 의 주장이 아니다 — `capability-map.md` §12 의 **`OPEN-DEC-07` 행**(활성)이 자기 종료 조건 미충족을 적으며 **판정 기준으로** `OPEN-OPS-07` 을 인용하는 자리다. **이 검사가 못 보는 것**: 같은 줄 안의 근접만 재므로 「누구에 대한 주장인가」는 사람이 읽는다 |
| **C-13** | `codex-review-gate` **preflight** — 리뷰 B5 (전문은 이 표 아래) | 0 | ① repo 흔적(`memory_summary`·`MEMORY.md`·`rollout_summaries/`·`skills/`·`rules`) **5건** ② `stage1_outputs` 중 `bid-vector-v2-review` 언급 **0** / **총 647행** — 총 행이 0이 아니므로 **DB 가 실제로 열렸고** 「0행」이 「못 열어서 0」이 아니다(독립성 전건 성립) ③ **B5 가 실제로 돈 CLI 는 `0.148.0`** — **당시 PATH 가 고른 바이너리**이고, 정본은 실행 산출물 `_workspace/m0-0e/codex.raw-output-B5.txt` 의 머리글 `OpenAI Codex v0.148.0` 이며, 그 경로는 `.gitignore` 대상이라 clean worktree 에 없으므로 **저장소 안의 대응물은 verdict JSON 의 `reviewer.cli_version`** 이다 — 둘이 일치한다. **B~B4 는 0.149.0**(같은 디렉터리의 앞선 머리글 넷, verdict 쪽은 `C-8`). **알려진 제한 — B5 시점의 스킬은 심판 버전을 고정하지 않았다**: `git show dc27007^:.claude/skills/codex-review-gate/SKILL.md` 의 §4 가 맨 `codex` 를 부르므로 **어느 바이너리가 도는지를 PATH 가 정했고** 그래서 라운드마다 갈렸다(0.149.0 → 0.148.0). 이 evidence 를 쓰는 **도중에도 갈렸다** — `codex --version` 이 **0.148.0 → 0.151.0** 으로 바뀌었고 지금 `type -a codex` 는 `~/.nvm/versions/node/v22.21.1/bin/codex`(**0.151.0**) 하나만 낸다. **이 축은 다른 레인이 같은 range 에서 닫았다** — 하네스 커밋 `dc27007`(**운영자 지정 2026-09-01**)이 `CODEX_BIN` 을 절대 경로로, `CODEX_PIN` 을 **0.151.0** 으로 고정했다. **그 핀은 B6 부터 적용된다** — 그래서 **B5 와 B6 는 엔진 버전이 다르고**, 스킬 자신이 그것을 *"조용한 PATH 결과가 아니라 여기 기록된 명시적 결정"* 이라 적는다. 판단은 그 커밋이 갖는다(**이 slice 범위 밖**) |
| **C-20** | `codex-review-gate` **preflight** — 리뷰 B11 | 0 | ① repo 흔적 **4** ② 매치 **0** / 총 **683행** — **B10 과 같고 총계가 오르지 않은 첫 라운드**(661 → 674 → 683 → 683). 배치 정지인지 일시 정지인지 **한 점으로는 가릴 수 없다** — 조건(「매치 0 · 총계 ≠ 0」)은 성립하고 **다음 라운드에 재관측**한다 ③ **`0.151.0`** ④ 누출 줄 기준 **4** · developer 구간 **0**(경계 = codex 첫 출력 **141** 행) ⑤ **방출 2 · 조기 `approve` 1** — **142** 행 `approve`(findings 0 · **`commands_run` 0**, **이번 라운드 신규 관측**: 앞선 라운드는 서술 하나였다) · **11157** 행 종말 `request_changes`(findings 1 · cmds 20)이고 **저장본과 일치**한다. **11162 행은 CLI 후행 echo 라 제외**했다(`tokens used` 뒤) — **성문화된 계수 규칙(`codex` 표지 앞선 줄만)이 즉시 값을 했다**: 그 절 없이는 **3회**로 기록됐을 라운드다 ⑥ **hooks** 이벤트 8 · 스크립트 1 · 실행 실측 3경로 stdout **0 바이트**(`ORCA_*` unset) ⑦ **재현성 커밋의 직접 보상** — codex 가 **커밋된 스윕을 직접 실행**하고 `58e3331` 과 차분해 `verified_paths` **전건 삭제 변이를 재평가**했다(명령 20). **계약 결함 0** — 스윕을 `fixtures/tools/` 로 옮긴 그 라운드에 심판이 그것으로 계약을 독립 재현했다 |
| **C-19** | `codex-review-gate` **preflight** — 리뷰 B10 | 0 | ① repo 흔적 **4**(변동 없음) ② 매치 **0** / 총 **683행**(계열 647 → 655 → 661 → 674 → 683) — **조건이 여섯 측정 모두에서 성립** ③ **`0.151.0`** — B6~B10 이 같은 엔진 ④ **누출 — 주입 아님.** 줄 기준 **5** · **developer 구간 0**(경계 = codex 첫 출력 **139** 행) ⑤ **종말 verdict 확인 — codex 가 스스로 낸 verdict 는 4회**(`approve` 2 · `request_changes` 2)이고 **조기 `approve` 가 한 라운드에 둘**이다(**140** 행 = 명령 0건 · `commands_run` 이 *"리뷰를 시작합니다…"* 서술 하나 · **3150** 행 = 중반). 종말 `request_changes`(findings 3 · `commands_run` 15)가 저장본과 일치한다. **B8~B10 체계적이고, B10 의 새 사실은 「한 라운드에 두 번」이다** ⑥ **끝자락 `approve`(7684 행)는 방출이 아니다** — 그 줄은 codex 가 돌린 `jq -c … reports/evidence/m0/0a/codex-review-20260830T223932Z.json` 의 **stdout** 이고, base·head 가 **0A 리뷰 A 의 것**(`6c6b3a2`…`6af7019`)이라 이 range 의 판정이 아니다. **파일 읽기 출력을 방출로 세지 않는다** — 누출 검사의 판독 규칙과 같은 갈래다 ⑦ **hooks** 이벤트 **8** · 스크립트 1 · 실행 실측 3경로 stdout **0 바이트**(`ORCA_*` unset) ⑧ **프롬프트 정합** — 누출 grep 2건은 무해(`CODEX-REVIEW.md` 축어 · B5 이래의 색인 줄)이고 **B9 수정 논거는 프롬프트에 0자** |
| **C-18** | `codex-review-gate` **preflight** — 리뷰 B9 | 0 | ① repo 흔적 **4**(B8 과 같다) ② 매치 **0** / 총 **674행**(재측 **683**) — 계열 647 → 655 → 661 → 674 → 683 이고 **조건 「매치 0 · 총계 ≠ 0」이 다섯 측정 모두에서 성립** ③ **`0.151.0`** — B6~B9 가 같은 엔진이라 넷은 직접 비교 가능 ④ **누출 검사 — 주입 아님.** 발생 **11**(줄 기준 **5**) · **developer 구간 0**(codex 첫 출력 `137` 행) ⑤ **종말 verdict 확인이 값을 했다** — raw `138` 행에 **조기 `approve`** 가 나왔다(`findings` 빈 목록 · `commands_run` 은 명령이 아니라 *"검토를 시작합니다…"* 서술 하나). 종말 판정은 **`request_changes`**(findings 3 · `commands_run` 13)이고 **저장본과 일치**한다. **B8·B9 연속 재현이라 `--output-schema` 아래의 체계적 거동**으로 읽는다 — 조기 출력을 저장했으면 **없는 approve** 를 등재할 뻔했다(정본은 스킬) ⑥ **hooks 표면** — 등록 이벤트 **8**, 전부 orca 스크립트 하나(`~/.orca/agent-hooks/codex-hook.sh`)이고 `ORCA_*` 미설정 시 no-op. **실증 시험 세 경로(리뷰 환경 · 활성 경로+닫힌 포트 · 빈 payload)에서 stdout 0 바이트.** **정적 스캔은 이 판정을 못 낸다** — 파이프된 `printf` 를 오탐해 정상 라운드를 떨어뜨릴 뻔했다. **「실행으로 잰다」**가 그 교훈이고 정본은 스킬이다 |
| **C-17** | `codex-review-gate` **preflight** — 리뷰 B8 | 0 | ① repo 흔적 **4**(B7 과 같다) ② 매치 **0** / 총 **661행**(B7 655 · 재측 **674**) — **조건 「매치 0 · 총계 ≠ 0」이 네 측정 모두에서 성립**하고 **총계는 계속 는다**(647 → 655 → 661 → 674). 배치가 살아 있으므로 **잠든 조건으로 두지 않고 라운드마다 둘 다 잰다** ③ **`0.151.0`** — 머리글이 정본이고 verdict 와 일치, assertion 통과. **B6·B7 과 같은 엔진이라 셋은 직접 비교 가능** ④ **누출 검사 — 주입이 아니다.** 발생 **12**(줄 기준 **9**) · **developer 구간 0**(codex 첫 출력이 `135` 행이라 그 앞이 developer 다). **보정한 규칙이 이 라운드에 실제로 값을 했다** — 매치 둘(`1974`·`1984`)이 **첫 diff hunk(`4199`)보다 앞**인데 **codex 가 읽은 이 파일의 preflight 블록**이다. 옛 「diff 시작 전 = developer」 어림이었으면 **오탐으로 중단**했을 자리다. 나머지는 diff 안(`SKILL.md` 패턴 표·`CLAUDE.md` 이력) ⑤ **신규 표면 한 줄** — 이 세션이 **hook 262 이벤트**를 실행했다(orca hook: stdout 무기록 · `ORCA_*` 미설정 no-op 실측, 침해 없음). **위협 모델과 확인 절차의 정본은 스킬**(`d756492` 가 등재)이고 여기는 실행 기록이다 |
| **C-16** | `codex-review-gate` **preflight** — 리뷰 B7 (명령은 `C-13`·`C-14` 와 같다) | 0 | ① repo 흔적 **4**(B6 은 5 — 줄었다) ② 매치 **0** / 총 **655행**(B6 은 647이었고, 이 evidence 를 쓰며 재측 **661**) — **조건 「매치 0 · 총계 ≠ 0」이 세 측정 모두에서 성립**한다 ③ **`0.151.0`** — 머리글과 verdict 가 일치하고 assertion 을 통과했다. **B6 과 같은 엔진이라 B6·B7 판정은 직접 비교 가능**하고, B5(0.148.0)와 견줄 때만 엔진 단서가 붙는다 ④ **누출 검사 — 주입이 아니다.** 발생 **4**(줄 기준 **2**) · **developer 구간 0**. **B6 과 자리가 다르다** — 이번 둘은 diff 가 아니라 **codex 가 읽은 이 파일의 `C-13`·`C-14` 블록**이다(패턴 리터럴이 거기 적혀 있다). **그래서 「diff 시작 전」은 developer 구간의 기준이 아니다** — 기준은 **머리글 부근의 developer 메시지**이고, diff 든 파일 읽기든 **codex 가 뜬 텍스트는 리뷰 대상**이다(정본은 스킬). **관찰**: stage1 총계가 라운드마다 늘어 **배치가 살아 있다** — 독립성 전제는 서지만 잠든 조건이 아니므로 **매치 수와 총계를 라운드마다 둘 다 재야 한다** |
| **C-15** | **전수 mutation 스윕** — `authoritative` 전건. `python3 fixtures/tools/mutation_sweep_adversarial.py`(확장 집합) · `python3 fixtures/tools/mutation_sweep_targeted.py`(표기 변형 등) — 계약 평가는 `manifest_contract.py` 를 공유한다. **셋 다 커밋돼 있어 clean HEAD 에서 그대로 돈다**(재현 근거의 정본은 `fixtures-scope.md` **F-8**). `_workspace/m0-0e/mutation/` 의 원본은 **작업 기록**이고 **정본은 `fixtures/tools/`** 다. **건수를 여기 적지 않는다** — 스윕 자신이 낸다 | 0 | **방법**: case 마다 기대값 **격리 사본**에 적대적 변이를 넣고 계약(`verified_paths` ∪ `verified_projections`)을 재평가한다. **적대 집합은 일곱 갈래**다 — 불리언 반전 · enum 적대값 · reason 제거 · **projection 피연산자의 대소문자 변형**(B8 이 빠져나간 원인, verifier r16 F-8)에, **확장 세 갈래**(운영자 결정 2026-09-02, Codex B9 가 쓴 수)가 더해졌다: **(a) `verifies` 가 주장하는 필드의 삭제** · **(b) projection 경로의 null 치환** · **(c) 다른 확정 토큰 치환**. **(b)(c)와 표기 변형은 projection 전건에서 기계로 생성**하고, **(a)는 판단**이라 스윕 스크립트가 근거를 case 별 주석으로 싣는다. **(a)의 기준은 Codex B10(high)이 조였다** — `verifies` **문면만이 아니라 그것이 근거로 인용한 결정이 정하는 것**(사유 토큰 · 계약 형태 · 관계 주장의 축)**도 주장에 든다.** 이 레인의 앞선 해석선(「사유를 괄호에 적은 case 만」)은 **기각됐다.** **세 갈래의 정의와 그것이 연 결과는 manifest 가 갖는다** — `uncovered_axes` 의 「계약 술어 부재로 authoritative 를 유지하지 못한 축」이 갈래 셋을 ①②③으로 적고, **강등된 case 의 `not_covered` 가 자기를 통과시킨 갈래를 라벨로 싣는다**. **그 case 의 `verifies` 를 거짓으로 만드는 변이가 통과하면 과소 계약**이다. **핵심 결과**: 이 라운드가 넣은 계약으로 **B7 이 실증한 변이체가 잡힌다**(`money-basis-006` 전체 변이 · provenance 단독 · corpus 적격 단독 · `floor-shortfall-001` 의 0% 렌더). **남는 「위반」 통과는 `uncovered_axes` 의 「계약이 덮지 못한 축 — 승인된 피연산자 부재」가 받는다** — 목록은 이 명령이 낸다. **통과가 곧 위반은 아니다**: 계약 밖 필드는 대부분 **도출 규칙 ③**(`verifies` 가 언급하지 않는 동반 산출·입력 되울림)으로 **일부러** 빠진 자리이고, 그 case 가 **주장에서 내린** 표현은 각 case 의 `not_covered` 가 받는다. 셋은 다른 갈래다. 반대 방향(미승인 의미 잠금)은 `C-11` 이 `[]` 로 낸다. **판정은 조건형이고 이제 분류의 기준이다** — **「`authoritative` = 확장 적대 집합에서 위반 변이체 통과 0」**(운영자 결정 2026-09-02, 동결+강등). 통과가 하나라도 있으면 그 case 는 **기계적으로 `insufficient-evidence` 로 내려간다** — 술어를 새로 만들지 않는다(`schema.extensions` 의 동결). **어느 case 가 내려갔는지는 이 명령이 내고 산문에 열거하지 않는다** |
| **C-14** | `codex-review-gate` **preflight** — 리뷰 B6 (명령은 `C-13` 과 같고 **④ 누출 검사**가 붙는다) | 0 | **①②는 스냅숏이고 값이 아니라 조건이 판정이다** — `~/.codex` 는 배치와 운영자의 대화형 codex 가 계속 고치므로 다시 재면 다른 수가 나온다. 이 evidence 를 쓰며 재실행해 실제로 갈렸다. ① repo 흔적 **5건**(재측 **4**) — 조건은 *"그 양을 기록한다"* 이고 값의 고정이 아니다 ② `bid-vector-v2-review` 매치 **0** / 총 **647행**(재측 **655**) — **조건은 「매치 0」과 「총계가 0 이 아니다」 둘**이고 **두 측정 모두에서 성립한다**(총계가 0 이면 DB 를 못 연 것이라 preflight 미충족이다) ③ **`0.151.0`** — raw-output 머리글과 verdict 의 `reviewer.cli_version` 이 일치한다. **스킬이 `CODEX_BIN`·`CODEX_PIN` 을 도입한 뒤 첫 라운드이고 그 assertion 을 통과**했다(B5 는 0.148.0 이었다 — `C-13`) ④ **누출 검사 — 주입이 아니다.** 주입 패턴 넷의 발생 **9**(줄 기준 **8**, 한 줄이 두 패턴에 걸린다)가 **전부 codex 가 뜬 diff hunk 안**이다: `.claude/skills/codex-review-gate/SKILL.md`(**주입 패턴 표 자신**) 여섯 · `CLAUDE.md` 변경 이력 둘. **developer 메시지 구간(머리글 부근) 매치 0.** 하네스 커밋이 그 표를 이 range 의 diff 에 넣어 생긴 **false-positive 바닥**이며, 판독 규칙의 **정본은 스킬**이고 여기는 이 라운드의 실행 기록이다 |

**`C-7b` 매치의 처리 — 이 레인의 `fixtures/manifest.yaml` 접촉을 선언된 예외로 둔다.**

**이 자리가 `C-7a` 의 경로 집합과 `C-7b` 매치의 정본이다** — `checklist.md`·`scope.md` 는 여기를
가리키고 같은 사실을 따로 적지 않는다. 수는 어느 쪽에도 적지 않는다(`C-7a`·`C-7b` 가 낸다).

**이 절은 커밋을 열거하지 않는다 — 두 부류와 각 부류의 검출·판정 규칙만 세운다.** **A 혼합
커밋**(`in_scope` 와 한 커밋에 담긴 것) · **B manifest 단독 커밋**. 무엇이 났는지는 명령이 낸다.

```sh
# A — 두 레인 pathspec 에 동시에 걸리는 혼합 커밋 전수. C-7b 가 잡는 자리다
comm -12 <(git log --format=%h 14686db..HEAD -- "${LANE[@]}" | sort) \
         <(git log --format=%h 14686db..HEAD -- fixtures/manifest.yaml | sort)
# B — manifest 단독이라 C-0 의 pathspec 밖이고 위 교집합에 들지 않는다. 아래가 후보 전수이며
#     어느 줄이 이 레인의 것인지는 **pathspec 으로 갈리지 않는다** — 커밋 메시지를 읽어 가른다
git log --format='%h %s' 14686db..HEAD -- fixtures/manifest.yaml
```

**부류 A — 혼합 커밋.** *판정은 오케스트레이터가 냈고 **운영자가 승인했다**(2026-08-31).* 사후 승인 대상이던 자리가 닫혔다.

*결정 축어 — 물음과 답을 그대로 옮긴다(대신 판단하지 않는다).*
물음: *"오케스트레이터가 『8701882의 두 레인 교차 커밋은 선언된 예외』로 판정했고 운영자 사후 승인
대상으로 표지돼 있다. Codex는 checklist·scope가 아직 『fixtures 무접촉』을 주장해 모순이라 지적한다.
이 예외를 승인하는가?"*
답: ***"승인 + 정본 일치화"*** — 「불승인 — 미해결 제한으로 기록」을 물리치고 택한 답이다. 답이 든
선택지 문면: *"예외를 운영자 결정으로 기록하고 checklist·scope·rollback 서술을 C-7 실측과 한 정본으로
일치시킴."*

*이 레인이 그 답을 적용한 범위*(운영자 발화가 아니라 적용이다). 아래 판정 문면은 그대로 두고 표지만
「사후 승인 대상」에서 「승인됨」으로 옮겼으며, `checklist.md` 의 두 행과 `scope.md` 의 세 자리
(out_of_scope · rollback · 경계 서술)를 이 정본을 가리키는 형태로 고쳤다.

- **무엇이 잡히는가.** `8701882`(「`milestone-1.md` 인용 좌표를 `:12` 에서 `:10` 으로 — 두 자리」)이
  `milestone-0.md` 와 `fixtures/manifest.yaml` 을 한 커밋에 담았다. 두 레인의 pathspec 에 동시에
  걸리므로 `C-7b` 가 매치를 낸다.
- **왜 예외로 두는가.** ① 내용이 **한 인용 좌표 정정을 두 레인 파일에 일관 적용**한 것이라 레인별
  산출물을 갈라 쓴 자리가 아니다 — 두 자리 중 하나만 고치면 저장소가 같은 문장에 다른 좌표를
  갖는다. ② **소급 분리가 이 slice 의 게이트와 양립하지 않는다** — 부모 사슬 직선·amend/rebase
  금지가 걸려 있어 역사를 다시 쓸 수 없다. 그 커밋은 base `14686db` **뒤**이지만 **0E Codex 리뷰
  세 라운드의 head(`857e7767`·`74ef4415`·`71e3e3a1`) 전부의 조상**이라 이미 리뷰된 range 안에
  있다(`git merge-base --is-ancestor 8701882 <head>` 가 낸다). 분리하려면 리뷰된 역사를 다시
  써야 한다.
- **승인은 커밋마다 따로 선다.** 위 2026-08-31 승인은 그 물음이 든 접촉에만 걸리고 **다른
  접촉으로 번지지 않는다.** 그래서 이 절은 근거를 열거하는 대신 **분류 규칙과 이 요구**를
  갖는다 — **명령이 내는 각 커밋의 개별 판정은 그 커밋 메시지**가 싣는다(어느 finding 이 그
  접촉을 요구했는지와 범위). 새 접촉이 나도 이 절은 낡지 않는다.

**부류 B — manifest 단독 커밋.**

- **`C-7b` 가 구조적으로 보지 못한다.** `C-0` 이 `in_scope` 경로로 레인을 뽑으므로 manifest 만
  건드린 커밋은 그 입력에 아예 없다 — **`C-7b` 가 내는 매치는 A 부류가 건드린 경로뿐**이다.
  B 부류의 검출은 위 블록의 마지막 명령이 한다 — 검사 밖의 접촉을 검사 결과로 숨기지 않는다.
- **왜 이 레인이 manifest 를 고치는가.** 접촉의 축은 **이 레인이 세운 계약·분류 축**이다 —
  `verified_paths` 계약(B4-high)과 그 계약이 무엇을 golden 으로 잠그는가의 판정이라, 그 위의 정정은
  이 레인이 맡는다. **어느 finding 이 어떤 범위를 요구했는지는 커밋마다 다르고 그 커밋 메시지가 갖는다.**
- **부류를 가리지 않는 공통 제약 둘.** ① **기대값·입력 파일과 해시 무접촉** ② **fixture-curator 레인의
  파일 무접촉**(신설 필드는 `F-7b` 가 읽는 필드의 모양을 바꾸지 않는다). 둘 다 `C-11` 이 낸다.

**`C-2`·`C-3` 의 python3 블록**(표 밖에 둔다. **quoted heredoc 이라 셸 확장이 없어** 이대로 붙여 돌리면 재현된다).

```sh
# C-2 — §12 의 그룹별 활성 행 수
python3 - <<'PY'
import re, io
s = io.open('docs/discovery/capability-map.md', encoding='utf-8').read()
sec = s.split('## 12. OPEN 결정 목록')[1].split('### 12.1')[0]
cur, g = None, {}
for l in sec.splitlines():
    m = re.match(r'^### (G\d)\.', l)
    if m: cur = m.group(1); g[cur] = []
    m2 = re.match(r'^\| *(OPEN-[A-Z]+-\d+) *\|', l)
    if m2 and cur: g[cur].append(m2.group(1))
print({k: len(v) for k, v in sorted(g.items())}, 'TOTAL', sum(len(v) for v in g.values()))
PY

# C-3 — 네 정본의 활성 합집합을 §14 의 (B)/(C) 와 양방향 대조
python3 - <<'PY'
import re, io, glob
rd = lambda f: io.open(f, encoding='utf-8').read()
cap = rd('docs/discovery/capability-map.md')
a = set(re.findall(r'^\| *(OPEN-[A-Z]+-\d+) *\|',
                   cap.split('## 12. OPEN 결정 목록')[1].split('### 12.1')[0], re.M))
sec9 = lambda t: re.search(r'^## 9\. `OPEN`.*?^## 10\.', t, re.M | re.S).group(0)
a |= set(re.findall(r'OPEN-REG-\d+', sec9(rd('docs/discovery/regression-ledger.md'))))
a |= set(re.findall(r'^\| \*\*`(OPEN-DIC-\d+)`\*\*', sec9(rd('docs/discovery/data-dictionary.md')), re.M))
for f in glob.glob('docs/adr/*.md'):                      # 취소선 없는 §5 제목만 = 활성
    a |= set(re.findall(r'^### `(OPEN-ADR-\d+)`', rd(f), re.M))
s = cap.split('## 14. M0 이월 목록')[1]
row = lambda t: set(re.findall(r'^\| `(OPEN-[A-Z]+-\d+)`', t, re.M))
B = row(s.split('### 14.2')[1].split('### 14.3')[0])
C = row(s.split('### 14.3')[1].split('### 14.4')[0])
print('활성', len(a), '| B', len(B), '| C', len(C),
      '| 미분류', sorted(a - (B | C)), '| 비활성분류', sorted((B | C) - a), '| B∩C', sorted(B & C))
PY
```

**`C-11` 의 python3 블록**(이 라운드가 더한 것뿐이다 — **`F-7b` 는 그 레인 문면 그대로 돌렸고 정본이 그 파일이라 옮겨 적지 않는다**). venv 는 `fixtures-commands.md` **F-7** 과 같다.

```sh
/tmp/fxvenv/bin/python - <<'PY'
import yaml, json
m = yaml.safe_load(open('fixtures/manifest.yaml'))
A = {'Measured', 'SampleInsufficient', 'Undeclared', 'UNKNOWN'}   # 4문 4답의 가족 A
REQ = {'path', 'projection', 'operand', 'operand_source', 'means'}
bad = []
for c in m['cases']:
    vp, vj = c.get('verified_paths') or [], c.get('verified_projections') or []
    exp = json.load(open(c['expected_file']))
    def at(p):                       # `[i]` 첨자는 풀지 않는다 — 그 경로는 None 이 되어 건너뛴다
        cur = exp
        for k in p[2:].split('.'):
            if not isinstance(cur, dict) or k not in cur: return None
            cur = cur[k]
        return cur
    bad += [(c['id'], '가족 A 잠금: ' + p) for p in vp if str(at(p)) in A]
    for e in vj:
        a, b = at(e['path']), e['operand']
        if e.get('normalize') == 'case-fold' and isinstance(a, str): a, b = a.casefold(), b.casefold()
        if (not REQ <= set(e) <= REQ | {'normalize'} or e['projection'] != 'not-equals'
                or e['operand'] in A or e['path'] in vp or a == b):
            bad.append((c['id'], 'projection: ' + str(e.get('path'))))
    if c['classification'] != 'authoritative' and (vp or vj):
        bad.append((c['id'], 'ie 인데 계약 필드가 있다'))
    bad += [(c['id'], 'change_history 필수 키') for h in c.get('change_history') or []
            if not {'date', 'change', 'reason', 'policy_version'} <= set(h)]
print('vj', sum(1 for c in m['cases'] if 'verified_projections' in c), '| violations', bad)
PY
```

**`C-12` 의 python3 블록.** 0E 가 닫은 `OPEN` 셋의 **현재형 활성/대기 주장**을 찾는다 — `capability-map.md` **§12.1**(복원·해소 이력 절)과 **취소선 안**은 이력 표시라 뺀다.

```sh
python3 - <<'PY'
import re, io, glob
resolved = ['OPEN-ADR-01', 'OPEN-OPS-07', 'OPEN-QUAL-05']
claim = re.compile(r'활성이다|활성이며|활성 실행 게이트|기입 대기|대기\*\*|아직 없다|미충족|미완이다')
docs = ['docs/discovery/capability-map.md', 'docs/discovery/data-dictionary.md',
        'docs/discovery/regression-ledger.md', 'milestone-0.md', 'milestone-1.md',
        'v2-지침서.md'] + sorted(glob.glob('docs/adr/*.md'))
hit = 0
for f in docs:
    lines = io.open(f, encoding='utf-8').read().splitlines()
    skip = set()
    if 'capability-map' in f:
        s = next(i for i, l in enumerate(lines) if l.startswith('### 12.1'))
        e = next(i for i, l in enumerate(lines) if l.startswith('### 12.2'))
        skip = set(range(s, e))
    for i, l in enumerate(lines):
        if i in skip: continue
        bare = re.sub(r'~~.*?~~', '', l)
        for r in resolved:
            if r in bare and claim.search(bare):
                hit += 1; print('%s:%d  %s' % (f, i + 1, r))
print('matches', hit)
PY
```

**preflight 명령**(**`preflight` 라 적힌 행들이 쓴다** — 라운드마다 늘므로 번호를 열거하지 않는다. 정본은 `.claude/skills/codex-review-gate/SKILL.md` 이고 여기 있는 것은 **각 라운드의 실행 기록**이다). ②는 총 행 수를 같은 핸들로 받고(0 이면 못 연 것이다), ③의 `codex --version` 은 **지금 PATH 가 고르는** 버전이라 **실행된 버전의 정본은 머리글**이다.

```sh
grep -rniE 'bid-vector-v2|regression-ledger|capability-map|OPEN-REG|0a2|0b-regression' \
  ~/.codex/memories/memory_summary.md ~/.codex/memories/MEMORY.md \
  ~/.codex/memories/rollout_summaries/ ~/.codex/memories/skills/ \
  ~/.codex/rules/default.rules 2>/dev/null | wc -l          # ① sessions/ 는 훑지 않는다
sqlite3 "file:$HOME/.codex/memories_1.sqlite?immutable=1" \
  "select count(*) from stage1_outputs
    where raw_memory like '%bid-vector-v2-review%'
       or rollout_summary like '%bid-vector-v2-review%';"    # ②
sqlite3 "file:$HOME/.codex/memories_1.sqlite?immutable=1" "select count(*) from stage1_outputs;"
type -a codex && codex --version                             # ③ 어느 바이너리인지까지 낸다
R=_workspace/m0-0e/codex.raw-output-B8.txt; P='MEMORY_SUMMARY|memories/MEMORY\.md|bid_vector_db|kis_unified_sts'; head -1 "$R"; grep -cE "$P" "$R"; grep -nE "$P" "$R" | cut -d: -f1; grep -n '^codex' "$R" | head -1   # ③ 실행된 버전의 정본 · ④ 매치 줄 수·위치·developer 구간의 끝. **라운드는 `$R` 의 파일명으로 고른다**
```
