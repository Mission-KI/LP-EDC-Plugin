package io.nexyo.edp.extensions.dtos.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GenericResponseDto(
        @JsonProperty("message") String message,
        @JsonProperty("status") Status status) {
}
