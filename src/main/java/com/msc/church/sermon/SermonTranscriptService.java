package com.msc.church.sermon;

import com.msc.church.auth.AuthenticatedUser;
import com.msc.church.auth.Role;
import com.msc.church.common.BusinessException;
import com.msc.church.common.ErrorCode;
import com.msc.church.sermon.dto.ParagraphBulkReplaceRequest;
import com.msc.church.sermon.dto.ParagraphDto;
import com.msc.church.sermon.dto.SermonTranscriptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Owns transcript-PDF upload + the render-ready transcript shape consumed by the
 * bilingual sermon reader. Pairs adjacent EN/KR paragraphs (matching pair_key)
 * into a single block so the client doesn't have to re-derive the pairing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SermonTranscriptService {

    private final SermonRepository sermonRepository;
    private final SermonParagraphRepository paragraphRepository;
    private final SermonPdfParser parser;

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    @Transactional
    public SermonTranscriptResponse uploadPdf(Long sermonId, MultipartFile file,
                                              AuthenticatedUser caller) {
        if (caller == null || (caller.role() != Role.ADMIN && caller.role() != Role.PASTOR)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Sermon sermon = sermonRepository.findById(sermonId)
                .orElseThrow(() -> new SermonNotFoundException(sermonId));

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "file");
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "pdf_only");
        }

        Path base = Paths.get(uploadsDir, "sermons", String.valueOf(sermonId)).toAbsolutePath().normalize();
        Path target = base.resolve("transcript.pdf");
        try {
            Files.createDirectories(base);
            file.transferTo(target);
        } catch (IOException e) {
            log.warn("Failed to save sermon PDF: sermonId={} err={}", sermonId, e.getMessage());
            throw new BusinessException(ErrorCode.AUDIO_IO);
        }

        // Replace any prior parsed paragraphs.
        paragraphRepository.deleteBySermon_Id(sermonId);
        paragraphRepository.flush();

        List<SermonParagraph> parsed;
        try {
            parsed = parser.parse(target, sermon);
        } catch (IOException e) {
            log.warn("PDF parse failed: sermonId={} err={}", sermonId, e.getMessage());
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "pdf_parse");
        }

        paragraphRepository.saveAll(parsed);
        sermon.setTranscriptPdfUrl("/uploads/sermons/" + sermonId + "/transcript.pdf");
        sermon.setTranscriptSingleLang(isSingleLanguage(parsed));

        log.info("Sermon transcript PDF parsed: sermonId={} paragraphs={} singleLang={}",
                sermonId, parsed.size(), sermon.getTranscriptSingleLang());

        return buildResponse(sermon, parsed);
    }

    /**
     * Admin transcript editor — returns raw paragraph rows (with ids) ordered
     * by {@code orderIdx}. The reader endpoint returns the rendered shape;
     * this one is for the edit screen.
     */
    @Transactional(readOnly = true)
    public List<ParagraphDto> listParagraphs(Long sermonId, AuthenticatedUser caller) {
        assertAdminOrPastor(caller);
        // Validate the sermon exists so the editor 404s cleanly.
        sermonRepository.findById(sermonId)
                .orElseThrow(() -> new SermonNotFoundException(sermonId));
        return paragraphRepository.findBySermon_IdOrderByOrderIdxAsc(sermonId).stream()
                .map(p -> new ParagraphDto(
                        p.getId(),
                        p.getOrderIdx() == null ? 0 : p.getOrderIdx(),
                        p.getSectionIdx(),
                        p.getSectionTitleKr(),
                        p.getSectionTitleEn(),
                        p.getKind(),
                        p.getLanguage(),
                        p.getText(),
                        p.getScriptureRef(),
                        p.getPairKey()))
                .toList();
    }

    /**
     * Replace the full paragraph list. Diffing strategy: rows with an id are
     * updated in place; rows without an id are inserted; existing rows whose id
     * is absent from the request are deleted. {@code orderIdx} is derived from
     * the array index (0-based) so the client never has to manage it.
     */
    @Transactional
    public List<ParagraphDto> bulkReplaceParagraphs(Long sermonId,
                                                    ParagraphBulkReplaceRequest req,
                                                    AuthenticatedUser caller) {
        assertAdminOrPastor(caller);
        Sermon sermon = sermonRepository.findById(sermonId)
                .orElseThrow(() -> new SermonNotFoundException(sermonId));
        if (req.paragraphs() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "paragraphs");
        }

        // Index existing rows by id so we can update in place.
        Map<Long, SermonParagraph> existing = new java.util.HashMap<>();
        for (SermonParagraph p : paragraphRepository.findBySermon_IdOrderByOrderIdxAsc(sermonId)) {
            existing.put(p.getId(), p);
        }

        List<SermonParagraph> rebuilt = new java.util.ArrayList<>();
        int order = 0;
        for (ParagraphBulkReplaceRequest.Input in : req.paragraphs()) {
            if (in.text() == null || in.text().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "text");
            }
            if (!isValidKind(in.kind()))     throw new BusinessException(ErrorCode.VALIDATION_FAILED, "kind");
            if (!isValidLanguage(in.language())) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "language");

            SermonParagraph p;
            if (in.id() != null && existing.containsKey(in.id())) {
                p = existing.remove(in.id());
                p.setOrderIdx(order);
                p.setSectionIdx(in.sectionIdx());
                p.setSectionTitleKr(blankToNull(in.sectionTitleKr()));
                p.setSectionTitleEn(blankToNull(in.sectionTitleEn()));
                p.setKind(in.kind());
                p.setLanguage(in.language());
                p.setText(in.text().trim());
                p.setScriptureRef(blankToNull(in.scriptureRef()));
                p.setPairKey(blankToNull(in.pairKey()));
            } else {
                p = SermonParagraph.builder()
                        .sermon(sermon)
                        .orderIdx(order)
                        .sectionIdx(in.sectionIdx())
                        .sectionTitleKr(blankToNull(in.sectionTitleKr()))
                        .sectionTitleEn(blankToNull(in.sectionTitleEn()))
                        .kind(in.kind())
                        .language(in.language())
                        .text(in.text().trim())
                        .scriptureRef(blankToNull(in.scriptureRef()))
                        .pairKey(blankToNull(in.pairKey()))
                        .build();
            }
            rebuilt.add(p);
            order++;
        }

        // Delete anything left over.
        if (!existing.isEmpty()) {
            paragraphRepository.deleteAllInBatch(existing.values());
        }
        paragraphRepository.saveAll(rebuilt);

        // Recompute single-language flag based on the new content.
        sermon.setTranscriptSingleLang(isSingleLanguage(rebuilt));

        log.info("Transcript paragraphs replaced: sermonId={} total={} deleted={} byUser={}",
                sermonId, rebuilt.size(), existing.size(), caller == null ? null : caller.id());

        return rebuilt.stream()
                .map(p -> new ParagraphDto(
                        p.getId(),
                        p.getOrderIdx() == null ? 0 : p.getOrderIdx(),
                        p.getSectionIdx(),
                        p.getSectionTitleKr(),
                        p.getSectionTitleEn(),
                        p.getKind(),
                        p.getLanguage(),
                        p.getText(),
                        p.getScriptureRef(),
                        p.getPairKey()))
                .toList();
    }

    private static final java.util.Set<String> VALID_KINDS =
            java.util.Set.of("paragraph", "scripture", "section_heading", "preacher_meta");
    private static final java.util.Set<String> VALID_LANGUAGES = java.util.Set.of("kr", "en");

    private static boolean isValidKind(String k) { return k != null && VALID_KINDS.contains(k); }
    private static boolean isValidLanguage(String l) { return l != null && VALID_LANGUAGES.contains(l); }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private void assertAdminOrPastor(AuthenticatedUser caller) {
        if (caller == null || (caller.role() != Role.ADMIN && caller.role() != Role.PASTOR)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    @Transactional(readOnly = true)
    public SermonTranscriptResponse loadTranscript(Long sermonId) {
        Sermon sermon = sermonRepository.findById(sermonId).orElse(null);
        if (sermon == null) return null;
        List<SermonParagraph> rows = paragraphRepository
                .findBySermon_IdOrderByOrderIdxAsc(sermonId);
        if (rows.isEmpty()) return null;
        return buildResponse(sermon, rows);
    }

    // ------------------------------------------------------------------

    private SermonTranscriptResponse buildResponse(Sermon sermon, List<SermonParagraph> rows) {
        // Group rows into sections in source order.
        List<SermonTranscriptResponse.Section> sections = new ArrayList<>();
        SectionBuilder current = null;
        // Pair-collapse: when two adjacent paragraphs share a pairKey, the second
        // one is folded into the block emitted for the first. This works because
        // the parser only ever sets pair_key on (EN-then-KR) adjacent pairs.
        SermonTranscriptResponse.Block pendingBlock = null;
        Map<String, Integer> pairToBlockIdx = new HashMap<>();

        for (SermonParagraph p : rows) {
            int sectionIdx = p.getSectionIdx() == null ? 0 : p.getSectionIdx();
            if (current == null || current.idx != sectionIdx) {
                if (current != null) sections.add(current.toSection());
                current = new SectionBuilder(sectionIdx, p.getSectionTitleKr(), p.getSectionTitleEn());
            }
            // Section heading rows are emitted twice (kr + en) so the section
            // titles are denormalized onto every paragraph row anyway. Skip
            // emitting the heading as a block — the section header carries it.
            if ("section_heading".equals(p.getKind())) continue;

            if (p.getPairKey() != null && pairToBlockIdx.containsKey(p.getPairKey())) {
                // Fold this paragraph into the existing block.
                int blockIdx = pairToBlockIdx.get(p.getPairKey());
                SermonTranscriptResponse.Block existing = current.blocks.get(blockIdx);
                current.blocks.set(blockIdx, mergeIntoBlock(existing, p));
                continue;
            }

            SermonTranscriptResponse.Block b = blockFromParagraph(p);
            current.blocks.add(b);
            if (p.getPairKey() != null) {
                pairToBlockIdx.put(p.getPairKey(), current.blocks.size() - 1);
            }
        }
        if (current != null) sections.add(current.toSection());

        boolean singleLang = sermon.getTranscriptSingleLang() != null && sermon.getTranscriptSingleLang();
        return new SermonTranscriptResponse(singleLang, sermon.getTranscriptPdfUrl(), sections);
    }

    private SermonTranscriptResponse.Block blockFromParagraph(SermonParagraph p) {
        boolean isKr = "kr".equals(p.getLanguage());
        boolean isScripture = "scripture".equals(p.getKind());
        return new SermonTranscriptResponse.Block(
                p.getKind(),
                isKr ? p.getText() : null,
                isKr ? null : p.getText(),
                isScripture && isKr ? p.getScriptureRef() : null,
                isScripture && !isKr ? p.getScriptureRef() : null);
    }

    private SermonTranscriptResponse.Block mergeIntoBlock(SermonTranscriptResponse.Block base, SermonParagraph p) {
        boolean isKr = "kr".equals(p.getLanguage());
        boolean isScripture = "scripture".equals(p.getKind());
        return new SermonTranscriptResponse.Block(
                base.kind(),
                isKr ? p.getText() : base.textKr(),
                isKr ? base.textEn() : p.getText(),
                isScripture && isKr ? p.getScriptureRef() : base.refKr(),
                isScripture && !isKr ? p.getScriptureRef() : base.refEn());
    }

    private boolean isSingleLanguage(List<SermonParagraph> rows) {
        long kr = rows.stream().filter(p -> "kr".equals(p.getLanguage())).count();
        long en = rows.stream().filter(p -> "en".equals(p.getLanguage())).count();
        long total = kr + en;
        if (total == 0) return false;
        // Threshold: if one language carries >70% of paragraphs, treat as single-language
        // and the reader hides the toggle.
        return (kr * 1.0 / total) > 0.7 || (en * 1.0 / total) > 0.7;
    }

    private static class SectionBuilder {
        final int idx;
        final String titleKr;
        final String titleEn;
        final List<SermonTranscriptResponse.Block> blocks = new ArrayList<>();

        SectionBuilder(int idx, String titleKr, String titleEn) {
            this.idx = idx;
            this.titleKr = titleKr;
            this.titleEn = titleEn;
        }

        SermonTranscriptResponse.Section toSection() {
            return new SermonTranscriptResponse.Section(idx, titleKr, titleEn, blocks);
        }
    }
}
