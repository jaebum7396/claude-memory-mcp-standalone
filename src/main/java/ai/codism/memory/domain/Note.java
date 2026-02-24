package ai.codism.memory.domain;

import ai.codism.memory.base.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "notes")
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class Note extends BaseEntity {

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "project", length = 100)
    private String project;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "embedding", columnDefinition = "vector(768)")
    @ColumnTransformer(write = "cast(? as vector)")
    private String embedding;
}
