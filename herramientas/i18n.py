#!/usr/bin/env python3
"""Mantenimiento de los textos de la app en sus 14 idiomas.

Los textos viven en dos ficheros de app/src/main/java/com/example/zerohaus/Util:
  - AppCadenas.kt    una propiedad por texto:  val clave: String get() = t("clave")
  - Traducciones.kt  un mapa por idioma:       "clave" to "texto",

Uso (desde la raíz del repositorio):
  python herramientas/i18n.py añadir textos.json    Añade claves nuevas a los 14 idiomas
  python herramientas/i18n.py cambiar textos.json   Cambia el texto de claves existentes
  python herramientas/i18n.py sin-uso [--borrar]    Lista (o borra) claves que no usa el código
  python herramientas/i18n.py comprobar             Verifica que los 14 idiomas están completos

Formato de textos.json (los 14 textos en el orden de IDIOMAS):
  {"miClave": ["Español", "English", "Català", ...]}
"""
import json
import os
import re
import sys

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
CODIGO = os.path.join(RAIZ, "app", "src", "main", "java", "com", "example", "zerohaus")
P_CADENAS = os.path.join(CODIGO, "Util", "AppCadenas.kt")
P_TRADUCCIONES = os.path.join(CODIGO, "Util", "Traducciones.kt")

# Orden de los bloques en Traducciones.kt
IDIOMAS = ["es", "en", "ca", "eu", "gl", "pt", "fr", "de", "it", "ar", "zh", "ro", "nl", "pl"]

RE_PROPIEDAD = re.compile(r'^\s*val (\w+): String get\(\) = t\("\1"\)\s*$')
RE_ENTRADA = re.compile(r'^\s*"(\w+)" to "(.*)",\s*$')
RE_CIERRE = re.compile(r"^\)")  # fin de un bloque de idioma: ")) }" o "), respaldo = espanol) }"


def leer(ruta):
    return open(ruta, encoding="utf-8").read().replace("\r\n", "\n").split("\n")


def escribir(ruta, lineas):
    open(ruta, "w", encoding="utf-8", newline="\n").write("\n".join(lineas))


def literal_kotlin(texto):
    return '"' + texto.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def claves_declaradas():
    return [m.group(1) for l in leer(P_CADENAS) if (m := RE_PROPIEDAD.match(l))]


def cargar_json(ruta):
    datos = json.load(open(ruta, encoding="utf-8-sig"))  # acepta JSON con BOM (Windows)
    for clave, textos in datos.items():
        if not re.fullmatch(r"[a-z]\w*", clave):
            sys.exit(f"Clave no válida: {clave!r} (camelCase empezando en minúscula)")
        if len(textos) != len(IDIOMAS) or any(not t.strip() for t in textos):
            sys.exit(f"{clave}: hacen falta {len(IDIOMAS)} textos no vacíos ({', '.join(IDIOMAS)})")
    return datos


def añadir(ruta_json):
    datos = cargar_json(ruta_json)
    existentes = set(claves_declaradas())
    repetidas = [k for k in datos if k in existentes]
    if repetidas:
        sys.exit(f"Ya existen: {repetidas} (usa 'cambiar')")

    cad = leer(P_CADENAS)
    fin_clase = max(i for i, l in enumerate(cad) if l == "}")
    cad[fin_clase:fin_clase] = [f'    val {k}: String get() = t("{k}")' for k in datos]
    escribir(P_CADENAS, cad)

    tra = leer(P_TRADUCCIONES)
    cierres = [i for i, l in enumerate(tra) if RE_CIERRE.match(l)]
    assert len(cierres) == len(IDIOMAS), f"se esperaban {len(IDIOMAS)} bloques, hay {len(cierres)}"
    for n in reversed(range(len(IDIOMAS))):
        tra[cierres[n]:cierres[n]] = [f"    \"{k}\" to {literal_kotlin(t[n])}," for k, t in datos.items()]
    escribir(P_TRADUCCIONES, tra)
    print(f"Añadidas {len(datos)} claves en {len(IDIOMAS)} idiomas")


def cambiar(ruta_json):
    datos = cargar_json(ruta_json)
    tra = leer(P_TRADUCCIONES)
    for clave, textos in datos.items():
        filas = [i for i, l in enumerate(tra) if (m := RE_ENTRADA.match(l)) and m.group(1) == clave]
        if len(filas) != len(IDIOMAS):
            sys.exit(f"{clave}: aparece {len(filas)} veces (se esperaban {len(IDIOMAS)})")
        for i, texto in zip(filas, textos):
            tra[i] = f"    \"{clave}\" to {literal_kotlin(texto)},"
    escribir(P_TRADUCCIONES, tra)
    print(f"Cambiadas {len(datos)} claves")


def sin_uso(borrar):
    codigo = ""
    for carpeta, _, ficheros in os.walk(CODIGO):
        for f in ficheros:
            ruta = os.path.join(carpeta, f)
            if f.endswith(".kt") and os.path.abspath(ruta) not in (os.path.abspath(P_CADENAS), os.path.abspath(P_TRADUCCIONES)):
                codigo += open(ruta, encoding="utf-8").read()
    sobran = [k for k in claves_declaradas() if not re.search(r"\." + k + r"\b", codigo)]
    print(f"{len(sobran)} claves sin uso: {' '.join(sobran)}")
    if borrar and sobran:
        escribir(P_CADENAS, [l for l in leer(P_CADENAS) if not ((m := RE_PROPIEDAD.match(l)) and m.group(1) in sobran)])
        escribir(P_TRADUCCIONES, [l for l in leer(P_TRADUCCIONES) if not ((m := RE_ENTRADA.match(l)) and m.group(1) in sobran)])
        print("Borradas.")


def comprobar():
    declaradas = set(claves_declaradas())
    bloques, actual = [], None
    for l in leer(P_TRADUCCIONES):
        if "AppCadenas(mapOf(" in l:
            actual = {}
        elif actual is not None and RE_CIERRE.match(l):
            bloques.append(actual)
            actual = None
        elif actual is not None and (m := RE_ENTRADA.match(l)):
            actual[m.group(1)] = m.group(2)
    ok = len(bloques) == len(IDIOMAS)
    for idioma, textos in zip(IDIOMAS, bloques):
        faltan, sobran = declaradas - textos.keys(), textos.keys() - declaradas
        vacias = [k for k, v in textos.items() if not v.strip()]
        if faltan or sobran or vacias:
            ok = False
            print(f"[{idioma}] faltan: {sorted(faltan)} · sobran: {sorted(sobran)} · vacías: {vacias}")
    print(f"{len(declaradas)} claves × {len(bloques)} idiomas: {'OK' if ok else 'CON ERRORES'}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    ordenes = {
        "añadir": lambda a: añadir(a[0]),
        "cambiar": lambda a: cambiar(a[0]),
        "sin-uso": lambda a: sin_uso("--borrar" in a),
        "comprobar": lambda a: comprobar(),
    }
    if len(sys.argv) < 2 or sys.argv[1] not in ordenes:
        print(__doc__)
        sys.exit(1)
    ordenes[sys.argv[1]](sys.argv[2:])
