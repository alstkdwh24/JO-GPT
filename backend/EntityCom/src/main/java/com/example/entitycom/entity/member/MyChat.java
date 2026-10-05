package com.example.entitycom.entity.member;


import com.example.entitycom.entity.chat.ShowChat;
import com.example.entitycom.entity.gpt.GPT;
import com.example.entitycom.entity.log.CreateTimeLogs;
import io.hypersistence.utils.hibernate.id.Tsid;
import com.example.entitycom.converter.AesEncryptConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="my_chat")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyChat {
    @Id
    @Tsid
    @Column(name = "my_chat_key", unique = true, nullable = false)
    private Long myChatKey;
    @Convert(converter = AesEncryptConverter.class) // 2차 암호화를 시켜주는 어노테이션
    @Lob
    @Column(nullable = true , name = "my_chat_contents", columnDefinition = "LONGTEXT")
    private String myChatContents;



    @Column(nullable = true , name = "my_chat_image")
    private String myChatImage;

    // GPT를 참조
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gpt_key" ,referencedColumnName ="gpt_key")
    private GPT gpt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_key" ,referencedColumnName ="member_key")
    private Members member;



    /* CreateTimeLogs 테이블 참조 */
    @OneToOne(mappedBy = "myChatKey", cascade = CascadeType.ALL, orphanRemoval = true)
    private CreateTimeLogs createTimeLogs;

    //ShowChat 참조 추가 - 이게 있어야 mappedBy = "showChat" 이 동작함
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_key" ,referencedColumnName ="show_key")
    private ShowChat showChat;


    public void setId(Long myChatKey) {
        this.myChatKey = myChatKey;
    }
}
