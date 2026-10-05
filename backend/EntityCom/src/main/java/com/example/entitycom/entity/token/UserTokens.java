package com.example.entitycom.entity.token;


import com.example.entitycom.entity.log.CreateTimeLogs;
import com.example.entitycom.entity.member.Members;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="User_tokens")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTokens {
    @Id
    @Tsid
    @Column(name = "user_token_key", unique = true, nullable = false)
    private Long userTokenKey; // PK 필드 (Members의 memberKey를 상속받음)

    // Members 테이블 조인
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_key" ,referencedColumnName ="member_key" ) // DB 컬럼명
    private Members member;

    @Column(nullable = false, name = "access_token")
    private String accessToken;

    @Column(nullable = false, name = "refresh_token")
    private String refreshToken;

    @Column(nullable = false, name = "expiration_date")
    private Long expirationDate;

    // 유저 토큰 사용시간 기록을 위한 조인
    @OneToOne(mappedBy = "userToken")
    private CreateTimeLogs createTimeLogs;



}
