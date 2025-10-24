package io.playtics.control.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepo extends JpaRepository<ApiKeyEntity, String> { }
