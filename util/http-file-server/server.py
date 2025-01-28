import os
from http.server import SimpleHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse

PORT = 8080
BASE_DIR = os.path.dirname(os.path.abspath(__file__))  # Base directory is /server
DATA_DIR = os.path.join(BASE_DIR, "data")  # Directory for GET request files
RESULTS_DIR = os.path.join(BASE_DIR, "edps")  # Directory for uploaded results

# Ensure directories exist
os.makedirs(DATA_DIR, exist_ok=True)
os.makedirs(RESULTS_DIR, exist_ok=True)

class CustomHandler(SimpleHTTPRequestHandler):
    def log_request_details(self):
        """Log details of the incoming request."""
        parsed_path = urlparse(self.path).path
        print("--- Path ---")
        print(parsed_path)
        print("--- Request Line ---")
        print(self.requestline)
        print("--- Headers ---")
        print(self.headers)

    def do_GET(self):
        """Handle GET requests."""
        self.log_request_details()

        parsed_path = urlparse(self.path).path
        file_name = parsed_path.lstrip("/")  # Extract the file name from the path

        if file_name:
            self.serve_data_file(file_name)
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")

    def serve_data_file(self, file_name):
        """Serve the specified file from the data directory."""
        data_file_path = os.path.join(DATA_DIR, file_name)

        if os.path.exists(data_file_path):
            # Guess the content type based on the file extension
            content_type = self.guess_type(data_file_path)
            self.send_response(200)
            self.send_header("Content-type", content_type)
            self.end_headers()
            with open(data_file_path, "rb") as file:
                self.wfile.write(file.read())
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"File not found.")

    def do_POST(self):
        """Handle POST requests for file upload."""
        self.log_request_details()
        parsed_path = urlparse(self.path).path

        if parsed_path == "/upload":
            self.handle_file_upload()
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")

    def handle_file_upload(self):
        """Handles file uploads sent as octet stream and saves them in the results directory."""
        file_name = self.headers.get("X-File-Name")

        if not file_name:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Missing filename in request.")
            return

        file_path = os.path.join(RESULTS_DIR, os.path.basename(file_name))

        try:
            content_length = int(self.headers.get("Content-Length", 0))

            with open(file_path, "wb") as output_file:
                output_file.write(self.rfile.read(content_length))

            self.send_response(201)
            self.end_headers()
            self.wfile.write(f"File uploaded successfully as {file_name}".encode())
        except Exception as e:
            self.send_response(500)
            self.end_headers()
            self.wfile.write(f"Error saving file: {e}".encode())


if __name__ == "__main__":
    server_address = ("", PORT)
    httpd = HTTPServer(server_address, CustomHandler)
    print(f"Serving on http://localhost:{PORT}")
    httpd.serve_forever()