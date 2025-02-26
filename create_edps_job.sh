#!/bin/bash

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

CONTRACT_ID="a5a3c863-ed25-42a7-9ab4-fde8074b9e74"

echo -e "${GREEN}Creating source asset for EDPS...${NC}\n"
curl -d @resources/requests/create-asset.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/assets \
  -s | jq


echo -e "${GREEN}Creating EDPS job...${NC}\n"
JOB_RESPONSE=$(curl -d "{\"contractId\": \"$CONTRACT_ID\"}" \
   -H 'content-type: application/json' http://localhost:19191/api/edp/edps/assetId1/jobs \
   -s | jq
)
echo "$JOB_RESPONSE" | jq

# Todo: Check extraction of job ID
JOB_ID=$(echo "$JOB_RESPONSE" | jq -r '.id')

echo -e "${GREEN}Creating result asset for job $JOB_ID...${NC}\n"

exit 0

# todo: add back
#curl -X POST http://localhost:19191/api/edp/edps/assetId1/jobs/$JOB_ID/result \
#  -H 'content-type: application/json' \
#  -d @resources/requests/fetch-edps-result.json
#
#
#echo -e "${GREEN}Publishing to Daseen...${NC}\n"
#curl -X POST http://localhost:19191/api/edp/daseen/resultAssetId1 | jq
#
#
#echo -e "\n${GREEN}Done!${NC}"