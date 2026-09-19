# MetroMind KMRL — Operations Dashboard (React UI)

[![React](https://img.shields.io/badge/React-18.3.1-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://reactjs.org/)
[![Vite](https://img.shields.io/badge/Vite-6.3.5-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-4.1-38BDF8?style=for-the-badge&logo=tailwind-css&logoColor=white)](https://tailwindcss.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)](LICENSE)

MetroMind KMRL is an enterprise-grade metro rail operations management dashboard designed for Kochi Metro Rail Limited (KMRL). It provides real-time train tracking, AI-driven dynamic schedule generation, fleet health monitoring, maintenance workflow approvals, and automated alert management.

---

## 🚀 Features & Modules

- **🤖 AI Schedule Generation & Dynamic Timetabling:** Real-time demand forecasting and timetable generation based on peak/off-peak passenger load.
- **🚆 Fleet Management & Health Indexing:** Live status monitoring of train sets, mileage tracking, night-yard positioning, and fitness certificates.
- **🛠️ Maintenance Workflow & Ticket Tracking:** Automated maintenance ticket creation, inspection scheduling, and component replacement tracking.
- **📋 Approver Panel:** Role-based approval management for train withdrawal, emergency maintenance, and schedule updates.
- **🔔 Real-time Alerts & Audit Logs:** Live system notifications, critical error tracking, and immutable audit logging.
- **🔒 Role-Based Access Control (RBAC):** Customized dashboards for SADA (Super Admin), Operations Controller (OC), Maintenance Manager (MDS), and Approver roles.

---

## 🛠️ Technology Stack

- **Framework:** React 18 (Vite)
- **Styling:** Tailwind CSS v4, Custom CSS Design System
- **Icons:** Lucide React & MUI Icons
- **Components:** Radix UI primitives & Shadcn UI architecture
- **State & HTTP:** Custom Data Cache & REST API Integration

---

## ⚙️ Getting Started

### Prerequisites

- **Node.js**: v18.0.0 or higher (v20 LTS recommended)
- **npm**: v9.0.0 or higher

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/Dharshini-Ramamoorthy/metromind-frontend.git
   cd metromind-frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Configure Environment Variables:
   Create a `.env` file in the project root:
   ```env
   VITE_API_BASE_URL=http://localhost:8080
   ```

4. Run local development server:
   ```bash
   npm run dev
   ```
   Access the dashboard at `http://localhost:5173`.

---

## 📦 Production Build

To compile the application for production deployment:

```bash
npm run build
```

To preview the production build locally:

```bash
npm run preview
```

---

## 📁 Directory Structure

```text
D:\ui/
├── app/                      # React application pages and components
│   ├── AISimulationPage.jsx  # AI schedule simulation tool
│   ├── AdminDashboard.jsx    # Super Admin (SADA) dashboard
│   ├── ApproverPanelPage.jsx # Approval workflows
│   ├── FleetPage.jsx         # Fleet management & train health
│   ├── MaintenancePage.jsx   # Maintenance tickets & scheduling
│   ├── OpsControllerDashboard.jsx # Operations controller view
│   ├── SchedulePage.jsx      # Timetable & trip planning
│   └── components/ui/        # Reusable UI component library
├── imports/                  # Design reference assets & screenshots
├── styles/                   # Global CSS, theme variables & Tailwind setup
├── index.html                # HTML entrypoint
├── vite.config.js            # Vite build configuration
└── package.json              # Project dependencies & scripts
```

---

## 🔗 Backend Microservices Repository

The backend API services (Spring Boot microservices architecture, API Gateway, Eureka Discovery, JWT Security) are available at:
👉 **[MetroMind Backend Repository](https://github.com/Dharshini-Ramamoorthy/MetroMind)**
