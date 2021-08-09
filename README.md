# spider
A microservice to crawl a set of sites by following links to pages of the relevant domains. 
Only the relevant host urls of the provided host(s) are considered. 
New URLs are entered into a GraphQL database.

## Used Frameworks / Libraries
_(not comprehensive, but the most important ones)_

-   [akka](https://akka.io/)
-   [Caliban Client](https://ghostdogpr.github.io/caliban/) to talk to GraphQL endpoint
-   [Sentry](https://sentry.io/welcome/) (error reporting)


## Configuration
Configuration is done using environment variables. 
The following configuration parameters are available.

Environment config values:
- `API_URL` - GraphQL API URL (**required**)
- `AUTH_SECRET` - GraphQL authentication secret (**required**)
- `SCRAPE_PARALLELISM` - number of pages that crawler visits in parallel (default: 100)
- `SCRAPE_INTERVAL` - time interval between page hits (default: 500ms)
- `SCRAPE_TIMEOUT` - timeout of each page load attempt (default: 20s)
- `SHUTDOWN_TIMEOUT` - time after which spider exits, if no new URLs have been found (default: 15s)
- `MAX_RETRIES` - max number of retries after attempts to load a page failed (default: 0)
