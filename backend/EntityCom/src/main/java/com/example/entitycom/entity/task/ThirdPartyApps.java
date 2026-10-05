package com.example.entitycom.entity.task;

import com.example.entitycom.entity.device.Devices;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
// 서드파티 앱 및 보안 정책 (App Registry)

@Table(name="third_party_apps")
@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ThirdPartyApps {
    @Id
    private Long ThirdPartyApps;

    public void setId(Long ThirdPartyApps) {
        this.ThirdPartyApps = ThirdPartyApps;
    }

    // Device 테이블 조인
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_key", referencedColumnName = "device_key")
    private Devices devices;

    @Column(nullable = false, name = "app_name")
    private String appName;

    @Column(nullable = false, name = "package_id")
    private String packageId;

    @Column(nullable = false, name = "allow_remote_control")
    private boolean allowRemoteControl;

    @Column(nullable = false, name = "allow_file_access")
    private boolean allowFileAccess;

    @Column(nullable = false, name = "is_monitored")
    private boolean isMonitored;

}
