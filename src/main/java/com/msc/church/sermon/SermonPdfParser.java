package com.msc.church.sermon;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a bilingual sermon transcript PDF into a list of paragraph rows.
 *
 * <p>Expected input shape (per the AM/SERMON_PDF_DESIGN.md analysis of the
 * 2026.05.10 sermon): each paragraph in English is immediately followed by its
 * Korean translation. Section headings appear inline as "1. Title 한국 제목".
 * Bible verses cite English first ("John 14:15 ..."), then Korean
 * ("요한복음 14:15 ...").
 *
 * <p>The parser is permissive — when the alternation isn't clean (single-language
 * PDF, scanned image, etc.) it still emits whatever paragraphs it can, and the
 * service marks the sermon as {@code transcript_single_lang} when appropriate
 * so the reader hides the language toggle.
 */
@Slf4j
@Component
public class SermonPdfParser {

    /** "1. Do I Love Jesus? 나는 예수님을 사랑하는가?" — English headline + Korean headline.
     *  Both groups capped at 120 chars so a paragraph that bled into the heading
     *  (PDFs sometimes drop the blank-line separator between heading and the next
     *  verse) doesn't get swallowed as a multi-thousand-char title. */
    private static final Pattern SECTION_HEADING = Pattern.compile(
            "^\\s*(\\d+)\\.\\s+([^가-힣]{1,120}?)\\s+([\\p{IsHangul}][^\\n]{0,119})\\s*$");

    /** A line that starts a new section. Used by {@link #splitIntoParagraphs} to break
     *  a paragraph even when no blank line separator preceded it. */
    private static final Pattern SECTION_HEADING_START = Pattern.compile("^\\s*\\d+\\.\\s+\\S");

    /** "John 14:15" / "Romans 8:23" / "1 Corinthians 13:3" — English Bible reference. */
    private static final Pattern EN_VERSE = Pattern.compile(
            "^\\s*((?:[1-3]\\s+)?[A-Z][a-zA-Z]+)\\s+(\\d+):(\\d+(?:[-–]\\d+)?)\\s+(.+)$",
            Pattern.DOTALL);

    /** "요한복음 14:15" — Korean Bible reference. */
    private static final Pattern KR_VERSE = Pattern.compile(
            "^\\s*([\\p{IsHangul}]+(?:서|기|음|상|하|상하)?)\\s+(\\d+)[:;](\\d+(?:[-–~]\\d+)?)\\s+(.+)$",
            Pattern.DOTALL);

    /** "1 / 8" page footer the PDF uses. Strip on read. */
    private static final Pattern PAGE_NUM = Pattern.compile("^\\s*\\d+\\s*/\\s*\\d+\\s*$");

    public List<SermonParagraph> parse(Path pdfPath, Sermon sermon) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            return parseText(text, sermon);
        }
    }

    /**
     * Visible for tests. Splits the raw extracted text into paragraphs and
     * classifies each one.
     */
    List<SermonParagraph> parseText(String text, Sermon sermon) {
        // Normalize: strip page footers, collapse multiple blank lines into a single
        // paragraph separator, join wrapped lines (PDF wraps mid-paragraph at column width).
        List<String> rawParas = splitIntoParagraphs(text);

        List<SermonParagraph> out = new ArrayList<>();
        int order = 0;
        int sectionIdx = 0;
        String sectionKr = null;
        String sectionEn = null;

        // Track the previous EN paragraph so we can stamp pair_key when its KR
        // translation arrives next.
        SermonParagraph pendingEn = null;

        for (String para : rawParas) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            // Section heading: bumps section state, emits a section_heading row in BOTH languages.
            Matcher mh = SECTION_HEADING.matcher(trimmed);
            if (mh.find()) {
                sectionIdx++;
                sectionEn = mh.group(2).trim();
                sectionKr = mh.group(3).trim();
                pendingEn = null;
                out.add(SermonParagraph.builder()
                        .sermon(sermon).orderIdx(order++)
                        .sectionIdx(sectionIdx).sectionTitleKr(sectionKr).sectionTitleEn(sectionEn)
                        .kind("section_heading").language("en")
                        .text(mh.group(1) + ". " + sectionEn)
                        .build());
                out.add(SermonParagraph.builder()
                        .sermon(sermon).orderIdx(order++)
                        .sectionIdx(sectionIdx).sectionTitleKr(sectionKr).sectionTitleEn(sectionEn)
                        .kind("section_heading").language("kr")
                        .text(mh.group(1) + ". " + sectionKr)
                        .build());
                continue;
            }

            // Scripture: starts with a recognizable reference.
            Matcher mEn = EN_VERSE.matcher(trimmed);
            Matcher mKr = KR_VERSE.matcher(trimmed);
            if (mEn.matches() && looksLikeBibleBook(mEn.group(1))) {
                String ref = mEn.group(1) + " " + mEn.group(2) + ":" + mEn.group(3);
                pendingEn = SermonParagraph.builder()
                        .sermon(sermon).orderIdx(order++)
                        .sectionIdx(sectionIdx).sectionTitleKr(sectionKr).sectionTitleEn(sectionEn)
                        .kind("scripture").language("en").scriptureRef(ref)
                        .text(mEn.group(4).trim())
                        .build();
                out.add(pendingEn);
                continue;
            }
            if (mKr.matches() && looksLikeKoreanBibleBook(mKr.group(1))) {
                String ref = mKr.group(1) + " " + mKr.group(2) + ":" + mKr.group(3);
                SermonParagraph kr = SermonParagraph.builder()
                        .sermon(sermon).orderIdx(order++)
                        .sectionIdx(sectionIdx).sectionTitleKr(sectionKr).sectionTitleEn(sectionEn)
                        .kind("scripture").language("kr").scriptureRef(ref)
                        .text(mKr.group(4).trim())
                        .build();
                pairUp(pendingEn, kr);
                out.add(kr);
                pendingEn = null;
                continue;
            }

            // Plain paragraph — language detected by Hangul-vs-Latin character ratio.
            String lang = detectLanguage(trimmed);
            SermonParagraph p = SermonParagraph.builder()
                    .sermon(sermon).orderIdx(order++)
                    .sectionIdx(sectionIdx).sectionTitleKr(sectionKr).sectionTitleEn(sectionEn)
                    .kind("paragraph").language(lang).text(trimmed)
                    .build();
            if ("en".equals(lang)) {
                pendingEn = p;
            } else if ("kr".equals(lang)) {
                pairUp(pendingEn, p);
                pendingEn = null;
            }
            out.add(p);
        }

        log.info("Parsed sermon PDF: paragraphs={} sections={} firstLanguageMix={}",
                out.size(), sectionIdx, languageMix(out));
        return out;
    }

    // ------------------------------------------------------------------

    private List<String> splitIntoParagraphs(String text) {
        // PDFBox's PDFTextStripper inserts a hard '\n' at every visual line wrap. A
        // paragraph break is normally a blank line. Three extra rules:
        //   1) skip the "1 / 8" page-number footer.
        //   2) a line that starts a numbered section heading (^\d+\.\s+\S) is its own
        //      paragraph — even when no blank line precedes it. PDFs sometimes drop
        //      the heading↔next-verse separator.
        //   3) when an English paragraph is immediately followed by its Korean
        //      translation (no blank line between, the 2026.05.10 source PDF does
        //      this), the splitter would otherwise merge them into one chunk and
        //      majority-classify by language. Detect a line whose dominant script
        //      differs from the buffer's current script and break there.
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        String bufLang = null; // 'en' / 'kr' / null when buf is empty
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (PAGE_NUM.matcher(trimmed).matches()) continue;
            if (trimmed.isEmpty()) {
                flush(buf, out);
                bufLang = null;
                continue;
            }
            if (SECTION_HEADING_START.matcher(trimmed).find()) {
                flush(buf, out);
                bufLang = null;
                out.add(trimmed);
                continue;
            }
            String lineLang = detectLanguage(trimmed);
            if (bufLang != null && !bufLang.equals(lineLang)) {
                flush(buf, out);
                bufLang = lineLang;
            } else if (bufLang == null) {
                bufLang = lineLang;
            }
            if (buf.length() > 0) buf.append(' ');
            buf.append(trimmed);
        }
        flush(buf, out);
        return out;
    }

    private void flush(StringBuilder buf, List<String> out) {
        String s = buf.toString().trim();
        if (!s.isEmpty()) out.add(s);
        buf.setLength(0);
    }

    /** Hangul-to-Latin character ratio decides per-paragraph language. */
    String detectLanguage(String text) {
        int hangul = 0, latin = 0;
        for (int i = 0; i < text.length(); i++) {
            int cp = text.codePointAt(i);
            Character.UnicodeBlock blk = Character.UnicodeBlock.of(cp);
            if (blk == Character.UnicodeBlock.HANGUL_SYLLABLES
                    || blk == Character.UnicodeBlock.HANGUL_JAMO
                    || blk == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) {
                hangul++;
            } else if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')) {
                latin++;
            }
        }
        return hangul > latin ? "kr" : "en";
    }

    private static void pairUp(SermonParagraph a, SermonParagraph b) {
        if (a == null || b == null) return;
        String key = UUID.randomUUID().toString();
        a.setPairKey(key);
        b.setPairKey(key);
    }

    private static String languageMix(List<SermonParagraph> ps) {
        long kr = ps.stream().filter(p -> "kr".equals(p.getLanguage())).count();
        long en = ps.stream().filter(p -> "en".equals(p.getLanguage())).count();
        return "kr=" + kr + " en=" + en;
    }

    /** A loose set of English Bible book starters — covers the common cases in our PDFs. */
    private static boolean looksLikeBibleBook(String token) {
        String t = token.trim();
        return t.matches("^(Genesis|Exodus|Leviticus|Numbers|Deuteronomy|Joshua|Judges|Ruth|Samuel|"
                + "Kings|Chronicles|Ezra|Nehemiah|Esther|Job|Psalm|Psalms|Proverbs|Ecclesiastes|"
                + "Song|Isaiah|Jeremiah|Lamentations|Ezekiel|Daniel|Hosea|Joel|Amos|Obadiah|Jonah|"
                + "Micah|Nahum|Habakkuk|Zephaniah|Haggai|Zechariah|Malachi|Matthew|Mark|Luke|John|"
                + "Acts|Romans|Corinthians|Galatians|Ephesians|Philippians|Colossians|Thessalonians|"
                + "Timothy|Titus|Philemon|Hebrews|James|Peter|Jude|Revelation|"
                + "1\\s*\\w+|2\\s*\\w+|3\\s*\\w+).*");
    }

    private static boolean looksLikeKoreanBibleBook(String token) {
        // Common Korean Bible book endings + a few full-name short books.
        return token.endsWith("복음") || token.endsWith("기") || token.endsWith("서")
                || token.endsWith("음") || token.endsWith("기")
                || token.matches("창세기|출애굽기|레위기|민수기|신명기|여호수아|사사기|룻기|"
                        + "에스라|느헤미야|에스더|시편|잠언|전도서|아가|이사야|예레미야|예레미야애가|"
                        + "에스겔|다니엘|호세아|요엘|아모스|오바댜|요나|미가|나훔|하박국|스바냐|"
                        + "학개|스가랴|말라기|마태복음|마가복음|누가복음|요한복음|사도행전|로마서|"
                        + "고린도전서|고린도후서|갈라디아서|에베소서|빌립보서|골로새서|데살로니가전서|"
                        + "데살로니가후서|디모데전서|디모데후서|디도서|빌레몬서|히브리서|야고보서|"
                        + "베드로전서|베드로후서|요한1서|요한2서|요한3서|유다서|요한계시록");
    }
}
