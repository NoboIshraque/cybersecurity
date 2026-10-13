# Lab TLS material

Generate the self-signed keystore/truststore for the Console–Sensor TLS link **here**.
Keystore files (`*.jks`, `*.p12`) are gitignored — never commit them.

```bash
keytool -genkeypair -alias mirage -keyalg RSA -keysize 2048 \
        -keystore lab-keystore.jks -validity 365
```

## Enabling TLS at runtime

Generate `lab.p12` here (above), then point the apps at it:

```bash
# Console (needs the keystore with the private key)
java -Dmirage.keystore=certs/lab.p12 -Dmirage.keystore.password=changeit \
     -cp target/classes com.mirage.console.ConsoleMain

# Sensor (needs a truststore; for a shared self-signed lab cert, the keystore works)
java -Dmirage.truststore=certs/lab.p12 -Dmirage.truststore.password=changeit \
     -cp target/classes com.mirage.sensor.SensorMain
```

Without these properties both apps use plain sockets (fine for a single-machine demo).
