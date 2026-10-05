package edu.institution.ims.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAccountRepository extends JpaRepository<StudentAccount, Long> {
    boolean existsByUserId(Long userId);
    boolean existsByUidIgnoreCase(String uid);
}
