package lyc.compiler.files;

import lyc.compiler.model.CompilerException;
import lyc.compiler.model.DuplicateVariableException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SymbolTableGenerator implements FileGenerator {
    private static SymbolTableGenerator symbolTable;

    // Mantengo LinkedHashMap para preservar orden de inserción (más prolijo en la salida)
    private final Map<String, SymbolTableData> symbols;

    private SymbolTableGenerator() {
        this.symbols = new LinkedHashMap<>();
    }

    public static SymbolTableGenerator getInstance() {
        if (symbolTable == null) {
            symbolTable = new SymbolTableGenerator();
        }
        return symbolTable;
    }

    @Override
    public void generate(FileWriter fileWriter) throws IOException {
        // 1) Armo todas las filas (header + datos) para poder medir anchos
        final String[] header = {"NOMBRE", "TIPODATO", "VALOR", "LONGITUD"};
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        rows.add(header);
    
        for (Map.Entry<String, SymbolTableData> e : this.symbols.entrySet()) {
            SymbolTableData d = e.getValue();
            String nombre  = nz(e.getKey());
            String tipo    = d.getType()   == null ? "" : d.getType();
            String valor   = d.getValue()  == null ? "" : d.getValue();
            String longitud= d.getLength() == null ? "" : d.getLength();
            rows.add(new String[]{nombre, tipo, valor, longitud});
        }
    
        // 2) Calcular anchos máximos por columna
        int[] w = new int[4];
        for (String[] r : rows) {
            for (int i = 0; i < 4; i++) {
                if (r[i].length() > w[i]) w[i] = r[i].length();
            }
        }
    
        // 3) Imprimir con formato dinámico (3 cols izquierdas, LONGITUD derecha)
        final String SEP = " | ";
        StringBuilder out = new StringBuilder();
    
        for (int idx = 0; idx < rows.size(); idx++) {
            String[] r = rows.get(idx);
            // NOMBRE, TIPODATO, VALOR -> left
            out.append(padRight(r[0], w[0])).append(SEP)
               .append(padRight(r[1], w[1])).append(SEP)
               .append(padRight(r[2], w[2])).append(SEP)
               // LONGITUD -> right
               .append(String.format("%" + w[3] + "s", r[3]))
               .append(System.lineSeparator());
    
            // línea separadora debajo del header
            if (idx == 0) {
                out.append(repeat('-', w[0])).append(SEP)
                   .append(repeat('-', w[1])).append(SEP)
                   .append(repeat('-', w[2])).append(SEP)
                   .append(repeat('-', w[3]))
                   .append(System.lineSeparator());
            }
        }
    
        fileWriter.write(out.toString());
    }

    /** Registra un token “genérico” (lo dejo como lo tenías). */
    public void addToken(String token) {
        this.symbols.putIfAbsent(token, new SymbolTableData());
    }

    /** Registra una CONSTANTE con tipo explícito: corrige LONGITUD y deja prefijo "_" */
    public void addToken(String token, String dataType) {
        String type = normalizeType(dataType);
        int length = computeLength(token, type);   // <-- corrige off-by-one y comillas
        String key = "_" + token;                  // constantes con prefijo, como hacías
        this.symbols.putIfAbsent(key, new SymbolTableData(type, token, Integer.toString(length)));
    }

    /** Sobrecarga por si en algún lugar te pasan value distinto del lexema. */
    public void addToken(String token, String dataType, String value) {
        String type = normalizeType(dataType);
        int length = computeLength(value != null ? value : token, type);
        String key = "_" + token;
        this.symbols.putIfAbsent(key, new SymbolTableData(type, value, Integer.toString(length)));
    }

    /** Asigna tipo a un identificador YA existente (tu método original). */
    public void addDataType(String id, String dataType) throws CompilerException {
        SymbolTableData data = this.symbols.get(id);
        if (data == null) {
            data = new SymbolTableData();
            this.symbols.put(id, data);
        }
        if (data.getType() != null) {
            throw new DuplicateVariableException("Variable " + id + " ya definida");
        }
        data.setType(normalizeType(dataType));
    }

    /** NUEVO: registra todas las VARIABLES declaradas con un tipo. */
    public void addIdentifiers(List names, String typeDef) {
        String type = normalizeType(String.valueOf(typeDef));
        for (Object o : names) {
            String id = String.valueOf(o);
            if (id == null || id.isEmpty()) continue;

            // Si no existía, la creo con tipo y longitud = largo del nombre. Valor vacío.
            if (!this.symbols.containsKey(id)) {
                this.symbols.put(id, new SymbolTableData(type, "", Integer.toString(id.length())));
            } else {
                // Si existía sin tipo, lo seteo (no piso un tipo ya asignado).
                SymbolTableData data = this.symbols.get(id);
                if (data.getType() == null) data.setType(type);
            }
        }
    }

    // --- Helpers ------------------------------------------------------------------

    private static String normalizeType(String t) {
        if (t == null) return null;
        String u = t.trim();
        if (u.equalsIgnoreCase("int") || u.equalsIgnoreCase("integer")) return "Int";
        if (u.equalsIgnoreCase("float")) return "Float";
        if (u.equalsIgnoreCase("string")) return "String";
        return u;
    }

    /** Longitud: dígitos para números; sin comillas en strings; else length crudo. */
    private static int computeLength(String lexeme, String type) {
        if (lexeme == null) return 0;
        if ("Int".equals(type) || "Float".equals(type)) {
            int c = 0;
            for (char ch : lexeme.toCharArray()) if (Character.isDigit(ch)) c++;
            return c;
        }
        if ("String".equals(type)) {
            if (lexeme.length() >= 2 && lexeme.startsWith("\"") && lexeme.endsWith("\""))
                return lexeme.length() - 2;
            return lexeme.length();
        }
        return lexeme.length();
    }
    
    private static String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s;
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }
    
    private static String repeat(char ch, int n) {
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) b.append(ch);
        return b.toString();
    }
    
    private static String nz(String s) { return s == null ? "" : s; }
}
