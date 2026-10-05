package com.example.jo_gpt_program.gpt.repository.jpa;

import com.example.entitycom.entity.connect.ConnectedAccounts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
// 연동 관련 로직
public interface ConnectedAccountsRepository extends JpaRepository<ConnectedAccounts, Long> {
    // memberKey + provider로 이미 연결된 계정 찾기
    Optional<ConnectedAccounts> findByMember_MemberKeyAndProvider(Long memberKey, String provider);

}
