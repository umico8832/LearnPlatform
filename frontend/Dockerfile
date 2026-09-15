# 多阶段构建：前端 Vue 应用
FROM node:26-alpine AS build
ARG VITE_TURNSTILE_SITE_KEY
ENV VITE_TURNSTILE_SITE_KEY=$VITE_TURNSTILE_SITE_KEY
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# 运行阶段：Nginx 静态资源服务
FROM nginx:alpine
ARG APP_REVISION=unknown
ARG APP_WORKTREE_STATE=unknown
ARG APP_SOURCE_FINGERPRINT=unknown
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
LABEL org.opencontainers.image.revision=$APP_REVISION \
      com.learnplatform.worktree-state=$APP_WORKTREE_STATE \
      com.learnplatform.source-fingerprint=$APP_SOURCE_FINGERPRINT
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
