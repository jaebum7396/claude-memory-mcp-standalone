package ai.codism.memory.mcp;

import ai.codism.memory.dto.MemoryDto;
import ai.codism.memory.dto.NoteDto;
import ai.codism.memory.service.MemoryService;
import ai.codism.memory.service.NoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class McpServerConfig {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);

    private final MemoryService memoryService;
    private final NoteService noteService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String hostname;
    private McpSyncServer mcpServer;

    public McpServerConfig(MemoryService memoryService, NoteService noteService,
                           JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                           @Qualifier("hostname") String hostname) {
        this.memoryService = memoryService;
        this.noteService = noteService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.hostname = hostname;
    }

    @PostConstruct
    public void start() {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(objectMapper);

        mcpServer = McpServer.sync(transport)
                .serverInfo("claude-memory", "1.0.0")
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(List.of(
                        memorySaveTool(),
                        memorySearchTool(),
                        memoryListTool(),
                        memoryDeleteTool(),
                        noteSaveTool(),
                        noteSearchTool(),
                        dbQueryTool(),
                        dbExecuteTool()
                ))
                .build();

        log.info("MCP Server started with 8 tools (host={})", hostname);
    }

    @PreDestroy
    public void stop() {
        if (mcpServer != null) {
            mcpServer.close();
        }
    }

    // ==================== Memory Tools ====================

    private McpServerFeatures.SyncToolSpecification memorySaveTool() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "category": { "type": "string", "description": "카테고리 (예: project, decision, config, todo)" },
                    "key": { "type": "string", "description": "고유 키" },
                    "value": { "type": "string", "description": "저장할 값" },
                    "metadata": { "type": "string", "description": "추가 메타데이터 (JSON 문자열, 선택)" }
                  },
                  "required": ["category", "key", "value"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new Tool("memory_save", "메모리에 키-값 데이터를 저장합니다 (같은 category+key면 업데이트)", schema),
                (exchange, args) -> {
                    String category = str(args, "category");
                    String key = str(args, "key");
                    String value = str(args, "value");
                    String metadata = str(args, "metadata");
                    MemoryDto saved = memoryService.save(hostname, category, key, value, metadata);
                    return result("저장 완료: [%s] %s = %s (id=%d)".formatted(
                            saved.getCategory(), saved.getMemoryKey(), saved.getMemoryValue(), saved.getId()));
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification memorySearchTool() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "query": { "type": "string", "description": "검색어 (의미 기반 벡터 검색)" },
                    "category": { "type": "string", "description": "카테고리 필터 (선택)" },
                    "limit": { "type": "integer", "description": "최대 결과 수 (기본 10)" }
                  },
                  "required": ["query"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new Tool("memory_search", "메모리에서 의미 기반으로 검색합니다 (벡터 유사도 검색)", schema),
                (exchange, args) -> {
                    String query = str(args, "query");
                    String category = str(args, "category");
                    int limit = intVal(args, "limit", 10);
                    var results = memoryService.vectorSearch(hostname, query, category, limit);
                    if (results.isEmpty()) return result("검색 결과 없음");
                    String text = results.stream()
                            .map(r -> "[%s] %s = %s (id=%d, 유사도=%.2f)".formatted(
                                    r.category(), r.key(), r.value(), r.id(), r.similarity()))
                            .collect(Collectors.joining("\n"));
                    return result("검색 결과 %d건:\n%s".formatted(results.size(), text));
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification memoryListTool() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "category": { "type": "string", "description": "카테고리 필터 (선택, 없으면 전체)" },
                    "limit": { "type": "integer", "description": "최대 개수 (기본 50)" }
                  }
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new Tool("memory_list", "메모리 목록을 조회합니다", schema),
                (exchange, args) -> {
                    String category = str(args, "category");
                    int limit = intVal(args, "limit", 50);
                    List<MemoryDto> results = memoryService.listByCategory(hostname, category);
                    if (results.size() > limit) results = results.subList(0, limit);
                    if (results.isEmpty()) return result("메모리 없음");
                    String text = results.stream()
                            .map(m -> "[%s] %s = %s (id=%d)".formatted(
                                    m.getCategory(), m.getMemoryKey(), m.getMemoryValue(), m.getId()))
                            .collect(Collectors.joining("\n"));
                    return result("메모리 %d건:\n%s".formatted(results.size(), text));
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification memoryDeleteTool() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "id": { "type": "integer", "description": "삭제할 메모리 ID" },
                    "category": { "type": "string", "description": "카테고리 (key와 함께 사용)" },
                    "key": { "type": "string", "description": "삭제할 키 (category와 함께 사용)" }
                  }
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new Tool("memory_delete", "메모리를 삭제합니다 (id 또는 category+key)", schema),
                (exchange, args) -> {
                    Long id = longVal(args, "id");
                    if (id != null) {
                        memoryService.delete(id);
                        return result("삭제 완료: id=%d".formatted(id));
                    }
                    String category = str(args, "category");
                    String key = str(args, "key");
                    if (category != null && key != null) {
                        memoryService.deleteByKey(hostname, category, key);
                        return result("삭제 완료: [%s] %s".formatted(category, key));
                    }
                    return result("id 또는 category+key를 지정해주세요");
                }
        );
    }

    // ==================== Note Tools ====================

    private McpServerFeatures.SyncToolSpecification noteSaveTool() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "project": { "type": "string", "description": "프로젝트명 (선택)" },
                    "title": { "type": "string", "description": "노트 제목" },
                    "content": { "type": "string", "description": "노트 내용" },
                    "tags": { "type": "string", "description": "태그 (JSON 배열 문자열, 선택)" }
                  },
                  "required": ["title", "content"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new Tool("note_save", "구조화된 노트를 저장합니다", schema),
                (exchange, args) -> {
                    String project = str(args, "project");
                    String title = str(args, "title");
                    String content = str(args, "content");
                    String tags = str(args, "tags");
                    NoteDto saved = noteService.saveNote(hostname, project, title, content, tags);
                    return result("노트 저장 완료: [%s] %s (id=%d)".formatted(
                            saved.getProject() != null ? saved.getProject() : "-", saved.getTitle(), saved.getId()));
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification noteSearchTool() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "query": { "type": "string", "description": "검색어 (의미 기반 벡터 검색)" },
                    "project": { "type": "string", "description": "프로젝트 필터 (선택)" },
                    "limit": { "type": "integer", "description": "최대 결과 수 (기본 10)" }
                  },
                  "required": ["query"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new Tool("note_search", "노트를 의미 기반으로 검색합니다 (벡터 유사도 검색)", schema),
                (exchange, args) -> {
                    String query = str(args, "query");
                    String project = str(args, "project");
                    int limit = intVal(args, "limit", 10);
                    var results = noteService.vectorSearch(hostname, query, project, limit);
                    if (results.isEmpty()) return result("검색 결과 없음");
                    String text = results.stream()
                            .map(r -> "[%s] %s: %s (id=%d, 유사도=%.2f)".formatted(
                                    r.project() != null ? r.project() : "-",
                                    r.title(),
                                    r.content().length() > 100 ? r.content().substring(0, 100) + "..." : r.content(),
                                    r.id(), r.similarity()))
                            .collect(Collectors.joining("\n"));
                    return result("노트 %d건:\n%s".formatted(results.size(), text));
                }
        );
    }

    // ==================== DB Tools ====================

    private McpServerFeatures.SyncToolSpecification dbQueryTool() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "sql": { "type": "string", "description": "실행할 SELECT SQL 쿼리" }
                  },
                  "required": ["sql"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new Tool("db_query", "SELECT SQL을 실행하여 결과를 반환합니다 (읽기 전용)", schema),
                (exchange, args) -> {
                    String sql = str(args, "sql");
                    if (sql == null || !sql.trim().toUpperCase().startsWith("SELECT")) {
                        return result("SELECT 쿼리만 실행 가능합니다");
                    }
                    try {
                        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
                        if (rows.isEmpty()) return result("결과 없음");
                        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows);
                        return result("결과 %d행:\n%s".formatted(rows.size(), json));
                    } catch (Exception e) {
                        return result("쿼리 오류: " + e.getMessage());
                    }
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification dbExecuteTool() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "sql": { "type": "string", "description": "실행할 SQL (INSERT/UPDATE/DELETE)" }
                  },
                  "required": ["sql"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new Tool("db_execute", "INSERT/UPDATE/DELETE SQL을 실행합니다", schema),
                (exchange, args) -> {
                    String sql = str(args, "sql");
                    if (sql == null) return result("SQL을 지정해주세요");
                    String upper = sql.trim().toUpperCase();
                    if (upper.startsWith("DROP") || upper.startsWith("TRUNCATE") || upper.startsWith("ALTER")) {
                        return result("DROP/TRUNCATE/ALTER는 실행할 수 없습니다");
                    }
                    try {
                        int affected = jdbcTemplate.update(sql);
                        return result("실행 완료: %d행 영향".formatted(affected));
                    } catch (Exception e) {
                        return result("실행 오류: " + e.getMessage());
                    }
                }
        );
    }

    // ==================== Helpers ====================

    private CallToolResult result(String text) {
        return new CallToolResult(List.of(new TextContent(text)), false);
    }

    private String str(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

    private int intVal(Map<String, Object> args, String key, int defaultVal) {
        Object val = args.get(key);
        if (val == null) return defaultVal;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return defaultVal; }
    }

    private Long longVal(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return null; }
    }
}
