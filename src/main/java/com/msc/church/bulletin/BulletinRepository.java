package com.msc.church.bulletin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface BulletinRepository extends JpaRepository<Bulletin, Long> {

    boolean existsByServiceDate(LocalDate serviceDate);

    Optional<Bulletin> findByServiceDate(LocalDate serviceDate);

    /**
     * Returns the most-recently-published bulletin whose service_date is on or before
     * the cutoff. Callers pass {@code today + 1 day} so a Sunday-morning user still
     * sees that day's bulletin even if their device clock has rolled past midnight.
     */
    Optional<Bulletin> findFirstByPublishedTrueAndServiceDateLessThanEqualOrderByServiceDateDesc(
            LocalDate cutoff);

    /**
     * Eager-fetches the to-one references; the four child collections are loaded
     * lazily on access (the service is {@code @Transactional} so they resolve while
     * the session is still open). Hibernate refuses to JOIN FETCH multiple "bag"
     * collections in one query — {@code MultipleBagFetchException} — and per-collection
     * {@code @BatchSize(50)} keeps the resulting query count to {@code 1 + 4} per
     * detail load, which is fine for V1.
     */
    @EntityGraph(attributePaths = {"presider", "nextWeekPrayer"})
    Optional<Bulletin> findWithChildrenById(Long id);

    @EntityGraph(attributePaths = {"presider", "nextWeekPrayer"})
    Optional<Bulletin> findWithChildrenByServiceDate(LocalDate serviceDate);

    Page<Bulletin> findByServiceDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    Page<Bulletin> findByPublishedAndServiceDateBetween(
            boolean published, LocalDate from, LocalDate to, Pageable pageable);

    Page<Bulletin> findByPublished(boolean published, Pageable pageable);
}
