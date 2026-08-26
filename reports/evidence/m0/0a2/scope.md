# Slice 계약 — M0 / 0A2 결정 통합 패스

```yaml
milestone: m0
slice: 0a2-decision-integration
base_sha: 6af7019996ec26903c7b531ed7ede12c22cf176e
head_sha: TBD  # 산출물 커밋 후 후속 커밋으로 기입 (0A와 같은 방식)
in_scope:
  - docs/discovery/capability-map.md
  - reports/evidence/m0/0a2/
  - reports/evidence/m0/0a/commands.md   # H-2 정정 주석 한정. 재작성 금지, append 방식
out_of_scope:
  - Kotlin/Spring/Python 애플리케이션 코드 (M0 금지 사항)
  - 다른 M0 산출물: regression-ledger(0B), data-dictionary(0C), ADR(0D), fixtures/manifest.yaml
  - reports/evidence/m0/0a/ 의 commands.md 외 파일 (0A는 동결. 읽기 전용 참조)
  - .claude/ 하네스
  - 운영 DB query, 실제 외부 API 호출
  - bid-vector/ symlink 아래 기존 저장소 수정 (읽기 전용)
acceptance_commands:
  - "N/A — 문서 slice. 아래 A1~A7을 checklist.md로 대조"
rollback: "N/A — 문서 산출물은 git revert로 복구. 0A head 6af7019로 되돌릴 수 있다"
```

작성: 2026-08-26, v2-slice-pipeline Phase 1

## 배경

0A(capability map)는 `6af7019`에서 동결됐다. Codex 독립 리뷰 3라운드와 verifier 재검증
3라운드를 거치는 동안 **"미해결 OPEN을 확정 서술이 선점한다"는 계열(계열 A)이 네 번
재발**했고, 매 라운드 새 표면을 열 때마다 신규 잔존이 나왔다. 근본 원인은 문장이 아니라
**활성 OPEN 66건 자체**였다 — 미결정 쟁점이 살아 있는 한 어떤 서술이 그중 하나를 무심코
확정할 표면이 계속 남는다.

따라서 인스턴스 추적을 중단하고 결정을 받았다. 운영자가 **29건을 확정**했고, 그 과정에서
**capability 누락 1건**과 **상호작용 모델(pull) 확정**이 나왔다. 이 slice는 그 결정들을
산출물에 한 번에 반영한다.

## acceptance 항목

- **A1.** 확정된 OPEN이 **결정 근거(누가·언제·무엇으로)와 함께** 해소되고, §12 집계와
  본문 인용이 일치한다. 해소된 id는 결번 사유와 함께 §12.1에 기록된다.
- **A2. 미결 OPEN은 하나도 임의 해소되지 않는다.** 결정 기록에 없는 항목을 이 패스가
  닫으면 slice 실패다. 해소 전후 id 집합을 대조해 증명한다.
- **A3.** 신규 capability(운영자가 입찰 목록을 검색하고 투찰가를 요청하는 경로)가
  `V2 필수` 분류·사용자 가치·acceptance scenario·분류 근거를 갖는다.
- **A4.** capability 재분류 5건이 근거와 함께 반영되고 §10 축별 표·집계가 일치한다.
- **A5.** verifier 라운드 6 발견 **M-4·M-5**가 해소되고, **H-2**가 정정된다.
- **A6.** 불변: 분류 줄 형식 위반 0, 중복 capability id 0, `V2 필수` 전건에 사용자 가치와
  acceptance scenario 존재, secret 스캔 통과.
- **A7.** 결정 기록이 `reports/evidence/m0/0a2/decisions.md`로 **커밋된다.**
  `_workspace/`는 gitignore 대상이라 감사 추적이 저장소에 남지 않는다. 결정은 산출물의
  근거이므로 커밋되어야 한다.

## 반영 대상 (운영자 결정, 2026-08-26)

원본: `_workspace/m0-open-decisions/decisions-log.md` (결정 29건, 근거·파급 포함)

1. **M0 차단 8건** — ML-04(정산 관측 시각 canonical fact) · NUM-04 · QUAL-06 · DEC-08 ·
   OPS-07 · ML-01(ML 커널 8개 경계) · OPS-05(브로커 없음) · STR-01(예산 basis = 기초금액)
2. **M1 차단 17건** — QUAL-01/02/08 · COL-02 · DEC-01/02/03/04/05/06/07/09 · SET-02 ·
   STR-03/05/06/07 (+ QUAL-03이 QUAL-01에 따라 닫힘, STR-11이 STR-07에 따라 닫힘)
3. **OPEN-NOTI-02** — at-most-once 확정. `OPEN-STR-08`도 함께 정리
4. **상호작용 모델 pull 확정** — 주기 감시는 제안까지, 투찰가는 on-demand
5. **capability 재분류 5건** — NOTI-06/NOTI-02/NOTI-05 강등 후보, NOTI-10 유지·강화,
   DEC-02 산출 시점 변경
6. **신규 capability** — 목록 검색 + 투찰가 요청 경로

## 주의

- `OPEN-STR-01`은 결정 결과 **`legacy-defect` 판정**이 됐다(운영자 의도는 기초금액인데
  legacy는 추정가격과 비교). 0B regression ledger 인계 항목으로 §13에 기록한다.
- `OPEN-DEC-09`는 **"실행되는 미적용 상태"** 단서가 결정의 일부다. 단서 없이 반영하면
  legacy 결함(선언과 실행의 불일치)을 이식한다.
- `OPEN-DEC-07`·`OPEN-DEC-03`·`OPEN-OPS-07`은 **결정은 확정, 실행 대기**다. 해소로
  기록하되 실행 대기 상태를 명시한다.
- `OPEN-OPS-03`은 **입력만 부분 확정**(1인 운영)이며 임계는 미결이다. 해소하지 마라.
- capability `OPS-06`(queue topology 배포 게이트)은 브로커 존재를 전제한다. 브로커 없음
  결정으로 **재정의 또는 폐기 대상**이며, 이 slice에서 그 사실을 기록한다.
