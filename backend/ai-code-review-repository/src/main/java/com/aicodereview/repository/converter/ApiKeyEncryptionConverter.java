package com.aicodereview.repository.converter;

import com.aicodereview.common.util.EncryptionUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter that encrypts/decrypts AI model API keys transparently.
 * Uses AES-256-GCM encryption via EncryptionUtil.
 *
 * <p>NOTE: Uses static field pattern because Hibernate may instantiate converters
 * directly via {@code new}. The {@code @Component} annotation ensures Spring creates
 * a managed instance first, setting the static key via {@code @Value}. The static
 * fallback in {@code getKey()} ensures the converter works even if Spring injection
 * hasn't occurred yet (e.g., during Flyway migration or test context startup).</p>
 */
@Slf4j
@Component
@Converter
public class ApiKeyEncryptionConverter implements AttributeConverter<String, String> {

    private static volatile String encryptionKey;

    private static final String DEFAULT_KEY = "default-dev-key-32chars-warning!";

    @Value("${app.encryption.key:" + DEFAULT_KEY + "}")
    public void setEncryptionKey(String key) {
        if (DEFAULT_KEY.equals(key)) {
            log.warn("Using default encryption key for API keys! Set ENCRYPTION_KEY environment variable for production.");
        }
        ApiKeyEncryptionConverter.encryptionKey = key;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return EncryptionUtil.encrypt(attribute, getKey());
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return EncryptionUtil.decrypt(dbData, getKey());
    }

    private String getKey() {
        return encryptionKey != null ? encryptionKey : DEFAULT_KEY;
    }
}
