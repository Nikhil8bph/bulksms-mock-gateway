# BulkSMS mock gateway

This repository provides a local testing sandbox that mocks selected BulkSMS API behavior. It is for development and testing purposes only and does not claim to be, replace, or serve as an alternative to BulkSMS or its services. For official product information, service requirements, and support, visit [BulkSMS.com](https://www.bulksms.com/).

## License

This project is released under the [MIT License](LICENSE). If you have a licensing concern or objection, contact [nikhil8.jsg@gmail.com](mailto:nikhil8.jsg@gmail.com).

Point the application under test at `http://localhost:8080/v1` and supply the sandbox API token ID and secret using HTTP Basic authentication. The gateway runs on loopback by default. It sends the SMS body and addressing details by Gmail SMTP to the configured inbox.

## Run locally

Use Java 21 or newer. In IntelliJ IDEA, select **BulkSMS Sandbox** from the run configuration menu and run or debug it. The shared configuration uses the EnvFile plugin to load the project-root `.env`. If you have a fresh checkout, copy [`.env.example`](.env.example) to `.env` first. Fill in the required values and keep the local `.env` private. Git ignores it.

| Variable | Value |
| --- | --- |
| `DB_URL` | MySQL JDBC URL, for example `jdbc:mysql://db.example.com:3306/sandbox?sslMode=REQUIRED`. |
| `DB_USERNAME` | MySQL username. |
| `DB_PASSWORD` | Password for the MySQL user. |
| `BULKSMS_TOKEN_ID` | A token ID you choose for this sandbox. |
| `BULKSMS_TOKEN_SECRET` | A secret you choose for the sandbox token. |
| `SMS_EMAIL_FROM` | Gmail or Google Workspace address. It is also the SMTP username. |
| `SMS_EMAIL_TO` | The inbox that receives all SMS test emails. |
| `GMAIL_APP_PASSWORD` | An app password for the Gmail account, without display spaces. [Google app password setup](https://support.google.com/accounts/answer/185833) |

Optional settings include `PORT`, `SANDBOX_BIND_ADDRESS` for direct local launches, `SANDBOX_PUBLIC_BASE_URL`, and `SANDBOX_WEBHOOK_ORIGINS`. Flyway creates the tables and seeds the sandbox accounts on startup. The database user needs schema creation and read/write permissions.

To start the gateway outside IntelliJ, set the same variables in PowerShell before running Maven.

```powershell
.\mvnw.cmd spring-boot:run
```

The gateway includes a sandbox OpenAPI definition. Open Swagger UI at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html). Choose **Authorize** and enter your `BULKSMS_TOKEN_ID` and `BULKSMS_TOKEN_SECRET` as the username and password. **Try it out** sends requests to the local gateway. The OpenAPI JSON is available at `/v3/api-docs`.

Submit and retrieve an SMS using the same authentication format as BulkSMS.

```powershell
$sandboxToken = "${env:BULKSMS_TOKEN_ID}:${env:BULKSMS_TOKEN_SECRET}"
$submission = @{ to = '+447700900001'; body = 'Sandbox SMS test' } | ConvertTo-Json -Compress
$submission | curl.exe --user $sandboxToken --header "Content-Type: application/json" --data-binary '@-' "http://localhost:8080/v1/messages?deduplication-id=123"
curl.exe --user $sandboxToken "http://localhost:8080/v1/messages"
```

## Run with Docker

Build the image from the project root. Swagger is included.

```powershell
docker build -t bulksms-mock-gateway .
docker run --rm --env-file .env -e PORT=8080 -p 127.0.0.1:8080:8080 bulksms-mock-gateway
```

Docker reads `.env` at runtime. Use plain `KEY=value` lines. The image sets Spring's `SERVER_ADDRESS` to `0.0.0.0`. The container connects to the configured MySQL server and Gmail SMTP.

## Deploy on Render

Push the project to a Git repository connected to Render. In the Render Dashboard, create a **Blueprint** from that repository. [`render.yaml`](render.yaml) creates a Docker web service, binds the gateway to `0.0.0.0` on Render's `PORT`, and uses `/healthz` to check that the sandbox database is reachable. [Render Blueprint setup](https://render.com/docs/infrastructure-as-code)

Enter `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `BULKSMS_TOKEN_ID`, `BULKSMS_TOKEN_SECRET`, `SMS_EMAIL_FROM`, `SMS_EMAIL_TO`, and `GMAIL_APP_PASSWORD` when Render prompts. For an existing service, add them under **Environment**. Render passes them to Spring as runtime environment variables. [Render environment variables](https://render.com/docs/configure-environment-variables)

The Blueprint uses the paid 512 MB plan because free Render web services block outbound SMTP on port 587. If your database restricts inbound IPs, allow the outbound CIDR ranges listed under **Connect → Outbound** on the Render service. [SMTP restrictions](https://render.com/docs/free) · [Render outbound IPs](https://render.com/docs/outbound-ip-addresses)

After deployment, use the service's HTTPS URL with `/v1` for API requests. Attachment links use `RENDER_EXTERNAL_URL`; set `SANDBOX_PUBLIC_BASE_URL` if you want them to use a custom domain. [Render default environment variables](https://render.com/docs/environment-variables)

Open `/swagger-ui.html` on the Render service to use the bundled definition. To use a custom definition, add it as a Render **Secret File** named `render-openapi.yaml`, set `SANDBOX_OPENAPI_SPEC` to `file:/etc/secrets/render-openapi.yaml`, and redeploy. The Swagger page serves the selected definition publicly and API requests still require the sandbox token. [Runtime secret files on Render](https://render.com/docs/docker-secrets#accessing-secret-files-at-runtime)

## Sandbox behavior

- Submissions support JSON arrays, form posts, and the simple GET endpoint. A successful submission persists the message before SMTP delivery. Pending messages appear as `UNKNOWN` in retrieval responses. `DELIVERED` means Gmail accepted the email. SMTP failures are retried and eventually become `FAILED.NOT_SENT`. An interruption after SMTP acceptance can result in a duplicate email.
- Blocked recipients produce `FAILED.BLOCKED` with a simulated credit charge. Scheduled messages appear in message retrieval once due. A repeated deduplication ID returns the original response for 12 hours.
- The sandbox starts with 100,000 simulated credits and a 50,000-message UTC daily quota. Test successful transfers with `{"toUsername":"sandbox-recipient","toUserId":2,"credits":10}`.
- Active webhook registrations are validated with an empty JSON array. Callback jobs use the BulkSMS event headers. Rejected batches are retried one message at a time, and changing a webhook's scope cancels its queued callbacks. Loopback callback URLs are enabled by default. Use a comma-separated list of explicit origins such as `https://example.test:8443` in `SANDBOX_WEBHOOK_ORIGINS` for other test receivers. The optional webhook contact email is stored for API parity.
- To test `RECEIVED` callbacks and related messages, submit `{"from":"+447700900001","to":"+447700900000","body":"Test reply"}` to the authenticated sandbox endpoint `POST /sandbox/inbound-messages`. Replies link to the latest delivered, repliable message addressed to the sender. Send with `"from":{"type":"REPLIABLE"}` to use the sandbox reply number `+447700900000`.
- Attachment pre-signing returns local upload and download URLs backed by MySQL. Uploads support up to 20 MiB and expire 30 days after pre-signing. Set `SANDBOX_PUBLIC_BASE_URL` if the client needs a different origin to access those URLs.
- To test phonebook features, seed `sms_contact (phone_number, first_name, surname)` and `sms_group_member (group_id, group_name, phone_number)` after migration. Store phone numbers as international digits without the leading plus.

Run the contract integration tests with `.\mvnw.cmd test`. They use H2 in MySQL mode and mock external delivery clients.
