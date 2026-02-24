# Claude Memory MCP Server (Standalone)

Claude Code에서 사용하는 **영구 메모리 MCP 서버**입니다. 대화가 끝나도 기억이 유지되며, 다음 세션에서도 이전 컨텍스트를 자동으로 활용할 수 있습니다.

PostgreSQL + pgvector 기반으로 벡터 유사도 검색을 지원하며, 외부 라이브러리 의존 없이 독립 실행 가능합니다.

## 어떤 문제를 해결하나요?

Claude Code는 세션이 끝나면 대화 내용을 잊습니다. 이 MCP 서버를 연결하면:

- 프로젝트 설정, API 키, DB 접속 정보 등을 한 번만 알려주면 **영구적으로 기억**합니다
- "지난번에 뭐였지?" 같은 질문에 **이전 컨텍스트를 검색**하여 답할 수 있습니다
- 디버깅 해결 과정, 아키텍처 결정 등을 **자동으로 저장**하여 지식이 축적됩니다
- **벡터 유사도 검색**으로 정확한 키워드를 몰라도 의미 기반으로 관련 기억을 찾습니다

## 아키텍처

```
Claude Code ──stdio──▶ MCP Server (Java) ──▶ PostgreSQL + pgvector (영구 저장)
                              │
                              ▼
                        Ollama (임베딩 생성)
```

- **MCP 프로토콜**: stdio transport (표준 입출력으로 Claude Code와 통신)
- **벡터 검색**: Ollama의 `nomic-embed-text` 모델로 768차원 임베딩 생성 → pgvector HNSW 인덱스로 코사인 유사도 검색
- **호스트 격리**: 컴퓨터(hostname)별로 메모리가 완전 격리되어 독립적으로 관리
- **Fallback**: Ollama 연결 실패 시 LIKE 검색으로 자동 전환 (임베딩 없이도 동작)

## MCP 도구 목록

| 도구 | 설명 | 주요 파라미터 |
|---|---|---|
| `memory_save` | 카테고리+키로 메모리 저장 (같은 키면 갱신) | `category`, `key`, `value`, `metadata`(선택) |
| `memory_search` | 벡터 유사도 기반 메모리 검색 | `query`, `category`(선택), `limit`(기본 10) |
| `memory_list` | 메모리 목록 조회 | `category`(선택), `limit`(기본 50) |
| `memory_delete` | 메모리 삭제 | `id` 또는 `category`+`key` |
| `note_save` | 구조화된 노트 저장 | `title`, `content`, `project`(선택), `tags`(선택) |
| `note_search` | 벡터 유사도 기반 노트 검색 | `query`, `project`(선택), `limit`(기본 10) |
| `db_query` | SELECT SQL 실행 (읽기 전용) | `sql` |
| `db_execute` | INSERT/UPDATE/DELETE SQL 실행 | `sql` |

## 빠른 시작 (Quick Start)

### 1단계: 사전 요구사항 설치

#### PostgreSQL + pgvector

```bash
# Ubuntu/Debian
sudo apt install postgresql postgresql-contrib

# pgvector 확장 설치 (PostgreSQL 버전에 맞게)
sudo apt install postgresql-16-pgvector  # PostgreSQL 16인 경우

# macOS (Homebrew)
brew install postgresql@16 pgvector
```

#### 데이터베이스 생성

```bash
# PostgreSQL에 접속하여 DB 및 확장 생성
sudo -u postgres psql

# psql 프롬프트에서:
CREATE DATABASE claude_memory;
\c claude_memory
CREATE EXTENSION IF NOT EXISTS vector;
\q
```

#### Ollama 설치 및 모델 다운로드

```bash
# Ollama 설치 (Linux)
curl -fsSL https://ollama.com/install.sh | sh

# macOS
brew install ollama

# 임베딩 모델 다운로드
ollama pull nomic-embed-text

# Ollama 서버 실행 (기본 포트 11434)
ollama serve
```

#### Java 17

```bash
# Ubuntu/Debian
sudo apt install openjdk-17-jdk

# macOS
brew install openjdk@17
```

### 2단계: 프로젝트 빌드

```bash
git clone https://github.com/jaebum7396/claude-memory-mcp-standalone.git
cd claude-memory-mcp-standalone
./gradlew bootJar
```

빌드 결과: `build/libs/claude-memory-mcp-standalone.jar`

### 3단계: Claude Code에 MCP 서버 등록

`~/.claude.json` 파일을 열어 `mcpServers` 항목을 추가합니다.

> `~/.claude.json`이 이미 있으면 `mcpServers` 키만 추가/병합하세요. 파일이 없으면 아래 내용 전체를 새로 생성합니다.

#### 방법 A: jar 직접 실행

```json
{
  "mcpServers": {
    "claude-memory": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "/절대경로/claude-memory-mcp-standalone/build/libs/claude-memory-mcp-standalone.jar"],
      "env": {
        "DB_URL": "jdbc:postgresql://localhost:5432/claude_memory",
        "DB_USERNAME": "postgres",
        "DB_PASSWORD": "your-password",
        "OLLAMA_URL": "http://localhost:11434"
      }
    }
  }
}
```

#### 방법 B: Docker 실행 (권장)

```bash
# 프로젝트 루트에서 Docker 이미지 빌드
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
        "--hostname", "my-computer",
        "-e", "DB_URL=jdbc:postgresql://localhost:5432/claude_memory",
        "-e", "DB_PASSWORD=your-password",
        "-e", "OLLAMA_URL=http://localhost:11434",
        "claude-memory-mcp-standalone:latest"
      ]
    }
  }
}
```

> **`--hostname` 설정**: 메모리는 hostname별로 격리됩니다. Docker 컨테이너는 매 실행마다 hostname이 바뀌므로, `--hostname`으로 고정값을 지정해야 이전 메모리를 계속 사용할 수 있습니다. 터미널에서 `hostname` 명령어를 실행하여 현재 컴퓨터명을 확인하세요.
>
> **`--network host`**: 컨테이너가 호스트의 네트워크를 직접 사용합니다. PostgreSQL과 Ollama가 localhost에서 실행 중이라면 이 옵션이 필요합니다.

### 4단계: 동작 확인

Claude Code를 재시작하면 MCP 서버가 자동으로 연결됩니다.

```
# Claude Code에서 테스트
> /remember 이것은 테스트 메모리입니다
> /recall 테스트
```

MCP 도구가 인식되면 `memory_save`, `memory_search` 등의 도구가 사용 가능해집니다.

## 슬래시 커맨드 설정

`~/.claude/commands/` 디렉토리에 아래 3개 파일을 생성하면 `/remember`, `/recall`, `/db` 커맨드를 사용할 수 있습니다.

```bash
# 디렉토리 생성
mkdir -p ~/.claude/commands
```

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

### 사용 예시

```bash
# 메모리 저장
/remember 프론트엔드 프로젝트 경로는 ~/workspace/my-frontend

# 메모리 검색
/recall 프론트엔드 프로젝트

# DB 직접 조회
/db SELECT count(*) FROM memories
/db SELECT category, memory_key, memory_value FROM memories ORDER BY created_at DESC LIMIT 10
```

## CLAUDE.md 설정 (권장)

`CLAUDE.md`에 메모리 규칙을 추가하면 Claude가 슬래시 커맨드 없이도 **자동으로** 메모리를 활용합니다.

전역 설정(`~/.claude/CLAUDE.md`)에 추가하면 모든 프로젝트에 적용되고, 프로젝트별 `CLAUDE.md`에 추가하면 해당 프로젝트에서만 적용됩니다.

아래 내용을 복사하여 `CLAUDE.md`에 붙여넣으세요. (`CLAUDE.md.example` 파일로도 제공됩니다.)

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

### 설정 전/후 비교

**설정 전** (매 세션마다 반복):
```
사용자: DB 접속 정보가 뭐였지?
Claude: 죄송합니다, DB 접속 정보를 알 수 없습니다. 알려주시겠어요?
```

**설정 후** (자동으로 기억 활용):
```
사용자: DB 접속 정보가 뭐였지?
Claude: [memory_search 호출]
        DB 접속 정보: jdbc:postgresql://localhost:5432/mydb, user: admin
```

## 환경변수 설정

모든 설정은 환경변수로 오버라이드할 수 있습니다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/claude_memory` | PostgreSQL 접속 URL |
| `DB_USERNAME` | `postgres` | DB 사용자명 |
| `DB_PASSWORD` | `postgres` | DB 비밀번호 |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama API 서버 URL |
| `OLLAMA_MODEL` | `nomic-embed-text` | 임베딩 모델명 |

## 데이터베이스 스키마

앱 시작 시 Hibernate가 테이블을 자동 생성하며, `schema-init.sql`이 벡터 인덱스를 자동 생성합니다. 수동 설정은 불필요합니다.

### memories 테이블

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT (PK) | 자동 증가 |
| `host` | VARCHAR(255) | 호스트명 (격리 키) |
| `category` | VARCHAR(100) | 카테고리 (예: project, config, auth) |
| `memory_key` | VARCHAR(255) | 고유 키 |
| `memory_value` | TEXT | 저장된 값 |
| `metadata` | TEXT | 추가 메타데이터 (JSON) |
| `embedding` | vector(768) | 벡터 임베딩 |
| `created_at`, `updated_at` | TIMESTAMP | 생성/수정 시각 |
| `del_yn` | CHAR(1) | 소프트 삭제 플래그 |

- `(host, category, memory_key)` 유니크 제약 → 같은 키로 저장하면 값이 갱신(upsert)
- HNSW 벡터 인덱스로 코사인 유사도 검색 가속

### notes 테이블

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT (PK) | 자동 증가 |
| `host` | VARCHAR(255) | 호스트명 (격리 키) |
| `project` | VARCHAR(100) | 프로젝트명 |
| `title` | VARCHAR(500) | 노트 제목 |
| `content` | TEXT | 노트 내용 |
| `tags` | TEXT | 태그 (JSON 배열) |
| `embedding` | vector(768) | 벡터 임베딩 |

## 트러블슈팅

### MCP 서버가 연결되지 않는 경우

1. `~/.claude.json`의 jar 경로가 **절대 경로**인지 확인
2. Claude Code를 완전히 종료 후 재시작
3. `java -jar /path/to/claude-memory-mcp-standalone.jar`를 터미널에서 직접 실행하여 에러 확인

### "Embedding failed, falling back to LIKE search" 경고

Ollama 서버에 연결할 수 없을 때 나타납니다. 벡터 검색 대신 텍스트 LIKE 검색으로 자동 전환되므로 **기능은 정상 동작**합니다.

- Ollama가 실행 중인지 확인: `curl http://localhost:11434/api/tags`
- 모델이 다운로드되었는지 확인: `ollama list`
- 환경변수 `OLLAMA_URL`이 올바른지 확인

### Docker에서 "Connection refused" 에러

PostgreSQL이나 Ollama가 localhost에서 실행 중이라면 `--network host` 옵션이 필요합니다. Docker의 기본 네트워크에서는 호스트의 localhost에 접근할 수 없습니다.

### 이전 세션의 메모리가 검색되지 않는 경우

hostname이 달라졌을 수 있습니다. Docker로 실행할 경우 `--hostname`을 고정값으로 설정하세요.

```bash
# 현재 호스트명 확인
hostname

# DB에서 호스트별 메모리 수 확인
/db SELECT host, count(*) FROM memories WHERE del_yn = 'N' GROUP BY host
```

## 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.4 |
| MCP SDK | 0.12.1 (stdio) |
| PostgreSQL | 14+ (pgvector 필요) |
| Ollama | 최신 (nomic-embed-text) |
| MapStruct | 1.5.5 |
| Lombok | 최신 |

## 라이선스

MIT License - 자유롭게 사용, 수정, 배포할 수 있습니다.
