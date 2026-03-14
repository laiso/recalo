# Security Guide

## Implemented Security Measures

### 1. API Key Management

**Location**: `data/api/SecurityUtils.kt`, `data/api/SessionManager.kt`

**Measures**:
- Android Keystore system
- AES-256-GCM encryption
- Stored in EncryptedSharedPreferences
- Keys are kept in the device-specific keystore

**Architecture**:
```
User input (API key)
    |
SecurityUtils.encrypt() <- Android Keystore (AES-256)
    |
EncryptedSharedPreferences
```

### 2. Network Security

**Location**: `res/xml/network_security_config.xml`

**Measures**:
- HTTPS only (cleartextTrafficPermitted="false")
- System certificates only
- OpenAI API domain explicitly specified

**Configuration**:
```xml
<base-config cleartextTrafficPermitted="false">
    <trust-anchors>
        <certificates src="system" />
    </trust-anchors>
</base-config>
```

### 3. Permission Management

**Location**: `AndroidManifest.xml`

**Principle of least privilege**:
```xml
<!-- Camera permission (not required) -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />

<!-- Health Connect permissions (nutrition data only) -->
<uses-permission android:name="android.permission.health.WRITE_NUTRITION" />
<uses-permission android:name="android.permission.health.READ_NUTRITION" />
```

**Permission rationale Activity**:
- `HealthConnectRationaleActivity.kt` explains the purpose of permission usage

### 4. File Storage

**Location**: `res/xml/file_paths.xml`

**Measures**:
- FileProvider usage (file URIs prohibited)
- Cache directory only
- No external storage access

```xml
<cache-path name="camera_images" path="images/"/>
```

### 5. Backup Control

**Location**: `res/xml/backup_rules.xml`

**Measures**:
- Encrypted preference files excluded from backup
- Local database is eligible for backup

```xml
<exclude domain="sharedpref" path="caroli_prefs_encrypted.xml"/>
<exclude domain="sharedpref" path="caroli_prefs.xml"/>
```

### 6. Database Security

**Location**: `data/local/CaroliDatabase.kt`

**Measures**:
- Local storage (Room)
- Stored within the app sandbox
- Rooted device protection relies on Android defaults

---

## Security Checklist

### Pre-Release

- [ ] No hardcoded API keys
- [ ] No sensitive information in logs
- [ ] HTTPS-only communication
- [ ] Only necessary permissions requested
- [ ] Permission usage explained to users
- [ ] Sensitive data excluded from backups

### Periodic Review

- [ ] Dependency vulnerability checks
- [ ] Security updates applied
- [ ] Response to emerging threats

---

## Threat Model

### Expected Threats

| Threat | Impact | Mitigation |
|--------|--------|------------|
| **Device loss** | API key leak | Protected by Android Keystore |
| **Man-in-the-middle** | Traffic interception | HTTPS enforced |
| **Malware** | Data theft | App sandbox |
| **Rooted device** | Decryption | Relies on standard encryption |

### Out-of-Scope Threats

- **Server-side attacks**: OpenAI API issues
- **Social engineering**: User sharing their API key

---

## Incident Response

### API Key Leak

1. **Immediate action**:
   - Instruct user to regenerate API key
   - Clear key in-app (`SessionManager.clear()`)

2. **Prevention**:
   - Investigate the leak
   - Implement additional measures

### Security Vulnerability Discovered

1. **Report**: GitHub Issues or security contact
2. **Response**: Release a fix promptly

---

## Compliance

### Regulations

- GDPR (personal data protection)
- Android best practices
- OWASP Mobile Top 10

### Data Protection

- **Stored data**: Nutrition records, photos
- **Sensitive data**: OpenAI API key (encrypted)
- **Third-party sharing**: OpenAI API only (image data)

---

## References

- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [OWASP Mobile Top 10](https://owasp.org/www-project-mobile-top-10/)
- [Android Keystore System](https://developer.android.com/training/articles/keystore)
- [EncryptedSharedPreferences](https://developer.android.com/topic/security/data)
