package ai.codism.memory.service;

import ai.codism.memory.base.mapper.BaseMapper;
import ai.codism.memory.base.service.BaseServiceImpl;
import ai.codism.memory.domain.Note;
import ai.codism.memory.dto.NoteDto;
import ai.codism.memory.mapper.NoteMapper;
import ai.codism.memory.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoteService extends BaseServiceImpl<Note, NoteDto, NoteRepository> {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);

    private final NoteRepository noteRepository;
    private final NoteMapper noteMapper;
    private final EmbeddingService embeddingService;

    @Override
    protected NoteRepository repository() { return noteRepository; }

    @Override
    protected BaseMapper<Note, NoteDto> mapper() { return noteMapper; }

    @Transactional
    public NoteDto saveNote(String host, String project, String title, String content, String tags) {
        String embeddingText = "%s: %s".formatted(title, content);
        float[] vector = embeddingService.embed(embeddingText);
        String vectorStr = embeddingService.toVectorString(vector);

        Note note = Note.builder()
                .host(host)
                .project(project)
                .title(title)
                .content(content)
                .tags(tags)
                .embedding(vectorStr)
                .build();
        return noteMapper.toDto(noteRepository.save(note));
    }

    public List<NoteSearchResult> vectorSearch(String host, String query, String project, int limit) {
        float[] queryVector = embeddingService.embed(query);
        if (queryVector == null) {
            log.warn("Embedding failed, falling back to LIKE search");
            return fallbackSearch(host, query, project);
        }

        String vectorStr = embeddingService.toVectorString(queryVector);
        List<Object[]> results;
        if (project != null && !project.isEmpty()) {
            results = noteRepository.findByVectorSimilarityAndProject(host, vectorStr, project, limit);
        } else {
            results = noteRepository.findByVectorSimilarity(host, vectorStr, limit);
        }

        List<NoteSearchResult> searchResults = new ArrayList<>();
        for (Object[] row : results) {
            Long id = ((Number) row[0]).longValue();
            String proj = (String) row[1];
            String noteTitle = (String) row[2];
            String noteContent = (String) row[3];
            double similarity = ((Number) row[4]).doubleValue();
            searchResults.add(new NoteSearchResult(id, proj, noteTitle, noteContent, similarity));
        }
        return searchResults;
    }

    private List<NoteSearchResult> fallbackSearch(String host, String query, String project) {
        List<Note> results;
        if (project != null && !project.isEmpty()) {
            results = noteRepository.findByHostAndProjectAndTitleContainingIgnoreCaseOrHostAndProjectAndContentContainingIgnoreCase(
                    host, project, query, host, project, query);
        } else {
            results = noteRepository.findByHostAndTitleContainingIgnoreCaseOrHostAndContentContainingIgnoreCase(
                    host, query, host, query);
        }
        return results.stream()
                .map(n -> new NoteSearchResult(n.getId(), n.getProject(), n.getTitle(), n.getContent(), 0.0))
                .toList();
    }

    public List<NoteDto> listByProject(String host, String project) {
        if (project != null && !project.isEmpty()) {
            return noteMapper.toDtoList(noteRepository.findByHostAndProject(host, project));
        }
        return noteMapper.toDtoList(noteRepository.findByHost(host));
    }

    public record NoteSearchResult(Long id, String project, String title, String content, double similarity) {}
}
