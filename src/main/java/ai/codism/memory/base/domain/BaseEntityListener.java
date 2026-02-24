package ai.codism.memory.base.domain;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class BaseEntityListener {

    @PrePersist
    public void prePersist(BaseEntity entity) {
        entity.setCreatedBy("system");
        entity.setModifiedBy("system");

        if (entity.getUseYn() == null) {
            entity.setUseYn("Y");
        }
        if (entity.getDelYn() == null) {
            entity.setDelYn("N");
        }
    }

    @PreUpdate
    public void preUpdate(BaseEntity entity) {
        entity.setModifiedBy("system");
    }
}
