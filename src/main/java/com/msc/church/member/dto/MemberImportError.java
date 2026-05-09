package com.msc.church.member.dto;

public record MemberImportError(int row, String field, String message) {
}
