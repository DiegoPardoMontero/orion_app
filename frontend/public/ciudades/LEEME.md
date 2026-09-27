# Ciudades por país

Un archivo por país (`CO.json`, `US.json`…), con una lista de `[ciudad, región]` de la más poblada a la
menos. Lo usa el buscador de ciudad de la postulación del profesor.

Datos de [GeoNames](https://www.geonames.org), licencia [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Se regeneran con `python3 scripts/generar-ciudades.py <carpeta>`, donde la carpeta tiene
`cities15000.txt`, `cities1000.txt` y `admin1CodesASCII.txt` descargados de
https://download.geonames.org/export/dump/.
