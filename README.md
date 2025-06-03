# Distributed File Storage Load Balancer

A high-performance, fault-tolerant distributed file storage system built with Java, featuring intelligent load balancing, real-time monitoring, and seamless horizontal scaling across containerized storage nodes.

## 🚀 Features

### Core Functionality
- **Distributed Architecture**: Scales across 4+ Docker containers with automatic service discovery
- **Intelligent Load Balancing**: Multiple algorithms (FCFS, Shortest Job Next, Round Robin) with dynamic switching
- **Real-time Operations**: Asynchronous file upload, download, delete, and edit operations
- **Fault Tolerance**: Automatic health monitoring, failover, and task reassignment
- **Concurrent Processing**: Multi-threaded task execution with comprehensive state management

### Security & Access Control
- **Role-based Authentication**: Admin and student user roles with granular permissions
- **Secure File Transfers**: SSH/SFTP integration for inter-container communication
- **Session Management**: Timeout handling and secure password hashing with salts
- **File Permissions**: Granular read/write access control per file and user

### Monitoring & Reliability
- **Real-time Progress Tracking**: Live updates via MQTT messaging
- **Health Monitoring**: Continuous container health checks with automatic failover
- **Conflict Detection**: Comprehensive task conflict resolution and retry mechanisms
- **Performance Metrics**: Load tracking and optimization across storage nodes

## 🛠 Technology Stack

- **Backend**: Java 11+, JavaFX
- **Messaging**: MQTT (Eclipse Paho)
- **Databases**: MySQL (primary), SQLite (sessions)
- **Containerization**: Docker & Docker Compose
- **File Transfer**: SSH/SFTP (JSch)
- **Build Tool**: Maven

## 🏗 Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   JavaFX GUI    │    │  Load Balancer   │    │  MQTT Broker    │
│                 │◄──►│                  │◄──►│                 │
│ - File Ops      │    │ - Algorithm      │    │ - Task Queue    │
│ - Progress      │    │ - Health Check   │    │ - Notifications │
│ - Authentication│    │ - Failover       │    │ - Pub/Sub       │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────┐
│                    Storage Layer                               │
├─────────────────┬─────────────────┬─────────────────┬──────────┤
│   Container 1   │   Container 2   │   Container 3   │Container4│
│                 │                 │                 │          │
│ - File Storage  │ - File Storage  │ - File Storage  │File Store│
│ - SSH Server    │ - SSH Server    │ - SSH Server    │SSH Server│
│ - Health Check  │ - Health Check  │ - Health Check  │Health Chk│
└─────────────────┴─────────────────┴─────────────────┴──────────┘
                                │
                                ▼
                    ┌──────────────────┐
                    │   MySQL DB       │
                    │                  │
                    │ - File Metadata  │
                    │ - User Data      │
                    │ - Permissions    │
                    │ - Task History   │
                    └──────────────────┘


- Built as part of SOFT20091 coursework
