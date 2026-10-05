package com.example.entitycom.entity.log;

import com.example.entitycom.entity.chat.ChatAttachments;
import com.example.entitycom.entity.chat.ShowChat;
import com.example.entitycom.entity.device.Devices;
import com.example.entitycom.entity.gpt.GPT;
import com.example.entitycom.entity.gpt.GPTSessions;
import com.example.entitycom.entity.gpt.GptChat;
import com.example.entitycom.entity.member.Members;
import com.example.entitycom.entity.member.MyChat;
import com.example.entitycom.entity.task.TaskQueue;
import com.example.entitycom.entity.token.UserTokens;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name="create_time_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
// 중요: JPA Auditing 활성화. JPA가 데이터베이스 테이블에 데이터가 저장되거나 수정될 때 생성시간, 수정시간, 생성자, 수정자 등을 자동으로 기록해주는 기능이다.
public class CreateTimeLogs {
    @Id
    @Tsid
    @Column(name = "create_time_logs_key", unique = true)
    private Long createTimeLogsKey;

    // ✅ 이 필드를 추가해야 시간이 자동 기록됨
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;


    // 멤버와 조인
    @OneToOne(fetch = FetchType.LAZY) // 지금 당장 필요하지 않는 데이터는 지연로딩으로 설정 나중에 가져온다.
    @JoinColumn(name = "member_key", referencedColumnName = "member_key")
    private Members member;

    // gpt_chat과 조인
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Gpt_chat_key", referencedColumnName = "Gpt_chat_key")
    private GptChat gptChatKey;

    // MyChat과 조인
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "my_chat_key", referencedColumnName = "my_chat_key")
    private MyChat myChatKey;

    // Devices와 조인
    @ManyToOne
    @JoinColumn(name = "device_key", referencedColumnName = "device_key")
    private Devices devices;

    /* TaskQueue 생성시간 조인 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_queue_key", referencedColumnName = "task_queue_key")
    private TaskQueue taskQueue;



    /* ChatAttachments 테이블 조인 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_attachment_key", referencedColumnName = "chat_attachment_key")
    private ChatAttachments chatAttachment;

    /*GPT*/
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gpt_key", referencedColumnName = "gpt_key")
    private GPT gpt;

    /* GPTSessions 생성시간 기록 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gpt_sessions_key", referencedColumnName = "GPTSessions_key")
    private GPTSessions gptSessions;

    /* ExecutionLogs 생성시간 기록 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_logs_key", referencedColumnName = "execution_logs_key")
    private ExecutionLogs executionLogs;

    /* 유저 토큰 사용시간 기록을 위한 조인 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_token_key", referencedColumnName = "user_token_key")
    private UserTokens userToken;

    /*ShowChat 참조 추가*/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_key", referencedColumnName = "show_key")
    private ShowChat showChat;
}
