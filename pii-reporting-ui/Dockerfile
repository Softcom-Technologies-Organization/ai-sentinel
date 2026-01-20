# syntax=docker/dockerfile:1

FROM node:20 AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci --legacy-peer-deps

COPY . .

RUN npm run build -- --configuration production

FROM nginx:alpine AS runtime

# Copy built Angular app
COPY --from=build /app/dist/ai-sentinel-ui-angular/browser /usr/share/nginx/html

# Integrate Nginx reverse-proxy configuration into the image
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
