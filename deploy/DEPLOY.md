# Despliegue de Mundia en IONOS

URL producción: **https://mundial.vicai.es**

---

## ⚠️ Variables importantes del frontend

El frontend se compila con Vite — las variables de entorno se **hornean** en el build y NO se pueden cambiar en runtime. Siempre hay que pasarlas como `--build-arg` al construir la imagen:

| Variable | Valor |
|---|---|
| `VITE_GOOGLE_CLIENT_ID` | `263536861475-tq28mv2gim01c1t509itv8ki7aug6l3n.apps.googleusercontent.com` |
| `VITE_API_BASE_URL` | `/api` |

**Si se olvidan, Google Login dará error "client_id not set".**

> ⚠️ **Git Bash en Windows** convierte `/api` a una ruta local (`C:/Program Files/Git/api`).
> Usar siempre **PowerShell** para el build del frontend, o anteponer `MSYS_NO_PATHCONV=1` en Git Bash.

---

## Estructura en el servidor

```
/home/vmhuecas/conf/
  ├── nginx.conf                        ← proxy nginx (contiene bloque mundial.vicai.es)
  ├── docker-compose.mundial.yml        ← orquestación de contenedores
  ├── mundia.env                        ← variables de entorno (secrets)
  └── certs/live/mundial.vicai.es/     ← certificados SSL
        ├── fullchain.pem
        └── privkey.pem

/home/vmhuecas/img/
  ├── mundia-frontend.tar
  └── mundia-backend.tar
```

### Puertos en producción

| Servicio  | Puerto host |
|-----------|-------------|
| Frontend  | 5010        |
| Backend   | 8086        |
| MariaDB   | 3310        |

---

## Rebuild y redeploy completo

### 1. En local — compilar imágenes

> ⚠️ Usar siempre `--no-cache` en backend para que Maven recompile los cambios Java.

```bash
cd C:\Users\PC\VicAI\mundia

docker build --no-cache -t mundia-backend:latest ./backend

docker build -t mundia-frontend:latest \
  --build-arg VITE_GOOGLE_CLIENT_ID=263536861475-tq28mv2gim01c1t509itv8ki7aug6l3n.apps.googleusercontent.com \
  --build-arg VITE_API_BASE_URL=/api \
  ./frontend

docker save mundia-backend:latest -o mundia-backend.tar
docker save mundia-frontend:latest -o mundia-frontend.tar
```

> ℹ️ Al arrancar el backend en pro, **Flyway aplicará automáticamente las migraciones pendientes** antes de que Spring Boot arranque. No hace falta ejecutar SQL manualmente.

### 2. Subir por FTP

Subir los `.tar` a `/home/vmhuecas/img/`

### 3. En el servidor — cargar y relanzar

```bash
docker load -i /home/vmhuecas/img/mundia-backend.tar
docker load -i /home/vmhuecas/img/mundia-frontend.tar

docker compose -f /home/vmhuecas/conf/docker-compose.mundial.yml --env-file /home/vmhuecas/conf/mundia.env up -d
```

---

## Actualizar solo un servicio

### Solo backend (sin tocar BD ni frontend)

```bash
# En local
docker build -t mundia-backend:latest ./backend
docker save mundia-backend:latest -o mundia-backend.tar

# Subir por FTP y en el servidor:
docker load -i /home/vmhuecas/img/mundia-backend.tar
docker compose -f /home/vmhuecas/conf/docker-compose.mundial.yml --env-file /home/vmhuecas/conf/mundia.env up -d --no-deps backend
```

### Solo frontend

```bash
# En local — SIEMPRE con los --build-arg
docker build -t mundia-frontend:latest \
  --build-arg VITE_GOOGLE_CLIENT_ID=263536861475-tq28mv2gim01c1t509itv8ki7aug6l3n.apps.googleusercontent.com \
  --build-arg VITE_API_BASE_URL=/api \
  ./frontend
docker save mundia-frontend:latest -o mundia-frontend.tar

# Subir por FTP y en el servidor:
docker load -i /home/vmhuecas/img/mundia-frontend.tar
docker compose -f /home/vmhuecas/conf/docker-compose.mundial.yml --env-file /home/vmhuecas/conf/mundia.env up -d --no-deps frontend
```

---

## mundia.env — variables del servidor

Ubicación: `/home/vmhuecas/conf/mundia.env`

```env
MARIADB_DATABASE=mundia
MARIADB_USER=mundia
MARIADB_PASSWORD=...
MARIADB_ROOT_PASSWORD=...
JWT_SECRET=...
GOOGLE_CLIENT_ID=263536861475-tq28mv2gim01c1t509itv8ki7aug6l3n.apps.googleusercontent.com
MUNDIA_ACCESS_CODE_ENABLED=true
MUNDIA_ACCESS_CODE=...
API_FOOTBALL_KEY=115f68fb7b701eda0e2ed589346f07e8
API_FOOTBALL_MODE=direct
API_FOOTBALL_SEASON=2026
API_FOOTBALL_LEAGUE=1
OPENAI_API_KEY=...
```

---

## Activar / desactivar código de acceso

Editar `/home/vmhuecas/conf/mundia.env`:

```
MUNDIA_ACCESS_CODE_ENABLED=true   # false para desactivarlo
MUNDIA_ACCESS_CODE=tu_clave_secreta
```

Aplicar sin tocar la BD:

```bash
docker compose -f /home/vmhuecas/conf/docker-compose.mundial.yml --env-file /home/vmhuecas/conf/mundia.env up -d --no-deps backend
```

---

## Renovar certificado SSL

> Caduca el **2026-09-02**

```bash
# 1. Solicitar renovación (pide añadir registro TXT en DNS de IONOS)
certbot certonly --manual --preferred-challenges dns -d mundial.vicai.es

# 2. Copiar los nuevos certificados
cp /etc/letsencrypt/live/mundial.vicai.es/fullchain.pem /home/vmhuecas/conf/certs/live/mundial.vicai.es/
cp /etc/letsencrypt/live/mundial.vicai.es/privkey.pem /home/vmhuecas/conf/certs/live/mundial.vicai.es/

# 3. Recargar nginx
docker exec proxy-familiahuecas nginx -s reload
```

---

## Comandos útiles en el servidor

```bash
# Ver estado de los contenedores
docker ps | grep mundia

# Ver logs del backend en tiempo real
docker logs -f mundia-backend

# Ver logs del frontend
docker logs -f mundia-frontend

# Parar todo (SIN borrar datos)
docker compose -f /home/vmhuecas/conf/docker-compose.mundial.yml down

# ⚠️ NUNCA usar down -v (borra los datos de la BD)
```

---

## Base de datos

Los datos persisten en el volumen Docker `mundia-prod_mariadb_prod_data` aunque se reinicien los contenedores. Solo se perderían con `docker compose down -v` — **nunca usar en producción**.

Tras el primer despliegue o si la BD está vacía, ir al panel **Admin → Sync** para cargar equipos, grupos y partidos desde la API deportiva.
