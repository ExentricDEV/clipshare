# ClipShare

A full-stack video sharing platform with secure authentication, async video processing, and real-time updates.

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen)

![React](https://img.shields.io/badge/React-19-blue)

![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)

![Keycloak](https://img.shields.io/badge/Keycloak-26-orange)

## Features

- 🎬 **Video Upload & Streaming** — Drag-and-drop upload with HLS streaming playback

- 🔐 **Secure Authentication** — OAuth2/OpenID Connect via Keycloak

- ⚡ **Async Video Processing** — RabbitMQ-powered transcoding pipeline

- 🗄️ **Database Migrations** — Flyway-managed schema versioning with PostgreSQL 18

## Quick Start

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) & Docker Compose

- Git

### Run the App

```bash

# Clone the repository
git clone  https://github.com/YOUR_USERNAME/clip-share.git
cd  clip-share


# Copy environment file
cp .env.example  .env


# Start all services
docker compose  up  -d


# View logs (optional)
docker compose  logs  -f

```

Once running, open [http://localhost:3000](http://localhost:3000) in your browser.

## License

MIT

---
