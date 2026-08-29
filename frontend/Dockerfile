# syntax=docker/dockerfile:1

# ---- Dependencies ----
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ---- Build ----
FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# NEXT_PUBLIC_* AND rewrite destinations are inlined into the client bundle /
# build manifest at build time (they cannot be changed via runtime env):
# - NEXT_PUBLIC_WS_URL: the browser connects straight to the backend's /ws
#   endpoint. Behind the optional gateway profile the value is irrelevant
#   (nginx proxies /ws same-origin).
# - KANVRA_BACKEND_URL: the /api/* rewrite target, baked into
#   routes-manifest.json by `next build`.
ARG NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
ARG KANVRA_BACKEND_URL=http://localhost:8080
ENV NEXT_PUBLIC_WS_URL=$NEXT_PUBLIC_WS_URL \
    KANVRA_BACKEND_URL=$KANVRA_BACKEND_URL \
    NEXT_TELEMETRY_DISABLED=1
# The repo currently has no public/ directory; create it so the standalone
# COPY below always succeeds if one is never added.
RUN mkdir -p public && npm run build

# ---- Runtime ----
# next.config.mjs sets output:"standalone", so the runtime only needs the
# self-contained server bundle plus its static assets (no node_modules).
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0 \
    NEXT_TELEMETRY_DISABLED=1
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public
USER node
EXPOSE 3000
# wget (busybox, ships with alpine) backs the container healthcheck.
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 \
  CMD wget -qO- http://127.0.0.1:3000/ >/dev/null 2>&1 || exit 1
CMD ["node", "server.js"]