package lsi.ubu.servicios;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lsi.ubu.util.ExecuteScript;
import lsi.ubu.util.PoolDeConexiones;

/**
 * GestionDonacionesSangre: Implementa la gestion de donaciones de sangre según
 * el enunciado del ejercicio
 * 
 * @author <a href="mailto:jmaudes@ubu.es">Jesus Maudes</a>
 * @author <a href="mailto:rmartico@ubu.es">Raul Marticorena</a>
 * @author <a href="mailto:pgdiaz@ubu.es">Pablo Garcia</a>
 * @author <a href="mailto:srarribas@ubu.es">Sandra Rodriguez</a>
 * @version 1.5
 * @since 1.0
 */
public class EsqueletoGestionDonacionesSangre {

	private static Logger logger = LoggerFactory.getLogger(EsqueletoGestionDonacionesSangre.class);

	private static final String script_path = "sql/";

	public static void main(String[] args) throws SQLException {
		tests();

		System.out.println("FIN.............");
	}

	public static void realizar_donacion(String m_NIF, int m_ID_Hospital, float m_Cantidad, Date m_Fecha_Donacion)
			throws SQLException {

		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con = null;
		PreparedStatement st = null;
		ResultSet rs = null;

		try {
			con = pool.getConnection();
			con.setAutoCommit(false); // Empezamos transaccion

			// 1. Validar cantidad maxima (0.45 litros)
			if (m_Cantidad > 0.45f) {
				throw new GestionDonacionesSangreException(
						GestionDonacionesSangreException.VALOR_CANTIDAD_DONACION_INCORRECTO);
			}

			// 2. Comprobar si el donante existe
			st = con.prepareStatement("SELECT COUNT(*) FROM donante WHERE nif = ?");
			st.setString(1, m_NIF);
			rs = st.executeQuery();
			if (rs.next() && rs.getInt(1) == 0) {
				throw new GestionDonacionesSangreException(GestionDonacionesSangreException.DONANTE_NO_EXISTE);
			}
			st.close();

			// 3. Comprobar si el hospital existe
			st = con.prepareStatement("SELECT COUNT(*) FROM hospital WHERE id_hospital = ?");
			st.setInt(1, m_ID_Hospital);
			rs = st.executeQuery();
			if (rs.next() && rs.getInt(1) == 0) {
				throw new GestionDonacionesSangreException(GestionDonacionesSangreException.HOSPITAL_NO_EXISTE);
			}
			st.close();

			// 4. Comprobar regla de los 15 dias
			st = con.prepareStatement(
				    "SELECT TRUNC(?) - TRUNC(MAX(fecha_donacion)) " +
				    "FROM donacion WHERE nif_donante = ?");
				st.setDate(1, new java.sql.Date(m_Fecha_Donacion.getTime()));
				st.setString(2, m_NIF);
				rs = st.executeQuery();
				if (rs.next() && rs.getObject(1) != null) {
				    int dias = rs.getInt(1);
				    if (dias < 15) {
				        throw new GestionDonacionesSangreException(GestionDonacionesSangreException.DONANTE_EXCEDE);
				    }
				}
				st.close();

			// 5. Insertar la donacion
			st = con.prepareStatement(
					"INSERT INTO donacion (id_donacion, nif_donante, cantidad, fecha_donacion) VALUES (seq_donacion.nextval, ?, ?, ?)");
			st.setString(1, m_NIF);
			st.setFloat(2, m_Cantidad);
			// Convertimos m_Fecha_Donacion (a java.sql porque me daba error no sé porqué
			st.setDate(3, new java.sql.Date(m_Fecha_Donacion.getTime()));
			st.executeUpdate();
			st.close();

			// 6. Incrementar reserva del hospital
			st = con.prepareStatement("UPDATE reserva_hospital SET cantidad = cantidad + ? WHERE id_hospital = ?");
			st.setFloat(1, m_Cantidad);
			st.setInt(2, m_ID_Hospital);
			st.executeUpdate();

			con.commit();

		} catch (SQLException e) {
			if (con != null)
				con.rollback();

			throw e;

		} finally {
			// Cerramos todo en el finally
			if (rs != null)
				rs.close();
			if (st != null)
				st.close();
			if (con != null)
				con.close();
		}
	}

	public static void anular_traspaso(int m_ID_Tipo_Sangre, int m_ID_Hospital_Origen, int m_ID_Hospital_Destino,
			Date m_Fecha_Traspaso) throws SQLException {

		PoolDeConexiones pool = PoolDeConexiones.getInstance();

		Connection con = null;
		PreparedStatement psHospital = null;
		PreparedStatement psTipoSangre = null;
		PreparedStatement psSelectTrasp = null;
		PreparedStatement psRestaDestino = null;
		PreparedStatement psSumaOrigen = null;
		PreparedStatement psDelete = null;

		try {
			con = pool.getConnection();
			con.setAutoCommit(false);

			// 1. Validar que el hospital ORIGEN existe
			psHospital = con.prepareStatement("SELECT COUNT(*) FROM hospital WHERE id_hospital = ?");

			psHospital.setInt(1, m_ID_Hospital_Origen);
			ResultSet rsHosp = psHospital.executeQuery();
			rsHosp.next();
			if (rsHosp.getInt(1) == 0) {
				throw new GestionDonacionesSangreException(GestionDonacionesSangreException.HOSPITAL_NO_EXISTE);
			}
			rsHosp.close();

			// 2. Validar que el hospital DESTINO existe
			psHospital.setInt(1, m_ID_Hospital_Destino);
			rsHosp = psHospital.executeQuery();
			rsHosp.next();
			if (rsHosp.getInt(1) == 0) {
				throw new GestionDonacionesSangreException(GestionDonacionesSangreException.HOSPITAL_NO_EXISTE);
			}
			rsHosp.close();

			// 3. Validar que el tipo de sangre existe
			psTipoSangre = con.prepareStatement("SELECT COUNT(*) FROM tipo_sangre WHERE id_tipo_sangre = ?");

			psTipoSangre.setInt(1, m_ID_Tipo_Sangre);
			ResultSet rsTs = psTipoSangre.executeQuery();
			rsTs.next();
			if (rsTs.getInt(1) == 0) {
				throw new GestionDonacionesSangreException(GestionDonacionesSangreException.TIPO_SANGRE_NO_EXISTE);
			}
			rsTs.close();

			// 4. Seleccionar los traspasos que cumplen los cuatro criterios
			// (puede haber más de uno en la misma fecha con los mismos parámetros)
			psSelectTrasp = con.prepareStatement("SELECT id_traspaso, cantidad" + " " + "FROM   traspaso" + " "
					+ "WHERE  id_tipo_sangre        = ?" + " " + "  AND  id_hospital_origen    = ?" + " "
					+ "  AND  id_hospital_destino   = ?" + " " + "  AND  trunc(fecha_traspaso) = trunc(?)");

			psSelectTrasp.setInt(1, m_ID_Tipo_Sangre);
			psSelectTrasp.setInt(2, m_ID_Hospital_Origen);
			psSelectTrasp.setInt(3, m_ID_Hospital_Destino);
			psSelectTrasp.setDate(4, new java.sql.Date(m_Fecha_Traspaso.getTime()));

			ResultSet rsTrasp = psSelectTrasp.executeQuery();

			// 5. Para cada traspaso: ajustar reservas y borrar el registro
			// · RESTAR cantidad en la reserva del hospital DESTINO
			// · SUMAR cantidad en la reserva del hospital ORIGEN
			psRestaDestino = con.prepareStatement("UPDATE reserva_hospital" + " " + "SET    cantidad = cantidad - ?"
					+ " " + "WHERE  id_tipo_sangre = ?" + " " + "  AND  id_hospital    = ?");

			psSumaOrigen = con.prepareStatement("UPDATE reserva_hospital" + " " + "SET    cantidad = cantidad + ?" + " "
					+ "WHERE  id_tipo_sangre = ?" + " " + "  AND  id_hospital    = ?");

			psDelete = con.prepareStatement("DELETE FROM traspaso WHERE id_traspaso = ?");

			while (rsTrasp.next()) {
				int idTraspaso = rsTrasp.getInt("id_traspaso");
				double cantidad = rsTrasp.getDouble("cantidad");

				// Restar en destino (ORA-02290 si la reserva quedaría < 0)
				psRestaDestino.setDouble(1, cantidad);
				psRestaDestino.setInt(2, m_ID_Tipo_Sangre);
				psRestaDestino.setInt(3, m_ID_Hospital_Destino);
				psRestaDestino.executeUpdate();

				// Sumar en origen
				psSumaOrigen.setDouble(1, cantidad);
				psSumaOrigen.setInt(2, m_ID_Tipo_Sangre);
				psSumaOrigen.setInt(3, m_ID_Hospital_Origen);
				psSumaOrigen.executeUpdate();

				// Borrar el traspaso
				psDelete.setInt(1, idTraspaso);
				psDelete.executeUpdate();
			}
			rsTrasp.close();

			con.commit();
			System.out.println("anular_traspaso: operación completada correctamente.");

		} catch (SQLException e) {
			if (con != null)
				con.rollback();

			// ORA-02290: violación de constraint CHECK → reserva destino < 0
			if (e.getErrorCode() == 2290) {
				throw new GestionDonacionesSangreException(
						GestionDonacionesSangreException.VALOR_CANTIDAD_TRASPASO_INCORRECTO);
			}

			logger.error(e.getMessage());
			throw e;

		} finally {
			if (psDelete != null)
				try {
					psDelete.close();
				} catch (SQLException ex) {
					logger.error(ex.getMessage());
				}
			if (psSumaOrigen != null)
				try {
					psSumaOrigen.close();
				} catch (SQLException ex) {
					logger.error(ex.getMessage());
				}
			if (psRestaDestino != null)
				try {
					psRestaDestino.close();
				} catch (SQLException ex) {
					logger.error(ex.getMessage());
				}
			if (psSelectTrasp != null)
				try {
					psSelectTrasp.close();
				} catch (SQLException ex) {
					logger.error(ex.getMessage());
				}
			if (psTipoSangre != null)
				try {
					psTipoSangre.close();
				} catch (SQLException ex) {
					logger.error(ex.getMessage());
				}
			if (psHospital != null)
				try {
					psHospital.close();
				} catch (SQLException ex) {
					logger.error(ex.getMessage());
				}
			if (con != null)
				try {
					con.close();
				} catch (SQLException ex) {
					logger.error(ex.getMessage());
				}
		}
	}

	public static void consulta_traspasos(String m_Tipo_Sangre) throws SQLException {

		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con = null;
		PreparedStatement psCheck = null;
		PreparedStatement psQuery = null;
		ResultSet rsCheck = null;
		ResultSet rsQuery = null;

		try {
			con = pool.getConnection();
			con.setAutoCommit(false);

			// 1. Comprobar si el tipo de sangre existe
			String sqlCheck = "SELECT id_tipo_sangre FROM tipo_sangre WHERE descripcion = ?";
			psCheck = con.prepareStatement(sqlCheck);
			psCheck.setString(1, m_Tipo_Sangre);
			rsCheck = psCheck.executeQuery();

			if (!rsCheck.next()) {
				// Lanza la excepción específica si no existe
				throw new GestionDonacionesSangreException(2);
			}
			int idTipoSangre = rsCheck.getInt("id_tipo_sangre");

			// 2. Consulta principal: Mostrar traspasos, hospital, reserva y tipo sangre
			String sqlQuery = "SELECT t.id_traspaso, t.fecha_traspaso, t.cantidad AS cantidad_traspasada, "
					+ "       ho.nombre AS hospital_origen, hd.nombre AS hospital_destino, "
					+ "       rho.cantidad AS reserva_origen, rhd.cantidad AS reserva_destino " + "FROM traspaso t "
					+ "JOIN hospital ho ON t.id_hospital_origen = ho.id_hospital "
					+ "JOIN hospital hd ON t.id_hospital_destino = hd.id_hospital "
					+ "JOIN reserva_hospital rho ON rho.id_hospital = t.id_hospital_origen AND rho.id_tipo_sangre = t.id_tipo_sangre "
					+ "JOIN reserva_hospital rhd ON rhd.id_hospital = t.id_hospital_destino AND rhd.id_tipo_sangre = t.id_tipo_sangre "
					+ "WHERE t.id_tipo_sangre = ? " + "ORDER BY t.id_hospital_destino, t.fecha_traspaso"; // Ordenado
																											// por
																											// destino y
																											// fecha

			psQuery = con.prepareStatement(sqlQuery);
			psQuery.setInt(1, idTipoSangre);
			rsQuery = psQuery.executeQuery();

			System.out.println("--- Historial de traspasos para: " + m_Tipo_Sangre + " ---");
			while (rsQuery.next()) {
				System.out.printf("Traspaso ID: %d | Fecha: %s | Cantidad: %.2f L%n", rsQuery.getInt("id_traspaso"),
						rsQuery.getDate("fecha_traspaso"), rsQuery.getFloat("cantidad_traspasada"));
				System.out.printf("  Origen: %s (Reserva actual: %.2f L)%n", rsQuery.getString("hospital_origen"),
						rsQuery.getFloat("reserva_origen"));
				System.out.printf("  Destino: %s (Reserva actual: %.2f L)%n", rsQuery.getString("hospital_destino"),
						rsQuery.getFloat("reserva_destino"));
				System.out.println("---------------------------------------------------");
			}

			con.commit();

		} catch (SQLException e) {
			if (con != null) {
				con.rollback(); // En cualquiera de las excepciones se hará rollback
			}

			// Si es la excepción de negocio (no existe el tipo de sangre), la lanzamos
			// directamente
			// para que no llegue al logger.error de abajo.
			if (e instanceof GestionDonacionesSangreException) {
				throw e;
			}

			// Si es cualquier otro error SQL imprevisto, se registra y se lanza.
			logger.error(e.getMessage());
			throw e;

		} finally {
			// Liberar todos los recursos utilizados con un bloque finally
			if (rsQuery != null)
				rsQuery.close();
			if (psQuery != null)
				psQuery.close();
			if (rsCheck != null)
				rsCheck.close();
			if (psCheck != null)
				psCheck.close();
			if (con != null)
				con.close();
		}
	}

	static public void creaTablas() {
		ExecuteScript.run(script_path + "gestion_donaciones_sangre.sql");
	}

	static void tests() throws SQLException {
		creaTablas();

		PoolDeConexiones pool = PoolDeConexiones.getInstance();

		// ------------------------------------------------------------------
		// PRUEBA 1: consulta_traspaso(caso normal)
		// ------------------------------------------------------------------
		inicializarDatosPrueba(pool);

		System.out.println("\n[TEST 1] Consulta normal para Tipo A...");
		try {
			consulta_traspasos("Tipo A.");
			System.out.println("TEST 1 SUPERADO: La consulta se ejecuto correctamente sin errores.");
		} catch (SQLException e) {
			System.out.println(" TEST 1 FALLIDO: " + e.getMessage());
		}

		// ------------------------------------------------------------------
		// PRUEBA 2: consulta_traspaso(Excepción)
		// ------------------------------------------------------------------
		// Volvemos a inicializar los datos antes de la siguiente prueba
		inicializarDatosPrueba(pool);

		System.out.println("\n[TEST 2] Consulta con Tipo de Sangre Inexistente...");
		try {
			consulta_traspasos("Tipo C.");
			System.out.println("TEST 2 FALLIDO: Debería haber saltado la excepción.");
		} catch (GestionDonacionesSangreException e) {
			System.out.println("TEST 2 SUPERADO. Excepción capturada correctamente: " + e.getMessage());
		} catch (SQLException e) {
			System.out.println("TEST 2 FALLIDO: Saltó una SQLException genérica en lugar de la propia.");
		}
		

		// --------------------------------------------------------------------------
		// Caso 3 – Anulación correcta (caso normal)
		// -------------------------------------------------------------------------
		// Traspaso: tipo=1, origen=1, destino=2, cantidad=2, fecha=11/01/2025
		// Hospital 2 reserva tipo 1 = 2.45 → restar 2 → 0.45 ≥ 0 → OK
		// -------------------------------------------------------------------------
		inicializarDatosPrueba(pool);

		System.out.println("\n=== CASO 3: Anulación correcta ===");
		try {
			anular_traspaso(1, 1, 2, toDate("11/01/2025"));
			System.out.println("CASO 3 OK: traspaso anulado sin excepción.");
		} catch (SQLException e) {
			System.out.println("CASO 3 FALLO inesperado: " + e.getMessage());
		}

		// -------------------------------------------------------------------------
		// Caso 4 – Hospital ORIGEN inexistente → excepción código 3
		// -------------------------------------------------------------------------
		inicializarDatosPrueba(pool);

		System.out.println("\n=== CASO 4: Hospital origen inexistente ===");
		try {
			anular_traspaso(1, 9999, 2, toDate("11/01/2025"));
			System.out.println("CASO 4 FALLO: debería haber lanzado excepción código 3.");
		} catch (GestionDonacionesSangreException ge) {
			if (ge.getErrorCode() == GestionDonacionesSangreException.HOSPITAL_NO_EXISTE) {
				System.out.println("CASO 4 OK: excepción código 3 capturada – " + ge.getMessage());
			} else {
				System.out.println("CASO 4 FALLO: código incorrecto " + ge.getErrorCode());
			}
		}

		// -------------------------------------------------------------------------
		// Caso 5 – Hospital DESTINO inexistente → excepción código 3
		// -------------------------------------------------------------------------
		inicializarDatosPrueba(pool);

		System.out.println("\n=== CASO 5: Hospital destino inexistente ===");
		try {
			anular_traspaso(1, 1, 9999, toDate("11/01/2025"));
			System.out.println("CASO 5 FALLO: debería haber lanzado excepción código 3.");
		} catch (GestionDonacionesSangreException ge) {
			if (ge.getErrorCode() == GestionDonacionesSangreException.HOSPITAL_NO_EXISTE) {
				System.out.println("CASO 5 OK: excepción código 3 capturada – " + ge.getMessage());
			} else {
				System.out.println("CASO 5 FALLO: código incorrecto " + ge.getErrorCode());
			}
		}

		// -------------------------------------------------------------------------
		// Caso 6 – Tipo de sangre inexistente → excepción código 2
		// -------------------------------------------------------------------------
		inicializarDatosPrueba(pool);

		System.out.println("\n=== CASO 6: Tipo de sangre inexistente ===");
		try {
			anular_traspaso(9999, 1, 2, toDate("11/01/2025"));
			System.out.println("CASO 6 FALLO: debería haber lanzado excepción código 2.");
		} catch (GestionDonacionesSangreException ge) {
			if (ge.getErrorCode() == GestionDonacionesSangreException.TIPO_SANGRE_NO_EXISTE) {
				System.out.println("CASO 6 OK: excepción código 2 capturada – " + ge.getMessage());
			} else {
				System.out.println("CASO 6 FALLO: código incorrecto " + ge.getErrorCode());
			}
		}

		// -------------------------------------------------------------------------
		// Caso 7 – Reserva destino quedaría negativa → excepción código 6
		// -------------------------------------------------------------------------
		// Traspaso: tipo=2, origen=3, destino=2, cantidad=10, fecha=16/01/2025
		// Hospital 2 reserva tipo 2 = 5.5 → restar 10 → -4.5 → ORA-02290 → código 6
		// -------------------------------------------------------------------------
		inicializarDatosPrueba(pool);

		System.out.println("\n=== CASO 7: Reserva destino insuficiente (código 6) ===");
		try {
			anular_traspaso(2, 3, 2, toDate("16/01/2025"));
			System.out.println("CASO 7 FALLO: debería haber lanzado excepción código 6.");
		} catch (GestionDonacionesSangreException ge) {
			if (ge.getErrorCode() == GestionDonacionesSangreException.VALOR_CANTIDAD_TRASPASO_INCORRECTO) {
				System.out.println("CASO 7 OK: excepción código 6 capturada – " + ge.getMessage());
			} else {
				System.out.println("CASO 7 FALLO: código incorrecto " + ge.getErrorCode());
			}
		}

		// -------------------------------------------------------------------------
		// Caso 8 – Ningún traspaso coincide con los parámetros (caso extremo benigno)
		// -------------------------------------------------------------------------
		// Fecha 01/01/1900 no existe en los datos de prueba → 0 filas afectadas, sin
		// excepción
		// -------------------------------------------------------------------------
		inicializarDatosPrueba(pool);

		System.out.println("\n=== CASO 8: Ningún traspaso encontrado ===");
		try {
			anular_traspaso(1, 1, 2, toDate("01/01/1900"));
			System.out.println("CASO 8 OK: ningún traspaso encontrado, sin excepción.");
		} catch (SQLException e) {
			System.out.println("CASO 8 FALLO inesperado: " + e.getMessage());
		}

		// -------------------------------------------------------------------------
		// Caso 9 – Varios traspasos con los mismos parámetros en la misma fecha
		// -------------------------------------------------------------------------
		// Traspaso tipo=3 (AB), origen=1, destino=2, cantidad=2.1, fecha=11/01/2025
		// Hospital 2 reserva tipo 3 = 8.82 → restar 2.1 → 6.72 ≥ 0 → OK
		// -------------------------------------------------------------------------
		inicializarDatosPrueba(pool);

		System.out.println("\n=== CASO 9: Varios traspasos misma fecha y parámetros ===");
		try {
			anular_traspaso(3, 1, 2, toDate("11/01/2025"));
			System.out.println("CASO 9 OK: traspaso tipo AB anulado sin excepción.");
		} catch (SQLException e) {
			System.out.println("CASO 9 FALLO inesperado: " + e.getMessage());
		}
		/*// -------------------------------------------------------------------------
		// Caso 10 – Donación correcta 
		// -------------------------------------------------------------------------
		inicializarDatosPrueba(pool);
		System.out.println("\n=== CASO 10: Donación correcta ===");
		try {
			// NIF que exista, Hospital 1, cantidad permitida, fecha válida
			realizar_donacion("12345678A", 1, 0.30f, toDate("26/01/2025"));
			System.out.println("CASO 10 OK: Donación registrada e incremento de reserva realizado.");
		} catch (SQLException e) {
			System.out.println("CASO 10 FALLO inesperado: " + e.getMessage());
		}*/

		// -------------------------------------------------------------------------
		// Caso 11 – Cantidad incorrecta (> 0.45) 
		// -------------------------------------------------------------------------
		inicializarDatosPrueba(pool);
		System.out.println("\n=== CASO 11: Cantidad superior al máximo (0.45 L) ===");
		try {
			realizar_donacion("12345678A", 1, 0.60f, toDate("25/01/2025"));
			System.out.println("CASO 11 FALLO: Debería haber saltado el código 5.");
		} catch (GestionDonacionesSangreException ge) {
			if (ge.getErrorCode() == GestionDonacionesSangreException.VALOR_CANTIDAD_DONACION_INCORRECTO) {
				System.out.println("CASO 11 OK: Excepción código 5 capturada correctamente.");
			} else {
				System.out.println("CASO 11 FALLO: Código incorrecto " + ge.getErrorCode());
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		// -------------------------------------------------------------------------
		// Caso 12 – Donante inexistente
		// -------------------------------------------------------------------------
		inicializarDatosPrueba(pool);
		
		System.out.println("\n=== CASO 12: Donante inexistente ===");
		try {
			realizar_donacion("99999999Z", 1, 0.30f, toDate("25/01/2025"));
			System.out.println("CASO 12 FALLO: Debería haber saltado el código 1.");
		} catch (GestionDonacionesSangreException ge) {
			if (ge.getErrorCode() == GestionDonacionesSangreException.DONANTE_NO_EXISTE) {
				System.out.println("CASO 12 OK: Excepción código 1 capturada.");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		// -------------------------------------------------------------------------
		// Caso 13 – Regla de los 15 días 
		// -------------------------------------------------------------------------
		// Si en inicializa_test el donante ya donó el 11/01/2025, el 20/01/2025 fallará
		// -------------------------------------------------------------------------
		inicializarDatosPrueba(pool);
		System.out.println("\n=== CASO 13: Regla de los 15 días (Donación muy seguida) ===");
		try {
			// Intentamos donar solo 9 días después de la anterior
			realizar_donacion("12345678A", 1, 0.20f, toDate("20/01/2025"));
			System.out.println("CASO 13 FALLO: Debería haber saltado el código 4.");
		} catch (GestionDonacionesSangreException ge) {
			if (ge.getErrorCode() == GestionDonacionesSangreException.DONANTE_EXCEDE) {
				System.out.println("CASO 13 OK: Excepción código 4 capturada.");
			} else {
				System.out.println("CASO 13 FALLO: Código incorrecto " + ge.getErrorCode());
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * MÉTODOs AUXILIARES: Contiene EXACTAMENTE el código que se no dio para inicializar
	 * los tests y otro método extra para manejar fechas
	 */
	static void inicializarDatosPrueba(PoolDeConexiones pool) {
		CallableStatement cll_reinicia = null; //
		Connection conn = null; //

		try {
			// Reinicio filas
			conn = pool.getConnection(); //
			cll_reinicia = conn.prepareCall("{call inicializa_test}"); //
			cll_reinicia.execute(); //

		} catch (SQLException e) {
			logger.error(e.getMessage()); //
		} finally {
			// Añadimos un try-catch interno porque los close() también lanzan SQLException
			try {
				if (cll_reinicia != null)
					cll_reinicia.close(); //
				if (conn != null)
					conn.close(); //
			} catch (SQLException ex) {
				logger.error(ex.getMessage());
			}
		}
	}

	private static Date toDate(String ddmmyyyy) {
		String[] parts = ddmmyyyy.split("/");
		java.util.Calendar cal = java.util.Calendar.getInstance();
		cal.set(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[0]), 0, 0, 0);
		cal.set(java.util.Calendar.MILLISECOND, 0);
		return cal.getTime();
	}
}