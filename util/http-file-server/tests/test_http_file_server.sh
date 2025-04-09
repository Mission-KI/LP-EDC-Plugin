#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

SERVER_PORT=8080
SERVER_URL="http://localhost:${SERVER_PORT}"

echo -e "${BLUE}Testing HTTP File Server (server.py)${NC}"
echo "========================================"

# Create test directory if it doesn't exist
TEST_DIR="test_files"
mkdir -p $TEST_DIR

# Create a test ZIP file if it doesn't exist
TEST_ZIP="${TEST_DIR}/test-upload.zip"
if [ ! -f "$TEST_ZIP" ]; then
    echo -e "${BLUE}Creating test ZIP file...${NC}"
    echo "Test content for upload" > ${TEST_DIR}/test-content.txt
    
    # Create a zip file
    zip "$TEST_ZIP" ${TEST_DIR}/test-content.txt
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Created test ZIP file: $TEST_ZIP${NC}"
        rm ${TEST_DIR}/test-content.txt
    else
        echo -e "${RED}Failed to create test ZIP file${NC}"
        exit 1
    fi
fi

# Create a multi-file ZIP to better simulate real-world files
MULTI_FILE_ZIP="${TEST_DIR}/multi-file-test.zip"
if [ ! -f "$MULTI_FILE_ZIP" ]; then
    echo -e "${BLUE}Creating multi-file test ZIP...${NC}"
    mkdir -p ${TEST_DIR}/temp_dir
    # Create multiple files with different content
    for i in {1..5}; do
        echo "Content for file $i with some random data: $RANDOM $RANDOM" > "${TEST_DIR}/temp_dir/file_$i.txt"
    done
    # Create a nested directory structure
    mkdir -p ${TEST_DIR}/temp_dir/subdir1/subdir2
    echo "Nested file content" > ${TEST_DIR}/temp_dir/subdir1/subdir2/nested.txt
    
    # Create the ZIP file
    zip -r "$MULTI_FILE_ZIP" ${TEST_DIR}/temp_dir
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Created multi-file ZIP: $MULTI_FILE_ZIP${NC}"
        # Clean up the temporary directory
        rm -rf ${TEST_DIR}/temp_dir
    else
        echo -e "${RED}Failed to create multi-file ZIP${NC}"
        exit 1
    fi
fi

# Get file sizes
FILE_SIZE=$(stat -f %z "$TEST_ZIP" 2>/dev/null || stat -c %s "$TEST_ZIP")
MULTI_FILE_SIZE=$(stat -f %z "$MULTI_FILE_ZIP" 2>/dev/null || stat -c %s "$MULTI_FILE_ZIP")
echo -e "Simple file size: ${GREEN}$FILE_SIZE bytes${NC}"
echo -e "Multi-file size: ${GREEN}$MULTI_FILE_SIZE bytes${NC}"

# PART 1: UPLOAD TESTS

echo -e "\n${BLUE}PART 1: TESTING FILE UPLOADS${NC}"
echo "============================="

# Test 1: PUT request with correct file extension
echo -e "\n${BLUE}Test 1: PUT request with .zip extension${NC}"
echo "PUT request to ${SERVER_URL}/test-upload.zip"

curl -i -X PUT \
  "${SERVER_URL}/test-upload.zip" \
  -H "Content-Length: $FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  --data-binary "@$TEST_ZIP"

# Test 2: POST request with Content-Disposition header
echo -e "\n\n${BLUE}Test 2: POST request with Content-Disposition header${NC}"
echo "POST request to ${SERVER_URL}"

curl -i -X POST \
  "${SERVER_URL}" \
  -H "Content-Length: $FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  -H 'Content-Disposition: attachment; filename="posted-file.zip"' \
  --data-binary "@$TEST_ZIP"

# Test 3: PUT request with multi-file ZIP
echo -e "\n\n${BLUE}Test 3: PUT request with multi-file ZIP${NC}"
echo "PUT request to ${SERVER_URL}/multi-file.zip"

curl -i -X PUT \
  "${SERVER_URL}/multi-file.zip" \
  -H "Content-Length: $MULTI_FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  --data-binary "@$MULTI_FILE_ZIP"

# Test 4: PUT request with chunked transfer encoding
echo -e "\n\n${YELLOW}Test 4: PUT request with chunked transfer encoding${NC}"
echo "PUT request to ${SERVER_URL}/chunked-upload.zip"

curl -i -X PUT \
  "${SERVER_URL}/chunked-upload.zip" \
  -H 'Content-Type: application/zip' \
  -H 'Transfer-Encoding: chunked' \
  --data-binary "@$TEST_ZIP"

# Test 5: PUT request with the default filename expected by the server
echo -e "\n\n${GREEN}Test 5: PUT request with edps-result-data.zip filename${NC}"
echo "PUT request to ${SERVER_URL}/edps-result-data.zip"

curl -i -X PUT \
  "${SERVER_URL}/edps-result-data.zip" \
  -H "Content-Length: $FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  --data-binary "@$TEST_ZIP"

# PART 2: DOWNLOAD TESTS

echo -e "\n\n${BLUE}PART 2: TESTING FILE DOWNLOADS${NC}"
echo "============================="

# Test 6: Basic GET request with correct file name
echo -e "\n${BLUE}Test 6: Basic GET request with correct file name${NC}"
echo "GET request to ${SERVER_URL}/test-upload.zip"

curl -i -X GET \
  "${SERVER_URL}/test-upload.zip" \
  -H 'accept: application/zip' \
  -o "${TEST_DIR}/downloaded-test-upload.zip"

# Test 7: GET request with trailing slash
echo -e "\n\n${BLUE}Test 7: GET request with trailing slash${NC}"
echo "GET request to ${SERVER_URL}/multi-file.zip/"

curl -i -X GET \
  "${SERVER_URL}/multi-file.zip/" \
  -H 'accept: application/zip' \
  -o "${TEST_DIR}/downloaded-multi-file.zip"

# Test 8: GET request without .zip extension
echo -e "\n\n${BLUE}Test 8: GET request without .zip extension${NC}"
echo "GET request to ${SERVER_URL}/edps-result-data"

curl -i -X GET \
  "${SERVER_URL}/edps-result-data" \
  -H 'accept: application/zip' \
  -o "${TEST_DIR}/downloaded-edps-result-data.zip"

# Test 9: GET request with URL-encoded characters
echo -e "\n\n${BLUE}Test 9: GET request with URL-encoded characters${NC}"
echo "GET request to ${SERVER_URL}/test-upload%2Ezip"

curl -i -X GET \
  "${SERVER_URL}/test-upload%2Ezip" \
  -H 'accept: application/zip' \
  -o "${TEST_DIR}/downloaded-encoded.zip"

echo -e "\n\n${GREEN}All tests completed${NC}"
echo "Check the server logs for detailed information about file handling"
echo "Downloaded files are saved in the ${TEST_DIR} directory" 