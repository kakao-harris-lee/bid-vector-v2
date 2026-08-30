# Slice 계약 — M0 / 0E · M0 종료

```yaml
milestone: m0
slice: 0e-m0-closeout
base_sha: 6c6b3a2ae658894ab105cec931f796a954e9230a
  # 0A 가 마지막으로 Codex 리뷰를 받은 head. 0E 착수 커밋(14686db)이 아니다 — 사유는 아래
  # 「리뷰 range 를 왜 여기서 잡는가」.
head_sha: 리뷰 시점의 HEAD (= 0E 의 마지막 커밋)
  # 0E 의 커밋 집합은 `git log --oneline 14686db..HEAD` 가 낸다(commands.md C-0).
  # SHA 를 여기 박아 두면 커밋이 늘 때마다 낡으므로 range 로 적는다 —
  # 커밋마다 이 값을 옮겨 적는 장치(선언 SHA 래칫)를 두지 않는다.
in_scope:
  - v2-지침서.md                          # §5(Q1) · §4.2(Q2) 문면 개정
  - milestone-1.md                         # 1C 유효기간 요구(Q2 동기화)
  - milestone-0.md                         # 산출물 목록(Q3) + M0 완료 기록
  - docs/adr/*.md                          # ① 「상태」 줄 아홉 (각 3행)
                                           # ② ADR 0001 §5 의 OPEN-ADR-01 행 — 해소로 갱신
  - docs/discovery/capability-map.md       # OPEN-OPS-07·OPEN-QUAL-05 registry + §14 이월 목록
  - reports/evidence/m0/0e/scope.md         # 이 패키지 셋뿐이다.
  - reports/evidence/m0/0e/commands.md      #   같은 디렉터리의 fixtures-*.md 는
  - reports/evidence/m0/0e/checklist.md     #   fixture-curator 레인 소유다
out_of_scope:
  - docs/discovery/data-dictionary.md      # 0C Codex approve 로 확정 — 읽기 전용
  - docs/discovery/regression-ledger.md    # 0B Codex approve 로 확정 — 읽기 전용
  - docs/adr/** 의 위 둘 이외 전부          # 결정 내용·§1~§4·§6 무접촉.
                                           # §5 도 OPEN-ADR-01 행 하나만이고 나머지 열둘은 무접촉
  - reports/evidence/m0/0a · 0a2 · 0a3 · 0b · 0c · 0d   # 확정된 evidence — 읽기 전용
  - fixtures/                              # fixture-curator 소유. 이 slice 가 만들지 않는다
  - _workspace/**                          # .gitignore 대상. 조사 노트는 읽기만 한다
  - fixtures/** · reports/evidence/m0/0e/fixtures-*.md   # fixture-curator 레인 소유
  - .claude/ 하네스 · CLAUDE.md            # 다른 레인 소유
  - Kotlin/Spring/Python 애플리케이션 코드  # M0 은 문서 slice다
  - bid-vector/ symlink 아래 기존 저장소    # 읽기 전용
  - 활성 OPEN 의 임의 해소                 # 아래 「이 slice 가 닫는 OPEN」 셋 외에는 손대지 않는다
acceptance_commands:
  - "N/A — 문서 slice. 아래 A1~A7 을 checklist.md 로 대조하고 근거 명령은 commands.md 가 갖는다"
rollback: |
    git revert 로 0E 의 커밋 전부를 되돌린다. 그 집합은 `git log --oneline 14686db..HEAD`
    가 낸다(commands.md C-0) — SHA 를 여기 열거해 두면 커밋이 늘 때마다 낡으므로 range 로
    적는다. 애플리케이션 코드·설정·스키마·fixture 변경이 없어 되돌림의 부작용이 없다.
    부분 되돌림도 가능하다(커밋이 개정 단위로 나뉘어 있다). 단 §14 이월 목록은 개정 셋과
    ADR 상태 줄이 만든 사실을 참조하므로, 그것만 남기고 나머지를 되돌리면 §14 가 낡는다.
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
| 5 | **ADR 0001~0009 「상태」 줄** | 0D 가 Codex `approve` 로 닫혔다(`df056259`) | `docs/adr/*.md:3` |
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

`14686db..HEAD` 는 **spec-writer(이 계약) · fixture-curator(`fixtures/**` ·
`reports/evidence/m0/0e/fixtures-*.md`) · 하네스(`.claude/**` · `CLAUDE.md`)** 셋의 커밋을
함께 담는다. 셋이 같은 브랜치에 병행해 커밋했다. **이 evidence 는 spec-writer 레인만
다루며, 그 커밋 집합은 위 `in_scope` 경로로 pathspec 을 걸어 뽑는다**(`commands.md` C-0).
**불변 확인도 그 집합만으로 한다**(C-7) — `14686db..HEAD` 전체로 재면 다른 레인의 변경이
섞여 이 레인이 `fixtures/` 를 건드린 것처럼 읽힌다.

**하네스 커밋 `e9bcaab` 이 evidence 규격을 이 slice 진행 중에 바꿨다** — 출력 전문 금지,
`핵심 결과` 한 줄, 크기 게이트(evidence 합계 ≤ 그 slice 산출물). **이 패키지는 그 규격을
따른다**(`commands.md` C-10 이 크기를 잰다).

---

## 리뷰 range 를 왜 `6c6b3a2` 부터 잡는가

**0A 의 최종 head 는 어느 Codex 리뷰 range 에도 들지 않았다.**

- 0A 의 유일한 `approve` 는 head `6c6b3a2a` 의 **`model_reasoning_effort=medium` 부수 실행**이고
  **같은 head 의 `high` 실행은 `request_changes`** 다. **0A 자신의 evidence 가 high 쪽을
  「3차 정본」으로 지정**한다(`reports/evidence/m0/0a/checklist.md:197`).
- 그 finding 에 대응한 **라운드 4~6 이 `capability-map.md` 를 네 커밋에서 고쳤고**
  (`0b0c8aa`·`23b9c2a`·`0581e97`·`d140f92`, 합 31 삽입/14 삭제) **0A 에 4차 리뷰가 없다.**
- **0A2 리뷰의 `reviewed_base` 가 그 뒤의 `6af7019`** 이라 그 네 커밋은 0A2 range 밖이다.

**그러므로 base 를 `6c6b3a2`(0A 가 마지막으로 리뷰받은 head)로 잡아 그 구간을 이 리뷰가
함께 덮는다.** 근거 명령은 `commands.md` **C-9**.

**알고 받아들이는 비용**: 이력이 선형이므로 `cd5a456` 이전의 어떤 base 를 잡아도
**0A2·0A3·0B·0C·0D 의 delta 가 range 에 다시 들어온다.** 그 다섯은 각각 `approve` 를
받았고(`commands.md` C-8) **이 리뷰의 새 대상은 ① 0A 라운드 4~6 의 `capability-map.md`
31/14 줄과 ② 0E 의 개정 넷**이다.

---

## 이 slice 가 하지 않은 것

- **새 조사·새 결정을 하지 않았다.** 모든 개정이 이미 내려진 운영자 결정의 집행이다.
- **`OPEN` 을 임의로 해소하지 않았다** — 닫은 셋은 전부 **종료 조건 충족을 확인**해서 닫았고,
  그 확인의 명령은 `commands.md` C-6 에 있다.
- **`docs/adr/**` 의 결정 내용을 건드리지 않았다.** 편집은 **「상태」 줄 아홉**과
  **`ADR 0001` §5 의 `OPEN-ADR-01` 행 하나**뿐이며, 그 행에서도 **미결 당시의 기록
  (결정 필요 사항·선택지·근거·부수 사실)을 지우지 않고 그대로 보존**한 채 해소 블록을
  앞에 얹었다. §1~§4·§6 과 §5 의 나머지 `OPEN` 열둘은 무접촉이다 — `commands.md` C-5a.
- **`data-dictionary.md`·`regression-ledger.md`·앞 여섯 slice 의 evidence·`fixtures/` 무접촉** —
  `commands.md` C-7b 가 이 레인의 커밋 집합만으로 확인한다(매치 0).
- **네 registry 를 통합하지도, 「활성 총계」를 확정하지도 않았다** — §14.0 이 그 사실을 적고
  담당을 **registry 통합 slice** 로 지목한다.
- **자기 검사 하네스를 만들지 않았다.** 필요한 대조는 `commands.md` 의 명령으로만 남겼다.
