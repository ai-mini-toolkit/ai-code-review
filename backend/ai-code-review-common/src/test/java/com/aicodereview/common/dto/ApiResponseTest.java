package com.aicodereview.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ApiResponse
 */
class ApiResponseTest {

    @Test
    void testSuccessWithData() {
        String testData = "test";
        ApiResponse<String> response = ApiResponse.success(testData);

        assertTrue(response.isSuccess());
        assertEquals(testData, response.getData());
        assertNull(response.getError());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testSuccessWithoutData() {
        ApiResponse<Void> response = ApiResponse.success();

        assertTrue(response.isSuccess());
        assertNull(response.getData());
        assertNull(response.getError());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testErrorWithDetails() {
        String errorMessage = "Test error";
        Object details = "error details";
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.BAD_REQUEST, errorMessage, details);

        assertFalse(response.isSuccess());
        assertNull(response.getData());
        assertNotNull(response.getError());
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), response.getError().getCode());
        assertEquals(errorMessage, response.getError().getMessage());
        assertEquals(details, response.getError().getDetails());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testErrorWithoutDetails() {
        String errorMessage = "Test error";
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.NOT_FOUND, errorMessage);

        assertFalse(response.isSuccess());
        assertNull(response.getData());
        assertNotNull(response.getError());
        assertEquals(ErrorCode.NOT_FOUND.getCode(), response.getError().getCode());
        assertEquals(errorMessage, response.getError().getMessage());
        assertNull(response.getError().getDetails());
        assertNotNull(response.getTimestamp());
    }
}
