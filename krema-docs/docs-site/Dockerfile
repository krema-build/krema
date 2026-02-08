FROM node:20-alpine AS build

WORKDIR /app
COPY package.json ./
RUN npm install --frozen-lockfile || npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
