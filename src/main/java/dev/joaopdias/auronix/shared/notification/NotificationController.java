package dev.joaopdias.auronix.shared.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.joaopdias.auronix.shared.security.AuthenticatedUser;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    @Autowired
    private SseRegistryService sseRegistryService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser authentication) {
        return sseRegistryService.register(authentication.id());
    }
}
