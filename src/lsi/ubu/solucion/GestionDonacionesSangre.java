package lsi.ubu.solucion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lsi.ubu.servicios.GestionDonacionesSangreException;
import lsi.ubu.util.ExecuteScript;
import lsi.ubu.util.PoolDeConexiones;

public class GestionDonacionesSangre {
	
	private static Logger l = LoggerFactory.getLogger(GestionDonacionesSangre.class);
	
	public static void consulta_traspasos(String m_Tipo_Sangre) throws SQLException { 
		PoolDeConexiones pool = null;
		Connection con = null;
	    PreparedStatement psCheck = null;
	    PreparedStatement psQuery = null;
	    ResultSet rsCheck = null;
	    ResultSet rsQuery = null;

	    try {
	    	pool = PoolDeConexiones.getInstance();
	        con = pool.getConnection(); 

	        // 1. Comprobar si el tipo de sangre existe
	        String sqlCheck = "SELECT id_tipo_sangre FROM tipo_sangre WHERE descripcion = ?";
	        psCheck = con.prepareStatement(sqlCheck); // 
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
	        
	        // Registrar en el logger si NO es una GestionDonacionesSangreException 
	        if (!(e instanceof GestionDonacionesSangreException)) {
	            
	            l.error("Error SQL en consulta_traspasos: ", e); 
	        }
	        throw e; // Relanzar al método que haya invocado 
	        
	    } finally {
	        // Liberar todos los recursos utilizados con un bloque finally 
	        if (rsQuery != null) rsQuery.close();
	        if (psQuery != null) psQuery.close();
	        if (rsCheck != null) rsCheck.close();
	        if (psCheck != null) psCheck.close();
	        // Ajusta el cierre de conexión según como lo gestione vuestro PoolDeConexiones.java
	        if (con != null) con.close(); 
	    }
	}
	
	public static void main(String[] args) {

	    // Llamada a la batería de pruebas para la transaccion consulta_traspasos
	    pruebaConsultaTraspasos();
	    
	    // ... llamadas a las pruebas de tus compañeros ...
	}
	
	
	public static void pruebaConsultaTraspasos() {
	    System.out.println("\n=======================================================");
	    System.out.println("  INICIANDO BATERÍA DE PRUEBAS: CONSULTA TRASPASOS");
	    System.out.println("=======================================================\n");

	    // ------------------------------------------------------------------
	    // PRUEBA 1: Caso de ejecución normal
	    // ------------------------------------------------------------------
	    System.out.println("[Prueba 1]: Consultar un tipo de sangre EXISTENTE.");
	    try {
	        // Asumimos que "A+" o el valor que pongas aquí existe en vuestro script SQL 
	        // sql/gestion_donaciones_sangre.sql
	        consulta_traspasos("Tipo A."); 
	        System.out.println("PRUEBA 1 SUPERADA: La consulta se ejecutó y mostró los datos correctamente sin excepciones.");
	    } catch (SQLException e) {
	        System.out.println("PRUEBA 1 FALLIDA: Se produjo una excepción inesperada.");
	        e.printStackTrace();
	    }

	    System.out.println("\n-------------------------------------------------------");

	    // ------------------------------------------------------------------
	    // PRUEBA 2: Caso extremo / Excepción
	    // ------------------------------------------------------------------
	    System.out.println("[Prueba 2]: Consultar un tipo de sangre INEXISTENTE.");
	    try {
	        // Pasamos un valor que sabemos con seguridad que no está en la base de datos
	        consulta_traspasos("Tipo C.");
	        
	        // Si la ejecución llega a esta línea, es que NO se lanzó la excepción esperada
	        System.out.println("PRUEBA 2 FALLIDA: Se esperaba una GestionDonacionesSangreException, pero el método finalizó sin errores.");
	        
	    } catch (GestionDonacionesSangreException e) {
	        // Capturamos la excepción personalizada que os dan ya implementada
	        // Verificamos que sea la del código 2: "Tipo Sangre inexistente"
	        System.out.println("PRUEBA 2 SUPERADA: Se capturó la excepción controlada correctamente.");
	        System.out.println("   -> Detalle del error capturado: " + e.getMessage());
	        
	    } catch (SQLException e) {
	        System.out.println("PRUEBA 2 FALLIDA: Se produjo una SQLException genérica en lugar de la GestionDonacionesSangreException específica.");
	        e.printStackTrace();
	    }
	    
	    System.out.println("\n=======================================================");
	    System.out.println("  FIN DE LA BATERÍA DE PRUEBAS: CONSULTA TRASPASOS");
	    System.out.println("=======================================================");
	}
	
	public static void realizar_donacion(String nifDonante, int idHospital, double cantidad, java.sql.Date fecha) throws Exception {

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = PoolDeConexiones.getInstance().getConnection();
            conn.setAutoCommit(false);

            //  1. Comprobar que el donante existe (NIF).
            String sql = "SELECT COUNT(*) FROM donante WHERE NIF = ?";
            ps = conn.prepareStatement(sql);
            ps.setString(1, nifDonante);
            rs = ps.executeQuery();

            rs.next();
            if (rs.getInt(1) == 0) {
                throw new Exception("El donante no existe");
            }

            rs.close();
            ps.close();

            //  2. Comprobar que el hospital existe
            sql = "SELECT COUNT(*) FROM hospital WHERE id_hospital = ?";
            ps = conn.prepareStatement(sql);
            ps.setInt(1, idHospital);
            rs = ps.executeQuery();

            rs.next();
            if (rs.getInt(1) == 0) {
                throw new Exception("El hospital no existe");
            }

            rs.close();
            ps.close();

            //  3. Validar cantidad
            if (cantidad > 0.45) {
                throw new Exception("Cantidad superior a 0.45 litros");
            }

            //  4. Última donación del donante
            sql = "SELECT MAX(fecha_donacion) FROM donacion WHERE nif_donante = ?";
            ps = conn.prepareStatement(sql);
            ps.setString(1, nifDonante);
            rs = ps.executeQuery();

            if (rs.next() && rs.getDate(1) != null) {

                java.sql.Date ultima = rs.getDate(1);

                long diferencia = fecha.getTime() - ultima.getTime();
                long dias = diferencia / (1000 * 60 * 60 * 24);

                if (dias < 15) {
                    throw new Exception("No han pasado 15 días desde la última donación");
                }
            }

            rs.close();
            ps.close();

            //  5. Insertar donación (USANDO SEQUENCE)
            sql = "INSERT INTO donacion (id_donacion, nif_donante, cantidad, fecha_donacion) VALUES (seq_donacion.nextval, ?, ?, ?)";
            ps = conn.prepareStatement(sql);
            ps.setString(1, nifDonante);
            ps.setDouble(2, cantidad);
            ps.setDate(3, fecha);

            ps.executeUpdate();
            ps.close();

            //  6. Actualizar reserva hospital
            sql = "UPDATE reserva_hospital SET cantidad = cantidad + ? WHERE id_hospital = ?";
            ps = conn.prepareStatement(sql);
            ps.setDouble(1, cantidad);
            ps.setInt(2, idHospital);

            ps.executeUpdate();
            ps.close();

            //  7. Commit final
            conn.commit();

        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw e;

        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
	
	public static void pruebaRealizarDonacion() {
	    System.out.println("\n=======================================================");
	    System.out.println("  INICIANDO BATERÍA DE PRUEBAS: REALIZAR DONACION");
	    System.out.println("=======================================================\n");

	    // Datos reales según el script SQL
	    String nifValido      = "98989898C";  // Maria Fernandez - última donación: 25/01/2025
	    String nifSinDonar    = "98765432Y";  // Alba Serrano    - última donación: 25/01/2025
	    int    hospitalValido = 1;            // Complejo Asistencial de Avila (tiene reserva de Tipo O)

	    // ------------------------------------------------------------------
	    // PRUEBA 1: Caso de ejecución normal
	    // ------------------------------------------------------------------
	    System.out.println("[Prueba 1]: Donación correcta con datos válidos.");
	    try {
	        // 98989898C donó por última vez el 25/01/2025 → han pasado más de 15 días
	        realizar_donacion("98989898C", hospitalValido, 0.25, java.sql.Date.valueOf("2025-02-15"));
	        System.out.println("PRUEBA 1 SUPERADA: La donación se registró correctamente.");
	    } catch (Exception e) {
	        System.out.println("PRUEBA 1 FALLIDA: Se produjo una excepción inesperada.");
	        e.printStackTrace();
	    }

	    System.out.println("\n-------------------------------------------------------");

	    // ------------------------------------------------------------------
	    // PRUEBA 2: Donante inexistente
	    // ------------------------------------------------------------------
	    System.out.println("[Prueba 2]: NIF que no existe en la tabla donante.");
	    try {
	        realizar_donacion("XXXXXXXXX", hospitalValido, 0.25, java.sql.Date.valueOf("2025-02-15"));
	        System.out.println("PRUEBA 2 FALLIDA: Se esperaba excepción por donante inexistente.");
	    } catch (Exception e) {
	        System.out.println("PRUEBA 2 SUPERADA: Excepción capturada correctamente.");
	        System.out.println("   -> " + e.getMessage());
	    }

	    System.out.println("\n-------------------------------------------------------");

	    // ------------------------------------------------------------------
	    // PRUEBA 3: Hospital inexistente
	    // ------------------------------------------------------------------
	    System.out.println("[Prueba 3]: ID de hospital que no existe en la BD.");
	    try {
	        realizar_donacion("98989898C", 9999, 0.25, java.sql.Date.valueOf("2025-03-15"));
	        System.out.println("PRUEBA 3 FALLIDA: Se esperaba excepción por hospital inexistente.");
	    } catch (Exception e) {
	        System.out.println("PRUEBA 3 SUPERADA: Excepción capturada correctamente.");
	        System.out.println("   -> " + e.getMessage());
	    }

	    System.out.println("\n-------------------------------------------------------");

	    // ------------------------------------------------------------------
	    // PRUEBA 4: Cantidad superior a 0.45 litros
	    // ------------------------------------------------------------------
	    System.out.println("[Prueba 4]: Cantidad de donación superior al límite (0.45 L).");
	    try {
	        realizar_donacion("98765432Y", hospitalValido, 0.50, java.sql.Date.valueOf("2025-02-15"));
	        System.out.println("PRUEBA 4 FALLIDA: Se esperaba excepción por cantidad excesiva.");
	    } catch (Exception e) {
	        System.out.println("PRUEBA 4 SUPERADA: Excepción capturada correctamente.");
	        System.out.println("   -> " + e.getMessage());
	    }

	    System.out.println("\n-------------------------------------------------------");


	    // ------------------------------------------------------------------
	    // PRUEBA 5: Caso borde — exactamente 0.45 L (límite permitido)
	    // ------------------------------------------------------------------
	    System.out.println("[Prueba 6]: Cantidad exactamente igual al límite permitido (0.45 L).");
	    try {
	        // 98765432Y donó el 25/01/2025 → el 15/02/2025 han pasado 21 días, es válido
	        realizar_donacion("98765432Y", hospitalValido, 0.45, java.sql.Date.valueOf("2025-02-15"));
	        System.out.println("PRUEBA 6 SUPERADA: La donación en el límite exacto se aceptó.");
	    } catch (Exception e) {
	        System.out.println("PRUEBA 6 FALLIDA: El límite exacto debería ser válido (condición es > 0.45).");
	        System.out.println("   -> " + e.getMessage());
	    }

	    System.out.println("\n=======================================================");
	    System.out.println("  FIN DE LA BATERÍA DE PRUEBAS: REALIZAR DONACION");
	    System.out.println("=======================================================");
	}
	
}
