# Slice 계약 — M0 / 0D ADR

```yaml
milestone: m0
slice: 0d-adr
base_sha: 998dc21754e39efa425c3cfad9d5b7d5540ad1ba   # 0A3 종료 + 하네스 커밋 직후의 HEAD
head_sha: 79700e1   # 이 갱신 커밋의 직전 커밋. 「head_sha와 range」 절 참조
in_scope:
  - docs/adr/                       # 신설. ADR 0001~0009
  - reports/evidence/m0/0d/         # 이 패키지
out_of_scope:
  - docs/discovery/capability-map.md      # 0A/0A2/0A3 Codex approve로 확정 — 읽기 전용
  - docs/discovery/regression-ledger.md   # 0B Codex approve로 확정 — 읽기 전용
  - reports/evidence/m0/0a/ · 0a2/ · 0a3/ · 0b/   # 확정된 evidence — 읽기 전용
  - docs/discovery/data-dictionary.md     # 0C 소관. 이 slice는 만들지 않는다
  - docs/discovery/legacy-reference-map.md # 존재하지 않는다. 생성 여부가 ADR 0009의 OPEN 자체다
  - fixtures/                              # fixture-curator 소유
  - 활성 OPEN 50건 전부 — 해소하지 않는다
      # capability-map.md §12 활성 45건 + regression-ledger.md §9 OPEN-REG 5건.
      # 예외는 OPEN-OPS-07 하나이고, 그것도 「ADR 대안 절 기입」이라는 종료 조건으로만 닫는다.
  - Kotlin/Spring/Python 애플리케이션 코드   # M0은 문서 slice다
  - .claude/ 하네스
  - bid-vector/ symlink 아래 기존 저장소 수정 (읽기 전용)
acceptance_commands:
  - "N/A — 문서 slice. 아래 A1~A8을 checklist.md로 대조"
rollback: "N/A — 문서 산출물은 git revert로 복구. 애플리케이션 코드·설정·스키마 변경이 없다"
```

작성: 2026-08-28, spec-writer (v2-slice-pipeline).

---

## 이 slice가 만드는 것

`milestone-0.md` §"Slice 0D"가 **최소 9건의 결정**을 요구한다. 그 9건을 ADR 파일 9개로
쓴다. `milestone-0.md` 「산출물」 블록이 이름을 지정한 **네 파일은 그 이름과 번호를
유지**한다.

| ADR | 파일 | `milestone-0.md` §"Slice 0D" 결정 |
| --- | --- | --- |
| 0001 | `docs/adr/0001-target-architecture.md` | 1 — Kotlin modular application + Python ML engine, service 재작성 / ML 재활용 경계 |
| 0002 | `docs/adr/0002-money-rate-basis.md` | 3 — 금액/rate/basis 표현 |
| 0003 | `docs/adr/0003-contract-transport.md` | 4 — gRPC/Protobuf 내부 계약 |
| 0004 | `docs/adr/0004-persistence-and-events.md` | 5 — DB 및 migration 도구 |
| 0005 | `docs/adr/0005-domain-events-and-outbox.md` | 6 — domain event / outbox / notification 방식 |
| 0006 | `docs/adr/0006-gradle-modules.md` | 2 — Gradle module과 의존 방향 |
| 0007 | `docs/adr/0007-test-pyramid-and-ratchet.md` | 7 — 테스트 pyramid와 mutation 대상 |
| 0008 | `docs/adr/0008-frontend-disposition.md` | 8 — React UI 재사용/재작성/후속 여부 |
| 0009 | `docs/adr/0009-ml-reuse-provenance.md` | 9 — ML 재활용 출처 기록 위치 (**OPEN**) |

**ADR 번호가 결정 번호와 어긋난다.** ADR 번호는 부여 순서이고 결정 번호는
`milestone-0.md`의 열거 순서다. 위 표가 둘의 대응이며, 각 ADR 머리에도 같은 대응이 있다.

**`0004-persistence-and-events.md`의 이름과 내용**: 이름은 `milestone-0.md` 「산출물」이
지정한 것이고 "events"를 포함하지만, **같은 문서의 결정 목록은 5(DB·migration)와
6(event/outbox/notification)을 별개 결정으로 센다**. 최소 9건을 만족하려면 둘을 한 파일에
합칠 수 없다. 지정된 이름을 바꾸지 않고 **0004는 결정 5를, 0005는 결정 6을** 담으며
0004가 그 자리에서 0005를 가리킨다.

---

## `head_sha`와 range

`head_sha`의 정본은 이 파일 머리의 yaml 하나다 — 값을 두 곳에 두지 않는다.

이 파일은 **자기 커밋의 diff를 서술하지 않는다.** yaml의 `head_sha`는 **이 파일을 쓴
가장 최근 커밋의 직전 커밋**이며, 그 뒤에 오는 커밋(`commands.md`·`checklist.md`)은 이
값을 갱신하지 않는다 — 뒤 커밋의 경로가 전부 `in_scope`(`docs/adr/` 또는
`reports/evidence/m0/0d/`) 안이라 **"in_scope 밖 경로가 없다"는 판정이 뒤 커밋에 낡지
않기 때문**이다. 확인하는 명령은 `commands.md` **C-2**다. Codex 리뷰의 `reviewed_head`는
리뷰 요청 시점의 HEAD이며 이 값과 다를 수 있다.

---

## acceptance — A1~A8

| # | 요구 | 출처 | 대조 |
| --- | --- | --- | --- |
| **A1** | ADR 9건이 `docs/adr/`에 있고, 각 파일이 `상태`·`맥락`·`결정`·`대안`·`결과` 절을 갖는다. 번호는 유일하고 연속이다 | `milestone-0.md` §"Slice 0D", agent-workflow §7 | `commands.md` C-3 |
| **A2** | `OPEN-OPS-07`의 종료 조건이 충족된다 — **OPS-21 표의 후보 전부**가 어느 ADR의 「대안」 절에 채택/불채택 판정과 사유를 갖고 등장한다 | `capability-map.md` §12 G6 `OPEN-OPS-07` 행 | `commands.md` C-7 |
| **A3** | 판정 기준이 "최신 버전"이 아니라 **M1에서 고정할 Kotlin 2.x + Spring Boot 3.x 조합과의 호환**이다 | `v2-지침서.md` §5, `decisions.md` `OPEN-OPS-07` | `checklist.md` §2 |
| **A4** | **활성 OPEN을 해소하지 않았다.** `capability-map.md`·`regression-ledger.md`가 무변경이고, ADR이 활성 OPEN이 소유한 쟁점을 확정 서술로 선점하지 않는다 | 팀 리드 지시, `0a2/checklist.md` §10.1 형태 7 | `commands.md` C-4 · `checklist.md` §3 |
| **A5** | 이미 확정된 운영자 결정(`OPEN-ML-01` · `OPEN-OPS-05` · 두 갈래 전략)을 **재확정하지 않고 인용**한다 | `reports/evidence/m0/0a2/decisions.md` | `commands.md` C-6 · `checklist.md` §4 |
| **A6** | ML 재활용 래칫 사전 조사가 수행됐고, 그 수치가 **명령으로 재현**된다. 미달 모듈의 M5 판단 재료가 남는다 | `milestone-0.md` §"추가 조사 항목" | `commands.md` C-5 |
| **A7** | ADR이 인용한 legacy 경로·행 범위가 `ed4b06c`에 실재한다 | `0b`·`0a3`가 세운 인용 규약 | `commands.md` C-6 |
| **A8** | `in_scope` 밖 경로 변경이 없고, 공백 오류·secret이 없다 | agent-workflow §6 | `commands.md` C-2 · C-8 |

---

## 이 slice가 닫는 것 — `OPEN-OPS-07`

`capability-map.md` §12 G6가 이 항목의 성격을 적는다 — *"이 항목은 결정이 아니라
**실행으로 닫힌다**"*, 종료 조건은 **ADR 대안 절 기입**이며 *"ADR 작성은 **0D 소관**"*이다.
조사는 `_workspace/m0-open-decisions/ops07-library-survey.md`로 완료돼 있다.

**조사 노트를 옮겨 적지 않았다.** `_workspace/`는 gitignore 대상이라 evidence가 아니므로,
ADR에는 **그 ADR의 판정을 떠받치는 사실만** 출처와 함께 옮기고 나머지는 남기지 않았다.
노트가 `확인 불가`로 적은 것은 ADR에서도 **확인하지 않았다**고 적는다.

**후보가 어느 ADR로 갈렸는가**:

| OPS-21 「필요」 행 | 후보 | 판정을 적은 ADR |
| --- | --- | --- |
| transactional outbox / 스케줄러 | Spring Modulith 이벤트 외부화 · JobRunr · db-scheduler | **0005** |
| advisory lock 추상화 | Spring Integration JDBC lock registry · ShedLock | **0005** |
| 재시도·backoff·circuit breaker·rate limiter | Resilience4j | **0005** |
| 아키텍처 규칙 강제 | ArchUnit · Konsist · Detekt | **0007** |
| 관측 | Micrometer | **0005** |

C-7이 이 대응을 스캐너로 확인한다 — 후보 이름이 그 ADR의 「대안」 절 안에 있고
채택/불채택 표시를 갖는지.

---

## 이 slice가 새로 등록하는 `OPEN`

**11건을 신설했다.** 근거가 없는 자리를 확정 서술로 채우는 대신 등록한다 —
`milestone-0.md`가 *"모호한 항목은 기존 Python 구현을 정답으로 채우지 말고 `OPEN`으로
남겨 사용자 결정을 요청한다"*고 요구하고, `0a2` §12.1이 *"신설은 임의 해소의 반대
방향이며 A2 위반이 아니다"*를 세웠다.

**등록 위치는 ADR 파일 안이며 `capability-map.md` §12가 아니다** — 그 파일은
`out_of_scope`(Codex approve로 확정)라 이 slice가 쓰지 못한다.
`regression-ledger.md`의 `OPEN-REG-01`~`05`가 같은 형태로 자기 문서에만 있고
*"`capability-map.md` §12와의 통합이 필요하며"*라 적은 선례를 따른다.
**§12 통합은 후속 slice의 일이다.**

| ID | 질문 | ADR | 성격 |
| --- | --- | --- | --- |
| `OPEN-ADR-01` | Spring Boot 세대 — OSS 지원이 끝난 3.5로 고정 / 4.x 이동 / 상용 지원 전제 중 무엇인가 | 0001 | M1 버전 고정이 소유. 조사가 `판정 보류`로 남긴 것 |
| `OPEN-ADR-02` | `v2-지침서.md` §2.1의 ML 앵커에 `app/domain/`을 추가하는가 | 0001 | 8개 커널 전부가 §2.1 앵커 밖이다 |
| `OPEN-ADR-03` | `v2-지침서.md` §3.2의 "업무 판정"이 **ML 승격 게이트 판정**을 포함하는가 | 0001 | 포함이면 `ml_release/gate.py`가 이식 대상에서 빠진다 |
| `OPEN-ADR-04` | `predictors/`의 규칙표 4파일이 **수학 커널**인가 **정책 데이터**인가 | 0001 | 후자면 Kotlin `decision`의 정책 테이블로 간다 |
| `OPEN-ADR-05` | `award_landing_ladder.py`의 업무 술어 호출이 §3.2 위반인가 학습 코퍼스 필터인가 | 0001 | 같은 술어가 두 목적에 쓰인다 |
| `OPEN-ADR-06` | 래칫 축에 **클래스/타입 크기**를 추가하는가 | 0007 | 추가하지 않으면 mixin 분해가 파일 래칫의 우회로로 남는다 |
| `OPEN-ADR-07` | mutation 도구 — Kotlin에서 무엇을 쓰는가 | 0007 | `OPEN-OPS-07` 조사 범위 밖. 미조사 |
| `OPEN-ADR-08` | Detekt 버전 경로 — 안정판 / alpha / 정식 대기 중 무엇인가 | 0007 | M1이 고정할 Kotlin 버전에 종속. 조사가 `판정 보류` |
| `OPEN-ADR-09` | V2 기간 중 운영자는 **어느 화면**으로 시스템을 쓰는가 | 0008 | `capability-map.md`에 UI 축이 없다 |
| `OPEN-ADR-10` | **ML 재활용 출처 기록 위치** — `legacy-reference-map.md` 통합 vs slice별 `reports/evidence/` | 0009 | **운영자 결정 사항**(2026-08-22 지시) |
| `OPEN-ADR-11` | ML 호출의 **동기 RPC 예산** — deadline·재시도·fail-open 금지의 구체값 | 0003 | M2가 소유. 값의 근거가 아직 없다 |

**`OPEN-ADR-09`·`OPEN-ADR-10`은 운영자 결정을 직접 요청하는 항목**이고 나머지 9건은
후속 마일스톤이 실행·측정으로 닫는다.

---

## 알려진 제한

1. **Codex 리뷰 레인은 legacy 인용을 재현하지 못한다** — 리뷰어 worktree에 `bid-vector`가
   없다. C-5·C-6의 검증은 이 레인과 verifier가 진다. 0A3 §7과 같은 상태다.
2. **`OPEN-OPS-07` 조사의 미확인 7건(U-1~U-7)을 이 slice가 해소하지 않았다.** 각 ADR의
   「확인하지 않은 것」 절이 그대로 옮긴다. 특히 `Kotlin 버전 호환 공식 서술 부재`(U-1)는
   **"호환된다"가 아니라 "그 축의 서술이 없다"**로만 쓴다.
3. **`Spring Integration JDBC lock registry`는 조사되지 않았다.** OPS-21 표의 후보인데
   조사 노트의 대상 9종에 없다. ADR 0005가 그 사실을 적고 후보로 남긴다 — **불채택이
   아니라 미조사다.**
4. **래칫 측정은 크기 축만 재고 결합도 축을 재지 않았다.** `v2-지침서.md` §5는 fan-in/out,
   public API 수, 순환 의존, duplicate helper를 함께 재라 요구한다. 이 slice는 파일·함수
   줄 수와 import 결합만 실측했다. 나머지 축은 M1 래칫 구현이 소유한다.
5. **수정 라운드 1의 두 발견은 같은 뿌리를 갖는다** — `F-1`은 **조사 노트의 오지목을
   정본 대조 없이 옮긴 것**이고, `F-4`는 **활성 registry와 대조하지 않고 하류 서술을 옮긴
   것**이다. 둘 다 *"옮긴 곳이 맞는지 정본에서 확인"*이 빠진 자리이며, 그 확인을
   `commands.md` **C-9.6**·**C-10**이 이제 명령으로 낸다. **C-9.6은 인접성을 판정하지
   못한다** — 목록만 내고 읽는 것은 사람이다.
6. **`commands.md`의 출력은 「선언 SHA 트리에서 다시 뜬다」까지만 보장된다.** C-11이
   그것을 26쌍에 대해 실행으로 잰다. **C-11이 재지 않는 것**: `checklist.md`·이 파일의
   인라인 블록, 각 블록 아래 **판정 산문의 참·거짓**, 그리고 C-11 자신.
   **`diff=0`은 「기록이 옳다」가 아니라 「기록이 그 트리에서 다시 뜬다」이다.**
7. **선언 SHA를 올리면 그 SHA에서 전부 다시 떠야 한다.** 라운드 2의 두 medium이 그
   규칙을 어긴 자리에서 났다 — 일부만 다시 뜨면 **같은 파일 안의 두 블록이 서로
   어긋난다**(C-9.3 ↔ C-9.6). C-11이 이제 그 어긋남을 한 번에 낸다.
8. **`legacy-reference-map.md`를 만들지 않았다.** 그 파일의 존재 여부 자체가
   `OPEN-ADR-10`의 선택지 한쪽이므로, 만들면 그 `OPEN`을 임의 해소하는 것이 된다.

---

## 갱신 이력

**라운드 이력은 이 파일 하나에 모은다.** `checklist.md`·`commands.md`에는 라운드
이력·자기평가 산문을 넣지 않는다 — 0A3이 스물두 라운드를 태운 뒤 후속에 남긴 권고다
(`0a3/checklist.md` §30).

| 라운드 | 내용 |
| --- | --- |
| 최초 | ADR 0001~0009 신설, `OPEN-ADR-01`~`11` 등록, evidence 패키지 작성 |
| `446aba5` | **secret 스캔(C-8)이 잡은 것을 고쳤다** — ADR 0004가 legacy 기본 `DATABASE_URL` 원문을 인용했고 그 문자열이 자격증명 형태(`user:password@host`)다. `agent-workflow.md` §6이 생성물에 남기지 못하게 하는 부류라 **값을 지우고 경로·행과 확인 명령을 가리키게** 바꿨다. 주장은 그대로다 |
| `c731399` | **ADR의 근거 포인터를 그 수를 실제로 내는 블록으로 맞췄다.** 세 자리가 존재하지 않는 출력을 가리켰다 — 0001 §4.2가 C-5.2에 「분해/allowlist 양쪽 재료」가 있다고 적었으나 그 블록은 함수 전수와 결합 축만 낸다. 그 주장을 지우고 이 ADR이 파일별 판정을 하지 않는다는 것을 적었다. mixin 크기와 `OPEN` 후보 실측은 `commands.md` **C-5.2d**·**C-5.2c**를 신설해 그리로 보냈다 |
| **수정 라운드 1** — verifier `not-ready`(medium 4 · low 4) | |
| `fb79029` | **F-1 · F-4 · F-6.** ADR 0005가 `OPS-13`을 「재시도·서킷브레이커」로 두 자리에서 잘못 지목했다 — 정본(`capability-map.md`)의 `OPS-13`은 **「설계 래칫」**이고 OPS-21 재시도 행의 대응은 `OPS-08 (a)(c)`·`COL-03`뿐이다. **조사 노트가 같은 오지목을 갖고 있었고 그것을 옮긴 것**이며, 정본이 이긴다. ADR 0001 §4.1이 활성 **`OPEN-ML-06`**의 쟁점을 확정 서술로 선점한 것(**계열 A**)은 **해소하지 않고 관계를 적었다.** 같은 부류를 전수로 훑어 인접 넷(`OPEN-SET-06`·`OPEN-OPS-02`·`OPEN-OPS-08`·`OPEN-ML-05`)에도 관계만 더했다. ADR 0007 Konsist 행이 떨어뜨린 조사 노트의 한정(`pushed_at`)을 되돌렸다 |
| `779266f` | **F-2 · F-3 · F-5 · F-7 · F-8.** C-3이 스캐너의 3줄을, C-5.4가 `DATABASE_URL` 6행 중 3행을 뺐다 — 둘 다 복원(마스킹 유지). C-9.4의 후행 공백을 `paste`로 없앴고, C-9.5의 「glob 순서」 주장을 `sort`로 대체했으며, C-1 주석의 라운드 이력을 이 파일 소유로 되돌렸다. **C-9.6·C-10을 신설**해 `F-4`·`F-1`이 요구한 전수 훑기의 자리를 만들었다. **함께 들어간 것**: C-8 출력 블록의 행 추가와 주석 재작성, `checklist.md` §9 신설 — 둘 다 라운드 2에서 되돌렸다 |
| **수정 라운드 2** — verifier 레인 A `ready-for-review` / 레인 B `not-ready`(medium 2 · low 3) | |
| `b925b91` | **B2-F3 · B5-F4 · B5-F5.** C-3n·C-6n이 `T`를 재할당해 **문서에서 그대로 복사해 돌리면 실패**했다(`99855e7`부터의 존치 결함) — 음성 fixture를 별도 변수로 옮겼다. C-10 출력 자리의 라벨 3줄을 `echo`가 내게 했다. **`commands.md`의 「명령과 출력만 담는다」가 거짓이 돼 있어** 라운드 서술·검증자 지목을 전부 걷어냈고 `checklist.md` §9(라운드 절)도 지웠다. **함께**: 브랜치가 0C 레인과 공유돼 `base...HEAD` 범위에 그 slice 경로가 섞이므로 C-2·C-4·C-8을 **이 slice의 커밋이 건드린 경로** 측정으로 바꿨고, C-1이 untracked를 세지 않게 해 선언 SHA 트리에서 재현되게 했다 |
| `79700e1` | **B2-F1 · B2-F2.** 앞 라운드가 선언 SHA를 올리며 **일부 블록만 다시 떴다** — C-9.3이 `fb79029`에서 27행인데 22행이 기록돼 같은 파일 C-9.6과 어긋났고, C-8 기록은 **어느 트리에서도 재현되지 않았다.** **26쌍 전부를 `b925b91` 트리에서 다시 떴다**(실제로 바뀐 것은 다섯, 나머지 스물하나는 이미 축어 일치). 머리를 블록별 선언 SHA와 실행 계약으로 다시 쓰고, **C-11**(선언 SHA 트리 대조 하네스)을 신설했다 |

**이력 절은 자기가 속한 커밋의 diff나 파일 목록을 주장하지 않는다.** 이미 커밋된 SHA만
지목하고, 그 커밋이 무엇을 바꿨는지는 `git show --stat --format='' <SHA>`에 맡긴다.
