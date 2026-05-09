package com.msc.church.sermon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface SermonRepository extends JpaRepository<Sermon, Long> {

    boolean existsBySermonDate(LocalDate sermonDate);

    @EntityGraph(attributePaths = {"preacher", "linkedBulletin"})
    Optional<Sermon> findWithRefsById(Long id);

    Optional<Sermon> findFirstByPublishedTrueOrderBySermonDateDesc();

    /**
     * V1 search: case-insensitive LIKE across titles, scripture, theme. With ~50
     * sermons indexed this is plenty fast. V2 follow-up: add a full-text column or
     * move to a search engine.
     */
    @Query("""
            SELECT s FROM Sermon s
            WHERE (:q IS NULL OR :q = ''
                   OR LOWER(COALESCE(s.titleKr, ''))    LIKE CONCAT('%', LOWER(:q), '%')
                   OR LOWER(COALESCE(s.titleEn, ''))    LIKE CONCAT('%', LOWER(:q), '%')
                   OR LOWER(s.scriptureRef)             LIKE CONCAT('%', LOWER(:q), '%')
                   OR LOWER(COALESCE(s.theme, ''))      LIKE CONCAT('%', LOWER(:q), '%'))
              AND (:year IS NULL OR FUNCTION('YEAR', s.sermonDate) = :year)
              AND (:preacherId IS NULL OR s.preacher.id = :preacherId)
              AND (:publishedOnly = false OR s.published = true)
            """)
    Page<Sermon> search(@Param("q") String q,
                        @Param("year") Integer year,
                        @Param("preacherId") Long preacherId,
                        @Param("publishedOnly") boolean publishedOnly,
                        Pageable pageable);
}
