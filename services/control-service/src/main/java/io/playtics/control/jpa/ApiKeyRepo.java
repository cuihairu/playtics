package io.playtics.control.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepo extends JpaRepository<ApiKeyEntity, String> {

    @Query("SELECT a FROM ApiKeyEntity a WHERE (:projectId IS NULL OR LOWER(a.projectId) LIKE LOWER(CONCAT('%',:projectId,'%'))) AND (:q IS NULL OR LOWER(a.apiKey) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(a.name) LIKE LOWER(CONCAT('%',:q,'%')))")
    Page<ApiKeyEntity> search(@Param("projectId") String projectId, @Param("q") String q, Pageable pageable);

    long deleteByProjectId(String projectId);
}
