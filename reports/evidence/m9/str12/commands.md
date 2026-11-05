# M9/STR12 — 실행한 명령과 핵심 결과

> 호스트 규율: 모든 Gradle 명령은 `~/.local/bin/bv-build.sh` 아래에서 돈다(빌드 자리 둘, 획득 순간
> 메모리 재확인). 점검 셋(`pgrep` · `free -m` · `ps --sort=-rss`)은 명령과 묶지 않고 **별도 호출로
> 먼저** 돌려 결과를 읽은 뒤 시작했다.
>
> **전건 `check` · `container` job · S-20 의 정본은 PR CI 다**(팀장 공지 2026-11-05 — 호스트 빌드 큐
> 포화, 대기 12). 로컬은 **표적 `--tests` 와 변이**만 돌렸다. §6 이 CI 결과를 든다.

## 0. 호스트 점검 (빌드 시작 전, 매 라운드)

| available | Swap free | 다른 레인 | 판정 |
| ---: | ---: | --- | --- |
| 16.3 GB | 6.2 GB | 없음 | 시작 |
| 17.0 GB | 6.2 GB | 없음 | 시작 |
| 11.5 GB | 5.1 GB | screen-display `one-command-check` | 시작(문턱 6GB·2GB 충족, 자리 둘 중 하나) |
| 15.6 GB | 5.6 GB | 없음 | 시작 |
| 10.9 GB | 5.1 GB | inbox-vocab `check` | 시작 |

## 1. RED 선행

| 층 | RED 확인 | GREEN |
| --- | --- | --- |
| 발송·렌더·경로 어댑터 | `:adapters:compileTestKotlin` **exit 1** — `TelegramSenderSettings`·`TelegramBotCredential`·`TelegramChatId`·`TelegramNotificationSender`·`telegramHttpClient`·`ConfiguredRouteDirectory` 전부 미해소. 그 층의 **어떤 단언도 아직 서지 않는다** | `:adapters:test --tests 'bidvector.adapters.notification.*'` **exit 0** · 53 test |
| 기동 검사(app) | **사후 재현** — §1b. 개발 중 컴파일 RED 를 보았으나 **그 실행의 출력을 잃었다**(`\| head -40` 이 파이프를 버퍼링해 0 바이트로 끝났다). 그래서 「먼저 확인했다」를 명령 exit 으로 들 수 없다 | `:app:test --tests 'bidvector.app.relay.*' --tests '…RelayWiringTest' --tests '…EvaluationCommitRunE2ETest'` **exit 0** · 42 test |
| 실 sender 조립 E2E | 위 adapters 컴파일 RED 에 포함(같은 소스셋) | `:adapters:test` 안에서 `PipelineTelegramDeliveryE2ETest` 1 test · fail 0 |

**이 자리의 정직한 한계**: app 층의 RED 는 **선행 측정으로 기록되지 않았다**. 같은 명제(「production
타입 없이는 그 test 가 서지 않는다」)를 §1b 가 사후에 재현하고, **거동 축은 변이 ③④ 가 든다** — 그쪽은
사후가 아니라 산출물 위에서 직접 측정한 것이다.

### 1b. app 층 RED 사후 재현 — **깨끗하게 재현되지 않는다**

두 번 시도하고 **둘 다 test 층에 닿지 못했다**. 기록을 남긴다(시도했고 안 됐다는 것이 사실이다):

| 시도 | 되돌린 것 | exit | 오류 자리 |
| --- | --- | ---: | --- |
| ① | `RelayBootDecision.kt` → 병합된 main 판 · `NotificationChannelProperties.kt` → 없음 | **1** | `e:` 8 건 — 전부 **`RelayWiring.kt`**(production)의 `Unresolved reference`. test 층에 닿기 전에 멈춘다 |
| ② | ① + `RelayWiring.kt` → 병합된 main 판 | **1** | `e:` 2 건 — 병합 전 배선이 **새 `NotificationRelayRun` 서명**을 만족하지 못한다(`No value passed for parameter 'channels'`·`'clock'`) |

**왜 안 되는가**: 이 slice 의 app 변경은 **모듈을 넘는 서명 변경**이다. production 을 되돌리면 그
되돌림이 배선 → 어댑터 조립로 연쇄해, 「test 만 새 상태」인 트리를 만들 수 없다. 더 되돌리면 되돌릴수록
RED 가 test 가 아니라 production 에서 난다.

**그래서 이 층의 RED 는 선행 측정으로 들지 않는다.** 대신 두 가지를 든다:
- **거동 축은 변이 ③④** 가 진다 — 사후 재현이 아니라 **산출물 위에서** 직접 측정했고, ④ 는 기동 검사를
  무력화했을 때 3 건이 붉고 **구조 단언은 초록**임을 함께 보였다(그 짝이 이 slice 의 방어다).
- adapters 층의 RED 는 **선행 측정으로 기록됐다**(위 §1 첫 행, exit 1).

이 한계를 `checklist.md` 에도 등재했다.

## 2. 표적 test (산출물 커밋 위에서)

| 명령 | exit | 핵심 결과 |
| --- | ---: | --- |
| `:adapters:test`(전건) | **0** | 1334 test · fail/err **0** · skip 4 |
| `:adapters:test --tests 'bidvector.adapters.notification.*'` | **0** | 53 test — Mapping 12 · Sender 10 · SenderContract 5 · CredentialLeak 6 · RouteDirectory 3 · Renderer 9 · Dependency 6 · JdbcFacts 2 |
| `:app:sizeGate` | **0** | 500 줄 한도 — 한 건 초과를 고친 뒤(543 → 479, 대역을 형제 파일로) |
| `:app:test`(표적 다섯 클래스) | **0** | 42 test — BootDecision 6 · Runner 3 · ExitCode 16 · Wiring 12 · CommitRunE2E 5 |

**새 test 클래스 아홉이 실제로 돌았음**을 결과 XML 로 확인했다(표적 실행은 `gateExecutionGate` 를
돌리지 않으므로 등재만으로는 「돌았다」가 서지 않는다). 등재는 `config/quality/gate-tests.properties`
에 **만드는 커밋에서 함께** 넣었다.

## 3. 변이 (바꿔치우기)

전부 **산출물 커밋 뒤에** 걸고, 되돌림은 `git restore -- <그 파일>` 하나씩 했다(`git checkout --` 는
쓰지 않는다 — 미커밋 evidence 를 함께 지운다). 적용 표지는 `git diff --numstat` 으로 확인했다.

| # | 무엇을 무엇으로 | 적용 표지 | exit | RED |
| --- | --- | --- | ---: | --- |
| ①a | `telegramDeliveryResult` 의 `Ambiguous -> Unknown(at)` → `Rejected(ProviderDeclined)` (**4 자리 중 1**) | 1/1 | **1** | **3** — `모호 사유 전수가 Unknown` · `응답 timeout` · `송신 뒤 끊김` |
| ①b | 같은 파일의 `Unknown(at)` **4 자리 전부** → `Rejected(ProviderDeclined)` | 4/4 | **1** | **9** — Mapping 4(`5xx·3xx` · `2xx ok 아님/해독 불가` · `모호 사유 전수` · `429`) · Sender 4(`429·5xx` · `2xx ok false/해독 불가` · `timeout` · `끊김`) · SenderContract 1(`모호 뒤 재호출`) |
| ② | `send` 의 `remembered.computeIfAbsent(key) { … }` → **기억은 쓰되 읽지 않는다**(`attemptOnce` → `remembered[key] = result`) | 6/1 | **1** | **3** — SenderContract `같은 키 두 번 → 수신 1` · `거부 뒤 재호출` · `모호 뒤 재호출` |
| ③ | `TelegramBotCredential.toString()` = `MASKED` → `"$MASKED=$raw"`(마스킹에 원문을 덧붙인다) | 2/1 | **1** | **1** — CredentialLeak `자격 값 타입의 toString 은 원문을 담지 않는다` |
| ④ | `RelayWiring` 의 `require(decision is Allowed)` → **항상 참인 술어**(`Allowed \|\| RefusedMissingChannelConfig`) — 판정 함수 호출은 **남긴다** | 2/1 | **1** | **3** — RelayWiring `설정이 비면 거부` · `칸 하나가 비어도 거부` · `공백이면 비어 있는 것으로 센다` |

**①a 를 지우지 않고 남긴다.** `Unknown` 자리가 넷인데 하나만 바꿨을 때 **셋만** 붉었다 — 상태 코드
축(429·5xx)과 본문 축(`ok:false`·해독 불가)의 모호는 **각자 다른 test 가** 지킨다는 뜻이다. 한 test 가
네 자리를 대신 지키고 있지 않음을 ①a·①b 의 차이가 잰다.

**②의 모양이 중요하다.** `computeIfAbsent` 를 단순히 지우면 `remembered` 가 미사용이 되어 `-Werror`
컴파일 실패가 된다 — 그것은 **test 가 잡은 것이 아니다**. 그래서 기억을 쓰되 **읽지 않는** 모양으로
바꿔치웠다. 같은 이유로 ③은 상수를 남기고 원문을 덧붙였고, ④는 판정 함수 호출을 남긴 채 술어만
무력화했다(그래서 「배선이 판정 함수를 지난다」 구조 단언은 **초록이고**, 거동 test 가 혼자 잡는다).

**②가 붉히지 않은 둘**: `다른 키는 각각 효과를 낸다` · `새 인스턴스는 같은 키를 다시 보낸다`. 둘 다
기억을 **읽는지와 무관**하므로 초록이 맞다 — 변이가 겨냥한 자리만 붉다는 음성 대조다.

**④가 붉히지 않은 하나가 이 slice 의 핵심 짝이다**: `배선은 판정 함수를 지난다`(바이트코드 단언)가
**초록**이다. 변이가 판정 함수 호출을 남긴 채 술어만 무력화했으므로 구조 축은 그것을 못 본다 — 거동
test 셋이 혼자 잡는다. 그 둘이 짝이라는 것을 `RelayWiringTest` KDoc 이 예고했고 이 측정이 확인한다.

**③이 드러낸 좁음(알려진 제한)**: 붉은 것이 **1 건**이다. 갈래 열하나를 도는 누출 sweep 은 갈래마다
**결과 문자열만** 보고 설정 문자열은 보지 않아서, 자격 `toString` 의 마스킹은 전용 test 한 자리만
지킨다. 방어가 없는 것은 아니지만(그 한 자리가 실제로 붉다) **갈래마다 재는 것보다 좁다** — sweep 에
`settings.toString` 을 더하면 열한 자리가 함께 붉어진다. 이 slice 에서 고치지 않았고
`checklist.md` §3 에 등재했다.

## 4. 비밀값 스캔

```
grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m9/str12/
```

- **exit 1(매치 0)**. 참조형으로 적는다 — 어휘를 이 문서에 축어로 쓰지 않는다.
- 이 때문에 자격 값 타입을 `…BotCredential` 로 두고 누출 test 를 `…CredentialLeakTest` 로 지었다
  (앞 이름은 스캔 어휘에 걸렸다 — 첫 작성에서 실측).

## 5. in_scope 대조

**병합을 제외한 내 커밋만**에서 산출한다 — 팀장 레인이 `origin/main` 을 합쳤으므로(`6c38fb32`) 단순
range 는 남의 변경을 끌어온다.

```
git log --no-merges --format=%H 79f3e5c4..HEAD \
  | while read -r s; do git log -1 --format=%s "$s" | grep -q m9-str12 && git show --format= --name-only "$s"; done \
  | sort -u
```

- 산출 **40 경로**, **전부 in_scope 의 부분집합**(접두 대조 결과 `OUT:` 0 건 — 빈 출력).
- 계약 밖이었다가 **서명 변경이 강제한** 둘은 `scope.md` 가 사유와 함께 등재했다:
  `EvaluationCommitRunE2ETest.kt` 와 그 형제 `RelayFailureInjection.kt`(크기 한도 때문에 분리).

## 6. PR CI — 전건의 정본

전건 `check`(+`qualityBaseline`·S-20) · `ml-engine` · `web` · `container` 네 job 의 정본은
**PR #127 의 CI** 다(팀장 공지 2026-11-05 — 호스트 빌드 큐 포화로 로컬 전건을 돌리지 않는다).

**그 결과를 이 문서에 적지 않는다.** evidence 가 복원 목록 안의 파일이라, CI 결과를 여기 넣으려는
편집이 커밋을 또 낳고 그때마다 rollback 실측이 무효가 된다(하네스 규율: 「마지막 HEAD 의 결과 정본은
verifier 와 PR 조치 코멘트다 — evidence 에 넣으려는 시도가 커밋을 또 낳는다」).

**정본 자리**: PR #127 의 조치 코멘트(`gh pr checks 127 --watch` 결과 + run id).

## 7. clean-tree

변이 넷을 되돌린 뒤 **산출물 경로에 잔재 0**:

```
git status --porcelain -- adapters app config docs   → 빈 출력
```

- 되돌림은 변이마다 `git restore -- <그 파일>` 하나씩. `git checkout --` 를 쓰지 않았다 — 그것은
  미커밋 evidence 를 함께 지운다.
- `NotificationChannelProperties.kt` 는 §1b 재현에서 트리 밖으로 옮겼다가 되돌렸고,
  `git diff HEAD -- <그 파일>` 이 **빈 출력**이다(커밋된 내용과 바이트 동일).
- 남은 미커밋은 `reports/evidence/m9/str12/**` 다섯뿐이고, 그것이 이 문서를 담은 커밋이 된다.
