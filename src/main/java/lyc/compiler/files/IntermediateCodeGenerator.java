package lyc.compiler.files;

import lyc.compiler.model.CompilerException;
import lyc.compiler.model.InvalidLabelException;
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
    
    // Set para rastrear etiquetas usadas (para optimización)
    private final java.util.Set<String> usedLabels;

    // Constructor privado para el Singleton
    private IntermediateCodeGenerator() {
        this.rpnCode = new ArrayList<>();
        this.labelCounter = 0;
        this.usedLabels = new java.util.HashSet<>();
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
        System.out.println("Nueva etiqueta: L" + labelCounter);
        return "L" + labelCounter++;
    }
    
    /**
     * Marca una etiqueta como usada para optimización.
     * @param label La etiqueta que se está usando.
     */
    public void markLabelUsed(String label) {
        this.usedLabels.add(label);
    }
    
    /**
     * Verifica si una etiqueta está siendo usada.
     * @param label La etiqueta a verificar.
     * @return true si la etiqueta está siendo usada.
     */
    public boolean isLabelUsed(String label) {
        return this.usedLabels.contains(label);
    }
    
    /**
     * Optimiza el código intermedio eliminando etiquetas no usadas.
     */
    public void optimizeLabels() throws CompilerException {
        System.out.println("Optimizando etiquetas - Total etiquetas generadas: " + labelCounter);
        System.out.println("Etiquetas usadas: " + usedLabels.size());
        
        // Validar flujo de etiquetas
        validateLabelFlow();
        
        // Aquí se podría implementar lógica más avanzada de optimización
        // Por ahora solo reportamos estadísticas
    }
    
    /**
     * Valida que todas las etiquetas estén correctamente definidas y usadas.
     */
    private void validateLabelFlow() throws CompilerException {
        System.out.println("Validando flujo de etiquetas...");
        java.util.Set<String> definedLabels = new java.util.HashSet<>();

        // 1. Encontrar todas las etiquetas definidas (ej: "L1:")
        for (String token : rpnCode) {
            if (token.endsWith(":")) {
                definedLabels.add(token.substring(0, token.length() - 1));
            }
        }

        // 2. Verificar que todas las etiquetas USADAS estén DEFINIDAS
        for (String label : usedLabels) {
            if (!definedLabels.contains(label)) {
                throw new InvalidLabelException("Error de flujo: Etiqueta " + label + " es usada pero no fue definida en el código.");
            }
        }

        // 3. (Opcional) Advertir sobre etiquetas DEFINIDAS pero nunca USADAS
        for (String label : definedLabels) {
            if (!usedLabels.contains(label)) {
                System.out.println("ADVERTENCIA: Etiqueta " + label + " fue definida pero nunca usada. Podría indicar código muerto o redundante.");
            }
        }

        System.out.println("Validación de flujo completada");
    }

    @Override
    public void generate(FileWriter fileWriter) throws IOException {
        // Optimizar etiquetas antes de generar el código
        try {
            optimizeLabels();
        } catch (CompilerException e) {
            throw new IOException("Error durante la validación del código intermedio: " + e.getMessage(), e);
        }
        
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