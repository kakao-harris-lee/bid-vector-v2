# M9/STR12 — 실행한 명령과 핵심 결과

> 호스트 규율: 모든 Gradle 명령은 `~/.local/bin/bv-build.sh` 아래에서 돈다(빌드 자리 둘, 획득 순간
> 메모리 재확인). 점검 셋(`pgrep` · `free -m` · `ps --sort=-rss`)은 명령과 묶지 않고 **별도 호출로
> 먼저** 돌려 결과를 읽은 뒤 시작했다.
>
> **전건 `check` · `container` job · S-20 의 정본은 PR CI 다**(팀장 공지 2026-11-05 — 호스트 빌드 큐
> 포화). 로컬은 **표적 `--tests` 와 변이**만 돌렸다. §6 이 CI 축의 상태를 든다.

## 0. 호스트 점검 (빌드 시작 전, 매 라운드)

| available | Swap free | 다른 레인 | 판정 |
| ---: | ---: | --- | --- |
| 16.3 GB | 6.2 GB | 없음 | 시작 |
| 11.5 GB | 5.1 GB | screen-display `one-command-check` | 시작(문턱 충족, 자리 둘 중 하나) |
| 10.9 GB | 5.1 GB | inbox-vocab `check` | 시작 |
| 10.1 GB | 8.0 GB | ops-migrations clone · gates-build | 시작(자리 둘이 다 차 있어 **큐에서 대기**) |
| 9.3 GB | 8.0 GB | 대기 큐 깊음(bv-build 대기 7) | 시작 |
| 8.7 GB | 7.4 GB | 자리 둘이 다 차 있음(각 30분 경과) | **큐에서 35분 대기 뒤 취소** — 아래 §1 |
| 8.6 GB | 7.3 GB | 대기 큐 8 | 시작 |

## 1. RED 선행

| 층 | RED 확인 | GREEN |
| --- | --- | --- |
| 발송·렌더·경로 어댑터 | `:adapters:compileTestKotlin` **exit 1** — 그 층의 production 타입 전부 미해소. **어떤 단언도 아직 서지 않는다** | `:adapters:test --tests 'bidvector.adapters.notification.*'` **exit 0** |
| 기동 검사(app) | **미기록** — §1b | `:app:test` 표적 **exit 0** |
| 실 sender 조립 E2E | 위 adapters 컴파일 RED 에 포함(같은 소스셋) | `PipelineTelegramDeliveryE2ETest` 1 test · fail 0 |

**승인 전 라운드(셋째 판)의 RED 도 선행이 아니다 — 이유가 다르다.** v N-1(base URL) 의 회귀 test 는
**구현보다 먼저 썼고**, 그 상태(test 있음 · 구현 없음)에서 표적 실행을 걸었다. 그런데 **호스트 빌드
큐가 35분 넘게 자리를 내주지 않았다**(자리 둘을 다른 레인의 30분짜리 빌드 둘이 쥐고 있었다). 큐에
남겨 두면 그 사이 다른 파일을 편집할 수 없어(걸리면 반쯤 편집된 트리를 컴파일한다) 그 실행을
**취소하고** RED 를 **역적용**으로 쟀다 — 변이 ⑨ 가 그 측정이고 RED 2 다(§3).

역적용은 verifier 가 r2 에서 「구판 production 위에서 새 test 로 잼」으로 쓴 것과 같은 방법이고
같은 명제를 잠근다. 그러나 **「먼저 재고 그 다음 구현」의 순서는 서지 않았다** — 갈음으로 적지 않는다.
보조 측정으로 JDK `URI` 의 거동을 직접 쟀다(열두 모양: 아홉은 `URI` 가 거부, 둘은 host·포트 검사가
필요, 둘은 양성 대조로 통과) — **옛 술어가 그 아홉을 전부 통과시킨다**는 사실이 그 자리의 근거다.

**수정 라운드(둘째 판)의 RED 는 선행이 아니다.** 그 라운드는 리뷰 지적을 받아 **코드를 먼저 고치고
test 를 뒤에 썼다** — 「test 먼저」를 지키지 않았다. 그래서 그 라운드가 더한 방어 넷의 근거는 선행
RED 이 아니라 **산출물 위의 변이 ⑤⑥⑦⑧** 이다(§3). 변이는 「그 방어를 빼면 그 test 가 붉다」를 재므로
같은 명제를 잠그지만, **측정 순서가 뒤바뀐 것은 사실**이고 그것을 갈음으로 적지 않는다.

### 1b. app 층 RED 사후 재현 — **깨끗하게 재현되지 않는다**

두 번 시도하고 둘 다 test 층에 닿지 못했다. 이 slice 의 app 변경이 **모듈을 넘는 서명 변경**이라
production 을 되돌리면 그 되돌림이 배선 → 어댑터 조립으로 연쇄해, 「test 만 새 상태」인 트리를 만들
수 없다(시도 ① 되돌림 8 건이 `RelayWiring` 의 미해소 · ② 되돌린 배선이 새 생성자 서명 불만족).
그래서 이 층의 RED 는 선행 측정으로 **들지 않고**, 거동 축은 변이 ④⑦⑧ 이 진다.

## 2. 표적 test (수정 라운드 산출물 커밋 위에서)

| 명령 | exit | 핵심 결과 |
| --- | ---: | --- |
| `:app:test` 표적(아키텍처 게이트 전부 + relay + `RelayWiringTest` + 평가 커밋 E2E) | **0** | 19 클래스 · **236 test** · fail/err **0** |
| `:app:ktlintMainSourceSetCheck` · `:app:ktlintTestSourceSetCheck` · `:app:detekt` · `:app:sizeGate` | **0** | 네 task 전부 통과(`sizeGate` 500 줄 한도) |
| `:adapters:test` 표적(`notification.*` + `relay.*` + 발송 E2E) | **0** | 13 클래스 · **83 test** · fail/err **0** |
| `:adapters:ktlintMainSourceSetCheck` · `:adapters:ktlintTestSourceSetCheck` · `:adapters:detekt` | **0** | 세 task 전부 통과 |

**승인 전 라운드 재실측 — 전부 `--rerun` 으로 돌렸다**(팀장 지시). `--rerun` 은 Gradle 9 에서
**task 옵션**이라 task 이름 **뒤**에 와야 한다 — 전역 자리에 두면 `Unknown command-line option` 으로
빌드가 서지 않는다(이 라운드에서 실측하고 고쳤다).

| 명령 | exit | 핵심 결과 |
| --- | ---: | --- |
| `:app:test --rerun` 표적(아키텍처 게이트 전부 + relay + `RelayWiringTest` + 평가 커밋 E2E) | **0** | 19 클래스 · **244 test** · fail/err **0** |
| `:app:ktlint{Main,Test}SourceSetCheck` · `:app:detekt` · `:app:sizeGate` (각 `--rerun`) | **0** | 네 task 전부 통과 |
| `:adapters:test --rerun` 표적(`notification.*` + `relay.*` + 발송 E2E) | **0** | 14 클래스 · **89 test** · fail/err **0** |
| `:adapters:ktlint{Main,Test}SourceSetCheck` · `:adapters:detekt` (각 `--rerun`) | **0** | 세 task 전부 통과 |

그 전에 **두 번 붉었고 둘 다 이 라운드가 만든 결함**이다: ① 3층 게이트가 새 보유자의 숨은 송신 멤버
등재를 요구했다(아래) ② `detekt` MaxLineLength·ktlint 인자 줄바꿈 여덟 — test 호출에 인자를 하나
더하며 생긴 것이고 줄을 나눠 고쳤다.

**게이트 등재가 다시 관측을 따라왔다.** `java.net.URI` 를 설정 `init` 이 쥐게 되자 전송 표면 쌍
하나가 늘고, 등재하자 **3층**(숨은 송신 멤버)이 켜져 `TelegramSenderSettings$Companion#uriAccepts`
하나를 더 요구했다 — 둘 다 **게이트 자신의 위반 출력에서 기계로 뽑아** 넣었다. 면제 래칫도 함께
움직였다(86 → 87, 기계 재계산 38 + 11 + 38, 중복 0).

**표적을 넓혔다 — 그것이 앞 라운드의 구멍이었다.** 앞 판 로컬 표적은 app 의 다섯 클래스뿐이어서
`TransportSurfaceGateTest`·`AppHttpDependencyGateTest` 가 **표적 밖**이었고, CI `check` 가 그 둘로
붉었다. 이제 `bidvector.app.architecture.*` 전부가 표적에 든다(위 19 클래스에 열셋이 그것이다).

**첫 시도는 컴파일 실패였다(exit 1).** 리뷰의 cr L-8(「전규정 이름을 import 로」)을 **확인 없이**
적용한 것이 `:app:compileTestKotlin` 을 깼다 — 그 파일은 도메인 `Clock` 을 이미 import 해 쓰고 있어
`java.time.Clock` 을 더하면 이름이 모호해진다. 지적을 되돌리고 **왜 전규정 이름이 맞는지**를 주석으로
남겼다(별 커밋). 이 라운드에서 **커밋한 트리가 컴파일되지 않은 구간이 있었다**는 사실을 적는다.

**test 결과 계수는 매 실행 전에 `build/test-results/test` 를 지우고 셌다** — 지우지 않으면 `--tests`
필터가 바꾸지 않은 앞 실행의 XML 이 남아 「돌지 않은 class」가 집계에 섞인다(이 라운드에서 실측:
지우기 전 tally 가 앞 라운드의 35 클래스를 그대로 냈다).

## 3. 변이 (바꿔치우기) — 여덟, 전부 RED

전부 **산출물 커밋 뒤에** 걸고, 되돌림은 `git restore -- <그 파일>` 하나씩(`git checkout --` 금지 —
미커밋 evidence 를 함께 지운다). 적용 표지는 `git diff --numstat` 으로 확인하고, 자리 수가 기대와
다르면 **적용하지 않고 중단**하도록 했다(더하기만 한 변이가 초록으로 지나가는 길을 막는다).

| # | 무엇을 무엇으로 | exit | RED | 붉은 자리 |
| --- | --- | ---: | ---: | --- |
| ①b | 결과 대응의 `Unknown` **4 자리 전부** → 확정 거부 | 1 | **9** | Mapping 4 · Sender 4 · SenderContract 1 |
| ② | 멱등 기억을 **쓰되 읽지 않는다** | 1 | **3** | SenderContract 셋 |
| ③ | 자격 `toString` 의 마스킹에 원문을 덧붙인다 | 1 | **3** | CredentialLeak — 전용 toString · **갈래 전수 sweep** · 양성 대조 |
| ④ | 기동 `require` 를 **항상 참인 술어**로(판정 함수 호출은 남긴다) | 1 | **4** | RelayWiring 넷 |
| ⑤ | 본문 상한 상수 4096 → 4,000,000 | 1 | **1** | Sender `4096 자를 넘는 본문은 잘리고 표지가 남는다` |
| ⑥ | 자격 모양 술어를 항상 참으로 | 1 | **2** | CredentialLeak 모양 거부 둘 |
| ⑦ | app 설정의 자격·chat id 칸이 **원문을 보간**한다 | 1 | **3** | NotificationChannelProperties 셋 |
| ⑧ | 기동 판정의 **활성 route 갈래**를 항상 거짓으로 | 1 | **2** | BootDecision `route 없으면 거부` · RelayWiring 같은 축 |

### 승인 전 라운드 — 변이 열, 전부 RED

이 라운드가 더한 방어 다섯(⑨~⑬)과, 코드를 고쳐 **기구가 움직인** 다섯(③⑤④⑦⑧)을 다시 걸었다.

| # | 무엇을 무엇으로 | exit | RED | 붉은 자리 |
| --- | --- | ---: | ---: | --- |
| ⑨ | base URL 의 URI 술어를 항상 참으로 (v N-1) | 1 | **2** | SenderSettings 거부 전수 · 거부 메시지 |
| ⑩ | 절단 경계의 high surrogate 판정을 low 로 바꿔치움 (v N-5) | 1 | **1** | Sender `걸친 쌍을 가르지 않는다` |
| ⑪ | 로그 속성 갈래를 항상 거짓으로 (v N-2) | 1 | **2** | BootDecision · RelayWiring 각 하나 |
| ⑫ | 설정 조립의 Live 게이트 제거 (cr L-6) | 1 | **1** | RelayWiring `비-Live 는 모양이 틀려도 기동한다` |
| ⑬ | 모양 거부 메시지에 길이를 되돌림 (v N-6) | 1 | **1** | CredentialLeak 거부 메시지 |
| ③ | 자격 `toString` 마스킹에 원문을 덧붙임 | 1 | **3** | r2 와 같다 |
| ④ | 기동 `require` 를 항상 참인 술어로 | 1 | **4** | r2 와 같다 |
| ⑤ | 본문 상한 상수 4096 → 4,000,000 | 1 | **3** | **1 → 3 으로 늘었다** — 새 골든 둘이 같은 절단을 지난다 |
| ⑦ | app 설정의 두 칸이 원문을 보간 | 1 | **3** | r2 와 같다(test 이름 하나가 바뀌었다) |
| ⑧ | 활성 route 갈래를 항상 거짓으로 | 1 | **3** | **2 → 3 으로 늘었다** — 새 순서 test 가 같은 갈래를 지난다 |

**음성 대조 — 이 라운드에서 새로 선 것 둘:**
- ⑩ 이 붉히지 **않은** 하나: `절단 지점에 걸치지 않은 서러게이트 쌍은 길이를 줄이지 않는다`.
  경계에 걸치지 않으면 물러나지 않는다 — 이 칸이 없으면 ⑩ 의 RED 가 「언제나 한 글자 버린다」는
  구현으로도 만족된다.
- ⑫ 가 붉히지 **않은** 넷: Live 축의 거부 test 넷이 초록이다. Live 게이트를 지워도 **Live 거동은
  바뀌지 않는다** — 바뀌는 것은 비-Live 뿐이고, 그래서 RED 가 그 한 칸이다.
- ④ 가 붉히지 **않은** 하나는 여전히 `배선은 판정 함수를 지난다`(바이트코드 단언)다 — 구조 단언과
  거동 test 의 짝이 세 라운드째 같은 모양으로 선다.

**⑤⑧ 의 RED 가 늘어난 것을 「방어가 세졌다」로 읽지 않는다.** 늘어난 것은 **같은 기구를 지나는
골든 칸의 수**다(상한·갈래). 방어의 폭이 아니라 측정의 밀도가 늘었다.

### ③ 이 넓어졌는가 — **test 수는 1 → 3 이고, 「열한 자리」는 애초에 성립하지 않는다**

앞 라운드가 「sweep 에 설정 문자열을 더하면 **열한 자리**가 함께 붉어진다」고 적었다. **그 문장은
틀렸다** — 갈래 전수 sweep 은 `@Test` **하나**가 11 갈래를 `forEach` 로 도는 모양이고 첫 누출에서
멈춘다(fail-fast). 그래서 붉는 **test 수**는 어느 쪽이든 1 이다.

실제로 바뀐 것은 **어느 축이 덮이는가**다. 앞 판 sweep 은 결과 문자열만 읽었고 결과 타입에는 자격이
실릴 칸이 없어, 자격 마스킹을 깨도 sweep 은 **초록**이었다(그래서 ③ 이 1 건). 이제 sweep 이 갈래마다
**설정 객체 문자열**도 읽고, 그 문자열은 자격 값 타입의 `toString` 을 품으므로 ③ 이 sweep 을 붉힌다.
측정값은 **1 → 3**(전용 toString test · sweep · 양성 대조)이다. 「열한」은 수정 라운드 evidence 에서
내린다 — 재지 않은 수를 적은 것이 그 문장의 결함이었다.

### 음성 대조 — 변이가 겨냥한 자리만 붉다

- ①a(네 자리 중 **하나**만 바꿈, 앞 라운드): RED **3**. 상태 코드 축과 본문 축의 모호를 **각자 다른
  test** 가 지킨다는 뜻 — 한 test 가 네 자리를 대신 지키고 있지 않다.
- ②가 붉히지 **않은** 둘: `다른 키는 각각 효과를 낸다` · `새 인스턴스는 같은 키를 다시 보낸다`.
  둘 다 기억을 읽는지와 무관하므로 초록이 맞다.
- ④가 붉히지 **않은** 하나가 이 slice 의 핵심 짝이다: `배선은 판정 함수를 지난다`(바이트코드 단언)가
  **초록**이다. 변이가 호출을 남긴 채 술어만 무력화했으므로 구조 축은 그것을 못 본다 — 거동 test 가
  혼자 잡는다. 앞 라운드에 이어 이 라운드에서도 같은 결과다(거부가 하나 늘어 RED 가 3 → 4).
- ⑤가 붉히지 **않은** 하나: `정확히 4096 자는 자르지 않는다 — 경계`. 상한을 올려도 **경계에서는 거동이
  같으므로** 초록이 맞다 — 상한 변이의 내장 음성 대조다.
- ⑧이 붉히지 **않은** 둘: `설정 결손과 route 부재가 함께면 설정 결손을 먼저 말한다` ·
  `Live 가 아니면 활성 route 가 없어도 허용이다`. 둘 다 무력화한 갈래를 지나지 않으므로 초록이 맞다 —
  순서 test 가 갈래 test 를 대신 지키고 있지 않다.

### 세 변이는 **모양을 바꿔야** 측정이 됐다

`capped` 호출을 지우고, 설정 마스킹 보조 함수를 전부 치우고, 판정 갈래를 삭제하는 모양은 각각 **미사용
private 멤버**를 남겨 `-Werror` 컴파일 실패가 된다 — 그것은 test 가 잡은 것이 아니다. 그래서 ⑤는 상한
**값**을, ⑥⑧은 **술어**를, ⑦은 두 칸의 **보간 대상**만 바꿔치웠다(⑦은 보조 함수가 남은 한 칸에서
계속 쓰이게 두었다). ②·③·④ 가 앞 라운드에서 같은 이유로 그 모양을 고른 것과 같다.

### 둘째 main 병합 뒤 재실측 (판정 전 마지막)

main `97ed49c4`(PR #134)을 병합한 뒤 같은 표적을 `--rerun` 으로 다시 돌렸다 — 둘 다 exit 0 이고
계수가 병합 전과 **같다**(app 19 클래스 244 test · adapters 14 클래스 89 test, fail 0). lint·detekt·
`sizeGate` 도 0. 변이는 이 라운드에서 기구를 바꾸지 않았으므로(문면 둘 + 등재 0) 다시 걸지 않았고,
앞 라운드의 열 건과 verifier r3 의 재현 아홉 건이 그 자리를 든다.

## 4. 비밀값 스캔 — 두 축, 참조형으로

```
command grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m9/str12/
git diff <병합된 main> HEAD | command grep '^+' | command grep -ciE -f config/quality/leak-patterns.txt
```

- **게이트 범위**(`reports/evidence/**` — CI 의 `leakPatternGate` 가 재는 범위): **매치 0**(exit 1).
- **diff 축(추가된 줄)**: **3**. 셋 다 KDoc·runbook 이 그 값의 **이름을 설명하는 산문**이고 값이 아니다
  (자격 값 타입 KDoc · 설정 KDoc · runbook §13.1 의 「그 값을 남기지 않는다」 문장). **실제 비밀값 0.**
- 승인 전 라운드에서 한때 **5** 였다 — 새 test 의 잘못된 base URL fixture 가 스캔 어휘에 걸리는 단어를
  담아 **거짓 양성 둘**을 보탰다. 이 slice 가 자격 값 타입 이름에서 이미 겪은 일이라 같은 처방(어휘 밖
  이름)을 test fixture 에도 적용해 다시 3 이 됐다.
- acceptance 문면이 「diff 에 0」이었던 것을 이 라운드에서 고쳤다(cr L-11) — 어휘를 **설명하는** 문서가
  diff 에 들어오므로 0 이 될 수 없는 기준이었다. 재는 것은 「어휘가 없다」가 아니라 「값이 없다」다.

## 5. in_scope 대조

```
git log --no-merges --format= --name-only HEAD ^<병합된 main> | sort -u
```

- 산출 **47 경로** / 커밋 25(승인 전 라운드 뒤). 늘어난 한 경로는 새 test 클래스
  `TelegramSenderSettingsTest.kt` 이고 in_scope 안이다. 병합된 main 을 `^` 로 제외하므로 **제목 필터가 필요 없다** — 앞 라운드가
  쓴 `grep -q m9-str12` 제목 필터를 이 라운드에서 버렸다(남의 커밋이 조상으로 들어오지 않는다).
  대조 실측: 그 17 개 중 제목이 `m9-str12` 가 아닌 것 **0**.
- **in_scope 밖 한 자리**: `AppHttpDependencyGateTest.kt`. 게이트 등재의 반쪽(면제 래칫 상수)이
  `config/quality/**` 가 아니라 그 test 파일에 살아서 손댔다. `scope.md` in_scope 에 사유와 함께
  등재했고 **팀장 확인 대상**이다(계약 갱신은 팀장이 승인한다).
- 앞 라운드가 등재한 두 자리(평가 커밋 E2E 와 그 형제 대역 파일)는 그대로 유효하다.

## 6. CI 축의 현재 상태 — 승인 전 라운드의 정본은 **아직 돌지 않았다**

판정 SHA `eaa6fbff` 의 CI run `42143719228` 은 **네 job 전부 success** 였고 `check` step 안의
`qualityBaseline` 과 S-20 `one-command-check.sh` 가 **이 브랜치에서 처음 초록**이었다(verifier 확인).
그 뒤 승인 전 라운드가 코드·게이트 등재·runbook 을 더 고쳤으므로 **그 정본은 팀장이 push 할 때 도는
다음 run** 이다. 로컬은 표적과 변이만 돌았다.

### 앞 라운드의 기록 (판정 SHA `4e7d7093`)

직전 run `42016726748`(판정 SHA `4e7d7093`): `web`·`container`·`ml-engine` **pass** ·
`check` **fail**. 산출물 artifact 의 결과 XML 을 받아 **실패 전수를 셌다**: 전 모듈 중 app 만
`4` 건이고 그 넷이 `TransportSurfaceGateTest` 2 · `AppHttpDependencyGateTest` 2 다 — **이 라운드가
고친 바로 그 둘이고, 그 밖의 red 는 0** 이다(cr Q-1 의 질문에 대한 답).

**그 run 에서 `qualityBaseline` 과 S-20 step 은 `check` 실패로 `skipped` 였다** — 그 둘은 이 브랜치에서
**아직 한 번도 돌지 않았다**. 이 라운드의 전건 정본은 **팀장이 push 할 때 도는 다음 CI run** 이다.
그 결과는 PR 조치 코멘트가 든다(evidence 에 넣으려는 편집이 커밋을 또 낳아 rollback 실측을 무효로
만든다 — 하네스 규율).

## 7. clean-tree

변이 여덟을 되돌린 뒤 **산출물 경로에 잔재 0**:

```
git status --porcelain -- adapters app config docs reports/evidence/m9/str12
```

- 변이 스크립트가 각 변이 뒤에 되돌림과 `--porcelain` 확인을 함께 돌았고, 여덟 모두 빈 출력이었다.
- 양성 대조: 되돌리기 **전**에는 같은 술어가 그 파일을 `M` 으로 낸다(적용 표지 `--numstat` 와 짝).

## 8. main 병합 — 합집합인지 계수로 확인

### 둘째 병합: main `97ed49c4`(PR #134)

겹친 파일은 **하나**(`gate-tests.properties`)이고 main 쪽 변경은 **주석 문단 하나**뿐이다. 충돌 0
자동 병합이라 계수로 봤다:

| 재는 것 | 결과 |
| --- | --- |
| 항목 집합 | **535** = 내 쪽 535 ∪ main 524(main 은 항목을 더하지 않았다 — 주석만) |
| `comm -3 merged (mine ∪ main)` | **빈 출력 양방향** |
| 새 중복 | **0**(병합 전과 같은 기존 중복 다섯뿐) |
| main 의 새 주석 문단 | **1 번** 나온다 |
| main 이 **대체한** 옛 문장 | **0 번**(접히지 않았다). 비슷한 문장이 하나 남아 보이지만 그것은 **다른 패키지**(extraction)의 문장이고 main 트리에도 1 번 있다 |
| 내 두 등재 | 각 **1 번**(새 test 클래스 둘) |

### 첫째 병합: main `3ad4f3f6`(PR #135)

main `3ad4f3f6`(PR #135)을 병합했다. 양쪽이 고친 파일 **셋**이고 셋 다 합집합임을 계수로 확인했다.

| 파일 | 자동 병합 | 확인 |
| --- | --- | --- |
| `gate-tests.properties` | 충돌 0 | 항목 수 **519 → 534**(= base + main 5 + 내 10). main 쪽 추가가 병합본에 없는 것 **0**, 내 쪽도 **0**, 양쪽에 없는 항목 **0**. 중복 항목 다섯은 **두 부모에 이미 같은 모양으로** 있다(새로 생긴 중복 0) |
| `m6-6e-operations.md` | 충돌 0 | 양쪽 추가 줄 중 병합본에 없는 것 **0 · 0** |
| `capability-map.md` | **충돌** | 한쪽을 고르면 다른 쪽 행이 사라진다 — 내 두 행(`OPEN-STR-12` 갱신 + `OPEN-STR-12-INTERACTIVE` 신설)과 main 의 새 행 하나를 **합집합**으로 두고, 내 갱신이 대체한 main 의 옛 `OPEN-STR-12` 행만 버렸다. 행 계수로 확인(각 id 가 의도한 수만큼) |

「충돌 0 자동 병합이 같은 본문을 접는다」가 알려진 함정이라 셋 다 **계수로** 봤다.
