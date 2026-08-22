# Dear Jolly 🎀

> *Write to Jolly ✏️, feel jolly 🎀*

## 소개 ✨

**Dear Jolly** 는 하루를 영어로 기록해 Jolly에게 편지를 보내는 앱입니다.
오늘 있었던 일을 영어 편지로 적어 Jolly 에게 전달하면, AI 가 표현을 다듬어 **교정과 팁이 담긴 답장**을 보내줍니다.

답장이 도착한 편지에는 **우표**가 한 장씩 붙습니다.
잘 쓴 날에만 주어지는 게 아니라 꾸준히 쓴 만큼 쌓이기 때문에, 우표첩은 그대로 하루하루를 이어온 기록이 됩니다.
완벽한 문장을 쓰려 애쓰기보다 오늘의 이야기를 계속 남겨보는 것은 어떨까요?


## Architecture ✨

```mermaid
flowchart TB
    App["📱 iOS · Android<br/>Dear Jolly App"]

    subgraph EC2["☁️ AWS EC2"]
        direction TB
        Caddy["🔒 Caddy<br/>HTTPS 종단 · 블루그린 전환"]

        subgraph Runtime["애플리케이션"]
            direction LR
            Blue["Spring Boot · blue<br/>:8081"]
            Green["Spring Boot · green<br/>:8082"]
        end

        subgraph Store["데이터 · 호스트 볼륨에 저장, 매일 백업"]
            direction LR
            MySQL[("MySQL 8.4<br/>편지 · 계정 · 피드백")]
            MinIO[("MinIO<br/>우표 이미지")]
        end
    end

    subgraph Ext["🌐 외부 연동"]
        direction LR
        OAuth["Kakao · Apple<br/>소셜 로그인"]
        LLM["LLM API<br/>편지 피드백 생성"]
    end

    App ==>|"HTTPS"| Caddy
    Caddy ==>|"active"| Blue
    Caddy -.->|"standby"| Green
    Blue --> MySQL
    Blue --> MinIO
    Blue -.-> OAuth
    Blue -.->|"비동기"| LLM
    App -.->|"우표 이미지"| MinIO

    classDef client fill:#FFF1F4,stroke:#E28BA0,stroke-width:1.5px,color:#3B2B31
    classDef gate   fill:#FFF6E5,stroke:#E0A82E,stroke-width:1.5px,color:#3B3327
    classDef app    fill:#EAF1FF,stroke:#5B87F5,stroke-width:1.5px,color:#26334D
    classDef data   fill:#E9F7EF,stroke:#46A56E,stroke-width:1.5px,color:#23402F
    classDef ext    fill:#F4EFFC,stroke:#8E72D6,stroke-width:1.5px,color:#332A47

    class App client
    class Caddy gate
    class Blue,Green app
    class MySQL,MinIO data
    class OAuth,LLM ext

    style EC2 fill:#FCFCFD,stroke:#AEB5BF,stroke-width:1.2px,stroke-dasharray:6 4,color:#4A5058
    style Ext fill:#FCFCFD,stroke:#AEB5BF,stroke-width:1.2px,stroke-dasharray:6 4,color:#4A5058
    style Runtime fill:#F6F9FF,stroke:#C4D3F5,stroke-dasharray:3 3,color:#4A5058
    style Store fill:#F5FBF7,stroke:#C1E2CE,stroke-dasharray:3 3,color:#4A5058
```

| 구성 | 역할 |
| --- | --- |
| 🔒 **Caddy** | TLS 를 종단하고, 배포할 때 blue ↔ green 사이에서 트래픽을 무중단 전환한다 |
| ⚙️ **Spring Boot** | 소셜 로그인부터 JWT 발급까지 인증 책임 전부를 서버가 진다 |
| 🗄️ **MySQL · MinIO** | 컨테이너가 아니라 호스트 디렉터리에 저장돼 컨테이너를 지워도 남는다. 매일 자동 백업 |
| 🤖 **LLM API** | 편지 피드백은 **비동기**다. 작성 응답은 즉시 돌아가고, 답장이 완료된 뒤 우표가 부여된다 |


## Tech Stack ✨

| 구분 | 사용 기술 |
| --- | --- |
| Language | Java 21 (Eclipse Temurin) |
| Framework | Spring Boot 3.5.7 (Web, Validation, Actuator) |
| ORM | Spring Data JPA · Hibernate |
| Database | MySQL 8.4 (`utf8mb4` / `Asia/Seoul`) |
| Migration | Flyway |
| Authentication | Spring Security · JWT (jjwt) · Kakao · Apple OAuth |
| Storage | MinIO (S3 호환, 우표 이미지) |
| Model | OpenAI API |
| Testing | JUnit 5 · Mockito · RestAssured · Testcontainers · H2 |
| Build | Gradle 8.14.3 (Wrapper) |
| Infra | AWS EC2 · ECR · CloudFormation, Docker · Docker Compose, Caddy |
| CI/CD | GitHub Actions (블루그린 무중단 배포) |
| API Docs | Swagger (springdoc-openapi) |
| IDE | IntelliJ |

## 문서 ✨

| 문서 | 내용 |
| --- | --- |
| [docs/RUN.md](docs/RUN.md) | **로컬 실행 방법 · 환경변수 · 테스트** |
| [docs/기능명세.md](docs/기능명세.md) | 도메인 규칙 · 처리 흐름 |
| [docs/API명세.md](docs/API명세.md) | 요청/응답 계약의 정본 |
| [docs/ERD.md](docs/ERD.md) | 데이터 모델의 정본 |
| [infra/README.md](infra/README.md) | 구축 · 배포 · 운영 |
