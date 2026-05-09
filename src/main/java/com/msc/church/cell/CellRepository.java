package com.msc.church.cell;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CellRepository extends JpaRepository<Cell, Long> {

    Optional<Cell> findByCode(String code);

    boolean existsByLeader_Id(Long memberId);

    List<Cell> findByActiveTrue(Sort sort);

    List<Cell> findByActiveTrueAndType(CellType type, Sort sort);

    List<Cell> findByLeader_Id(Long memberId);
}
