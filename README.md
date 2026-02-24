# Claude Memory MCP Server (Standalone)

Claude Code에서 사용하는 영구 메모리 MCP 서버. PostgreSQL + pgvector 기반으로 벡터 유사도 검색을 지원합니다.

> codism-common 의존성을 제거한 독립 실행 버전입니다.

## 주요 기능

- **벡터 유사도 검색**: Ollama `nomic-embed-text` 모델로 임베딩 생성 → pgvector HNSW 인덱스로 코사인 유사도 검색
- **호스트별 메모리 격리**: 컴퓨터(hostname)별로 메모리가 완전 격리되어 독립적으로 관리
- **키-값 메모리**: category + key 기반 upsert (같은 키면 갱신)
- **구조화된 노트**: 프로젝트별 노트 저장 및 검색
- **Raw SQL 실행**: SELECT(읽기) / INSERT·UPDATE·DELETE(쓰기) 직접 실행

## 기술 스택

- Java 17, Spring Boot 3.2.4
- PostgreSQL + pgvector
- Ollama (nomic-embed-text)
- MCP SDK 0.12.1 (stdio transport)

## MCP 도구 목록

| 도구 | 설명 |
|---|---|
| `memory_save` | 카테고리+키로 메모리 저장 (upsert) |
| `memory_search` | 벡터 유사도 기반 메모리 검색 |
| `memory_list` | 메모리 목록 조회 |
| `memory_delete` | 메모리 삭제 (id 또는 category+key) |
| `note_save` | 구조화된 노트 저장 |
| `note_search` | 벡터 유사도 기반 노트 검색 |
| `db_query` | SELECT SQL 실행 (읽기 전용) |
| `db_execute` | INSERT/UPDATE/DELETE SQL 실행 |

## 사전 요구사항

- Java 17+
- PostgreSQL (pgvector 확장 설치)
- Ollama (`nomic-embed-text` 모델)

```bash
# pgvector 확장 활성화
psql -d claude_memory -c "CREATE EXTENSION IF NOT EXISTS vector;"

# Ollama 모델 다운로드
ollama pull nomic-embed-text
```

## 빌드

```bash
./gradlew bootJar
```

빌드 결과: `build/libs/claude-memory-mcp-standalone.jar`

## Claude Code 설정

`~/.claude.json`의 `mcpServers`에 추가합니다.

### 방법 1: jar 직접 실행

```json
{
  "mcpServers": {
    "claude-memory": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "/path/to/claude-memory-mcp-standalone.jar"]
    }
  }
}
```

### 방법 2: Docker 실행 (권장)

```bash
# Docker 이미지 빌드
docker build --tag claude-memory-mcp-standalone:latest .
```

```json
{
  "mcpServers": {
    "claude-memory": {
      "type": "stdio",
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "--network", "host",
        "--hostname", "<your-hostname>",
        "claude-memory-mcp-standalone:latest"
      ]
    }
  }
}
```

> `--hostname`을 지정하면 해당 호스트명으로 메모리가 격리됩니다. `hostname` 명령어로 현재 호스트명을 확인하세요.

## 슬래시 커맨드 설정

`~/.claude/commands/`에 아래 파일을 생성하면 `/remember`, `/recall`, `/db` 커맨드를 사용할 수 있습니다.

**`~/.claude/commands/remember.md`**
```
memory_save 도구를 사용하여 다음 내용을 메모리에 저장해줘.
카테고리와 키를 적절히 판단해서 지정해줘.

저장할 내용: $ARGUMENTS
```

**`~/.claude/commands/recall.md`**
```
memory_search 도구를 사용하여 다음 내용을 메모리에서 검색해줘.
결과를 보기 좋게 정리해서 보여줘.

검색어: $ARGUMENTS
```

**`~/.claude/commands/db.md`**
```
db_query 도구를 사용하여 다음 SQL을 실행해줘.
SELECT가 아닌 경우 db_execute 도구를 사용해.
결과를 보기 좋게 정리해서 보여줘.

SQL: $ARGUMENTS
```

사용 예시:
```
/remember 프론트엔드 프로젝트 경로는 ~/workspace/my-frontend
/recall 프론트엔드 프로젝트
/db SELECT count(*) FROM memories
```

## CLAUDE.md 설정 (권장)

전역 `~/.claude/CLAUDE.md` 또는 프로젝트 `CLAUDE.md`에 아래 규칙을 추가하면 Claude가 자동으로 메모리를 활용합니다.

```markdown
# Memory 규칙

## MUST: 인증/권한/설정 정보가 필요하면 메모리부터 검색
- API 키, 토큰, 비밀번호, 인증서, 계정 정보 등이 필요한 작업을 할 때 반드시 memory_search를 먼저 호출할 것
- 서버 접속 정보, DB 연결 정보, 외부 서비스 설정 등이 필요하면 반드시 memory_search를 먼저 호출할 것
- 사용자가 언급하는 프로젝트, 설정, 이전 작업 등의 컨텍스트를 모르겠으면 반드시 memory_search를 먼저 호출할 것
- "모르겠다", "정보가 없다"고 답하기 전에 항상 메모리 검색을 시도할 것

## 저장/검색 트리거
- 사용자가 "기억해", "저장해" 등의 표현을 쓰면 memory_save로 MCP 메모리에 저장
- 사용자가 "이전에 뭐였지?", "찾아봐" 등의 표현을 쓰면 memory_search로 MCP 메모리에서 검색
- 사용자가 "~에 있어", "~야", "~거든" 등으로 정보를 알려주면 memory_save로 자동 저장
- 중요한 결정사항이나 설정이 확정되면 자동으로 저장

## 능동적 저장 (사용자 요청 없이도 자동 수행)
작업 중 아래 정보를 발견하면 memory_save로 즉시 저장할 것. 사용자에게 "저장했습니다"라고 간단히 알려줄 것.

- **프로젝트 구조/설정**: 처음 접하는 프로젝트의 기술스택, 경로, 빌드 방법, 주요 설정을 파악했을 때
- **인증/접속 정보**: 코드나 설정 파일에서 DB 접속 정보, API 키, 서버 URL 등을 발견했을 때
- **버그 해결**: 디버깅으로 원인을 찾고 해결했을 때 (문제 증상 + 원인 + 해결법)
- **아키텍처 결정**: 설계 방향이 확정되었을 때 (선택한 방식 + 이유)
- **환경 차이**: 로컬/개발/운영 환경별 다른 설정을 확인했을 때
- **자주 쓰는 명령어**: 빌드, 배포, 테스트 등 반복 사용되는 명령어 조합
- **의존성/호환성**: 라이브러리 버전 충돌, 호환성 이슈를 해결했을 때
```

`CLAUDE.md.example` 파일도 함께 제공됩니다.

## 설정 (application.yml)

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/claude_memory` | DB 접속 URL |
| `DB_USERNAME` | `postgres` | DB 사용자 |
| `DB_PASSWORD` | `postgres` | DB 비밀번호 |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama 서버 URL |
| `OLLAMA_MODEL` | `nomic-embed-text` | 임베딩 모델 |

환경변수로 설정:
```bash
DB_URL=jdbc:postgresql://myhost:5432/claude_memory \
DB_PASSWORD=mypassword \
OLLAMA_URL=http://localhost:11434 \
java -jar claude-memory-mcp-standalone.jar
```

Docker 실행 시:
```bash
docker run -i --rm --network host \
  -e DB_URL=jdbc:postgresql://localhost:5432/claude_memory \
  -e DB_PASSWORD=mypassword \
  claude-memory-mcp-standalone:latest
```

## 자동 스키마 관리

- Hibernate `ddl-auto: update`로 테이블 자동 생성/변경
- `schema-init.sql`로 HNSW 벡터 인덱스 및 host 인덱스 자동 생성
- 앱 시작 시 `host=null` 데이터를 현재 호스트명으로 자동 마이그레이션

## 라이선스

MIT License
