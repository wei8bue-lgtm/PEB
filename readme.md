# PEB - Production Engineering Backbone

A comprehensive web-based CAD and eBOM management system with revision control, workflow management, and structure hierarchy support.

## Overview

Production Engineering Backbone (PEB) is an enterprise-grade application designed to manage:
- **CAD Objects**: Design and Drawing objects with 3D visualization (JT format)
- **eBOM Objects**: Machine, System, Subsystem, Module, Submodule, Part, and Auxiliary components
- **Revision Control**: Complete revision management with release workflows
- **Structure Management**: Hierarchical bill of materials with validation rules
- **Document Management**: File attachments with categorization
- **Search**: Powerful wildcard search across all attributes
- **Export**: XML export for machine-readable eBOM data

## System Requirements

### Development Environment
- Java 17 or higher
- Maven 3.8+
- Oracle Database 19c or higher
- 8GB RAM minimum
- Modern web browser (Chrome, Firefox, Edge)

### Production Environment
- Windows Server 2019/2022
- Oracle Database 19c+ (High Availability configuration)
- 16GB+ RAM
- Load balancer for scalability (100-10,000 users)

## Technology Stack

- **Backend**: Spring Boot 3.2.0
- **Database**: Oracle Database with HikariCP connection pooling
- **Security**: SAML 2.0 SSO authentication
- **Frontend**: Thymeleaf, HTML5, CSS3, JavaScript
- **Build Tool**: Maven
- **Email**: Spring Mail (SMTP)
- **File Processing**: Thumbnailator for image thumbnails

## Project Structure

## Authentication

### Default Users (Development)

For development and testing, the following users are available:

| Username | Password   | Permissions                  |
|----------|------------|------------------------------|
| admin    | admin123   | READ, WRITE, DELETE, EXECUTE |
| user     | user123    | READ, WRITE                  |
| viewer   | viewer123  | READ                         |

### Production SSO Integration

For production deployment, replace the `UserDetailsService` bean in `SecurityConfig.java` with one of:

#### Option 1: LDAP Authentication

Add dependency:
```xml
<dependency>
 <groupId>org.springframework.boot</groupId>
 <artifactId>spring-boot-starter-data-ldap</artifactId>
</dependency>
