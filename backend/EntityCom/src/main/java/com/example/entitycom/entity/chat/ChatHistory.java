package com.example.entitycom.entity.chat;


import com.example.entitycom.entity.device.Devices;
import com.example.entitycom.entity.gpt.GPTSessions;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
//명령어 히스토리

@Entity
@Table(name = "chat_history")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ChatHistory {
    @Id
    @Tsid
    @Column(name = "chat_history_key", unique = true, nullable = false)
    private Long chatHistoryKey;

    /*GPTSession 테이블 조인*/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gpt_sessions_key_gpt_sessions_key", referencedColumnName = "GPTSessions_key")
    private GPTSessions gptSessions;
    /*Device 테이블 조인*/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "devices_key_device_key", referencedColumnName = "device_key")
    private Devices devices;
    //chatHistory 테이블 조인
    @OneToMany(mappedBy = "chatHistory", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ChatAttachments> chatAttachments = new ArrayList<>();


    public void setDevicesKey(Devices devices) {
        this.devices = devices;
    }

    public void setGptSessionsKey(GPTSessions gptSessions) {
        this.gptSessions = gptSessions;
    }

    public void setId(Long chatHistoryKey) {
        this.chatHistoryKey = chatHistoryKey;
    }
}
