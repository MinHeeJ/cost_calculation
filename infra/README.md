# 실행 설정

개발 기본값은 Compose에 있으며 운영에서는 DB_PASSWORD, DB_USER, DB_NAME 및 published port를 교체한다.
개발용 비밀번호는 운영 비밀값이 아니다. 실제 비밀값은 저장소나 이미지에 넣지 않는다.

실행: docker compose -f infra/docker-compose.yml up --build -d --wait
frontend: http://localhost:3000, backend: http://localhost:8080 (환경변수로 변경 가능).
DB는 호스트 포트를 공개하지 않는다. db → backend Flyway → frontend 순서다.
health API 구현은 별도 모듈에서 제공하며 이 기반 단계는 해당 controller를 소유하지 않는다.

실제 PostgreSQL 통합 테스트는 전용 빈 PostgreSQL 16 DB의 TEST_DATABASE_URL,
TEST_DATABASE_USER, TEST_DATABASE_PASSWORD를 지정하면 실행한다. 미설정 시 DB 연결 전에 skip한다.
SQL 적용 자체는 runner의 throwaway PostgreSQL 검증도 사용한다.

package-lock.json은 self-check가 선택된 npm으로 materialize한 lockfile이다. 수작업으로 작성하지 않는다.
