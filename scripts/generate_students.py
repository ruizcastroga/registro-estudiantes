#!/usr/bin/env python3
"""
Script para generar estudiantes de prueba con cédulas costarricenses
y códigos de barras Code 128 en formato PDF.
"""

import csv
import random
import os
from io import BytesIO

# Importar librerías para barcodes y PDF
import barcode
from barcode.writer import ImageWriter
from PIL import Image
from reportlab.lib.pagesizes import letter
from reportlab.lib.units import inch, cm
from reportlab.pdfgen import canvas
from reportlab.lib import colors

# Directorio de salida
OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))
CSV_FILE = os.path.join(OUTPUT_DIR, "estudiantes_costa_rica.csv")
PDF_FILE = os.path.join(OUTPUT_DIR, "carnets_codigo_barras.pdf")

# Nombres y apellidos costarricenses comunes
NOMBRES_MASCULINOS = [
    "José", "Carlos", "Juan", "Luis", "Miguel", "David", "Daniel", "Andrés",
    "Diego", "Gabriel", "Sebastián", "Alejandro", "Matías", "Santiago", "Nicolás",
    "Esteban", "Felipe", "Ricardo", "Fernando", "Pablo", "Rodrigo", "Mauricio",
    "Adrián", "Emmanuel", "Bryan", "Kevin", "Steven", "Anthony", "Christopher"
]

NOMBRES_FEMENINOS = [
    "María", "Ana", "Laura", "Sofía", "Valeria", "Daniela", "Gabriela", "Fernanda",
    "Isabella", "Camila", "Valentina", "Lucía", "Paula", "Andrea", "Carolina",
    "Natalia", "Alejandra", "Stephanie", "Nicole", "Katherine", "Pamela", "Karla",
    "Mariela", "Priscilla", "Tatiana", "Melissa", "Kimberly", "Jennifer", "Jessica"
]

APELLIDOS = [
    "Rodríguez", "Hernández", "García", "Martínez", "López", "González", "Sánchez",
    "Ramírez", "Torres", "Flores", "Rivera", "Gómez", "Díaz", "Cruz", "Morales",
    "Reyes", "Jiménez", "Ruiz", "Vargas", "Castillo", "Romero", "Herrera", "Medina",
    "Aguilar", "Peña", "Chaves", "Solano", "Mora", "Arias", "Quesada", "Rojas",
    "Villalobos", "Vindas", "Zúñiga", "Calderón", "Monge", "Montero", "Alfaro",
    "Araya", "Barrantes", "Brenes", "Camacho", "Campos", "Carvajal", "Castro",
    "Chinchilla", "Cordero", "Corrales", "Esquivel", "Fallas", "Fernández"
]

GRADOS = [
    "Pre-Kinder", "Kinder", "Preparatoria",
    "1ro Primaria", "2do Primaria", "3ro Primaria", "4to Primaria", "5to Primaria", "6to Primaria",
    "7mo Secundaria", "8vo Secundaria", "9no Secundaria", "10mo Secundaria", "11vo Secundaria"
]

RELACIONES_TUTOR = ["Madre", "Padre", "Abuela", "Abuelo", "Tía", "Tío"]


def generar_cedula_cr():
    """Genera un número de cédula costarricense (9 dígitos)."""
    # Primer dígito: provincia (1-7)
    provincia = random.randint(1, 7)
    # Siguientes 8 dígitos: número aleatorio
    resto = random.randint(10000000, 99999999)
    return f"{provincia}{resto}"


def generar_telefono_cr():
    """Genera un número de teléfono costarricense."""
    # Celulares empiezan con 8 o 7
    prefijo = random.choice(["8", "7", "6"])
    numero = random.randint(1000000, 9999999)
    return f"{prefijo}{numero}"


def generar_estudiantes(cantidad=100):
    """Genera lista de estudiantes con datos aleatorios."""
    estudiantes = []
    cedulas_usadas = set()

    for i in range(cantidad):
        # Generar cédula única
        while True:
            cedula = generar_cedula_cr()
            if cedula not in cedulas_usadas:
                cedulas_usadas.add(cedula)
                break

        # Género aleatorio
        es_femenino = random.random() < 0.5
        nombre = random.choice(NOMBRES_FEMENINOS if es_femenino else NOMBRES_MASCULINOS)

        # Apellidos
        apellido1 = random.choice(APELLIDOS)
        apellido2 = random.choice(APELLIDOS)
        apellido = f"{apellido1} {apellido2}"

        # Grado (distribución realista)
        grado = random.choice(GRADOS)

        # Requiere acompañante (menores de 7mo generalmente sí)
        grados_requieren = ["Pre-Kinder", "Kinder", "Preparatoria", "1ro Primaria",
                           "2do Primaria", "3ro Primaria", "4to Primaria", "5to Primaria", "6to Primaria"]
        requiere_acompanante = "Sí" if grado in grados_requieren else "No"

        # Tutor (si requiere acompañante)
        if requiere_acompanante == "Sí":
            relacion = random.choice(RELACIONES_TUTOR)
            if relacion in ["Madre", "Abuela", "Tía"]:
                tutor_nombre = random.choice(NOMBRES_FEMENINOS)
            else:
                tutor_nombre = random.choice(NOMBRES_MASCULINOS)
            tutor_apellido = apellido1  # Mismo primer apellido
            tutor_nombre_completo = f"{tutor_nombre} {tutor_apellido}"
            tutor_telefono = generar_telefono_cr()
        else:
            tutor_nombre_completo = ""
            relacion = ""
            tutor_telefono = ""

        estudiantes.append({
            "codigo": cedula,
            "nombre": nombre,
            "apellido": apellido,
            "grado": grado,
            "requiere_acompanante": requiere_acompanante,
            "tutor_nombre": tutor_nombre_completo,
            "tutor_relacion": relacion,
            "tutor_telefono": tutor_telefono
        })

    return estudiantes


def guardar_csv(estudiantes, archivo):
    """Guarda los estudiantes en un archivo CSV."""
    with open(archivo, 'w', newline='', encoding='utf-8') as f:
        campos = ["codigo", "nombre", "apellido", "grado", "requiere_acompanante",
                  "tutor_nombre", "tutor_relacion", "tutor_telefono"]
        writer = csv.DictWriter(f, fieldnames=campos)
        writer.writeheader()
        writer.writerows(estudiantes)
    print(f"CSV guardado: {archivo}")


def generar_codigo_barras(codigo):
    """Genera imagen de código de barras Code128."""
    CODE128 = barcode.get_barcode_class('code128')

    # Crear código de barras
    writer = ImageWriter()
    code = CODE128(codigo, writer=writer)

    # Guardar en memoria
    buffer = BytesIO()
    code.write(buffer, options={
        'module_width': 0.4,
        'module_height': 15,
        'font_size': 10,
        'text_distance': 5,
        'quiet_zone': 6
    })
    buffer.seek(0)

    return Image.open(buffer)


def generar_pdf_carnets(estudiantes, archivo):
    """Genera PDF con carnets de código de barras."""
    c = canvas.Canvas(archivo, pagesize=letter)
    width, height = letter

    # Configuración del carnet
    carnet_width = 3.5 * inch
    carnet_height = 2.2 * inch
    margin = 0.5 * inch
    cards_per_row = 2
    cards_per_col = 4

    card_x_spacing = (width - 2 * margin) / cards_per_row
    card_y_spacing = (height - 2 * margin) / cards_per_col

    estudiante_idx = 0
    total_estudiantes = len(estudiantes)

    while estudiante_idx < total_estudiantes:
        # Nueva página
        for row in range(cards_per_col):
            for col in range(cards_per_row):
                if estudiante_idx >= total_estudiantes:
                    break

                est = estudiantes[estudiante_idx]

                # Posición del carnet
                x = margin + col * card_x_spacing
                y = height - margin - (row + 1) * card_y_spacing + 0.2 * inch

                # Dibujar borde del carnet
                c.setStrokeColor(colors.HexColor('#2196F3'))
                c.setLineWidth(2)
                c.roundRect(x, y, carnet_width, carnet_height, 10)

                # Título
                c.setFillColor(colors.HexColor('#2196F3'))
                c.rect(x, y + carnet_height - 0.4 * inch, carnet_width, 0.4 * inch, fill=1)
                c.setFillColor(colors.white)
                c.setFont("Helvetica-Bold", 10)
                c.drawCentredString(x + carnet_width/2, y + carnet_height - 0.28 * inch, "CARNET ESTUDIANTIL")

                # Nombre del estudiante
                c.setFillColor(colors.black)
                c.setFont("Helvetica-Bold", 11)
                nombre_completo = f"{est['nombre']} {est['apellido']}"
                if len(nombre_completo) > 30:
                    nombre_completo = nombre_completo[:27] + "..."
                c.drawCentredString(x + carnet_width/2, y + carnet_height - 0.7 * inch, nombre_completo)

                # Grado
                c.setFont("Helvetica", 9)
                c.setFillColor(colors.HexColor('#666666'))
                c.drawCentredString(x + carnet_width/2, y + carnet_height - 0.9 * inch, est['grado'])

                # Código de barras
                try:
                    barcode_img = generar_codigo_barras(est['codigo'])

                    # Guardar temporalmente
                    temp_path = f"/tmp/barcode_{est['codigo']}.png"
                    barcode_img.save(temp_path)

                    # Dibujar en PDF
                    barcode_width = 2.5 * inch
                    barcode_height = 0.8 * inch
                    barcode_x = x + (carnet_width - barcode_width) / 2
                    barcode_y = y + 0.2 * inch
                    c.drawImage(temp_path, barcode_x, barcode_y, barcode_width, barcode_height)

                    # Limpiar archivo temporal
                    os.remove(temp_path)
                except Exception as e:
                    print(f"Error generando código de barras para {est['codigo']}: {e}")
                    # Mostrar código como texto si falla
                    c.setFont("Courier-Bold", 12)
                    c.drawCentredString(x + carnet_width/2, y + 0.5 * inch, est['codigo'])

                estudiante_idx += 1

        if estudiante_idx < total_estudiantes:
            c.showPage()

    c.save()
    print(f"PDF guardado: {archivo}")
    print(f"Total de carnets generados: {total_estudiantes}")


def main():
    print("=" * 60)
    print("Generador de Estudiantes y Códigos de Barras")
    print("=" * 60)

    # Generar estudiantes
    print("\nGenerando 100 estudiantes con cédulas costarricenses...")
    estudiantes = generar_estudiantes(100)

    # Guardar CSV
    print("\nGuardando CSV...")
    guardar_csv(estudiantes, CSV_FILE)

    # Generar PDF con códigos de barras
    print("\nGenerando PDF con carnets...")
    generar_pdf_carnets(estudiantes, PDF_FILE)

    print("\n" + "=" * 60)
    print("¡Completado!")
    print(f"- CSV: {CSV_FILE}")
    print(f"- PDF: {PDF_FILE}")
    print("=" * 60)

    # Mostrar algunos ejemplos
    print("\nEjemplos de estudiantes generados:")
    for est in estudiantes[:5]:
        print(f"  {est['codigo']} - {est['nombre']} {est['apellido']} ({est['grado']})")


if __name__ == "__main__":
    main()
