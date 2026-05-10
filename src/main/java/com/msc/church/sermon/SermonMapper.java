package com.msc.church.sermon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msc.church.bulletin.Bulletin;
import com.msc.church.member.Member;
import com.msc.church.sermon.dto.SermonBulletinRef;
import com.msc.church.sermon.dto.SermonDetail;
import com.msc.church.sermon.dto.SermonMemberRef;
import com.msc.church.sermon.dto.SermonSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hand-written mapper. The reflection-questions field is JSON in the DB and a
 * {@code List<String>} in the response, so the conversion lives here rather than
 * being annotated into MapStruct.
 */
@Component
@RequiredArgsConstructor
public class SermonMapper {

    private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public SermonSummary toSummary(Sermon s) {
        if (s == null) return null;
        return new SermonSummary(
                s.getId(),
                s.getSermonDate(),
                s.getTitleKr(),
                s.getTitleEn(),
                s.getScriptureRef(),
                memberRef(s.getPreacher()),
                s.getPreacherNameLabel(),
                s.getTheme(),
                s.getAudioUrl() != null && !s.getAudioUrl().isBlank(),
                s.isPublished()
        );
    }

    public SermonDetail toDetail(Sermon s) {
        return toDetail(s, null);
    }

    public SermonDetail toDetail(Sermon s, com.msc.church.sermon.dto.SermonTranscriptResponse transcript) {
        if (s == null) return null;
        return new SermonDetail(
                s.getId(),
                s.getSermonDate(),
                s.getTitleKr(),
                s.getTitleEn(),
                memberRef(s.getPreacher()),
                s.getPreacherNameLabel(),
                s.getScriptureRef(),
                s.getScriptureTextKr(),
                s.getScriptureTextEn(),
                s.getAudioUrl(),
                s.getVideoUrl(),
                s.getTranscriptKr(),
                s.getTranscriptEn(),
                s.getTheme(),
                parseQuestions(s.getCellReflectionQuestionsJson()),
                bulletinRef(s.getLinkedBulletin()),
                s.isPublished(),
                s.getPublishedAt(),
                transcript,
                s.getTranscriptPdfUrl()
        );
    }

    public String serializeQuestions(List<String> questions) {
        if (questions == null || questions.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(questions);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize reflection questions", e);
        }
    }

    public List<String> parseQuestions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, LIST_OF_STRING);
        } catch (JsonProcessingException e) {
            // Bad JSON in DB shouldn't crash the read path; just hand back nothing.
            return List.of();
        }
    }

    private SermonMemberRef memberRef(Member m) {
        if (m == null) return null;
        return new SermonMemberRef(m.getId(), m.getNameKr(), m.getNameEn());
    }

    private SermonBulletinRef bulletinRef(Bulletin b) {
        if (b == null) return null;
        return new SermonBulletinRef(b.getId(), b.getServiceDate());
    }
}
