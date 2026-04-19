# PlaygroundApps

Recipe Collection app — Android client + Node.js backend.

## Infrastructure

| Service | Provider | Region | Dashboard |
|---------|----------|--------|-----------|
| Backend server | [Render](https://render.com) (free tier) | EU Central (Frankfurt) | [Dashboard](https://dashboard.render.com/web/srv-d6vsubdm5p6s73ahnco0) |
| Database | [Neon](https://neon.tech) Postgres (free tier) | AWS EU Central (Frankfurt) | [Console](https://console.neon.tech) |
| Image storage | [Cloudinary](https://cloudinary.com) (free tier) | Auto | [Console](https://console.cloudinary.com) |

### Render environment variables

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | Neon Postgres connection string |
| `CLOUDINARY_URL` | Cloudinary connection string (API key + secret + cloud name) |
| `GEMINI_API_KEY` | Google Gemini API key for recipe parsing and translation |
| `PORT` | Server port (set by Render) |

## Links

- [Render Dashboard (Server)](https://dashboard.render.com/web/srv-d6vsubdm5p6s73ahnco0)
- [Neon Console (Database)](https://console.neon.tech)
- [Cloudinary Console (Images)](https://console.cloudinary.com)
- [APK Releases](https://github.com/DvirSh/PlaygroundApps/releases/)
