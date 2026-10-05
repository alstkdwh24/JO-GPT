package com.example.jo_gpt_program.gpt.config.filter;

import com.example.entitycom.enums.Role;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
// 토큰에서 꺼낸 사용자 정보를 담는 DTP
public class UserInfoDto {

    private String id;
    private String memberId;
    private String nickname;
    private Role role;

    // Spring Security 에서 권한으로 이용
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }
}
