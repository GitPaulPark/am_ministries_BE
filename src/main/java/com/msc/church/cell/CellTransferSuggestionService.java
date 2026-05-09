package com.msc.church.cell;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.cell.dto.CellMemberRef;
import com.msc.church.cell.dto.TransferApproveRequest;
import com.msc.church.cell.dto.TransferDismissRequest;
import com.msc.church.cell.dto.TransferSuggestionResponse;
import com.msc.church.cell.dto.TransferSuggestionResponse.CellRefSlim;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.member.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Newcomer-graduation workflow. Suggestions are created by
 * {@link NewcomerGraduationScheduler}; pastors approve (which performs the actual
 * membership transition atomically) or dismiss them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CellTransferSuggestionService {

    private final CellTransferSuggestionRepository repository;
    private final CellRepository cellRepository;
    private final CellMembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public List<TransferSuggestionResponse> listPending() {
        return repository
                .findByStatusOrderBySuggestedAtAsc(TransferSuggestionStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TransferSuggestionResponse approve(Long id, TransferApproveRequest request, AuthenticatedUser caller) {
        CellTransferSuggestion s = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (s.getStatus() != TransferSuggestionStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        Cell toCell = cellRepository.findById(request.toCellId())
                .orElseThrow(() -> new CellNotFoundException(request.toCellId()));

        // 1. Deactivate the source-cell membership.
        membershipRepository.findByMember_IdAndActiveTrue(s.getMember().getId()).stream()
                .filter(cm -> cm.getCell() != null
                        && cm.getCell().getId().equals(s.getFromCell().getId()))
                .findFirst()
                .ifPresent(cm -> {
                    cm.setActive(false);
                    cm.setLeftAt(LocalDate.now());
                    cm.setPrimary(false);
                });

        // 2. Make sure no other primary is hanging around (shouldn't, but defensive).
        membershipRepository.clearPrimaryFor(s.getMember().getId());

        // 3. Create the new primary membership in the target cell.
        CellMembership newMembership = CellMembership.builder()
                .member(s.getMember())
                .cell(toCell)
                .primary(true)
                .active(true)
                .joinedAt(LocalDate.now())
                .role("MEMBER")
                .build();
        membershipRepository.save(newMembership);

        // 4. Mark the suggestion approved.
        s.setStatus(TransferSuggestionStatus.APPROVED);
        s.setToCell(toCell);
        s.setApprovedAt(LocalDateTime.now());
        s.setApprovedByUserId(caller == null ? null : caller.id());
        if (request.notes() != null) s.setNotes(request.notes());

        log.info("Transfer approved: id={} member={} from={} to={} byUser={}",
                id, s.getMember().getId(),
                s.getFromCell().getId(), toCell.getId(),
                caller == null ? null : caller.id());
        return toResponse(s);
    }

    @Transactional
    public TransferSuggestionResponse dismiss(Long id, TransferDismissRequest request, AuthenticatedUser caller) {
        CellTransferSuggestion s = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (s.getStatus() != TransferSuggestionStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        s.setStatus(TransferSuggestionStatus.DISMISSED);
        s.setApprovedAt(LocalDateTime.now());
        s.setApprovedByUserId(caller == null ? null : caller.id());
        if (request != null && request.notes() != null) s.setNotes(request.notes());
        log.info("Transfer dismissed: id={} byUser={}", id,
                caller == null ? null : caller.id());
        return toResponse(s);
    }

    /**
     * Scheduler entry point. Looks up newcomer cells, finds members whose primary
     * membership in a NEWCOMER cell exceeds the threshold, and creates a PENDING
     * suggestion for each (skipping members who already have one).
     */
    @Transactional
    public int scanForGraduations(int weeksThreshold) {
        List<Cell> newcomerCells = cellRepository.findByActiveTrue(
                org.springframework.data.domain.Sort.by("code")).stream()
                .filter(c -> c.getType() == CellType.NEWCOMER)
                .toList();
        int created = 0;
        LocalDate today = LocalDate.now();
        for (Cell cell : newcomerCells) {
            for (CellMembership cm : membershipRepository.findActivePrimaryByCellId(cell.getId())) {
                LocalDate joined = cm.getJoinedAt();
                if (joined == null) continue;
                long weeks = ChronoUnit.WEEKS.between(joined, today);
                if (weeks < weeksThreshold) continue;
                if (repository.countPendingForMember(cm.getMember().getId()) > 0) continue;
                CellTransferSuggestion s = CellTransferSuggestion.builder()
                        .member(cm.getMember())
                        .fromCell(cell)
                        .status(TransferSuggestionStatus.PENDING)
                        .build();
                repository.save(s);
                created++;
            }
        }
        if (created > 0) log.info("Newcomer graduation scan created {} suggestions", created);
        return created;
    }

    private TransferSuggestionResponse toResponse(CellTransferSuggestion s) {
        Member m = s.getMember();
        return new TransferSuggestionResponse(
                s.getId(),
                m == null ? null : new CellMemberRef(m.getId(), m.getNameKr(), m.getNameEn()),
                m == null ? null : m.getJoinedAt(),
                cellRef(s.getFromCell()),
                cellRef(s.getToCell()),
                s.getSuggestedAt(),
                s.getStatus(),
                s.getNotes()
        );
    }

    private CellRefSlim cellRef(Cell c) {
        if (c == null) return null;
        return new CellRefSlim(c.getId(), c.getCode(), c.getNameKr(), c.getNameEn());
    }
}
