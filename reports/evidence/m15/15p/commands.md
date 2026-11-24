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
| `./tools/prod-alert-check.sh` | exit 0 — **74 건 전부 통과**(15M 은 49 · 15P 첫 판은 56) |
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
이다(따옴표 안이 한 토큰으로 묶인다). 두 층 모두 열려 있었고 15P 가 닫았다.

## 수정 라운드 1 — verifier 의 44 꼴을 전수로 다시 쟀다

verifier 의 씨앗을 읽어 **내 scratch 경로로 다시 만들고** 돌렸다(저장소에 들이지 않았다). 열일곱이 첫
판을 지나갔고, 그 전부가 `bash -n` 통과 + 게이트 밖에서 디스크 항목 생성(열여섯은 파일 1, `ln -s` 는
심볼릭 링크 1 — `find -type f` 가 링크를 세지 않아 `-type l` 로 따로 쟀다).

| 꼴 | 첫 판 | **수정 라운드 1** | r1 이 드는 사유(첫 위반 문면) |
| --- | --- | --- | --- |
| `\tee` · `te\e` | 0 · 0 | **1 · 1** | 명령어에 역슬래시가 있다 |
| `/usr/bin/tee` · `"tee"` · `te''e` | 0 · 1 · 1 | **1 · 1 · 1** | allowlist 밖의 명령어 |
| `c=tee; "$c"` · `$(echo tee)` | 0 · 1 | **1 · 1** | 명령어가 상수가 아니다 |
| `declare -n r=y; $r` | 0 | **2** | allowlist 밖의 선언: declare |
| alias(`shopt -s expand_aliases`) | 0 | **3** | allowlist 밖의 명령어: shopt |
| `/bin/bash -c` · `dash -c` · `trap` | 0 · 0 · 0 | **1 · 1 · 1** | allowlist 밖의 명령어 |
| `source /dev/stdin` · `. /dev/stdin <<X` | 0 · 0 | **1 · 1** | allowlist 밖의 명령어: source · . |
| `${x@P}` | 0 | **2** | 확장 연산자 @ — 값을 코드로 되돌린다 |
| 산술 첨자 `(( x ))` | 0 | **1** | 산술 평가 — 첨자·값이 명령 치환을 담을 수 있다 |
| `sort -o` · `ln -s` · `history -w` | 0 · 0 · 0 | **1 · 1 · 2** | allowlist 밖의 명령어 |
| `sed -n 'w …'` · `python3 -c` · `cp /dev/stdin` | 0 · 0 · 1 | **1 · 1 · 1** | 같음(제한 2 ⓑ 가 좁아진 자리) |
| `curl -s -o …` | 0 | **2** | 명령어 curl 의 허용 밖 인자: -s · -o |
| `command tee` · `builtin eval` · `env tee` · `xargs tee` · `exec tee` | 1 씩 | **1 씩** | `command` 는 -v 꼴만 · 나머지는 allowlist 밖 |

**44 중 43 이 붉다.** 남는 하나는 `printf a >&3` 한 줄짜리 씨앗이고 **결함이 아니다** — fd 복제는 허용
꼴이고 그 씨앗만으로는 아무 파일도 만들어지지 않는다(열려 있지 않은 fd). `exec 3>file` 이 앞에 붙는
꼴(s01)은 그 리다이렉션이 붉다.

**경보 스크립트(무변)는 allowlist 아래서도 위반 0** 이다. 그 파일의 census 를 먼저 쟀다:
`CallExpr.Args[0]` 상수 **20 종 · 비상수 0** · `DeclClause` 는 `local` 넷 + `export` 하나(옵션 0) ·
`ParamExp` 의 `Exp.Op` 는 `:-`·`#`·`##`·`%`·`%%`·`-` 뿐(**`@` 는 0**) · `ArithmCmd`/`ArithmExp` **0** ·
`curl` 인자는 `--config` 와 프로세스 치환 하나 · `command` 인자는 `-v` 둘.

## 수정 라운드 1 — fail-closed 와 LOW 넷

| 잰 것 | 결과 |
| --- | --- |
| **공허한 RED**(cr MEDIUM) — 판이 어긋난 핀으로 양성 대조 씨앗을 잰다 | 술어 **exit 2 · stdout 빈 줄**. 옛 꼴 `[ "$n" != 0 ]` → 「붉다」(공허한 초록) · 새 꼴 `rc==0 && 첫줄>0` → 「붉지 않다」 |
| 정책 파일에 `tool.shfmt.version` 을 **두 번** 선언 | `./tools/prod-alert-check.sh` **exit 2** — 「2번 선언됐다(정확히 한 번)」. 앞 판은 판 대조에서 「판이 다르다」로 서서 진짜 사유를 가렸다 |
| `shfmt` 가 PATH 에 없다(최상위) | **exit 2** · 「판정 불가」 한 줄 · 검사 0 건 실행 |
| zsh 전용 쓰기 연산자(cr 열린 질문) — `>>|`·`&>|`·`&>>|` 를 `-ln bash` 로 | **셋 다 파싱 실패**(「`>>\|` redirects are a zsh feature」). 파싱 실패는 이미 위반이므로 `WRITE_OPS` 에 더하지 않는다. `>\|` 는 파싱되고 이미 목록에 있다 |
| `isdigit()` 비-ASCII | `target.isascii() and target.isdigit()` 로 바꿨다 — `>&²`·`/dev/fd/٣` 가 허용으로 새지 않는다 |
| `subprocess` 시간 상한 · CI `curl` | `timeout=30` · `--connect-timeout 10 --max-time 120` |
| `install shfmt` 자리 | 15M 의 S-27e 설명 문단 **앞**(= S-27d 직후)으로 옮겼다 — 고아 주석 0, 거동 0 |

## 음성 대조 — 거짓 RED 가 사라졌다

주석 속 꺾쇠 짝·`->`·`a > b`, `[[ b > a ]]`, 문자열 속 `output = "/dev/null"`, `echo "$a" >&2` 를 한
파일에 모아 쟀다: 옛 술어 **7 건**(전부 거짓) · 새 술어 **0 건**. 경보 스크립트 주석의 「…」 규약이
거둬진 근거다. **`(( a > b ))` 는 수정 라운드 1 에서 이 파일에서 뺐다** — 리다이렉션으로 오독되지는
않지만 산술 평가 자체가 위반이기 때문이고, 그 꼴은 양성 대조 쪽이 든다(scope 알려진 제한 7).

## fail-closed — 「위반 0」과 「판정 불가」를 가른다

| 상황 | 결과 |
| --- | --- |
| `shfmt` 가 `PATH` 에 없다(최상위) | `./tools/prod-alert-check.sh` **exit 2** · 「판정 불가」 한 줄 · 검사 0 건 실행 |
| `shfmt` 가 `PATH` 에 없다(술어 층) | 술어 **exit 2** — 검사 안의 대조가 잰다 |
| `shfmt` 판이 핀과 다르다(`v0.0.0` 미끼) | 술어 **exit 2** — 검사 안의 대조가 잰다 |
| 경보 스크립트(무변) | 위반 **0** |

## 누출 스캔

- 참조형 `command grep -ciE -f config/quality/leak-patterns.txt` 를 **이 slice 가 더한 줄**에 돌려 매치
  **0**(첫 판 276 줄 · 수정 라운드 1 이 더한 187 줄 — 각각 따로 쟀다).
- 같은 훑개의 **양성 대조**: 바늘 한 줄을 심은 사본에서 매치 **1** — 부재가 공허하지 않다.
- 산출물 셋(`tools/prod-alert-*.sh` · 정책 파일)에 `파일:줄` 좌표 **0**.

## 바꾸지 않은 것

- `tools/prod-alert-telegram.sh` 의 **거동** — 머리 주석 한 문단 말고는 무편집(`git diff` 8 줄).
- `reports/evidence/m15/15m/` — 한 자도 고치지 않았다. 그 문서의 제한 13·16·17 을 어떻게 읽는지는
  `scope.md` 의 「15M evidence 와의 관계」에 있다.
- 런타임 축(fd·argv·디스크)·⑫ 까지의 단언 **43 건** — 전부 그대로다.
