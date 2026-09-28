# Material de la ficha de Google Play — ZeroHaus

Gráficos, logos y textos para publicar y actualizar ZeroHaus en Google Play.

## Contenido

```
playstore-assets/
├── graphics/
│   ├── icon-512.png                   Icono de la ficha (obligatorio)
│   ├── feature-graphic-1024x500.png   Gráfico destacado (obligatorio)
│   └── symbol-1024.png                Símbolo del logo en alta resolución
├── source-logos/                      Logos originales (SVG, PDF y PNG)
├── textos/
│   ├── 1-store-listing.md             Nombre, descripciones, categoría y etiquetas
│   ├── 2-data-safety.md               Formulario de seguridad de los datos  (no versionado)
│   ├── 3-release-notes.md             Novedades de la versión 2.2.0 (es / en)
│   └── 4-app-access-tester.md         Cuenta de prueba para la revisión  (no versionado)
├── generar_graficos.py                Genera los gráficos a partir de los logos
└── README.md
```

Los ficheros marcados como **no versionado** están en `.gitignore`: contienen
credenciales de la cuenta de prueba o datos de contacto personales y no deben
llegar a un repositorio público. Se guardan solo en local.

La política de privacidad vigente es [`public/privacidad.html`](../public/privacidad.html),
servida por Firebase Hosting en `https://zerohaus-2a865.web.app/privacidad`.

## Publicar una actualización

1. Sube el `.aab` firmado (`./gradlew :app:bundleRelease`) a la pista que toque.
2. Copia las novedades de `textos/3-release-notes.md`.
3. Si cambió la ficha, actualízala con `textos/1-store-listing.md`.
4. Revisa que la **seguridad de los datos** (`textos/2-data-safety.md`) sigue
   coincidiendo con lo que hace la app y con la política de privacidad.
5. En **Acceso a la app**, la cuenta de prueba de `textos/4-app-access-tester.md`
   debe tener el email verificado (la app lo exige desde la 2.2).

## Capturas de pantalla

Mínimo 2 (recomendado 6–8), en formato 9:16 y con al menos 320 px en el lado
corto. Pantallas recomendadas: informe energético con la etiqueta, simulador
«¿qué pasa si…?», consejos con IA, directorio o mapa de profesionales, chat y
estadísticas del profesional. Guárdalas en `graphics/screenshots/`.

## Regenerar los gráficos

```bash
cd playstore-assets
python generar_graficos.py
```

Los colores y la posición del logo se ajustan en las constantes del script.
