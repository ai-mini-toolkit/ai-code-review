package com.aicodereview.repository.converter;

import com.aicodereview.common.util.EncryptionUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter that encrypts/decrypts webhook secrets transparently.
 * Uses AES-256-GCM encryption via EncryptionUtil.
 */
@Slf4j
@Component
@Converter
public class WebhookSecretConverter implements AttributeConverter<String, String> {

    private static String encryptionKey;

    private static final String DEFAULT_KEY = "default-dev-key-32chars-warning!";

    @Value("${app.encryption.key:" + DEFAULT_KEY + "}")
    public void setEncryptionKey(String key) {
        if (DEFAULT_KEY.equals(key)) {
            log.warn("Using default encryption key! Set ENCRYPTION_KEY environment variable for production.");
        }
        WebhookSecretConverter.encryptionKey = key;
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
