package io.playtics.control.experiment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExperimentRepo extends JpaRepository<ExperimentEntity, String> {
    List<ExperimentEntity> findByProjectId(String projectId);
    List<ExperimentEntity> findByProjectIdAndStatus(String projectId, String status);

    @Query("SELECT e FROM ExperimentEntity e WHERE e.projectId=:pid AND (:st IS NULL OR e.status=:st) ORDER BY e.updatedAt DESC")
    Page<ExperimentEntity> pageBy(@Param("pid") String projectId, @Param("st") String status, Pageable pageable);
}
