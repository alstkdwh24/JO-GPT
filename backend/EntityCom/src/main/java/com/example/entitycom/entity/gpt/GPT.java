package com.example.entitycom.entity.gpt;


import com.example.entitycom.entity.log.CreateTimeLogs;
import com.example.entitycom.entity.member.Members;
import com.example.entitycom.entity.member.MyChat;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "gpt")
@NoArgsConstructor
@AllArgsConstructor
public class GPT {
    @Id
    @Tsid
    @Column(name = "gpt_key", unique = true)
    private Long GPTKey;

    // Members 테이블 조인
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_key")
    private Members member;

    @Column(nullable = false, name = "gpt_model")
    private String GptModel;


    /* GptChat 테이블 데이터 조인 리스트화 */
    @OneToMany(mappedBy = "gptKey", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GptChat> gptChat = new ArrayList<>();

    /* MyChat 테이블 데이터 조인 리스트화 */
    @OneToMany(mappedBy = "gpt", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MyChat> myChat = new ArrayList<>();

    /* CreateTimeLogs GPT 생성 시각 */
    @OneToOne(mappedBy = "gpt", cascade = CascadeType.ALL, orphanRemoval = true)
    private CreateTimeLogs createTimeLogs;

    @Builder
    public GPT(Long GPTKey, Members member, String GptModel) {
        this.GPTKey = GPTKey;
        this.member = member;
        this.GptModel = GptModel;
        this.gptChat = new ArrayList<>();
        this.myChat = new ArrayList<>();
    }

    public void setId(Long GPTKey) {
        this.GPTKey = GPTKey;
    }
}
