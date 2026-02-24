package ai.codism.memory.repository;

import ai.codism.memory.base.repository.BaseRepository;
import ai.codism.memory.domain.Note;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoteRepository extends BaseRepository<Note> {

    List<Note> findByHostAndProject(String host, String project);

    List<Note> findByHost(String host);

    List<Note> findByHostAndTitleContainingIgnoreCaseOrHostAndContentContainingIgnoreCase(
            String host1, String title, String host2, String content);

    List<Note> findByHostAndProjectAndTitleContainingIgnoreCaseOrHostAndProjectAndContentContainingIgnoreCase(
            String host1, String project1, String title, String host2, String project2, String content);

    @Query(value = "SELECT id, project, title, content, " +
            "1 - (embedding <=> cast(:queryVector as vector)) as similarity " +
            "FROM notes WHERE embedding IS NOT NULL AND del_yn = 'N' AND host = :host " +
            "ORDER BY embedding <=> cast(:queryVector as vector) LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findByVectorSimilarity(@Param("host") String host, @Param("queryVector") String queryVector, @Param("limit") int limit);

    @Query(value = "SELECT id, project, title, content, " +
            "1 - (embedding <=> cast(:queryVector as vector)) as similarity " +
            "FROM notes WHERE embedding IS NOT NULL AND del_yn = 'N' AND host = :host AND project = :project " +
            "ORDER BY embedding <=> cast(:queryVector as vector) LIMIT :limit",
            nativeQuery = true)
    List<Object[]> findByVectorSimilarityAndProject(
            @Param("host") String host,
            @Param("queryVector") String queryVector,
            @Param("project") String project,
            @Param("limit") int limit);
}
