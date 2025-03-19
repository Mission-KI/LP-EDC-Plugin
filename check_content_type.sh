#!/bin/bash

# Make a GET request to the mock server and save output to a temporary file
# -i includes the response headers in the output
# -s makes curl silent (no progress meter)
echo "Fetching edps-result-data.zip from localhost:8080..."
echo "======================================================"
echo 
curl -i -s http://localhost:8080/edps-result-data.zip | awk '
# Print HTTP status line
NR==1 {print "\033[1;34m" $0 "\033[0m"; next}

# Empty line separates headers from body
/^$/ {body=1; print ""; next}

# Print headers in green with formatting
!body {
  split($0, parts, ":")
  if (length(parts) > 1) {
    header = parts[1]
    sub(parts[1]":", "", $0)
    value = $0
    # Highlight Content-Type in a different color
    if (tolower(header) == "content-type") {
      printf "\033[1;33m%-20s\033[0m: \033[1;32m%s\033[0m\n", header, value
    } else {
      printf "\033[1;36m%-20s\033[0m: %s\n", header, value
    }
  } else {
    print
  }
  next
}

# If we want to show beginning of body content (optional)
body && NR < 10 {
  print "\033[0;37m" $0 "\033[0m"
}
'
echo
echo "======================================================"
echo "Request completed" 