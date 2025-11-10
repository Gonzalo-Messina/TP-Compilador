package lyc.compiler.files;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class AsmCodeGenerator implements FileGenerator {

    private static AsmCodeGenerator instance;

    // Contador para temporales creadas por el ensamblador
    private int tempCounter = 0;

    // --- Estado para el pre-análisis ---
    // Almacena todos los temporales (ej: @T1) que se necesitarán
    private final Set<String> temporaries = new LinkedHashSet<>();
    // Almacena todos los literales de string (ej: "hola") que se necesitarán
    private final Set<String> stringLiterals = new LinkedHashSet<>();
    // Almacena los índices de la RPN que son destinos de salto
    private final Set<Integer> jumpTargets = new LinkedHashSet<>();
    // Almacena los operandos (variables y constantes)
    private final LinkedHashSet<String> operands = new LinkedHashSet<>();

    private AsmCodeGenerator() {}

    public static AsmCodeGenerator getInstance() {
        if (instance == null) {
            instance = new AsmCodeGenerator();
        }
        return instance;
    }

    @Override
    public void generate(FileWriter writer) throws IOException {

        // 1. Reiniciar estado (si se genera varias veces)
        resetState();

        IntermediateCodeGenerator icg = IntermediateCodeGenerator.getInstance();
        SymbolTableGenerator symbolTable = SymbolTableGenerator.getInstance();
        List<String> rpn = icg.getRpnCode();

        // 2. Pre-análisis (Pasada 1): Detectar Jumps, Operandos y Strings
        // (En una sola pasada)
        performFirstPreScan(rpn);

        // 3. Pre-análisis (Pasada 2): Simulación (Dry Run) para detectar Temporales
        performTemporaryDiscovery(rpn);

        // --------------------------
        // Sección .MODEL / .DATA
        // --------------------------
        writer.write(".MODEL LARGE\n");
        writer.write(".386\n");
        writer.write(".STACK 200h\n\n");

        writer.write(".DATA\n");

        // Registrar constantes numéricas en la tabla
        for (String op : operands) {
            if (isNumberLiteral(op)) {
                // registra la constante con prefijo "_" y tipo Float
                symbolTable.addToken(op, "Float", op);
            }
        }

        // Emisión de variables y constantes (detectadas en Pasada 1)
        for (String op : operands) {
            if (isNumberLiteral(op)) {
                // constante → _<lexema> dd <valor>
                writer.write(String.format("_%s dd %s\n", op, op));
            } else {
                // identificador → dd 0.0
                writer.write(String.format("%s dd 0.0\n", op));
            }
        }

        // Emisión de temporales (detectados en Pasada 2)
        for (String tempName : temporaries) {
            writer.write(String.format("%s dd 0.0\n", tempName));
            // Registrarlos también en la tabla de símbolos
            symbolTable.addToken(tempName);
        }

        // Emisión de literales de string (detectados en Pasada 1)
        for (String rawLiteral : stringLiterals) {
            writer.write(getStringLiteralData(rawLiteral));
        }

        // Buffer para textos de WRITE
        writer.write("_NEWLINE db 0DH,0AH,0\n");

        // --------------------------
        // Sección .CODE
        // --------------------------
        writer.write("\n.CODE\n");
        writer.write("START:\n");
        writer.write("    MOV AX, @DATA\n");
        writer.write("    MOV DS, AX\n");
        writer.write("    MOV ES, AX\n");
        writer.write("    FINIT\n\n");

        Stack<String> evalStack = new Stack<>();

        // 4. Generación de Código (Pasada 3)
        for (int pc = 0; pc < rpn.size(); pc++) {

            // Emitir label si esta celda es destino de salto
            if (jumpTargets.contains(pc)) {
                writer.write(String.format("L%d:\n", pc));
            }

            String token = rpn.get(pc);

            // WRITE (literal)
            if (token.equals("WRITE")) {
                if (evalStack.isEmpty()) {
                    throw new RuntimeException("RPN inválida: 'WRITE' sin operando en la pila en pc=" + pc);
                }
                String stringToPrint = evalStack.pop(); // <--- MODIFICADO
                // (Corregido) Solo obtenemos el label, ya fue definido en .data
                String label = getStringLiteralLabel(stringToPrint); // <--- MODIFICADO
                writer.write("    MOV DX, OFFSET " + label + "\n");
                writer.write("    MOV AH, 09h\n");
                writer.write("    INT 21h\n");
                writer.write("    MOV DX, OFFSET _NEWLINE\n");
                writer.write("    MOV AH, 09h\n");
                writer.write("    INT 21h\n\n");
                // pc++; // (REMOVIDO)
                continue;
            }

            // Aritméticos
            if (isArithmeticOperator(token)) {
                if (evalStack.size() < 2) {
                    throw new RuntimeException("RPN inválida: operador aritmético sin suficientes operandos en pc=" + pc);
                }
                String op2 = evalStack.pop();
                String op1 = evalStack.pop();

                // (Corregido) Obtenemos el nombre del temp, ya fue declarado en .data
                String aux = generateTempName();

                writer.write(String.format("    FLD %s\n", op1));
                writer.write(String.format("    FLD %s\n", op2));

                switch (token) {
                    case "+": writer.write("    FADD\n"); break;
                    case "-": writer.write("    FSUB\n"); break;
                    case "*": writer.write("    FMUL\n"); break;
                    case "/": writer.write("    FDIV\n"); break;
                }

                writer.write(String.format("    FSTP %s\n\n", aux));
                evalStack.push(aux);
                continue;
            }

            // Asignación (Asume RPN: <valor_fuente> <variable_destino> :=)
            if (token.equals(":=")) {
                if (evalStack.size() < 2) {
                    throw new RuntimeException("RPN inválida: ':=' sin suficientes operandos en pc=" + pc);
                }
                // El orden es importante
                String dst = evalStack.pop(); // variable destino
                String src = evalStack.pop(); // valor fuente
                writer.write(String.format("    FLD %s\n", src));
                writer.write(String.format("    FSTP %s\n\n", dst));
                continue;
            }

            // Comparaciones (CMP)
            if (token.equals("CMP")) {
                if (evalStack.size() < 2) {
                    throw new RuntimeException("RPN inválida: 'CMP' sin suficientes operandos en pc=" + pc);
                }
                String op2 = evalStack.pop();
                String op1 = evalStack.pop();
                writer.write(String.format("    FLD %s\n", op1));
                writer.write(String.format("    FLD %s\n", op2));
                writer.write("    FXCH\n");
                writer.write("    FCOMP\n");
                writer.write("    FSTSW ax\n");
                writer.write("    SAHF\n\n");
                continue;
            }

            // Branch (BLE, BGE, BLT, BGT, BEQ, BNE, BI)
            if (isBranch(token)) {
                if (pc + 1 < rpn.size()) {
                    String dest = rpn.get(pc + 1);
                    if (dest.matches("^\\d+$")) {
                        String asmJump = mapBranchToAsm(token, dest);
                        writer.write("    " + asmJump + "\n\n");
                        pc++; // consumimos el destino
                        continue;
                    } else {
                        throw new RuntimeException("RPN inválida: branch sin destino numérico en pc=" + pc);
                    }
                }
            }

            // Operando normal -> lo pusheamos en la pila virtual
            if (isNumberLiteral(token)) {
                evalStack.push("_" + token); // Coincide con la etiqueta de .data
            } else if (isStringLiteral(token)) { // <--- AÑADIDO
                evalStack.push(token); // Pushea el string crudo (ej: "hola")
            } else {
                evalStack.push(token);
            }
        }

        // --- INICIO DE LA CORRECCIÓN ---
        // Chequeo final: ¿Hay algún salto al final del programa?
        // (ej: un BI a la celda rpn.size())
        if (jumpTargets.contains(rpn.size())) {
            writer.write(String.format("L%d:\n", rpn.size()));
        }
        // --- FIN DE LA CORRECCIÓN ---

        // FIN DEL PROGRAMA
        writer.write("    MOV AX, 4C00h\n");
        writer.write("    INT 21h\n");
        writer.write("END START\n");
    }

    /** Reinicia el estado interno para una nueva generación. */
    private void resetState() {
        tempCounter = 0;
        temporaries.clear();
        stringLiterals.clear();
        jumpTargets.clear();
        operands.clear();
    }

    /**
     * PASADA 1: Recorre la RPN para encontrar Jumps, Operandos y Strings.
     */
    private void performFirstPreScan(List<String> rpn) {
        for (int i = 0; i < rpn.size(); i++) {
            String tok = rpn.get(i);

            if (isBranch(tok)) {
                if (i + 1 < rpn.size()) {
                    String dest = rpn.get(i + 1);
                    if (dest.matches("^\\d+$")) {
                        jumpTargets.add(Integer.parseInt(dest));
                    }
                }
                i++; // <-- AÑADIR ESTA LÍNEA para saltar el token de destino
            } else if (tok.equals("WRITE")) {
                // No hace nada, es un operador. NO mira hacia adelante.
            } else if (isStringLiteral(tok)) { // <--- AÑADIDO
                stringLiterals.add(tok);
            } else if (!isOperator(tok) && !isControlToken(tok)) {
                operands.add(tok);
            }
        }
    }

    /**
     * PASADA 2: Simula la ejecución de la RPN solo para descubrir
     * la cantidad de variables temporales necesarias.
     */
    private void performTemporaryDiscovery(List<String> rpn) {
        Stack<String> dryRunStack = new Stack<>();
        int dryRunTempCounter = 0;

        for (int pc = 0; pc < rpn.size(); pc++) {
            String token = rpn.get(pc);

            if (token.equals("WRITE")) {
                if (!dryRunStack.isEmpty()) dryRunStack.pop(); // Pop string
            } else if (isArithmeticOperator(token)) {
                if (dryRunStack.size() < 2) continue; // ignorar errores en dry run
                dryRunStack.pop();
                dryRunStack.pop();
                dryRunTempCounter++;
                String tempName = "@T" + dryRunTempCounter;
                temporaries.add(tempName); // ¡Descubierto!
                dryRunStack.push(tempName); // simula push del resultado
            } else if (token.equals(":=") || token.equals("CMP")) {
                if (dryRunStack.size() < 2) continue;
                dryRunStack.pop();
                dryRunStack.pop();
            } else if (isBranch(token)) {
                pc++; // skip destination
            } else if (!isOperator(token) && !isControlToken(token)) { // <-- MODIFICADO
                dryRunStack.push(token); // simula push de operando (y strings)
            }
        }
    }


    // --------------------------
    // Helpers
    // --------------------------

    private boolean isOperator(String t) {
        return isArithmeticOperator(t) || t.equals(":=") || t.equals("CMP") || isBranch(t) || t.equals("WRITE");
    }

    private boolean isArithmeticOperator(String t) {
        return t.equals("+") || t.equals("-") || t.equals("*") || t.equals("/");
    }

    private boolean isControlToken(String t) {
        return t.equals("_PLHDR");
    }

    private boolean isBranch(String t) {
        return t.equals("BLE") || t.equals("BGE") || t.equals("BLT") ||
                t.equals("BGT") || t.equals("BEQ") || t.equals("BNE") ||
                t.equals("BI");
    }

    private boolean isStringLiteral(String s) { // <--- AÑADIDO
        return s != null && s.startsWith("\"") && s.endsWith("\"");
    }

    private boolean isNumberLiteral(String s) {
        return s != null && (s.matches("^-?\\d+$") || s.matches("^-?\\d+\\.\\d+$"));
    }

    private String mapBranchToAsm(String br, String dest) {
        switch (br) {
            case "BLE": return "JNA L" + dest; // Jump if Not Above (<=)
            case "BGE": return "JAE L" + dest; // Jump if Above or Equal (>=)
            case "BLT": return "JB L"  + dest; // Jump if Below (<)
            case "BGT": return "JA L"  + dest; // Jump if Above (>)
            case "BEQ": return "JE L"  + dest; // Jump if Equal (==)
            case "BNE": return "JNE L" + dest; // Jump if Not Equal (!=)
            case "BI":  return "JMP L" + dest; // Salto Incondicional
        }
        return null;
    }

    /**
     * Obtiene el nombre del siguiente temporal (ej: @T1).
     * El temporal ya fue declarado en .data gracias al pre-análisis.
     */
    private String generateTempName() {
        tempCounter++;
        return "@T" + tempCounter;
    }

    /**
     * Limpia un literal (ej: quita comillas) y devuelve su etiqueta única.
     */

     
    private String getStringLiteralLabel(String raw) {
        String clean = raw;
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1);
        }
        // Usar hash del string limpio para evitar colisiones si "foo" y \"foo\" existen
        return "_STR_" + Math.abs(clean.hashCode());
    }

    /**
     * Devuelve la línea completa de definición de datos (db) para un literal.
     */
    private String getStringLiteralData(String raw) {
        String clean = raw;
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1);
        }
        String label = getStringLiteralLabel(raw);
        // Asegura que el string termine con '$' para la INT 21h/09h
        return label + " db \"" + clean + "$\"\n";
    }
}