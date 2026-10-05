package com.example.entitycom.entity.log;

import com.example.entitycom.entity.task.TaskQueue;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name="completed_time_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CompletedTimeLogs {
    @Id
    private Long completedTimeLogKey;

    public void setId(Long completedTimeLogKey) {
        this.completedTimeLogKey = completedTimeLogKey;
    }

    @CreatedDate // 최초 기록만 원할 때 @CreatedDate
    @Column(nullable = false, name = "completed_time")
    private LocalDateTime completedTime;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_queue_key", referencedColumnName = "task_queue_key")
    private TaskQueue taskQueue;
}
