package com.englishlab.repository;

import com.englishlab.model.entity.UserIdentity;
import com.englishlab.model.entity.UserIdentity.Provider;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    /** Carga usuario y rol en la misma consulta (se usa para emitir el JWT). */
    @EntityGraph(attributePaths = {"user", "user.role"})
    Optional<UserIdentity> findByProviderAndProviderSubject(Provider provider, String providerSubject);

    boolean existsByUserIdAndProvider(Long userId, Provider provider);
}
