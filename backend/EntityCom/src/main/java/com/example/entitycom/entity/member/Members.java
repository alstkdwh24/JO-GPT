package com.example.entitycom.entity.member;

import com.example.entitycom.converter.AesEncryptConverter;
import com.example.entitycom.entity.chat.ShowChat;
import com.example.entitycom.entity.connect.ConnectedAccounts;
import com.example.entitycom.entity.device.Devices;
import com.example.entitycom.entity.gpt.GPT;
import com.example.entitycom.entity.gpt.GPTSessions;
import com.example.entitycom.entity.log.CreateTimeLogs;
import com.example.entitycom.entity.token.MonthlyUsage;
import com.example.entitycom.entity.token.PlanLimits;
import com.example.entitycom.entity.token.UserTokens;
import com.example.entitycom.enums.Role;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

//유저 아이디 엔터티
@Entity
@Table(name = "members")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Members {
    @Id
    @Tsid
    @Column(name = "member_key", unique = true)
    private Long memberKey;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Convert(converter = AesEncryptConverter.class)
    @Column(name = "nickname", nullable = false)
    private String nickname;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role; // 역할 (ADMIN, USER, GUEST 등)

    @Column(name = "extra_settings", columnDefinition = "TEXT")
    private String extraSettings; // 추가 설정 리스트 (개인 설정 리스트)

    @Column(name = "custom_prompt", columnDefinition = "TEXT")
    private String customPrompt; // 사용자 커스텀 프롬프트


    // 회원 정보를 참조하기 위한 조인
    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserCredentials userCredentials;

    // SNS로그인을 참조하기 위한 조인
    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private AuthProviders authProviders;

    // 기기 정보를 참조하기 위한 조인
    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private Devices devices;

    // GPT 대화내용을 참조하기 위한 조인
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GPT> gptList = new ArrayList<>();

    // GPT 세션 정보를 참조하기 위한 조인
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GPTSessions> gptSessionsList = new ArrayList<>();

    // 월 토큰 사용량을 참조하기 위한 조인
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MonthlyUsage> monthlyUsageList = new ArrayList<>();

    // 사용자의 토큰사용량 제한을 참조하기 위한 조인
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanLimits> planLimits = new ArrayList<>();

    // 유저의 토큰 사용량을 참조하기 위한 조인
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserTokens> userTokenList = new ArrayList<>();

    // 생성 시간 관련 조인
    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private CreateTimeLogs createTimeLogs;

    @OneToMany(mappedBy = "members", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ShowChat> showChatList = new ArrayList<>();

    // Members.java 에 추가
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConnectedAccounts> connectedAccounts = new ArrayList<>();

    public void changeMemberId(String memberId) {
        if (memberId != null && !memberId.isEmpty()) {
            this.memberId = memberId;
        }
    }

    public void changeRole(Role role) {
        if (role != null) {
            this.role = role;
        }
    }

    // 해결: String 매개변수를 받는 메서드를 직접 선언합니다.
    public void changeNickname(String newNickname) {
        // 필요하다면 이곳에 길이 제한 등 검증 로직을 추가할 수 있습니다.
        if (newNickname == null || newNickname.trim().isEmpty()) {
            throw new IllegalArgumentException("닉네임은 비어있을 수 없습니다.");
        }
        this.nickname = newNickname;
    }


    // 삽입할때는 Members 엔티티에서 저장한 후에, 자식 객체를 담아두는 것이 좋습니다. 이렇게 하면 연관관계가 명확해지고, 데이터 일관성을
    // 유지할 수 있습니다.
    public void changeUserCredentials(UserCredentials userCredentials) {
        this.userCredentials = userCredentials;
        if (userCredentials != null && userCredentials.getMember() != this) {
            userCredentials.changeId(this);
        }
    }

    public void changeCustomPrompt(String customPrompt) {
        if (customPrompt != null) {
            this.customPrompt = customPrompt;
        }
    }
}
