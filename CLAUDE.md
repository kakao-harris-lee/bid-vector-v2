# Claude 구현 지침

이 저장소에서 애플리케이션 코드는 Claude가 구현한다. Codex는 독립 리뷰어다.

## 운영자 지시 2026-09-04 — 아래 모든 절보다 우선

- **Codex 심판에서 코드 부분을 제외한다.** 코드 slice 의 diff·구현 결과는 Codex 로 보내지
  않는다. 코드 리뷰는 Claude 측 `verifier`(저작 레인과 다른 패스)가 본다. milestone 계약의
  「Codex approve + 사용자 승인」은 코드 slice 에서 **「verifier ready-for-review + 사용자
  승인」** 으로 읽는다. 이유: 외부 유료 호출이 너무 잦아져 비용·시간이 늘었다(1A 에서
  Codex 16라운드).
- **목표·마일스톤·로드맵·스팩(명세·ADR·discovery·slice 계약)은 세션 모델(Opus 5 [1m]
  또는 Fable 5.1 [1m]) 하나가 단독으로 쓴다.** `spec-writer`·`deep-reasoner`·legacy-scout
  팬아웃 같은 다단계 기획 파이프라인과 Codex 검토를 붙이지 않는다. 세션 모델이 저장소를
  직접 읽고 문서를 직접 쓴다. 이유: 스팩 하나에 몇 시간이 걸렸다.
- Codex 레인은 운영자가 코드·기획 문서 외 산출물을 명시 지정해 요청할 때만 남는다.

## 시작 전 필수 읽기

1. `README.md`
2. `v2-지침서.md`
3. `agent-workflow.md`
4. `data-extract.md` (fixture/corpus 작업 시 단일 기준 문서)
5. 현재 `milestone-N.md`
6. 관련 `docs/adr/`와 `docs/discovery/`

## 구현 규칙

- 사용자와 Codex 승인을 받은 마일스톤/slice 하나만 작업한다.
- 작업 전 base SHA, in/out scope, acceptance command를 `reports/evidence/`에 기록한다.
- `bid-vector` symlink의 기존 프로젝트는 read-only 참고 자료다. 사용자의 별도 지시 없이
  수정하지 않는다.
- 기존 Python 구조·API·DB schema·출력을 1:1로 복제하지 않는다.
- 기대 동작은 승인된 도메인 명세와 authoritative fixture에서 가져온다.
- 테스트를 먼저 만들고 최소 구현 후 전체 acceptance command를 실행한다.
- 실제 DB/API/LLM/Telegram/email, push/merge/deploy는 사용자 승인 없이 실행하지 않는다.
- Codex review 파일을 수정하거나 자신의 구현을 승인하지 않는다.

## 리뷰 요청 조건

- 구현 diff가 커밋되어 base/head가 고정됨
- 관련 test/lint/type/architecture/contract 명령 통과
- 변경된 fixture와 정책 version의 근거 기록
- 알려진 제한과 rollback 또는 비활성화 방법 기록

Codex가 `request_changes`를 반환하면 같은 scope에서 Claude가 수정하고 다시 검증한다. 두 번의
수정 라운드 뒤에도 blocker가 남으면 자동 반복하지 말고 사용자에게 보고한다.

## 하네스: V2 slice pipeline

**목표:** 모든 마일스톤/slice 작업을 `조사 → 구현 → 검증 → Codex 독립 리뷰 → 사용자 보고`
파이프라인으로 실행하고, 레인 간 격리(구현자/검증자/심판)를 보장한다.

**트리거:** 마일스톤/slice 작업(조사, 명세·ADR·fixture 작성, 구현, 검증, Codex 리뷰,
수정 라운드, 부분 재실행) 요청 시 `v2-slice-pipeline` 스킬을 사용하라. 지침서에 대한
단순 질문은 직접 응답 가능.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-08-22 | 초기 구성 (에이전트 7종, 스킬 3종) | 전체 | - |
| 2026-09-04 | **Codex 심판에서 코드 제외** — Phase 5 를 코드 slice 에서 건너뛰고 완료 조건을 verifier+사용자 승인으로 · **기획 문서(명세·ADR·slice 계약) 단독 저작** — 세션 모델(Opus 5 [1m]/Fable 5.1 [1m])이 spec-writer·deep-reasoner 없이 직접 작성 | CLAUDE.md, codex-reviewer, spec-writer, codex-review-gate, v2-slice-pipeline | 운영자 지시 — 외부 유료 호출 과다(1A Codex 16라운드)와 스팩 저작 지연 |
| 2026-08-22 | 재활용 우선 방침 반영 — Python ML 재활용, 라이브러리 조사 선행, 재작성 목적(유지보수·회귀 감소) 명시 | ml-implementer, kotlin-implementer, legacy-scout, v2-slice-pipeline | 운영자 지시 |
| 2026-08-22 | 두 갈래 전략 확정 — service만 Kotlin 재작성, ML은 재활용+튜닝 (지침서 greenfield 서술 개정과 동기화) | ml-implementer, v2-slice-pipeline | 운영자 지시로 지침서 개정 |
| 2026-08-22 | 독립 감사 반영 — Codex 리뷰를 저장소 밖 clean worktree로 격리, .gitignore 추가, clean-tree 게이트 단일 정의(in_scope 한정), 커밋 단계·안전 규칙·재작업 상한(총 4회)·OPEN 에스컬레이션·secret 스캔 추가, M0 문서 slice N/A 규칙, verdict JSON schema 번들 | 전 에이전트, 전 스킬, .gitignore | 하네스 독립 감사 blocker 3건·high 9건 수정 |
| 2026-08-22 | Kotlin 코딩 규율 성문화 — TDD 우선, DI 적극, 분기 도배 금지(패턴 대체), 매직넘버 금지(정책 데이터/설정 외부화), 중복·주석 최소화, 회귀 구조적 방지 | v2-지침서 §5, kotlin-implementer | 운영자 지시 |
| 2026-08-22 | codex --output-schema용 strict 변형 스키마 추가 (모든 키 required, line null 허용, reviewer 제외) | codex-review-gate | 첫 리뷰 실행에서 OpenAI strict 제약 위반으로 400 실패 발견 |
| 2026-08-26 | codex 호출에 `model_reasoning_effort=high` 고정 | codex-review-gate | M0/0A 3차 리뷰에서 같은 range가 effort에 따라 medium `approve` / high `request_changes`로 갈려 리뷰 재현성 결여 발견 |
| 2026-08-27 | codex 호출에 `features.memories=false` 추가 + memory 흔적 preflight | codex-review-gate | M0/0B 4차 리뷰에서 codex의 첫 명령이 worktree 밖 `~/.codex/memories/MEMORY.md`를 읽어, worktree 격리가 읽기를 막지 못함이 드러남. **override의 효과는 다음 라운드에서 실증 확인 대기** |
| 2026-08-30 | preflight ②의 `?mode=ro` → `?immutable=1` 교체 + 총 행 수 동반 확인 | codex-review-gate | `mode=ro`가 이 환경에서 반복 실패(`unable to open database file (14)`). 0D가 진동을 관측 다섯으로, 0C 6차 리뷰어가 기제를 진술. **기제는 미확정이고 교체는 「`immutable=1`이 열린다」는 실측에만 기댄다.** `immutable=1`이 동시 writer 부재를 가정하므로 「0행」이 「못 열어서 0」이 아님을 총 행 수로 확인한다 |
| 2026-08-30 | 재작업 상한을 **5회**로 명시하고 「상한 도달 = 접근 재검토」로 성문화, 리뷰·검증을 증분마다 걸지 않도록 Phase 4·5 규정 | v2-slice-pipeline | 운영자 지시 — M0/0C에서 검증·수정 루프를 스무 라운드 넘게 돌렸다. 상한이 있었으나 「한 라운드 더」를 매번 여쭙는 방식으로 무력화됐다 |
| 2026-08-30 | evidence 규격을 금지로 명시 — **출력 전문 금지**(핵심 결과 한 줄) · **라운드 이력 절 금지** · **자기 검사 하네스 금지** · **크기 게이트**(evidence ≤ 산출물) | evidence-pack, v2-slice-pipeline | 운영자 지시 — M0 실측에서 evidence 20,373줄 대 산출물 8,730줄(2.3배), 0C 커밋 154개 중 108개가 산출물 무접촉. 출력을 문서에 박은 것이 선언 SHA 래칫·재취득·축어 대조·고정 표본을 차례로 불렀다 |
| 2026-08-31 | 잔존 「총 4회」 둘(Phase 5 병기·에러 표)을 「총 5회」로 정합 — 2026-08-30 상한 개정의 전파 누락 | v2-slice-pipeline | Codex 0E 재리뷰 B2 finding #2 (medium) — 한 워크플로우에 상한이 둘 |
| 2026-09-01 | codex 바이너리 경로(nvm `v22.21.1/bin/codex`)·버전(0.151.0) 고정 + 실행 전 버전 assertion(불일치 = preflight 미충족) | codex-review-gate | B5 리뷰 레인 실측 — 바이너리 둘 공존(homebrew 0.148.0 / nvm 0.151.0)으로 PATH 순서가 라운드마다 심판 버전을 조용히 결정(B3·B4 는 0.149.0, B5 는 0.148.0). effort 고정과 같은 재현성 사유. 핀 값 0.151.0 은 운영자 지정(사용 중인 최신) — **B6 부터 B5 와 엔진이 다름**을 스킬에 명기 |
| 2026-09-01 | Phase 5 에 **장부층 종결선** 신설 — 재리뷰가 도메인·계약층 finding 없이 장부층(evidence 정합성) non-blocker 만 내면 수정 라운드 없이 「알려진 제한」 등재 + 사용자 승인으로 종결 | v2-slice-pipeline | 운영자 채택 — 0E finding 계보 분석: B4 이후 전 finding 이 수정 커밋 자신이 낡게 만든 장부층(B5 high 는 B4 수정이, r9 F-1·F-2 는 B5 수정이 생성). 「approve = 장부의 완벽성」이면 루프가 정의상 안 끝남. 도메인층 finding 이 하나라도 있으면 미적용 |
| 2026-09-02 | 누출 검사 **판독 규칙** 추가 — diff 가 스킬 자신·CLAUDE.md 이력을 담는 라운드는 grep 에 상시 false-positive 바닥, developer 구간 매치만 주입으로 판독 | codex-review-gate | B6 실측 — `dc27007` 이 주입 패턴 표를 리뷰 diff 에 넣어 매치 9 발생, 전부 diff hunk 안·developer 구간 0. 핀 도입(2026-09-01)이 만든 부작용의 마무리 |
| 2026-09-02 | 장부층 종결선을 **정지·보고형으로 개정** — request_changes 는 slice 를 닫지 못하고, 장부층-only·상한 도달 = 자동 라운드 없이 **미승인 상태로 정지·보고**, 종결은 운영자 개별 명시 결정(예외 기록) | v2-slice-pipeline | Codex B8 high — 원 규칙이 리뷰의 차단력을 제거해 agent-workflow.md(임의 축소 금지)·milestone-0.md:133(approve+사용자 승인)과 충돌. 운영자 결정으로 반루프 취지(자동 라운드 금지)만 남기고 재분류 권한은 철회 |
| 2026-09-02 | 검증 6단계에 **종말 verdict 확인** 추가(`commands_run` 빈약한 approve = 미완 실행) + 위협 모델에 **hooks 표면** 등재(stdout 쓰는 hook = preflight 미충족) | codex-review-gate | B8 리뷰 레인 실측 — codex 가 작업 전 조기 approve 를 내뱉음(raw 136행, `-o` 종말 캡처로 사고 면함) · 한 라운드 262 hook 이벤트가 두 플래그로 안 닫힘(현 orca hook 은 stdout 무기록·no-op 확인) |
| 2026-09-02 | hooks 검사를 **실행 실측**으로 규정(정적 grep 금지 — 3경로 실행 stdout 실측) · 조기 verdict 를 **체계적 거동**으로 갱신(B8·B9 연속) | codex-review-gate | B9 레인 실측 — 정적 스캔이 curl 파이프·`/dev/null` 리다이렉트된 printf 를 오탐해 정상 라운드를 떨어뜨릴 뻔함 · 조기 approve 가 B9 에서도 재현(raw 138행) |
| 2026-09-02 | 조기 verdict 가 **실행 중 어느 지점에서든** 나옴을 명기 — B10 에서 한 라운드 verdict 6회, 명령 0건 approve 가 끝자락(7684행) | codex-review-gate | B10 레인 실측 — 조기 방출이 앞부분에 국한되지 않음이 확인돼 종말 verdict 확인의 근거를 갱신. 캡처가 조금만 일렀어도 가짜 승인이 저장될 뻔함 |
| 2026-09-02 | **직전 행을 정정** — 「끝자락 7684행 approve」는 codex 가 jq 로 읽은 0A verdict JSON 의 stdout(방출 아님), 「6회」는 템플릿·파일 출력 혼입(실제 방출 4회, 조기 approve 2회는 140·3150행 — 둘 다 종말 이전). **방출 계수 규칙**(codex 표지 기준 — 뜬 텍스트는 방출 아님)을 스킬에 성문화 | codex-review-gate | 구현 레인이 C-19 등재 중 원문 대조로 오독 발견(파일 읽기 출력을 방출로 — 누출 검사와 같은 갈래). B10 의 실제 신사실은 「한 라운드 조기 approve 2회」이고 종말 확인의 필요성은 불변 |
| 2026-09-02 | 계수 규칙에 **CLI echo 절** 추가 — `tokens used` 블록이 앞서면 `codex exec` 의 종료 시 재출력이지 방출 아님 | codex-review-gate | 리뷰 레인이 성문화된 규칙을 재실측해 발견 — echo(B8 10027 · B9 9181 · B10 7704행, 세 라운드 전부)가 「codex 표지」 규칙을 통과해 라운드마다 +1 과대계수. 보정 계수: B8 2 · B9 2 · B10 4(조기 approve 2 — 2%·41% 지점, 중반 방출 실재) |
| 2026-09-02 | 정지선 문단의 **「Codex approve 없는 종결 예외」 절 삭제** — 정지선은 미승인 정지·보고뿐, 종결 조건은 milestone-0(approve+사용자 승인)이 정본, 조건 변경은 agent-workflow.md·milestone 계약의 별도 명시 개정으로 | v2-slice-pipeline | Codex B13 high — B8 개정이 차단을 선언한 자리에서 곧바로 예외로 풀어 같은 문단의 「차단력 불변」과 자기모순, 13라운드 request_changes 인 slice 를 완료로 표시할 문이 남아 있었음. 운영자 결정 2026-09-02 |
| 2026-09-02 | 조기 verdict **위치 무관**을 올바른 근거로 복원 — B13 조기 approve 2회가 25%·89% 지점(성문화 계수 규칙 하의 진짜 방출) | codex-review-gate | B10 에서 오독 근거(jq stdout)로 철회했던 명제가 B13 실측으로 다시 섬. 89% 방출은 실행 끝자락이라 「앞부분만 거르면 된다」로 완화 불가 — 종말 확인 유지 근거 강화 |
| 2026-09-02 | 계수 규칙의 **판별자를 형태로** — `reviewed_base`·`reviewed_head`·`findings` 동반 객체만 방출로 셈(앞머리 `{"verdict":"` 앵커 금지) | codex-review-gate | B14 레인 실측 — 도메인 스키마의 `verdict` 필드(`license-*` 기대값)가 fixture 출력으로 raw 에 8건 찍혀 앞머리 앵커 카운터가 오탐. jq stdout·CLI echo 에 이은 세 번째 표면 |
| 2026-09-02 | 계수 판별을 **앵커(codex 표지 선행) ∩ 형태** 교집합으로 정정 — 형태 단독 금지 | codex-review-gate | 구현 레인이 B13~B15 세 라운드를 재실측: 앵커 3·3·2(매번 옳음) 대 형태 단독 4·4·4(매번 과다 — CLI echo 포함, B15 는 판별자를 설명하는 evidence 행 C-23 까지 셈 = 네 번째 표면). B14 의 「형태가 더 튼튼」은 그 라운드에 둘 다 맞았던 것일 뿐 |
| 2026-09-02 | 코드 slice 용 **오프라인 Gradle 실행 능력** — RO 의존 캐시 사본(`GRADLE_RO_DEP_CACHE`) + worktree 안 `GRADLE_USER_HOME`(wrapper 배포본만) + `--offline`, 레인 사전 스모크, `.gradle-home/` 만 status 예외, 네트워크는 계속 차단 | codex-review-gate | M1/1A 1차 리뷰에서 Codex 가 gradle 을 한 번도 못 돌림(`~/.gradle` 잠금 쓰기·DNS 차단) — finding 5 전부 정적 판독. 운영자 채택. 절차는 HEAD 에서 `clean check --offline` 통과로 실측 |
| 2026-09-02 | Phase 3 에 **스테이징 규율** — 구현 레인은 `git add <in_scope>` 만(`-A`·`-a`·`.` 금지, 커밋 전 `--cached` 대조), 하네스 레인은 편집 즉시 커밋, 혼입 시 이력 되쓰기 없이 evidence 선언 | v2-slice-pipeline | M1/1A 에서 하네스 레인의 미커밋 SKILL.md 편집이 구현 레인 커밋 `e733cfa` 에 혼입 — 공유 working tree 의 병렬 레인이 만든 첫 경계 위반. 1차 책임은 미커밋 편집을 남긴 오케스트레이터 |
| 2026-09-02 | §4b 에 **한계 명기** — codex sandbox 의 소켓 금지로 gradle 시동 불가(플래그 우회 없음), 운영자 결정 **네트워크 미개방·정적 리뷰 유지**. 코드 slice 리뷰 = 정적 판독 + 레인이 사전 스모크로 만든 `build/` 산출물 열람, 실행 재확인 정본은 verifier | codex-review-gate | M1/1A 2차 실측 — 캐시·쓰기는 §4b 로 해소됐으나 세 번째 원인(seatbelt `DatagramSocket` 금지)이 드러남. 유일 우회 `network_access=true` 는 유출 표면이라 운영자 거부 |
| 2026-09-02 | 「만들지 않는 것」에 **낡는 좌표** 추가 — 편집 대상 파일에 `file:line` 금지(인용문·절 제목·결정 ID 로), 정본 한 자리 + 포인터 | evidence-pack | M1/1A verifier r5 — `e733cfa` 의 +5줄로 줄 번호 참조 14곳 밀림, 둘은 엉뚱한 조건 지목. 「좌표 낡음」 클래스 네 번째(SHA→version→수치→줄 번호). 운영자 결정: 규격 성문화 + 5라운드를 구조 처리에 한정 |
| 2026-09-02 | 낡는 좌표 규격에 **역방향 파급 검사** 추가 — 승인 문서에 줄을 넣거나 빼면 그 파일을 가리키는 다른 문서의 `file:line` 을 grep 해 영향 목록을 내고 범위 밖은 알려진 제한 등재 | evidence-pack | M1/1A verifier r6 — `e733cfa` 의 milestone-1.md +5줄로 capability-map.md 의 M0 작성 좌표 열둘이 어긋났는데 diff 에 안 보여 정적 리뷰가 못 봄. r5 스윕과 직전 규격이 「내가 쓰는 좌표」만 봐 공유한 사각 |
| 2026-09-02 | §4b 사전 스모크를 **acceptance 전건 · `--no-daemon`** 으로 | codex-review-gate | M1/1A 3차 — `qualityBaseline` 이 `check` 밖이라 산출물 없어 codex 재대조 불가 · 2차 정리 후 데몬이 worktree 에 registry 를 다시 써 3차 `worktree add` 실패 |
| 2026-09-02 | **Phase 2.5 설계 검토** 신설 — 게이트·계약형 slice 는 구현 전 deep-reasoner 검토 필수(구성상 닫히는가·우회 경로 ≥5) | v2-slice-pipeline | 운영자 지시 — M1/1A 의 deny-list 설계를 Codex 3·verifier 7 라운드로 발견. 설계 리뷰 부재가 최대 비용 원인 |
| 2026-09-02 | verifier **차단 문턱** — not-ready 는 산출물 blocker/high 만, 장부층·low 는 등재 후 일괄 | v2-slice-pipeline, agents/verifier | 운영자 지시 — 1A verifier not-ready 5 중 3 이 장부층 |
| 2026-09-02 | 코드 slice 의 리뷰 preflight 정본을 **레인이 쓰는 형제 `codex-review-<UTC>.preflight.json`** 으로(reviewer 메타는 verdict JSON) — commands.md 라운드별 C-행 등재 폐지. `residual_risks` 는 Codex 소유라 레인이 쓰지 않음 | evidence-pack, codex-review-gate | 운영자 지시 — 라운드마다 등재 커밋 1 이 붙고 값은 JSON 과 중복 |
| 2026-09-02 | 구현 레인 모델 **sonnet** (`kotlin-implementer`·`ml-implementer`), 판정·검증 레인 opus 유지 | agents, v2-slice-pipeline | 운영자 지시 — 진짜 결함은 검증 레인이 잡았고 기계적 수정에 opus 단가 불필요 |
| 2026-09-02 | Codex 는 설계 검토·Phase 4 통과 뒤에만 — 게이트 강화의 실검증은 첫 도메인 코드 slice 에서 | v2-slice-pipeline | 운영자 지시 — 1A 의 결정적 결함(빈 도메인에서만 초록)은 실제 도메인 코드가 있어야 드러남 |
| 2026-09-02 | §4b 프롬프트에 `.gradle-home/` 비대상 명시 · 사전 스모크 명령 변수 확장 금지 | codex-review-gate | 1A 4차 — codex 가 세 라운드째 「clean worktree 와 다르다」 residual · zsh 단어 분리로 스모크 전건 실패가 exit 0 으로 가려질 뻔함 |
| 2026-09-03 | clean-tree 게이트 정의에 **경로 개별 인자 · 양성 대조 1회** 명시 | evidence-pack | 1A 구현 레인 실측 — in_scope 목록을 변수 하나로 넘겨 pathspec 하나가 되어 매치 0·exit 0 = 거짓 통과. 직전 행의 변수 확장 함정이 검증 쪽에서 재발 |
| 2026-09-03 | Phase 2.5 에 **(0) 위협 모델 경계 문장** 선행 — 방어하는 것/안 하는 것을 승인 문서 문면과 대조해 등재, 경계 밖 finding 은 경계 참조로 | v2-slice-pipeline | 1A 설계 검토 3차 진단 — 설계 검토 뒤에도 Codex 5·7·8차가 같은 계열(신뢰 앵커↔게이트 입력)이었고, 뿌리는 「우회 ≥5」가 아니라 모델 부재(`-x`·`enabled=false` 한 줄로 어떤 게이트도 열림). 운영자 채택 2026-09-03 |
| 2026-09-04 | slice 커밋 집합을 **range 가 아니라 in_scope 경로의 변경**으로 정의 — scope.md 에 상시 「하네스 레인 변경」 절(`git log <base>..HEAD -- CLAUDE.md .claude/` 로 등재, 운영자 승인 하 같은 range), rollback 은 **in_scope 경로 한정**(range revert 금지) | evidence-pack, v2-slice-pipeline | Codex 1A 15차 high — 하네스 레인이 같은 브랜치에 즉시 커밋하는 구조(2026-09-02 스테이징 규율)라 range 에 하네스 커밋 13개가 섞였는데 미선언이었고 rollback 의 range revert 가 그것까지 되돌렸다. 운영자 결정 2026-09-04 |
| 2026-09-04 | Phase 4 차단 문턱에 **예외** — 게이트 술어(판정 로직)를 바꾸는 커밋은 severity 무관 표적 재검증 · §4b 의 `--no-daemon` 을 「재발 방지」에서 **「완화」로 정정** + worktree 제거 뒤 잔여 디렉터리 확인 절차 | v2-slice-pipeline, codex-review-gate | 1A 실측 둘 — verifier r19 M-2 일괄 커밋이 재검증 없이 Codex 14차 high(`val java = 1` 로 전 참조 소실)가 됨 · 13차 리뷰 레인에서 `--no-daemon` 에도 single-use 데몬 fork 가 삭제된 worktree 에 registry 재생성. 운영자 지시 2026-09-04(열린 항목 진행) |
| 2026-09-04 | 경로 한정 rollback 명령을 `git checkout <base> --` 에서 **`git restore --source=<base> --staged --worktree --`** 로 정정 + rollback 명령은 **임시 clone 실측** 의무 | evidence-pack | Codex 1A 16차 high — 직전 행의 명령이 base 에 없는 신규 경로(129/135)마다 pathspec 오류로 exit 1. 구현 레인의 dry-run(diff 성립 확인)이 실행 가능성을 증명하지 못했다 |
| 2026-09-03 | 역방향 파급 grep 을 **축약형 포함(stem 기준)** 으로 · 「아래쪽 편집이라 안 밀림」 판단은 두 줄 번호를 명령으로 낸 뒤에만 | evidence-pack | 1A verifier r12 L-1 — ADR 0007 을 `docs/adr/0007:186` 으로 인용한 5건을 전체 파일명 grep 이 0건으로 놓쳤고, 문서 머리 +22줄 삽입에 「밀리지 않는다」로 적힘 |
