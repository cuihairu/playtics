package io.playtics.control.experiment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperimentRepo extends JpaRepository<ExperimentEntity, String> {
    List<ExperimentEntity> findByProjectId(String projectId);
    List<ExperimentEntity> findByProjectIdAndStatus(String projectId, String status);
}
