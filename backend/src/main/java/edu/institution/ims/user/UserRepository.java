package edu.institution.ims.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    @EntityGraph(attributePaths = {"studentProfile", "studentAccount"})
    List<User> findAllByRoleOrderByEmailAsc(Role role);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id in :ids order by u.id")
    List<User> findAllByIdForAssignmentUpdate(@Param("ids") Collection<Long> ids);
    @Override @EntityGraph(attributePaths = {"studentProfile", "studentAccount"}) Page<User> findAll(Specification<User> spec, Pageable pageable);
}
