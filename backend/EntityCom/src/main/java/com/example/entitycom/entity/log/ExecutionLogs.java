package com.example.entitycom.entity.log;

import com.example.entitycom.entity.task.TaskQueue;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
// ExecutionLogs는 시스템 내에서 실행되는 TaskQueue의 상세 실행기록과 로그를 저장하는 역할을 한다.
@Entity
@Getter
@Table(name = "execution_logs")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionLogs {
    @Id
    @Tsid
    @Column(name = "execution_logs_key", unique = true, nullable = false)
    private Long executionLogKey;

    /* TaskQueue 실행되는 것을 알기 위한 조인 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_queue_key" ,referencedColumnName ="task_queue_key")
    private TaskQueue taskQueue;

    @Column(nullable = false, name = "execution_log")
    private String executionLog;

    @Column(nullable = false, name = "message")
    private String message;

    @Column(nullable = false, name = "screen_shot_path")
    private String screenShotPath;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    /* ExecutionLogs 생성시간 기록 */
    @OneToOne(mappedBy = "executionLogs")
    private CreateTimeLogs createTimeLogsKey;

    @PrePersist
    private void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }



    public void setId(Long executionLogKey) {
        this.executionLogKey = executionLogKey;
    }
}
