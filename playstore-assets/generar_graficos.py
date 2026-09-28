"""
Genera todos los activos gráficos a partir del logo original del usuario:

  - graphics/icon-512.png                 (Play Store, 512x512)
  - graphics/feature-graphic-1024x500.png (Play Store)
  - graphics/symbol-1024.png              (símbolo recortado, para uso libre)

Y los iconos del launcher Android en mipmap-{m,h,x,xx,xxx}dpi.

Fuente: source-logos/transparent-logo.png (4000x4000, símbolo en mitad superior).
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).parent
SOURCE = ROOT / "source-logos" / "transparent-logo.png"
GFX = ROOT / "graphics"
GFX.mkdir(exist_ok=True)

# Paleta forest green ZeroHaus (color de la app) + colores del logo
VERDE = (22, 163, 74)            # #16A34A — primary forest de la app
VERDE_OSCURO = (6, 95, 70)       # #065F46 — variante oscura para gradientes
GRIS_TEXTO = (17, 24, 39)        # #111827 — texto oscuro de la app
BLANCO_OFF = (255, 255, 255)     # blanco puro como fondo de los iconos
BLANCO = (255, 255, 255)


def cargar_simbolo():
    """Carga el logo original y recorta solo el símbolo (casa+hojas)."""
    img = Image.open(SOURCE).convert("RGBA")
    # Bounding box detectado: (1395, 1127, 2613, 2132) sobre 4000x4000
    sym_box = (0, 0, img.width, int(img.height * 0.55))
    upper = img.crop(sym_box)
    bbox = upper.getbbox()
    if bbox is None:
        raise RuntimeError("No se encontró contenido en el logo")
    simbolo = upper.crop(bbox)
    return simbolo


def cuadrar_simbolo(simbolo, lado, padding_pct=0.10, bg=None):
    """Coloca el símbolo centrado dentro de un cuadrado de `lado` x `lado`.
    Si `bg` es None → fondo transparente; si es un color RGB → fondo sólido."""
    if bg is None:
        canvas = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    else:
        canvas = Image.new("RGBA", (lado, lado), bg + (255,))

    # Calculamos el tamaño máximo del símbolo respetando el padding
    pad = int(lado * padding_pct)
    max_size = lado - 2 * pad
    # Escalamos el símbolo manteniendo aspecto
    s = simbolo.copy()
    sw, sh = s.size
    escala = min(max_size / sw, max_size / sh)
    nw, nh = int(sw * escala), int(sh * escala)
    s = s.resize((nw, nh), Image.LANCZOS)

    # Centrado
    x = (lado - nw) // 2
    y = (lado - nh) // 2
    canvas.paste(s, (x, y), s)
    return canvas


def gradiente_diagonal(size, top_left, bottom_right):
    w, h = size
    img = Image.new("RGB", size, top_left)
    px = img.load()
    diag = (w + h - 2)
    for y in range(h):
        for x in range(w):
            t = (x + y) / max(1, diag)
            r = int(top_left[0] * (1 - t) + bottom_right[0] * t)
            g = int(top_left[1] * (1 - t) + bottom_right[1] * t)
            b = int(top_left[2] * (1 - t) + bottom_right[2] * t)
            px[x, y] = (r, g, b)
    return img


def buscar_fuente(size, bold=False):
    if bold:
        candidatos = [
            "C:/Windows/Fonts/segoeuib.ttf",
            "C:/Windows/Fonts/arialbd.ttf",
        ]
    else:
        candidatos = [
            "C:/Windows/Fonts/seguisb.ttf",
            "C:/Windows/Fonts/segoeui.ttf",
            "C:/Windows/Fonts/arial.ttf",
        ]
    for f in candidatos:
        try:
            return ImageFont.truetype(f, size)
        except OSError:
            continue
    return ImageFont.load_default()


def make_play_icon():
    """Icono Play Store 512×512: cuadrado COMPLETO verde forest gradient con
    el símbolo en blanco encima. NO redondeamos los bordes — Play Store
    aplica su propia máscara según el launcher de cada usuario, y si
    redondeamos aquí, las esquinas quedan transparentes/blancas en la
    ficha de la Store."""
    simbolo = cargar_simbolo()
    lado = 512
    # Fondo verde a sangre completa (sin transparencia en los bordes)
    out = gradiente_diagonal((lado, lado), VERDE, VERDE_OSCURO).convert("RGBA")
    # Símbolo en blanco centrado
    sym_canvas = cuadrar_simbolo(simbolo, lado, padding_pct=0.15, bg=None)
    px = sym_canvas.load()
    for y in range(sym_canvas.height):
        for x in range(sym_canvas.width):
            r, g, b, a = px[x, y]
            if a > 0:
                px[x, y] = (255, 255, 255, a)
    out.paste(sym_canvas, (0, 0), sym_canvas)
    path = GFX / "icon-512.png"
    out.save(path, "PNG")
    print(f"Generado: {path}")


def make_feature_graphic():
    """Feature graphic 1024×500: símbolo en BLANCO sobre gradiente forest green
    (mismo estilo que el icon-512) + wordmark "ZeroHaus" a la derecha."""
    simbolo = cargar_simbolo()
    simbolo_blanco = _simbolo_blanco(simbolo)
    w, h = 1024, 500
    img = gradiente_diagonal((w, h), VERDE, VERDE_OSCURO)

    # Símbolo en blanco a la izquierda, ocupando casi todo el alto
    sym_size = 380
    sym_canvas = cuadrar_simbolo(simbolo_blanco, sym_size, padding_pct=0.05, bg=None)
    img.paste(sym_canvas, (60, (h - sym_size) // 2), sym_canvas)

    draw = ImageDraw.Draw(img)
    fuente_titulo = buscar_fuente(96, bold=True)
    fuente_sub = buscar_fuente(32)

    x_text = 500
    draw.text((x_text, 175), "ZeroHaus", font=fuente_titulo, fill=BLANCO)
    draw.text((x_text, 305), "eficiencia energética", font=fuente_sub, fill=(220, 252, 231))

    path = GFX / "feature-graphic-1024x500.png"
    img.save(path, "PNG")
    print(f"Generado: {path}")


def make_symbol_1024():
    """Símbolo recortado y limpio a 1024×1024 sobre fondo transparente, para uso libre."""
    simbolo = cargar_simbolo()
    canvas = cuadrar_simbolo(simbolo, 1024, padding_pct=0.06, bg=None)
    path = GFX / "symbol-1024.png"
    canvas.save(path, "PNG")
    print(f"Generado: {path}")


def _simbolo_blanco(simbolo):
    """Devuelve una copia del símbolo con todos los píxeles teñidos de blanco
    (manteniendo alpha)."""
    s = simbolo.copy().convert("RGBA")
    px = s.load()
    for y in range(s.height):
        for x in range(s.width):
            r, g, b, a = px[x, y]
            if a > 0:
                px[x, y] = (255, 255, 255, a)
    return s


def make_launcher_mipmaps():
    """Genera mipmap ic_launcher.webp en todas las densidades: fondo forest
    green con el símbolo en blanco encima."""
    simbolo = cargar_simbolo()
    simbolo_blanco = _simbolo_blanco(simbolo)
    densidades = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    res_dir = ROOT.parent / "app" / "src" / "main" / "res"
    for densidad, size in densidades.items():
        # Fondo verde con degradado
        fondo = gradiente_diagonal((size, size), VERDE, VERDE_OSCURO).convert("RGBA")
        sym_canvas = cuadrar_simbolo(simbolo_blanco, size, padding_pct=0.12, bg=None)

        # Versión cuadrada redondeada (ic_launcher.webp)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle(
            [0, 0, size, size], radius=int(size * 0.22), fill=255
        )
        out.paste(fondo, (0, 0), mask)
        out.paste(sym_canvas, (0, 0), sym_canvas)
        path = res_dir / f"mipmap-{densidad}" / "ic_launcher.webp"
        out.save(path, "WEBP", quality=95)
        print(f"Generado: {path}")

        # Versión redonda (ic_launcher_round.webp)
        round_canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(round_mask).ellipse([0, 0, size, size], fill=255)
        round_canvas.paste(fondo, (0, 0), round_mask)
        round_canvas.paste(sym_canvas, (0, 0), sym_canvas)
        round_path = res_dir / f"mipmap-{densidad}" / "ic_launcher_round.webp"
        round_canvas.save(round_path, "WEBP", quality=95)
        print(f"Generado: {round_path}")


def _simbolo_tintado(simbolo, color):
    """Devuelve una copia del símbolo con todos los píxeles teñidos al color
    indicado (manteniendo alpha)."""
    s = simbolo.copy().convert("RGBA")
    px = s.load()
    r0, g0, b0 = color
    for y in range(s.height):
        for x in range(s.width):
            r, g, b, a = px[x, y]
            if a > 0:
                px[x, y] = (r0, g0, b0, a)
    return s


def make_drawable_logo():
    """Genera el drawable WebP del logo in-app teñido al verde brand forest
    de ZeroHaus (#16A34A). Se ve sobre superficies blancas (login, perfil,
    splash dentro de la card blanca)."""
    simbolo = cargar_simbolo()
    simbolo_forest = _simbolo_tintado(simbolo, VERDE)
    out = cuadrar_simbolo(simbolo_forest, 512, padding_pct=0.04, bg=None)
    path = ROOT.parent / "app" / "src" / "main" / "res" / "drawable" / "zerohaus_logo.webp"
    out.save(path, "WEBP", quality=95)
    print(f"Generado: {path}")


def make_adaptive_icon_foreground():
    """Foreground del adaptive icon 432×432: símbolo en blanco sobre fondo
    transparente (el background.xml provee el verde forest)."""
    simbolo = cargar_simbolo()
    simbolo_blanco = _simbolo_blanco(simbolo)
    canvas = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    sym_size = 280  # ~65% del lado, dentro de la safe zone
    sym = cuadrar_simbolo(simbolo_blanco, sym_size, padding_pct=0.0, bg=None)
    canvas.paste(sym, ((432 - sym_size) // 2, (432 - sym_size) // 2), sym)
    path = ROOT.parent / "app" / "src" / "main" / "res" / "drawable" / "ic_launcher_foreground.png"
    canvas.save(path, "PNG")
    print(f"Generado: {path}")


if __name__ == "__main__":
    make_symbol_1024()
    make_play_icon()
    make_feature_graphic()
    make_launcher_mipmaps()
    make_drawable_logo()
    make_adaptive_icon_foreground()
    print("\nTodo listo.")
