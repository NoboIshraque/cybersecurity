# Lab TLS material

Generate the self-signed keystore/truststore for the Console–Sensor TLS link **here**.
Keystore files (`*.jks`, `*.p12`) are gitignored — never commit them.

```bash
keytool -genkeypair -alias mirage -keyalg RSA -keysize 2048 \
        -keystore lab-keystore.jks -validity 365
```
