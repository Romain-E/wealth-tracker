# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------------------------
# Build: type check, generate the API types from openapi.json, bundle.
# ---------------------------------------------------------------------------------------------
FROM node:25-alpine AS build
WORKDIR /web

# Dependencies first, so a code change reuses this layer. --ignore-scripts: no package gets to run
# code at install time; nothing this project depends on needs to.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --ignore-scripts

COPY . .
RUN npm run build

# ---------------------------------------------------------------------------------------------
# Runtime: static files behind nginx, as a non-root user on an unprivileged port.
# ---------------------------------------------------------------------------------------------
FROM nginxinc/nginx-unprivileged:1.29-alpine AS runtime

# Templates are rendered at start-up by the image's entrypoint, which substitutes API_UPSTREAM.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /web/dist /usr/share/nginx/html

# Where /api is forwarded. Compose and the Helm chart set it to the API's service name.
ENV API_UPSTREAM=http://api:8080

EXPOSE 8080
