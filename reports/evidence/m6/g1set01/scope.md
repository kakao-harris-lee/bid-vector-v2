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

(없음)

## 하네스 레인 변경 (상시)

- 없음.
