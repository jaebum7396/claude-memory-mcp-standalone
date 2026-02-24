package ai.codism.memory.dto;

import ai.codism.memory.base.dto.BaseDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class NoteDto extends BaseDto {
    private String project;
    private String title;
    private String content;
    private String tags;
}
