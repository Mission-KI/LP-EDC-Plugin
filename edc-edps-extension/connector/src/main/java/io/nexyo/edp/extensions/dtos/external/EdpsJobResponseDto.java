package io.nexyo.edp.extensions.dtos.external;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EdpsJobResponseDto(
        @JsonProperty("job_id")
        String jobUuid,

        @JsonProperty("state")
        String state,

        @JsonProperty("state_details")
        String details
) {
}
