#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}EDPS ZIP File Upload Test${NC}"
echo "================================"

# Create a test ZIP file if it doesn't exist
TEST_ZIP="test-edps-result.zip"
if [ ! -f "$TEST_ZIP" ]; then
    echo -e "${BLUE}Creating test ZIP file...${NC}"
    echo "Test content" > test-file.txt
    zip "$TEST_ZIP" test-file.txt
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Created test ZIP file: $TEST_ZIP${NC}"
    else
        echo -e "${RED}Failed to create test ZIP file${NC}"
        exit 1
    fi
fi

# Create a multi-file ZIP to better simulate real-world conditions
MULTI_FILE_ZIP="multi-file-test.zip"
if [ ! -f "$MULTI_FILE_ZIP" ]; then
    echo -e "${BLUE}Creating multi-file test ZIP...${NC}"
    mkdir -p temp_dir
    # Create multiple files with different content
    for i in {1..10}; do
        echo "Content for file $i with some random data: $RANDOM $RANDOM" > "temp_dir/file_$i.txt"
    done
    # Create a nested directory structure
    mkdir -p temp_dir/subdir1/subdir2
    echo "Nested file content" > temp_dir/subdir1/subdir2/nested.txt
    
    # Create the ZIP file
    zip -r "$MULTI_FILE_ZIP" temp_dir
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Created multi-file ZIP: $MULTI_FILE_ZIP${NC}"
        # Clean up the temporary directory
        rm -rf temp_dir
    else
        echo -e "${RED}Failed to create multi-file ZIP${NC}"
        exit 1
    fi
fi

# Get file sizes
FILE_SIZE=$(stat -f %z "$TEST_ZIP")
MULTI_FILE_SIZE=$(stat -f %z "$MULTI_FILE_ZIP")
echo -e "Simple file size: ${GREEN}$FILE_SIZE bytes${NC}"
echo -e "Multi-file size: ${GREEN}$MULTI_FILE_SIZE bytes${NC}"

# Test 1: Upload with proper Content-Length header
echo -e "\n${BLUE}Test 1: Upload with proper Content-Length header${NC}"
echo "Sending PUT request to http://localhost:8081/connector/edp/test-connector/edp-result.zip"

curl -i -X 'PUT' \
  'http://localhost:8081/connector/edp/test-connector/edp-result.zip' \
  -H "Content-Length: $FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  -H 'accept: application/json' \
  --data-binary "@$TEST_ZIP"

echo -e "\n\n${BLUE}Test 2: Upload with chunked transfer encoding${NC}"
echo "Sending PUT request with chunked encoding"

curl -i -X 'PUT' \
  'http://localhost:8081/connector/edp/test-connector/edp-result-chunked.zip' \
  -H 'Content-Type: application/zip' \
  -H 'accept: application/json' \
  -H 'Transfer-Encoding: chunked' \
  --data-binary "@$TEST_ZIP"

echo -e "\n\n${BLUE}Test 3: Upload corrupted ZIP file${NC}"
echo "Creating and sending corrupted ZIP file"

# Create a corrupted zip file by truncating the original
head -c $(($FILE_SIZE / 2)) "$TEST_ZIP" > corrupt-test.zip
CORRUPT_SIZE=$(stat -f %z "corrupt-test.zip")
echo -e "Corrupted file size: ${RED}$CORRUPT_SIZE bytes${NC}"

curl -i -X 'PUT' \
  'http://localhost:8081/connector/edp/test-connector/corrupt-edp-result.zip' \
  -H "Content-Length: $CORRUPT_SIZE" \
  -H 'Content-Type: application/zip' \
  -H 'accept: application/json' \
  --data-binary "@corrupt-test.zip"

echo -e "\n\n${YELLOW}Test 4: Upload multi-file ZIP (closer to real-world scenario)${NC}"
echo "Sending multi-file ZIP with proper Content-Length"

curl -i -X 'PUT' \
  'http://localhost:8081/connector/edp/test-connector/multi-file-edp-result.zip' \
  -H "Content-Length: $MULTI_FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  -H 'accept: application/json' \
  --data-binary "@$MULTI_FILE_ZIP"

echo -e "\n\n${YELLOW}Test 5: Upload multi-file ZIP with chunked encoding${NC}"
echo "Sending multi-file ZIP with chunked encoding"

curl -i -X 'PUT' \
  'http://localhost:8081/connector/edp/test-connector/multi-file-chunked.zip' \
  -H 'Content-Type: application/zip' \
  -H 'accept: application/json' \
  -H 'Transfer-Encoding: chunked' \
  --data-binary "@$MULTI_FILE_ZIP"

echo -e "\n\n${GREEN}Tests completed${NC}"
echo "Check the server logs for detailed information about the ZIP file validation" 