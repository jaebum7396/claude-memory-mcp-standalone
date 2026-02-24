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
public class MemoryDto extends BaseDto {
    private String category;
    private String memoryKey;
    private String memoryValue;
    private String metadata;
}
