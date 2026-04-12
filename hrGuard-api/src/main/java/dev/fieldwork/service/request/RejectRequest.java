package dev.fieldwork.service.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
        @NotBlank(message = "반려 사유는 필수입니다.")
        @Size(max = 500)
        String reason
) {
}
