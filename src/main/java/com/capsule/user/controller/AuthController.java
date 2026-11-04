package com.capsule.user.controller;

import com.capsule.common.config.ApiTags;
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
import com.capsule.user.dto.PasswordResetRequest;
import com.capsule.user.dto.SignupRequest;
import com.capsule.user.dto.TermsResponse;
import com.capsule.user.dto.VerificationResponse;
import com.capsule.user.service.AuthService;
import com.capsule.user.service.PasswordResetService;
import com.capsule.user.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** api.md 1~7, 24~26 */
@RestController
@Tag(name = ApiTags.AUTH)
class AuthController {

    private final AuthService auth;
    private final TokenService tokens;
    private final PasswordResetService passwordReset;

    AuthController(AuthService auth, TokenService tokens, PasswordResetService passwordReset) {
        this.auth = auth;
        this.tokens = tokens;
        this.passwordReset = passwordReset;
    }

    @Operation(summary = "25. 비밀번호 재설정 메일 요청", description = "이메일로 비밀번호 재설정 링크 발송. 가입 여부와 상관없이 같은 응답 (명세: api.md 25)")
    @PostMapping("/api/auth/password/reset-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestPasswordReset(@RequestBody EmailRequest req) {
        passwordReset.requestReset(req.email());
    }

    @Operation(summary = "26. 비밀번호 재설정", description = "메일 링크의 토큰과 새 비밀번호로 변경. 바꾸면 모든 기기에서 로그아웃 (명세: api.md 26)")
    @PostMapping("/api/auth/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@RequestBody PasswordResetRequest req) {
        passwordReset.reset(req.token(), req.newPassword());
    }

    @Operation(summary = "1. 약관 조회", description = "회원가입 화면에 띄울 필수 약관 2종 최신 버전 (명세: api.md 1)")
    @GetMapping("/api/terms")
    TermsResponse terms() {
        return new TermsResponse(auth.terms());
    }

    @Operation(summary = "2. 이메일 인증번호 요청", description = "가입할 이메일로 6자리 인증번호 발송 (5분 유효) (명세: api.md 2)")
    @PostMapping("/api/auth/email/verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ExpiresResponse requestVerification(@RequestBody EmailRequest req) {
        return new ExpiresResponse(auth.requestVerification(req.email()));
    }

    @Operation(summary = "3. 이메일 인증 확인", description = "인증번호 확인 → 가입용 토큰 발급 (명세: api.md 3)")
    @PostMapping("/api/auth/email/verification/confirm")
    VerificationResponse confirmVerification(@RequestBody ConfirmRequest req) {
        return new VerificationResponse(auth.confirmVerification(req.email(), req.code()));
    }

    @Operation(summary = "4. 회원가입", description = "이메일 · 비밀번호 · 닉네임 · 약관 동의로 가입, 바로 로그인 토큰 발급 (명세: api.md 4)")
    @PostMapping("/api/auth/email/signup")
    ResponseEntity<LoginResponse> signup(@RequestBody SignupRequest req) {
        return loggedIn(HttpStatus.CREATED, auth.signup(req));
    }

    @Operation(summary = "5. 이메일 로그인", description = "이메일 · 비밀번호 로그인 (명세: api.md 5)")
    @PostMapping("/api/auth/email/login")
    ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
        return loggedIn(HttpStatus.OK, auth.login(req.email(), req.password()));
    }

    @Operation(summary = "6. 카카오 로그인 · 가입", description = "카카오 인가 코드로 로그인, 처음이면 가입까지 (소셜은 카카오만) (명세: api.md 6)")
    @PostMapping("/api/auth/social/KAKAO")
    ResponseEntity<KakaoResponse> kakao(@RequestBody KakaoRequest req) {
        Login login = auth.kakao(req);
        Issued issued = tokens.issue(login.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issued.cookie())
                .body(new KakaoResponse(
                        issued.accessToken(), login.userId(), login.newUser(), login.onboardingRequired()));
    }

    @Operation(summary = "7. 토큰 갱신", description = "액세스 토큰 만료 시 재발급. 리프레시 토큰 쿠키로 (명세: api.md 7)")
    @PostMapping("/api/auth/refresh")
    ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = TokenService.REFRESH_COOKIE, required = false) String refreshToken) {
        Issued issued = tokens.rotate(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issued.cookie())
                .body(new AccessTokenResponse(issued.accessToken()));
    }

    /** 진행 중 세션은 끝내지 않는다 (재로그인 시 이어하기) */
    @Operation(summary = "24. 로그아웃", description = "리프레시 토큰 폐기. 진행 중 세션은 유지 (재로그인 시 이어하기) (명세: api.md 24)")
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
