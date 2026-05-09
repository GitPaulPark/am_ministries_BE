package com.msc.church.cell;

import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.member.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Internal API surface for the Member module. The full Cells module (Week 5) will add
 * the public CRUD; for now only the operations strictly needed by member create/update
 * live here, so the canonical Member pattern stays tested end-to-end.
 */
@Service
@RequiredArgsConstructor
public class CellMembershipService {

    private final CellRepository cellRepository;
    private final CellMembershipRepository membershipRepository;

    /**
     * Sets the given cell as the member's primary, demoting any existing primary.
     * Creates a new membership row if one doesn't exist for the (member, cell) pair.
     */
    @Transactional
    public void setPrimary(Member member, Long cellId) {
        Cell cell = cellRepository.findById(cellId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CELL_NOT_FOUND));

        membershipRepository.clearPrimaryFor(member.getId());

        CellMembership existing = membershipRepository
                .findByMember_IdAndPrimaryTrue(member.getId())
                .orElse(null);
        if (existing != null && existing.getCell().getId().equals(cellId)) {
            existing.setPrimary(true);
            existing.setActive(true);
            return;
        }

        CellMembership membership = CellMembership.builder()
                .member(member)
                .cell(cell)
                .primary(true)
                .active(true)
                .joinedAt(LocalDate.now())
                .role("MEMBER")
                .build();
        membershipRepository.save(membership);
        // The owning side (CellMembership.member) is what actually persists; this just
        // keeps the in-memory Member.memberships list consistent so callers re-using
        // the same managed entity within the transaction (e.g. MemberService.create)
        // see the new membership without a flush + clear + re-query dance.
        member.getMemberships().add(membership);
    }
}
