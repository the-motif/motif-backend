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
