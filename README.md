# EDC EDPS Extension

build the connector:

```bash
./gradlew edc-edps-extension:connector:build
```

run the connector:

```bash
java -Dedc.keystore=app-edc-edps-demo/resources/certs/cert.pfx -Dedc.keystore.password=123456 -Dedc.fs.config=-Dedc.fs.config=edc-edps-extension/connector/src/main/resources/application.properties -jar app-edc-edps-demo/connector/build/libs/connector.jar
```

# edp calls

create EDPS Job
```bash 
curl -X POST http://localhost:19191/api/edps
```


demo.csv file at
```
http://localhost:8080/data.csv
```

post demo data at
```bash
curl -X POST -F "file=@yourfile.zip" http://localhost:8080/upload
```


------


## EDP Workflow


### 1. Start the EDC server

Build the connector:

```bash
./gradlew edc-edps-extension:connector:build
```

Run the connector:

```bash
java -Dedc.keystore=app-edc-edps-demo/resources/certs/cert.pfx -Dedc.keystore.password=123456 -Dedc.fs.config=-Dedc.fs.config=edc-edps-extension/connector/src/main/resources/application.properties -jar app-edc-edps-demo/connector/build/libs/connector.jar
```

Alternatively, start the `io/nexyo/edp/extensions/Runner.java` in the IDE.


### 2. Start HTTP server to providing demo data.csv

start local demo http server
```bash
python ./util/http-demo-server/server.py
```


### 1. Create Asset

```bash
curl -d @edc-edps-extension/connector/src/main/resources/requests/create-asset.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/assets \
  -s | jq 
```

[Optional] Check if asset is created:

```bash 
curl  http://localhost:19193/management/v3/assets/assetId1
```


### 2. Create EDPS Job

```bash 
curl -X POST http://localhost:19191/api/edps/assetId/jobs
```



## MSC

### Test create data upload with python mock server


```
curl -X POST http://localhost:8080/upload \
     -H "Content-Type: application/octet-stream" \
     -H "X-File-Name: util/http-demo-server/data/data.csv" \
     --data-binary @util/http-demo-server/data/data.csv
```