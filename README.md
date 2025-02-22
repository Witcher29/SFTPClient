# SFTP Client for Domain-IP Management

This project is a console-based Java application that connects to an SFTP server to manage a file containing domain-IP address pairs in a JSON-like structure. The application allows users to perform various operations such as listing domain-IP pairs, retrieving IPs by domain, retrieving domains by IP, adding new pairs, and deleting existing pairs. The application ensures that all domains and IPs are unique and validates IP addresses to ensure they are in IPv4 format.

## Features

- **SFTP Connection**: Connects to an SFTP server using provided credentials.
- **Domain-IP Management**:
  - List all domain-IP pairs.
  - Retrieve an IP address by domain name.
  - Retrieve a domain name by IP address.
  - Add a new domain-IP pair (with validation for uniqueness and IPv4 format).
  - Delete a domain-IP pair by domain or IP.
- **Input Validation**: Ensures that IP addresses are valid IPv4 and that domains and IPs are unique.
- **Sorted Output**: Lists domain-IP pairs sorted alphabetically by domain.
- **Cross-Platform**: Works on Linux and other platforms.

## Prerequisites

- Java SE 8 or higher.
- Maven for building the project.
- An SFTP server with a file containing domain-IP pairs in JSON-like format.

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/sftp-domain-ip-client.git
   cd sftp-domain-ip-client
