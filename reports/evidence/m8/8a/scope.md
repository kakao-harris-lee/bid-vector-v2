# M8/8A — 6G 수집 상태 확정 · 계약 (2026-11-03)

- base: main `3706d4fe`(PR #117 = M8 승인) · 브랜치 `m8-8a/2026-11-03`
- 근거: `milestone-8.md` slice 표 8A 와 완료 조건 첫 줄. 사용자 승인 = PR #117 머지(2026-11-03).
- 성격: **읽기·문서 slice** — 외부 effect 0. 새 KONEPS 호출 0 · 수집 실행 0 · DB 쓰기 0 · cron 변경 0.

## 물을 것 (완료 조건 = 아래 넷 각각에 수치와 근거)

1. **멈춘 원인**: day28(2026-10-29)부터 `budgetLimit=TOTAL`. 이것이 원장의 `calls-total`(실행기 인자 80,000) 소진인지,
   다른 TOTAL 축인지를 실행기 코드와 원장 행으로 확정한다. day20~27 의 `QuotaExhausted` 는 일 한도(`calls-per-day`)인지 포털
   한도인지도 같이.
2. **확보 표본**: dev DB(컨테이너 `bid-vector-v2-dev`)에 적재된 개찰 수집분을 **읽기 전용**으로 센다 — 공고 수, 상세(개찰결과)
   확보 수, 업무(공사·용역)별, 공고일 창별, 계획 T2 의 「공고당 필요 축」 채움률(예비가격 15 · 추첨 번호 4 · 전체 투찰자 행 ·
   A·순공사원가 등). 계획의 최소 표본(창당 483 · 최소 3창)과 대조해 **충분/부족**을 숫자로 적는다.
3. **기록 공백**: git 기록은 day15 에서 멈췄다. day16~32 를 host 로그(`~/.local/bid-vector-logs/cron-6g.log` · `m6-6g/*.log`)에서
   옮겨 `reports/evidence/m6/6g/commands.md` 의 같은 표 형식으로 메운다(START/END·exit·frame·sampled·requested·상세 수·
   halted 사유). 로그가 없는 날(day17 등)과 1초 종료일(day23·24)은 그대로 「없음」으로 적는다.
4. **기록 브랜치 처분**: `origin/m6-6g-collection/2026-10-02`(main 에 없는 커밋 7, day6~15 기록)를 이 브랜치로 병합하거나
   cherry-pick 해 main 의 `commands.md` 에 싣는다. 충돌은 합집합으로 풀고 행 수를 센다.

cron 처분은 이미 정해졌다 — 운영자가 2026-11-02 주석 처리했다(crontab 백업 존재). 이 slice 는 그 사실만 적는다.

## in_scope

- `reports/evidence/m8/8a/**`(scope · checklist)
- `reports/evidence/m6/6g/commands.md`(day6~32 기록 — 기록 브랜치 커밋 + host 로그 이관)
- `milestone-8.md` 진행 한 줄(8A 종결 문단)

## out_scope

- 코드 · 정책 · 실행기 · 스냅숏 생성(8C) · 판정(8E) · capability-map
- 새 호출 · 수집 재개 — 표본이 부족하면 **수치로 적고 멈춘다**(연장은 별도 승인)

## DB 읽기 규칙

- 읽기 전용 질의만(`SELECT`, 트랜잭션 `READ ONLY`). 자격은 컨테이너 env 에서 읽되 **evidence·로그·채팅에 쓰지 않는다**.
- 결과는 **집계만** 적는다 — 공고번호·상호·사업자번호·담당자 등 식별자 0. 누출 스캔 참조형 0.
- 실행 중인 수집기가 없을 때(`pgrep -af 'java -jar .*app.jar'` 빈 출력) 질의한다.

## acceptance

- 문서 slice: CI 4 job 초록(PR head).
- `git diff --name-only 3706d4fe..HEAD` ⊆ in_scope.
- `command grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m8/8a reports/evidence/m6/6g/commands.md` — 새 줄 0.
- 판정: verifier `ready-for-review` + code-reviewer(sonnet) 병렬 → 사용자 승인.

## rollback

`git restore --source=3706d4fe --staged --worktree -- reports/evidence/m6/6g/commands.md milestone-8.md` + `reports/evidence/m8/8a/` 삭제.

## 하네스 레인 변경

없음.
