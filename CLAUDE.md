# Claude 구현 지침

이 저장소에서 애플리케이션 코드는 Claude 가 구현한다. Codex 는 되돌리기 어려운 경로와 계약 파일에 한정된
독립 심판이다. 이 문서는 **현행 규율만** 담는다 — 규율이 생긴 사유와 실측 이력은
`docs/harness/change-history.md`(2026-09-16 에 이 문서에서 분리, append-only)에 있고, 각 규율의 정본은
「대상」 열이 가리키는 스킬·에이전트 파일(`.claude/skills/*`, `.claude/agents/*`)이다 — **예외는 아래 하네스
절 끝의 ※ 다섯**으로, 그것들은 스킬 파일에 아직 문장이 없어 이 문서의 요약이 정본이다.

## 운영자 지시 (2026-09-04, 2026-09-11 갱신) — 아래 모든 절보다 우선

- **Codex 심판의 코드 범위는 되돌리기 어려운 경로로 한정한다(2026-09-11).** 인증·인가, 암호화·비밀값 취급, DB
  마이그레이션, 데이터 파기처럼 사고가 나면 복구가 없거나 비싼 slice 는 운영자가 범위·비용을 승인하면 Codex
  심판에 올린다. **`contracts/**` 의 proto·승인 태그 정책 변경(계약 파일)도 같은 절차**다(전역 규약 §4 ②,
  첫 적용 PR #5). 그 밖의 코드 slice 는 Codex 로 보내지 않고 Claude 측 `verifier`(저작 레인과 다른 패스)가
  본다 — milestone 계약의 「Codex approve + 사용자 승인」은 그런 slice 에서 **「verifier ready-for-review +
  사용자 승인」** 으로 읽는다. Codex 는 유료 외부 호출이므로 운영자가 명시 요청하고 범위·비용을 승인할 때만
  탄다(「리뷰해줘」는 승인이 아니라 레인 선택). **대상은 둘** — 위의 되돌리기 어려운 경로·계약 파일 slice 와
  **기획 문서(스팩·로드맵)의 계획 검토**(전역 규약 §4 ②). 그 밖의 코드 slice 는 대상이 아니다. 전역 규약
  `~/.claude/review-lane.md`.
- **리뷰 판정은 PR 에 남긴다**(전역 규약 §2). PR 을 만드는 변경이면 머지 전에 판정을 PR 코멘트로 남긴다 —
  지적이 0건이어도 남긴다. **세션 안에만 있는 리뷰는 남에게는 없는 리뷰다.** 게이트(`privacy-gate`·
  `contract-keeper`·`migration-reviewer`)를 돌렸으면 그 판정도 같은 자리에 붙이고, 수정한 뒤에는 **조치
  코멘트를 새로** 단다(기존 코멘트를 덮어쓰지 않는다). 코드 slice 는 `code-reviewer`(호출 시 `model: sonnet`
  명시, 저자와 다른 패스)를 verifier 와 병렬로 띄운다.
- **목표·마일스톤·로드맵·스팩(명세·ADR·discovery·slice 계약)은 세션 모델(Opus 5 [1m] 또는 Fable 5.1 [1m])
  하나가 단독으로 쓴다.** 다단계 기획 파이프라인(spec-writer·deep-reasoner·legacy-scout 팬아웃)과 Codex
  검토를 붙이지 않는다. 세션 모델이 저장소를 직접 읽고 문서를 직접 쓴다.

## 시작 전 필수 읽기

1. `README.md`
2. `v2-지침서.md`
3. `agent-workflow.md`
4. `data-extract.md` (fixture/corpus 작업 시 단일 기준 문서)
5. 현재 `milestone-N.md`
6. 관련 `docs/adr/`와 `docs/discovery/`

## 구현 규칙

- 사용자 승인을 받은 마일스톤/slice 하나만 작업한다.
- 작업 전 base SHA, in/out scope, acceptance command 를 `reports/evidence/` 에 기록한다.
- `bid-vector` symlink 의 기존 프로젝트는 read-only 참고 자료다. 사용자의 별도 지시 없이 수정하지 않는다.
- 기존 Python 구조·API·DB schema·출력을 1:1 로 복제하지 않는다.
- 기대 동작은 승인된 도메인 명세와 authoritative fixture 에서 가져온다.
- 테스트를 먼저 만들고 최소 구현 후 전체 acceptance command 를 실행한다.
- 실제 DB/API/LLM/Telegram/email, push/merge/deploy 는 사용자 승인 없이 실행하지 않는다.
- Codex review 파일을 수정하거나 자신의 구현을 승인하지 않는다.

## 리뷰 요청 조건

- 구현 diff 가 커밋되어 base/head 가 고정됨
- 관련 test/lint/type/architecture/contract 명령 통과
- 변경된 fixture 와 정책 version 의 근거 기록
- 알려진 제한과 rollback 또는 비활성화 방법 기록

Codex 가 `request_changes` 를 반환하면 같은 scope 에서 Claude 가 수정하고 다시 검증한다. 두 번의 수정 라운드
뒤에도 blocker 가 남으면 자동 반복하지 말고 사용자에게 보고한다.

## 하네스: V2 slice pipeline

**목표:** 모든 마일스톤/slice 작업을 `조사 → 구현 → 검증 → (Codex 독립 리뷰) → 사용자 보고` 파이프라인으로
실행하고, 레인 간 격리(구현자/검증자/심판)를 보장한다.

**트리거:** 마일스톤/slice 작업(조사, 명세·ADR·fixture 작성, 구현, 검증, 리뷰, 수정 라운드, 부분 재실행)
요청 시 `v2-slice-pipeline` 스킬을 사용하라. 지침서에 대한 단순 질문은 직접 응답 가능.

### 현행 규율 요약 (정본은 스킬·에이전트 파일 — 여기는 색인이다)

**레인과 모델**
- 구현 레인(`kotlin-implementer`·`ml-implementer`)은 sonnet, 판정·검증·설계 레인(`verifier`·`codex-reviewer`·
  `deep-reasoner`)은 opus. 모델은 에이전트 frontmatter 가 단일 출처.
- 저작과 검토는 다른 패스. 구현 레인은 자기 결과를 승인하지 않고, verifier 는 구현 대화를 받지 않는다.
- 진행 중 slice 의 브랜치는 종결(verifier ready-for-review + 사용자 승인) 뒤에만 병합한다. 병합 전 대조는 버릴
  clone/worktree 에서.
- 구현 레인의 완료 보고와 팀장 지시가 엇갈리면 HEAD 가 움직인다 — 검증 전 **레인 동결 + 판정 대상 SHA 고정**.

**계약(Phase 1)과 설계 검토(Phase 2.5)**
- scope.md 는 base SHA·in/out scope·acceptance·rollback·**상시 「하네스 레인 변경」 절**을 갖는다. slice 의 커밋
  집합은 range 가 아니라 **in_scope 경로의 변경**이다.
- acceptance 는 **CI 워크플로 job 의 명령 그대로**(`.github/workflows/ci.yml`). 부분 게이트는 안 돌린 것과
  같다; 줄일 수 있는 단위는 job(Kotlin `check` / Python `ml-engine`)뿐이다.
- 게이트·계약형 slice 는 구현 전 설계 검토 — (0) 위협 모델 경계 문장 → (1) 열거인가 구성인가 → (2) 우회 ≥5 →
  **(2b) 값 획득 축**(새 public 표면 전수, 「경계로 처리」 행도 실측, `object` 커널 주입 자리, 수정 라운드마다
  갱신) → (3) 과잉·미달.
- **게이트 술어는 문자열이 아니라 구조로 닫는다** — 텍스트 grep·주석 문자열 술어는 스타일 하나로 열린다.
  import 계약·AST·컴파일처럼 우회하려면 게이트 자체를 바꿔야 하는 층에 건다.
- 값 한 줄 slice 도 「그 값이 wire 에 닿는가」·「그 값이 만드는 거동 변화」를 test 로 잠근다.
- 수정 라운드가 만드는 새 파일은 **in_scope 와 대조**해 계약을 갱신한다(반복 사각).

**커밋·스테이징(공유 working tree)**
- `git add <in_scope 경로> && git commit -m … -- <같은 경로들>` — `-A`·`-a`·`.` 금지, 경로는 개별 인자.
  하네스 레인은 편집 즉시 커밋. 혼입이 생기면 이력을 되쓰지 않고 evidence 에 사실로 선언.
- rollback 목록은 자기를 담은 커밋을 가리킬 수 없다 — 공유 파일을 만지는 문서 커밋과 목록 갱신 커밋을 나눈다.

**검증(Phase 4)**
- slice 끝에 한 번. 차단 문턱은 산출물 blocker/high 만(`not-ready`); 장부층·low 는 등재 후 승인 전 일괄.
  **예외**: 게이트 술어를 바꾸는 커밋은 severity 무관 표적 재검증.
- 재작업 상한 5회(not-ready + request_changes 합산) — 도달하면 「한 라운드 더」가 아니라 진단을 붙여 보고.
- verifier 의 최고 가치는 우회 고안·변이 실측이다. 표적은 구현 보고의 알려진 제한을 승인 문서 문면과 대조해
  만든다.
- Codex 라운드는 설계 검토·Phase 4 통과 뒤 의미 있는 단위로만. 장부층-only 재리뷰는 자동 라운드 없이 미승인
  정지·보고(종결 조건은 milestone 계약이 정본).

**evidence(`evidence-pack`)**
- 출력 전문 금지(핵심 결과 한 줄), 라운드 이력 절 금지, 자기 검사 하네스 금지, 크기 게이트(evidence ≤ 산출물).
- **낡는 좌표 금지** — 이 slice 가 편집하는 파일에 `file:line` 을 쓰지 않는다(인용문·절 제목·결정 ID 로).
  게이트 baseline·allowlist 키도 좌표를 쓰지 않는다. 역방향 파급(`grep -rn '<stem>:[0-9]'`)도 본다.
- **비밀값 스캔 어휘를 evidence 에 축어로 적지 않는다** — 참조형 `grep -rniE -f config/quality/leak-patterns.txt`.
  evidence 편집 커밋마다 그 HEAD 에서 Kotlin `check` 재실측하되, **마지막 HEAD 의 결과 정본은 verifier 와
  PR 조치 코멘트**다(evidence 에 넣으려는 시도가 커밋을 또 낳는다).
- clean-tree 게이트: `git status --porcelain -- <in_scope 개별 인자>` 빈 출력 + 양성 대조 1회(비파괴 절삭,
  `checkout --` 금지).

**rollback**
- range revert 가 아니라 in_scope 경로 한정 `git restore --source=<base> --staged --worktree --`. 목록은
  `git diff --name-status <base>..HEAD` 기계 산출, 라운드마다 재산출.
- 공유 파일은 커밋 해시 hunk 격리(`git diff <sha>~1..<sha> -- <파일> | git apply -R`), `--3way` 도 자동
  해소에 실패하므로 수동 절차를 미리 적는다. 같은 slice 의 자기 이력은 착수 커밋 기준 단일 역적용 + 하네스 절
  재등재. 확인은 「내 줄 사라짐」과 「남의 줄 남음」 둘 다.
- 임시 clone 에서 ①~⑥ 실측: 명령 exit · D/M 수 · diff 빈 것 · ④ compile · ⑤ test · **⑥ 게이트**(초록이 아니면
  보완 경로까지 실행). 갈음은 「HEAD 초록」이 아니라 **트리 동일성**으로만.
- 실측은 **그 slice 의 마지막 산출물 커밋**에서 돌고 `실측 HEAD: <sha>` 를 rollback.md 에 적는다. verifier 는
  그 SHA 가 판정 대상 SHA 와 같은지를 먼저 보고 다르면 **미검증**으로 처리한다. 앞 라운드 실측을 옮기지 않는다.
  이 확인은 **CI 텍스트 게이트로 대체되지 않는다**(2026-09-18 측정: 실제 PR 32건 재생 FAIL 23·진짜 결함 0·오탐 1).

**Codex 레인(`codex-review-gate`)**
- 저장소 밖 clean worktree, `model_reasoning_effort=high`, `features.memories=false`, 바이너리 핀(0.154.0,
  라운드 도중 변경 금지), hooks 는 실행 실측, 종말 verdict 확인(방출 계수 = 앵커 ∩ 형태), 오프라인 Gradle 은
  RO 캐시 + `--no-daemon`(sandbox 소켓 금지로 시동 불가 — 정적 리뷰 + 사전 스모크 산출물).

**CI**
- 상시 붉은 게이트도, 안 돌린 게이트도 아무것도 막지 못한다. 러너에 도구가 없으면 로컬 초록은 의미가 없다
  (`buf` 설치·`fetch-depth: 0`). collection 선택에 따라 답이 달라지는 게이트는 서브프로세스 격리로.

**그 밖의 계열(색인 생략 — 정본과 이력만)**: 재활용 우선·두 갈래 전략(v2-지침서, ml-implementer) · Kotlin 코딩 규율
(v2-지침서 §5, kotlin-implementer) · OPEN 에스컬레이션·M0 문서 slice N/A 규칙(v2-slice-pipeline Phase 6, evidence-pack)
· codex strict 스키마·`immutable=1` preflight·누출 판독 규칙·preflight 형제 JSON·`.gradle-home/`·사전 스모크
(codex-review-gate) · CI 액션 major 정책(`.github/workflows/ci.yml` 주석).

※ **아직 스킬 파일에 문장이 없어 이 요약이 정본인 규율** — 「레인 동결 + 판정 대상 SHA 고정」(구현 레인의 완료
보고와 팀장 지시가 엇갈리면 검증 중 HEAD 가 움직인다 — 판정 전 동결하고 SHA 를 고정한다) · 「rollback 갈음은
「HEAD 초록」이 아니라 트리 동일성(되돌린 트리의 파일 SHA 가 이미 실측한 트리와 같음)으로만」 · 「collection 선택에
따라 답이 갈리는 게이트는 서브프로세스 격리」 · 「수정 라운드가 만든 새 파일 ↔ in_scope 대조를 보고 항목으로」 ·
「code-reviewer(sonnet)를 verifier 와 병렬로」(전역 규약 §1 유래). 다음 하네스 편집에서 해당 스킬(v2-slice-pipeline
Phase 3·4, evidence-pack rollback 절)로 옮긴다. **근거의 자리**: 뒤 넷은 M5 evidence·`milestone-5.md` 에
실측이 있고(5E-1 순수성 게이트 · 5E-3·5F-2 rollback · 각 slice checklist), 「레인 동결 + 판정 대상 SHA
고정」은 **이 문서가 유일한 근거**다(2026-09-16 M5/5F-1 에서 구현 완료 보고와 팀장 지시가 엇갈려 검증 중
HEAD 가 다섯 커밋 이동한 실측 — 그 사건은 PR #24 조치 코멘트에 있고 이력 행은 다음 하네스 편집에서 세운다).
