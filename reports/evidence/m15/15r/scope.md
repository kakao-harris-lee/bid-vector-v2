# M15 / 15R — 알림 실발송 활성화 전 runbook·래퍼 고침 (PR #204 독립 보안 리뷰 반영)

```yaml
milestone: M15
slice: 15r-activation-runbook-fixes
base_sha: 928ac2fa0f8fb1fdb3a6c444eac5bceb756b3d0a   # main, PR #204 머지 커밋
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - docs/runbook/m7-7f-production.md      # §9.5 ② · §13.2 · §13.3 · §13.4 — S-1·S-2·S-3·S-4·S-9·S-10 과 runner 문면
  - docs/runbook/m6-6e-operations.md      # §3.2 — relay 래퍼의 「모르는 코드는 7」· 「relay 러너의 새 코드는 7 부터」
  - tools/prod-notification-relay.sh      # S-3(종료 코드 열거) · S-7(stray 이름을 모양으로 거른다)
  - tools/prod-env.sh                     # S-8(데몬 선택 축 둘을 비운다)
  - tools/prod-guards-check.sh            # 위 전부의 단언
  - milestone-15.md                       # 이 slice 가 닫는 것 한 문단
  - reports/evidence/m15/15r/
out_of_scope:
  - /etc · systemd · 운영 host 의 실제 설치와 활성화(§13.4 ①~⑤) — 운영자 몫이다. 이 slice 는 저장소
    파일만 고친다. 이미 깔린 경보 unit(2026-11-24 설치)을 새 문면에 맞추는 것도 운영자 몫이고, 그
    절차 한 토막을 runbook 에 적는 것까지가 이 slice 다(D-15R-4).
  - /etc/bidvector-v2/*.env 의 값 — 읽지 않는다.
  - 실 Telegram 발송 · 실 API 호출.
  - 러너 코드(app/**) — 바꾸지 않는다. RelayExitCode 는 읽기만 한다(D-15R-3 의 등식).
  - S-5(공급망 — ci.yml 액션 SHA 핀 등) — 별도 slice.
  - S-6(저장소 쓰기 권한 == 자격 신뢰 경계 문면) — 운영자가 승인한 묶음 밖이다. 아래 「알려진 제한」에
    등재만 한다.
  - 앞 slice 의 evidence(15m·15p·15q 의 scope.md) — 고치지 않는다. 리뷰가 그 파일들에 더하라고 한 행
    (15m 통로 표의 core dump · 15m 제한 11 의 「5 부터」)은 이 파일의 「앞 evidence 에 대한 정정」 절이 든다.
  - tools/prod-evaluation-commit.sh · tools/prod-notification-sweep.sh — 러너 종료 코드를 옮기는 `case`
    가 없다(착수 실측). 손대지 않는다.
acceptance_commands:
  # CI `container` job 의 로컬 가용 step 둘(S-27d · S-27e) — 축어.
  - ./tools/prod-guards-check.sh
  - ./tools/prod-alert-check.sh
  - bash -n tools/prod-notification-relay.sh tools/prod-env.sh tools/prod-guards-check.sh
  # CI `check` job — evidence 가 leakPatternGate 의 스캔 대상이라 줄일 수 없다. 마지막 HEAD 에서 한 번
  # (빌드 자리는 ~/.local/bin/bv-build.sh, 호스트 점검 셋 먼저).
  - ./gradlew check
  # (CI) container job 의 나머지 step 은 이미지 빌드가 필요해 판정은 PR head 의 CI 다.
rollback: reports/evidence/m15/15r/rollback.md
```

## 무엇을 닫는가

PR #204 에 남긴 독립 보안 리뷰(Codex 대체 sonnet, 코멘트 7160547788)의 판정은 「고친 뒤 활성화」였다.
운영자가 §13.4 ① 에서 가장 먼저 치는 두 줄이 하나는 자격 디렉터리 권한을 `0700` → `0755` 로 되돌리고
(S-1), 다른 하나는 이미 채운 `relay.env` 를 말없이 0 바이트로 만든다(S-2). 이 slice 는 그 둘과, 같은
리뷰의 S-3·S-4·S-7·S-8·S-9·S-10, 그리고 「깨끗했던 것」 절이 문면 정정을 권한 세 자리를 고친다.

## 위협 모델 경계

**막는 것** — 정직한 운영자가 runbook 을 축어로 따라 치다가, 또는 정직한 다음 저자가 runbook·래퍼·러너를
고치다가 만드는 사고다. 자격 디렉터리 권한 후퇴, 자격 파일 소실, 러너의 새 종료 코드가 `SuccessExitStatus`
에 삼켜지는 것, 자격 파일의 바이트가 journal 에 이름으로 찍히는 것, 운영자 셸의 docker context 가 래퍼를
다른 데몬으로 보내는 것, core dump 로 자격이 디스크에 가는 것.

**막지 않는 것** — runbook 이나 게이트를 일부러 속이려는 저자. runbook 을 보는 단언은 산문 속 코드 블록의
텍스트를 본다. 같은 뜻을 다른 꼴로 적어 술어를 비키는 저자는 경계 밖이다(그 저자는 `prod-guards-check.sh`
도 고칠 수 있다). 대신 술어마다 **뽑은 수를 고정**해, 줄이 사라지거나 꼴이 바뀌어 술어가 아무것도 못
집는 경우는 초록이 아니라 실패가 되게 한다.

## 결정

**D-15R-1 (S-1)** §13.2 의 `sudo install -d -m 755 /etc/bidvector-v2` 줄을 **지운다.** 그 디렉터리는 §1 ③ 이
만든다. 지운 자리에 「§1 ③ 이 만든 `0700` 디렉터리를 그대로 쓴다 — 여기서 다시 만들지 않는다」 한 줄을
산문으로 남긴다. 단언: runbook 코드 블록의 `install -d` 줄 가운데 `/etc/bidvector-v2` 또는
`/var/lib/bidvector-v2` 를 가리키는 것은 전부 `-m 700` 이고, 그 줄 수가 고정값과 같다.

**D-15R-2 (S-2)** `relay.env` 를 만드는 줄을 멱등 꼴로 바꾼다 — 없을 때만 만들고(`test -e … ||`), 권한은
내용을 건드리지 않는 `chown`·`chmod` 로 맞춘다. §1 ③ 의 경고 문장을 옮기고, 빈 파일이 되면 relay 와 경보가
**둘 다** 조용히 실패한다는 것을 적는다. 단언: runbook 코드 블록에서 `/dev/null` 을 `/etc/bidvector-v2/`
아래로 `install` 하는 줄은 전부 `test -e` 가드를 달고 있고, 그 수가 고정값과 같다. 새 꼴의 거동(채운
파일에 다시 쳐도 바이트가 그대로 · 권한만 600 으로)은 버릴 디렉터리에서 한 번 재어 commands.md 에 적는다.

**D-15R-3 (S-3)** relay 래퍼의 끝 `case` 를 열거로 닫는다 — `0|1|2` 는 그대로, `3` → `5`, `4` → `6`, 그 밖은
원래 코드를 문면에 담아 **`7`** 로 낸다. `7` 은 `SuccessExitStatus=4 5` 밖이라 경보가 운다. 단언 둘:
① 거동 — 가짜 docker 가 `0 1 2 3 4 5 6 9 137` 을 낼 때 래퍼가 내는 코드와 stderr 문면. ② 등식 —
`RelayLines.kt` 의 `RelayExitCode` 값 집합 == 래퍼 `case` 가 이름으로 받는 집합(`{0,1,2,3,4}`). 값 추출은
**상수 수 == 뽑은 값 수**를 함께 재어, 열거의 꼴이 바뀌어 추출이 비면 실패한다. 문면: §13.3 종료 코드 표에
`7` 행을 더하고, §13.3 과 `m6-6e-operations.md` §3.2 에 「`5`·`6`·`7` 은 래퍼의 것이다 · relay 러너에 값을
더하면 래퍼 `case` 와 이 표를 함께 고친다(등식 단언이 강제한다)」를 적는다. 리뷰의 「7 부터」는 쓰지
않는다 — `7` 을 래퍼가 쓰기 때문이다.

**D-15R-4 (S-4 · S-9)** §9.5 ② 경보 unit 에서 `EnvironmentFile=-/etc/bidvector-v2/relay.env` 를 지우고
`LimitCORE=0` 을 더한다. 위 주석을 「이 파일은 §13.2 대로 `User=` 가 직접 읽는다」로 바꾸고, 파일 부재가
여전히 스크립트의 `exit 2` 한 창구로 모인다는 것을 남긴다(§9.5 머리의 `-` 접두 설명도 함께 고친다).
이 host 에는 앞 판 unit 이 이미 깔려 있으므로, **깔린 unit 을 맞추는 절차 한 토막**(줄 지우기 ·
`LimitCORE=0` · `daemon-reload` · `systemctl show` 로 확인)을 §9.5 에 둔다. 단언: runbook 의 어떤
`EnvironmentFile=` 줄도 `relay.env` 를 가리키지 않는다(0) · 경보 unit 블록에 `LimitCORE=0` 이 하나.

**D-15R-5 (S-7)** stray 키를 전수로 찍지 않는다. 환경 변수 이름 꼴(`^[A-Z][A-Z0-9_]*$`)만 이름으로 찍고
나머지는 개수로만 말한다. 거절 자체(exit 3)는 그대로다. 단언: 진짜 compose 앞에서, 이름 꼴이 아닌 키를
낳는 fixture 는 거절되고 그 키 문자열이 stderr 에 없다 · 이름 꼴인 stray 는 이름이 찍힌다.
리뷰의 실측 예(`LEAKED_123456`)는 이름 꼴이라 이 처방으로도 찍힌다 — 그것은 「알려진 제한」이다
(이름을 전부 가리면 운영자가 무엇을 지워야 하는지 모른다).

**D-15R-6 (S-8)** `prod-env.sh` 가 source 시점에 `DOCKER_HOST`·`DOCKER_CONTEXT` 를 비운다. `DOCKER_CONFIG`·
`HOME` 은 건드리지 않는다(cr N-1). 단언: 두 변수를 쥔 채 source 하면 둘 다 비어 있고 `DOCKER_CONFIG` 는 남는다.

**D-15R-7 (S-10)** §13.3 의 `ExecStopPost=` 둘에 §9.2 의 경고(「프로젝트 이름이 다르면 이 줄도 함께
바꾼다」)를 붙인다. 단언: runbook 의 `ExecStopPost` 가 지우는 컨테이너 이름 셋의 접두가
`docker/compose.prod.yaml` 의 `name:` 과 같다.

**D-15R-8 (runner 문면)** §13.3·§13.4 에 넷을 적는다. ① `--claim-limit 1` 은 「한 통」이 아니라 「많아야 한
통」이다. ② `--owner` 는 아무것도 거르지 않는다 — ③ 의 상한은 전적으로 `--claim-limit` 에서 온다.
③ 임대는 owner 별이 아니다 — 「같은 owner 의 relay 가 임대를 쥐고 있다」류 문구를 코드가 실제로 하는
것에 맞춘다(구현 레인이 `app/**` 의 임대 질의를 읽어 확인하고 근거를 commands.md 에 적는다. 래퍼의
`LEASE_BUSY` 문면도 같은 말을 한다면 함께 고친다). ④ `TimeoutStartSec` 상한과 trap 의 `docker rm -f`
가 `CLAIMED` 고아를 만드는 쪽이고, 다음 run 의 `isolated>0` 은 그 귀결일 수 있다.

## 설계 검토 (세션 모델)

**(1) 열거인가 구성인가.** D-15R-3 의 `case` 는 통과 집합을 열거하고 나머지를 한 자리(`7`)로 모으므로
구성상 닫힌다 — 새 코드가 생겨도 조용한 통과가 없다. 나머지 단언은 runbook 텍스트를 보는 개수 등식이고,
위 경계 문장대로 「뽑은 수 고정」으로 공허한 초록만 막는다.

**(2) 우회·오작동 다섯.**
1. `RelayExitCode` 가 `LEASE_BUSY(value = 3)` 꼴로 바뀐다 → 상수 수 ≠ 뽑은 값 수 → 실패.
2. 러너가 `5` 를 새로 낸다 → 등식 실패(CI) · 운영에서는 래퍼가 `7`.
3. `docker run` 자신의 실패(125·126·127)와 신호 종료(137·143) → 전에는 그대로 통과, 이제 `7`. `ExecStopPost`
   의 조건은 `EXIT_STATUS` 3·4 만 빼므로 `7` 에서도 뒤처리가 돈다. 원래 코드는 문면에 남는다. **구현 레인은
   래퍼의 trap·`set -e` 와 새 `case` 가 겹치는 자리가 없는지 실측한다**(신호로 래퍼 자신이 죽는 경로는
   `case` 에 닿지 않는다).
4. §13.2 블록에 누가 `install -d -m 755 /etc/bidvector-v2` 를 되살린다 → D-15R-1 단언 실패. 변이로 잰다.
5. `EnvironmentFile=` 을 주석 안에 적은 산문 → 단언은 줄 머리 지시만 센다. 주석의 언급은 걸리지 않아야 한다
   (오탐 대조).
6. `DOCKER_HOST` 가 꼭 필요한 host(rootless docker) → 래퍼가 기본 소켓을 보고 `exit 3` 으로 선다.
   fail-closed 이고 「알려진 제한」에 등재한다. **CI 러너와 이 host 가 그 변수 없이 도는지 구현 레인이 확인한다.**

**(2b) 값 획득 축.** 새 public 표면은 없다. 새로 생기는 것은 종료 코드 `7` 하나이고, 그것은 실패 쪽으로만
읽힌다.

**(3) 과잉·미달.** 리뷰는 S-9 를 경보 unit 하나에만 권했다 — relay·평가 unit 에는 넣지 않는다(15m 제한 7 의
hardening 묶음 slice 몫). S-6 은 승인 묶음 밖이라 등재만 한다.

## 변이 표적 (구현 레인이 재고, verifier 가 다시 잰다)

변이는 **더하기가 아니라 바꿔치우기**로, 적용 직후 `git diff --numstat` 으로 들어갔는지 먼저 본다.
1. §13.2 에 `-m 755` 줄 되살리기 → guards RED
2. `relay.env` 줄에서 `test -e … ||` 떼기 → RED
3. 래퍼 `*)` 를 `exit "$rc"` 로 되돌리기 → RED(거동 단언)
4. `RelayLines.kt` 에 여섯째 상수 `(5)` 더하기(버릴 사본에서) → RED(등식)
5. §9.5 ② 에 `EnvironmentFile=` 줄 되살리기 → RED · `LimitCORE=0` 지우기 → RED
6. stray 를 전수 출력으로 되돌리기 → RED
7. `prod-env.sh` 의 unset 떼기 → RED
8. §13.3 `ExecStopPost` 한 줄의 접두 바꾸기 → RED

## 앞 evidence 에 대한 정정 (그 파일들은 고치지 않는다)

- 15m/scope.md 위협 모델의 통로 다섯에 **여섯째 — core dump** 가 있다. 이 slice 가 경보 unit 에
  `LimitCORE=0` 을 둔다(S-9).
- 15m/scope.md 제한 11 의 「러너의 새 코드는 5 부터」는 **훑기 러너에 한해** 참이다. relay 에서는 래퍼가
  `5`·`6`·`7` 을 쓰고 러너의 코드를 옮겨 내므로, 러너의 새 값은 **래퍼 `case` 에 이름으로 등재한 뒤에만**
  뜻을 갖는다(D-15R-3 의 등식이 그것을 강제한다).
- 15m/scope.md 통로 다섯(`/proc/<pid>/environ`)을 받아들인 사유 「relay 와 같은 배선」은 15Q 에서 전제가
  사라졌다. 이 slice 가 경보 unit 의 그 통로를 걷는다(S-4).

## 하네스 레인 변경

없음(착수 시점). 리뷰 요청 시점마다 `git log 928ac2fa0..HEAD -- .claude CLAUDE.md` 로 다시 본다.

## 알려진 제한

1. **S-6 미처리.** `<저장소>` 쓰기 권한이 봇 자격·chat id·운영 DB 의 신뢰 경계라는 문면(§13.3 · §1.4b)을
   이 slice 는 더하지 않는다. 운영자 결정 대기.
2. **S-5 미처리.** 공급망 slice 로 간다.
3. runbook 단언은 텍스트 술어다(위 경계 문장).
4. D-15R-5 — 이름 꼴인 stray 키는 여전히 journal 에 찍힌다.
5. D-15R-6 — `DOCKER_HOST` 가 필요한 host 에서는 래퍼가 서지 않는다(fail-closed).
6. 이미 깔린 경보 unit 은 운영자가 §9.5 의 절차로 맞추기 전까지 앞 판 그대로다.

(구현·검증 라운드가 더하는 제한은 이 뒤에 번호를 이어 적는다.)

## rollback

`rollback.md` — in_scope 경로 한정 복원, 복원 원천은 `928ac2fa0f8fb1fdb3a6c444eac5bceb756b3d0a`.
이 slice 는 운영 host 에 아무것도 깔지 않으므로 되돌림은 저장소 안에서 끝난다.
