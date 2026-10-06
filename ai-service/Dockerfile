# Kreuzberg's locked Linux native addon requires GLIBCXX_3.4.31.
# Debian Trixie provides GCC 14's libstdc++; Bookworm's default library is too old.
FROM node:24-trixie-slim

WORKDIR /app

# libc/libm/the ELF loader come from Debian; libgcc and libstdc++ are the
# remaining DT_NEEDED libraries of @kreuzberg/node-linux-x64-gnu 4.10.2.
# ca-certificates supports npm/model HTTPS downloads; binutils supplies strings.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates libstdc++6 libgcc-s1 binutils \
    && rm -rf /var/lib/apt/lists/* \
    && libstdcpp="$(find /usr/lib -name libstdc++.so.6 -print -quit)" \
    && test -n "$libstdcpp" \
    && strings "$libstdcpp" | grep -Fx 'GLIBCXX_3.4.31'

RUN chown node:node /app
USER node

COPY --chown=node:node package.json package-lock.json ./
# tsx is a devDependency but is required by the existing npm start command.
# Optional platform packages include Kreuzberg's Linux native addon.
RUN npm ci --include=dev --include=optional

# Loading the low-level entry point forces dlopen; importing the public module
# alone is insufficient because its binding is initialized lazily.
RUN node -e "require('./node_modules/@kreuzberg/node/index.js'); console.log('Kreuzberg native binding loaded successfully')"

COPY --chown=node:node scripts/setup-model.mjs scripts/setup-jd-model.mjs ./scripts/
# Render forwards existing non-secret model settings as Docker build arguments.
# Empty revisions retain the setup scripts' existing defaults.
ARG RESUME_MODEL_REVISION
ARG JD_MODEL_REVISION
ARG RESUME_MODEL_PATH=./models/resume-ner
ARG JD_MODEL_PATH=./models/jd-embedding
RUN npm run model:setup

COPY --chown=node:node tsconfig.json ./
COPY --chown=node:node src/ ./src/
COPY --chown=node:node test/ ./test/
# Exercise the unchanged extraction + tokenizer + ONNX path on Linux using
# synthetic PDF/DOCX fixtures, before an image can reach deployment.
RUN npm run typecheck \
    && npm test \
    && AI_REAL_MODEL_TEST=true node --import tsx --test \
        --test-name-pattern="real DistilBERT ONNX path" test/real-model.test.ts

ENV NODE_ENV=production \
    AI_SERVICE_HOST=0.0.0.0 \
    AI_SERVICE_PORT=10000

EXPOSE 10000
CMD ["npm", "start"]
