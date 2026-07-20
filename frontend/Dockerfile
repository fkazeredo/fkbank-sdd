# The SPA plus the nginx edge that fronts the whole application on one origin.
FROM node:22-alpine AS build
WORKDIR /app

# Dependencies are copied and installed before the sources, so a code change does not
# invalidate the (slow) install layer.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# The unprivileged nginx image rather than the stock one: it is built to run as a normal user,
# with the cache, pid and log paths already owned by that user. Hand-patching those permissions
# onto the root image is the usual alternative and it breaks quietly on upgrades.
# Port 8090 is above 1024, so no privileged bind is needed.
FROM nginxinc/nginx-unprivileged:1.29-alpine AS runtime

# Declared explicitly even though the base image already sets it: a container escape should not
# begin as root, and the supply-chain scanner checks this Dockerfile rather than the base image.
USER 101

COPY --from=build /app/dist/fkbank/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8090

# 127.0.0.1, never `localhost`: inside the container `localhost` resolves to IPv6 ::1 first,
# where nginx is not listening, and the healthcheck would fail for a reason unrelated to the
# application.
HEALTHCHECK --interval=5s --timeout=3s --retries=20 \
  CMD wget -q -O /dev/null http://127.0.0.1:8090/ || exit 1
