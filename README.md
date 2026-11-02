# TaskGraph

Plugin de Paper para organizar las tareas comunales de un servidor de Minecraft. Las tareas forman un grafo de dependencias que se muestra en la pantalla de logros (advancements) vanilla, así que los jugadores no necesitan mods en el cliente.

> Última versión: **0.2.1**. En desarrollo: 0.3.0, con un grafo más ordenado (bloques y líneas múltiples) e íconos elegibles.

## Cómo se ve

- Cada tarea es un logro en la pestaña "Tareas del servidor".
- Las tareas sin dependencias cuelgan del logro raíz. La primera dependencia de cada tarea es la línea que la une con su padre; las demás aparecen en la descripción como "También requiere: ...".
- Las columnas indican la profundidad: todo lo que una tarea requiere queda a su izquierda.
- El progreso es comunal: una tarea resuelta aparece completada para todos los jugadores, incluidos los que entran después.
- Las tareas se manejan desde una ventana del juego (`/tareas`, la tecla G o el menú de pausa) o con los comandos `/tarea`. Ver [Interfaz](#interfaz).

## Requisitos

- Servidor [Paper](https://papermc.io/) para Minecraft Java 26.3
- Java 25
- Plugin [UltimateAdvancementAPI](https://modrinth.com/plugin/ultimateadvancementapi/versions) **3.0.0-beta-4** instalado en el servidor. En Modrinth está marcada como *beta*: la versión que aparece primero (2.x) **no sirve**.

## Compilar

```bash
./gradlew build
```

En Windows: `gradlew.bat build`. El jar queda en `build/libs/`.

## Instalar en el servidor

1. Descargar `UltimateAdvancementAPI-Plugin-3.0.0-beta-4.jar` desde Modrinth y copiarlo a la carpeta `plugins/` del servidor.
2. Copiar el jar de `build/libs/` a la misma carpeta `plugins/`.
3. Reiniciar el servidor.

### Si el plugin no arranca

| Mensaje en el log | Qué pasa | Solución |
|---|---|---|
| `Unknown/missing dependency plugins: [UltimateAdvancementAPI]` | Falta UltimateAdvancementAPI | Instalar la 3.0.0-beta-4 y reiniciar |
| `TaskGraph needs UltimateAdvancementAPI 3.0.0-beta-4, but 2.8.2 is installed` | Está instalada otra versión, en general la 2.x | Reemplazar el jar por el de la 3.0.0-beta-4 (no el "Mojang-Mapped-Legacy") y reiniciar |
| `Could not load .../tasks.json, disabling TaskGraph` | El archivo de tareas está dañado | Ver [Datos](#datos) |

## Prueba local

> **Solo para pruebas locales.** El servidor de prueba corre con `online-mode=false` (cualquiera puede entrar con cualquier nombre, sin verificar la cuenta) y escuchando solo en `127.0.0.1`. No uses esta configuración en un servidor público.

El proyecto trae un servidor de prueba con [run-paper](https://github.com/jpenilla/run-task). La tarea `runServer`:
- descarga Paper 26.3 build 222, el mismo build contra el que compila el plugin;
- descarga UltimateAdvancementAPI 3.0.0-beta-4 desde Modrinth;
- compila el plugin y lo instala;
- antes de arrancar, copia `dev-server/server.properties` a `run/`. Solo copia lo que falte en `run/`, así que tus cambios locales se conservan. Para volver a la configuración original, borrá `run/server.properties`.

El servidor vive en `run/`, que está en `.gitignore`.

### Levantar el servidor

1. `./gradlew runServer` (en Windows: `gradlew.bat runServer`).
2. La primera vez el servidor se detiene y pide aceptar la EULA de Minecraft. Leela en https://aka.ms/MinecraftEULA y, si estás de acuerdo, cambiá `eula=false` por `eula=true` en `run/eula.txt`. Después volvé a correr `./gradlew runServer`.
3. Entrá desde el cliente a `localhost`. Para probar con dos jugadores hacen falta dos clientes con **nombres distintos**: si usan el mismo nombre, el segundo echa al primero.
4. Para apagarlo, escribí `stop` en la consola.

El mundo de prueba es plano, en modo creativo y pacífico. Así es fácil agarrar cualquier ítem para usarlo de ícono.

### Checklist de prueba de humo

Jugadores: **A** y **B**.

- [ ] **La pestaña se ve y es legible.** A crea "Madera", "Piedra", "Mesa de trabajo" (depende de "Madera"), "Horno" (depende de "Piedra" y después de "Madera") y "Pico" (depende de "Mesa de trabajo" y "Piedra"). Por ejemplo: `/tarea crear Mesa de trabajo` y `/tarea depende "Mesa de trabajo" Madera`. La pestaña "Tareas del servidor" muestra el raíz a la izquierda, cada tarea a la derecha de sus dependencias, y ningún ícono encima de otro.
- [ ] **Tildes y colores.** Crear una tarea con título y descripción con tildes y ñ, por ejemplo `/tarea crear Jardín de la ñandú` y `/tarea desc "Jardín de la ñandú" Regar todos los días`. En la pestaña se ven bien, con el título en blanco, la descripción en gris, "Asignada a" en celeste y "También requiere" en amarillo.
- [ ] **Sin toasts ni anuncios al entrar o reconstruir.** Con tareas ya resueltas, B sale y vuelve a entrar: no aparece ningún toast ni mensaje de logro en el chat. Lo mismo cuando A edita cualquier tarea (`desc`, `asignar`) y la pestaña se reconstruye.
- [ ] **`/tarea resolver` anuncia una sola vez.** A resuelve una tarea: A y B ven un único mensaje "✔ A resolvió la tarea ...", sin el anuncio de logro vanilla, y la tarea aparece completada para los dos.
- [ ] **Revocación con jugador offline.** A resuelve "Jardín" (sin dependencias) y B la ve completada. B sale. A la borra con `/tarea borrar Jardín` y la vuelve a crear con `/tarea crear Jardín`. B entra: "Jardín" aparece **pendiente**, no completada.
- [ ] **Quitar dependencias.** Con "Horno" dependiendo de "Piedra" y "Madera": `/tarea quitardep Horno Piedra` mueve "Horno" a colgar de "Madera" en la pestaña, y `/tarea quitardep Horno Madera` lo deja colgando del raíz. Tab en el segundo argumento sugiere solo las dependencias de "Horno".
- [ ] **Permisos.** En la consola, `op A` (B queda sin op). B crea "Silla" y A crea "Mesa".
  - B no puede borrar "Mesa"; sí puede borrar "Silla". A, como admin, puede borrar cualquiera.
  - B se asigna "Mesa"; A no puede soltarla salvo como admin, y `/tarea asignar Mesa` de A la reasigna avisando quién la tenía.
  - Con `deop A`, A ya no puede reasignar "Mesa" mientras la tenga B. `/tarea soltar Mesa` de B la libera.
- [ ] **Títulos sin distinguir mayúsculas ni tildes.** `/tarea info jardin` encuentra "Jardín", y `/tarea crear JARDIN` se rechaza porque ya existe. `/tarea renombrar Jardín Huerta` cambia el nombre en la pestaña y conserva el estado.
- [ ] **Interfaz: lista.** `/tareas` abre "Tareas del servidor". Las tareas aparecen primero las disponibles (▶, verde), después las bloqueadas (✘, gris) y al final las resueltas (✔, verde oscuro); dentro de cada grupo, por orden alfabético. El tooltip de cada botón muestra estado y asignado.
- [ ] **Interfaz: lista con 30 tareas.** Con el servidor apagado, copiar `dev-server/samples/tasks-30.json` sobre `run/plugins/TaskGraph/tasks.json` y arrancar. `/tareas` muestra las 30, se puede desplazar hasta el final y el orden por grupos se mantiene. La pestaña de logros también se ve legible.
- [ ] **Interfaz: crear y editar.** "Nueva tarea" con un ítem en la mano crea la tarea con ese ícono y abre su ficha. Un título repetido muestra el error y conserva lo escrito. "Editar" cambia título y descripción en un solo paso.
- [ ] **Interfaz: ficha y botones.** En la ficha se ve el ícono, la descripción y los datos. "Resolver" solo aparece si está disponible y anuncia en el chat una sola vez. "Agregar dependencia" ofrece solo tareas que no crean ciclos. "Quitar dependencia" lista solo las dependencias actuales.
- [ ] **Interfaz: permisos.** B no ve "Borrar" en una tarea de A; A como op sí. B ve "Asignarme" en una tarea libre y no lo ve si la tiene otra persona; A como op ve "Reasignarme".
- [ ] **Interfaz: ventana vieja.** A abre la ficha de una tarea y la deja abierta; B la borra con `/tarea borrar`. Cuando A toca cualquier botón, ve "Esa tarea ya no existe." y vuelve a la lista, sin errores en el log.
- [ ] **Atajos.** Apretar **G** (acciones rápidas) abre "Tareas del servidor" y "Abrir lista" muestra la lista. En el menú de pausa (Esc) aparece el botón "Tareas del servidor" y hace lo mismo. `/tareas` sigue funcionando.
- [ ] **Cerrar sin espera.** En la lista, "Cerrar" vuelve al juego al instante, sin la pantalla "Waiting for Server".

Si algo falla, guardá `run/logs/latest.log`.

## Datos

Las tareas se guardan en `plugins/TaskGraph/tasks.json`. Ese archivo es la fuente de verdad: el progreso que ve cada jugador en la pantalla de logros se sincroniza contra él.

Si el archivo está dañado o tiene datos inválidos (por ejemplo, un ciclo de dependencias), el plugin se desactiva al arrancar y deja el archivo intacto. Revisar el log del servidor, corregir el archivo y reiniciar.

## Interfaz

`/tareas` abre la lista de tareas en una ventana del juego. Hay dos atajos más, sin escribir comandos (ver [Atajos](#atajos)). Cada tarea abre su ficha, con botones para asignarse, soltar, resolver, agregar o quitar dependencias, editar y borrar. Cada botón aparece solo si quien mira puede usarlo.

- **Orden de la lista:** primero las disponibles (todas sus dependencias resueltas), después las bloqueadas y al final las resueltas.
- **Nueva tarea:** el ícono es el ítem que tenés en la mano al tocar «Crear».
- **Agregar dependencia** solo ofrece tareas que no crearían un ciclo.
- **Ventanas viejas:** si alguien cambia o borra una tarea mientras otra persona tiene su ficha abierta, los botones vuelven a comprobar todo al tocarse. Cada botón sirve una sola vez y vence a los 5 minutos.

Los comandos `/tarea` siguen disponibles como alternativa, por ejemplo desde la consola.

### Atajos

Además de `/tareas`, la interfaz se abre desde:

- **La tecla de acciones rápidas** del juego: **G** por defecto, se puede cambiar en Opciones → Controles.
- **El botón "Tareas del servidor" del menú de pausa** (Esc).

Los dos abren primero una ventana chica con el botón **Abrir lista**, que lleva a la misma lista que `/tareas`. Ese paso intermedio es necesario porque la ventana del atajo se registra al arrancar el servidor y no puede mostrar las tareas del momento.

> Si otro plugin o datapack también agrega acciones rápidas, la tecla muestra una lista para elegir entre ellas en lugar de abrir la de tareas directamente. Lo mismo pasa con varios botones en el menú de pausa. `/tareas` funciona siempre igual.

## Comandos

Escribir `/tarea` sin argumentos muestra la ayuda. Los títulos se autocompletan con Tab.

Las tareas se nombran por su **título**, que es único: no distingue mayúsculas, tildes ni espacios repetidos ("Jardín" y "jardin" son la misma tarea).

**Comillas:** cuando un título no es lo último del comando y tiene espacios o tildes, va entre comillas: `/tarea depende "Mesa de trabajo" Madera`. El autocompletado las agrega solo. El último argumento nunca las necesita.

| Comando | Qué hace |
|---|---|
| `/tareas` | Abre la interfaz con la lista de tareas (solo jugadores). |
| `/tarea crear <título>` | Crea una tarea. El ícono es el ítem que tenés en la mano (papel si está vacía). |
| `/tarea renombrar "<título>" <nuevo título>` | Cambia el título. El estado, las dependencias y el progreso se conservan. |
| `/tarea desc "<título>" <texto>` | Cambia la descripción. |
| `/tarea depende "<título>" <dependencia>` | Hace que la tarea dependa de otra. Se rechaza si crea un ciclo. La primera dependencia es el padre visual; las siguientes se listan como "También requiere". |
| `/tarea quitardep "<título>" <dependencia>` | Quita una dependencia. Si era el padre visual, la siguiente pasa a serlo; sin dependencias, la tarea vuelve a colgar del raíz. Tab sugiere solo las dependencias de esa tarea. |
| `/tarea asignar <título>` | Te asigna la tarea. Si ya está asignada a otra persona se rechaza: esa persona la suelta o un admin la reasigna. |
| `/tarea soltar <título>` | Quita la asignación. Solo quien la tiene asignada o un admin. |
| `/tarea resolver <título>` | Marca la tarea como resuelta para todos. Requiere que todas sus dependencias estén resueltas. |
| `/tarea info <título>` | Muestra estado, quién la creó, asignado, dependencias y qué tareas la necesitan. |
| `/tarea borrar <título>` | Borra la tarea. Solo quien la creó o un admin. Se rechaza si otras tareas dependen de ella. |

### Permisos

| Permiso | Por defecto | Qué permite |
|---|---|---|
| `taskgraph.admin` | operadores | Borrar cualquier tarea, soltar tareas de otros y reasignar con `asignar` una tarea que tiene otra persona. |

Todo lo demás (crear, renombrar, describir, agregar y quitar dependencias, asignarse tareas libres, resolver y ver info) está abierto a todos los jugadores. La consola del servidor cuenta como admin. Las tareas creadas antes de que se guardara el creador solo las puede borrar un admin.

Cada cambio se guarda en el JSON y regenera la pestaña de logros para todos los jugadores online.

## Licencia

[MIT](LICENSE). UltimateAdvancementAPI es un plugin aparte, con su propia licencia (LGPL), y no viene incluido en el jar de TaskGraph.
