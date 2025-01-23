import os
from http.server import SimpleHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse
import cgi

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
        """Handles file uploads and saves them in the results directory."""
        content_type, params = cgi.parse_header(self.headers.get("Content-Type"))

        if content_type != "multipart/form-data":
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Invalid content type")
            return

        form = cgi.FieldStorage(fp=self.rfile, headers=self.headers, environ={"REQUEST_METHOD": "POST"})
        file_field = form["file"]

        if file_field.filename:
            file_path = os.path.join(RESULTS_DIR, os.path.basename(file_field.filename))
            with open(file_path, "wb") as output_file:
                output_file.write(file_field.file.read())

            self.send_response(201)
            self.end_headers()
            self.wfile.write(b"File uploaded successfully")
        else:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"No file uploaded")

# Start the server
if __name__ == "__main__":
    server_address = ("", PORT)
    httpd = HTTPServer(server_address, CustomHandler)
    print(f"Serving on http://localhost:{PORT}")
    httpd.serve_forever()
