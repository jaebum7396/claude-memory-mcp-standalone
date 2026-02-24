package ai.codism.memory.base.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SQLRestriction;

@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@Setter
@MappedSuperclass
@EntityListeners(BaseEntityListener.class)
@SQLRestriction("del_yn = 'N'")
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_by", length = 50, updatable = false)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Builder.Default
    @ColumnDefault("'Y'")
    @Column(name = "use_yn", length = 1, nullable = false)
    private String useYn = "Y";

    @Builder.Default
    @ColumnDefault("'N'")
    @Column(name = "del_yn", length = 1, nullable = false)
    private String delYn = "N";

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void setModifiedBy(String modifiedBy) {
        this.updatedBy = modifiedBy;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.delYn = "Y";
    }

    public void toggleUse() {
        this.useYn = "Y".equals(this.useYn) ? "N" : "Y";
    }
}
