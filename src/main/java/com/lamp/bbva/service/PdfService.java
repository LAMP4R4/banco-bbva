package com.lamp.bbva.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.lamp.bbva.entity.movimientosEntity;
import com.lamp.bbva.entity.usuarioEntity;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

// Genera en PDF los comprobantes y reportes formales del banco (sin adornos
// graficos, solo texto y tablas) usando OpenPDF.
@Service
public class PdfService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale LOCALE_MX = new Locale("es", "MX");
    private static final Color COLOR_BANCO = new Color(15, 33, 71);
    private static final Color COLOR_GRIS_CLARO = new Color(240, 240, 240);

    public byte[] generarComprobanteMovimiento(movimientosEntity movimiento) {
        Document documento = new Document(PageSize.A5, 40, 40, 60, 40);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            agregarEncabezado(documento, "Comprobante de Movimiento");

            Paragraph folio = new Paragraph("Folio: #" + movimiento.getId(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY));
            folio.setSpacingAfter(15);
            documento.add(folio);

            PdfPTable tabla = new PdfPTable(2);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[] { 1f, 1.6f });

            agregarFilaDato(tabla, "Fecha", movimiento.getFecha().format(FORMATO_FECHA));
            agregarFilaDato(tabla, "Tipo de movimiento", movimiento.getTipo());
            agregarFilaDato(tabla, "Descripción", movimiento.getDescripcion());
            agregarFilaDato(tabla, "Cuenta origen", movimiento.getCuentaOrigen());
            agregarFilaDato(tabla, "Cuenta destino", movimiento.getCuentaDestino());
            agregarFilaDato(tabla, "Monto", "$" + formatearMonto(movimiento.getMonto()) + " MXN");
            agregarFilaDato(tabla, "Estado", movimiento.getEstadoMovimiento());
            documento.add(tabla);

            Paragraph nota = new Paragraph(
                    "Este comprobante fue generado electrónicamente por el sistema y no requiere firma autógrafa.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY));
            nota.setSpacingBefore(30);
            documento.add(nota);

            documento.close();
        } catch (DocumentException e) {
            throw new RuntimeException("No se pudo generar el comprobante.", e);
        }
        return salida.toByteArray();
    }

    public byte[] generarHistorialCliente(usuarioEntity usuario, String clabe, List<movimientosEntity> movimientos) {
        Document documento = new Document(PageSize.A4, 40, 40, 60, 40);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            agregarEncabezado(documento, "Historial de Gastos y Ganancias");
            documento.add(datosIdentificacion(usuario, clabe));

            double totalIngresos = 0;
            double totalGastos = 0;

            if (movimientos.isEmpty()) {
                documento.add(new Paragraph("Aún no hay movimientos registrados.", fuenteTexto()));
            } else {
                PdfPTable tabla = new PdfPTable(5);
                tabla.setWidthPercentage(100);
                tabla.setWidths(new float[] { 1.3f, 3f, 1.5f, 1.5f, 1.5f });
                agregarEncabezadoTabla(tabla, "Fecha", "Descripción", "Tipo", "Monto", "Estado");

                for (movimientosEntity mov : movimientos) {
                    boolean esIngreso = clabe.equals(mov.getCuentaDestino());
                    if (esIngreso) {
                        totalIngresos += mov.getMonto();
                    } else {
                        totalGastos += mov.getMonto();
                    }

                    tabla.addCell(celda(mov.getFecha().format(FORMATO_FECHA)));
                    tabla.addCell(celda(mov.getDescripcion()));
                    tabla.addCell(celda(mov.getTipo()));
                    tabla.addCell(celda((esIngreso ? "+$" : "-$") + formatearMonto(mov.getMonto())));
                    tabla.addCell(celda(mov.getEstadoMovimiento()));
                }
                documento.add(tabla);
            }

            Paragraph resumen = new Paragraph();
            resumen.setSpacingBefore(20);
            resumen.add(new Chunk("Total de ganancias (ingresos): ", fuenteEtiqueta()));
            resumen.add(new Chunk("$" + formatearMonto(totalIngresos) + " MXN\n", fuenteTexto()));
            resumen.add(new Chunk("Total de gastos: ", fuenteEtiqueta()));
            resumen.add(new Chunk("$" + formatearMonto(totalGastos) + " MXN\n", fuenteTexto()));
            resumen.add(new Chunk("Balance neto: ", fuenteEtiqueta()));
            resumen.add(new Chunk("$" + formatearMonto(totalIngresos - totalGastos) + " MXN", fuenteTexto()));
            documento.add(resumen);

            documento.close();
        } catch (DocumentException e) {
            throw new RuntimeException("No se pudo generar el historial.", e);
        }
        return salida.toByteArray();
    }

    public byte[] generarReporteClientes(List<usuarioEntity> clientes,
            Map<Long, List<movimientosEntity>> historialPorCliente) {
        Document documento = new Document(PageSize.A4, 40, 40, 60, 40);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            agregarEncabezado(documento, "Reporte de Cartera de Clientes");

            Paragraph totalClientes = new Paragraph("Total de clientes registrados: " + clientes.size(),
                    fuenteEtiqueta());
            totalClientes.setSpacingAfter(15);
            documento.add(totalClientes);

            if (clientes.isEmpty()) {
                documento.add(new Paragraph("Aún no hay clientes registrados.", fuenteTexto()));
            }

            for (usuarioEntity cliente : clientes) {
                boolean tieneCuenta = cliente.getCuentas() != null && !cliente.getCuentas().isEmpty();
                String clabe = tieneCuenta ? cliente.getCuentas().get(0).getClabe() : "Sin cuenta asignada";
                double saldo = tieneCuenta ? cliente.getCuentas().get(0).getSaldo() : 0.0;
                String estadoCuenta = tieneCuenta ? cliente.getCuentas().get(0).getEstado() : "-";

                Paragraph nombreCliente = new Paragraph(cliente.getNombre() + " (" + cliente.getUserName() + ")",
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_BANCO));
                nombreCliente.setSpacingBefore(18);
                nombreCliente.setSpacingAfter(4);
                documento.add(nombreCliente);

                Paragraph datosCuenta = new Paragraph(
                        "CLABE: " + clabe + "   |   Saldo: $" + formatearMonto(saldo) + " MXN   |   Estado: "
                                + estadoCuenta,
                        fuenteTexto());
                datosCuenta.setSpacingAfter(8);
                documento.add(datosCuenta);

                List<movimientosEntity> movimientos = historialPorCliente.getOrDefault(cliente.getId(), List.of());
                if (movimientos.isEmpty()) {
                    documento.add(new Paragraph("Sin movimientos registrados.",
                            FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY)));
                } else {
                    PdfPTable tabla = new PdfPTable(4);
                    tabla.setWidthPercentage(100);
                    tabla.setWidths(new float[] { 1.3f, 3f, 1.3f, 1.3f });
                    agregarEncabezadoTabla(tabla, "Fecha", "Descripción", "Tipo", "Monto");
                    for (movimientosEntity mov : movimientos) {
                        boolean esIngreso = clabe.equals(mov.getCuentaDestino());
                        tabla.addCell(celda(mov.getFecha().format(FORMATO_FECHA)));
                        tabla.addCell(celda(mov.getDescripcion()));
                        tabla.addCell(celda(mov.getTipo()));
                        tabla.addCell(celda((esIngreso ? "+$" : "-$") + formatearMonto(mov.getMonto())));
                    }
                    documento.add(tabla);
                }
            }

            documento.close();
        } catch (DocumentException e) {
            throw new RuntimeException("No se pudo generar el reporte.", e);
        }
        return salida.toByteArray();
    }

    private void agregarEncabezado(Document documento, String titulo) throws DocumentException {
        Paragraph banco = new Paragraph("Banco BBVA", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, COLOR_BANCO));
        documento.add(banco);

        Paragraph subtitulo = new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA, 13, Color.DARK_GRAY));
        subtitulo.setSpacingAfter(4);
        documento.add(subtitulo);

        Paragraph fechaEmision = new Paragraph("Fecha de emisión: " + LocalDate.now().format(FORMATO_FECHA),
                FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
        fechaEmision.setSpacingAfter(18);
        documento.add(fechaEmision);
    }

    private Paragraph datosIdentificacion(usuarioEntity usuario, String clabe) {
        Paragraph datos = new Paragraph();
        datos.add(new Chunk("Cliente: ", fuenteEtiqueta()));
        datos.add(new Chunk(usuario.getNombre() + "\n", fuenteTexto()));
        datos.add(new Chunk("Usuario: ", fuenteEtiqueta()));
        datos.add(new Chunk(usuario.getUserName() + "\n", fuenteTexto()));
        datos.add(new Chunk("CLABE: ", fuenteEtiqueta()));
        datos.add(new Chunk(clabe, fuenteTexto()));
        datos.setSpacingAfter(15);
        return datos;
    }

    private void agregarFilaDato(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell celdaEtiqueta = new PdfPCell(new Phrase(etiqueta, fuenteEtiqueta()));
        celdaEtiqueta.setBorder(Rectangle.BOTTOM);
        celdaEtiqueta.setBackgroundColor(COLOR_GRIS_CLARO);
        celdaEtiqueta.setPadding(6);
        tabla.addCell(celdaEtiqueta);

        PdfPCell celdaValor = new PdfPCell(new Phrase(valor != null ? valor : "-", fuenteTexto()));
        celdaValor.setBorder(Rectangle.BOTTOM);
        celdaValor.setPadding(6);
        tabla.addCell(celdaValor);
    }

    private void agregarEncabezadoTabla(PdfPTable tabla, String... titulos) {
        for (String titulo : titulos) {
            PdfPCell celda = new PdfPCell(new Phrase(titulo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
            celda.setBackgroundColor(COLOR_BANCO);
            celda.setPadding(6);
            tabla.addCell(celda);
        }
    }

    private PdfPCell celda(String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto != null ? texto : "-", fuenteTexto()));
        celda.setPadding(5);
        return celda;
    }

    private org.openpdf.text.Font fuenteTexto() {
        return FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    }

    private org.openpdf.text.Font fuenteEtiqueta() {
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
    }

    private String formatearMonto(double monto) {
        return String.format(LOCALE_MX, "%,.2f", monto);
    }

}
