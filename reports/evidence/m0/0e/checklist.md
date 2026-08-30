# 완료 조건 대조 — M0 / 0E

**대조 대상은 `milestone-0.md` 「완료 조건」 7행이다.** 게이트는 **산출물**에 건다 —
이 파일이 자기에 대해 하는 말은 게이트 항목이 아니다. 각 행의 판정은 **산출물 좌표**와
**`commands.md` 의 명령**으로만 지지된다.

**A1~A6 은 0E 가 다시 재지 않았다.** 그 판정은 각 조건을 소유한 slice 의 **Codex `approve`**
가 갖는다(`commands.md` C-8 이 verdict 전수를 낸다). 0E 가 그 조건을 **깨뜨리지 않았는지**만
확인했고 그 확인 방법을 함께 적는다. **A7 이 0E 의 실제 작업이다.**

---

| # | 완료 조건 (`milestone-0.md`) | 소유 slice · 판정 근거 | 0E 가 깨뜨리지 않았는가 |
| --- | --- | --- | --- |
| **A1** | V2 필수 capability마다 사용자 가치와 acceptance scenario가 있다 | **0A · 0A2 · 0A3** — `docs/discovery/capability-map.md` §1~§10. 마지막 verdict `approve`(0A3 `20f09baf`) | **깨뜨리지 않았다.** 0E 의 `capability-map.md` 변경은 **§12(OPEN registry)와 §14(신설)** 뿐이며 capability 절(§1~§10)에 닿지 않는다. `commands.md` C-4·C-7 의 `git diff` 가 범위를 낸다 |
| **A2** | Kotlin service 범위에 Python 파일/endpoint를 그대로 옮기는 작업 항목이 없다 | **0A 계열** — 같은 verdict | **깨뜨리지 않았다.** 0E 는 작업 항목을 신설하지 않았다. 신설한 §14 는 **`OPEN` 의 담당 지목**이며 구현 항목이 아니다 |
| **A3** | ML 재활용 대상이 모듈 단위로 식별되고 잘라낼 결합이 명시돼 있다. 재활용은 구현 전략으로만 기록한다 | **0A 계열**(ML-11) + **0D** `ADR 0001`·`ADR 0009` — `approve` `df056259` | **깨뜨리지 않았다.** 오히려 **Q3 이 그 기록 위치를 정합시켰다** — `milestone-0.md` 산출물 목록의 `legacy-reference-map.md` 가 `ADR 0009` (d) 와 어긋난 채 남아 있던 것을 제거하고 대체 사유를 기록했다 |
| **A4** | 모든 도메인 숫자의 unit/basis/provenance가 정의되거나 `OPEN`이다 | **0C** — `docs/discovery/data-dictionary.md`. `approve` `7cbdc9bb` | **깨뜨리지 않았다.** `data-dictionary.md` **무접촉**(`commands.md` C-7 이 빈 diff 를 낸다). 0E 가 닫은 셋 중 어느 것도 도메인 숫자의 unit/basis/provenance 축이 아니다 — Boot 라인 · 라이브러리 조사 기입 · 면허 유효기간 범위다 |
| **A5** | 기존 회귀마다 V2의 타입·계약·테스트 중 최소 하나의 예방책이 있다 | **0B** — `docs/discovery/regression-ledger.md`. `approve` `7701556c` | **깨뜨리지 않았다.** `regression-ledger.md` **무접촉**(C-7). `OPEN-REG-05` 는 활성 그대로이며 §14.2 가 담당(**M1 1B**)만 붙였다 |
| **A6** | Python 결과가 정답이 아니라 참고임을 모든 관련 문서가 일관되게 명시한다 | **0A~0D 전체** | **깨뜨리지 않았다.** 0E 가 새로 쓴 문면 어디에도 legacy 산출을 기대값으로 삼는 서술이 없다. §14 의 담당 지목은 **`milestone-N.md` 문면**과 **정본의 「소유」 필드**에서만 왔고, 「유도」인 행은 그 사실을 행마다 표시했다 |
| **A7** | **`OPEN` 결정이 0개이거나 사용자가 명시적으로 다음 단계 진행을 승인했다** | **0E** | 아래 상술 |

---

## A7 상술 — 이 slice 가 실제로 한 것

### A7-1. `OPEN` 0개 경로는 성립하지 않는다

`capability-map.md` §12 머리말이 *"G2·G4·G5의 22건은 다수가 V2 코퍼스나 운영 관측을 선행
조건으로 가지며 **사용자가 지금 답해도 닫히지 않는다**"* 라 적는다. **그러므로 명시 승인만이
유일한 경로**이며, 완료 조건이 그 두 경로를 `또는` 으로 잇는다.

**활성 전수**: `commands.md` **C-1**(네 정본의 registry 전수) · **C-2**(그룹별 셈이 §12
머리말과 맞는가).

### A7-2. 승인의 **대상**이 갖춰졌는가 — 이월 목록

**산출물**: `docs/discovery/capability-map.md` **§14**.

| 요구 | 산출물 좌표 | 대조 |
| --- | --- | --- |
| 활성 `OPEN` **전부**를 담는가 | §14.1 · §14.2 · §14.3 | `commands.md` **C-3** — 「활성인데 미분류」와 「분류인데 비활성」을 양방향으로 낸다. **미분류 1건(`OPEN-ADR-01`)** 이 나오며 그 사유·담당을 §14.1·§14.5-1 이 적는다 |
| **담당 slice** 를 지목하는가 | §14.2 · §14.3 의 `담당` 열 | 행마다 채워져 있다. 지목할 근거가 없는 곳은 **`미지목`** 으로 적고 **선행 조건**을 대신 적었다(예: `OPEN-REG-02` → legacy DB 열람 승인) |
| 지목이 **정본**인지 **유도**인지 구별되는가 | 같은 표의 `지목 성격` 열 | `정본` / `유도` / `갈림` 셋 중 하나가 행마다 있다 |
| `OPEN-DIC-08`~`10` 의 담당이 **유도**임을 밝히는가 | §14.2 표 아래 단락 · §14.3 `OPEN-DIC-09` 행 | 0C 종료 규칙이 요구한 slice 지목이 `data-dictionary.md` §9 의 세 행에 **없다**는 사실을 적고, `milestone-1.md` 문면에서 유도했음을 밝힌다 |
| **(A)** 가 닫혔는가 | §14.1 | `OPEN-ADR-01`(Q1) · `OPEN-OPS-07`(registry 갱신) 닫힘 · `OPEN-ADR-08` 은 종속이 풀려 (B) 로 · `OPEN-ADR-06` 은 운영자 판단으로 (B) 로. **(A) 에 남는 활성 `OPEN` 없음** |
| **비-`OPEN`** 이월이 분리돼 있는가 | §14.4 | `N-1`(`fixtures/manifest.yaml` 부재) · `N-3`(0A 리뷰 range) · `N-4`(ADR 상태 줄) · `N-5`(개정 셋) · `N-6`(완료 게이트) · `F-4` · `F-6` |
| **확정하지 않은 것**이 적혀 있는가 | §14.5 | 6항목 |

### A7-3. 승인의 **경로**가 갖춰졌는가 — 완료 기록

**산출물**: `milestone-0.md` 「M0 완료 기록」.

| 요구 | 대조 |
| --- | --- |
| 여섯 slice 와 각각의 Codex verdict | 표 6행. `reviewed_base … reviewed_head` 와 verdict 파일 경로를 싣는다. 원본 전수는 `commands.md` **C-8** |
| **0A 가 이 slice 리뷰에 포함된다는 사실** | 「단서」 절. 근거 명령은 `commands.md` **C-9**, 계약은 `scope.md` 의 `base_sha: 6c6b3a2…` |
| 이월 목록 지목 | 「이월 목록」 절이 `capability-map.md` §14 를 가리킨다 |
| **「사용자 명시 승인」 자리를 비워 두었는가** | 「사용자 명시 승인」 절 — **(비어 있음)** 으로 남기고 *"이 자리는 사용자가 채운다"* 를 적었다. **0E 가 대신 적지 않았다** |

### A7-4. 닫은 `OPEN` 셋이 **종료 조건 충족**으로 닫혔는가

**임의 해소가 아님을 산출물로 보인다.**

| id | 종료 조건 | 충족의 산출물 좌표 | 명령 |
| --- | --- | --- | --- |
| `OPEN-OPS-07` | ADR 대안 절 기입 | `docs/adr/0005-domain-events-and-outbox.md` **§3** (제목이 `## 3. 대안 — \`OPEN-OPS-07\` 후보의 판정`) | `commands.md` **C-6** — `grep -n '^## 3\.'` 이 그 제목을 낸다 |
| `OPEN-QUAL-05` | `v2-지침서.md` §4.2 문면 개정 | `v2-지침서.md` §4.2 (+ `milestone-1.md` 1C) | `commands.md` **C-4** 의 `git diff` |
| `OPEN-ADR-01` | Boot 라인 결정 | `v2-지침서.md` §5 | 같은 diff. **`ADR 0001` §5 행의 기록 갱신은 남는다**(§14.5-1) |

**해소 사유는 registry 자신에도 적혀 있다** — `capability-map.md` §12.1 「M0 종료 slice 0E
해소 2건」과 §12.2 의 두 행. 그 표가 **닫힌 축과 남는 축을 분리**한다.

### A7-5. 편집 경계

| 경계 | 대조 |
| --- | --- |
| `docs/adr/**` 는 「상태」 줄만 | `commands.md` **C-5** — `git diff -U0` 의 `@@` 헤더 아홉이 전부 3행 하나다 |
| `data-dictionary.md`·`regression-ledger.md` 무접촉 | `commands.md` **C-7** — 빈 diff |
| 앞 여섯 slice 의 evidence 무접촉 | 같은 명령 — 빈 diff |
| `fixtures/` 무접촉 | `commands.md` **C-7** — `git log --oneline 14686db..HEAD -- fixtures` 와 `git diff --name-only … -- fixtures` 가 둘 다 빈 출력. **`ls` 로 재지 않는다** — fixture-curator 레인이 0E 와 병행하므로 시점 의존 관측은 근거가 되지 않는다 |
| 다른 레인의 변경(`CLAUDE.md`·`.claude/`·`fixtures/`)을 커밋에 넣지 않았다 | `commands.md` **C-0** — `git status --porcelain` 에 남아 있고 **C-7** 의 `git diff --name-only 14686db..HEAD` 에 없다 |

---

## 판정

- **A1~A6**: 소유 slice 의 Codex `approve` 가 이미 판정했고 **0E 가 깨뜨리지 않았다.**
- **A7**: **「`OPEN` 0개」는 성립하지 않으므로 「사용자 명시 승인」 경로만 남고,
  그 승인의 대상(`capability-map.md` §14)과 경로(`milestone-0.md` 「M0 완료 기록」)가
  갖춰졌다. 승인 자체는 사용자의 몫이며 이 slice 가 적지 않았다.**
- **남는 것 하나**: `OPEN-ADR-01` 의 `ADR 0001` §5 행 기록 갱신 — 담당 **M1 1A**.
  결정은 났고 registry 문면만 낡았다. `commands.md` **C-3** 이 그 한 건을 기계로 낸다.
