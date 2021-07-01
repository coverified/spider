# spider
A microservice crawling a set of sites by following links to pages of the relevant domains. New URLs are entered into a GraphQL database.

## Configuration
The following environment variables can be set, many of which fallback to given default values:
- `API_URL` - GraphQL API URL (**required**)
- `AUTH_SECRET` - GraphQL authentication secret (**required**)
- `SCRAPE_PARALLELISM` - number of pages that crawler visits in parallel (default: 100)
- `SCRAPE_INTERVAL` - time interval between page hits (default: 500ms)
- `SCRAPE_TIMEOUT` - timeout of each page load attempt (default: 20s)
- `SHUTDOWN_TIMEOUT` - time after which spider exits, if no new URLs have been found (default: 15s)
- `MAX_RETRIES` - max number of retries after attempts to load a page failed (default: 0)
