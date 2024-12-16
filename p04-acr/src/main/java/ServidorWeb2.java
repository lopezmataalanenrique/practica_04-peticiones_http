/*
* Práctica No.4 de Aplicaciones para comunicaciones en red
* */
// Para usar ServerSocket y Socket
import java.net.*;
// Para usar DataInputStream y DataOutputStream
import java.io.*;
// Para usar utilidades como Map y HashMap
import java.text.SimpleDateFormat;
import java.util.*;
// Para clases de programación concurrente como ExecutorService y Executors
import java.util.concurrent.*;
import java.util.TimeZone;

public class ServidorWeb2 {

   public static final int PUERTO = 8000;
   // Definimos el puerto
   private static final int NUMERODEHILOS = 100;
   // Define el número máximo de hilos
   private ServerSocket ss;
   // Para aceptar conexiones
   private ExecutorService pool;
   // Para gestionar la alberca de hilos

   // Inicializar el servidor y la alberca de hilos
   public ServidorWeb2() throws Exception {
      System.out.println("Iniciando Servidor...");
      this.ss = new ServerSocket(PUERTO);
      this.pool = Executors.newFixedThreadPool(NUMERODEHILOS);
      System.out.println("Servidor iniciado en el puerto " + PUERTO);
      System.out.println("Esperando clientes...");
      // Bucle infinito para esperar nuevas conexiones de clientes
      while (true) {
         try {
            Socket client = ss.accept();
            // Envía un nuevo hilo al manejador
            pool.submit(new Manejador(client));
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   // Creamos una instancia de ServidorWeb2
   public static void main(String[] args) throws Exception {
      new ServidorWeb2();
   }
   // Permite que cada instancia se ejecute en un hilo
   class Manejador implements Runnable {
      // Socket para la conexión del cliente y flujos de entrada y salida
      private Socket socket;
      private DataOutputStream dos;
      private DataInputStream dis;

      public Manejador(Socket _socket) {
         this.socket = _socket;
      }
      // Aquí se manejan las solicitudes del cliente
      @Override
      public void run() {
         try {
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            byte[] buffer = new byte[50000];
            int bytesRead = dis.read(buffer);
            // Verificar si se han leído bytes
            if (bytesRead == -1) {
               System.out.println("Cliente desconectado o no se enviaron los datos.");
               return;
            }
            /* Convierte los bytes leídos a cadena de caracteres
            * y lo muestra en pantalla*/
            String peticion = new String(buffer, 0, bytesRead);
            System.out.println("\n----- PETICION -----");
            System.out.println(peticion);
            /* Si la petición esta vacía se envía un error 400
            * y sale del método */
            if (peticion.isEmpty()) {
               enviarError(400, "Bad Request");
               return;
            }
            // Para dividir la petición en líneas.
            // La primera línea contiene el tipo de petición, el recurso, los parámetros y el protocolo.
            StringTokenizer st = new StringTokenizer(peticion, "\n");
            String primeraLinea = st.nextToken();

            if (primeraLinea.toUpperCase().startsWith("HEAD")) {
               procesarSolicitud(primeraLinea, true);
            } else if (primeraLinea.toUpperCase().startsWith("GET")) {
               procesarSolicitud(primeraLinea, false);
            } else if (primeraLinea.toUpperCase().startsWith("POST")) {
               procesarPost(peticion);
            } else if(primeraLinea.toUpperCase().startsWith("PUT")){
               procesarPut(peticion);
            } else if (primeraLinea.toUpperCase().startsWith("DELETE")) {
               procesarDelete(primeraLinea);
            } else{
               enviarError(501, "Not Implemented");
            }
         } catch (Exception e) {
            e.printStackTrace();
         } finally {
            cerrarConexion();
         }
      }

      private void procesarDelete(String linea) throws IOException {
         String fileName = obtenerArchivo(linea);

         if (fileName == null || fileName.isEmpty()) {
            enviarError(400, "Bad Request");
            return;
         }
         File file = new File(fileName);

         if (!file.exists()) {
            enviarError(404, "Not found");
            return;
         }
         if (file.delete()) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            String fechaActual = dateFormat.format(new Date());
            String respuesta = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Content-Length: 22\r\n"
                    + "Date: " + fechaActual + "\r\n\r\n"
                    + "Recurso eliminado.";
            dos.write(respuesta.getBytes());
         } else {
            enviarError(500, "Internal Server Error");
         }

         dos.flush();
         dos.close();

         System.out.println("\n----- RESPUESTA -----");
         System.out.println("Recurso eliminado: " + fileName);
      }

      private void procesarPut(String peticion) throws IOException {
         String[] partes = peticion.split("\r\n\r\n", 2);
         if (partes.length < 2) {
            enviarError(400, "Bad Request");
            return;
         }
         String cabecera = partes[0];
         String cuerpo = partes[1];

         // Extraer la URL y el nombre del archivo
         String primeraLinea = cabecera.split("\n")[0];
         String fileName = obtenerArchivo(primeraLinea);

         if (fileName == null || fileName.isEmpty()) {
            enviarError(400, "Bad Request");
            return;
         }

         // Guardar el contenido en un archivo
         File file = new File(fileName);
         try(FileOutputStream fos = new FileOutputStream(file)){
            fos.write(cuerpo.getBytes());
         }

         SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
         dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
         String fechaActual = dateFormat.format(new Date());

         String cuerpoRespuesta = "Recurso guardado correctamente.";
         int longitudCuerpo = cuerpoRespuesta.length();

         // Respuesta al cliente
         String respuesta = "HTTP/1.1 201 Created\r\nContent-Type: text/plain\r\nContent-Length: "
                 + longitudCuerpo + "\r\nDate: " + fechaActual + "\r\n\r\n" + cuerpoRespuesta;

         dos.write(respuesta.getBytes());
         dos.flush();

         System.out.println("\n----- RESPUESTA -----");
         System.out.println(respuesta);

      }

      private void procesarSolicitud(String linea, boolean isHead) throws IOException {
         String fileName = obtenerArchivo(linea);
         Map<String, String> parametros = obtenerParametrosURL(linea);

         System.out.println("\n----- PARAMETROS -----");
         if (parametros.isEmpty()) {
            System.out.println("No se enviaron parámetros.");
         } else {
            parametros.forEach((clave, valor) -> System.out.println(clave + " = " + valor));
         }

         if (fileName == null || fileName.isEmpty()) {
            fileName = "index.html";
         }

         SendHeaders(fileName, dos, isHead);
      }

      private void procesarPost(String peticion) throws IOException {
         String[] partes = peticion.split("\r\n\r\n", 2);
         if (partes.length < 2) {
            enviarError(400, "Bad Request");
            return;
         }
         String cuerpo = partes[1];

         // Separar el cuerpo en las diferentes partes del formulario usando el delimitador de partes
         // Se asume que el delimitador de partes comienza con '--' y termina con la cadena delimitadora final
         String[] partesCuerpo = cuerpo.split("--");

         // Variable para almacenar el Content-Type encontrado
         String contentType = "text/plain"; // Valor por defecto en caso de que no se encuentre

         // Iterar sobre las partes del cuerpo, ignorando la primera parte
         for (int i = 1; i < partesCuerpo.length; i++) {
            String parte = partesCuerpo[i].trim();

            // Solo procesar las partes que contienen un Content-Type
            if (parte.contains("Content-Type:")) {
               // Buscar el Content-Type dentro de la parte
               String[] lineas = parte.split("\r\n");
               for (String linea : lineas) {
                  if (linea.startsWith("Content-Type:")) {
                     contentType = linea.split(":")[1].trim();
                     break;
                  }
               }
               break; // Ya encontramos el Content-Type, no necesitamos seguir buscando
            }
         }

         System.out.println("\n----- CUERPO -----");
         System.out.println(cuerpo);

         Map<String, String> parametros = obtenerParametros(cuerpo);

         SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
         dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
         String fechaActual = dateFormat.format(new Date());

         String cuerpoRespuesta = "Datos recibidos correctamente.";
         int longitudCuerpo = cuerpoRespuesta.length();

         // Crear la respuesta incluyendo el Content-Type extraído
         String respuesta = "HTTP/1.1 201 OK\r\nContent-Type: " + contentType + "\r\nContent-Length: " + longitudCuerpo + "\r\nDate: "+ fechaActual +"\r\n\r\n" + cuerpoRespuesta;

         dos.write(respuesta.getBytes());
         dos.flush();
         dos.close();

         System.out.println("\n----- RESPUESTA -----");
         System.out.println(respuesta);
      }

      private String obtenerArchivo(String linea) {
         int i = linea.indexOf("/");
         int f = linea.indexOf(" ", i);
         if (i >= 0 && f > i) {
            return linea.substring(i + 1, f).trim().split("\\?")[0];
         }
         return null;
      }

      private Map<String, String> obtenerParametrosURL(String linea) {
         int i = linea.indexOf("?");
         if (i == -1) return Collections.emptyMap();
         int f = linea.indexOf(" ", i);
         if (f == -1) f = linea.length();
         String queryString = linea.substring(i + 1, f);
         return obtenerParametros(queryString);
      }

      private Map<String, String> obtenerParametros(String data) {
         Map<String, String> parametros = new HashMap<>();
         int espacioIdx = data.indexOf(" ");
         if (espacioIdx != -1) {
            data = data.substring(0, espacioIdx);
         }

         String[] pares = data.split("&");
         for (String par : pares) {
            String[] claveValor = par.split("=");
            if (claveValor.length == 2) {
               parametros.put(claveValor[0], claveValor[1]);
            } else if (claveValor.length == 1) {
               parametros.put(claveValor[0], "");
            }
         }
         return parametros;
      }

      private void SendHeaders(String fileName, DataOutputStream dos, boolean isHead) {
         try {
            File file = new File(fileName);

            if (!file.exists()) {
               enviarError(404, "Not Found");
               return;
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            String fechaActual = dateFormat.format(new Date());
            long tamArchivo = file.length();
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 200 OK\r\n");
            sb.append("Content-Type: ").append(obtenerTipoContenido(fileName)).append("\r\n");
            sb.append("Content-Length: ").append(tamArchivo).append("\r\n");
            sb.append("Date: ").append(fechaActual).append("\r\n");
            sb.append("\r\n");

            dos.write(sb.toString().getBytes());
            dos.flush();

            if (!isHead) {
               // Verificar la extensión del archivo
               String extensionArchivo = obtenerExtensionArchivo(fileName);
               if (extensionArchivo.equalsIgnoreCase("html") || extensionArchivo.equalsIgnoreCase("htm") || extensionArchivo.equalsIgnoreCase("txt")) {
                  //Imprimir el contenido del archivo
                  try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                     String line;
                     System.out.println("\n----- CUERPO -----");
                     while ((line = br.readLine()) != null) {
                        System.out.println(line);
                     } // fin del while
                  }
               } else {
                  // Mostrar un mensaje indicando que no se puede visualizar el archivo con esa extensión
                  System.out.println("\n----- NO SE PUEDE VISUALIZAR EL ARCHIVO -----");
                  System.out.println("Archivo: " + fileName);
               }
               // Enviar el contenido del archivo
               try (FileInputStream fis = new FileInputStream(file)) {
                  byte[] buffer = new byte[1024];
                  int bytesRead;
                  while ((bytesRead = fis.read(buffer)) != -1) {
                     dos.write(buffer, 0, bytesRead);
                  }
               }
            }

            System.out.println("\n----- RESPUESTA -----");
            System.out.println(sb);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      // Método para obtener la extesnión del archivo
      private String obtenerExtensionArchivo(String fileName){
         int lastIndex = fileName.lastIndexOf(".");
         if (lastIndex == -1) {
            return ""; // Sin extensión
         }
         return fileName.substring(fileName.lastIndexOf(".") + 1);
      }
      // Método para obtener el tipo de contenido
      private String obtenerTipoContenido(String fileName){
         if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html";
         } else if (fileName.endsWith(".txt") || fileName.endsWith(".txt")) {
            return "text/plain";
         } else if (fileName.endsWith(".pdf")) {
            return "application/pdf";
         } else if (fileName.endsWith(".jpg")) {
            return "image/jpeg";
         } else if (fileName.endsWith(".png")) {
            return "image/png";
         } else if (fileName.endsWith(".gif")) {
            return "image/gif";
         } else if (fileName.endsWith(".mp4")) {
            return "video/mp4";
         } else if (fileName.endsWith(".avi")) {
            return "video/avi";
         } else if (fileName.endsWith(".mov")) {
            return "video/mov";
         } else {
            return "application/octet-stream"; // Tipo por defecto
         }
      }

      private void enviarError(int codigo, String mensaje) {
         try {
            String respuesta = "HTTP/1.1 " + codigo + " " + mensaje + "\r\n\r\n";
            dos.write(respuesta.getBytes());
            dos.flush();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }

      private void cerrarConexion() {
         try {
            if (dos != null) dos.close();
            if (dis != null) dis.close();
            if (socket != null) socket.close();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
   }
}
