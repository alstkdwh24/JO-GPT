package com.example.entitycom.entity.chat;

import com.example.entitycom.entity.gpt.GptChat;
import com.example.entitycom.entity.log.CreateTimeLogs;
import com.example.entitycom.entity.member.Members;
import com.example.entitycom.entity.member.MyChat;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Table(name = "show_chat")

public class ShowChat {
    @Id
    @Tsid
    @Column(name = "show_key", unique = true, nullable = false)
    private Long showChatKey;
    //Hibernate는 List를 내부적으로 **"Bag"** 라는 자료구조로 처리하는데, Bag 두개를 동시에 JOIN FETCH 하는 것을 허용하지 않습니다. 그래서 MultipleBagFetchException이 발생한 것입니다. 이를 set으로 바꾸면 Hibernate가  Bag 가 아닌 set으로 처리하므로 fetch 가 가능해집니다.
    @OneToMany(mappedBy = "showChat", cascade = CascadeType.ALL, fetch = FetchType.LAZY )
    private Set<MyChat> myChat;

    @OneToMany(mappedBy = "showChat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<GptChat> gptChat;
    //cascade 영속성 정의,  부모 엔티티에 대한 작업(저장, 삭제 등을) 자식 엔티티에도 자동으로 전파하는 JPA 기능
    @OneToMany(mappedBy = "showChat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)  // CreateTimeLogs에 showChat 필드 추가 필요
    private Set<CreateTimeLogs> createTimeLogs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "members_key", referencedColumnName = "member_key" )
    private Members members;

    @OneToOne
    @JoinColumn(name = "chat_attachment_key")
    private ChatAttachments chatAttachment;


}
