package dev.joaopdias.auronix.core.user.dto;

public record AuthResponseDto (
    String token,
    UserResponseDto user
) {
    
}
