package com.msc.church.meeting;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommitteeRepository extends JpaRepository<Committee, Long> {

    Optional<Committee> findByCode(String code);

    List<Committee> findByActiveTrue(Sort sort);
}
