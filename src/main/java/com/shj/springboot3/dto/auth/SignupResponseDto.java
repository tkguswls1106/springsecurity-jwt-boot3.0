package com.shj.springboot3.dto.auth;

import com.shj.springboot3.dto.user.UserResponseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SignupResponseDto {

    private UserResponseDto userResponseDto;
    private TokenDto tokenDto;
}
