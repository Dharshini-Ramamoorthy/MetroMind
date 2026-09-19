# MetroMind KMRL — Enterprise Metro Rail Management System

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.4-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2023.0.3-6DB33F?style=for-the-badge&logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![React](https://img.shields.io/badge/React-18.3.1-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://reactjs.org/)
[![Vite](https://img.shields.io/badge/Vite-6.3.5-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev/)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)

MetroMind KMRL is an integrated, enterprise-grade metro rail operations management platform for Kochi Metro Rail Limited (KMRL). It combines a Spring Cloud microservices backend with a modern React frontend for real-time train tracking, AI schedule generation, fleet health indexing, maintenance workflow approvals, and automated alert management.

---

## 🏗️ Repository Architecture

```text
kmrl_backend/
├── api-gateway/            # Spring Cloud Gateway (Port 8080) - Entrypoint & JWT Auth
├── eureka-server/          # Netflix Eureka Service Registry (Port 8761)
├── user-service/           # User authentication & RBAC management (Port 8081)
├── schedule-service/       # Dynamic timetable & train assignment engine (Port 8082)
├── report-service/         # Operational metrics & network performance reports (Port 8083)
├── fleet-service/          # Train set health, mileage & yard management (Port 8084)
├── maintenance-service/    # Maintenance tickets, inspections & fitness (Port 8085)
├── alert-service/          # Real-time rule engine & audit logging (Port 8086)
├── approver-service/       # Multi-level approval workflows (Port 8087)
├── forecast-service/       # Passenger demand forecasting engine (Port 8088)
└── frontend/               # React 18 + Vite + Tailwind CSS Operations Dashboard
```

---

## 🚀 Key Features

### Backend (Spring Boot Microservices)
- **Service Discovery:** Centralized registration via Netflix Eureka Server.
- **API Gateway:** Centralized routing, rate limiting, and JWT authentication.
- **Role-Based Access Control (RBAC):** Gateway header enforcement (`X-User-Id`, `X-User-Role`, `X-Gateway-Secret`) across SADA, OC, MDS, and Approver roles.
- **Circuit Breakers & Resilience:** Integrated Resilience4j fault tolerance and automated fallbacks across inter-service calls.
- **Database Persistence:** JPA/Hibernate persistence with Oracle Database and MongoDB integration.

### Frontend (React + Vite)
- **AI Schedule Simulation:** Visual timetable generator and passenger load optimization.
- **Fleet Health Indexing:** Live status cards for all train sets (Ready, Maintenance, Standby, Withdrawn).
- **Approver Panel:** Actionable approval queue for high-priority operational decisions.
- **Real-Time Alerts:** Live alert notifications with severity tagging and resolution controls.

---

## ⚙️ Getting Started

### Prerequisites
- **Java OpenJDK 17+**
- **Maven 3.8+**
- **Node.js 18+ (Node 20 LTS recommended)**
- **Docker & Docker Compose** (Optional for containerized startup)

---

### Running the Microservices Backend

1. Start Service Registry:
   ```bash
   cd eureka-server
   ..\mvnw.cmd spring-boot:run
   ```
2. Start API Gateway & Microservices:
   ```bash
   cd api-gateway
   ..\mvnw.cmd spring-boot:run
   ```

---

### Running the Frontend Dashboard

1. Navigate to the `frontend` directory:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start development server:
   ```bash
   npm run dev
   ```
   Access the dashboard at `http://localhost:5173`.

---

## 🐳 Docker Deployment

To launch all infrastructure services and microservices together using Docker Compose:

```bash
docker-compose up -d --build
```
