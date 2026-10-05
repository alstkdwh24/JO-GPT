package com.example.entitycom.entity.gpt;

import com.example.entitycom.entity.chat.ShowChat;
import com.example.entitycom.entity.log.CreateTimeLogs;

import io.hypersistence.utils.hibernate.id.Tsid;
import com.example.entitycom.converter.AesEncryptConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "gpt_chat")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GptChat {
    @Id
    @Tsid
    @Column(name = "Gpt_chat_key", unique = true, nullable = false)
    private Long GptChatKey;

    @Convert(converter = AesEncryptConverter.class)
    @Column(nullable = false, name = "Gpt_chat_contents", columnDefinition = "TEXT")
    private String gptChatContents;

    @Column(nullable = true, name = "Gpt_chat_image")
    private String GptChatImage;

    @OneToOne(mappedBy = "gptChatKey", cascade = CascadeType.ALL, orphanRemoval = true)
    // orphanRemoval: 부모 객체와 연결이 끊어진 자식 객체를 데이터베이스에서 자동으로 삭제하는 설정
    // cascade: 부모 엔티티에서 수행되는 모든 상태 변경을 자식 엔티티에도 동일하게 적용하겠다는 설정
    // - PERSIST: 부모 엔티티가 영속화될 때 자식 엔티티도 함께 영속화 (저장)
    // - REMOVE: 부모 엔티티가 삭제될 때 자식 엔티티도 함께 삭제
    // - MERGE: 부모 엔티티가 병합될 때 자식 엔티티도 함께 병합 (수정 반영)
    // - REFRESH / DETACH: 부모 엔티티가 최신 상태로 갱신되거나 영속성 컨텍스트에서 분리될 때 자식 엔티티도 동일하게 처리
    private CreateTimeLogs createTimeLogs;

    // GPT 테이블과 조인
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "gpt_key_gpt_key", referencedColumnName = "GPT_key")
    private GPT gptKey;

    /* ShowChat 참조 추가 */
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "show_key", referencedColumnName = "show_key")
    private ShowChat showChat;

    public void setId(Long GptChatKey) {
        this.GptChatKey = GptChatKey;
    }
}
