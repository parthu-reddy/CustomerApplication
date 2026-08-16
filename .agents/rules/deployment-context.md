# Deployment Environment Context

All services in this Food Delivery system are deployed on an **Oracle Cloud Infrastructure (OCI)** Ampere A1 VM (4 OCPUs, 24 GB RAM, Ubuntu 22.04 ARM) and exposed via a Cloudflare tunnel.

| Resource | Value |
| :---- | :---- |
| **Cloudflare Tunnel Domain** | `https://eng-restricted-dad-separately.trycloudflare.com/` |
| **Oracle VM Public IP** | `140.245.234.137` |
| **SSH Key Path** | `/Users/parthureddy/Documents/OracleSSH/ssh-key-2026-08-16.key` |
| **Remote Code Directory** | `~/Food Delivery.nosync` |
| **Spring Profile** | `dev` (always, unless explicitly overridden) |
| **API Gateway Port** | `8080` (routed through Cloudflare tunnel) |
| **React Frontend Port** | `80` (Nginx, routed through Cloudflare tunnel) |
| **Eureka Dashboard Port** | `8761` |

## Connectivity Rules

- **All HTTP verification** (curl, Schemathesis, Pact provider verification, health checks) MUST target `https://eng-restricted-dad-separately.trycloudflare.com/` as the base URL.
- **NEVER use** `localhost`, `127.0.0.1`, or the raw Oracle IP `140.245.234.137` in any HTTP test commands.
- **Database access** requires SSH into the Oracle VM and using `docker compose exec` from the `~/Food Delivery.nosync/Deployment` directory.

## Useful Commands

```bash
# SSH into Oracle VM
ssh -i /Users/parthureddy/Documents/OracleSSH/ssh-key-2026-08-16.key ubuntu@140.245.234.137

# Execute PostgreSQL query on deployed DB
ssh -i /Users/parthureddy/Documents/OracleSSH/ssh-key-2026-08-16.key ubuntu@140.245.234.137 \
  "cd 'Food Delivery.nosync/Deployment' && docker compose exec -T -e PGPASSWORD=password postgres psql -U postgres -c 'SHOW wal_level;'"

# Check Kafka consumer groups
ssh -i /Users/parthureddy/Documents/OracleSSH/ssh-key-2026-08-16.key ubuntu@140.245.234.137 \
  "cd 'Food Delivery.nosync/Deployment' && docker compose exec -T kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list"

# Check container status
ssh -i /Users/parthureddy/Documents/OracleSSH/ssh-key-2026-08-16.key ubuntu@140.245.234.137 "docker ps --format 'table {{.Names}}\t{{.Status}}'"
```
