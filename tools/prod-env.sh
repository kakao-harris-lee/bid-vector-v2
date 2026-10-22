# M7/7F 본편 — 운영 프로필의 **compose 호출 단일 출처**. 실행 파일이 아니라 `source` 로 읽는 조각이다
# (`tools/local-tls-env.sh` 와 같은 형태 — 실행 비트를 주지 않는다).
#
# 로컬 조각과 달리 **값을 하나도 짓지 않는다**: 운영 값은 host 의 env 파일 하나가 주고(저장소 밖),
# 그 경로를 호출자가 `--env-file` 로 넘긴다. 여기서 자리값을 만들면 운영 env 파일에 없는 키가
# 조용히 채워져 「키 집합 등식」 preflight 가 공허해진다.
#
# 덧쓰기 순서가 두 자리로 갈리지 않게 파일 목록만 한 자리에 둔다.

BIDVECTOR_PROD_COMPOSE_FILES=(docker/compose.yaml docker/compose.local-tls.yaml docker/compose.prod.yaml)
BIDVECTOR_PROD_COMPOSE=(docker compose)
for _f in "${BIDVECTOR_PROD_COMPOSE_FILES[@]}"; do
  BIDVECTOR_PROD_COMPOSE+=(-f "$_f")
done
unset _f
