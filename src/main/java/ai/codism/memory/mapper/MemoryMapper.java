package ai.codism.memory.mapper;

import ai.codism.memory.base.mapper.BaseMapper;
import ai.codism.memory.domain.Memory;
import ai.codism.memory.dto.MemoryDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MemoryMapper extends BaseMapper<Memory, MemoryDto> {
}
