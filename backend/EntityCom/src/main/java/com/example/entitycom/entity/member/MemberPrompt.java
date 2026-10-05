package com.example.entitycom.entity.member;

import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_prompts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberPrompt {

    @Id
    @Tsid
    @Column(name = "prompt_key", unique = true, nullable = false)
    private Long promptKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_key", referencedColumnName = "member_key")
    private Members member;

    @Column(name = "prompt_name", nullable = false)
    private String promptName;

    @Column(name = "prompt_content", columnDefinition = "TEXT")
    private String promptContent;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
