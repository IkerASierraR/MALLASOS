# Convenciones del repositorio (propuesta para T0.3)

> Propuesta: el equipo debe aprobarla o cambiarla.

## Ramas

- `main` guarda solo lo que compila y pasa las pruebas.
- El trabajo se hace en ramas cortas desde `main`: `feat/<modulo>`, `fix/<tema>` o `docs/<tema>`.
- Cada rama se borra después de fusionarla.

## Pull requests

- Un PR por tarea, con su ID en el título, por ejemplo `T2.15`.
- Se fusiona con una revisión de otra persona y con estas pruebas en verde:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

## Commits

Formato `tipo(ámbito): resumen en español (T0.0)`. Tipos usados: `feat`, `fix`, `docs`, `test`, `build` y `chore`. Sin `Co-Authored-By` ni atribución a IA.

## Versiones

- Versiones de prueba: etiqueta `v1.0-pruebaN`, publicada como prerelease.
- Entrega final: etiqueta `v1.0` (T4.1).
- La clave de firma nunca va al repositorio.
