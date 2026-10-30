package hu.kerekpar.adapter.in.web;

import hu.kerekpar.adapter.in.web.api.AuthApi;
import hu.kerekpar.adapter.in.web.api.model.LoginRequestDto;
import hu.kerekpar.adapter.in.web.api.model.LoginResponseDto;
import hu.kerekpar.adapter.in.web.security.LoginAttemptLimiter;
import hu.kerekpar.application.port.in.LoginUseCase;
import hu.kerekpar.domain.exception.UnauthenticatedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    private final LoginUseCase login;
    private final LoginAttemptLimiter limiter;

    public AuthController(LoginUseCase login, LoginAttemptLimiter limiter) {
        this.login = login;
        this.limiter = limiter;
    }

    @Override
    public ResponseEntity<LoginResponseDto> login(LoginRequestDto request) {
        String username = request.getUsername();
        limiter.checkAllowed(username);
        LoginUseCase.LoginResult result;
        try {
            result = login.execute(new LoginUseCase.Command(username, request.getPassword()));
        } catch (UnauthenticatedException e) {
            limiter.recordFailure(username);
            throw e;
        }
        limiter.reset(username);
        return ResponseEntity.ok(ApiMapper.toDto(result));
    }
}
