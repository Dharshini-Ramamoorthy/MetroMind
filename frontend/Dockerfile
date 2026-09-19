# Multi-stage Dockerfile for MetroMind KMRL React/Vite Frontend
# Stage 1: Build production bundle
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package descriptors and install dependencies
COPY package*.json ./
RUN npm ci --legacy-peer-deps || npm install

# Copy application code and build
COPY . .
RUN npm run build

# Stage 2: Serve production bundle with Nginx
FROM nginx:1.25-alpine

# Remove default nginx html
RUN rm -rf /usr/share/nginx/html/*

# Copy build output from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy custom nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
