package edu.stusystem.api;

import edu.stusystem.security.CurrentUser;
import edu.stusystem.security.JwtService;
import edu.stusystem.service.IdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final IdentityService identity;
    private final JwtService jwt;
    public AuthController(IdentityService identity, JwtService jwt) { this.identity = identity; this.jwt = jwt; }
    public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {}
    @PostMapping("/auth/login") Map<String,Object> login(@Valid @RequestBody LoginRequest request) {
        CurrentUser user = identity.authenticate(request.identifier(), request.password());
        return Map.of("success", true, "data", Map.of("token", jwt.issue(user), "expiresIn", 3600, "user", identity.profile(user)));
    }
    @GetMapping("/me") Map<String,Object> me(@AuthenticationPrincipal CurrentUser user) { return Map.of("success", true, "data", identity.profile(user)); }
    @PostMapping("/auth/logout") Map<String,Object> logout(@RequestHeader("Authorization") String header, @AuthenticationPrincipal CurrentUser user) {
        identity.revoke(user.id(), jwt.parse(header.substring(7)));
        return Map.of("success", true, "data", Map.of("revoked", true));
    }
}
