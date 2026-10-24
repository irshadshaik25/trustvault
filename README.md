# TrustVault — Secure Document Sharing

TrustVault is a Spring Boot application for uploading, managing, and sharing documents with permission-based access and audit logging.

## Features
- User registration with BCrypt password hashing
- Spring Security authentication
- Private document storage
- Document upload, listing, download, replacement, and deletion
- Viewer and Editor sharing permissions
- Access revocation
- Audit logging
- File size and file-type validation
- PostgreSQL persistence

## Technology Stack
- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Maven
- HTML

## Prerequisites
- JDK 21
- PostgreSQL
- A PostgreSQL database named trustvault

## Configuration
Set DB_USERNAME and DB_PASSWORD environment variables before starting the application. Never commit passwords, cookies, or session credentials.

## Run Locally
Run these commands from the project root:

    .\mvnw.cmd clean test
    .\mvnw.cmd spring-boot:run

Health endpoint: http://localhost:8080/api/health

Upload page: http://localhost:8080/upload.html

## Testing
Run .\mvnw.cmd clean test and manually verify authentication, document operations, sharing, revocation, and audit logs.

## Security
This is a challenge implementation. Review file-content validation, secret management, and database migrations before production deployment.

## License
No license has been specified.
