package com.example.entitycom.entity.member;

import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="auth_providers")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthProviders {
    @Id
    @Tsid
    @Column(name = "id", unique = true)
    private Long AuthProvidersKey;
    @Column(nullable = false, name = "provider")
    private String provider;

    @Column(nullable = false, name = "provider_id")
    private String providerId;
    @OneToOne(fetch = FetchType.LAZY)

    //Members ?뚯씠釉붽낵 議곗씤?섏뿬 ?뚯썝 ?뺣낫 李몄“
    @JoinColumn(name = "member_key", referencedColumnName = "member_key")//李몄“?섎뒗 而щ읆紐?
    private Members member;


}
