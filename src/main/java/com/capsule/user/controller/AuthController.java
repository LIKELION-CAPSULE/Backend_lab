package com.capsule.user.controller;

import com.capsule.user.dto.AccessTokenResponse;
import com.capsule.user.dto.ConfirmRequest;
import com.capsule.user.dto.EmailRequest;
import com.capsule.user.dto.ExpiresResponse;
import com.capsule.user.dto.Issued;
import com.capsule.user.dto.KakaoRequest;
import com.capsule.user.dto.KakaoResponse;
import com.capsule.user.dto.Login;
import com.capsule.user.dto.LoginRequest;
import com.capsule.user.dto.LoginResponse;
import com.capsule.user.dto.SignupRequest;
import com.capsule.user.dto.TermsResponse;
import com.capsule.user.dto.VerificationResponse;
import com.capsule.user.service.AuthService;
import com.capsule.user.service.TokenService;
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
        Issued issued = tokens.issue(login.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issued.cookie())
                .body(new KakaoResponse(
                        issued.accessToken(), login.userId(), login.newUser(), login.onboardingRequired()));
    }

    @PostMapping("/api/auth/refresh")
    ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = TokenService.REFRESH_COOKIE, required = false) String refreshToken) {
        Issued issued = tokens.rotate(refreshToken);
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
        Issued issued = tokens.issue(login.userId());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, issued.cookie())
                .body(new LoginResponse(issued.accessToken(), login.userId(), login.onboardingRequired()));
    }
}
