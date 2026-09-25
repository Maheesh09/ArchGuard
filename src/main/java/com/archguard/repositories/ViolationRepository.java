package com.archguard.repositories;

import com.archguard.models.Violation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ViolationRepository extends JpaRepository<Violation, UUID> {
}