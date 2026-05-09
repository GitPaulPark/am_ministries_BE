package com.msc.church.member.dto;

import java.util.List;

public record MemberImportResult(
        int imported,
        int failed,
        List<MemberImportError> errors
) {
}
