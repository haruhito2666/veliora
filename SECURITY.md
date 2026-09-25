# Security Policy

## Current deployment model

Veliora currently runs in **authless local mode**. The Android app, REST API, and WebSocket streams do not require accounts, bearer tokens, or user ownership checks. Anyone who can reach the gateway can read, create, modify, and cancel data in every organization on that instance.

This is acceptable only for a trusted single-user machine, localhost, or a private network that you control. Do not expose the gateway directly to the public internet, an untrusted LAN, or a multi-user environment.

## Reporting a vulnerability

Please do not open a public issue for a security vulnerability. Contact the maintainers privately through the repository hosting provider's security advisory channel and include:

- affected component and version/commit;
- reproduction steps;
- impact and required attacker access;
- any logs or proof-of-concept output with secrets removed.

We will acknowledge reports when possible and coordinate a fix and disclosure timeline. Do not test against other people's instances or send unsolicited messages through Caspian channels.

## Operator checklist

- Bind the gateway to localhost or a private interface.
- Use a firewall or reverse proxy when other devices must connect.
- Keep PostgreSQL, NATS, Redis, and the API on a private network.
- Use environment secrets from a secret manager; never commit `.env` files.
- Rotate LLM, SMTP/Resend, and Telegram credentials after suspected exposure.
- Back up PostgreSQL before applying migrations; migration `002_remove_auth.sql` permanently removes account/session tables.
- Review outbound Caspian recipients and content before enabling credentials.

## Reporting non-security bugs

Use the normal issue tracker for functionality, documentation, and compatibility problems. See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution workflow.
