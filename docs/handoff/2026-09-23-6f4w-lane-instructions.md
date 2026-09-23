<!-- 원본: bid-vector-v2-m6f4w/_workspace/m6-6f4w/00_handoff.md (gitignore 경로) 를 2026-09-23 장비 이동 핸드오프를 위해 그대로 옮겼다. 진입점은 docs/handoff/2026-09-23-handoff.md. -->

# M6/6F-4-w 인계 — 세션 재시작 지점 (2026-09-23)

**재시작 사유**: `Agent` 호출이 `Failed to create teammate pane: Timed out waiting for the Orca
runtime to respond` 로 **다섯 번 연속 실패**해 검토 레인을 띄울 수 없었다. teammate 를 전부 종료한
뒤에도, 한 번에 셋을 띄운 것도 하나씩 띄운 것도 같은 결과. `tmux list-panes -a` 는 패널 1개 —
패널 고갈이 아니다.

## 지금 상태

- `main` = **`02164e44`** (PR #42 = M6/6F-7 머지 완료, CI 3 job 전부 통과).
- 6F-4-w 레인: worktree `/Users/harris/Development/private/bid-vector-v2-m6f4w`,
  브랜치 `m6-6f4w/2026-09-23`, **판정 대상 SHA `1f34858d`** 에 **동결**. 작업 트리 빈 출력.
  커밋 17개. **push 안 했다.**
- 구현 레인(`m6f4w-impl`)·조사 레인은 종료했다. 실행 중인 teammate 없다.

## 다음 행동 — 이것 하나다

**`1f34858d` 에서 검토 레인 셋을 병렬로 띄운다.** 지시문 전문은 이 파일 아래에 있다.

| 레인 | subagent_type | model |
|---|---|---|
| verifier | `verifier` | (frontmatter opus) |
| code-reviewer | `oh-my-claudecode:code-reviewer` | **`sonnet` 명시 필수** (frontmatter 가 opus 라 빠뜨리면 샌다) |
| privacy-gate | `privacy-gate` | (frontmatter) |

그 뒤 경로: 판정 → (필요시 수정 라운드) → 장부층 일괄 소거 → **milestone-6.md 종결 문단**(팀장) →
**push·PR 승인 요청** → 판정 코멘트 넷 → CI → **머지 승인 요청**.

**사용자 승인 없이 push/merge 하지 않는다.** `gh`·`git push` 는 프록시 때문에 `~/.internal-bin/gh`·
`~/.internal-bin/git` 을 쓴다.

## 이 slice 가 무엇인가

`WatchSubjectPort` 의 **첫 production 구현**(`Notice` → `WatchSubject` 순수 매퍼, DB 재조회 없음)
\+ `KeywordScopeText`·`FullScopeText` 의 **생성 경계 폐쇄**. 후자가 닫는 것은 **실측된 구멍**이다 —
6F-4 verifier r2 가 요건 텍스트로 `KeywordScopeText` 를 직접 만들어 **필수 키워드를 만족시키는 test 가
초록인 것**을 쟀다.

계약 정본: `reports/evidence/m6/6f4w/scope.md` — **D-6F4W-1~12**, 계약 갱신 절 셋.
주요 결정: ① `OPEN-6F4-TITLE-WIRING` 을 축으로 갈라 포트 절반만 닫음(수집 절반은
`OPEN-6F4-TITLE-INGEST` 신설) ② 부재 → `Found`(빈 텍스트), 매퍼를 **전 함수**로 두어 `Unavailable` 을
**타입으로 표현 불가능**하게 ③ 부재 기초금액 사유 = `EMPTY_INPUT` ④ `in_scope` 를 넷 확장(계약 갱신 2)
⑤ 「assemble 출력은 `""` 이거나 비공백 포함」 불변식을 속성 test 로 잠금(D-6F4W-12).

## 구현 레인의 자기 보고 (통과로 치지 말 것 — 검증 대상이다)

- `check` exit 0 (337 tasks) · `qualityBaseline` exit 0.
- 캐시 우회 `--rerun-tasks` 50/50 실제 실행, test 1205건(strategy 68 · adapters 661 ·
  workflow 314 · app 162), failure 0.
- D-6F4W-12 변이(`isNotBlank`→`isNotEmpty`) → RED 확인 → 원복 → GREEN.
- negative fixture 4~7 RED→GREEN TDD.
- `in_scope` ↔ rollback 드리프트 0(32파일). rollback 임시 clone 에서 ①~⑥ 전건 실측.
- `one-command-check.sh` exit 1 — S-1(Python PyPI) 로컬 network timeout, 이 slice 무관 주장.
- D-6F4W-8 집계: ⓐ 32 · ⓑ 1 · `.value` 전환 6 · compile-fixture 2건은 별도 집계 밖.

**알려진 제한 중 가장 의심스러운 것**: 「`of` 가 `internal` 이라 `strategy` 모듈 안에서는 호출 가능.
Kotlin 이 top-level 함수에 『이 함수만 호출 가능』한 가시성을 못 줘 **구조적으로 더 못 닫는다**」 —
**6F-7 에서 「못 한다고 적었는데 사실은 할 수 있었다」가 HIGH 로 나온 것과 같은 형태**다. 검증 표적 1번.

## 6F-4-w 뒤에 남은 것

배선 4단계 중 **2번이 이것**이다.
- 3번: **6A-3 + 6F-3 합침** — 평가 엔드포인트 + `CapacityPort`. (`CapacityPort.snapshot()` 이 인자를
  안 받으므로 「요청이 활성 수를 나른다」는 어댑터를 요청마다 짓는다는 뜻이고, 그 생성 자리가 곧 평가
  엔드포인트다 — 둘은 같은 일이라 합쳤다.) `MlAnalysisPort` 처분(실 gRPC vs `UnavailableMlAnalysis`)을
  이 slice 에서 올린다.
- 4번: **`OPEN-6F-ASSEMBLY`** — 포트 아홉을 app 에 꽂기. 1~3 이 전제. 6F-7 이 둘을 넘겼다
  (`SQLException`→`Failed` 의 트랜잭션 원자성 · 하위 소비자의 `Failed` 로깅).
- 독립: **6A-2**(세션 엔드포인트 + app 이미지, 6A-1 의 OPEN 둘을 받는다) · **6B-2**(백업·복구 리허설).
- **운영자 결정 대기**: 6B-3(보존 기간) · 6B-4(큐 정책) · 6F-5-b(실 LLM 승인).
- 배선 뒤: 6D · 6E(**`OPEN-6A1-CONNECTION-POOL` 이 여기서 닫혀야 운영 진입**).

---

# 검토 레인 지시문 — 그대로 복사해 쓸 것

## (1) verifier — `subagent_type: verifier`

M6/**6F-4-w** slice 검증. worktree `/Users/harris/Development/private/bid-vector-v2-m6f4w`,
브랜치 `m6-6f4w/2026-09-23`, **판정 대상 SHA `1f34858d`**. **레인은 동결했다** — 시작·종료 HEAD 가 같고
`git status --porcelain` 이 빈 출력인지 확인해라(다르면 미검증). 구현 대화는 받지 않는다.
`~/.internal-bin/git` 을 써라.

읽을 것: 계약 `reports/evidence/m6/6f4w/scope.md`(**D-6F4W-1~12**, 갱신 절 셋) ·
`commands.md`·`checklist.md`·`rollback.md`. 리포트는 `_workspace/m6-6f4w/10_verifier_report.md`.
완료되면 팀장에게 요약 회신(길면 파일 경로만).

**milestone 종결 문단은 아직 없다 — 판정 뒤 팀장이 쓴다.** 그것을 미비로 세지 마라.

### 이 slice 가 한 것

`WatchSubjectPort` 의 첫 production 구현(`Notice` → `WatchSubject` 순수 매퍼) + **감시 텍스트 두 타입의
생성 경계 폐쇄**. 후자가 닫는 것은 **실측된 구멍**이다 — 6F-4 verifier r2 가 요건 텍스트로
`KeywordScopeText` 를 직접 만들어 **필수 키워드를 만족시키는 test 가 초록인 것**을 쟀다.

### 표적

**① 폐쇄가 진짜 닫혔나 — 「`of` 를 `private` 으로 못 만드나」**
구현 레인이 알려진 제한에 「`of` 가 `internal` 이라 `strategy` 모듈 안에서는 호출 가능. Kotlin 이
top-level 함수에 『이 함수만 호출 가능』한 가시성을 못 줘 **구조적으로 더 못 닫는다**」고 적었다.
**그 「못 한다」를 의심해라** — 6F-7 에서 「못 한다고 적었는데 사실은 할 수 있었다」가 HIGH 로 나왔다.
- `assemble*` 와 두 타입이 **같은 파일**이면 `of` 를 **`private`** 으로 할 수 있다. **실제로 바꿔
  컴파일해 봐라.**
- `of` 시그니처가 정말 `assemble*` 와 같아 **raw 문자열 주입 경로가 아닌가.** `of(raw: String)` 류가
  하나라도 있으면 **폐쇄가 이름만 남는다.**
- 폐쇄 뒤 `strategy/src/test` 에서 **우회가 실제로 되는가.**

**② negative fixture 4~7 이 「옳은 이유로」 실패하나**
negative 가 오타 같은 엉뚱한 이유로 실패하면 아무것도 안 잠근다(그래서 이 저장소에 `mutant-*` 가 있다).
넷의 **컴파일 에러 메시지를 실제로 읽어** 「생성자 접근 불가」·「copy 접근 불가」인지 확인해라.

**③ 안 답해진 계약 항목 — D-6F4W-11 조건 3**
`positive-2-keyword-scope-own-text`·`negative-2-keyword-scope-cross-text`·`mutant-2` 각각
**「폐쇄 뒤에도 고유한 무언가를 재는가」**를 답하라고 했는데 구현 보고는 「부호 유지·리터럴만 이관」으로만
적고 집계 밖으로 뺐다. **네가 답해라.** `positive-2` 는 「자기 모듈 텍스트로는 만들 수 있다」를 증명하던
것으로 보이는데 **폐쇄하면 그 명제가 거짓**이다 — 그런데도 positive 로 통과한다면 **무엇을 재고 있나.**

**④ 상수 풀 부재 단언이 실제로 막나 (우회 3)**
어댑터가 커널을 안 부르고 **직접 이어붙이면** RED 인가. **변이로 재라**(`numstat` 으로 적용 확인 먼저).
「있는지만 보는 게이트는 없어야 할 것을 못 막는다」가 이 저장소의 반복 지적이다.

**⑤ D-6F4W-12 속성 test 가 항진명제가 아닌가**
`isNotBlank`→`isNotEmpty` 변이 RED 를 **재현**하고, **단언 양변이 같은 생성 경로를 지나지 않는지** 봐라 —
게이트가 자기 입력을 재계산하면 변화가 상쇄된다(M6 에서 두 slice 연속 HIGH, **리뷰는 못 잡고 변이 실측만
잡았다**).

**⑥ `.value` 전환 6건이 단언을 약화시켰나**
D-6F4W-11 조건 1 이 「test 의 주제가 문자열 내용 자체일 때만」으로 제한했다. 6건 각각 정당한가.

**⑦ ⓑ 처분 1건**
`FullScopeText("   ")` 를 「도달 불가」로 지웠다. **실측을 재현해라.** 낼 수 있으면 지운 것이 덮개 손실이다.

### 그 밖

- **acceptance 재실행**: 보고는 `check` exit 0(337 tasks) · `qualityBaseline` exit 0 ·
  `--rerun-tasks` 50/50. **버릴 clone 에서 재라**(`git clone .` 또는 `worktree add --detach`.
  **`cp -r` 금지** — 연결 worktree 복사는 원본 index 를 오염시킨다). **캐시 우회 필수** — 6F-7 에서
  `check` exit 0 인데 산출물 모듈 test 가 `FROM-CACHE` 였다.
- `one-command-check.sh` exit 1 이 **정말 S-1(Python) 하나뿐인지.** **프록시를 우회하지 마라.**
- **in_scope ↔ rollback 독립 재산출** + **글롭에 한 줄 심어 잡히는지 양성 대조**(비파괴 절삭 복원,
  `checkout --` 금지). 6A-1·6F-7 에서 **두 번 연속** 이 자리가 눈이 멀었다.
- **rollback 유효성**: `git diff --name-only <실측 HEAD>..1f34858d -- <되돌리는 경로들>` 빈 출력인가
  (2026-09-19 정정 술어 — 「실측 HEAD == 판정 SHA」가 아니다).
- evidence 규격: 크기 게이트 · **축어 좌표 0** · **참조형** 누출 스캔 · 출력 전문 없음 ·
  라운드 이력 절 없음.

### 판정

`not-ready` 는 **산출물 blocker/high 만**. 장부층·low 는 등재 후 승인 전 일괄. 재작업 상한 5회.
**네 최고 가치는 우회 고안과 변이 실측이다** — 표적은 구현 보고의 **알려진 제한을 계약 문면과 대조**해
만들어라. `ready-for-review` 면 말미를 **PR 코멘트로 옮길 형태**로(닫힌 것 / 남은 알려진 제한 /
**확인하지 않은 것**). **Codex 는 이 slice 대상이 아니다** — 그 사실도 적어라.

---

## (2) code-reviewer — `subagent_type: oh-my-claudecode:code-reviewer`, **`model: sonnet` 명시**

M6/**6F-4-w** 코드 리뷰. **저자와 다른 패스다 — 구현 대화를 받지 않았고, 코드를 고치지 않는다. 지적과
근거만 낸다.**

worktree `/Users/harris/Development/private/bid-vector-v2-m6f4w`, **판정 대상 SHA `1f34858d`**(동결됨).
`~/.internal-bin/git` 을 써라.

diff: `git diff $(git merge-base HEAD origin/main)..1f34858d -- . ':(exclude)reports/evidence'
':(exclude)milestone-6.md'`
계약: `reports/evidence/m6/6f4w/scope.md`(**D-6F4W-1~12**).
리포트는 `_workspace/m6-6f4w/11_code_review.md`. 팀장에게 요약 회신(길면 파일 경로만).

### 이 slice

`WatchSubjectPort` 의 첫 production 구현(`Notice` → `WatchSubject` **순수 매퍼**, DB 재조회 없음) +
`KeywordScopeText`·`FullScopeText` 의 **생성 경계 폐쇄**(`private constructor` +
`@ConsistentCopyVisibility` + 팩토리). 후자 때문에 기존 test 43곳의 직접 생성을 조립 함수 경유로 **이관**했다.

### 계약이 못 박은 것 — 코드와 어긋나면 지적이다

- **D-6F4W-3**: 매퍼는 **`WatchSubject` 를 내는 전(total) 함수**다. **이 어댑터에서 `Unavailable` 은
  표현 불가능**해야 한다 — `WatchSubjectOutcome` 을 내는 매퍼가 있으면 계약 위반.
- **D-6F4W-2**: 부재 → 빈 텍스트 / 빈 집합. **부재를 `Unavailable` 로 처리하면 안 된다**(전건 탈락이 된다).
- **D-6F4W-4**: 부재 기초금액의 사유 코드는 **`EMPTY_INPUT`**. 다른 값이면 지적.
- **우회 3**: 어댑터는 **커널을 부르고 조립을 복제하지 않는다.** 어댑터 안에 구분자·이어붙이기 상수가
  있으면 지적.
- **우회 6**: 어댑터가 **DB 를 다시 보면 안 된다**(`Notice` 가 이미 값을 나른다).
- **D-6F4W-11 조건 1**: `.value` 비교는 **그 test 의 주제가 문자열 내용 자체일 때만**. 6건이라고
  보고됐다 — **각각이 정당한지 봐라.**

### 이 저장소가 반복해서 데인 자리 — 이 눈으로 봐라

- **있는지만 보는 단언은 없어야 할 것을 못 막는다.** 존재 단언 대신 **부재 단언·집합 등식**인지.
- **위치·이름 술어는 종점이 없다.** 파일명·패키지 경로를 문자열로 박은 게이트는 이웃 파일로 옮기면
  열린다. **종점은 타입과 가시성**이다.
- **게이트가 자기 입력을 재계산하면 항진명제다.** 단언 양변이 같은 생성 경로를 지나는지.
- **닫히지 않는 것을 닫혔다고 적지 않는다.** KDoc·주석에 「보장한다」·「항상」류 전칭이 있는지.
  **반대로 닫히는 것을 못 닫는다고 적지도 않았는지** — 구현 레인이 알려진 제한에 「Kotlin 이 top-level
  함수에 『이 함수만 호출 가능』한 가시성을 못 줘 **구조적으로 더 못 닫는다**」고 적었다. **그 주장을
  소스로 검증해라**(같은 파일이면 `private` 이 가능한가?).
- **이관이 단언을 약화시켰는가.** 43곳을 옮기면서 **조용히 느슨해진 test** 가 없는지 — 이게 이번 리뷰의
  핵심 축이다.

### Kotlin 코딩 규율 (v2-지침서 §5)

TDD · DI · **분기 도배 금지** · **매직넘버 금지** · 중복·주석 최소화 · 회귀의 구조적 방지.

### 형식 (전역 규약 §2)

`## 리뷰 판정 — code-reviewer (sonnet, 저자와 다른 패스)` / `**BLOCKER n · HIGH n · MEDIUM n ·
LOW n — 머지 가능 / 머지 불가**` / `### 리뷰어가 코드로 확인한 것` / `### 지적`(심각도 · 파일:줄 ·
무엇이 어떤 입력에서 깨지는가) / `### 확인 불가`

**확인 못 한 것을 확인한 것처럼 적지 마라.** 빌드를 직접 못 돌렸으면 그렇게 적어라(acceptance 재실행은
verifier 레인의 몫이다).

---

## (3) privacy-gate — `subagent_type: privacy-gate`

M6/**6F-4-w** privacy-gate 판정. **저작 레인과 다른 패스다 — 코드를 고치지 않고 「위반/준수/확인 불가」만
낸다.**

worktree `/Users/harris/Development/private/bid-vector-v2-m6f4w`, **판정 대상 SHA `1f34858d`**(동결됨).
`~/.internal-bin/git` 을 써라.

diff: `git diff $(git merge-base HEAD origin/main)..1f34858d -- . ':(exclude)reports/evidence'
':(exclude)milestone-6.md'`
계약: `reports/evidence/m6/6f4w/scope.md`(위협 모델 절 + D-6F4W-1~12).
리포트는 `_workspace/m6-6f4w/12_privacy_gate.md`. 팀장에게 요약 회신.

### 왜 이 slice 에 게이트가 붙나

이 slice 는 **공고명·기관명·업종 라벨을 하나의 텍스트로 조립**해 감시 판정의 입력으로 만든다
(`KeywordScopeText`·`FullScopeText`). 즉 **원문 텍스트가 새 경로로 흐른다.** 6F-7 의 payload 는
수치·enum 뿐이었지만 **이번은 원문 문자열이다** — 등급이 다르다.

### 볼 것

**① 조립된 텍스트가 로그·예외 메시지·`toString()` 으로 새는가**
신설 코드에 `logger.`·`println`·`log.` 호출이 있는가. 두 타입이 `data class` 라면 **합성 `toString()` 이
원문을 그대로 낸다** — 그 값이 예외 메시지·에러 응답·진단 출력에 실릴 경로가 있는가. **6A-1 에서
`data class` 의 합성 `toString()` 이 평문 자격증명을 흘린 전례**가 있다(등급은 다르다: 공고명은 공개
정보다. **그래도 경로를 적어라**). 어댑터가 실패할 때 `Notice` 내용을 메시지에 보간하는가.

**② 이 텍스트가 outbox·audit 같은 영속 경로에 닿는가**
6F-7 이 `NotificationRequested` payload 를 outbox 에 넣었다. 이 slice 의 조립 텍스트가 **그 payload 로
흘러 영속되는가** — 흐르면 **6B-3(승인된 보존 기간 없음)의 적용 대상이 하나 느는 것**이고, 그 사실이
`checklist.md` 알려진 제한에 **문면으로 있는지** 봐라. 6F-7 에서 같은 항목이 권고로 나왔다.

**③ 개인정보 축 — 기관명·담당자**
조립 입력에 `demand_agency_name`·`notice_agency_name` 이 들어간다. **기관명은 조직 정보이지 개인정보가
아니다** — 다만 **담당자명·연락처가 섞여 들어올 필드가 있는지** 타입 정의를 전수해라. 없으면 「없음」을
근거와 함께 적어라.

**④ 경계 폐쇄가 보안 불변식으로서 성립하는가**
이 slice 가 닫는 것은 「승인된 조립 함수만 감시 텍스트를 만든다」이고, 그것이 지키는 것은 **운영자의 감시
규칙(필수/제외 키워드)이 무력화되지 않는 것**이다. 우회가 **실측된 구멍**이었다(6F-4 verifier r2: 요건
텍스트로 직접 만들어 필수 키워드를 만족시키는 test 가 초록). **폐쇄가 실제로 그 경로를 막는지**
negative fixture 로 확인해라.

**⑤ 비밀값 스캔 — 참조형으로만**
`grep -rniE -f config/quality/leak-patterns.txt <신설·변경 파일들>`. **스캔 어휘를 리포트에 축어로 적지
마라** — 명령을 참조형으로 적고 결과(exit code)만 적어라.

### 형식

전역 규약 §2. **「확인 불가」는 통과가 아니다** — 사유를 적어라. 해당 없는 절은 「해당 없음」으로 묶고
**왜 안 닿는지** 한 줄씩 적어라.
