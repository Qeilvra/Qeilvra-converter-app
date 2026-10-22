# Hosting decision — checked October 22, 2026

No service has been provisioned, no payment method added, and no live deployment changed during this audit.

## Confirmed deployment failure

The owner confirmed that only the Vercel frontend exists; Spring Boot has never been deployed. Read-only HTTP probes returned:

| URL | Actual result |
| --- | --- |
| `https://qeilvra-converter-app.vercel.app/` | 200 HTML |
| `https://qeilvra-converter-app.vercel.app/tools/pdf-to-word` | 404 Vercel `NOT_FOUND` |
| `https://qeilvra-converter-app.vercel.app/api/v1/health/capabilities` | 404 Vercel `NOT_FOUND` |

There are two problems: missing SPA deep-link routing, and missing conversion backend. The repository's Vercel configuration repairs the former when redeployed. A rewrite cannot create a running Java service. The latter requires an actual backend address and `VITE_PROCESSING_API_URL` set at frontend build time. Backend CORS must allow the exact frontend origin.

## Recommended no-card development solution

Use the existing backend on your own machine, first through localhost. Once Docker and engine integration tests pass, a **Cloudflare Quick Tunnel** can temporarily expose it over HTTPS for testing with Vercel or an Android device. Cloudflare documents that Quick Tunnels need no account or domain. This therefore avoids card registration and paid hosting. It uses your machine's RAM, CPU, electricity, and internet connection; it is not an always-on cloud host.

After explicit approval to expose the local service:

```powershell
$env:FILEFLOW_ALLOWED_ORIGINS='https://qeilvra-converter-app.vercel.app'
docker compose up --build processing
# In a separate terminal, after installing cloudflared:
cloudflared tunnel --url http://localhost:8080
```

Set Vercel `VITE_PROCESSING_API_URL` to the printed `https://...trycloudflare.com/api/v1` and rebuild only after deployment approval. Set Android's `fileflowProcessingApiUrl` to the same URL for a test build. Changing this URL needs no document database or Supabase account.

Anyone who knows the tunnel address can reach the service. Keep its request limits enabled and use only synthetic documents for this demonstration. The URL changes on restart and availability ends when your machine or tunnel stops. Quick Tunnels have no uptime guarantee and are explicitly for testing, so this is not a Google Play production backend.

Source: [Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).

## Cloud options and constraints

**Render Free** is a candidate for a small demonstration, subject to account eligibility. Its official tutorial says no payment is required, and its free-service documentation describes operation without a payment method. This does not guarantee every signup avoids additional anti-abuse verification. Do not enter a card or upgrade if prompted under this project's constraints. Choose Web Service, Docker, repository root `server`, Dockerfile `Dockerfile`, Free compute, and the frontend's exact CORS origin. No database is needed.

Render free instances sleep after 15 idle minutes and can take about a minute to wake. The available memory is substantially below this project's existing 1.5 GB worker allocation; a Java heap plus LibreOffice can exhaust it. Use concurrency 1 and small synthetic inputs only until measured. Render explicitly advises against free instances for production. Do not promise reliable 50 MB/500-page conversion there.

Sources: [Render first deploy](https://render.com/docs/your-first-deploy), [free instance restrictions](https://render.com/docs/free), [compute pricing](https://render.com/pricing).

**Hugging Face Docker Spaces is not a qualifying free solution for a new account under current rules.** Although CPU Basic has no hourly compute price, the current official documentation requires a paid plan to create Docker/Gradio compute Spaces. Older tutorials recommending it without a subscription are insufficient evidence.

Source: [Spaces overview, creation and hardware rules](https://huggingface.co/docs/hub/spaces-overview).

No verified, unrestricted, always-on, no-card cloud host suitable for this complete worker has been established. The safe zero-subscription path today is local processing and temporary testing; public release needs a separately approved hosting decision or moving more features on-device.
