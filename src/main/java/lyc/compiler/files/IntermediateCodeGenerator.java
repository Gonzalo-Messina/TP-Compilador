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

    // El contador de etiquetas y su lógica se eliminan.

    // Constructor privado para el Singleton
    private IntermediateCodeGenerator() {
        this.rpnCode = new ArrayList<>();
        // this.labelCounter = 0; // Eliminado
        // this.usedLabels = new java.util.HashSet<>(); // Eliminado
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
     * @return El índice (posición) en el que se agregó el token.
     */
    public int addToken(String token) {
        // La posición del token es su índice en la lista (size() antes de añadir)
        int index = this.rpnCode.size();
        this.rpnCode.add(token);
        return index;
    }

    // --- Métodos de Backpatching (Reemplazan la lógica de Etiquetas) ---

    /**
     * Devuelve el índice de la siguiente instrucción a ser agregada.
     * Esto es el "índice de destino" para los saltos.
     * @return El índice de la siguiente instrucción.
     */
    public int getInstructionCount() {
        // La siguiente instrucción se agregará al final, por lo tanto, el índice
        // es el tamaño actual de la lista.
        return this.rpnCode.size();
    }

    public List<String> getRpnCode() {
        //Devuelve la lista completa de tokens RPN generados.
        return this.rpnCode;
    }

    /**
     * Parchea un marcador de posición de salto con un índice de destino real.
     * Usado por las acciones semánticas de IF y WHILE.
     * @param indexToPatch El índice del token (placeholder "_PLHDR") a modificar.
     * @param targetIndex La posición (índice) a la que debe saltar.
     */
    public void backpatch(int indexToPatch, String targetIndex) {
        // 1. Verificación básica de índice
        if (indexToPatch < 0 || indexToPatch >= this.rpnCode.size()) {
            System.err.println("ADVERTENCIA: Intento de parchear un índice fuera de rango: " + indexToPatch);
            return;
        }

        // 2. Opcional: Verificar que estemos modificando el placeholder
        if (!this.rpnCode.get(indexToPatch).equals("_PLHDR")) {
            System.err.println("ADVERTENCIA: Parcheando un token que no es un placeholder en el índice: " + indexToPatch);
        }

        // 3. Modifica el token. El formato será "POSICIÓN"
        this.rpnCode.set(indexToPatch, targetIndex);
        System.out.println("PATCHED: Indice " + indexToPatch + " => " + targetIndex);
    }

    // Los métodos newLabel, markLabelUsed, isLabelUsed, optimizeLabels, validateLabelFlow se eliminan.

    @Override
    public void generate(FileWriter fileWriter) throws IOException {

        // No hay optimización ni validación de etiquetas, ya que no se usan.

        // Escribe el título o cabecera del código intermedio
        fileWriter.write("Código Intermedio (Notación Polaca Inversa con Índices)\n");
        fileWriter.write("------------------------------------------------------\n");

        // Itera sobre la lista de tokens RPN acumulados y los escribe en el archivo
        for (int i = 0; i < rpnCode.size(); i++) {
            // Incluimos el índice al inicio de cada línea para mejor visibilidad
            fileWriter.write( String.format("[%d] %s%n", i, rpnCode.get(i)) );
        }

        fileWriter.write("------------------------------------------------------\n");
    }
}