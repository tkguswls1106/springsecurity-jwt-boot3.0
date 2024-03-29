package com.shj.springboot3.service.impl;

import com.shj.springboot3.domain.user.User;
import com.shj.springboot3.domain.user.UserRepository;
import com.shj.springboot3.dto.auth.SignupResponseDto;
import com.shj.springboot3.dto.auth.TokenDto;
import com.shj.springboot3.dto.user.UserResponseDto;
import com.shj.springboot3.domain.user.Role;
import com.shj.springboot3.dto.user.UserSignupRequestDto;
import com.shj.springboot3.jwt.TokenProvider;
import com.shj.springboot3.service.AuthService;
import com.shj.springboot3.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;


    @Transactional
    @Override
    public SignupResponseDto signup(UserSignupRequestDto userSignupRequestDto) {
        Long userId = SecurityUtil.getCurrentMemberId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("해당 사용자는 존재하지 않습니다."));

        if(!user.getRole().equals(Role.ROLE_GUEST)  // Role이 GUEST인 사용자만 이용가능한 api 이다.
                || user.getMoreInfo1() != null || user.getMoreInfo2() != null || user.getMoreInfo3() != null) {
            throw new RuntimeException("이미 가입완료 되어있는 사용자입니다.");
        }

        user.updateMoreInfo(userSignupRequestDto);
        user.updateRole();
        UserResponseDto userResponseDto = new UserResponseDto(user);

        // 추가정보 입력후, 위에서 Role을 GUEST->USER로 업데이트했지만,
        // 헤더의 jwt 토큰에 등록해둔 권한도 수정해야하기에, Access 토큰도 따로 재발급해야함.
        TokenDto tokenDto = tokenProvider.generateAccessTokenByRefreshToken(userId, Role.ROLE_USER, user.getRefreshToken());

        return new SignupResponseDto(userResponseDto, tokenDto);
    }
}