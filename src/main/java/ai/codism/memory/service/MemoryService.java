package ai.codism.memory.service;

import ai.codism.memory.base.mapper.BaseMapper;
import ai.codism.memory.base.service.BaseServiceImpl;
import ai.codism.memory.domain.Memory;
import ai.codism.memory.dto.MemoryDto;
import ai.codism.memory.mapper.MemoryMapper;
import ai.codism.memory.repository.MemoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemoryService extends BaseServiceImpl<Memory, MemoryDto, MemoryRepository> {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryRepository memoryRepository;
    private final MemoryMapper memoryMapper;
    private final EmbeddingService embeddingService;

    @Override
    protected MemoryRepository repository() { return memoryRepository; }

    @Override
    protected BaseMapper<Memory, MemoryDto> mapper() { return memoryMapper; }

    @Transactional
    public MemoryDto save(String host, String category, String key, String value, String metadata) {
        String embeddingText = "[%s] %s: %s".formatted(category, key, value);
        float[] vector = embeddingService.embed(embeddingText);
        String vectorStr = embeddingService.toVectorString(vector);

        Optional<Memory> existing = memoryRepository.findByHostAndCategoryAndMemoryKey(host, category, key);
        if (existing.isPresent()) {
            Memory memory = existing.get();
            memory.setMemoryValue(value);
            if (metadata != null) memory.setMetadata(metadata);
            if (vectorStr != null) memory.setEmbedding(vectorStr);
            return memoryMapper.toDto(memoryRepository.save(memory));
        } else {
            Memory memory = Memory.builder()
                    .host(host)
                    .category(category)
                    .memoryKey(key)
                    .memoryValue(value)
                    .metadata(metadata)
                    .embedding(vectorStr)
                    .build();
            return memoryMapper.toDto(memoryRepository.save(memory));
        }
    }

    public List<MemorySearchResult> vectorSearch(String host, String query, String category, int limit) {
        float[] queryVector = embeddingService.embed(query);
        if (queryVector == null) {
            log.warn("Embedding failed, falling back to LIKE search");
            return fallbackSearch(host, query, category);
        }

        String vectorStr = embeddingService.toVectorString(queryVector);
        List<Object[]> results;
        if (category != null && !category.isEmpty()) {
            results = memoryRepository.findByVectorSimilarityAndCategory(host, vectorStr, category, limit);
        } else {
            results = memoryRepository.findByVectorSimilarity(host, vectorStr, limit);
        }

        List<MemorySearchResult> searchResults = new ArrayList<>();
        for (Object[] row : results) {
            Long id = ((Number) row[0]).longValue();
            String cat = (String) row[1];
            String memKey = (String) row[2];
            String memValue = (String) row[3];
            double similarity = ((Number) row[4]).doubleValue();
            searchResults.add(new MemorySearchResult(id, cat, memKey, memValue, similarity));
        }
        return searchResults;
    }

    private List<MemorySearchResult> fallbackSearch(String host, String query, String category) {
        List<Memory> results;
        if (category != null && !category.isEmpty()) {
            results = memoryRepository.findByHostAndCategoryAndMemoryValueContainingIgnoreCase(host, category, query);
        } else {
            results = memoryRepository.findByHostAndMemoryValueContainingIgnoreCase(host, query);
        }
        return results.stream()
                .map(m -> new MemorySearchResult(m.getId(), m.getCategory(), m.getMemoryKey(), m.getMemoryValue(), 0.0))
                .toList();
    }

    public List<MemoryDto> listByCategory(String host, String category) {
        if (category != null && !category.isEmpty()) {
            return memoryMapper.toDtoList(memoryRepository.findByHostAndCategory(host, category));
        }
        return memoryMapper.toDtoList(memoryRepository.findByHost(host));
    }

    @Transactional
    public void deleteByKey(String host, String category, String key) {
        memoryRepository.findByHostAndCategoryAndMemoryKey(host, category, key)
                .ifPresent(memory -> {
                    memory.softDelete();
                    memoryRepository.save(memory);
                });
    }

    public record MemorySearchResult(Long id, String category, String key, String value, double similarity) {}
}
