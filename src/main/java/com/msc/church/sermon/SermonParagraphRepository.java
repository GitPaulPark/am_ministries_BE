package com.msc.church.sermon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SermonParagraphRepository extends JpaRepository<SermonParagraph, Long> {

    List<SermonParagraph> findBySermon_IdOrderByOrderIdxAsc(Long sermonId);

    long deleteBySermon_Id(Long sermonId);
}
