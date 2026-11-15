# motif-backend

## 설정 파일

`app/src/main/resources/`

| 파일 | 커밋 | 용도 |
|---|---|---|
| `application.yaml` | O | 공통 설정. 기본 프로필을 local로 지정 |
| `application-local.yaml` | X | 각자 PC의 DB·Redis 접속 정보 |
| `application-local.yaml.example` | O | 위 파일의 틀. 복사해서 `application-local.yaml`로 만들고 값을 채운다 |
| `application-prod.yaml` | O | 운영용. 값은 `${DB_PASSWORD}`처럼 자리만 두고 서버 환경 변수로 넣는다 |

처음 받았을 때:

```bash
cp app/src/main/resources/application-local.yaml.example app/src/main/resources/application-local.yaml
```

`application-local.yaml`의 `motif.jwt.secret`은 32바이트 이상 아무 문자열로 채운다.
`motif.crypto.secret-key`(계좌·주민번호 암호화 키)는 아래 명령 결과로 채운다.

```bash
python -c "import os,base64;print(base64.b64encode(os.urandom(32)).decode())"
```

## API 확인

| 방법 | 위치 |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` (운영에서는 꺼짐) |
| IntelliJ HTTP Client | `http/*.http` 열고 ▶ 실행, 환경은 `local` 선택 |

로그인 후 받은 `accessToken`을 Swagger 우측 Authorize에 넣으면 인증 필요한 API도 호출 가능.
포트를 바꿨다면 `http/http-client.private.env.json`(커밋 안 됨)에 `baseUrl`을 덮어쓴다.
