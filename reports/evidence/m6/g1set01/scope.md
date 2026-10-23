# M6/G1-SET01 — 상호 매칭 커널 (SET-01) (착수 계약, 2026-10-21, 팀장)

- **근거**: 운영자 결정 2026-10-15(G1 9) · 7A 조정안 G1 행 SET-01: 「상호 매칭 커널(입력 칸은 7C ④)」. 운영자 결정 2026-10-21
  「7B 예외 + G1 커널 병행」 — ML 없이 되는 G1 커널을 7B 와 병렬로 세운다.
- **base**: main `bee7c36f` · worktree `bid-vector-v2-m6-g1set01` · 브랜치 `m6-g1set01/2026-10-21`.
- **입력 정본**:
  - `docs/discovery/capability-map.md` SET-01 절: acceptance 네 줄과 분류 근거 「상호 정규화 정확매치만, 금액을 판정에 쓰지 않는다,
    한 번만 기록되고 덮이지 않는다」. 동반 OPEN `OPEN-SET-03`(정규화 토큰 목록) · `OPEN-SET-05`(재공고 대상 선택).
  - 추적표 SET-01 행(「운영자 상호 필드가 없다」).
  - 추적표 머리 ⓐ 술어 ②.

## 산출물

1. **`settlement` 모듈의 첫 실제 타입**(ADR 0006 D-2 — `ModuleBoundaryAnchor` 를 지운다):
   - **상호 정규화**: 법인 접두 · 접미 토큰 제거 + 공백 제거 + casefold. 토큰 목록은 코드 상수가 아니라 **versioned 정책 데이터**로 둔다.
     목록은 capability 분류 근거가 인용한 legacy 목록을 출처와 함께 옮기고, 그 출처를 evidence 에 적는다. 목록이 충분한가는
     `OPEN-SET-03` 으로 남긴다.
   - **판정 함수**: 시그니처가 **금액을 받지 않는다**(legacy 형태 처리 — 코드로 강제). 입력은 운영자 상호 상태와 결과 쪽 상호 상태다.
   - **결과는 3-state 이상 sealed**: 낙찰(같은 회사) · 패찰(다른 상호) · 미기록(결과 쪽 상호 없음 — `false` 아님) · 판정 안 함(운영자 상호
     미설정).
   - **한 번만 기록되고 덮이지 않는다**: 확정 판정의 불변성을 타입 · 커널 수준에서 표현한다(영속화는 소비 slice 몫 — 아래 4).
2. **「운영자 상호 미설정이면 판정하지 않고 외부 조회도 하지 않는다」** — 커널은 「미설정」을 입력 상태로 받아 판정 안 함을 돌려준다.
   외부 조회를 하지 않는다는 성질은 **조회를 결정하는 술어**로 커널에 둔다(호출부가 그 술어를 지나야 조회한다). 실제 조회 경로는 없다
   (아래 4).
3. **test**:
   - 법인 표기 차이 흡수(정책 데이터의 토큰마다 사례).
   - 동일 금액 · 다른 상호 = 패찰(금액이 시그니처에 없음을 컴파일 실패 하네스 관례로).
   - 낙찰가만 있고 상호 없음 = 미기록.
   - 미설정 = 판정 안 함 + 조회 술어 거짓.
   - 덮어쓰기 불가.
   - 변이에서 RED: 정규화 단계 하나 제거 · 3-state 를 boolean 으로 · 미설정을 패찰로 · 조회 술어 무시. 바꿔치기 변이 + numstat + 통제군
     GREEN.
4. **ⓐ 판정의 정직성** — 추적표 술어 ② 로는 **ⓒ(부분 측정)가 예상된다**:
   - 운영자 상호 필드가 없다. 입력 칸은 7C ④ 이고, 이 slice 는 마이그레이션을 만들지 않는다 — 마이그레이션 레인은 7B 하나다.
   - 낙찰자 상호가 canonical 로 적재되지 않는다. 계약상 `bidwinnrNm` 은 낙찰 목록에만 있고 6G 는 그 끝점을 걷지 않는다. 결정 블록은
     외부 호출을 새로 허용하지 않는다.

   행 본문에 두 빈 자리와 홀더(7C ④ · 낙찰 목록 수집 축 — 새 OPEN 이 필요하면 레인 제안)를 적는다. 의심되면 ⓒ 다.
5. **추적표 · capability**: SET-01 행을 실제 판정대로(부분 측정 + 새 test FQN, 좌표 금지), 집계를 맞춘다. 병렬 레인 g1dec05 가 같은
   추적표 · capability map 을 고친다(DEC-04 정정 · 집계 15/17 · OPEN 셋). **나중 머지 쪽이 정리한다** — 집계 줄은 rebase 없이 머지 시
   재계산하므로 이 slice 는 자기 행과 자기 증감만 적고, 집계 머리 숫자는 머지 직전에 맞춘다.

## 설계 검토 (Phase 2.5, 팀장)

- **(0) 위협 모델**: 방어 —
  - 금액 근접으로 「낙찰」이라 부르는 회귀(legacy 정직 명세)
  - 상호 없음을 패찰로 접기
  - 미설정 상태에서 조회 · 판정
  - 확정 판정 덮어쓰기
  - 사업자번호 · 대표자명 슬롯 생성(P-10 (a))
  - 상호(PII 축 `COMPANY_IDENTITY_CONCEPTS`)를 로그 · evidence 에 실값으로 남김

  방어하지 않음 — 토큰 목록의 완전성(`OPEN-SET-03`) · 재공고 선택(`OPEN-SET-05`, 운영자 결정 대상).
- **(1) 구성**: 결과 sealed · `when` 전수. 정규화는 순수 함수. 정책 데이터는 `*PolicyData` 관례. settlement 는 `:shared-kernel` 만 의존한다.
- **(2) 우회 ≥5**: 산출물 3 의 변이 넷 + 금액 인자 추가(컴파일 실패) + 토큰 목록을 코드 상수로(정책 test RED).
- **(3) 과잉 · 미달**:
  - 하지 않는다: 영속화 · 마이그레이션 · 운영자 상호 입력 · 낙찰 목록 수집 · endpoint · SET-02 · 재공고 규칙.
  - 미달 금지: 금액 미사용 · 3-state · 미설정 무조회 · 불변.

## in_scope / out_scope

- in: `settlement/**` · `config/quality/*.properties`(새 모듈 실제 타입 · 새 test 등재, 넓히는 줄마다 근거) · `build-logic` 의 모듈 이름 test
  (필요할 때만) · `reports/evidence/m6/6e1/acceptance-trace.md` SET-01 행 · `docs/discovery/capability-map.md` SET-01 절(과 새 OPEN 이 있으면
  OPEN 표) · `reports/evidence/m6/g1set01/**`.
- out: 마이그레이션 · `adapters/**` · `app/**` · `workflow/**`(소비자가 없으면 0) · `openapi/**` · `web/**` · `.github/**` · 수집 끝점 · 다른 행의
  판정.

## acceptance

`.github/workflows/ci.yml` 의 `check` job 명령 그대로. `web` · `ml-engine` · `container` 는 입력 변경 0 을 path-limited diff 로 보이면 생략한다.

## rollback

`git restore --source=bee7c36f --staged --worktree -- <in_scope 개별 경로>`(evidence 제외), 새 파일은 삭제. 목록 기계 산출, 마지막 산출물
커밋에서 ①~⑥ 실측.

## 검토 레인

verifier(opus) · code-reviewer(sonnet) 병렬. 표적:
- 금액이 판정에 들어갈 경로 0.
- 3-state 위장 0.
- 미설정 무조회 술어.
- 불변.
- 정책 데이터 출처.
- PII 실값 노출 0.
- ⓐ/ⓒ 가 술어 ② 를 지키는가.

Codex 없음.

## 호스트 빌드

레인 넷 병렬. 모든 Gradle 명령은 `flock ~/.local/bid-vector-build.lock <명령>` 으로 감싼다. 점검 셋은 별도 호출로 먼저 본다.
**00:30~03:00 에는 무거운 작업을 시작하지 않는다.** 6G DB · run-state · collection worktree 무접촉. evidence 커밋 뒤
`leakPatternGate` 를 재실행한다.

## 계약 갱신 이력

**D-G1SET01-1 (2026-10-23, 팀장 — 검토 r1 수령: verifier not-ready(H 2 · M 3, 재작업 1/5) · CR REQUEST_CHANGES(H 2 · M 2 · L 3 · Q 2), 일괄 배치).**
순서: ① 이력 재작성 → ② 산출물 배치 → ③ main `54e2c5e2` 위로 rebase(base 재고정 — 위 「base」 줄은 이 결정으로 `54e2c5e2` 로 읽는다)
→ ④ 마지막 산출물 커밋에서 전체 재측정 → 동결.
- **① CR H-2 — 이력 재작성(운영자 보고 끝, 브랜치 push 0 이므로 외부 흔적 없음).** 실 상호가 든 커밋을 포함해 `bee7c36f..HEAD` 전 범위를
  비파괴 재작성으로 고친다(원 HEAD 는 레인 백업 ref 로 남기고 push 하지 않는다). 술어: 상호 목록 파일을 **저장소 밖**(레인 scratchpad)에 두고
  `git log -p <base>..HEAD` 에 그 목록 적중 0, 목록 자체는 어느 커밋·evidence 에도 적지 않는다. squash 는 쓰지 않는다(PR 브랜치로 원 커밋이 올라간다).
  재작성 뒤 모든 SHA 가 바뀌므로 evidence 의 SHA 는 ④ 에서 한 번에 다시 적는다.
- **② 산출물**
  - **CR H-1 + verifier H-2 — 관문을 타입으로 닫는다.** `Lookup` 이 자기를 만든 정책을 나르고 `judgeAwardOutcome(lookup, winner)` 로 정책 인자를
    지운다. `Lookup` 생성자는 `internal` + `@ConsistentCopyVisibility`. 컴파일 fixture: 모듈 밖 `Lookup(...)` 직접 생성 음성 · `copy` 음성 ·
    정상 경로 양성 · 철자 변형 음성(넷). 회귀 test: 「협동조합 가상해양」 축(정책이 다른 두 정규화가 같은 판정에 들어올 길이 없음).
  - **verifier H-1 — 정확매치를 test 로 잠근다.** 한쪽이 다른 쪽의 접두·접미·부분 문자열인 쌍 셋 이상이 `Lost`. 변이 `==`→접두/포함 RED 실측.
  - **verifier M-2 = CR(전역 부분 문자열 토큰 제거) — 계약 문면대로 접두·접미만 제거한다.** 상호 중간의 토큰은 남긴다(거짓 `Won` 쪽을 줄이는
    방향, legacy 와의 차이는 evidence 에 의도된 어긋남으로 적는다). 「가상주식회사건설 ≠ 가상건설」 test.
  - **verifier M-1 — 금액이 판정에 들어올 길을 구조로 닫는다.** `Published`·`Lookup` 필드 집합 등식 test(금액·수치 필드 0) · `SettledAwardOutcome.Won`
    생성은 판정 함수만(`internal` 생성자 + 컴파일 fixture 음성 하나).
  - **CR L-2**: 빈·공백만 상호는 `Declared`·`Published` 로 만들어지지 않는다(생성 거부 또는 `NotDeclared` 로 접기 — 레인 판단, test 동반).
  - **CR Q-1**(`AlreadySettled` 가 어긋난 재판정을 조용히 버림): 거동은 유지하고 어긋남을 사유로 돌려주는 test 또는 제한 등재 — 레인 판단, 처분표에.
- **장부층**
  - CR M-2: `DomainApiTypeFixtureTest` KDoc 이 거짓이 된 사실을 제한으로 선언(build-logic 은 in_scope 밖, 후속).
  - CR L-1 = verifier low: 「`isWhitespace()` = Python `\s`」 → 「U+0085 하나를 뺀 부분집합(안전 방향)」. zero-width 글자·전각 라틴·괄호 혼용·NFD 미달
    매치는 제한으로(`OPEN-SET-03` 옆).
  - CR L-3: 이 slice 가 편집하는 파일의 좌표를 인용·절 제목으로.
  - **verifier M-3 — container 생략 근거 기각**: app.jar 가 settlement.jar 를 싣는다. ④ 에서 container job 을 재생기로 전부 잰다(네 태그 이미지 ID 기록·복원).
  - evidence 크기 게이트 초과 해소(산출물 이하로).
- **④ 재측정**: check · 변이(신규 포함, 통제군 `--rerun`) · rollback ①~⑥(base `54e2c5e2`) · container 재생 → 동결, 판정 SHA(산출물·evidence) 보고.

**D-G1SET01-2 (2026-10-23, 팀장 — 검토 r2 수령: verifier not-ready H-1r2(재작업 2/5) · CR REQUEST_CHANGES N-1, 일괄 배치).** 판정 대상 산출물 `61c156c3`.
- **CR N-1 — 낙찰/패찰 구별을 잠근다.** `Won() shouldNotBe Lost()` 양방향 · `hashCode`/`toString` 구별 test 한 묶음 + 변이 한 행(두 값을 같게 만드는
  `equals` 본문 바꿔치우기 → RED). X1·X2·N5 의 RED 가 이 등식에 기대는 사실을 변이표에 적는다.
- **verifier H-1r2 — 낙찰자 쪽 정책 동반을 잠근다.** 검증자가 확인한 사례: `decideAwardLookup(Declared("가상해양"), 협동조합 토큰 정책)` 대
  `Published("협동조합 가상해양")` → `Won`. 「판정이 출하 정책으로 낙찰자를 정규화」 변이 RED 실측.
- **verifier M-1r2 = CR N-3**: `Lost` 생성자 `internal` 을 fixture 6 과 같은 음성·양성 쌍 + 변이 한 행으로 잠근다.
- **CR N-2**: `stripWhitespace` 에 U+0085 를 더해 legacy 공백 집합과 맞춘다(test 동반). 「부분집합」 제한 문면은 그에 맞춰 정정.
- **장부층**: verifier L-1(Java 호출자는 `internal` 을 우회 — 저장소 Java 원본 0, 제한 등재) · L-2(container run 23 + uses 4, 새 이미지 ID 는
  app·web 둘 — 문면 정정, 재재생 불필요) · L-3(「의도된 어긋남」 분류의 근거는 D-G1SET01-1 ② 로 인용) · CR Q-1r2(정책 **출처**는 열려 있음 —
  제한 + `OPEN-SET-03` 옆 한 줄) · CR N-3 문면.
- main 이 움직이지 않았으면 rebase 없이 마지막 산출물 커밋에서 check · 변이(통제군 `--rerun`) · rollback ①~⑥(base `54e2c5e2`) 재측정 → 동결.
  container 는 app 입력(settlement)이 바뀌므로 재생기로 다시 잰다.

**D-G1SET01-3 (2026-10-23, 팀장 — 검토 r3 수령: verifier ready-for-review · CR APPROVE(LOW 2), 승인 전 일괄).** 판정 대상 산출물 `dee96d41`.
- CR P-1: 제한 §3-13 정정 — Java 소스는 `sourceLanguageGate` 와 `java.setSrcDirs(emptyList())` 가 이미 막는다. 남는 것은 저장소 밖 JVM 소비자뿐.
- CR P-2: `OperatorTradeName` KDoc 의 「새지 않는다」를 실제 거동(U+0085 만으로 된 상호는 `isNotBlank` 를 지나 `NormalizesToEmpty` 로 간다 — 안전,
  legacy 일치, test 가 단언)에 맞춘다. 주석·KDoc 만, 거동 0.
- **rebase**: main `56bb7474`(#84 7C⑤-0 머지) 위로. 설정 파일 겹침은 합집합, 의존 게이트 면제 수 재계산. base 는 `56bb7474` 로 재고정.
- 재측정: 마지막 산출물 커밋에서 check(찬 clone) · rollback ①~⑥(base `56bb7474`) · 유효성 술어 · container 재생(rebase 로 app 입력이 바뀜).
  변이는 거동 0 이고 settlement 입력이 rebase 로 바뀌지 않으면 재실행하지 않는다(그 사실을 `git diff` 로 보인다).

## 하네스 레인 변경 (상시)

- 없음.
