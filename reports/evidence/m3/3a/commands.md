# 실행 명령과 종료 코드 — M3 / 3A

base `a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f`, 구현 head(r0) `b0b40cd`, **수정 라운드 1 head**
`a9383b7593e1f7f723fec094e49566287ad6c7ea`. verifier r1 판정과 F-1~F-8 은
`_workspace/m3-3a/02_verifier_report.md` 참고. 출력 전문은 남기지 않는다(evidence-pack 스킬
규격) — 핵심 결과 한 줄만.

## 수정 라운드 1 — finding 처리 한 줄씩

| finding | 처리 | 커밋 |
| --- | --- | --- |
| F-1 blocker | COL-06 property test 를 블록 본문(Unit)으로 정정 — 선언 53 대 실행 52 이던 것을 61=61 로 일치시켰다(라운드 안에서 새 test 도 늘어 최종 67=67) | `b72b712` |
| F-2 high | `KonepsFieldContract` 에 `unit` 슬롯 추가, `canonicalize` 가 통화·과세 리터럴 대신 계약 값을 읽는다 | `a9383b7` |
| F-3 medium | `basisMismatch` 런타임 검사 + `ContractViolationAxis.BASIS` 거부 경로 신설 | `a9383b7`(F-2 와 같은 커밋 — 사유는 커밋 메시지) |
| F-4 medium | `KonepsFieldContract`·`KonepsFieldContractRegistry` 생성자 `internal` + `resolveAmount` 가 정책 값 하나(`AmountAxis`)를 받도록 재구성 | `a9383b7` |
| F-5 medium | `NoticeCollected` 에 업무구분·배정예산·낙찰하한율 채움, `Notice`(상태 보유 + `applyEvent`)·`OpeningResult`·`QualificationText` 신설 | `a9383b7` |
| F-6 low | 이 문서 head 갱신, `policy-values.md` "미도착" 문구를 "도착·미승인"으로 정정, `checklist.md` 신설 | 이 커밋 |
| F-7 low | 역방향 파급 grep 을 stem 축약형까지 재실행(아래) — 추가 매치 0건, verifier 확인과 일치 | 이 커밋 |
| F-8 low | 아래 표에 「인용문(grep 출력)」 표기를 명시 | 이 커밋 |

## acceptance_commands 재실행(head `a9383b7`)

### S-0 — 격리 worktree `clean check`
## 2026-09-07T06:26:30Z
- cmd: `git worktree add --detach /tmp/m3-3a-r1-worktree HEAD && (cd /tmp/m3-3a-r1-worktree && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 347 actionable tasks, 전부 실행. `git worktree remove --force` 로 정리, 잔여 디렉터리 없음 확인.

### S-1 — 저장소 루트 `clean check`
## 2026-09-07T06:20:00Z
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 338 actionable tasks. `testShapeGate`(하네스 신설 게이트) 포함 전부 통과.

### S-2 — 도메인 test
## 2026-09-07T06:26:30Z
- cmd: `./gradlew :procurement:test`
- exit: 0
- 핵심 결과: 67 tests, 0 failed, 0 error(`procurement/build/test-results/test/*.xml` 집계). 선언 `@Test` 도 67 — F-1 이 잡은 discover 누락이 재발하지 않는다.

### S-3 — 도메인 게이트 5종
## 2026-09-07T06:26:30Z
- cmd: `./gradlew :procurement:domainApiTypeGate :procurement:domainSourceReferenceGate :procurement:typeShapeGate :procurement:sizeGate :procurement:cpdCheck`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — `unit` 슬롯 추가로 `KonepsFieldContract` 멤버 수가 늘었지만 `typeShapeGate`(타입 멤버 30 한도) 그대로 통과.

### S-4 — app conformance (조건부: authoritative ≥ 1 아님 → 기존 corpus 회귀 확인으로 기록)
## 2026-09-07T06:26:30Z
- cmd: `./gradlew :app:test --tests '*Conformance*'`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — 기존 다섯 축 회귀 무손상.

### S-5 — quality baseline
## 2026-09-07T06:26:30Z
- cmd: `./gradlew qualityBaseline`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`.

### S-6 — mutation sweep(조건부: manifest 편집 시) → 해당 없음
- `fixtures/manifest.yaml` 은 이 slice out_of_scope(curator 레인 소유)이고 편집하지 않았다.

## 리뷰 요청 조건 점검(head `a9383b7`)

### clean-tree 게이트(in_scope 경로 개별 인자 + 양성 대조)
## 2026-09-07T06:26:30Z
- cmd: `git status --porcelain -- procurement/ shared-kernel/src/main/kotlin/bidvector/sharedkernel/NoticeRound.kt shared-kernel/src/main/kotlin/bidvector/sharedkernel/Provenance.kt shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt shared-kernel/src/test/kotlin config/quality/gate-tests.properties app/src/test/kotlin/bidvector/app/conformance decision/src/test/kotlin/bidvector/decision/FloorShortfallKernelTest.kt reports/evidence/m3/3a`
- exit: 0, 출력 없음(clean).
- 양성 대조: `procurement/.../NoticeId.kt` 에 한 줄 추가 → `M procurement/...` 관측 → `git checkout --` 복구 → 다시 clean.

### 역방향 파급 grep — 전체 파일명 + stem 축약형(F-7)
## 2026-09-07T06:26:30Z
- cmd(전체 파일명): `grep -rn 'Provenance\.kt:[0-9]\|Rate\.kt:[0-9]' --include='*.md' --include='*.kt' --include='*.properties' . | grep -v /build/`
- 결과: 매치 1건 — `reports/evidence/m1/1a/commands.md:818`(**인용문** — `BidRate.kt:8`가 이 문서 §"역방향 파급 grep" 절이 자기 grep 출력을 적어 둔 것이지, `Provenance.kt`·`Rate.kt`를 가리키는 좌표가 아니다). 실제 영향 없음.
- cmd(stem 축약형): `grep -rn 'Provenance:[0-9]\|[^A-Za-z]Rate:[0-9]' --include='*.md' . | grep -v /build/ | grep -v _workspace/m3-3a`
- exit: 1(매치 없음) — verifier r1 §「6」재실행 결과와 일치.

### secret 스캔
## 2026-09-07T06:26:30Z
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m3/3a/`
- exit: 0(매치 있음), 확인: 전부 이 문서·curator `fixtures-koneps-collection.md`(다른 레인 소유)가 **자기 grep 패턴을 인용한 것**이거나
  `API token quota exceeded`(legacy 관찰 문구 인용)뿐 — 비밀값 아님(F-8 「인용문」 표기 대상).
## 2026-09-07T06:26:30Z
- cmd: `git diff b0b40cd..HEAD -- procurement/ | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 1(매치 없음) — 이번 라운드 코드 diff 자체에는 벤진 매치조차 없다.

판단이 갈린 지점·알려진 제한·병렬 레인 경계 검사는 `checklist.md`로 옮겼다(F-6).
