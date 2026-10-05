package com.example.entitycom.entity.member;

import jakarta.persistence.Id;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="user_credentials")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCredentials {



    @Id // 기본 키 필드 추가
    @Column(name = "member_key") // DB의 실제 PK 컬럼명으로 명시
    private Long memberKey;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    // referencedColumnName을 부모 테이블의 PK 컬럼명인 "member_key"로 수정
    @JoinColumn(name = "member_key" ,referencedColumnName ="member_key") // DB 컬럼명
    private Members member;
    @Column(nullable = false, name = "user_pw")
    private String userPw;

    // 편의 메서드: 연관관계 설정 시 ID가 자동으로 세팅되도록 함
    public void changeId(Members member) {
        this.member = member;
        if (member != null) {
            this.memberKey = member.getMemberKey();
        }
    }

    public void changeUserPw(String userPw) {
        this.userPw = userPw;
    }
}
