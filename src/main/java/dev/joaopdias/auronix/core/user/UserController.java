package dev.joaopdias.auronix.core.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.joaopdias.auronix.core.user.dto.AuthResponseDto;
import dev.joaopdias.auronix.core.user.dto.CreateUserDto;
import dev.joaopdias.auronix.core.user.dto.LoginUserDto;
import dev.joaopdias.auronix.core.user.dto.UpdateUserDto;
import dev.joaopdias.auronix.core.user.dto.UserResponseDto;
import dev.joaopdias.auronix.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/user")
public class UserController {
    
    @Autowired
    private UserService userService;

    @PostMapping()
    public UserResponseDto create(
        @RequestBody @Valid CreateUserDto createUserDto,
        HttpServletResponse response
    ) {
        AuthResponseDto authResponseDto = userService.create(createUserDto);
        setCookie(authResponseDto.token(), response);
        return authResponseDto.user();
    }

    @PostMapping("/login")
    public UserResponseDto login(
        @RequestBody @Valid LoginUserDto loginUserDto,
        HttpServletResponse response
    ) {
        AuthResponseDto authResponseDto = userService.login(loginUserDto);
        setCookie(authResponseDto.token(), response);
        return authResponseDto.user();
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("access_token", "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    @GetMapping()
    public UserResponseDto decodeToken(
        @CookieValue(name = "access_token") String token,
        HttpServletResponse response
    ) {
        AuthResponseDto authResponseDto = userService.decodeToken(token);
        setCookie(authResponseDto.token(), response);
        return authResponseDto.user();
    }

    @PatchMapping()
    public void update(
        @AuthenticationPrincipal AuthenticatedUser authentication,
        @RequestBody @Valid UpdateUserDto updateUserDto
    ) {
        userService.update(authentication.id(), updateUserDto);
    }

    @DeleteMapping()
    public void update(@AuthenticationPrincipal AuthenticatedUser authentication) {
        userService.delete(authentication.id());
    }

    private void setCookie(String token, HttpServletResponse response){
        ResponseCookie cookie = ResponseCookie.from("access_token", token)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(60 * 60 * 2)
            .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

}
