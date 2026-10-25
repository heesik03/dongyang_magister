package com.magister.backend.domain.workspace.repository;

import com.magister.backend.domain.workspace.entity.Workspace;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {}
