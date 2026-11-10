# Multi-stage Dockerfile for LedgerLine React Frontend
FROM node:20-alpine AS builder
WORKDIR /app

# Copy dependency specifications and install cleanly
COPY package*.json ./
RUN npm ci || npm install

# Copy source and build production bundle
COPY . .
RUN npm run build

# Web server stage with Nginx Alpine
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

