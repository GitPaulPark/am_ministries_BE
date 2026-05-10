package com.msc.church.sermon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused unit tests for {@link SermonPdfParser#parseText}. The PDFBox path is
 * exercised end-to-end by an integration test; here we exercise the parser logic
 * in isolation with synthetic text in the same shape as
 * {@code TalkFile_26.05.10 AM Sermon_KO.pdf} produces from
 * {@link org.apache.pdfbox.text.PDFTextStripper}.
 */
class SermonPdfParserTest {

    private final SermonPdfParser parser = new SermonPdfParser();
    private final Sermon sermon = Sermon.builder().build();

    @Test
    void detectsEnglishAndKoreanParagraphsByCharacterRatio() {
        String text = """
                Who is this commandment for? This is a command Jesus has given us.

                이 계명은 누구를 위한 것일까요? 분명히 이것은 예수님께서 우리에게 주신 명령입니다.
                """;

        List<SermonParagraph> out = parser.parseText(text, sermon);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).getLanguage()).isEqualTo("en");
        assertThat(out.get(1).getLanguage()).isEqualTo("kr");
        // Adjacent EN/KR paragraphs share a pair_key.
        assertThat(out.get(0).getPairKey()).isNotNull();
        assertThat(out.get(0).getPairKey()).isEqualTo(out.get(1).getPairKey());
    }

    @Test
    void parsesNumberedBilingualSectionHeadings() {
        String text = """
                1. Do I Love Jesus? 나는 예수님을 사랑하는가?

                Who is this commandment for?

                이 계명은 누구를 위한 것일까요?

                2. The Commands 계명들

                Do you love Jesus?

                여러분은 예수님을 사랑하십니까?
                """;

        List<SermonParagraph> out = parser.parseText(text, sermon);

        // Two heading rows per section (en + kr).
        long headingCount = out.stream().filter(p -> "section_heading".equals(p.getKind())).count();
        assertThat(headingCount).isEqualTo(4);

        // Section index increments and section titles are denormalized onto child paragraphs.
        SermonParagraph s2Para = out.stream()
                .filter(p -> "paragraph".equals(p.getKind()) && p.getSectionIdx() == 2 && "kr".equals(p.getLanguage()))
                .findFirst().orElseThrow();
        assertThat(s2Para.getSectionTitleEn()).isEqualTo("The Commands");
        assertThat(s2Para.getSectionTitleKr()).isEqualTo("계명들");
    }

    @Test
    void parsesBibleVersesAsScriptureBlocksWithReferences() {
        String text = """
                John 14:15 If you love me, keep my commands.

                요한복음 14:15 저희가 나를 사랑하면 나의 계명을 지키리라
                """;

        List<SermonParagraph> out = parser.parseText(text, sermon);

        assertThat(out).hasSize(2);
        SermonParagraph en = out.get(0);
        SermonParagraph kr = out.get(1);

        assertThat(en.getKind()).isEqualTo("scripture");
        assertThat(en.getLanguage()).isEqualTo("en");
        assertThat(en.getScriptureRef()).isEqualTo("John 14:15");
        assertThat(en.getText()).isEqualTo("If you love me, keep my commands.");

        assertThat(kr.getKind()).isEqualTo("scripture");
        assertThat(kr.getLanguage()).isEqualTo("kr");
        assertThat(kr.getScriptureRef()).isEqualTo("요한복음 14:15");
        assertThat(kr.getPairKey()).isEqualTo(en.getPairKey());
    }

    @Test
    void stripsPageNumberFooters() {
        String text = """
                1 / 8

                Who is this commandment for?

                2 / 8

                이 계명은 누구를 위한 것일까요?
                """;

        List<SermonParagraph> out = parser.parseText(text, sermon);

        // Both page-number footers should be silently dropped.
        assertThat(out).hasSize(2);
        assertThat(out).noneMatch(p -> p.getText().contains("/"));
    }

    @Test
    void joinsHardLineBreaksWithinParagraph() {
        // PDFTextStripper hard-wraps mid-paragraph at column width; our parser
        // joins those lines into a single rendered paragraph.
        String text = """
                Who is this commandment for? This is a command
                Jesus has given us. That is clear. But who is it
                for?
                """;

        List<SermonParagraph> out = parser.parseText(text, sermon);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getText()).doesNotContain("\n");
        assertThat(out.get(0).getText()).contains("Who is this commandment for?");
        assertThat(out.get(0).getText()).contains("That is clear.");
    }

    @Test
    void splitsAdjacentEnglishAndKoreanParagraphsEvenWithoutBlankLine() {
        // The 2026.05.10 source PDF doesn't put a blank line between an English
        // paragraph and its Korean translation — PDFBox emits them as adjacent
        // lines. Without a language-aware splitter, both get joined into one
        // chunk and majority-classified as English. Verify they end up split.
        String text = """
                Who is this commandment for? This is a command Jesus has given us.
                That is clear. But who is it for? Did He say this for His own benefit?
                이 계명은 누구를 위한 것일까요? 분명히 이것은 예수님께서 우리에게 주신 명령입니다.
                그 점은 분명합니다. 그렇다면 이 말씀은 누구를 위한 것입니까?

                He created this world.
                """;

        List<SermonParagraph> out = parser.parseText(text, sermon);

        assertThat(out).hasSize(3);
        assertThat(out.get(0).getLanguage()).isEqualTo("en");
        assertThat(out.get(0).getText()).contains("Who is this commandment for?");
        assertThat(out.get(1).getLanguage()).isEqualTo("kr");
        assertThat(out.get(1).getText()).contains("이 계명은 누구를 위한 것일까요?");
        assertThat(out.get(2).getLanguage()).isEqualTo("en");
        // Adjacent EN/KR pair gets a shared pair_key.
        assertThat(out.get(0).getPairKey()).isNotNull();
        assertThat(out.get(0).getPairKey()).isEqualTo(out.get(1).getPairKey());
    }

    @Test
    void languageDetectionFallsBackToEnglishOnEmptyText() {
        // Direct probe of the heuristic — purely-Latin text returns 'en'.
        assertThat(parser.detectLanguage("Hello world")).isEqualTo("en");
        // Purely-Hangul text returns 'kr'.
        assertThat(parser.detectLanguage("안녕하세요")).isEqualTo("kr");
        // Mixed but Hangul-dominant returns 'kr'.
        assertThat(parser.detectLanguage("이것은 매우 긴 한국어 문장 sermon 입니다")).isEqualTo("kr");
        // Edge: empty string defaults to 'en' (kr=0 latin=0, hangul > latin is false).
        assertThat(parser.detectLanguage("")).isEqualTo("en");
    }
}
