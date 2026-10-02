# M6/6G-2e — 실수집 전 필수 여섯: 개찰 갈래 범위 정책 · 실행 상태 셋 · 백테스트 CLI · seed 수 고정 (계약, 착수 2026-10-02)

> **지위: 착수(2026-10-02).** base `c63d0d3a`(PR #53 6G-2a 머지 뒤 `main`), worktree `bid-vector-v2-m6-6g2e`, 브랜치 `m6-6g2e/2026-10-02`. 운영자 결정: **A-1 개찰 갈래 `maxSpanDays` = 120일**(2026-10-02) · A-2 즉시 착수(2026-10-01). 초안 이력: `docs/m6-6g2-contracts`.
> **초안 시점 문면:** 운영자 결정 2026-10-01 「6G-2a 다음에 6G-2e 를 먼저, 그 뒤 2b → 2c」.
> 수령 OPEN: **`OPEN-6G-OPENING-RANGE-CAP`**(PR #52 리뷰, 2026-10-01 — 실수집 차단) · **`OPEN-6G-REVIEW-FOLLOWUPS` ★ 셋**(6G-2d D-6G2d-53) · **`OPEN-6G-BACKTEST-CLI`**(runbook 대조).
> 이 slice 가 머지되기 전에는 A-1 기간의 실 KONEPS 수집을 시작할 수 없다(6G-2d D-6G2d-55 조건에 추가).

- base: **`c63d0d3a`**.
- 레인: `kotlin-implementer`(①~④) + `ml-implementer`(⑤, 파일 하나) — 둘은 파일 집합이 겹치지 않는다. 호스트 빌드 직렬.

## 왜 이 slice 인가

실수집 runbook(PR #52)의 `/code-review` 가 6G 산출물의 실수집 전제 결함을 잡았다: **수집 범위 상한 31일**(`COLLECTION_RANGE_POLICY.maxSpanDays`, 6F-8 D-6F8-3 — 공고 목록 갈래의 호출 폭주 방지)이 개찰 갈래(`OpeningCollectionWiring.openingCollectionRange` → `resolveCollectionRange`)에도 걸려 A-1 승인 기간(업무별 하한율 변경일 이후 ~ 현재, 최대 16주)이 `SPAN_TOO_LONG` 으로 기동 거부된다. 창을 쪼개 같은 실행 상태 디렉터리로 돌릴 수도 없다 — 확정 표본이 `SampleScope(from, to, divisions)` 를 고정해 「표본틀 범위가 지금 설정과 다르다」로 거부된다. 6G 검증은 짧은 창 E2E 만 돌려 이 자리를 못 봤다. 함께, 6G-2d 가 실수집 전으로 미룬 실행 상태 결함 셋과 백테스트 job 의 CLI 부재를 한 slice 로 닫는다.

## 착수 실측 (착수 시 채운다)

| 항목 | 값 |
|---|---|
| base SHA | `c63d0d3a` |
| `resolveCollectionRange` 호출 자리 | **둘** — `CollectionWiring`(공고 목록) · `OpeningCollectionWiring`(개찰); 함수는 `CollectionWiringSupport.resolveCollectionRange`(팀장 grep). 추출 배선은 부르지 않는다(착수 실측으로 확정) |
| 개찰 갈래 호출 수가 창 길이에 의존하는가 | 개찰결과 목록 = 기간 조회 → 쪽 수가 기간에 비례(상한 20,000/80,000 이 묶음) · 상세 셋 = 공고당 1회 → 표본 크기에만 의존(6G P-2) |
| seed 로더 | `ml_engine/evaluation/policy.py` — `stability_seeds` 는 평탄 색인 키, 검사는 「비어 있지 않음」뿐(길이 미요구) — D-6b 의 자리 |
| 백테스트 CLI | 없음(`backtest_job.py` 에 `__main__`·argparse 없음, `pyproject.toml` scripts 없음) |
| Kotlin `check` test 수 · pytest | 2,588(6G-2d 종결) · 1,350(6G-2a 종결) |

## 재사용 조사 (Phase 2)

| 후보 | 판정 | 근거 |
|---|---|---|
| `EffectiveDatedPolicy<CollectionRangePolicyData>` 와 `resolveCollectionRange(from, to, clock, label)` | **채택·확장** — 정책 인스턴스를 **갈래별로** 둔다(`OPENING_COLLECTION_RANGE_POLICY`), 함수에 정책 인자를 받게 | 상한의 뜻(호출 폭주 방지)은 갈래마다 다르다: 공고 목록은 기간에 비례, 개찰 상세는 표본 크기에 비례 |
| 6G-2d verifier 가 남긴 probe(torn-only 꼬리 · Busy/heal 순서) `scratchpad/g2d-*-keep` | **참고** — 사라졌으면 `verifier-r3.md` 기술로 재작성 | |
| `ml_engine.app.backtest_job.run_backtest_job` | **그대로**, `__main__` 만 더한다 | 판정 경로 무변경 |

## 결정

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2e-1** | **개찰 갈래의 범위 정책을 분리한다.** `OPENING_COLLECTION_RANGE_POLICY`(정책 데이터, 리터럴 금지 — `maxSpanDays` 는 A-1 의 16주를 덮는 값으로 운영자 승인 A-1: 제안 **120일**) 를 `OpeningCollectionWiring` 이 쓰고, 공고 목록 갈래는 31일 그대로. 추출 갈래(`SnapshotExtraction` 관측 창)도 같은 함수를 쓰면 개찰 쪽 정책을 따른다(실측으로 확정). 개찰 갈래의 호출 수는 창 길이가 아니라 **표본 크기 × 축 + 개찰결과 목록 쪽 수**이고 상한 20,000/80,000 이 따로 묶는다. test: 120일 창 수락 · 121일 거부 · 공고 목록 갈래 32일 거부 그대로. 변이: 공고 목록 갈래에 긴 정책을 꽂음 → RED | 상한의 뜻이 갈래마다 다르다 |
| **D-6G2e-2** | **표본틀 창은 한 번에 확정**(D-6G-39·50 그대로) — 창을 쪼개는 길은 열지 않는다. 쪼갠 창으로 같은 디렉터리를 쓰려는 기동은 지금처럼 거부 | 모집단이 하나여야 표본이 하나다 |
| **D-6G2e-3** | **★① 찢어진 조각만 남은 꼬리 라운드**: `read()` 가 걸러 낸 torn 표식도 열린 라운드의 증거다 — `interruptedRounds` 가 꼬리의 torn 표식을 보고 `Failed(INTERRUPTED)` 로 닫아 상한에 하나로 센다(예산은 이미 하나로 셈 — 두 장부 일치). test: 첫 PENDING 쓰기 중 크래시 세 번 → 네 번째 0 호출. 변이 → RED | 6G-2d cr 4차 ② |
| **D-6G2e-4** | **★② `incompleteAValues` 는 A 축 행 자체의 적용 여부로 센다** — 기초금액 축 행의 `formulaAApplies` 에 묶지 않는다. test: 기초금액 축이 빈 공고의 A 결손 → 계수 1. 변이 → RED | 6G-2d cr 4차 ③ |
| **D-6G2e-5** | **★③ 복구 순서와 가드**: `verifyIntegrity`(directory_id · 모르는 파일 · 장부 유무)를 `healTornTail` **앞**에 — 거부될 디렉터리는 고쳐 쓰지 않는다(검사가 원장 줄 수를 요구하면 읽기 전용 계수로) · `LedgerDigest` 읽기를 잠금 가드 **안**으로(비UTF-8 등 예외가 잠금을 남기지 않게). test: 복사된 디렉터리(directory_id 불일치) + 찢어진 끝 줄 → 거부되고 원장 바이트 불변 · 비UTF-8 원장 → 예외 뒤 같은 프로세스에서 다시 열면 Busy 아님 | 6G-2d cr 4차 ⑦ |
| **D-6G2e-6** | **백테스트 CLI**(Python 파일 하나 `ml_engine/app/backtest_cli.py` 또는 `backtest_job.__main__`): 인자 `--snapshot-uri`(file:// 강제, 상대 경로면 절대로) · `--backtest-policy` · `--inference-policy` · `--output-dir`(스냅숏 디렉터리 **밖** 강제) · 종료 코드 0/1 · `JobFailed` 사유 출력. 판정 경로·정책 로더 무변경(`evaluation/**` diff 0), import-linter 층 계약 준수(app 층). test: 성공 경로 + 네 실패 사유 + 출력이 스냅숏 안이면 거부 | runbook 2-4 의 스크립트를 코드로 |
| **D-6G2e-6b** | **(6G-2a vr r1 M-4 이월) seed 수를 정책 스키마가 고정한다** — `stability_seeds` 색인 목록 판독기가 연속성만 요구해 하나만 있어도 로드되고 그러면 seed 안정성 레그가 항상 참이다. 로더(`ml_engine` 정책 로더, Python `src`)가 길이 == 5(승인 A-3 값, 정책 schema 에서)를 요구하고 아니면 `*_POLICY_REJECTED`. test: seed 넷·하나 → 거부, 다섯 → 수락. `OPEN-6G2A-SEED-COUNT-NOT-PINNED` 닫음. ml-implementer 몫(⑤ 와 같은 레인) | 6G-2a 가 로더를 out_scope 로 두어 넘긴 것 |
| **D-6G2e-7** | **게이트 술어 확장 0 · 실행 상태 형식 version 2 유지 · 스키마 칸·golden 무변경.** 새 정책 데이터는 등재 | |
| **D-6G2e-8** | **runbook 갱신은 이 slice 의 산출물** — `docs/runbook/m6-6g-real-collection.md` 0 절의 차단 행 둘을 닫고 2-2·2-4 를 코드대로 | 문서가 코드를 따라간다 |

## 계약 갱신 r0-b (2026-10-02, 팀장 — Python 레인 조사 수령: seed 수의 자리 · in_scope 공백)

| ID | 결정 |
|---|---|
| **D-6G2e-9** | **in_scope carve-out(Python)**: `ml-engine/src/ml_engine/evaluation/backtest/policy.py`(백테스트 정책 키 스키마 — D-6b 의 자리; 길이는 공용 `collect_indexed_list` 가 아니라 정책별 키 스키마에, 리터럴 `5` 금지 → 다섯 키 열거 + `len`) · `ml-engine/src/ml_engine/evaluation/policy.py`(평가 정책 키 스키마, D-10) · `ml-engine/tests/app/test_backtest_policy_sensitivity.py`(6G-2a 양성 대조 등록이 설계상 RED 가 되므로 그 등록 삭제) · `ml-engine/tests/evaluation/test_evaluation_policy.py`(D-10 helper). out_scope 의 「`evaluation/**`」는 이 네 파일을 제외하고 유지 — `evaluation/backtest/run.py` 등 판정 경로 무변경 |
| **D-6G2e-10** | **평가 정책 로더(`load_evaluation_policy`, `evaluation-v1.yaml`)도 seed 다섯을 요구한다** — 같은 구멍(M5 학습 안정성 레그가 seed 하나로 공허). 조건: 출하 `evaluation-v1.yaml` 이 정확히 다섯이 아니면 **멈추고 보고**(학습 경로를 깨지 않는다). test helper 가 두 seed 만 쓰던 것을 다섯으로, 거부 test 여섯이 「잘못된 이유로 통과」하지 않음을 확인. 한 커밋에 두 로더 |

## 계약 갱신 r1 (2026-10-02, 팀장 — 두 레인 완료 보고 수령: Kotlin HEAD `caccd807` · Python HEAD `4e8cfc76`)

| ID | 결정 |
|---|---|
| **D-6G2e-11** | **이탈 수용(Kotlin)**: ① D-3 의 처방 자리는 `interruptedRounds` 가 아니라 **원장 어댑터** — 조각에서 (시각·축·공고)가 읽히면 **의도 줄**로 되살려(결말로는 안 됨; 의도 줄이므로 `walk = null`, 조각 시각은 **`at`** 로 실려 `interruptedRounds` 가 그것을 읽는다 — PR #54 리뷰 ⑨ 문면 정정; `tornLines` 이중 셈 없음) 기존 규칙이 열린 라운드로 본다 ② 「세 번 크래시 → 네 번째 0 호출」은 사슬 둘(조각 → 열린 라운드 · 의도 줄 → 상한 → 0 호출)로 — 한 test 로 재려면 workflow 지원 250 줄을 adapters 에 복제해야 한다. verifier 는 접합부 변이 둘로 확인. **추출 배선 실측**: `SnapshotExtractionWiring` 은 범위를 재지 않는다(KONEPS 호출 없음) — 거동으로 못 박음 |
| **D-6G2e-12** | **이탈 수용(Python)**: ① D-10 으로 두 로더 모두 seed 다섯(허가대로) ② `test_evaluation_policy.py` 의 부수 정정 둘 — 거부 test 여섯이 seed 수로만 거부하던 것을 기준값 통일 + 양성 대조, `..._rejects_gapped_indexed_list` 가 틈을 만들지 않고 엄격 상승으로 거부하던 공허(이 slice 이전 부채) 정정 ③ `pyproject.toml` 무변경(`[project.scripts]` 없음 → `python -m ml_engine.app.backtest_cli`) ④ Python 커밋 trailer 가 `Claude Opus 5` — 이력 되쓰지 않음, 사실 선언 |
| **D-6G2e-13** | **교차 사실**: evidence 두 파일이 Kotlin 커밋 `caccd807` 에 Python 절을 품은 채 처음 추적됨(공유 트리에서 untracked 였음 — 두 커밋 메시지가 선언) · runbook 공유 hunk 역적용 순서는 **Kotlin(`dea3e6da`) → Python(`2e02b35f`)**, Python 축은 수동 절차가 정본(`--3way` 충돌) · 착수 표 test 수 2,588 → 되돌린 트리 기준 **2,580**(+10 = 2,590), pytest 1,350 → 1,372(+22) — 표는 고치지 않고 evidence 에 사실 |
| **D-6G2e-14** | **r1 판정 SHA 는 이 갱신 커밋.** verifier 표적: D-1 120/121/32 + 변이 둘 · D-2 쪼갠 창 거부 · D-3 조각 → 의도 줄 → 상한 사슬(접합 변이 둘, 세 번 크래시 판을 verifier 가 직접 이어 잼) · D-4 기초금액 축 빈 + A 결손 · D-5 복사 디렉터리+찢어진 꼬리 거부·바이트 불변, 비UTF-8 뒤 Busy 아님 · D-6 CLI 성공·실패 사유 넷·스냅숏 안 출력 거부·경로→URI · D-6b/10 두 로더 1/4/5/6 · 출하 YAML 둘 로드 · acceptance `check` job 넷 + `ml-engine` job + container(판단) · rollback 두 축 문서 절차 그대로(runbook 순서 포함, 복원 목록 ⊇ 변경 소스 전수) · 새 public 표면(정책 인스턴스·`main(argv)`) · 형식 version 2 · `evaluation/backtest/run.py` 등 판정 경로 diff 0. code-reviewer(sonnet): 정적, 두 레인 diff |

## 계약 갱신 r2 (2026-10-02, 팀장 — verifier r1 **not-ready**(정정) H-1·M-1·M-2·L · code-reviewer r1 H-1·M-1~4·L 11 수령)

판정 SHA `8b39b4c4`. 통과: D-1 변이 셋 RED·모듈 밖 생성 불가(Kotlin·억제·copy·Java) · D-2 · D-3 출하 조립 사슬(INTERRUPTED 1·2·3 → 네 번째 0 호출) · D-5 변이 · D-6 변이 셋 · seed 1/4/6 거부·5 수락 · acceptance `check` 2,590 · `ml-engine` 1,372 · container S-21~25 · rollback 두 축(Python 은 `7463ad35` 문서, 경로 (가)(나)). 막는 것 하나. **재작업 1/5.**

| ID | 결정 |
|---|---|
| **D-6G2e-15** | **(vr H-1 = cr M-1·M-2 — 데이터 정확성) `incompleteAValues` 회귀.** D-4 가 「A 행 자체의 적용 여부」로 바꾸면서 기초금액 축이 **Y** 인데 A 행이 비었거나 판독 불가인 경우(base 1 → 0), 품질관리비만 있고 그 술어가 부재·빈 값인 경우(1 → 0)를 세지 않는다. 값(`a_value`)은 동일, 공시 계수만 과소. 처방(verifier 제안 채택): **기초금액 술어가 Y 면 센다**; A 행 입력 존재는 기초금액 술어가 **부재·미지일 때만** 쓴다; 품질관리비 금액은 그 술어와 무관하게 **입력**으로 친다. 여섯 판(base Y+빈 · Y+판독 불가 · 비용만+술어 부재 · 비용만+빈 · 축 부재+빈 → 0 · N+비용만 → 0)을 test 로, 변이: 어느 한 모양을 빠뜨림 → RED. 알려진 제한 4(「한 모양만 갈린다」) 정정 |
| **D-6G2e-16** | **(cr H-1 · vr M-2 — 해당 없음, fail-closed) CLI 경로.** 포함 검사는 URI 문자열 재유도가 아니라 **해석된 `Path`** 로; 이미 URI 인 입력은 `url2pathname` 으로 복원; `file:<상대>` 도 변환 통지. 공백·한글 경로 test(판독기는 out_scope 라 그 경로의 성공은 요구하지 않는다 — **`OPEN-6G2E-SNAPSHOT-READER-URI-DECODE`** 신설, 6G-2c). 변이: 문자열 재유도로 되돌림 → 공백 경로 판 RED |
| **D-6G2e-17** | **(cr M-3 · vr M-1 — 해당 없음) 복구 순서**: 읽기 전용인 접두 대조·표본 해시 대조를 `healTornTail` **앞**으로(크래시 사례 답 불변 — 찢어진 끝 줄은 접두 대조에서 제외하고 센다). 거부될 디렉터리의 원장 바이트 불변 test(접두 변조 + 찢어진 꼬리 → 거부 · 바이트 동일). 변이 → RED. 불가하면 이탈로 사유 |
| **D-6G2e-18** | **장부**: runbook §5 의 「31일 상한 차단」 줄 제거(L-1/cr M-4) · D-11 에 D-5 의 실제 순서 선언 · 알려진 제한 4 정정 · evidence 두 축 갱신. **r2 는 표적**: D-15 여섯 판+변이 · D-16 공백·한글 경로 넷 + 변이 · D-17 변이 · acceptance 두 job(container 는 D-17 이 실행 상태 코드라 한 번 더) · rollback 두 축 재실측(runbook 순서) · 새 public 표면 0 |

## 계약 갱신 r2-b (2026-10-02, 팀장 — 수정 라운드 1 두 레인 보고 수령)

| ID | 결정 |
|---|---|
| **D-6G2e-19** | **수용(Kotlin)**: D-15 계수 정본 = 기초금액 술어(Y 셈 · N 안 셈 · 부재·미지만 A 행 입력), 품질관리비는 항상 입력 — 판 일곱 한 표, 변이 셋 각자 다른 판 RED · D-17 복구 앞 넷 읽기만(찢어진 끝 줄은 접두 대조 제외·줄 수 포함), 변이 RED · 크기 게이트로 test 파일 둘을 재는 물음으로 분할(`gate-tests` 등재 1) · **container job 미실행 이탈 수용** — compose 가 수집 변수를 두지 않고 실행 상태 배선 셋이 `mode=once` 조건부라 그 job 은 `RunStateDirectory` 를 적재하지 않는다(verifier 가 r2 에서 한 번 더 돌린다, 결과는 참고) · rollback 역적용 순서 둘 추가(같은 파일의 자기 커밋은 새 것부터 · `config/quality` hunk 는 ④⑤⑥ 앞) |
| **D-6G2e-20** | **수용(Python)**: D-16 — 포함 검사는 `_snapshot_dir` 가 돌려준 해석된 `Path` · URI 입력 `url2pathname` · 상대 `file:` 절대화+통지 · test helper 도 같은 디코딩 결함이라 함께 정정 · 변이 RED 2 · **`OPEN-6G2E-SNAPSHOT-READER-URI-DECODE`**(6G-2c, 공백·한글 경로는 끝까지 못 읽음, 거부는 판독기와 무관하게 성립) · Python-only 되돌림 절차의 앵커를 §5 삭제에 맞춰 재앵커(+ 부활 0 확인), 전체 역적용 순서 `a67a9162 → dea3e6da → 2e02b35f` |
| **D-6G2e-21** | **사실 선언(두 번째 혼입)**: Kotlin evidence 커밋 `0f7e3e16` 이 Python 의 미커밋 r2 evidence 편집을 **선언 없이** 품었다(첫 번째 `caccd807` 은 선언). 되쓰지 않는다 — Python `9c274fc0` 이 두 흡수 커밋과 저작 이력 자리를 기록. 교훈: 공유 evidence 파일은 레인마다 **별 파일**(`commands-kotlin.md`/`commands-python.md`)로 두는 편이 혼입을 구조로 막는다 → 다음 두 레인 slice(6G-2c)부터 적용(하네스 메모리) |
| **D-6G2e-22** | **r2 판정 SHA 는 이 갱신 커밋.** verifier 표적: D-15 판 일곱 + 변이 셋 · D-17 변조+찢어진 꼬리 → 거부·바이트 동일 + 변이 · D-16 공백·한글 경로 넷 + 변이 · acceptance `check` 넷(2,592) + `ml-engine`(1,377) + container(참고) · rollback 두 축 문서 절차 그대로(순서 셋: 두 레인 Kotlin → Python, 자기 커밋 새 것부터, `config/quality` 먼저; 유효성 술어 `5fddfd1a..판정 SHA` · `a67a9162..판정 SHA`) · 새 public 표면 0 · 형식 version 2 · 판정 경로 diff 0. code-reviewer: `8b39b4c4..판정 SHA` |

## 계약 갱신 r3 (2026-10-02, 팀장 — verifier r2 **ready-for-review** L 2 · code-reviewer r2 M 1 · L 5 수령)

판정 SHA `71ffd202`. r1 의 high·medium 전부 실행으로 닫힘(D-15 열일곱 판 · D-17 변조 셋 거부·바이트 동일·중간 파일 0 · D-16 넷 + 변이 RED 2 · acceptance `check` 2,592 · `ml-engine` 1,377 · container S-21~25 · rollback 두 축 문서 절차 그대로, 복원 목록 19 == 19). **승인 전 일괄 하나**(코드 거동 최소) 뒤 종결. 재작업 1/5 불변.

| ID | 결정 |
|---|---|
| **D-6G2e-23** | **승인 전 일괄 — Python**: ① (cr MR2-1, 게이트 하드닝) `urllib.request.url2pathname` 은 app 층에 HTTP 모듈을 들인다(import-linter 열거 계약을 지나감) → `urllib.parse.unquote` 로(Windows 문면 제거) ② (vr L-r2-1) `%`+hex 이름의 실제 디렉터리를 URI 로 넘기면 guard 는 디코딩 경로를, 판독기는 리터럴 경로를 봐서 스냅숏 안 출력이 **통과**한다 — 판독기 OPEN 이 닫힐 때까지 **guard 는 디코딩 경로와 리터럴 경로 둘 다** 스냅숏 안인지 본다(어느 쪽이든 안이면 거부). test: `a%41b` 디렉터리 + URI + 안쪽 출력 → 거부. 변이(한쪽만) → RED. **Kotlin**: ③ (cr L) 접두 대조가 「마지막 줄」 두 뜻을 섞어 공백만 남은 찢어진 꼬리를 거짓 거부할 수 있음 → 한 뜻으로, test ④ (cr L) `carriesAValueInput` 이 파싱된 금액을 읽어 A 행 금액이 판독 불가일 때 fallback 이 없음 → 원문 존재로, test ⑤ 죽은 코드(`linesOf`·`lineCountOf`) 제거 · 재동기가 `LedgerDigest.lines` 를 두고 원장 전체를 다시 읽는 중복 제거 ⑥ 장부: rollback Kotlin 절 제목에 D-15/17/18 · 「변이 셋이 각자 다른 판」 → 「실패 집합이 다름」(vr L-r2-2) · evidence 트리 동일성 확인 범위를 `9c274fc0` 까지 명기(Python). **공유 evidence 혼입 방지**: 이번 일괄부터 evidence 커밋 전 `git diff -- reports/evidence/m6/6g2e/` 를 읽고 **자기 hunk 만** `git add -p` — 다른 레인 줄이 보이면 선언 |
| **D-6G2e-24** | **종결 절차**: 일괄 커밋 뒤 verifier 표적 확인(①② 변이 · ③④ test · acceptance 두 job · rollback 두 축 재실측 · evidence 정직성) → 팀장 종결 문단(별 커밋) → verifier 가 그 커밋에서 rollback 절차 재실행 → rollback 실측 HEAD 갱신(별 커밋) → push · PR · 코멘트(r1·r2·종결 확인) · `/code-review` · 처분 · CI 초록이면 머지(사용자 사전 승인). 수렴 규칙(6G-2d D-50 과 같음): `/code-review` 에 새 high 가 없으면 나머지는 등재 |

## 계약 갱신 r4 (2026-10-02, 팀장 — PR #54 `/code-review` 9건 수령 · D-24 수렴 규칙 발동)

새 high 없음 → 코드 변경 0, 등재. 리뷰 에이전트가 공유 main 체크아웃에서 PR head 를 잠시 checkout 했다가 복원한 사실(worktree 아닌 main)을 기록한다 — 흔적 없음 확인.

| ID | 결정 |
|---|---|
| **D-6G2e-25** | **`OPEN-6G-REVIEW-FOLLOWUPS` 추가(6G-2c)**: ① `verifyThenHeal` 이 중단된 확정의 되돌림(표본 파일 둘 삭제)을 읽기 전용 접두 대조 **앞**에 돌려, 접두 변조로 거부될 디렉터리의 표본 파일이 먼저 지워진다 — 순서를 접두 대조 → 되돌림으로(한 줄). 발생 조건은 「확정 중단 + 접두 변조」라 정직한 운영에서 드묾 ② 접두 대조가 찢어진 조각을 줄 수에 더해 **진짜 절단**(장부가 센 줄의 꼬리 손실)이 「변조」로 진단된다 — 둘 다 거부지만 메시지가 틀림 ⑦ Busy 경로에서도 `LedgerDigest` 를 지어 원장이 판독 불가면 Busy 대신 예외 — Held 에서만 짓기 ⑧ `SnapshotAValueContractTest`(·`SnapshotAmountContractTest`) 미등재 — snapshot 패키지에 양방향 등재 test 없음 ③ `_APPROVED_SEED_KEYS` 두 로더에 중복(열거를 실제 키 집합과 비교하지 않음) — 공용 자리로 ④ CLI 가 기존 `verdict.json` 을 조용히 덮어씀 — 거부 또는 명시 플래그(runbook 은 스냅숏별 판정 디렉터리라 실수집에서는 안 겹침) ⑤⑥ 기동 시 `state.json` 두 번·원장 세 번 읽기 |
| **D-6G2e-26** | **문면 정정(이 커밋)**: D-11 ① 「walk = 조각 시각」 → 「의도 줄이라 `walk = null`, 조각 시각은 `at`」(리뷰 ⑨). **머지**: 사용자 사전 승인 + D-24. 머지 뒤 실수집 시작 조건 전부 충족(키 = 개발 키) |

## 위협 모델 — 6G-2e 고유 경계 (Phase 2.5 (0))

**지키는 것**: ① 공고 목록 갈래의 31일 상한은 그대로(호출 폭주 방지) ② 개찰 갈래의 긴 창은 표본 크기·호출 상한이 묶는다 ③ 실행 상태 회계(크래시 라운드 하나 · 거부될 디렉터리 불변 · 잠금 잔류 없음) ④ CLI 는 판정 경로를 바꾸지 않는다.
**경계 밖**: 게이트 하드닝(2a·2b) · fsync 묶기 · 이중 파싱 · 반사실.

### (2) 우회 — 다섯
1. 긴 정책을 공고 목록 갈래에도 꽂는다 ← 갈래별 정책 인스턴스, 변이 RED. 2. `maxSpanDays` 리터럴을 배선에 적는다 ← 정책 데이터 + 리터럴 게이트. 3. 창을 쪼개 표본을 두 번 뽑는다 ← D-2 거부 유지 test. 4. torn 표식을 세지 않고 `read()` 에서 버린다 ← D-3 변이. 5. CLI 가 출력 디렉터리를 스냅숏 안에 둔다 ← D-6 거부 test.

### (2b) 값 획득 축
새 public: 정책 인스턴스 하나(값 읽기만) · CLI 진입점(판정 함수 호출만). 그 밖 0 기대.

## 운영자 승인 필요 (착수 전)
- **A-1 개찰 갈래 `maxSpanDays`**: **120일 확정(운영자 2026-10-02)** — 16주 + 여유 8일; 그 이상은 기동 거부(실수 방지), 호출 수는 상한이 묶는다.
- **A-2 착수 시점**: 6G-2a 머지 직후(결정됨 2026-10-01).

## in_scope
- `workflow/src/main/kotlin/bidvector/workflow/collection/**`(범위 정책·`interruptedRounds`) · `workflow/src/test/kotlin/bidvector/workflow/collection/**`
- `app/src/main/kotlin/bidvector/app/wiring/**`(개찰·추출 배선의 정책 선택) · `app/src/main/kotlin/bidvector/app/collection/**` · `app/src/test/**`
- `adapters/src/main/kotlin/bidvector/adapters/snapshot/**`(★ 셋) · `adapters/src/test/**` · `procurement/src/main/kotlin/bidvector/procurement/**`(필요 시) · `procurement/src/test/**`
- `config/quality/**`(등재만) · `ml-engine/src/ml_engine/app/**`(CLI 파일 하나) · `ml-engine/tests/app/**` · `ml-engine/pyproject.toml`(스크립트 등재 시)
- **(r0-b D-6G2e-9)** `ml-engine/src/ml_engine/evaluation/backtest/policy.py` · `ml-engine/src/ml_engine/evaluation/policy.py`(정책 키 스키마의 seed 길이만) · `ml-engine/tests/evaluation/test_evaluation_policy.py`
- `docs/runbook/m6-6g-real-collection.md` · `reports/evidence/m6/6g2e/**` · `milestone-6.md`(문단만)
**out_scope**: `ml-engine/src/ml_engine/evaluation/**` · 정책 YAML 값 · 스키마·golden · koneps transport · `reports/evidence/m6/6g*/**`(이전 slice).

## acceptance
CI `check` job + `ml-engine` job 명령 그대로; 실행 상태 E2E 가 container job 에 있으면 그것도. 변이표(D-1·3·4·5·6).

## rollback
in_scope 경로 한정 `git restore --source=<base> --staged --worktree --`; 공유 파일(`milestone-6.md` · `config/quality/*` · runbook)은 hunk 격리; 버릴 clone ①~⑥(Kotlin·Python 양 축). 복원 목록은 `git diff --name-only base..HEAD` 의 소스 전수 ⊆ 목록을 verifier 가 등식으로 확인(6G-2d 교훈).

## 리뷰 레인
`verifier` + `code-reviewer`(sonnet). Codex 없음. privacy-gate 불필요.

## 하네스 레인 변경
(착수 뒤)

## OPEN 수령·신설 (예상)
| OPEN | 처분 |
|---|---|
| `OPEN-6G-OPENING-RANGE-CAP` | **이 slice 가 닫는다**(D-1) |
| `OPEN-6G-REVIEW-FOLLOWUPS` ★ 셋 | **이 slice 가 닫는다**(D-3·4·5) — 나머지 항목은 6G-2c 그대로 |
| `OPEN-6G-BACKTEST-CLI` | **이 slice 가 닫는다**(D-6) |
| `OPEN-6G2A-SEED-COUNT-NOT-PINNED` | **이 slice 가 닫는다**(D-6b) |
