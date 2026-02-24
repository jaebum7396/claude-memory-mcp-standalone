package ai.codism.memory.repository;

import ai.codism.memory.base.repository.BaseRepository;
import ai.codism.memory.domain.Memory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemoryRepository extends BaseRepository<Memory> {

    Optional<Memory> findByHostAndCategoryAndMemoryKey(String host, String category, String memoryKey);

    List<Memory> findByHostAndCategory(String host, String category);

    List<Memory> findByHost(String host);

    List<Memory> findByHostAndMemoryValueContainingIgnoreCase(String host, String query);

    List<Memory> findByHostAndCategoryAndMemoryValueContainingIgnoreCase(String host, String category, String query);

    @Query(value = "SELECT id, category, memory_key, memory_value, " +
            "1 - (embedding <=> cast(:queryVector as vector)) as similarity " +
            "FROM memories WHERE embedding IS NOT NULL AND del_yn = 'N' AND host = :host " +
            "ORDER BY embedding <=> cast(:queryVector as vector) LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findByVectorSimilarity(@Param("host") String host, @Param("queryVector") String queryVector, @Param("limit") int limit);

    @Query(value = "SELECT id, category, memory_key, memory_value, " +
            "1 - (embedding <=> cast(:queryVector as vector)) as similarity " +
            "FROM memories WHERE embedding IS NOT NULL AND del_yn = 'N' AND host = :host AND category = :category " +
            "ORDER BY embedding <=> cast(:queryVector as vector) LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findByVectorSimilarityAndCategory(
            @Param("host") String host,
            @Param("queryVector") String queryVector,
            @Param("category") String category,
            @Param("limit") int limit);
}
