import os
from http.server import SimpleHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse

# Configuration
PORT = 8080
BASE_DIR = os.path.dirname(os.path.abspath(__file__))  # Base directory is /server
DATA_DIR = os.path.join(BASE_DIR, "data")  # Directory for GET request files
RESULTS_DIR = os.path.join(BASE_DIR, "results")  # Directory for uploaded results

# Ensure directories exist
os.makedirs(DATA_DIR, exist_ok=True)
os.makedirs(RESULTS_DIR, exist_ok=True)

class CustomHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        """Handle GET requests."""
        parsed_path = urlparse(self.path).path

        if parsed_path == "/data.csv":
            self.serve_data_file()
        else:
            super().do_GET()

    def serve_data_file(self):
        """Serve the data.csv file from the data directory."""
        data_file_path = os.path.join(DATA_DIR, "data.csv")

        if os.path.exists(data_file_path):
            self.send_response(200)
            self.send_header("Content-type", "text/csv")
            self.end_headers()
            with open(data_file_path, "rb") as file:
                self.wfile.write(file.read())
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"File not found.")

    def do_POST(self):
        """Handle POST requests for file upload."""
        parsed_path = urlparse(self.path).path

        if parsed_path == "/upload":
            self.handle_file_upload()
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")

    def handle_file_upload(self):
        """Handles file uploads sent as octet stream and saves them in the results directory."""
        content_type = self.headers.get("Content-Type")

        print("--- Incoming POST Request ---")

        # Log the entire request
        print("--- Request Line ---")
        print(self.requestline)

        print("--- Headers ---")
        print(self.headers)

#         if content_type != "application/octet-stream":
#             self.send_response(400)
#             self.end_headers()
#             self.wfile.write(b"Invalid content type. Expected application/octet-stream.")
#             return

        file_name = "data_eps.zip"  # self.headers.get("X-File-Name")  # Custom header to specify the file name
        if not file_name:
            self.send_response(400)
            self.end_headers()
            #self.wfile.write(b"Missing X-File-Name header.")
            return

        file_path = os.path.join(RESULTS_DIR, os.path.basename(file_name))

        try:
            content_length = int(self.headers.get("Content-Length", 0))

            with open(file_path, "wb") as output_file:
                output_file.write(self.rfile.read(content_length))

            self.send_response(201)
            self.end_headers()
            self.wfile.write(b"File uploaded successfully")
        except Exception as e:
            self.send_response(500)
            self.end_headers()
            self.wfile.write(f"Error saving file: {e}".encode())


# Start the server
if __name__ == "__main__":
    server_address = ("", PORT)
    httpd = HTTPServer(server_address, CustomHandler)
    print(f"Serving on http://localhost:{PORT}")
    httpd.serve_forever()
