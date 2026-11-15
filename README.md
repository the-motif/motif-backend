# motif-backend

## 설정 파일

`app/src/main/resources/`

| 파일 | 커밋 | 용도 |
|---|---|---|
| `application.yaml` | O | 공통 설정, 기본 프로필 local |
| `application-local.yaml` | X | 각자 PC의 DB·Redis 접속 정보 |
| `application-local.yaml.example` | O | local 파일의 틀, 복사 후 값 채움 |
| `application-prod.yaml` | O | 운영용, 값은 `${DB_PASSWORD}` 형태로 서버 환경 변수 주입 |

처음 받았을 때

```bash
cp app/src/main/resources/application-local.yaml.example app/src/main/resources/application-local.yaml
```

| 키 | 값 |
|---|---|
| `motif.jwt.secret` | 32바이트 이상 아무 문자열 |
| `motif.crypto.secret-key` | 계좌·주민번호 암호화 키, 아래 명령 결과 |
| `motif.admin.email`·`password` | 첫 관리자 계정, 기동 시 없으면 생성 |

```bash
python -c "import os,base64;print(base64.b64encode(os.urandom(32)).decode())"
```

## API 확인

| 방법 | 위치 |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` (운영에서는 꺼짐) |
| IntelliJ HTTP Client | `http/*.http` 열고 ▶ 실행, 환경 `local` 선택 |
| OpenAPI 명세 | `docs/openapi.json` (Swagger Editor·swift-openapi-generator 입력용) |

- 인증 필요한 API: 로그인 응답의 `accessToken` → Swagger 우측 Authorize에 입력
- 포트 변경 시: `http/http-client.private.env.json`(커밋 안 됨)에 `baseUrl` 덮어씀
- 명세 갱신: 앱 실행 후 `/v3/api-docs` 응답으로 `docs/openapi.json` 교체 (API 변경 시)

## 테스트

```bash
./gradlew test
```

- Docker 실행 중이면 가능 (`application-local.yaml`·docker compose 불필요)
- MySQL·Redis: Testcontainers가 기동·삭제
- 설정: `app/src/test/resources/application-test.yaml`
- 통합 테스트: `@IntegrationTest` 사용
