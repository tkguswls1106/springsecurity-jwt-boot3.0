package com.shj.springboot3.jwt;

import com.shj.springboot3.dto.token.TokenDto;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TokenProvider {  // JWT를 생성하고 검증하는 역할을 하는 클래스

    private static final String AUTHORITIES_KEY = "auth";
    private static final String BEARER_TYPE = "bearer";
    private static final long ACCESS_TOKEN_EXPIRE_TIME = 1000 * 60 * 360;  // 360분 = 6시간
    private final Key key;  // 이 key는 JWT의 토큰 서명(signature)을 생성하고 검증하는 데 사용됨.


    // 주의할점은, 여기의 @Value는 'springframework.beans.factory.annotation.Value'소속이다! lombok의 @Value와 착각하지 말자!
    public TokenProvider(@Value("${jwt.secret}") String secretKey) {  // 이 생성자는 JWT의 시크릿 key를 설정하고, 이를 바탕으로 JWT를 생성하고 검증할 수 있는 TokenProvider 클래스를 초기화하는 역할임.
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }


    // 토큰 생성
    public TokenDto generateTokenDto(Authentication authentication) {  // 파라미터로 전달해주는 authentication은 현재 인증 성공한 사용자를 나타내는 Authentication 객체이다.
        // (참고로 이 메소드의 파라미터 인증객체의 name 안에는 로그인계정아이디가 아닌, CustomUserDetailsService의 createUserDetails메소드에서 진행하여 나온 String으로 변환된 사용자DB의PKid가 들어있다.)

        // 사용자의 권한(authority) 정보를 문자열로 변환하는 부분임.
        // 예를 들어, 사용자가 "ROLE_USER"와 "ROLE_ADMIN" 두 가지 권한을 가지고 있다면, 위 코드는 "ROLE_USER,ROLE_ADMIN"과 같은 문자열을 생성함.
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        long now = (new Date()).getTime();
        Date tokenExpiresIn = new Date(now + ACCESS_TOKEN_EXPIRE_TIME);
//        System.out.println(tokenExpiresIn);
//        // 이것은 원래 'Fri Jul 21 23:25:11 KST 2023'처럼 '로그인한시각+만료시간6시간 = 로그인토큰만료시간'을 콘솔에 출력해주는 코드이다.
//        // 참고로, 'Fri Jul 21 23:25:11 KST 2023'의 의미는 '요일 월 일 시:분:초 기준시각나라 년도' 이다.

        String accessToken = Jwts.builder()
                .setSubject(authentication.getName())  // Payload에 String으로 변환해둔 사용자DB의PKid와 권한 정보가 저장되어야만한다. (아이디)
                .claim(AUTHORITIES_KEY, authorities)  // Access Token은 Refresh Token과는 다르게, Payload에 사용자의 아이디와 권한 정보가 저장되어야만한다. (권한)
                .setExpiration(tokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();  // 컴팩트화로써, 최종적으로 JWT를 문자열로 변환하는 역할임.

        return TokenDto.builder()
                .grantType(BEARER_TYPE)
                .accessToken(accessToken)
                .tokenExpiresIn(tokenExpiresIn.getTime())
                .build();
    }

    public Authentication getAuthentication(String accessToken) {  // Access Token의 Payload에 저장된 사용자의 아이디와 권한 정보를 토대로 인증하여 Authentication 객체를 만들어 반환하는 메소드
        Claims claims = parseClaims(accessToken);  // Access Token의 Payload에 저장된 Claim을 꺼내옴. (JWT 토큰에서 사용자의 아이디와 권한 정보를 획득할 목적)

        if (claims.get(AUTHORITIES_KEY) == null) {
            throw new RuntimeException("권한 정보가 없는 토큰입니다.");  // 클라이언트가 잘못된 요청을 한 것이 아니라, 서버에서 처리 중에 예기치 않은 에러가 발생한 것이기에, 500 error status code가 적절하다.
        }

        // 해당 계정이 갖고있는 권한 목록들을 리턴하는 역할임.
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))  // "ROLE_USER,ROLE_ADMIN"과 같은 문자열을 ','기준으로 분리함.
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        // 위 정보를 사용하여 Spring Security의 UserDetails 사용자 정보 객체를 생성함. claims.getSubject()는 사용자의 식별자인 계정아이디를 의미함.
        // 이때, 빈 문자열 ""은 패스워드를 나타내며, 여기서는 사용되지 않는다. 왜냐하면 일반적으로 패스워드는 토큰 자체에 저장되어 있지 않기 때문이다. 대신에 토큰의 서명을 통해 검증될 것이다.
        UserDetails principal = new User(claims.getSubject(), "", authorities);  // domain의 User가 아닌, security.core.userdetails.User 이다.
        // 인증 객체 생성.
        // 이는 AuthenticationProvider를 사용하지 않은채로 UsernamePasswordAuthenticationToken 클래스로 Authentication 인증 객체를 생성하는 과정으로 이루어졌으므로,
        // 별도의 아이디+비밀번호 일치 체킹 로직 없이, 단지 제공된 자격 증명이 시스템DB에 저장된 사용자에 대한 자격 증명과 일치하는지 확인하는 인증 객체 생성 용도로 사용된 것이다. (당연히 상식적으로도 현재 로그인 유지된 사용자를 판단하는데 비밀번호를 체킹할리가 없음)
        // 즉, 쉽게말하자면 현재 로그인 되어있는 사용자의 JWT토큰에 대해 검사하여, 이를 통해 JWT토큰에서 추출한 계정아이디에 대한 사용자 정보가 시스템DB에 일치하는지 확인하고 권한 또한 확인함.
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, "", authorities);

        return authentication;  // 이 인증된 객체는 Spring Security에서 사용자의 보안 컨텍스트를 관리하고 액세스 제어 검사를 수행하는 데 사용될 수 있다.
    }

    public boolean validateToken(String token) {  // 토큰의 key서명이 일치하고 유효한지 검사하는 메소드이다. (JWT를 검증하고 처리하는 단계)
        try {
            // setSigningKey(key)는 JWT의 서명을 확인하는 데 사용되는 key를 설정하는 역할임.
            // parseClaimsJws(token)은 JWT 문자열(token)을 구문 분석하고 확인하는 메소드로써,
            // 토큰의 서명이 유효한 경우, 토큰에서 구문 분석된 클레임을 포함하는 'Jws'(클레임이 포함된 JSON 웹 서명) 개체를 반환하고,
            // 서명이 유효하지 않거나 토큰 형식이 잘못된 경우, JwtException 예외 처리가 발생함.
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.info("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }

    private Claims parseClaims(String accessToken) {  // Access Token의 Payload에 저장된 Claim을 꺼내오는 메소드 (JWT 토큰에서 사용자의 아이디와 권한 정보를 획득할 목적)
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(accessToken).getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}