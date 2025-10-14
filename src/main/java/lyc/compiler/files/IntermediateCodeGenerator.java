package lyc.compiler.files;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class IntermediateCodeGenerator implements FileGenerator {

    // Instancia única del Singleton
    private static IntermediateCodeGenerator instance;

    // Lista para almacenar la secuencia de RPN
    private final List<String> rpnCode;
    
    // Contador para generar etiquetas únicas para los saltos (if, while, etc.)
    private int labelCounter;

    // Constructor privado para el Singleton
    private IntermediateCodeGenerator() {
        this.rpnCode = new ArrayList<>();
        this.labelCounter = 0;
    }

    // Método público para obtener la instancia única
    public static IntermediateCodeGenerator getInstance() {
        if (instance == null) {
            instance = new IntermediateCodeGenerator();
        }
        return instance;
    }

    /**
     * Agrega un token (operando o operador) a la secuencia de RPN.
     * @param token El token a agregar (ej: "mi_variable", "5", "+").
     */
    public void addToken(String token) {
        this.rpnCode.add(token);
    }

    /**
     * Crea una nueva etiqueta única para ser usada en saltos.
     * @return El nombre de la etiqueta (ej: "L0", "L1").
     */
    public String newLabel() {
        System.out.println("Nueva etiqueta: L" + (labelCounter+1));
        return "L" + labelCounter++;

    }

    @Override
    public void generate(FileWriter fileWriter) throws IOException {
        // Escribe el título o cabecera del código intermedio
        fileWriter.write("Código Intermedio (Notación Polaca Inversa)\n");
        fileWriter.write("------------------------------------------\n");

        // Itera sobre la lista de tokens RPN acumulados y los escribe en el archivo
        for (String token : rpnCode) {
            // Escribimos cada token seguido de un espacio para que quede en una sola línea
            // o con un salto de línea para mayor claridad. Usemos salto de línea.
            fileWriter.write( token + "\n");
        }
        
        fileWriter.write("------------------------------------------\n");
    }
}