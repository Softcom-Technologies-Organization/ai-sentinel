# syntax=docker/dockerfile:1

FROM node:20 AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

RUN npm run build -- --configuration production

FROM nginx:alpine AS runtime

COPY --from=build /app/dist/sentinelle-ui-angular/browser /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
