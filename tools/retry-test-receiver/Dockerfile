FROM node:22-alpine
ENV RETRY_RECEIVER_HOST=0.0.0.0
ENV RETRY_RECEIVER_PORT=9090
WORKDIR /app
COPY server.mjs ./server.mjs
EXPOSE 9090
CMD ["node", "server.mjs"]
