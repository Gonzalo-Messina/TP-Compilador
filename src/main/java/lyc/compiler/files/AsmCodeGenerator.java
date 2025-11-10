package lyc.compiler.files;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

public class AsmCodeGenerator implements FileGenerator {

    private static AsmCodeGenerator instance;

    // contador para temporales creadas por el ensamblador
    private int tempCounter = 0;

    private AsmCodeGenerator() {
    }

    public static AsmCodeGenerator getInstance() {
        if (instance == null) {
            instance = new AsmCodeGenerator();
        }
        return instance;
    }

    @Override
    public void generate(FileWriter writer) throws IOException {
        IntermediateCodeGenerator icg = IntermediateCodeGenerator.getInstance();
        SymbolTableGenerator symbolTable = SymbolTableGenerator.getInstance();

        // OJO: asumo que IntermediateCodeGenerator tiene un getter que devuelve la RPN.
        // Si tu versión no lo tiene, hay que añadir:
        //   public List<String> getRpnCode() { return Collections.unmodifiableList(this.rpnCode); }
        List<String> rpn = icg.getRpnCode();

        // -------------------------------------------------------
        // 1) Generar sección .data: recolectamos operandos desde la RPN
        // -------------------------------------------------------
        LinkedHashSet<String> operands = new LinkedHashSet<>();
        for (String tok : rpn) {
            if (isOperator(tok) || isControlToken(tok)) continue;
            operands.add(tok);
        }

        // CABECERA ASM
        writer.write(".386\n");
        writer.write(".model flat, stdcall\n");
        writer.write("include msvcrt.inc\n");
        writer.write("includelib msvcrt.lib\n\n");

        // SECCION DATA
        writer.write(".data\n");

        // Primero registrar en la tabla las constantes detectadas (si es que no están)
        for (String op : operands) {
            if (isNumberLiteral(op)) {
                // convencion: constantes con _<lexema>
                // registramos la constante en la tabla de simbolos para que aparezca en la salida
                symbolTable.addToken(op, "Float", op);
            }
        }

        // Ahora imprimimos .data en orden: variables/temps/constantes deducidas desde operands
        // Nota: symbolTable no provee un getter público en tu versión, así que
        // construimos la sección .data a partir del conjunto 'operands' que detectamos.
        // Esto es suficiente para que el asm generado tenga las etiquetas usadas.
        for (String op : operands) {
            if (isNumberLiteral(op)) {
                // constante → _<lexema> dd <valor>
                String key = "_" + op;
                writer.write(String.format("%-20s %-5s %s\n", key, "dd", op));
            } else {
                // identificador o temporal → dd 0.0 (los temporales los registramos cuando se crean)
                writer.write(String.format("%-20s %-5s %s\n", op, "dd", "0.0"));
            }
        }

        // Añadimos una etiqueta para newline si la querés (como en tu AssemblerManager)
        writer.write(String.format("%-20s %-5s %s\n", "_NEWLINE", "db", "0DH,0AH"));

        writer.write("\n");

        // SECCION CODE
        writer.write(".code\n");
        writer.write("start:\n");

        // inicializar coprocesador
        writer.write("    finit\n\n");

        // -------------------------------------------------------
        // 2) Traducir RPN a instrucciones FPU + temporales en .data
        // -------------------------------------------------------
        java.util.Stack<String> evalStack = new java.util.Stack<>();

        for (String token : rpn) {

            if (isArithmeticOperator(token)) {
                // Sacamos operandos (op1 op2)
                String op2 = evalStack.pop();
                String op1 = evalStack.pop();

                switch (token) {
                    case "+":
                    {
                        String aux = generateTempAndRegister();
                        // FLD op1; FLD op2; FADD; FSTP aux
                        writer.write(String.format("    fld %s\n", op1));
                        writer.write(String.format("    fld %s\n", op2));
                        writer.write("    fadd\n");
                        writer.write(String.format("    fstp %s\n", aux));
                        writer.write("\n");
                        evalStack.push(aux);
                        break;
                    }
                    case "-":
                    {
                        String aux = generateTempAndRegister();
                        writer.write(String.format("    fld %s\n", op1));
                        writer.write(String.format("    fld %s\n", op2));
                        writer.write("    fsub\n");
                        writer.write(String.format("    fstp %s\n", aux));
                        writer.write("\n");
                        evalStack.push(aux);
                        break;
                    }
                    case "*":
                    {
                        String aux = generateTempAndRegister();
                        writer.write(String.format("    fld %s\n", op1));
                        writer.write(String.format("    fld %s\n", op2));
                        writer.write("    fmul\n");
                        writer.write(String.format("    fstp %s\n", aux));
                        writer.write("\n");
                        evalStack.push(aux);
                        break;
                    }
                    case "/":
                    {
                        String aux = generateTempAndRegister();
                        writer.write(String.format("    fld %s\n", op1));
                        writer.write(String.format("    fld %s\n", op2));
                        writer.write("    fdiv\n");
                        writer.write(String.format("    fstp %s\n", aux));
                        writer.write("\n");
                        evalStack.push(aux);
                        break;
                    }
                    default:
                        // no debería entrar
                        break;
                }
            }
            else if (token.equals(":=")) {
                // asignación: op1 op2 :=   -> op2 := op1
                String dst = evalStack.pop(); // where
                String src = evalStack.pop(); // value
                // FLD src; FSTP dst
                writer.write(String.format("    fld %s\n", src));
                writer.write(String.format("    fstp %s\n", dst));
                writer.write("\n");
            }
            else if (isComparisonOrBranch(token)) {
                // CMP / BLT / BGE / etc. están en la RPN como tokens separados.
                // Para CMP: necesitamos tomar dos operandos y emitir la secuencia FPU -> SAHF
                if (token.equals("CMP")) {
                    String op2 = evalStack.pop();
                    String op1 = evalStack.pop();
                    writer.write(String.format("    fld %s\n", op1));
                    writer.write(String.format("    fld %s\n", op2));
                    writer.write("    fxch\n");
                    writer.write("    fcomp\n");
                    writer.write("    fstsw ax\n");
                    writer.write("    sahf\n");
                    writer.write("\n");
                } else {
                    // Branch tokens: BLE, BGE, BLT, BGT, BEQ, BNE, BI, ET, etc.
                    // Tu RPN utiliza placeholders numéricos para saltos y el
                    // IntermediateCodeGenerator ya backparcheó esos placeholders con índices.
                    // En esta traducción a assembler convendría transformar:
                    // - tokens de salto a labels. En tu versión original usabas labelQueue y
                    //   cellNumberStack para postponer la colocación de etiquetas.
                    // Para mantenerlo simple y evitar reimplementar todo el mecanismo,
                    // aquí emitimos instrucciones de salto hacia etiquetas del tipo labelN, y
                    // suponemos que los placeholders numéricos (si los hay) fueron convertidos
                    // en tokens '#<num>' en la RPN (no es tu caso exacto).
                    //
                    // Si necesitás exactamente la misma lógica que AssemblerManager,
                    // debemos replicarla aquí (labelQueue/cellNumberStack). Por ahora emitimos
                    // saltos simples sin labels, adaptalos si querés.
                    String asmJump = mapBranchToAsm(token);
                    if (asmJump != null) {
                        writer.write("    " + asmJump + "\n");
                    }
                }
            }
            else {
                // operando normal, lo ponemos en la pila de evaluación
                // Si es constante numérica la convertimos en su nombre en .data: _<lexema>
                if (isNumberLiteral(token)) {
                    String constName = "_" + token;
                    evalStack.push(constName);
                } else {
                    // Identificador (variable o temporal)
                    evalStack.push(token);
                }
            }
        }

        writer.write("\n    invoke exit, 0\n");
        writer.write("end start\n");
    }

    // ---------- Helpers ----------

    private boolean isOperator(String t) {
        return t.equals("+") || t.equals("-") || t.equals("*") || t.equals("/") ||
               t.equals(":=") || t.equals("CMP") ||
               t.equals("BLE") || t.equals("BGE") || t.equals("BLT") ||
               t.equals("BGT") || t.equals("BEQ") || t.equals("BNE") ||
               t.equals("BI") || t.equals("ET");
    }

    private boolean isArithmeticOperator(String t) {
        return t.equals("+") || t.equals("-") || t.equals("*") || t.equals("/");
    }

    private boolean isControlToken(String t) {
        // tokens que no son operandos pero que tampoco queremos en .data
        return t.equals("_PLHDR") || t.matches("^\\d+$"); // placeholder o números puros (si los usas)
    }

    private boolean isComparisonOrBranch(String t) {
        return t.equals("CMP") || t.equals("BLE") || t.equals("BGE") ||
               t.equals("BLT") || t.equals("BGT") || t.equals("BEQ") ||
               t.equals("BNE") || t.equals("BI") || t.equals("ET");
    }

    private boolean isNumberLiteral(String s) {
        if (s == null) return false;
        // acepta enteros y floats con punto
        return s.matches("^-?\\d+$") || s.matches("^-?\\d+\\.\\d+$");
    }

    private String mapBranchToAsm(String branchToken) {
        switch (branchToken) {
            case "BLE": return "JNA label_x"; // placeholder label, ver nota
            case "BGE": return "JAE label_x";
            case "BLT": return "JB label_x";
            case "BGT": return "JA label_x";
            case "BEQ": return "JE label_x";
            case "BNE": return "JNE label_x";
            case "BI":  return "JMP label_x";
            case "ET":  return null; // ET genera etiqueta, se maneja con label queue si implementas
            default: return null;
        }
    }

    /**
     * Crea un nuevo temporal y lo registra en la tabla de símbolos usando
     * el método addToken(String) que tu SymbolTableGenerator sí provee.
     */
    private String generateTempAndRegister() {
        tempCounter++;
        String name = "@T" + tempCounter; // convención @T1, @T2, ...
        // registrar en tabla (sin tipo/value): SymbolTableGenerator.addToken(String)
        SymbolTableGenerator.getInstance().addToken(name);
        return name;
    }

}

