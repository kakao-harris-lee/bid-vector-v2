# M15 / 15U — 운영 공고 수집 (공고 목록 갈래 일회 러너 래퍼와 절차)

```yaml
milestone: M15
slice: 15u-prod-collection
base_sha: 4f91faf59fb6b34f347e22369a3a4bfb27242101   # main, PR #207 머지 커밋
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - tools/prod-collection.sh              # 신설 — 공고 목록 갈래 일회 러너 래퍼
  - tools/prod-env.sh                     # 둘째 env 파일(키 집합 닫힘) 술어를 relay 와 함께 쓰게 뽑는다면 여기
  - tools/prod-notification-relay.sh      # 위 술어를 뽑을 때만(거동 무변경 — 가드 단언 계수로 증명)
  - tools/prod-guards-check.sh            # 새 래퍼의 거동 단언
  - docs/runbook/m7-7f-production.md      # §14 신설 — 수집 unit·timer·경보·활성화 단계·첫 run 측정
  - docs/runbook/m6-6e-operations.md      # §3.3 — 래퍼가 러너의 종료 코드를 옮긴다는 주(옮길 때만)
  - config/quality/runbook-prod-path-lines.txt   # runbook 의 운영 경로 줄 등재(그 게이트가 요구하면)
  - docs/discovery/capability-map.md      # OPEN-6F10-SCHEDULER 행 갱신 · 신설 OPEN 행
  - milestone-15.md                       # 15U 한 문단
  - reports/evidence/m15/15u/
out_of_scope:
  - 러너 코드(`app/**`·`workflow/**`·`adapters/**`) — 바꾸지 않는다. 바꾸는 것은 거는 법뿐이다.
  - 개찰 갈래(`opening-collection`)·낙찰 갈래(`award-collection`) — 표본·캠페인 형태라 주기 운영에
    맞는 모양이 아니다. 평가 후보는 `notice` 만 읽는다(아래 「조사」 1).
  - /etc · systemd 의 실제 설치, 조달청 실호출, 운영 DB 쓰기 — slice 안에서는 **하나도 하지 않는다**.
    로컬 실측은 가짜 `docker`·`git` 앞에서 돈다(15Q 와 같다). 머지·release 반입 뒤의 첫 실 run 은
    운영자 승인(2026-11-27 「운영 수집 진행해」) 아래 §14 의 단계로 따로 한다.
  - `/etc/bidvector-v2/*.env` 의 값 — 읽지 않는다.
  - 후보 상한(`BIDVECTOR_APP_CANDIDATE_CAP`)의 새 값 — **수가 없으면 정하지 않는다**(운영자 결정
    2026-11-27: 실측 후 조정). 이 slice 는 「무엇을 어디서 재는가」까지다.
  - 평가 후보 질의를 좁히는 것 · 수집 단계 필터 — 운영자가 고르지 않았다.
acceptance_commands:
  - ./tools/prod-guards-check.sh          # CI `container` job 의 그 step 축어(전제: 잠금 자리 · 진짜 docker CLI)
  - bash -n tools/prod-collection.sh tools/prod-env.sh tools/prod-notification-relay.sh tools/prod-guards-check.sh
  - ./gradlew check                       # CI `check` job — evidence·runbook 을 훑는 게이트(leak·runbook 줄)가 여기 있다
  # (CI) container job 전건 — 판정은 PR head 의 CI.
rollback: reports/evidence/m15/15u/rollback.md
```

## 무엇을 닫는가

운영 DB 에 `notice` 가 0 건이다(2026-11-27 읽기 전용 조회 — `collection_run` 0 · `notice` 0). 평가 커밋과
relay 타이머는 매일 빈 채로 돈다. 저장소에 운영 수집을 거는 진입점이 없다 — `tools/prod-*.sh` 에 수집
래퍼가 없고 운영 runbook 에 수집 unit 절이 없다. 이 slice 가 그 진입점과 절차를 만든다.

## 조사 (2026-11-27, main `4f91faf59`)

1. **평가 후보는 `notice` 만 읽는다** — `Sql.SELECT_OPEN_CANDIDATES` 의 조건은 「입찰 가능 상태 · 마감 일시가
   지금보다 뒤」 둘뿐이다. 개찰·낙찰 갈래의 표는 후보 선정에 쓰이지 않는다. 그래서 공고 목록 갈래 하나로 족하다.
2. **후보가 상한을 넘으면 run 이 실패한다** — `JdbcCandidateSource.openCandidates` 는 상한+1 을 읽어 넘으면
   `CandidateCapExceededException` 을 던진다(건너뜀이 아니다). 운영 값은 1000 이고 질의에 지역·업종 조건이 없다.
   전국 공사+용역 공고가 하루 수백~수천 건이므로(6G 표본틀 113일 65,064 행이 하한 근사 — 운영 실측 전)
   **수집을 켜면 하루 이틀 안에 평가가 붉어진다.** 운영자 결정: 실측 후 상한 조정(D-15U-6).
3. **호출 상한 `calls-total` 은 실행 상태 디렉터리의 생애 누적이다** — `AttemptHistory.spend` 가 시도 원장
   전체의 줄을 센다. 끝없이 도는 운영에서 한 디렉터리를 쓰면 언젠가 닿고, 그 뒤 매 run 이 `INCOMPLETE(2)` 다.
   원장은 매 기동 전량을 읽는다.
4. **같은 창의 재수집은 `notice` 에는 멱등, `raw_observation` 에는 누적이다** — 관측 키에 관측 시각이 들어가
   재수집마다 새 행이 선다. `collection_run` 도 슬롯마다 한 행씩 는다. 창이 넓을수록 매일 그만큼 쌓인다.
5. **재호출 상한·영구 정착은 이 갈래에 없다** — 어느 날의 실패가 다음 날 같은 슬롯을 막지 않는다.
6. 러너 종료 코드는 다섯(0 COMPLETE · 1 FAILED · 2 INCOMPLETE · 3 ALREADY_RUNNING · 4 UNLOCKABLE)이고
   **래퍼의 3(전제 실패)·4(미룸)와 겹친다.**
7. 조회 범위: `to` 는 오늘(KST)까지 되고 창은 31일 이하다. 슬롯 = 날짜 × 업종, 쪽 100 행.
8. app 이미지의 사용자는 uid/gid 10001 이고 일회 컨테이너에 쓰기 가능한 볼륨이 없다 — 실행 상태 디렉터리를
   host 에서 붙여야 한다. 서비스 키는 `/etc/bidvector-v2/koneps.env` 의 한 키다(compose 템플릿 밖).

위 가운데 2·3·4 의 **수**(하루 공고 수 · 호출 수 · 행 증가)는 운영 실측 전이다. 구현 레인은 1~8 의 코드
사실을 자기 눈으로 다시 확인하고, 어긋나면 계약 갱신 절에 적은 뒤 진행한다.

## 결정

- **D-15U-1 범위 = 공고 목록 갈래 하나.** 조사 1.
- **D-15U-2 래퍼의 모양은 `tools/prod-evaluation-commit.sh` 와 같다** — 같은 compose 파일 셋 · 프로젝트
  이름 · release 태그는 `<state>/current` · `--pull never` · 잠금 둘(같은 순서) · manifest 이미지 ID ·
  반입 트리 == release 커밋 · 설정 통로 · 일회 컨테이너 잔존·이름 충돌(`<프로젝트>-app-run-collection`) ·
  신호 trap · 금지 JVM 옵션 · 값 없는 플래그 = 3. **반입이 막는 것을 이 러너만 통과시키는 길을 만들지 않는다.**
- **D-15U-3 서비스 키는 둘째 env 파일로, 키 집합을 닫는다.** `--koneps-env-file`. 통로는 relay 가 실측으로
  세운 `run --env-from-file` 이고 가드도 그 것을 그대로 진다: 허용 키는 `bidvector.koneps.service-key` 가
  받는 환경 변수 이름뿐(손 목록이 아니라 relay 와 같은 방식으로 도출) · 밖의 키는 거절 · other 에 열린
  권한 거절 · 값의 `$` 거절 · 경로의 보간 거절 · JVM 옵션 축. 술어를 relay 와 **공유**하는 쪽을 먼저
  검토한다(사본 둘은 한쪽만 고쳐지는 날이 온다). 공유하면 relay 의 거동이 바뀌지 않았음을 가드 단언
  계수(앞 판과 같은 수 통과)로 보인다. **값은 argv·로그·오류 문면 어디에도 나오지 않는다.**
- **D-15U-4 실행 상태는 host 디렉터리를 붙이고, KST 달마다 가른다.** `--run-state-root <host 절대 경로>`
  아래 `notice-list/<YYYY-MM>` 을 그 run 의 실행 상태 디렉터리로 쓴다(컨테이너 안 경로는 고정). 사유는
  조사 3 — `calls-total` 이 **월 상한**이 되고 원장 크기가 달 단위로 묶인다. 러너를 고치지 않고 닫는 길이다.
  root 는 저장소 밖이어야 하고(래퍼가 `<저장소>` 안이면 거절), 없으면 **만들지 않고 거절**한다(소유자
  10001 로 만드는 것은 운영자의 한 번 — §14). 달 디렉터리는 래퍼가 만든다. 붙이는 것은 그 한 자리뿐이고
  쓰기 가능이다. 이 붙임이 `_prod_config_surface_ok` 가 지키는 통로와 어떻게 만나는지는 설계 검토 (2b).
- **D-15U-5 창과 상한은 인자다 — 기본값을 지어내지 않는다.** 둘 중 하나를 요구한다:
  `--from <날짜> --to <날짜>`(첫 적재·메우기) 또는 `--days-back <N>`(주기: `to` = 오늘 KST, `from` = 오늘-N).
  `--calls-per-day`·`--calls-total` 은 필수다. `--categories` 는 필수다. 날짜·정수는 모양을 검사하고,
  범위(31일·미래)는 러너가 기동 실패로 드러내는 것을 그대로 둔다(같은 술어를 셸에 다시 적지 않는다).
  처방 unit 은 `--days-back 1 --categories construction,service` 이다 — 창이 하루 겹쳐 자정 무렵 공고와
  하루의 결손을 메우되 조사 4 의 누적을 2 배로 묶는다. **이틀 넘게 host 가 꺼져 있으면 구멍이 남는다**
  (`Persistent=true` 는 run 하나를 따라잡을 뿐 창을 넓히지 않는다) — 메우는 법은 `--from/--to` 손 실행이고
  §14 에 적는다. 알려진 제한으로 등재.
- **D-15U-6 타이머는 실측 뒤에 켠다(운영자 결정 2026-11-27).** §14 의 단계는 이 순서다:
  ① root 디렉터리·unit 파일 설치(타이머는 아직 enable 하지 않는다) ② **평가 커밋 타이머를 잠시 멈춘다**
  (수집 뒤 첫 평가가 상한 초과로 붉어져 경보가 가는 것을 막는다 — 멈춤과 되켬을 한 쌍으로 적는다)
  ③ 하루 창 · 작은 `--calls-per-day` 로 손 실행 ④ 읽기 전용 질의로 재는 것: `notice` 행 수 · **마감 전
  후보 수**(평가 질의와 같은 조건) · 호출 수(마침 줄·원장) · `raw_observation` 증가 ⑤ 운영자가 그 수로
  후보 상한을 정해 `prod.env` 를 고친다(값은 이 문서가 정하지 않는다) ⑥ 평가 커밋을 손으로 한 번 돌려
  소요와 마침 줄의 계수를 적는다(`OPEN-M15-EVAL-COST-PER-ACCOUNT` ⓑⓒ 와 같은 표) ⑦ 타이머 enable ·
  평가 커밋 타이머 되켬. 측정 표의 칸은 비워 둔다.
- **D-15U-7 주기 = 07:10 · 13:10 KST 매 달력일, 훑기(07:30)보다 앞.** 평가 커밋(07:35)이 그 run 의 공고를
  본다. `Persistent=true` 따라잡기에서 넷이 함께 뜰 때의 순서는 `After=` 로 세운다 — 훑기·평가 커밋 unit
  처방에 `After=bidvector-collection.service` 를 더한다(relay 의 그 줄과 같은 뜻). 잠금 경합의 미룸은
  이 래퍼도 `4` 이고 `SuccessExitStatus=4` drop-in 을 처방한다. `TimeoutStartSec` 을 둔다(러너는 도는 내내
  잠금을 쥔다 — 값은 레인이 호출 수 × 간격으로 근거를 대고 고른다). `ExecStopPost` 조건은 §9.2 와 같다.
- **D-15U-8 종료 코드는 겹치지 않게 옮긴다.** 래퍼의 3(전제)·4(미룸)는 다른 래퍼들과 같은 뜻으로 둔다.
  러너의 3·4 는 relay 가 한 것처럼 다른 수로 옮기고(선례의 수와 겹치지 않게), 모르는 값도 한 수로 모은다.
  경보에서 빼는 것은 「기다리면 풀리는 것」뿐이다: 래퍼의 미룸. **`INCOMPLETE(2)` 는 빼지 않는다** —
  일 상한·쿼터·절단으로 그날 공고가 덜 들어온 것이고 사람이 알아야 한다. `UNLOCKABLE` 도 빼지 않는다.
  `ALREADY_RUNNING` 은 래퍼의 잠금 둘 아래에서는 손 실행이 같은 디렉터리를 쥔 경우뿐이다 — 뺄지 말지를
  레인이 근거와 함께 정한다.

## 위협 모델 경계

**방어한다**: 운영자가 unit·env 를 손으로 고치다 내는 실수(값 없는 플래그 · 틀린 날짜 모양 · 템플릿 밖
키 · 권한이 열린 키 파일 · 저장소 안을 가리키는 실행 상태 경로) · 반입 중·되돌림 중·다른 러너와의 겹침 ·
release 와 다른 트리에서의 실행 · 서비스 키가 argv·로그·오류 문면·compose 보간으로 새는 길 ·
일회 컨테이너 잔존.

**방어하지 않는다**: `<저장소>` 에 쓸 수 있는 사람(§13.3 의 문면 그대로 — 래퍼를 고치면 키가 나간다,
통제는 §1.4b) · root · 조달청 응답의 내용 · 러너가 이미 지는 것(범위·쿼터·호출 상한 — 러너의 술어를
셸에 사본으로 두지 않는다) · host 디스크 고갈(원장과 `raw_observation` 의 증가는 재는 자리만 둔다).

이 경계는 15Q·15R 이 relay 래퍼에 세운 것과 같고 요구의 축소가 아니다.

## 설계 검토

**(1) 열거인가 구성인가.** 키 집합은 relay 와 같이 **속성 이름에서 도출**한다(손 목록이 아니다). 인자
검사는 「받는 꼴 + 나머지 정지」다. 가드 단언은 문면 grep 이 아니라 **가짜 `docker` 가 받은 argv** 로 잰다
(15Q 의 shim) — 창·상한·모드·붙임·env 파일이 실제 호출에 실렸는가.

**(2) 우회 — 레인은 각각을 가드 단언으로 닫거나 경계로 등재한다.**
1. `--from` 만 주고 `--to` 를 빼거나, `--days-back` 과 `--from` 을 함께 준다 → 거절(조용히 하나를 고르지 않는다).
2. `--days-back 0`·음수·`031`·공백 · `--from 2026-13-40` → 모양 거절. `--days-back` 이 31 을 넘는 것은 러너 몫.
3. `--run-state-root` 가 상대 경로 · 저장소 안 · 심링크로 저장소 안 · 없는 경로 · 파일 → 거절.
4. `koneps.env` 에 `JAVA_TOOL_OPTIONS` 나 `SPRING_*`·`BIDVECTOR_PERSISTENCE_*` 를 끼운다 → 키 집합 밖 거절.
5. `--categories 'construction --bidvector.collection.calls-total=999999'` 같은 인자 주입 → 값 모양 거절
   (소문자·쉼표만). 러너 인자는 배열로 넘기고 낱말 쪼개짐에 기대지 않는다.
6. 달이 바뀌는 자정에 걸친 run → 달 디렉터리는 run 시작 때 **한 번** 정한다(창 계산과 같은 「오늘」).
   KST 로 계산하고 host 의 TZ 에 기대지 않는다.
7. 평가 커밋·relay·훑기와 동시에 → 잠금에서 미룸(4), 일회 컨테이너를 띄우지 않는다.
8. 러너가 3(`ALREADY_RUNNING`)을 내는데 래퍼가 그대로 돌려줘 「전제 실패」로 읽힌다 → D-15U-8.

**(2b) 값 획득 축 — 이 slice 가 새로 여는 표면.**

| 새 표면 | 밖에 허락하는 것 | 처분 |
|---|---|---|
| `--koneps-env-file` → 컨테이너 env | 그 파일을 쓰는 사람이 컨테이너 env 를 정한다 | 키 집합 닫힘(D-15U-3). **실측한다** — 밖의 키 하나를 넣은 파일이 거절되고 가짜 docker 가 불리지 않는다 |
| host 디렉터리 붙임 | 컨테이너가 host 의 그 자리에 쓴다 · 래퍼가 `run -v` 를 쓴다 | 붙이는 원천은 인자에서 온 root 아래 **래퍼가 지은 한 경로**뿐. 인자가 붙임 문법(`:`)을 실어 둘째 붙임·옵션(`:ro` 뒤집기)을 만들지 못함을 **실측한다**(root 에 `:` 가 있으면 거절). `_prod_config_surface_ok` 가 보는 것은 `config` 출력이라 `run -v` 를 보지 못한다 — 그 사실을 문면으로 적고 「경계로 처리」가 아니라 위 거절로 닫는다 |
| `--from/--to/--days-back/--calls-*/--categories` → 러너 인자 | 운영자가 호출량·창을 정한다 | 의도된 권한. 모양 검사 + 주입 거절(우회 5) |
| 달 디렉터리 생성 | 래퍼가 root 아래에 디렉터리를 만든다 | root 밖을 만들지 못함을 실측(달 문자열은 래퍼가 `date` 로 짓고 인자에서 오지 않는다) |

**(3) 과잉·미달.** 수집 창 술어를 셸에 다시 적지 않는다(과잉) · 스케줄러를 앱에 넣지 않는다
(`OPEN-6F10-SCHEDULER` 는 외부 타이머로 답한다) · 후보 상한을 정하지 않는다. 미달이 되는 자리는 「래퍼는
있는데 runbook 의 측정 단계가 없다」다 — D-15U-6 이 산출물이다.

## 구현 레인이 정하고 근거를 적을 것

- `TimeoutStartSec` 값 · 러너 3·4 를 옮길 수 · `ALREADY_RUNNING` 의 경보 처분 · 컨테이너 안 실행 상태 경로 ·
  D-15U-3 술어를 `prod-env.sh` 로 뽑을지(뽑지 않으면 사유).
- 처방 unit 의 `--calls-per-day`·`--calls-total` — **지어내지 않는다.** 포털 한도(키 × operation × 일
  1,000 — m6-6g §5)에서 내려오는 근거가 있는 값이면 적고, 없으면 자리표시자(`<…>`)로 두고 §14 ④ 의 실측이
  채우게 한다.
- runbook 에 넣는 읽기 전용 질의는 운영 DB 에 돌리지 않는다(문면만). 후보 수 질의는
  `Sql.SELECT_OPEN_CANDIDATES` 의 조건을 옮기되 상태 어휘를 코드에서 확인한다.

## 알려진 제한 (착수 시점 — 레인이 실측으로 갱신)

1. 이틀 넘는 정지는 구멍을 남긴다(D-15U-5). 구멍을 **재는** 자리가 없다 — `collection_run` 의 날짜 덮개를
   세는 질의를 §14 에 둔다.
2. `status` 는 재수집으로 갱신되지 않는다(취소 공고가 후보로 남는다) — 러너의 기존 거동이고 이 slice 밖.
3. `raw_observation`·`collection_run` 은 run 마다 는다 — 보존 러너가 이 표를 다루는지는 레인이 확인해 적는다.
4. 달 경계에서 일 상한 회계가 새 디렉터리로 넘어가 그날의 소비가 0 에서 다시 선다(월 1회, 최대 하루치).
5. 후보 상한 초과는 수집이 켜진 뒤의 상시 위험이다 — 상한을 올려도 공고가 그 위로 쌓이면 다시 붉다.
   근본 처방(후보 질의를 좁히기)은 별 slice 후보로 OPEN 에 올린다.
6. 평가는 ML 없이 `Review(MlUnavailable)` 로 판정한다(`EvaluationWiring` — 현행) — 이 slice 와 무관한 현행 사실.

## 하네스 레인 변경

없음(착수 시점).

## 계약 갱신

없음(착수 시점).
