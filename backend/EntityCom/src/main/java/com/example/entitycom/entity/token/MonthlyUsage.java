package com.example.entitycom.entity.token;

import com.example.entitycom.entity.member.Members;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 실제 사용량
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name="monthly_usage")
public class MonthlyUsage {
    @Id
    @Tsid
    @Column(name = "monthly_usage_key", unique = true)
    private Long MonthlyUsageKey;

    @Column(nullable = false, name = "usage_count")
    private String yyyyMM;

    // 아이디를 참고하기 위한 조인
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_key", referencedColumnName = "member_key")
    private Members member;

    @Column(nullable = false, name = "owner_type")
    private String ownerType;

    @Column(nullable = false, name = "used_unit")
    private int usedUnit;

    @Column(nullable = false, name = "reserved_unit")
    private int reservedUnit;

    public void setId(Long MonthlyUsageKey) {
        this.MonthlyUsageKey = MonthlyUsageKey;
    }
}
