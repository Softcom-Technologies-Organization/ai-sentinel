# PII Encryption - Architecture and Usage

## Overview

The system uses **AES-256-GCM** with **HKDF** derivation to encrypt PII (Personally Identifiable Information) data before database storage.

## Architecture

### Layers

```
┌─────────────────────────────────────────┐
│      ScanResultEncryptor (Application)  │
│  Orchestrator: encrypts/decrypts PII    │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│    EncryptionService (Domain - Port)    │
│     Business encryption interface       │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│    AesGcmEncryptionAdapter (Infra)      │
│     AES-GCM + HKDF implementation       │
└─────────────────────────────────────────┘
```

### Encryption Flow

```
1. Salt Generation (256 random bits)
2. IV Generation (96 random bits)
3. DEK Derivation via HKDF(KEK, salt, "pii-dek")
4. AAD Construction (type|start|end)
5. AES-GCM Encryption(DEK, IV, AAD, plaintext)
6. DEK Memory Cleanup
7. Token: ENC:v1:<salt>:<iv>:<ciphertext_with_tag>
```

### Security

| Aspect                | Mechanism        | Details                          |
|-----------------------|------------------|----------------------------------|
| **Confidentiality**   | AES-256          | Symmetric encryption             |
| **Integrity**         | 128-bit GCM Tag  | Built-in authentication          |
| **Authenticity**      | AAD              | Cryptographically bound metadata |
| **Non-replayability** | Unique Salt + IV | Each encryption is unique        |
| **Isolation**         | HKDF             | Unique DEK per value             |

## Token Format

### Structure

```
ENC:v1:<salt_base64>:<iv_base64>:<ciphertext_with_tag_base64>
```

### Sizes

- **Prefix**: `ENC:v1:` (7 characters)
- **Salt**: 32 bytes (256 bits) → ~44 base64 characters
- **IV**: 12 bytes (96 bits) → 16 base64 characters
- **Ciphertext**: variable + 16 bytes (GCM tag) → depends on plaintext

### Example

```
Plaintext: "john.doe@example.com" (20 chars)
Encrypted: ENC:v1:dGVzdHNhbHQxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMw==:YWJjZGVmZ2hpamts:Q2lwaGVydGV4dFdpdGhHY21UYWdFeGFtcGxl
           ^^^^^^ ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
           Prefix Salt (32 bytes)                             IV (12 bytes)    Ciphertext + Tag (36 bytes)
```

## Key Management

### KEK (Key Encryption Key)

#### Characteristics
- **Size**: 256 bits (32 bytes)
- **Format**: Base64
- **Storage**: Environment variable
- **Name**: Configurable (default: `ENCRYPTION_KEK`)

#### Initial Generation

Use the provided PowerShell script:

```powershell
# See pii-reporting-api/create-new-encryption-key.ps1
$key = New-Object byte[] 32
[Security.Cryptography.RNGCryptoServiceProvider]::new().GetBytes($key)
$keyBase64 = [Convert]::ToBase64String($key)
Write-Host "Generated key (store securely):"
Write-Host $keyBase64
```

Or with OpenSSL:

```bash
openssl rand -base64 32
```

#### Configuration

**application.yml**:
```yaml
encryption:
  kek-env-variable: ENCRYPTION_KEK
```

**Environment variables**:
```bash
export ENCRYPTION_KEK="your_base64_key_here"
```

### DEK (Data Encryption Key)

- **Derivation**: HKDF-SHA256 from KEK
- **Size**: 256 bits (32 bytes)
- **Lifetime**: Single encryption operation
- **Cleanup**: Immediate after use with `Arrays.fill()`

### Key Rotation

**Generate new KEK**
   ```powershell
   .\create-new-encryption-key.ps1
   ```

## Usage

### Encrypting PII

```java
@Autowired
private ScanResultEncryptor encryptor;

// Create a ScanResult with plaintext PII
ScanResult result = ScanResult.builder()
    .scanId("scan-123")
    .entities(List.of(
        PiiEntity.builder()
            .type("EMAIL")
            .text("john.doe@example.com")  // Plaintext
            .start(0)
            .end(20)
            .build()
    ))
    .build();

// Encrypt
ScanResult encrypted = encryptor.encrypt(result);
// result.personallyIdentifiableInformationList[0].sensitiveValue now contains "ENC:v1:..."

// Persist to DB
repository.save(encrypted);
```

### Decrypting PII

```java
// Retrieve from DB
ScanResult encrypted = repository.findById("scan-123");

// Decrypt
ScanResult decrypted = encryptor.decrypt(encrypted);
// decrypted.personallyIdentifiableInformationList[0].sensitiveValue contains "john.doe@example.com"

// Use decrypted data
return decrypted;
```

### Partial Handling

The system supports partial decryption:

```java
// If some personallyIdentifiableInformationList are encrypted and others are not
ScanResult mixed = ...; // Some .sensitiveValue with "ENC:v1:", others plaintext

ScanResult decrypted = encryptor.decrypt(mixed);
// Only encrypted values are decrypted
// Plaintext values remain unchanged
```

## Tampering Detection

GCM automatically detects any modification:

| Modification Type       | Detection                                | Exception             |
|-------------------------|------------------------------------------|-----------------------|
| Ciphertext modification | Invalid GCM tag                          | `EncryptionException` |
| IV modification         | Decryption fails                         | `EncryptionException` |
| Salt modification       | Different DEK → invalid tag              | `EncryptionException` |
| AAD modification        | Invalid GCM tag                          | `EncryptionException` |
| Token substitution      | Invalid tag                              | `EncryptionException` |
| Replay attack           | Unique salt → detected at business level | -                     |

### Log Examples

```
2025-01-22 10:30:15 ERROR AesGcmEncryptionAdapter - Decryption failed: AEADBadTagException
```

## Migration from Old Format

If migrating from a format with HMAC (4 parts):

```java
public String migrateToken(String oldToken, EncryptionMetadata metadata) {
    // Old format: ENC:v1:<salt>:<iv>:<ct>:<mac>
    if (oldToken.startsWith("ENC:v1:") && oldToken.split(":").length == 5) {
        // Decrypt with old adapter
        String plaintext = oldAdapter.decrypt(oldToken, metadata);
        
        // Re-encrypt with new format (3 parts)
        return newAdapter.encrypt(plaintext, metadata);
    }
    return oldToken;
}
```

## Monitoring

### Metrics to Monitor

1. **Decryption failure rate**
   - Indicator: Tampering or wrong key
   - Alert threshold: > 1%

2. **Crypto operation latency**
   - P50: < 1ms
   - P99: < 5ms

3. **Memory usage**
   - Verify DEKs are properly cleaned

### Security Logs

```java
log.error("Encryption failed: {}", e.getClass().getSimpleName());
log.error("Decryption failed: {}", e.getClass().getSimpleName());
```

⚠️ **Important**: Never log sensitive data (plaintext, ciphertext, keys)

## Operational Security

### Best Practices

✅ **DO**:
- Store KEK in a secrets manager (AWS Secrets Manager, Azure Key Vault)
- Implement automatic key rotation
- Audit all KEK access
- Use HTTPS for all communications
- Monitor decryption failures

❌ **DON'T**:
- Never commit KEK to Git
- Never log keys or sensitive data
- Don't share KEK between environments
- Don't use the same KEK in dev/staging/prod
- Don't disable AAD verification

## Support

For any questions about encryption implementation:

1. Consult the tests: `AesGcmEncryptionAdapterTest.java`
2. Read the code: `AesGcmEncryptionAdapter.java`

## References

- [NIST SP 800-38D - GCM](https://csrc.nist.gov/publications/detail/sp/800-38d/final)
- [RFC 5869 - HKDF](https://datatracker.ietf.org/doc/html/rfc5869)
- [OWASP Cryptographic Storage](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)
- [Google Tink](https://github.com/google/tink) (future alternative)
