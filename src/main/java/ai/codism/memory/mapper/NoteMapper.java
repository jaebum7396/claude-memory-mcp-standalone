package ai.codism.memory.mapper;

import ai.codism.memory.base.mapper.BaseMapper;
import ai.codism.memory.domain.Note;
import ai.codism.memory.dto.NoteDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NoteMapper extends BaseMapper<Note, NoteDto> {
}
