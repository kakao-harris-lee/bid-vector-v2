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
  - config/quality/runbook-prod-path-lines.txt   # runbook 의 운영 경로 줄 등재(그 게이트가 요구했다 — 추가 15 · 삭제 0)
  - config/quality/runbook-koneps-env-lines.txt  # 신설(계약 갱신 ③) — 자격 파일이 둘이 됐다. S-2 와 같은 처방
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

**(2b) 값 획득 축 — 이 slice 가 새로 여는 표면.** (실측 열은 구현 라운드에서 채웠다 — 단언의 자리는
`tools/prod-guards-check.sh` 의 「수집 러너」 절이고 전부 **가짜 docker 가 받은 argv** 로 잰다.)

| 새 표면 | 밖에 허락하는 것 | 처분 | 실측 |
|---|---|---|---|
| `--koneps-env-file` → 컨테이너 env | 그 파일을 쓰는 사람이 컨테이너 env 를 정한다 | 키 집합 닫힘(D-15U-3). 허용 이름은 `bidvector.koneps.service-key` 가 받는 둘뿐이고 **`base-url`·`operations.*` 도 밖이다**(그 둘은 compose 축이라, 여기 들어오면 반입 가드를 거치지 않고 조회 주소를 바꾼다) | **닫힘** — 밖의 키 여섯 꼴(`JAVA_TOOL_OPTIONS` · `BIDVECTOR_PERSISTENCE_JDBCURL` · 따옴표 뒤 둘째 대입 · 값의 `${VAR}` · 안 닫힌 따옴표 · `BIDVECTOR_KONEPS_BASE_URL`)이 `exit 3` 이고 **가짜 docker 의 ` run --rm` 이 0 줄**이다. 값(`fixture`)이 어느 출력에도 없음도 함께 센다 |
| host 디렉터리 붙임 | 컨테이너가 host 의 그 자리에 쓴다 · 래퍼가 `run -v` 를 쓴다 | 붙이는 원천은 인자에서 온 root 아래 **래퍼가 지은 한 경로**뿐. `_prod_config_surface_ok` 가 보는 것은 `config` 출력이라 `run -v` 를 보지 못한다 — 그 사실을 래퍼 머리말에 적고 「경계로 처리」가 아니라 인자 쪽 거절로 닫는다 | **닫힘** — root 가 `:` 를 담거나(실경로에도 다시 본다) 상대 경로·없는 경로·파일·**저장소 안**·**심링크로 저장소 안**이면 `exit 3` 이다. 양성 run 에서 **` -v ` 인자가 정확히 하나**이고 그 값이 `<달 디렉터리>:/var/lib/bidvector-run-state` 임을 argv 로 센다(둘째 붙임이 생기면 그 계수가 선다) |
| `--from/--to/--days-back/--calls-*/--categories` → 러너 인자 | 운영자가 호출량·창을 정한다 | 의도된 권한. 모양 검사 + 주입 거절(우회 5) | **닫힘** — 창 넷 꼴(없음 · `--from` 만 · `--to` 만 · 둘 함께) · 날짜 꼴 둘 · `--days-back` 넷 꼴(`0`·`-1`·`031`·공백) · 상한 셋 꼴 · 업종 셋 꼴(주입·대문자·빈 칸)이 `exit 3`. **반대쪽도 쟀다**: 31일 넘는 창은 래퍼가 막지 **않고** 그대로 러너에 간다(러너의 술어를 셸에 베껴 적지 않았다는 음성 대조) |
| 달 디렉터리 생성 | 래퍼가 root 아래에 디렉터리를 만든다 | root 밖을 만들지 못함을 실측(달 문자열은 래퍼가 `date` 로 짓고 인자에서 오지 않는다) | **닫힘** — 양성 run 뒤 root 아래에 생긴 것은 `notice-list` **하나**이고 root 밖(`$T`·`$T/state`)에는 아무것도 없다. 달 문자열은 창 계산과 **같은 KST 「오늘」**에서 나온다(`--days-back 1` 의 from·to 를 argv 로 대조) |
| **달 디렉터리의 권한**(신설 행 — 구현 라운드) | 컨테이너(uid/gid 10001)와 래퍼(`deploy`)가 **같은 자리에 쓴다** → group 공유가 전제다 | 새로 만드는 자리는 `mkdir -m 2770` 으로 모양을 보장하고, **이미 있는 자리**는 group rwx 열림 · other 닫힘을 **본 뒤** 돈다(러너의 쓰기 실패는 종료 1 이라 「예외」와 섞인다 — 래퍼가 3 으로 먼저 가른다) | **mode 축 닫힘** — 이미 있는 달 디렉터리가 `0700` 이면 `exit 3` 이고 `2770` 으로 고치면 같은 run 이 통과한다(양성·음성 쌍). **gid 축은 미측정** — 그 등식은 runbook §14.1 의 운영자 확인이고 사유는 측정의 한계다(`OPEN-M15-COLLECTION-RUN-STATE-GID`) |
| **공유 술어**(신설 행 — 구현 라운드) | `tools/prod-env.sh` 의 둘째 env 파일 검사가 **래퍼 둘의 유일한 문턱**이 됐다 — 그 함수가 헐거워지면 relay 와 수집이 **한꺼번에** 열린다 | 사본 둘을 두는 쪽이 「한쪽만 고쳐지는 날」을 만들므로 공유가 맞다(v2-지침서 §5). 대신 **두 래퍼의 단언이 같은 함수를 각자 지나게** 둔다 — relay 쪽 단언 집합이 그대로 통과하고 수집 쪽이 같은 꼴 여섯을 다시 잰다(한쪽만 재면 공유가 곧 단일 실패점이다) | **닫힘** — relay 단언 **130 건 전부 통과**(뽑기 전과 같은 집합. 그중 셋은 라벨에 `relay —` 접두가 붙었다) · 수집 쪽이 같은 축을 독립으로 여섯 꼴 잰다. 전건 269 → 428 |

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

## 알려진 제한 (레인이 실측으로 갱신 — 2026-11-27)

1. 이틀 넘는 정지는 구멍을 남긴다(D-15U-5). **구멍을 재는 자리를 만들었다** — runbook §14.5 ④ ⓔ 가
   `collection_run` 의 `reference_date` 덮개를 최근 30일에서 센다(30 보다 작으면 그만큼이 구멍이다).
   **메우는 것은 자동이 아니다** — `--from/--to` 손 실행(§14.7)이고, 그 창도 31일 이하라 넓은 구멍은
   나눠 돌린다. runbook §11 제한 15.
2. `status` 는 재수집으로 갱신되지 않는다 — 러너의 기존 거동이고 이 slice 밖이다. **영향 범위를 좁혀
   적었다**(runbook §11 제한 20): 후보 질의의 둘째 조건(`deadline_at > now()`)이 마감 지난 것은 걸러
   내므로 남는 것은 「마감 전에 취소된 공고」다.
3. **답을 실측했다** — `raw_observation` 은 보존 러너의 **마스킹** 대상이고(`RetentionRule.MaskPii`, PII 만
   지우고 행은 남긴다 — 6G 백테스트 표본을 지키는 선택) `collection_run` 은 **면제 등재**다
   (`RetentionRule.Exempt(COLLECTION_LEDGER)`). **둘 다 파기되지 않으므로 run 마다 단조 증가한다.**
   그래서 디스크 증가는 막는 자리가 아니라 **재는 자리**만 둔다(§14.5 ④ ⓒⓓ · §11 제한 17).
4. 달 경계에서 일 상한 회계가 새 디렉터리로 넘어가 그날의 소비가 0 에서 다시 선다(월 1회, 최대 하루치) —
   달로 가르는 선택의 대가다(runbook §11 제한 16 · §14.6).
5. 후보 상한 초과는 수집이 켜진 뒤의 상시 위험이다 — 상한을 올려도 공고가 그 위로 쌓이면 다시 붉다.
   OPEN 에 올렸다: **`OPEN-M15-CANDIDATE-QUERY-SCOPE`**(capability map · runbook §11 제한 18).
6. 평가는 ML 없이 `Review(MlUnavailable)` 로 판정한다(`EvaluationWiring` — 현행) — 이 slice 와 무관한 현행 사실.
7. **(신설) 래퍼는 실행 상태 자리의 gid 등식을 재지 않는다.** 보는 것은 달 디렉터리의 mode(group rwx 열림 ·
   other 닫힘)까지이고, 그 group 이 이미지의 **10001** 인가는 runbook §14.1 의 운영자 확인이다. 사유는
   **측정의 한계**다 — CI 러너도 로컬 레인도 그 gid 로 `chgrp` 할 권한이 없어 fixture 를 만들 수 없고,
   양성 대조 없는 단언은 「조용히 전부 거절하는 술어」와 구별되지 않는다. 어긋났을 때의 귀결은
   fail-closed 다(컨테이너가 원장에 쓰지 못해 러너가 기동에서 죽는다). OPEN:
   **`OPEN-M15-COLLECTION-RUN-STATE-GID`**.
8. **(신설) 공유 술어는 단일 실패점이다.** `tools/prod-env.sh` 의 둘째 env 파일 검사가 relay 와 수집의
   유일한 문턱이라, 그 함수가 헐거워지면 둘이 한꺼번에 열린다. 그 대가를 받아들인 사유와 겹(두 래퍼가
   각자 같은 축을 재게 둔다)은 (2b) 표의 마지막 행이다.
9. **(신설) 가드의 창 계산 단언은 KST 자정 경계에서 흔들릴 수 있다.** 검사가 `TZ=Asia/Seoul date` 로
   계산한 값과 래퍼가 같은 명령으로 계산한 값이 자정 전후 한 초에서 갈릴 수 있다 — **조용히 지나가지
   않고 붉다**(미측정이 아니라 거짓 RED 다). 자정과 무관한 축은 `--from/--to` 갈래의 단언이 따로 잰다.
10. **(신설) 이 slice 는 실 호출을 하나도 하지 않았다.** 조달청 API·운영 DB·운영 compose·systemd·
   `/etc` 를 건드리지 않았고(로컬 실측은 가짜 `docker`·`git` 앞에서 돌았다), **첫 실 호출은 runbook
   §14.5 ③ 이 낸다.** 그래서 「운영에서 이 래퍼가 실제로 돈다」는 아직 미측정이고, 측정 표의 칸은 비어
   있다. 상한 둘(`1000`·`31000`)은 포털 한도에서 내려온 수이고 **운영 실측 전**이다.

## 하네스 레인 변경

없음. `.claude/` 를 한 줄도 바꾸지 않았다 — 이 slice 가 만진 게이트(`tools/prod-guards-check.sh`)와 기대
집합 파일(`config/quality/`)은 산출물 층이다.

## 계약 갱신

**① 「조사」 1~8 을 코드에서 다시 확인했다 — 여덟 전부 그대로다.** 자리까지 적는다:
1. `Sql.SELECT_OPEN_CANDIDATES` 의 `WHERE` 는 `status = ANY(?) AND deadline_at > ?` 둘뿐이다. 상태 집합은
   `biddableStatuses()` 가 `isBiddable` 에서 **기계 산출**하고 그 술어는 `Open`·`Renoticed` 둘을 낸다.
2. `JdbcCandidateSource.openCandidates` 가 `cap + 1` 을 읽어 `scanned.size > cap` 이면
   `CandidateCapExceededException` 을 **던진다**(건너뜀이 아니다).
3. `seededBudget` 이 `runState.attempts.read().spend(dayStartOf(today, KST))` 로 seed 하고 그 원장은
   디렉터리 생애의 줄 전부다.
4. 관측 키에 관측 시각이 들어가 재수집마다 새 행이 서고, `collection_run` 도 슬롯마다 한 행이다.
5. 이 갈래에 재호출 상한·영구 정착이 없다(그 축은 개찰 갈래의 것이다).
6. `CollectionExitCode` 는 다섯이다(`COMPLETE`·`FAILED`·`INCOMPLETE`·`ALREADY_RUNNING(3)`·
   `UNLOCKABLE(4)` — 뒤 둘은 이름 붙인 상수다).
7. `to` 는 오늘(KST)까지 · 창 31일 이하(`COLLECTION_RANGE_POLICY`) · 슬롯 = 날짜 × 업종 · 쪽 100 행.
   **보태 적을 것**: 그 100 은 `KonepsOpenApiNoticeSource` 의 `DEFAULT_ROWS_PER_PAGE` 이고 **이 갈래에는
   그 값을 바꾸는 설정 키가 없다**(개찰 갈래의 `rows-per-page` 와 다르다) — 래퍼가 그 축에 플래그를
   열지 않은 사유다.
8. 이미지의 `groupadd --gid 10001` · `useradd --uid 10001` 이고 `USER bidvector` 다. 일회 컨테이너에 쓰기
   가능한 볼륨이 없다.

**② 조사에 없던 사실 하나가 설계를 정했다.** `RunStateDirectory` 는 `requireStateDirectory` 로
**디렉터리가 없으면 거부하고 만들지 않는다**(「경로를 잘못 대면 상한이 0 에서 시작한다」). 그래서 달
디렉터리를 만드는 것은 래퍼여야 하고, 래퍼는 `deploy` 로 돌고 컨테이너는 uid/gid 10001 로 도므로 **둘이
같은 원장에 쓰는 길은 group 공유뿐**이다 — root 에 setgid(§14.1 의 `install -d -m 2770 -o "$USER" -g 10001`)
와 래퍼의 `mkdir -m 2770` 이 그 한 쌍이다. 이것이 (2b) 의 신설 행 「달 디렉터리의 권한」과 알려진 제한 7 의
출처다. **대안으로 `run --user` 를 쓰면 host 권한이 단순해지지만** 이 러너만 이미지가 선언한 사용자와 다른
신분으로 돌게 되어 D-15U-2 의 「반입이 막는 것을 이 러너만 통과시키는 길을 만들지 않는다」를 어긴다 —
그래서 택하지 않았다.

**③ in_scope 에 없던 신설 파일 하나.** `config/quality/runbook-koneps-env-lines.txt` — 자격 파일이 둘이
됐으므로(`relay.env` · `koneps.env`) M15/15R 의 S-2 가 닫은 사고 부류(`install … /dev/null` 이 이미 채운
자격 파일을 말없이 0 바이트로 만든다)를 **같은 처방으로** 이 축에도 둔다. in_scope 의
`config/quality/runbook-prod-path-lines.txt`(「그 게이트가 요구하면」)는 **요구했다** — §14 의 새 줄 열다섯이
그 집합에 들어왔다(전부 추가 · 삭제 0).

**④ 레인이 정한 것과 근거.**
- **`TimeoutStartSec=15m`** — 처방 창의 슬롯은 넷(날짜 둘 × 업종 둘), 쪽은 100 행이라 기대 호출은 열
  단위다. 최소 호출 간격 1 초 + 요청 시한 10 초이므로 스물넷 호출이면 최악 약 4 분이고, 15m 는 그 위에
  한 자릿수 여유를 두면서도 **훑기(07:30)까지의 20 분 안**에 끝나 그 주기의 사슬을 굶기지 않는다
  (선례 셋도 15m 이다).
- **러너 3·4 를 옮길 수 = 5·6, 그 밖은 7** — relay 가 쓴 수와 같다. 다른 수를 고를 이유가 없다(두 래퍼가
  서로 다른 unit 이므로 수가 겹쳐도 충돌하지 않고, 같은 수를 쓰면 §9.5 의 경보 술어를 하나로 읽는다).
- **`ALREADY_RUNNING`(→`5`)의 경보 처분 = 빼지 않는다.** 래퍼의 잠금 둘을 지난 뒤 그 코드가 서는 경로는
  **래퍼를 거치지 않은 실행**(손으로 띄운 `java -jar`·컨테이너)이 같은 실행 상태 디렉터리를 쥔 경우뿐이다
  — 래퍼를 거친 손 실행은 프로젝트 잠금에서 `4` 로 먼저 미룬다(가드의 잠금 단언이 그 축이다). 즉 설계된
  정상이 아니라 사람이 알아야 하는 어긋남이다. relay 의 `5`(`LEASE_BUSY` — 임대 경합은 설계된 정상)와
  **다른 판단**이고 그 차이를 `10-defer.conf` 주석과 §14.4 가 적는다.
- **컨테이너 안 실행 상태 경로 = `/var/lib/bidvector-run-state`**(고정). 이미지에 그 경로를 쓰는 다른
  자리가 없고, 저장소 표식이 없는 자리라 러너의 「실험 입력은 저장소 밖」 가드도 지난다.
- **D-15U-3 술어를 `tools/prod-env.sh` 로 뽑았다**(공유). 계약이 먼저 검토하라고 한 쪽이고, 사본 둘을
  두면 한쪽만 고쳐지는 날이 온다(v2-지침서 §5). relay 의 거동 무변경은 가드 단언 집합이 증인이다.
- **`--calls-per-day 1000` · `--calls-total 31000`** — 지어내지 않았다. 포털 한도가 키 × operation × 일
  1,000 이고(m6-6g §5 실측) 이 갈래는 업종마다 operation 하나를 부르므로, **두 operation 에 걸친 한
  예산**이 1,000 이면 최악에도 어느 한 operation 의 한도를 넘지 않는다. 총 상한은 디렉터리가 달마다
  갈리므로 `1,000 × 31 = 31000` 이다. §14.5 ③ 의 첫 손 실행은 더 작은 `50`/`50` 으로 묶는다(6G §7 2항의
  노출 제한과 같은 모양).
- **runbook 의 읽기 전용 질의는 운영 DB 에 돌리지 않았다**(문면만). 후보 수 질의의 상태 어휘는
  `isBiddable` 에서 확인한 `Open`·`Renoticed` 이고, 그 질의가 DB 의 `now()` 를 쓰는 것과 러너가 주입된
  시계를 쓰는 차이도 §14.5 ④ 에 적었다.

**⑤ 수정 라운드가 만드는 새 파일은 없다**(위 ③ 하나로 끝이다 — 라운드마다 이 절을 다시 본다).
