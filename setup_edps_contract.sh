#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting EDPS contract setup...${NC}\n"

# Setup Service Provider side
echo -e "${GREEN}1. Creating EDPS Asset...${NC}"
curl -d @resources/requests/create-edps-asset.json \
  -H 'content-type: application/json' http://localhost:29193/management/v3/assets \
  -s | jq

echo -e "\n${GREEN}2. Creating Policy...${NC}"
curl -d @resources/requests/create-policy.json \
  -H 'content-type: application/json' http://localhost:29193/management/v3/policydefinitions \
  -s | jq

echo -e "\n${GREEN}3. Creating Contract Definition...${NC}"
curl -d @resources/requests/create-contract-definition.json \
  -H 'content-type: application/json' http://localhost:29193/management/v3/contractdefinitions \
  -s | jq

# Establish EDPS contract
echo -e "\n${GREEN}4. Fetching Catalog...${NC}"
CATALOG_RESPONSE=$(curl -d @resources/requests/fetch-service-provider-catalog.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/catalog/request \
  -s)

echo "$CATALOG_RESPONSE" | jq

# Extract contract offer ID
CONTRACT_OFFER_ID=$(echo "$CATALOG_RESPONSE" | jq -r '."dcat:dataset"."odrl:hasPolicy"."@id"')
echo -e "\n${YELLOW}Found contract offer ID: $CONTRACT_OFFER_ID${NC}"

# Update negotiate-edps-contract.json with the contract offer ID
echo -e "\n${GREEN}5. Updating contract negotiation request and initiating negotiation...${NC}"
sed "s/{{contract-offer-id}}/$CONTRACT_OFFER_ID/" resources/requests/negotiate-edps-contract.json > temp_negotiate.json

NEGOTIATION_RESPONSE=$(curl -d @temp_negotiate.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/contractnegotiations \
  -s)

echo "$NEGOTIATION_RESPONSE" | jq

# Get contract negotiation ID
NEGOTIATION_ID=$(echo "$NEGOTIATION_RESPONSE" | jq -r '.id // .["@id"]')

echo -e "\n${YELLOW}Waiting for contract negotiation to complete...${NC}"
sleep 2

# Check contract negotiation status
CONTRACT_ID=$(curl -X POST http://localhost:29193/management/v3/contractnegotiations/request \
  -s | jq -r 'sort_by(.createdAt) | last | .contractAgreementId')

echo -e "${YELLOW}Contract Agreement ID: $CONTRACT_ID${NC}"

# Update start-transfer.json with the contract ID
echo -e "\n${GREEN}6. Initiating transfer process...${NC}"
sed "s/{{contract-id}}/$CONTRACT_ID/" resources/requests/start-transfer.json > temp_transfer.json

TRANSFER_RESPONSE=$(curl -d @temp_transfer.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/transferprocesses \
  -s)

echo "$TRANSFER_RESPONSE" | jq

# Cleanup temporary files
rm temp_negotiate.json temp_transfer.json

echo -e "\n${GREEN}Setup complete!${NC}" 