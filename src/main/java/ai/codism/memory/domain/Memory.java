package ai.codism.memory.domain;

import ai.codism.memory.base.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "memories",
        uniqueConstraints = @UniqueConstraint(columnNames = {"host", "category", "memory_key"}))
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class Memory extends BaseEntity {

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "category", length = 100, nullable = false)
    private String category;

    @Column(name = "memory_key", length = 255, nullable = false)
    private String memoryKey;

    @Column(name = "memory_value", columnDefinition = "TEXT", nullable = false)
    private String memoryValue;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "embedding", columnDefinition = "vector(768)")
    @ColumnTransformer(write = "cast(? as vector)")
    private String embedding;
}
