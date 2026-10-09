package io.github.gagann06.internshiptracker.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {
    Optional<UserToken> findByTokenHashAndPurpose(String tokenHash, UserTokenPurpose purpose);

    @Modifying
    @Query("delete from UserToken u where u.userId = :userId and u.purpose = :purpose and u.usedAt is null")
    void deleteUnusedTokensForUserAndPurpose(@Param("userId") Long userId, @Param("purpose") UserTokenPurpose purpose);
}
