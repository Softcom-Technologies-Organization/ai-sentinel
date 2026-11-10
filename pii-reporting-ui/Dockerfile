# syntax=docker/dockerfile:1

FROM --platform=$BUILDPLATFORM node:22-alpine AS build

# Activer corepack pour utiliser pnpm (lu depuis packageManager dans package.json)
RUN corepack enable

WORKDIR /app

# Copier d'abord les fichiers de dependances pour maximiser le cache Docker
COPY package.json pnpm-lock.yaml .npmrc ./
RUN corepack install
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build --configuration production

FROM nginx:alpine AS runtime

# Copy built Angular app
COPY --from=build /app/dist/ai-sentinel-ui-angular/browser /usr/share/nginx/html

# Integrate Nginx reverse-proxy configuration into the image
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
