import os
import logging
import json
import uuid
from http.server import SimpleHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse
import zipfile

PORT = 8081
BASE_DIR = os.path.dirname(os.path.abspath(__file__))  # Base directory is /server
DATA_DIR = os.path.join(BASE_DIR, "data")  # Directory for GET request files
os.makedirs(DATA_DIR, exist_ok=True)

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler()
    ]
)

class CustomHandler(SimpleHTTPRequestHandler):
    def log_request_details(self):
        """Log details of the incoming request."""
        logging.info("--- Request Details ---")
        logging.info(f"Path: {self.path}")
        logging.info(f"Headers: {self.headers}")

    def do_GET(self):
        """Handle GET requests."""
        self.log_request_details()
        parsed_path = urlparse(self.path).path

        if parsed_path.startswith("/v1/dataspace/analysisjob/") and "/result" in parsed_path:
            self.serve_analysisjob_result(parsed_path)
        elif parsed_path.startswith("/v1/dataspace/analysisjob/") and "/status" in parsed_path:
            self.serve_analysisjob_status()
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")

    def serve_analysisjob_status(self):
        """Serve job status."""
        response = {
            "job_id": "xxx",
            "state": "WAITING_FOR_DATA",
            "state_detail": "Job is waiting for data to be uploaded."
        }
        response_bytes = json.dumps(response, indent=2).encode('utf-8')

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response_bytes)))
        self.end_headers()
        self.wfile.write(response_bytes)

    def serve_analysisjob_result(self, parsed_path):
        """Serve the processed .zip file."""
        try:
            parts = parsed_path.split("/")
            job_id = parts[4]  # Extract job_id
        except IndexError:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Invalid path format.")
            return

        # Look for the corresponding .zip file
        zip_file_name = f"{job_id}.zip"
        zip_file_path = os.path.join(DATA_DIR, zip_file_name)

        if os.path.exists(zip_file_path):
            self.send_response(200)
            self.send_header("Content-Type", "application/zip")
            self.end_headers()
            with open(zip_file_path, "rb") as file:
                self.wfile.write(file.read())
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Result file not found.")

    def do_POST(self):
        """Handle POST requests."""
        self.log_request_details()
        parsed_path = urlparse(self.path).path

        if parsed_path == "/v1/dataspace/analysisjob":
            self.handle_create_analysisjob()
        elif parsed_path.startswith("/v1/dataspace/analysisjob/") and "/data" in parsed_path:
            self.handle_analysisjob_upload(parsed_path)
        elif parsed_path.startswith("/create-edp"):
            self.handle_daseen_upload(parsed_path)
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")


    def handle_create_analysisjob(self):
        """Create a new analysis job and return mock response."""
        try:
            # Verify content type if needed
            content_type = self.headers.get('Content-Type', '')
            if content_type and 'application/json' not in content_type:
                self.send_error(415, "Unsupported Media Type. Expected application/json")
                return

            job_id = str(uuid.uuid4())

            response = {
                "job_id": job_id,
                "state": "WAITING_FOR_DATA",
                "state_detail": "Job is waiting for data to be uploaded."
            }

            response_bytes = json.dumps(response, indent=2).encode('utf-8')

            self.send_response(201)
            self.send_header("Content-Length", str(len(response_bytes)))
            self.send_header("Content-Type", "application/json")
            self.send_header("Location", "/v1/dataspace/analysisjob")
            self.end_headers()
            self.wfile.write(response_bytes)
            self.wfile.flush()

            logging.info(f"Created new analysis job with ID: {job_id}")
        except Exception as e:
            logging.error(f"Error creating analysis job: {str(e)}")
            self.send_error(500, f"Internal server error: {str(e)}")

    def handle_analysisjob_upload(self, parsed_path):
        """Handle file uploads for analysis jobs."""
        try:
            # Expected format: /v1/dataspace/analysisjob/{job_id}/data
            parts = parsed_path.split("/")
            job_id = parts[4]  # Extract job_id
        except IndexError:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Invalid path format.")
            return

        # Save the file as {jobid}-{filename}.csv
        result_file_name = f"{job_id}.csv"
        result_file_path = os.path.join(DATA_DIR, result_file_name)

        try:
            content_length = int(self.headers.get("Content-Length", 0))
            with open(result_file_path, "wb") as output_file:
                output_file.write(self.rfile.read(content_length))

            self.process_edps(result_file_name)

            self.send_response(201)
            self.end_headers()
            self.wfile.write(f"File uploaded and processed successfully.".encode())
        except Exception as e:
            self.send_response(500)
            self.end_headers()
            self.wfile.write(f"Error processing file: {e}".encode())

    def process_edps(self, result_file_name):
        """Process the CSV file into a .zip file."""
        result_file_path = os.path.join(DATA_DIR, result_file_name)
        zip_file_name = result_file_name.replace(".csv", ".zip")
        zip_file_path = os.path.join(DATA_DIR, zip_file_name)

        try:
            with zipfile.ZipFile(zip_file_path, "w") as zip_file:
                zip_file.write(result_file_path, arcname=result_file_name)
            logging.info("Processed %s into %s", result_file_name, zip_file_name)
        except Exception as e:
            logging.info("Error creating zip file %s", e)

    def handle_daseen_upload(self, parsed_path):
        """Handle daseen file uploads."""
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()

        response = {
            "state": "SUCCESS",
            "state_detail": "EDPS data published to Daseen"
        }
        self.wfile.write(json.dumps(response).encode())


if __name__ == "__main__":
    server_address = ("", PORT)
    httpd = HTTPServer(server_address, CustomHandler)
    logging.info("Serving EDPS mock on http://localhost:%s", PORT)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()
        logging.info("Server stopped.")
