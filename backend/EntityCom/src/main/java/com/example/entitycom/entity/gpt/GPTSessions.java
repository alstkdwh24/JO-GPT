package com.example.entitycom.entity.gpt;


import com.example.entitycom.entity.chat.ChatHistory;
import com.example.entitycom.entity.log.CreateTimeLogs;
import com.example.entitycom.entity.member.Members;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
// GPT 세션 및 지능형 분석(AI & Intelligence)

@Entity
@Getter
@Table(name = "gptSessions")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GPTSessions {
    @Id
    @Tsid
    @Column(name = "GPTSessions_key", unique = true)
    private Long GPTSessionsKey;

    public void setId(Long GPTSessionsKey) {
        this.GPTSessionsKey = GPTSessionsKey;
    }

    /*Members태이블 조인 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_key", referencedColumnName = "member_key") // DB 컬럼명
    private Members member;

    @Column(nullable = false, name = "model_name")
    private String modelName;

    /*CreateTimeLogs 테이블과 조인으로 세션 생성 시간 기록*/
    @OneToOne(mappedBy = "gptSessions")
    private CreateTimeLogs createTimeLogs;



    @OneToMany(mappedBy = "gptSessions", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ChatHistory> chatHistory = new ArrayList<>();




}
