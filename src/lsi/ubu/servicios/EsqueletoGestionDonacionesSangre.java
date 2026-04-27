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
 * GestionDonacionesSangre:
 * Implementa la gestion de donaciones de sangre según el enunciado del ejercicio
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

	public static void main(String[] args) throws SQLException{		
		tests();

		System.out.println("FIN.............");
	}
	
	public static void realizar_donacion(String m_NIF, int m_ID_Hospital,
			float m_Cantidad,  Date m_Fecha_Donacion) throws SQLException {
		
		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con=null;

	
		try{
			con = pool.getConnection();
			//Completar por el alumno
			
		} catch (SQLException e) {
			//Completar por el alumno			
			
			logger.error(e.getMessage());
			throw e;		

		} finally {
			/*A rellenar por el alumno*/
		}
		
		
	}
	
	public static void anular_traspaso(int m_ID_Tipo_Sangre, int m_ID_Hospital_Origen,int m_ID_Hospital_Destino,
			Date m_Fecha_Traspaso)
			throws SQLException {
		
		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con=null;

	
		try{
			con = pool.getConnection();
			//Completar por el alumno
			
		} catch (SQLException e) {
			//Completar por el alumno			
			
			logger.error(e.getMessage());
			throw e;		

		} finally {
			/*A rellenar por el alumno*/
		}		
	}
	
	public static void consulta_traspasos(String m_Tipo_Sangre)
			throws SQLException {
				
		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con=null;
		PreparedStatement psCheck = null;
		PreparedStatement psQuery = null;
		ResultSet rsCheck = null;
		ResultSet rsQuery = null;
	
		try{
			con = pool.getConnection();
			
			// 1. Comprobar si el tipo de sangre existe
			String sqlCheck = "SELECT id_tipo_sangre FROM tipo_sangre WHERE descripcion = ?";
			psCheck = con.prepareStatement(sqlCheck);
			psCheck.setString(1, m_Tipo_Sangre);
			rsCheck = psCheck.executeQuery();

			if (!rsCheck.next()) {
				// Lanza la excepción específica si no existe [cite: 42]
				throw new GestionDonacionesSangreException(2); 
			}
			int idTipoSangre = rsCheck.getInt("id_tipo_sangre");

			// 2. Consulta principal: Mostrar traspasos, hospital, reserva y tipo sangre [cite: 27]
			String sqlQuery = 
				"SELECT t.id_traspaso, t.fecha_traspaso, t.cantidad AS cantidad_traspasada, " +
				"       ho.nombre AS hospital_origen, hd.nombre AS hospital_destino, " +
				"       rho.cantidad AS reserva_origen, rhd.cantidad AS reserva_destino " +
				"FROM traspaso t " +
				"JOIN hospital ho ON t.id_hospital_origen = ho.id_hospital " +
				"JOIN hospital hd ON t.id_hospital_destino = hd.id_hospital " +
				"JOIN reserva_hospital rho ON rho.id_hospital = t.id_hospital_origen AND rho.id_tipo_sangre = t.id_tipo_sangre " +
				"JOIN reserva_hospital rhd ON rhd.id_hospital = t.id_hospital_destino AND rhd.id_tipo_sangre = t.id_tipo_sangre " +
				"WHERE t.id_tipo_sangre = ? " +
				"ORDER BY t.id_hospital_destino, t.fecha_traspaso"; // Ordenado por destino y fecha 

			psQuery = con.prepareStatement(sqlQuery);
			psQuery.setInt(1, idTipoSangre);
			rsQuery = psQuery.executeQuery();

			System.out.println("--- Historial de traspasos para: " + m_Tipo_Sangre + " ---");
			while (rsQuery.next()) {
				System.out.printf("Traspaso ID: %d | Fecha: %s | Cantidad: %.2f L%n",
						rsQuery.getInt("id_traspaso"), rsQuery.getDate("fecha_traspaso"), rsQuery.getFloat("cantidad_traspasada"));
				System.out.printf("  Origen: %s (Reserva actual: %.2f L)%n",
						rsQuery.getString("hospital_origen"), rsQuery.getFloat("reserva_origen"));
				System.out.printf("  Destino: %s (Reserva actual: %.2f L)%n",
						rsQuery.getString("hospital_destino"), rsQuery.getFloat("reserva_destino"));
				System.out.println("---------------------------------------------------");
			}

			con.commit();
			
		} catch (SQLException e) {
			if (con != null) {
				con.rollback(); // En cualquiera de las excepciones se hará rollback 
			}
			
			// Si es la excepción de negocio (no existe el tipo de sangre), la lanzamos directamente
			// para que no llegue al logger.error de abajo.
			if (e instanceof GestionDonacionesSangreException) {
				throw e;
			}
			
			// Si es cualquier otro error SQL imprevisto, se registra y se lanza.
			logger.error(e.getMessage());
			throw e;		

		} finally {
			// Liberar todos los recursos utilizados con un bloque finally 
			if (rsQuery != null) rsQuery.close();
			if (psQuery != null) psQuery.close();
			if (rsCheck != null) rsCheck.close();
			if (psCheck != null) psCheck.close();
			if (con != null) con.close(); 
		}		
	}
	
	static public void creaTablas() {
		ExecuteScript.run(script_path + "gestion_donaciones_sangre.sql");
	}

	static void tests() throws SQLException{
		creaTablas();
		
		PoolDeConexiones pool = PoolDeConexiones.getInstance();		
		
		//Relatar caso por caso utilizando el siguiente procedure para inicializar los datos
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
	}
		
		/**
		 * MÉTDO AUXILIAR:
		 * Contiene EXACTAMENTE el código que se no dio para inicializarlos tests
		 */
		static void inicializarDatosPrueba(PoolDeConexiones pool) {
			CallableStatement cll_reinicia=null; //
			Connection conn = null; //
			
			try {
				//Reinicio filas
				conn = pool.getConnection(); //
				cll_reinicia = conn.prepareCall("{call inicializa_test}"); //
				cll_reinicia.execute(); //
				
			} catch (SQLException e) {				
				logger.error(e.getMessage());	//	
			  } 
			finally {
				// Añadimos un try-catch interno porque los close() también lanzan SQLException
				try {
					if (cll_reinicia!=null) cll_reinicia.close(); //
					if (conn!=null) conn.close(); //
				} catch (SQLException ex) {
					logger.error(ex.getMessage());
				}
			}	
		}
}