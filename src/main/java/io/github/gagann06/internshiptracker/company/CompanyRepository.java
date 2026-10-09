package io.github.gagann06.internshiptracker.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    List<Company> findAllByOwnerId(Long ownerId);
    Optional<Company> findByIdAndOwnerId(Long id, Long ownerId);
    boolean existsByIdAndOwnerId(Long id, Long ownerId);
    boolean existsByOwnerIdAndNameIgnoreCase(Long ownerId, String name);
    boolean existsByOwnerIdAndNameIgnoreCaseAndIdNot(Long ownerId, String name, Long id);

    @Modifying
    @Query("delete from Company c where c.ownerId = :ownerId")
    void deleteAllOwnedBy(@Param("ownerId") Long ownerId);
}
