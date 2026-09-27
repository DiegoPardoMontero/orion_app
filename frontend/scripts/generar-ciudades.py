"""Genera public/ciudades/<PAÍS>.json a partir de los volcados de GeoNames (CC BY 4.0).

Uso: python3 scripts/generar-ciudades.py <carpeta con cities15000.txt, cities1000.txt y admin1CodesASCII.txt>

Colombia va completa —cada cabecera municipal y los poblados de más de mil habitantes, con su
departamento—, porque es donde está casi todo el que se postula. Los demás países llevan sus ciudades
de más de 15.000 habitantes. Si alguien no encuentra la suya, el buscador deja escribirla.

Cada archivo es una lista de [nombre, región], de la más poblada a la menos: así lo primero que
sugiere el buscador es lo más probable. Datos de https://www.geonames.org, licencia CC BY 4.0.
"""
import json
import os
import sys
from collections import defaultdict

ORIGEN = sys.argv[1]
DESTINO = os.path.join(os.path.dirname(__file__), "..", "public", "ciudades")
# Lugares habitados; fuera los barrios (PPLX), los abandonados o históricos y las localidades sueltas.
# GeoNames trae algunas regiones sin tilde.
CORRECCIONES = {"Bogota D.C.": "Bogotá D.C."}
FUERA = {"PPLX", "PPLH", "PPLQ", "PPLW", "PPLL", "PPLR", "PPLF", "PPLS"}

regiones = {}
with open(os.path.join(ORIGEN, "admin1CodesASCII.txt"), encoding="utf-8") as f:
    for linea in f:
        codigo, nombre, *_ = linea.rstrip("\n").split("\t")
        for sufijo in (" Department", " Departamento", " Province", " State", " Region"):
            if nombre.endswith(sufijo):
                nombre = nombre[: -len(sufijo)]
        regiones[codigo] = CORRECCIONES.get(nombre, nombre)


def leer(archivo, filtro):
    with open(os.path.join(ORIGEN, archivo), encoding="utf-8") as f:
        for linea in f:
            c = linea.rstrip("\n").split("\t")
            nombre, clase, pais, admin1, poblacion = c[1], c[7], c[8], c[10], int(c[14] or 0)
            if clase in FUERA or not filtro(pais):
                continue
            yield pais, nombre, regiones.get(f"{pais}.{admin1}", ""), poblacion


por_pais = defaultdict(dict)
fuentes = [("cities1000.txt", lambda p: p == "CO"), ("cities15000.txt", lambda p: p != "CO")]
for archivo, filtro in fuentes:
    for pais, nombre, region, poblacion in leer(archivo, filtro):
        clave = (nombre, region)
        if poblacion > por_pais[pais].get(clave, -1):
            por_pais[pais][clave] = poblacion

os.makedirs(DESTINO, exist_ok=True)
for pais, ciudades in por_pais.items():
    lista = sorted(ciudades.items(), key=lambda kv: (-kv[1], kv[0][0]))
    with open(os.path.join(DESTINO, f"{pais}.json"), "w", encoding="utf-8") as f:
        json.dump([[n, r] for (n, r), _ in lista], f, ensure_ascii=False, separators=(",", ":"))
print(len(por_pais), "países")
