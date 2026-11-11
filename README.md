# Simulador de SAP GUI (ECC 6.0) – pagos a proveedores con F110

Aplicación de escritorio que reproduce el aspecto y el comportamiento de SAP GUI
conectado a un ECC 6.0, centrada en el caso de uso de la PoC: pago a proveedores
con la transacción **F110**, con control humano y aprobación en BCM.

**Es un simulador.** No se conecta a ningún sistema SAP: los datos y la lógica
viven en memoria y se reinician al cerrar. Todos los datos son ficticios
(sociedades Northgate US01/US02/CA01, proveedores 1000–1009).

## Tecnología

Java 17 + Swing, sin dependencias externas. Es la misma base que **SAP GUI for
Java** (el cliente oficial multiplataforma). SAP GUI for Windows es C++/Win32 y
no se puede compilar ni probar en este entorno; por eso se ha elegido la variante Java.

## Arranque

```bash
mvn -q package
```

```bash
java -jar target/sapgui.jar
```

Opciones:

| Opción | Efecto |
| --- | --- |
| `--direct` | Abre la pantalla de login sin pasar por SAP Logon |
| `--user NOMBRE` | Inicia sesión automáticamente (mandante 100) |
| `--tcode F110` | Arranca directamente en una transacción |
| `--scripting 8777` | Activa la interfaz de scripting en `127.0.0.1:8777` |

Login: mandante `100`, cualquier usuario y cualquier contraseña no vacía.
Formato de fecha: `MM/DD/YYYY`.

## Qué incluye

- **SAP Logon**, pantalla de login, **SAP Easy Access** (árbol de menú y favoritos),
  campo de comandos (`/nF110`, `/n`, `/o`, `/i`, `/nex`), varias sesiones,
  Sistema → Status, barra de estado con mensajes S/E/W.
- **F110** con sus cinco pestañas (Status, Parameter, Free selection, Additional Log,
  Printout/data medium) y el ciclo completo:
  1. Fecha de ejecución e identificación.
  2. Parámetros: fecha de contabilización, sociedades (lista `US01,US02` o rango
     `US01-US02`), vías de pago, próxima fecha de pago, rango de proveedores.
  3. Log adicional y variante de aviso de pago (`RFFOAVIS_FPAYM`, remittance por e-mail).
  4. Guardar (Ctrl+S) → planificar propuesta → el job tarda ~1,5 s; hay que refrescar
     con Enter o *Status* → visualizar/editar propuesta y su log.
  5. Planificar ejecución de pagos → documentos de pago, compensación de partidas
     y log de la ejecución.
- **Excepciones** que aparecen en la propuesta: partida con bloqueo de pago,
  proveedor sin vía de pago válida, sin datos bancarios, y cuenta bloqueada por otra
  propuesta abierta (`TEST1`, de ayer).
- **Pop-ups** que interrumpen el flujo (el punto débil de los scripts actuales):
  planificación, «Parameters were changed. Save?» al salir, borrar propuesta,
  aprobación de lotes, cierre de sesión.
- **BNK_APP** (Bank Communication Management): cada ejecución crea lotes por
  sociedad y vía de pago. Los lotes de más de 50.000 necesitan dos aprobadores
  distintos (control dual).
- **FBL1N**: partidas abiertas/compensadas de proveedores, para comprobar el
  resultado del pago.

Vías de pago: `T` ACH/transferencia, `C` cheque, `W` wire.

## Interfaz de scripting

Con `--scripting PUERTO` se expone por HTTP (solo loopback) una interfaz inspirada
en el modelo de objetos de SAP GUI Scripting. Los controles usan identificadores
con el formato real (`wnd[0]/usr/ctxtF110V-LAUFD`, `wnd[0]/tbar[1]/btn[13]`,
`wnd[1]/usr/chkF110V-XSTRF`…); `id` admite también el sufijo del identificador.

| Método | Parámetros | Equivalente |
| --- | --- | --- |
| `/session` | – | título, transacción, mensaje de la barra de estado, ventana activa y texto del pop-up |
| `/objects` | – | lista de controles de la ventana activa |
| `/set` | `id`, `value` | `findById(id).text = value` / `.selected` |
| `/press` | `id` | `findById(id).press` / `.select` (botones, pestañas, radios) |
| `/vkey` | `key` | `sendVKey` (0 Enter, 3 Back, 8 Execute, 11 Save, 12 Cancel, 15 Exit) |
| `/transaction` | `tcode` | `StartTransaction` |
| `/table` | `id` | contenido de una lista ALV |
| `/selectRow`, `/doubleClickRow` | `row` | selección en la lista ALV |
| `/screenshot` | – | PNG de la ventana activa |

Cada llamada queda registrada en `logs/scripting-audit.log` (trazabilidad).

```bash
curl -G http://127.0.0.1:8777/set --data-urlencode 'id=wnd[0]/tbar[0]/okcd' --data-urlencode 'value=/nF110'
```

```bash
curl 'http://127.0.0.1:8777/vkey?key=0'
```

## Limitaciones

- No hay persistencia ni conexión RFC/DIAG: no sustituye a una instancia ECC real
  para validar conectividad, autorizaciones o rendimiento.
- La pestaña *Free selection* se puede rellenar pero no filtra la propuesta.
- Solo existen F110, FBL1N y BNK_APP; el resto de transacciones devuelve
  «Transaction … does not exist».
- No se incluyen el logotipo ni recursos gráficos propiedad de SAP; los iconos
  están dibujados por código.
