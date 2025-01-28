# EDC - EDPS Extension



The Api reference for the Management Api can be found here: 
https://github.com/eclipse-edc/Connector/blob/gh-pages/openapi/management-api/3.0.6/management-api.yaml

The Api reference for the EDPS Api can be found here: 
  


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
python ./util/http-file-server/server.py
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
curl -X POST http://localhost:19191/api/edps/assetId1/jobs
```


### 3. Get EDPS Result

```bash
curl -X POST http://localhost:19191/api/edps/assetId1/jobs/{jobId}/result \
  -H 'content-type: application/json' \
  -d @edc-edps-extension/connector/src/main/resources/requests/fetch-edps-result.json 
````


### 4. Create Result Asset

```bash
curl -d @edc-edps-extension/connector/src/main/resources/requests/create-result-asset.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/assets \
  -s | jq 
```


### 5. Publish Result to Daseen

```bash
curl -H 'content-type: application/json' http://localhost:19193/api/daseen/resultAssetId1/publish \
  -s | jq 
```