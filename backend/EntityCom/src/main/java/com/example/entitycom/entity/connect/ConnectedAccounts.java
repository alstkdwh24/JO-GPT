package com.example.entitycom.entity.connect;

import com.example.entitycom.converter.AesEncryptConverter;
import com.example.entitycom.entity.member.Members;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "connected_accounts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// connect 엔티티 구글 서비스랑 연결하기 위한
public class ConnectedAccounts {
    @Id
    @Tsid
    @Column(name = "account_key")
    private Long accountKey;

    // Members 와 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_key", referencedColumnName = "member_key")
    private Members member;

    @Column(name = "provider")
    private String provider;

    @Column(name = "provider_email")
    private String providerEmail;

    @Convert(converter = AesEncryptConverter.class)
    @Column(name = "access_token" ,columnDefinition="TEXT")
    private String accessToken;

    @Convert(converter = AesEncryptConverter.class)
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expiry")
    private LocalDateTime tokenExpiry;

    public void updateTokens(String accessToken, String refreshToken, LocalDateTime expiry) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenExpiry = expiry;
    }
    public void setAccountKey(Long accountKey) {
        this.accountKey = accountKey;
    }
}
