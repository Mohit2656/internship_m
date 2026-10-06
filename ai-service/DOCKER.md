# AI service Docker deployment

The locked `@kreuzberg/node-linux-x64-gnu` 4.10.2 addon requires
`GLIBCXX_3.4.31`. The Dockerfile uses the official `node:24-trixie-slim`
image (Debian 13), installs `libstdc++6` and `libgcc-s1`, and fails the build
if the C++ ABI symbol is missing or the native addon cannot load.

The inspected Linux addon directly links only libstdc++, libgcc, libc,
libm, and the ELF loader. Debian supplies the last three. `ca-certificates`
is for HTTPS dependency/model downloads; `binutils` provides `strings`
for the ABI check. No source compiler is needed for the locked prebuilt
addon. The current extraction calls do not enable OCR, so this image does
not add Tesseract or change OCR behavior.

The existing parser, model setup scripts, inference implementation, API,
and safe error responses are unchanged. No existing AI Dockerfile or
Render Blueprint was present; this configuration belongs only to the AI
service.

## Existing Render AI web service settings

Use the existing AI service's **Settings > Build > Source > Edit** to
select its current repository/branch and change Runtime to **Docker**.
Using the existing service retains its URL and backend integration.

| Setting | Value |
| --- | --- |
| Runtime / Language | Docker |
| Branch | main |
| Root Directory | ai-service |
| Dockerfile Path | Dockerfile |
| Docker Build Context Directory | . |
| Docker Command | Empty (use Dockerfile CMD) |
| Health Check Path | /health |
| Build / Start commands | Managed by Dockerfile; no native Node commands |

These paths are relative to the Root Directory. If keeping Root Directory
empty instead, use `ai-service/Dockerfile` for Dockerfile Path and
`ai-service` for Docker Build Context Directory. Do not combine the two
path configurations.

The image defaults to `AI_SERVICE_HOST=0.0.0.0` and
`AI_SERVICE_PORT=10000`. If existing Render environment variables override
these defaults, the effective host must be `0.0.0.0` and the effective
`AI_SERVICE_PORT` must match Render's listening port (normally 10000).
`AI_SERVICE_PORT` overrides the image default; the application does not
read `PORT` directly. Keep the existing backend AI URL and all other
runtime settings.

Existing non-secret `RESUME_MODEL_REVISION`, `JD_MODEL_REVISION`,
`RESUME_MODEL_PATH`, and `JD_MODEL_PATH` settings are accepted as build
arguments. Render forwards matching environment variables automatically.
With no overrides, the unchanged setup scripts use their existing model
repositories, revisions, and paths under `/app/models`. If overriding a
model path, it must be writable by the image's `node` user during setup,
available inside the image, and identical at build time and runtime.

Render instructions:
- [Change an existing service runtime](https://render.com/docs/native-runtimes#changing-a-services-runtime)
- [Docker builds and commands](https://render.com/docs/docker)
- [Root-relative paths](https://render.com/docs/monorepo-support#root-directory)

## Build and start behavior

1. Install the Debian runtime packages and verify `GLIBCXX_3.4.31`.
2. Run `npm ci --include=dev --include=optional` using the unchanged lockfile.
   Keep devDependencies because `npm start` uses `tsx`.
3. Force-load Kreuzberg's native binding so linker failures stop the build.
4. Run `npm run model:setup` for the existing resume and JD models.
5. Run typecheck, the normal test suite, and the existing real-model
   PDF/DOCX tests on Linux with synthetic documents.
6. Start with `npm start` in `/app`, binding to `0.0.0.0:10000` by default.

Model downloads occur during the image build and are cached in their own
layer. Local models and Windows dependencies are excluded from the build
context. No models are downloaded by the container's start command.

## Validation commands

From the repository root with Docker running:

```powershell
docker build --pull --platform linux/amd64 --progress=plain -t ims-resume-ai:local ./ai-service
docker run --rm --platform linux/amd64 ims-resume-ai:local sh -c 'strings /lib/x86_64-linux-gnu/libstdc++.so.6 | grep -Fx GLIBCXX_3.4.31'
docker run --rm --platform linux/amd64 -p 10000:10000 ims-resume-ai:local
```

In another PowerShell window:

```powershell
curl.exe --fail http://127.0.0.1:10000/health
curl.exe --fail -X POST -F "file=@C:\path\to\resume.pdf" http://127.0.0.1:10000/internal/resume/analyze
```

The health response and multipart `file` request remain unchanged. To test
another port, pass `-e AI_SERVICE_PORT=10001 -p 10001:10001` to `docker run`.

Local checks (inside `ai-service`):

```powershell
npm.cmd run typecheck
npm.cmd test
```

Caveats: the first image build needs network access to npm and Hugging
Face and time/memory for model setup and inference checks. The native
runtime fix does not increase the Render service's memory allocation.
A successful Docker build is the Linux compatibility/inference validation;
local Windows tests alone do not establish that the image works. The base
tag can receive Node patch updates; `--pull` refreshes it during local
validation while keeping Debian Trixie as the OS family.
