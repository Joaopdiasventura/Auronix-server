package dev.joaopdias.auronix.core.user;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.joaopdias.auronix.core.user.entities.User;

@Repository
public interface UserRepository extends  JpaRepository<User, UUID> {
    User findByEmail(String email);   
}
