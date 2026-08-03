package com.linkforge.users.repository;

import com.linkforge.users.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByUserIdAndEnabledTrue(UUID userId);

    List<ApiKey> findByUserId(UUID userId);

    @Modifying
    @Query("UPDATE ApiKey k SET k.lastUsedAt = :now, k.totalRequests = k.totalRequests + 1 WHERE k.id = :id")
    void recordUsage(@Param("id") UUID id, @Param("now") LocalDateTime now);

    long countByUserId(UUID userId);
}
