# TaskGraph

Plugin de Paper para organizar las tareas comunales de un servidor de Minecraft. Las tareas forman un grafo de dependencias que se muestra en la pantalla de logros (advancements) vanilla, así que los jugadores no necesitan mods en el cliente.

> Última versión: **0.3.0**. En desarrollo: 0.4.0, con varias pestañas (proyectos), ocultar ramas terminadas, selector de íconos y pestañas "+" para crear tareas y proyectos.

## Cómo se ve

- Cada proyecto es una pestaña de la pantalla de logros, y cada tarea es un logro en la pestaña de su proyecto. Las tareas sin proyecto están en "General", que es siempre la primera pestaña. El logro raíz de cada pestaña muestra el nombre del proyecto y cuántas de sus tareas están resueltas.
- Las tareas sin dependencias en su proyecto cuelgan del logro raíz. Cada dependencia del mismo proyecto es una línea que une la tarea con lo que requiere. Las dependencias que no son la primera también aparecen en la descripción como "También requiere: ...", y las de otro proyecto dicen cuál, por ejemplo "Picar piedra (en General)".
- Las columnas indican la profundidad: todo lo que una tarea requiere queda a su izquierda.
- Cada tarea se ubica a la altura de sus dependencias, en lugar de ir al final de la columna. Los grupos de tareas que no se conectan entre sí se dibujan en bloques separados, uno debajo del otro, con una fila vacía en medio.
- **Ocultar resueltas**, una opción de cada proyecto: saca de su pestaña las ramas terminadas, es decir, las tareas resueltas de las que ya no depende nada pendiente (en ningún proyecto). Una tarea resuelta que todavía desbloquea algo pendiente sigue a la vista. El raíz dice cuántas hay ocultas, y al volver a mostrarlas aparecen completadas, sin toasts.
- El progreso es comunal: una tarea resuelta aparece completada para todos los jugadores, incluidos los que entran después.
- Las tareas se manejan desde una ventana del juego (`/tareas`, la tecla G o el menú de pausa) o con los comandos `/tarea`. Ver [Interfaz](#interfaz).
- **Pestañas "+ Tarea" y "+ Proyecto"** (solo con [PacketEvents](#requisitos)): las dos últimas pestañas de la pantalla de logros. Abrir "+ Tarea" abre «Nueva tarea» en el proyecto de la pestaña que estabas mirando (General si no miraste ninguna); "+ Proyecto" abre «Nuevo proyecto». Al cancelar volvés al juego, y la próxima vez que abrís los logros vuelven a la pestaña donde estabas.
  - En la pantalla de logros no se puede poner un botón propio ni detectar clics en un logro: el cliente solo avisa qué pestaña se abre. Por eso el "+" es una pestaña.
  - El cliente abre siempre los logros en su primera pestaña; el servidor la cambia enseguida por la última que miraste, así que se ve un salto corto.

## Requisitos

- Servidor [Paper](https://papermc.io/) para Minecraft Java 26.3
- Java 25
- Plugin [UltimateAdvancementAPI](https://modrinth.com/plugin/ultimateadvancementapi/versions) **3.0.0-beta-4** instalado en el servidor. En Modrinth está marcada como *beta*: la versión que aparece primero (2.x) **no sirve**.
- Opcional: plugin [PacketEvents](https://modrinth.com/plugin/packetevents/versions) **2.14.0** (la versión `+spigot`). Solo hace falta para las pestañas "+ Tarea" y "+ Proyecto". Sin él, todo lo demás funciona igual y para crear tareas se usa `/tareas` o la tecla G.

## Compilar

```bash
./gradlew build
```

En Windows: `gradlew.bat build`. El jar queda en `build/libs/`.

## Instalar en el servidor

1. Descargar `UltimateAdvancementAPI-Plugin-3.0.0-beta-4.jar` desde Modrinth y copiarlo a la carpeta `plugins/` del servidor.
2. Opcional, para las pestañas "+": descargar `packetevents-spigot-2.14.0.jar` desde Modrinth y copiarlo a `plugins/`.
3. Copiar el jar de `build/libs/` a la misma carpeta `plugins/`.
4. Reiniciar el servidor. El log dice `PacketEvents found: the "+ Tarea" and "+ Proyecto" tabs are on` o `PacketEvents not found: the "+" tabs are off`.

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
- descarga UltimateAdvancementAPI 3.0.0-beta-4 y PacketEvents 2.14.0 desde Modrinth. Para probar sin PacketEvents: `./gradlew runServer -PnoPacketEvents`;
- registra en el log cada pestaña de logros que abre o cierra un cliente (líneas `[debug]`), para revisar el comportamiento de las pestañas "+";
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

- [ ] **La pestaña se ve y es legible.** A crea "Madera", "Piedra", "Mesa de trabajo" (depende de "Madera"), "Horno" (depende de "Piedra" y después de "Madera") y "Pico" (depende de "Mesa de trabajo" y "Piedra"). Por ejemplo: `/tarea crear Mesa de trabajo` y `/tarea depende "Mesa de trabajo" Madera`. La pestaña "General" muestra el raíz a la izquierda, cada tarea a la derecha de sus dependencias, y ningún ícono encima de otro.
- [ ] **Bloques y altura.** A crea "Hacha" (depende de "Madera") después de las anteriores: queda a la altura de "Madera", no al final de la columna. Una tarea suelta nueva, por ejemplo `/tarea crear Pozo`, aparece en un bloque aparte debajo del resto, con una fila vacía en medio.
- [ ] **Líneas múltiples.** "Horno" se une con una línea a "Piedra" y con otra a "Madera", y "Pico" a "Mesa de trabajo" y a "Piedra". Ningún logro de más aparece en la pestaña. Al resolver "Piedra" y "Madera", y después "Horno", no hay toasts ni mensajes de logro aparte del anuncio "✔".
- [ ] **Tildes y colores.** Crear una tarea con título y descripción con tildes y ñ, por ejemplo `/tarea crear Jardín de la ñandú` y `/tarea desc "Jardín de la ñandú" Regar todos los días`. En la pestaña se ven bien, con el título en blanco, la descripción en gris, "Asignada a" en celeste y "También requiere" en amarillo.
- [ ] **Sin toasts ni anuncios al entrar o reconstruir.** Con tareas ya resueltas, B sale y vuelve a entrar: no aparece ningún toast ni mensaje de logro en el chat. Lo mismo cuando A edita cualquier tarea (`desc`, `asignar`) y la pestaña se reconstruye.
- [ ] **`/tarea resolver` anuncia una sola vez.** A resuelve una tarea: A y B ven un único mensaje "✔ A resolvió la tarea ...", sin el anuncio de logro vanilla, y la tarea aparece completada para los dos.
- [ ] **Revocación con jugador offline.** A resuelve "Jardín" (sin dependencias) y B la ve completada. B sale. A la borra con `/tarea borrar Jardín` y la vuelve a crear con `/tarea crear Jardín`. B entra: "Jardín" aparece **pendiente**, no completada.
- [ ] **Quitar dependencias.** Con "Horno" dependiendo de "Piedra" y "Madera": `/tarea quitardep Horno Piedra` borra la línea desde "Piedra" y deja la de "Madera", y `/tarea quitardep Horno Madera` lo deja colgando del raíz. Tab en el segundo argumento sugiere solo las dependencias de "Horno".
- [ ] **Permisos.** En la consola, `op A` (B queda sin op). B crea "Silla" y A crea "Mesa".
  - B no puede borrar "Mesa"; sí puede borrar "Silla". A, como admin, puede borrar cualquiera.
  - B se asigna "Mesa"; A no puede soltarla salvo como admin, y `/tarea asignar Mesa` de A la reasigna avisando quién la tenía.
  - Con `deop A`, A ya no puede reasignar "Mesa" mientras la tenga B. `/tarea soltar Mesa` de B la libera.
- [ ] **Títulos sin distinguir mayúsculas ni tildes.** `/tarea info jardin` encuentra "Jardín", y `/tarea crear JARDIN` se rechaza porque ya existe. `/tarea renombrar Jardín Huerta` cambia el nombre en la pestaña y conserva el estado.
- [ ] **Interfaz: lista.** `/tareas` abre "Tareas del servidor" con los proyectos; "General" abre su lista. Las tareas aparecen primero las disponibles (▶, verde), después las bloqueadas (✘, gris) y al final las resueltas (✔, verde oscuro); dentro de cada grupo, por orden alfabético. El tooltip de cada botón muestra estado y asignado.
- [ ] **Migración del formato 1.** Con el servidor apagado, copiar `dev-server/samples/tasks-30.json` (formato 1) sobre `run/plugins/TaskGraph/tasks.json` y arrancar. El log muestra `Migrated tasks.json to format version 2` y `Loaded 30 tasks in 1 projects`. Aparece `run/plugins/TaskGraph/tasks.v1.json.bak` igual al ejemplo, y `tasks.json` dice `"version": 2` y tiene `"project": "general"` en cada tarea. La pestaña, que ahora se llama "General", y `/tareas` muestran las mismas tareas con el mismo estado. Al reiniciar otra vez no hay una nueva migración.
- [ ] **Varias pestañas.** Con el servidor apagado, copiar `dev-server/samples/tasks-projects.json` sobre `run/plugins/TaskGraph/tasks.json` y arrancar. El log muestra `Loaded 11 tasks in 3 projects`. La pantalla de logros tiene tres pestañas, en este orden: "General", "Base" y "Minería". Cada raíz muestra el nombre del proyecto y sus resueltas ("3 de 4" en General). En "Base", "Portón" tiene una línea a "Muralla" y dice "También requiere: Juntar madera (en General)"; "Muralla" cuelga del raíz y dice "También requiere: Picar piedra (en General)". "Pico de hierro" cuelga del raíz de "Minería".
- [ ] **Orden estable de pestañas.** Con el ejemplo anterior, A resuelve `/tarea resolver Horno` y después `/tarea resolver "Pico de hierro"`, y edita una tarea de "Base" (`/tarea desc Pozo Con balde`). Después de cada cambio las pestañas siguen en el mismo orden, el raíz de "Minería" dice "1 de 3" y "Pico de hierro" aparece completada. Lo mismo después de que B sale y vuelve a entrar.
- [ ] **Pantalla abierta mientras otro resuelve.** B abre la pantalla de logros en la pestaña "Base" y la deja abierta. A resuelve `/tarea resolver Pozo`. B sigue en "Base", la pantalla no se cierra y "Pozo" aparece completada (si hace falta, al cambiar de pestaña y volver). Probarlo también con B en "Minería".
- [ ] **Proyectos con comandos.** A (op) y B (sin op).
  - B: `/tarea proyecto crear Base` crea el proyecto y aparece una pestaña "Base" al final, después de las que ya había. `/tarea proyecto crear base` se rechaza (ya existe).
  - B: `/tarea crear Muralla` y `/tarea mover Muralla Base`: "Muralla" pasa a la pestaña "Base". `/tarea info Muralla` dice "Proyecto: Base". `/tarea mover Muralla Base` otra vez se rechaza ("ya está en el proyecto").
  - B: `/tarea proyecto renombrar Base Castillo`: la pestaña cambia de nombre y sigue en el mismo lugar. `/tarea proyecto lista` muestra los proyectos con sus resueltas.
  - B: `/tarea proyecto borrar Castillo` se rechaza: solo admins. A: el mismo comando se rechaza porque tiene tareas y lista "Muralla". A mueve "Muralla" a General y lo vuelve a intentar: el proyecto y su pestaña desaparecen. `/tarea proyecto borrar General` se rechaza siempre.
- [ ] **Mover una tarea resuelta.** Crear el proyecto "Base" otra vez, resolver una tarea de General sin dependencias y moverla a "Base": aparece completada en "Base", sin toast ni mensaje de logro, y ya no está en General. B sale y vuelve a entrar: sigue igual.
- [ ] **Interfaz: proyectos.** `/tareas` muestra un botón por proyecto con "(resueltas/total)" y «Nuevo proyecto». Crear uno desde ahí abre su lista vacía. «Nueva tarea» dentro de un proyecto la crea en ese proyecto (su pestaña). En la ficha, «Mover a otro proyecto» lista los demás proyectos y «Volver» lleva a la lista del proyecto nuevo. «Agregar dependencia» muestra "(en General)" en las tareas de otro proyecto. B no ve «Borrar proyecto»; A sí, salvo en General. «Renombrar proyecto» con el mismo nombre dice "No había cambios.".
- [ ] **Interfaz: proyecto viejo.** A abre la lista de un proyecto vacío y la deja abierta; desde otro cliente, un admin lo borra con `/tarea proyecto borrar`. Cuando A toca «Nueva tarea», ve "Ese proyecto ya no existe." en la lista de proyectos, sin errores en el log.
- [ ] **Ocultar ramas terminadas.** Con el servidor apagado, copiar `dev-server/samples/tasks-projects.json` sobre `run/plugins/TaskGraph/tasks.json` y arrancar. En General están resueltas "Juntar madera", "Picar piedra" y "Mesa de trabajo".
  - `/tarea proyecto ocultar General`: no desaparece ninguna, porque las tres todavía desbloquean algo pendiente. "Juntar madera" la necesita "Portón" (en Base), "Picar piedra" la necesitan "Horno" y "Muralla", y "Mesa de trabajo" la necesita "Pico de hierro". El raíz no dice nada de ocultas.
  - `/tarea resolver Horno`, `/tarea resolver Pico de hierro`: ahora "Mesa de trabajo" y "Horno" quedan con todos sus dependientes resueltos y desaparecen de General; el raíz dice "2 resueltas ocultas". "Picar piedra" sigue por "Muralla".
  - `/tarea proyecto lista` marca General con "(oculta ramas terminadas)". `/tareas` → General sigue listando las 4 tareas y dice "2 resueltas ocultas".
  - B sale y vuelve a entrar: las ocultas no aparecen y no hay toasts.
  - `/tarea proyecto mostrar General`: las dos vuelven a aparecer **completadas**, sin toast ni mensaje de logro. El botón «Ocultar resueltas» / «Mostrar resueltas» del Dialog hace lo mismo.
- [ ] **Pestañas "+" con PacketEvents.** `./gradlew runServer`; el log dice `PacketEvents found`. Al final de la pantalla de logros están "+ Tarea" y "+ Proyecto", y el raíz de cada proyecto dice "Nueva tarea acá: pestaña «+ Tarea»".
  - Desde la pestaña "Base", abrir "+ Tarea": se abre "Nueva tarea en Base" y la tarea creada queda en Base. Salir y volver a entrar y abrir "+ Tarea" sin pasar por otra pestaña: "Nueva tarea en General".
  - «Cancelar» o Esc vuelven al juego. Al abrir los logros otra vez aparecen en Base (después de un salto corto desde la primera pestaña), no en "+ Tarea".
  - Abrir y cerrar los logros cinco o seis veces seguidas: el Dialog no se abre solo y la pestaña no salta. Elegir una pestaña vanilla a propósito, cerrar y abrir: se queda en esa.
  - "+ Proyecto" abre "Nuevo proyecto"; el proyecto creado aparece antes de "+ Tarea", que siguen siendo las dos últimas.
  - Tecla G → «Nueva tarea»: abre en el proyecto de la última pestaña mirada.
- [ ] **Sin PacketEvents.** `./gradlew runServer -PnoPacketEvents`; el log dice `PacketEvents not found` y no hay `NoClassDefFoundError`. No hay pestañas "+" y el raíz dice "Nueva tarea: /tareas o la tecla G.". Tecla G → «Nueva tarea» abre "Nueva tarea en General". `/tareas`, crear, resolver, el selector de íconos y `/tarea proyecto ocultar` funcionan igual.
- [ ] **Interfaz: lista con 30 tareas.** Con el servidor apagado, copiar `dev-server/samples/tasks-30.json` sobre `run/plugins/TaskGraph/tasks.json` y arrancar. `/tareas` muestra las 30, se puede desplazar hasta el final y el orden por grupos se mantiene. La pestaña de logros también se ve legible.
- [ ] **Interfaz: crear y editar.** "Nueva tarea" con un ítem en la mano crea la tarea con ese ícono y abre su ficha. Un título repetido muestra el error y conserva lo escrito. "Editar" cambia título y descripción en un solo paso.
- [ ] **Íconos.** `/tarea icono Madera ` y Tab sugiere ítems; `/tarea icono Madera oak_log` cambia el ícono en la pestaña y en la ficha. `/tarea icono Madera air` se rechaza. En "Nueva tarea", el ícono `DIAMOND` crea la tarea con un diamante aunque tengas otra cosa en la mano; `diamante` muestra el error y conserva lo escrito; vacío usa el ítem en la mano. "Cambiar ícono" en la ficha acepta `minecraft:oak_log`, y vacío con la mano vacía muestra el error.
- [ ] **Selector de íconos.** En «Nueva tarea», escribí un título y una descripción y tocá «Elegir ícono»: se abre el cofre con las 8 categorías y «Todos los ítems». En «Comida», un clic en el pan vuelve a la ventana con el título y la descripción intactos, el campo en `bread` y la vista previa del pan; «Crear» la crea con ese ícono. En «Todos los ítems», «Siguiente ▶» y «◀ Anterior» cambian de página y el título muestra el número; «Volver» lleva a las categorías. Cerrar el cofre con Esc o tocar «Cancelar» vuelve a la ventana con lo que había. No se puede sacar ni mover ningún ítem del cofre, ni con shift-clic ni arrastrando, y el inventario propio no cambia. «Cambiar ícono» en la ficha hace lo mismo y «Guardar» aplica el elegido. El log del arranque no muestra el aviso `The icon picker skips items`.
- [ ] **Interfaz: ficha y botones.** En la ficha se ve el ícono, la descripción y los datos. "Resolver" solo aparece si está disponible y anuncia en el chat una sola vez. "Agregar dependencia" ofrece solo tareas que no crean ciclos. "Quitar dependencia" lista solo las dependencias actuales.
- [ ] **Interfaz: permisos.** B no ve "Borrar" en una tarea de A; A como op sí. B ve "Asignarme" en una tarea libre y no lo ve si la tiene otra persona; A como op ve "Reasignarme".
- [ ] **Interfaz: ventana vieja.** A abre la ficha de una tarea y la deja abierta; B la borra con `/tarea borrar`. Cuando A toca cualquier botón, ve "Esa tarea ya no existe." y vuelve a la lista, sin errores en el log.
- [ ] **Atajos.** Apretar **G** (acciones rápidas) abre "Tareas del servidor" y "Abrir lista" muestra la lista de proyectos. En el menú de pausa (Esc) aparece el botón "Tareas del servidor" y hace lo mismo. `/tareas` sigue funcionando.
- [ ] **Cerrar sin espera.** En la lista, "Cerrar" vuelve al juego al instante, sin la pantalla "Waiting for Server".

Si algo falla, guardá `run/logs/latest.log`.

## Datos

Las tareas se guardan en `plugins/TaskGraph/tasks.json`. Ese archivo es la fuente de verdad: el progreso que ve cada jugador en la pantalla de logros se sincroniza contra él.

Si el archivo está dañado o tiene datos inválidos (por ejemplo, un ciclo de dependencias), el plugin se desactiva al arrancar y deja el archivo intacto. Revisar el log del servidor, corregir el archivo y reiniciar.

**Formato 2 (desde la 0.4.0).** El archivo guarda también los proyectos y en qué proyecto está cada tarea. Un `tasks.json` de una versión anterior (formato 1) se convierte solo al arrancar:
- todas sus tareas pasan al proyecto General;
- antes de convertirlo se guarda una copia del archivo original como `plugins/TaskGraph/tasks.v1.json.bak`;
- el log muestra `Migrated tasks.json to format version 2`.

Si el archivo viejo tiene errores, no se convierte ni se toca.

## Interfaz

`/tareas` abre una ventana del juego con la lista de proyectos. Hay dos atajos más, sin escribir comandos (ver [Atajos](#atajos)). Cada proyecto abre su lista de tareas, y cada tarea abre su ficha, con botones para asignarse, soltar, resolver, agregar o quitar dependencias, editar, cambiar el ícono, mover a otro proyecto y borrar. Cada botón aparece solo si quien mira puede usarlo.

- **Proyectos:** cada botón muestra cuántas tareas tiene resueltas, por ejemplo "Base (2/5)". «Nuevo proyecto» crea otro, con su propia pestaña de logros. Dentro de un proyecto están «Renombrar proyecto», «Ocultar resueltas» (o «Mostrar resueltas») y, solo para admins y si no es General, «Borrar proyecto». La lista del Dialog muestra siempre todas las tareas, también las ocultas en la pestaña.
- **Orden de la lista:** primero las disponibles (todas sus dependencias resueltas), después las bloqueadas y al final las resueltas.
- **Nueva tarea:** se crea en el proyecto que tenés abierto. El campo «Ícono» es opcional. Acepta el id de un ítem con o sin `minecraft:` y sin distinguir mayúsculas (`diamond`, `minecraft:diamond`, `DIAMOND`). Si queda vacío, el ícono es el ítem que tenés en la mano al tocar «Crear».
- **Elegir ícono**, en Nueva tarea y en Cambiar ícono: abre un menú de cofre con categorías (Bloques, Naturaleza, Herramientas, Combate, Comida, Redstone, Decoración, Varios) y «Todos los ítems», en orden alfabético y por páginas. Al hacer clic en un ítem volvés a la ventana con lo que ya habías escrito y el ícono elegido. Cerrar el cofre o «Cancelar» vuelve sin cambios. Los ítems del menú no se pueden sacar.
- **Cambiar ícono**, en la ficha: el mismo campo. Vacío usa el ítem que tenés en la mano.
- **Agregar dependencia** solo ofrece tareas que no crearían un ciclo. Las de otro proyecto aparecen con el nombre del proyecto, por ejemplo "Madera (en General)".
- **Mover a otro proyecto**, en la ficha: aparece cuando hay más de un proyecto. La tarea conserva dependencias, estado y asignado.
- **Ventanas viejas:** si alguien cambia o borra una tarea mientras otra persona tiene su ficha abierta, los botones vuelven a comprobar todo al tocarse. Cada botón sirve una sola vez y vence a los 5 minutos.

Los comandos `/tarea` siguen disponibles como alternativa, por ejemplo desde la consola.

### Atajos

Además de `/tareas`, la interfaz se abre desde:

- **La tecla de acciones rápidas** del juego: **G** por defecto, se puede cambiar en Opciones → Controles.
- **El botón "Tareas del servidor" del menú de pausa** (Esc).

Los dos abren primero una ventana chica con dos botones: **Abrir lista**, que lleva a la misma lista de proyectos que `/tareas`, y **Nueva tarea**, que la crea en el proyecto de la última pestaña de logros que miraste (en General sin PacketEvents, o si no miraste ninguna). Cancelar «Nueva tarea» vuelve al juego. Ese paso intermedio es necesario porque la ventana del atajo se registra al arrancar el servidor y no puede mostrar las tareas del momento.

> Si otro plugin o datapack también agrega acciones rápidas, la tecla muestra una lista para elegir entre ellas en lugar de abrir la de tareas directamente. Lo mismo pasa con varios botones en el menú de pausa. `/tareas` funciona siempre igual.

## Comandos

Escribir `/tarea` sin argumentos muestra la ayuda. Los títulos de tareas y proyectos se autocompletan con Tab.

Las tareas se nombran por su **título**, que es único: no distingue mayúsculas, tildes ni espacios repetidos ("Jardín" y "jardin" son la misma tarea). Los proyectos también se nombran por título, único entre proyectos (un proyecto puede llamarse igual que una tarea).

**Comillas:** cuando un título no es lo último del comando y tiene espacios o tildes, va entre comillas: `/tarea depende "Mesa de trabajo" Madera`. El autocompletado las agrega solo. El último argumento nunca las necesita.

| Comando | Qué hace |
|---|---|
| `/tareas` | Abre la interfaz con la lista de proyectos (solo jugadores). |
| `/tarea crear <título>` | Crea una tarea en General. El ícono es el ítem que tenés en la mano (papel si está vacía); se cambia después con `icono`. Para otro proyecto, usar `mover` o la interfaz. |
| `/tarea renombrar "<título>" <nuevo título>` | Cambia el título. El estado, las dependencias y el progreso se conservan. |
| `/tarea desc "<título>" <texto>` | Cambia la descripción. |
| `/tarea icono "<título>" <ítem>` | Cambia el ícono. Tab sugiere los ítems; acepta `diamond` o `minecraft:diamond`. Se rechazan los que no sirven de ícono, como el aire. |
| `/tarea depende "<título>" <dependencia>` | Hace que la tarea dependa de otra, de cualquier proyecto. Se rechaza si crea un ciclo. Cada dependencia del mismo proyecto se dibuja con una línea; las de otro proyecto y todas desde la segunda se listan en "También requiere". |
| `/tarea quitardep "<título>" <dependencia>` | Quita una dependencia y su línea. Sin dependencias, la tarea vuelve a colgar del raíz. Tab sugiere solo las dependencias de esa tarea. |
| `/tarea asignar <título>` | Te asigna la tarea. Si ya está asignada a otra persona se rechaza: esa persona la suelta o un admin la reasigna. |
| `/tarea soltar <título>` | Quita la asignación. Solo quien la tiene asignada o un admin. |
| `/tarea resolver <título>` | Marca la tarea como resuelta para todos. Requiere que todas sus dependencias estén resueltas. |
| `/tarea info <título>` | Muestra proyecto, estado, quién la creó, asignado, dependencias y qué tareas la necesitan. |
| `/tarea borrar <título>` | Borra la tarea. Solo quien la creó o un admin. Se rechaza si otras tareas dependen de ella. |
| `/tarea mover "<título>" <proyecto>` | Pasa la tarea a otro proyecto (otra pestaña). Conserva dependencias, estado y asignado. |
| `/tarea proyecto lista` | Lista los proyectos con sus tareas resueltas. `/tarea proyecto` solo hace lo mismo. |
| `/tarea proyecto crear <título>` | Crea un proyecto, con su propia pestaña de logros. |
| `/tarea proyecto renombrar "<proyecto>" <nuevo título>` | Cambia el nombre de un proyecto, incluido General. |
| `/tarea proyecto ocultar <proyecto>` | Oculta de la pestaña del proyecto las ramas terminadas. `mostrar` las vuelve a mostrar. |
| `/tarea proyecto borrar <proyecto>` | Borra un proyecto. Solo admins. Se rechaza si tiene tareas o si es General. |

### Permisos

| Permiso | Por defecto | Qué permite |
|---|---|---|
| `taskgraph.admin` | operadores | Borrar cualquier tarea, borrar proyectos, soltar tareas de otros y reasignar con `asignar` una tarea que tiene otra persona. |

Todo lo demás (crear, renombrar, describir, cambiar el ícono, agregar y quitar dependencias, mover tareas, crear y renombrar proyectos, ocultar o mostrar resueltas, asignarse tareas libres, resolver y ver info) está abierto a todos los jugadores. La consola del servidor cuenta como admin. Las tareas creadas antes de que se guardara el creador solo las puede borrar un admin.

Cada cambio se guarda en el JSON y regenera la pestaña de logros para todos los jugadores online.

## Licencia

[MIT](LICENSE). UltimateAdvancementAPI es un plugin aparte, con su propia licencia (LGPL), y no viene incluido en el jar de TaskGraph.
