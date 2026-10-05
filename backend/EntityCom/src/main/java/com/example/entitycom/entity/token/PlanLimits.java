package com.example.entitycom.entity.token;

import com.example.entitycom.entity.member.Members;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="plan_limits")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanLimits {
    @Id
    @Tsid
    @Column(name = "plan_limit_key", unique = true)
    private Long planLimitKey;

    // Members 테이블 조인
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_key", referencedColumnName = "member_key")
    private Members member;
    @Column(nullable = false, name = "owner_type")
    private String ownerType;

    @Column(nullable = false, name = "monthly_token_limit")
    private int monthlyTokenLimit;

    public void setId(Long planLimitKey) {
        this.planLimitKey = planLimitKey;
    }
}
