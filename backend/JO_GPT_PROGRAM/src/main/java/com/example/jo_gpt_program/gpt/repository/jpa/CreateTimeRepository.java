package com.example.jo_gpt_program.gpt.repository.jpa;

import com.example.entitycom.entity.log.CreateTimeLogs;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreateTimeRepository extends JpaRepository<CreateTimeLogs, Long> {

}
