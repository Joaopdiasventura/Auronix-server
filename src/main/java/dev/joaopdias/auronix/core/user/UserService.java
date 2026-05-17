package dev.joaopdias.auronix.core.user;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import dev.joaopdias.auronix.core.user.dto.AuthResponseDto;
import dev.joaopdias.auronix.core.user.dto.CreateUserDto;
import dev.joaopdias.auronix.core.user.dto.LoginUserDto;
import dev.joaopdias.auronix.core.user.dto.UpdateUserDto;
import dev.joaopdias.auronix.core.user.entities.User;
import dev.joaopdias.auronix.shared.services.SecurityService;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SecurityService securityService;

    public AuthResponseDto create(CreateUserDto createUserDto) {
        validateEmail(createUserDto.email());

        User user = new User();

        user.setEmail(createUserDto.email());
        user.setName(createUserDto.name());
        user.setPassword(securityService.hashPassword(createUserDto.password()));
        
        user = userRepository.save(user);
        
        String token = securityService.createJwt(user.getId());

        return new AuthResponseDto(token, user.toResponseDto());
    }

    public AuthResponseDto login(LoginUserDto loginUserDto) {
        User user = userRepository.findByEmail(loginUserDto.email());
        if (user == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email ou senha inválidos");

        boolean passwordMatches = securityService.matchesPassword(loginUserDto.password(), user.getPassword());
        if (!passwordMatches) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email ou senha inválidos");

        String token = securityService.createJwt(user.getId());

        return new AuthResponseDto(token, user.toResponseDto());
    }

    public AuthResponseDto decodeToken(String token) {
        UUID id = securityService.decodeJwt(token);
        User user = findById(id);
        String newToken = securityService.createJwt(user.getId());
        return new AuthResponseDto(newToken, user.toResponseDto());
    }

    public User findById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada"));
    }

    public void update(UUID id, UpdateUserDto updateUserDto) {
        User user = findById(id);

        if (updateUserDto.email() != null && !updateUserDto.email().equals(user.getEmail())) {
            validateEmail(updateUserDto.email());
            user.setEmail(updateUserDto.email());
        }

        if (updateUserDto.name() != null) 
            user.setName(updateUserDto.name());
        

        if (updateUserDto.password() != null) 
            user.setPassword(securityService.hashPassword(updateUserDto.password()));

        userRepository.save(user);
    }

    public void delete(UUID id) {
        User user = findById(id);
        userRepository.delete(user);
    }

    private void validateEmail(String email) {
        User user = userRepository.findByEmail(email);
        if (user != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esse email já está em uso");
    }
}
