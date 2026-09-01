# Slice 계약 — M0 / 0E · M0 종료

```yaml
milestone: m0
slice: 0e-m0-closeout
base_sha: 14686dbf3bff4085203ffdcd931564fd1e36edf0
  # 0E 착수 커밋. 리뷰는 **둘로 나뉜다** — 이 계약의 range 는 14686db..HEAD 이고,
  # 0A 미리뷰 창(6c6b3a2..6af7019)은 같은 시점에 **별도 리뷰**로 받는다.
  # 사유와 실측은 아래 「리뷰 range 를 왜 둘로 나누는가」.
head_sha: 리뷰 시점의 HEAD (= 0E 의 마지막 커밋)
  # 0E 의 커밋 집합은 `git log --oneline 14686db..HEAD` 가 낸다(commands.md C-0).
  # SHA 를 여기 박아 두면 커밋이 늘 때마다 낡으므로 range 로 적는다 —
  # 커밋마다 이 값을 옮겨 적는 장치(선언 SHA 래칫)를 두지 않는다.
in_scope:
  - v2-지침서.md                          # §5(Q1) · §4.2(Q2) 문면 개정
  - milestone-1.md                         # 1C 유효기간 요구(Q2 동기화)
  - milestone-0.md                         # 산출물 목록(Q3) + M0 완료 기록
  - docs/adr/*.md                          # ① 「상태」 줄 아홉
                                           # ② ADR 0001 §5 의 OPEN-ADR-01 행 — 해소로 갱신
  - docs/discovery/capability-map.md       # OPEN-OPS-07·OPEN-QUAL-05 registry + §14 이월 목록
  - reports/evidence/m0/0e/scope.md         # 이 레인의 evidence 는 `fixtures-*.md` 를
  - reports/evidence/m0/0e/commands.md      #   뺀 나머지다 — 그쪽은 fixture-curator
  - reports/evidence/m0/0e/checklist.md     #   레인 소유다. 개수를 적지 않는다:
  - reports/evidence/m0/0e/rollback.md      #   agent-workflow.md §6 이 요구하면 는다
out_of_scope:
  - docs/discovery/data-dictionary.md      # 0C Codex approve 로 확정 — 읽기 전용
  - docs/discovery/regression-ledger.md    # 0B Codex approve 로 확정 — 읽기 전용
  - docs/adr/** 의 위 둘 이외 전부          # 결정 내용·§1~§4·§6 무접촉.
                                           # §5 도 OPEN-ADR-01 행 하나만이고 나머지 열둘은 무접촉
  - reports/evidence/m0/0a · 0a2 · 0a3 · 0b · 0c · 0d   # 확정된 evidence — 읽기 전용.
                                           # 예외 하나: 리뷰 A(6c6b3a2..6af7019)의 verdict JSON
                                           # 한 건이 m0/0a 아래에 신설된다(b2b3860). Codex #1 의
                                           # required_fix 가 「A 의 독립 verdict 를 받아 기록한 뒤에만
                                           # N-3 을 닫는다」를 요구했고, verdict 는 그 range 를 받은
                                           # slice 의 디렉터리에 append-only 로 쌓인다. 기존 파일은
                                           # 하나도 고치지 않는다 — 이 slice 의 range 에서
                                           # 앞선 slice evidence 를 수정한 커밋은 0 이고 신설이 1 이다
                                           # (`git log --diff-filter=M 14686db..HEAD -- reports/evidence/m0/0{a,a2,a3,b,c,d}/`).
  - fixtures/                              # fixture-curator 소유. 이 slice 가 만들지 않는다.
                                           # 단 이 레인이 manifest.yaml 을 건드린 **선언된 예외**가
                                           # 있다 — 자리·근거·검출은 열거하지 않고 정본인
                                           # commands.md 「`C-7b` 매치의 처리」가 갖는다
  - _workspace/**                          # .gitignore 대상. 조사 노트는 읽기만 한다
  - fixtures/** · reports/evidence/m0/0e/fixtures-*.md   # fixture-curator 레인 소유
  - .claude/ 하네스 · CLAUDE.md            # 다른 레인 소유
  - Kotlin/Spring/Python 애플리케이션 코드  # M0 은 문서 slice다
  - bid-vector/ symlink 아래 기존 저장소    # 읽기 전용
  - 활성 OPEN 의 임의 해소                 # 아래 「이 slice 가 닫는 OPEN」 셋 외에는 손대지 않는다
acceptance_commands:
  - "N/A — 문서 slice. 아래 A1~A7 을 checklist.md 로 대조하고 근거 명령은 commands.md 가 갖는다"
rollback: |
    **정본은 `reports/evidence/m0/0e/rollback.md`**(`agent-workflow.md` §6 이 요구하는 파일).
    되풀이하지 않는다 — 같은 사실을 두 자리에 적으면 한쪽이 낡는다.
```

작성: 2026-08-30, spec-writer (v2-slice-pipeline).

---

## 이 slice 가 하는 일

**M0 를 닫는다.** 앞 여섯 slice 가 남긴 **집행 유예 항목**을 집행하고, 활성 `OPEN` 전부에
**담당을 붙여 이월**하며, **M0 완료를 기록**한다. 새 조사도 새 결정도 하지 않는다 —
**결정은 전부 이미 나 있고 집행만 남아 있던 것들**이다(`_workspace/m0-open-decisions/decisions-log.md`
「귀속 정본표」의 `A-53`·`A-56`·`A-57`·`A-09`·`A-59`·`A-58`).

| # | 무엇을 | 근거(결정) | 산출물 |
| --- | --- | --- | --- |
| 1 | **Q1** — `v2-지침서.md` §5 의 Boot 라인을 **3.x → 4.x** | 운영자 결정 2026-08-29 (`A-53`) | `v2-지침서.md` §5 |
| 2 | **Q2** — 면허 **유효기간 축만** versioned policy data 요구에서 제거 | 운영자 결정 2026-08-28 **U-7** + 문면 집행 2026-08-29 (`A-56`) | `v2-지침서.md` §4.2 · `milestone-1.md` 1C |
| 3 | **Q3** — `milestone-0.md` 산출물 목록에서 `legacy-reference-map.md` 제거 | 운영자 결정 2026-08-29 (`A-57`) · 대체 근거는 `ADR 0009` **(d)** | `milestone-0.md` 「산출물」 |
| 4 | **`OPEN-OPS-07` registry 갱신** | 종료 조건(ADR 대안 절 기입)이 `ADR 0005` §3 으로 **이미 충족**(`A-59`) | `capability-map.md` §12 · §12.1 · §12.2 |
| 5 | **ADR 0001~0009 「상태」 줄** | 0D 가 Codex `approve` 로 닫혔다(`df056259`). **채택의 「사용자 명시 승인」은 운영자 2026-08-31** — 정본은 `milestone-0.md` 「승인에 드는 것」의 ADR 행 | `docs/adr/*.md:3` |
| 6 | **이월 목록** — 활성 `OPEN` 마다 담당 slice | `A-58` 이 M0 완료 게이트 항목으로 든다 | `capability-map.md` **§14** |
| 7 | **M0 완료 기록** | `milestone-0.md` 완료 조건 | `milestone-0.md` 「M0 완료 기록」 |

### 이 slice 가 닫는 `OPEN` — 셋뿐이다

| id | 왜 닫히는가 | 남는 것 |
| --- | --- | --- |
| `OPEN-ADR-01` | Q1 이 **선택지 (b) Boot 4.x** 로 답했고, 종료 조건인 **`v2-지침서.md` §5 문면 개정**을 이 slice 가 집행했다. **`ADR 0001` §5 행도 해소로 갱신**했다 — 취소선 + 결정·결정자·종료 조건·남는 축 | **나머지 라이브러리의 4.x 호환 실측 재확인** → **M1 1A**(`v2-지침서.md` §5 의 신설 항목이 소유). **새 `OPEN` 을 만들지 않았다** — 고를 것이 없는 **측정**이고, 같은 형태의 선례가 `ADR 0004`(Flyway → 1A 스모크 빌드)이며, 그 축에 남은 **결정**은 `OPEN-ADR-08` 이 이미 들고 있다. 근거 전문은 `ADR 0001` §5 해소 블록 |
| `OPEN-OPS-07` | 종료 조건 = ADR 대안 절 기입. **`ADR 0005` §3 이 그 기입**이며 0D 가 `approve` 를 받았다. **결정 부재가 아니라 기록 미갱신이었다** | advisory lock 행 → `OPEN-ADR-12` · 3.x→4.x 전제 재확인 → **M1 1A** · 대상 모수(6종 ↔ 9종) → registry 통합 slice |
| `OPEN-QUAL-05` | 종료 조건 = `v2-지침서.md` §4.2 문면 개정. Q2 가 집행했다 | **없다.** U-7 의 「유효기간 미검증 노출」 요구는 `OPEN` 이 아니라 **명세**로 두 문서에 남겼다 |

**`OPEN-ADR-06`(래칫의 클래스/타입 축)은 `OPEN` 으로 유지한다** — 운영자 판단 2026-08-30.
지금 정할 실측 근거가 없고 **1A 가 빌드를 세우며 잰다.** (A) 가 아니라 **(B)/1A** 로 이월한다.

---

## 이 range 에는 세 레인이 있다

셋이 같은 브랜치에 병행해 커밋했고 **이 evidence 는 spec-writer 레인만 다룬다.** 레인 구분과
커밋 집합을 뽑는 법의 정본은 `commands.md` 의 같은 절이다 — **여기서 되풀이하지 않는다.**

**하네스 커밋 `e9bcaab` 이 evidence 규격을 이 slice 진행 중에 바꿨다** — 출력 전문 금지, `핵심 결과`
한 줄, 크기 게이트(evidence 합계 ≤ 산출물). **이 패키지는 그 규격을 따른다**(`commands.md` C-10 이 잰다).

---

## 리뷰 range 를 왜 둘로 나누는가

**0A 의 최종 head 는 어느 Codex 리뷰 range 에도 들지 않았다.**

- 0A 의 유일한 `approve` 는 head `6c6b3a2a` 의 **`model_reasoning_effort=medium` 부수 실행**이고
  **같은 head 의 `high` 실행은 `request_changes`** 다. **0A 자신의 evidence 가 high 쪽을
  「3차 정본」으로 지정**한다(`reports/evidence/m0/0a/checklist.md:197`).
- 그 finding 에 대응한 **라운드 4~6 이 `capability-map.md` 를 네 커밋에서 고쳤고**
  (`0b0c8aa`·`23b9c2a`·`0581e97`·`d140f92`, 합 31 삽입/14 삭제) **0A 에 4차 리뷰가 없다.**
- **0A2 리뷰의 `reviewed_base` 가 그 뒤의 `6af7019`** 이라 그 네 커밋은 0A2 range 밖이다.

### 두 range

| 리뷰 | range | 규모 | 무엇을 닫는가 |
| --- | --- | --- | --- |
| **A** | `6c6b3a2..6af7019` | 10 커밋 · 1,116 삽입 | **`N-3`** — 0A 라운드 4~6 의 `capability-map.md` 31/14 줄 |
| **B** | `14686db..HEAD` | 0E 전부 | 0E 의 개정 넷 · fixture 63 case · 수정 라운드 둘 |

**A 의 head 를 `cd5a456` 이 아니라 `6af7019` 로 잡는다** — `cd5a456` 까지만 잡으면
`6af7019`(0A 라운드 6 evidence 커밋) 자신이 어느 range 에도 안 들고, 0A2 의 range 가
`6af7019..` 로 시작하므로 그 한 커밋이 새 틈이 된다. `6af7019` 를 head 로 두면
**A 와 0A2 의 range 가 맞닿는다.**

### 왜 하나로 잡지 않는가 — 실측

앞선 계약은 base 를 `6c6b3a2` 하나로 잡고 *"이력이 선형이므로 0A2·0A3·0B·0C·0D 의 delta 가
range 에 다시 들어온다"* 를 **비용으로 감수**한다고 적었다. 그 비용을 실제로 쟀다.

- `6c6b3a2..HEAD` 는 **407 커밋 · 200 파일 · 33,343 삽입**이다.
- 그 가운데 구간(`6c6b3a2..14686db`, 384 커밋) 중 **어떤 Codex 리뷰 range 에도 들지 않은
  것은 16 커밋뿐**이고 나머지 **368 은 `approve` 난 range 에 덮여 있다.**
- 그 16 은 세 갈래다 — **① 0A 미리뷰 창 9** · **② slice 종료 커밋 4**
  (`ec115a7`·`48151b9`·`2e669fc`·`ebb9f4c`) · **③ 하네스 커밋 3**
  (`d7b1c10`·`998dc21`·`14686db`).
- **A 는 ① 아홉에 `d7b1c10` 이 딸려 열이다** — 이력이 선형이라 그 하네스 커밋이 창 안에
  있다. 그래서 **리뷰 밖에 남는 것은 여섯**(② 넷 + ③ 의 `998dc21`·`14686db`)이다.

**그러므로 26,000 줄을 다시 넣어 얻는 새 대상이 없다.** 나누면 A 와 B 가 합쳐
**7,160 삽입**이고 **새 대상은 하나도 빠지지 않는다.**

**결정**: 운영자, 2026-08-31. 근거는 위 실측.

### 리뷰 밖으로 남는 것 — 알려진 제한

**② slice 종료 커밋 4 와 ③ 하네스 커밋 둘(`998dc21`·`14686db`), 합 6 은 어느 리뷰에도
들지 않는다.** (③ 의 `d7b1c10` 은 A 가 덮는다.)

- **②는 구조적이다.** 종료 커밋은 그 slice 의 `approve` verdict 를 등재하는 커밋이므로
  **정의상 자기 리뷰의 head 뒤에 온다.** range 를 넓혀 이 일곱을 덮으면 그 확장을 기록하는
  커밋이 다시 밖에 남는다 — 되풀이되는 형태이지 닫히는 틈이 아니다. 내용은 이미 받은
  verdict JSON 과 그 등재이며 산출물의 새 주장이 아니다.
- **남는 ③ 둘은 `.claude/**` 로 모든 slice 의 `in_scope` 밖**이다. 하네스는 심판
  레인 자신의 도구라 그 레인의 리뷰 대상이 아니다.
- 재현: `commands.md` **C-9**.

---

## 이 slice 가 하지 않은 것

- **새 조사를 하지 않았고, 이 slice 가 결정을 내리지도 않았다.** 개정은 전부 운영자 결정의
  집행이다 — 대부분 착수 전에 내려진 것이고, **`ADR 0001`~`0009` 채택 승인 하나만 이 slice
  진행 중(2026-08-31)에 운영자에게 물어 받았다.** 물음과 답은 `milestone-0.md` 「승인에
  드는 것」의 ADR 행이 그대로 싣는다.
- **`OPEN` 을 임의로 해소하지 않았다** — 닫은 셋은 전부 **종료 조건 충족을 확인**해서 닫았고,
  그 확인의 명령은 `commands.md` C-6 에 있다.
- **`docs/adr/**` 의 결정 내용을 건드리지 않았다.** 편집은 **「상태」 줄 아홉**과
  **`ADR 0001` §5 의 `OPEN-ADR-01` 행 하나**뿐이며, 그 행에서도 **미결 당시의 기록
  (결정 필요 사항·선택지·근거·부수 사실)을 지우지 않고 그대로 보존**한 채 해소 블록을
  앞에 얹었다. §1~§4·§6 과 §5 의 나머지 `OPEN` 열둘은 무접촉이다 — `commands.md` C-5a.
- **`data-dictionary.md`·`regression-ledger.md`·앞 여섯 slice 의 evidence 무접촉** —
  `commands.md` C-7b 가 이 레인의 커밋 집합만으로 확인한다. **`fixtures/` 는 무접촉이 아니다** —
  이 레인이 `fixtures/manifest.yaml` 을 건드린 **선언된 예외**가 있다. 그 자리·근거·검출 방법의
  정본은 `commands.md` 의 **「`C-7b` 매치의 처리」**이며 **여기서 열거하지 않는다** — 되풀이하면
  접촉이 늘 때마다 낡는다.
- **네 registry 를 통합하지도, 「활성 총계」를 확정하지도 않았다** — §14.0 이 그 사실을 적고
  담당을 **registry 통합 slice** 로 지목한다.
- **자기 검사 하네스를 만들지 않았다.** 필요한 대조는 `commands.md` 의 명령으로만 남겼다.
