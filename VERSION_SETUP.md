# 📦 Sistema Automático de Versionado - Mundia

## Resumen de cambios implementados

### Backend (Spring Boot)
✅ Nuevo endpoint `/api/version` que devuelve:
- Versión actual (desde `pom.xml`)
- Timestamp de build
- Rama de git
- Commit ID

### Frontend (Vue3)
✅ Carga automática de versión al iniciar
✅ Muestra versión en el login (pie de página)
✅ Fallback a "(offline)" si no puede conectar

### Scripts
✅ Script PowerShell para automatizar todo el proceso de versionado

---

## 🚀 Cómo usar

### Opción 1: Script automático (RECOMENDADO)

```powershell
# Navega a la carpeta del proyecto
cd C:\Users\PC\VicAI\mundia

# Ejecuta el script
.\version.ps1 -Version "1.0.1"

# O sin parámetro (te lo pide interactivamente)
.\version.ps1
```

**Qué hace el script:**
1. Valida el formato semver (1.0.1)
2. Actualiza `backend/pom.xml` con la versión
3. Actualiza `frontend/package.json` con la versión
4. Hace commit: `chore: version bump to 1.0.1`
5. Crea tag: `v1.0.1`
6. Hace push a origin (commits + tag)

### Opción 2: Manual (si prefieres control total)

```bash
# 1. Actualizar versión en pom.xml
# Cambiar: <version>0.0.1-SNAPSHOT</version>
# Por:     <version>1.0.1</version>

# 2. Actualizar versión en package.json
# Cambiar: "version": "0.0.0",
# Por:     "version": "1.0.1",

# 3. Commitear cambios
git add backend/pom.xml frontend/package.json
git commit -m "chore: version bump to 1.0.1"

# 4. Crear tag
git tag -a v1.0.1 -m "Release version 1.0.1"

# 5. Pushear
git push
git push origin v1.0.1
```

---

## 📡 Cómo funciona

### Flujo de versionado automático

```
Tu máquina
    ↓
1. Ejecutas script/actualizas versión
    ↓
2. pom.xml + package.json actualizados
    ↓
3. Git tag creado (v1.0.1)
    ↓
4. Push a GitHub
    ↓
COMPILACIÓN (en tu máquina o CI/CD)
    ↓
5. Maven genera META-INF/build-info.properties con versión
    ↓
6. Aplicación arranca
    ↓
USUARIO EN NAVEGADOR
    ↓
7. Frontend llama a GET /api/version
    ↓
8. Backend lee versión desde build-info.properties
    ↓
9. Versión aparece en login
```

### Endpoint API

**GET `/api/version`**

Response:
```json
{
  "version": "1.0.1",
  "buildTime": "2026-06-11T14:30:45Z",
  "branch": "master",
  "commit": "abc123def456"
}
```

---

## ⚙️ Configuración técnica

### Backend (pom.xml)
- Maven plugin `spring-boot-maven-plugin` genera `build-info.properties`
- Spring Boot Actuator expone la información via `BuildProperties`
- Endpoint en `com.mundia.backend.version.VersionController`

### Frontend (App.vue)
- Variable reactiva: `appVersion`
- Carga en `onMounted()`
- Mostrada en login con formato: `v1.0.1`

---

## ✅ Checklist antes de ejecutar

Antes de hacer un nuevo versionado, verifica:

- [ ] Todos los cambios están committeados
- [ ] La rama `master` está limpia (`git status` sin cambios)
- [ ] Backend compila correctamente (`mvn clean package` localmente)
- [ ] Frontend no tiene errores (`npm run build`)
- [ ] Decidiste la nueva versión (semver: MAJOR.MINOR.PATCH)

---

## 🔄 Próximas mejoras (opcional)

Puedes agregar:
- CI/CD para compilar y pushear automáticamente después de un tag
- Changelog automático desde commits
- GitHub Releases automáticas con notas
- Verificación de versión duplicada en tags

---

## 🐛 Troubleshooting

### "Error: pom.xml no actualizado"
→ Verifica que `backend/pom.xml` existe en la raíz correcta

### "Error: push fallido"
→ Verifica autenticación con GitHub (`git config --list`)

### "Versión no aparece en el login"
→ Verifica que `/api/version` responde: `curl http://localhost:8080/api/version`

### "Script no se ejecuta en PowerShell"
→ Ejecuta: `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`
