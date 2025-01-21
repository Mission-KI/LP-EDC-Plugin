# EDC EDPS Extension

build the connector:

```bash
./gradlew app-edc-edps-demo:connector:build`
```

run the connector:

```bash
java -Dedc.keystore=app-edc-edps-demo/resources/certs/cert.pfx -Dedc.keystore.password=123456 -Dedc.fs.config=app-edc-edps-demo/resources/configuration/provider-configuration.properties -jar app-edc-edps-demo/connector/build/libs/connector.jar
```

dummy change