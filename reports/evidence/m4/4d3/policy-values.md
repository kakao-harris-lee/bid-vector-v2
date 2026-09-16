# 정책 값 — M4/4D-3

N/A — 이 slice 는 새 정책 값(버전드 데이터·환경 설정)을 신설하지 않는다. `Weight`의
[0,1] 경계·`PredictionDiagnostics`의 0 이상 경계는 계약(`prediction.proto` `Weight`·
`uint32` 필드)이 이미 고정한 수학적 범위이지 운영 판단으로 바뀔 수 있는 정책 값이
아니다(매직 넘버 금지 규율의 대상이 아님 — Rate/Uncertainty 등 기존 값 타입의 `init`
경계와 같은 성격).
