#!/usr/bin/env python3
"""
Script para generar 10 carnés físicos de visitante con códigos de barras Code 128.
Los códigos generados (VIS-001 ... VIS-010) deben registrarse en el sistema
a través de la pestaña "Carnés" del módulo de Visitantes antes de usarlos.

Uso:
    python3 generate_visitor_badges.py

Salida:
    carnets_visitantes.pdf   — listo para imprimir, recortar y plastificar
"""

import os
from io import BytesIO

import barcode
from barcode.writer import ImageWriter
from PIL import Image
from reportlab.lib import colors
from reportlab.lib.pagesizes import letter
from reportlab.lib.units import inch
from reportlab.pdfgen import canvas

# ---------------------------------------------------------------------------
# Configuración
# ---------------------------------------------------------------------------

OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))
PDF_FILE   = os.path.join(OUTPUT_DIR, "carnets_visitantes.pdf")

# Prefijo y cantidad de carnés
BADGE_PREFIX = "VIS"
BADGE_COUNT  = 10
# Produce: VIS-001, VIS-002, ... VIS-010


def badge_codes(prefix: str, count: int) -> list[str]:
    """Genera la lista de códigos con cero-padding."""
    digits = len(str(count))          # 10 → 2 dígitos
    return [f"{prefix}-{str(i).zfill(digits)}" for i in range(1, count + 1)]


# ---------------------------------------------------------------------------
# Generación del código de barras
# ---------------------------------------------------------------------------

def generar_codigo_barras(codigo: str) -> Image.Image:
    """Devuelve una imagen PIL con el código de barras Code 128 del código dado."""
    CODE128 = barcode.get_barcode_class("code128")
    writer  = ImageWriter()
    code    = CODE128(codigo, writer=writer)

    buffer = BytesIO()
    code.write(buffer, options={
        "module_width":  0.5,
        "module_height": 18,
        "font_size":     12,
        "text_distance": 6,
        "quiet_zone":    8,
        "write_text":    True,
        "dpi":           300,
    })
    buffer.seek(0)
    return Image.open(buffer)


# ---------------------------------------------------------------------------
# Generación del PDF
# ---------------------------------------------------------------------------

def generar_pdf(codes: list[str], pdf_path: str) -> None:
    """
    Genera un PDF tamaño carta con los carnés de visitante listos para imprimir.
    Diseño: 2 columnas × 4 filas = 8 por página (2 páginas para 10 carnés).
    Cada carné tiene el ancho/alto de una tarjeta CR80 (85.6 × 54 mm).
    """
    c = canvas.Canvas(pdf_path, pagesize=letter)
    page_w, page_h = letter

    # Dimensiones del carné (CR80 aproximado)
    card_w = 3.37 * inch   # 85.6 mm
    card_h = 2.13 * inch   # 54 mm

    cols            = 2
    rows            = 4
    margin_x        = (page_w - cols * card_w) / (cols + 1)
    margin_y_top    = 0.5 * inch
    margin_y_bottom = 0.5 * inch
    gap_y           = (page_h - margin_y_top - margin_y_bottom - rows * card_h) / (rows - 1)

    # Colores del tema
    COLOR_HEADER = colors.HexColor("#1565C0")   # azul oscuro
    COLOR_ACCENT = colors.HexColor("#E3F2FD")   # azul muy claro
    COLOR_BORDER = colors.HexColor("#1976D2")   # azul medio

    idx = 0
    total = len(codes)

    while idx < total:
        for row in range(rows):
            for col in range(cols):
                if idx >= total:
                    break

                code = codes[idx]

                # Coordenadas (esquina inferior-izquierda del carné)
                x = margin_x + col * (card_w + margin_x)
                y = page_h - margin_y_top - (row + 1) * card_h - row * gap_y

                # --- Fondo del carné ---
                c.setFillColor(colors.white)
                c.setStrokeColor(COLOR_BORDER)
                c.setLineWidth(1.5)
                c.roundRect(x, y, card_w, card_h, radius=6, fill=1, stroke=1)

                # --- Encabezado azul ---
                header_h = 0.38 * inch
                c.setFillColor(COLOR_HEADER)
                # Clip superior redondeado manualmente con rect + rect superpuesto
                c.roundRect(x, y + card_h - header_h, card_w, header_h,
                            radius=6, fill=1, stroke=0)
                # Tapar la mitad inferior del roundRect del encabezado
                c.rect(x, y + card_h - header_h, card_w, header_h / 2, fill=1, stroke=0)

                c.setFillColor(colors.white)
                c.setFont("Helvetica-Bold", 10)
                c.drawCentredString(
                    x + card_w / 2,
                    y + card_h - header_h + 0.10 * inch,
                    "VISITANTE"
                )

                # --- Franja de código alfanumérico ---
                c.setFillColor(COLOR_ACCENT)
                badge_label_h = 0.22 * inch
                badge_label_y = y + card_h - header_h - badge_label_h
                c.rect(x, badge_label_y, card_w, badge_label_h, fill=1, stroke=0)

                c.setFillColor(COLOR_HEADER)
                c.setFont("Helvetica-Bold", 9)
                c.drawCentredString(
                    x + card_w / 2,
                    badge_label_y + 0.065 * inch,
                    f"Código: {code}"
                )

                # --- Código de barras ---
                try:
                    barcode_img  = generar_codigo_barras(code)
                    tmp_path = f"/tmp/visitor_barcode_{code.replace('-', '_')}.png"
                    barcode_img.save(tmp_path, dpi=(300, 300))

                    bc_w = card_w * 0.88
                    bc_h = 0.90 * inch
                    bc_x = x + (card_w - bc_w) / 2
                    bc_y = y + 0.08 * inch

                    c.drawImage(tmp_path, bc_x, bc_y, bc_w, bc_h,
                                preserveAspectRatio=True)
                    os.remove(tmp_path)

                except Exception as e:
                    print(f"  [!] Error generando barras para {code}: {e}")
                    c.setFont("Courier-Bold", 11)
                    c.setFillColor(colors.black)
                    c.drawCentredString(x + card_w / 2, y + 0.5 * inch, code)

                idx += 1

        if idx < total:
            c.showPage()

    c.save()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    print("=" * 55)
    print("  Generador de Carnés de Visitante")
    print("=" * 55)

    codes = badge_codes(BADGE_PREFIX, BADGE_COUNT)

    print(f"\nGenerando {BADGE_COUNT} carnés con prefijo '{BADGE_PREFIX}':")
    for code in codes:
        print(f"  • {code}")

    print(f"\nCreando PDF: {PDF_FILE}")
    generar_pdf(codes, PDF_FILE)

    print("\n" + "=" * 55)
    print("¡Listo!")
    print(f"  PDF  → {PDF_FILE}")
    print()
    print("Próximos pasos:")
    print("  1. Imprima el PDF, recorte y plastifique los carnés.")
    print("  2. En la app, vaya a 'Control de Visitantes' → pestaña")
    print("     'Carnés' e importe los códigos o agrégue uno por uno:")
    for code in codes:
        print(f"       {code}")
    print("  3. Al escanear un carné al ingreso el sistema lo")
    print("     marcará como 'En uso' y pedirá los datos del visitante.")
    print("=" * 55)


if __name__ == "__main__":
    main()
