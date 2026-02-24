package ai.codism.memory.base.service;

import java.util.List;

public interface BaseService<ENTITY, DTO> {
    DTO create(DTO dto);
    ENTITY createEntity(DTO dto);
    DTO update(Long id, DTO dto);
    ENTITY updateEntity(Long id, DTO dto);
    void delete(Long id);
    void hardDelete(Long id);
    List<Long> bulkDelete(List<Long> ids);
    DTO getById(Long id);
    ENTITY getEntityById(Long id);
    List<DTO> getAll();
    List<DTO> searchAll(DTO condition);
    long count(DTO condition);
}
