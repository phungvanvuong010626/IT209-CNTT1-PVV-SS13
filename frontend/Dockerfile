# syntax=docker/dockerfile:1
# Dockerfile đa giai đoạn cho frontend Campus Hub Guard (React + Vite, phục vụ bằng nginx).

# ---------- Giai đoạn 1: build ----------
FROM node:22-alpine AS build
WORKDIR /app

# Cài dependency trước để tận dụng cache lớp.
COPY package.json package-lock.json ./
RUN npm ci

# Copy mã nguồn và build bản tĩnh vào thư mục dist.
COPY . .
RUN npm run build

# ---------- Giai đoạn 2: runtime ----------
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
