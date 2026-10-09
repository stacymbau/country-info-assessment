# Deploying to Kubernetes

Works on Windows (PowerShell), macOS and Linux. PowerShell commands are shown first, bash equivalents in the scripts folder.

## What gets deployed

| Object | File | Purpose |
|---|---|---|
| Namespace `country-info` | `k8s/namespace.yaml` | Isolation |
| ConfigMap | `k8s/configmap.yaml` | Non-secret settings: DB URL, SOAP URL and timeouts, JVM flags, Spring profile |
| Secret `country-info-secret` | created by the deploy script | DB username/password. Template only: `k8s/secret.example.yaml` |
| MySQL StatefulSet, PVC, headless Service | `k8s/mysql.yaml` | Demo database with a 5Gi volume. Use a managed DB in production |
| Deployment (2 replicas) | `k8s/deployment.yaml` | Rolling updates, startup/liveness/readiness probes, resource limits, non-root, read-only filesystem, graceful shutdown |
| Service | `k8s/service.yaml` | ClusterIP, port 80 to container port 8080 |
| HorizontalPodAutoscaler | `k8s/hpa.yaml` | 2 to 10 replicas at 70% CPU |

`k8s/kustomization.yaml` ties them together so one command applies everything.

## 1. Prerequisites

* Docker, `kubectl`, and JDK 21 + Maven (only if your Dockerfile copies a prebuilt jar; the Dockerfile in this kit compiles inside Docker)
* A Kubernetes cluster. Pick one:
    * **Docker Desktop:** Settings, Kubernetes, tick *Enable Kubernetes*, wait until it shows running
    * **minikube:** `minikube start`
    * **kind:** `kind create cluster`

Verify:

```powershell
kubectl config get-contexts          # pick one: kubectl config use-context docker-desktop
kubectl get nodes                    # must show Ready
```

Optional: *metrics-server* (the HPA needs it; without it the HPA shows `<unknown>` and the app still works) and an ingress controller (only for the Ingress; you can use port-forward instead).

## 2. Deploy

```powershell
$env:DB_PASSWORD         = 'choose-a-password'
$env:MYSQL_ROOT_PASSWORD = 'choose-another'

.\scripts\deploy.ps1                    # Docker Desktop Kubernetes
.\scripts\deploy.ps1 -Load minikube     # minikube: copies the image into the cluster
.\scripts\deploy.ps1 -Load kind         # kind: same
```

Remote cluster with a registry:

```powershell
.\scripts\deploy.ps1 -Image registry.example.com/team/country-info-service:1.0.0 -Push
```

For a private registry, create an image pull secret and add `imagePullSecrets` to `k8s/deployment.yaml`.

The script: checks cluster access, builds the image, loads or pushes it, creates the namespace and Secret, applies the manifests, points the Deployment at your image, and waits until MySQL and the app are ready. It stops with a clear message at the first failing step.

Linux/macOS/Git Bash: `DB_PASSWORD=... MYSQL_ROOT_PASSWORD=... LOAD=minikube ./scripts/deploy.sh`

## 3. Verify

```powershell
kubectl -n country-info get pods,svc,hpa,pdb
```

Expect `mysql-0` and two `country-info-service-...` pods at `1/1 Running`. The app pods can take 1 to 2 minutes (MySQL start, Flyway, JVM).

Reach the app (leave this window open):

```powershell
kubectl -n country-info port-forward svc/country-info-service 8080:80
```

In a second window:

```powershell
curl.exe http://localhost:8080/actuator/health/readiness     # {"status":"UP"}
.\scripts\smoke-test.ps1                                      # runs every endpoint and prints status codes
```

Expected statuses: readiness 200, first POST 201, repeat POST 200, list/get 200, update 200, empty name 400, Atlantis 404, delete 204, get after delete 404.

Check the data:

```powershell
kubectl -n country-info exec -it mysql-0 -- mysql -ucountryapp -p countrydb
```

(Enter the `DB_PASSWORD` you chose. Then `SHOW TABLES;` should list `country_info`, `languages`, `flyway_schema_history`.)

## 4. Update to a new version

```powershell
.\scripts\deploy.ps1 -Image country-info-service:1.0.1
kubectl -n country-info rollout status deployment/country-info-service
kubectl -n country-info rollout history deployment/country-info-service
kubectl -n country-info rollout undo deployment/country-info-service      # roll back
```

`maxUnavailable: 0` and the readiness probe mean traffic only goes to pods that are ready, so there is no downtime. Database changes ship as new Flyway files (`V2__...sql`), never by editing an applied one, and should be backward compatible so old and new pods can run side by side.

## 5. Scale

```powershell
kubectl -n country-info scale deployment/country-info-service --replicas=4
kubectl -n country-info get hpa -w
```

The HPA takes over again within a few minutes. Each pod holds up to 10 DB connections (`DB_POOL_SIZE`), so 10 pods can open 100. Check MySQL `max_connections` (default 151) before raising the maximum.

## 6. Configuration changes

Edit `k8s/configmap.yaml`, then:

```powershell
kubectl apply -k k8s/
kubectl -n country-info rollout restart deployment/country-info-service
```

Pods read the ConfigMap at start-up, so the restart is required.

## 7. Production hardening checklist

* Managed MySQL with backups and HA: remove `mysql.yaml` from `kustomization.yaml` and set `DB_URL`
* Secrets from a manager (External Secrets, Sealed Secrets, Vault), not from environment variables on a laptop
* TLS on the Ingress; NetworkPolicy limiting egress to MySQL and the SOAP host
* Scrape `/actuator/prometheus`; alert on restarts, 5xx rate, p95 latency, `resilience4j_circuitbreaker_state`, pending Hikari connections
* Pin image tags by digest and scan images in CI

## 8. Remove everything

```powershell
.\scripts\undeploy.ps1        # deletes the namespace, including the MySQL volume (data lost)
```
