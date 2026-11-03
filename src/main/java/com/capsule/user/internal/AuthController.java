package com.capsule.user.internal;

import com.capsule.user.internal.AuthService.KakaoRequest;
import com.capsule.user.internal.AuthService.Login;
import com.capsule.user.internal.AuthService.SignupRequest;
import com.capsule.user.internal.TermsRepository.TermVersion;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** api.md 1~7, 24 */
@RestController
class AuthController {

    record TermsResponse(List<TermVersion> terms) {}

    record EmailRequest(String email) {}

    record ExpiresResponse(int expiresInSeconds) {}

    record ConfirmRequest(String email, String code) {}

    record VerificationResponse(String verificationToken) {}

    record LoginRequest(String email, String password) {}

    record LoginResponse(String accessToken, long userId, boolean onboardingRequired) {}

    record KakaoResponse(String accessToken, long userId, boolean isNewUser, boolean onboardingRequired) {}

    record AccessTokenResponse(String accessToken) {}

    private final AuthService auth;
    private final TokenService tokens;

    AuthController(AuthService auth, TokenService tokens) {
        this.auth = auth;
        this.tokens = tokens;
    }

    @GetMapping("/api/terms")
    TermsResponse terms() {
        return new TermsResponse(auth.terms());
    }

    @PostMapping("/api/auth/email/verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ExpiresResponse requestVerification(@RequestBody EmailRequest req) {
        return new ExpiresResponse(auth.requestVerification(req.email()));
    }

    @PostMapping("/api/auth/email/verification/confirm")
    VerificationResponse confirmVerification(@RequestBody ConfirmRequest req) {
        return new VerificationResponse(auth.confirmVerification(req.email(), req.code()));
    }

    @PostMapping("/api/auth/email/signup")
    ResponseEntity<LoginResponse> signup(@RequestBody SignupRequest req) {
        return loggedIn(HttpStatus.CREATED, auth.signup(req));
    }

    @PostMapping("/api/auth/email/login")
    ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
        return loggedIn(HttpStatus.OK, auth.login(req.email(), req.password()));
    }

    @PostMapping("/api/auth/social/KAKAO")
    ResponseEntity<KakaoResponse> kakao(@RequestBody KakaoRequest req) {
        Login login = auth.kakao(req);
        TokenService.Issued issued = tokens.issue(login.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issued.cookie())
                .body(new KakaoResponse(
                        issued.accessToken(), login.userId(), login.newUser(), login.onboardingRequired()));
    }

    @PostMapping("/api/auth/refresh")
    ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = TokenService.REFRESH_COOKIE, required = false) String refreshToken) {
        TokenService.Issued issued = tokens.rotate(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issued.cookie())
                .body(new AccessTokenResponse(issued.accessToken()));
    }

    /** 진행 중 세션은 끝내지 않는다 (재로그인 시 이어하기) */
    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = TokenService.REFRESH_COOKIE, required = false) String refreshToken) {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, tokens.revoke(refreshToken))
                .build();
    }

    private ResponseEntity<LoginResponse> loggedIn(HttpStatus status, Login login) {
        TokenService.Issued issued = tokens.issue(login.userId());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, issued.cookie())
                .body(new LoginResponse(issued.accessToken(), login.userId(), login.onboardingRequired()));
    }
}
