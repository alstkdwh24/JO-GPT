package com.example.entitycom.entity.chat;


import com.example.entitycom.entity.log.CreateTimeLogs;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
// 멀티모달 첨부 파일 (Multimodal Context)

@Entity
@Table(name="chat_attachments")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAttachments {
    @Id
    @Tsid
    @Column(name = "chat_attachment_key", unique = true, nullable = false)
    private Long chatAttachmentKey;

    // ChatHistory 테이블 조인
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_history_key")
    private ChatHistory chatHistory;

    @Column(nullable = false, name = "file_name")
    private String fileName;

    @Column(nullable = false, name = "file_path")
    private String filePath;

    @Column(nullable = false, name = "file_type")
    private String fileType;

    @Column(nullable = false, name = "extracted_text")
    private String extractedText;


    /* 이 테이블 데이터 생성 시간 */
    @OneToOne(mappedBy = "chatAttachment")
    private CreateTimeLogs createTimeLogs;

    public void setId(Long chatAttachmentKey) {
        this.chatAttachmentKey = chatAttachmentKey;
    }




}
