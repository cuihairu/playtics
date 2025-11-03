package io.pit.control.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepo extends JpaRepository<ProjectEntity, String> {

    @Query("SELECT p FROM ProjectEntity p WHERE (:q IS NULL OR LOWER(p.id) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%',:q,'%')))")
    Page<ProjectEntity> search(@Param("q") String q, Pageable pageable);
}
