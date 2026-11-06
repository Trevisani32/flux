FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx/cabecalhos-seguranca.conf /etc/nginx/snippets/cabecalhos-seguranca.conf
COPY --from=build /app/dist/web/browser /usr/share/nginx/html
EXPOSE 80
