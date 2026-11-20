package com.glory.inventorysaas.repository;

import com.glory.inventorysaas.entity.User;
import com.glory.inventorysaas.entity.enums.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {"company", "warehouse"})
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByCompanyIdAndRole(UUID companyId, Role role);

    List<User> findByCompanyId(UUID companyId);
}
