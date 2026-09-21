# multiplayer-hardcore 🏕️

Servidor de Minecraft **Spigot 26.3** enfocado en modo hardcore multiplayer con reinicio automático de mundo. Extraído de `server-minecraft` incluyendo solo lo necesario para el core del servidor y el plugin **HardcoreReset**, sin datos de mundo.

## Requisitos

- **Java 25** (o superior, LTS recomendado)
- **Docker** + Docker Compose (solo si vas a usar el túnel de playit.gg)
- **Gradle** (solo si vas a recompilar el plugin/mod desde el código fuente)

## Puesta en marcha rápida

1. Clona el repo con submódulos:
   ```bash
   git clone --recurse-submodules <url-del-repo>
   ```
   Si ya lo clonaste sin `--recurse-submodules`:
   ```bash
   git submodule update --init --recursive
   ```

2. Acepta la EULA de Minecraft: arranca el servidor una vez (creará `eula.txt`), ábrelo y cambia `eula=false` a `eula=true`.

3. (Opcional) Túnel a internet con playit.gg:
   - Copia `docker-compose.yml.example` a `docker-compose.yml`.
   - Crea una cuenta/agente en [playit.gg](https://playit.gg) y pega tu `SECRET_KEY` en el archivo.
   - **Nunca subas tu `docker-compose.yml` real con la clave** (ya está en `.gitignore`).

4. Arranca todo (Windows):
   ```bat
   start-server.bat
   ```

   O manualmente:
   ```bash
   java -Xmx2G -Xms1G -jar spigot-26.3.jar nogui
   ```

## Estructura del proyecto

- `spigot-26.3.jar` — core del servidor.
- `plugins/HardcoreReset.jar` + `plugins/HardcoreReset/config.yml` — plugin compilado, listo para usar.
- `worldreset-plugin/` — código fuente del plugin Bukkit **HardcoreReset**: gestiona el reinicio de mundo al morir en hardcore. Incluye submódulo con el datapack de corazones custom.
- `worldreset-mod/` — mod complementario relacionado al reinicio de mundo.
- `*.yml` (`server.properties`, `spigot.yml`, `bukkit.yml`, `config.yml`, etc.) — configuración base del servidor.

## Mundos

Las carpetas `world*` y `arena_*` no se versionan ni se incluyen: cada quien genera sus propios mundos al arrancar el servidor.

## Créditos

Datapack de corazones hardcore: submódulo [`hardcore-datapack`](https://github.com/wasantac/hardcore-datapack).
