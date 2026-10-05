package com.example.entitycom.entity.device;

import com.example.entitycom.entity.chat.ChatHistory;
import com.example.entitycom.entity.log.CreateTimeLogs;
import com.example.entitycom.entity.member.Members;
import com.example.entitycom.entity.task.TaskQueue;
import com.example.entitycom.entity.task.ThirdPartyApps;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
// 기기 및 에이전트 관리 (Electron, Flutter 연동의 핵심)

@Entity
@Table(name = "devices")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Devices {
    @Id
    @Tsid
    @Column(name = "device_key", unique = true, nullable = false)
    private Long deviceKey;

    public void setId(Long deviceKey) {
        this.deviceKey = deviceKey;
    }

    /* Members 테이블 조인 */
    @OneToOne
    @JoinColumn(name = "member_key", referencedColumnName = "member_key") // DB 컬럼명
    private Members member;

    @Column(nullable = true, name = "device_name")
    private String deviceName;

    @Column(nullable = false, name = "device_type")
    private String deviceType;

    @Column(nullable = false, name = "os_info")
    private String osInfo;

    @Column(nullable = false, name = "fcm_token")
    private String fcmToken;

    @Column(nullable = true, name = "agent_status")
    private String agentStatus;

    @Column(nullable = false, name = "cpu_usage")
    private int cpuUsage;
    @Column(nullable = false, name = "ram_usage")
    private int ramUsage;

    @Column(nullable = false, name = "battery_level")
    private int batteryLevel;

    /* CreateTimeLogs 테이블 조인 리스트화 */
    @OneToMany(mappedBy = "devices", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CreateTimeLogs> createTimeLogsList = new ArrayList<>();

    /* TaskQueue 테이블 조인 리스트화 */
    @OneToMany(mappedBy = "senderDevice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskQueue> taskQueueList = new ArrayList<>();

    /* ChatHistory 테이블 조인 리스트화 */
    @OneToMany(mappedBy = "devices", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ChatHistory> chatHistoryList = new ArrayList<>();

    /* ThirdPartyApps 테이블 조인 리스트화 */
    @OneToMany(mappedBy = "devices", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ThirdPartyApps> thirdPartyAppsList = new ArrayList<>();
}