# 🚀 Optimización de Docker Builds

## Por qué tarda tanto

El build de Maven tarda ~6-7 minutos principalmente por:

1. **Descargando dependencias Maven** (300+ librerías)
   - Primera ejecución: ~5-6 minutos
   - Con caché reutilizable: ~30 segundos

2. **Compilación de Spring Boot**
   - Procesar anotaciones
   - Generar build-info.properties
   - ~1-2 minutos

## 🎯 Cómo acelerar (3 opciones)

### ❌ **Lo que NO deberías hacer:**
```bash
# LENTO - descarga todas las dependencias de nuevo
docker build --no-cache -t mundia-backend:latest ./backend
```

### ✅ **Opción 1: Build normal (RECOMENDADO para desarrollo)**
```bash
# RÁPIDO - reutiliza caché de dependencias
docker build -t mundia-backend:latest ./backend
docker build -t mundia-frontend:latest ./frontend
```

**Tiempo esperado:**
- Primera vez: ~7 minutos (descarga dependencias)
- Siguientes: ~2-3 minutos (reutiliza caché)
- Si solo cambias código: ~1 minuto

### ✅ **Opción 2: Rebuild sin caché (SOLO para production)**
```bash
# Si REALMENTE necesitas limpiar caché (raro)
docker builder prune --all
docker build -t mundia-backend:latest ./backend
```

### ✅ **Opción 3: Build sin Docker (más rápido en desarrollo)**
```bash
# Backend (si tienes Java + Maven)
cd backend && mvn clean package -DskipTests

# Frontend (si tienes Node)
cd frontend && npm run build
```

---

## 📊 Tiempos esperados

| Escenario | Tiempo | Caché |
|-----------|--------|-------|
| Primera compilación | 7-8 min | ❌ No |
| Cambio en código Java | 2-3 min | ✅ Sí |
| Cambio en dependencias | 6-7 min | ❌ No |
| Build frontend | 30 seg | ✅ Sí |
| Limpio completo | 10-15 min | ❌ No |

---

## 🔧 Configuración avanzada

### Script optimizado para builds rápidos

```powershell
# Build local sin Docker (más rápido)
cd backend
mvn clean package -DskipTests -q
cd ../frontend
npm run build

# O con Docker caché
docker build -t mundia-backend:latest ./backend
docker build -t mundia-frontend:latest ./frontend
```

### Si necesitas compilación muy frecuente

Usa modo desarrollo local:

```bash
# Backend: Hot reload con Spring Boot DevTools
cd backend
mvn spring-boot:run

# Frontend: Dev server con HMR
cd frontend
npm run dev
```

---

## 💡 Tips de optimización

1. **No uses `--no-cache` a menos que realmente lo necesites**
   - Docker maneja caché automáticamente
   - Reutiliza layers (especialmente pom.xml)

2. **Agrupa cambios relacionados**
   - Cambios en Java → rebuild
   - Cambios en pom.xml → refetch dependencias (lento)

3. **Para CI/CD, considera buildkit**
   ```bash
   DOCKER_BUILDKIT=1 docker build -t mundia-backend:latest ./backend
   ```

4. **Monitorea con `docker images --digests`**
   - Ver qué layers se reutilizan

---

## 📋 Checklist de optimización

- [x] .dockerignore en backend
- [x] .dockerignore en frontend
- [ ] Considerar usar BuildKit para CI/CD
- [ ] Separar stage de dependencias (opcional, para máxima velocidad)
