package com.aicodereview.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Cryptographic utility methods for secure operations.
 * <p>
 * This utility class provides cryptographic functions with emphasis on security best practices,
 * particularly preventing timing attacks through constant-time operations.
 * </p>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li><b>Constant-Time Comparison:</b> Prevents timing attacks by ensuring comparison
 *       operations take the same time regardless of input values</li>
 *   <li><b>Null Safety:</b> Gracefully handles null inputs without throwing exceptions</li>
 *   <li><b>Security Focused:</b> Implements cryptographic best practices</li>
 * </ul>
 *
 * <h3>Security Note:</h3>
 * This class is critical for webhook signature verification. Never use standard
 * {@code String.equals()} for comparing secrets or signatures, as it's vulnerable
 * to timing attacks where attackers can infer the correct value by measuring
 * comparison execution time.
 *
 * @see java.security.MessageDigest#isEqual(byte[], byte[])
 * @since 2.1.0
 * @author AI Code Review System
 */
public final class CryptoUtils {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private CryptoUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Compares two strings in constant time to prevent timing attacks.
     * <p>
     * This method uses {@link MessageDigest#isEqual(byte[], byte[])} which performs
     * constant-time comparison. Unlike {@code String.equals()}, this method takes
     * the same amount of time whether the strings match or not, preventing attackers
     * from inferring the correct value through timing analysis.
     * </p>
     *
     * <h4>Why Constant-Time Comparison?</h4>
     * <p>
     * Standard string comparison ({@code String.equals()}) returns immediately upon
     * finding the first mismatched character. This means:
     * </p>
     * <ul>
     *   <li>If first character differs: very fast return (~1 comparison)</li>
     *   <li>If first 10 characters match: slower return (~10 comparisons)</li>
     *   <li>If all characters match: slowest return (full string comparison)</li>
     * </ul>
     * <p>
     * Attackers can measure these timing differences to guess the correct value
     * character by character. Constant-time comparison prevents this attack by
     * always performing the same number of operations.
     * </p>
     *
     * <h4>Use Cases:</h4>
     * <ul>
     *   <li>Webhook signature verification (CRITICAL)</li>
     *   <li>API key comparison</li>
     *   <li>Password hash comparison</li>
     *   <li>Any security-sensitive string comparison</li>
     * </ul>
     *
     * <h4>Example Usage:</h4>
     * <pre>{@code
     * // CORRECT - Use for security-sensitive comparisons:
     * String computedSignature = computeHmac(payload, secret);
     * if (CryptoUtils.constantTimeEquals(requestSignature, computedSignature)) {
     *     // Signature valid
     * }
     *
     * // WRONG - Never use for secrets:
     * if (requestSignature.equals(computedSignature)) {
     *     // Vulnerable to timing attacks!
     * }
     * }</pre>
     *
     * <h4>Null Handling:</h4>
     * <ul>
     *   <li>Both null: returns {@code true}</li>
     *   <li>One null: returns {@code false}</li>
     *   <li>Neither null: performs constant-time byte comparison</li>
     * </ul>
     *
     * @param a the first string to compare (can be null)
     * @param b the second string to compare (can be null)
     * @return {@code true} if the strings are equal, {@code false} otherwise
     * @see MessageDigest#isEqual(byte[], byte[])
     */
    public static boolean constantTimeEquals(String a, String b) {
        // Null handling: both null = equal, one null = not equal
        if (a == null || b == null) {
            return a == b;
        }

        // Convert strings to UTF-8 byte arrays
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        // Use MessageDigest.isEqual() which performs constant-time comparison
        // This method is provided by Java specifically for security-sensitive comparisons
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
