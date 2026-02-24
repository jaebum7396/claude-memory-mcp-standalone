package ai.codism.memory.base.service;

import ai.codism.memory.base.domain.BaseEntity;
import ai.codism.memory.base.mapper.BaseMapper;
import ai.codism.memory.base.repository.BaseRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public abstract class BaseServiceImpl<ENTITY, DTO, REPOSITORY extends BaseRepository<ENTITY>>
        implements BaseService<ENTITY, DTO> {

    protected abstract REPOSITORY repository();
    protected abstract BaseMapper<ENTITY, DTO> mapper();

    @Override
    @Transactional
    public DTO create(DTO dto) {
        return mapper().toDto(createEntity(dto));
    }

    @Override
    @Transactional
    public ENTITY createEntity(DTO dto) {
        ENTITY entity = mapper().toEntity(dto);
        return repository().save(entity);
    }

    @Override
    @Transactional
    public DTO update(Long id, DTO dto) {
        return mapper().toDto(updateEntity(id, dto));
    }

    @Override
    @Transactional
    public ENTITY updateEntity(Long id, DTO dto) {
        ENTITY entity = getEntityById(id);
        mapper().updateEntity(entity, dto);
        return repository().save(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ENTITY entity = getEntityById(id);
        if (entity instanceof BaseEntity baseEntity) {
            baseEntity.softDelete();
            repository().save(entity);
        } else {
            repository().deleteById(id);
        }
    }

    @Override
    @Transactional
    public void hardDelete(Long id) {
        if (!repository().existsById(id)) {
            throw new EntityNotFoundException("Entity not found: " + id);
        }
        repository().deleteById(id);
    }

    @Override
    @Transactional
    public List<Long> bulkDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<ENTITY> entities = repository().findAllById(ids);
        List<Long> deletedIds = new ArrayList<>();
        for (ENTITY entity : entities) {
            if (entity instanceof BaseEntity baseEntity) {
                baseEntity.softDelete();
                deletedIds.add(baseEntity.getId());
            }
        }
        repository().saveAll(entities);
        return deletedIds;
    }

    @Override
    public DTO getById(Long id) {
        return mapper().toDto(getEntityById(id));
    }

    @Override
    public ENTITY getEntityById(Long id) {
        return repository().findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Entity not found: " + id));
    }

    @Override
    public List<DTO> getAll() {
        return mapper().toDtoList(repository().findAll());
    }

    @Override
    public List<DTO> searchAll(DTO condition) {
        return getAll();
    }

    @Override
    public long count(DTO condition) {
        return repository().count();
    }
}
