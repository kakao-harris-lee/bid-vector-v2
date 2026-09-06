# commands — M1 / 1C (Qualification, 면허 자격 판정 커널)

실행 명령과 종료 코드. 출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이다(`evidence-pack`
SKILL). 라운드 이력 절은 만들지 않는다 — 아래는 verifier r2 표적 재검증(ready-for-review)
뒤 잔여 non-blocker 처리의 **최종 재실측**이다(초판 수치는 이 문서가 아니라 git log 가 갖는다).

HEAD(최종): **이 커밋 자신**(`git log -1 -- reports/evidence/m1/1c/` 로 확인 — evidence
커밋은 정의상 자기 자신의 해시를 문서에 미리 적을 수 없다, verifier r2 F-9/N-3 가 등재한
구조적 한계와 같은 갈래). base_sha: `4a6ca5c4ee5bb666fe786fbe395c5c00be775e75`.

## Q-0 — `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`

- exit: 0
- 핵심 결과: 격리 worktree(scratchpad, 검증 뒤 `git worktree remove --force` + 잔여 디렉터리
  없음 확인)에서 `clean check` 전건 통과.

## Q-1 — `./gradlew --no-build-cache clean check`

- exit: 0
- 핵심 결과: 9 모듈(+build-logic) 전건 `check` 통과(286 tasks). `qualification` 도메인
  코드(6 파일) 위에서 1A·1A-b 게이트 전부 초록.

## Q-2 — `./gradlew :qualification:test`

- exit: 0
- 핵심 결과: `LicenseEligibilityTest` **26/26**(example — 위협 모델 우회 (1)~(6) 대응 +
  verifier r1 F-1 PROBE A·D·F-2 구성 거부·F-4 PROBE F2·F3 + verifier r2 N-1 PROBE A3·
  N-2 PROBE WS 셋) · `LicenseEligibilityPropertyTest` **5/5**(R-QUAL-06·결측 그룹 폴딩·
  보유 부재 불변식·그룹 순서 무관·보유 0 이면 Eligible 없음).

## Q-3 — `./gradlew :qualification:domainApiTypeGate :qualification:domainSourceReferenceGate :qualification:typeShapeGate :qualification:sizeGate`

- exit: 0
- 핵심 결과: 넷 다 통과. 구현 중 겪은 게이트 회피 넷 — 전부 임계값·정책 파일 무변경:
  (a) `sortedBy` 합성 class 의 `jarContentGate` 위반 → `sorted()` (b) `String.lowercase()`
  의 `Locale`/`Appendable` 참조 → `CharArray` 직접 조립 (c) verifier r1 수정 뒤 detekt
  `TooManyFunctions`(12, 한도 11) → `LicenseGroupFolding.kt` 분리 (d) verifier r2 N-2 —
  `Char.isWhitespace()`(Java `Character.isWhitespace`∪`isSpaceChar` 위임)가 `Locale`/
  `Appendable` 표면에 닿지 않음을 실측(`ArchitectureGateTest` 그대로 초록).

## Q-4 — `./gradlew :app:test --tests '*Conformance*'`

- exit: 0
- 핵심 결과: `SharedKernelCorpusConformanceTest` 21/21 — license-*(002·003·004·005·006·
  007·009·012) authoritative 8 이 dynamic test 로 실행·대조 + dispatch 표 완결성 test +
  1B/1C 두 축의 insufficient-evidence 이월 test 둘. `permsnIndstrytyList` runner 경로는
  authoritative 8 에 그 필드가 없어 여전히 무커버(checklist 알려진 제한 ⑨, verifier r2 N-4).

## Q-5 — `./gradlew qualityBaseline`

- exit: 0
- 핵심 결과: qualification 모듈 실측 갱신, ratchet 위반 없음.

## Q-7 — `./gradlew :build-logic:test`

- exit: 0
- 핵심 결과: 1A 승계 test 스위트 무변경 통과(build-logic in_scope 밖).

## Q-6 — 생략

manifest.yaml 을 편집하지 않았다(`git diff --stat <base>..<head> -- fixtures/manifest.yaml`
0행). scope.md 조건대로 스윕 생략.

## 변이 실측 (한시적 편집, 매 회 원본으로 복원 후 `git status --short` 로 무변경 확인, 커밋 없음)

**최초 구현 라운드(3건)** — 결측 그룹 OR 흩기(2 실패) · 보유 부재 `Ineligible` 접기(2 실패) ·
`missingByGroup` 보유 면허 혼입(3 실패). 전부 원복 확인.

**verifier r1 수정 라운드(2건)** — F-1 회귀(`restrictedSatisfied` 공허 충족 복원, 3 실패:
PROBE A·D + property) · F-4 회귀(`KEY_STRIP_CHARS` 축소, 2 실패: 전각 괄호·나카구로) ·
F-3 재확인(하드코딩 별칭 삽입, `clean check` exit 0 — 게이트 무방어 재현). 전부 원복 확인.

**verifier r2 표적 재검증 처리(2건, 이 커밋에 포함)**

7. **N-1 회귀 재현** — `foldGroups` 의 `satisfiedGroups.isNotEmpty()` 분기와
   `evaluations.any { it.ambiguous }` 분기 순서를 맞바꾼다(ambiguous 를 먼저). 결과:
   **1 실패**(example `실제 충족 그룹이 있으면...PROBE A3` — 기대 `Eligible`이 `Uncertain`
   으로 나옴). 원복 확인.
8. **N-2 회귀 재현** — `isKeyNoise` 의 `c.isWhitespace()` 를 `c == ' '` 로 좁힌다. 결과:
   **3 실패**(전각 공백·NBSP/얇은 공백·수직탭/폼피드 test 셋 — 전부 거짓 `Ineligible`).
   원복 확인.

여덟 변이 모두 조치 뒤 `:qualification:test` 재실행 exit 0(26/5)로 복원을 확인했다.
