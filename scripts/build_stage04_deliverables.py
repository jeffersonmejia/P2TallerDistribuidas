from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import (
    Image, PageBreak, Paragraph, Preformatted, SimpleDocTemplate, Spacer, Table, TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
PDF_PATH = ROOT / "output" / "pdf" / "Taller_Consistencia_MejiaChavezJeffersonPaul.pdf"
ZIP_PATH = ROOT / "output" / "zip" / "Proyecto_MejiaChavezJeffersonPaul.zip"
BACKUP = ROOT / "database" / "backup" / "BD_MejiaChavezJeffersonPaul.backup"
REPO_URL = "https://github.com/jeffersonmejia/P2TallerDistribuidas"

GREEN = colors.HexColor("#315d4d")
INK = colors.HexColor("#1f2924")
MUTED = colors.HexColor("#65736b")
PALE = colors.HexColor("#eef4ef")
LINE = colors.HexColor("#d9e1da")


def build_pdf():
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle(name="CoverTitle", parent=styles["Title"], fontName="Helvetica-Bold",
                              fontSize=27, leading=32, textColor=GREEN, alignment=TA_CENTER, spaceAfter=12))
    styles.add(ParagraphStyle(name="Deck", parent=styles["Normal"], fontSize=12, leading=18,
                              textColor=MUTED, alignment=TA_CENTER, spaceAfter=12))
    styles.add(ParagraphStyle(name="H1x", parent=styles["Heading1"], fontSize=19, leading=23,
                              textColor=GREEN, spaceBefore=8, spaceAfter=9))
    styles.add(ParagraphStyle(name="H2x", parent=styles["Heading2"], fontSize=12, leading=15,
                              textColor=INK, spaceBefore=7, spaceAfter=5))
    styles.add(ParagraphStyle(name="Bodyx", parent=styles["BodyText"], fontSize=9.2, leading=13.5,
                              textColor=INK, spaceAfter=6))
    styles.add(ParagraphStyle(name="Smallx", parent=styles["BodyText"], fontSize=7.6, leading=10,
                              textColor=MUTED, spaceAfter=3))
    styles.add(ParagraphStyle(name="Cellx", parent=styles["BodyText"], fontSize=7.5, leading=10,
                              textColor=INK))
    styles.add(ParagraphStyle(name="CodeLine", parent=styles["Code"], fontName="Courier", fontSize=7.5,
                              leading=10.5, textColor=colors.HexColor("#334b3e"), backColor=PALE,
                              borderPadding=5))
    doc = SimpleDocTemplate(str(PDF_PATH), pagesize=letter, rightMargin=48, leftMargin=48,
                            topMargin=48, bottomMargin=46, title="Taller de consistencia — Restaurant DBMS",
                            author="Jefferson Mejía")
    story = []

    story += [Spacer(1, 0.6 * inch), Paragraph("Taller de consistencia", styles["CoverTitle"]),
              Paragraph("Restaurant DBMS · transacciones PostgreSQL, pagos simulados y patrones", styles["Deck"]),
              Spacer(1, 0.25 * inch)]
    cover = Table([
        [Paragraph("BASE DE DATOS", styles["Smallx"]), Paragraph("Restaurant DBMS", styles["Bodyx"])],
        [Paragraph("AUTOR", styles["Smallx"]), Paragraph("Jefferson Mejía", styles["Bodyx"])],
        [Paragraph("FECHA DE VALIDACIÓN", styles["Smallx"]), Paragraph("22 de noviembre de 2026", styles["Bodyx"])],
        [Paragraph("REPOSITORIO", styles["Smallx"]), Paragraph(f'<link href="{REPO_URL}" color="#315d4d">{REPO_URL}</link>', styles["Bodyx"])],
        [Paragraph("ENTORNO", styles["Smallx"]), Paragraph("Java 25 · Spring Boot 3.5.6 · PostgreSQL 16 · React 19 · Vite 7", styles["Bodyx"])],
    ], colWidths=[1.55 * inch, 4.9 * inch])
    cover.setStyle(TableStyle([("BACKGROUND", (0, 0), (-1, -1), PALE), ("BOX", (0, 0), (-1, -1), .6, LINE),
                               ("INNERGRID", (0, 0), (-1, -1), .35, LINE), ("VALIGN", (0, 0), (-1, -1), "TOP"),
                               ("LEFTPADDING", (0, 0), (-1, -1), 12), ("RIGHTPADDING", (0, 0), (-1, -1), 12),
                               ("TOPPADDING", (0, 0), (-1, -1), 10), ("BOTTOMPADDING", (0, 0), (-1, -1), 5)]))
    story += [cover, Spacer(1, 0.35 * inch),
              Paragraph("El sistema administra órdenes y pagos de demostración. Las confirmaciones al simulador son ficticias y no representan cobros reales.", styles["Bodyx"]),
              Paragraph("La aplicación no tiene login. DEMO_OPERATOR identifica exclusivamente al actor fijo de auditoría académica.", styles["Bodyx"]),
              PageBreak()]

    story += [Paragraph("Arquitectura y base de datos", styles["H1x"]),
              Paragraph("El backend se organiza por features: orders, payments, customers, catalog, audit y demonstrations. Cada feature separa dominio, aplicación, infraestructura y presentación donde corresponde. Los casos de uso dependen de puertos; persistencia, HTTP y notificación implementan adaptadores.", styles["Bodyx"])]
    architecture = [
        ["Interfaz", "React + Vite + Material Design 3, idioma español"],
        ["API", "Controllers REST, DTOs y manejo centralizado de errores"],
        ["Aplicación", "Casos de uso, puertos, límites transaccionales y eventos"],
        ["Infraestructura", "JPA/Hibernate, Flyway, PostgreSQL, simulador HTTP y Resilience4j"],
        ["Proveedor", "Proceso Node local en 127.0.0.1:8090; solo simula autorizaciones"],
    ]
    story.append(make_table(architecture, [1.25 * inch, 5.2 * inch], styles))
    story += [Spacer(1, 7), Paragraph("Modelo", styles["H2x"]),
              Paragraph("La base conserva las tablas originales de clientes, órdenes, estados, menú, pagos y detalles. Flyway ejecuta el SQL original de estructura y seeds en V1/V2; V3 agrega Audit_Events. Payments.order_id conserva su unicidad, y las claves foráneas y restricciones CHECK se validan en cada restauración. Hibernate usa ddl-auto=validate.", styles["Bodyx"]),
              Paragraph("Flujo de pago", styles["H2x"]),
              Paragraph("La API calcula el total con BigDecimal, llama al simulador fuera de la transacción SQL larga y luego vuelve a bloquear la misma orden. La confirmación persiste pago y estado dentro de una transacción; el evento ORDER_COMPLETED se publica después del commit. Una excepción inyectada tras el flush revierte el pago y el estado.", styles["Bodyx"]),
              Paragraph("Evidencia de implementación (texto seleccionable)", styles["H2x"])]
    code_lines = [
        '<font color="#315d4d"><b>@Transactional</b></font>',
        '<font color="#315d4d">public</font> OrderPaymentResult finish(int orderId, String method,',
        '        BigDecimal authorizedAmount, boolean injectFailure) {',
        '    OrderAggregate order = orders.findForUpdate(orderId)',
        '        .orElseThrow(() -&gt; OrderException.notFound(...));',
        '    <font color="#78877e"><i>// Revalidar y persistir bajo el bloqueo de la orden.</i></font>',
        '    commitHook.afterWrites(injectFailure);',
        '    events.publishEvent(new OrderCompletedEvent(...));',
        '}',
    ]
    code_rows = [[Paragraph(line, styles["CodeLine"])] for line in code_lines]
    code_table = Table(code_rows, colWidths=[6.45 * inch], hAlign="LEFT")
    code_table.setStyle(TableStyle([("BACKGROUND", (0, 0), (-1, -1), PALE),
                                    ("LEFTPADDING", (0, 0), (-1, -1), 8),
                                    ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                                    ("TOPPADDING", (0, 0), (-1, -1), 2),
                                    ("BOTTOMPADDING", (0, 0), (-1, -1), 2)]))
    story.append(code_table)
    story += [Paragraph("Fuente: app/src/main/java/edu/restaurant/features/orders/application/FinalizeOrderPayment.java", styles["Smallx"]), PageBreak()]

    story += [Paragraph("ACID y SOLID", styles["H1x"])]
    acid = [
        ["Propiedad", "Demostración", "Resultado observado"],
        ["Atomicidad", "COMMIT y fallo después del flush", "Orden #22 completada con 1 pago; rollback #19 pendiente, 0 pagos"],
        ["Consistencia", "Cantidad cero, orden ausente y autorización incoherente", "HTTP 400 / 404 / 409; sin pago confirmado"],
        ["Aislamiento", "Dos solicitudes HTTP simultáneas con bloqueo pesimista", "Una HTTP 201; otra HTTP 409; un pago único"],
        ["Durabilidad", "Reinicio del backend y PostgreSQL con volumen conservado", "Orden demo #21 continúa completed, con pago y evento"],
    ]
    story.append(make_table(acid, [0.95 * inch, 2.35 * inch, 3.2 * inch], styles))
    story += [Spacer(1, 8), Paragraph("Cinco principios SOLID", styles["H2x"])]
    solid = [
        ["SRP", "Controllers, casos de uso, persistencia y auditoría tienen responsabilidades separadas."],
        ["OCP", "PaymentGateway permite cambiar el adaptador sin alterar el caso de uso."],
        ["LSP", "Notificación de consola y memoria cumplen el mismo contrato y comparten pruebas."],
        ["ISP", "Puertos pequeños distinguen autorización, auditoría, evidencia y notificación."],
        ["DIP", "Aplicación y dominio usan contratos; infraestructura los implementa."],
    ]
    story.append(make_table(solid, [0.7 * inch, 5.75 * inch], styles))
    story += [Paragraph("Patrones", styles["H2x"]),
              Paragraph("Proxy: PaymentServiceProxy registra operación y actor antes de delegar. Observer: AuditObserver y NotificationObserver reaccionan en AFTER_COMMIT. Adapter: HttpPaymentGatewayAdapter transforma el contrato interno a value/code/operationKey. Circuit Breaker: Resilience4j corta llamadas HTTP en OPEN y permite una sonda real HALF_OPEN.", styles["Bodyx"]),
              Paragraph("Fallo externo", styles["H2x"]),
              Paragraph("Cuando el simulador rechaza o no está disponible, la orden permanece pendiente y no se inserta pago confirmado. Las claves de operación hacen idempotente la autorización simulada.", styles["Bodyx"]), PageBreak()]

    story += [Paragraph("Evidencia visual del laboratorio", styles["H1x"]),
              Paragraph("Cada captura procede de la interfaz local conectada al backend y PostgreSQL del entorno de validación. Los resultados incluyen correlación, hora y conteos SQL antes/después.", styles["Bodyx"])]
    lab_image = ROOT / "docs" / "evidence" / "AT-laboratory.png"
    story.append(Image(str(lab_image), width=6.45 * inch, height=7.16 * inch, kind="proportional"))
    story += [Spacer(1, 4), Paragraph("Panel de escenarios local. El estado del simulador, el breaker y las órdenes LAB04 provienen de los servicios activos.", styles["Smallx"]), PageBreak(),
              Paragraph("CRUD y datos identificables", styles["H1x"]),
              Paragraph("La pantalla de órdenes consulta PostgreSQL. Las filas LABORATORIO DEMO identifican las operaciones del laboratorio; se conservan después de completar o cancelar.", styles["Bodyx"]),
              Image(str(ROOT / "docs" / "evidence" / "AT01-orders.png"), width=6.45 * inch, height=5.45 * inch, kind="proportional"),
              Spacer(1, 4), Paragraph("Captura de órdenes con IDs, fecha, estado y total. La lista muestra el commit #22, la orden durable #21 y órdenes pendientes de rollback.", styles["Smallx"]), PageBreak()]

    story += [Paragraph("COMMIT y ROLLBACK en PostgreSQL", styles["H1x"]),
              Paragraph("COMMIT · orden #22 · 22 nov 2026, 12:12 p. m. · HTTP 201 para crear y confirmar · estado completed · 1 detalle · 1 pago · 1 evento ORDER_COMPLETED.", styles["Bodyx"]),
              Image(str(ROOT / "docs" / "evidence" / "AT02-commit.png"), width=6.45 * inch, height=5.45 * inch, kind="proportional"),
              Spacer(1, 4), Paragraph("El panel muestra la orden #22 completada, la correlación, la hora y las consultas SQL antes/después del commit.", styles["Smallx"]), PageBreak(),
              Paragraph("ROLLBACK después de las escrituras", styles["H1x"]),
              Paragraph("Orden #19 · 22 nov 2026, 12:08 p. m. · HTTP 500 en la confirmación inyectada después de escrituras · estado pending · 1 detalle · 0 pagos · 0 eventos de completado.", styles["Bodyx"])]
    story.append(Image(str(ROOT / "docs" / "evidence" / "AT03-rollback.png"), width=6.45 * inch, height=5.45 * inch, kind="proportional"))
    story += [Spacer(1, 3), Paragraph("Captura auténtica de la vista del rollback: identifica la orden #19, la correlación, el instante, HTTP 500 y la lectura SQL sin cambios parciales.", styles["Smallx"]),
              Paragraph("Consulta SQL reproducible", styles["H2x"]),
              Preformatted('''SELECT o."order_id", s."status_name",
       (SELECT count(*) FROM "Payments" p WHERE p."order_id"=o."order_id") AS pagos,
       (SELECT count(*) FROM "Audit_Events" e WHERE e."order_id"=o."order_id"
          AND e."action"='ORDER_COMPLETED') AS eventos_commit
FROM "Orders" o JOIN "Order_Status" s USING ("status_id")
WHERE o."order_id" IN (19, 21, 22);''', styles["CodeLine"]), PageBreak()]

    story += [Paragraph("Resultados y conclusiones", styles["H1x"])]
    cases = [
        ["Caso", "Verificación"],
        ["AT-01 a AT-04", "CRUD lógico y rechazo de entradas/importes inválidos."],
        ["AT-05 a AT-09", "Carrera de dos clientes, persistencia tras reinicio, Proxy, dos Observer y Adapter."],
        ["AT-10 a AT-12", "CLOSED → OPEN → HALF_OPEN → CLOSED; una sonda fallida devuelve a OPEN."],
        ["AT-13", "Backup custom restaurado: 12 tablas, 42 restricciones, 22 órdenes, 20 detalles, 15 pagos y 23 eventos."],
        ["AT-14", "ZIP extraído y reconstruido con los comandos del README; sin secretos, builds ni dependencias."],
    ]
    story.append(make_table(cases, [1.3 * inch, 5.15 * inch], styles))
    story += [Spacer(1, 9), Paragraph("Validación automatizada", styles["H2x"]),
              Paragraph("mvn -q test: 27 pruebas, 0 fallos. npm run lint y npm run build: exit 0. npm test del simulador: 4 pruebas, 0 fallos. Testcontainers ejecutó PostgreSQL 16 y las pruebas de integración HTTP.", styles["Bodyx"]),
              Paragraph("La aplicación demuestra atomicidad, consistencia, aislamiento y durabilidad con consultas directas a PostgreSQL. El modo demo permanece local; no se añadieron autenticación, inventario ni servicios externos de cobro.", styles["Bodyx"]),
              Paragraph("Archivos de entrega: Taller_Consistencia_MejiaChavezJeffersonPaul.pdf y Proyecto_MejiaChavezJeffersonPaul.zip. El backup va dentro del ZIP.", styles["Bodyx"])]
    doc.build(story, onFirstPage=draw_footer, onLaterPages=draw_footer)


def make_table(rows, widths, styles):
    data = []
    for row_index, row in enumerate(rows):
        cells = []
        for value in row:
            text = str(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            cells.append(Paragraph(text, styles["Cellx"]))
        data.append(cells)
    table = Table(data, colWidths=widths, repeatRows=1 if rows and len(rows[0]) > 2 else 0, hAlign="LEFT")
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), PALE), ("TEXTCOLOR", (0, 0), (-1, 0), GREEN),
        ("BOX", (0, 0), (-1, -1), .55, LINE), ("INNERGRID", (0, 0), (-1, -1), .35, LINE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"), ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6), ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    return table


def draw_footer(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(LINE)
    canvas.line(48, 34, letter[0] - 48, 34)
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(MUTED)
    canvas.drawString(48, 22, "Restaurant DBMS · Taller de consistencia")
    canvas.drawRightString(letter[0] - 48, 22, f"Página {doc.page}")
    canvas.restoreState()


def build_zip():
    excluded_dirs = {".git", "node_modules", "target", "dist", "coverage", "output", "__pycache__", ".idea", ".vscode"}
    excluded_files = {".env", "Thumbs.db", ".DS_Store"}
    if not BACKUP.is_file():
        raise FileNotFoundError(f"Falta el backup requerido: {BACKUP}")
    with ZipFile(ZIP_PATH, "w", compression=ZIP_DEFLATED, compresslevel=8) as archive:
        for path in sorted(ROOT.rglob("*")):
            if not path.is_file():
                continue
            rel = path.relative_to(ROOT)
            if any(part in excluded_dirs for part in rel.parts):
                continue
            if path.name in excluded_files or path.suffix.lower() in {".log", ".class", ".jar", ".pyc"}:
                continue
            if path.name.startswith(".env.") and path.name != ".env.example":
                continue
            archive.write(path, Path("P2TallerDistribuidas") / rel)


def main():
    (ROOT / "output" / "pdf").mkdir(parents=True, exist_ok=True)
    (ROOT / "output" / "zip").mkdir(parents=True, exist_ok=True)
    build_pdf()
    build_zip()
    print(f"PDF={PDF_PATH.relative_to(ROOT)} ({PDF_PATH.stat().st_size} bytes)")
    print(f"ZIP={ZIP_PATH.relative_to(ROOT)} ({ZIP_PATH.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
