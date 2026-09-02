---
name: codex-review-gate
description: "Codex CLI로 base...head diff의 독립 리뷰를 clean worktree에서 실행하는 절차. Codex 리뷰 요청, 재리뷰, 리뷰 JSON 저장, verdict 확인 작업 시 반드시 이 스킬을 사용. codex-reviewer 에이전트의 표준 절차."
---

# Codex Review Gate — 독립 리뷰 실행 절차

codex CLI(로컬 `codex`, v0.151 기준)로 slice diff의 독립 리뷰를 실행하고 판정 JSON을
보존한다. 판정 계약과 리뷰 기준은 `CODEX-REVIEW.md`와 `agent-workflow.md` 4~5절이
정의하며, 이 스킬은 실행 mechanics만 다룬다.

## 독립성 규칙 (why)

Codex 리뷰의 가치는 구현자와 판단 맥락을 공유하지 않는 데서 나온다. 구현 대화의 결론,
Claude의 자체 평가(verifier 리포트 포함), "이 부분은 의도된 것"류의 해명을 리뷰 입력에
넣으면 리뷰가 자기 승인이 된다. Codex에는 **요구사항 문서, base/head, diff, 커밋된
repository**만 제공한다.

이를 파일시스템 수준에서 보장하기 위해 리뷰는 **저장소 밖 clean worktree**에서 실행한다
(agent-workflow.md 5절). worktree에는 커밋된 파일만 존재하므로 `_workspace/`의 조사
노트·verifier 리포트·구현 노트가 Codex에 노출되지 않고, untracked `bid-vector` symlink도
없어 기존 저장소 접근이 원천 차단된다.

## 실행 절차

### 1. 입력 검증과 사전 스냅샷

```bash
git rev-parse --verify <base>^{commit}
git rev-parse --verify <head>^{commit}
git status --porcelain -- <scope.md의 in_scope 경로>   # 결과가 있으면 중단하고 반환
git rev-parse HEAD > _workspace/{slice}/pre-review-head.txt   # 사전 스냅샷
```

### 2. clean worktree 생성

```bash
git worktree add ../bid-vector-v2-review-{slice} <head SHA>
```

worktree는 저장소 디렉토리 **밖**에 만든다. 리뷰 종료 후 반드시 정리한다.

### 3. prompt 구성

`_workspace/{slice}/codex-prompt.md`(메인 저장소 쪽, worktree 아님)에 다음 순서로 조립:

1. `CODEX-REVIEW.md` 전문 (수정·요약 금지)
2. 리뷰 대상 명시: milestone/slice 이름, base SHA, head SHA
3. 참조 경로: `v2-지침서.md`, `agent-workflow.md`, 해당 `milestone-N.md`, 관련 ADR,
   `reports/evidence/<milestone>/<slice>/` (커밋된 것만 worktree에 보인다)
4. 출력 지시: agent-workflow.md 4절의 JSON 판정 계약만 출력할 것. schema는
   `references/codex-verdict.schema.json`과 동일 구조.

재리뷰라면 이전 finding JSON 경로(커밋된 evidence)와 새 base/head를 2번에 추가하고,
이전 finding 해소 확인만 하지 말고 **새 HEAD 전체를 다시 검증**하도록 지시한다.

### 4. codex 실행

worktree를 작업 루트로, 명령 재실행(테스트 등)을 위해 workspace-write sandbox를 쓴다 —
쓰기 가능 범위가 폐기 예정인 worktree로 한정되므로 메인 저장소는 보호된다.

```bash
CODEX_BIN=/Users/harris/.nvm/versions/node/v22.21.1/bin/codex   # 심판 바이너리 고정 — PATH 에 맡기지 않는다
CODEX_PIN="0.151.0"                   # 심판 버전 고정 — 바꾸려면 이 스킬을 고친다
"$CODEX_BIN" --version | grep -qF "$CODEX_PIN" || {
  echo "codex 버전 불일치: $("$CODEX_BIN" --version) ≠ $CODEX_PIN — preflight 미충족, 리뷰 중단"
  exit 1
}
"$CODEX_BIN" --version   # 기록용 — 리뷰 메타데이터의 값은 여전히 raw-output 머리글이 정본
"$CODEX_BIN" exec -s workspace-write -C ../bid-vector-v2-review-{slice} \
  -c model_reasoning_effort="high" \
  --disable memories --ignore-rules \
  --output-schema .claude/skills/codex-review-gate/references/codex-output.strict.schema.json \
  -o _workspace/{slice}/codex-verdict.json \
  - < _workspace/{slice}/codex-prompt.md \
  > _workspace/{slice}/codex.raw-output.txt 2>&1
```

`--output-schema`에는 **strict 변형**(`codex-output.strict.schema.json`)을 쓴다 — OpenAI
strict structured output은 모든 property가 required여야 하므로, `line`은 null 허용으로
바꾸고 레인이 주입하는 `reviewer`는 제외한 스키마다. 저장물의 정본 스키마는
`codex-verdict.schema.json`이며(reviewer 포함), 6단계 검증과 저장은 정본 기준이다.

**`model_reasoning_effort`는 반드시 `high`로 고정한다.** 생략하면 CLI 기본값에 의존해
같은 base...head가 라운드마다 다른 effort로 리뷰되고, 판정이 갈릴 수 있다. M0/0A 3차
리뷰에서 실제로 같은 range가 medium `approve` / high `request_changes`로 갈렸다
(`reports/evidence/m0/0a/codex-review-20260825T235414Z.json` vs `...235415Z.json`).
리뷰 재현성은 심판 레인의 전제이므로 effort는 메타데이터로만 기록할 값이 아니라
호출 시 고정할 값이다. effort를 바꿔야 할 사유가 생기면 이 스킬을 고쳐서 바꾸고,
호출부에서 즉흥적으로 덮어쓰지 않는다.

**바이너리 경로와 버전도 같은 이유로 고정한다.** 이 머신에는 codex 바이너리가 둘 공존하고
(`/opt/homebrew/bin/codex` 0.148.0 · `~/.nvm/versions/node/*/bin/codex` 0.151.0, 2026-09-01
실측) PATH 순서가 라운드마다 심판 엔진 버전을 조용히 결정해 왔다 — M0/0E 의 B3·B4 는
0.149.0, B5 는 0.148.0 으로 돌았고 그 사이 업그레이드도 기록 없이 지나갔다. 버전이 다르면
판정 차이가 finding 때문인지 엔진 때문인지 가를 수 없다. 그래서 경로는 `CODEX_BIN` 으로
박고 버전은 실행 전 assertion 으로 대조하며, **불일치는 그 라운드의 preflight 미충족이다**
(다른 바이너리로 대체 실행하지 않는다). 업그레이드는 이 스킬의 핀 값을 고치는 명시적
결정으로만 하고 `CLAUDE.md` 변경 이력에 남긴다. 기록용 버전 값의 출처는 여전히
`codex.raw-output.txt` 머리글이지 기억이 아니다.

핀은 **0.151.0**(nvm 쪽, 운영자가 사용 중인 최신)이다 — 운영자 지정 2026-09-01. 그래서
**M0/0E 의 B6 부터는 B5(0.148.0)와 엔진 버전이 다르다** — 이 전환은 조용한 PATH 결과가
아니라 여기 기록된 명시적 결정이며, B6 판정을 읽을 때 이 사실을 함께 읽는다. nvm 경로는
node 버전을 품고 있어 node 업그레이드 시 경로가 사라진다 — 그때는 실행이 시끄럽게
실패하므로 핀 갱신 결정을 새로 받으면 된다.

**`--disable memories --ignore-rules`는 반드시 붙인다.** worktree 격리는 **프롬프트 주입을
막지 못한다.** 두 표면이 있고 **둘은 서로 다른 플래그로 닫힌다.**

- **memory 주입** — codex는 매 세션 developer 메시지로 memory 지침과 `memory_summary.md`
  **전문**을 주입하고, 그 지침이 `MEMORY.md` 질의를 시킨다. **모델이 스스로 찾아 읽은 것이
  아니라 무조건 주입이다.** 주입되는 요약이 이 저장소를 이름으로 지목하고
  *"Uses Claude for implementation and Codex for independent … review"*까지 적는다 —
  **심판이 프롬프트를 읽기도 전에 분업 구조와 운영자 선호를 알고 시작한다.**
- **rules 주입** — `~/.codex/rules/default.rules`(execpolicy allowlist)가 프롬프트에 통째로
  들어가고 거기에 `bid_vector_db`·`kis_unified_sts` 등 **다른 저장소의 실제 경로·명령**이
  들어 있다. **`--disable memories`로는 사라지지 않는다.**

**효과는 실측으로 확인됐다**(2026-08-27, 스모크 2회의 세션 rollout 대조):

| 주입 패턴 | 기본 | `--disable memories` | `+ --ignore-rules` |
| --- | --- | --- | --- |
| `MEMORY_SUMMARY` | 1 | **0** | 0 |
| `memories/MEMORY.md` | 8 | **0** | 0 |
| `bid_vector_db` | 1 | – | **0** |
| `kis_unified_sts` | 4 | – | **0** |

**판독 규칙 — 숫자만 읽지 마라.** 리뷰 range 의 diff 가 이 스킬 파일이나 `CLAUDE.md`
변경 이력을 담는 라운드에서는 위 패턴 표 자신이 diff 로 codex 앞에 놓이므로, raw-output
grep 에 **상시 false-positive 바닥**이 생긴다. 매치의 위치를 갈라 읽는다 — **developer
메시지 구간(머리글 부근)의 매치만 주입**이고, codex 가 뜬 텍스트(diff hunk·파일 읽기
출력·변경 이력)의 매치는 리뷰 대상이다. **「diff 시작 전 = developer 구간」이 아니다** —
파일 읽기 출력도 diff 앞에 온다(B7 실측). 실측(B6, 2026-09-01): 매치 9 전부 diff hunk 안,
developer 구간 0 — 주입 아님.

**막지 못하는 것**: 두 플래그는 **주입과 포인터**를 없앨 뿐 `~/.codex` **읽기 자체는 여전히
가능**하다. exec에 읽기 루트를 좁히는 수단은 찾지 못했다(`--sandbox-state-readable-root`는
`codex sandbox` 전용). 읽히는 표면은 `MEMORY.md` 하나가 아니라 최소 12개이며
`rollout_summaries/`·`sessions/`의 **원본 transcript**와 `memories/skills/`(legacy 저장소의
리뷰 절차서가 이미 skill로 굳어 있다)가 포함된다.

**`CODEX_HOME` 이전은 쓰지 않는다** — `--ignore-user-config` help가 *"auth still uses
`CODEX_HOME`"*라고 명시하고, `auth.json`은 API key가 아니라 **ChatGPT 로그인 토큰**
(`last_refresh` 포함)이라 사본이 만료로 갈라질 수 있다. 호출 단위 플래그가 같은 목적을
인증 위험 없이 달성한다.

**`config.toml`의 `[features] memories=false`도 쓰지 않는다** — 운영자의 대화형 codex까지
끈다. 리뷰 레인의 권한 밖이다.

**세 번째 주입 표면 — hooks (`~/.codex/hooks.json`).** codex 세션은 hook 을 실행하며
(B8 실측: 한 라운드에 262 이벤트) **두 플래그로 닫히지 않는다.** hook 이 stdout 에 쓰면
컨텍스트로 **조용히 주입**된다. 현재 등록된 orca hook 은 stdout 에 아무것도 쓰지 않고
`ORCA_*` 환경변수 미설정 시 no-op 이라 침해가 없음을 실측했으나, 표면은 실재한다 —
**preflight 에서 `~/.codex/hooks.json` 의 hook 목록과 각 스크립트가 stdout 에 쓰는지를
라운드마다 확인**하고, stdout 에 쓰는 hook 이 발견되면 그 라운드는 preflight 미충족이다.
**이 검사는 grep 이 아니라 실행으로 잰다** — `echo`/`printf` 정적 스캔은 curl 로 파이프되거나
`/dev/null` 로 버려지는 출력을 오탐한다(B9 에서 정상 라운드를 떨어뜨릴 뻔했다). codex 가
부르는 방식 그대로 실행해(리뷰 환경 그대로 · 변수 설정+닫힌 포트 · 빈 payload) stdout
바이트를 실측한다.

**preflight**: 리뷰 실행 전 아래를 돌려 이 저장소 흔적의 양을 기록한다. `sessions/`는
용량이 커서 훑지 않는다.

```bash
grep -rniE 'bid-vector-v2|regression-ledger|capability-map|OPEN-REG|0a2|0b-regression' \
  ~/.codex/memories/memory_summary.md ~/.codex/memories/MEMORY.md \
  ~/.codex/memories/rollout_summaries/ ~/.codex/memories/skills/ \
  ~/.codex/rules/default.rules 2>/dev/null | wc -l
```

**리뷰는 memory에 직접 쓰지 않는다 — 비동기 배치가 transcript를 집어 올린다.** 경로는
`세션 transcript → memory_stage1 → memory_consolidate_global → MEMORY.md`이며
`~/.codex/memories_1.sqlite`가 그 실물이다(`memory_stage1` done 632건).
**리뷰 라운드 세션 16개는 아직 한 건도 올라오지 않았다**(2026-08-27 실측). 그러나
**선택되지 않을 구조적 이유는 없다.** 그래서 아래 두 번째 검사가 첫 번째보다 먼저 움직인다.

```bash
sqlite3 "file:$HOME/.codex/memories_1.sqlite?immutable=1" \
  "select thread_id, rollout_slug, datetime(generated_at,'unixepoch','localtime')
     from stage1_outputs
    where raw_memory like '%bid-vector-v2-review%'
       or rollout_summary like '%bid-vector-v2-review%';"
```

**`?mode=ro`가 아니라 `?immutable=1`이다.** 앞서 `mode=ro`를 썼고 이 환경에서 반복 실패했다
— `Error: in prepare, unable to open database file (14)`. M0/0D가 그 진동을 관측 다섯으로
기록했고(sidecar `-wal`/`-shm`이 있으면 열리고 checkpoint된 뒤 없으면 실패), M0/0C 6차
리뷰어가 기제를 진술했다 — **`mode=ro`도 WAL `-shm` sidecar를 만들어야 하는데 샌드박스가
그것을 막고, `immutable=1`은 잠금을 아예 건너뛴다.** **그 기제는 실험으로 확정되지 않았다**
— 진술이지 증거가 아니다. 확정된 것은 **`immutable=1`이 이 환경에서 열린다**는 실측뿐이고,
이 교체는 그 실측에만 기댄다.

**`immutable=1`은 동시 writer가 없음을 가정한다.** 그래서 **0행을 받았을 때 그것이
「못 열어서 0」이 아님을 같은 핸들로 확인한다** — 아래처럼 총 행 수를 함께 받아라.

```bash
sqlite3 "file:$HOME/.codex/memories_1.sqlite?immutable=1" \
  "select count(*) from stage1_outputs;"
```

**총 행 수가 0이면 DB를 못 연 것이고 그 라운드는 preflight 미충족이다.**

**0행이 아닌 날이 오면 그 라운드부터 심판은 자기 과거 판정을 물려받는다.** 위 grep은
consolidate **이후**를 보고 이 질의는 stage1을 보므로, 이 질의가 먼저 걸린다.
0행이 아니면 **리뷰를 중단하고 사용자에게 보고한다** — 심판 독립성의 전제가 깨진 것이다.

**이 출력을 verdict JSON에 넣지 마라.** `residual_risks`는 Codex가 쓰는 필드이고 이 스킬의
금지 조항이 **리뷰 JSON 수정**을 막는다(정본 스키마도 `additionalProperties: false`다).
preflight 결과는 **레인 산출물**이다 — codex-reviewer의 반환 보고와 그 slice의
`commands.md`에 남긴다.

**`codex debug prompt-input`을 preflight 도구로 쓰지 마라** — memory 주입을 렌더하지 않아
항상 「없음」이라고 답한다.

- prompt는 stdin(`-`)으로 전달한다 (ARG_MAX 회피).
- Bash `timeout` 최대치는 10분(600000ms)이다. 리뷰는 보통 이를 초과하므로 **처음부터
  `run_in_background: true`로 실행**하고 완료 알림을 기다린다. 폴링이 필요하면
  `codex-verdict.json` 생성 여부를 확인한다.
- 첫 운영 실행 전에 `codex exec -s read-only "echo ok"` 수준의 스모크 테스트로 비대화
  모드 동작(trust 프롬프트 여부)을 1회 검증한다.

### 4b. 코드 slice — 오프라인 Gradle 실행 능력 (운영자 채택 2026-09-02)

M1/1A 1차 리뷰에서 Codex 는 gradle 을 한 번도 실행하지 못했다 — sandbox 가 `~/.gradle`
잠금 파일 쓰기와 네트워크(DNS)를 막아 wrapper 다운로드·의존 해석이 전부 실패했고, 다섯
finding 이 전부 정적 판독이었다. 코드 slice 의 리뷰가 실행 없이 도는 것은 구조적 약점이므로,
**격리(네트워크 차단·쓰기는 worktree 안)를 유지한 채** 실행 능력을 준다:

```bash
RO=$HOME/.codex-review/gradle-ro                       # read-only 의존 캐시 사본 (증분)
mkdir -p "$RO" && rsync -a --delete "$HOME/.gradle/caches/modules-2/" "$RO/modules-2/"
W=../bid-vector-v2-review-{slice}                      # clean worktree
mkdir -p "$W/.gradle-home/wrapper/dists"
DIST=$(grep -o 'gradle-[0-9.]*-bin' "$W/gradle/wrapper/gradle-wrapper.properties" | head -1)
cp -R "$HOME/.gradle/wrapper/dists/$DIST" "$W/.gradle-home/wrapper/dists/"
export GRADLE_USER_HOME="$W/.gradle-home" GRADLE_RO_DEP_CACHE="$RO"
( cd "$W" && ./gradlew --offline -q help )             # 레인이 먼저 스모크 — 실패하면 codex 에 넘기지 않는다
```

- 이 env 를 export 한 채 §4 의 `codex exec` 를 부른다(sandbox 는 env 를 상속한다). 프롬프트에
  「gradle 은 `--offline` 으로, `GRADLE_USER_HOME`·`GRADLE_RO_DEP_CACHE` 는 이미 설정됨」을 한 줄
  적는다. 네트워크는 열지 않는다 — 심판이 저장소 내용을 밖으로 보낼 표면이 생긴다.
- `GRADLE_RO_DEP_CACHE` 는 **live `~/.gradle/caches` 를 직접 가리키지 않는다** — Gradle 이 RO
  캐시의 동시 쓰기 부재를 전제하므로 사본을 쓴다. rsync 는 증분이라 2회차부터 빠르다.
- `.gradle-home/` 은 worktree 안의 untracked 디렉터리다 — §5·§7 의 `git status --porcelain`
  대조에서 **그 경로 하나만 허용된 예외**이고, 그 밖의 변경은 여전히 오염이다.
- `git worktree add` 는 codex 가 할 수 없다(메인 `.git` 쓰기 거부 — 격리가 맞게 동작한 것).
  A-0 류 「clean checkout 빌드」 재현은 codex 의 worktree 자체가 clean checkout 이므로 그 자리에서
  `./gradlew --offline clean check` 로 대신한다고 프롬프트에 적는다.
- 의존이 로컬 캐시에 없으면(새 라이브러리) `--offline` 이 실패한다 — 그때는 구현 레인이 먼저
  로컬에서 한 번 빌드해 캐시를 채운 뒤 rsync 한다. 캐시 채우기는 심판이 아니라 구현 레인의 일이다.

**한계 (M1/1A 2차 실측 · 운영자 결정 2026-09-02: 정적 리뷰 유지).** 위 절차로 캐시·쓰기 문제는
해소됐으나 **codex 안에서는 gradle 이 시동조차 못 한다** — seatbelt sandbox 가 소켓 생성을
금지하고 Gradle 은 시동 시 file-lock contention 용 `DatagramSocket` 을 조건 없이 만든다
(`BasicGlobalScopeServices.createFileLockContentionHandler()` 바이트코드에 프로퍼티 게이트 없음,
플래그 우회 불가). 유일한 우회는 `sandbox_workspace_write.network_access=true` 인데 운영자가
**열지 않기로** 결정했다. 그러므로 코드 slice 의 Codex 리뷰는 **정적 판독 + 레인이 만든 실행
산출물 열람**이다: 레인은 사전 스모크(`./gradlew --offline --no-build-cache clean check`)의
`build/` 산출물(테스트 XML·kover 리포트·게이트 출력)을 **지우지 않고** worktree 에 남기고,
프롬프트에 「`build/**` 는 리뷰 레인이 head 에서 오프라인으로 실행한 결과이며 codex 는 gradle
을 실행할 수 없다」를 한 줄 적는다. 실행 재확인의 정본은 verifier 레인이다. `build/` 는 ignore
대상이라 status 대조에 영향 없다.

### 5. 무효 라운드 검사

실행 후 worktree의 오염을 확인한다:

```bash
git -C ../bid-vector-v2-review-{slice} status --porcelain
git -C ../bid-vector-v2-review-{slice} rev-parse HEAD   # <head>와 같아야 함
```

수정·신규 파일·새 커밋이 발견되면 그 라운드는 무효다(agent-workflow.md 1절): 무효
사유와 오염 내용을 기록하고, worktree를 삭제 후 새로 만들어 재실행한다. 메인 저장소는
worktree 격리로 인해 영향받지 않는다.

### 6. 판정 JSON 검증

`codex-verdict.json`에서 다음을 검증한다:

- `verdict`가 `approve` 또는 `request_changes`
- `reviewed_base`/`reviewed_head`가 요청한 SHA와 일치
- `request_changes`면 `findings[]`에 severity/file/evidence/required_fix 존재
- **저장된 verdict 가 raw output 의 종말 메시지와 일치하고 `commands_run` 이 실질적**
  (2개 이상, 실제 검토 명령 포함)**인지.** codex 는 schema-valid 한 조기 verdict 를
  내뱉을 수 있다 — B8(raw 136행)·B9(138행) 각 1회, B10 은 **한 라운드에 2회**(140행
  첫 출력 직후 · 3150행 41%), B13 도 2회(**25% · 89%** 지점)로 연속 실측된
  **`--output-schema` 하의 체계적 거동**이고, **위치 무관**이다 — B13 의 89% 방출은
  실행이 거의 끝난 자리라 「앞부분만 거르면 된다」로 완화할 수 없다(B10 에서 같은 주장이
  오독 근거로 철회됐다가 B13 에서 성문화된 계수 규칙 하의 진짜 방출로 복원됐다).
  매번 종말 `request_changes` 가 `-o` 로 저장돼 사고를 면했다. **방출을 셀 때는 codex
  표지가 앞선 줄만 센다** — raw 의 `"verdict"` 문자열에는 프롬프트의 스키마 템플릿과
  codex 가 읽은 파일의 출력이 섞인다(B10 에서 커밋된 0A verdict JSON 을 jq 로 읽은
  stdout 7684행을 「끝자락 방출」로 오독할 뻔했다). 누출 검사와 같은 판독 규칙이다 —
  **codex 가 뜬 텍스트는 방출이 아니다.** 그리고 **`tokens used` 블록이 앞서면 CLI
  echo 다** — `codex exec` 는 종료 시 마지막 메시지를 stdout 에 다시 찍는다(B8~B10
  세 라운드 전부 실측). 이 절이 없으면 라운드마다 +1 과대계수된다. **판별은 두 조건의
  교집합이다** — ① `codex` 표지가 직전에 선행하고(앵커) ② 리뷰 verdict 의 형태
  (`reviewed_base`·`reviewed_head`·`findings` 동반)일 것. 형태 단독은 부족하다 —
  B13~B15 실측에서 앵커는 매번 옳았고(3·3·2) 형태 단독은 매번 과다(4·4·4)였다: CLI echo
  를 늘 포함하고, B15 에선 이 판별자를 설명하는 evidence 행(C-23)이 두 패턴을 한 줄에
  인용해 형태를 통과했다(네 번째 표면 — 판별자의 실패 형태를 적는 문서 자신). 앵커 단독도
  부족하다 — 도메인 출력 스키마가 `verdict` 를 필드명으로 쓰므로(`license-*` 기대값
  `{"verdict":"Eligible",…}`, B14 실측 8건) 도구 출력 뒤의 fixture 줄을 걸러야 한다.
  캡처 의미가 바뀌거나
  타임아웃이 나면 조기 verdict 가 저장될 수 있으므로, **`commands_run` 이 빈약한
  `approve` 는 판정이 아니라 미완 실행으로 취급**하고 재실행한다.

검증 실패 시 1회 재실행. 재실패 시 raw 출력을 보존하고 실패로 보고한다. JSON을 임의로
보정·생성하지 않는다.

### 7. append-only 저장과 worktree 정리

```bash
cp _workspace/{slice}/codex-verdict.json \
   reports/evidence/<milestone>/<slice>/codex-review-$(date -u +%Y%m%dT%H%M%SZ).json
git worktree remove ../bid-vector-v2-review-{slice}
```

기존 리뷰 파일은 절대 덮어쓰지 않는다. 재리뷰는 새 timestamp 파일로 저장한다 —
리뷰 이력 자체가 감사 증적이다.

저장 전에 판정 JSON에 `reviewer: {cli_version, model}` 필드가 없으면 실측값으로 채워
넣는다. 이 주입은 agent-workflow.md 4절 계약이 리뷰 레인에 위임한 메타데이터 기록이며,
verdict·findings에는 손대지 않는다. 같은 값을 `commands.md`에도 기록한다.

**두 값의 출처는 `codex.raw-output.txt`의 머리글이지 기억이 아니다.** 그 파일 첫 10줄에
`model:`과 `reasoning effort:` 줄이 실측으로 찍힌다. **`model`에는 effort를 함께 적는다** —
`gpt-5.6-sol (reasoning effort: high)` 형태다. M0/0A3 1차에서 effort 표기가 빠져
**고정이 실제로 풀린 것인지 표기만 빠진 것인지 판정 불가**가 됐고, raw output을 열어서야
`reasoning effort: high`가 확인됐다. **effort는 이 하네스가 재현성 때문에 못박은 값이라
그 표기가 빠지면 verdict의 비교 가능성을 확인할 수 없다.**

```bash
sed -n '1,10p' _workspace/{slice}/codex.raw-output.txt | grep -E '^(model|reasoning effort):'
```

머리글에 `reasoning effort: high`가 없으면 **그 라운드는 무효다** — 고정이 적용되지 않은
것이므로 verdict를 저장하지 말고 재실행한다.

### 8. 반환

오케스트레이터에 verdict, blocker/high finding 수, 저장 경로를 보고한다. verdict 이후의
처리(수정 라운드, 사용자 보고)는 오케스트레이터의 책임이다.

## 금지

- 자체 판정으로 codex 실행을 대체
- 구현 대화 요약·자체 평가·verifier 리포트를 리뷰 입력에 포함
- 메인 저장소(worktree 아닌 쪽)를 codex의 작업 루트로 지정
- 리뷰 JSON 수정, finding 축소, 파일 덮어쓰기
- `approve`를 merge/push/배포 승인으로 해석
