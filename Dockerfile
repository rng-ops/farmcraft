FROM node:20-alpine AS builder

WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@8.15.0 --activate

# Copy package files
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml* ./
COPY packages/types/package.json ./packages/types/
COPY packages/protocol/package.json ./packages/protocol/
COPY packages/pow-core/package.json ./packages/pow-core/
COPY server/recipe-server/package.json ./server/recipe-server/

# Install dependencies
RUN pnpm install --frozen-lockfile

# Copy source files
COPY packages/ ./packages/
COPY server/ ./server/

# Build
RUN pnpm run build

# Production image
FROM node:20-alpine AS runner

WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@8.15.0 --activate

ENV NODE_ENV=production
ENV PORT=3000
ENV WS_PORT=3001

# Copy built files
COPY --from=builder /app/package.json ./
COPY --from=builder /app/pnpm-workspace.yaml ./
COPY --from=builder /app/pnpm-lock.yaml* ./
COPY --from=builder /app/packages/types/package.json ./packages/types/
COPY --from=builder /app/packages/types/dist ./packages/types/dist
COPY --from=builder /app/packages/protocol/package.json ./packages/protocol/
COPY --from=builder /app/packages/protocol/dist ./packages/protocol/dist
COPY --from=builder /app/packages/pow-core/package.json ./packages/pow-core/
COPY --from=builder /app/packages/pow-core/dist ./packages/pow-core/dist
COPY --from=builder /app/server/recipe-server/package.json ./server/recipe-server/
COPY --from=builder /app/server/recipe-server/dist ./server/recipe-server/dist

# Install production dependencies only
RUN pnpm install --prod --frozen-lockfile

EXPOSE 3000 3001

CMD ["node", "server/recipe-server/dist/index.js"]
