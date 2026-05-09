package com.msc.church.member;

import com.msc.church.cell.CellMembership;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable predicates for {@code GET /members}. Combined with {@code Specification.and(...)}
 * by the service so each filter is independently testable.
 */
public final class MemberSpecifications {

    private MemberSpecifications() {
    }

    /** Search across name_kr, name_en, email, phone (case-insensitive). */
    public static Specification<Member> matchesQuery(String q) {
        if (q == null || q.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String like = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nameKr")), like),
                cb.like(cb.lower(cb.coalesce(root.get("nameEn"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like),
                cb.like(cb.coalesce(root.get("phone"), ""), like)
        );
    }

    public static Specification<Member> hasStatus(MemberStatus status) {
        if (status == null) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Member> hasRoleLabel(String roleLabel) {
        if (roleLabel == null || roleLabel.isBlank()) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> cb.equal(root.get("roleLabel"), roleLabel);
    }

    /** Filter by primary cell. Joins memberships and matches active + primary. */
    public static Specification<Member> inPrimaryCell(Long cellId) {
        if (cellId == null) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> {
            // distinct so a member with multiple memberships isn't duplicated.
            if (query != null) query.distinct(true);
            Join<Member, CellMembership> mem = root.join("memberships");
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(mem.get("primary"), true));
            preds.add(cb.equal(mem.get("active"), true));
            preds.add(cb.equal(mem.get("cell").get("id"), cellId));
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
