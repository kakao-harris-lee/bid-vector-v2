# M15 / 15P — 실행한 명령과 결과

전부 이 저장소의 worktree 에서 돌았다. 실 Telegram·운영 host·실 DB 에 닿은 명령은 없다.
`shfmt` 는 핀한 판의 릴리스 바이너리를 버릴 자리에 받아 `PATH` 앞에 두고 돌렸다(**시스템에 깔지 않았다**).

## 전제 — 파서 설치

- `curl -fsSL -o … https://github.com/mvdan/sh/releases/download/v3.14.1/shfmt_v3.14.1_linux_amd64` ·
  `sha256sum` → `76e77641…7ea7bf` — `config/quality/shell-guard-policy.properties` 의 핀과 **같다**.
- `shfmt --version` → `v3.14.1`.

## acceptance

| 명령 | 결과 |
| --- | --- |
| `./tools/prod-alert-check.sh` | exit 0 — **56 건 전부 통과**(15M 은 49) |
| `./tools/prod-guards-check.sh` | exit 0 — 통과 74 · 어긋남 0(15M 과 같다 — 이 slice 가 만지지 않는다) |
| `./tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | exit 0 — job 5 · id 15 · 참조 18 · 매달린 0(새 step 에 `id` 가 없어 계수 무변) |
| (CI) `container` job 전건 | **PR 의 첫 회차가 낸다** — 이미지 빌드·trivy·compose 기동이 로컬 비용이 크다(15M 과 같은 자리) |

## 씨앗을 게이트 **밖에서** 먼저 쟀다

양성 대조의 씨앗이 진짜 결함인지를 술어에게 묻지 않는다. 검사가 심는 아홉을 그대로 떠서(수집 한 줄을
덧댄 사본을 돌리고 측정 뒤 지웠다 — 추적 밖) 버릴 자리에서 실행했다.

| 씨앗 | `bash -n` | 실제로 만든 파일 | 옛 술어(15M) | 새 술어(15P) |
| --- | --- | --- | --- | --- |
| 고정 경로 설정 쓰기(`> "$CFG"`) | ok | `/tmp/x` 547 바이트 | 2 | **1** |
| `${#x}` 관용구 | ok | 1 | 2 | **1** |
| `$#` 관용구 | ok | 1 | 2 | **1** |
| `a#b` 관용구 | ok | 1 | 2 | **1** |
| ANSI-C 인용 `$'\' # '` | ok | 1 | 2 | **1** |
| **REG-1 here-doc 짝** | ok | 1 | **0** | **1** |
| `tee` | ok | 1 | 2 | **1** |
| `mktemp` | ok | 1 | 4 | **2** |
| `eval` | ok | 1 | **0** | **1** |

고정 경로 씨앗은 인자 없이 돌면 사용법 검사에서 먼저 선다 — 가짜 자격·chat id·닫힌 포트 기준 URL 을
주어 curl 줄까지 몰고 나서 쟀다(그래서 547 바이트가 실제로 디스크에 남았다).

**15M 제한 13 ⓑ 의 문면 하나가 틀렸다**: 「`eval "… > /tmp/x"` 는 오히려 잡힌다」. 실측은 옛 술어 **0 건**
이다(따옴표 안이 한 토큰으로 묶인다). 두 층 모두 열려 있었고 15P 가 낱말로 닫았다.

## 음성 대조 — 거짓 RED 가 사라졌다

주석 속 꺾쇠 짝·`->`·`a > b`, `(( a > b ))`, `[[ b > a ]]`, 문자열 속 `output = "/dev/null"` 을 한 파일에
모아 쟀다: 옛 술어 **7 건**(전부 거짓) · 새 술어 **0 건**. 경보 스크립트 주석의 「…」 규약이 거둬진 근거다.

## fail-closed — 「위반 0」과 「판정 불가」를 가른다

| 상황 | 결과 |
| --- | --- |
| `shfmt` 가 `PATH` 에 없다(최상위) | `./tools/prod-alert-check.sh` **exit 2** · 「판정 불가」 한 줄 · 검사 0 건 실행 |
| `shfmt` 가 `PATH` 에 없다(술어 층) | 술어 **exit 2** — 검사 안의 대조가 잰다 |
| `shfmt` 판이 핀과 다르다(`v0.0.0` 미끼) | 술어 **exit 2** — 검사 안의 대조가 잰다 |
| 경보 스크립트(무변) | 위반 **0** |

## 누출 스캔

- 참조형 `command grep -ciE -f config/quality/leak-patterns.txt` 를 **이 slice 가 더한 줄 276** 에 돌려
  매치 **0**.
- 같은 훑개의 **양성 대조**: 바늘 한 줄을 심은 사본에서 매치 **1** — 부재가 공허하지 않다.
- 산출물 셋(`tools/prod-alert-*.sh` · 정책 파일)에 `파일:줄` 좌표 **0**.

## 바꾸지 않은 것

- `tools/prod-alert-telegram.sh` 의 **거동** — 머리 주석 한 문단 말고는 무편집(`git diff` 8 줄).
- `reports/evidence/m15/15m/` — 한 자도 고치지 않았다. 그 문서의 제한 13·16·17 을 어떻게 읽는지는
  `scope.md` 의 「15M evidence 와의 관계」에 있다.
- 런타임 축(fd·argv·디스크)·⑫ 까지의 단언 **43 건** — 전부 그대로다.
