package com.example.entitycom.entity.task;

import com.example.entitycom.converter.JsonObjectConverter;
import com.example.entitycom.entity.chat.ChatHistory;
import com.example.entitycom.entity.device.Devices;
import com.example.entitycom.entity.log.CompletedTimeLogs;
import com.example.entitycom.entity.log.CreateTimeLogs;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minidev.json.JSONObject;


// 제어 워크플로우 및 작업 큐 (Cross-Device Task Queue)
// Windows에서 Phone으로, Phone에서 Windows로의 명령 중계 핵심
@Entity
@Table(name="task_queue")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskQueue {
    @Id
    @Tsid
    @Column(name = "task_queue_key", unique = true, nullable = false)
    private Long taskQueueKey;

    // ChatHistory 테이블 조인
    @OneToOne
    @JoinColumn(name = "chat_history_key", referencedColumnName = "chat_history_key")
    private ChatHistory chatHistory;

    // Devices 테이블 조인 senderDevice
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_device_id", referencedColumnName = "device_key")
    private Devices senderDevice;

    // Devices 테이블 조인 receiverDevice
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_device_id", referencedColumnName = "device_key")
    private Devices receiverDevice;

    @Column(nullable = false, name = "action_type")
    private String actionType;

    // Json형태로 변환된 명령의 페이로드 데이터 JSONObject객체를 toJSONString()으로 변환
    // DB에서 가져온 문자열을 net.minidev.json.parser.JSONParser를 사용하여 다시 JSONObject객체로 변환
    @Convert(converter = JsonObjectConverter.class)
    @Column(name = "command_payload", columnDefinition = "TEXT")
    private JSONObject commandPayload;

    @Column(nullable = false, name = "status")
    private String status;

    @Column(nullable = false, name = "priority")
    private int priority;

    /* TaskQueue 생성시간 조인 */
    @OneToOne(mappedBy = "taskQueue", cascade = CascadeType.ALL, orphanRemoval = true)
    private CreateTimeLogs createTimeLogs;

    /* TaskQueue 완료시간 */
    @OneToOne(mappedBy = "taskQueue", cascade = CascadeType.ALL, orphanRemoval = true)
    private CompletedTimeLogs completedTimeLogs;



    public void setId(Long taskQueueKey) {
        this.taskQueueKey = taskQueueKey;
    }

}
