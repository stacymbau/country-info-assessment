# Troubleshooting on Kubernetes

Shortcut for PowerShell so commands stay short:

```powershell
function k { kubectl -n country-info @args }
```

Then `k get pods` means `kubectl -n country-info get pods`. (Bash: `alias k='kubectl -n country-info'`.)

## 1. Triage in one minute

```powershell
k get pods -o wide                                  # status, restarts, node
k get events --sort-by=.lastTimestamp               # newest events last
k describe pod <pod-name>                           # probe failures, OOMKilled, image errors
k logs deploy/country-info-service --tail=100
k logs <pod-name> --previous                        # logs from the container that just crashed
```

Read pod status first:

| Status | Meaning | Go to |
|---|---|---|
| `ImagePullBackOff` / `ErrImagePull` | Cluster cannot get the image | table below |
| `Pending` | Not scheduled | table below |
| `Init:0/1` | Waiting for MySQL | table below |
| `CrashLoopBackOff` | App starts and dies | `k logs <pod> --previous`, section 2 |
| `Running` but `0/1` | Readiness probe failing | table below |

## 2. Symptom table

| Symptom | Likely cause | Check and fix |
|---|---|---|
| `ImagePullBackOff` on a local cluster | Image exists only in your Docker, not in the cluster | minikube: `-Load minikube`. kind: `-Load kind`. Docker Desktop: use the kubeadm cluster type, or push to a registry. Name must match: `k get deploy country-info-service -o jsonpath="{.spec.template.spec.containers[0].image}"` |
| `ImagePullBackOff` on a remote cluster | Not pushed, wrong tag, private registry | `docker push`, check `k describe pod`, add `imagePullSecrets` |
| `Pending` | No capacity, or PVC unbound | `k describe pod` Events; `k get pvc`; `kubectl get storageclass` |
| `Init:0/1` stuck | MySQL not reachable | `k logs <pod> -c wait-for-mysql`; `k get pods -l app=mysql`; `k logs mysql-0` |
| `CrashLoopBackOff`: `Communications link failure` | Wrong DB host/port, or MySQL not ready | Check `DB_URL` in ConfigMap (`k get cm country-info-config -o yaml`); service name must be `mysql` |
| `CrashLoopBackOff`: `Access denied for user` | Credentials differ from what MySQL was created with. MySQL only reads its password the first time the volume is created | Dev: `k delete pvc data-mysql-0` after deleting the StatefulSet pod, or `.\scripts\undeploy.ps1` and redeploy with the right password |
| `CrashLoopBackOff`: `Found non-empty schema(s) ... but no schema history table` | Database already had tables, created outside Flyway | Dev: reset the volume as above. Or set `spring.flyway.baseline-on-migrate=true` if the tables match V1 |
| `CrashLoopBackOff`: `Schema-validation: missing table` | Flyway did not run (no migration file, or Flyway dependency missing) | `k logs <pod> \| Select-String flyway`; check `db/migration/V1__*.sql` is in the jar |
| `CrashLoopBackOff`: `No qualifying bean ... CacheManager` or `RestClient$Builder` | Image built from an old or incomplete jar | Rebuild with `docker build --no-cache`; use the multi-stage Dockerfile so the jar is always rebuilt |
| `OOMKilled` (exit 137) | Heap plus overhead above the memory limit | `k describe pod`; raise `limits.memory` in `deployment.yaml`, or lower `-XX:MaxRAMPercentage` in the ConfigMap `JAVA_OPTS` |
| `Running` `0/1`, readiness failing | DB unreachable or pool exhausted | `k port-forward <pod> 8081:8080`, then `curl.exe localhost:8081/actuator/health/readiness` |
| Restarts: `Liveness probe failed` | JVM hung, CPU throttled, or start slower than the startup probe allows | `k describe pod`; raise the startup probe `failureThreshold` |
| POST returns **503** | SOAP provider down or slow, or circuit open | Section 4 |
| POST returns **404** | Provider does not know that country name | Test the exact name in SoapUI |
| **400** | Validation failure | Read the `details` array in the response |
| **500** | Unexpected bug | Take the `correlationId` from the response, section 3 |
| HPA shows `<unknown>/70%` | metrics-server not installed | `k top pods`; install metrics-server |
| Ingress 404 or 502 | No controller, wrong host, no endpoints | `k describe ingress`; `k get endpoints country-info-service` must list pod IPs. Easiest while testing: use port-forward |
| Rollout stuck | New pods never become ready | `k rollout status deploy/country-info-service`, then `k rollout undo deploy/country-info-service` |
| Port-forward refuses or drops | Pod restarted, or port in use | Re-run it; use another local port `8081:80` |

## 3. Follow one request through the logs

Every response carries an `X-Correlation-Id` header and error bodies include `correlationId`.

```powershell
curl.exe -i http://localhost:8080/api/v1/countries/9999        # note the correlationId in the body
k logs -l app=country-info-service --tail=-1 --prefix | Select-String "<correlationId>"
```

You should see the access line (`method path status durationMs`), any SOAP calls (`operation`, `durationMs`) and business events (`Country stored ...`) for that one request. In Kubernetes the logs are JSON (profile `k8s`), so they are searchable in any log platform.

Raise the log level without rebuilding:

```powershell
k set env deploy/country-info-service LOGGING_LEVEL_COM_ASSESSMENT=DEBUG
k set env deploy/country-info-service LOGGING_LEVEL_COM_ASSESSMENT-      # remove it again
```

## 4. SOAP provider problems

Can a pod reach the provider at all?

```powershell
k exec deploy/country-info-service -- sh -c "wget -qO- --timeout=5 'http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL' | head -c 200"
```

If that fails, check cluster egress and DNS (`k exec deploy/country-info-service -- nslookup webservices.oorsprong.org`) and any NetworkPolicy. Also open the WSDL URL in a browser: the provider itself can be down.

Circuit breaker and degraded-mode metrics (through a port-forward to one pod):

```powershell
k port-forward deploy/country-info-service 8081:8080
curl.exe -s http://localhost:8081/actuator/prometheus | Select-String "resilience4j_circuitbreaker_state","country_onboard_total","resilience4j_retry_calls"
```

* `resilience4j_circuitbreaker_state{state="open"} 1`: the breaker is open. Calls fail fast for 30 seconds, then trial calls decide. It closes by itself when the provider recovers.
* `country_onboard_total{outcome="degraded"}` rising: the provider is down but stored countries are still served from MySQL.
* Cache hit ratio: `curl.exe "http://localhost:8081/actuator/metrics/cache.gets?tag=result:hit"`.

To change timeouts, edit `SOAP_READ_TIMEOUT` in the ConfigMap, apply, and restart the deployment.

## 5. Database checks

```powershell
k exec -it mysql-0 -- mysql -ucountryapp -p countrydb
```

```sql
SHOW TABLES;
SELECT * FROM flyway_schema_history;
SELECT COUNT(*) FROM country_info;
SHOW PROCESSLIST;       -- connection pressure
```

Pool metrics: `hikaricp_connections_active` and `hikaricp_connections_pending`. Pending above zero for long means the pool is too small or the database is slow.

## 6. Common operations

```powershell
k rollout restart deploy/country-info-service
k scale deploy/country-info-service --replicas=3
k rollout undo deploy/country-info-service
k exec -it <pod-name> -- sh                    # image is read-only except /tmp
k top pods                                     # needs metrics-server
```

Decode a secret value (never paste it in a ticket):

```powershell
$v = k get secret country-info-secret -o jsonpath="{.data.DB_USERNAME}"
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($v))
```

## 7. When asking for help, include

`k get pods -o wide`, `k describe pod <pod>`, `k logs <pod> --previous`, the `correlationId`, the image tag, and `k get cm country-info-config -o yaml`. Never include the Secret.
