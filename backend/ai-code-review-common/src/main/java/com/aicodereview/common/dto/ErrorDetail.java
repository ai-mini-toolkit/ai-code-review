package com.aicodereview.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error detail for API responses
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorDetail {
    private String code;
    private String message;
    private Object details;
}
